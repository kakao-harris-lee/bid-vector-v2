package bidvector.guardless

import bidvector.app.BidVectorApplication
import bidvector.app.http.GlobalErrorHandler
import bidvector.guardprobe.GuardProbeApplication
import bidvector.guardprobe.GuardProbeController
import bidvector.guardprobe.H4Controller
import bidvector.guardprobe.H5Controller
import bidvector.guardprobe.PROBE_SHAPES
import bidvector.guardprobe.ProbeConversion
import bidvector.guardprobe.Reached
import bidvector.guardprobe.callProbe
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import

/**
 * **양성 대조 — 가드를 빼면 같은 모양이 핸들러에 닿는다**(M7/7B 계약 D-7B-8 ③ · 검토 r6 R6-3).
 *
 * `AccountBindingGuardTest` 의 「모양마다 400 · 도달 0」은 그 모양의 경로가 실재하고 가드가 없으면 `AccountId` 가 지어진다는 것을
 * 이 test 가 보일 때만 뜻이 있다. 이 조립은 같은 probe controller · 위협 흉내 변환기 · 오류 처리기를 **가드 없이** 올린다(컴포넌트
 * 스캔 없이 `@Import` 만 — `bidvector.guardprobe` 의 가드 조립이 끼어들지 않는다). 모양마다 200 이고 도달이 정확히 1 이다.
 * 예외 하나: `Map` 의 키(MK)는 가드가 없으면 Jackson 이 `AccountId` 키 역직렬화기를 찾지 못해 500 이다(지어지지 않는다) — 이
 * 모양의 대조는 「404 · 405 가 아니다(매핑이 있다)」까지이고, 가드가 그것을 400 으로 맞춘다(검토 r6 V6-L1).
 *
 * **격리**(검토 r7 R7-3): 이 조립은 `@SpringBootConfiguration` 이라 출하 조립이 중첩 조립을 빼는 제외 필터(`@SpringBootApplication`
 * 애너테이션)에 걸리지 않는다. 그래서 격리는 **패키지가 진다** — 이 조립과 가드 probe 조립은 출하 스캔 뿌리(`BidVectorApplication` 의
 * 패키지, HTTP test 조립의 `bidvector.app.http` 도 그 아래)의 **밖**에 있어야 한다. 아래 test 가 그것을 단언한다(옮기면 붉다).
 */
@SpringBootTest(
    classes = [GuardlessProbeApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
class GuardlessProbeTest {
    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = TestRestTemplate()

    @Test
    fun `가드를 빼면 AccountId 를 싣는 모양이 핸들러에 닿는다 — 양성 대조`() {
        val observed =
            PROBE_SHAPES.associate { shape ->
                Reached.count.set(0)
                val status = callProbe(restTemplate, port, shape)
                shape.name to (status to Reached.count.get())
            }

        observed shouldBe
            PROBE_SHAPES.associate { shape ->
                shape.name to if (shape.path.startsWith(MAP_KEY_PATH)) (SERVER_ERROR to 0) else (OK to 1)
            }
    }

    @Test
    fun `가드 probe 조립과 가드 없는 조립은 출하 스캔 뿌리 밖에 있다 — 격리`() {
        val root = BidVectorApplication::class.java.packageName
        listOf(
            GuardlessProbeApplication::class.java,
            GuardProbeApplication::class.java,
            GuardProbeController::class.java,
        ).map { it.packageName }
            .filter { it == root || it.startsWith("$root.") } shouldBe emptyList()
    }

    private companion object {
        const val OK = 200
        const val SERVER_ERROR = 500
        const val MAP_KEY_PATH = "/probe/mapkey"
    }
}

/** 가드 없는 조립 — probe controller · 위협 흉내 변환기 · 공유 오류 처리기만. 나머지는 Boot 자동 구성. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = [DataSourceAutoConfiguration::class])
@Import(
    GuardProbeController::class,
    H4Controller::class,
    H5Controller::class,
    ProbeConversion::class,
    GlobalErrorHandler::class,
)
open class GuardlessProbeApplication
