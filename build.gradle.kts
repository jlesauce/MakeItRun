// Top-level build file where you can add configuration options common to all sub-projects/modules.

// Depuis AGP 9, le support de Kotlin est integre : le plugin "kotlin-android" ne doit plus etre
// applique. AGP embarque sa propre version de Kotlin (2.2.10), qu'on releve ici vers une version
// plus recente selon la procedure decrite sur https://kotl.in/gradle/agp-built-in-kotlin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
