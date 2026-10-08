package com.icon.nexus.onboarding

const val ONBOARDING_ROUTE = "onboarding"
const val MAIN_ROUTE = "main"

/**
 * A finished or skipped introduction opens the main screen. Anything else
 * starts the introduction again.
 */
fun initialDestination(showOnboarding: Boolean): String {
    return if (showOnboarding) ONBOARDING_ROUTE else MAIN_ROUTE
}

/**
 * Denying the microphone does not trap the introduction. The person can
 * still finish or skip to the end.
 */
fun canLeaveOnboarding(microphoneGranted: Boolean): Boolean {
    return microphoneGranted || !microphoneGranted
}
