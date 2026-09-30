package bidvector.adapters.snapshot

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Comparator

/**
 * **D-6G2d-4 ⓐ · 18 — 실행 상태 형식의 fail-closed 판별.** 무결성 장부가 「파일이 바뀌었나」를 묻는
 * 것과 달리 이 축은 「이 코드가 읽을 줄 아는 형식인가」를 묻는다 — 옛 디렉터리는 넷이 서로 맞아도
 * 기동되지 않아야 한다. 그래서 test 클래스도 따로다(파일 500줄 한도).
 */
class RunStateFormatTest : RunStateDirectoryFixture() {
    /**
     * **D-6G2d-4 ⓐ — 실행 상태에 형식 version 이 있다.** 이것이 없으면 옛 형식 디렉터리가 그대로
     * 기동하고, 옛 코드가 쓴 AXIS 줄이 「빈 응답 = 0 행」으로 읽혀 축이 통째로 빠진 완료 행이 나온다
     * (vr r5-t probe W7). 실수집이 아직 없는 지금이 형식을 닫는 유일하게 싼 때다.
     */
    @Test
    fun `첫 확정이 형식 version 을 남긴다`() {
        open().sampleList.confirm(runStateSample())

        Files.readString(root().resolve(STATE_NAME)) shouldContain "\"format_version\":$RUN_STATE_FORMAT_VERSION"
    }

    /** D-6G2d-4 ⓐ — version 이 없는 장부는 옛 형식이다. 경고가 아니라 **기동 거부**다(fail-closed). */
    @Test
    fun `형식 version 이 없는 장부는 기동을 거부한다`() {
        open().sampleList.confirm(runStateSample())
        val state = root().resolve(STATE_NAME)
        Files.writeString(state, Files.readString(state).replace("\"format_version\":$RUN_STATE_FORMAT_VERSION,", ""))

        shouldThrow<RunStateFormatRefusedException> { reopen() }.fault shouldBe RunStateFormatFault.MISSING
    }

    /** D-6G2d-4 ⓐ — 다른 version 도 거부다. 이 코드가 읽을 줄 아는 형식은 하나다. */
    @Test
    fun `형식 version 이 다르면 기동을 거부한다`() {
        open().sampleList.confirm(runStateSample())
        val state = root().resolve(STATE_NAME)
        val ahead = RUN_STATE_FORMAT_VERSION + 1
        Files.writeString(
            state,
            Files.readString(state).replace(
                "\"format_version\":$RUN_STATE_FORMAT_VERSION",
                "\"format_version\":$ahead",
            ),
        )

        shouldThrow<RunStateFormatRefusedException> { reopen() }.fault shouldBe RunStateFormatFault.MISMATCHED
    }

    /**
     * **D-6G2d-18 (vr r1 L-1) — 형식 version 은 정수만이다.** 일반 판독기는 문자열과 선행 0 을 받아
     * 주는데 이 칸은 우리가 쓰는 값이라 관용할 이유가 없다. 그리고 칸이 있는데 형태가 틀린 것은
     * 「없다」가 아니라 **「다르다」**다 — 그 구별이 옛 디렉터리와 손상된 장부를 가른다.
     */
    @Test
    fun `형식 version 은 정수만 받는다 — 문자열·선행 0·소수는 다르다`() {
        listOf("\"$RUN_STATE_FORMAT_VERSION\"", "0$RUN_STATE_FORMAT_VERSION", "$RUN_STATE_FORMAT_VERSION.0")
            .forEach { malformed ->
                open().sampleList.confirm(runStateSample())
                val state = root().resolve(STATE_NAME)
                Files.writeString(
                    state,
                    Files
                        .readString(state)
                        .replace("\"format_version\":$RUN_STATE_FORMAT_VERSION", "\"format_version\":$malformed"),
                )

                shouldThrow<RunStateFormatRefusedException> { reopen() }.fault shouldBe RunStateFormatFault.MISMATCHED
                releaseLocks()
                Files.walk(root()).sorted(Comparator.reverseOrder()).forEach(Files::delete)
            }
    }

    /** 값이 명시 `null` 인 것은 「없다」다 — 그 장부는 이 칸이 생기기 전에 쓰였다. */
    @Test
    fun `형식 version 이 명시 null 이면 없는 것이다`() {
        open().sampleList.confirm(runStateSample())
        val state = root().resolve(STATE_NAME)
        Files.writeString(
            state,
            Files.readString(state).replace("\"format_version\":$RUN_STATE_FORMAT_VERSION", "\"format_version\":null"),
        )

        shouldThrow<RunStateFormatRefusedException> { reopen() }.fault shouldBe RunStateFormatFault.MISSING
    }

    /**
     * **cr r4 ③ — 교체 전에 바이트를 굳힌다.** `ATOMIC_MOVE` 는 **이름의 교체**만 원자적으로 만든다.
     * 바이트가 아직 페이지 캐시에 있는 동안 rename 이 먼저 굳으면, 그 사이의 전원 손실 뒤에 이름은 새
     * 파일을 가리키는데 내용이 0 바이트인 모양이 남는다 — 복구가 도는 순간은 방금 죽은 기계 위라 그
     * 창이 실제로 열린다.
     *
     * 내구성 자체는 단위 test 로 잴 수 없다(크래시를 심을 자리가 없다). 잴 수 있는 것은 **쓰기가 그
     * 보장을 요구하는가**이고, 옵션 배열이 그 요구의 정본이다 — 빠지면 이 등식이 붉어진다. 교체 뒤의
     * 디렉터리 fsync 는 같은 함수 안에 있고 관측 가능한 표면이 없다.
     */
    @Test
    fun `실행 상태 쓰기는 SYNC 를 요구한다`() {
        DURABLE_WRITE_OPTIONS.toList() shouldContainExactly
            listOf(
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC,
            )
    }
}
