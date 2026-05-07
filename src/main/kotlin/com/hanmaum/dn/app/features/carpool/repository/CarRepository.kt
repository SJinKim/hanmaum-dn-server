package com.hanmaum.dn.app.features.carpool.repository

import com.hanmaum.dn.app.features.carpool.domain.Car
import com.hanmaum.dn.app.features.carpool.domain.CarPassenger
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface CarRepository : JpaRepository<Car, Long> {
    // Finde alle Autos für ein bestimmtes Datum (z.B. nächsten Sonntag)
    fun findAllBySessionDate(date: LocalDate): List<Car>
}

@Repository
interface CarPassengerRepository : JpaRepository<CarPassenger, Long> {
    // Prüfen, ob ein Member an diesem Datum schon in IRGENDEINEM Auto sitzt
    // Dazu joinen wir zur Car Tabelle, um das Datum zu prüfen
    @org.springframework.data.jpa.repository.Query(
        """
        SELECT COUNT(cp) > 0 FROM CarPassenger cp 
        JOIN cp.car c 
        WHERE cp.member.id = :memberId AND c.sessionDate = :date
    """,
    )
    fun isMemberAlreadyDriving(
        memberId: Long,
        date: LocalDate,
    ): Boolean

    // Finde den Eintrag zum Löschen (Aussteigen)
    fun findByCarIdAndMemberId(
        carId: Long,
        memberId: Long,
    ): Optional<CarPassenger>
}
