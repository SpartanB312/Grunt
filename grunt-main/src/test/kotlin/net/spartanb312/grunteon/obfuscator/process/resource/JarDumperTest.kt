package net.spartanb312.grunteon.obfuscator.process.resource

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
