import type { OpeningResultView } from '../api/queries';

import type { StateDisplay } from './stateDisplay';

/**
 * 개찰 결과의 표시(M7/7E-3a, SET-02 표시 절반).
 *
 * **아홉 갈래를 접지 않는다**(D-7E3a-7). 계약이 「데이터가 없다 · 결과가 없다 · 아직 개찰
 * 전이다」를 서로 다른 값으로 둔 이유는 운영자의 다음 행동이 다르기 때문이다 — 접으면 그
 * 구분이 화면에서 사라진다.
 *
 * **잠정과 확정은 다른 칸이다.** 아래 두 읽기가 각각 자기 접두의 칸만 보므로, 한쪽 값이
 * 다른 쪽의 대체값이 되는 길이 구조적으로 없다(분기로 막으면 그 분기를 지우는 변이가 조용히
 * 지나간다).
 */
export type OpeningResultState = OpeningResultView['state'];
export type ProvisionalRankOneState = OpeningResultView['provisionalRankOneState'];

export const OPENING_RESULT_STATE_DISPLAY = {
  NOTICE_ABSENT: {
    label: '공고를 모름',
    why: '그 공고를 수집한 적이 없다',
    tone: 'absent',
  },
  NOT_OPENED_YET: {
    label: '아직 개찰 전',
    why: '투찰 마감이 아직 지나지 않았다',
    tone: 'neutral',
  },
  NOT_COLLECTED: {
    label: '개찰 자료 없음',
    why: '개찰 축 원문도 결과도 아직 없다',
    tone: 'absent',
  },
  OBSERVED_NOT_PROMOTED: {
    label: '원문만 적재됨',
    why: '개찰 축 원문은 있으나 결과로 승격되지 않았다',
    tone: 'unjudged',
  },
  OPENED: { label: '개찰 완료', tone: 'neutral' },
  FAILED_BID: { label: '유찰', tone: 'neutral' },
  REBID: { label: '재입찰', tone: 'neutral' },
  PROGRESS_UNRECOGNIZED: {
    label: '진행구분 미등재',
    why: '라벨이 등재 어휘 밖이다 — 개찰완료로 접지 않는다',
    tone: 'unjudged',
  },
  PROGRESS_ABSENT: {
    label: '진행구분 없음',
    why: '결과 행은 있으나 진행구분 칸이 비었다',
    tone: 'unjudged',
  },
} as const satisfies Record<OpeningResultState, StateDisplay>;

/**
 * 잠정 1위 축의 상태 넷. **순위를 투찰금액으로 재계산하지 않는다**(계약) — 동값으로 1위가
 * 둘이면 그 사실(`RANK_DUPLICATED`)이 답이고 화면이 하나를 고르지 않는다.
 */
export const PROVISIONAL_RANK_ONE_DISPLAY = {
  NOT_OBSERVED: { label: '투찰 행 미관측', tone: 'absent' },
  RANK_MISSING: {
    label: '순위 1 없음',
    why: '어느 행도 순위 1이 아니다',
    tone: 'unjudged',
  },
  RANK_DUPLICATED: {
    label: '순위 1이 여럿',
    why: '동값을 임의로 깨지 않는다',
    tone: 'unjudged',
  },
  DETERMINED: { label: '잠정 1위 확인', tone: 'neutral' },
} as const satisfies Record<ProvisionalRankOneState, StateDisplay>;

/**
 * 금액 한 축의 읽기 결과 — **세 갈래**다. 「아직 안 왔다(`ABSENT`)」와 「반쯤 왔다
 * (`INCOMPLETE`)」를 한 얼굴로 접지 않는다: 전자는 수집·승격의 순서이고 후자는 서버가 계약을
 * 어긴 것이라 운영자가 할 일이 다르다.
 *
 * `INCOMPLETE` 에서 **숫자를 그리지 않는 것**이 이 타입의 목적이다. 금액은 값만으로 해석되지
 * 않는다(v2-지침서 §4) — 통화·basis·과세·provenance 없이 「1,350,000,000원」을 보이면 운영자가
 * 그 넷을 짐작한다.
 */
export type MoneyReading<T> =
  | { readonly kind: 'PRESENT'; readonly value: T }
  | { readonly kind: 'ABSENT' }
  | { readonly kind: 'INCOMPLETE'; readonly missing: readonly string[] };

/** 확정 낙찰금액 — provenance 까지 **다섯 칸**이 함께 와야 값이다. */
export interface ConfirmedAward {
  readonly amountWon: number;
  readonly currency: string;
  readonly basis: string;
  readonly vatTreatment: string;
  readonly provenance: string;
}

/**
 * 잠정 1위 투찰금액 — 계약이 **세 칸**(금액·통화·과세)만 준다. 확정 축과 칸 수가 다른 것이
 * 두 축을 한 타입에 담지 않는 이유다(basis·provenance 가 없는 축에 그 칸을 만들면 화면이
 * 빈 값을 채우고 싶어진다).
 */
export interface ProvisionalRankOneBid {
  readonly amountWon: number;
  readonly currency: string;
  readonly vatTreatment: string;
  /** 투찰율은 **표기 그대로의 문자열**이다 — 숫자로 바꾸면 소수 자리를 잃는다(계약). */
  readonly bidRateFraction: string | null;
  /** 잠정 축의 자기 관측 시각 — 소비자가 낡음을 스스로 판정한다(계약). */
  readonly observedAt: string | null;
}

/**
 * 필수 칸의 짝에서 세 갈래를 고른다. **전부 없으면 ABSENT · 전부 있으면 PRESENT · 그 사이는
 * INCOMPLETE** 이고, 빠진 칸의 이름을 그대로 들고 간다(진단이 「무언가 없다」로 뭉개지지 않게).
 */
function readRequired<T>(
  required: ReadonlyArray<readonly [name: string, value: unknown]>,
  build: () => T,
): MoneyReading<T> {
  const missing = required.filter(([, value]) => value === null || value === undefined).map(([name]) => name);
  if (missing.length === required.length) {
    return { kind: 'ABSENT' };
  }
  if (missing.length > 0) {
    return { kind: 'INCOMPLETE', missing };
  }
  return { kind: 'PRESENT', value: build() };
}

/**
 * 확정 낙찰금액 — **`confirmedAward*` 칸만 읽는다.** 잠정 축의 칸은 이 함수의 본문에 이름조차
 * 없으므로 「확정이 비면 잠정으로 메운다」가 성립하지 않는다(SET-02).
 */
export function confirmedAwardReading(view: OpeningResultView): MoneyReading<ConfirmedAward> {
  return readRequired(
    [
      ['confirmedAwardAmountWon', view.confirmedAwardAmountWon],
      ['confirmedAwardAmountCurrency', view.confirmedAwardAmountCurrency],
      ['confirmedAwardAmountBasis', view.confirmedAwardAmountBasis],
      ['confirmedAwardAmountVatTreatment', view.confirmedAwardAmountVatTreatment],
      ['confirmedAwardAmountProvenance', view.confirmedAwardAmountProvenance],
    ],
    () => ({
      amountWon: view.confirmedAwardAmountWon!,
      currency: view.confirmedAwardAmountCurrency!,
      basis: view.confirmedAwardAmountBasis!,
      vatTreatment: view.confirmedAwardAmountVatTreatment!,
      provenance: view.confirmedAwardAmountProvenance!,
    }),
  );
}

/**
 * 잠정 1위 투찰금액 — **`provisionalRankOneBidAmount*` 칸만 읽는다.** 투찰율·관측 시각은
 * 금액 축의 필수가 아니다(금액이 없어도 그 둘만 올 수 있고, 그때 금액은 `ABSENT` 다).
 */
export function provisionalRankOneReading(view: OpeningResultView): MoneyReading<ProvisionalRankOneBid> {
  return readRequired(
    [
      ['provisionalRankOneBidAmountWon', view.provisionalRankOneBidAmountWon],
      ['provisionalRankOneBidAmountCurrency', view.provisionalRankOneBidAmountCurrency],
      ['provisionalRankOneBidAmountVatTreatment', view.provisionalRankOneBidAmountVatTreatment],
    ],
    () => ({
      amountWon: view.provisionalRankOneBidAmountWon!,
      currency: view.provisionalRankOneBidAmountCurrency!,
      vatTreatment: view.provisionalRankOneBidAmountVatTreatment!,
      bidRateFraction: view.provisionalRankOneBidRateFraction ?? null,
      observedAt: view.provisionalRankOneObservedAt ?? null,
    }),
  );
}
