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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        StatementImportEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

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

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finances_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.categoryDao())
                }
            }
        }

        suspend fun populateDatabase(categoryDao: CategoryDao) {
            if (categoryDao.getCategoriesCount() == 0) {
                val defaultCategories = listOf(
                    CategoryEntity(name = "Alimentação", type = CategoryType.EXPENSE),
                    CategoryEntity(name = "Transporte", type = CategoryType.EXPENSE),
                    CategoryEntity(name = "Moradia", type = CategoryType.EXPENSE),
                    CategoryEntity(name = "Lazer", type = CategoryType.EXPENSE),
                    CategoryEntity(name = "Saúde", type = CategoryType.EXPENSE),
                    CategoryEntity(name = "Salário", type = CategoryType.INCOME),
                    CategoryEntity(name = "Investimentos", type = CategoryType.INCOME),
                    CategoryEntity(name = "Outros", type = CategoryType.EXPENSE)
                )
                categoryDao.insertCategories(defaultCategories)
            }
        }
    }
}
