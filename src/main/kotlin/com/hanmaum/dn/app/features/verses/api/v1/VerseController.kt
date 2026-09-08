package com.hanmaum.dn.app.features.verses.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.verses.api.v1.dto.DailyVerseResponse
import com.hanmaum.dn.app.features.verses.api.v1.dto.SetWeeklyVerseRequest
import com.hanmaum.dn.app.features.verses.api.v1.dto.WeeklyVerseResponse
import com.hanmaum.dn.app.features.verses.service.VerseService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RestController
@RequestMapping("/verses")
class VerseController(
    private val verseService: VerseService,
) {
    /**
     * GET /api/v1/verses/today
     * Role: any authenticated member — the 오늘의 말씀 card on Home.
     *
     * An empty payload means the reading plan has no passage today, which is the normal
     * state on Sundays. A 503 means the congregation's bible API could not be reached; the
     * two are deliberately distinguishable, so the card never claims there is no passage
     * when there is one.
     */
    @GetMapping("/today")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "200", description = "Today's passage, or an empty payload on a day without one.")
    @OpenApiResponse(responseCode = "503", description = "The congregation's bible API could not be reached.")
    fun getToday(): ResponseEntity<ApiResponse<DailyVerseResponse>> = ResponseEntity.ok(ApiResponse.success(data = verseService.getToday()))

    /**
     * GET /api/v1/verses/weekly
     * Role: any authenticated member — the 주간 암송 구절 card on Home.
     *
     * An empty payload means no verse has been chosen for the running week. Unlike the daily
     * passage this one carries its text: a memory verse is short, and reciting it is the
     * point of the card.
     */
    @GetMapping("/weekly")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "200", description = "This week's memory verse, or an empty payload if unset.")
    @OpenApiResponse(responseCode = "503", description = "The congregation's bible API could not be reached.")
    fun getWeekly(): ResponseEntity<ApiResponse<WeeklyVerseResponse>> =
        ResponseEntity.ok(ApiResponse.success(data = verseService.getWeekly()))

    /**
     * PUT /api/v1/verses/weekly
     * Role: ADMIN — chooses the memory verse for a week.
     *
     * The upstream has no weekly-verse endpoint, so this selection is the only source there
     * is. Re-sending for the same week overwrites; weekStart defaults to the running week.
     */
    @PutMapping("/weekly")
    @PreAuthorize("hasRole('ADMIN')")
    fun setWeekly(
        @Valid @RequestBody request: SetWeeklyVerseRequest,
    ): ResponseEntity<ApiResponse<WeeklyVerseResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(data = verseService.setWeekly(request), message = "주간 암송 구절이 설정되었습니다."),
        )
}
