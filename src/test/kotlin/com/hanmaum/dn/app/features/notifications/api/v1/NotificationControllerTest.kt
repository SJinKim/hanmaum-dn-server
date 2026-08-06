package com.hanmaum.dn.app.features.notifications.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import com.hanmaum.dn.app.features.notifications.domain.NotificationType
import com.hanmaum.dn.app.features.notifications.service.NotificationService
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(NotificationController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class NotificationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var notificationService: NotificationService

    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `GET notifications returns paginated notifications with correct mapping`() {
        val createdAt = Instant.parse("2026-07-16T10:00:00Z")
        val seenAt = Instant.parse("2026-07-16T11:00:00Z")

        val member =
            Member(
                keycloakId = "kc-001",
                email = "user@example.com",
                firstName = "John",
                lastName = "Doe",
            )
        member.id = 1L

        val notification =
            AppNotification(
                member = member,
                type = NotificationType.ANNOUNCEMENT,
                title = "Test Title",
                body = "Test Body",
                referenceType = null,
                referencePublicId = null,
            )
        notification.createdAt = createdAt
        notification.seenAt = seenAt

        val page: Page<AppNotification> = PageImpl(listOf(notification), PageRequest.of(0, 20), 1)

        `when`(notificationService.getNotifications("kc-001", "user@example.com", 0, 20))
            .thenReturn(page)

        mockMvc
            .perform(
                get("/api/v1/me/notifications")
                    .param("page", "0")
                    .param("size", "20")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].type").value("ANNOUNCEMENT"))
            .andExpect(jsonPath("$.data.items[0].title").value("Test Title"))
            .andExpect(jsonPath("$.data.items[0].body").value("Test Body"))
            .andExpect(jsonPath("$.data.items[0].seenAt").value("2026-07-16T11:00:00Z"))
            .andExpect(jsonPath("$.data.items[0].readAt").doesNotExist())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `POST notifications mark-read for foreign notification throws NoSuchElementException`() {
        val notificationId = UUID.randomUUID()
        doThrow(NoSuchElementException("notification not found"))
            .`when`(notificationService)
            .markRead("kc-001", "user@example.com", notificationId)

        mockMvc
            .perform(
                post("/api/v1/me/notifications/$notificationId/read")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE notification removes own notification`() {
        val notificationId = UUID.randomUUID()

        mockMvc
            .perform(
                delete("/api/v1/me/notifications/$notificationId")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `DELETE notification for foreign notification throws NoSuchElementException`() {
        val notificationId = UUID.randomUUID()
        doThrow(NoSuchElementException("notification not found"))
            .`when`(notificationService)
            .deleteNotification("kc-001", "user@example.com", notificationId)

        mockMvc
            .perform(
                delete("/api/v1/me/notifications/$notificationId")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE notifications clears all notifications for caller`() {
        mockMvc
            .perform(
                delete("/api/v1/me/notifications")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `PUT device-tokens registers device token with platform binding`() {
        mockMvc
            .perform(
                put("/api/v1/me/device-tokens")
                    .contentType("application/json")
                    .content("""{"token": "device-token-123", "platform": "ANDROID"}""")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `DELETE device-tokens removes token`() {
        mockMvc
            .perform(
                delete("/api/v1/me/device-tokens/device-token-123")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `GET notification-settings returns current push setting`() {
        `when`(notificationService.getPushEnabled("kc-001", "user@example.com"))
            .thenReturn(true)

        mockMvc
            .perform(
                get("/api/v1/me/notification-settings")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.pushEnabled").value(true))
    }

    @Test
    fun `POST mark-seen marks all notifications as seen`() {
        mockMvc
            .perform(
                post("/api/v1/me/notifications/mark-seen")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `POST read-all marks all notifications as read`() {
        mockMvc
            .perform(
                post("/api/v1/me/notifications/read-all")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `GET unseen-count returns count of unseen notifications`() {
        `when`(notificationService.getUnseenCount("kc-001", "user@example.com"))
            .thenReturn(5L)

        mockMvc
            .perform(
                get("/api/v1/me/notifications/unseen-count")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.count").value(5))
    }

    @Test
    fun `PUT notification-settings updates push enabled setting`() {
        mockMvc
            .perform(
                put("/api/v1/me/notification-settings")
                    .contentType("application/json")
                    .content("""{"pushEnabled": false}""")
                    .with(
                        jwt().jwt {
                            it.subject("kc-001")
                            it.claim("email", "user@example.com")
                        },
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }
}
