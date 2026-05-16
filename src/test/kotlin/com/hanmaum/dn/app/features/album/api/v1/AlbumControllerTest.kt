package com.hanmaum.dn.app.features.album.api.v1

import com.hanmaum.dn.app.features.album.api.v1.dto.AlbumDto
import com.hanmaum.dn.app.features.album.service.AlbumService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(AlbumController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
class AlbumControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var albumService: AlbumService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    // MemberStatusInterceptor uses this; mock it so the interceptor's real logic runs
    // (unauthenticated requests return true from preHandle without hitting the DB)
    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `GET albums returns 200 without authentication`() {
        `when`(albumService.getAlbums()).thenReturn(
            listOf(
                AlbumDto(
                    publicId = UUID.fromString("3f2a1b4c-0000-0000-0000-000000000001"),
                    name = "여름수련회",
                    pcloudCode = "CODE1",
                    displayOrder = 0,
                ),
            ),
        )

        mockMvc
            .perform(get("/api/v1/albums"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("여름수련회"))
            .andExpect(jsonPath("$[0].pcloudCode").value("CODE1"))
            .andExpect(jsonPath("$[0].displayOrder").value(0))
    }

    @Test
    fun `GET albums returns empty array when no albums exist`() {
        `when`(albumService.getAlbums()).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/albums"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }
}
