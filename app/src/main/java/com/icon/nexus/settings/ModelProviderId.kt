package com.icon.nexus.settings

enum class ModelProviderId(val wireName: String) {
    DEMO("demo"),
    GEMINI("gemini"),
    ;

    companion object {
        fun fromWire(value: String?): ModelProviderId =
            entries.firstOrNull { it.wireName == value } ?: DEMO
    }
}
