package bidvector.adapters.qualification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F5-6, `EvaluationAdapterDependencyTest`
 * 와 같은 형태·같은 이유). `qualification` 패키지는 [bidvector.workflow.evaluation]
 * (`LicenseGatePort`·`OperatorProfilePort`·`ProfileFacts`)·[bidvector.qualification](판정
 * 커널·값 타입)·[bidvector.procurement](`Notice`·`NoticeId`)·`shared-kernel`(`Resolution`
 * 등)을 봐야 한다. `bidvector.adapters.persistence`는 domain이 아니라 **같은 모듈의 다른
 * adapter 패키지**다 — `Sql` 문이 거기 산다(`PersistenceAdapterDependencyTest`·
 * `EvaluationAdapterDependencyTest`의 allow-list를 넓히지 않는다 — 이 test는 그 test들과
 * 별개다). `bidvector.adapters.qualification` 자신은 이 패키지의 클래스끼리 서로 참조할 수
 * 있어야 하므로 포함한다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.evaluation",
        "bidvector.qualification",
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.qualification",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 점 표기 좌표(`a.b.C`) 양쪽에서 `bidvector...` 부분을 뽑는다
 * — 상수 풀 전체를 훑는다(`EvaluationAdapterDependencyTest`·`StrategyAdapterDependencyTest`와
 * 같은 정규식·같은 근거).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * D-6F5-9(verifier r1 HIGH-1) — `LICENSE_QUALIFICATION_POLICY`는 `bidvector.qualification`에
 * 있고 그 루트는 이 게이트의 **허용 루트**다(어댑터가 `LicenseEligibility`·`LicenseVerdict`를
 * 보려면 필연이라 좁힐 수 없다). 패키지 루트 단위 술어로는 「커널을 쓴다」와 「정책을 직접
 * 읽는다」를 가를 수 없어, 그 두 어휘(정책 로더가 컴파일된 클래스의 `getstatic`/`invoke`
 * 대상으로 남기는 이름)의 **부재**를 따로 건다.
 *
 * D-6F5-14(verifier r2 HIGH, 범위 정정) — 대상은 `StoredRequirementLicenseGate` 한 파일이
 * 아니라 **`bidvector.adapters.qualification` 패키지 전체 class 파일**이다. 계약(위협 모델
 * ②③, D-6F5-9)이 방어하겠다고 선언한 대상은 **어댑터(패키지)**이지 클래스 하나가 아니다 —
 * 정책 읽기를 같은 패키지의 새 형제 파일로 옮기는 리팩터링은 클래스 단위 술어를 우회하지만
 * 패키지 단위 술어는 우회하지 못한다(verifier r2 실측).
 */
private const val LICENSE_POLICY_LOADER_CLASS_MARKER = "LicensePolicyKt"
private const val LICENSE_POLICY_LOADER_GETTER_MARKER = "getLICENSE_QUALIFICATION_POLICY"

/**
 * D-6F5-10(verifier r1 HIGH-2) — 「`LicenseEligibility`·`judge` 어휘가 있는지」만 보는 참조
 * 단언은 호출을 **남긴 채 결과만 갈아치우는** 우회를 못 잡는다. 어댑터는 `LicenseVerdict`를
 * 생성할 이유가 없다(반환만 한다) — 그 사실을 상수 풀에서 생성자 참조 부재로 건다.
 *
 * D-6F5-14(verifier r2 HIGH, 범위 정정) — 위와 같은 이유로 **패키지 전체**에 대해 돈다.
 * `LicenseVerdict.Eligible(emptySet())` 조립을 새 형제 파일로 옮기면 클래스 단위 술어는
 * 우회되지만 패키지 단위 술어는 그 형제 파일의 class 를 같이 훑어 잡는다(verifier r2 실측 ②).
 *
 * D-6F5-15(verifier r2 LOW-1, 문면 정정 — 넓히는 방향) — 이 마커는 **생성자 참조 부재**보다
 * 넓다: `LicenseVerdict$`는 subtype 의 내부 이름 접두이므로 `is LicenseVerdict.Eligible` 같은
 * **읽기 전용 타입 검사 한 줄**도 컴파일이 `instanceof …LicenseVerdict$Eligible`을 상수 풀에
 * 남겨 같이 걸린다(실측). 그래서 어댑터는 verdict를 **통과시키기만 한다** — 상수 풀에 어떤
 * subtype 좌표도 두지 않는다(생성도 `is` 검사도 막힌다). 마커를 생성 형태로 좁히지 않는다
 * (좁히면 `copy`·`data object` 싱글턴 참조 같은 다른 획득 경로가 열린다 — 이 slice가 이미
 * 겪은 실패 방향).
 */
private const val LICENSE_VERDICT_SUBTYPE_MARKER = "LicenseVerdict\$"

/**
 * D-6F5-6 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회(scope.md 우회 5)를 소스 텍스트 정규식
 * 형태는 못 본다(6F-1 verifier MUT-E3 실측 — `EvaluationAdapterDependencyTest`가 인계한
 * 교훈) — 신설 게이트는 구조로 닫는 쪽만 쓴다(CLAUDE.md 「게이트 술어는 문자열이 아니라
 * 구조로」).
 */
class QualificationAdapterDependencyTest {
    @Test
    fun `qualification 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val violations = qualificationPackageClassFiles().flatMap(::disallowedBytecodeReferences).distinct()
        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(문자열 판정 자체는 이 값으로 잰다). */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.settlement") shouldBe true
        isDisallowed("bidvector.workflow.ml") shouldBe true
        isDisallowed("bidvector.workflow.strategy") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
        isDisallowed("bidvector.adapters.extraction") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.evaluation") shouldBe false
        isDisallowed("bidvector.qualification") shouldBe false
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.qualification") shouldBe false
    }

    /**
     * scope.md 우회 2 방어 — `StoredRequirementLicenseGate`가 `LicenseVerdict`를 직접
     * 조립하지 않고 실제로 `LicenseEligibility`를 참조하는지, 컴파일된 상수 풀로 잰다(SQL
     * 문자열 grep 같은 스타일 술어가 아니다 — `EvaluationAdapterDependencyTest`의
     * `biddableStatuses` 참조 단언과 같은 형태). 판정 호출을 지우고 직접 조립으로 바꾸면
     * 이 이름 자체가 상수 풀에서 사라진다. **이 존재 단언 하나로는 부족하다** — 호출을
     * 남긴 채 결과만 갈아치우는 우회는 아래 `LicenseVerdict` 부재 단언(D-6F5-10)이 잡는다.
     */
    @Test
    fun `StoredRequirementLicenseGate 의 컴파일된 클래스는 LicenseEligibility 를 참조한다`() {
        val output = javapOutput(storedRequirementLicenseGateClassFile())
        output shouldContain "LicenseEligibility"
        output shouldContain "judge"
    }

    /**
     * D-6F5-9(verifier r1 HIGH-1) — 재현: `verdictFor` 첫 줄 앞에
     * `LICENSE_QUALIFICATION_POLICY.resolve(...)`를 전체 한정 좌표로 심으면 의존 게이트의
     * 허용 루트 판정(`isDisallowed`)도, 전건 `check`도 초록이었다(어댑터가 정책의 두 번째
     * 독자가 되는 것을 D-6F5-5가 막으려 했으나 실제로 막는 게이트가 없었다). 정책 로더
     * 좌표가 상수 풀에 남는지를 직접 잰다.
     *
     * D-6F5-14(verifier r2 HIGH) — `StoredRequirementLicenseGate` 한 파일만 보면 정책 읽기를
     * **같은 패키지의 새 형제 파일**로 옮기는 것만으로 우회된다(verifier r2 실측, 전건 `check`
     * exit 0). **패키지 전체 class 파일**에 대해 돌려 그 우회를 막는다.
     */
    @Test
    fun `qualification 패키지의 컴파일된 클래스는 정책 로더 좌표를 참조하지 않는다`() {
        qualificationPackageClassFiles().forEach { classFile ->
            withClue("정책 로더 좌표 위반: ${classFile.name}") {
                val output = javapOutput(classFile)
                output shouldNotContain LICENSE_POLICY_LOADER_CLASS_MARKER
                output shouldNotContain LICENSE_POLICY_LOADER_GETTER_MARKER
            }
        }
    }

    /**
     * D-6F5-10(verifier r1 HIGH-2) — 재현: `judge(...)` 호출은 남긴 채 `Collected(빈 rows)`
     * 경로의 반환값만 `LicenseVerdict.Eligible(emptySet())`로 뒤집어도 전건 `check`가
     * 초록이었다(커널 참값은 `Uncertain(RequirementDataAbsent)` — 부적격을 적격으로 뒤집는
     * 가장 비싼 방향의 오판). 원판 상수 풀에는 `LicenseVerdict$` 항목이 0건이다 — 어댑터는
     * verdict를 생성할 이유가 없다.
     *
     * D-6F5-14(verifier r2 HIGH) — 조립을 **같은 패키지의 새 형제 파일**로 옮기고 게이트
     * test 가 덮지 않는 경로(`Unparsable` 행이 섞인 `Collected`)에서 반환하면, 게이트 클래스
     * 하나만 보는 술어는 우회된다(verifier r2 실측 ②③). **패키지 전체 class 파일**에 대해
     * 돌려 형제 파일의 조립도 함께 잡는다.
     *
     * D-6F5-15(verifier r2 LOW-1) — 술어는 생성뿐 아니라 **읽기**(`is LicenseVerdict.Eligible`)
     * 도 막는다. 이름·문서를 「통과만 한다 — subtype 좌표를 두지 않는다」로 넓혀 술어와 맞춘다.
     */
    @Test
    fun `qualification 패키지의 컴파일된 클래스는 LicenseVerdict 를 통과만 시킨다 — subtype 좌표를 두지 않는다`() {
        qualificationPackageClassFiles().forEach { classFile ->
            withClue("LicenseVerdict subtype 좌표 위반: ${classFile.name}") {
                javapOutput(classFile) shouldNotContain LICENSE_VERDICT_SUBTYPE_MARKER
            }
        }
    }

    /**
     * 양성 대조(D-6F5-9) — verifier가 실측한 변이 상태의 상수 풀 문구(`javap -p -v` 출력의
     * `Methodref` 행 형태)를 표본으로 써서, 그 어휘가 있으면 위 부재 단언이 실제로
     * 실패함을 보인다. 술어가 늘 통과만 하는 회귀를 막는다.
     */
    @Test
    fun `정책 로더 좌표 어휘가 있으면 부재 단언이 실패한다 — 양성 대조`() {
        val mutatedConstantPoolSample =
            "  #45 = Methodref  #12.#67  // bidvector/qualification/LicensePolicyKt.getLICENSE_QUALIFICATION_POLICY:()Lbidvector/sharedkernel/EffectiveDatedPolicy;"

        shouldThrow<AssertionError> { mutatedConstantPoolSample shouldNotContain LICENSE_POLICY_LOADER_CLASS_MARKER }
        shouldThrow<AssertionError> { mutatedConstantPoolSample shouldNotContain LICENSE_POLICY_LOADER_GETTER_MARKER }
    }

    /**
     * 양성 대조(D-6F5-10, D-6F5-15) — 위와 같은 이유, `LicenseVerdict` subtype 좌표 어휘
     * 표본으로 잰다. 표본은 생성자 참조 형태를 쓰지만 마커 자체는 `is` 검사가 남기는
     * `instanceof` 어휘도 같은 문자열로 걸린다(D-6F5-15 KDoc 참고).
     */
    @Test
    fun `LicenseVerdict subtype 좌표 어휘가 있으면 부재 단언이 실패한다 — 양성 대조`() {
        val mutatedConstantPoolSample =
            "  #78 = Methodref  #23.#90  // bidvector/qualification/LicenseVerdict\$Eligible.\"<init>\":(Ljava/util/Set;)V"

        shouldThrow<AssertionError> { mutatedConstantPoolSample shouldNotContain LICENSE_VERDICT_SUBTYPE_MARKER }
    }
}

private fun storedRequirementLicenseGateClassFile(): File {
    val classFile =
        File("build/classes/kotlin/main/bidvector/adapters/qualification/StoredRequirementLicenseGate.class")
    check(classFile.isFile) {
        "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    return classFile
}

/**
 * D-6F5-14(verifier r2 HIGH) — `bidvector.adapters.qualification` **패키지 전체**의 컴파일된
 * class 파일 목록. 허용 루트 판정(위 첫 test)이 이미 쓰던 `walkTopDown()` 목록을 부재 단언
 * 둘(정책 로더 좌표·`LicenseVerdict$`)과 공유해, 세 자리 모두 같은 대상(패키지)을 본다 — 대상이
 * 클래스 하나로 좁아지는 것을 막는다. 빈 디렉터리를 「위반 없음」으로 오판하지 않도록
 * `shouldNotBeEmpty`로 막는다.
 */
private fun qualificationPackageClassFiles(): List<File> {
    val classesDir = File("build/classes/kotlin/main/bidvector/adapters/qualification")
    check(classesDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.
    return classFiles
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 **실행 중인 JVM의 `java.home`**에서 해석한다
 * (`EvaluationAdapterDependencyTest`와 같은 이유 — CI의 JDK 21과 다른 `javap`를 부를 여지를
 * 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

private fun javapOutput(classFile: File): String {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}

private fun disallowedBytecodeReferences(classFile: File): List<String> =
    BIDVECTOR_INTERNAL_NAME
        .findAll(javapOutput(classFile))
        .map { it.value.replace('/', '.') }
        .filter(::isDisallowed)
        .toList()
