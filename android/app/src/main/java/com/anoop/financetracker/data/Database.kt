package com.anoop.financetracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, name")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: String): AccountEntity?

    @Upsert suspend fun upsert(a: AccountEntity)
    @Upsert suspend fun upsertAll(a: List<AccountEntity>)
    @Delete suspend fun delete(a: AccountEntity)
    @Query("DELETE FROM accounts") suspend fun clear()
}

@Dao
interface TxDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC, createdAt DESC")
    fun observeAll(): Flow<List<TxEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC, createdAt DESC")
    suspend fun all(): List<TxEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TxEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :acct OR toAccountId = :acct")
    suspend fun countForAccount(acct: String): Int

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Insert that silently keeps the existing row: used for idempotent automatic posting. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(t: TxEntity): Long

    @Upsert suspend fun upsert(t: TxEntity)
    @Upsert suspend fun upsertAll(t: List<TxEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM transactions") suspend fun clear()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets") fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun all(): List<BudgetEntity>
    @Upsert suspend fun upsert(b: BudgetEntity)
    @Upsert suspend fun upsertAll(b: List<BudgetEntity>)
    @Query("DELETE FROM budgets WHERE category = :cat") suspend fun delete(cat: String)
    @Query("DELETE FROM budgets") suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY name") fun observeAll(): Flow<List<GoalEntity>>
    @Query("SELECT * FROM goals ORDER BY name") suspend fun all(): List<GoalEntity>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun byId(id: String): GoalEntity?
    @Upsert suspend fun upsert(g: GoalEntity)
    @Upsert suspend fun upsertAll(g: List<GoalEntity>)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM goals") suspend fun clear()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY done, due") fun observeAll(): Flow<List<BillEntity>>
    @Query("SELECT * FROM bills ORDER BY done, due") suspend fun all(): List<BillEntity>
    @Query("SELECT * FROM bills WHERE id = :id") suspend fun byId(id: String): BillEntity?
    @Upsert suspend fun upsert(b: BillEntity)
    @Upsert suspend fun upsertAll(b: List<BillEntity>)
    @Query("DELETE FROM bills WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM bills") suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY nextDate") fun observeAll(): Flow<List<RecurringEntity>>
    @Query("SELECT * FROM recurring ORDER BY nextDate") suspend fun all(): List<RecurringEntity>
    @Query("SELECT * FROM recurring WHERE id = :id") suspend fun byId(id: String): RecurringEntity?
    @Upsert suspend fun upsert(r: RecurringEntity)
    @Upsert suspend fun upsertAll(r: List<RecurringEntity>)
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM recurring") suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, name") fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories ORDER BY kind, name") suspend fun all(): List<CategoryEntity>
    @Upsert suspend fun upsert(c: CategoryEntity)
    @Upsert suspend fun upsertAll(c: List<CategoryEntity>)
    @Query("DELETE FROM categories WHERE `key` = :key") suspend fun delete(key: String)
    @Query("DELETE FROM categories") suspend fun clear()
}

@Database(
    entities = [AccountEntity::class, TxEntity::class, BudgetEntity::class, GoalEntity::class,
        BillEntity::class, RecurringEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun tx(): TxDao
    abstract fun budgets(): BudgetDao
    abstract fun goals(): GoalDao
    abstract fun bills(): BillDao
    abstract fun recurring(): RecurringDao
    abstract fun categories(): CategoryDao

    companion object {
        const val NAME = "finance.db"

        /** Encrypted on-device database (SQLCipher, key protected by Android Keystore). */
        fun encrypted(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase))
                .addMigrations(*Migrations.ALL)
                .build()
        }

        /** Plain database for JVM tests (SQLCipher's native library is not available there). */
        fun plain(context: Context, name: String?): AppDatabase {
            val b = if (name == null) Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            else Room.databaseBuilder(context, AppDatabase::class.java, name)
            return b.allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
        }
    }
}

/** Schema migrations. Version 1 is the first release; add new Migration objects here when the schema changes. */
object Migrations {
    val ALL: Array<androidx.room.migration.Migration> = arrayOf()
}
