package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sentinelDataStore by preferencesDataStore(name = "sentinel_prefs")

class LanguagePreferences(private val context: Context) {

    private val langKey = stringPreferencesKey("app_language")
    private val archiveConsentKey = booleanPreferencesKey("evidence_archive_consent")

    val languageFlow: Flow<AppLanguage> = context.sentinelDataStore.data.map { prefs ->
        val raw = prefs[langKey]
        if (raw == AppLanguage.EN.code) AppLanguage.EN else AppLanguage.AR
    }

    val evidenceConsentFlow: Flow<Boolean> = context.sentinelDataStore.data.map { prefs ->
        prefs[archiveConsentKey] ?: false
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.sentinelDataStore.edit { prefs ->
            prefs[langKey] = language.code
        }
    }

    suspend fun setEvidenceConsent(enabled: Boolean) {
        context.sentinelDataStore.edit { prefs ->
            prefs[archiveConsentKey] = enabled
        }
    }
}
