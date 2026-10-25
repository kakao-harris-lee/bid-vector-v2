package bidvector.adapters.profile

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/** V22 두 표의 이름 — 상수 풀에 이 문자열이 있으면 그 class 가 V22 를 **직접** 읽거나 쓰거나 등재한다. */
private val V22_TABLES = listOf("account_business_profile", "account_construction_capacity")

/** V22 값 타입 · port 의 좌표(빗금 · 점 두 표기 — 이름 기반 로드가 남기는 쪽까지). */
private val PROFILE_COORDINATE = Regex("""bidvector[/.]workflow[/.]profile[/.]""")

/**
 * 판정 입력 port 의 좌표. 이 문자열은 **구현**(`implements`)이든 **SAM 람다**(indy 호출 자리의 반환
 * 타입)든 **사용**(매개변수 타입)이든 그 class 의 상수 풀에 남는다 — 그래서 이 한 술어가 세 경우를
 * 함께 센다(r2 CR R2-3: Kotlin 2.x 의 indy SAM 변환은 class 파일을 내지 않아 class 파일 모집단으로는
 * 보이지 않는다).
 */
private val JUDGEMENT_INPUT_PORT = Regex("""bidvector[/.]workflow[/.]evaluation[/.]OperatorProfilePort""")

/** 표 이름을 들어도 되는 **소스 파일** — SQL 문장 · 그 `const` 를 인라인하는 어댑터 · 보존 레지스트리. */
private val TABLE_NAME_SOURCES =
    setOf("BusinessProfileSql.kt", "JdbcBusinessProfileStore.kt", "RetentionTableRegistry.kt")

/** V22 값 타입을 봐도 되는 **소스 파일** 하나. */
private val PROFILE_TYPE_SOURCES = setOf("JdbcBusinessProfileStore.kt")

/**
 * **V22 격리 — `adapters` 모듈 전체에 거는 규칙 둘**(M7/7C④, r2 F5 · CR R2-3 처분 (가)).
 *
 * r1 의 우회 셋을 패키지 단위로 막자 한 칸 옆에서 다섯이 열렸다(verifier r2 N1~N5) — 우회마다 술어를
 * 하나 더하는 방식을 여기서 멈추고, **축을 바꾼다**. 두 규칙 다 패키지 목록이 아니라 **모듈 전체**를
 * 모집단으로 쓰고, 허용은 class 이름이 아니라 **`SourceFile` 속성**(그 class 가 어느 `.kt` 에서
 * 나왔는가)으로 준다. 그래서 새 패키지를 만들거나(N3) class 이름을 허용 목록처럼 지어도(r1 CR R2-L2)
 * 열리지 않는다.
 *
 * - **규칙 1 — V22 표 이름**: `account_business_profile` · `account_construction_capacity` 가 상수 풀에
 *   있는 class 는 [TABLE_NAME_SOURCES] 에서 나온 것뿐이다. SQL 문자열로 표를 직접 읽는 우회(N2)가
 *   여기 걸린다. `BusinessProfileSql` 의 `const val` 은 **쓰는 자리에 인라인**되므로 그 문자열은
 *   SQL 객체 밖으로도 퍼진다 — 그 자리를 허용 목록이 이름으로 적는다.
 * - **규칙 2 — V22 값 타입 좌표**: [PROFILE_COORDINATE] 가 있는 class 는 [PROFILE_TYPE_SOURCES] 에서
 *   나온 것뿐이다(X1 · N3).
 * - **규칙 3 — 판정 입력 port 를 지닌 class**: [JUDGEMENT_INPUT_PORT] 가 상수 풀에 있는 class 는 V22 표
 *   이름도 값 타입 좌표도 **함께 지니지 않는다**. 허용 목록과 **무관하게** 적용된다 — 허용 파일 안에서
 *   `OperatorProfilePort { … }` 람다로 V22 를 접는 우회(CR R2-3 의 indy 변종)가 여기 걸린다.
 *
 * **재는 것은 「직접 참조 · 직접 구현 · 람다 구현」뿐이다.** 재지 않는 것(그래서 주장하지 않는다):
 * 허용 파일의 `String` 반환 helper 를 거치는 **이행 값 흐름**(N1) · 선언 자리를 제3 패키지로 옮긴
 * 인터페이스(N4) · 실행 시 조립한 이름(N5) · 모듈 밖(`app` — 2b 가 정당하게 참조한다).
 * 그 셋은 scope 알려진 제한과 2b 표적에 있고, **진짜 방어는 2b 배선의 포트 경계**다.
 *
 * 상수 풀을 **바이트로** 훑는다(ASCII 이름) — `javap` 는 걸린 후보의 `SourceFile` 을 읽을 때만 부른다.
 */
class BusinessProfileIsolationGateTest {
    @Test
    fun `V22 표 이름이 있는 adapters class 는 허용 소스 파일에서만 나온다`() {
        val bearers = adaptersClasses().filter { file -> V22_TABLES.any { it in bytesOf(file) } }
        bearers.shouldNotBeEmpty() // 빈 모집단을 「위반 없음」으로 오판하지 않는다.

        bearers.filterNot { sourceFileOf(it) in TABLE_NAME_SOURCES }.map { it.name } shouldBe emptyList()
    }

    @Test
    fun `V22 값 타입 좌표가 있는 adapters class 는 그 어댑터 파일에서만 나온다`() {
        val bearers = adaptersClasses().filter { PROFILE_COORDINATE.containsMatchIn(bytesOf(it)) }
        bearers.shouldNotBeEmpty()

        bearers.filterNot { sourceFileOf(it) in PROFILE_TYPE_SOURCES }.map { it.name } shouldBe emptyList()
    }

    @Test
    fun `판정 입력 port 를 지닌 class 는 V22 표도 값 타입도 함께 지니지 않는다`() {
        val portBearers = adaptersClasses().filter { JUDGEMENT_INPUT_PORT.containsMatchIn(bytesOf(it)) }
        portBearers.shouldNotBeEmpty() // V12 port 구현이 적어도 하나 있다.

        portBearers.filter { file -> bearsV22(bytesOf(file)) }.map { it.name } shouldBe emptyList()
    }

    /** 양성 대조 — 세 술어가 각각 참을 낸다(늘 거짓만 내는 회귀를 막는다). */
    @Test
    fun `세 술어가 실제로 참을 낸다 — 양성 대조`() {
        bearsV22("… insert into account_business_profile …") shouldBe true
        bearsV22("… bidvector/workflow/profile/TradeName …") shouldBe true
        bearsV22("… bidvector.workflow.profile.TradeName …") shouldBe true
        bearsV22("… bidvector/workflow/evaluation/ProfileFacts …") shouldBe false
        JUDGEMENT_INPUT_PORT.containsMatchIn("()Lbidvector/workflow/evaluation/OperatorProfilePort;") shouldBe true
        JUDGEMENT_INPUT_PORT.containsMatchIn("bidvector/workflow/evaluation/ProfileFacts") shouldBe false
    }

    /** 양성 대조 — `SourceFile` 추출이 실제 값을 낸다(늘 `null` 이면 위 셋이 조용히 초록이다). */
    @Test
    fun `SourceFile 추출이 실제 파일 이름을 낸다 — 양성 대조`() {
        val sqlClass = File(adaptersClassesRoot, "bidvector/adapters/profile/BusinessProfileSql.class")
        sourceFileOf(sqlClass) shouldBe "BusinessProfileSql.kt"
    }
}

private val adaptersClassesRoot = File("build/classes/kotlin/main")

private fun adaptersClasses(): List<File> {
    check(adaptersClassesRoot.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${adaptersClassesRoot.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    val classes = adaptersClassesRoot.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    classes.shouldNotBeEmpty()
    return classes
}

/** 상수 풀을 포함한 class 파일 전체 바이트 — 이름은 ASCII 라 1바이트 인코딩으로 훑어도 된다. */
private fun bytesOf(classFile: File): String = String(classFile.readBytes(), Charsets.ISO_8859_1)

private fun bearsV22(bytes: String): Boolean =
    V22_TABLES.any { it in bytes } || PROFILE_COORDINATE.containsMatchIn(bytes)

/**
 * 그 class 가 나온 `.kt` 파일 — **class 이름이 아니라 class 파일의 `SourceFile` 속성**이다. 이름으로
 * 허용을 얻는 길(r1 CR R2-L2)이 이 축에는 없다.
 */
private fun sourceFileOf(classFile: File): String? {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "javap 실행 실패: ${classFile.name}\n$output" }
    return Regex("""^SourceFile: "([^"]+)"""", RegexOption.MULTILINE).find(output)?.groupValues?.get(1)
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
