package com.icon.nexus.ui

const val VOICE_ROUTE = "voice"
const val CONVERSATIONS_ROUTE = "conversations"
const val CINEMATIC_ROUTE = "cinematic"

enum class IconPlace {
    Home,
    Voice,
    Conversations,
    Cinematic,
    Settings,
}

enum class IconNav {
    OpenVoice,
    BackHome,
    OpenConversations,
    OpenSettings,
    OpenCinematic,
}

/**
 * Home, Voice, and Conversations are the three destinations. Settings and
 * Cinematic are reached from Home and are not part of that row.
 */
fun destinationAfter(from: IconPlace, action: IconNav): IconPlace {
    return when (from to action) {
        IconPlace.Home to IconNav.OpenVoice -> IconPlace.Voice
        IconPlace.Voice to IconNav.BackHome -> IconPlace.Home
        IconPlace.Voice to IconNav.OpenConversations -> IconPlace.Conversations
        IconPlace.Home to IconNav.OpenSettings -> IconPlace.Settings
        IconPlace.Home to IconNav.OpenCinematic -> IconPlace.Cinematic
        else -> from
    }
}
