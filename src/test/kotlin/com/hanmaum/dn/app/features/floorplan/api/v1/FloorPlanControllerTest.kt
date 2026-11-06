package com.hanmaum.dn.app.features.floorplan.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.floorplan.domain.Floor
import com.hanmaum.dn.app.features.floorplan.domain.Room
import com.hanmaum.dn.app.features.floorplan.service.FloorPlanService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(FloorPlanController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class FloorPlanControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var floorPlanService: FloorPlanService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `GET floors returns wrapped empty list`() {
        `when`(floorPlanService.getFloors()).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/floorplan/floors").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `GET rooms returns wrapped empty list`() {
        val floorId = UUID.randomUUID()
        `when`(floorPlanService.getRooms(floorId)).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/floorplan/floors/$floorId/rooms").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `GET floorplan responses keep data payloads`() {
        val floor = Floor(2, "2층")
        `when`(floorPlanService.getFloors()).thenReturn(listOf(floor))
        `when`(floorPlanService.getRooms(floor.publicId)).thenReturn(listOf(Room(floor, "소그룹실", "모임", listOf(listOf(1.0, 2.0)))))

        mockMvc
            .perform(get("/api/v1/floorplan/floors").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].name").value("2층"))

        mockMvc
            .perform(get("/api/v1/floorplan/floors/${floor.publicId}/rooms").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].name").value("소그룹실"))
    }
}
