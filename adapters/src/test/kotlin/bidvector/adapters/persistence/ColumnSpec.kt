package bidvector.adapters.persistence

/**
 * 컬럼 한 칸의 기대 — 표 · 이름 · 타입 · NOT NULL · DEFAULT 유무.
 *
 * `CleanMigrationColumnTest` 안의 `private data class` 였다가 **top-level `internal` 로
 * 올렸다**(M7/7B). 사유는 그 파일이 500줄 한도를 넘어 입찰 건 넷의 등재를 이 파일로 갈랐고,
 * 두 파일이 같은 타입을 써야 하기 때문이다 — 타입을 복제하면 두 목록이 서로 다른 모양으로
 * 갈릴 수 있다. 가시성이 `internal` 이라 `adapters` 밖으로는 나가지 않는다.
 *
 * `hasDefault` — 이름·타입·NOT NULL 삼중항만으로는
 * `session_version INTEGER NOT NULL DEFAULT 0`(D-6B1-3 이 금지한 바로 그 것)이 초록으로
 * 지났다. 정확한 DEFAULT 식 문자열이 아니라 **있고 없음**만 잰다 — 식 문자열은 Postgres
 * 버전·캐스트 표기가 갈릴 수 있어 이름 재작성만으로 오탐이 날 수 있다(예: `1` vs
 * `1::bigint`). 기존 행은 전부 `hasDefault` 를 안 적어 Kotlin 기본값 `false` 를 쓴다 —
 * 실제로 DEFAULT 가 있는 21개 컬럼만 `true` 로 명시했다(추가만, 기존 표 기대치는 그대로).
 */
internal data class ColumnSpec(
    val table: String,
    val column: String,
    val dataType: String,
    val nullable: Boolean,
    val hasDefault: Boolean = false,
)
