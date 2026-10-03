package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * D-6G2b-1~5 전송 표면 게이트의 **양성** 쪽 — production 바이트코드가 (클래스, 전송 표면 타입) 쌍의
 * 정확 집합을 지킨다. 6G 의 두 게이트(HTTP 클라이언트 보유자 · 우회 타입 금지)를 이 하나가 대신한다.
 * 규칙은 [TransportSurfaceRules], 목록은 `architecture-policy.properties` 가 갖는다(음성 쪽은
 * `TransportSurfaceGateCatchesViolationsTest`).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransportSurfaceGateTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = transportRules(ReferenceCollection.FULL)
    private val ownerOnly = transportRules(ReferenceCollection.OWNER_ONLY)
    private val production: JavaClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TEST_FIXTURES)
            .importPackages(policy.packageRoot)

    private fun transportRules(collection: ReferenceCollection) =
        TransportSurfaceRules(
            packageRoot = policy.packageRoot,
            surfacePackages = policy.transportSurfacePackages.toSet(),
            surfaceTypes = policy.transportSurfaceTypes.toSet(),
            collection = collection,
        )

    @Test
    fun `판정 뿌리가 실제로 production 에 있다 — 규칙이 공허하지 않다`() {
        policy.transportRoots.forEach { root ->
            production.filter { it.packageName == root || it.packageName.startsWith("$root.") }.shouldNotBeEmpty()
        }
    }

    /**
     * D-6G2b-3 — 쌍 등식. 클래스 단위 등재였다면 등재된 보유자가 **새 전송 타입을 더 쥐어도** 초록이다.
     * 무해 타입 예외는 없다 — 값 타입도 쌍으로 등재한다(목록에 오른 타입이 모든 클래스에서 자유로워지는
     * 것을 피한다).
     */
    @Test
    fun `production 의 클래스-전송 표면 타입 쌍은 등재 쌍과 같다`() {
        rules.observedPairs(production, policy.transportRoots) shouldBe policy.transportHolderPairs
    }

    @Test
    fun `등재 밖 전송 표면 참조가 production 에 없다`() {
        rules
            .rules(policy.transportRoots, policy.transportHolderPairs)
            .forEach { rule -> rule.check(production) }
    }

    /** D-6G2b-4 — 용도 어휘는 닫힌 집합이고, 한 쌍이 두 용도에 들어 두 번 세어지지 않는다. */
    @Test
    fun `용도 키 집합은 닫힌 어휘와 같고 한 쌍이 두 용도에 들지 않는다`() {
        policy.transportHolderPurposeKeys shouldBe policy.transportPurposes.toSet()
        policy.transportHolderPairList.size shouldBe policy.transportHolderPairs.size
    }

    /** 술어 밖 타입을 등재해 집합을 채우는 길 — 등재 쌍의 타입은 전부 전송 표면 안이어야 한다. */
    @Test
    fun `등재 쌍의 타입은 전부 전송 표면 술어 안이다`() {
        policy.transportHolderPairs.filterNot { rules.isSurfaceType(it.second) }.shouldBeEmpty()
    }

    /**
     * 양성 대조 — 고정 문자열이 아니라 **등재를 읽는 결과**에서 한 쌍을 빼서 잰다. 리터럴로 재면 규칙이
     * 등재 집합을 무엇으로 읽든 늘 참이 된다.
     */
    @Test
    fun `등재에서 한 쌍을 빼면 정확히 그 쌍이 위반으로 나온다 — 양성 대조`() {
        val registered = policy.transportHolderPairs
        val dropped = registered.first()

        val details =
            rules
                .rules(policy.transportRoots, registered - dropped)
                .flatMap { it.evaluate(production).failureReport.details }

        withClue("빼낸 쌍 $dropped") {
            details.filter { it.contains("${dropped.first} -> ${dropped.second}") }.shouldNotBeEmpty()
        }
    }

    /**
     * D-6G2b-24·30(cr M-4) — 수입된 production 모집단을 고정한다. 모듈 하나가 런타임 classpath 에서 빠지면
     * `roots=bidvector` 는 그대로라 모든 등식이 **공허하게** 초록이 된다.
     *
     * 기대값은 test 상수가 아니라 정책의 `layer.*` 다 — 아래 바깥 참조 판정 대상도 같은 자리에서 읽으므로
     * 모듈을 빠뜨리는 편집은 둘을 **함께** 붉게 만든다.
     */
    @Test
    fun `수입된 production 모듈 집합이 layer 선언과 같다 — 모듈이 빠지면 등식이 공허해진다`() {
        val imported =
            production
                .map { it.packageName }
                .filter { it.startsWith("${policy.packageRoot}.") }
                .map { it.split('.').take(2).joinToString(".") }
                .toSet()

        imported shouldBe policy.allModules.map { "${policy.packageRoot}.$it" }.toSet()
    }

    /**
     * cr L-9 — 정책 파일에 같은 키가 두 번 적히면 `Properties` 가 조용히 마지막만 남겨, 등재 목록 하나가
     * 사라져도 키 집합 등식은 그대로다. 원문 줄에서 센다.
     */
    @Test
    fun `정책 파일에 중복 선언된 키가 없다`() {
        policy.duplicateKeys shouldBe emptySet()
    }

    /** D-6G2b-22 — 모듈별 허용 패키지 키 집합이 닫힌 모듈 목록과 같다. */
    @Test
    fun `바깥 참조 허용 집합의 모듈 키는 닫힌 목록과 같다`() {
        policy.externalModuleKeys shouldBe policy.externalJudgedModules.toSet()
    }

    /** D-6G2b-22 — 모듈별 관측 == 허용(두 방향). 쓰이지 않는 허용 패키지도 RED 다. */
    @Test
    fun `모듈별 바깥 참조 패키지는 허용 집합과 같다 — 두 방향`() {
        policy.externalJudgedModules.forEach { module ->
            val root = "${policy.packageRoot}.$module"
            withClue("모듈 $module") {
                rules.observedExternalPackages(production, root) shouldBe
                    policy.externalAllowedPackages(module).toSet()
            }
        }
    }

    @Test
    fun `허용 밖 바깥 패키지를 참조하는 production 클래스가 없다`() {
        policy.externalJudgedModules.forEach { module ->
            rules
                .externalReferenceRules("${policy.packageRoot}.$module", policy.externalAllowedPackages(module).toSet())
                .forEach { rule -> rule.check(production) }
        }
    }

    /**
     * 양성 대조 — 허용 집합에서 패키지 하나를 빼면 **정확히 그 패키지**가 위반으로 나온다. 고정 문자열이
     * 아니라 정책을 읽은 결과에서 뺀다.
     */
    @Test
    fun `허용 패키지 하나를 빼면 그 패키지가 위반으로 나온다 — 양성 대조`() {
        val module = policy.externalJudgedModules.first()
        val root = "${policy.packageRoot}.$module"
        val allowed = policy.externalAllowedPackages(module).toSet()
        val dropped = allowed.first()

        val details =
            rules
                .externalReferenceRules(root, allowed - dropped)
                .flatMap { it.evaluate(production).failureReport.details }

        withClue("빼낸 패키지 $dropped") { details.filter { it.endsWith(" -> $dropped") }.shouldNotBeEmpty() }
    }

    /** 양성 대조 — 관측에 없는 패키지를 허용에 더하면 두 방향 등식이 그것을 낸다. */
    @Test
    fun `관측에 없는 허용 패키지를 더하면 등식이 그것을 낸다 — 양성 대조`() {
        val module = policy.externalJudgedModules.first()
        val root = "${policy.packageRoot}.$module"
        val ghost = "com.example.unused"

        val observed = rules.observedExternalPackages(production, root)

        ((policy.externalAllowedPackages(module).toSet() + ghost) - observed) shouldBe setOf(ghost)
    }

    /**
     * 양성 대조 — 호출 대상의 **인자·반환 타입** 수집이 production 에서 실제로 쌍을 더한다. 수집이 조용히
     * 소유 타입만 보는 쪽으로 되돌려지면 이 단언이 RED 가 된다(KA1 의 형태가 거기서 열렸다).
     */
    @Test
    fun `호출 인자·반환 타입 수집이 production 에서 쌍을 더한다 — 양성 대조`() {
        val full = rules.observedPairs(production, policy.transportRoots)
        val shallow = ownerOnly.observedPairs(production, policy.transportRoots)

        (full - shallow).shouldNotBeEmpty()
        (shallow - full).shouldBeEmpty()
    }

}
