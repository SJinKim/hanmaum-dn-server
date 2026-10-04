package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.NewcomerReadAccess
import com.hanmaum.dn.app.common.security.NewcomerWriteAccess
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.LinkNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitStatsResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.UpdateNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.service.NewcomerVisitService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * 방문 기록 (#261). Its own controller because `/newcomers/{publicId}` is UUID-typed;
 * the literal `/newcomers/visits` mapping wins over the template.
 */
@RestController
@RequestMapping("/newcomers/visits")
class NewcomerVisitController(
    private val service: NewcomerVisitService,
) {
    @GetMapping
    @NewcomerReadAccess
    @Operation(operationId = "listNewcomerVisits")
    fun list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<NewcomerVisitResponse>>> = ResponseEntity.ok(ApiResponse.success(service.list(from, to, page, size)))

    @GetMapping("/stats")
    @NewcomerReadAccess
    @Operation(operationId = "getNewcomerVisitStats")
    fun stats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ResponseEntity<ApiResponse<NewcomerVisitStatsResponse>> = ResponseEntity.ok(ApiResponse.success(service.stats(from, to)))

    @PostMapping
    @NewcomerWriteAccess
    @Operation(operationId = "createNewcomerVisit")
    fun create(
        @Valid @RequestBody request: CreateNewcomerVisitRequest,
    ): ResponseEntity<ApiResponse<NewcomerVisitResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = service.create(request), message = "방문이 기록되었습니다."))

    @PatchMapping("/{publicId}")
    @NewcomerWriteAccess
    @Operation(operationId = "updateNewcomerVisit")
    fun update(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateNewcomerVisitRequest,
    ): ResponseEntity<ApiResponse<NewcomerVisitResponse>> = ResponseEntity.ok(ApiResponse.success(service.update(publicId, request)))

    @PutMapping("/{publicId}/profile")
    @NewcomerWriteAccess
    @Operation(operationId = "linkNewcomerVisitProfile")
    fun linkProfile(
        @PathVariable publicId: UUID,
        @RequestBody request: LinkNewcomerVisitRequest,
    ): ResponseEntity<ApiResponse<NewcomerVisitResponse>> =
        ResponseEntity.ok(ApiResponse.success(service.linkProfile(publicId, request.newcomerPublicId)))

    @DeleteMapping("/{publicId}")
    @NewcomerWriteAccess
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteNewcomerVisit")
    fun delete(
        @PathVariable publicId: UUID,
    ) = service.softDelete(publicId)
}
