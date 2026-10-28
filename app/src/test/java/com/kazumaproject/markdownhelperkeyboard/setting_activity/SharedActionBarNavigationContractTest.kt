package com.kazumaproject.markdownhelperkeyboard.setting_activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SharedActionBarNavigationContractTest {

    @Test
    fun fragmentsNeverClearTheSharedActionBarTitleAfterNavigation() {
        val sourceRoot = projectFile("app/src/main/java", "src/main/java")
        val blankSharedTitleAssignments = listOf(
            Regex("""supportActionBar\?\.title\s*=\s*(?:""|null)"""),
            Regex("""requireActivity\(\)\.title\s*=\s*(?:""|null)"""),
            Regex(
                """supportActionBar\?\.apply\s*\{[\s\S]{0,300}?title\s*=\s*(?:""|null)""",
            ),
        )
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val source = file.readText()
                blankSharedTitleAssignments.any { it.containsMatchIn(source) }
            }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Fragments must leave destination titles to NavigationUI: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun sharedNavigationDestinationsNeverDeclareAnEmptyLabel() {
        val navigation = projectFile(
            "app/src/main/res/navigation/mobile_navigation.xml",
            "src/main/res/navigation/mobile_navigation.xml",
        ).readText()

        assertFalse(
            "Shared ActionBar destinations need a non-empty NavigationUI title",
            navigation.contains("android:label=\"\""),
        )
    }

    @Test
    fun customToolbarFragmentsDoNotToggleTheSharedActionBar() {
        listOf(
            "ui/candidate_view_height_setting/CandidateViewHeightSettingFragment.kt",
            "ui/candidate_view_height_setting/CandidateHeightDefaultsFragment.kt",
        ).forEach { relativePath ->
            val source = mainFile(relativePath).readText()

            assertFalse(
                "$relativePath must leave shared ActionBar visibility to MainActivity",
                source.contains("supportActionBar?.hide()") ||
                    source.contains("supportActionBar?.show()"),
            )
        }
    }

    private fun mainFile(relativePath: String): File {
        val sourcePath = "java/com/kazumaproject/markdownhelperkeyboard/setting_activity/$relativePath"
        return projectFile("app/src/main/$sourcePath", "src/main/$sourcePath")
    }

    private fun projectFile(vararg candidates: String): File {
        return candidates.asSequence()
            .map(::File)
            .firstOrNull(File::exists)
            ?: error("Unable to locate any of ${candidates.toList()}")
    }
}
