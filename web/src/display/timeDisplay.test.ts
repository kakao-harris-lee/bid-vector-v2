import { describe, expect, it } from 'vitest';

import {
  ABSENT_INSTANT,
  UNREADABLE_INSTANT,
  dayCountText,
  daysUntil,
  instantDisplayOf,
  readClock,
} from './timeDisplay';

/**
 * 시각 표기의 전수(M9/screen-display D-1). **네 갈래**가 다른 얼굴이다 — 읽힌 값 · 없는 값 ·
 * 읽히지 않은 값 · 상대 날 수.
 */
describe('절대 시각은 KST 로 보이고 초 이하를 버린다 (D-1)', () => {
  it('UTC 가 날을 넘기면 KST 의 날로 보인다 — 아홉 시간을 더해 센다', () => {
    expect(instantDisplayOf('2026-11-04T15:30:00Z').text).toBe('2026-11-05 00:30');
  });

  /** 1988 년 이후 한국에 일광절약시간이 없다 — 여름 날짜도 같은 오프셋이다. */
  it('여름 날짜에도 오프셋이 같다', () => {
    expect(instantDisplayOf('2026-07-15T15:00:00Z').text).toBe('2026-07-16 00:00');
  });

  /** **절삭이다 — 올림이 아니다.** 59.999 초가 다음 분으로 올라가면 마감 시각이 과장된다. */
  it('초 이하를 버린다 — 다음 분으로 올리지 않는다', () => {
    expect(instantDisplayOf('2026-11-05T00:30:59.999Z').text).toBe('2026-11-05 09:30');
  });

  it('오프셋을 달고 온 값도 같은 순간으로 읽는다', () => {
    expect(instantDisplayOf('2026-11-05T09:30:00+09:00').text).toBe('2026-11-05 09:30');
    expect(instantDisplayOf('2026-11-05T00:30:00Z').text).toBe('2026-11-05 09:30');
  });

  it('한 자리 수를 0 으로 채운다', () => {
    expect(instantDisplayOf('2026-01-02T00:05:00Z').text).toBe('2026-01-02 09:05');
  });

  /** 「같은 해 생략」은 하지 않는다(D-1) — 해가 빠진 날짜는 다음 해 공고와 구분되지 않는다. */
  it('해를 생략하지 않는다', () => {
    expect(instantDisplayOf('2026-11-05T00:30:00Z').text).toContain('2026');
  });

  it('자정은 00:00 이고 24:00 이 아니다', () => {
    expect(instantDisplayOf('2026-11-04T15:00:00Z').text).toBe('2026-11-05 00:00');
  });

  /** `<time dateTime>` 은 **기계 가독 원문**이다 — 거기까지 서식을 바꾸지 않는다. */
  it('읽힌 값은 원문을 기계 가독 칸에 그대로 든다', () => {
    const display = instantDisplayOf('2026-11-05T00:30:00Z');

    expect(display.kind).toBe('readable');
    expect(display.machine).toBe('2026-11-05T00:30:00Z');
  });
});

describe('없는 시각과 읽히지 않는 시각은 다른 얼굴이다 (D-1)', () => {
  it('없으면 없음 표지를 내고 기계 칸을 비운다', () => {
    for (const absent of [null, undefined]) {
      const display = instantDisplayOf(absent);

      expect(display.kind).toBe('absent');
      expect(display.text).toBe(ABSENT_INSTANT);
      expect(display.machine).toBeNull();
    }
  });

  /**
   * **조용한 빈칸을 만들지 않는다**(D-1). 읽히지 않은 값은 「서버가 안 보냈다」가 아니라
   * 「보낸 값을 읽지 못했다」이고, 그 둘이 같은 얼굴이면 운영자가 가릴 수 없다.
   */
  it('읽히지 않으면 보이는 표지를 내고 원문을 그대로 내지 않는다', () => {
    const display = instantDisplayOf('어제쯤');

    expect(display.kind).toBe('unreadable');
    expect(display.text).toBe(UNREADABLE_INSTANT);
    expect(display.text).not.toContain('어제쯤');
    expect(display.machine).toBeNull();
  });

  it('빈 문자열은 없는 값이 아니라 읽히지 않은 값이다', () => {
    expect(instantDisplayOf('').kind).toBe('unreadable');
  });

  it('두 표지가 서로 다른 글자다', () => {
    expect(ABSENT_INSTANT).not.toBe(UNREADABLE_INSTANT);
  });
});

/**
 * D-day 는 요구표가 **클라이언트 계산으로 승인한 유일한 자리**(ⓐ 행)다 — 그래서 남은 날 수만
 * 계산하고 **상태 라벨은 계산하지 않는다**(마감 여부는 `status` 가 말한다, D-7E-4).
 *
 * 7E-1 때는 `noticeDisplay` 에 있었다. M9/screen-display D-1·D-2 로 **시각 축의 한 모듈**로
 * 옮겼다 — 상대 표기도 같은 모듈을 지난다.
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

  /** CR L-2 — 지난 쪽에 내림을 쓰면 몇 시간 전 마감이 하루 과장된다. */
  it('몇 시간 전에 마감한 공고는 하루로 세지 않는다', () => {
    expect(daysUntil('2026-10-28T18:00:00Z', now)).toBe(0);
    expect(daysUntil('2026-10-27T18:00:00Z', now)).toBe(-1);
  });

  it('마감이 없으면 날 수가 없다 — 지어내지 않는다', () => {
    expect(daysUntil(null, now)).toBeNull();
    expect(daysUntil(undefined, now)).toBeNull();
  });

  it('읽히지 않는 마감으로 날을 세지 않는다', () => {
    expect(daysUntil('어제쯤', now)).toBeNull();
  });

  it('D-day 문면은 남은 쪽과 지난 쪽의 부호가 다르다', () => {
    expect(dayCountText(2)).toBe('D-2');
    expect(dayCountText(0)).toBe('D-0');
    expect(dayCountText(-2)).toBe('D+2');
  });

  it('날 수가 없으면 D-day 자리도 없다', () => {
    expect(dayCountText(null)).toBeNull();
  });
});

/**
 * 벽시계를 읽는 자리가 **이 모듈 하나**다(`OPEN-7E-GATE-RESIDUAL` ⑨ 처방). 나머지
 * `src/screens`·`src/display` 는 lint 가 막는다 — 그 술어는 `eslint.config.js` 가 든다.
 */
describe('벽시계 읽기는 한 자리다', () => {
  it('지금을 Date 로 낸다', () => {
    const before = Date.now();
    const read = readClock();

    expect(read).toBeInstanceOf(Date);
    expect(read.getTime()).toBeGreaterThanOrEqual(before);
  });
});
