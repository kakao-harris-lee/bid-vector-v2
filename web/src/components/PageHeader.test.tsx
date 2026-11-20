// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import { PageHeader } from './PageHeader';

/**
 * 화면 머리의 짝 test(M14/14A). 와이어프레임의 머리는 **제목 · 요약 한 줄 · 행동**이고, 세
 * 자리가 모두 선택이 아니라 **있으면 그리고 없으면 자리를 만들지 않는** 것이 요점이다 — 빈
 * 요약 노드를 늘 그리면 보조기기가 빈 문단을 읽고, 시각적으로도 제목 아래 여백이 거짓으로
 * 벌어진다.
 */
afterEach(cleanup);

describe('제목', () => {
  it('제목이 머리글 요소다 — 글자 크기로만 제목인 척하지 않는다', () => {
    render(<PageHeader title="오늘의 공고" />);

    expect(screen.getByRole('heading', { name: '오늘의 공고' })).not.toBeNull();
  });

  /**
   * 셸의 `h1` 은 앱 이름이다(기존 셸 test 가 그것을 든다). 화면 제목이 같은 층에 서면 한
   * 문서에 `h1` 이 둘이 되어 보조기기의 개요가 갈린다.
   */
  it('셸의 h1 아래 층이다', () => {
    render(<PageHeader title="오늘의 공고" />);

    expect(screen.getByRole('heading', { name: '오늘의 공고' }).tagName).toBe('H2');
  });
});

describe('요약 한 줄', () => {
  it('주면 제목 곁에 선다', () => {
    render(<PageHeader title="결과 확인" summary="실측만 셉니다" testId="probe" />);

    expect(screen.getByTestId('probe-summary').textContent).toBe('실측만 셉니다');
  });

  /**
   * **자리의 존재를 잰다**(변이 실측). 글자로만 재면 빈 노드를 늘 그리는 변이가 지나간다 —
   * 빈 문단의 `textContent` 는 빈 글자라 제목만 남은 글자와 구분되지 않는다.
   */
  /** 빈 글자는 **없음**이다(cr L-1) — 그대로 그리면 빈 문단이 선다. */
  it('빈 글자를 주면 자리를 만들지 않는다', () => {
    render(<PageHeader title="결과 확인" summary="" testId="probe" />);

    expect(screen.queryByTestId('probe-summary')).toBeNull();
    expect(screen.getByTestId('probe').textContent).toBe('결과 확인');
  });

  it('주지 않으면 빈 자리를 만들지 않는다 — 제목 글자가 머리의 전부다', () => {
    render(<PageHeader title="결과 확인" testId="probe" />);

    expect(screen.queryByTestId('probe-summary')).toBeNull();
    expect(screen.getByTestId('probe').textContent).toBe('결과 확인');
  });
});

/**
 * **요약은 `ReactNode` 다**(cr L-4) — 뒤 레인이 요약 줄 안에 부재 값이나 강조를 실을 수 있어야
 * 한다. 넓히면서 cr L-1 의 구멍(빈 자리를 그리는 꼴)을 다시 열지 않는 것이 이 묶음의 요점이다.
 */
describe('요약이 글자가 아닐 때 (cr L-4)', () => {
  it('요소를 그대로 그린다', () => {
    render(
      <PageHeader
        title="오늘의 공고"
        summary={<span data-testid="inner">감시 조건 통과 7건</span>}
        testId="probe"
      />,
    );

    expect(screen.getByTestId('probe-summary').contains(screen.getByTestId('inner'))).toBe(true);
  });

  it('여러 조각을 담은 배열도 그린다', () => {
    render(
      <PageHeader title="오늘의 공고" summary={['통과 ', <b key="n">7</b>, '건']} testId="probe" />,
    );

    expect(screen.getByTestId('probe-summary').textContent).toBe('통과 7건');
  });

  /** **빈 자리로 읽히는 값은 그리지 않는다** — 넓힌 뒤에도 cr L-1 의 구멍이 닫혀 있다. */
  it('빈 자리로 읽히는 값에는 자리를 만들지 않는다', () => {
    for (const blank of [undefined, null, false, true, '', '   ', [], ['', null]]) {
      cleanup();
      render(<PageHeader title="결과 확인" summary={blank} testId="probe" />);

      expect(screen.queryByTestId('probe-summary'), String(blank)).toBeNull();
      expect(screen.getByTestId('probe').textContent, String(blank)).toBe('결과 확인');
    }
  });

  /** 숫자 0 은 **값**이다 — 빈 글자와 다르다. */
  it('숫자 0 은 그린다', () => {
    render(<PageHeader title="결과 확인" summary={0} testId="probe" />);

    expect(screen.getByTestId('probe-summary').textContent).toBe('0');
  });
});

describe('행동', () => {
  it('주면 머리 안에 든다', () => {
    render(<PageHeader title="내 입찰함" actions={<button type="button">새로 고침</button>} testId="probe" />);

    expect(screen.getByTestId('probe').contains(screen.getByRole('button', { name: '새로 고침' }))).toBe(true);
  });

  /** 행동이 **제목 뒤**다 — 탭 차례가 제목을 건너뛰고 버튼으로 가지 않는다. */
  it('행동이 제목보다 뒤에 선다', () => {
    render(<PageHeader title="내 입찰함" actions={<button type="button">새로 고침</button>} />);
    const heading = screen.getByRole('heading', { name: '내 입찰함' });
    const action = screen.getByRole('button', { name: '새로 고침' });

    expect(heading.compareDocumentPosition(action) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('주지 않으면 빈 버튼 자리를 만들지 않는다', () => {
    render(<PageHeader title="내 입찰함" testId="probe" />);

    expect(screen.queryByTestId('probe-actions')).toBeNull();
    expect(screen.queryByRole('button')).toBeNull();
  });
});

describe('표지', () => {
  it('testId 가 없으면 바깥 상자도 안쪽 자리도 속성을 내지 않는다', () => {
    const { container } = render(
      <PageHeader title="설정" summary="한 줄" actions={<button type="button">행동</button>} />,
    );

    expect(container.querySelectorAll('[data-testid]')).toHaveLength(0);
  });
});
