package bidvector.decision

import org.junit.jupiter.api.Test

/**
 * DEC-10 게이트 경계의 컴파일 실패 하네스(D-DEC10-6 ⓒ) — **밴드를 거치지 않은 값으로 게이트
 * 통과 값을 만들 수 없다**(R-FLOOR-03 의 검증 방법). 같은 모듈 test 는 `internal` 생성자를
 * 그냥 부를 수 있어 이 폐쇄를 재지 못하므로, 음성 fixture 를 **모듈 밖 컴파일 단위**로 돌린다.
 *
 * 네 명제를 따로 잰다 — 같은 기법이라고 갈음하지 않는다(DEC-05 r2 HIGH-1 의 교훈):
 * ⑥ 통과 값을 직접 **생성** · ⑥b 통과 값을 `copy` 로 **갈아끼우기**(생성 폐쇄와 다른 명제) ·
 * ⑦ 「통과했다」는 판정 자체를 **위조** · ⑧ 통과 값에서 **원값을 되꺼내기**(모듈 밖에서
 * 원값을 쥐면 그 값으로 다른 산술을 부를 수 있다) · ⑨ DEC-04 임계에 **원값을 넘기기**.
 */
class FloorTrustCompileFailureHarnessTest {
    @Test
    fun `6 게이트 통과 값은 모듈 밖에서 직접 지을 수 없고 게이트 경유는 컴파일된다`() {
        assertNegativeFails("6-plausible-floor-rate-direct-construction", CONSTRUCTOR_DENIED)
        assertPositiveCompiles("6-floor-trust-via-gate")
    }

    @Test
    fun `6-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "6-plausible-floor-rate-direct-construction-typo",
            realDiagnosticFragment = CONSTRUCTOR_DENIED,
        )
    }

    /**
     * 생성 폐쇄와 `copy` 폐쇄는 **다른 명제**다 — `@ConsistentCopyVisibility` 를 떼면
     * 모듈 밖에서 `copy(value = 밴드 밖 원값)` 으로 통과 값을 위조할 수 있다.
     */
    @Test
    fun `6b 게이트 통과 값의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("6b-plausible-floor-rate-copy", COPY_DENIED)
    }

    @Test
    fun `7 통과 판정은 모듈 밖에서 지을 수 없다 — 통과했다는 주장의 위조`() {
        assertNegativeFails("7-trusted-direct-construction", CONSTRUCTOR_DENIED)
    }

    @Test
    fun `7-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "7-trusted-direct-construction-typo",
            realDiagnosticFragment = CONSTRUCTOR_DENIED,
        )
    }

    /**
     * 통과 값의 공개 표면은 `rate` 하나다 — 원값(`value`)이 `internal` 이라 모듈 밖에서는
     * 되꺼낼 수 없다. 이것이 **평가 margin 경로(다른 모듈)가 원값을 쥘 자리가 없다**는 사실의
     * 기계적 증명이다.
     */
    @Test
    fun `8 통과 값에서 원값을 되꺼낼 수 없다`() {
        assertNegativeFails("8-gated-value-raw-read", RAW_VALUE_DENIED)
    }

    @Test
    fun `8-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "8-gated-value-raw-read-typo",
            realDiagnosticFragment = RAW_VALUE_DENIED,
        )
    }

    @Test
    fun `9 DEC-04 임계는 원값을 받지 않는다`() {
        assertNegativeFails("9-critical-rate-with-raw-floor", "argument type mismatch")
    }

    @Test
    fun `9-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "9-critical-rate-with-raw-floor-typo",
            realDiagnosticFragment = "argument type mismatch",
        )
    }

    /**
     * **가격 경로 ③ 의 진입**(D-DEC10-22, 운영자 결정 2026-10-28) — verifier r4 가 실측한 세탁
     * 두 꼴(`toString()` 정규식 파싱 · 영속 행의 `BigDecimal`)이 **컴파일 단계에서** 멈춘다.
     * 그 두 꼴의 공통 마지막 걸음이 「숫자를 ③ 의 입력으로 만든다」이므로, 그 자리를 타입으로
     * 닫으면 세탁의 **형태를 열거하지 않고** 한 번에 닫힌다(census 는 타입을 따라가므로 표현이
     * 바뀌면 못 본다 — 그것이 r4 의 교훈이다).
     */
    @Test
    fun `10 ③ 의 입력은 모듈 밖에서 직접 지을 수 없고 게이트 산출물 경유는 컴파일된다`() {
        assertNegativeFails("10-margin-inputs-direct-construction", CONSTRUCTOR_DENIED)
        assertPositiveCompiles("10-margin-entry-without-floor")
    }

    @Test
    fun `10-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "10-margin-inputs-direct-construction-typo",
            realDiagnosticFragment = CONSTRUCTOR_DENIED,
        )
    }

    /** 생성 폐쇄와 `copy` 폐쇄는 다른 명제다 — 기존 입력의 하한율만 갈아끼우는 길도 닫혀 있다. */
    @Test
    fun `10b ③ 의 입력은 copy 로 하한율을 갈아끼울 수 없다`() {
        assertNegativeFails("10b-margin-inputs-copy", COPY_DENIED)
    }

    /** 세탁 꼴 ① — 파싱해 만든 **율**을 진입에 넘긴다. */
    @Test
    fun `11 ③ 의 진입은 날 율을 받지 않는다`() {
        assertNegativeFails("11-margin-entry-with-bare-rate", "argument type mismatch", ENTRY_TYPE_EXPECTED)
    }

    @Test
    fun `11-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "11-margin-entry-with-bare-rate-typo",
            realDiagnosticFragment = ENTRY_TYPE_EXPECTED,
        )
    }

    /**
     * 세탁 꼴 ② — 영속 행의 `BigDecimal` 을 **율로 만들어** 진입에 넘긴다.
     *
     * 앞 판은 날 `BigDecimal` 을 그대로 넘겼는데 그 꼴은 진입이 `Rate?` 였던 시절에도 컴파일되지
     * 않아 **D-DEC10-22 를 가르지 못했다**(verifier r5 L-1 — 되돌림 변이 R1 에서 11 만 RED,
     * 12 는 초록). 지금 꼴은 되돌리면 컴파일되므로 그 변이가 둘 다 RED 로 만든다.
     */
    @Test
    fun `12 ③ 의 진입은 영속 행에서 지은 율도 받지 않는다`() {
        assertNegativeFails("12-margin-entry-with-bigdecimal", "argument type mismatch", ENTRY_TYPE_EXPECTED)
    }

    @Test
    fun `12-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "12-margin-entry-with-bigdecimal-typo",
            realDiagnosticFragment = ENTRY_TYPE_EXPECTED,
        )
    }
}

/**
 * ③ 진입 폐쇄의 진단을 **심볼로 지목**하는 조각(CR r5 M-2 · verifier r5 L-1). 일반 단편
 * `"argument type mismatch"` 는 **어느 인자가** 어긋났는지 보지 않아, `marginInputsFrom` 의
 * 다른 인자 타입이 바뀌어도 fixture 가 초록으로 남는다. 같은 처방을 이 저장소가 두 번 세웠다 —
 * `CONSTRUCTOR_DENIED`(r1 CR L-2) · `COPY_DENIED`(DEC-05 r5 V5-M1).
 */
private const val ENTRY_TYPE_EXPECTED = "'PlausibleFloorRate?' was expected"

/**
 * r1 CR L-2 — 일반 단편 `"cannot access"` 는 **다른 접근 실패**에도 참이다(import 한 타입 자체가
 * `internal` 로 바뀌는 경우 등). DEC-05 r5 가 `copy` 단편에 적용한 처방과 같게 **심볼을 지목**한다.
 */
private const val CONSTRUCTOR_DENIED = "cannot access 'constructor("

private const val RAW_VALUE_DENIED = "cannot access 'val value:"
