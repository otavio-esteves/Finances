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
}
