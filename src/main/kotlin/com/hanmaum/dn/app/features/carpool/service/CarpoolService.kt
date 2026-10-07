package com.hanmaum.dn.app.features.carpool.service

import com.hanmaum.dn.app.features.carpool.api.v1.dto.CarDto
import com.hanmaum.dn.app.features.carpool.api.v1.dto.CreateCarRequest
import com.hanmaum.dn.app.features.carpool.domain.Car
import com.hanmaum.dn.app.features.carpool.domain.CarPassenger
import com.hanmaum.dn.app.features.carpool.repository.CarPassengerRepository
import com.hanmaum.dn.app.features.carpool.repository.CarRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

/**
 * Every write acts for the caller, who comes from the JWT (#286). The member used to be a
 * request parameter, so anyone signed in could join, leave or drive as someone else.
 */
@Service
class CarpoolService(
    private val carRepository: CarRepository,
    private val passengerRepository: CarPassengerRepository,
    private val memberRepository: MemberRepository,
    private val currentMember: CurrentMemberResolver,
) {
    @Transactional(readOnly = true)
    fun getCarsForDate(
        date: LocalDate,
        callerSubject: String,
    ): List<CarDto> {
        val callerId = currentMember.require(callerSubject).id!!

        return carRepository.findAllBySessionDate(date).map { car ->
            CarDto(
                publicId = car.publicId,
                driverName = "${car.driver.firstName} ${car.driver.lastName}",
                carName = car.name,
                maxSeats = car.maxSeats,
                currentPassengers = car.currentPassengers,
                departureLocation = car.departureLocation,
                departureTime = car.departureTime,
                isFull = car.currentPassengers >= car.maxSeats,
                isJoinedByMe = passengerRepository.findByCarIdAndMemberId(car.id!!, callerId).isPresent,
            )
        }
    }

    /** The caller drives unless an admin names someone else. */
    @Transactional
    fun createCar(
        req: CreateCarRequest,
        callerSubject: String,
        isAdmin: Boolean,
    ): UUID {
        val caller = currentMember.require(callerSubject)
        val driver =
            when (val requested = req.driverMemberId) {
                null, caller.publicId -> caller
                else -> {
                    if (!isAdmin) {
                        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only an admin may set another driver.")
                    }
                    memberRepository
                        .findByPublicId(requested)
                        .orElseThrow { EntityNotFoundException("Driver not found") }
                }
            }

        val car =
            Car(
                driver = driver,
                sessionDate = req.sessionDate,
                name = req.name,
                maxSeats = req.maxSeats,
                departureLocation = req.departureLocation,
                departureTime = req.departureTime,
            )
        return carRepository.save(car).publicId
    }

    @Transactional
    fun joinCar(
        carPublicId: UUID,
        callerSubject: String,
    ) {
        val car = findCar(carPublicId)
        val member = currentMember.require(callerSubject)

        if (car.currentPassengers >= car.maxSeats) {
            throw IllegalStateException("Car is full")
        }
        if (passengerRepository.isMemberAlreadyDriving(member.id!!, car.sessionDate)) {
            throw IllegalStateException("You are already in a car for this date")
        }

        passengerRepository.save(CarPassenger(car = car, member = member))
        car.currentPassengers += 1
        carRepository.save(car)
    }

    @Transactional
    fun leaveCar(
        carPublicId: UUID,
        callerSubject: String,
    ) {
        val car = findCar(carPublicId)
        val member = currentMember.require(callerSubject)

        val passengerEntry =
            passengerRepository
                .findByCarIdAndMemberId(car.id!!, member.id!!)
                .orElseThrow { EntityNotFoundException("You are not in this car") }

        passengerRepository.delete(passengerEntry)
        car.currentPassengers -= 1
        carRepository.save(car)
    }

    private fun findCar(publicId: UUID): Car =
        carRepository
            .findByPublicId(publicId)
            .orElseThrow { EntityNotFoundException("Car not found") }
}
