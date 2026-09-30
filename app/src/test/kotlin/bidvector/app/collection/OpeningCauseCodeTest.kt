package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

private val CAUSE_AT: Instant = Instant.parse("2026-06-17T02:00:00Z")

/** 걷기 칸이 **없는** AXIS 줄 — 그 칸이 생기기 전에 쓰인 줄이다(형식 version 1 의 모양). */
private val LEGACY_AXIS_LINE =
    "{\"at\":\"$CAUSE_AT\",\"axis\":\"RESERVE_PRICE_DETAIL\"," +
        "\"notice_key_hash\":null,\"outcome\":\"SUCCEEDED\",\"kind\":\"AXIS\"}\n"

/**
 * **D-6G2d-48 ① — 형식 거부는 사유 토큰으로 러너에 닿는다.** 러너가 예외에서 뽑는 값은 로그 줄과
 * 종료 사유 **둘 다**에 실리는 하나이고(`openingCauseCodeOf` 의 결과), 앞 판은 그 자리에 클래스 이름이
 * 떨어져 운영자가 「옛 디렉터리인가 손상인가」를 가릴 수 없었다.
 *
 * 거부를 **지어내지 않는다** — 실제 실행 상태 디렉터리에 옛 줄을 심고 출하 판독이 올리는 예외를 그대로
 * 러너의 매핑에 넣는다(그 예외 타입은 어댑터 모듈 안이라 app 이 이름으로 만들 수 없다).
 */
class OpeningCauseCodeTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `옛 형식 원장 줄은 사유 토큰으로 떨어진다 — 클래스 이름이 아니다`() {
        val failure = shouldThrow<Exception> { readLedgerWithLegacyLine() }

        openingCauseCodeOf(failure) shouldBe "RUN_STATE_FORMAT_LEGACY_LINE"
        openingCauseCodeOf(failure) shouldNotContain "Exception"
    }

    /** 반대 방향 — 형식 거부가 아닌 예외는 그대로 클래스 이름이다(앞 규율 무변경). */
    @Test
    fun `형식 거부가 아니면 사유 코드는 그대로다`() {
        openingCauseCodeOf(IllegalStateException("무엇이든")) shouldBe "java.lang.IllegalStateException"
    }

    /**
     * 장부를 **세운 뒤** 옛 줄을 붙인다 — 원장이 장부보다 앞선 모양은 재동기가 처리하므로(D-6G-48)
     * 남는 거부 사유는 형식뿐이다. 잠금은 첫 디렉터리가 들고 있어 두 번째 열기는 읽기 전용이다.
     */
    private fun readLedgerWithLegacyLine() {
        val root = Files.createDirectories(temp.resolve("run-state"))
        val held = RunStateDirectory(root)
        try {
            held.attempts.append(
                CollectionAttempt(
                    noticeKey = null,
                    axis = SourceEndpoint.OPENING_RESULT_LIST,
                    outcome = AttemptOutcome.Succeeded,
                    at = CAUSE_AT,
                    kind = AttemptKind.PENDING,
                    walk = null,
                ),
            )
            Files.writeString(root.resolve("attempts.jsonl"), LEGACY_AXIS_LINE, StandardOpenOption.APPEND)
            RunStateDirectory(root).attempts.read()
        } finally {
            held.close()
        }
    }
}
