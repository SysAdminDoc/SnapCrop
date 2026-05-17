plugins {
    alias(libs.plugins.cyclonedx.bom) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "com.sysadmindoc"
    version = "6.19.0"
}
