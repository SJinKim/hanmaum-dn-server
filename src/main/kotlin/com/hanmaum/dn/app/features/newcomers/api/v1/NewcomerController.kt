package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.common.security.NewcomerReadAccess
import com.hanmaum.dn.app.common.security.NewcomerWriteAccess
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.GraduateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerGraduationResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerOptionsResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.UpdateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerIdentityStatus
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import com.hanmaum.dn.app.features.newcomers.service.NewcomerGraduationService
import com.hanmaum.dn.app.features.newcomers.service.NewcomerService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/newcomers")
class NewcomerController(
    private val service: NewcomerService,
    private val graduationService: NewcomerGraduationService,
) {
    @GetMapping
    @NewcomerReadAccess
    fun list(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) lifecycleStatus: NewcomerLifecycle?,
        @RequestParam(required = false) hasVisited: Boolean?,
        @RequestParam(required = false) caregiverPublicId: UUID?,
        @RequestParam(required = false) groupPublicId: UUID?,
        @RequestParam(required = false) identityStatus: NewcomerIdentityStatus?,
        @RequestParam(required = false) attendance: PostAssignmentAttendance?,
        @RequestParam(required = false) registeredFrom: LocalDate?,
        @RequestParam(required = false) registeredTo: LocalDate?,
        @RequestParam(defaultValue = "registrationDate") sort: String,
        @RequestParam(defaultValue = "desc") direction: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<Page<NewcomerResponse>>> =
        ResponseEntity.ok(
            ApiResponse.success(
                service.list(
                    search,
                    lifecycleStatus,
                    hasVisited,
                    caregiverPublicId,
                    groupPublicId,
                    identityStatus,
                    attendance,
                    registeredFrom,
                    registeredTo,
                    sort,
                    direction,
                    page,
                    size,
                ),
            ),
        )

    @GetMapping("/options")
    @NewcomerReadAccess
    fun options(): ResponseEntity<ApiResponse<NewcomerOptionsResponse>> = ResponseEntity.ok(ApiResponse.success(service.options()))

    @GetMapping("/{publicId}")
    @NewcomerReadAccess
    fun get(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<NewcomerResponse>> = ResponseEntity.ok(ApiResponse.success(service.get(publicId)))

    @PostMapping
    @NewcomerWriteAccess
    fun create(
        @Valid @RequestBody request: CreateNewcomerRequest,
    ): ResponseEntity<ApiResponse<NewcomerResponse>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(request)))

    @PatchMapping("/{publicId}")
    @NewcomerWriteAccess
    fun update(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateNewcomerRequest,
    ): ResponseEntity<ApiResponse<NewcomerResponse>> = ResponseEntity.ok(ApiResponse.success(service.update(publicId, request)))

    @DeleteMapping("/{publicId}")
    @NewcomerWriteAccess
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable publicId: UUID,
    ) = service.softDelete(publicId)

    @PostMapping("/{publicId}/graduate")
    @Operation(operationId = "graduateNewcomer")
    @NewcomerWriteAccess
    fun graduate(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: GraduateNewcomerRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<NewcomerGraduationResponse>> =
        ResponseEntity.ok(ApiResponse.success(graduationService.graduate(publicId, request, jwt.subject)))

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun invalidNewcomerValue(): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "A newcomer field contains an invalid value.")
        problem.title = HttpStatus.BAD_REQUEST.reasonPhrase
        return ResponseEntity.badRequest().body(problem)
    }
}
