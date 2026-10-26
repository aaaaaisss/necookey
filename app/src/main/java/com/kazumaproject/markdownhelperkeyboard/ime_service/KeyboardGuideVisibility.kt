package com.kazumaproject.markdownhelperkeyboard.ime_service

internal enum class FlickGuideSurface {
    Custom,
    Other
}

internal data class FlickGuideRuntimeConfig(
    val enabled: Boolean,
    val allowMultiCharacterLabels: Boolean
)

internal fun resolveFlickGuideRuntimeConfig(
    surface: FlickGuideSurface,
    customGuideEnabled: Boolean
): FlickGuideRuntimeConfig {
    return when (surface) {
        FlickGuideSurface.Custom -> FlickGuideRuntimeConfig(
            enabled = customGuideEnabled,
            allowMultiCharacterLabels = false
        )

        FlickGuideSurface.Other -> FlickGuideRuntimeConfig(
            enabled = false,
            allowMultiCharacterLabels = false
        )
    }
}
