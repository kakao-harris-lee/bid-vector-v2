package bidvector.adapters.profile

import bidvector.workflow.evaluation.OperatorProfilePort
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F6-6, `StrategyAdapterDependencyTest`·
 * `EvaluationAdapterDependencyTest`와 같은 형태·같은 이유). `profile` 패키지는
 * [bidvector.workflow.evaluation](`OperatorProfilePort`·`ProfileFacts`)·
 * [bidvector.qualification](`OperatorLicenses`·`LicenseName`)·[bidvector.strategy]
 * (`CategoryCode`)를 봐야 한다. `bidvector.adapters.persistence`는 domain이 아니라
 * **같은 모듈의 다른 adapter 패키지**다 — SQL 문(`Sql.SELECT_PROFILE` 등)과 바인딩 헬퍼
 * (`getTextList`·`setTextArray`)가 거기 산다(다른 형제 dependency test들의 allow-list를
 * 넓히지 않는다 — 이 test는 그것들과 별개다). `bidvector.adapters.profile` 자신은 이
 * 패키지의 클래스끼리(`JdbcOperatorProfileRepository` → `OperatorProfileRow`) 서로
 * 참조할 수 있어야 하므로 포함한다.
 *
 * **`bidvector.workflow.profile`(V22 사업 정보의 값 타입 · port)은 이 집합에 없다** — class 단위로
 * 따로 연다([BUSINESS_PROFILE_FILES]).
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.evaluation",
        "bidvector.workflow.access",
        "bidvector.qualification",
        "bidvector.strategy",
        "bidvector.adapters.persistence",
        "bidvector.adapters.profile",
    )

/** V22 사업 정보 값 타입 · port 의 좌표 — 이 패키지에서 **class 단위**로만 열린다. */
private const val BUSINESS_PROFILE_ROOT = "bidvector.workflow.profile"

/**
 * V22 사업 정보 어댑터의 **소스 파일 둘** — 이 둘에서 나온 class 만 [BUSINESS_PROFILE_ROOT] 를 본다
 * (M7/7C④ r1 F1 · CR M-4 의 X1). 패키지 단위로 열면 **V12 판정 입력 port 의 구현**
 * (`JdbcOperatorProfileRepository` 와 같은 자리)이 새 입력을 `ProfileFacts` 로 접어 넣을 수 있고,
 * 그것이 verifier 가 실측한 우회다(새 class 를 이 패키지에 두고 두 세계를 함께 본다).
 *
 * 비교는 **파일 단위**다: Kotlin 이 내는 중첩 class(`X$Companion`)와 파일 facade(`XKt`)가 같은 소스
 * 파일에서 나오므로 그 둘을 같은 이름으로 접는다.
 */
private val BUSINESS_PROFILE_FILES = setOf("BusinessProfileSql", "JdbcBusinessProfileStore")

private fun isDisallowed(
    importedPackage: String,
    allowsBusinessProfile: Boolean,
): Boolean {
    val allowed = if (allowsBusinessProfile) ALLOWED_ROOTS + BUSINESS_PROFILE_ROOT else ALLOWED_ROOTS
    return importedPackage.startsWith("bidvector.") &&
        allowed.none { importedPackage == it || importedPackage.startsWith("$it.") }
}

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다(형제 dependency test들과 같은
 * 정규식·같은 근거).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * D-6F6-6 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회(scope.md 우회 5)를 소스 텍스트 정규식
 * 형태는 못 본다(6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다(CLAUDE.md
 * 「게이트 술어는 문자열이 아니라 구조로」).
 *
 * **이 test 가 재는 것**: (1) 이 패키지의 class 가 허용 루트 밖 `bidvector` 좌표를 참조하지 않는다 —
 * V22 값 타입은 그 어댑터 파일 둘에서만 허용. (2) **판정 입력 port 의 구현은 V22 값 타입을 아예 보지
 * 않는다** — 그 class 가 두 세계를 함께 보면 본사 소재지를 `ProfileFacts` 의 자유 지역어로 접어
 * 판정에 흘릴 수 있다. (2) 는 class 이름이 아니라 **구현 관계**로 재므로 이름을 바꿔 피할 수 없고,
 * 어댑터 파일 둘 안에 그런 class 를 새로 두는 경우도 걸린다.
 * **재지 않는 것**: 다른 패키지·모듈(각자 형제 test 가 있다)과 값이 흐르는 경로 일반.
 */
class ProfileAdapterDependencyTest {
    @Test
    fun `profile 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classFiles = profileClassFiles()

        val violations =
            classFiles
                .flatMap { file ->
                    disallowedBytecodeReferences(file, allowsBusinessProfile(file)).map { "${file.name}: $it" }
                }.distinct()
        violations shouldBe emptyList()
    }

    @Test
    fun `판정 입력 port 를 구현하는 클래스는 V22 사업 정보 값 타입을 참조하지 않는다`() {
        val portImplementations =
            profileClassFiles().filter { OperatorProfilePort::class.java.isAssignableFrom(load(it)) }
        portImplementations.shouldNotBeEmpty() // 구현이 하나도 없으면 아래 단언은 빈 모집단 위에 선다.

        portImplementations
            .filter { file -> bytecodeReferences(file).any { it.startsWith("$BUSINESS_PROFILE_ROOT.") } }
            .map { it.name } shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(문자열 판정 자체는 이 값으로 잰다). */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.decision", allowsBusinessProfile = false) shouldBe true
        isDisallowed("bidvector.procurement", allowsBusinessProfile = false) shouldBe true
        isDisallowed("bidvector.workflow.strategy", allowsBusinessProfile = false) shouldBe true
        isDisallowed("bidvector.workflow.event", allowsBusinessProfile = false) shouldBe true
        isDisallowed("bidvector.adapters.ml", allowsBusinessProfile = false) shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.evaluation", allowsBusinessProfile = false) shouldBe false
        isDisallowed("bidvector.qualification", allowsBusinessProfile = false) shouldBe false
        isDisallowed("bidvector.strategy", allowsBusinessProfile = false) shouldBe false
        isDisallowed("bidvector.adapters.persistence", allowsBusinessProfile = false) shouldBe false
        isDisallowed("bidvector.adapters.profile", allowsBusinessProfile = false) shouldBe false
    }

    /** V22 값 타입은 **그 어댑터 파일 둘에서만** 허용된다 — 패키지 단위가 아니다(X1). */
    @Test
    fun `V22 값 타입 좌표는 class 단위로만 열린다 — 양성 대조`() {
        isDisallowed("$BUSINESS_PROFILE_ROOT.TradeName", allowsBusinessProfile = false) shouldBe true
        isDisallowed("$BUSINESS_PROFILE_ROOT.TradeName", allowsBusinessProfile = true) shouldBe false

        allowsBusinessProfile(File("JdbcBusinessProfileStore.class")) shouldBe true
        allowsBusinessProfile(File("JdbcBusinessProfileStore\$Companion.class")) shouldBe true
        allowsBusinessProfile(File("JdbcBusinessProfileStoreKt.class")) shouldBe true
        allowsBusinessProfile(File("JdbcOperatorProfileRepository.class")) shouldBe false
        allowsBusinessProfile(File("RegionFoldingProfilePort.class")) shouldBe false
    }
}

private val classesRoot = File("build/classes/kotlin/main")

private fun profileClassFiles(): List<File> {
    val classesDir = File(classesRoot, "bidvector/adapters/profile")
    check(classesDir.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.
    return classFiles
}

/** 이 class 가 나온 **소스 파일** 이름 — 중첩 class 와 파일 facade 를 같은 이름으로 접는다. */
private fun allowsBusinessProfile(classFile: File): Boolean {
    val sourceFile =
        classFile.name
            .removeSuffix(".class")
            .substringBefore('$')
            .removeSuffix("Kt")
    return sourceFile in BUSINESS_PROFILE_FILES
}

/** 초기화하지 않고 올린다 — 어댑터의 static 초기화를 돌리지 않는다. */
private fun load(classFile: File): Class<*> {
    val binaryName =
        classFile
            .relativeTo(classesRoot)
            .path
            .removeSuffix(".class")
            .replace(File.separatorChar, '.')
    return Class.forName(binaryName, false, ProfileAdapterDependencyTest::class.java.classLoader)
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 **실행 중인 JVM의 `java.home`**에서 해석한다
 * (형제 dependency test들과 같은 이유 — CI의 JDK 21과 다른 `javap`를 부를 여지를 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

private fun bytecodeReferences(classFile: File): List<String> {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }

    return BIDVECTOR_INTERNAL_NAME.findAll(output).map { it.value.replace('/', '.') }.toList()
}

private fun disallowedBytecodeReferences(
    classFile: File,
    allowsBusinessProfile: Boolean,
): List<String> = bytecodeReferences(classFile).filter { isDisallowed(it, allowsBusinessProfile) }
