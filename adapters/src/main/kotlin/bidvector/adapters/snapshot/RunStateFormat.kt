package bidvector.adapters.snapshot

import bidvector.adapters.koneps.JsonValue

/**
 * 실행 상태 **형식**의 version(D-6G2d-4 ⓐ) — 장부가 이 값을 싣고, 다르거나 없으면 기동을 거부한다.
 *
 * 실수집이 한 번도 돌지 않은 지금이 형식을 닫는 유일하게 싼 때다. 이 값이 없던 동안 옛 디렉터리가
 * 그대로 기동했고, 옛 코드가 쓴 걷기 없는 AXIS 줄이 「빈 응답 = 0 행」으로 읽혀 축이 통째로 빠진
 * **완료 행**이 나왔다(vr r5-t probe W7). 관용할 이유가 없다 — 옛 형식은 읽지 않는다.
 *
 * **2 로 올렸다**(D-6G2d-16). AXIS 줄의 결말 어휘에 관문 거부가 생겼다 — version 1 의 원장에서 상한
 * 거부는 일시 실패(`FAILED:`) 로 적혀 있고, 이 코드가 그 줄을 읽으면 **재호출 상한에 세어** 호출 0 번인
 * 축을 확정시킨다(고치려던 결함 그대로다). 어휘가 바뀌면 version 이 오른다. 올리는 비용은 지금 0 이다:
 * version 1 로 쓰인 디렉터리는 test 임시 디렉터리 밖에 존재하지 않는다(실수집 전).
 */
internal const val RUN_STATE_FORMAT_VERSION = 2

/**
 * 형식 거부의 **닫힌 사유** — 잠금 경합(`ALREADY_RUNNING`)과도, 장부 불일치와도 다른 값이다.
 * [causeCode] 는 기동 실패 출력에서 **그대로 grep 되는 토큰**이다(cr r1 M-3 부분 이행 — 전용 종료
 * 코드는 배선 재설계가 필요해 이 라운드 밖이다, evidence 「이탈」).
 */
internal enum class RunStateFormatFault(
    val causeCode: String,
) {
    /** 장부에 형식 version 칸이 없다(또는 값이 `null`) — 이 칸이 생기기 전에 쓰인 디렉터리다. */
    MISSING("RUN_STATE_FORMAT_MISSING"),

    /** 칸은 있는데 이 코드가 읽을 줄 아는 형태·값이 아니다(문자열·선행 0·소수·다른 수). */
    MISMATCHED("RUN_STATE_FORMAT_MISMATCHED"),

    /**
     * **원장 줄**이 이 형식의 것이 아니다(D-6G2d-44) — 걷기 칸이 생기기 전에 쓰인 AXIS 줄이다.
     * 장부(`state.json`)가 아니라 줄에서 드러나므로 사유를 따로 둔다: 잠금을 못 잡아 장부 대조를
     * 건너뛴 열기에서도, 장부가 지워진 디렉터리에서도 이 줄은 **형식**으로 거부돼야 한다. 앞 판은
     * 값 타입의 generic `require` 로 죽어 운영자 출력에 「손상」과 같은 모양으로 보였다.
     */
    LEGACY_LINE("RUN_STATE_FORMAT_LEGACY_LINE"),
}

/**
 * 옛 형식 실행 상태의 기동 거부(D-6G2d-4 ⓐ) — 무결성 불일치(`IllegalArgumentException`)와 **다른
 * 타입**이다: 운영자가 「사고인가 옛 디렉터리인가」를 그 자리에서 가릴 수 있어야 한다. 모듈 밖에서
 * 이름으로 잡을 자리가 없어 `internal` 이다(새 public 표면 0).
 */
internal class RunStateFormatRefusedException(
    val fault: RunStateFormatFault,
) : RuntimeException("실행 상태 형식이 이 코드의 것이 아니다 — cause=${fault.causeCode}")

/**
 * 형식 version 대조(D-6G2d-4 ⓐ · 18) — 없거나 다르면 **기동 거부**다(경고가 아니다). 거부는 잠금을
 * 놓고 나가야 하므로 [heldOrRelease] 안에서 일어난다(이 함수는 [readFacts] 가 부른다).
 *
 * **정수만 받는다**(vr r1 L-1). 일반 판독기(`asIntOrNull`)는 문자열 `"1"` 과 선행 0 `01` 을 받아
 * 주는데, 이 칸은 **우리가 쓰는 값**이라 관용할 이유가 없고 관용은 형식 판별을 무르게 만든다.
 * 칸이 있는데 형태가 틀리면 그것은 「없다」가 아니라 「다르다」다 — `1.0` 이 MISSING 으로 가면
 * 운영자가 옛 디렉터리와 손상된 장부를 구별할 수 없다.
 */
internal fun requireCurrentFormat(declared: JsonValue?) {
    if (declared == null || declared == JsonValue.JsonNull) {
        throw RunStateFormatRefusedException(RunStateFormatFault.MISSING)
    }
    val version = (declared as? JsonValue.JsonNumber)?.raw?.takeIf(STRICT_FORMAT_VERSION::matches)?.toIntOrNull()
    if (version != RUN_STATE_FORMAT_VERSION) {
        throw RunStateFormatRefusedException(RunStateFormatFault.MISMATCHED)
    }
}

/** 형식 version 의 형태 — 선행 0 도 부호도 소수점도 없는 십진 정수 하나다(D-6G2d-18). */
private val STRICT_FORMAT_VERSION = Regex("0|[1-9][0-9]*")
