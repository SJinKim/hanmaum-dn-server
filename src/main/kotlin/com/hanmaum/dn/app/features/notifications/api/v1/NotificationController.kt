package com.hanmaum.dn.app.features.notifications.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.notifications.api.toDto
import com.hanmaum.dn.app.features.notifications.api.v1.dto.NotificationPageResponse
import com.hanmaum.dn.app.features.notifications.api.v1.dto.NotificationSettingsDto
import com.hanmaum.dn.app.features.notifications.api.v1.dto.RegisterDeviceTokenRequest
import com.hanmaum.dn.app.features.notifications.api.v1.dto.UnseenCountResponse
import com.hanmaum.dn.app.features.notifications.service.NotificationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/me")
class NotificationController(
    private val notificationService: NotificationService,
) {
    private val Jwt.email: String? get() = getClaimAsString("email")

    @GetMapping("/notifications")
    fun getNotifications(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<NotificationPageResponse>> {
        val result = notificationService.getNotifications(jwt.subject, jwt.email, page, size)
        return ResponseEntity.ok(
            ApiResponse.success(
                data =
                    NotificationPageResponse(
                        items = result.content.map { it.toDto() },
                        page = result.number,
                        hasNext = result.hasNext(),
                    ),
            ),
        )
    }

    @GetMapping("/notifications/unseen-count")
    fun getUnseenCount(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<UnseenCountResponse>> =
        ResponseEntity.ok(ApiResponse.success(data = UnseenCountResponse(notificationService.getUnseenCount(jwt.subject, jwt.email))))

    @PostMapping("/notifications/mark-seen")
    fun markAllSeen(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.markAllSeen(jwt.subject, jwt.email)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @PostMapping("/notifications/{publicId}/read")
    fun markRead(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.markRead(jwt.subject, jwt.email, publicId)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @PostMapping("/notifications/read-all")
    fun markAllRead(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.markAllRead(jwt.subject, jwt.email)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @DeleteMapping("/notifications/{publicId}")
    fun deleteNotification(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.deleteNotification(jwt.subject, jwt.email, publicId)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @DeleteMapping("/notifications")
    fun deleteAllNotifications(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.deleteAll(jwt.subject, jwt.email)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @PutMapping("/device-tokens")
    fun registerDeviceToken(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: RegisterDeviceTokenRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.registerDeviceToken(jwt.subject, jwt.email, request.token, request.platform)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @DeleteMapping("/device-tokens/{token}")
    fun deleteDeviceToken(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable token: String,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.deleteDeviceToken(jwt.subject, jwt.email, token)
        return ResponseEntity.ok(ApiResponse.success())
    }

    @GetMapping("/notification-settings")
    fun getSettings(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<NotificationSettingsDto>> =
        ResponseEntity.ok(ApiResponse.success(data = NotificationSettingsDto(notificationService.getPushEnabled(jwt.subject, jwt.email))))

    @PutMapping("/notification-settings")
    fun updateSettings(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: NotificationSettingsDto,
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationService.setPushEnabled(jwt.subject, jwt.email, request.pushEnabled)
        return ResponseEntity.ok(ApiResponse.success())
    }
}
