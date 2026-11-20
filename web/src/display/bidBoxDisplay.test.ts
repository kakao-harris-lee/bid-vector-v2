import { describe, expect, it } from 'vitest';

import { APPROVED_OPERATIONS } from '../api/approvedOperations';
import {
  BID_CASE_STATE_DISPLAY,
  NEXT_CHECKPOINT_DISPLAY,
  NOTICE_ROW_ABSENT_TEXT,
  type BidCaseState,
} from './caseDisplay';
import {
  ACTION_INPUT,
  ACTION_OPERATION,
  ALL_SELECTION,
  BID_BOX_ACTION,
  BID_BOX_SELECTIONS,
  BID_BOX_TABS,
  EXIT_FROM_STATES,
  STATE_TAB,
  bidBoxActionOf,
  blankToServer,
  checkpointRowsOf,
  exitsOf,
  isBidBoxSelection,
  openCaseCountOf,
  selectionShows,
  TERMINAL_TAB,
  stateMeaningsOf,
  tabOf,
  type BidBoxActionKind,
  type BidBoxTabId,
} from './bidBoxDisplay';

/** 계약 enum 전수 — 이 집합이 표의 키 집합과 같은지가 아래 등식들의 기준이다. */
const CONTRACT_STATES = Object.keys(BID_CASE_STATE_DISPLAY) as readonly BidCaseState[];

/** 다음 확인 시점 어휘 전수 — 레일의 사건 낱말이 이 밖으로 나가지 않는 것이 게이트 술어다. */
const CONTRACT_CHECKPOINT_KINDS = Object.keys(
  NEXT_CHECKPOINT_DISPLAY,
) as readonly (keyof typeof NEXT_CHECKPOINT_DISPLAY)[];

describe('D 내 입찰함의 상태 → 탭 표', () => {
  it('와이어프레임의 상태 탭 일곱이고 id 가 중복되지 않는다', () => {
    const ids = BID_BOX_TABS.map((one) => one.id);

    expect(ids).toStrictEqual([
      'watching',
      'price-received',
      'bid-planned',
      'recorded',
      'awaiting-opening',
      'under-review',
      'closed',
    ]);
    expect(new Set(ids).size).toBe(ids.length);
  });

  /**
   * **탭은 상태의 묶음이고 추론이 아니다**(D-7E-4). 상태 값은 API 가 주고, 그 값을 어느 탭에
   * 넣는지만 이 표가 정한다. 계약에 상태가 늘면 `satisfies` 가 컴파일에서 멈추고, 이 등식은
   * 그 전제(키 집합 == 계약 전수)가 실제로 성립하는지를 잰다.
   */
  it('표의 키 집합이 계약 상태 전수와 같다', () => {
    expect(Object.keys(STATE_TAB).sort()).toStrictEqual([...CONTRACT_STATES].sort());
  });

  it('모든 상태가 상태 탭 가운데 하나로 간다 — 표 밖의 탭 0', () => {
    const tabIds = new Set<string>(BID_BOX_TABS.map((one) => one.id));

    for (const state of CONTRACT_STATES) {
      expect(tabIds, `${state} 가 표에 없는 탭으로 간다`).toContain(STATE_TAB[state]);
    }
  });

  it('상태 탭 전부 적어도 한 상태를 받는다 — 빈 탭을 만들지 않는다', () => {
    const used = new Set<BidBoxTabId>(CONTRACT_STATES.map((state) => STATE_TAB[state]));

    expect([...used].sort()).toStrictEqual(BID_BOX_TABS.map((one) => one.id).sort());
  });

  /**
   * **M14/14D 가 D-7E2-8 의 한 자리를 뒤집었다**(팀장 판정 2026-11-20 · 표시 계약 변경).
   * 앞 판은 「투찰 예정」을 「투찰가 받음」에 접었다 — 「둘 다 제출 전이고 투찰가를 쥔 단계」라는
   * 문면 판정이었다. 와이어프레임이 둘을 가르고, 2026-11-19 운영자 지시가 그 그림을 정보 구조의
   * 정본으로 세웠다. 운영자가 할 일도 다르다(투찰가를 **받은** 건 ↔ 낼 값을 **정한** 건).
   *
   * 「마감 지남(미확인)」을 「종료」에 두는 쪽은 그대로다 — 소급 기록 간선이 남아 있다는 사실은
   * 카드의 `bad` 어조 배지가 나른다.
   */
  it('투찰 예정이 자기 탭을 가진다 — D-7E2-8 의 접음을 뒤집었다', () => {
    expect(STATE_TAB.BID_PLANNED).toBe('bid-planned');
    expect(STATE_TAB.PRICE_RECEIVED).toBe('price-received');
    expect(STATE_TAB.DEADLINE_PASSED_UNRECORDED).toBe('closed');
  });

  it('조회 자리에 폴백이 없다 — 표의 값을 그대로 낸다', () => {
    for (const state of CONTRACT_STATES) {
      expect(tabOf(state)).toBe(STATE_TAB[state]);
    }
  });
});

/* ── 「전체」는 상태 탭이 아니라 다른 축이다(M14/14D) ───────────────────────────
   어느 상태도 「전체」로 가지 않는다. 그래서 상태 → 탭 표에 넣지 않고 **고르기의 축**으로
   세운다 — 표의 `satisfies` 전수성이 그대로 선다. */

describe('고르기의 축 — 「전체」와 상태 탭', () => {
  it('고르기는 「전체」 하나 + 상태 탭 전부이고 차례가 「전체」부터다', () => {
    expect(BID_BOX_SELECTIONS.map((one) => one.id)).toStrictEqual([
      ALL_SELECTION.id,
      ...BID_BOX_TABS.map((one) => one.id),
    ]);
  });

  it('「전체」는 상태 → 탭 표의 값이 아니다 — 상태가 그리로 가지 않는다', () => {
    for (const state of CONTRACT_STATES) {
      expect(STATE_TAB[state], `${state} 가 「전체」로 간다`).not.toBe(ALL_SELECTION.id);
    }
  });

  it('상태 탭은 자기 묶음만 보이고 「전체」는 전부 보인다', () => {
    expect(CONTRACT_STATES.length, '상태 전수가 비면 아래 루프가 공허해진다').toBeGreaterThan(0);

    for (const state of CONTRACT_STATES) {
      expect(selectionShows(ALL_SELECTION.id, state), `${state} 가 「전체」에서 빠진다`).toBe(true);
      expect(selectionShows(tabOf(state), state), `${state} 가 자기 탭에서 빠진다`).toBe(true);

      const other = BID_BOX_TABS.map((one) => one.id).filter((id) => id !== tabOf(state));
      for (const id of other) {
        expect(selectionShows(id, state), `${state} 가 남의 탭 ${id} 에 샌다`).toBe(false);
      }
    }
  });

  /**
   * **「전체」는 종단까지 센다**(팀장 판정 2026-11-20). 와이어프레임은 사이드바 배지(9)와
   * 「전체」(9)를 같게 두고 「종료 12」를 따로 세우지만, 라벨이 「전체」인데 종료를 빼면
   * 거짓말이다. 배지는 **지금 들여다볼 건**(비종단)을 세고 이 축은 **전부**를 센다 — 두 수가
   * 다르다는 사실을 레일 「상태 뜻」이 한 줄로 말한다.
   */
  it('「전체」의 수가 사이드바 배지의 축(비종단)보다 크다', () => {
    const fixture: readonly BidCaseState[] = ['WATCHING', 'BID_PLANNED', 'WON', 'LOST', 'SKIPPED'];

    const all = fixture.filter((state) => selectionShows(ALL_SELECTION.id, state));
    // **배지가 세는 그 함수를 그대로 부른다**(cr M-2). 앞 판은 여기서 `tabOf(state) !== 'closed'`
    // 를 **다시 유도**했고, 그래서 셸이 셈을 바꿔도 레일의 문장이 조용히 거짓이 될 수 있었다.
    const badge = openCaseCountOf(fixture.map((state) => ({ state })));

    expect(all.length).toBe(fixture.length);
    expect(badge, '종단이 빠지지 않으면 이 대조가 공허하다').toBeLessThan(all.length);
  });

  it('배지 셈이 종단 탭의 상태를 전부 뺀다 — 표에서 유도한다', () => {
    const terminal = CONTRACT_STATES.filter((state) => tabOf(state) === TERMINAL_TAB);
    const open = CONTRACT_STATES.filter((state) => tabOf(state) !== TERMINAL_TAB);

    expect(terminal.length, '종단 상태가 0 이면 이 단언이 공허하다').toBeGreaterThan(0);
    expect(openCaseCountOf(terminal.map((state) => ({ state })))).toBe(0);
    expect(openCaseCountOf(CONTRACT_STATES.map((state) => ({ state })))).toBe(open.length);
  });

  /** 꼴이 계약과 다르면 **아무 말도 하지 않는다** — 배지 하나가 셸 전체를 무너뜨리지 않게. */
  it('배열이 아닌 답에는 0 을 낸다', () => {
    expect(openCaseCountOf(undefined)).toBe(0);
  });

  it('바깥에서 온 문자열은 「전체」와 상태 탭만 받는다', () => {
    for (const one of BID_BOX_SELECTIONS) {
      expect(isBidBoxSelection(one.id), one.id).toBe(true);
    }
    expect(isBidBoxSelection('WATCHING'), '상태 이름은 고르기가 아니다').toBe(false);
    expect(isBidBoxSelection(null)).toBe(false);
    expect(isBidBoxSelection('__proto__'), '프로토타입 키가 고르기로 읽힌다').toBe(false);
  });
});

/* ── 레일 「다음 확인 시점」(M14/14D) ─────────────────────────────────────────── */

describe('레일의 일정 줄 — 사건 낱말은 열거에서만', () => {
  const source = (
    caseId: number,
    kind: (typeof CONTRACT_CHECKPOINT_KINDS)[number],
    at: string | null,
  ) => ({ caseId, nextCheckpointKind: kind, nextCheckpointAt: at });

  it('낱말이 전부 `nextCheckpointKind` 표의 라벨이다 — 지어낸 사건 0', () => {
    const rows = checkpointRowsOf(
      CONTRACT_CHECKPOINT_KINDS.filter((kind) => kind !== 'NONE').map((kind, at) =>
        source(at, kind, `2099-01-0${at + 1}T00:00:00Z`),
      ),
    );
    const allowed = new Set(Object.values(NEXT_CHECKPOINT_DISPLAY).map((one) => one.label));

    expect(rows.length, '줄이 하나도 없으면 아래 단언이 공허하다').toBeGreaterThan(0);
    for (const row of rows) {
      expect(allowed, `${row.label} 은 열거 밖의 낱말이다`).toContain(row.label);
    }
  });

  it('「없음」과 시각 없는 줄은 일정이 아니다', () => {
    const rows = checkpointRowsOf([
      source(1, 'NONE', null),
      source(2, 'NONE', '2099-01-01T00:00:00Z'),
      source(3, 'DEADLINE', null),
      source(4, 'DEADLINE', '2099-01-01T00:00:00Z'),
    ]);

    expect(rows.map((one) => one.caseId)).toStrictEqual([4]);
  });

  it('이른 시각이 먼저다 — 읽히지 않는 시각은 뒤로', () => {
    const rows = checkpointRowsOf([
      source(1, 'OPENING', '2099-03-01T00:00:00Z'),
      source(2, 'DEADLINE', '2099-01-01T00:00:00Z'),
      source(3, 'DOCUMENT_DUE', '읽을 수 없는 값'),
      source(4, 'NOTICE_EXPECTED', '2099-02-01T00:00:00Z'),
    ]);

    expect(rows.map((one) => one.caseId)).toStrictEqual([2, 4, 1, 3]);
  });

  it('빈 목록은 빈 일정이다 — 줄을 지어내지 않는다', () => {
    expect(checkpointRowsOf([])).toStrictEqual([]);
  });

  /**
   * **줄이 어느 건인지 말한다**(M14/14F · v M-1). 앞 판의 줄은 `caseId · kind · label · at` 뿐이라
   * 건이 둘 이상이면 「투찰 마감 · 시각」이 여러 개 나란히 서서 **서로 구별되지 않았다**. 더
   * 나쁜 것은 보는 탭과 무관하다는 점이다 — 레일은 입찰함 전체를 보므로 「종료」 탭을 보는 중에
   * **관심 탭 건의** 마감 시각이 선다. 와이어프레임의 줄은 건을 부르고, 계약 「데이터」 절도 그
   * 줄이 공고명을 든다고 적었다(14E 투영이 이미 그 값을 싣는다).
   */
  it('줄이 공고명을 든다 — 건이 둘 이상이면 서로 구별된다', () => {
    const rows = checkpointRowsOf([
      { ...source(1, 'DEADLINE', '2099-01-01T00:00:00Z'), noticeKnown: true, noticeTitle: '합성 공고 가' },
      { ...source(2, 'DEADLINE', '2099-01-02T00:00:00Z'), noticeKnown: true, noticeTitle: '합성 공고 나' },
    ]);

    expect(rows.map((one) => one.title)).toStrictEqual(['합성 공고 가', '합성 공고 나']);
  });

  /**
   * **세 사실을 가른다**: 공고 행이 없다 ↔ 행은 있고 제목이 비었다 ↔ 제목이 공백뿐이다. 글자
   * 기준으로 보지 않으면 마지막이 **조용한 빈칸**으로 선다(이 마일스톤이 같은 꼴을 세 번 고쳤다).
   */
  it('제목이 없는 세 꼴이 각자 사유를 든다', () => {
    const rows = checkpointRowsOf([
      { ...source(1, 'DEADLINE', '2099-01-01T00:00:00Z'), noticeKnown: false, noticeTitle: null },
      { ...source(2, 'DEADLINE', '2099-01-02T00:00:00Z'), noticeKnown: true, noticeTitle: null },
      { ...source(3, 'DEADLINE', '2099-01-03T00:00:00Z'), noticeKnown: true, noticeTitle: '   ' },
    ]);

    const titles = rows.map((one) => one.title);
    expect(titles[0], '공고 행이 없는 사실을 말하지 않는다').toBe(NOTICE_ROW_ABSENT_TEXT);
    expect(titles[1], '행은 있고 제목이 빈 자리').toBeNull();
    expect(titles[2], '공백뿐인 제목이 조용한 빈칸으로 섰다').toBeNull();
    // 앞의 둘은 **다른 사실**이다 — 한 얼굴로 접으면 운영자가 가를 수 없다.
    expect(titles[0]).not.toBe(titles[1]);
  });
});

/* ── 레일 「상태 뜻」(M14/14D) ─────────────────────────────────────────────────── */

describe('레일의 「상태 뜻」 — 보이는 상태의 뜻만, 표에서', () => {
  it('문면이 전부 상태 표의 것이다 — 지어낸 설명 0', () => {
    const meanings = stateMeaningsOf(CONTRACT_STATES);

    expect(meanings.length, '뜻이 하나도 없으면 아래 단언이 공허하다').toBeGreaterThan(0);
    for (const one of meanings) {
      const display = BID_CASE_STATE_DISPLAY[one.state];
      expect(one.label).toBe(display.label);
      expect(one.why, `${one.state} 의 뜻이 표의 것이 아니다`).toBe(
        (display as { readonly why?: string }).why,
      );
    }
  });

  it('뜻이 없는 상태는 줄을 만들지 않는다 — 빈 설명을 지어내지 않는다', () => {
    const withoutWhy = CONTRACT_STATES.filter(
      (state) => (BID_CASE_STATE_DISPLAY[state] as { readonly why?: string }).why === undefined,
    );

    expect(withoutWhy.length, '뜻 없는 상태가 0 이면 이 대조가 공허하다').toBeGreaterThan(0);
    expect(stateMeaningsOf(withoutWhy)).toStrictEqual([]);
  });

  it('보이는 상태의 것만 든다 — 목록에 없는 상태의 뜻은 서지 않는다', () => {
    const meanings = stateMeaningsOf(['DEADLINE_PASSED_UNRECORDED']);

    expect(meanings.map((one) => one.state)).toStrictEqual(['DEADLINE_PASSED_UNRECORDED']);
  });

  it('같은 상태가 여럿이어도 뜻은 한 줄이다', () => {
    const meanings = stateMeaningsOf(['WATCHING', 'WATCHING', 'SKIPPED']);

    expect(meanings.map((one) => one.state)).toStrictEqual(['WATCHING', 'SKIPPED']);
  });

  /**
   * 와이어프레임의 「상태 뜻」이 말하는 것 가운데 **잠정 1위 ≠ 낙찰**이 가장 비싼 오해다.
   * 그 문장은 상태 표의 `why` 로 선다 — 레일이 자기 글을 따로 들지 않는다.
   */
  it('잠정 1위가 자기 뜻을 가진다 — 낙찰과 다르다는 말이 표에 있다', () => {
    expect(stateMeaningsOf(['OPENED_PROVISIONAL_FIRST']).length).toBe(1);
  });
});

describe('D 내 입찰함의 다음 행동 표', () => {
  it('표의 키 집합이 계약 상태 전수와 같다', () => {
    expect(Object.keys(BID_BOX_ACTION).sort()).toStrictEqual([...CONTRACT_STATES].sort());
  });

  /**
   * **전이 행동은 승인 목록에서 파생한다**(목록을 복제하지 않는다). 승인되지 않은 operation 을
   * 부르는 행동을 표에 두면 이 등식이 깨진다 — 「화면의 능력 == 승인 목록」을 표 쪽에서 잠근다.
   */
  it('표의 전이 행동이 전부 승인 목록 안이다', () => {
    const approved = new Set(APPROVED_OPERATIONS.map((one) => `${one.method} ${one.path}`));
    const operations = Object.values(ACTION_OPERATION).filter((one) => one !== null);

    expect(operations.length, '전이 행동이 하나도 없으면 이 단언이 공허해진다').toBeGreaterThan(0);
    expect(operations.filter((one) => !approved.has(one))).toStrictEqual([]);
  });

  it('행동 종류 전수가 operation 표에 있다 — 모르는 종류 0', () => {
    const kinds = new Set<BidBoxActionKind>();
    for (const state of CONTRACT_STATES) {
      const row = BID_BOX_ACTION[state];
      // 내려놓기도 **이 모집단에 든다**(M13/13D) — 빼면 새 종류 둘이 operation 표에 없어도
      // 이 단언이 조용하다.
      for (const action of [row.primary, row.secondary, ...exitsOf(state)]) {
        if (action !== null) {
          kinds.add(action.kind);
        }
      }
    }

    for (const kind of kinds) {
      expect(Object.keys(ACTION_OPERATION), `${kind} 가 operation 표에 없다`).toContain(kind);
    }
  });

  /**
   * 계약 전이표(`BID_TRANSITIONS`)가 허락하는 쌍과 같은 모양인지 — 「제출함」 뒤 넷은 D 가
   * 가지고, 담기 뒤 넷은 C 가 가진다(전이가 아니라 화면 이동이다).
   */
  it('「제출함」 뒤 생애주기 넷이 그 상태의 primary 다', () => {
    expect(bidBoxActionOf('BID_RECORDED').primary?.kind).toBe('AWAIT_OPENING');
    expect(bidBoxActionOf('AWAITING_OPENING').primary?.kind).toBe('RECORD_OPENING');
    expect(bidBoxActionOf('OPENED_PROVISIONAL_FIRST').primary?.kind).toBe('ENTER_QUALIFICATION_REVIEW');
    expect(bidBoxActionOf('OPENED_OUT_OF_RANK').primary?.kind).toBe('RECORD_OUTCOME');
    expect(bidBoxActionOf('UNDER_QUALIFICATION_REVIEW').primary?.kind).toBe('RECORD_OUTCOME');
  });

  it('잠정 1위에서만 둘째 행동이 있다 — 적격심사를 거치지 않는 결과 기록', () => {
    const withSecondary = CONTRACT_STATES.filter((state) => BID_BOX_ACTION[state].secondary !== null);

    expect(withSecondary).toStrictEqual(['OPENED_PROVISIONAL_FIRST']);
    expect(bidBoxActionOf('OPENED_PROVISIONAL_FIRST').secondary?.kind).toBe('RECORD_OUTCOME');
  });

  it('담기 뒤 네 상태는 C 로 보낸다 — D 가 그 전이를 부르지 않는다', () => {
    for (const state of ['WATCHING', 'PRICE_RECEIVED', 'BID_PLANNED', 'DEADLINE_PASSED_UNRECORDED'] as const) {
      expect(bidBoxActionOf(state).primary?.kind, state).toBe('OPEN_WORKSHEET');
    }
    expect(ACTION_OPERATION.OPEN_WORKSHEET, '화면 이동은 operation 을 부르지 않는다').toBeNull();
  });

  it('나중에는 되돌림이 primary 이고 워크시트가 둘째가 아니다 — 다음 행동은 하나다', () => {
    expect(bidBoxActionOf('DEFERRED').primary?.kind).toBe('RESUME');
    expect(bidBoxActionOf('DEFERRED').secondary).toBeNull();
  });

  /**
   * 결과 셋과 건너뜀에 버튼을 **두지 않는다** — 정정(`/outcome-correction`)은 목록 밖이고
   * (D-7E2-8) 「준비 중」 버튼을 지어내지 않는다(D-7E-5).
   */
  it('종단·결과 상태에는 행동이 없다', () => {
    for (const state of ['WON', 'LOST', 'FAILED_OR_REBID', 'SKIPPED'] as const) {
      expect(bidBoxActionOf(state).primary, state).toBeNull();
      expect(bidBoxActionOf(state).secondary, state).toBeNull();
    }
  });
});

/* ── 내려놓기 둘(M13/13D) ─────────────────────────────────────────────────────
   출발 집합이 계약 전이표와 같은지는 `scripts/bid-exit-transitions.test.mjs` 가 정본을 읽어
   잰다. 여기서 재는 것은 **그 표를 화면이 어떻게 쓰는가**다. */

describe('내려놓기의 출발 집합과 유도', () => {
  it('상태 전수에서 내려놓기가 표의 멤버십과 정확히 같다', () => {
    expect(CONTRACT_STATES.length, '상태 전수가 비면 아래 루프가 공허해진다').toBeGreaterThan(0);

    for (const state of CONTRACT_STATES) {
      const kinds = exitsOf(state).map((one) => one.kind);
      const expected = (['DEFER', 'SKIP'] as const).filter((kind) =>
        (EXIT_FROM_STATES[kind] as readonly string[]).includes(state),
      );

      expect(kinds, `${state}: 유도가 표와 다르다`).toStrictEqual(expected);
    }
  });

  /** §6.5 — 내려놓기는 **다음 행동 하나를 차지하지 않는다**. 그래서 축이 따로 선다. */
  it('내려놓기가 primary·secondary 에 끼지 않는다', () => {
    for (const state of CONTRACT_STATES) {
      const row = bidBoxActionOf(state);
      const next = [row.primary, row.secondary].filter((one) => one !== null).map((one) => one.kind);

      expect(next, `${state}: 다음 행동 자리에 내려놓기가 있다`).not.toContain('DEFER');
      expect(next, `${state}: 다음 행동 자리에 내려놓기가 있다`).not.toContain('SKIP');
    }
  });

  it('종단과 결과 단계에서는 내려놓기가 열리지 않는다', () => {
    for (const state of ['SKIPPED', 'WON', 'LOST', 'FAILED_OR_REBID', 'BID_RECORDED'] as const) {
      expect(exitsOf(state), `${state}: 서버가 받지 않는 전이를 버튼으로 낸다`).toStrictEqual([]);
    }
  });

  it('「나중에」는 되돌릴 수 있고 「건너뜀」은 확인을 묻는다', () => {
    // 되돌림 전이가 있는 쪽은 확인을 두지 않는다 — 실행 취소가 그 자리를 든다(D-7E-5).
    expect(ACTION_INPUT.DEFER.confirm).toBeUndefined();
    expect(ACTION_INPUT.SKIP.confirm).toBeDefined();
    // 「다시 보기」가 그 역전이다 — 그것이 있어 실행 취소가 거짓말이 되지 않는다.
    expect(ACTION_OPERATION.RESUME).toContain('/resume');
  });

  it('건너뜀만 자유 텍스트이고 상한을 든다', () => {
    const shapes = Object.entries(ACTION_INPUT).filter(([, input]) => input.shape === 'TEXT');

    expect(shapes.map(([kind]) => kind)).toStrictEqual(['SKIP']);
    expect(ACTION_INPUT.SKIP.maxChars, '상한이 없으면 보내기 전 거름이 공허해진다').toBeGreaterThan(0);
  });
});

describe('서버 기준 「공백뿐」 판정 — 화면이 서버보다 많이 거부하지 않는다', () => {
  /** 바이트 순서 표시. 축어로 적지 않고 코드포인트로 짓는다. */
  const bom = String.fromCodePoint(0xfeff);
  /** 서버만 공백으로 보는 제어문자 하나(실측 넷 가운데 하나). */
  const unitSeparator = String.fromCodePoint(0x1f);

  it('빈 값과 공백뿐인 값은 보내지 않는다', () => {
    expect(blankToServer('')).toBe(true);
    expect(blankToServer(' \t\n')).toBe(true);
    expect(blankToServer(String.fromCodePoint(0x3000))).toBe(true);
  });

  it('서버가 글자로 보는 값은 보낸다 — JS `trim` 만 쓰면 여기서 더 거부한다', () => {
    // 이 글자 하나가 두 집합의 유일한 차이다(실측). `trim` 술어는 이 둘을 공백으로 보고
    // 서버는 보지 않으므로, 화면이 거부하면 **서버보다 많이** 거부하는 것이 된다.
    expect(blankToServer(bom)).toBe(false);
    expect(blankToServer(`${bom} \t`)).toBe(false);
  });

  it('서버만 공백으로 보는 제어문자는 화면이 보낸다 — 화면이 더 좁은 쪽이다', () => {
    // 그 요청은 서버가 400 으로 거부한다. 화면이 **덜** 거부하는 방향이라 그대로 둔다.
    expect(blankToServer(unitSeparator)).toBe(false);
  });

  it('평범한 사유는 보낸다', () => {
    expect(blankToServer('예산이 맞지 않는다')).toBe(false);
  });
});
