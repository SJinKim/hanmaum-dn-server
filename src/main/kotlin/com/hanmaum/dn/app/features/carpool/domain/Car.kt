package com.hanmaum.dn.app.features.carpool.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "cars")
class Car(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_member_id", nullable = false)
    var driver: Member,
    @Column(name = "session_date", nullable = false)
    var sessionDate: LocalDate,
    var name: String? = null,
    @Column(name = "max_seats", nullable = false)
    var maxSeats: Int,
    @Column(name = "current_passengers")
    var currentPassengers: Int = 0,
    @Column(name = "departure_location")
    var departureLocation: String? = null,
    @Column(name = "departure_time")
    var departureTime: LocalTime? = null,
) : BaseEntity()
