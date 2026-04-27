package br.com.otavioesteves.finances.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.otavioesteves.finances.data.local.dao.CategoryDao
import br.com.otavioesteves.finances.data.local.dao.TransactionDao
import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [CategoryEntity::class, TransactionEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finances_database"
                )
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
