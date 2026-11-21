package com.example.catalog

import android.content.Context
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class CatalogIntegrityVerifier(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun loadManifest(): CatalogManifest? = runCatching {
        val raw = context.assets.open("catalog_manifest.json").bufferedReader().use { it.readText() }
        json.decodeFromString<CatalogManifest>(raw)
    }.getOrNull()

    fun verify(): IntegrityReport {
        val manifest = loadManifest()
            ?: return IntegrityReport(
                allVerified = false,
                catalogVersion = "UNKNOWN",
                verifiedAssets = listOf("catalog_manifest.json" to false)
            )

        val results = manifest.assets.map { asset ->
            val ok = runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                context.assets.open(asset.assetPath).use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                hex.equals(asset.sha256, ignoreCase = true)
            }.getOrDefault(false)
            asset.assetPath to ok
        }

        return IntegrityReport(
            allVerified = results.isNotEmpty() && results.all { it.second },
            catalogVersion = manifest.catalogVersion,
            verifiedAssets = results
        )
    }
}
