package dev.playagain32.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkCompatibilityTest {
    @Test fun missingImportReportsIncludeEverySymbolAndDeduplicate() {
        val names = (1..19).map { "missing_$it" }
        val message = ApkCompatibility.missingImportsMessage(names + names)
        for (name in names) assertTrue(message.contains("$name,") || message.contains("$name."))
        assertEquals(1, Regex("missing_19\\b").findAll(message).count())
    }
    private val arm64 = setOf("arm64-v8a")
    private fun route(abis: Set<String>, device: Set<String> = arm64, minSdk: Int = 21) =
        ApkCompatibility.decide(abis, device, minSdk, 36).route

    @Test fun arm32NeedsTranslationOnArm64OnlyPhone() {
        assertEquals(ApkCompatibility.Route.TranslateArm32, route(setOf("armeabi-v7a")))
    }
    @Test fun compatibleApksKeepTheirOriginalCodeAndSignature() {
        assertEquals(ApkCompatibility.Route.InstallOriginal, route(arm64))
        assertEquals(ApkCompatibility.Route.InstallOriginal, route(emptySet()))
        assertEquals(ApkCompatibility.Route.InstallOriginal, route(setOf("armeabi-v7a"), arm64 + "armeabi-v7a"))
    }
    @Test fun x86IsNotMistakenForOldArm() {
        assertEquals(ApkCompatibility.Route.Unsupported, route(setOf("x86")))
    }
    @Test fun translationDoesNotBypassANewerMinimumAndroidVersion() {
        assertEquals(ApkCompatibility.Route.Unsupported, route(setOf("armeabi"), minSdk = 37))
    }
    @Test fun oldTargetsOnCompatiblePhonesNeedOnlyMetadataPreparation() {
        for (abis in listOf(emptySet(), arm64, setOf("armeabi-v7a"))) {
            assertEquals(ApkCompatibility.Route.PrepareSdk,
                ApkCompatibility.decide(abis, arm64 + "armeabi-v7a", 16, 34, 22).route)
        }
        assertEquals(ApkCompatibility.Route.InstallOriginal,
            ApkCompatibility.decide(arm64, arm64, 21, 34, 23).route)
        assertEquals(ApkCompatibility.Route.PrepareSdk,
            ApkCompatibility.decide(arm64, arm64, 21, 35, 23).route)
        assertEquals(ApkCompatibility.Route.InstallOriginal,
            ApkCompatibility.decide(emptySet(), arm64, 16, 33, 19).route)
    }
    @Test fun raisingTargetMetadataDoesNotMakeAnUnsupportedAbiCompatible() {
        assertEquals(ApkCompatibility.Route.Unsupported,
            ApkCompatibility.decide(setOf("x86"), arm64, 16, 34, 19).route)
        assertEquals(ApkCompatibility.Route.TranslateArm32,
            ApkCompatibility.decide(setOf("armeabi-v7a"), arm64, 16, 34, 19).route)
    }
}
