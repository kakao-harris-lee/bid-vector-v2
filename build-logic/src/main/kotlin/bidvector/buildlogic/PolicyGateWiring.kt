package bidvector.buildlogic

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * **정책 데이터만 먹는 게이트들의 배선** — 입력이 (정책 파일, 모듈 이름, 관례 트리) 뿐인 축을
 * `bidvector.kotlin-conventions` 에서 이 자리로 옮겼다.
 *
 * 왜 옮겼나: 그 convention plugin 이 543 줄로 **파일 500 줄 한도를 넘어** `buildLogicSizeGate` 가 PR CI
 * 에서 붉었다(verifier B-1). 한도를 올리는 것은 게이트를 끄는 것과 같으므로 나눴다. 자른 선은 크기가
 * 아니라 **의존 모양**이다 — 여기 있는 축은 source set·아카이브·해석된 configuration 을 쓰지 않아
 * 호출 순서에 기대지 않는다(`sourceLanguageGate`·`environmentAccessGate` 만 source set 컨테이너를
 * 쓰고, 그것도 provider 안에서 지연으로 읽는다).
 *
 * 왜 새 plugin id 가 아니라 함수인가: `conventionCoverageGate` 는 **`qualityBaseline` 등록 집합**으로
 * 「게이트가 적용된 모듈」을 센다. plugin 을 하나 더 만들면 그 plugin 만 빠진 모듈을 세는 축이 없어
 * 「게이트의 부재는 조용하다」가 한 겹 더 생긴다. 호출 한 줄이면 부재가 그 파일의 diff 에 보인다.
 */
internal fun Project.registerPolicyGates(configDir: Directory): List<TaskProvider<out Task>> {
    val architecturePolicy = configDir.file("quality/architecture-policy.properties")
    return listOf(
        registerSourceLanguageGate(),
        registerUnusedImportGate(architecturePolicy),
        registerDomainSourceReferenceGate(architecturePolicy),
        registerEnvironmentAccessGate(architecturePolicy),
        registerDomainApiTypeGate(architecturePolicy, configDir),
        registerMigrationOrderGate(configDir),
    )
}

/** 소스 트리에 Kotlin 아닌 소스가 있으면 실패한다 — `java.setSrcDirs` 봉쇄의 2차 그물. */
private fun Project.registerSourceLanguageGate(): TaskProvider<SourceLanguageGateTask> =
    tasks.register<SourceLanguageGateTask>("sourceLanguageGate") {
        description = "소스 트리에 Kotlin 아닌 소스가 있으면 실패한다 — java.setSrcDirs 봉쇄의 2차 그물"
        sourceDirectories.from(layout.projectDirectory.dir("src"))
        excludedDirectories.from(provider { sourceSets().flatMap { it.resources.srcDirs } })
        // `md` 는 소스가 아니라 그 트리를 설명하는 문서다 — 컴파일되지 않으므로 class output 을 만들 수
        // 없고, 이 게이트가 막으려는 것(게이트를 비껴가는 산출물)에 해당하지 않는다.
        allowedExtensions = setOf("kt", "kts", "md")
        report = layout.buildDirectory.file("reports/source-language-gate/offenders.txt")
    }

/**
 * 쓰이지 않는 import(`OPEN-KTLINT-UNUSED-IMPORTS-SILENT`) — ktlint 의 `no-unused-imports` 가 켜져 있는데도
 * 조용했던 축이다(실측, 운영자 등재 결정 2026-09-26). 도구가 조용한 축은 도구로 덮지 않는다.
 * `sourceLanguageGate` 와 같은 이유로 **관례 트리를 직접** 걸고 source set 에서 파생시키지 않는다.
 */
private fun Project.registerUnusedImportGate(policy: RegularFile): TaskProvider<UnusedImportGateTask> =
    tasks.register<UnusedImportGateTask>("unusedImportGate") {
        description = "import 가 들여온 이름이 그 파일 본문에서 쓰이는지 잰다 — ktlint 가 조용한 축"
        policyFile = policy
        sources.from(layout.projectDirectory.dir("src"), layout.projectDirectory.file("build.gradle.kts"))
        report = layout.buildDirectory.file("reports/unused-import/violations.txt")
    }

/**
 * 바이트코드 층(ArchUnit)과 의존 그래프 층이 함께 놓치는 자리 — 컴파일 시 인라인되는 상수와 class
 * literal 은 domain 소스가 **이름으로** 부르지만 산출물에 타입 참조를 남기지 않는다.
 */
private fun Project.registerDomainSourceReferenceGate(
    policy: RegularFile,
): TaskProvider<DomainSourceReferenceGateTask> =
    tasks.register<DomainSourceReferenceGateTask>("domainSourceReferenceGate") {
        description = "domain main 소스가 허용 목록 밖 좌표를 이름으로 부르는지 잰다"
        policyFile = policy
        moduleName = project.name
        sourceRoot.from(layout.projectDirectory.dir("src/main/kotlin"))
        report = layout.buildDirectory.file("reports/domain-source-reference/references.txt")
    }

/**
 * **실행 환경 스니핑 0 의 정본**(OPS-13) — 산출물의 멤버 참조를 전 모듈 production 클래스에서 잰다.
 * 소스 쌍 축(`domainSourceReferenceGate` ⓒ)은 글자를 세므로 별칭·정적 import 로 열렸다(실측 —
 * verifier H-1 · 리뷰 HIGH). 컴파일된 참조는 그 형태들을 하나로 모은다.
 */
private fun Project.registerEnvironmentAccessGate(policy: RegularFile): TaskProvider<EnvironmentAccessGateTask> =
    tasks.register<EnvironmentAccessGateTask>("environmentAccessGate") {
        description = "production 클래스가 실행 환경을 묻는 멤버를 부르는지 산출물에서 잰다(OPS-13 임계 0)"
        policyFile = policy
        moduleName = project.name
        classes.from(provider { sourceSets().getByName("main").output.classesDirs })
        dependsOn(tasks.named("classes"))
        report = layout.buildDirectory.file("reports/environment-access/violations.txt")
    }

/**
 * public domain API 의 타입 표면에 raw 부동소수가 없는지 잰다 — milestone-1.md 「완료 조건」의 강제 장치.
 * 소스 참조 게이트와 축이 달라(이름 참조 대 타입 표면) 별도 task 로 둔다.
 */
private fun Project.registerDomainApiTypeGate(
    policy: RegularFile,
    configDir: Directory,
): TaskProvider<DomainApiTypeGateTask> =
    tasks.register<DomainApiTypeGateTask>("domainApiTypeGate") {
        description = "domain main 의 public(+protected) API 타입 표면에 raw 부동소수가 없는지 잰다"
        policyFile = policy
        apiPolicyFile = configDir.file("quality/api-type-policy.properties")
        moduleName = project.name
        sourceRoot.from(layout.projectDirectory.dir("src/main/kotlin"))
        report = layout.buildDirectory.file("reports/domain-api-type/violations.txt")
    }

/**
 * 마이그레이션 번호 순서(`OPEN-MIGRATION-ORDER-GATE`) — 규율은 D-6F5-8 이고 그 규율에 자동 게이트가 없다는
 * 사실이 D-6F5-13 으로 등재됐다. 소유 모듈은 정책이 고르고(`migration.module`) 위치도 정책 값이다.
 * **비-소유 모듈도 읽는다** — Flyway 의 모집단이 classpath 라서다(verifier H-2).
 *
 * 판정 입력은 **처리된 리소스**다 — 관례 소스 디렉터리가 아니라 Flyway 가 읽는 그 자리이고, 그래서
 * 관례 디렉터리·`resources.srcDir`·`processResources { into(…) }` 세 경로가 한 입력으로 모인다.
 */
private fun Project.registerMigrationOrderGate(configDir: Directory): TaskProvider<MigrationOrderGateTask> {
    // 관례를 박지 않고 정책 데이터에서 읽는다 — 그 경로가 선언 입력이자 판정 대상의 유일한 자리다.
    val migrationPolicy = configDir.file("quality/migration-policy.properties")
    // **소비자가 읽는 자리**를 본다(verifier M-1): Flyway 는 `classpath:db/migration` 을 읽으므로
    // 판정 입력도 **처리된 리소스** 밑의 그 위치다. 관례 소스 디렉터리만 보면
    // `processResources { from(…) { into("db/migration") } }` 가 그 자리에 넣은 파일을 놓친다(실측).
    val classpathLocation = readPolicy(migrationPolicy.asFile).requireValue("migration.classpath-location")
    return tasks.register<MigrationOrderGateTask>("migrationOrderGate") {
        description = "마이그레이션 version 집합이 결번·중복 없이 1..max 와 같은지 잰다(Flyway outOfOrder=false)"
        policyFile = migrationPolicy
        moduleName = project.name
        migrationDirectory.from(
            provider {
                val main = sourceSets().getByName("main")
                main.output.resourcesDir?.resolve(classpathLocation)
            },
        )
        dependsOn(tasks.named("processResources"))
        report = layout.buildDirectory.file("reports/migration-order/violations.txt")
    }
}

/** source set 컨테이너는 **지연으로** 읽는다 — 이 배선이 Kotlin plugin 적용 순서에 기대지 않게. */
private fun Project.sourceSets(): SourceSetContainer = extensions.getByType<SourceSetContainer>()
