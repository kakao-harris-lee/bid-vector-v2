package bidvector.adapters.retention

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `FROM raw_observation` 질의 한 덩어리 — 세미콜론·문장 경계가 없는 Kotlin 문자열 조립이라
 * `SELECT` 부터 그 표 이름까지를 한 매치로 잡는다. 대소문자를 가리지 않고 줄바꿈을 넘는다.
 */
private val SELECT_FROM_RAW_OBSERVATION =
    Regex(
        """SELECT\b[^;]{0,2000}?\bFROM\s+raw_observation\b""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

/**
 * **열 이름 `payload` 하나**를 가리키는 토큰 경계 — `payload_fields`·`state_payload` 는 `_` 가
 * 단어 문자라 이 경계에 걸리지 않는다(그 둘이 이 술어의 주된 오탐원이다).
 */
private val BARE_PAYLOAD_COLUMN = Regex("""(?<![\w])payload(?![\w])""")

/**
 * D-6B3-1 ④ — **`raw_observation.payload` 를 읽는 production 자리가 0 임을 잠근다.**
 *
 * 왜 이 test 가 필요한가: 마스킹의 allow-list 는 그 행의 투영 열(`payload_fields`) 키 집합이고,
 * 그 선택이 안전한 근거는 **아무도 `payload` 를 읽지 않는다**는 사실이다. 누가 읽기 시작하면
 * 그 근거가 사라지고 「allow-list ⊇ 소비자 읽기 키 집합」 검사가 필요해진다 — 그 순간을
 * 이 test 가 RED 로 알린다. 읽는 자리가 생겨도 조용히 지나가면 마스킹이 그 소비자의 필드를
 * 지운다.
 *
 * **공허 RED 대조**를 함께 둔다: 수집 모집단이 비면 이 test 는 아무것도 잠그지 않는다(표 이름이
 * 바뀌거나 질의가 다른 형태로 조립되면 조용히 0 건이 된다). 그래서 「`FROM raw_observation`
 * 질의가 적어도 하나 수집된다」를 **먼저** 단언한다.
 */
class RawPayloadProductionReaderTest {
    @Test
    fun `공허 대조 — 모듈 production 소스 루트가 수집된다`() {
        productionSourceRoots().shouldNotBeEmpty()
    }

    @Test
    fun `공허 대조 — FROM raw_observation 질의가 적어도 하나 수집된다`() {
        selectsFromRawObservation().shouldNotBeEmpty()
    }

    @Test
    fun `그 질의 가운데 payload 열을 읽는 것은 없다`() {
        val readers =
            selectsFromRawObservation()
                .filter { (_, statement) -> BARE_PAYLOAD_COLUMN.containsMatchIn(statement) }
                .map { (file, _) -> file }

        readers.shouldBeEmpty()
    }

    /**
     * 술어가 **실제로 payload 열을 가려낼 수 있는지** 재는 음성 대조 — 같은 정규식에 읽기 형태의
     * 질의를 넣으면 잡힌다. 이것이 없으면 위 test 의 초록이 「술어가 아무것도 못 본다」와
     * 구별되지 않는다.
     */
    @Test
    fun `음성 대조 — 술어는 payload 를 읽는 질의를 잡고 투영 열은 놓아준다`() {
        val reading = "SELECT observation_key, payload FROM raw_observation WHERE x = ?"
        val projectionOnly = "SELECT payload_fields::text FROM raw_observation WHERE x = ?"

        val readingMatch = SELECT_FROM_RAW_OBSERVATION.find(reading)!!.value
        val projectionMatch = SELECT_FROM_RAW_OBSERVATION.find(projectionOnly)!!.value

        BARE_PAYLOAD_COLUMN.containsMatchIn(readingMatch) shouldBe true
        BARE_PAYLOAD_COLUMN.containsMatchIn(projectionMatch) shouldBe false
    }

    /** (파일 경로, 질의 문면) 쌍 전수 — 저장소 전체의 production Kotlin 을 본다. */
    private fun selectsFromRawObservation(): List<Pair<String, String>> =
        productionSourceRoots()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file ->
                SELECT_FROM_RAW_OBSERVATION
                    .findAll(file.readText())
                    .map { file.path to it.value }
                    .toList()
            }

    /**
     * 모듈마다 `src/main/kotlin` 하나다(`module.expected-source-sets`) — test 작업 디렉터리는
     * 모듈 디렉터리이므로 저장소 뿌리는 그 부모다. 뿌리를 **발견**하고 열거하지 않는다: 새 모듈이
     * 생기면 자동으로 범위에 든다.
     */
    private fun productionSourceRoots(): List<File> =
        (File("..").listFiles()?.toList() ?: emptyList())
            .filter { it.isDirectory }
            .map { File(it, "src/main/kotlin") }
            .filter { it.isDirectory }
}
