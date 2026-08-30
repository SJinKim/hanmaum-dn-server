package com.hanmaum.dn.app.features.notifications.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What happened. Producers pick one when creating a notification.
 *
 * [UNKNOWN] is never written: it is what a stored value parses to when this build does not
 * recognise it — see [AppNotification.typeName].
 */
enum class NotificationType {
    ANNOUNCEMENT,
    EVENT,
    MINISTRY,
    TRAINING,
    ATTENDANCE,
    MEMBER,
    UNKNOWN,
    ;

    companion object {
        fun from(stored: String): NotificationType = entries.firstOrNull { it.name == stored } ?: UNKNOWN
    }
}

/**
 * What the notification points at — the client derives icon and accent colour from it and
 * falls back to a neutral rendering for values it does not know.
 *
 * [UNKNOWN] is never written; see [NotificationType].
 */
enum class NotificationReferenceType {
    ANNOUNCEMENT,
    EVENT,
    MINISTRY,
    TRAINING,
    ATTENDANCE,
    MEMBER,
    UNKNOWN,
    ;

    companion object {
        fun from(stored: String): NotificationReferenceType = entries.firstOrNull { it.name == stored } ?: UNKNOWN
    }
}

@Entity
@Table(name = "notifications")
class AppNotification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    type: NotificationType,
    @Column(nullable = false)
    var title: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    var body: String,
    referenceType: NotificationReferenceType? = null,
    @Column(name = "reference_public_id")
    var referencePublicId: UUID? = null,
) : BaseEntity() {
    /**
     * The type exactly as stored, mapped as text rather than as an enum.
     *
     * `@Enumerated(STRING)` throws while *loading* a row whose value this build does not
     * know, which fails the whole page query rather than the one row — the 500 HDN-119
     * reported. The column is a plain VARCHAR with no CHECK constraint, so any value can
     * legitimately arrive: from a newer instance during a rolling deploy, or by hand.
     *
     * Keeping the raw string also means such a value is passed through to the client
     * untouched. The client already falls back to a neutral rendering for types it does
     * not know, and a newer client may well know one this server does not.
     */
    @Column(name = "type", nullable = false, length = 32)
    var typeName: String = type.name

    /** The reference type as stored; see [typeName]. */
    @Column(name = "reference_type", length = 32)
    var referenceTypeName: String? = referenceType?.name

    /**
     * [typeName] parsed for server-side branching, [NotificationType.UNKNOWN] when this
     * build does not know it. Read-only on purpose: writing through this would turn an
     * unrecognised value into UNKNOWN on the next flush, because Hibernate rewrites every
     * column of a dirty row — marking a notification read would silently destroy its type.
     */
    val type: NotificationType get() = NotificationType.from(typeName)

    /** [referenceTypeName] parsed; see [type]. */
    val referenceType: NotificationReferenceType? get() = referenceTypeName?.let(NotificationReferenceType::from)

    @Column(name = "seen_at")
    var seenAt: Instant? = null

    @Column(name = "read_at")
    var readAt: Instant? = null
}
