package com.example.catalog

import android.content.Context
import kotlinx.serialization.json.Json

class CurrencyCatalogRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val integrityVerifier = CatalogIntegrityVerifier(context)

    @Volatile
    private var cachedCatalog: CurrencyMasterCatalog? = null

    @Volatile
    private var cachedIntegrity: IntegrityReport? = null

    fun getCatalog(): CurrencyMasterCatalog {
        cachedCatalog?.let { return it }
        val raw = context.assets.open("currency_master_catalog.json")
            .bufferedReader()
            .use { it.readText() }
        val parsed = json.decodeFromString<CurrencyMasterCatalog>(raw)
        cachedCatalog = parsed
        return parsed
    }

    fun getIntegrityReport(): IntegrityReport {
        cachedIntegrity?.let { return it }
        return integrityVerifier.verify().also { cachedIntegrity = it }
    }

    fun getManifest(): CatalogManifest? = integrityVerifier.loadManifest()

    fun findEntries(
        currency: CurrencyCode? = null,
        denomination: Int? = null,
        material: SubstrateMaterial? = null
    ): List<BanknoteCatalogEntry> {
        return getCatalog().entries.filter { entry ->
            (currency == null || entry.currency == currency) &&
                (denomination == null || entry.denominationValue == denomination) &&
                (material == null || entry.material == material)
        }
    }

    fun findById(id: String): BanknoteCatalogEntry? =
        getCatalog().entries.firstOrNull { it.id == id }

    fun findSeriesRule(letter: String): UsdSeriesYearRule? =
        getCatalog().usdSeriesYears.firstOrNull {
            it.letter.equals(letter.trim(), ignoreCase = true)
        }

    fun findFederalReserve(codeOrLetter: String): FederalReserveDistrict? {
        val clean = codeOrLetter.trim().uppercase()
        return getCatalog().usdFederalReserveDistricts.firstOrNull {
            it.letter == clean || it.code == clean
        }
    }

    fun findDenominationRule(currency: CurrencyCode, denomination: Int): DenominationSerialRule? =
        getCatalog().denominationSerialRules.firstOrNull {
            it.currency == currency && denomination in it.denominations
        }

    fun findSignatureCombination(currency: CurrencyCode, seriesOrIssue: String, denomination: Int): SignatureCombinationRule? =
        getCatalog().signatureCombinations.firstOrNull {
            it.currency == currency &&
                denomination in it.applicableDenominations &&
                (it.seriesOrIssue.equals(seriesOrIssue, ignoreCase = true) ||
                    seriesOrIssue.contains(it.seriesOrIssue, ignoreCase = true))
        }

    fun findStarNoteRule(currency: CurrencyCode): StarNoteRule? =
        getCatalog().starNoteRules.firstOrNull { it.currency == currency }

    fun findSerialExceptions(currency: CurrencyCode): List<SerialExceptionRule> =
        getCatalog().serialExceptions.filter { it.currency == currency }
}
