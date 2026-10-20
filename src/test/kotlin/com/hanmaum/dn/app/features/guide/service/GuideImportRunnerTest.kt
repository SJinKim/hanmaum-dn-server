package com.hanmaum.dn.app.features.guide.service

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.support.GenericApplicationContext
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuideImportRunnerTest {
    private val service = mock<GuideImportService>()
    private val file = Path.of("synthetic.csv")
    private val args = DefaultApplicationArguments()

    private fun run(
        properties: GuideImportProperties,
        vararg profiles: String,
    ) {
        val context = GenericApplicationContext()
        context.environment.setActiveProfiles(*profiles)
        context.refresh()
        try {
            GuideImportRunner().guideImport(properties, service, context.environment, context).run(args)
            assertTrue(context.isActive, "Other startup runners must still have an open context")
            GuideImportRunner().guideImportShutdown(context).onApplicationEvent(
                ApplicationReadyEvent(SpringApplication(GuideImportRunner::class.java), emptyArray(), context, Duration.ZERO),
            )
        } finally {
            assertFalse(context.isActive, "Every maintenance invocation must shut down its context")
            context.close()
        }
    }

    @Test fun `ordinary dev staging and production profiles cannot import`() {
        for (profile in listOf("dev", "staging", "prod")) {
            assertFailsWith<IllegalArgumentException> { run(GuideImportProperties(file = file), profile) }
        }
        verifyNoInteractions(service)
    }

    @Test fun `explicit maintenance imports once then closes even for production target`() {
        whenever(service.import(file, true, null)).thenReturn(
            GuideImportResult(true, "synthetic-sha", 0, 0, 0, listOf(0, 0, 0), 0, 0, emptyList(), emptyList()),
        )
        run(GuideImportProperties(file = file), "prod", "guide-import")
        verify(service).import(file, true, null)
    }

    @Test fun `import failure closes the context and propagates nonzero failure to boot`() {
        whenever(service.import(file, false, "hash")).thenThrow(IllegalArgumentException("Import conflicts"))
        val failure =
            assertFailsWith<IllegalArgumentException> {
                run(GuideImportProperties(file = file, dryRun = false, expectedSha256 = "hash"), "guide-import")
            }
        assertEquals("Import conflicts", failure.message)
    }

    @Test fun `rollback is also one shot and does not accept a simultaneous import`() {
        val id = UUID.randomUUID()
        whenever(service.rollback(id, true)).thenReturn(0)
        run(GuideImportProperties(rollbackBatch = id), "guide-import")
        verify(service).rollback(id, true)
        assertFailsWith<IllegalArgumentException> {
            run(GuideImportProperties(file = file, rollbackBatch = id), "guide-import")
        }
    }
}
