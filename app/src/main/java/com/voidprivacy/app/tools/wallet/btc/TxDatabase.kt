package com.voidprivacy.app.tools.wallet.btc

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "transactions")
data class TxEntity(
    @PrimaryKey val txid: String,
    val amount: Long,        // satoshis (positive = incoming, negative = outgoing)
    val address: String,
    val timestamp: Long,     // unix millis
    val direction: String,   // "in" or "out"
    val confirmations: Int,
)

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecent(): List<TxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<TxEntity>)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Database(entities = [TxEntity::class], version = 1, exportSchema = false)
abstract class TxDatabase : RoomDatabase() {
    abstract fun txDao(): TxDao

    companion object {
        @Volatile private var INSTANCE: TxDatabase? = null

        fun get(context: Context): TxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val passphrase = com.voidprivacy.app.DbKeyManager.getPassphrase(context)
                    val factory = SupportOpenHelperFactory(passphrase)
                    Room.databaseBuilder(
                        context.applicationContext,
                        TxDatabase::class.java,
                        "void_tx_history"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration()
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
