package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateFormLinkRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormLink
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerFormLinkRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerFormSubmissionRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NewcomerFormServiceTest {
    private val links = mock<NewcomerFormLinkRepository>()
    private val submissions = mock<NewcomerFormSubmissionRepository>()
    private val profiles = mock<NewcomerProfileRepository>()
    private val newcomers = mock<NewcomerService>()
    private val service = NewcomerFormService(links, submissions, profiles, newcomers, "2026-09", 10)

    @Test
    fun `created link returns plaintext token but stores only its hash`() {
        whenever(links.save(any<NewcomerFormLink>())).thenAnswer { it.arguments[0] }

        val response = service.createLink(CreateFormLinkRequest(), "admin-sub")

        assertNotNull(response.token)
        assertFalse(response.token!!.matches(Regex("[0-9a-f]{64}")))
        assertEquals(43, response.token!!.length)
    }

    @Test
    fun `link cannot be valid for more than 24 hours`() {
        val error =
            assertThrows<NewcomerException> {
                service.createLink(CreateFormLinkRequest(Instant.now().plusSeconds(25 * 3600)), "admin-sub")
            }

        assertEquals(HttpStatus.BAD_REQUEST, error.status)
    }
}
