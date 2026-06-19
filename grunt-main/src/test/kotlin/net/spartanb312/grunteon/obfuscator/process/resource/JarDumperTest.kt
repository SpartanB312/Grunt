package net.spartanb312.grunteon.obfuscator.process.resource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes

class JarDumperTest {

    @Test
    fun processableClassesUseComputeFramesByDefault() {
        assertFalse(
            JarDumper.shouldUseComputeMax(
                forceComputeMax = false,
                missingAny = false,
                processableByGlobalExclusion = true
            )
        )
    }

    @Test
    fun computeMaxIsUsedWhenForcedMissingOrGloballyExcluded() {
        assertTrue(
            JarDumper.shouldUseComputeMax(
                forceComputeMax = true,
                missingAny = false,
                processableByGlobalExclusion = true
            )
        )
        assertTrue(
            JarDumper.shouldUseComputeMax(
                forceComputeMax = false,
                missingAny = true,
                processableByGlobalExclusion = true
            )
        )
        assertTrue(
            JarDumper.shouldUseComputeMax(
                forceComputeMax = false,
                missingAny = false,
                processableByGlobalExclusion = false
            )
        )
    }

    @Test
    fun missingWarningIsOnlyForProcessableClassesFallingBackToComputeMax() {
        assertTrue(
            JarDumper.shouldWarnComputeMaxDueToMissing(
                forceComputeMax = false,
                missingAny = true,
                processableByGlobalExclusion = true
            )
        )
        assertFalse(
            JarDumper.shouldWarnComputeMaxDueToMissing(
                forceComputeMax = true,
                missingAny = true,
                processableByGlobalExclusion = true
            )
        )
        assertFalse(
            JarDumper.shouldWarnComputeMaxDueToMissing(
                forceComputeMax = false,
                missingAny = true,
                processableByGlobalExclusion = false
            )
        )
    }

    @Test
    fun pathResourceOutputReportsSizeWhenAvailable() {
        val path = createTempFile("grunteon-output-size", ".jar")
        path.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertEquals(4, PathResourceOutput(path).sizeBytes())
    }
}
