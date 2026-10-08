package com.icon.nexus.onboarding

import com.icon.nexus.ai.AIEvent
import com.icon.nexus.ai.AIManager
import com.icon.nexus.ai.AIProvider
import com.icon.nexus.ai.AIRequest
import com.icon.nexus.ai.DemoProvider
import com.icon.nexus.audio.QueuedSpeechSynthesizer
import com.icon.nexus.audio.SpeechInputGate
import com.icon.nexus.data.AppSettings
import com.icon.nexus.data.InMemoryConversationRepository
import com.icon.nexus.data.SettingsRepository
import com.icon.nexus.domain.AppStateMachine
import com.icon.nexus.settings.ModelProviderId
import com.icon.nexus.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {
    @After
    fun resetMainDispatcher() {
        runCatching { Dispatchers.resetMain() }
    }

    @Test
    fun incompleteFlagShowsOnboarding() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = onboardingViewModel(AppSettings.defaults())
        assertFalse(AppSettings.defaults().onboardingComplete)
        assertTrue(viewModel.showOnboarding.value)
        assertEquals(ONBOARDING_ROUTE, initialDestination(viewModel.showOnboarding.value))
    }

    @Test
    fun completedFlagSkipsOnboarding() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = onboardingViewModel(
            AppSettings.defaults().copy(onboardingComplete = true),
        )
        assertFalse(viewModel.showOnboarding.value)
        assertEquals(MAIN_ROUTE, initialDestination(viewModel.showOnboarding.value))
    }

    @Test
    fun microphoneDenialStillFinishes() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val stored = OnboardingSettingsRepository(AppSettings.defaults())
        val viewModel = onboardingViewModel(stored.get(), stored)
        assertTrue(canLeaveOnboarding(microphoneGranted = false))
        viewModel.finishOnboarding(microphoneGranted = false)
        assertTrue(stored.get().onboardingComplete)
        assertFalse(viewModel.showOnboarding.value)
        assertEquals(MAIN_ROUTE, initialDestination(viewModel.showOnboarding.value))
    }

    @Test
    fun geminiStoresTheKey() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val stored = OnboardingSettingsRepository(AppSettings.defaults())
        val viewModel = onboardingViewModel(stored.get(), stored)
        viewModel.setProvider(ModelProviderId.GEMINI)
        viewModel.setApiKey("gemini-key")
        viewModel.finishOnboarding(microphoneGranted = true)
        val saved = stored.get()
        assertEquals("gemini-key", saved.apiKey)
        assertEquals(ModelProviderId.GEMINI, saved.provider)
        assertFalse(saved.demoMode)
        assertTrue(saved.onboardingComplete)
        assertFalse(viewModel.showOnboarding.value)
    }

    @Test
    fun demoFinishesWithNoKey() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val stored = OnboardingSettingsRepository(AppSettings.defaults())
        val viewModel = onboardingViewModel(stored.get(), stored)
        viewModel.setProvider(ModelProviderId.DEMO)
        viewModel.finishOnboarding(microphoneGranted = true)
        val saved = stored.get()
        assertEquals("", saved.apiKey)
        assertEquals(ModelProviderId.DEMO, saved.provider)
        assertTrue(saved.demoMode)
        assertTrue(saved.onboardingComplete)
        assertFalse(viewModel.showOnboarding.value)
        assertEquals(MAIN_ROUTE, initialDestination(false))
    }

    private fun onboardingViewModel(
        initial: AppSettings,
        settings: SettingsRepository = OnboardingSettingsRepository(initial),
    ): MainViewModel {
        var nextId = 0L
        val gemini = IdleGemini()
        return MainViewModel(
            machine = AppStateMachine { ++nextId },
            speechInput = SpeechInputGate(),
            synthesizer = QueuedSpeechSynthesizer(),
            aiManager = AIManager(settings, DemoProvider(), gemini),
            settings = settings,
            initialSettings = initial,
            gemini = gemini,
            conversations = InMemoryConversationRepository(),
        )
    }
}

private class OnboardingSettingsRepository(
    initial: AppSettings,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<AppSettings> = state.asStateFlow()

    override suspend fun get(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private class IdleGemini : AIProvider {
    override val id: String = "gemini"

    override fun streamReply(request: AIRequest): Flow<AIEvent> = flow { }
}
