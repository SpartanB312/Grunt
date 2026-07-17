package net.spartanb312.grunteon.obfuscator.process.nativecode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeOutputNamingTest {

    @Test
    fun usesConfiguredLoaderLibraryAndDllNames() {
        val bundle = NativeCppBackend.generate(
            methods = emptyList(),
            config = NativePipelineConfig(
                loaderBaseInternalName = "demo/native/RuntimeLoader",
                libraryBaseName = "demo_native",
                libraryResourceDirectory = "demo/assets/native",
                dllName = "demo_payload.dll",
                compilerMode = NativeCompilerMode.Zig,
                targetPlatforms = listOf(NativeTargetPreset.WindowsX86_64, NativeTargetPreset.LinuxX86_64)
            ),
            classExists = { it == "demo/native/RuntimeLoader" }
        )
        val targets = bundle.resolvedLibraryTargets.associateBy { it.platform.resourceDirectory }

        assertEquals("demo/native/RuntimeLoader\$1", bundle.plan.loaderInternalName)
        assertEquals("demo_payload.dll", targets.getValue("windows-x86_64").libraryFileName)
        assertEquals(
            "demo/assets/native/windows-x86_64/demo_payload.dll",
            targets.getValue("windows-x86_64").resourceName
        )
        assertEquals("libdemo_native.so", targets.getValue("linux-x86_64").libraryFileName)
        assertEquals(
            "demo/assets/native/linux-x86_64/libdemo_native.so",
            targets.getValue("linux-x86_64").resourceName
        )
    }

    @Test
    fun derivesWindowsDllNameFromLibraryBaseNameWhenOverrideIsBlank() {
        val bundle = NativeCppBackend.generate(
            methods = emptyList(),
            config = NativePipelineConfig(
                libraryBaseName = "fallback_native",
                dllName = "",
                compilerMode = NativeCompilerMode.Zig,
                targetPlatforms = listOf(NativeTargetPreset.WindowsX86_64)
            ),
            classExists = { false }
        )

        assertEquals("fallback_native.dll", bundle.resolvedLibraryTargets.single().libraryFileName)
    }

    @Test
    fun rejectsPathLikeAndWindowsInvalidOutputNames() {
        listOf("../payload.dll", "C:payload.dll", "bad?.dll", "CON.dll", "trailing. ").forEach { dllName ->
            assertFailsWith<IllegalArgumentException>("Expected invalid DLL name: $dllName") {
                NativeCppBackend.generate(
                    methods = emptyList(),
                    config = NativePipelineConfig(dllName = dllName),
                    classExists = { false }
                )
            }
        }
        listOf("demo\\RuntimeLoader", "demo/C:RuntimeLoader", "demo/${1.toChar()}RuntimeLoader").forEach { name ->
            assertFailsWith<IllegalArgumentException>("Expected invalid Loader name: $name") {
                NativeCppBackend.generate(
                    methods = emptyList(),
                    config = NativePipelineConfig(loaderBaseInternalName = name),
                    classExists = { false }
                )
            }
        }
        listOf("", "/native", "native/", "native//lib", "native/../lib", "native\\lib").forEach { directory ->
            assertFailsWith<IllegalArgumentException>("Expected invalid resource directory: $directory") {
                NativeCppBackend.generate(
                    methods = emptyList(),
                    config = NativePipelineConfig(libraryResourceDirectory = directory),
                    classExists = { false }
                )
            }
        }
    }
}
