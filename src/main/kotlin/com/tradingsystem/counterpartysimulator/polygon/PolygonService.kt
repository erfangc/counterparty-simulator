package com.tradingsystem.counterpartysimulator.polygon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers

@Service
class PolygonService(private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(PolygonService::class.java)
    private val httpClient = HttpClient.newHttpClient()
    private val polygonApiKey = System.getenv("POLYGON_API_KEY")
    fun previousClose(ticker: String): PreviousClose {
        val start = System.currentTimeMillis()
        val url = "https://api.polygon.io/v2/aggs/ticker/$ticker/prev?adjusted=true&apiKey=$polygonApiKey"
        val httpResponse = httpClient.send(
            HttpRequest
                .newBuilder()
                .GET()
                .uri(URI.create(url))
                .build(),
            BodyHandlers.ofString()
        )
        val stop = System.currentTimeMillis()
        val responseBody = httpResponse.body()
        val statusCode = httpResponse.statusCode()
        log.info("Finished calling GET $url status=$statusCode  body=$responseBody took ${stop - start}ms")
        if (statusCode == 200) {
            return objectMapper.readValue(responseBody)
        } else {
            error("polygon.io failed to respond check the logs for exact error")
        }
    }
}