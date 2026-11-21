package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM inspection_vault ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM inspection_vault WHERE reportId = :reportId LIMIT 1")
    suspend fun getById(reportId: String): VaultEntity?

    @Query(
        "SELECT * FROM inspection_vault " +
            "WHERE currencyCode = :currency AND denominationValue = :denomination " +
            "AND normalizedSerial = :normalizedSerial AND normalizedSerial != '' " +
            "AND reportId != :excludeReportId " +
            "ORDER BY timestampMillis DESC LIMIT 5"
    )
    suspend fun findMatchingSerials(
        currency: String,
        denomination: Int,
        normalizedSerial: String,
        excludeReportId: String
    ): List<VaultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VaultEntity)

    @Query("DELETE FROM inspection_vault WHERE reportId = :reportId")
    suspend fun deleteById(reportId: String)

    @Query("DELETE FROM inspection_vault")
    suspend fun deleteAll()
}

@Dao
interface CatalogProfileDao {

    @Query("SELECT COUNT(*) FROM catalog_profiles WHERE catalogVersion = :version")
    suspend fun countForVersion(version: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<CatalogProfileEntity>)

    @Query("SELECT * FROM catalog_profiles ORDER BY currencyCode, denominationValue")
    suspend fun getAll(): List<CatalogProfileEntity>
}
