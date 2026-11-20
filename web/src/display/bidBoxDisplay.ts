import type { components } from '../api/generated/operator-api';

import {
  BID_CASE_STATE_DISPLAY,
  NEXT_CHECKPOINT_DISPLAY,
  NOTICE_ROW_ABSENT_TEXT,
  shownText,
  type BidCaseState,
} from './caseDisplay';
import { instantDisplayOf } from './timeDisplay';

type Outcome = components['schemas']['RecordOutcomeRequest']['outcome'];

/**
 * 화면 D 내 입찰함의 두 표(M7/7E-2, D-7E2-8).
 *
 * **상태는 API 가 주고 이 파일은 그것을 어디에 놓을지만 정한다.** 탭도 다음 행동도 상태
 * 하나에서 **표 조회**로 나온다 — 문자열 접두를 보거나(`state.startsWith('OPENED')`) 시각을
 * 비교하는 추론이 없다(D-7E-4). 계약에 상태가 늘면 두 표의 `satisfies` 가 컴파일에서 멈춘다.
 */

/** 와이어프레임 D 의 **상태 탭 일곱** — 탭의 **정본 순서**가 이 배열이다. */
export const BID_BOX_TABS = [
  { id: 'watching', label: '관심' },
  { id: 'price-received', label: '투찰가 받음' },
  { id: 'bid-planned', label: '투찰 예정' },
  { id: 'recorded', label: '투찰함' },
  { id: 'awaiting-opening', label: '개찰 대기' },
  { id: 'under-review', label: '심사 중' },
  { id: 'closed', label: '종료' },
] as const;

export type BidBoxTabId = (typeof BID_BOX_TABS)[number]['id'];

/**
 * 상태 14 → 상태 탭 7. **정확히 들어맞지 않는 한 자리**를 팀장이 문면으로 정했다 —
 * `DEADLINE_PASSED_UNRECORDED` 는 「종료」에 둔다(D-7E2-8). 소급 기록 간선이 남아 있지만
 * 운영자가 매일 보는 묶음은 아니고, 그 사실은 카드의 `bad` 어조 배지가 나른다.
 *
 * **M14/14D 가 D-7E2-8 의 다른 한 자리를 뒤집었다**(팀장 판정 2026-11-20 · 표시 계약 변경).
 * 앞 판은 `BID_PLANNED`(투찰 예정)를 「투찰가 받음」에 접었다 — 「둘 다 제출 전이고 투찰가를 쥔
 * 단계」라는 문면 판정이었다. 와이어프레임이 둘을 가르고, 2026-11-19 운영자 지시가 그 그림을
 * 정보 구조의 정본으로 세웠다. 운영자가 할 일도 같지 않다 — 투찰가를 **받은** 건과 낼 값을
 * **정한** 건은 다음 걸음이 다르다.
 */
export const STATE_TAB = {
  WATCHING: 'watching',
  DEFERRED: 'watching',
  PRICE_RECEIVED: 'price-received',
  BID_PLANNED: 'bid-planned',
  BID_RECORDED: 'recorded',
  AWAITING_OPENING: 'awaiting-opening',
  OPENED_PROVISIONAL_FIRST: 'under-review',
  OPENED_OUT_OF_RANK: 'under-review',
  UNDER_QUALIFICATION_REVIEW: 'under-review',
  WON: 'closed',
  LOST: 'closed',
  FAILED_OR_REBID: 'closed',
  SKIPPED: 'closed',
  DEADLINE_PASSED_UNRECORDED: 'closed',
} as const satisfies Record<BidCaseState, BidBoxTabId>;

/** 표 조회의 **유일한 자리** — 폴백이 없다(표 밖의 상태에 탭을 지어내지 않는다). */
export function tabOf(state: BidCaseState): BidBoxTabId {
  return STATE_TAB[state];
}

/**
 * 바깥에서 온 문자열이 탭 id 인가(M7/7E-3a r1 M-2) — URL 질의처럼 **우리가 쓰지 않은 값**이
 * 들어오는 자리에서 쓴다. 집합을 손으로 적지 않고 [BID_BOX_TABS] 에서 파생하므로 탭이 늘면
 * 이 가드가 함께 넓어진다.
 */
export function isBidBoxTabId(value: string | null): value is BidBoxTabId {
  return value !== null && BID_BOX_TABS.some((one) => one.id === value);
}

/* ── 「전체」는 상태 탭이 아니라 다른 축이다(M14/14D) ───────────────────────────
   와이어프레임의 탭 줄 맨 앞이 「전체」인데 **어느 상태도 그리로 가지 않는다**. 상태 → 탭 표에
   넣으면 그 표의 `satisfies` 가 재던 것(「모든 상태가 어느 탭에 속하는가」)이 뜻을 잃고,
   「전체」가 다른 탭과 겹치는 묶음이 되어 같은 건이 두 번 세어진다. 그래서 **고르기의 축**으로
   따로 선다 — 고른 것이 「전체」면 거르지 않고, 아니면 표 조회로 거른다. */

/**
 * 「전체」 고르기. **종단까지 센다**(팀장 판정 2026-11-20).
 *
 * 와이어프레임은 사이드바 배지(9)와 「전체」(9)를 같게 두고 「종료 12」를 따로 세운다 — 그림의
 * 「전체」는 종단을 뺀 수다. 그러나 라벨이 「전체」인데 종료를 빼면 거짓말이다. 배지는 **지금
 * 들여다볼 건**을 세고(셸이 종단 탭을 이 표에서 유도해 뺀다) 이 축은 **전부**를 센다. 두 수가
 * 다르다는 사실은 레일 「상태 뜻」이 한 줄로 말한다.
 */
export const ALL_SELECTION = { id: 'all', label: '전체' } as const;

/** 탭 줄에 서는 차례 — 「전체」가 먼저고 그 뒤가 상태 탭의 정본 순서다. */
export const BID_BOX_SELECTIONS = [ALL_SELECTION, ...BID_BOX_TABS] as const;

export type BidBoxSelection = (typeof BID_BOX_SELECTIONS)[number]['id'];

/** 고른 것이 그 상태의 건을 보이는가 — 거르기의 **유일한 자리**다. */
export function selectionShows(selection: BidBoxSelection, state: BidCaseState): boolean {
  return selection === ALL_SELECTION.id || tabOf(state) === selection;
}

/**
 * 바깥에서 온 문자열이 고르기인가 — URL 질의가 그 자리다. 집합을 손으로 적지 않고
 * [BID_BOX_SELECTIONS] 에서 파생하므로 축이 늘면 이 가드가 함께 넓어진다.
 */
export function isBidBoxSelection(value: string | null): value is BidBoxSelection {
  return value !== null && BID_BOX_SELECTIONS.some((one) => one.id === value);
}

/**
 * 종단 탭 — 「다 끝난 건」의 묶음이다. **이 이름이 한 자리**이고 셸도 화면도 여기서 읽는다.
 */
export const TERMINAL_TAB: BidBoxTabId = 'closed';

/**
 * **지금 들여다볼 건의 수** — 사이드바 배지가 세는 축이다(M14/14A 가 세우고 14D 가 여기로
 * 옮겼다).
 *
 * 왜 표시층에 있나(cr M-2): 입찰함 레일의 문장이 「왼쪽 배지는 지금 들여다볼 건만 셉니다」라고
 * **배지에 대해 주장한다**. 앞 판은 셸이 자기 안에서 이 셈을 들었고 레일의 문장과 그것을 잇는
 * 자리가 없었다 — 셸이 셈을 바꾸면 그 문장이 조용히 거짓이 되고 아무것도 붉지 않았다. 이제
 * 셸과 레일과 짝 test 가 **같은 자리**를 읽는다.
 *
 * **꼴이 계약과 다르면 아무 말도 하지 않는다.** 배지는 「모르면 그리지 않는다」로 서 있고,
 * 받은 것이 배열이 아니라는 것도 모르는 것의 한 갈래다 — 그 가드가 없으면 배지 하나가 셸
 * 전체를 무너뜨린다(실측).
 */
export function openCaseCountOf(cases: readonly { readonly state: BidCaseState }[] | undefined): number {
  if (!Array.isArray(cases)) {
    return 0;
  }
  return cases.filter((one) => tabOf(one.state) !== TERMINAL_TAB).length;
}

/* ── 레일 둘(M14/14D · 와이어프레임 D 의 오른쪽) ───────────────────────────────── */

/**
 * 일정 줄이 읽는 칸 — `BidCase` 가 이 모양을 만족한다(그 타입을 여기로 끌어오지 않는다).
 *
 * 시각 칸이 `undefined` 까지 받는 까닭: 계약이 그 칸을 `required` 에 넣지 않아 생성 타입에서만
 * 선택 칸이다(14E 가 등재한 OPEN). 서버는 **언제나** 싣지만, 그 사실에 기대지 않고 둘 다 받아
 * 같은 답(일정이 아니다)을 낸다.
 */
export interface CheckpointSource {
  readonly caseId: number;
  readonly nextCheckpointKind: NextCheckpointKind;
  readonly nextCheckpointAt?: string | null;
  /**
   * 줄의 **신원**을 만드는 두 칸(M14/14F · v M-1). 14E 투영이 싣는 값이고 입찰함·결과 카드가
   * 이미 같은 둘을 읽는다 — 레일만 그것 없이 서 있었다.
   *
   * 선택 칸인 까닭은 이 모듈이 **모양을 받는다**는 규율이다(`BidCase` 타입을 끌어오지 않는다).
   * 주지 않으면 신원이 `null` 이고, 그 답은 「제목 자리가 비었다」와 같다.
   */
  readonly noticeKnown?: boolean;
  readonly noticeTitle?: string | null;
}

type NextCheckpointKind = keyof typeof NEXT_CHECKPOINT_DISPLAY;

/**
 * 일정이 **아닌** 어휘 — 「다음 확인 시점이 없다」는 답이지 사건이 아니다.
 *
 * `satisfies` 로 적는 까닭: 타입 주석(`: NextCheckpointKind`)을 달면 값이 유니온 전체로 넓어져
 * 아래 걸러내기가 **좁히지 못한다**. 어휘에서 이 이름이 사라지면 여기가 컴파일에서 멈춘다.
 */
const NO_CHECKPOINT = 'NONE' as const satisfies NextCheckpointKind;

export interface CheckpointRow {
  readonly caseId: number;
  readonly kind: Exclude<NextCheckpointKind, typeof NO_CHECKPOINT>;
  /** 사건 낱말 — **열거의 표에서만** 온다. 「참가신청」처럼 표에 없는 말을 지어내지 않는다. */
  readonly label: string;
  /** 시각 원문. 서식은 `timeDisplay` 한 자리의 일이고 그리는 것은 화면이다. */
  readonly at: string;
  /**
   * **이 줄이 어느 건인가**(M14/14F · v M-1). 공고명, 또는 공고 행이 없으면 그 사실의 문면.
   * `null` 은 「행은 있고 제목 자리가 비었다」이고 그때 그리는 것은 **부재 어휘**다 — 그 선택은
   * 화면의 일이다(이 층은 글자를 지어내지 않는다).
   */
  readonly title: string | null;
}

/**
 * 레일 「다음 확인 시점」의 줄 — 서버가 준 확인 시점을 이른 것부터 늘어놓는다.
 *
 * **제목이 「이번 주」가 아닌 까닭**(와이어프레임 이탈 · 사유를 남긴다).
 *
 * **시계를 못 읽어서가 아니다**(v M-2 가 앞 판의 이 사유를 바로잡았다). lint 는 벽시계를 막는
 * 것이 아니라 **한 자리로 몰아 준다** — `timeDisplay.readClock()` 으로 읽어 `now` 를 내려 주는
 * 길이 열려 있고, 워크시트와 오늘의 공고가 이미 그 꼴이다.
 *
 * 막는 것은 둘이다. ⓐ 「이번 주」는 **KST 주 경계를 새로 셈하는 일**이고(어느 요일에 시작하는가 ·
 * 자정 경계 · 주를 넘는 마감) 그 셈은 이 slice 의 범위 밖이다. ⓑ 그렇게 그은 주는 서버의
 * `overdue` 판정과 **다른 시점**을 볼 수 있다 — 이 저장소는 브라우저 시계로 「지남」을 말했다가
 * 서버 판정과 어긋난 적이 있고, 그래서 「지남」을 말하는 자리를 서버 값 하나로 좁혔다.
 *
 * 그래서 **거르지 않고 늘어놓는다** — 늘어놓는 데 쓰는 것은 서로의 시각뿐이고 「지금」이 아니다.
 * 「이번 주 일정」은 OPEN 후보로 등재했다.
 *
 * 정렬 키가 `instantDisplayOf(...).text` 인 것도 같은 규율이다: KST 고정폭 글자라 사전식
 * 비교가 곧 시각 순이고, 읽히지 않는 값은 그 글자가 숫자가 아니라 **뒤로 밀린다**. 원문
 * (`date-time`)을 그대로 비교하면 오프셋이 섞일 때 차례가 어긋난다.
 */
export function checkpointRowsOf(cases: readonly CheckpointSource[]): readonly CheckpointRow[] {
  return cases
    .flatMap<CheckpointRow>((one) => {
      const kind = one.nextCheckpointKind;
      const at = one.nextCheckpointAt;
      if (kind === NO_CHECKPOINT || at === null || at === undefined) {
        return [];
      }
      /**
       * 신원은 **세 사실을 가른다**(v M-1): 공고 행이 없다 ↔ 행은 있고 제목이 비었다 ↔ 제목이
       * 공백뿐이다. 마지막을 글자 기준으로 보지 않으면 **조용한 빈칸**이 선다(이 마일스톤이 같은
       * 꼴을 세 번 고쳤다). 「행이 없다」의 문면은 **한 자리**에서 온다 — 알림함·입찰함·결과가
       * 같은 글자를 쓴다.
       */
      const title =
        one.noticeKnown === false ? NOTICE_ROW_ABSENT_TEXT : (shownText(one.noticeTitle) ?? null);
      return [{ caseId: one.caseId, kind, label: NEXT_CHECKPOINT_DISPLAY[kind].label, at, title }];
    })
    .sort((left, right) => {
      const a = instantDisplayOf(left.at).text;
      const b = instantDisplayOf(right.at).text;
      return a < b ? -1 : a > b ? 1 : 0;
    });
}

/** 「상태 뜻」 한 줄 — 상태와 그 표의 문면이다. 레일이 자기 글을 따로 들지 않는다. */
export interface StateMeaning {
  readonly state: BidCaseState;
  readonly label: string;
  readonly why: string;
}

/**
 * 레일 「상태 뜻」 — **보이는 상태의 뜻만**, 그리고 **상태 표의 `why` 에서만** 온다.
 *
 * 뜻이 없는 상태는 줄을 만들지 않는다(빈 설명을 지어내지 않는다). 목록에 없는 상태의 뜻도
 * 서지 않는다 — 운영자가 자기 입찰함에 없는 상태를 읽을 까닭이 없다.
 *
 * 차례는 **상태 표의 선언 순서**다(건이 들어온 차례가 아니다) — 같은 화면이 목록 차례에 따라
 * 다른 순서로 설명을 늘어놓으면 읽는 사람이 그 차례에 뜻이 있다고 읽는다.
 */
export function stateMeaningsOf(states: readonly BidCaseState[]): readonly StateMeaning[] {
  const shown = new Set(states);
  return (Object.keys(BID_CASE_STATE_DISPLAY) as readonly BidCaseState[])
    .filter((state) => shown.has(state))
    .flatMap((state) => {
      const display: { readonly label: string; readonly why?: string } = BID_CASE_STATE_DISPLAY[state];
      return display.why === undefined ? [] : [{ state, label: display.label, why: display.why }];
    });
}

/**
 * D 가 낼 수 있는 행동의 종류. `OPEN_WORKSHEET` 만 **전이가 아니다** — 담기 뒤 네 상태의
 * 다음 전이는 화면 C 가 가지고 있으므로 D 는 그 화면으로 보낸다.
 */
export type BidBoxActionKind =
  | 'OPEN_WORKSHEET'
  | 'DEFER'
  | 'RESUME'
  | 'SKIP'
  | 'AWAIT_OPENING'
  | 'RECORD_OPENING'
  | 'ENTER_QUALIFICATION_REVIEW'
  | 'RECORD_OUTCOME';

/**
 * 행동 → 그것이 부르는 **(메서드, 경로)**. `null` 은 「서버를 부르지 않는다」다.
 *
 * 이 표가 있는 이유는 test 가 **승인 목록에서 파생한 집합**과 등식을 둘 수 있게 하는 것이다 —
 * 승인되지 않은 operation 을 부르는 행동을 표에 두면 그 등식이 깨진다.
 */
export const ACTION_OPERATION = {
  OPEN_WORKSHEET: null,
  DEFER: 'POST /api/bid-cases/{caseId}/defer',
  RESUME: 'POST /api/bid-cases/{caseId}/resume',
  SKIP: 'POST /api/bid-cases/{caseId}/skip',
  AWAIT_OPENING: 'POST /api/bid-cases/{caseId}/await-opening',
  RECORD_OPENING: 'POST /api/bid-cases/{caseId}/opening',
  ENTER_QUALIFICATION_REVIEW: 'POST /api/bid-cases/{caseId}/qualification-review',
  RECORD_OUTCOME: 'POST /api/bid-cases/{caseId}/outcome',
} as const satisfies Record<BidBoxActionKind, string | null>;

export interface BidBoxAction {
  readonly label: string;
  readonly kind: BidBoxActionKind;
}

/** 한 상태의 행동 둘 — `primary` 가 §6.5 의 「다음 행동 하나」이고 `secondary` 는 갈림이 있을 때만. */
export interface BidBoxActionRow {
  readonly primary: BidBoxAction | null;
  readonly secondary: BidBoxAction | null;
}

const OPEN_WORKSHEET: BidBoxAction = { label: '공고 워크시트', kind: 'OPEN_WORKSHEET' };
const RESUME: BidBoxAction = { label: '다시 보기', kind: 'RESUME' };
const AWAIT_OPENING: BidBoxAction = { label: '개찰 대기로', kind: 'AWAIT_OPENING' };
const RECORD_OPENING: BidBoxAction = { label: '개찰 결과 기록', kind: 'RECORD_OPENING' };
const ENTER_REVIEW: BidBoxAction = { label: '적격심사 중으로', kind: 'ENTER_QUALIFICATION_REVIEW' };
const RECORD_OUTCOME: BidBoxAction = { label: '결과 기록', kind: 'RECORD_OUTCOME' };

const NO_ACTION: BidBoxActionRow = { primary: null, secondary: null };

/**
 * 상태 14 → 다음 행동. **계약 전이표(`BID_TRANSITIONS`)와 같은 모양**이다 — 표에 없는 쌍을
 * 버튼으로 내면 그 요청이 409 가 되므로, 어느 상태에서 어느 전이가 열리는지를 분기가 아니라
 * 데이터로 둔다.
 *
 * **담기 뒤 네 상태**(관심·투찰가 받음·투찰 예정·마감 지남)는 C 로 보낸다 — 그 전이(투찰가
 * 요청·초안·제출함)를 가진 화면이 C 다. `null` 은 「이 slice 가 그 전이를 만들지 않는다」이고
 * 그때 버튼을 **두지 않는다**(「준비 중」 버튼을 지어내지 않는다).
 *
 * `OPENED_PROVISIONAL_FIRST` 만 둘째 행동을 갖는다 — 계약 전이표가 그 상태에서 사용자에게
 * 두 간선(적격심사 진입 · 결과 기록)을 열어 두기 때문이다.
 */
export const BID_BOX_ACTION = {
  WATCHING: { primary: OPEN_WORKSHEET, secondary: null },
  DEFERRED: { primary: RESUME, secondary: null },
  PRICE_RECEIVED: { primary: OPEN_WORKSHEET, secondary: null },
  BID_PLANNED: { primary: OPEN_WORKSHEET, secondary: null },
  BID_RECORDED: { primary: AWAIT_OPENING, secondary: null },
  AWAITING_OPENING: { primary: RECORD_OPENING, secondary: null },
  OPENED_PROVISIONAL_FIRST: { primary: ENTER_REVIEW, secondary: RECORD_OUTCOME },
  OPENED_OUT_OF_RANK: { primary: RECORD_OUTCOME, secondary: null },
  UNDER_QUALIFICATION_REVIEW: { primary: RECORD_OUTCOME, secondary: null },
  WON: NO_ACTION,
  LOST: NO_ACTION,
  FAILED_OR_REBID: NO_ACTION,
  SKIPPED: NO_ACTION,
  DEADLINE_PASSED_UNRECORDED: { primary: OPEN_WORKSHEET, secondary: null },
} as const satisfies Record<BidCaseState, BidBoxActionRow>;

export function bidBoxActionOf(state: BidCaseState): BidBoxActionRow {
  return BID_BOX_ACTION[state];
}

/* ── 내려놓기 둘(M13/13D) ─────────────────────────────────────────────────────
   「나중에」와 「건너뜀」은 **다음 행동이 아니다** — 건을 내려놓는 축이다. 그래서 위의
   `primary`/`secondary` 를 차지하지 않고 자기 축으로 선다(§6.5 「다음 행동 하나가 화면을
   지배한다」를 비우지 않는다). 둘을 한 행에 밀어 넣으면 관심 상태에서 행동이 넷이 되고
   그 가운데 무엇이 다음 걸음인지 화면이 말하지 못한다. */

/** 내려놓기의 종류 — 전체 종류에서 **뽑아 쓴다**(목록을 두 번 적지 않는다). */
export type ExitActionKind = Extract<BidBoxActionKind, 'DEFER' | 'SKIP'>;

/**
 * 내려놓기의 **출발 상태 집합** — 계약 전이표(`BID_TRANSITIONS`)의 사본이다.
 *
 * **이 사본이 정본과 갈리면 운영자가 409 를 본다.** 그래서 `scripts/bid-exit-transitions.test.mjs`
 * 가 정본을 읽어 이 표와 **등식**을 걸고, 그 전이를 **사용자가 낼 수 있는지**(주체 축)까지 본다.
 * 계약에 상태가 늘어 이 표가 모르면 그 등식이 붉는다 — 컴파일이 아니라 게이트가 드는 자리다
 * (새 상태에 빈 배열을 조용히 내주는 것이 이 표의 기본값이므로).
 *
 * 「건너뜀」이 「나중에」보다 넓은 것은 정본의 모양이다 — 나중에 둔 건도 건너뛸 수 있고,
 * 투찰 예정까지는 내려놓을 수 있다. 반대로 「나중에」는 투찰 예정에서 열리지 않는다.
 */
export const EXIT_FROM_STATES = {
  DEFER: ['WATCHING', 'PRICE_RECEIVED'],
  SKIP: ['WATCHING', 'DEFERRED', 'PRICE_RECEIVED', 'BID_PLANNED'],
} as const satisfies Record<ExitActionKind, readonly BidCaseState[]>;

/** 카드에 서는 차례 — **되돌릴 수 있는 것이 먼저**다. */
const EXIT_ORDER = ['DEFER', 'SKIP'] as const satisfies readonly ExitActionKind[];

const EXIT_ACTION = {
  DEFER: { label: '나중에', kind: 'DEFER' },
  SKIP: { label: '건너뜀', kind: 'SKIP' },
} as const satisfies Record<ExitActionKind, BidBoxAction>;

/**
 * 그 상태에서 열린 내려놓기 — **표에서 유도한다**. 상태별로 다시 적으면 사본이 둘이 되고
 * 한쪽만 고쳐진다(이 파일의 선택지 문면이 같은 이유로 상태 표에서 온다).
 */
export function exitsOf(state: BidCaseState): readonly BidBoxAction[] {
  return EXIT_ORDER.filter((kind) => EXIT_FROM_STATES[kind].some((one) => one === state)).map(
    (kind) => EXIT_ACTION[kind],
  );
}

/**
 * 「지난 일정」 경고의 문면 — **판정은 서버가 한다**(`BidCase.overdue`). 이 값은 그 boolean 을
 * 문면으로 옮길 뿐이고 시각을 비교하지 않는다.
 */
export const OVERDUE_DISPLAY = {
  label: '지난 일정',
  why: '다음 확인 시점이 지났다고 서버가 판정했다',
  tone: 'bad',
} as const;

/** 행동이 받아야 하는 입력의 모양. `LINK` 만 서버를 부르지 않는다. */
export type ActionInputShape = 'LINK' | 'INSTANT' | 'DATETIME' | 'CHOICE' | 'TEXT';

/**
 * 바이트 순서 표시 — **화면과 서버의 「공백뿐」 판정이 갈리는 유일한 글자**다(실측).
 *
 * 서버는 Kotlin 의 공백 판정을 쓰고 그 집합은 JS `trim` 의 집합과 두 방향으로 다르다:
 * 이 글자를 JS 는 공백으로 보고 **서버는 보지 않으며**, 반대로 제어문자 넷은 서버가 공백으로
 * 보고 JS 는 보지 않는다. 뒤쪽은 화면이 보내고 서버가 거부하는 꼴이라 **화면이 더 좁은** 쪽이고
 * 그대로 둔다. 앞쪽은 화면이 서버보다 **많이 거부하는** 꼴이라 맞춰야 한다.
 */
const BYTE_ORDER_MARK = '﻿';

/** 위 글자를 셈에서 **보이는 글자로** 바꿔 세울 자리표. 무엇이든 공백만 아니면 된다. */
const VISIBLE_STAND_IN = '.';

/**
 * 서버가 **공백뿐**으로 볼 값인가 — 「보내지 않는다」 판정이다. 400 을 쓰기 실패 얼굴로 받는 것은
 * 운영자에게 무엇이 잘못됐는지 알려 주지 않으므로 화면이 먼저 본다.
 *
 * 술어가 `trim` 하나가 아닌 이유는 [BYTE_ORDER_MARK] 의 주석에 있다.
 */
export function blankToServer(value: string): boolean {
  return value.split(BYTE_ORDER_MARK).join(VISIBLE_STAND_IN).trim() === '';
}

export interface ActionChoice {
  /**
   * 선택지의 값. 결과 셋은 **계약 enum** 이므로 그 타입을 그대로 흘린다(CR r1 L-8) — 전송 자리의
   * `as 'WON' | 'LOST' | …` 캐스팅이 사라진다. 개찰 둘은 boolean 을 문자열로 나른다.
   */
  readonly value: Outcome | 'true' | 'false';
  readonly label: string;
}

/**
 * 개찰 결과의 두 선택지 — 계약은 **순위 숫자를 받지 않는다**(`provisionalFirst` boolean 하나).
 * 문면은 그 두 결과 상태의 라벨이다.
 */
const OPENING_CHOICES = [
  { value: 'true', label: BID_CASE_STATE_DISPLAY.OPENED_PROVISIONAL_FIRST.label },
  { value: 'false', label: BID_CASE_STATE_DISPLAY.OPENED_OUT_OF_RANK.label },
] as const;

/** 결과 선택지의 **값 집합** — `isOutcome` 이 이것을 쓴다(손으로 두 번 적지 않는다). */
export const OUTCOME_VALUES: ReadonlySet<string> = new Set<Outcome>(['WON', 'LOST', 'FAILED_OR_REBID']);

/** 결과 셋 — 계약 enum 전수(`RecordOutcomeRequest.outcome`)이고 문면은 상태 표에서 온다. */
const OUTCOME_CHOICES: readonly ActionChoice[] = [
  { value: 'WON', label: BID_CASE_STATE_DISPLAY.WON.label },
  { value: 'LOST', label: BID_CASE_STATE_DISPLAY.LOST.label },
  { value: 'FAILED_OR_REBID', label: BID_CASE_STATE_DISPLAY.FAILED_OR_REBID.label },
];

export interface ActionInput {
  readonly shape: ActionInputShape;
  /** 입력 칸의 라벨 — `LINK`·`INSTANT` 에는 칸이 없다. */
  readonly label?: string;
  readonly choices?: readonly ActionChoice[];
  /**
   * 자유 텍스트의 **글자 수 상한**(`TEXT` 만). 계약이 그 칸에 선 상한이고 **생성 타입에는 값으로
   * 오지 않는다**(설명 주석에만 있다) — 그래서 `scripts/bid-exit-transitions.test.mjs` 가 계약
   * 원문의 그 칸과 **등식**을 건다. 이 파일이 그 수를 적는 **유일한 자리**다.
   *
   * 세는 단위가 **서버와** 같다는 것은 실측으로 갈랐다 — 서버와 화면이 둘 다 UTF-16 코드 단위로
   * 센다(계약의 다른 칸 하나는 서버가 코드포인트로 세는데 이 칸은 아니다).
   *
   * **계약 원문의 단위는 또 다르다**(cr LOW): 그 선언은 JSON Schema 규칙상 **코드포인트** 수다.
   * 그래서 대리쌍이 섞인 사유에서는 화면·서버가 계약보다 **먼저** 거부한다 — 안전한 방향이고,
   * 화면이 계약이 아니라 **서버와** 맞춘 것이 맞는 선택이다. 등식이 재는 것은 그 두 수가 같다는
   * 사실이지 단위가 같다는 사실이 아니다.
   */
  readonly maxChars?: number;
  /**
   * 보내기 전에 한 번 더 묻는 말 — **되돌림 전이가 없는 전이**에만 둔다(D-7E-5 의 짝). 실행
   * 취소를 줄 수 없는 자리에서 운영자를 멈추는 길이 이것뿐이다.
   */
  readonly confirm?: string;
}

/**
 * 행동 → 그것이 운영자에게 묻는 것. **기본값을 두지 않는다**(§6.5 「값이 없으면 빈 자리」):
 * 개찰 결과와 결과 셋은 고르기 전에는 요청을 보내지 않는다 — `false`·`LOST` 를 기본값으로
 * 두면 운영자가 고르지 않은 사실이 기록으로 남는다.
 *
 * 선택지의 문면은 **상태 표에서 가져온다**(`BID_CASE_STATE_DISPLAY`) — 같은 말을 두 곳에
 * 적지 않는다. 계약이 결과 어휘를 늘리면 그 표가 먼저 멈춘다.
 */
export const ACTION_INPUT: Record<BidBoxActionKind, ActionInput> = {
  OPEN_WORKSHEET: { shape: 'LINK' },
  // 「나중에」는 **리마인드 시각이 반드시 있어야 한다**(계약) — 「보류」라는 상태가 없고 그
  // 시각이 다음 확인 시점이 된다. 기본값을 두지 않는 것은 이 표의 규약 그대로다.
  DEFER: { shape: 'DATETIME', label: '리마인드 시각' },
  RESUME: { shape: 'INSTANT' },
  // 「건너뜀」은 **유일한 종단**이고 사유가 지울 수 없는 타임라인에 남는다(계약). 그래서 실행
  // 취소 대신 확인을 두고, 사유는 자유 텍스트다 — 이 카드의 **첫 자유 텍스트 입력**이다.
  SKIP: {
    shape: 'TEXT',
    label: '건너뜀 사유',
    maxChars: 500,
    confirm: '이 건을 건너뜁니다. 되돌릴 수 없고 사유가 기록에 남습니다.',
  },
  AWAIT_OPENING: { shape: 'DATETIME', label: '개찰 일시' },
  RECORD_OPENING: { shape: 'CHOICE', label: '개찰 결과', choices: OPENING_CHOICES },
  ENTER_QUALIFICATION_REVIEW: { shape: 'DATETIME', label: '서류 기한' },
  RECORD_OUTCOME: { shape: 'CHOICE', label: '결과', choices: OUTCOME_CHOICES },
};
