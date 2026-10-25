package bidvector.adapters.profile

import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.OperatorProfilePort
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarFile

/**
 * V22 두 표의 이름 — 상수 풀에 이 문자열이 있으면 그 class 가 V22 를 **직접** 읽거나 쓰거나 등재한다.
 * **대소문자를 무시해 찾는다**: PostgreSQL 은 따옴표 없는 식별자를 소문자로 접으므로 대문자로 적은 SQL 도
 * 같은 표를 읽는다(r3 F9 B1 실측 — 소문자 부분 문자열 술어는 글자 크기 하나로 열렸다).
 */
private val V22_TABLES = listOf("account_business_profile", "account_construction_capacity")

/** V22 값 타입 · port 의 좌표(빗금 · 점 두 표기 — 이름 기반 로드가 남기는 쪽까지). */
private val PROFILE_COORDINATE = Regex("""bidvector[/.]workflow[/.]profile[/.]""")

/**
 * 허용 키 — **`SourceFile` 속성 하나가 아니라 「class 가 놓인 패키지 경로 + `SourceFile`」**이다.
 * `SourceFile` 은 경로를 담지 않아서 같은 이름의 파일을 다른 패키지에 두면 허용이 따라왔다(r3 CR R3-1 ·
 * F9 B3 실측).
 *
 * 패키지는 산출물 **경로**에서 나온다. 다만 **이 test 혼자서 위조를 막지는 못한다** — Kotlin 은 소스
 * 디렉터리와 `package` 선언이 같기를 요구하지 않으므로, 아무 디렉터리에 `package bidvector.adapters.profile`
 * 을 선언하면 산출물은 허용 자리에 떨어진다. 그 길을 막는 것은 **`check` 의 detekt
 * `InvalidPackageDeclaration`** 이고(verifier r4 C6 실측: 이 test 는 초록이고 `:adapters:detekt` 가 붉었다),
 * 이 규칙은 **그 전제 위에 선다**(scope 알려진 제한).
 */
private val TABLE_NAME_SOURCES =
    setOf(
        "bidvector/adapters/profile/BusinessProfileSql.kt",
        "bidvector/adapters/profile/JdbcBusinessProfileStore.kt",
        "bidvector/adapters/retention/RetentionTableRegistry.kt",
    )

/** V22 값 타입을 봐도 되는 자리 하나(같은 키 형태). */
private val PROFILE_TYPE_SOURCES = setOf("bidvector/adapters/profile/JdbcBusinessProfileStore.kt")

/**
 * **V22 격리 — `adapters` 모듈 전체에 거는 규칙 셋**(M7/7C④, r2 F5 처분 (가) · r3 F9 보강).
 *
 * 우회마다 술어를 하나 더하는 방식은 r2 에서 멈췄다. 축은 둘이다 — 모집단은 **모듈 전체의 컴파일
 * 산출물**, 허용은 **그 class 가 놓인 패키지 + `SourceFile`** 이다.
 *
 * - **규칙 1 — V22 표 이름**(대소문자 무시): 그 문자열을 지닌 class 는 [TABLE_NAME_SOURCES] 에서 나온
 *   것뿐이다. SQL 문자열로 표를 직접 읽는 우회(N2 · B1)가 걸린다. `BusinessProfileSql` 의 `const val` 은
 *   쓰는 자리에 **인라인**되므로 그 문자열이 SQL 객체 밖으로도 퍼진다 — 그 자리를 허용 키가 적는다.
 * - **규칙 2 — V22 값 타입 좌표**: [PROFILE_COORDINATE] 를 지닌 class 는 [PROFILE_TYPE_SOURCES] 에서 나온
 *   것뿐이다(X1 · N3 · B3).
 * - **규칙 3 — 판정 입력 port 를 지닌 class**: `workflow.evaluation` 의 port 좌표를 지닌 class 는 V22 표
 *   이름도 값 타입 좌표도 **함께 지니지 않는다**. 허용 키와 **무관하게** 적용된다 — 허용 파일 안에서
 *   `OperatorProfilePort { … }` 람다로 V22 를 접는 우회(indy 변종)와 중첩 구현(N7)이 걸린다. port 집합은
 *   이름 목록이 아니라 **그 패키지의 「봉인되지 않은 인터페이스」 전수**를 산출물에서 뽑는다 — 봉인
 *   인터페이스는 결과 · 값 타입(`CandidateEvaluation` 류)이고 port 가 아니다. 멤버 수로 고르지 **않는다**:
 *   추상 메서드가 둘인 port 가 생겨도 집합에 남는다(r4 F13 · CR R4-L2 — 앞 판은 SAM 만 셌다). 좌표는
 *   **빗금 · 점 두 표기**로 담는다(r4 CR R4-L3 — 이름 기반 로드가 남기는 쪽까지).
 *
 * **재는 것은 「리터럴 표 이름(대소문자 무시) · 직접 타입 참조 · SAM 구현 · SAM 람다」뿐이다.** 재지
 * 않는 것(그래서 주장하지 않는다): **classpath 자원 파일의 SQL**(모집단이 `build/classes` 다 — B2) ·
 * **함수 타입을 거쳐 evaluation 팩토리로 넘기는 인자 람다**(B4) · 허용 파일의 `String` 반환 helper 를
 * 거치는 **이행 값 흐름**(N1) · **제3 패키지에 선언된 인터페이스**(N4) · **실행 시 조립한 이름**(N5) ·
 * 모듈 밖(`app` — 2b 가 정당하게 참조한다). 그 다섯은 scope 알려진 제한 5 에 있고, **진짜 방어는 2b
 * 배선의 포트 경계**다(2b 표적 4).
 *
 * 상수 풀을 **바이트로** 훑고(ASCII 이름), 파일마다 **한 번만** 읽어 세 규칙이 그 결과를 함께 쓴다
 * (r3 CR R3-6). `javap` 는 걸린 후보의 `SourceFile` 을 읽을 때만 부른다.
 */
class BusinessProfileIsolationGateTest {
    @Test
    fun `V22 표 이름이 있는 adapters class 는 허용 자리에서만 나온다`() {
        val bearers = scannedClasses.filter { it.bearsTableName }
        bearers.shouldNotBeEmpty() // 빈 모집단을 「위반 없음」으로 오판하지 않는다.

        bearers.filterNot { allowanceKey(it.file) in TABLE_NAME_SOURCES }.map { it.file.name } shouldBe emptyList()
    }

    @Test
    fun `V22 값 타입 좌표가 있는 adapters class 는 그 어댑터 자리에서만 나온다`() {
        val bearers = scannedClasses.filter { it.bearsProfileType }
        bearers.shouldNotBeEmpty()

        bearers.filterNot { allowanceKey(it.file) in PROFILE_TYPE_SOURCES }.map { it.file.name } shouldBe emptyList()
    }

    @Test
    fun `판정 입력 port 를 지닌 class 는 V22 표도 값 타입도 함께 지니지 않는다`() {
        val ports = portCoordinates()
        val portBearers = scannedClasses.filter { entry -> ports.any { it in entry.bytes } }
        portBearers.shouldNotBeEmpty() // 그 port 를 지닌 class 가 적어도 하나 있다.

        portBearers.filter { it.bearsTableName || it.bearsProfileType }.map { it.file.name } shouldBe emptyList()
    }

    /** 양성 대조 — 두 표지 술어가 각각 참을 낸다(늘 거짓만 내는 회귀를 막는다). */
    @Test
    fun `표 이름과 값 타입 술어가 실제로 참을 낸다 — 양성 대조`() {
        hasTableName("… insert into account_business_profile …") shouldBe true
        hasTableName("… SELECT X FROM ACCOUNT_BUSINESS_PROFILE WHERE …") shouldBe true // 대문자 SQL(B1)
        hasTableName("… bidvector/workflow/profile/TradeName …") shouldBe false
        PROFILE_COORDINATE.containsMatchIn("… bidvector/workflow/profile/TradeName …") shouldBe true
        PROFILE_COORDINATE.containsMatchIn("… bidvector.workflow.profile.TradeName …") shouldBe true
        PROFILE_COORDINATE.containsMatchIn("… bidvector/workflow/evaluation/ProfileFacts …") shouldBe false
    }

    /**
     * 양성 대조 — 허용 키가 **패키지까지** 본다. 같은 `SourceFile` 이름을 다른 패키지에 둔 자리는 허용에
     * 들지 않는다(r3 F9 B3). `SourceFile` 추출이 실제 값을 내는 것도 함께 잰다 — 늘 `null` 이면 위 셋이
     * 조용히 초록이다.
     */
    @Test
    fun `허용 키는 패키지 + SourceFile 이다 — 양성 대조`() {
        val sqlClass = File(adaptersClassesRoot, "bidvector/adapters/profile/BusinessProfileSql.class")
        sourceFileOf(sqlClass) shouldBe "BusinessProfileSql.kt"
        allowanceKey(sqlClass) shouldBe "bidvector/adapters/profile/BusinessProfileSql.kt"
        (allowanceKey(sqlClass) in TABLE_NAME_SOURCES) shouldBe true

        packageOf(File(adaptersClassesRoot, "bidvector/adapters/regionfold2/Spoofed.class")) shouldBe
            "bidvector/adapters/regionfold2"
        ("bidvector/adapters/regionfold2/JdbcBusinessProfileStore.kt" in TABLE_NAME_SOURCES) shouldBe false
        ("bidvector/adapters/regionfold2/JdbcBusinessProfileStore.kt" in PROFILE_TYPE_SOURCES) shouldBe false
    }

    /**
     * 양성 대조 — port 집합이 **전수**이고 그것이 test 로 잠긴다(r4 CR R4-L2). 앞 판은 크기만 봐서 집합이
     * 조용히 줄어도 초록이었다. 여기서는 **그 패키지의 `*Port` 가 하나도 빠지지 않는다**를 단언하고,
     * 멤버가 둘인 표본도 술어가 받는지(F13), 봉인된 결과 타입은 빠지는지를 함께 잰다.
     */
    @Test
    fun `판정 입력 port 집합은 evaluation 의 봉인 아닌 인터페이스 전수다 — 양성 대조`() {
        val ports = portCoordinates()
        val named = evaluationInterfaces().filter { it.simpleName.endsWith("Port") }
        named.shouldNotBeEmpty()
        named.filterNot { it.name.replace('.', '/') in ports }.map { it.simpleName } shouldBe emptyList()

        (OperatorProfilePort::class.java.name.replace('.', '/') in ports) shouldBe true
        (OperatorProfilePort::class.java.name in ports) shouldBe true // 점 표기도 담는다(R4-L3)
        isPortType(TwoMemberPortSample::class.java) shouldBe true // 멤버 둘도 port 다(F13)
        isPortType(CandidateEvaluation::class.java) shouldBe false // 봉인 결과 타입은 아니다
    }
}

private val adaptersClassesRoot = File("build/classes/kotlin/main")

/** 한 class 파일의 스캔 결과 — 바이트를 **한 번만** 읽어 세 규칙이 함께 쓴다(r3 CR R3-6). */
private class Scanned(
    val file: File,
    val bytes: String,
) {
    val bearsTableName: Boolean = hasTableName(bytes)
    val bearsProfileType: Boolean = PROFILE_COORDINATE.containsMatchIn(bytes)
}

private val scannedClasses: List<Scanned> by lazy {
    check(adaptersClassesRoot.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${adaptersClassesRoot.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    val classes =
        adaptersClassesRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { Scanned(it, String(it.readBytes(), Charsets.ISO_8859_1)) }
            .toList()
    check(classes.isNotEmpty()) { "adapters 산출물이 비었다: ${adaptersClassesRoot.absolutePath}" }
    classes
}

private fun hasTableName(bytes: String): Boolean {
    val lower = bytes.lowercase()
    return V22_TABLES.any { it in lower }
}

/** 산출물 **경로**에서 나오는 패키지 — 이름으로 꾸밀 수 없다. */
private fun packageOf(classFile: File): String =
    classFile.parentFile
        .relativeTo(adaptersClassesRoot)
        .path
        .replace(File.separatorChar, '/')

private fun allowanceKey(classFile: File): String = "${packageOf(classFile)}/${sourceFileOf(classFile)}"

/** 그 class 가 나온 `.kt` 파일 — class 파일의 `SourceFile` 속성이다(class 이름이 아니다). */
private fun sourceFileOf(classFile: File): String? {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "javap 실행 실패: ${classFile.name}\n$output" }
    return Regex("""^SourceFile: "([^"]+)"""", RegexOption.MULTILINE).find(output)?.groupValues?.get(1)
}

/** 양성 대조용 합성 표본 — 멤버가 **둘**인 port 모양(SAM 이 아니다). */
private interface TwoMemberPortSample {
    fun first(): String

    fun second(): String
}

/**
 * port 술어 — **인터페이스이고 봉인되지 않았다**. 봉인 인터페이스는 결과 · 값 타입이라 port 가 아니고
 * (Kotlin 의 `sealed interface` 는 바이트코드에 `PermittedSubclasses` 를 남긴다 — 실측), 멤버 수는 보지
 * 않는다(추상 메서드가 둘인 port 도 port 다, r4 F13).
 */
private fun isPortType(type: Class<*>): Boolean = type.isInterface && !type.isSealed

/**
 * `workflow.evaluation` 의 인터페이스 전수를 산출물에서 올린다(이름 목록을 적지 않는다). class 를 올리지
 * 못하면 **크게 실패한다** — 조용히 빠뜨리면 그만큼 규칙 3 의 모집단이 줄고 그 사실이 보이지 않는다
 * (r4 CR R4-L4).
 */
private fun evaluationInterfaces(): List<Class<*>> {
    val anchor = OperatorProfilePort::class.java
    val source = anchor.protectionDomain.codeSource?.location ?: error("code source 를 찾지 못했다")
    val binaryNames = binaryNamesUnder(File(source.toURI()), "bidvector/workflow/evaluation")
    check(binaryNames.isNotEmpty()) { "판정 패키지의 class 를 찾지 못했다: $source" }
    return binaryNames
        .map { name ->
            runCatching { Class.forName(name, false, anchor.classLoader) }
                .getOrElse { error("판정 패키지의 class 를 올리지 못했다: $name — $it") }
        }.filter { it.isInterface }
}

/** 규칙 3 이 찾는 좌표 — **빗금 · 점 두 표기**(이름 기반 로드가 남기는 쪽까지, r4 CR R4-L3). */
private fun portCoordinates(): Set<String> =
    evaluationInterfaces()
        .filter(::isPortType)
        .flatMap { listOf(it.name.replace('.', '/'), it.name) }
        .toSet()

private fun binaryNamesUnder(
    codeSource: File,
    packagePath: String,
): List<String> {
    val packageName = packagePath.replace('/', '.')
    if (codeSource.isDirectory) {
        return File(codeSource, packagePath)
            .listFiles { file -> file.extension == "class" }
            .orEmpty()
            .map { "$packageName.${it.nameWithoutExtension}" }
    }
    return JarFile(codeSource).use { jar ->
        jar
            .entries()
            .asSequence()
            .map { it.name }
            .filter { it.startsWith("$packagePath/") && it.endsWith(".class") }
            .filterNot { '/' in it.removePrefix("$packagePath/") }
            .map { "$packageName.${it.removePrefix("$packagePath/").removeSuffix(".class")}" }
            .toList()
    }
}

/**
 * `javap` 를 OS `PATH` 가 아니라 **실행 중인 JVM 의 `java.home`** 에서 해석한다(형제 dependency test 들과
 * 같은 이유 — CI 의 JDK 21 과 다른 `javap` 를 부를 여지를 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}
