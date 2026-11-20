// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { SummaryTile, tileUnavailable, tileValue } from './SummaryTile';

/**
 * 요약 타일의 짝 test(M14/14A). 이 컴포넌트의 요점은 **두 얼굴이 타입으로 갈려 있는 것**이다.
 *
 * 와이어프레임의 결과 화면은 타일 넷을 나란히 두는데 그 가운데 둘은 **서버가 그 값을 내지
 * 않는다**(계약 「데이터」 절). 값 얼굴 하나에 빈 글자를 넣어 그리면 운영자가 그것을 0 이나
 * 「아직 계산 중」으로 읽는다 — 그래서 없는 값은 **숫자 자리를 아예 갖지 않고** 사유를 든다.
 */
afterEach(cleanup);

describe('값 얼굴', () => {
  it('라벨 · 값 · 각주를 그대로 그린다', () => {
    render(<SummaryTile label="투찰 (기록됨)" face={tileValue('12', { note: '최근 90일' })} testId="probe" />);
    const tile = screen.getByTestId('probe');

    expect(tile.textContent).toContain('투찰 (기록됨)');
    expect(tile.textContent).toContain('12');
    expect(tile.textContent).toContain('최근 90일');
  });

  it('곁수를 주면 값 곁에 선다 — 분모를 다른 줄로 흘리지 않는다', () => {
    render(<SummaryTile label="적격" face={tileValue('11', { beside: '/ 12' })} testId="probe" />);

    expect(screen.getByTestId('probe-value').textContent).toContain('/ 12');
  });

  it('각주를 주지 않으면 빈 자리를 만들지 않는다', () => {
    render(<SummaryTile label="낙찰" face={tileValue('2')} testId="probe" />);

    expect(screen.getByTestId('probe').textContent).toBe('낙찰2');
  });
});

describe('없는 값 얼굴', () => {
  it('사유를 그린다', () => {
    render(
      <SummaryTile label="하한 대비 위치" face={tileUnavailable('서버가 실현 하한을 내지 않는다')} testId="probe" />,
    );

    expect(screen.getByTestId('probe-unavailable').textContent).toBe('서버가 실현 하한을 내지 않는다');
  });

  /**
   * **값 자리가 없다.** 「있는데 비었다」와 「애초에 없다」가 같은 모양이면 그 둘을 가를 수 없고,
   * 비교하는 눈은 빈 숫자 자리를 0 으로 채워 읽는다.
   */
  it('값 자리를 아예 그리지 않는다 — 빈 숫자 칸을 두지 않는다', () => {
    render(<SummaryTile label="적격" face={tileUnavailable('계수를 내는 칸이 없다')} testId="probe" />);

    expect(screen.queryByTestId('probe-value')).toBeNull();
  });

  it('두 얼굴이 같은 표지를 쓰지 않는다 — 한 쪽만 읽는 단언이 공허해지지 않는다', () => {
    render(<SummaryTile label="투찰" face={tileValue('12')} testId="probe" />);

    expect(screen.queryByTestId('probe-unavailable')).toBeNull();
    expect(screen.getByTestId('probe-value')).not.toBeNull();
  });
});

describe('표지', () => {
  it('testId 가 없으면 바깥 상자도 안쪽 자리도 속성을 내지 않는다', () => {
    const { container } = render(<SummaryTile label="투찰" face={tileValue('12')} />);

    expect(container.querySelectorAll('[data-testid]')).toHaveLength(0);
  });
});
