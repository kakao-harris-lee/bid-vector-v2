package bidvector.app.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * DEC-10 게이트 등식의 **음성** 쪽(D-DEC10-6 ⓓ) — production 을 지키는 **같은 술어**에 fixture
 * 루트를 넣어 심은 소비자가 관측 집합에 나타나는지 잰다(`CollectionArchitectureGateCatchesViolationsTest`
 * 와 같은 형태). 어느 **대상 때문에** 잡혔는지를 함께 확인한다 — 다른 이유로 잡혀도 통과하는
 * masking 을 막는다.
 *
 * 심은 소비자가 **production 허용 목록에 없다**는 것도 함께 센다. 그렇지 않으면 「음성 fixture 가
 * 허용되어 있어서」 양성 쪽이 초록인 상태를 구별할 수 없다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FloorTrustGateCatchesViolationsTest {
    private val policy = ArchitecturePolicy.load()
    private val gatedRules = DivisionValueRules(policy.floorTrustGatedType)
    private val rawRules = FloorTrustRules(policy.floorTrustRawType)
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating.floortrust"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)

    private val rawReader = "$fixtureRoot.RogueRawFloorRateReader"
    private val gatedReader = "$fixtureRoot.RogueGatedFloorRateReader"
    private val fractionReader = "$fixtureRoot.RogueFloorFractionReader"
    private val carrier = "$fixtureRoot.RogueFloorRateCarrier"

    @Test
    fun `심은 원값 소비자가 획득 집합에 나타난다 — 대상까지 같다`() {
        val observed = rawRules.observedAcquisitionUnits(violating).withoutSignatures()

        withClue("관측: $observed") {
            observed shouldContain (
                "$rawReader.rawFloorRateOf" to "bidvector.procurement.Notice#getFloorRate"
            )
        }
    }

    @Test
    fun `심은 통과 값 소비자가 멤버 접근 집합에 나타난다 — 대상까지 같다`() {
        val observed = gatedRules.observedTypeAccesses(violating)

        withClue("관측: $observed") {
            observed shouldContain (gatedReader to "getRate")
        }
    }

    /**
     * r1 v H-1 (a) — 획득 축이 **보지 못하는** 두 형태를 멤버 접근 축이 센다. 파생 접근자는
     * 반환이 `BigDecimal` 이라, 운반 클래스는 자기 필드 읽기라 획득 축 밖이다.
     */
    @Test
    fun `파생 접근자와 운반 클래스가 원값 멤버 접근 집합에 나타난다`() {
        val observed = rawRules.observedMemberAccessUnits(violating).withoutSignatures()

        withClue("관측: $observed") {
            observed shouldContain ("$fractionReader.fractionOf" to "getRate")
            observed shouldContain ("$carrier.rate" to "getRate")
        }
    }

    /**
     * **파생 접근자는 획득 축에 없다** — 원값을 **인자로 받아** 숫자만 내보내므로 반환·필드
     * 타입이 원값인 자리가 없다. 그래서 멤버 접근 축을 따로 둔 이유가 이 단언이다.
     *
     * 운반 클래스는 **r4 부터 두 축에 함께** 나타난다(r3 v H-1) — 자기 필드 읽기를 제외하던
     * 규칙을 없애면서, 운반 타입이 자기 값을 내보내는 자리가 획득으로도 세어진다. 그 변화가
     * 바로 「운반 타입의 자기 메서드가 모든 축에서 보이지 않던」 사각을 닫은 것이다.
     */
    @Test
    fun `파생 접근자는 획득 축에 없고 운반 클래스는 두 축에 다 있다`() {
        val acquisitions = rawRules.observedAcquisitionUnits(violating).map { it.first.withoutSignature() }
        val memberAccesses = rawRules.observedMemberAccessUnits(violating).map { it.first.withoutSignature() }

        withClue("획득: $acquisitions · 멤버 접근: $memberAccesses") {
            acquisitions shouldNotContain "$fractionReader.fractionOf"
            acquisitions shouldContain "$carrier.rate"
            // 「두 축에 다」의 둘째 축(CR r4 LOW) — 앞 판은 이 줄이 없어 이름이 단언하지 않는
            // 것을 약속했다. 둘째 축의 존재는 다른 test 에 있었고, 이름이 가리키는 자리에 없었다.
            memberAccesses shouldContain "$carrier.rate"
        }
    }

    @Test
    fun `심은 소비자 넷은 production 허용 목록 밖이다 — 양성 쪽이 음성을 허용해 초록이 된 것이 아니다`() {
        val allowedAcquirers = policy.floorTrustRawAcquisitionPairs.map { it.first.withoutSignature() }
        val allowedAccessors = policy.floorTrustRawTypeAccessPairs.map { it.first.withoutSignature() }

        allowedAcquirers shouldNotContain "$rawReader.rawFloorRateOf"
        policy.floorTrustGatedTypeAccessPairs.map { it.first } shouldNotContain gatedReader
        allowedAccessors shouldNotContain "$fractionReader.fractionOf"
        allowedAccessors shouldNotContain "$carrier.rate"
    }
}

/** 서명을 떼고 `클래스.메서드` 만 본다 — 이 test 가 재는 것은 **어느 함수가 걸렸는가**다. */
private fun String.withoutSignature(): String = substringBefore('(')

private fun Set<Pair<String, String>>.withoutSignatures(): List<Pair<String, String>> =
    map { (unit, target) -> unit.withoutSignature() to target }
