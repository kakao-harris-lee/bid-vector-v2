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
     * 알 수 없고, 목록에 좌표 하나를 빠뜨린 것도 드러나지 않는다 — Codex #3 이 지적한 형태다
     * (`java.net.http` 만 적어 `java.net.HttpURLConnection` 이 통과했다).
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
    )
    fun `금지 가족마다 심은 위반을 그 사유로 잡는다`(
        fixture: String,
        forbiddenTarget: String,
    ) {
        rules.domainMayOnlyDependOnAllowedPackages(fixtureRoot).mustReport(fixture, forbiddenTarget)
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
