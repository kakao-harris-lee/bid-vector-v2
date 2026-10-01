package bidvector.adapters.snapshot

import bidvector.adapters.koneps.KonepsJsonParser
import bidvector.adapters.koneps.asIntOrNull
import bidvector.adapters.koneps.asObject
import bidvector.adapters.koneps.asStringOrNull
import java.nio.file.Files
import java.nio.file.Path

/**
 * 실행 상태의 무결성 장부(D-6G-48) — 이 넷이 맞아야 기동한다.
 *
 * 앞 판은 표본 해시 하나였고, 그래서 **원장만 지우거나 잘라도** 거부 없이 상한이 0 에서 다시
 * 시작했다(vr M-7). 원장의 해시와 줄 수를 함께 적으면 삭제·절삭·부분 복사가 전부 어긋난다.
 * [directoryId] 는 첫 확정이 지은 표식이다 — 파일 셋이 한 실행의 것임을 말한다.
 *
 * **세 파일을 통째로 복사한 것은 구별되지 않는다**(넷이 서로 맞으므로). 그것은 운영자 행위이고
 * 경계 밖이다 — 이 장부가 겨누는 것은 사고와 오조작이다.
 */
internal class RunStateFacts(
    val directoryId: String,
    val sampleListSha256: String,
    val sampleScopeSha256: String,
    val attemptsSha256: String,
    val attemptLines: Int,
)

/**
 * 장부 파일의 **판독** — 형식 판별과 값 읽기가 한 타입에 있다. 디렉터리에서 갈라낸 이유는 관심사가
 * 다르기 때문이다: 디렉터리는 잠금·복구·교체를 지고, 여기는 **그 한 파일을 어떻게 읽는가**를 진다.
 */
internal class RunStateFactsFile(
    private val stateFile: Path,
) {
    /** 장부의 칸들 — 파일이 없거나 JSON 객체가 아니면 「장부 없음」이다(첫 실행이 그 모양이다). */
    private fun fields() =
        runCatching { Files.readString(stateFile) }
            .getOrNull()
            ?.let { KonepsJsonParser.parse(it, ATTEMPT_MAX_DEPTH).asObject()?.fields }

    /**
     * **읽기만으로 형식을 본다**(D-6G2d-44) — 장부가 있으면 그 version 을 요구하고, 없으면 검사할
     * 것이 없다(첫 실행). 다른 칸은 보지 않는다: 잠금을 못 잡은 실행에 무결성 대조의 몫까지 지우면
     * 「읽기만 하는 열기」가 남의 손상으로 죽는다.
     */
    fun requireReadableFormat() {
        fields()?.let { requireCurrentFormat(it["format_version"]) }
    }

    fun read(): RunStateFacts? {
        val fields = fields() ?: return null
        requireCurrentFormat(fields["format_version"])
        return RunStateFacts(
            directoryId = requireNotNull(fields["directory_id"].asStringOrNull()) { "장부에 디렉터리 식별자가 없다" },
            sampleListSha256 = requireNotNull(fields["sample_list_sha256"].asStringOrNull()) { "장부에 표본 해시가 없다" },
            sampleScopeSha256 =
                requireNotNull(fields["sample_scope_sha256"].asStringOrNull()) { "장부에 범위 해시가 없다" },
            attemptsSha256 = requireNotNull(fields["attempts_sha256"].asStringOrNull()) { "장부에 원장 해시가 없다" },
            attemptLines = requireNotNull(fields["attempt_lines"].asIntOrNull()) { "장부에 원장 줄 수가 없다" },
        )
    }
}
