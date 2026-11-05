import { describe, expect, it } from 'vitest';

import {
  NOTICES_RESULT_STATE_DISPLAY,
  NOTICE_STATUS_DISPLAY,
  SUGGESTIONS_RESULT_STATE_DISPLAY,
  SUGGESTION_LICENSE_VERDICT_DISPLAY,
  amountDisplayOf,
  noticeStatusDisplayOf,
} from './noticeDisplay';
import { displayOrUnknown } from './stateDisplay';

describe('화면 B 상태 표시 표 — 계약 enum 전수', () => {
  it('공고 검색의 빈 결과 두 뜻이 다른 문면이다', () => {
    expect(Object.keys(NOTICES_RESULT_STATE_DISPLAY).sort()).toStrictEqual(
      ['MATCHED', 'NOTHING_COLLECTED', 'NO_MATCH'].sort(),
    );
    expect(displayOrUnknown(NOTICES_RESULT_STATE_DISPLAY, 'NOTHING_COLLECTED').label).not.toBe(
      displayOrUnknown(NOTICES_RESULT_STATE_DISPLAY, 'NO_MATCH').label,
    );
  });

  /** D-7E-9 — 제안 목록의 「감시 조건 미설정」은 **API 상태**이고 화면이 추론하지 않는다. */
  it('제안 목록의 네 상태가 전부 표에 있다', () => {
    expect(Object.keys(SUGGESTIONS_RESULT_STATE_DISPLAY).sort()).toStrictEqual(
      ['MATCHED', 'NO_OPEN_NOTICES', 'WATCH_NOT_CONFIGURED', 'NO_MATCH'].sort(),
    );
    expect(displayOrUnknown(SUGGESTIONS_RESULT_STATE_DISPLAY, 'WATCH_NOT_CONFIGURED').label).toContain('감시 조건');
  });

  it('제안 카드의 면허 판정은 두 값이고 판정 불가가 미달이 아니다', () => {
    expect(Object.keys(SUGGESTION_LICENSE_VERDICT_DISPLAY).sort()).toStrictEqual(['ELIGIBLE', 'UNCERTAIN'].sort());
    expect(displayOrUnknown(SUGGESTION_LICENSE_VERDICT_DISPLAY, 'UNCERTAIN').tone).toBe('unjudged');
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

  /**
   * v r2 L-8 — 모르는 코드를 **중립 배지**로 내면 아는 라벨처럼 보인다. 미지 상태의 관례를
   * `unknownStateDisplay` 하나로 모았으므로 코드를 싣되 어조가 「아직 판정하지 않음」이다.
   */
  it('모르는 값은 서버가 준 코드를 싣고 미지 어조로 보인다', () => {
    const unknown = noticeStatusDisplayOf('SomeFutureStatus');

    expect(unknown.label).toContain('SomeFutureStatus');
    expect(unknown.tone).toBe('unjudged');
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
    // 부속 세 칸은 **표의 문면**이다(M9 D-3) — 통화는 금액 글자가 이미 들었으므로 여기 없다.
    expect(shown.provenance).toBe('기초금액 · 과세 구분 모름 · 공고 게시값');
    for (const token of ['BASE_AMOUNT', 'UNKNOWN', 'PUBLISHED']) {
      expect(shown.provenance, `${token} 이 화면 문면에 남았다`).not.toContain(token);
    }
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

// D-day 의 test 는 **`timeDisplay.test.ts` 로 옮겼다**(M9 D-1·D-2) — 판정 함수가 그 모듈로
// 갔으므로 단언도 함께 간다(여기 남기면 두 자리가 같은 주장을 하고 한쪽이 먼저 낡는다).
