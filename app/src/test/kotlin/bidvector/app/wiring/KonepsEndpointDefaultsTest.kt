package bidvector.app.wiring

import bidvector.app.collection.KonepsEndpointProperties
import bidvector.app.collection.KonepsOpeningEndpointProperties
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration

/**
 * M9/portal-02 D-1 — 조달청 입찰공고정보서비스 개편(data.go.kr 공지 2026-11-03,
 * NOTICE_0000000005128)의 **이름 전환**을 기본값 문면으로 잠근다. BASE URL 은
 * `…/ad/BidPublicInfoService02` 이고 그 서비스의 오퍼레이션 다섯은 이름 뒤에 `02` 가 붙었다.
 * 옛 URL 은 공지일로부터 90일만 산다.
 *
 * **낙찰정보서비스(`as/ScsbidInfoService`)는 공지 대상이 아니다** — 그쪽 기본값이 함께 끌려가면
 * 그 절반이 404 가 되므로 불변을 같은 자리에서 잰다.
 *
 * 설정으로 읽는 값이라 **바인딩을 지나서** 본다: 데이터 클래스의 기본 인자를 직접 읽으면 Spring 이
 * 그 값을 실제로 쓰는지는 재지 못한다. 기본값이 호출에 실려 나가는 것(wire)은 수집 E2E 가 mock
 * KONEPS 의 정확 일치 표로 잰다(`MockKonepsRoutingTest` · `OpeningCollectionE2ETest`).
 */
class KonepsEndpointDefaultsTest {
    @Test
    fun `입찰공고정보서비스 기본 base 는 02 개편판이다`() {
        withDefaults { notice, _ ->
            notice.baseUrl shouldBe "https://apis.data.go.kr/1230000/ad/BidPublicInfoService02"
        }
    }

    @Test
    fun `공고 목록 기본 경로 둘은 02 다 — 표가 더 자라도 이 등식이 본다`() {
        withDefaults { notice, _ ->
            notice.operations.mapValues { (_, operation) -> operation.path } shouldBe
                mapOf(
                    "construction" to "getBidPblancListInfoCnstwk02",
                    "service" to "getBidPblancListInfoServc02",
                )
        }
    }

    @Test
    fun `A값과 기초금액 기본 경로 셋은 02 다 — 같은 서비스의 오퍼레이션이다`() {
        withDefaults { _, opening ->
            opening.bidPriceFormulaAPath shouldBe "getBidPblancListBidPrceCalclAInfo02"
            opening.operations.mapValues { (_, operation) -> operation.baseAmountPath } shouldBe
                mapOf(
                    "construction" to "getBidPblancListInfoCnstwkBsisAmount02",
                    "service" to "getBidPblancListInfoServcBsisAmount02",
                )
        }
    }

    @Test
    fun `낙찰정보서비스 기본값은 불변이다 — 공지 대상이 아니라 02 를 붙이면 404 가 된다`() {
        withDefaults { _, opening ->
            opening.scsbidBaseUrl shouldBe "https://apis.data.go.kr/1230000/as/ScsbidInfoService"
            opening.openingCompletePath shouldBe "getOpengResultListInfoOpengCompt"
            opening.operations.mapValues { (_, operation) -> operation.openingResultListPath } shouldBe
                mapOf(
                    "construction" to "getOpengResultListInfoCnstwk",
                    "service" to "getOpengResultListInfoServc",
                )
            opening.operations.mapValues { (_, operation) -> operation.reservePriceDetailPath } shouldBe
                mapOf(
                    "construction" to "getOpengResultListInfoCnstwkPreparPcDetail",
                    "service" to "getOpengResultListInfoServcPreparPcDetail",
                )
        }
    }

    /**
     * 위 세 test 가 문면을 열거하고 이것은 **경계**를 잰다 — 두 서비스의 기본 경로 집합을 통째로 갈라,
     * 한쪽에만 `02` 가 있고 다른 쪽에는 없음을 본다. 기본 표에 업종 행이 더해질 때 열거를 고치는 것을
     * 잊어도 이 등식이 먼저 걸린다(열거만 두면 새 행은 어느 단언도 지나지 않는다).
     */
    @Test
    fun `02 는 입찰공고정보서비스 축에만 붙는다 — 두 서비스의 기본 경로 집합이 갈린다`() {
        withDefaults { notice, opening ->
            val bidPublicInfo =
                notice.operations.values.map { it.path } +
                    opening.operations.values.map { it.baseAmountPath } +
                    opening.bidPriceFormulaAPath
            val scsbidInfo =
                opening.operations.values.flatMap { listOf(it.openingResultListPath, it.reservePriceDetailPath) } +
                    opening.openingCompletePath

            bidPublicInfo.filterNot { it.endsWith(REVISION_SUFFIX) }.shouldContainExactly(emptyList())
            scsbidInfo.filter { it.endsWith(REVISION_SUFFIX) }.shouldContainExactly(emptyList())
        }
    }

    private fun withDefaults(block: (KonepsEndpointProperties, KonepsOpeningEndpointProperties) -> Unit) {
        AnnotationConfigApplicationContext().use { context ->
            context.register(KonepsDefaultsBinding::class.java)
            context.refresh()
            block(
                context.getBean(KonepsEndpointProperties::class.java),
                context.getBean(KonepsOpeningEndpointProperties::class.java),
            )
        }
    }
}

/** 속성을 하나도 주지 않고 **바인딩만** 세운다 — 남는 값이 곧 기본값이다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KonepsEndpointProperties::class, KonepsOpeningEndpointProperties::class)
internal class KonepsDefaultsBinding

private const val REVISION_SUFFIX = "02"
