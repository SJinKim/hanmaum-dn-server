package com.hanmaum.dn.app.features.floorplan.service

import com.hanmaum.dn.app.features.floorplan.domain.Floor
import com.hanmaum.dn.app.features.floorplan.domain.Room
import com.hanmaum.dn.app.features.floorplan.repository.FloorRepository
import com.hanmaum.dn.app.features.floorplan.repository.RoomRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FloorPlanServiceTest {
    @Mock
    private lateinit var floorRepository: FloorRepository

    @Mock
    private lateinit var roomRepository: RoomRepository

    @InjectMocks
    private lateinit var floorPlanService: FloorPlanService

    @Test
    fun `getFloors returns list from repository`() {
        val floors = listOf(Floor(floorNumber = 1, name = "1층"))
        `when`(floorRepository.findAllActive()).thenReturn(floors)

        val result = floorPlanService.getFloors()

        assertEquals(floors, result)
        verify(floorRepository).findAllActive()
    }

    @Test
    fun `getFloors returns empty list when no active floors`() {
        `when`(floorRepository.findAllActive()).thenReturn(emptyList())

        val result = floorPlanService.getFloors()

        assertEquals(emptyList<Floor>(), result)
    }

    @Test
    fun `getRooms delegates to repository with given publicId`() {
        val floor = Floor(floorNumber = 1, name = "1층")
        val rooms =
            listOf(
                Room(floor = floor, name = "대예배실", description = "주일 예배 공간"),
            )
        val floorId = UUID.randomUUID()
        `when`(roomRepository.findActiveByFloorPublicId(floorId)).thenReturn(rooms)

        val result = floorPlanService.getRooms(floorId)

        assertEquals(rooms, result)
        verify(roomRepository).findActiveByFloorPublicId(floorId)
    }

    @Test
    fun `getRooms returns empty list when floor has no rooms`() {
        val floorId = UUID.randomUUID()
        `when`(roomRepository.findActiveByFloorPublicId(floorId)).thenReturn(emptyList())

        val result = floorPlanService.getRooms(floorId)

        assertEquals(emptyList<Room>(), result)
    }
}
