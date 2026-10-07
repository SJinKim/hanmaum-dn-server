package com.hanmaum.dn.app.features.carpool.api.v1

import com.hanmaum.dn.app.common.security.Roles
import com.hanmaum.dn.app.features.carpool.api.v1.dto.CarDto
import com.hanmaum.dn.app.features.carpool.api.v1.dto.CreateCarRequest
import com.hanmaum.dn.app.features.carpool.service.CarpoolService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/carpool")
@PreAuthorize("isAuthenticated()")
class CarpoolController(
    private val carpoolService: CarpoolService,
) {
    @GetMapping
    fun getCars(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        authentication: JwtAuthenticationToken,
    ): List<CarDto> = carpoolService.getCarsForDate(date, authentication.token.subject)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCar(
        @RequestBody req: CreateCarRequest,
        authentication: JwtAuthenticationToken,
    ): UUID = carpoolService.createCar(req, authentication.token.subject, authentication.isAdmin())

    @PostMapping("/{carPublicId}/join")
    fun joinCar(
        @PathVariable carPublicId: UUID,
        authentication: JwtAuthenticationToken,
    ) {
        carpoolService.joinCar(carPublicId, authentication.token.subject)
    }

    @PostMapping("/{carPublicId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveCar(
        @PathVariable carPublicId: UUID,
        authentication: JwtAuthenticationToken,
    ) {
        carpoolService.leaveCar(carPublicId, authentication.token.subject)
    }

    private fun JwtAuthenticationToken.isAdmin() = authorities.any { it.authority == Roles.authority(Roles.ADMIN) }
}
