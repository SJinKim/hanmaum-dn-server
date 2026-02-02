package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.toEntity
import com.hanmaum.dn.app.features.members.api.updateForm
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.RegisterMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class MemberService (
    private val memberRepository: MemberRepository,
    private val churchGroupRepository: ChurchGroupRepository,
    private val keycloak: Keycloak,
    @Value($$"${app.keycloak.realm}") private val realm: String
) {
    // Helper: String -> UUID
    private fun parseId(id: String): UUID {
        return try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw EntityNotFoundException("Invalid ID format")
        }
    }

    @Transactional(readOnly=true)
    fun getAllMembers(): List<Member> = memberRepository.findAll()

    @Transactional
    fun getMember(publicIdStr: String): Member {
        val uuid = parseId(publicIdStr)
        return memberRepository.findByPublicId(uuid)
            .orElseThrow { EntityNotFoundException("Member not found") }
    }

    @Transactional
    fun createMember(request: CreateMemberRequest): Member {
        val newMember = request.toEntity()

        if(request.groupId != null) {
            val group = churchGroupRepository.findById(request.groupId)
                .orElseThrow{ EntityNotFoundException("Group with ID ${request.groupId} not found") }
            newMember.group = group
        }
        return memberRepository.save(newMember)
    }

    @Transactional
    fun updateMember(publicIdStr: String, request: UpdateMemberRequest): Member {
        val member = getMember(publicIdStr)
        member.updateForm(request)

        if(request.groupId != null) {
            if (member.group?.id != request.groupId) {
                val group = churchGroupRepository.findById(request.groupId)
                    .orElseThrow { EntityNotFoundException("Group with ID ${request.groupId} not found") }
                member.group = group
            }
        } else {
            // Optional: Wenn null gesendet wird, Gruppe entfernen?
            // member.group = null
            // Entscheide hier: Ignorieren oder Löschen?
            // Aktuell ignorieren wir null im Request (behalten die alte Gruppe).
        }
        return memberRepository.save(member)
    }

    @Transactional
    fun softDeleteMember(publicIdStr: String) {
        val member = getMember(publicIdStr)
        member.deletedAt = LocalDateTime.now()
        member.memberStatus = MemberStatus.DELETED
        memberRepository.save(member)
    }

    @Transactional
    fun registerMember(req: RegisterMemberRequest): Member {

        val existingUser = memberRepository.findByEmail(req.email)

        if(existingUser != null) {
            throw IllegalArgumentException("이미 가입된 이메일입니다. Already existing email: ${existingUser.email}")
        }

        // 1. Suche nach existierenden Namen (ignoriert Bindestriche/Großschreibung)
        val existingMembers = memberRepository.findSimilarNames(req.firstName, req.lastName)

        // 2. Discriminator Logik
        val takenDiscriminators = existingMembers
            .map { it.discriminator }
            .toSet()

        var currentDiscriminator: String? = null

        // Loop: null -> A -> B -> C -> ... bis einer frei ist
        if (takenDiscriminators.contains(null)) {
            var charCode = 'A'
            // Loop: Prüfe A, B, C... solange bis einer frei ist
            while (takenDiscriminators.contains(charCode.toString())) {
                charCode++
            }
            currentDiscriminator = charCode.toString()
        }

        // 3. Defaults & Entity Erstellung
        val newMember = Member(
            lastName = req.lastName,
            firstName = req.firstName,
            discriminator = currentDiscriminator,
            email = req.email,

            gender = try { req.gender?.let { Gender.valueOf(it) } } catch (e: Exception) { null },
            baptism = try { req.baptism?.let { Baptism.valueOf(it) } } catch (e: Exception) { Baptism.UNBAPTIZED },

            city = req.city,
            birthDate = req.birthDate,
            phoneNumber = req.phoneNumber,
            street = req.street,
            zipCode = req.zipCode,

            // --- FESTE WERTE (Business Logic) ---
            role = "", // Startet leer
            group = null, // Startet ohne Gruppe
            registrationDate = LocalDate.now(), // Heute
            memberStatus = MemberStatus.ACTIVE, // Default aktiv

        )

        val savedMember = memberRepository.save(newMember)

        // 2 User in KC anglegen
        val keycloakUser = UserRepresentation().apply {
            username = req.email
            email = req.email
            firstName = req.firstName
            lastName = req.lastName
            isEnabled = true
            isEmailVerified = false

            val cred = CredentialRepresentation().apply {
                type = CredentialRepresentation.PASSWORD
                value = req.password
                isTemporary = false
            }
            credentials = listOf(cred)
        }

        val response: Response = keycloak.realm(realm).users().create(keycloakUser)

        if(response.status != 201) {
            val errorBody = response.readEntity(String::class.java)
            throw RuntimeException("Keycloak Fehler: $errorBody")
        }
        return savedMember
    }

    // Beispiel für das Lesen (Login/Profil)
    fun getMemberProfile(email: String): MemberResponse {
        val member = memberRepository.findByEmail(email)
            ?: throw RuntimeException("User not found")

        return MemberResponse(
            id = member.id!!,
            firstName = member.firstName,
            lastName = member.lastName,
            email = member.email ?: "",
            role = member.role ?: "",
            groupName = member.group?.name,
            city = member.city ?: ""
        )
    }
}