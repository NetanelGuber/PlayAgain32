package dev.playagain32.app

/** Installation policy and native translation are distinct decisions. */
object ApkCompatibility {
    enum class Route { TranslateArm32, PrepareSdk, InstallOriginal, Unsupported }
    data class Decision(val route: Route, val explanation: String)

    fun minimumInstallTarget(deviceSdk: Int): Int = when {
        deviceSdk >= 35 -> 24
        deviceSdk >= 34 -> 23
        else -> 1
    }

    fun decide(abis: Set<String>, deviceAbis: Set<String>, minimumSdk: Int, deviceSdk: Int,
        targetSdk: Int = Int.MAX_VALUE): Decision {
        if (minimumSdk > deviceSdk) return Decision(Route.Unsupported, "Needs Android API $minimumSdk or newer.")
        if (abis.isEmpty() || abis.any { it in deviceAbis }) {
            val minimumTarget = minimumInstallTarget(deviceSdk)
            if (targetSdk < minimumTarget) return Decision(Route.PrepareSdk,
                "This APK targets Android API $targetSdk; this phone requires at least $minimumTarget. Preparation updates its Android metadata and re-signs a copy, keeping its game code and native libraries.")
            return Decision(Route.InstallOriginal, "Its code matches your phone. The original APK and signature are kept.")
        }
        if ("arm64-v8a" in deviceAbis && abis.any { it == "armeabi-v7a" || it == "armeabi" }) {
            return Decision(Route.TranslateArm32, "32-bit ARM code needs the compatibility runtime.")
        }
        return Decision(Route.Unsupported, "This APK's native architecture is not supported on this phone.")
    }
}
