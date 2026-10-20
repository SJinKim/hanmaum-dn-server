package com.hanmaum.dn.app.features.guide.api.v1

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class GuideExceptionHandlerTest {
    private val handler = GuideExceptionHandler()

    private fun integrity(
        state: String,
        constraint: String,
    ) = DataIntegrityViolationException(
        "private note must not appear",
        ConstraintViolationException("private note", SQLException("private note", state), constraint),
    )

    @Test fun `only the active date constraint maps to conflict`() {
        assertEquals(409, handler.integrity(integrity("23505", "guide_worship_reports_active_date")).status)
        assertEquals(409, handler.conflict().status)
    }

    @Test fun `unrelated integrity errors are server failures with safe diagnostics`() {
        val logger = LoggerFactory.getLogger(GuideExceptionHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            assertEquals(500, handler.integrity(integrity("23514", "guide_worship_reports_service1_count_check")).status)
            assertEquals(
                500,
                handler.integrity(DataIntegrityViolationException("private actor too long", SQLException("private actor", "22001"))).status,
            )
            assertEquals(2, appender.list.size)
            assertFalse(appender.list.any { it.formattedMessage.contains("private") })
            assertEquals(
                true,
                appender.list
                    .first()
                    .formattedMessage
                    .contains("23514"),
            )
            assertEquals(
                true,
                appender.list
                    .last()
                    .formattedMessage
                    .contains("22001"),
            )
            appender.list.forEach { assertNull(it.throwableProxy) }
        } finally {
            logger.detachAppender(appender)
        }
    }
}
