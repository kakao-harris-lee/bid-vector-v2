package bidvector.app.policy

import bidvector.decision.PUBLISHED_FLOOR_TRUST_POLICY
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.math.BigDecimal

private const val LOW_KEY = "floor.rate_band_low"
private const val HIGH_KEY = "floor.rate_band_high"

/**
 * DEC-10 값 일치(D-DEC10-1·D-DEC10-11) — Kotlin 출하 정책의 개연 밴드와 Python 백테스트 정책의
 * `floor.rate_band_*` 가 **같은 수**여야 한다. 두 층이 같은 밴드를 각자 적고 있고(Python 쪽은
 * 사전 등록 실험이라 이 slice 가 바꾸지 않는다), 한쪽만 움직이면 **백테스트가 제외한 공고와
 * 운영이 제외하는 공고가 갈린다** — 그러면 백테스트 수치가 운영 거동의 근거가 되지 못한다.
 *
 * 비교는 `compareTo` 다 — `0.30`(Kotlin 문자열)과 `0.3`(yaml 숫자)은 **같은 수**이고 척도만
 * 다르다. `equals` 로 재면 척도 차이가 값 불일치로 보인다.
 */
class FloorTrustBandParityTest {
    private val pythonPolicy: Map<*, *> by lazy {
        val path =
            requireNotNull(System.getProperty("bidvector.ml.backtest-policy")) {
                "bidvector.ml.backtest-policy system property 가 없다 — app/build.gradle.kts 의 주입을 보라"
            }
        Yaml().load<Map<*, *>>(File(path).readText())
    }

    private val shippedBand by lazy {
        PUBLISHED_FLOOR_TRUST_POLICY.entries
            .single()
            .second
            .plausibilityBand
    }

    @Test
    fun `밴드 하한이 Python 백테스트 정책과 같은 수다`() {
        val python = BigDecimal(pythonPolicy[LOW_KEY].toString())

        withClue("Kotlin=${shippedBand.min} Python=$python") {
            shippedBand.min.compareTo(python) shouldBe 0
        }
    }

    @Test
    fun `밴드 상한이 Python 백테스트 정책과 같은 수다`() {
        val python = BigDecimal(pythonPolicy[HIGH_KEY].toString())

        withClue("Kotlin=${shippedBand.max} Python=$python") {
            shippedBand.max.compareTo(python) shouldBe 0
        }
    }

    /**
     * 공허한 통과 방지 — 키가 사라지면 `null.toString()` 이 `"null"` 이 되어 `BigDecimal` 생성이
     * 터지므로 위 둘이 **실패로** 바뀐다. 그 전건(키가 실제로 있다)을 여기서 따로 센다.
     */
    @Test
    fun `Python 정책에 두 키가 실제로 있다 — 등식이 공허하지 않다`() {
        pythonPolicy[LOW_KEY] shouldNotBe null
        pythonPolicy[HIGH_KEY] shouldNotBe null
    }

    /** 상한이 1 미만인 것이 B6 의 요점이다(하한율 `1.00000` 라이브 47건이 하한으로 성립하지 않는다). */
    @Test
    fun `출하 밴드 상한은 1 미만이고 하한은 0 보다 크다`() {
        withClue("밴드=[${shippedBand.min}, ${shippedBand.max}]") {
            (shippedBand.max < BigDecimal.ONE) shouldBe true
            (shippedBand.min > BigDecimal.ZERO) shouldBe true
        }
    }
}
