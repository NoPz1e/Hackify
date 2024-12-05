// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

// Se precisar incluir dependências para o próprio Gradle (buildscript):
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.1") // Certifique-se de usar a sintaxe Kotlin
    }
}
