package com.hanmaum.dn.app.features.notifications.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class NotificationType {
    ANNOUNCEMENT,
    EVENT,
}

enum class NotificationReferenceType {
    ANNOUNCEMENT,
    EVENT,
}

@Entity
@Table(name = "notifications")
class AppNotification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var type: NotificationType,
    @Column(nullable = false)
    var title: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    var body: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 32)
    var referenceType: NotificationReferenceType? = null,
    @Column(name = "reference_public_id")
    var referencePublicId: UUID? = null,
) : BaseEntity() {
    @Column(name = "seen_at")
    var seenAt: Instant? = null

    @Column(name = "read_at")
    var readAt: Instant? = null
}
