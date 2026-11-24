package com.hanmaum.dn.app.common.observability

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer

/** Additional export-safe fields, not a sanitizer for the existing raw log message. */
class SafeLogJsonCustomizer : StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    override fun customize(members: JsonWriter.Members<ILoggingEvent>) {
        members
            .add("diagnostic_exception_type") { event: ILoggingEvent ->
                event.throwableProxy?.className?.takeIf { CLASS_NAME.matches(it) }
            }.whenHasLength()
        members
            .add("diagnostic_stack") { event: ILoggingEvent -> safeFrames(event.throwableProxy) }
            .whenNotEmpty()
    }

    private fun safeFrames(throwable: IThrowableProxy?): List<String> =
        generateSequence(throwable) { it.cause }
            .take(MAX_CAUSES)
            .flatMap { it.stackTraceElementProxyArray.orEmpty().asSequence() }
            .take(MAX_EXAMINED_FRAMES)
            .map { it.stackTraceElement }
            .filter { frame ->
                CLASS_NAME.matches(frame.className) &&
                    METHOD_NAME.matches(frame.methodName) &&
                    frame.fileName?.let { SOURCE_FILE.matches(it) } == true
            }.sortedBy { !it.className.startsWith("com.hanmaum.dn.") }
            .distinct()
            .take(MAX_FRAMES)
            .map { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber.coerceAtLeast(0)})" }
            .toList()

    private companion object {
        const val MAX_CAUSES = 3
        const val MAX_FRAMES = 12
        const val MAX_EXAMINED_FRAMES = 128
        val CLASS_NAME = Regex("[A-Za-z_$][A-Za-z0-9_.$]{0,199}")
        val METHOD_NAME = Regex("[A-Za-z_$<>][A-Za-z0-9_$<>]{0,119}")
        val SOURCE_FILE = Regex("[A-Za-z0-9_.$-]{1,100}\\.(kt|java)")
    }
}
