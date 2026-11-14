package com.motif

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MotifBackendApplication

fun main(args: Array<String>) {
	runApplication<MotifBackendApplication>(*args)
}
