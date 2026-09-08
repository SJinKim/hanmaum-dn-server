package com.hanmaum.dn.app.features.verses.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.verses.api.v1.dto.MarkVerseRecordRequest
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordBlock
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordsResponse
import com.hanmaum.dn.app.features.verses.service.VerseRecordService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/**
 * Streaks for the two Home verse cards.
 *
 * There is deliberately no DELETE. "No taking it back" is enforced by the operation not
 * existing, not by trusting a client not to call it.
 */
@RestController
@RequestMapping("/verses/records")
class VerseRecordController(
    private val verseRecordService: VerseRecordService,
) {
    /**
     * GET /api/v1/verses/records
     * Role: any authenticated member — both streaks in one payload, so Home loads once.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getRecords(authentication: JwtAuthenticationToken): ResponseEntity<ApiResponse<VerseRecordsResponse>> =
        ResponseEntity.ok(ApiResponse.success(data = verseRecordService.getRecords(authentication.token.subject)))

    /**
     * POST /api/v1/verses/records
     * Role: any authenticated member — marks today for one kind.
     *
     * The body carries no date: the server stamps the day, so "today only" is not a rule a
     * changed device clock can defeat. A day already marked is a 409, which the client
     * treats as success — the member's intent already holds.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "201", description = "Marked; the refreshed block for that kind.")
    @OpenApiResponse(responseCode = "400", description = "Today cannot be marked for this kind.")
    @OpenApiResponse(responseCode = "409", description = "Already marked today. Clients treat this as success.")
    fun mark(
        authentication: JwtAuthenticationToken,
        @Valid @RequestBody request: MarkVerseRecordRequest,
    ): ResponseEntity<ApiResponse<VerseRecordBlock>> {
        val block = verseRecordService.mark(authentication.token.subject, request.kind)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = block, message = "기록되었습니다."))
    }
}
