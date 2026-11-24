package com.hanmaum.dn.app.common.observability

import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class RequestDiagnosticsFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(RequestDiagnosticsFilter::class.java)

    override fun shouldNotFilterAsyncDispatch(): Boolean = false

    override fun shouldNotFilterErrorDispatch(): Boolean = false

    override fun doFilterNestedErrorDispatch(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) = doFilterInternal(request, response, filterChain)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val state =
            request.getAttribute(STATE_ATTRIBUTE) as? RequestState
                ?: RequestState(UUID.randomUUID().toString()).also { request.setAttribute(STATE_ATTRIBUTE, it) }
        if (!response.isCommitted) response.setHeader(REQUEST_ID_HEADER, state.id)
        withRequestId(state.id) {
            // This tracks an escaping failure without intercepting or swallowing it.
            var returned = false
            try {
                filterChain.doFilter(request, response)
                returned = true
            } finally {
                if (!returned) state.failed.set(true)
                val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
                if (route != null && ROUTE_PATTERN.matches(route)) state.route.compareAndSet(null, route)
                if (request.isAsyncStarted) {
                    registerAsyncCompletion(request, response, state)
                } else {
                    recordFailure(request, response, state)
                }
            }
        }
    }

    private fun registerAsyncCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        state: RequestState,
    ) {
        if (!state.listenerRegistered.compareAndSet(false, true)) return
        val listener =
            object : AsyncListener {
                override fun onComplete(event: AsyncEvent) = recordFailure(request, response, state)

                override fun onError(event: AsyncEvent) {
                    state.failed.set(true)
                }

                override fun onTimeout(event: AsyncEvent) {
                    state.failed.set(true)
                }

                override fun onStartAsync(event: AsyncEvent) {
                    event.asyncContext.addListener(this)
                }
            }
        try {
            request.asyncContext.addListener(listener)
        } catch (_: IllegalStateException) {
            // Completion may race listener registration. Record the final response,
            // while retaining the same atomic once-only guard as an async dispatch.
            recordFailure(request, response, state)
        }
    }

    private fun recordFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        state: RequestState,
    ) {
        val status = if (response.status < 400 && state.failed.get()) 500 else response.status
        if (status !in 500..599 || !state.recorded.compareAndSet(false, true)) return
        val route = state.route.get() ?: request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
        val safeRoute = route?.takeIf { ROUTE_PATTERN.matches(it) } ?: "UNMAPPED"
        val method = request.method.takeIf { it in METHODS } ?: "OTHER"
        withRequestId(state.id) {
            log
                .atError()
                .addKeyValue("event_kind", "http_5xx")
                .addKeyValue("http_status", status)
                .addKeyValue("http_method", method)
                .addKeyValue("route_template", safeRoute)
                .log("HTTP server failure")
        }
    }

    private fun withRequestId(
        id: String,
        action: () -> Unit,
    ) {
        val previous = MDC.get(REQUEST_ID_MDC)
        MDC.put(REQUEST_ID_MDC, id)
        try {
            action()
        } finally {
            if (previous == null) MDC.remove(REQUEST_ID_MDC) else MDC.put(REQUEST_ID_MDC, previous)
        }
    }

    private class RequestState(
        val id: String,
        val failed: AtomicBoolean = AtomicBoolean(false),
        val recorded: AtomicBoolean = AtomicBoolean(false),
        val listenerRegistered: AtomicBoolean = AtomicBoolean(false),
        val route: AtomicReference<String?> = AtomicReference(null),
    )

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-ID"
        const val REQUEST_ID_MDC = "request_id"
        private const val STATE_ATTRIBUTE = "com.hanmaum.dn.requestDiagnostics"
        private val ROUTE_PATTERN = Regex("[A-Za-z0-9_/{}/.*:-]{1,240}")
        private val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE")
    }
}
