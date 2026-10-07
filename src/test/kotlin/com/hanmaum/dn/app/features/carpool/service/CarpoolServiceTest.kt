package com.hanmaum.dn.app.features.carpool.service

import com.hanmaum.dn.app.features.carpool.api.v1.dto.CreateCarRequest
import com.hanmaum.dn.app.features.carpool.domain.Car
import com.hanmaum.dn.app.features.carpool.domain.CarPassenger
import com.hanmaum.dn.app.features.carpool.repository.CarPassengerRepository
import com.hanmaum.dn.app.features.carpool.repository.CarRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CarpoolServiceTest {
    @Mock private lateinit var carRepository: CarRepository

    @Mock private lateinit var passengerRepository: CarPassengerRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var currentMember: CurrentMemberResolver

    @InjectMocks
    private lateinit var carpoolService: CarpoolService

    private val subject = "kc-001"
    private val sessionDate = LocalDate.of(2026, 3, 29)

    private fun member(
        id: Long,
        firstName: String = "길동",
        lastName: String = "홍",
    ): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        return m
    }

    private fun car(
        id: Long,
        driver: Member,
        maxSeats: Int = 4,
        currentPassengers: Int = 0,
    ): Car {
        val c =
            Car(
                driver = driver,
                sessionDate = sessionDate,
                name = "테스트 차",
                maxSeats = maxSeats,
                currentPassengers = currentPassengers,
            )
        c.id = id
        return c
    }

    private fun request(driverMemberId: UUID? = null) =
        CreateCarRequest(
            driverMemberId = driverMemberId,
            sessionDate = sessionDate,
            name = "테스트 차",
            maxSeats = 4,
            departureLocation = null,
            departureTime = null,
        )

    // --- getCarsForDate ---

    @Test
    fun `getCarsForDate returns empty list when no cars`() {
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(carRepository.findAllBySessionDate(any<LocalDate>())).thenReturn(emptyList())

        assertTrue(carpoolService.getCarsForDate(sessionDate, subject).isEmpty())
    }

    @Test
    fun `getCarsForDate maps car fields to dto`() {
        val driver = member(1L, "길동", "홍")
        val c = car(10L, driver, maxSeats = 4, currentPassengers = 2)
        c.departureLocation = "강남역"
        c.departureTime = LocalTime.of(10, 0)
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(carRepository.findAllBySessionDate(any<LocalDate>())).thenReturn(listOf(c))
        `when`(passengerRepository.findByCarIdAndMemberId(10L, 2L)).thenReturn(Optional.empty())

        val result = carpoolService.getCarsForDate(sessionDate, subject)

        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals(c.publicId, publicId)
            assertEquals("길동 홍", driverName)
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
        val c = car(10L, member(1L), maxSeats = 3, currentPassengers = 3)
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(carRepository.findAllBySessionDate(any<LocalDate>())).thenReturn(listOf(c))
        `when`(passengerRepository.findByCarIdAndMemberId(10L, 2L)).thenReturn(Optional.empty())

        assertTrue(carpoolService.getCarsForDate(sessionDate, subject)[0].isFull)
    }

    @Test
    fun `getCarsForDate marks isJoinedByMe for the caller from the token`() {
        val caller = member(2L)
        val c = car(10L, member(1L))
        `when`(currentMember.require(subject)).thenReturn(caller)
        `when`(carRepository.findAllBySessionDate(any<LocalDate>())).thenReturn(listOf(c))
        `when`(passengerRepository.findByCarIdAndMemberId(10L, 2L))
            .thenReturn(Optional.of(CarPassenger(car = c, member = caller)))

        assertTrue(carpoolService.getCarsForDate(sessionDate, subject)[0].isJoinedByMe)
    }

    // --- createCar ---

    @Test
    fun `createCar makes the caller the driver when none is named`() {
        val caller = member(2L)
        `when`(currentMember.require(subject)).thenReturn(caller)
        `when`(carRepository.save(any<Car>())).thenAnswer { it.arguments[0] }

        carpoolService.createCar(request(), subject, isAdmin = false)

        val captor = argumentCaptor<Car>()
        verify(carRepository).save(captor.capture())
        assertSame(caller, captor.firstValue.driver)
    }

    @Test
    fun `createCar forbids a member to name another driver`() {
        `when`(currentMember.require(subject)).thenReturn(member(2L))

        val ex =
            assertThrows<ResponseStatusException> {
                carpoolService.createCar(request(UUID.randomUUID()), subject, isAdmin = false)
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verify(carRepository, never()).save(any<Car>())
    }

    @Test
    fun `createCar lets an admin name another driver`() {
        val other = member(3L)
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(memberRepository.findByPublicId(other.publicId)).thenReturn(Optional.of(other))
        `when`(carRepository.save(any<Car>())).thenAnswer { it.arguments[0] }

        carpoolService.createCar(request(other.publicId), subject, isAdmin = true)

        val captor = argumentCaptor<Car>()
        verify(carRepository).save(captor.capture())
        assertSame(other, captor.firstValue.driver)
    }

    @Test
    fun `createCar throws EntityNotFoundException when the admin names an unknown driver`() {
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(memberRepository.findByPublicId(any<UUID>())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            carpoolService.createCar(request(UUID.randomUUID()), subject, isAdmin = true)
        }
    }

    // --- joinCar ---

    @Test
    fun `joinCar throws EntityNotFoundException when car not found`() {
        `when`(carRepository.findByPublicId(any<UUID>())).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { carpoolService.joinCar(UUID.randomUUID(), subject) }
    }

    @Test
    fun `joinCar throws IllegalStateException when car is full`() {
        val fullCar = car(1L, member(1L), maxSeats = 2, currentPassengers = 2)
        `when`(carRepository.findByPublicId(fullCar.publicId)).thenReturn(Optional.of(fullCar))
        `when`(currentMember.require(subject)).thenReturn(member(2L))

        val ex = assertThrows<IllegalStateException> { carpoolService.joinCar(fullCar.publicId, subject) }
        assertEquals("Car is full", ex.message)
    }

    @Test
    fun `joinCar throws IllegalStateException when member already in another car on that date`() {
        val c = car(1L, member(1L))
        `when`(carRepository.findByPublicId(c.publicId)).thenReturn(Optional.of(c))
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(passengerRepository.isMemberAlreadyDriving(2L, sessionDate)).thenReturn(true)

        val ex = assertThrows<IllegalStateException> { carpoolService.joinCar(c.publicId, subject) }
        assertEquals("You are already in a car for this date", ex.message)
    }

    @Test
    fun `joinCar seats the caller from the token and increments currentPassengers`() {
        val caller = member(2L)
        val c = car(1L, member(1L), currentPassengers = 1)
        `when`(carRepository.findByPublicId(c.publicId)).thenReturn(Optional.of(c))
        `when`(currentMember.require(subject)).thenReturn(caller)
        `when`(passengerRepository.isMemberAlreadyDriving(2L, sessionDate)).thenReturn(false)
        `when`(passengerRepository.save(any<CarPassenger>())).thenAnswer { it.arguments[0] }
        `when`(carRepository.save(any<Car>())).thenAnswer { it.arguments[0] }

        carpoolService.joinCar(c.publicId, subject)

        assertEquals(2, c.currentPassengers)
        val captor = argumentCaptor<CarPassenger>()
        verify(passengerRepository).save(captor.capture())
        assertSame(caller, captor.firstValue.member)
    }

    // --- leaveCar ---

    @Test
    fun `leaveCar throws EntityNotFoundException when the caller is not in the car`() {
        val c = car(1L, member(1L))
        `when`(carRepository.findByPublicId(c.publicId)).thenReturn(Optional.of(c))
        `when`(currentMember.require(subject)).thenReturn(member(2L))
        `when`(passengerRepository.findByCarIdAndMemberId(1L, 2L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { carpoolService.leaveCar(c.publicId, subject) }
    }

    @Test
    fun `leaveCar removes the caller and decrements currentPassengers`() {
        val caller = member(2L)
        val c = car(1L, member(1L), currentPassengers = 2)
        val entry = CarPassenger(car = c, member = caller)
        `when`(carRepository.findByPublicId(c.publicId)).thenReturn(Optional.of(c))
        `when`(currentMember.require(subject)).thenReturn(caller)
        `when`(passengerRepository.findByCarIdAndMemberId(1L, 2L)).thenReturn(Optional.of(entry))
        `when`(carRepository.save(any<Car>())).thenAnswer { it.arguments[0] }

        carpoolService.leaveCar(c.publicId, subject)

        assertEquals(1, c.currentPassengers)
        verify(passengerRepository).delete(entry)
    }
}
