import bidvector.buildlogic.CompatibilitySmokeTask
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("bidvector.kotlin-conventions")
    alias(libs.plugins.spring.boot)
}

// 1A 는 controller 도 진입점도 만들지 않는다 — Boot 플러그인은 4.x 호환 실측의 자리로만 선다.
tasks.named<BootJar>("bootJar") { enabled = false }
tasks.named<Jar>("jar") { enabled = true }

dependencies {
    implementation(project(":adapters"))
    implementation(project(":workflow"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter)
    implementation(libs.db.scheduler)
    implementation(libs.resilience4j.retry)
    implementation(libs.micrometer.core)
    implementation(libs.flyway.core)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit6)

    // 위반 fixture 전용 — 금지 가족을 실제로 컴파일해야 게이트가 그것을 잡는지 잴 수 있다.
    testImplementation(libs.jakarta.persistence)
    testImplementation(libs.jakarta.jms)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.grpc.api)
    testImplementation(libs.protobuf.java)
}

// 아키텍처 게이트는 조합 지점에서 돈다 — app 의 test runtime classpath 에 아홉 모듈이 모두 있다.
tasks.test {
    val architecturePolicy = layout.settingsDirectory.file("config/quality/architecture-policy.properties")
    inputs.file(architecturePolicy).withPropertyName("architecturePolicy")
    systemProperty("bidvector.architecture.policy", architecturePolicy.asFile.absolutePath)
    // T-D 의 정본은 도출된 후보의 **분류**다 — 같은 목록을 여기 두 벌로 두지 않는다.
    val memberEffects = layout.settingsDirectory.file("config/quality/member-effects.properties")
    inputs.file(memberEffects).withPropertyName("memberEffects")
    systemProperty("bidvector.member.effects", memberEffects.asFile.absolutePath)
}

// 해석만 재면 「호환」을 주장할 수 없다 — 그 버전의 API 로 컴파일되고 JVM 에서 로드되는지는
// 테스트가 잰다. 두 층을 한 명령으로 묶는다.
val compatibilitySmokeTest =
    tasks.register<Test>("compatibilitySmokeTest") {
        group = "verification"
        description = "채택 라이브러리의 대표 API 를 컴파일하고 JVM 에서 로드한다"
        val testSourceSet = sourceSets.getByName("test")
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        filter { includeTestsMatching("*BootCompatibilitySmokeTest") }
    }

val compatibilitySmoke =
    tasks.register<CompatibilitySmokeTask>("compatibilitySmoke") {
        group = "verification"
        description = "채택 라이브러리가 Boot 4.x BOM 아래에서 해석·컴파일·로드되는지 잰다"
        dependsOn(compatibilitySmokeTest)
        graphs.add(configurations.named("testRuntimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        expectedModules =
            setOf(
                "org.springframework.boot:spring-boot-starter",
                "com.github.kagkarlsson:db-scheduler",
                "io.github.resilience4j:resilience4j-retry",
                "io.micrometer:micrometer-core",
                "org.flywaydb:flyway-core",
                "org.testcontainers:testcontainers-postgresql",
                "com.tngtech.archunit:archunit-junit6",
                // 카탈로그가 BOM 을 이기는지 — Boot 4.1.1 BOM 은 kotlin 2.3.21 / jupiter 6.0.3 을 관리한다.
                "org.jetbrains.kotlin:kotlin-stdlib",
                "org.junit.jupiter:junit-jupiter",
            )
        report = layout.buildDirectory.file("reports/compatibility-smoke/resolved-modules.txt")
    }

tasks.named("check") {
    dependsOn(compatibilitySmoke)
}
