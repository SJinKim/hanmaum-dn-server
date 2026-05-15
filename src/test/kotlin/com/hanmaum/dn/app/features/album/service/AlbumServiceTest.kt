package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.features.album.domain.Album
import com.hanmaum.dn.app.features.album.repository.AlbumRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AlbumServiceTest {
    @Mock
    private lateinit var albumRepository: AlbumRepository

    @InjectMocks
    private lateinit var albumService: AlbumService

    @Test
    fun `getAlbums returns albums sorted by displayOrder`() {
        val album1 = Album(name = "여름수련회", pcloudCode = "CODE1", displayOrder = 0)
        val album2 = Album(name = "봄나들이", pcloudCode = "CODE2", displayOrder = 1)
        `when`(albumRepository.findAllActive()).thenReturn(listOf(album1, album2))

        val result = albumService.getAlbums()

        assertEquals(2, result.size)
        assertEquals("여름수련회", result[0].name)
        assertEquals("CODE1", result[0].pcloudCode)
        assertEquals(0, result[0].displayOrder)
    }

    @Test
    fun `getAlbums returns empty list when no albums exist`() {
        `when`(albumRepository.findAllActive()).thenReturn(emptyList())

        val result = albumService.getAlbums()

        assertEquals(emptyList<Any>(), result)
    }
}
