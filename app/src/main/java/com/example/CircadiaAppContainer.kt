package com.example

import android.content.Context
import com.example.data.local.CircadiaDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.GeminiImpactService
import com.example.data.remote.GoogleFitRestService
import com.example.data.remote.NvidiaNimService
import com.example.data.repository.HealthSyncRepository
import com.example.data.repository.SleepImpactAiRepository
import com.example.data.repository.SubstanceRepository

class CircadiaAppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database by lazy { CircadiaDatabase.getInstance(appContext) }
    private val dao by lazy { database.circadiaDao() }

    val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    val substanceRepository by lazy { SubstanceRepository(dao) }
    val aiRepository by lazy {
        SleepImpactAiRepository(
            dao = dao,
            prefsRepo = prefsRepository,
            nvidiaService = NvidiaNimService(),
            geminiService = GeminiImpactService(appContext)
        )
    }
    val healthSyncRepository by lazy {
        HealthSyncRepository(
            dao = dao,
            prefsRepo = prefsRepository,
            googleFitService = GoogleFitRestService()
        )
    }
}
