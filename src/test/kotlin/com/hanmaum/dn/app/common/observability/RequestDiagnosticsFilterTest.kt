package com.hanmaum.dn.app.common.observability

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.hanmaum.dn.app.common.config.GlobalExceptionHandler
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.mock.web.MockAsyncContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

class RequestDiagnosticsFilterTest {
    private val filter = RequestDiagnosticsFilter()
    private val logger = LoggerFactory.getLogger(RequestDiagnosticsFilter::class.java) as Logger
    private val appender =
        object : ListAppender<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                event.prepareForDeferredProcessing()
                super.append(event)
            }
        }

    @BeforeEach
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
        MDC.clear()
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        appender.stop()
        MDC.clear()
    }

    @Test
    fun `server generates unique IDs and restores the prior logging context`() {
        MDC.put("request_id", "outer-context")
        val ids =
            (1..2).map {
                val request = MockHttpServletRequest("GET", "/api/v1/example")
                val response = MockHttpServletResponse()
                request.addHeader("X-Request-ID", "untrusted-client-value")
                filter.doFilter(
                    request,
                    response,
                    FilterChain { _, _ ->
                        assertThat(MDC.get("request_id")).isEqualTo(response.getHeader("X-Request-ID"))
                    },
                )
                assertThat(MDC.get("request_id")).isEqualTo("outer-context")
                UUID.fromString(response.getHeader("X-Request-ID"))
            }
        assertThat(ids).doesNotHaveDuplicates()
        assertThat(appender.list).isEmpty()
    }

    @Test
    fun `5xx uses only the route template and excludes request payloads`() {
        val request = MockHttpServletRequest("POST", "/api/v1/members/private-person")
        val response = MockHttpServletResponse()
        request.queryString = "email=sensitive@example.invalid"
        request.setContent("private request body".toByteArray())
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/members/{id}")
        filter.doFilter(request, response, FilterChain { _, _ -> response.status = 503 })
        val event = appender.list.single()
        val fields = event.keyValuePairs.associate { it.key to it.value }
        assertThat(fields).containsEntry("event_kind", "http_5xx").containsEntry("http_status", 503)
        assertThat(fields).containsEntry("route_template", "/api/v1/members/{id}")
        assertThat(fields.toString()).doesNotContain("private-person", "sensitive@", "request body")
        assertThat(event.mdcPropertyMap["request_id"]).isEqualTo(response.getHeader("X-Request-ID"))
        assertThat(event.throwableProxy).isNull()
        assertThat(MDC.get("request_id")).isNull()
    }

    @Test
    fun `ordinary client errors do not create paging events`() {
        for (status in listOf(200, 400, 401, 403, 404, 409, 429)) {
            filter.doFilter(
                MockHttpServletRequest(),
                MockHttpServletResponse(),
                FilterChain { _, r ->
                    (r as MockHttpServletResponse).status =
                        status
                },
            )
        }
        assertThat(appender.list).isEmpty()
    }

    @Test
    fun `MVC error handling returns the correlation header and the actual controller template`() {
        val mvc =
            MockMvcBuilders
                .standaloneSetup(ProbeController())
                .setControllerAdvice(GlobalExceptionHandler())
                .addFilters<StandaloneMockMvcBuilder>(filter)
                .build()
        mvc
            .perform(get("/diagnostics-probe/private-person").queryParam("email", "sensitive@example.invalid"))
            .andExpect(status().isInternalServerError)
            .andExpect(header().exists("X-Request-ID"))
        val fields =
            appender.list
                .single()
                .keyValuePairs
                .associate { it.key to it.value }
        assertThat(fields).containsEntry("route_template", "/diagnostics-probe/{id}")
        assertThat(fields.toString()).doesNotContain("private-person", "sensitive@")
    }

    @Test
    fun `an escaping error is preserved and never falls back to the concrete URL`() {
        val request = MockHttpServletRequest("GET", "/private/sensitive@example.invalid")
        val response = MockHttpServletResponse()
        val failure = IllegalStateException("private exception payload")
        assertThatThrownBy { filter.doFilter(request, response, FilterChain { _, _ -> throw failure }) }
            .isSameAs(failure)
        val fields =
            appender.list
                .single()
                .keyValuePairs
                .associate { it.key to it.value }
        assertThat(fields).containsEntry("route_template", "UNMAPPED").containsEntry("http_status", 500)
        assertThat(appender.list.single().formattedMessage).doesNotContain("private exception payload")
        assertThat(MDC.get("request_id")).isNull()
    }

    @Test
    fun `async completion without dispatch logs once and clears the listener MDC`() {
        val request = MockHttpServletRequest("GET", "/async")
        val response = MockHttpServletResponse()
        request.isAsyncSupported = true
        filter.doFilter(request, response, FilterChain { _, _ -> request.startAsync(request, response) })
        assertThat(appender.list).isEmpty()
        val id = response.getHeader("X-Request-ID")
        response.status = 500
        val context = request.asyncContext as MockAsyncContext
        context.complete()
        request.dispatcherType = DispatcherType.ERROR
        filter.doFilter(request, response, FilterChain { _, _ -> })
        assertThat(appender.list).hasSize(1)
        assertThat(appender.list.single().mdcPropertyMap["request_id"]).isEqualTo(id)
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(id)
        assertThat(MDC.get("request_id")).isNull()
    }

    @RestController
    class ProbeController {
        @GetMapping("/diagnostics-probe/{id}")
        fun fail(
            @PathVariable id: String,
        ): String = error("synthetic failure for $id")
    }
}
