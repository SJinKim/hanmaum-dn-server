package com.hanmaum.dn.app.features.members.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.GenerationType
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(name = "members")
@SQLDelete(sql = "UPDATE members SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "members_seq_gen")
    @SequenceGenerator(name = "members_seq_gen", sequenceName = "members_id_seq", allocationSize = 1)
    val id: Long? = null,

    @Column(name = "korean_name", nullable = false)
    var koreanName: String,

    @Column(name = "discriminator")
    var discriminator: String? = null,

    @Column(name = "gender")
    var gender: String? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "address_street")
    var addressStreet: String? = null,

    @Column(name = "registration_date")
    var registrationDate: LocalDate? = null,

    @Column(name = "member_status")
    var memberStatus: String = "ACTIVE"

) : BaseEntity() {

    // Helper für Frontend Anzeige
    val displayName: String
        get() = if (!discriminator.isNullOrBlank()) "$koreanName ($discriminator)" else koreanName

    // --- SICHERE JPA IMPLEMENTIERUNG ---

    // 1. Zwei Entities sind gleich, wenn ihre Klasse passt und die ID identisch ist (und nicht null)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Member) return false

        // Wenn ID null ist (noch nicht gespeichert), sind sie nie gleich (außer selbes Objekt)
        if (id == null || other.id == null) return false

        return id == other.id
    }

    // 2. HashCode sollte bei JPA Entities idealerweise fix sein oder nur auf ID basieren
    // Ein konstanter HashCode ist bei JPA oft sicherer für Lazy Loading
    override fun hashCode(): Int {
        // Rückgabe der Klasse als HashCode verhindert Bugs, wenn ID sich nach Speichern ändert
        return javaClass.hashCode()
    }

    // 3. ToString ohne Beziehungen, um Endlosschleifen zu verhindern
    override fun toString(): String {
        return "Member(id=$id, name='$koreanName')"
    }
}