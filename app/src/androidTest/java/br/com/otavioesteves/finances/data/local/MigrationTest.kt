package br.com.otavioesteves.finances.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrate9To10SeedsTheMagnitudeBudget() {
        helper.createDatabase(testDbName, 9).apply {
            execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Outros', 'EXPENSE')")
            execSQL("INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes, origin) VALUES (1, 'Compra', 1234, 1, '2026-11-01', 'EXPENSE', NULL, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate(testDbName, 10, true, AppDatabase.MIGRATION_9_10)
        db.query("SELECT amountCents FROM transaction_total WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(1234L, it.getLong(0))
        }
    }

    @Test
    fun migrate8To9PreservesDataAndLeavesUnknownInputKeysNull() {
        helper.createDatabase(testDbName, 8).apply {
            execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Outros', 'EXPENSE')")
            execSQL("INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes, origin) VALUES (1, 'Mercado', 1000, 1, '2026-11-01', 'EXPENSE', NULL, 'IMPORTED')")
            execSQL("INSERT INTO category_memories (descriptionKey, inputType, categoryId) VALUES ('mercado', 'EXPENSE', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(testDbName, 9, true, AppDatabase.MIGRATION_8_9)
        db.query("SELECT amountCents, categoryMemoryDescriptionKey, categoryMemoryInputType FROM transactions WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(1000L, it.getLong(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
        db.query("SELECT categoryId FROM category_memories WHERE descriptionKey = 'mercado'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(0))
        }
    }

    @Test
    fun migrate1Through9PreservesTransactionsAndValidatesTheCompleteSchema() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Outros', 'EXPENSE')")
            execSQL("INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes) VALUES (1, 'Original', 1234, 1, '2026-01-01', 'EXPENSE', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(testDbName, 9, true,
            AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
        db.query("SELECT description, amountCents, origin FROM transactions WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Original", it.getString(0))
            assertEquals(1234L, it.getLong(1))
            assertEquals("MANUAL", it.getString(2))
        }
    }

    @Test
    fun migrate7To8KeepsOldTransactionsUnlinkedAndCreatesMemory() {
        helper.createDatabase(testDbName, 7).apply {
            execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Outros', 'EXPENSE')")
            execSQL("INSERT INTO statement_imports (id, fileName, importedAt, transactionCount) VALUES (1, 'antigo.csv', '2026-11-01T09:00:00', 1)")
            execSQL("INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes, origin) VALUES (1, 'Mercado', 1000, 1, '2026-11-01', 'EXPENSE', NULL, 'IMPORTED')")
            close()
        }
        val db = helper.runMigrationsAndValidate(testDbName, 8, true, AppDatabase.MIGRATION_7_8)
        db.query("SELECT importId, sourceLabel, amountCents FROM transactions WHERE id = 1").use {
            assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertTrue(it.isNull(1)); assertEquals(1000L, it.getLong(2))
        }
        db.query("SELECT canUndo FROM statement_imports WHERE id = 1").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        db.execSQL("INSERT INTO category_memories (descriptionKey, inputType, categoryId) VALUES ('mercado', 'EXPENSE', 1)")
    }

    private val testDbName = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun create_database_version_1_is_successful() {
        // Create the database in version 1
        val db = helper.createDatabase(testDbName, 1)

        // The database is successfully created. We can close it now.
        db.close()

        // This test ensures that the schema exported for version 1 is valid
        // and that the MigrationTestHelper can successfully read it and
        // instantiate the SQLite database based on it.
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To2_addsOriginColumnAndNewTables() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes) " +
                    "VALUES (1, 'Mercado', 5000, 1, '2026-01-10', 'EXPENSE', NULL)"
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        val cursor = migratedDb.query("SELECT origin FROM transactions WHERE id = 1")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("MANUAL", it.getString(it.getColumnIndexOrThrow("origin")))
        }

        // New tables must exist and accept inserts under the migrated schema.
        migratedDb.execSQL(
            "INSERT INTO statement_imports (id, fileName, importedAt, transactionCount) " +
                "VALUES (1, 'extrato.csv', '2026-01-15T10:00:00', 3)"
        )
        migratedDb.execSQL(
            "INSERT INTO chat_messages (id, role, content, createdAt) " +
                "VALUES (1, 'USER', 'Quanto gastei em janeiro?', '2026-01-15T10:00:00')"
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_preservesOldImportsAndAddsUniqueFingerprint() {
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                "INSERT INTO statement_imports (id, fileName, importedAt, transactionCount) " +
                    "VALUES (1, 'antigo.csv', '2026-01-15T10:00:00', 3)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, AppDatabase.MIGRATION_2_3)
        db.query("SELECT fingerprint FROM statement_imports WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0))
        }
        db.execSQL(
            "INSERT INTO statement_imports (fileName, importedAt, transactionCount, fingerprint) " +
                "VALUES ('novo.csv', '2026-01-16T10:00:00', 1, 'abc')"
        )
        assertTrue(runCatching {
            db.execSQL(
                "INSERT INTO statement_imports (fileName, importedAt, transactionCount, fingerprint) " +
                    "VALUES ('repetido.csv', '2026-01-17T10:00:00', 1, 'abc')"
            )
        }.isFailure)
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_addsDateOrderIndexWithoutLosingTransactions() {
        helper.createDatabase(testDbName, 3).apply {
            execSQL(
                "INSERT INTO categories (id, name, type) VALUES (1, 'Alimentação', 'EXPENSE')"
            )
            execSQL(
                "INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes, origin) " +
                    "VALUES (1, 'Mercado', 5000, 1, '2026-01-10', 'EXPENSE', NULL, 'IMPORTED')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 4, true, AppDatabase.MIGRATION_3_4)
        db.query("SELECT description FROM transactions WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Mercado", it.getString(0))
        }
        db.query("PRAGMA index_list('transactions')").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "index_transactions_date_id") found = true
            }
            assertTrue(found)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate4To5_addsChatOrderIndexWithoutLosingMessages() {
        helper.createDatabase(testDbName, 4).apply {
            execSQL(
                "INSERT INTO chat_messages (id, role, content, createdAt) " +
                    "VALUES (1, 'USER', 'Pergunta', '2026-01-15T10:00:00')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 5, true, AppDatabase.MIGRATION_4_5)
        db.query("SELECT content FROM chat_messages WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Pergunta", it.getString(0))
        }
        db.query("PRAGMA index_list('chat_messages')").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "index_chat_messages_createdAt_id") found = true
            }
            assertTrue(found)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate5To6_addsTransferCategoryWithoutDuplicatingExistingOne() {
        helper.createDatabase(testDbName, 5).apply {
            execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Alimentação', 'EXPENSE')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 6, true, AppDatabase.MIGRATION_5_6)
        db.query("SELECT COUNT(*) FROM categories WHERE type = 'TRANSFER'").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT name FROM categories WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Alimentação", it.getString(0))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate6To7_preservesTransactionsAndAddsOptionalIcon() {
        helper.createDatabase(testDbName, 6).apply {
            execSQL("INSERT INTO categories (id, name, type) VALUES (1, 'Alimentação', 'EXPENSE')")
            execSQL(
                "INSERT INTO transactions (id, description, amountCents, categoryId, date, type, notes, origin) " +
                    "VALUES (1, 'Mercado', 5000, 1, '2026-01-10', 'EXPENSE', NULL, 'IMPORTED')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 7, true, AppDatabase.MIGRATION_6_7)
        db.query("SELECT description, iconKey FROM transactions WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Mercado", it.getString(0))
            assertTrue(it.isNull(1))
        }
        db.execSQL("UPDATE transactions SET iconKey = 'market' WHERE id = 1")
        db.query("SELECT iconKey FROM transactions WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("market", it.getString(0))
        }
    }
}
