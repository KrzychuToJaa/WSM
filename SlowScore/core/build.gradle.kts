plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
jacoco { toolVersion = "0.8.15" }
tasks.test { finalizedBy(tasks.jacocoTestReport) }
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required.set(true); html.required.set(true) }
}
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules { rule { limit { minimum = "0.65".toBigDecimal() } } }
}
tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
