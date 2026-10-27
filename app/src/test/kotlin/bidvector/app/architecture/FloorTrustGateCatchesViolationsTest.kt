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
    private val rawRules = DivisionValueRules(policy.floorTrustRawType)
    private val fixtureRoot = "${policy.packageRoot}.archfixture.violating.floortrust"
    private val violating: JavaClasses = ClassFileImporter().importPackages(fixtureRoot)

    private val rawReader = "$fixtureRoot.RogueRawFloorRateReader"
    private val gatedReader = "$fixtureRoot.RogueGatedFloorRateReader"

    @Test
    fun `심은 원값 소비자가 획득 집합에 나타난다 — 대상까지 같다`() {
        val observed = rawRules.observedAcquisitions(violating)

        withClue("관측: $observed") {
            observed shouldContain (rawReader to "bidvector.procurement.Notice#getFloorRate")
        }
    }

    @Test
    fun `심은 통과 값 소비자가 멤버 접근 집합에 나타난다 — 대상까지 같다`() {
        val observed = gatedRules.observedTypeAccesses(violating)

        withClue("관측: $observed") {
            observed shouldContain (gatedReader to "getRate")
        }
    }

    @Test
    fun `심은 소비자 둘은 production 허용 목록 밖이다 — 양성 쪽이 음성을 허용해 초록이 된 것이 아니다`() {
        policy.floorTrustRawAcquisitionPairs.map { it.first } shouldNotContain rawReader
        policy.floorTrustGatedTypeAccessPairs.map { it.first } shouldNotContain gatedReader
    }
}
