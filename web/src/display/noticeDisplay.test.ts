import { describe, expect, it } from 'vitest';

import {
  NOTICES_RESULT_STATE_DISPLAY,
  NOTICE_STATUS_DISPLAY,
  SUGGESTIONS_RESULT_STATE_DISPLAY,
  SUGGESTION_LICENSE_VERDICT_DISPLAY,
  amountDisplayOf,
  daysUntil,
  noticeStatusDisplayOf,
} from './noticeDisplay';
import { displayOf } from './stateDisplay';

describe('화면 B 상태 표시 표 — 계약 enum 전수', () => {
  it('공고 검색의 빈 결과 두 뜻이 다른 문면이다', () => {
    expect(Object.keys(NOTICES_RESULT_STATE_DISPLAY).sort()).toStrictEqual(
      ['MATCHED', 'NOTHING_COLLECTED', 'NO_MATCH'].sort(),
    );
    expect(displayOf(NOTICES_RESULT_STATE_DISPLAY, 'NOTHING_COLLECTED').label).not.toBe(
      displayOf(NOTICES_RESULT_STATE_DISPLAY, 'NO_MATCH').label,
    );
  });

  /** D-7E-9 — 제안 목록의 「감시 조건 미설정」은 **API 상태**이고 화면이 추론하지 않는다. */
  it('제안 목록의 네 상태가 전부 표에 있다', () => {
    expect(Object.keys(SUGGESTIONS_RESULT_STATE_DISPLAY).sort()).toStrictEqual(
      ['MATCHED', 'NO_OPEN_NOTICES', 'WATCH_NOT_CONFIGURED', 'NO_MATCH'].sort(),
    );
    expect(displayOf(SUGGESTIONS_RESULT_STATE_DISPLAY, 'WATCH_NOT_CONFIGURED').label).toContain('감시 조건');
  });

  it('제안 카드의 면허 판정은 두 값이고 판정 불가가 미달이 아니다', () => {
    expect(Object.keys(SUGGESTION_LICENSE_VERDICT_DISPLAY).sort()).toStrictEqual(['ELIGIBLE', 'UNCERTAIN'].sort());
    expect(displayOf(SUGGESTION_LICENSE_VERDICT_DISPLAY, 'UNCERTAIN').tone).toBe('unjudged');
  });
});

/**
 * **공고 상태는 계약이 enum 으로 적지 않는다**(`NoticeSummary.status` 가 `type: string`) —
 * 그래서 이 축만 `tsc` 가 전수를 보장하지 못한다. 알려진 값에는 문면을 주고, **모르는 값은
 * 서버가 준 코드를 그대로** 보인다(문면을 지어내지 않는다). 알려진 제한으로 등재한다.
 */
describe('공고 상태 — 계약이 enum 이 아닌 축', () => {
  it('알려진 값에는 문면이 있다', () => {
    expect(noticeStatusDisplayOf('Open').label).toBe('입찰 중');
    expect(Object.keys(NOTICE_STATUS_DISPLAY)).toContain('Closed');
  });

  it('모르는 값은 서버가 준 코드를 그대로 보이고 문면을 지어내지 않는다', () => {
    const unknown = noticeStatusDisplayOf('SomeFutureStatus');

    expect(unknown.label).toBe('SomeFutureStatus');
    expect(unknown.tone).toBe('neutral');
  });
});

/** 「값이 없으면 빈 자리(가짜 기본값 금지)」 — §6.5. 0 원도, 「미정」도 지어내지 않는다. */
describe('기초금액 표시', () => {
  it('금액과 basis·과세·provenance 가 함께 보인다', () => {
    const shown = amountDisplayOf({
      baseAmountWon: 1_200_000_000,
      baseAmountCurrency: 'KRW',
      baseAmountBasis: 'BASE_AMOUNT',
      baseAmountVatTreatment: 'UNKNOWN',
      baseAmountProvenance: 'PUBLISHED',
    });

    expect(shown.present).toBe(true);
    expect(shown.amount).toBe('1,200,000,000원');
    expect(shown.provenance).toContain('BASE_AMOUNT');
  });

  it('금액이 없으면 0 이 아니라 「값 없음」이다', () => {
    const shown = amountDisplayOf({
      baseAmountWon: null,
      baseAmountCurrency: null,
      baseAmountBasis: null,
      baseAmountVatTreatment: null,
      baseAmountProvenance: null,
    });

    expect(shown.present).toBe(false);
    expect(shown.amount).toBe('값 없음');
    expect(shown.amount).not.toMatch(/0/);
  });
});

/**
 * D-day 는 요구표가 **클라이언트 계산으로 승인한 유일한 자리**(ⓐ 행)다 — 그래서 남은 날 수만
 * 계산하고 **상태 라벨은 계산하지 않는다**(마감 여부는 `status` 가 말한다, D-7E-4).
 */
describe('마감 남은 날 — 클라이언트 계산의 경계', () => {
  const now = new Date('2026-10-29T00:00:00Z');

  it('마감이 있으면 남은 날을 센다', () => {
    expect(daysUntil('2026-10-31T00:00:00Z', now)).toBe(2);
    expect(daysUntil('2026-10-29T06:00:00Z', now)).toBe(0);
  });

  it('마감이 지났으면 음수를 내고 「마감」이라는 말을 만들지 않는다', () => {
    expect(daysUntil('2026-10-27T00:00:00Z', now)).toBe(-2);
  });

  it('마감이 없으면 날 수가 없다 — 지어내지 않는다', () => {
    expect(daysUntil(null, now)).toBeNull();
  });
});
