package bidvector.adapters.notification

import bidvector.adapters.architecture.BytecodeConstantPool
import bidvector.adapters.architecture.SELF_COORDINATE
import bidvector.adapters.architecture.isOutsideRoots
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트 — `RelayAdapterDependencyTest` 와 같은
 * 형태·같은 이유(패키지마다 자기 게이트, `adapters` test 의 여덟 선례).
 *
 * **이 패키지가 따로 있는 이유가 이 목록이다.** 발송 축은 `workflow.notification` 의 port 와
 * `adapters.persistence` 의 연결 경계를 보고, `relay` 조립은 거기에 `adapters.event`(outbox·
 * inbox·임대)를 더해 본다. 둘을 한 패키지에 두면 `relay` 의 허용 루트가 「조립이 보는 것」과
 * 「발송이 보는 것」을 함께 담아 무엇을 말하는지 흐려진다.
 *
 * 목록에 **없는** 것: `procurement`·`decision`·`workflow.evaluation`·`adapters.koneps` — 발송 축은
 * 판정을 다시 조립하지 않고 공고 표의 **표시 칸**만 읽는다(그래서 공고 사실 값 타입도 이 패키지
 * 자신의 것이다).
 */
internal val ALLOWED_ROOTS: Set<String> =
    java.util.Set.copyOf(
        setOf(
            "bidvector.workflow.notification",
            "bidvector.workflow.event",
            "bidvector.workflow.strategy",
            "bidvector.adapters.persistence",
            "bidvector.adapters.notification",
        ),
    )

private fun isDisallowed(coordinate: String): Boolean = isOutsideRoots(coordinate, ALLOWED_ROOTS)

/**
 * 로깅 API 좌표 — 이 패키지는 **하나도 쥐지 않는다**. 누출 방어의 로그 축을 문자열 검사가 아니라
 * 구조로 닫는 자리다(`TelegramCredentialLeakTest` KDoc): 로거가 없으면 흘릴 자리가 없고, 로거를
 * 들이는 편집이 **이 단언에서** 붉어져 그때 누출 축을 다시 보게 된다.
 */
private val LOGGING_ROOTS =
    listOf(
        "org[/.]slf4j",
        "java[/.]util[/.]logging",
        "org[/.]apache[/.]log(ging|4j)",
        "ch[/.]qos[/.]logback",
    )

private val LOGGING_NAME = Regex("""(${LOGGING_ROOTS.joinToString("|")})[A-Za-z0-9_/.$]*""")

private val CLASSES_DIR = File("build/classes/kotlin/main/bidvector/adapters/notification")

private fun productionClassFiles(): List<File> {
    check(CLASSES_DIR.isDirectory) {
        "빌드 산출물을 찾지 못했다: ${CLASSES_DIR.absolutePath} — :adapters:compileKotlin 선행 필요"
    }
    val files = CLASSES_DIR.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    files.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.
    return files
}

/**
 * **이 축의 판정은 이 class 에 없다** — [bidvector.adapters.architecture.ScopedBytecodeLayerGateTest] 가 표를 순회하며 한다(M12/12A ④).
 * 여기 남은 것은 이 축에만 있는 주장이고, 아래는 그 판정의 술어가 **상수 풀**인 이유다.
 *
 * **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v` 로 훑는다** — import 문 없이
 * 전체 한정 좌표로 직접 참조하는 우회를 소스 정규식은 보지 못한다(6F-1 verifier MUT-E3 실측).
 */
class NotificationAdapterDependencyTest {
    /**
     * **추출 층의 양성 대조** — 추출이 조용히 아무것도 못 뽑게 되면(javap 출력 형태 변화·경로 오류)
     * 위반 목록이 빈 목록이 되어 게이트가 영구 초록이다.
     */
    @Test
    fun `상수 풀 추출이 허용 루트 안의 알려진 좌표를 실제로 뽑는다 — 추출 층 양성 대조`() {
        val extracted =
            productionClassFiles().flatMap { coordinatesOf(it, SELF_COORDINATE) }.toSet()

        extracted shouldContain "bidvector.workflow.notification.NotificationSender"
        extracted shouldContain "bidvector.workflow.notification.ContentRenderer"
        extracted shouldContain "bidvector.workflow.notification.RouteDirectory"
    }

    /**
     * 누출 방어의 **로그 축**(D-3) — 이 패키지의 어느 클래스도 로깅 API 를 쥐지 않는다. 발송 축의
     * 관측은 `RelayReport` 계수와 러너 로그가 지고, 그 둘은 자격·chat id 를 손에 쥐지 않는다.
     */
    @Test
    fun `notification 패키지는 로깅 API 를 참조하지 않는다`() {
        val logging =
            productionClassFiles().flatMap { coordinatesOf(it, LOGGING_NAME) }.distinct()

        logging shouldBe emptyList()
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        ALLOWED_ROOTS.forEach { root -> isDisallowed(root) shouldBe false }
        isDisallowed("bidvector.workflow.notification.DeliveryResult") shouldBe false
    }

    /** 로깅 술어의 양성 대조 — 정규식이 실제로 로깅 좌표를 잡는다. */
    @Test
    fun `로깅 술어는 알려진 로깅 좌표를 잡는다 — 양성 대조`() {
        LOGGING_NAME.containsMatchIn("org/slf4j/Logger") shouldBe true
        LOGGING_NAME.containsMatchIn("org.slf4j.LoggerFactory") shouldBe true
        LOGGING_NAME.containsMatchIn("java/util/logging/Logger") shouldBe true
        LOGGING_NAME.containsMatchIn("ch/qos/logback/classic/Logger") shouldBe true
        LOGGING_NAME.containsMatchIn("java/net/http/HttpClient") shouldBe false
    }
}

private fun coordinatesOf(
    classFile: File,
    shape: Regex,
): List<String> =
    shape
        .findAll(BytecodeConstantPool.bodyOf(classFile))
        .map { it.value.replace('/', '.') }
        .toList()
