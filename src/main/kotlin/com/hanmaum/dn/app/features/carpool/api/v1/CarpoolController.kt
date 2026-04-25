package com.hanmaum.dn.app.features.carpool.api.v1

import com.hanmaum.dn.app.features.carpool.api.v1.dto.CarDto
import com.hanmaum.dn.app.features.carpool.api.v1.dto.CreateCarRequest
import com.hanmaum.dn.app.features.carpool.service.CarpoolService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/carpool")
class CarpoolController(
    private val carpoolService: CarpoolService,
) {
    @GetMapping
    fun getCars(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) date: LocalDate,
        // Später holen wir die ID aus dem SecurityContext (Token)
        // Für jetzt simulieren wir es via Header oder Parameter, oder du lässt es null
        @RequestParam(required = false) myMemberId: String?,
    ): List<CarDto> = carpoolService.getCarsForDate(date, myMemberId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCar(
        @RequestBody req: CreateCarRequest,
    ) {
        carpoolService.createCar(req)
    }

    @PostMapping("/{carId}/join")
    fun joinCar(
        @PathVariable carId: Long,
        @RequestParam memberId: String,
    ) {
        carpoolService.joinCar(carId, memberId)
    }

    @PostMapping("/{carId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveCar(
        @PathVariable carid: Long,
        @RequestParam memberId: String,
    ) {
        carpoolService.leaveCar(carid, memberId)
    }
}
