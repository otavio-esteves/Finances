package br.com.otavioesteves.finances.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.otavioesteves.finances.data.local.dao.CategoryDao
import br.com.otavioesteves.finances.data.local.dao.ChatMessageDao
import br.com.otavioesteves.finances.data.local.dao.StatementImportDao
import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicLong

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        StatementImportEntity::class,
        ChatMessageEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    private val datasetRevision = AtomicLong()

    val dataRevision: Long get() = datasetRevision.get()

    /** Called inside the restoring transaction so guarded chat writes serialize with the replacement. */
    internal fun markDataRestored() { datasetRevision.incrementAndGet() }

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun statementImportDao(): StatementImportDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS statement_imports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fileName TEXT NOT NULL,
                        importedAt TEXT NOT NULL,
                        transactionCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE statement_imports ADD COLUMN fingerprint TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_statement_imports_fingerprint " +
                        "ON statement_imports (fingerprint)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_date_id ON transactions (date, id)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_chat_messages_createdAt_id " +
                        "ON chat_messages (createdAt, id)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO categories (name, type) " +
                        "SELECT 'Transferência', 'TRANSFER' WHERE NOT EXISTS " +
                        "(SELECT 1 FROM categories WHERE type = 'TRANSFER')"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN iconKey TEXT")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finances_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(DefaultCategoriesCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    /** Runs on Room's opening thread, before the first DAO query can observe the database. */
    internal class DefaultCategoriesCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DEFAULT_CATEGORIES.forEach { category ->
                db.execSQL("INSERT INTO categories (name, type) VALUES (?, ?)", arrayOf(category.name, category.type.name))
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            // Repair databases restored by older app versions without usable category types.
            db.beginTransaction()
            try {
                ESSENTIAL_CATEGORIES.forEach { category ->
                    db.execSQL(
                        "INSERT INTO categories (name, type) SELECT ?, ? WHERE NOT EXISTS " +
                            "(SELECT 1 FROM categories WHERE type = ?)",
                        arrayOf(category.name, category.type.name, category.type.name)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}

internal val DEFAULT_CATEGORIES = listOf(
    CategoryEntity(name = "Alimentação", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Transporte", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Moradia", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Lazer", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Saúde", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Salário", type = CategoryType.INCOME),
    CategoryEntity(name = "Investimentos", type = CategoryType.INCOME),
    CategoryEntity(name = "Outros", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Transferência", type = CategoryType.TRANSFER)
)

internal val ESSENTIAL_CATEGORIES = listOf(
    CategoryEntity(name = "Outros", type = CategoryType.EXPENSE),
    CategoryEntity(name = "Salário", type = CategoryType.INCOME),
    CategoryEntity(name = "Transferência", type = CategoryType.TRANSFER)
)
