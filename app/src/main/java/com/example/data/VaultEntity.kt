package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspection_vault",
    indices = [
        Index(value = ["currencyCode", "denominationValue", "normalizedSerial"]),
        Index(value = ["timestampMillis"])
    ]
)
data class VaultEntity(
    @PrimaryKey val reportId: String,
    val timestampMillis: Long,
    val currencyCode: String,
    val denominationValue: Int,
    val normalizedSerial: String,
    val perceptualDHash: String,
    val conditionSignature: String,
    val duplicateClassificationCode: String,
    val verdictCode: String,
    val anomalyCount: Int,
    val isDuplicateFlagged: Boolean,
    val usableCoveragePercent: Int,
    val confidencePercent: Int,
    val archivedFrontPath: String?,
    val archivedBackPath: String?,
    val reportJson: String
)

@Entity(tableName = "catalog_profiles")
data class CatalogProfileEntity(
    @PrimaryKey val entryId: String,
    val currencyCode: String,
    val denominationValue: Int,
    val issueYear: String,
    val material: String,
    val provenanceTier: String,
    val catalogVersion: String
)
