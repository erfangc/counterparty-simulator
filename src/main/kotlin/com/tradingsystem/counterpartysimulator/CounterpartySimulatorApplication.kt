package com.tradingsystem.counterpartysimulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CounterpartySimulatorApplication

fun main(args: Array<String>) {
	runApplication<CounterpartySimulatorApplication>(*args)
}
