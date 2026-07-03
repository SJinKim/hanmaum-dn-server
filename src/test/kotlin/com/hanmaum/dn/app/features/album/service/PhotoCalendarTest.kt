package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import com.hanmaum.dn.app.features.album.repository.CalendarDayProjection
import com.hanmaum.dn.app.features.album.repository.PhotoSaveRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.sql.Date
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@ExtendWith(MockitoExtension::class)
class PhotoCalendarTest {
    @Mock private lateinit var albumRepository: AlbumRepository

    @Mock private lateinit var photoRepository: AlbumPhotoRepository

    @Mock private lateinit var saveRepository: PhotoSaveRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var storage: S3StorageService

    private fun service() = PhotoQueryService(albumRepository, photoRepository, saveRepository, memberRepository, storage)

    private class Row(
        val day: LocalDate,
        val count: Long,
        val thumb: String?,
    ) : CalendarDayProjection {
        override fun getDay(): Date = Date.valueOf(day)

        override fun getPhotoCount(): Long = count

        override fun getCoverThumbKey(): String? = thumb
    }

    @Test
    fun `calendar maps aggregation rows to dtos with presigned covers`() {
        `when`(
            photoRepository.aggregateCalendarDays(
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
            ),
        ).thenReturn(listOf(Row(LocalDate.of(2026, 7, 1), 2, "t1"), Row(LocalDate.of(2026, 7, 5), 1, null)))
        `when`(storage.presignGet("t1")).thenReturn("https://signed/t1")

        val days = service().getCalendar(YearMonth.of(2026, 7))

        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 7, 1), days[0].date)
        assertEquals(2L, days[0].photoCount)
        assertEquals("https://signed/t1", days[0].coverThumbUrl)
        assertEquals(null, days[1].coverThumbUrl)
    }

    @Test
    fun `photos by date fetches the UTC day window`() {
        val group = ChurchGroup(division = "1구역", name = "소망").also { it.id = 1L }
        val member =
            Member(lastName = "김", firstName = "철수", group = group).also {
                it.id = 7L
                it.keycloakId = "kc-7"
            }
        val album = Album(name = "소망", group = group).also { it.id = 10L }
        val photo =
            AlbumPhoto(album, "albums/${album.publicId}/p.jpg", "image/jpeg", 5L, member).also {
                it.id = 100L
                it.takenAt = Instant.parse("2026-07-02T10:00:00Z")
            }
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-7")).thenReturn(member)
        `when`(
            photoRepository.findActiveTakenBetween(
                Instant.parse("2026-07-02T00:00:00Z"),
                Instant.parse("2026-07-03T00:00:00Z"),
            ),
        ).thenReturn(listOf(photo))
        `when`(saveRepository.findByMemberIdAndPhotoIdAndDeletedAtIsNull(7L, 100L)).thenReturn(null)
        `when`(storage.presignGet(photo.originalKey)).thenReturn("https://signed/orig")

        val result = service().getPhotosByDate(LocalDate.of(2026, 7, 2), "kc-7")

        assertEquals(1, result.size)
        assertEquals(photo.publicId, result[0].photoId)
    }
}
