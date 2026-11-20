// @vitest-environment jsdom
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { AbsentValue } from '../components/AbsentValue';
import { KeyValueRow } from '../components/KeyValueRow';
import { SummaryTile, tileUnavailable, tileValue } from '../components/SummaryTile';
import { absenceTextOf, ABSENCE_REASONS } from '../display/absence';
import { instantDisplayOf } from '../display/timeDisplay';

import { InstantText } from './InstantText';

/**
 * 부재 어휘의 **렌더 층**(M14/14A ④ · 계약 「게이트」 절 · v M-2).
 *
 * 소스 훑기(`scripts/absence-vocabulary`)는 **글자**를 센다. 그래서 런타임에 조립한 글자는 그
 * 눈에 들지 않는다 — 이 층이 그 자리를 받는다: **그려진 DOM** 에 걷어낸 어휘가 0 이고, **같은
 * 렌더에서** 사유 문면이 N 개 이상이다.
 *
 * 둘째 조건이 요점이다. 부재가 하나도 없는 fixture 에서는 「0 건」이 늘 참이라 첫 조건만으로는
 * 공허하다 — 그래서 **부재가 실제로 그려지는** 표본을 쓰고 그 수를 함께 센다.
 *
 * **알려진 제한**: 이 층은 fixture 에 매인다(여기 마운트한 자리만 본다). 손으로 적은 리터럴을
 * 넓게 받는 것은 소스 훑기 쪽이고, 둘은 서로를 대신하지 않는다.
 */
afterEach(cleanup);

/** 걷어낸 옛 문면 — **조각에서 조립한다**(축어로 적으면 이 파일이 자기 훑기에 걸린다). */
const RETIRED_WORDING = ['값', '없음'].join(' ');

/**
 * 그려진 **텍스트와 두 속성**을 모은다. 사유가 속성으로 날아가도 같은 눈에 들어야 한다
 * (13A 문면 게이트가 같은 축을 든다).
 */
function renderedCopyOf(root: HTMLElement): string {
  const attributes = [...root.querySelectorAll('[title], [aria-label]')]
    .flatMap((one) => [one.getAttribute('title'), one.getAttribute('aria-label')])
    .filter((one): one is string => one !== null);
  return [root.textContent ?? '', ...attributes].join('\n');
}

const occurrencesOf = (text: string, needle: string): number => text.split(needle).length - 1;

const reasonCountOf = (text: string): number =>
  ABSENCE_REASONS.reduce((total, reason) => total + occurrencesOf(text, absenceTextOf(reason)), 0);

/**
 * 부재가 **실제로 그려지는** 표본. 자리마다 다른 길로 부재에 닿는다 — 값이 `null` 인 용어 줄,
 * 빈 글자인 용어 줄, 없는 값 얼굴의 타일, 읽은 뒤 비어 있는 시각 칸, 응답 전의 자리.
 */
function AbsenceSample() {
  return (
    <div>
      <dl>
        <KeyValueRow label="상호" value={null} testId="trade-name" />
        <KeyValueRow label="본사 소재지" value="" testId="region" />
      </dl>
      <SummaryTile label="적격" face={tileUnavailable('서버가 계수를 내지 않는다')} testId="eligible" />
      <SummaryTile label="투찰" face={tileValue('12')} testId="submitted" />
      <InstantText display={instantDisplayOf(null)} testId="checkpoint-at" />
      <AbsentValue reason="notLoaded" testId="pending" />
      {/*
        M14/14F — **운영자가 아직 적지 않은 칸**(v L-3). 그 사유에 닿는 길은 입력칸을 비추는 읽기
        자리다(넘김 카드의 투찰 금액). 사유가 늘면 아래 「전수가 적어도 한 번씩」 단언이 **그
        자리에서 멈추고**, 이 줄이 그 요구에 답한다 — 표본이 어휘보다 뒤처지지 않는 것이 그
        단언의 일이다.
      */}
      <AbsentValue reason="notEntered" testId="not-entered" />
    </div>
  );
}

describe('렌더 층 — 걷어낸 어휘가 그려진 DOM 에 없다', () => {
  it('부재가 실제로 그려지는 표본에서 옛 문면이 0 번이다', () => {
    const { container } = render(<AbsenceSample />);

    expect(occurrencesOf(renderedCopyOf(container), RETIRED_WORDING)).toBe(0);
  });

  /**
   * **비공허** — 같은 렌더에서 사유 문면을 센다. 부재가 하나도 없는 표본이면 위 단언이 늘 참이라
   * 아무것도 재지 않는다.
   */
  it('같은 렌더에서 사유 문면이 여럿이다', () => {
    const { container } = render(<AbsenceSample />);

    expect(reasonCountOf(renderedCopyOf(container))).toBeGreaterThanOrEqual(4);
  });

  /** 사유 **둘 다** 그려진다 — 한쪽만 서면 「두 사실이 다른 얼굴」이 이 층에서 공허하다. */
  it('사유 전수가 적어도 한 번씩 선다', () => {
    const copy = renderedCopyOf(render(<AbsenceSample />).container);

    for (const reason of ABSENCE_REASONS) {
      expect(occurrencesOf(copy, absenceTextOf(reason)), reason).toBeGreaterThan(0);
    }
  });
});

describe('양성 대조 — 술어 자신', () => {
  /** 런타임 조립은 소스 훑기의 눈 **밖**이다. 이 층이 받는 자리가 정확히 그것이다. */
  it('조각에서 조립한 옛 문면을 잡는다', () => {
    const Planted = () => <span>{['값', '없음'].join(' ')}</span>;
    const { container } = render(<Planted />);

    expect(occurrencesOf(renderedCopyOf(container), RETIRED_WORDING)).toBe(1);
  });

  /** 속성으로 날아간 옛 문면도 같은 눈에 든다. */
  it('속성에 실린 옛 문면을 잡는다', () => {
    const Planted = () => <span title={['값', '없음'].join(' ')}>값</span>;
    const { container } = render(<Planted />);

    expect(occurrencesOf(renderedCopyOf(container), RETIRED_WORDING)).toBe(1);
  });

  /** 음성 대조 — 사유 문면은 잡히지 않는다. */
  it('새 사유 문면은 옛 문면으로 세지 않는다', () => {
    const { container } = render(<AbsentValue reason="notSent" />);

    expect(occurrencesOf(renderedCopyOf(container), RETIRED_WORDING)).toBe(0);
    expect(reasonCountOf(renderedCopyOf(container))).toBe(1);
  });
});
