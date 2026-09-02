package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 경계 게이트의 **음성** 쪽 — `milestone-1.md:80` 이 요구하는 *"일부러 넣은 fixture 가 실제로
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
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "FrameworkLeak",
            "PersistenceLeak",
            "SerializationLeak",
            "HttpLeak",
            "BrokerLeak",
            "SqlLeak",
            "FileIoLeak",
            "GrpcLeak",
            "ProtobufLeak",
            "ChannelLeak",
        ],
    )
    fun `금지 가족마다 심은 위반을 잡는다`(fixture: String) {
        rules.domainMustNotDependOnFrameworks(fixtureRoot) mustReport fixture
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

    private infix fun List<ArchRule>.mustReport(mentioned: String) {
        val details =
            flatMap { rule ->
                rule
                    .allowEmptyShould(true)
                    .evaluate(violating)
                    .failureReport.details
            }
        details.filter { it.contains(mentioned) }.shouldNotBeEmpty()
    }
}
