package com.hanmaum.dn.app.features.verses.domain

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * One member marking one thing off on one day.
 *
 * Insert-only by design. There is no delete and no update: "no taking it back" is enforced
 * by the operation not existing, rather than by trusting the client not to call it. The
 * date is stamped by the server for the same reason — a body-supplied date would put the
 * "today only" rule on the client, where changing the device clock defeats it.
 */
@Entity
@Table(
    name = "verse_records",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_verse_record", columnNames = ["member_id", "record_date", "kind"]),
    ],
)
class VerseRecord(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @Column(name = "record_date", nullable = false)
    val recordDate: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    val kind: VerseRecordKind,
) : BaseEntity()
