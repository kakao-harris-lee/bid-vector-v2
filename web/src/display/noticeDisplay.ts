import type { components, operations } from '../api/generated/operator-api';

import type { StateDisplay } from './stateDisplay';

/** 화면 B(오늘의 공고)가 소비하는 축의 표시 표(D-7E-4 · D-7E-9). */
type NoticesResultState = operations['searchNotices']['responses']['200']['headers']['X-Result-State'];
type SuggestionsResultState = operations['listSuggestions']['responses']['200']['headers']['X-Result-State'];
type Suggestion = components['schemas']['SuggestedNotice'];

/** 빈 결과의 **두 뜻**은 서버가 가른다 — 화면이 「결과 0 건」으로 접지 않는다. */
export const NOTICES_RESULT_STATE_DISPLAY = {
  MATCHED: { label: '조건에 맞는 공고가 있습니다', tone: 'neutral' },
  NO_MATCH: { label: '조건에 맞는 공고가 없습니다', why: '수집된 공고는 있습니다 — 조건을 넓혀 보십시오', tone: 'absent' },
  NOTHING_COLLECTED: { label: '아직 수집된 공고가 없습니다', why: '조건이 좁은 것이 아닙니다', tone: 'absent' },
} as const satisfies Record<NoticesResultState, StateDisplay>;

/**
 * D-7E-9 — 제안 목록이 비는 세 가지 이유가 서로 다른 답이다. 「감시 조건 미설정」은
 * **API 상태**이고 화면이 전략을 읽어 추론하지 않는다.
 */
export const SUGGESTIONS_RESULT_STATE_DISPLAY = {
  MATCHED: { label: '감시 조건을 통과한 공고입니다', tone: 'neutral' },
  NO_OPEN_NOTICES: { label: '입찰 가능한 열린 공고가 없습니다', tone: 'absent' },
  WATCH_NOT_CONFIGURED: {
    label: '감시 조건이 설정되지 않았습니다',
    why: '조건이 없으면 어떤 공고도 통과하지 않습니다 — 설정에서 조건을 세우십시오',
    tone: 'absent',
  },
  NO_MATCH: { label: '열린 공고는 있으나 통과한 공고가 없습니다', tone: 'absent' },
} as const satisfies Record<SuggestionsResultState, StateDisplay>;

/** 제안 카드는 **면허 축만** 판정한다 — 두 값이고 판정 불가가 미달이 아니다. */
export const SUGGESTION_LICENSE_VERDICT_DISPLAY = {
  ELIGIBLE: { label: '면허 충족', tone: 'good' },
  UNCERTAIN: { label: '면허 판정 불가', why: '미달이 아니라 판정하지 못했다', tone: 'unjudged' },
} as const satisfies Record<Suggestion['licenseVerdict'], StateDisplay>;

/**
 * **이 축만 계약이 enum 으로 적지 않는다**(`NoticeSummary.status` 가 `type: string`) — 그래서
 * `tsc` 가 전수를 보장하지 못하는 유일한 상태 축이다(알려진 제한). 알려진 값에는 문면을 주고
 * 모르는 값은 **서버가 준 코드를 그대로** 보인다 — 문면을 지어내지 않는다는 규율은 같다.
 */
export const NOTICE_STATUS_DISPLAY: Readonly<Record<string, StateDisplay>> = {
  Open: { label: '입찰 중', tone: 'neutral' },
  Renoticed: { label: '재공고', tone: 'neutral' },
  Closed: { label: '마감', tone: 'neutral' },
  Awarded: { label: '낙찰자 결정', tone: 'neutral' },
  Failed: { label: '유찰', tone: 'neutral' },
  Cancelled: { label: '취소', tone: 'neutral' },
};

export function noticeStatusDisplayOf(status: string): StateDisplay {
  return NOTICE_STATUS_DISPLAY[status] ?? { label: status, tone: 'neutral' };
}

/** 기초금액은 **금액과 provenance 가 한 값**이다(DEC-01) — 숫자만 떼어 보여 주지 않는다. */
export interface AmountDisplay {
  readonly present: boolean;
  readonly amount: string;
  readonly provenance: string | null;
}

type AmountFields = Pick<
  components['schemas']['NoticeSummary'],
  'baseAmountWon' | 'baseAmountCurrency' | 'baseAmountBasis' | 'baseAmountVatTreatment' | 'baseAmountProvenance'
>;

const ABSENT_AMOUNT = '값 없음';

/**
 * 값이 없으면 **빈 자리**다(§6.5 가짜 기본값 금지) — `0원`·`미정`을 지어내지 않는다. 통화가
 * 원이 아니면 숫자만 적지 않고 통화 코드를 함께 적는다(단위를 아는 척하지 않는다).
 */
export function amountDisplayOf(fields: AmountFields): AmountDisplay {
  const won = fields.baseAmountWon;
  if (won === null || won === undefined) {
    return { present: false, amount: ABSENT_AMOUNT, provenance: null };
  }
  const currency = fields.baseAmountCurrency;
  const formatted = won.toLocaleString('ko-KR');
  const amount = currency === 'KRW' || currency === null || currency === undefined ? `${formatted}원` : `${formatted} ${currency}`;
  const provenance = [fields.baseAmountBasis, fields.baseAmountVatTreatment, fields.baseAmountProvenance]
    .filter((part): part is string => part !== null && part !== undefined)
    .join(' · ');
  return { present: true, amount, provenance: provenance === '' ? null : provenance };
}

const MILLIS_PER_DAY = 86_400_000;

/**
 * 마감까지 남은 날 — 요구표가 **클라이언트 계산으로 승인한 유일한 자리**(ⓐ 행)다. 「마감됨」
 * 같은 **상태 라벨은 만들지 않는다** — 그것은 `status` 가 말한다(D-7E-4). 마감이 없으면 날 수도
 * 없다.
 *
 * **남은 쪽과 지난 쪽의 어림이 다르다**(CR L-2). 남은 쪽은 **내림**이다(반 나절 남았으면
 * `D-0` — 「아직 오늘」). 지난 쪽에 같은 내림을 쓰면 6시간 전에 마감한 공고가 `D+1` 로 **하루
 * 과장**된다(-0.25 → -1). 그래서 지난 쪽은 0 쪽으로 자른다(`Math.ceil`).
 */
export function daysUntil(deadlineAt: string | null | undefined, now: Date): number | null {
  if (deadlineAt === null || deadlineAt === undefined) {
    return null;
  }
  const deadline = Date.parse(deadlineAt);
  if (Number.isNaN(deadline)) {
    return null;
  }
  const days = (deadline - now.getTime()) / MILLIS_PER_DAY;
  const counted = days >= 0 ? Math.floor(days) : Math.ceil(days);
  // `Math.ceil(-0.25)` 는 **`-0`** 이다 — 그것은 `>= 0` 비교에서 참이라 「지난 날」이 「남은 날」
  // 분기로 새고, 표시도 `D+-0` 꼴이 될 수 있다. 0 으로 모은다.
  return counted === 0 ? 0 : counted;
}
