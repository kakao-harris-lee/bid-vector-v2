package bidvector.workflow.profile

import bidvector.workflow.evaluation.ProfileFacts
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/** `workflow` 모듈의 컴파일 산출물 뿌리 — 이름 좌표를 이 아래 class 파일의 바이트에서 찾는다. */
private val workflowClasses = File("build/classes/kotlin/main/bidvector/workflow")

/** 판정이 서는 패키지 — 그쪽이 이 패키지(입력)를 보지 않아야 한다. */
private const val EVALUATION_PACKAGE = "bidvector.workflow.evaluation"

/**
 * 이 패키지의 좌표 — **내부 이름(빗금)과 점 표기 둘 다** 본다. 이름 기반 클래스 로드
 * (`Class.forName("bidvector.workflow.profile.…")`)가 남기는 것은 점 표기이고, 빗금만 찾는 술어는
 * 그 경로를 못 본다(r1 F1 · CR M-4 의 X3 실측 — 형제 `ProfileAdapterDependencyTest` 는 이미 둘 다 본다).
 */
private val PROFILE_NAME_REFERENCE = Regex("""bidvector[/.]workflow[/.]profile[/.]""")

/**
 * **「아직 판정하지 않음」 표시의 구조 쪽 잠금**(M7 완료 조건 G2, scope 산출물 4) — 판정 경로가 이
 * 패키지의 입력을 읽기 시작하면 붉어진다.
 *
 * **이 test 가 재는 것**(r2 뒤 넷):
 * 1. 판정 대기 표의 축과 그 ID — wire 값이라 이름으로 잠근다.
 * 2. `workflow` 모듈에서 이 패키지 **밖** class 가 이 패키지를 **이름으로** 참조하지 않는다 — 상수 풀
 *    바이트를 빗금 · 점 두 표기로 본다.
 * 3. 이 패키지의 class 가 [EVALUATION_PACKAGE] 의 타입을 **구현 · 상속하지 않는다** — 판정 쪽이
 *    인터페이스를 선언하고 입력 쪽이 구현하면(의존 역전) 판정 쪽 상수 풀에 이 패키지 이름이 하나도
 *    남지 않아 2 가 초록이다(X2 실측).
 * 4. 이 패키지의 class 가 그 타입을 **SAM 람다로 만들지도 않는다** — Kotlin 2.x 의 SAM 변환은 indy 라
 *    class 파일을 내지 않으므로(r2 CR R2-3) 3 의 모집단에 들지 않는다. 그래서 **indy 호출 자리의
 *    반환 타입**을 따로 본다 — 인자까지 세면 `data class` 의 문자열 접합 indy 가 오탐이 된다(실측).
 *    **대상 타입이 판정 패키지인 람다만** 센다: 함수 타입을 거쳐 판정 쪽 팩토리로 넘기는 길은 indy 반환
 *    타입이 `kotlin/jvm/functions/…` 라 이 축 밖이다(r3 CR R3-4 — 제한 5 의 N1 · N4 와 같은 자리).
 *
 * **재는 것은 「직접 이름 참조 · 직접 구현 · 판정 타입을 대상으로 한 SAM 람다」뿐이다.** 재지 않는 것
 * (그래서 주장하지 않는다):
 * ⓐ `app` 모듈 — **2b 부터 정당하게 참조한다**(HTTP 표면이 요청을 이 패키지의 값으로 옮기고 응답을
 * 그린다). 그쪽에서 막아야 하는 것은 참조가 아니라 **판정 쪽으로 건너가는 것**이고, 그 자리는 조립
 * 하나다 — `ProfileAssemblyBoundaryTest` 가 「V22 를 받거나 내는 `@Bean` 은 프로필 조립의 것뿐」을
 * 집합으로 잰다(2b 표적 4). ⓑ `adapters` 모듈 —
 * V22 를 지닌 class 의 자리는 모듈 전체에 걸리는 `BusinessProfileIsolationGateTest` 가 `SourceFile`
 * 속성으로 정한다. ⓒ domain 모듈(`qualification` 등) — 층 게이트가 `workflow` 참조 자체를 막는다.
 * ⓓ **이행 값 흐름**(허용 파일의 `String` 반환 helper 를 거치는 길) · **제3 패키지에 선언된 인터페이스**
 * (선언 자리를 한 칸 옮기면 3 · 4 의 술어 밖이다) · **실행 시 조립한 이름**(상수 풀에 없다) —
 * verifier r2 가 N1 · N4 · N5 로 실측한 자리이고, scope 알려진 제한과 2b 표적에 있다. **진짜 방어는
 * 2b 배선의 포트 경계**이고, 판정이 그 값을 실제로 쓰는지는 판정을 세우는 slice 의 test 가 진다.
 *
 * 소스 텍스트가 아니라 **컴파일 산출물**을 본다 — import 없이 전체 한정 이름으로 쓰는 우회를 소스
 * 정규식은 못 본다(`ProfileAdapterDependencyTest` 와 같은 근거).
 */
class ProfileJudgementIsolationTest {
    /**
     * 축 ID 는 **wire 값**이다(프로필 조회 응답이 이 이름을 그대로 싣는다, 2b). 그래서 이름을 직접
     * 잠근다 — 7C ② 의 자격 카드가 같은 세 문자열을 쓰기로 했고(`D-7C2-1` Q5), 한쪽에서 상수 이름을
     * 바꾸면 두 표면의 어휘가 조용히 갈린다. 그 등식은 **수동**이다(scope 알려진 제한 2).
     */
    @Test
    fun `판정 대기 축은 QUAL-08 · QUAL-10 · QUAL-11 셋이고 ID 는 7C② 어휘와 같다`() {
        UnjudgedQualificationAxis.entries.map { it.name } shouldBe
            listOf("QUAL_08_REGION", "QUAL_10_PERFORMANCE", "QUAL_11_CONSTRUCTION_CAPACITY")
        UnjudgedQualificationAxis.entries.map { it.capability } shouldBe listOf("QUAL-08", "QUAL-10", "QUAL-11")
    }

    @Test
    fun `workflow 의 profile 밖 클래스는 profile 패키지를 이름으로 참조하지 않는다`() {
        val outside = classFiles().filterNot(::inProfilePackage).toList()
        outside.shouldNotBeEmpty() // 빈 모집단을 「위반 없음」으로 오판하지 않는다.

        outside.filter(::referencesProfileByName).map { it.relativeTo(workflowClasses).path } shouldBe emptyList()
    }

    @Test
    fun `profile 패키지의 클래스는 판정 패키지의 타입을 구현하거나 상속하지 않는다`() {
        val profileClasses = classFiles().filter(::inProfilePackage).map(::loadClass).toList()
        profileClasses.shouldNotBeEmpty()

        profileClasses
            .filter { type -> supertypesOf(type).any(::declaredInEvaluation) }
            .map { it.name } shouldBe emptyList()
    }

    @Test
    fun `profile 패키지의 클래스는 판정 패키지의 타입을 람다로 만들지도 않는다`() {
        val profileClasses = classFiles().filter(::inProfilePackage).toList()
        profileClasses.shouldNotBeEmpty()

        profileClasses
            .filter { file -> lambdaTargets(javap(file)).any(::isEvaluationCoordinate) }
            .map { it.name } shouldBe emptyList()
    }

    /**
     * 양성 대조 둘. **(1) 추출이 실제 산출물의 indy 자리를 본다** — 이 패키지의 열거가 쓰는 Kotlin
     * 람다가 indy 라서 `kotlin/jvm/functions/Function1` 이 나온다. **(2) 문자열 접합 indy 의 인자는 집지
     * 않는다** — 그 자리를 세면 `data class` 의 `toString()` 마다 오탐이 난다(앞 판이 실제로
     * `OnboardingSnapshot` 에서 붉었다). 끝에서 끝까지의 증거는 **변이**다(evidence 의 전·후 표).
     */
    @Test
    fun `람다 대상 추출은 indy 반환 타입만 집는다 — 양성 대조`() {
        val enumClass = File(workflowClasses, "profile/OnboardingItem.class")
        lambdaTargets(javap(enumClass)) shouldContain "kotlin/jvm/functions/Function1"

        val stringConcat =
            "  12: invokedynamic #30,  0  // InvokeDynamic #0:makeConcatWithConstants:" +
                "(Lbidvector/workflow/evaluation/ProfileFacts;)Ljava/lang/String;"
        lambdaTargets(stringConcat).none(::isEvaluationCoordinate) shouldBe true

        val samConversion =
            "  18: invokedynamic #44,  0  // InvokeDynamic #1:current:" +
                "()Lbidvector/workflow/evaluation/OperatorProfilePort;"
        lambdaTargets(samConversion).any(::isEvaluationCoordinate) shouldBe true
    }

    /** 양성 대조 — 이름 술어가 두 표기에서 각각 참을 낸다(늘 거짓만 내는 회귀를 막는다). */
    @Test
    fun `이름 술어는 빗금 표기와 점 표기 둘 다 걸린다 — 양성 대조`() {
        PROFILE_NAME_REFERENCE.containsMatchIn("bidvector/workflow/profile/HeadquartersRegion") shouldBe true
        PROFILE_NAME_REFERENCE.containsMatchIn("bidvector.workflow.profile.HeadquartersRegion") shouldBe true
        PROFILE_NAME_REFERENCE.containsMatchIn("bidvector/workflow/evaluation/ProfileFacts") shouldBe false
    }

    /**
     * 양성 대조 — 상위 타입 걷기가 실제로 상위 타입을 내고, 판정 패키지 술어가 참을 낼 수 있다.
     * 이 둘이 없으면 위 구현 · 상속 test 는 「상위 타입이 늘 빈 집합」이거나 「술어가 늘 거짓」일 때도
     * 초록이다.
     */
    @Test
    fun `상위 타입 걷기와 판정 패키지 술어가 참을 낸다 — 양성 대조`() {
        supertypesOf(OnboardingItem::class.java).contains(Enum::class.java) shouldBe true
        declaredInEvaluation(ProfileFacts::class.java) shouldBe true
        declaredInEvaluation(OnboardingItem::class.java) shouldBe false
    }
}

private fun classFiles(): Sequence<File> {
    check(workflowClasses.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${workflowClasses.absolutePath} — :workflow:compileKotlin 선행 필요"
    }
    return workflowClasses.walkTopDown().filter { it.isFile && it.extension == "class" }
}

private fun inProfilePackage(classFile: File): Boolean =
    classFile.parentFile.name == "profile" && classFile.parentFile.parentFile == workflowClasses

private fun referencesProfileByName(classFile: File): Boolean =
    PROFILE_NAME_REFERENCE.containsMatchIn(String(classFile.readBytes(), Charsets.ISO_8859_1))

private fun declaredInEvaluation(type: Class<*>): Boolean = type.name.startsWith("$EVALUATION_PACKAGE.")

private fun isEvaluationCoordinate(name: String): Boolean = name.startsWith("bidvector/workflow/evaluation/")

/** indy 호출 자리의 **반환 타입** — SAM 변환은 그 인터페이스를 돌려준다. */
private val INDY_RETURN_TYPE = Regex("""InvokeDynamic #\d+:[^:\s]+:\([^)]*\)L([A-Za-z0-9_/$]+);""")

/**
 * **람다로 만든 타입의 좌표** — indy 호출 자리의 **반환 타입만** 센다. 인자까지 세면 오탐이 난다:
 * `data class` 의 `toString()` 은 **문자열 접합 indy** 를 쓰고 그 자리의 **인자**에 판정 패키지 타입이
 * 실린다(`OnboardingSnapshot` 실측 — `makeConcatWithConstants:(…ProfileFacts…)Ljava/lang/String;`).
 * 그 패키지를 **쓰는** 것은 허용 방향이라 위반이 아니다.
 */
private fun lambdaTargets(javapOutput: String): List<String> =
    INDY_RETURN_TYPE
        .findAll(javapOutput)
        .map { it.groupValues[1] }
        .distinct()
        .toList()

private fun javap(classFile: File): String {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "javap 실행 실패: ${classFile.name}\n$output" }
    return output
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

/** class 파일 경로 → 그 class. **초기화하지 않고** 올린다(static 초기화를 돌리지 않는다). */
private fun loadClass(classFile: File): Class<*> {
    val binaryName =
        classFile
            .relativeTo(File("build/classes/kotlin/main"))
            .path
            .removeSuffix(".class")
            .replace(File.separatorChar, '.')
    return Class.forName(binaryName, false, ProfileJudgementIsolationTest::class.java.classLoader)
}

/** 상위 타입 전부(상속 사슬 + 인터페이스 닫힘) — 중간 타입을 거친 구현도 걸린다. */
private fun supertypesOf(type: Class<*>): Set<Class<*>> {
    val seen = mutableSetOf<Class<*>>()
    val pending = ArrayDeque(listOfNotNull(type.superclass) + type.interfaces)
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (seen.add(next)) {
            pending += listOfNotNull(next.superclass) + next.interfaces
        }
    }
    return seen
}
