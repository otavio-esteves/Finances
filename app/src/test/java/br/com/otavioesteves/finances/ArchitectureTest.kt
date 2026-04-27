package br.com.otavioesteves.finances

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureTest {

    private val basePackagePath = "src/main/java/br/com/otavioesteves/finances"
    
    private val rootDir: File by lazy {
        var current: File = File(".").absoluteFile
        while (current.parentFile != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile!!
        }
        current
    }

    private val appDir: File by lazy {
        File(rootDir, "app")
    }

    @Test
    fun domain_shouldNotDependOnData() {
        checkImports("$basePackagePath/domain") { import ->
            assertFalse("Domain should not depend on Data: $import", import.contains(".data."))
        }
    }

    @Test
    fun domain_shouldNotDependOnUiOrPresentation() {
        checkImports("$basePackagePath/domain") { import ->
            assertFalse("Domain should not depend on UI: $import", import.contains(".ui."))
            assertFalse("Domain should not depend on Presentation: $import", import.contains(".presentation."))
        }
    }

    @Test
    fun domain_shouldNotDependOnAndroidSdk() {
        checkImports("$basePackagePath/domain") { import ->
            val isAndroidImport = import.startsWith("android.") || import.startsWith("androidx.")
            // Allow some basic annotations if necessary, but generally avoid SDK
            assertFalse("Domain should not depend on Android SDK: $import", isAndroidImport)
        }
    }

    @Test
    fun viewModels_shouldNotInstantiateRepositoryImplDirectly() {
        val viewModelDir = File(appDir, "$basePackagePath/presentation")
        if (!viewModelDir.exists()) return

        viewModelDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name.endsWith("ViewModel.kt") }
            .forEach { file ->
                val content = file.readText()
                assertFalse(
                    "ViewModel ${file.name} should not instantiate RepositoryImpl directly",
                    content.contains("RepositoryImpl(") || 
                    (content.contains("InMemory") && content.contains("Repository("))
                )
            }
    }

    @Test
    fun money_shouldNotUseDoubleOrFloat() {
        val moneyFile = File(appDir, "$basePackagePath/domain/model/Money.kt")
        if (!moneyFile.exists()) return
        
        val content = moneyFile.readText()
        assertFalse("Money should not use Double", content.contains("Double"))
        assertFalse("Money should not use Float", content.contains("Float"))
    }

    @Test
    fun project_shouldNotUseWrongPackage() {
        val srcDir = File(appDir, "src")
        if (!srcDir.exists()) return
        
        srcDir.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filter { it.name != "ArchitectureTest.kt" } // Exclude this test
            .forEach { file ->
                val content = file.readText()
                assertFalse(
                    "File ${file.absolutePath} contains wrong package reference 'com.example.finances'",
                    content.contains("com.example.finances")
                )
            }
    }

    private fun checkImports(relativeContextPath: String, validator: (String) -> Unit) {
        val dir = File(appDir, relativeContextPath)
        if (!dir.exists()) return
        
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEach { line ->
                    if (line.trim().startsWith("import ")) {
                        val import = line.trim().substringAfter("import ").substringBefore(" ")
                            .substringBefore(";")
                        validator(import)
                    }
                }
            }
    }
}
