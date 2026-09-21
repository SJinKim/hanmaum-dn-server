package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerIdentityStatus
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerImportRecord
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerImportRecordRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

data class NewcomerImportReport(
    val read: Int,
    val imported: Int,
    val skipped: Int,
    val review: Int,
    val invalid: Int,
    val issues: List<NewcomerImportIssue>,
)

data class NewcomerImportIssue(
    val rowNumber: Int,
    val code: String,
)

@Service
class NewcomerImportService(
    private val reader: NewcomerImportReader,
    private val members: MemberRepository,
    private val profiles: NewcomerProfileRepository,
    private val records: NewcomerImportRecordRepository,
) {
    @Transactional
    fun import(
        path: Path,
        layout: NewcomerImportLayout,
        dryRun: Boolean,
        decisions: Map<Int, UUID>,
        caregivers: Map<String, UUID>,
    ): NewcomerImportReport {
        val sourceFingerprint = sha256(Files.readAllBytes(path))
        var imported = 0
        var skipped = 0
        var review = 0
        var invalid = 0
        val issues = mutableListOf<NewcomerImportIssue>()
        val rows = reader.read(path, layout)
        rows.forEach { row ->
            val parsed = parse(row)
            if (parsed.issues.isNotEmpty()) {
                invalid++
                issues += parsed.issues.map { NewcomerImportIssue(row.rowNumber, it) }
                return@forEach
            }
            val candidate = requireNotNull(parsed.candidate)
            if (records.existsBySourceFingerprintAndRowNumber(sourceFingerprint, row.rowNumber)) {
                skipped++
                return@forEach
            }
            val matches = candidateMatches(candidate)
            val caregiver = candidate.caregiver?.let { caregivers[it] }?.let { members.findByPublicIdAndDeletedAtIsNull(it).orElse(null) }
            if (candidate.caregiver != null && caregiver == null) {
                issues += NewcomerImportIssue(row.rowNumber, "UNRESOLVED_CAREGIVER")
                return@forEach
            }
            val selected = decisions[row.rowNumber]?.let { members.findByPublicIdAndDeletedAtIsNull(it).orElse(null) }
            if (decisions.containsKey(row.rowNumber) && selected == null) {
                issues += NewcomerImportIssue(row.rowNumber, "INVALID_MANUAL_DECISION")
                return@forEach
            }
            if (matches.isNotEmpty() && selected == null) {
                review++
                issues += NewcomerImportIssue(row.rowNumber, "MANUAL_RECONCILIATION_REQUIRED")
                return@forEach
            }
            if (dryRun) {
                imported++
                return@forEach
            }
            val member = selected ?: members.save(candidate.toMember())
            candidate.applyTo(member)
            val profile =
                profiles
                    .findByMemberIdAndDeletedAtIsNull(member.id!!)
                    ?.also { candidate.applyTo(it, caregiver) }
                    ?: profiles.save(
                        NewcomerProfile(
                            member = member,
                            intakeRound = candidate.intakeRound,
                            caregiver = caregiver,
                            identityStatus = candidate.identityStatus,
                            firstVisitDate = candidate.firstVisitDate,
                            postAssignmentAttendance = candidate.attendance,
                        ),
                    )
            records.save(NewcomerImportRecord(sourceFingerprint, row.rowNumber, candidate.fingerprint(), member, profile))
            imported++
        }
        return NewcomerImportReport(rows.size, imported, skipped, review, invalid, issues)
    }

    private fun candidateMatches(candidate: Candidate): List<Member> =
        buildList {
            candidate.email?.let { members.findByEmailAndDeletedAtIsNull(it)?.let(::add) }
            addAll(members.findSimilarNames(candidate.firstName, candidate.lastName))
        }.distinctBy { it.publicId }

    private fun parse(row: NewcomerImportRow): ParseResult {
        val lastName = row.fields["lastName"].orEmpty().trim()
        val firstName = row.fields["firstName"].orEmpty().trim()
        val issues = mutableListOf<String>()
        if (lastName.isBlank() || firstName.isBlank()) issues += "INVALID_REQUIRED_FIELD"
        val birthDate = parseOptional(row.fields["birthDate"], "INVALID_BIRTH_DATE", ::dateOrNull, issues)
        val firstVisitDate = parseOptional(row.fields["firstVisitDate"], "INVALID_FIRST_VISIT_DATE", ::dateOrNull, issues)
        val gender = parseOptional(row.fields["gender"], "INVALID_GENDER", ::normalizeGender, issues)
        val baptism = parseOptional(row.fields["baptism"], "INVALID_BAPTISM", ::normalizeBaptism, issues)
        val identityStatus = parseOptional(row.fields["identityStatus"], "INVALID_IDENTITY_STATUS", ::normalizeIdentity, issues)
        val attendance = parseOptional(row.fields["attendance"], "INVALID_ATTENDANCE", ::normalizeAttendance, issues)
        val intakeRound =
            parseOptional(row.fields["intakeRound"], "INVALID_INTAKE_ROUND", String::toIntOrNull, issues)?.also {
                if (it < 1) issues += "INVALID_INTAKE_ROUND"
            }
        if (issues.isNotEmpty()) return ParseResult(null, issues)
        return ParseResult(
            Candidate(
                lastName,
                firstName,
                row.fields["email"]
                    ?.trim()
                    ?.lowercase()
                    ?.takeIf(String::isNotBlank),
                row.fields["phone"]?.trim()?.takeIf(String::isNotBlank),
                birthDate,
                gender,
                baptism,
                identityStatus,
                attendance,
                intakeRound,
                row.fields["caregiver"]?.trim()?.takeIf(String::isNotBlank),
                firstVisitDate,
            ),
            emptyList(),
        )
    }

    private fun <T> parseOptional(
        value: String?,
        issueCode: String,
        parser: (String) -> T?,
        issues: MutableList<String>,
    ): T? {
        val normalized = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return parser(normalized) ?: run {
            issues += issueCode
            null
        }
    }

    private fun dateOrNull(value: String): LocalDate? =
        sequenceOf(
            { LocalDate.parse(value) },
            { OffsetDateTime.parse(value.replace(' ', 'T')).toLocalDate() },
            { LocalDateTime.parse(value.replace(' ', 'T')).toLocalDate() },
        ).mapNotNull { parser -> runCatching(parser).getOrNull() }.firstOrNull()

    private fun normalizeGender(value: String?) =
        when (value?.trim()?.lowercase()) {
            "m", "male", "남", "남성" -> Gender.M
            "f", "female", "여", "여성" -> Gender.F
            else -> null
        }

    private fun normalizeBaptism(value: String?) =
        when (value?.trim()) {
            "입교" -> Baptism.CONFIRMATION
            "미세례" -> Baptism.UNBAPTIZED
            "유아세례" -> Baptism.INFANT_BAPTIZED
            "일반세례", "세례" -> Baptism.GENERAL_BAPTIZED
            else -> null
        }

    private fun normalizeIdentity(value: String?) =
        enumAlias(
            value,
            mapOf(
                "직장인" to NewcomerIdentityStatus.EMPLOYEE,
                "대학생" to NewcomerIdentityStatus.UNIVERSITY_STUDENT,
            ),
        )

    private fun normalizeAttendance(value: String?) =
        enumAlias(
            value,
            mapOf(
                "정기출석" to PostAssignmentAttendance.REGULAR,
                "가끔출석" to PostAssignmentAttendance.OCCASIONAL,
                "예배만" to PostAssignmentAttendance.WORSHIP_ONLY,
            ),
        )

    private inline fun <reified T : Enum<T>> enumAlias(
        value: String?,
        aliases: Map<String, T>,
    ): T? =
        value?.trim()?.let {
            aliases[it]
                ?: enumValues<T>().firstOrNull { entry -> entry.name == it }
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Candidate(
        val lastName: String,
        val firstName: String,
        val email: String?,
        val phone: String?,
        val birthDate: LocalDate?,
        val gender: Gender?,
        val baptism: Baptism?,
        val identityStatus: NewcomerIdentityStatus?,
        val attendance: PostAssignmentAttendance?,
        val intakeRound: Int?,
        val caregiver: String?,
        val firstVisitDate: LocalDate?,
    ) {
        fun toMember() =
            Member(
                lastName,
                firstName,
                gender = gender,
                birthDate = birthDate,
                phoneNumber = phone,
                email = email,
                memberStatus = MemberStatus.PENDING,
                baptism = baptism,
            )

        fun fingerprint(): String =
            MessageDigest
                .getInstance(
                    "SHA-256",
                ).digest(listOf(lastName, firstName, email, phone, birthDate).joinToString("|").toByteArray())
                .joinToString("") {
                    "%02x".format(it)
                }

        fun applyTo(member: Member) {
            gender?.let { member.gender = it }
            birthDate?.let { member.birthDate = it }
            phone?.let { member.phoneNumber = it }
            email?.let { member.email = it }
            baptism?.let { member.baptism = it }
        }

        fun applyTo(
            profile: NewcomerProfile,
            resolvedCaregiver: Member?,
        ) {
            intakeRound?.let { profile.intakeRound = it }
            caregiver?.let { profile.caregiver = resolvedCaregiver }
            identityStatus?.let { profile.identityStatus = it }
            firstVisitDate?.let { profile.firstVisitDate = it }
            attendance?.let { profile.postAssignmentAttendance = it }
        }
    }

    private data class ParseResult(
        val candidate: Candidate?,
        val issues: List<String>,
    )
}
