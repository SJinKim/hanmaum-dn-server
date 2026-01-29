package com.hanmaum.dn.app.features.carpool.service

import com.hanmaum.dn.app.features.carpool.api.v1.dto.CarDto
import com.hanmaum.dn.app.features.carpool.api.v1.dto.CreateCarRequest
import com.hanmaum.dn.app.features.carpool.domain.Car
import com.hanmaum.dn.app.features.carpool.domain.CarPassenger
import com.hanmaum.dn.app.features.carpool.repository.CarPassengerRepository
import com.hanmaum.dn.app.features.carpool.repository.CarRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class CarpoolService (
    private val carRepository: CarRepository,
    private val passengerRepository: CarPassengerRepository,
    private val memberRepository: MemberRepository
) {

    // 1. Liste holen (mit Info: Sitze ich drin?)
    @Transactional(readOnly = true)
    fun getCarsForDate(date: LocalDate, currentMemberPublicId: String?): List<CarDto> {
        val cars = carRepository.findAllBySessionDate(date)

        // Wir brauchen die interne ID des Users, um zu prüfen, ob er mitfährt
        val currentMemberId = currentMemberPublicId?.let {
            memberRepository.findByPublicId(UUID.fromString(it))
                .orElse(null)?.id
        }

        return cars.map { car ->
            // Prüfen ob User Passagier ist (könnte man performanter lösen, reicht aber für MVP)
            val isJoined = if (currentMemberId != null) {
                passengerRepository.findByCarIdAndMemberId(car.id!!, currentMemberId).isPresent
            } else false

            CarDto(
                id = car.id!!,
                driverName = "${car.driver.firstName} ${car.driver.lastName}",
                carName = car.name,
                maxSeats = car.maxSeats,
                currentPassengers = car.currentPassengers,
                departureLocation = car.departureLocation,
                departureTime = car.departureTime,
                isFull = car.currentPassengers >= car.maxSeats,
                isJoinedByMe = isJoined
            )
        }
    }

    // 2. Auto erstellen
    @Transactional
    fun createCar(req: CreateCarRequest): Long {
        val driver = memberRepository.findByPublicId(UUID.fromString(req.driverMemberId))
            .orElseThrow { EntityNotFoundException("Driver not found") }

        val car = Car(
            driver = driver,
            sessionDate = req.sessionDate,
            name = req.name,
            maxSeats = req.maxSeats,
            departureLocation = req.departureLocation,
            departureTime = req.departureTime
        )
        return carRepository.save(car).id!!
    }

    // 3. Einsteigen (Join)
    @Transactional
    fun joinCar(carId: Long, memberPublicId: String) {
        val car = carRepository.findById(carId)
            .orElseThrow { EntityNotFoundException("Car not found") }

        val member = memberRepository.findByPublicId(UUID.fromString(memberPublicId))
            .orElseThrow { EntityNotFoundException("Member not found") }

        // Validierungen
        if (car.currentPassengers >= car.maxSeats) {
            throw IllegalStateException("Car is full")
        }
        if (passengerRepository.isMemberAlreadyDriving(member.id!!, car.sessionDate)) {
            throw IllegalStateException("You are already in a car for this date")
        }

        // Passagier speichern
        passengerRepository.save(CarPassenger(car = car, member = member))

        // Counter erhöhen
        car.currentPassengers += 1
        carRepository.save(car)
    }

    // 4. Aussteigen (Leave)
    @Transactional
    fun leaveCar(carId: Long, memberPublicId: String) {
        val member = memberRepository.findByPublicId(UUID.fromString(memberPublicId))
            .orElseThrow { EntityNotFoundException("Member not found") }

        val passengerEntry = passengerRepository.findByCarIdAndMemberId(carId, member.id!!)
            .orElseThrow { EntityNotFoundException("You are not in this car") }

        val car = passengerEntry.car

        // Löschen
        passengerRepository.delete(passengerEntry)

        // Counter verringern
        car.currentPassengers -= 1
        carRepository.save(car)
    }
}