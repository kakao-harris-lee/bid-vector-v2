package bidvector.app.collection

import java.nio.file.Files
import java.nio.file.Path

/**
 * 저장소 밖 실행 상태(D-6G-47 H-1) — 공고 목록 갈래도 승인 상한 아래이므로 그 갈래를 켜는 E2E 는
 * 실행 상태 디렉터리를 대야 한다. 한 test 의 모든 기동이 같은 자리를 써야 상한이 누적된다.
 */
internal val NOTICE_E2E_RUN_STATE: Path = Files.createTempDirectory("6g-notice-e2e-run-state")
