package com.tradingsystem.counterpartysimulator

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import quickfix.*
import quickfix.field.*
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.util.*

@Service
class CounterpartySimulator(private val objectMapper: ObjectMapper) : ApplicationAdapter() {

    private lateinit var acceptor: SocketAcceptor
    private val log = LoggerFactory.getLogger(CounterpartySimulator::class.java)

    companion object {
        private val senderCompID = System.getenv("FIX_SENDER_COMP_ID") ?: "TARGET"
        private val targetCompID = System.getenv("FIX_TARGET_COMP_ID") ?: "SENDER"
        private val socketAcceptorPort = System.getenv("FIX_SOCKET_ACCEPTOR_PORT") ?: "9876"
    }

    @PostConstruct
    fun setup() {
        val sessionSettings = loadSessionSettingsFromEnvironment()
        acceptor = SocketAcceptor(
            this,
            FileStoreFactory(sessionSettings),
            sessionSettings,
            ScreenLogFactory(),
            DefaultMessageFactory()
        )
        acceptor.start()
    }

    @PreDestroy
    fun stop() {
        acceptor.stop()
    }

    override fun onLogon(sessionId: SessionID?) {
        super.onLogon(sessionId)
    }

    override fun onLogout(sessionId: SessionID?) {
        super.onLogout(sessionId)
    }

    override fun toApp(message: Message, sessionId: SessionID?) {
        println("toApp: $message")
    }

    override fun fromApp(message: Message, sessionId: SessionID?) {
        println("fromApp: $message")
        if (message.header.getString(MsgType.FIELD) == MsgType.ORDER_SINGLE) {
            handleNewOrderSingle(message, sessionId)
        }
    }

    private fun handleNewOrderSingle(message: Message, sessionId: SessionID?) {
        val clOrdID = message.getString(ClOrdID.FIELD)
        val symbol = message.getString(Symbol.FIELD)
        val side = message.getChar(Side.FIELD)
        val orderQty = message.getDouble(OrderQty.FIELD)

        log.info("Building execution report for clOrdId=$clOrdID side=$side symbol=$symbol orderQty=$orderQty")
        val executionReport = Message()
        executionReport.header.setString(MsgType.FIELD, MsgType.EXECUTION_REPORT)
        val orderId = UUID.randomUUID().toString()
        executionReport.setField(OrderID(orderId))
        val execId = UUID.randomUUID().toString()
        executionReport.setField(ExecID(execId))
        executionReport.setField(ExecType(ExecType.FILL))
        executionReport.setField(OrdStatus(OrdStatus.FILLED))
        executionReport.setField(ClOrdID(clOrdID))
        executionReport.setField(Symbol(symbol))
        executionReport.setField(Side(side))
        executionReport.setField(LastShares(orderQty))
        val price = getQuote(symbol)
        executionReport.setField(AvgPx(price))
        executionReport.setField(LeavesQty(0.0))
        executionReport.setField(CumQty(orderQty))
        executionReport.setField(LastPx(price))
        log.info("Building execution report for clOrdId=$clOrdID side=$side symbol=$symbol orderQty=$orderQty price=$price execId=${execId} orderId=$orderId")

        Session.sendToTarget(executionReport, sessionId)
    }

    private val httpClient = HttpClient.newHttpClient()

    private val alphaVantageApiKey = System.getenv("ALPHA_VANTAGE_API_KEY")

    private fun getQuote(symbol: String): Double {
        val url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=$symbol&apikey=$alphaVantageApiKey"
        val start = System.currentTimeMillis()
        log.info("Calling GET $url")
        val httpResponse = httpClient.send(
            HttpRequest.newBuilder().GET().uri(URI.create(url)).build(),
            HttpResponse.BodyHandlers.ofString()
        )
        val responseBody = httpResponse.body()
        val stop = System.currentTimeMillis()
        log.info("Finished calling GET $url status=${httpResponse.statusCode()} body=$responseBody took ${stop - start}ms")
        val json = objectMapper.readTree(responseBody)
        val price = json.at("/Global Quote/05. price").asDouble()
        return price
    }


    private fun loadSessionSettingsFromEnvironment(): SessionSettings {
        val settings = """
        [DEFAULT]
        ConnectionType=acceptor
        ReconnectInterval=5
        SocketAcceptPort=$socketAcceptorPort
        StartTime=00:00:00
        EndTime=00:00:00
        HeartBtInt=30
        FileStorePath=./fix_file_store
        
        [SESSION]
        BeginString=FIX.4.4
        SenderCompID=$senderCompID
        TargetCompID=$targetCompID
        SocketAcceptPort=$socketAcceptorPort
        StartTime=00:00:00
        EndTime=00:00:00
        HeartBtInt=30
        """.trimIndent()
        val byteInputStream = ByteArrayInputStream(settings.toByteArray(Charset.defaultCharset()))
        val sessionSettings = SessionSettings(byteInputStream)
        return sessionSettings
    }
}