plugins {
    jacoco
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

jacoco { toolVersion = "0.8.15" }
val coverageClasses = files(
    fileTree("core/build/classes/kotlin/main"),
    fileTree("app/build/tmp/kotlin-classes/debug") {
        exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*")
    },
)
val coverageData = files("core/build/jacoco/test.exec", "app/build/jacoco/testDebugUnitTest.exec")
tasks.register<JacocoReport>("coverageReport") {
    dependsOn(":core:test", ":app:testDebugUnitTest")
    classDirectories.setFrom(coverageClasses)
    sourceDirectories.setFrom(files("core/src/main/kotlin", "app/src/main/java"))
    executionData.setFrom(coverageData)
    reports { xml.required.set(true); html.required.set(true) }
}
tasks.register<JacocoCoverageVerification>("coverageCheck") {
    dependsOn("coverageReport")
    classDirectories.setFrom(coverageClasses)
    executionData.setFrom(coverageData)
    violationRules { rule { limit { counter = "LINE"; minimum = "0.65".toBigDecimal() } } }
}
