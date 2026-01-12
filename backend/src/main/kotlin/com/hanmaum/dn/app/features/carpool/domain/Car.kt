package com.hanmaum.dn.app.features.carpool.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity
@Table(name = "cars")
class Car(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

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
    var departureTime: LocalDateTime? = null,
) : BaseEntity()