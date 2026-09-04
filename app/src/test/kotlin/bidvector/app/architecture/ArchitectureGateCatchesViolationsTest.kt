package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * 경계 게이트의 **음성** 쪽 — `milestone-1.md` 「완료 조건」이 요구하는 *"일부러 넣은 test fixture가 실제로
 * 실패"* 다. `ADR 0006` §4 — *"게이트가 있다는 주장이 아니라 게이트가 잡는다는 증거가 기준"*.
 *
 * 규칙을 fixture 전용으로 새로 쓰지 않는다. production 을 지키는 **같은 규칙 값**에 fixture
 * 루트를 넣어 평가하므로, 이 단언이 참이면 production 게이트가 그 위반을 잡는다는 뜻이다.
 * 위반 목록에서 심어 둔 타입 이름을 확인하므로, fixture 가 더 이상 위반을 표현하지 않게 되면
 * 「다른 위반이 대신 잡혀서」 통과하는 일이 없다.
 *
 * `ArchRule.evaluate` 는 빌드를 깨뜨리지 않고 구조화된 결과를 준다 — fixture 는 컴파일되지만
 * production classpath 에는 오르지 않는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArchitectureGateCatchesViolationsTest {
    private val policy = ArchitecturePolicy.load()
    private val rules = ArchitectureRules(policy)
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)

    /**
     * **가족마다 하나씩 심는다.** 한 가족(Spring)만 확인하면 나머지 금지 목록이 실제로 구속하는지
     * 알 수 없고, 목록에 좌표 하나를 빠뜨린 것도 드러나지 않는다 — `java.net.http` 만 적으면
     * `java.net.HttpURLConnection` 이 통과하는 식이다.
     */
    @ParameterizedTest(name = "{0} ← {1}")
    @CsvSource(
        "FrameworkLeak,org.springframework",
        "PersistenceLeak,jakarta.persistence",
        "SerializationLeak,tools.jackson",
        "HttpLeak,java.net",
        "BrokerLeak,jakarta.jms",
        "SqlLeak,java.sql",
        "FileIoLeak,java.io",
        "GrpcLeak,io.grpc",
        "ProtobufLeak,com.google.protobuf",
        "ChannelLeak,java.nio.channels",
        "ConsoleIoLeak,kotlin.io",
        "AllowListFileIoLeak,java.util.Formatter",
        "ResourceBundleLeak,java.util.ResourceBundle",
        "AmbientEnvLeak,java.util.Locale",
        "ReflectionLeak,kotlin.reflect",
    )
    fun `금지 가족마다 심은 위반을 그 사유로 잡는다`(
        fixture: String,
        forbiddenTarget: String,
    ) {
        rules.domainMayOnlyDependOnAllowedPackages(fixtureRoot).mustReport(fixture, forbiddenTarget)
    }

    /**
     * T-D 는 클래스가 아니라 **멤버**로 잡으므로 사유 문자열이 다르다 — 따로 단언한다.
     * 목록은 손 열거가 아니라 `memberEffectGate` 가 도출한 후보의 분류이며, 아래 넷은 그
     * 도출이 스스로 낸 좌표다(시스템 프로퍼티 · 예외의 I/O · 비결정성 · 실행 스택).
     */
    @ParameterizedTest(name = "{0} ← {1}")
    @CsvSource(
        "SystemPropertyLeak,getBoolean",
        "ExceptionIoLeak,printStackTrace",
        "NondeterminismLeak,random",
        "NondeterminismLeak,shuffle",
        "StackTraceLeak,getStackTrace",
    )
    fun `허용된 클래스 안의 금지 멤버를 잡는다`(
        fixture: String,
        forbiddenMember: String,
    ) {
        rules.domainMayOnlyDependOnAllowedPackages(fixtureRoot).mustReport(fixture, forbiddenMember)
    }

    /**
     * **구체 예외 타입을 거쳐도 잡힌다.** 바이트코드의 owner 가 `IllegalStateException` 이므로
     * owner 로 재면 이 접근이 통과한다 — 그래서 선언 클래스로 잰다.
     */
    @Test
    fun `상속으로 물려받은 금지 멤버도 선언 클래스로 잡는다`() {
        rules
            .domainMayOnlyDependOnAllowedPackages(fixtureRoot)
            .mustReport("ExceptionIoLeak.reportConcrete", "printStackTrace")
    }

    /**
     * **양성 쪽.** 실제 도메인이 쓸 형태(`data class`·nullable·컬렉션·`sealed`·`when`·비교)가
     * 같은 규칙을 통과해야 한다. 이것이 없으면 게이트는 도메인이 비어 있는 동안만 초록이다.
     */
    @Test
    fun `실제 도메인 형태는 통과한다`() {
        val allowed = ClassFileImporter().importPackages("${policy.packageRoot}.archfixture.allowed")
        val violations =
            rules
                .domainMayOnlyDependOnAllowedPackages("${policy.packageRoot}.archfixture.allowed")
                .flatMap { rule -> rule.evaluate(allowed).failureReport.details }
        violations.shouldBeEmpty()
    }

    /**
     * **실측이 알려진 제한 11 의 전제를 정정한다.** 그 항목은 상수 풀의 Class 엔트리가 ArchUnit
     * 의존으로 기록되지 않는다고 적었다(`javap` 기준). 이 fixture 로 재보면 ArchUnit 은 class
     * literal 을 `references class object` 라는 **별도 의존 종류**로 추적한다 — 바이트코드
     * 상수 풀의 모양과 ArchUnit 의 의미 모델은 다른 층이다. `checklist.md` 가 이 실측으로
     * 정정된다.
     */
    @Test
    fun `class literal 은 바이트코드 층도 별도 의존 종류로 잡는다`() {
        rules
            .domainMayOnlyDependOnAllowedPackages(fixtureRoot)
            .mustReport("ClassLiteralLeak", "HttpURLConnection")
    }

    /**
     * **사각의 양성 고정.** 알려진 제한 7 — 컴파일 시 인라인되는 상수는 바이트코드에 타입
     * 참조를 남기지 않아 이 규칙이 못 본다(위 테스트가 보이듯 class literal 은 이 사각이
     * 아니라 뺐다). 그 주장을 evidence 산문이 아니라 이 단언이 고정한다: 두 fixture 이름이
     * 위반 상세 어디에도 없어야 한다. 이 사각을 덮는 것은 `domainSourceReferenceGate`(소스 층)다.
     */
    @Test
    fun `인라인 상수는 바이트코드 층이 보고하지 않는다`() {
        val details =
            rules
                .domainMayOnlyDependOnAllowedPackages(fixtureRoot)
                .flatMap { rule ->
                    rule
                        .allowEmptyShould(true)
                        .evaluate(violating)
                        .failureReport.details
                }
        listOf("InlinedConstantLeak", "FullyQualifiedReferenceLeak", "ShadowedRootLeak").forEach { fixture ->
            details.filter { it.contains(fixture) }.shouldBeEmpty()
        }
    }

    /**
     * **사각의 양성 고정 — raw `Double` public API.** 사각의 이유는 fixture 형태마다 다르다
     * (`javap` 로 확인) — `rate(): Double` 처럼 수식 없는 반환은 JVM primitive `D` 라 클래스
     * 참조가 아예 안 남는다. `ratio: Double?`·`weights(List<Double>)` 는 boxing·제네릭 소거로
     * `java.lang.Double`/`Number` 참조가 **남지만**, 그 좌표는 `class.allowed.api` 가 이미 여는
     * T-C 클래스라 이 규칙이 위반으로 세지 않는다 — 「참조가 없어서」와 「참조는 있어도 허용
     * 목록 안이라서」 둘 다 같은 결론(미보고)에 이른다. 이 사각을 덮는 것은
     * `domainApiTypeGate`(타입 표면 층 — 소스의 이름 자체를 본다)다. 소스 참조 층(13차)이 같은
     * fixture 를 못 보는 것은 `build-logic` 자신의 테스트가 든다(app 에서 그 내부 API 를 부를
     * 수 없다).
     */
    @Test
    fun `raw Double public API 는 바이트코드 층이 보고하지 않는다`() {
        val details =
            rules
                .domainMayOnlyDependOnAllowedPackages(fixtureRoot)
                .flatMap { rule ->
                    rule
                        .allowEmptyShould(true)
                        .evaluate(violating)
                        .failureReport.details
                }
        listOf("RawDoubleApi", "AliasedDoubleApi", "TypeAliasDoubleApi").forEach { fixture ->
            details.filter { it.contains(fixture) }.shouldBeEmpty()
        }
    }

    @Test
    fun `업무 모듈 사이의 직접 참조를 잡는다`() {
        rules.businessDomainModulesMustNotReferenceEachOther(fixtureRoot) mustReport "CycleLeft"
    }

    @Test
    fun `역방향 의존을 잡는다`() {
        rules.dependencyDirectionIsOneWay(fixtureRoot) mustReport "UpwardDependency"
    }

    @Test
    fun `패키지 순환을 잡는다`() {
        rules.packagesMustBeFreeOfCycles(fixtureRoot) mustReport "CycleRight"
    }

    @Test
    fun `기술 계층 이름의 패키지를 잡는다`() {
        rules.packageNamesMustNotBeTechnicalLayers(fixtureRoot) mustReport "TechnicalLayerName"
    }

    /**
     * **이름만 보지 않는다.** 위반 상세에 fixture 이름이 있기만 하면 통과하게 두면, 그 클래스가
     * **다른 이유로** 잡혀도 단언이 초록이 된다 — 실제로 컴파일러 삽입 `@NotNull` 이 그 masking 을
     * 만들어, 금지를 정책에서 걷어도 음성 단언이 죽지 않았다. 그래서 **어느 대상 때문에** 잡혔는지를
     * 함께 확인한다.
     */
    private fun List<ArchRule>.mustReport(
        mentioned: String,
        forbiddenTarget: String,
    ) {
        val details =
            flatMap { rule ->
                rule
                    .allowEmptyShould(true)
                    .evaluate(violating)
                    .failureReport.details
            }
        details
            .filter { it.contains(mentioned) && it.contains(forbiddenTarget) }
            .shouldNotBeEmpty()
    }

    private infix fun List<ArchRule>.mustReport(mentioned: String) = mustReport(mentioned, "")
}
