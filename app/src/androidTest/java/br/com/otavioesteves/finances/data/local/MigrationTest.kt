package br.com.otavioesteves.finances.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
