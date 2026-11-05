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
   * **문면을 축어로 적는다**(D-1 갱신 2026-11-05). 상수끼리 맞대면 글자가 바뀌어도 등식이
   * 참이다 — 그리고 이 글자는 **저장소의 다른 없음 자리와 같아야 하는 값**이라 그 사실을
   * 여기서 잠근다(시각 칸만 다른 말을 쓰면 이 slice 가 없애려는 불일치를 만든다).
   */
  it('없음 표지가 화면 전반의 「값 없음」과 같은 글자다', () => {
    expect(ABSENT_INSTANT).toBe('값 없음');
    expect(instantDisplayOf(null).text).toBe('값 없음');
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

  /**
   * **오프셋이 없으면 읽지 않는다**(r1 cr M-3 · v L-4). `Date.parse` 는 오프셋 없는 date-time 을
   * **장비 지역 시각**으로 읽는다 — 그러면 「+9h 뒤 UTC 칸으로 읽어 장비 시간대를 배제」가
   * 깨지고, 「알 수 없음」이 아니라 **그럴듯한 틀린 시각**이 선다. 조용한 오답이 이 모듈이
   * 피하려는 바로 그것이다.
   */
  it('오프셋이 없는 값은 읽지 않는다 — 장비 시간대로 추측하지 않는다', () => {
    for (const naive of ['2026-11-13T17:28', '2026-11-13T17:28:00', '2026-11-13T17:28:00.000']) {
      expect(instantDisplayOf(naive).kind, `${naive} 을 읽어 버렸다`).toBe('unreadable');
    }
  });

  it('Z 와 숫자 오프셋은 둘 다 읽는다', () => {
    expect(instantDisplayOf('2026-11-05T00:30:00Z').kind).toBe('readable');
    expect(instantDisplayOf('2026-11-05T09:30:00+09:00').kind).toBe('readable');
    expect(instantDisplayOf('2026-11-05T09:30:00+0900').kind).toBe('readable');
    expect(instantDisplayOf('2026-11-04T19:30:00-05:00').kind).toBe('readable');
  });

  /**
   * **넘치는 날짜를 조용히 굴리지 않는다**(같은 지적). `Date.parse('2026-02-30T00:00:00Z')` 는
   * NaN 이 아니라 **3월 2일**이다 — 서버가 어긋난 값을 보낸 사실이 사라진다.
   */
  it('달력에 없는 날짜는 읽지 않는다 — 다음 달로 굴리지 않는다', () => {
    for (const invalid of ['2026-02-30T00:00:00Z', '2026-13-01T00:00:00Z', '2026-11-31T00:00:00Z']) {
      expect(instantDisplayOf(invalid).kind, `${invalid} 을 굴려 읽었다`).toBe('unreadable');
    }
  });

  it('윤년 2월 29일은 읽는다 — 왕복 검사가 참을 거부하지 않는다', () => {
    expect(instantDisplayOf('2028-02-29T00:00:00Z').kind).toBe('readable');
  });

  it('`daysUntil` 도 같은 입력을 세지 않는다', () => {
    const now = new Date('2026-10-29T00:00:00Z');

    expect(daysUntil('2026-11-13T17:28', now)).toBeNull();
    expect(daysUntil('2026-02-30T00:00:00Z', now)).toBeNull();
  });

  it('두 표지가 서로 다른 글자다', () => {
    expect(UNREADABLE_INSTANT).toBe('알 수 없음');
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
 * **KST 달력일의 차다 — 남은 시간이 아니다**(M9/web-followups, `OPEN-M9-DDAY-CALENDAR`).
 *
 * 앞 판은 24시간으로 나눠 내림·올림했다. 그래서 운영자가 **달력을 보고 세는 날**과 어긋났다:
 * 23:00 에 이튿날 01:00 마감이면 달력으로 하루 뒤인데 `D-0` 이었다. 아래가 그 경계 전수다.
 */
describe('D-day 는 KST 달력일로 센다 (OPEN-M9-DDAY-CALENDAR)', () => {
  /** 23:00 KST(= 14:00Z) 에 **이튿날** 01:00 KST(= 16:00Z) 마감 — OPEN 이 지목한 자리다. */
  it('두 시간 뒤 마감이 달력으로 이튿날이면 D-1 이다', () => {
    const now = new Date('2026-11-06T14:00:00Z');

    expect(daysUntil('2026-11-06T16:00:00Z', now)).toBe(1);
  });

  /** 같은 달력일 안이면 **거의 하루**가 남아도 `D-0` 이다 — 오늘 마감이기 때문이다. */
  it('같은 달력일 안의 마감은 23시간 58분이 남아도 D-0 이다', () => {
    const now = new Date('2026-11-06T15:01:00Z');

    expect(daysUntil('2026-11-07T14:59:00Z', now)).toBe(0);
  });

  /** 거울상 — 한 시간 전 마감이 달력으로 **어제**면 `D+1` 이다(하루 과장이 아니라 달력 사실이다). */
  it('한 시간 전 마감이 달력으로 어제면 D+1 이다', () => {
    const now = new Date('2026-11-06T15:01:00Z');

    expect(daysUntil('2026-11-06T14:00:00Z', now)).toBe(-1);
  });

  /**
   * **자정 양쪽의 1 분**이 날을 가른다. 두 눈금이 같은 달력일/다른 달력일로 갈리는 가장 좁은
   * 자리라, 오프셋을 잘못 더하면 여기가 먼저 틀린다.
   */
  it('KST 자정 양쪽의 1 분이 날을 가른다', () => {
    const beforeMidnight = new Date('2026-11-06T14:59:00Z');
    const afterMidnight = new Date('2026-11-06T15:00:00Z');

    expect(daysUntil('2026-11-06T15:00:00Z', beforeMidnight)).toBe(1);
    expect(daysUntil('2026-11-06T15:00:00Z', afterMidnight)).toBe(0);
  });

  /** 여러 날은 **시각과 무관하게** 달력일 수로 센다 — 끝의 시·분이 날 수를 깎지 않는다. */
  it('여러 날도 달력일 수로 센다 — 시·분이 날 수를 깎지 않는다', () => {
    const now = new Date('2026-11-06T14:59:00Z');

    expect(daysUntil('2026-11-09T00:01:00Z', now)).toBe(3);
    expect(daysUntil('2026-11-03T23:59:00Z', now)).toBe(-2);
  });

  /**
   * **`-0` 자리가 없어졌다.** 앞 판은 `Math.ceil(-0.25)` 가 `-0` 이라 「지난 날」이 「남은 날」
   * 분기로 새고 표시가 `D+-0` 이 될 수 있었다. 달력일 차는 정수라 그 자리가 **구성상** 없다.
   */
  it('같은 달력일의 지난 마감도 양의 0 이다 — D+-0 꼴이 서지 않는다', () => {
    const now = new Date('2026-11-06T06:00:00Z');
    const days = daysUntil('2026-11-06T05:00:00Z', now);

    expect(days).toBe(0);
    expect(Object.is(days, -0), '-0 이 돌아왔다 — 표시가 D+-0 이 된다').toBe(false);
    expect(dayCountText(days)).toBe('D-0');
  });
});

/**
 * 벽시계를 읽는 자리가 **이 모듈 하나**다(`OPEN-7E-GATE-RESIDUAL` ⑨ 처방). 나머지
 * `src/screens`·`src/display` 는 lint 가 막는다 — 그 술어는 `eslint.config.js` 가 든다.
 */
describe('벽시계 읽기는 한 자리다', () => {
  /**
   * **이 파일도 시계 금지의 대상이다**(예외는 `timeDisplay.ts` 하나) — 그래서 기준을 `Date.now()`
   * 로 잡지 않고 **고정 시점의 파싱값**으로 잡는다. 「0 이나 고정 epoch 가 아니라 지금을 읽는다」가
   * 이 단언의 내용이다.
   */
  it('지금을 Date 로 낸다', () => {
    const read = readClock();

    expect(read).toBeInstanceOf(Date);
    expect(read.getTime()).toBeGreaterThan(Date.parse('2026-01-01T00:00:00Z'));
  });
});
