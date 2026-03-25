package com.hanmaum.dn.app.features.carpool.service

import com.hanmaum.dn.app.features.carpool.api.v1.dto.CreateCarRequest
import com.hanmaum.dn.app.features.carpool.domain.Car
import com.hanmaum.dn.app.features.carpool.domain.CarPassenger
import com.hanmaum.dn.app.features.carpool.repository.CarPassengerRepository
import com.hanmaum.dn.app.features.carpool.repository.CarRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CarpoolServiceTest {

    @Mock private lateinit var carRepository: CarRepository
    @Mock private lateinit var passengerRepository: CarPassengerRepository
    @Mock private lateinit var memberRepository: MemberRepository

    @InjectMocks
    private lateinit var carpoolService: CarpoolService

    private val sessionDate = LocalDate.of(2026, 3, 29)

    private fun member(id: Long, firstName: String = "철수", lastName: String = "김"): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        return m
    }

    private fun car(id: Long, driver: Member, maxSeats: Int = 4, currentPassengers: Int = 0): Car {
        val c = Car(
            driver = driver,
            sessionDate = sessionDate,
            name = "테스트 차",
            maxSeats = maxSeats,
            currentPassengers = currentPassengers,
        )
        c.id = id
        return c
    }

    // --- getCarsForDate ---

    @Test
    fun `getCarsForDate returns empty list when no cars`() {
        `when`(carRepository.findAllBySessionDate(any(LocalDate::class.java))).thenReturn(emptyList())

        assertTrue(carpoolService.getCarsForDate(sessionDate, null).isEmpty())
    }

    @Test
    fun `getCarsForDate maps car fields to dto`() {
        val driver = member(1L, "철수", "김")
        val c = car(10L, driver, maxSeats = 4, currentPassengers = 2)
        c.departureLocation = "강남역"
        c.departureTime = LocalTime.of(10, 0)
        `when`(carRepository.findAllBySessionDate(any(LocalDate::class.java))).thenReturn(listOf(c))

        val result = carpoolService.getCarsForDate(sessionDate, null)

        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals(10L, id)
            assertEquals("철수 김", driverName)
            assertEquals("테스트 차", carName)
            assertEquals(4, maxSeats)
            assertEquals(2, currentPassengers)
            assertEquals("강남역", departureLocation)
            assertFalse(isFull)
            assertFalse(isJoinedByMe)
        }
    }

    @Test
    fun `getCarsForDate marks car as full when seats exhausted`() {
        val driver = member(1L)
        val c = car(10L, driver, maxSeats = 3, currentPassengers = 3)
        `when`(carRepository.findAllBySessionDate(any(LocalDate::class.java))).thenReturn(listOf(c))

        val result = carpoolService.getCarsForDate(sessionDate, null)

        assertTrue(result[0].isFull)
    }

    @Test
    fun `getCarsForDate marks isJoinedByMe true when current member is a passenger`() {
        val driver = member(1L, "이", "드라이버")
        val currentMember = member(2L, "현재", "유저")
        val c = car(10L, driver)
        `when`(carRepository.findAllBySessionDate(any(LocalDate::class.java))).thenReturn(listOf(c))
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(currentMember))
        `when`(passengerRepository.findByCarIdAndMemberId(10L, 2L))
            .thenReturn(Optional.of(CarPassenger(car = c, member = currentMember)))

        val result = carpoolService.getCarsForDate(sessionDate, currentMember.publicId.toString())

        assertTrue(result[0].isJoinedByMe)
    }

    @Test
    fun `getCarsForDate marks isJoinedByMe false when current member is not a passenger`() {
        val driver = member(1L)
        val currentMember = member(2L)
        val c = car(10L, driver)
        `when`(carRepository.findAllBySessionDate(any(LocalDate::class.java))).thenReturn(listOf(c))
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(currentMember))
        `when`(passengerRepository.findByCarIdAndMemberId(10L, 2L)).thenReturn(Optional.empty())

        val result = carpoolService.getCarsForDate(sessionDate, currentMember.publicId.toString())

        assertFalse(result[0].isJoinedByMe)
    }

    // --- createCar ---

    @Test
    fun `createCar throws EntityNotFoundException when driver not found`() {
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            carpoolService.createCar(CreateCarRequest(
                driverMemberId = UUID.randomUUID().toString(),
                sessionDate = sessionDate,
                name = null,
                maxSeats = 4,
                departureLocation = null,
                departureTime = null,
            ))
        }
    }

    @Test
    fun `createCar saves car and returns its id`() {
        val driver = member(1L)
        val savedCar = car(99L, driver)
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(driver))
        `when`(carRepository.save(any(Car::class.java))).thenReturn(savedCar)

        val id = carpoolService.createCar(CreateCarRequest(
            driverMemberId = driver.publicId.toString(),
            sessionDate = sessionDate,
            name = "테스트 차",
            maxSeats = 4,
            departureLocation = null,
            departureTime = null,
        ))

        assertEquals(99L, id)
    }

    // --- joinCar ---

    @Test
    fun `joinCar throws EntityNotFoundException when car not found`() {
        `when`(carRepository.findById(anyLong())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            carpoolService.joinCar(99L, UUID.randomUUID().toString())
        }
    }

    @Test
    fun `joinCar throws EntityNotFoundException when member not found`() {
        val driver = member(1L)
        `when`(carRepository.findById(anyLong())).thenReturn(Optional.of(car(1L, driver)))
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            carpoolService.joinCar(1L, UUID.randomUUID().toString())
        }
    }

    @Test
    fun `joinCar throws IllegalStateException when car is full`() {
        val driver = member(1L)
        val passenger = member(2L)
        val fullCar = car(1L, driver, maxSeats = 2, currentPassengers = 2)
        `when`(carRepository.findById(1L)).thenReturn(Optional.of(fullCar))
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(passenger))

        val ex = assertThrows<IllegalStateException> { carpoolService.joinCar(1L, passenger.publicId.toString()) }
        assertEquals("Car is full", ex.message)
    }

    @Test
    fun `joinCar throws IllegalStateException when member already in another car on that date`() {
        val driver = member(1L)
        val passenger = member(2L)
        val c = car(1L, driver, maxSeats = 4)
        `when`(carRepository.findById(1L)).thenReturn(Optional.of(c))
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(passenger))
        `when`(passengerRepository.isMemberAlreadyDriving(2L, sessionDate)).thenReturn(true)

        val ex = assertThrows<IllegalStateException> { carpoolService.joinCar(1L, passenger.publicId.toString()) }
        assertEquals("You are already in a car for this date", ex.message)
    }

    @Test
    fun `joinCar saves passenger entry and increments currentPassengers`() {
        val driver = member(1L)
        val passenger = member(2L)
        val c = car(1L, driver, maxSeats = 4, currentPassengers = 1)
        `when`(carRepository.findById(1L)).thenReturn(Optional.of(c))
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(passenger))
        `when`(passengerRepository.isMemberAlreadyDriving(2L, sessionDate)).thenReturn(false)
        `when`(passengerRepository.save(any(CarPassenger::class.java))).thenAnswer { it.arguments[0] }
        `when`(carRepository.save(any(Car::class.java))).thenAnswer { it.arguments[0] }

        carpoolService.joinCar(1L, passenger.publicId.toString())

        assertEquals(2, c.currentPassengers)
        verify(passengerRepository).save(any(CarPassenger::class.java))
    }

    // --- leaveCar ---

    @Test
    fun `leaveCar throws EntityNotFoundException when member not found`() {
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            carpoolService.leaveCar(1L, UUID.randomUUID().toString())
        }
    }

    @Test
    fun `leaveCar throws EntityNotFoundException when passenger entry not found`() {
        val m = member(2L)
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(m))
        `when`(passengerRepository.findByCarIdAndMemberId(1L, 2L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            carpoolService.leaveCar(1L, m.publicId.toString())
        }
    }

    @Test
    fun `leaveCar deletes passenger entry and decrements currentPassengers`() {
        val driver = member(1L)
        val passenger = member(2L)
        val c = car(1L, driver, maxSeats = 4, currentPassengers = 2)
        val entry = CarPassenger(car = c, member = passenger)
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(passenger))
        `when`(passengerRepository.findByCarIdAndMemberId(1L, 2L)).thenReturn(Optional.of(entry))
        `when`(carRepository.save(any(Car::class.java))).thenAnswer { it.arguments[0] }

        carpoolService.leaveCar(1L, passenger.publicId.toString())

        assertEquals(1, c.currentPassengers)
        verify(passengerRepository).delete(entry)
    }
}
