package com.example.cleanup.domain.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanPlannerTest {

    private val primary = VolumeSnapshot(name = "external_primary", version = "v1", generation = 120)
    private val stored = mapOf("external_primary" to StoredVolume("external_primary", "v1", 100))

    @Test
    fun `sin escaneo previo se lee todo`() {
        val plan = ScanPlanner.plan(forceFull = false, current = listOf(primary), stored = emptyMap())
        assertNull(plan.single().sinceGeneration)
    }

    @Test
    fun `misma version lee solo desde la ultima generacion`() {
        val plan = ScanPlanner.plan(forceFull = false, current = listOf(primary), stored = stored)
        assertEquals(100L, plan.single().sinceGeneration)
    }

    @Test
    fun `forzar lee todo aunque haya estado previo`() {
        val plan = ScanPlanner.plan(forceFull = true, current = listOf(primary), stored = stored)
        assertNull(plan.single().sinceGeneration)
    }

    @Test
    fun `si cambia la version de MediaStore se lee todo`() {
        val rebuilt = primary.copy(version = "v2")
        val plan = ScanPlanner.plan(forceFull = false, current = listOf(rebuilt), stored = stored)
        assertNull(plan.single().sinceGeneration)
    }

    @Test
    fun `si la generacion retrocede se lee todo`() {
        val reset = primary.copy(generation = 5)
        val plan = ScanPlanner.plan(forceFull = false, current = listOf(reset), stored = stored)
        assertNull(plan.single().sinceGeneration)
    }

    @Test
    fun `una tarjeta SD nueva se lee completa y el resto incremental`() {
        val sd = VolumeSnapshot(name = "1234-abcd", version = "s1", generation = 7)
        val plan = ScanPlanner.plan(forceFull = false, current = listOf(primary, sd), stored = stored)
        assertEquals(100L, plan.first { it.volume.name == "external_primary" }.sinceGeneration)
        assertNull(plan.first { it.volume.name == "1234-abcd" }.sinceGeneration)
    }
}
