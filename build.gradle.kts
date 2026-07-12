plugins {
    alias(libs.plugins.cyclonedx.bom) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("verifyWrapperJar") {
    group = "verification"
    description = "Verifies the Gradle 9.4.1 wrapper JAR against Gradle's published SHA-256."
    doLast {
        val wrapperJar = layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.jar").asFile
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val actual = wrapperJar.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val expected = "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c"
        check(actual == expected) { "Gradle wrapper JAR checksum mismatch: $actual" }
    }
}

allprojects {
    group = "com.sysadmindoc"
    version = "6.33.0"
}
