package com.hanmaum.dn.app.features.ministry.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

@Entity
@Table(name = "ministries")
class Ministry(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "short_description", nullable = false, length = 200)
    var shortDescription: String,
    @Column(name = "long_description", nullable = false, columnDefinition = "TEXT")
    var longDescription: String,
    @Column(name = "image_url")
    var imageUrl: String? = null,
    @Column(name = "is_ministry_active", nullable = false)
    var isMinistryActive: Boolean = true,
) : BaseEntity() {
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ministry_requirements",
        joinColumns = [JoinColumn(name = "ministry_id")],
    )
    @OrderColumn(name = "display_order")
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    val requirements: MutableList<String> = mutableListOf()

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ministry_schedules",
        joinColumns = [JoinColumn(name = "ministry_id")],
    )
    @OrderColumn(name = "display_order")
    val schedules: MutableList<MinistrySchedule> = mutableListOf()

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "ministry_contacts",
        joinColumns = [JoinColumn(name = "ministry_id")],
    )
    @OrderColumn(name = "display_order")
    val contacts: MutableList<MinistryContact> = mutableListOf()

    fun replaceRequirements(newRequirements: List<String>) {
        requirements.clear()
        requirements.addAll(newRequirements)
    }

    fun replaceSchedules(newSchedules: List<MinistrySchedule>) {
        schedules.clear()
        schedules.addAll(newSchedules)
    }

    fun replaceContacts(newContacts: List<MinistryContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
    }
}
