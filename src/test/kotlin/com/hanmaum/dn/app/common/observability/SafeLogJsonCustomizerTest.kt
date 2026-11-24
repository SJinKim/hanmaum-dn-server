package com.hanmaum.dn.app.common.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.logback.StructuredLogEncoder
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment
import tools.jackson.databind.json.JsonMapper

class SafeLogJsonCustomizerTest {
    private val writer = JsonWriter.of<ILoggingEvent> { SafeLogJsonCustomizer().customize(it) }

    @Test
    fun `export fields retain technical frames but exclude messages and causes`() {
        val cause = IllegalArgumentException("sensitive@example.invalid authorization=private-value")
        cause.stackTrace = arrayOf(StackTraceElement("com.hanmaum.dn.app.ExampleService", "run", "ExampleService.kt", 42))
        val failure = IllegalStateException("private request body", cause)
        failure.stackTrace = arrayOf(StackTraceElement("org.springframework.example.Framework", "call", "Framework.java", 10))
        val json = writer.writeToString(event(failure))
        assertThat(json).contains("java.lang.IllegalStateException", "ExampleService.run(ExampleService.kt:42)")
        assertThat(json).doesNotContain("sensitive@", "private-value", "private request body", "authorization")
    }

    @Test
    fun `diagnostic stacks have a fixed frame limit and reject forged technical names`() {
        val failure = IllegalStateException("not exported")
        failure.stackTrace =
            arrayOf(StackTraceElement("sensitive@example.invalid", "run", "private/file.kt", 5)) +
            (1..50).map { StackTraceElement("com.hanmaum.dn.app.ExampleService", "run$it", "ExampleService.kt", it) }
        val json = writer.writeToString(event(failure))
        assertThat(json).doesNotContain("sensitive@", "private/file", "run13(")
        assertThat(Regex("ExampleService\\.run\\d+\\(").findAll(json).count()).isEqualTo(12)
    }

    @Test
    fun `normal logs do not invent exception diagnostics`() {
        assertThat(writer.writeToString(event(null))).isEqualTo("{}")
    }

    @Test
    fun `production ECS encoder loads the configured customizer and preserves correlation`() {
        val environment =
            MockEnvironment()
                .withProperty("logging.structured.json.customizer", SafeLogJsonCustomizer::class.java.name)
        val context = LoggerContext()
        context.putObject(Environment::class.java.name, environment)
        val encoder =
            StructuredLogEncoder().apply {
                this.context = context
                setFormat("ecs")
                start()
            }
        try {
            val failure = IllegalStateException("not for export")
            val entry = event(failure) as LoggingEvent
            entry.mdcPropertyMap = mapOf("request_id" to "server-generated-id")
            val json = JsonMapper.builder().build().readTree(encoder.encode(entry))
            assertThat(json.path("diagnostic_exception_type").asString()).isEqualTo("java.lang.IllegalStateException")
            assertThat(json.path("request_id").asString()).isEqualTo("server-generated-id")
            assertThat(json.path("log").path("level").asString()).isEqualTo("ERROR")
        } finally {
            encoder.stop()
            context.stop()
        }
    }

    private fun event(failure: Throwable?): ILoggingEvent =
        LoggingEvent(
            javaClass.name,
            LoggerFactory.getLogger(javaClass) as Logger,
            Level.ERROR,
            "message is not part of safe diagnostics",
            failure,
            null,
        )
}
