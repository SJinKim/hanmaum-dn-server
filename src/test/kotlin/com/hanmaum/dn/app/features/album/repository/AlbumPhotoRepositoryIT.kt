package com.hanmaum.dn.app.features.album.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class AlbumPhotoRepositoryIT {
    @Autowired lateinit var photoRepository: AlbumPhotoRepository

    @Autowired lateinit var entityManager: EntityManager

    private fun photo(
        album: Album,
        member: Member,
        takenAt: Instant,
        status: PhotoStatus = PhotoStatus.ACTIVE,
    ): AlbumPhoto =
        AlbumPhoto(
            album = album,
            originalKey = "albums/${album.publicId}/x.jpg",
            contentType = "image/jpeg",
            sizeBytes = 10L,
            uploadedBy = member,
        ).also {
            it.takenAt = takenAt
            it.thumbKey = "albums/${album.publicId}/thumbs/x.jpg"
            it.status = status
            entityManager.persist(it)
        }

    @Test
    fun `paged listing returns only ACTIVE photos of the album, newest takenAt first`() {
        val group = ChurchGroup(division = "1구역", name = "소망")
        entityManager.persist(group)
        val member = Member(lastName = "김", firstName = "철수", group = group)
        entityManager.persist(member)
        val album = Album(name = "소망", group = group)
        entityManager.persist(album)

        val old = photo(album, member, Instant.parse("2026-07-01T10:00:00Z"))
        val new = photo(album, member, Instant.parse("2026-07-02T10:00:00Z"))
        photo(album, member, Instant.parse("2026-07-03T10:00:00Z"), status = PhotoStatus.PENDING)
        entityManager.flush()

        val page =
            photoRepository.findByAlbumIdAndStatusAndDeletedAtIsNullOrderByTakenAtDesc(
                album.id!!,
                PhotoStatus.ACTIVE,
                PageRequest.of(0, 10),
            )

        assertEquals(2, page.totalElements)
        assertEquals(new.publicId, page.content[0].publicId)
        assertEquals(old.publicId, page.content[1].publicId)
    }

    @Test
    fun `calendar aggregation groups ACTIVE photos per day with count and first thumb`() {
        val group = ChurchGroup(division = "1구역", name = "믿음")
        entityManager.persist(group)
        val member = Member(lastName = "이", firstName = "영희", group = group)
        entityManager.persist(member)
        val album = Album(name = "믿음", group = group)
        entityManager.persist(album)

        photo(album, member, Instant.parse("2026-07-01T09:00:00Z"))
        photo(album, member, Instant.parse("2026-07-01T18:00:00Z"))
        photo(album, member, Instant.parse("2026-07-05T12:00:00Z"))
        photo(album, member, Instant.parse("2026-07-06T12:00:00Z"), status = PhotoStatus.PENDING)
        entityManager.flush()

        val from = Instant.parse("2026-07-01T00:00:00Z")
        val to = from.plus(31, ChronoUnit.DAYS)
        val days = photoRepository.aggregateCalendarDays(from, to)

        assertEquals(2, days.size)
        assertEquals(2L, days[0].getPhotoCount())
        assertEquals(1L, days[1].getPhotoCount())
    }
}
