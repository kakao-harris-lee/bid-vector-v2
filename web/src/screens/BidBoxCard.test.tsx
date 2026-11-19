// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { BidCase } from '../api/queries';
import { ACTION_INPUT, EXIT_FROM_STATES, exitsOf } from '../display/bidBoxDisplay';
import { BID_CASE_STATE_DISPLAY, type BidCaseState } from '../display/caseDisplay';

import { BidBoxCard, type ActionPayload } from './BidBoxCard';

/**
 * **카드가 내려놓기 표를 따르는가**(M13/13D 설계 검토 우회 ⑤).
 *
 * 등식 게이트(`scripts/bid-exit-transitions.test.mjs`)는 **표가 옳은지만** 재고 화면이 그 표를
 * 따르는지는 재지 않는다 — 카드가 표를 보지 않고 버튼을 직접 그리면 그 게이트는 초록이다.
 * 그 자리를 여기서 **상태 전수**로 닫는다. 표본 몇 개짜리 단언은 표를 무시한 카드를 통과시킨다.
 *
 * **앵커는 종류별 testId 다.** 카드 안 버튼을 전부 집는 술어로는 이 집합을 셀 수 없다 —
 * 상태 배지가 버튼이 되면 그 집합에 함께 들기 때문이다(13E). 버튼 **계수**를 쓰는 단언도 두지
 * 않는다(그 수는 남의 slice 가 머지되면 움직인다).
 *
 * 루프의 상태 집합은 **계약 어휘에서 유도한다**(손으로 적은 열넷이 아니다). 그 집합이 계약
 * 열거와 같은지는 위 게이트가 **독립으로** 센다 — 그래야 이 루프가 자기 출처를 자기 근거로
 * 삼지 않는다.
 */
const CONTRACT_STATES = Object.keys(BID_CASE_STATE_DISPLAY) as readonly BidCaseState[];

const CASE_ID = 11;

function bidCase(state: BidCaseState): BidCase {
  return {
    caseId: CASE_ID,
    noticeNumber: 'SYN-13D-CASE',
    noticeRound: '000',
    bidKind: 'REAL',
    state,
    caseVersion: 3,
    nextCheckpointKind: 'DEADLINE',
    nextCheckpointAt: '2099-01-03T00:00:00Z',
    overdue: false,
    reconciliationCandidate: false,
  };
}

function renderCard(state: BidCaseState): { readonly sent: ReturnType<typeof vi.fn> } {
  const sent = vi.fn();
  const router = createMemoryRouter(
    [
      { path: '/bids', element: <BidBoxCard bidCase={bidCase(state)} onSend={sent} /> },
      { path: '/notices/:noticeNumber/:noticeRound', element: <p>워크시트 자리</p> },
    ],
    { initialEntries: ['/bids'] },
  );
  render(<RouterProvider router={router} />);
  return { sent };
}

/** 그 카드에 실제로 그려진 내려놓기 종류 — testId 에서 뽑는다. */
function renderedExitKinds(): readonly string[] {
  return screen
    .queryAllByTestId(/^exit-/u)
    .map((element) => element.getAttribute('data-testid') ?? '')
    .map((testId) => testId.replace(/^exit-/u, '').replace(new RegExp(`-${CASE_ID}$`, 'u'), ''));
}

/** 「건너뜀」을 열고 사유를 적는다 — 확인 전까지. */
function openSkipWith(reason: string): void {
  fireEvent.click(screen.getByTestId(`exit-SKIP-${CASE_ID}`));
  fireEvent.change(screen.getByLabelText(ACTION_INPUT.SKIP.label!), { target: { value: reason } });
}

const send = (): void => {
  fireEvent.click(screen.getByTestId('action-send'));
};

afterEach(cleanup);

describe('카드가 내려놓기 표를 따른다 — 상태 전수', () => {
  it('상태마다 그려진 내려놓기가 표가 내는 것과 같다', () => {
    // **비공허 단언이 루프 앞에 선다**: 상태 집합이 비면 아래 루프가 조용히 통과한다.
    expect(CONTRACT_STATES.length, '계약 어휘가 비었다 — 루프가 공허해진다').toBeGreaterThan(0);
    // 내려놓기가 **어디에도** 서지 않는 판(표가 빈 판)도 루프만으로는 통과한다 — 그 꼴을 막는다.
    expect(
      CONTRACT_STATES.filter((state) => exitsOf(state).length > 0).length,
      '어느 상태에도 내려놓기가 없다 — 등식이 공허해진다',
    ).toBeGreaterThan(0);

    for (const state of CONTRACT_STATES) {
      cleanup();
      renderCard(state);

      expect(renderedExitKinds(), `${state}: 그려진 내려놓기가 표와 다르다`).toStrictEqual(
        exitsOf(state).map((one) => one.kind),
      );
    }
  });

  it('표가 든 상태에서만 그 버튼이 보인다 — 표의 멤버십을 그대로 되비춘다', () => {
    for (const kind of ['DEFER', 'SKIP'] as const) {
      for (const state of EXIT_FROM_STATES[kind]) {
        cleanup();
        renderCard(state);

        expect(renderedExitKinds(), `${state}: ${kind} 가 없다`).toContain(kind);
      }
    }
  });
});

describe('건너뜀 — 종단이라 확인을 묻는다', () => {
  it('첫 누름은 보내지 않고 확인을 띄운다', () => {
    const { sent } = renderCard('WATCHING');
    openSkipWith('예산이 맞지 않는다');

    send();

    expect(screen.getByTestId('action-confirm').textContent).toBe(ACTION_INPUT.SKIP.confirm);
    expect(sent, '확인 전에 보냈다 — 되돌릴 수 없는 전이다').not.toHaveBeenCalled();
  });

  it('두 번째 누름이 사유를 적힌 그대로 싣는다', () => {
    const { sent } = renderCard('WATCHING');
    const reason = '예산이 맞지 않는다';
    openSkipWith(reason);

    send();
    send();

    expect(sent).toHaveBeenCalledTimes(1);
    expect(sent).toHaveBeenCalledWith(CASE_ID, { kind: 'SKIP', value: reason } satisfies ActionPayload);
  });

  /** 확인한 뒤 글자를 고치면 **운영자가 본 문장과 보내는 값이 달라진다**. */
  it('값을 고치면 확인이 되돌아간다', () => {
    const { sent } = renderCard('WATCHING');
    openSkipWith('처음 사유');
    send();
    expect(screen.getByTestId('action-confirm')).toBeDefined();

    fireEvent.change(screen.getByLabelText(ACTION_INPUT.SKIP.label!), { target: { value: '고친 사유' } });

    expect(screen.queryByTestId('action-confirm')).toBeNull();
    send();
    expect(sent, '고친 뒤 첫 누름이 보냈다').not.toHaveBeenCalled();
  });
});

describe('건너뜀 사유 — 보내기 전에 거른다', () => {
  it('빈 사유와 공백뿐인 사유는 보내지 않는다', () => {
    for (const reason of ['', '   ']) {
      cleanup();
      const { sent } = renderCard('WATCHING');
      openSkipWith(reason);

      send();

      expect(screen.getByTestId('action-missing')).toBeDefined();
      expect(sent, `${JSON.stringify(reason)}: 빈 사유를 보냈다`).not.toHaveBeenCalled();
    }
  });

  /**
   * 경계 셋과 **대리쌍** 하나. 상한의 수를 손으로 적지 않고 표에서 가져온다 — 계약과의 등식은
   * 게이트가 들고, 여기서 재는 것은 「그 수를 경계로 실제로 거르는가」다.
   *
   * 대리쌍을 넣는 이유: 세는 단위가 UTF-16 코드 단위라는 것이 계약 쪽과 맞춘 전제다. 한 글자가
   * 두 칸을 쓰는 입력에서 그 전제가 깨지면 화면과 서버의 상한이 어긋난다.
   */
  it('상한 −1·상한은 보내고 상한 +1 은 보내지 않는다', () => {
    const limit = ACTION_INPUT.SKIP.maxChars!;
    const pair = String.fromCodePoint(0x1d400);

    expect(pair.length, '대리쌍 글자가 두 칸이 아니다 — 이 대조의 전제가 깨졌다').toBe(2);

    const atLimit = pair.repeat(Math.floor(limit / 2)) + 'x'.repeat(limit % 2);
    expect(atLimit.length, '경계 문자열의 길이가 상한이 아니다').toBe(limit);

    for (const [reason, accepted] of [
      [atLimit.slice(0, -1), true],
      [atLimit, true],
      [`${atLimit}x`, false],
    ] as const) {
      cleanup();
      const { sent } = renderCard('WATCHING');
      openSkipWith(reason);

      send();
      if (accepted) {
        send();
      }

      expect(sent.mock.calls.length > 0, `길이 ${reason.length}: 기대와 다르다`).toBe(accepted);
      expect(screen.queryByTestId('action-too-long') !== null, `길이 ${reason.length}: 안내가 기대와 다르다`).toBe(
        !accepted,
      );
    }
  });

  /** 세어 보이는 것이 거르기의 **근거**다 — 자르지 않으므로 운영자가 길이를 알아야 한다. */
  it('글자 수를 상한과 함께 보인다', () => {
    renderCard('WATCHING');
    openSkipWith('네 글자');

    expect(screen.getByTestId('action-length').textContent).toBe(`4 / ${ACTION_INPUT.SKIP.maxChars}`);
  });
});

describe('나중에 — 리마인드 시각을 받는다', () => {
  it('시각 없이 보내지 않고, 적으면 그 값을 싣는다', () => {
    const { sent } = renderCard('WATCHING');
    fireEvent.click(screen.getByTestId(`exit-DEFER-${CASE_ID}`));

    send();
    expect(sent, '시각 없이 보냈다').not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(ACTION_INPUT.DEFER.label!), {
      target: { value: '2099-01-02T09:30' },
    });
    send();

    expect(sent).toHaveBeenCalledWith(CASE_ID, {
      kind: 'DEFER',
      value: '2099-01-02T09:30',
    } satisfies ActionPayload);
  });

  /** 되돌릴 수 있는 전이라 **확인을 묻지 않는다** — 실행 취소가 그 자리를 든다. */
  it('확인 단계를 두지 않는다', () => {
    renderCard('WATCHING');
    fireEvent.click(screen.getByTestId(`exit-DEFER-${CASE_ID}`));
    fireEvent.change(screen.getByLabelText(ACTION_INPUT.DEFER.label!), {
      target: { value: '2099-01-02T09:30' },
    });

    send();

    expect(screen.queryByTestId('action-confirm')).toBeNull();
  });
});

/**
 * 폼을 **닫지 않고 다른 내려놓기를 열 때**(cr HIGH). 내려놓기 버튼 줄은 폼이 열려 있어도 계속
 * 그려지므로 이 길은 평범한 조작이다. 폼의 정체가 행동에 묶이지 않으면 앞 입력과 확인 상태가
 * 그대로 넘어가고, 그러면 **빈 값 가드가 걸리지 않은 채** 되돌릴 수 없는 전이가 나간다.
 */
describe('폼을 닫지 않고 다른 내려놓기를 열면 앞 상태가 남지 않는다', () => {
  it('나중에 → 건너뜀: 앞의 시각 입력이 사유 칸에 남지 않는다', () => {
    const { sent } = renderCard('WATCHING');
    fireEvent.click(screen.getByTestId(`exit-DEFER-${CASE_ID}`));
    fireEvent.change(screen.getByLabelText(ACTION_INPUT.DEFER.label!), {
      target: { value: '2099-02-03T10:30' },
    });

    fireEvent.click(screen.getByTestId(`exit-SKIP-${CASE_ID}`));

    expect((screen.getByLabelText(ACTION_INPUT.SKIP.label!) as HTMLTextAreaElement).value).toBe('');
    expect(screen.getByTestId('action-length').textContent).toBe(`0 / ${ACTION_INPUT.SKIP.maxChars}`);
    // 빈 값 가드가 **걸려야** 한다 — 앞 값이 남으면 걸리지 않고 그대로 나간다.
    send();
    expect(screen.getByTestId('action-missing')).toBeDefined();
    expect(sent, '앞 폼의 값이 건너뜀 사유로 나갔다').not.toHaveBeenCalled();
  });

  it('건너뜀 → 나중에: 앞의 확인 상태가 남지 않는다', () => {
    const { sent } = renderCard('WATCHING');
    fireEvent.click(screen.getByTestId(`exit-SKIP-${CASE_ID}`));
    fireEvent.change(screen.getByLabelText(ACTION_INPUT.SKIP.label!), { target: { value: '예산이 맞지 않는다' } });
    send();
    expect(screen.getByTestId('action-confirm')).toBeDefined();

    fireEvent.click(screen.getByTestId(`exit-DEFER-${CASE_ID}`));

    // 빈 확인 문장과 「확인」 글자를 단 위험 어조 버튼이라는 자기모순 화면을 만들지 않는다.
    expect(screen.queryByTestId('action-confirm')).toBeNull();
    expect(screen.getByTestId('action-send').textContent).toBe('보내기');
    expect((screen.getByLabelText(ACTION_INPUT.DEFER.label!) as HTMLInputElement).value).toBe('');
    send();
    expect(sent, '앞 폼의 사유가 리마인드 시각으로 나갔다').not.toHaveBeenCalled();
  });
});
