package bidvector.app.collection

import java.nio.file.Files
import java.nio.file.Path

/**
 * 저장소 밖 실행 상태(D-6G-47 H-1) — 공고 목록 갈래도 승인 상한 아래이므로 그 갈래를 켜는 E2E 는
 * 실행 상태 디렉터리를 대야 한다. 한 test 의 모든 기동이 같은 자리를 써야 상한이 누적된다.
 */
internal val NOTICE_E2E_RUN_STATE: Path = Files.createTempDirectory("6g-notice-e2e-run-state")

/**
 * 시도 원장의 줄 갈래별 개수(D-6G-56) — `PENDING` 은 상한이 세는 것, `HTTP` 는 나간 호출의 결말이다.
 * E2E 가 이것을 **mock 이 받은 요청 수**와 맞댄다: 원장이 센 것을 원장의 합으로 재면 덜 센 것을
 * 볼 수 없다.
 */
internal fun attemptKindCount(
    root: Path,
    kind: String,
): Int =
    runCatching { Files.readString(root.resolve("attempts.jsonl")) }
        .getOrDefault("")
        .lineSequence()
        .count { it.contains("\"kind\":\"$kind\"") }
