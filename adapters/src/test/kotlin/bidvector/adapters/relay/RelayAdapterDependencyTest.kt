package bidvector.adapters.relay

import bidvector.adapters.architecture.BytecodeConstantPool
import bidvector.adapters.architecture.SELF_COORDINATE
import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F10-18 ①,
 * `EvaluationAdapterDependencyTest`·`StrategyAdapterDependencyTest` 와 같은 형태·같은 이유).
 *
 * **이 패키지가 존재하는 이유가 이 목록이다.** relay 조립은 `workflow.notification`
 * (`DispatchNotification`·정책·환경·`RelayOutboxNotifications`)과 `adapters.event`
 * (`JdbcOutboxPort`·`JdbcInboxPort`·lease·`ConsumerTransactions`)를 **둘 다** 이름으로 불러야
 * 한다. `adapters.event` 에 두면 `EventAdapterDependencyTest` 가 막는데, 그 게이트는
 * `workflow.notification` 을 허용 루트 밖으로 두고 **양성 대조로 명시 단언**한다 — codec 이
 * 알림 타입을 보지 않는다는 것이 그 게이트의 뜻이다. 그 단언을 지우는 것(게이트의 자기 검사를
 * 약화)보다 간선을 새 패키지 안에 가두는 편이 좁다.
 *
 * `workflow.strategy` 는 `OperatorId`(`RelayTarget.owner`), `sharedkernel` 은 정책 해소 값
 * 타입, `adapters.persistence` 는 트랜잭션 경계([bidvector.adapters.persistence.TransactionBoundary])다.
 * `workflow.evaluation`·`procurement`·`decision` 은 **목록에 없다** — relay 는 판정을 다시
 * 조립하지 않고 payload 투영만 읽는다.
 *
 * **M9/STR12 — `adapters.notification` 한 좌표가 늘었다.** 6F-10 의 자리지킴 셋(`Unavailable*`)이
 * 있던 자리를 실 발송 축 셋(route·renderer·sender)이 대체했고, 그 셋은 이 패키지가 아니라 자기
 * 패키지에 산다. 그 패키지를 여기 두지 **않은** 이유가 이 목록의 뜻이다 — 이 목록은 「**조립**이
 * 보는 것」이고, 거기에 HTTP 전송·공고 표 읽기를 더하면 그 등식이 무엇을 말하는지 흐려진다.
 * 늘어난 간선은 **조립 → 구현** 한 방향뿐이다: 반대 방향은 `NotificationAdapterDependencyTest`
 * 가 막는다(그 허용 루트에 `adapters.event`·`adapters.relay` 가 없다).
 */
internal val ALLOWED_ROOTS: Set<String> =
    java.util.Set.copyOf(
        setOf(
            "bidvector.workflow.event",
            "bidvector.workflow.notification",
            "bidvector.workflow.strategy",
            "bidvector.sharedkernel",
            "bidvector.adapters.event",
            "bidvector.adapters.notification",
            "bidvector.adapters.persistence",
            "bidvector.adapters.relay",
        ),
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

/**
 * **이 축의 판정은 이 class 에 없다** — [bidvector.adapters.architecture.ScopedBytecodeLayerGateTest] 가 표를 순회하며 한다(M12/12A ④).
 * 여기 남은 것은 이 축에만 있는 주장이고, 아래는 그 판정의 술어가 **상수 풀**인 이유다.
 *
 * **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v` 로 훑는다**(D-6F2-1 과 같은
 * 근거). import 문 없이 전체 한정 좌표로 직접 참조하는 우회를 소스 텍스트 정규식 형태는 보지
 * 못한다(6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다.
 */
class RelayAdapterDependencyTest {
    /**
     * cr L-7 — **추출 층의 양성 대조.** 아래 두 대조는 문자열 술어만 재고 추출
     * (`javap` 출력 → 정규식)은 재지 않는다. 추출이 조용히 아무것도 못 뽑게 되면(javap 출력
     * 형태 변화·경로 오류) 위반 목록이 빈 목록이 되어 게이트가 영구 초록이다 —
     * `classFiles.shouldNotBeEmpty()` 는 빈 디렉터리만 막는다.
     *
     * 그래서 추출이 **실제로 뽑는다**는 증거를 둔다: relay 패키지 바이트코드에서 뽑은 좌표
     * 집합이 허용 루트 안의 알려진 이름을 담는다.
     */
    @Test
    fun `상수 풀 추출이 허용 루트 안의 알려진 좌표를 실제로 뽑는다 — 추출 층 양성 대조`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/relay")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val extracted =
            classesDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .flatMap { classFile ->
                    SELF_COORDINATE
                        .findAll(BytecodeConstantPool.bodyOf(classFile))
                        .map { it.value.replace('/', '.') }
                }.toSet()

        extracted shouldContain "bidvector.workflow.notification.RelayOutboxNotifications"
        extracted shouldContain "bidvector.adapters.event.JdbcOutboxPort"
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.event") shouldBe false
        isDisallowed("bidvector.workflow.notification") shouldBe false
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.event") shouldBe false
        isDisallowed("bidvector.adapters.notification") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.relay") shouldBe false
    }

    /**
     * D-6F10-2 「사본 SQL 0」의 참조 단언 — 조립은 종단 전이를 **port 로만** 지난다. 이
     * 패키지의 어느 class 도 `EventSql` 을 참조하지 않는다(6D-1 의 test relay 가 raw
     * `MARK_DELIVERED` 를 직접 실행한 자리를 반복하지 않는다). 상수 풀이라 import 를 쓰든
     * 전체 한정 좌표를 쓰든 같다.
     */
    @Test
    fun `relay 패키지는 전이 SQL 상수를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/relay")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty()

        val sqlReferences =
            classFiles.flatMap { classFile ->
                SELF_COORDINATE
                    .findAll(BytecodeConstantPool.bodyOf(classFile))
                    .map { it.value.replace('/', '.') }
                    .filter { it.startsWith("bidvector.adapters.event.EventSql") }
                    .toList()
            }

        sqlReferences shouldBe emptyList()
    }
}
