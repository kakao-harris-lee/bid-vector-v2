package bidvector.app.collection

import bidvector.adapters.snapshot.RunStateDirectory
import bidvector.procurement.AttemptKind
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.CollectionAttempt
import bidvector.procurement.SourceEndpoint
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

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

internal fun attemptLinesOf(root: Path): List<String> =
    Files.readString(root.resolve("attempts.jsonl")).trimEnd('\n').lines()

/**
 * 오늘치를 그만큼 써 둔 실행 상태 — **출하 경로로** 적는다(손으로 줄만 쓰면 무결성 장부와 어긋나
 * 기동이 거부되고, 그 거부는 재려는 것이 아니다). 쓰고 나면 잠금을 놓는다.
 */
internal fun seedSpentCallsAt(
    root: Path,
    at: Instant,
    calls: Int,
) {
    val runState = RunStateDirectory(root)
    repeat(calls) {
        runState.attempts.append(
            CollectionAttempt(
                noticeKey = null,
                axis = SourceEndpoint.OPENING_RESULT_LIST,
                outcome = AttemptOutcome.Succeeded,
                at = at,
                kind = AttemptKind.PENDING,
            ),
        )
    }
    runState.close()
}

/** 이 nonce 의 개찰완료 원문만 — 컨테이너를 test 끼리 나눠 쓰므로 좁힌다. */
private fun openingRowsOf(nonce: String): String =
    "source_endpoint = 'OPENING_COMPLETE' AND payload_fields::text LIKE '%$nonce%'"

/** 남은 투찰 원문 전부 — 다시 걸었으면 앞 걷기의 쪽까지 든다(원문은 append-only 다). */
internal fun allOpeningRowsSql(nonce: String): String =
    "SELECT count(*) FROM raw_observation WHERE ${openingRowsOf(nonce)}"

/** 추출이 실제로 쓰는 몫 — (공고, 축)마다 **마지막 걷기**의 행만(D-6G-58). */
internal fun latestWalkOpeningRowsSql(nonce: String): String =
    "SELECT count(*) FROM raw_observation r WHERE ${openingRowsOf(nonce)} " +
        "AND r.observed_at = (SELECT max(r2.observed_at) FROM raw_observation r2 " +
        "WHERE r2.source_endpoint = r.source_endpoint " +
        "AND r2.payload_fields ->> 'bidNtceNo' = r.payload_fields ->> 'bidNtceNo')"

/**
 * D-6F9-2 — 오퍼레이션마다 응답이 싣는 세부 분류 키가 다르다(6F-8 실측): 용역은 용역구분·공공조달분류
 * 번호·명, 공사는 주공종이고 주공종은 일부 항목만 채워진다(전기공사업 · 빈 문자열 · 키 없음이 한
 * 슬롯에 섞인다).
 */
internal fun classificationFor(
    category: String,
    index: Int,
): Map<String, String> =
    when (category) {
        "Servc" -> {
            mapOf(
                "srvceDivNm" to if (index == 1) "일반용역" else "기술용역",
                "pubPrcrmntClsfcNo" to SERVICE_CLASS_CODE,
                "pubPrcrmntClsfcNm" to SERVICE_CLASS_NAME,
            )
        }

        else -> {
            when (index) {
                1 -> mapOf("mainCnsttyNm" to CONSTRUCTION_TYPE)
                2 -> mapOf("mainCnsttyNm" to "")
                else -> emptyMap()
            }
        }
    }

/** 빈 문자열로 오는 옵션 값(D-6F8-11) — 새 세부 분류 키도 빈 값은 없는 값이다. */
internal fun blankClassificationFor(category: String): Map<String, String> =
    when (category) {
        "Servc" -> mapOf("srvceDivNm" to "", "pubPrcrmntClsfcNo" to " ", "pubPrcrmntClsfcNm" to "  ")
        else -> mapOf("mainCnsttyNm" to "   ")
    }

internal const val SERVICE_CLASS_CODE = "81111500"
internal const val SERVICE_CLASS_NAME = "정보시스템 개발 서비스"
internal const val CONSTRUCTION_TYPE = "전기공사업"

/** test 마다 다른 공고번호 표식 — 같은 컨테이너를 쓰는 앞 test 의 적재를 물려받지 않는다. */
internal fun newE2ENonce(): String =
    java.util.UUID
        .randomUUID()
        .toString()
        .take(NONCE_CHARS)
        .uppercase()

private const val NONCE_CHARS = 8
