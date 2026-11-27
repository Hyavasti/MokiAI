package com.example.moki

import android.app.Application
import com.example.moki.actions.*
import com.example.moki.brain.*
import com.example.moki.data.local.MokiDatabase
import com.example.moki.data.preferences.MokiSettingsRepository
import com.example.moki.skills.MokiSkillRegistry

class MokiApplication : Application() {

    lateinit var database: MokiDatabase
        private set
    lateinit var settingsRepository: MokiSettingsRepository
        private set
    lateinit var skillRegistry: MokiSkillRegistry
        private set
    lateinit var geminiNanoEngine: GeminiNanoEngine
        private set
    lateinit var liteRtLmEngine: LiteRtLmEngine
        private set
    lateinit var cloudEscalationEngine: CloudEscalationEngine
        private set
    lateinit var brainRouter: MokiBrainRouter
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = MokiDatabase.getInstance(this)
        settingsRepository = MokiSettingsRepository(this)
        skillRegistry = MokiSkillRegistry()
        geminiNanoEngine = GeminiNanoEngine()
        liteRtLmEngine = LiteRtLmEngine(this)
        cloudEscalationEngine = CloudEscalationEngine(this, settingsRepository)

        registerDefaultSkills()

        brainRouter = MokiBrainRouter(
            context = this,
            skillRegistry = skillRegistry,
            deterministicMatcher = DeterministicMatcher(),
            geminiNanoEngine = geminiNanoEngine,
            liteRtLmEngine = liteRtLmEngine,
            cloudEscalationEngine = cloudEscalationEngine,
            chatMessageDao = database.chatMessageDao(),
            traceLogDao = database.traceLogDao(),
            settingsRepository = settingsRepository
        )
    }

    private fun registerDefaultSkills() {
        skillRegistry.register(GreetingAction())
        skillRegistry.register(CalculatorAction())
        skillRegistry.register(FlashlightAction(this))
        skillRegistry.register(AppLauncherAction(this))
        skillRegistry.register(FileManagerAction(this))
        skillRegistry.register(NotesAction(this, database.noteDao()))
        skillRegistry.register(AlarmAction(this))
        skillRegistry.register(SmsAction(this))
        skillRegistry.register(MessengerAction(this))
        skillRegistry.register(EmailAction(this))
        skillRegistry.register(PhoneCallAction(this))
        skillRegistry.register(ScreenLockAction(this))
        skillRegistry.register(GoogleMapsAction(this))
        skillRegistry.register(WeatherAction(this))
        skillRegistry.register(DeviceStatusAction(this))
        skillRegistry.register(
            ScreenSummaryAction(
                geminiNanoEngine = geminiNanoEngine,
                liteRtLmEngine = liteRtLmEngine,
                cloudEscalationEngine = cloudEscalationEngine,
                settingsRepository = settingsRepository
            )
        )
        skillRegistry.register(
            WebSearchAction(
                liteRtLmEngine = liteRtLmEngine,
                cloudEngine = cloudEscalationEngine
            )
        )
    }

    companion object {
        lateinit var instance: MokiApplication
            private set
    }
}