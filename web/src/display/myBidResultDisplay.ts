import type { MyBidAggregate, MyBidCase } from '../api/queries';

import type { StateDisplay } from './stateDisplay';

/**
 * 내 입찰 결과의 표시(M7/7E-3b, SET-01 표시 절반).
 *
 * **서버가 판정하고 화면은 고른 문면을 붙일 뿐이다.** 비율도 계수도 차이도 서버 값이고, 이
 * 파일에 산술이 없다 — 있는 것은 「어떤 값에 어떤 말을 붙이는가」와 「어떤 칸이 어느 축에
 * 속하는가」뿐이다.
 *
 * 어휘 표는 전부 `satisfies Record<Enum, StateDisplay>` 다. 계약에 값이 늘면 `tsc` 가 그
 * 자리에서 멈추므로 새 값이 조용히 폴백 문면으로 떨어지지 않는다.
 */
export type ReconciledOutcome = MyBidCase['reconciledOutcome'];
export type ReconciliationBlocker = NonNullable<MyBidCase['reconciliationBlocker']>;
export type OutcomeAgreement = MyBidCase['outcomeAgreement'];
export type GapState = MyBidCase['gapState'];
export type SubmittedAmountOrigin = MyBidCase['submittedAmountOrigin'];
export type AwardRateState = MyBidAggregate['recordedRateState'];

/**
 * 서버 대사의 결말 다섯. **판정 불가는 패찰이 아니다** — 어조가 그 사실을 나른다(`unjudged`
 * 이지 `bad` 가 아니다). 낙찰·패찰을 `good`/`bad` 로 두는 것은 가치 판단이 아니라 운영자가
 * 기대한 결과인지를 한눈에 가르는 것이고, 유찰·재입찰은 **자기 결말**이라 중립이다.
 */
export const RECONCILED_OUTCOME_DISPLAY = {
  WON: { label: '낙찰(대사)', why: '내 상호가 낙찰자와 맞았다', tone: 'good' },
  LOST: { label: '패찰(대사)', why: '낙찰자가 내가 아니다', tone: 'bad' },
  FAILED_BID: { label: '유찰', why: '낙찰자가 없다 — 판정 불가가 아니라 결말이다', tone: 'neutral' },
  REBID: { label: '재입찰', why: '재공고는 새 공고라 새 건이 된다', tone: 'neutral' },
  UNDECIDABLE: {
    label: '아직 판정 못 함',
    why: '견줄 상호나 개찰 결과가 아직 없다 — 패찰이 아니다',
    tone: 'unjudged',
  },
} as const satisfies Record<ReconciledOutcome, StateDisplay>;

/**
 * 판정이 서지 않은 사유 **열**(D-7C5B-11). 개찰 축 여섯은 「판정할 거리가 아직 없다」이고
 * 커널 축 넷은 「거리는 있는데 견줄 상호가 없다」다.
 *
 * 둘을 접지 않는 이유는 **운영자가 할 일이 다르기 때문**이다: 전자는 기다리면 풀리고, 운영자
 * 상호 축 둘은 프로필을 고치면 풀린다. 「판정 불가」 하나로 뭉치면 그 구별이 화면에서 사라진다.
 */
export const RECONCILIATION_BLOCKER_DISPLAY = {
  NOTICE_ABSENT: { label: '공고를 모름', why: '그 공고를 수집한 적이 없다', tone: 'absent' },
  NOT_OPENED_YET: { label: '아직 개찰 전', why: '개찰이 지나야 견줄 결과가 생긴다', tone: 'neutral' },
  NOT_COLLECTED: { label: '개찰 자료 없음', why: '개찰 축 원문도 결과도 아직 없다', tone: 'absent' },
  OBSERVED_NOT_PROMOTED: {
    label: '원문만 적재됨',
    why: '개찰 축 원문은 있으나 결과로 승격되지 않았다',
    tone: 'unjudged',
  },
  PROGRESS_UNRECOGNIZED: {
    label: '진행구분 미등재',
    why: '상류가 등재 어휘 밖 라벨을 보냈다 — 개찰완료로 접지 않는다',
    tone: 'unjudged',
  },
  PROGRESS_ABSENT: {
    label: '진행구분 없음',
    why: '결과 행은 있으나 진행구분 칸이 비었다',
    tone: 'unjudged',
  },
  OPERATOR_NAME_NOT_DECLARED: {
    label: '내 상호 미설정',
    why: '설정에서 상호를 적으면 이 사유는 풀린다',
    tone: 'unjudged',
  },
  OPERATOR_NAME_NORMALIZES_TO_EMPTY: {
    label: '내 상호가 빈 값',
    why: '법인 표기 토큰만 남아 견줄 이름이 없다 — 설정에서 고칠 수 있다',
    tone: 'unjudged',
  },
  WINNER_NAME_NOT_PUBLISHED: {
    label: '낙찰자 상호 미공개',
    why: '낙찰자가 아니라는 뜻이 아니다 — 견줄 이름이 아직 공개되지 않았다',
    tone: 'absent',
  },
  WINNER_NAME_NORMALIZES_TO_EMPTY: {
    label: '낙찰자 상호가 빈 값',
    why: '공개된 이름이 법인 표기 토큰만이라 견줄 수 없다',
    tone: 'absent',
  },
} as const satisfies Record<ReconciliationBlocker, StateDisplay>;

/**
 * 운영자 기록과 서버 대사의 관계 셋. **어긋남은 사실이지 판정이 아니다**(D-7C5B-3) — 어느
 * 쪽이 틀렸다고 읽히면 안 되므로 `bad` 를 쓰지 않는다.
 */
export const OUTCOME_AGREEMENT_DISPLAY = {
  AGREE: { label: '내 기록과 같음', tone: 'good' },
  DISAGREE: {
    label: '내 기록과 다름',
    why: '어느 쪽이 틀렸다는 판정이 아니다 — 둘이 다르다는 사실이다',
    tone: 'unjudged',
  },
  NOT_COMPARABLE: {
    label: '견줄 수 없음',
    why: '한쪽이 아직 확정이 아니다 — 없는 어긋남을 만들지 않는다',
    tone: 'unjudged',
  },
} as const satisfies Record<OutcomeAgreement, StateDisplay>;

/** 차이의 상태 둘 — 「확정 낙찰가가 없다」와 「차이가 0 이다」는 다른 값이다(계약). */
export const GAP_STATE_DISPLAY = {
  COMPUTED: { label: '차이 있음', tone: 'neutral' },
  AWARD_ABSENT: {
    label: '확정 낙찰가가 없어 뺄 수 없음',
    why: '잠정 금액으로 채우지 않는다',
    tone: 'absent',
  },
} as const satisfies Record<GapState, StateDisplay>;

/**
 * 비율의 상태 셋. **분모 0 은 0% 가 아니다** — 두 부재가 서로 다른 사실이라 문면도 다르다:
 * 「셀 것이 없다」와 「셌는데 분모에 드는 것이 없다」.
 */
export const AWARD_RATE_STATE_DISPLAY = {
  MEASURED: { label: '잰 값', tone: 'neutral' },
  ABSENT_NO_RECORDED_BIDS: {
    label: '셀 것이 없음',
    why: '투찰 기록이 있는 실투찰 건 자체가 없다',
    tone: 'absent',
  },
  ABSENT_NO_DECIDED_CASES: {
    label: '분모에 드는 건이 없음',
    why: '표본은 있으나 전부 분모 밖이다(유찰·재입찰 · 판정 불가 · 미확정)',
    tone: 'absent',
  },
} as const satisfies Record<AwardRateState, StateDisplay>;

/**
 * 투찰 금액의 출처 둘. `PRICE_MENU` 는 **오늘 도달 불가**다(D-7C5B-10 ①) — 투찰가 메뉴가 출하
 * 경로에 없다. 칸을 두되 그 사실이 **상태로** 보이게 한다(빈칸이면 운영자가 「기록을 잃었다」로
 * 읽는다).
 */
export const SUBMITTED_AMOUNT_ORIGIN_DISPLAY = {
  OPERATOR_ENTERED: { label: '직접 입력', why: '메뉴에서 고른 것이 아니다', tone: 'neutral' },
  PRICE_MENU: { label: '추천 메뉴에서 고름', tone: 'neutral' },
} as const satisfies Record<SubmittedAmountOrigin, StateDisplay>;

/**
 * 집계의 표본이 **화면의 카드 수가 아님**을 말하는 문면(D-7E3b-7 ⒟).
 *
 * 실측에서 표본 3 · 카드 2 였다: 집계는 「투찰 기록이 있는 실투찰 건 전부」를 세고 카드는
 * 「결과 단계 일곱」만 선다. 이 말이 없으면 운영자가 카드를 세어 검산하다 어긋난다.
 */
export const SAMPLE_NOTE =
  '투찰 기록이 있는 실투찰 건 전부를 센 수입니다 — 위 카드 수(결과 단계만)와 다를 수 있습니다';

/**
 * 차이의 읽기 — **상태가 답일 때 숫자를 그리지 않는다.**
 *
 * `gapWon` 을 참/거짓으로 보면 차이 0 이 부재로 접힌다. 그래서 갈래를 **서버가 준 상태**로
 * 가르고, `COMPUTED` 인데 칸이 비어 있으면 그것은 계약 위반이라 진단(`INCOMPLETE`)으로 낸다.
 */
export type GapReading =
  | { readonly kind: 'COMPUTED'; readonly differenceWon: number; readonly currency: string }
  /** 상태 자신이 답이다 — 뺄 것이 없다. */
  | { readonly kind: 'ABSENT'; readonly state: GapState }
  | { readonly kind: 'INCOMPLETE'; readonly missing: readonly string[] };

/** `gapState` 와 `gap*` 두 칸만 본다 — 투찰 금액·확정 낙찰가의 칸 이름이 본문에 없다. */
export function awardGapReading(one: {
  readonly gapState: GapState;
  readonly gapWon?: number | null;
  readonly gapCurrency?: string | null;
}): GapReading {
  switch (one.gapState) {
    case 'COMPUTED': {
      const missing = (
        [
          ['gapWon', one.gapWon],
          ['gapCurrency', one.gapCurrency],
        ] as const
      )
        .filter(([, value]) => value === null || value === undefined)
        .map(([name]) => name);
      if (missing.length > 0) {
        return { kind: 'INCOMPLETE', missing };
      }
      return { kind: 'COMPUTED', differenceWon: one.gapWon!, currency: one.gapCurrency! };
    }
    case 'AWARD_ABSENT':
      return { kind: 'ABSENT', state: one.gapState };
    default: {
      const unreachable: never = one.gapState;
      throw new Error(`모르는 차이 상태: ${String(unreachable)}`);
    }
  }
}

/**
 * 건별 결과를 **`caseId` 로** 찾을 수 있게 접는다.
 *
 * 순서로 짝지으면 어긋난다(실측): `cases[]` 는 확인 시각 내림차순이고 화면의 카드는
 * `listBidCases` 의 차례다. 두 목록의 **길이도 다르다** — `cases[]` 는 「투찰 기록이 있는
 * 실투찰 건 전부」이고 카드는 「결과 단계 일곱」이다.
 */
export function caseResultsById(cases: readonly MyBidCase[]): ReadonlyMap<number, MyBidCase> {
  return new Map(cases.map((one) => [one.caseId, one]));
}

/**
 * 카드 하나가 받는 결과 조회의 답 — **넷이 서로 다른 사실이다.**
 *
 * 「아직 안 왔다」·「못 읽었다」·「조회에 그 건이 없다」를 한 값(`undefined`)으로 접으면 카드가
 * 적재 중에도 「이 건이 없습니다」를 적는다. 그 말은 **거짓**이고, 운영자는 자기 기록이 사라진
 * 줄 안다.
 */
export type CaseResult =
  | { readonly kind: 'LOADING' }
  /** 얼굴은 화면이 하나로 그린다 — 카드는 조용하다(7E-3a r3 CR L-1 과 같은 자리). */
  | { readonly kind: 'FAILED' }
  | { readonly kind: 'FOUND'; readonly result: MyBidCase }
  /** 조회는 성공했는데 이 건이 없다 — 투찰 기록이 없는 건이다. */
  | { readonly kind: 'MISSING' };

/** 집계 한 축 — 응답의 접두사를 벗긴 꼴. */
export interface RateAxis {
  readonly id: 'recorded' | 'reconciled';
  readonly label: string;
  /** 이 축이 **무엇을 세는가** — 두 축을 나란히 두면 그 차이가 질문이 된다. */
  readonly why: string;
  readonly state: AwardRateState;
  /** 서버가 준 **문자열 그대로**(D-7E3b-8) — 숫자로 바꾸면 소수 자리를 잃는다. */
  readonly rate: string | null;
  readonly wonCount: number;
  readonly lostCount: number;
  readonly denominator: number;
  readonly failedOrRebidCount: number;
  readonly undecidedCount: number;
}

/**
 * 집계를 두 축으로 편다 — **이 함수가 접두사를 아는 유일한 자리**다.
 *
 * 축마다 JSX 를 따로 두면 한쪽만 고쳐지는 길이 생기므로 화면은 같은 컴포넌트에 이 값을 두 번
 * 넘긴다. 반환이 **길이 둘의 튜플**이라 축 하나를 빠뜨리면 컴파일에서 멈춘다.
 */
export function rateAxesOf(aggregate: MyBidAggregate): readonly [RateAxis, RateAxis] {
  return [
    {
      id: 'recorded',
      label: '내가 적은 결과 기준',
      why: '운영자가 입찰함에 기록한 상태를 센다',
      state: aggregate.recordedRateState,
      rate: aggregate.recordedAwardRate,
      wonCount: aggregate.recordedWonCount,
      lostCount: aggregate.recordedLostCount,
      denominator: aggregate.recordedDenominator,
      failedOrRebidCount: aggregate.recordedFailedOrRebidCount,
      undecidedCount: aggregate.recordedUndecidedCount,
    },
    {
      id: 'reconciled',
      label: '서버 대사 기준',
      why: '공개된 낙찰 결과와 내 상호를 견준 판정을 센다',
      state: aggregate.reconciledRateState,
      rate: aggregate.reconciledAwardRate,
      wonCount: aggregate.reconciledWonCount,
      lostCount: aggregate.reconciledLostCount,
      denominator: aggregate.reconciledDenominator,
      failedOrRebidCount: aggregate.reconciledFailedOrRebidCount,
      undecidedCount: aggregate.reconciledUndecidedCount,
    },
  ];
}
