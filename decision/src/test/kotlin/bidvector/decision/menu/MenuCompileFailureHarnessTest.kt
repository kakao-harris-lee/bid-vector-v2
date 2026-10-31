package bidvector.decision.menu

import bidvector.decision.COPY_DENIED
import bidvector.decision.assertMutantDoesNotMatchRealFragment
import bidvector.decision.assertNegativeFails
import bidvector.decision.assertPositiveCompiles
import org.junit.jupiter.api.Test

/**
 * 메뉴 생성 경계의 컴파일 실패 하네스(1B·strategy `CompileFailureHarnessTest` 관례) —
 * `kotlin-compiler-embeddable` 로 음성 fixture 를 이 모듈의 산출 classpath 위에서 **별도
 * 컴파일 단위**로 돌린다. 모듈 밖에서 보이는 것만 재는 것이 요점이다: 같은 모듈 test 는
 * `internal` 생성자를 그냥 부를 수 있어 폐쇄를 재지 못한다.
 *
 * **이 하네스가 DEC-05 무조건 acceptance 첫째(「값만 있고 근거 없는 옵션을 만들 수 없다」)의
 * 유일한 기계적 증명이다** — 「근거를 채우는 호출부가 오늘은 하나뿐」은 증명이 아니고,
 * 생성 경로가 닫혀 있다는 사실만이 증명이다. 생성자를 공개로 되돌리는 변이에서 fixture 1 이
 * 양성이 되어 RED 다.
 *
 * fixture 1b·4a~4e — 결과 그래프 **여섯 타입**의 `copy` 가 각각 모듈 밖에서 닫혀 있다
 * (`BidMenuOption` 포함 — r4 V4-M1 이 그 하나가 빠진 것을 찾았다).
 *
 * fixture 1 — [BidMenuOption] 은 모듈 밖에서 직접 지을 수 없다(유일한 생성 경로는
 * [composeBidMenu]). fixture 2 — [BidMenuOutcome.Offered] 도 같다. fixture 3 —
 * [BidMenuOutcome.PartiallyOffered] 도 같다. 셋을 나눈 이유는 명제가 다르기 때문이다:
 * 「근거 없는 **옵션**」 · 「판정을 거치지 않은 **정상 3안**」(밴드 붕괴를 정상 3안으로
 * 바꿔치는 경로) · 「**보류 사유를 지어낸 부분 메뉴**」(라운드 2 가 낸 세 번째 조립 경로 —
 * 어느 태도를 왜 뺐는지를 밖에서 쓸 수 있으면 보류가 위조된다)의 위조다.
 *
 * **셋째를 「같은 기법이니 자명하다」로 갈음하지 않는다** — 라운드 2 의 HIGH-1 이 바로
 * 「자명해 보이는 대응」을 재지 않아 생겼다. 같은 기법이라도 새 표면은 따로 잰다.
 */
class MenuCompileFailureHarnessTest {
    @Test
    fun `1 BidMenuOption 은 모듈 밖에서 직접 지을 수 없고 커널 경유는 컴파일된다`() {
        assertNegativeFails("1-menu-option-direct-construction", "cannot access")
        assertPositiveCompiles("1-menu-via-kernel")
    }

    /**
     * fixture 1b — 생성 폐쇄와 **`copy` 폐쇄는 다른 명제**다(r4 V4-M1). `BidMenuOption` 은
     * `@ConsistentCopyVisibility` 를 달고도 그 `copy` 를 재는 fixture 가 없어서,
     * `@ExposedCopyVisibility` 로 바꾸는 변이(XOPT)가 **GREEN** 이었다 — 모듈 밖에서
     * `copy(binding = …)` 로 근거는 그대로 둔 채 binding 만 갈아끼울 수 있었다.
     */
    @Test
    fun `1b BidMenuOption 의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("1b-menu-option-copy", COPY_DENIED)
    }

    @Test
    fun `1-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "1-menu-option-direct-construction-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    @Test
    fun `2 Offered 는 모듈 밖에서 직접 지을 수 없고 커널 경유는 컴파일된다`() {
        assertNegativeFails("2-menu-offered-direct-construction", "cannot access")
        assertPositiveCompiles("1-menu-via-kernel")
    }

    @Test
    fun `2-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "2-menu-offered-direct-construction-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    @Test
    fun `3 PartiallyOffered 는 모듈 밖에서 직접 지을 수 없고 커널 경유는 컴파일된다`() {
        assertNegativeFails("3-menu-partially-offered-direct-construction", "cannot access")
        assertPositiveCompiles("1-menu-via-kernel")
    }

    @Test
    fun `3-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "3-menu-partially-offered-direct-construction-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    /**
     * fixture 4a~4e — `copy` 도 모듈 밖에서 닫혀 있다(r2 M-4 · ③′). 생성 폐쇄만 재면
     * **`copy` 로 가둔 컬렉션을 갈아끼우는 경로**가 열린 채 남는다.
     *
     * **타입마다 fixture 를 따로 둔다**(r3 N-3). 앞 판은 한 파일에 copy 둘을 넣어
     * 「**어느 하나라도** 막히면 통과」하는 술어였다 — 그러면 나머지가 열려도 초록이고,
     * 실제로 `Offered`·`PartiallyOffered` 는 대상 밖이었다. 하나의 음성 fixture 는 하나의
     * 명제만 재야 한다.
     */
    @Test
    fun `4a Offered 의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("4a-menu-offered-copy", COPY_DENIED)
        assertPositiveCompiles("1-menu-via-kernel")
    }

    @Test
    fun `4b PartiallyOffered 의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("4b-menu-partially-offered-copy", COPY_DENIED)
    }

    @Test
    fun `4c BandCollapsed 의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("4c-menu-band-collapsed-copy", COPY_DENIED)
    }

    @Test
    fun `4d Unmeasurable 의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("4d-menu-unmeasurable-copy", COPY_DENIED)
    }

    @Test
    fun `4e CollapsedWithOtherStances 의 copy 는 모듈 밖에서 닫혀 있다`() {
        assertNegativeFails("4e-menu-collapsed-reason-copy", COPY_DENIED)
    }

    /**
     * 비공허성 통제군 — **`BandCollapsed` 꼴 하나만 실측한다**(cr r4 L-3). 나머지 다섯
     * fixture 의 비공허성은 **같은 모양이라는 유추**이지 측정이 아니다. 유추로 메운 자리를
     * 「공유 통제군」이라고 적어 측정처럼 보이게 하지 않는다 — 넓히려면 타입마다 오타 변이를
     * 둬야 하고, 그 비용 대비 가치는 재리뷰 판단에 맡긴다.
     */
    @Test
    fun `4-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다(BandCollapsed 꼴)`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "4-menu-outcome-copy-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    /**
     * **술어의 판별력**을 잰다(r5 V5-M1). 이 mutant 는 **이름에 `copy` 가 들어가지만**
     * 접근 불가의 대상은 `copy` 가 **아니다**(`AppliedFloor` 의 `internal` 생성자).
     *
     * 앞 판의 `"copy"` 조각이라면 **경로에 들어 있는 `copy` 때문에 통과**한다 — 그래서 이
     * test 가 그 조각에서 RED 다. 변이 `V5M` 이 그것을 실측한다.
     */
    @Test
    fun `5-M2 copy 가 아닌 접근 불가는 copy 봉쇄 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "5-menu-copy-named-non-copy-access",
            realDiagnosticFragment = COPY_DENIED,
        )
    }
}
