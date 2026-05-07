package com.hanmaum.dn

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ChurchDnBackendApplication

fun main(args: Array<String>) {
    runApplication<ChurchDnBackendApplication>(*args)
}
