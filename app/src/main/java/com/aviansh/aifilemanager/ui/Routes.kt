package com.aviansh.aifilemanager.ui

object Routes {
    const val HOME = "files_screen"
    const val AI_SETTINGS = "ai_settings"

    @Deprecated("Renamed to AI_SETTINGS — the screen now configures Gemini and OpenAI compatible endpoints.")
    const val GEMINI_SETTINGS = AI_SETTINGS
}
