package com.example.demo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.*

@SpringBootApplication
class MyApplication {
    @Bean
	fun http() = coRouter {
		GET("/") {
			ServerResponse.ok().bodyValueAndAwait("hello, world")
		}
	}
}

fun main(args: Array<String>) {
	runApplication<MyApplication>(*args)
}