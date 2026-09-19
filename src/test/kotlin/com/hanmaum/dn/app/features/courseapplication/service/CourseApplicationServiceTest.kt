package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.courseapplication.client.CourseApplicationApiClient
import com.hanmaum.dn.app.features.courseapplication.client.CourseApplicationApiRejectedException
import com.hanmaum.dn.app.features.courseapplication.client.CourseApplicationApiUnavailableException
import com.hanmaum.dn.app.features.courseapplication.client.ExternalApplication
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCourse
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCreateApplicationRequest
import com.hanmaum.dn.app.features.courseapplication.client.ExternalCreatedApplication
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttempt
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttemptStatus
import com.hanmaum.dn.app.features.courseapplication.repository.CourseApplicationAttemptRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingApplicationRequest
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.TrainingVariant
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class CourseApplicationServiceTest {
    @Mock private lateinit var trainingRepo: TrainingRepository

    @Mock private lateinit var userTrainingRepo: UserTrainingRepository

    @Mock private lateinit var attemptRepo: CourseApplicationAttemptRepository

    @Mock private lateinit var memberRepo: MemberRepository

    /** Runs every callback directly: these tests are about the steps, not transaction boundaries. */
    private val transactionManager =
        object : PlatformTransactionManager {
            override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

            override fun commit(status: TransactionStatus) = Unit

            override fun rollback(status: TransactionStatus) = Unit
        }

    private val client = FakeClient()

    private lateinit var service: CourseApplicationService

    private val zone = ZoneId.of("Europe/Berlin")

    // 2026-09-14 12:00 in Berlin.
    private val now = Instant.parse("2026-09-14T10:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val today = LocalDate.of(2026, 9, 14)

    private val oneOnOne = course(3, "일대일 제자양육", "0000-00-00 00:00:00", "2099-03-12 23:59:59")
    private val qtWomen = course(105, "큐베세 여자반", "2026-09-01 00:00:00", "2026-09-30 23:59:59")
    private val qtYouth = course(106, "큐베세 직장인/청년 반", "2026-09-01 00:00:00", "2026-09-20 23:59:59")
    private val qtYouthPast = course(98, "큐베세 직장인/청년 반", "2025-09-21 07:00:00", "2025-10-12 23:59:59")
    private val motherwise = course(95, "마더와이즈 \"회복\" 13기", "2026-09-01 00:00:00", "2026-09-30 23:59:59")

    @BeforeEach
    fun setUp() {
        service =
            CourseApplicationService(
                client = client,
                trainingRepository = trainingRepo,
                userTrainingRepository = userTrainingRepo,
                attemptRepository = attemptRepo,
                currentMemberResolver = CurrentMemberResolver(memberRepo, org.mockito.kotlin.mock()),
                transactionManager = transactionManager,
                clock = clock,
            )
        client.courses = listOf(oneOnOne, qtWomen, qtYouth, qtYouthPast, motherwise)
    }

    // ─── listTrainings ────────────────────────────────────────────────────────

    @Test
    fun `the list holds trainings with external courses only, open ones first`() {
        givenMember(member(gender = Gender.M))
        val qtBasic = training(1L, TrainingCode.QT_BASIC_SEMINAR, "큐티베이직세미나", 20, "큐베세")
        val one = training(2L, TrainingCode.ONE_ON_ONE, "일대일제자양육", 40)
        val ministry = training(3L, TrainingCode.MINISTRY_CLASS, "사역반", 70)
        `when`(trainingRepo.findActiveWithAliases()).thenReturn(listOf(qtBasic, one, ministry))
        client.courses = listOf(oneOnOne, qtWomen, qtYouthPast, motherwise)

        val result = service.listTrainings("kc-001")

        // 사역반 has no external course; 큐베세 is only open as 여자반, which a man cannot take.
        assertEquals(listOf(one.publicId.toString(), qtBasic.publicId.toString()), result.map { it.publicId })
        assertTrue(result[0].openForRegistration)
        assertTrue(result[0].isAlwaysOpen)
        assertEquals(false, result[1].openForRegistration)
        assertEquals(false, result[1].isAlwaysOpen)
    }

    @Test
    fun `a woman sees 큐베세 open through the 여자반 and its window`() {
        givenMember(member(gender = Gender.F))
        val qtBasic = training(1L, TrainingCode.QT_BASIC_SEMINAR, "큐티베이직세미나", 20, "큐베세")
        `when`(trainingRepo.findActiveWithAliases()).thenReturn(listOf(qtBasic))
        client.courses = listOf(qtWomen, qtYouthPast)

        val result = service.listTrainings("kc-001").single()

        assertTrue(result.openForRegistration)
        assertEquals(LocalDate.of(2026, 9, 30), result.registrationEndsAt?.toLocalDate())
    }

    @Test
    fun `the list is a 503 with a code when the application API is down`() {
        givenMember(member())
        client.unavailable = true

        val e = assertThrows<CourseApplicationException> { service.listTrainings("kc-001") }

        assertEquals(503, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_UNAVAILABLE, e.code)
        assertEquals("준비중입니다.", e.message)
    }

    // ─── getTrainingDetail ────────────────────────────────────────────────────

    @Test
    fun `the detail offers the open courses, flags the ineligible one and prefills from the profile`() {
        val member = member(gender = Gender.M)
        givenMember(member)
        val qtBasic = training(1L, TrainingCode.QT_BASIC_SEMINAR, "큐티베이직세미나", 20, "큐베세")
        `when`(trainingRepo.findWithAliasesByPublicId(qtBasic.publicId)).thenReturn(qtBasic)

        val detail = service.getTrainingDetail(qtBasic.publicId, "kc-001")

        assertEquals(listOf(105, 106), detail.courses.map { it.externalCourseId })
        assertEquals(listOf(false, true), detail.courses.map { it.isEligible })
        assertTrue(detail.openForRegistration)
        assertEquals("김철수", detail.applicantPrefill?.name)
        assertEquals("M", detail.applicantPrefill?.gender)
        assertTrue(
            detail.courses
                .single { it.externalCourseId == 106 }
                .formFields
                .any { it.name == "phone" },
        )
    }

    @Test
    fun `the prefill lists 양육 by status in the form's wording`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val overview = training(4L, TrainingCode.BIBLE_OVERVIEW, "성경개관", 60)
        val one = training(2L, TrainingCode.ONE_ON_ONE, "일대일제자양육", 40)
        val ministry =
            Training(code = TrainingCode.MINISTRY_CLASS, name = "Ministry Class", sortOrder = 70, nameKo = null)
                .also { setId(it, 3L) }
        val kairos = training(5L, TrainingCode.KAIROS, "카이로스", 80)
        val panorama = training(6L, TrainingCode.BIBLE_PANORAMA, "성경파노라마", 50)
        `when`(userTrainingRepo.findByMemberId(1L)).thenReturn(
            listOf(
                UserTraining(
                    member = member,
                    training = overview,
                    status = TrainingStatus.COMPLETED,
                    variant = TrainingVariant.OLD_TESTAMENT,
                ),
                UserTraining(
                    member = member,
                    training = overview,
                    status = TrainingStatus.COMPLETED,
                    variant = TrainingVariant.NEW_TESTAMENT,
                    completedAt = LocalDate.of(2019, 3, 1),
                ),
                UserTraining(
                    member = member,
                    training = qtBasic,
                    status = TrainingStatus.COMPLETED,
                    completedAt = LocalDate.of(2017, 5, 1),
                ),
                UserTraining(member = member, training = one, status = TrainingStatus.APPLIED),
                UserTraining(member = member, training = ministry, status = TrainingStatus.ENROLLED),
                UserTraining(member = member, training = kairos, status = TrainingStatus.IN_PROGRESS),
                UserTraining(member = member, training = panorama, status = TrainingStatus.DROPPED),
                UserTraining(member = member, training = panorama, status = TrainingStatus.UNKNOWN),
            ),
        )

        val prefill = requireNotNull(service.getTrainingDetail(qtBasic.publicId, "kc-001").applicantPrefill)

        // By completion date, the undated one last.
        assertEquals("큐티베이직세미나 / 2017년 5월\n성경개관 신약 / 2019년 3월\n성경개관 구약", prefill.history)
        // No Korean name recorded: the catalog name stands in.
        assertEquals("일대일제자양육\nMinistry Class", prefill.waiting)
        assertEquals("카이로스", prefill.running)
    }

    @Test
    fun `without matching 양육 or baptism the prefill fields are null, not empty`() {
        givenMember(member())
        val qtBasic = givenTraining()

        val prefill = requireNotNull(service.getTrainingDetail(qtBasic.publicId, "kc-001").applicantPrefill)

        assertNull(prefill.history)
        assertNull(prefill.waiting)
        assertNull(prefill.running)
        assertNull(prefill.baptized)
        assertNull(prefill.baptizeType)
    }

    @Test
    fun `the baptism is prefilled as the form's 세례 여부 and 세례 구분 codes`() {
        val qtBasic = givenTraining()
        val expected =
            mapOf(
                Baptism.INFANT_BAPTIZED to ("1" to "1"),
                Baptism.CONFIRMATION to ("2" to "3"),
                Baptism.GENERAL_BAPTIZED to ("3" to "4"),
                Baptism.UNBAPTIZED to ("4" to "5"),
            )

        expected.forEach { (baptism, codes) ->
            givenMember(member().apply { this.baptism = baptism })

            val prefill = requireNotNull(service.getTrainingDetail(qtBasic.publicId, "kc-001").applicantPrefill)

            assertEquals(codes, prefill.baptized to prefill.baptizeType, "for $baptism")
        }
    }

    // ─── apply ────────────────────────────────────────────────────────────────

    @Test
    fun `an application is sent with the profile data and recorded as created and applied`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        givenInsertedAttempt()
        `when`(userTrainingRepo.save(any<UserTraining>())).thenAnswer { it.arguments[0] }

        val result = service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(externalCourseId = 106, history = "없음"))

        val sent = client.created.single()
        assertEquals(member.publicId.toString(), sent.clientUserId)
        assertEquals(106, sent.courseId)
        assertEquals("김철수", sent.aName)
        assertEquals("1995-05-01", sent.aBirthdate)
        assertEquals("chulsoo@example.com", sent.aEmail)
        assertEquals("+49 170 1234567", sent.aHandy)
        assertEquals("M", sent.aGender)
        assertEquals("Frankfurt", sent.aResidence)
        assertEquals("4", sent.aGroup)
        assertEquals("없음", sent.aHistory)

        val attempt = savedAttempt()
        assertEquals(attempt.clientApplicationId, sent.clientApplicationId)
        assertEquals(CourseApplicationAttemptStatus.CREATED, attempt.status)
        assertEquals(123L, attempt.externalApplicationId)

        val participation = argumentCaptor<UserTraining>()
        verify(userTrainingRepo).save(participation.capture())
        assertEquals(TrainingStatus.APPLIED, participation.firstValue.status)
        assertEquals(today, participation.firstValue.appliedOn)

        assertEquals("APPLIED", result.status)
        assertEquals(today, result.appliedOn)
        assertEquals("큐베세 직장인/청년 반", result.courseName)
    }

    @Test
    fun `values in the request win over the profile`() {
        givenMember(member())
        val qtBasic = givenTraining()
        givenInsertedAttempt()
        `when`(userTrainingRepo.save(any<UserTraining>())).thenAnswer { it.arguments[0] }

        service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(externalCourseId = 106, phone = " +49 151 000 ", name = ""))

        val sent = client.created.single()
        assertEquals("+49 151 000", sent.aHandy)
        // A blank value is no value: the profile fills it.
        assertEquals("김철수", sent.aName)
    }

    @Test
    fun `a retry after the application was recorded answers without calling out again`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val created =
            attempt(member, qtBasic, 106).apply {
                setId(this, 7L)
                markCreated(55L)
            }
        `when`(attemptRepo.findLive(1L, 106)).thenReturn(created)
        `when`(attemptRepo.findById(7L)).thenReturn(Optional.of(created))
        `when`(userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(1L, 1L, null)).thenReturn(
            Optional.of(UserTraining(member = member, training = qtBasic, status = TrainingStatus.APPLIED, appliedOn = today)),
        )

        val result = service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(externalCourseId = 106))

        assertTrue(client.created.isEmpty())
        assertEquals("APPLIED", result.status)
    }

    @Test
    fun `a pending attempt the external API already has is recorded without sending it again`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val pending = attempt(member, qtBasic, 106).also { setId(it, 7L) }
        `when`(attemptRepo.findLive(1L, 106)).thenReturn(pending)
        `when`(attemptRepo.findById(7L)).thenReturn(Optional.of(pending))
        `when`(userTrainingRepo.save(any<UserTraining>())).thenAnswer { it.arguments[0] }
        client.byClientId = ExternalApplication(id = 88L, courseId = 106, status = "active")

        service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(externalCourseId = 106))

        assertTrue(client.created.isEmpty())
        assertEquals(88L, pending.externalApplicationId)
        assertEquals(CourseApplicationAttemptStatus.CREATED, pending.status)
    }

    @Test
    fun `an idempotency conflict means the application exists and it is recorded`() {
        givenMember(member())
        val qtBasic = givenTraining()
        givenInsertedAttempt()
        `when`(userTrainingRepo.save(any<UserTraining>())).thenAnswer { it.arguments[0] }
        client.rejectCreateWith = CourseApplicationApiRejectedException(409, "idempotency_conflict", "x")
        client.byClientId = ExternalApplication(id = 77L, courseId = 106, status = "active")

        service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(externalCourseId = 106))

        assertEquals(77L, savedAttempt().externalApplicationId)
    }

    @Test
    fun `a full course is a 409 and the attempt stays pending for a later try`() {
        givenMember(member())
        val qtBasic = givenTraining()
        givenInsertedAttempt(finished = false)
        client.rejectCreateWith = CourseApplicationApiRejectedException(409, "capacity_full", "정원이 마감되었습니다.")

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106)) }

        assertEquals(409, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_FULL, e.code)
        assertEquals(CourseApplicationAttemptStatus.PENDING, savedAttempt().status)
        verify(userTrainingRepo, never()).save(any<UserTraining>())
    }

    @Test
    fun `external field errors are renamed to request fields`() {
        givenMember(member())
        val qtBasic = givenTraining()
        givenInsertedAttempt(finished = false)
        client.rejectCreateWith =
            CourseApplicationApiRejectedException(422, "validation_failed", "x", mapOf("aEmail" to "올바른 이메일 주소여야 합니다."))

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106)) }

        assertEquals(422, e.status.value())
        assertEquals(mapOf("email" to "올바른 이메일 주소여야 합니다."), e.fieldErrors)
    }

    @Test
    fun `an outage while sending is a 503`() {
        givenMember(member())
        val qtBasic = givenTraining()
        givenInsertedAttempt(finished = false)
        client.unavailableOnCreate = true

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106)) }

        assertEquals(ApiErrorCode.COURSE_APPLICATION_UNAVAILABLE, e.code)
    }

    @Test
    fun `a man cannot apply to a 여자반`() {
        givenMember(member(gender = Gender.M))
        val qtBasic = givenTraining()

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(105)) }

        assertEquals(403, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_NOT_ELIGIBLE, e.code)
        assertTrue(client.created.isEmpty())
        verify(attemptRepo, never()).saveAndFlush(any<CourseApplicationAttempt>())
    }

    @Test
    fun `a closed course is a 409`() {
        givenMember(member())
        val qtBasic = givenTraining()

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(98)) }

        assertEquals(ApiErrorCode.COURSE_APPLICATION_CLOSED, e.code)
        assertTrue(client.created.isEmpty())
    }

    @Test
    fun `a course of another training is a 404`() {
        givenMember(member())
        val qtBasic = givenTraining()

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(95)) }

        assertEquals(404, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_COURSE_NOT_FOUND, e.code)
    }

    @Test
    fun `a member already taking the training cannot apply again`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        `when`(userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(1L, 1L, null)).thenReturn(
            Optional.of(UserTraining(member = member, training = qtBasic, status = TrainingStatus.IN_PROGRESS)),
        )

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106)) }

        assertEquals(ApiErrorCode.COURSE_APPLICATION_ALREADY_APPLIED, e.code)
        assertTrue(client.created.isEmpty())
    }

    @Test
    fun `missing contact data in both request and profile is a 400 naming the fields`() {
        givenMember(member(email = null, phone = null))
        val qtBasic = givenTraining()

        val e = assertThrows<CourseApplicationException> { service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106)) }

        assertEquals(400, e.status.value())
        assertEquals(setOf("email", "phone"), e.fieldErrors?.keys)
        verify(attemptRepo, never()).saveAndFlush(any<CourseApplicationAttempt>())
    }

    @Test
    fun `a dropped participation is reopened for the new application`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        givenInsertedAttempt()
        val dropped =
            UserTraining(
                member = member,
                training = qtBasic,
                status = TrainingStatus.DROPPED,
                appliedOn = LocalDate.of(2024, 3, 1),
                startedOn = LocalDate.of(2024, 3, 10),
            )
        `when`(userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(1L, 1L, null)).thenReturn(Optional.of(dropped))

        service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106))

        assertEquals(TrainingStatus.APPLIED, dropped.status)
        assertEquals(today, dropped.appliedOn)
        assertNull(dropped.startedOn)
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    fun `a cancellation goes out first, then drops the participation here`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val created = givenLatestAttempt(member, qtBasic) { markCreated(55L) }
        val participation = givenParticipation(member, qtBasic, TrainingStatus.APPLIED)

        val result = service.cancel(qtBasic.publicId, "kc-001")

        assertEquals(listOf(55L), client.cancelled)
        assertEquals(CourseApplicationAttemptStatus.CANCELLED, created.status)
        assertEquals(TrainingStatus.DROPPED, participation.status)
        assertEquals("DROPPED", result.status)
        assertEquals(106, result.externalCourseId)
    }

    @Test
    fun `a pending attempt is resolved by its client id before it is cancelled`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val pending = givenLatestAttempt(member, qtBasic)
        givenParticipation(member, qtBasic, TrainingStatus.APPLIED)
        client.byClientId = ExternalApplication(id = 88L, courseId = 106, status = "active")

        service.cancel(qtBasic.publicId, "kc-001")

        assertEquals(listOf(88L), client.cancelled)
        assertEquals(88L, pending.externalApplicationId)
        assertEquals(CourseApplicationAttemptStatus.CANCELLED, pending.status)
    }

    @Test
    fun `a pending attempt the external API never received is a 404 without cancelling anything`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val pending = givenLatestAttempt(member, qtBasic, stubLookup = false)

        val e = assertThrows<CourseApplicationException> { service.cancel(qtBasic.publicId, "kc-001") }

        assertEquals(404, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_NOT_FOUND, e.code)
        assertTrue(client.cancelled.isEmpty())
        assertEquals(CourseApplicationAttemptStatus.PENDING, pending.status)
    }

    @Test
    fun `a member without an application to the training gets a 404`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val otherTraining = training(2L, TrainingCode.ONE_ON_ONE, "일대일제자양육", 40)
        `when`(attemptRepo.findByMember(1L, CourseApplicationAttemptStatus.entries.toSet())).thenReturn(
            listOf(attempt(member, otherTraining, 3).apply { markCreated(9L) }),
        )

        val e = assertThrows<CourseApplicationException> { service.cancel(qtBasic.publicId, "kc-001") }

        assertEquals(ApiErrorCode.COURSE_APPLICATION_NOT_FOUND, e.code)
        assertTrue(client.cancelled.isEmpty())
    }

    @Test
    fun `a repeated cancellation answers 200 again without calling out`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        givenLatestAttempt(member, qtBasic, stubLookup = false) {
            markCreated(55L)
            markCancelled(55L)
        }

        val result = service.cancel(qtBasic.publicId, "kc-001")

        assertTrue(client.cancelled.isEmpty())
        assertEquals("DROPPED", result.status)
    }

    @Test
    fun `an outage while cancelling is a 503 and nothing changes here`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val created = givenLatestAttempt(member, qtBasic, stubLookup = false) { markCreated(55L) }
        val participation = givenParticipation(member, qtBasic, TrainingStatus.APPLIED)
        client.unavailableOnCancel = true

        val e = assertThrows<CourseApplicationException> { service.cancel(qtBasic.publicId, "kc-001") }

        assertEquals(503, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_UNAVAILABLE, e.code)
        assertEquals(CourseApplicationAttemptStatus.CREATED, created.status)
        assertEquals(TrainingStatus.APPLIED, participation.status)
    }

    @Test
    fun `a completed training cannot be cancelled`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        givenLatestAttempt(member, qtBasic, stubLookup = false) { markCreated(55L) }
        givenParticipation(member, qtBasic, TrainingStatus.COMPLETED)

        val e = assertThrows<CourseApplicationException> { service.cancel(qtBasic.publicId, "kc-001") }

        assertEquals(409, e.status.value())
        assertEquals(ApiErrorCode.COURSE_APPLICATION_NOT_CANCELLABLE, e.code)
        assertTrue(client.cancelled.isEmpty())
    }

    @Test
    fun `an older completion is history and stays completed when the new application is cancelled`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        givenLatestAttempt(member, qtBasic) { markCreated(55L) }
        val history =
            givenParticipation(member, qtBasic, TrainingStatus.COMPLETED).apply {
                appliedOn = LocalDate.of(2019, 3, 1)
            }

        service.cancel(qtBasic.publicId, "kc-001")

        assertEquals(listOf(55L), client.cancelled)
        assertEquals(TrainingStatus.COMPLETED, history.status)
    }

    @Test
    fun `applying again after a cancellation creates a new external application, not a replay`() {
        val member = member()
        givenMember(member)
        val qtBasic = givenTraining()
        val attempts = mutableListOf<CourseApplicationAttempt>()
        `when`(attemptRepo.saveAndFlush(any<CourseApplicationAttempt>())).thenAnswer { invocation ->
            (invocation.arguments[0] as CourseApplicationAttempt).also {
                setId(it, attempts.size + 7L)
                it.createdAt = now
                attempts.add(0, it)
            }
        }
        `when`(attemptRepo.findById(any())).thenAnswer { invocation ->
            Optional.ofNullable(attempts.firstOrNull { it.id == invocation.arguments[0] })
        }
        // Keyed like uq_course_application_attempt_member_course: a cancelled attempt is not live.
        `when`(attemptRepo.findLive(1L, 106)).thenAnswer { attempts.firstOrNull { it.status != CourseApplicationAttemptStatus.CANCELLED } }
        `when`(attemptRepo.findByMember(1L, CourseApplicationAttemptStatus.entries.toSet())).thenAnswer { attempts.toList() }
        var participation: UserTraining? = null
        `when`(userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(1L, 1L, null)).thenAnswer {
            Optional.ofNullable(participation)
        }
        `when`(userTrainingRepo.save(any<UserTraining>())).thenAnswer { invocation ->
            (invocation.arguments[0] as UserTraining).also { participation = it }
        }

        service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106))
        service.cancel(qtBasic.publicId, "kc-001")
        val again = service.apply(qtBasic.publicId, "kc-001", TrainingApplicationRequest(106))

        assertEquals(2, client.created.size)
        assertTrue(client.created[0].clientApplicationId != client.created[1].clientApplicationId)
        assertEquals(listOf(CourseApplicationAttemptStatus.CREATED, CourseApplicationAttemptStatus.CANCELLED), attempts.map { it.status })
        assertEquals(TrainingStatus.APPLIED, participation?.status)
        assertEquals("APPLIED", again.status)
    }

    // ─── myTrainingApplications ───────────────────────────────────────────────

    @Test
    fun `나의 신청 shows the participation status only when it belongs to the application`() {
        val member = member()
        givenMember(member)
        val qtBasic = training(1L, TrainingCode.QT_BASIC_SEMINAR, "큐티베이직세미나", 20)
        val one = training(2L, TrainingCode.ONE_ON_ONE, "일대일제자양육", 40)
        val qtAttempt = attempt(member, qtBasic, 106).apply { markCreated(1L) }
        val oneAttempt = attempt(member, one, 3).apply { markCreated(2L) }
        `when`(attemptRepo.findByMember(1L, setOf(CourseApplicationAttemptStatus.CREATED))).thenReturn(listOf(qtAttempt, oneAttempt))
        `when`(userTrainingRepo.findByMemberId(1L)).thenReturn(
            listOf(
                // Completed years before this application: says nothing about it.
                UserTraining(member = member, training = qtBasic, status = TrainingStatus.COMPLETED, appliedOn = LocalDate.of(2019, 3, 1)),
                UserTraining(member = member, training = one, status = TrainingStatus.ENROLLED, appliedOn = today),
            ),
        )

        val result = service.myTrainingApplications("kc-001")

        assertEquals(listOf("APPLIED", "ENROLLED"), result.map { it.status })
        assertEquals(listOf("큐베세 직장인/청년 반", "일대일 제자양육"), result.map { it.courseName })
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private class FakeClient : CourseApplicationApiClient {
        var courses: List<ExternalCourse> = emptyList()
        var unavailable = false
        var unavailableOnCreate = false
        var rejectCreateWith: CourseApplicationApiRejectedException? = null
        var byClientId: ExternalApplication? = null
        var unavailableOnCancel = false
        val created = mutableListOf<ExternalCreateApplicationRequest>()
        val cancelled = mutableListOf<Long>()

        override fun listCourses(): List<ExternalCourse> {
            if (unavailable) throw CourseApplicationApiUnavailableException("down")
            return courses
        }

        override fun createApplication(request: ExternalCreateApplicationRequest): ExternalCreatedApplication {
            if (unavailableOnCreate) throw CourseApplicationApiUnavailableException("down")
            rejectCreateWith?.let { throw it }
            created += request
            return ExternalCreatedApplication(ExternalApplication(id = 123L, courseId = request.courseId, status = "active"), false)
        }

        override fun findApplicationByClientId(clientApplicationId: UUID): ExternalApplication? = byClientId

        override fun cancelApplication(externalApplicationId: Long): ExternalApplication {
            if (unavailableOnCancel) throw CourseApplicationApiUnavailableException("down")
            cancelled += externalApplicationId
            return ExternalApplication(id = externalApplicationId, courseId = 106, status = "cancelled")
        }
    }

    private fun course(
        id: Int,
        name: String,
        startsAt: String,
        endsAt: String,
    ) = ExternalCourse(id = id, name = name, registrationStartsAt = startsAt, registrationEndsAt = endsAt)

    private fun member(
        gender: Gender? = Gender.M,
        email: String? = "chulsoo@example.com",
        phone: String? = "+49 170 1234567",
    ) = Member(
        lastName = "김",
        firstName = "철수",
        gender = gender,
        birthDate = LocalDate.of(1995, 5, 1),
        phoneNumber = phone,
        email = email,
        city = "Frankfurt",
    ).also { setId(it, 1L) }

    private fun training(
        id: Long,
        code: TrainingCode,
        nameKo: String,
        sortOrder: Int,
        vararg aliases: String,
    ) = Training(code = code, name = code.name, sortOrder = sortOrder, nameKo = nameKo).also {
        setId(it, id)
        it.aliases.addAll(aliases)
    }

    private fun attempt(
        member: Member,
        training: Training,
        courseId: Int,
    ) = CourseApplicationAttempt(
        member = member,
        training = training,
        externalCourseId = courseId,
        externalCourseName = client.courses.single { it.id == courseId }.name,
    ).also { it.createdAt = now }

    private fun givenMember(member: Member) {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
    }

    private fun givenTraining(): Training {
        val qtBasic = training(1L, TrainingCode.QT_BASIC_SEMINAR, "큐티베이직세미나", 20, "큐베세")
        `when`(trainingRepo.findWithAliasesByPublicId(qtBasic.publicId)).thenReturn(qtBasic)
        return qtBasic
    }

    /** A fresh PENDING insert with id 7; [finished] also stubs the lookup the finishing step does. */
    private fun givenInsertedAttempt(finished: Boolean = true) {
        `when`(attemptRepo.saveAndFlush(any<CourseApplicationAttempt>())).thenAnswer { invocation ->
            (invocation.arguments[0] as CourseApplicationAttempt).also {
                setId(it, 7L)
                it.createdAt = now
                if (finished) `when`(attemptRepo.findById(7L)).thenReturn(Optional.of(it))
            }
        }
    }

    /**
     * The member's latest attempt at 큐베세, course 106, id 7. [stubLookup] also stubs the
     * lookup the recording step does, which a test that stops before it must leave out.
     */
    private fun givenLatestAttempt(
        member: Member,
        training: Training,
        stubLookup: Boolean = true,
        state: CourseApplicationAttempt.() -> Unit = {},
    ): CourseApplicationAttempt {
        val attempt = attempt(member, training, 106).also { setId(it, 7L) }.apply(state)
        `when`(attemptRepo.findByMember(1L, CourseApplicationAttemptStatus.entries.toSet())).thenReturn(listOf(attempt))
        if (stubLookup) `when`(attemptRepo.findById(7L)).thenReturn(Optional.of(attempt))
        return attempt
    }

    private fun givenParticipation(
        member: Member,
        training: Training,
        status: TrainingStatus,
    ): UserTraining {
        val row = UserTraining(member = member, training = training, status = status, appliedOn = today)
        `when`(userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(1L, 1L, null)).thenReturn(Optional.of(row))
        return row
    }

    private fun savedAttempt(): CourseApplicationAttempt {
        val captor = argumentCaptor<CourseApplicationAttempt>()
        verify(attemptRepo).saveAndFlush(captor.capture())
        return captor.firstValue
    }

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        var type: Class<*>? = entity.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == "id" }
            if (field != null) {
                field.isAccessible = true
                field.set(entity, id)
                return
            }
            type = type.superclass
        }
        error("No id field on ${entity.javaClass}")
    }
}
