package com.example

import android.content.Context
import com.example.data.local.CircadiaDatabase
import com.example.data.local.InMemoryCircadiaDao
import com.example.data.local.SwitchableCircadiaDao
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.CloudDatabaseSyncService
import com.example.data.remote.GeminiImpactService
import com.example.data.remote.GoogleFitRestService
import com.example.data.remote.NvidiaNimService
import com.example.data.repository.DatabasePersistenceRepository
import com.example.data.repository.HealthSyncRepository
import com.example.data.repository.MedicationCatalogRepository
import com.example.data.repository.SleepImpactAiRepository
import com.example.data.repository.SubstanceRepository

class CircadiaAppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database by lazy { CircadiaDatabase.getInstance(appContext) }
    private val roomDao by lazy { database.circadiaDao() }
    private val memoryDao by lazy { InMemoryCircadiaDao() }
    val prefsRepository by lazy { UserPreferencesRepository(appContext) }
    private val cloudSyncService by lazy { CloudDatabaseSyncService(appContext, prefsRepository) }

    private val switchableDao by lazy {
        SwitchableCircadiaDao(
            inMemoryDao = memoryDao,
            roomDao = roomDao,
            prefsRepo = prefsRepository,
            cloudSyncService = cloudSyncService
        )
    }

    val persistenceRepository by lazy {
        DatabasePersistenceRepository(
            inMemoryDao = memoryDao,
            roomDao = roomDao,
            prefsRepo = prefsRepository,
            cloudSyncService = cloudSyncService
        )
    }

    val substanceRepository by lazy { SubstanceRepository(switchableDao) }
    val medicationCatalogRepository by lazy { MedicationCatalogRepository(appContext) }
    val aiRepository by lazy {
        SleepImpactAiRepository(
            dao = switchableDao,
            prefsRepo = prefsRepository,
            nvidiaService = NvidiaNimService(),
            geminiService = GeminiImpactService(appContext)
        )
    }
    val healthSyncRepository by lazy {
        HealthSyncRepository(
            dao = switchableDao,
            prefsRepo = prefsRepository,
            googleFitService = GoogleFitRestService()
        )
    }
}
