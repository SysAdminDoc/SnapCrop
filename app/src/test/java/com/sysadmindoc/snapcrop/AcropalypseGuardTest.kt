package com.sysadmindoc.snapcrop

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class AcropalypseGuardTest {
    private val dangerousPattern =
        Regex("""openOutputStream\([^)]*"[rw]+"[^t]""")

    @Test
    fun noNonTruncatingOverwriteInSource() {
        val srcDir = File("src/main/java")
        if (!srcDir.exists()) return
        val violations = srcDir.walkTopDown()
            .filter { it.extension == "kt" || it.extension == "java" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { idx, line ->
                    if (dangerousPattern.containsMatchIn(line))
                        "${file.name}:${idx + 1}: $line"
                    else null
                }
            }
            .toList()
        if (violations.isNotEmpty()) {
            fail(
                "openOutputStream with non-truncating write mode detected " +
                "(aCropalypse CVE-2023-21036 risk). Use insert()+default mode or \"wt\":\n" +
                violations.joinToString("\n")
            )
        }
    }
}
