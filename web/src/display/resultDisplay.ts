import type { BidCaseState } from './caseDisplay';

/**
 * 화면 E 결과 확인의 두 표(M7/7E-3a).
 *
 * **E 는 읽기 화면이다**(D-7E3a-8) — 그래서 D 의 `bidBoxDisplay` 와 달리 여기에 전이 표가
 * 없다. 있는 것은 「어느 상태가 이 화면의 묶음인가」와 「어느 자리가 아직 비어 있는가」뿐이다.
 */

/**
 * 상태 14 → 결과 단계인가. **계약 문면(「화면 E 건별」)이 든 일곱**이 참이다.
 *
 * `BID_RECORDED`(제출함)가 거짓인 이유: 그 건의 다음 행동은 개찰 대기로 넘기는 것이고 그
 * 전이는 D 가 든다 — 결과를 기다리는 단계에 아직 들어가지 않았다. `SKIPPED` ·
 * `DEADLINE_PASSED_UNRECORDED` 도 거짓이다(결과가 날 건이 아니다).
 *
 * `satisfies` 가 전수를 들므로 계약에 상태가 늘면 이 자리가 컴파일에서 멈춘다 — 새 상태가
 * 조용히 「E 에 안 보임」으로 떨어지지 않는다.
 */
export const RESULT_STAGE = {
  WATCHING: false,
  DEFERRED: false,
  PRICE_RECEIVED: false,
  BID_PLANNED: false,
  BID_RECORDED: false,
  AWAITING_OPENING: true,
  OPENED_PROVISIONAL_FIRST: true,
  OPENED_OUT_OF_RANK: true,
  UNDER_QUALIFICATION_REVIEW: true,
  WON: true,
  LOST: true,
  FAILED_OR_REBID: true,
  SKIPPED: false,
  DEADLINE_PASSED_UNRECORDED: false,
} as const satisfies Record<BidCaseState, boolean>;

/** 표 조회의 **유일한 자리** — 상태 문자열을 들여다보는 추론이 없다. */
export function isResultStage(state: BidCaseState): boolean {
  return RESULT_STAGE[state];
}

/**
 * 채우지 않은 자리. **뒤에 오는 것(`LATER`)과 영영 오지 않는 것(`PERMANENT`)을 가른다** —
 * 운영자가 기다릴지 말지가 그것으로 갈린다.
 *
 * 표로 둔 덕에 **M7/7E-3b 의 일이 줄을 지우는 diff 로 끝났다**: 「준비 중」이던 다섯(내
 * 투찰가 · 차이 · 고른 안 · 자동 대사 · 낙찰률)은 `readMyBidResults` 가 서면서 화면이 받았다.
 * 그 자리를 표에 남겨 두면 이미 그린 값 옆에 「준비 중」이 함께 서고, 운영자는 그 칸이 아직
 * 안 되는 것으로 읽는다.
 *
 * 남은 둘은 **영영 오지 않는** 자리다. `kind` 를 그대로 두는 이유는 그 구별이 이 표의
 * 요점이기 때문이고, 새 `LATER` 가 생기면 그때도 같은 자리가 받는다.
 */
export interface PendingSlot {
  readonly id: string;
  readonly label: string;
  /** 왜 비어 있는가 — 배지만 적지 않는다. */
  readonly why: string;
  readonly kind: 'LATER' | 'PERMANENT';
}

/**
 * 자리의 분류 → **배지 문면**(M14/14D — 13A OPEN ⓖ 를 닫는 자리).
 *
 * 앞 판은 한 카드에서 두 말을 했다. 배지는 「다루지 않음」(영영 오지 않는다), 사유는 「지금은
 * 제공하지 않습니다」(지금은 아니다). 갈림의 뿌리는 **기록된 결정이 채택 장부보다 낡은 것**이다 —
 * M7 결정이 역산·추천 오차를 영구 빈 자리로 분류했고, 그 뒤 2026-11-14 에 페이퍼 투찰 정산이
 * **채택**됐다(구현 대기). 13A 는 **사유만** 장부에 맞췄고 배지는 낡은 결정 쪽에 남았다.
 *
 * 팀장 판정(2026-11-20): 추천은 장부 쪽이므로 **배지를 사유와 맞는 문면으로** 옮기고, `kind` 는
 * 그 기능 마일스톤이 값을 낼 때 정한다. 그래서 이 slice 가 움직이는 것은 **문면 한 칸**이고
 * 분류 축은 그대로다 — 「뒤에 오는 것」과 「지금 없는 것」의 구별이 여전히 둘을 가른다.
 */
const PENDING_BADGE = {
  LATER: '준비 중',
  // 「다루지 않음」이 아니다 — 그 말은 영영 오지 않는다는 주장이고, 사유의 「지금은」과 어긋난다.
  PERMANENT: '지금은 없음',
} as const satisfies Record<PendingSlot['kind'], string>;

/** 배지 조회의 **유일한 자리** — 화면이 분류를 보고 글자를 고르지 않는다. */
export function pendingBadgeOf(kind: PendingSlot['kind']): string {
  return PENDING_BADGE[kind];
}

export const RESULT_PENDING_SLOTS: readonly PendingSlot[] = [
  {
    id: 'back-calculation',
    label: '역산(이 금액이면?)',
    why: '지금은 제공하지 않습니다',
    kind: 'PERMANENT',
  },
  {
    id: 'recommendation-error',
    label: '추천 오차',
    why: '추천 값이 먼저 서야 잴 수 있습니다 — 지금은 제공하지 않습니다',
    kind: 'PERMANENT',
  },
];
