package com.creatorledger.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PlatformBackendApplication

fun main(args: Array<String>) {
	runApplication<PlatformBackendApplication>(*args)
}
