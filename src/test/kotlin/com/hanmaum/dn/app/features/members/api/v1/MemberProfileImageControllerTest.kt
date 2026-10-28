package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberPurgeService
import com.hanmaum.dn.app.features.members.service.MemberService
import com.hanmaum.dn.app.features.members.service.ProfileImageService
import com.hanmaum.dn.app.features.members.service.ProfileImageStorageUnavailableException
import com.hanmaum.dn.app.features.members.service.ProfileImageTooLargeException
import com.hanmaum.dn.app.features.members.service.ProfileImageUnsupportedTypeException
import jakarta.persistence.EntityNotFoundException
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** The profile-picture endpoints over the wire (HDN-140): status codes, roles and the response body. */
@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberProfileImageControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberPurgeService: MemberPurgeService

    @MockitoBean private lateinit var profileImageService: ProfileImageService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val imageBytes = byteArrayOf(1, 2, 3, 4)

    private fun profile() =
        MemberResponse(
            publicId = "pub-1",
            firstName = "철수",
            lastName = "김",
            email = "chulsoo@example.com",
            emailVerified = true,
            status = MemberStatus.ACTIVE,
            registrationDate = LocalDate.of(2024, 3, 17),
            profileImageUrl = "https://s3.example/profile-images/pub-1/a.jpg?sig",
            profileImageVersion = 3,
        )

    private fun memberToken() =
        jwt()
            .jwt { it.subject("kc-001").claim("email", "chulsoo@example.com") }
            .authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    private fun adminToken() =
        jwt()
            .jwt { it.subject("kc-admin").claim("email", "admin@example.com") }
            .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private fun upload() =
        multipart(HttpMethod.PUT, "/api/v1/members/me/profile-image")
            .file(MockMultipartFile("file", "me.jpg", "image/jpeg", imageBytes))

    private fun stubProfile() {
        `when`(
            memberService.getMemberProfile(eq("kc-001"), anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(profile())
    }

    @Test
    fun `upload hands the file to the service and answers with the fresh profile`() {
        stubProfile()
        val received = AtomicReference<ByteArray>()
        `when`(profileImageService.uploadForMember(eq("kc-001"), any())).thenAnswer {
            received.set(it.getArgument<InputStream>(1).readAllBytes())
            Member(lastName = "김", firstName = "철수")
        }

        mockMvc
            .perform(upload().with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.profileImageUrl").value("https://s3.example/profile-images/pub-1/a.jpg?sig"))
            .andExpect(jsonPath("$.data.profileImageVersion").value(3))

        assertContentEquals(imageBytes, received.get())
    }

    @Test
    fun `upload of a too large image is 413`() {
        doThrow(ProfileImageTooLargeException()).`when`(profileImageService).uploadForMember(any(), any())

        mockMvc.perform(upload().with(memberToken())).andExpect(status().isContentTooLarge)
    }

    @Test
    fun `upload of an unsupported type is 415`() {
        doThrow(ProfileImageUnsupportedTypeException()).`when`(profileImageService).uploadForMember(any(), any())

        mockMvc.perform(upload().with(memberToken())).andExpect(status().isUnsupportedMediaType)
    }

    @Test
    fun `upload while storage is down is 503`() {
        doThrow(ProfileImageStorageUnavailableException(RuntimeException("down")))
            .`when`(profileImageService)
            .uploadForMember(any(), any())

        mockMvc.perform(upload().with(memberToken())).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `upload without a token is 401`() {
        mockMvc.perform(upload()).andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleting the own picture answers with the fresh profile`() {
        stubProfile()

        mockMvc
            .perform(delete("/api/v1/members/me/profile-image").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.publicId").value("pub-1"))

        verify(profileImageService).deleteForMember("kc-001")
    }

    @Test
    fun `admin deletes a member's picture with 204`() {
        val publicId = UUID.randomUUID()

        mockMvc
            .perform(delete("/api/v1/members/$publicId/profile-image").with(adminToken()))
            .andExpect(status().isNoContent)

        verify(profileImageService).deleteByAdmin(publicId)
    }

    @Test
    fun `admin delete of an unknown member is 404`() {
        val publicId = UUID.randomUUID()
        doThrow(EntityNotFoundException("Member not found")).`when`(profileImageService).deleteByAdmin(publicId)

        mockMvc
            .perform(delete("/api/v1/members/$publicId/profile-image").with(adminToken()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a member cannot delete someone else's picture`() {
        val publicId = UUID.randomUUID()

        mockMvc
            .perform(delete("/api/v1/members/$publicId/profile-image").with(memberToken()))
            .andExpect(status().isForbidden)

        verify(profileImageService, never()).deleteByAdmin(any())
    }
}
