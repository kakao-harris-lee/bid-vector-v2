// @vitest-environment jsdom
import { QueryClient, QueryClientProvider, useQuery, type UseQueryResult } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import type {
  BidCase,
  BidTimelineEntry,
  NoticeSummary,
  OpeningResultView,
  StrategyReadResponse,
  SuggestedNotice,
} from '../api/queries';
import { Button } from '../components/Button';
import { StateBadge } from '../components/StateBadge';
import { RouteError } from '../RouteError';
import { notFoundSlot, screenSlots } from '../routes';
import { ScreenPlaceholder } from '../ScreenPlaceholder';

import * as absence from '../display/absence';
import * as bidBoxDisplay from '../display/bidBoxDisplay';
import * as caseDisplay from '../display/caseDisplay';
import * as editSessionDisplay from '../display/editSessionDisplay';
import * as floorMargin from '../display/floorMargin';
import * as moneyDisplay from '../display/moneyDisplay';
import * as myBidResultDisplay from '../display/myBidResultDisplay';
import * as noticeDisplay from '../display/noticeDisplay';
import * as notificationDisplay from '../display/notificationDisplay';
import * as onboardingDisplay from '../display/onboardingDisplay';
import * as openingResultDisplay from '../display/openingResultDisplay';
import * as profileDisplay from '../display/profileDisplay';
import * as resultDisplay from '../display/resultDisplay';
import * as stateDisplay from '../display/stateDisplay';
import * as timeDisplay from '../display/timeDisplay';
import * as worksheetDisplay from '../display/worksheetDisplay';

import { BidBoxCard } from './BidBoxCard';
import { NoticeCard, SuggestionCard } from './NoticeCard';
import { ResultCard } from './ResultCard';
import { StrategyEditPanel, StrategyFieldRow, type StrategyEditSession } from './strategyEdit';
import { KonepsHandoff } from './WorksheetRecord';
import { WorksheetTimeline } from './WorksheetTimeline';

/**
 * **문면 게이트의 렌더 층**(M13/13A). 소스 층(`scripts/screen-copy-vocabulary.test.mjs`)은 `src/**`
 * 전수의 문자열을 보고, 이 층은 **그려진 결과**를 본다 — 중간 변수로 비켜 가도, 값을 런타임에
 * 조립해도, 그려진 글자에 개발 코드가 있으면 여기서 멈춘다.
 *
 * 읽는 방식이 이 층의 요점이고 **두 방향이 함께** 선다. 한 요소 **안**의 인접 텍스트 노드는
 * 합치고(`normalize` — `<p>SET-{n}</p>` 꼴의 보간이 그렇지 않으면 두 층을 모두 지나간다),
 * 요소 **사이**는 가른다(「…차이 금액 R」 다음 요소의 「-4,000원」이 붙으면 **가짜 요구표 행
 * ID** 가 선다). 「보간 대조」와 「결합 대조」가 그 두 방향을 같은 자리에서 잰다.
 * `title`·`aria-label` 은 따로 담는다 — 눈에 안 보이는 사유가 거기 산다.
 *
 * 금지 꼴의 **정본은 아래 `COPY_FORBIDDEN_SHAPES` 선언**이고 소스 층이 그것을 AST 로 읽는다.
 * 사본을 두지 않으려는 것이다 — 두 층이 한 모듈을 import 할 수 없고(W-4 가 `src` 밖 import 를
 * 막는다) `src` 의 TS 프로그램에는 node 타입이 없어 이 층이 데이터 파일도 읽을 수 없다.
 */
afterEach(cleanup);

/**
 * **금지 꼴의 정본이 이 배열이다.** 소스 층은 이 파일을 **AST 로 읽어** 같은 집합을 쓴다(저장소의
 * `GATE_MOUNTED_CARDS` 관례와 같은 꼴) — 사본을 두지 않으려는 것이고, 데이터 파일로 빼는 길은
 * 막혀 있다: `src` 의 TS 프로그램에는 node 타입이 없어 이 층이 파일을 읽을 수 없다(실측 — 그
 * 경계를 넓히는 것은 새 결정이다).
 *
 * 줄을 더하거나 고치면 **음성 대조**(두 층 모두에 있다)를 함께 본다. 좁히는 변이는 소스 층의
 * 포함 단언이 잡고, 넓히는 변이는 음성 대조가 잡는다.
 */
export const COPY_FORBIDDEN_SHAPES = [
  // capability ID 와 같은 꼴 **전수**. 접두사 목록에 기대지 않는다 — 두 글자 이상 접두 + 번호.
  // 음성: D-day `D-0`·`D-14`(한 글자 접두) · 합성 공고번호 `SYN-M9-0`(`M9` 뒤가 숫자가 아니다).
  { name: '대문자 접두 ID', source: String.raw`\b[A-Z]{2,}-\d+\b` },
  // OPEN ID. 끝을 열어 둔다(토막이 여럿이다).
  { name: 'OPEN ID', source: String.raw`OPEN-[A-Z0-9][A-Z0-9-]*` },
  // 마일스톤/slice 표기. **슬래시가 가른다** — 합성 공고번호가 음성 대조다.
  { name: '마일스톤 표기', source: String.raw`M\d+/` },
  // 결정 ID. **둘째 하이픈이 가른다** — D-day 가 음성 대조다.
  { name: '결정 ID', source: String.raw`\b[DR]-[0-9A-Za-z]+-[0-9A-Za-z]+\b` },
  // 요구표 행 ID. 음수 금액이 이어 붙으면 가짜로 서는 꼴이라 이 층은 노드 단위로 읽는다.
  { name: '요구표 행 ID', source: String.raw`R-\d\b` },
  /**
   * **등록부의 접두사에 묶인 꼴**(팀장 판정 2026-11-18 — 제한 3 을 등재가 아니라 닫는다).
   *
   * 위 「대문자 접두 ID」는 **번호가 붙은** ID 만 덮는다. 등록부에는 번호가 없는 ID 토막도 있고
   * (`OPEN-…` 의 토막들) 그것이 홀로 쓰이면 어느 꼴에도 걸리지 않았다. 번호 요구를 그냥 버리면
   * 합성 공고번호를 문다(실측) — 그래서 **접두사를 등록부의 것으로 못박는다**. 그 접두사 집합이
   * capability map §0.6 의 표와 같은지는 소스 층이 등식으로 잰다(이 줄이 정본이고 사본은 없다).
   *
   * 넓은 「대문자 접두 ID」 꼴을 **함께** 둔다 — 표에 없는 접두의 번호 ID 도 덮으려는 것이고,
   * 그래서 표가 줄어도 번호 ID 의 보호는 줄지 않는다.
   */
  {
    name: '등록부 접두 ID',
    source: String.raw`\b(?:COL|STR|QUAL|ML|DEC|NOTI|SET|OPS|BID)-[A-Z0-9][A-Z0-9-]*\b`,
  },
  /**
   * **맨 slice 토막**(cr MED-1). 「마일스톤 표기」는 슬래시를 요구하므로 `M7/7E` 는 잡고
   * **`7A`·`7E-2`·`6A3` 은 놓쳤다** — 이 slice 가 지운 문장의 뒷 절이 바로 그 꼴이었다.
   * 숫자 뒤 대문자 한 자로 시작하는 토막을 잡되 꼬리는 소문자·숫자까지만 받는다.
   *
   * **넓히지 않은 자리**: 대문자 꼬리를 받으면 `1GB` 꼴을 물고, `[A-Z]` 를 `[A-Z]+` 로 열면
   * 단위 표기와 부딪힌다. 지금 꼴은 음성 대조 스물(그 가운데 D-day 넷 · 금액 셋 · 합성
   * 공고번호 셋 · 한국어 수량 넷)과 소스 코퍼스 4208 조각에서 **위양성 0** 이다(실측).
   */
  { name: '맨 slice 토막', source: String.raw`\b\d{1,2}[A-H][0-9a-z]*\b` },
] as const;

const SHAPES: readonly { readonly name: string; readonly source: string }[] = COPY_FORBIDDEN_SHAPES;

/** 한 조각에서 걸린 꼴 전수. 조각 **하나씩** 본다 — 그것이 「텍스트 노드 단위」의 구현이다. */
function forbiddenIn(piece: string): readonly string[] {
  return SHAPES.flatMap((shape) => {
    const hits = piece.match(new RegExp(shape.source, 'gu'));
    return hits === null ? [] : [`[${shape.name}] ${[...new Set(hits)].join(' ')}`];
  });
}

/**
 * 눈에 보이는 글자와 **보이지 않는 사유**(`title`·`aria-label`)를 조각으로 모은다.
 *
 * **먼저 인접 텍스트 노드를 합친다**(`normalize`). React 는 `<p>SET-{n}</p>` 를 **한 요소 안의
 * 텍스트 노드 둘**로 그리므로, 노드마다 따로 보면 `SET-` 과 `03` 이 각각 아무 꼴에도 걸리지
 * 않는다(실측 — 그 꼴이 두 층을 모두 지나갔다). 계약이 말한 구분자는 **요소 경계**이지 노드
 * 경계가 아니다. 합치면 한 요소 안의 보간은 한 조각이 되고, 형제 요소(「…R」 + 「-4,000원」)는
 * 여전히 갈린다 — 그 둘을 같은 자리에서 잰다.
 */
function piecesOf(root: ParentNode): readonly string[] {
  // **사본에서 합친다**(cr L-r1-3) — `normalize()` 는 DOM 을 바꾸므로 살아 있는 트리에 걸면
  // 같은 렌더를 두 번 재는 test 가 서로 다른 트리를 본다. 사본은 `title`·`aria-label` 까지
  // 함께 복제되고, 이 층이 보는 것은 텍스트와 그 둘뿐이다.
  const copy = (root as Node).cloneNode(true);
  copy.normalize();
  const pieces: string[] = [];
  const walk = (node: Node): void => {
    if (node.nodeType === node.TEXT_NODE) {
      const text = node.nodeValue ?? '';
      if (text.trim() !== '') {
        pieces.push(text);
      }
      return;
    }
    if (node instanceof Element) {
      for (const name of ['title', 'aria-label'] as const) {
        const value = node.getAttribute(name);
        if (value !== null && value.trim() !== '') {
          pieces.push(value);
        }
      }
    }
    node.childNodes.forEach(walk);
  };
  walk(copy);
  return pieces;
}

const sweep = (root: ParentNode): readonly string[] =>
  piecesOf(root).flatMap((piece) => forbiddenIn(piece).map((found) => `${found} :: ${piece.trim().slice(0, 70)}`));

/* ── 표시층 표 전수 — 모듈 집합을 디렉터리에서 유도하고 소스 층이 등식을 건다 ───────── */

const DISPLAY_MODULES: Readonly<Record<string, Readonly<Record<string, unknown>>>> = {
  'absence.ts': absence,
  'bidBoxDisplay.ts': bidBoxDisplay,
  'caseDisplay.ts': caseDisplay,
  'editSessionDisplay.ts': editSessionDisplay,
  'floorMargin.ts': floorMargin,
  'moneyDisplay.ts': moneyDisplay,
  'myBidResultDisplay.ts': myBidResultDisplay,
  'noticeDisplay.ts': noticeDisplay,
  'notificationDisplay.ts': notificationDisplay,
  'onboardingDisplay.ts': onboardingDisplay,
  'openingResultDisplay.ts': openingResultDisplay,
  'profileDisplay.ts': profileDisplay,
  'resultDisplay.ts': resultDisplay,
  'stateDisplay.ts': stateDisplay,
  'timeDisplay.ts': timeDisplay,
  'worksheetDisplay.ts': worksheetDisplay,
};

interface CopyRow {
  readonly label: string;
  readonly why?: string;
  readonly tone?: unknown;
}

/**
 * 표의 행을 **구조로** 찾는다 — `label` 이 글자인 객체가 행이다. 중첩을 따라가므로 모듈에 표를
 * 더하거나 표에 행을 더하면 자동으로 든다(깊이 상한은 데이터가 자료 구조임을 전제한다).
 */
function copyRowsOf(value: unknown, depth = 0): readonly CopyRow[] {
  if (depth > 6 || value === null || typeof value !== 'object') {
    return [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((one) => copyRowsOf(one, depth + 1));
  }
  const record = value as Record<string, unknown>;
  if (typeof record.label === 'string') {
    return [{ label: record.label, why: typeof record.why === 'string' ? record.why : undefined, tone: record.tone }];
  }
  return Object.values(record).flatMap((one) => copyRowsOf(one, depth + 1));
}

const ROWS_BY_MODULE = Object.entries(DISPLAY_MODULES).map(([file, module]) => ({
  file,
  rows: Object.values(module).flatMap((one) => copyRowsOf(one)),
}));
const ALL_ROWS = ROWS_BY_MODULE.flatMap((one) => one.rows);

/** `tone` 이 있는 행은 **실제 2층 라벨**로 그린다 — 라벨은 글자, 사유는 `title` 과 sr-only 다. */
function CopyRowView({ row }: { readonly row: CopyRow }) {
  if (typeof row.tone === 'string') {
    return <StateBadge display={{ label: row.label, why: row.why, tone: row.tone as never }} />;
  }
  return (
    <span>
      <span title={row.why}>{row.label}</span>
      {row.why === undefined ? null : <span>{row.why}</span>}
    </span>
  );
}

/* ── 카드 fixture — **최소**다. 어휘 전수는 기존 DOM 게이트가 들고, 이 층은 문면만 본다 ── */

const NOTICE_NUMBER = 'SYN-M9-0';
/**
 * **제목이 없는 공고** — 그 자리에서만 공고번호가 글자로 그려진다(카드는 `title ?? noticeNumber`
 * 를 쓴다). 계약이 지명한 「공고번호가 **문장 안에 끼어** 렌더되는」 자리가 이것이다: 담기
 * 버튼의 접근 이름이 `<제목 또는 번호> 입찰함에 담기` 라, 제목이 없으면 번호가 문장에 든다.
 * fixture 어휘 단언의 **비공허 짝**이 이 번호를 쓴다.
 */
const TITLELESS_NUMBER = 'SYN-M9-9';
const CASE_NUMBER = 'SYN-M9-CASE';
const SESSION_ID = 'SYN-M9-SESSION';
const COMMAND_ID = 'SYN-M9-COMMAND';
const ROUND = '000';
const NOW = new Date('2026-11-18T00:00:00Z');
const SENTINEL = '던진 값의 원문 조각';
/** 식별자 fixture 전수 — **금지 집합 밖**임을 스윕 **앞에** 단언한다(아래 「fixture 어휘」). */
const FIXTURE_IDENTIFIERS = [NOTICE_NUMBER, TITLELESS_NUMBER, CASE_NUMBER, SESSION_ID, COMMAND_ID, ROUND] as const;

/** D-day 네 값을 **실제 마감 배지**로 그린다 — 음성 대조가 합성 글자가 아니라 렌더 결과다. */
const DEADLINE_OFFSETS_IN_DAYS = [0, 1, 14, -3] as const;

const noop = () => undefined;

/**
 * 넘김 카드의 하한 여유 fixture — **근거가 선 쪽**을 준다. 빈 자리만 그리면 그 카드의 문면
 * 절반이 미측정으로 남으므로, 아래 비공허 단언이 이 값이 실제로 재어졌는지 본다.
 */
const HANDOFF_MARGIN = floorMargin.floorMarginOf({
  groundsState: 'USABLE',
  rateFraction: '0.87745',
  baseAmountWon: 1_200_000_000,
  baseAmountVatTreatment: 'INCLUSIVE',
  bidAmountWon: 1_100_000_000,
  bidVatTreatment: 'INCLUSIVE',
});

function noticeRow(offsetDays: number): NoticeSummary {
  return {
    noticeNumber: `${NOTICE_NUMBER}-${offsetDays}`,
    noticeRound: ROUND,
    status: 'Open',
    title: '합성 공고',
    demandAgencyName: '합성 수요기관',
    noticeAgencyName: null,
    baseAmountWon: 1_200_000_000,
    baseAmountCurrency: 'KRW',
    baseAmountBasis: 'BASE_AMOUNT',
    baseAmountVatTreatment: 'INCLUSIVE',
    baseAmountProvenance: 'PUBLISHED',
    deadlineAt: new Date(NOW.getTime() + offsetDays * 24 * 60 * 60 * 1000).toISOString(),
  };
}

const titlelessNotice = (): NoticeSummary => ({ ...noticeRow(0), noticeNumber: TITLELESS_NUMBER, title: null });

const suggestionRow = (offsetDays: number): SuggestedNotice => ({
  ...noticeRow(offsetDays),
  licenseVerdict: 'ELIGIBLE',
  unjudgedAxes: [],
});

const bidCaseRow = (): BidCase => ({
  caseId: 11,
  noticeNumber: CASE_NUMBER,
  noticeRound: ROUND,
  bidKind: 'REAL',
  state: 'WATCHING',
  caseVersion: 3,
  nextCheckpointKind: 'DEADLINE',
  nextCheckpointAt: '2026-12-31T16:05:00Z',
  overdue: false,
  reconciliationCandidate: false,
});

const timelineRows = (): readonly BidTimelineEntry[] => [
  {
    caseVersion: 1,
    fromState: null,
    toState: 'WATCHING',
    command: 'ADD_TO_BID_BOX',
    actorKind: 'USER',
    retroactive: false,
    occurredAt: '2026-07-15T15:00:00Z',
  },
];

const strategyRow = (): StrategyReadResponse => ({
  revision: 7,
  focusCategories: ['토목'],
  focusRegionTerms: ['서울특별시'],
  excludeRegionTerms: [],
  requiredKeywordTerms: [],
  excludeKeywordTerms: [],
});

const editSession = (strategy: StrategyReadResponse): StrategyEditSession => ({
  open: {
    session: {
      sessionId: SESSION_ID,
      state: 'WAITING_FOR_CONFIRMATION',
      field: 'MIN_BUDGET',
      sessionVersion: 2,
      expiresAt: '2026-12-31T16:05:00Z',
      strategyRevision: strategy.revision,
    },
    field: 'MIN_BUDGET',
    sent: editSessionDisplay.sentValueView('MIN_BUDGET', {
      commandId: COMMAND_ID,
      field: 'MIN_BUDGET',
      amountWon: 2_500_000_000,
    }),
  },
  inProgress: true,
  busy: false,
  isError: false,
  error: null,
  conflictCode: null,
  raw: '',
  missing: false,
  begin: noop,
  retry: noop,
  onRaw: noop,
  onSendValue: noop,
  onConfirm: noop,
  onEdit: noop,
  onCancel: noop,
  dismiss: noop,
});

/** `ResultCard` 는 `UseQueryResult` 를 받는다 — 캐스팅하지 않고 실제 `useQuery` 를 fixture 로 푼다. */
function ResultCardHarness() {
  const opening: UseQueryResult<OpeningResultView> = useQuery({
    queryKey: ['copy-gate-opening'],
    queryFn: () => Promise.reject(new Error('합성 실패')),
    retry: false,
  });
  return (
    <ul>
      <ResultCard bidCase={bidCaseRow()} opening={opening} result={{ kind: 'MISSING' }} />
    </ul>
  );
}

/** 결합 대조 — 「…R」 로 끝나는 요소와 음수 금액 요소를 **형제**로 둔다(계약이 지명한 쌍). */
function AdjacentNegativeAmount() {
  return (
    <p>
      <span>차이 금액 R</span>
      <span>-4,000원</span>
    </p>
  );
}

function renderAll() {
  const strategy = strategyRow();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const tree = (
    <>
      <ul>
        {DEADLINE_OFFSETS_IN_DAYS.map((offset) => (
          <NoticeCard key={`n${offset}`} notice={noticeRow(offset)} now={NOW} action={null} />
        ))}
        {/* 담기 버튼의 접근 이름을 **실화면과 같은 꼴**로 둔다 — 공고번호가 문장 안에 든다. */}
        <NoticeCard
          notice={titlelessNotice()}
          now={NOW}
          action={
            <Button onClick={noop} ariaLabel={`${titlelessNotice().title ?? TITLELESS_NUMBER} 입찰함에 담기`}>
              담기
            </Button>
          }
        />
        {DEADLINE_OFFSETS_IN_DAYS.map((offset) => (
          <SuggestionCard key={`s${offset}`} suggestion={suggestionRow(offset)} now={NOW} action={null} />
        ))}
        <BidBoxCard bidCase={bidCaseRow()} pending={false} onSend={noop} />
      </ul>
      <ResultCardHarness />
      <WorksheetTimeline entries={timelineRows()} />
      {/*
        **13C 가 세운 카드**(머지 차례에 들어왔다). 카드 의무 등식이 이 자리를 요구한다 — 새
        카드가 생기면 문면 게이트의 모집단에 자동으로 드는 것이 그 등식의 요점이다. fixture 는
        최소이고(이 층은 문면만 본다) 하한 여유는 **근거가 선 쪽**을 준다 — 빈 자리만 그리면
        그 카드의 문면 절반이 미측정으로 남는다.
      */}
      <KonepsHandoff
        noticeNumber={TITLELESS_NUMBER}
        amountWon={1_100_000_000}
        margin={HANDOFF_MARGIN}
      />
      <StrategyFieldRow field="MIN_BUDGET" strategy={strategy} disabled={false} onBegin={noop} />
      <StrategyEditPanel session={editSession(strategy)} strategy={strategy} />
      {[...screenSlots, notFoundSlot].map((slot) => (
        <ScreenPlaceholder key={slot.id} slot={slot} />
      ))}
      {ALL_ROWS.map((row, index) => (
        <CopyRowView key={`r${index}`} row={row} />
      ))}
      <AdjacentNegativeAmount />
    </>
  );
  const router = createMemoryRouter([{ path: '/', element: tree }], { initialEntries: ['/'] });
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

function Thrower(): never {
  throw new Error(SENTINEL);
}

/**
 * 던지기를 **바깥에서** 끈다 — 렌더 안에서 한 번만 던지는 꼴은 설 수 없다(실측: React 가 실패한
 * 렌더를 다시 돌려 경계가 서기 **전에** 회복했다). 플래그를 test 가 쥐면 경계가 먼저 서고,
 * 그 뒤 「다시 시도」가 정말로 자리를 다시 여는지 잴 수 있다.
 */
function makeFlakyRoute() {
  const state = { shouldThrow: true };
  const Flaky = () => {
    if (state.shouldThrow) {
      throw new Error(SENTINEL);
    }
    return <p data-testid="recovered">화면이 섰습니다</p>;
  };
  return { state, Flaky };
}

function renderRouteBoundary() {
  const router = createMemoryRouter([{ path: '/', element: <Thrower />, errorElement: <RouteError /> }], {
    initialEntries: ['/'],
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

/* ── 단언 ─────────────────────────────────────────────────────────────────────── */

describe('문면 게이트 — 모집단이 비지 않았다', () => {
  /**
   * **모듈 표의 값이 서로 다른가**(머지 차례의 변이 T 가 찾았다). 소스 층의 등식은 이 파일의
   * **import 선언**을 세므로, 키를 그대로 두고 **값만** 다른 모듈로 바꿔치우면 그 모듈의 행이
   * 조용히 빠지는데도 초록이었다. 값 집합의 크기로 그 자리를 닫는다.
   */
  it('넘김 카드의 하한 여유 fixture 가 실제로 재어진다', () => {
    // 재지 못한 값이면 카드가 빈 자리만 그리고 그 문면이 스윕에 닿지 않는다.
    expect(HANDOFF_MARGIN.kind).not.toBe('unmeasurable');
  });

  it('표시층 모듈 표의 값이 서로 다른 모듈이다', () => {
    const entries = Object.entries(DISPLAY_MODULES);
    expect(new Set(entries.map(([, module]) => module)).size).toBe(entries.length);
  });

  it('금지 꼴 · 표 행 · 그린 조각의 하한', () => {
    expect(SHAPES.length).toBeGreaterThanOrEqual(7);
    expect(ALL_ROWS.length).toBeGreaterThanOrEqual(100);
    expect(ROWS_BY_MODULE.filter((one) => one.rows.length > 0).length).toBeGreaterThanOrEqual(8);
    const { container } = renderAll();
    expect(piecesOf(container).length).toBeGreaterThanOrEqual(300);
  });

  it('fixture 어휘가 금지 집합 밖이다 — 면제를 만들지 않으므로 이 단언이 진단이다', () => {
    expect(FIXTURE_IDENTIFIERS.filter((one) => forbiddenIn(one).length > 0)).toEqual([]);
    // 비공허 짝: 그 어휘가 **실제로 그려진다**(집합이 비면 위 단언이 공허하다).
    const { container } = renderAll();
    const drawn = piecesOf(container).join('\u0001');
    // 제목 없는 공고의 번호는 카드 제목과 담기 버튼의 접근 이름 **둘**로 그려진다.
    expect(drawn.split(TITLELESS_NUMBER).length - 1).toBeGreaterThanOrEqual(2);
  });
});

describe('문면 게이트 — 렌더한 DOM 에 개발 코드가 0 이다', () => {
  it('카드 · 표 전수 · 빈 자리 전수', () => {
    const { container } = renderAll();
    expect(sweep(container)).toEqual([]);
  });

  it('라우트 오류 경계', () => {
    const { container } = renderRouteBoundary();
    expect(sweep(container)).toEqual([]);
  });
});

describe('문면 게이트 — 라우트 경계가 던진 값의 원문을 보이지 않는다', () => {
  it('원문 조각이 그려지지 않고 다시 시도가 있다', () => {
    renderRouteBoundary();
    const boundary = screen.getByTestId('route-error');
    expect(boundary.textContent ?? '').not.toContain(SENTINEL);
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeDefined();
  });
});

describe('라우트 오류 — 「다시 시도」가 자리를 다시 연다', () => {
  it('던지기를 끈 뒤 누르면 화면이 선다', async () => {
    const { state, Flaky } = makeFlakyRoute();
    const router = createMemoryRouter([{ path: '/', element: <Flaky />, errorElement: <RouteError /> }], {
      initialEntries: ['/'],
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
    expect(screen.getByTestId('route-error')).toBeDefined();
    state.shouldThrow = false;
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(await screen.findByTestId('recovered')).toBeDefined();
  });

  /**
   * 음성 대조 — **다른 test 에** 둔다. 한 test 안에서 두 번 그리면 `render` 가 돌려주는 질의가
   * `document.body` 에 묶여 앞 렌더가 남긴 자리를 집는다(실측). 그 함정이 이 저장소의 공유
   * 하네스에서 이미 한 번 섰다.
   */
  it('던지기가 그대로면 눌러도 경계가 남는다', () => {
    const { Flaky } = makeFlakyRoute();
    const router = createMemoryRouter([{ path: '/', element: <Flaky />, errorElement: <RouteError /> }], {
      initialEntries: ['/'],
    });
    render(<RouterProvider router={router} />);
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(screen.getByTestId('route-error')).toBeDefined();
    expect(screen.queryByTestId('recovered')).toBeNull();
  });
});

describe('문면 — 걷어 낸 내부 용어가 그 자리에 돌아오지 않는다', () => {
  /**
   * 금지 **꼴**은 개발 코드를 잡지만 내부 용어(「배선」·「커널」 같은 말)는 열거할 수 없다 — 그
   * 판단은 사람의 것이다. 그래서 이 slice 가 고친 자리만 **축어로** 잠근다. 일반 축은 열지 않는다.
   */
  /**
   * 탐색 라벨의 **머리 글자**는 금지 꼴이 아니다 — 「A 」는 개발 코드가 아니라 요구표의 화면
   * 구분을 화면에 흘린 것이다. 꼴로는 잡히지 않으므로 자리 표에 **직접** 거는 술어를 둔다.
   */
  it('자리 표 라벨이 요구표의 머리 글자로 시작하지 않는다', () => {
    const slots = [...screenSlots, notFoundSlot];
    // 비공허: 자리 표가 비면 아래 단언이 공허하다.
    expect(slots.length).toBeGreaterThanOrEqual(7);
    expect(slots.filter((slot) => /^[A-Z] /u.test(slot.label)).map((slot) => slot.id)).toEqual([]);
  });

  it('표시층의 고친 행들', () => {
    const fixed = [
      resultDisplay.RESULT_PENDING_SLOTS[0]?.why ?? '',
      resultDisplay.RESULT_PENDING_SLOTS[1]?.why ?? '',
      worksheetDisplay.BID_PRICE_MENU_REASON_DISPLAY.MENU_PATH_NOT_WIRED.label,
      worksheetDisplay.BID_PRICE_MENU_REASON_DISPLAY.MENU_PATH_NOT_WIRED.why ?? '',
      worksheetDisplay.ML_UNAVAILABLE_REASON_DISPLAY.UNSUPPORTED_SCHEMA.label,
      myBidResultDisplay.RECONCILIATION_BLOCKER_DISPLAY.OPERATOR_NAME_NORMALIZES_TO_EMPTY.why ?? '',
      myBidResultDisplay.RECONCILIATION_BLOCKER_DISPLAY.WINNER_NAME_NORMALIZES_TO_EMPTY.why ?? '',
    ];
    for (const term of ['배선', '커널', '스키마', '토큰']) {
      expect(fixed.filter((one) => one.includes(term))).toEqual([]);
    }
    /**
     * **영구 부재 단언도 돌아오면 안 된다**(cr MED-2 · 수정 라운드 1 의 변이 Q 가 찾았다).
     * 그 문장은 내부 용어가 아니라 **장부와 어긋나는 주장**이라 어느 꼴도 잡지 못한다 —
     * 되돌리는 변이가 조용히 초록이었다. 두 자리 모두 「나중에 오는 자리」이므로(하나는 채택
     * 뒤 구현 대기, 하나는 후속 분류) 그 주장만 축어로 막는다.
     */
    for (const claim of ['나중에 생기는 자리가 아닙니다', '영영', '다루지 않는 자리입니다']) {
      expect(fixed.filter((one) => one.includes(claim))).toEqual([]);
    }
    // 비공허: 고친 행이 비어 있지 않다(빈 글자는 어느 금지 어휘도 담지 않아 위 단언이 공허하다).
    expect(fixed.filter((one) => one.trim() === '')).toEqual([]);
  });
});

describe('문면 게이트 — 양성·음성 대조', () => {
  it('심은 문면 하나가 그려지면 붉는다', () => {
    const { container } = render(<p>{`사유는 ${'SET'}-03 이다`}</p>);
    expect(sweep(container).length).toBeGreaterThan(0);
  });

  /**
   * **맨 slice 토막의 양성·음성**(cr MED-1). 이 slice 가 지운 문장의 뒷 절(「7A 는 자리만 둔다」)이
   * 앞 판의 꼴 여섯을 모두 지나갔다 — 그 자리를 심은 문면으로 잠근다.
   */
  it('맨 slice 토막이 심어지면 붉는다', () => {
    const { container } = render(<p>이 화면은 7E 에서 섭니다. 7A 는 자리만 둡니다.</p>);
    expect(sweep(container).length).toBeGreaterThan(0);
  });

  it('맨 slice 토막 꼴이 수량·금액·D-day 를 물지 않는다', () => {
    const notTokens = ['3안', '4.2억', '12건', '1,200,000,000원', 'D-0', 'D+3', '44px', '1GB', '4 / 8 완료'];
    expect(notTokens.filter((one) => forbiddenIn(one).length > 0)).toEqual([]);
  });

  it('D-day 네 값이 실제 배지로 그려지고 걸리지 않는다', () => {
    const { container } = renderAll();
    const drawn = piecesOf(container);
    for (const offset of DEADLINE_OFFSETS_IN_DAYS) {
      const expected = timeDisplay.dayCountText(offset);
      expect(expected).not.toBeNull();
      expect(drawn.some((piece) => piece.includes(expected ?? ''))).toBe(true);
    }
  });

  /**
   * **보간 대조**(v H-1). 한 요소 **안**의 보간은 React 가 텍스트 노드 둘로 그린다 — 노드마다
   * 따로 보면 어느 쪽도 꼴에 걸리지 않아 두 층이 모두 초록이었다(실측). 합치면 붉는다.
   *
   * 이 대조와 아래 결합 대조는 **반대 방향**이라 짝으로 둔다: 요소 **안**은 합쳐야 하고 요소
   * **사이**는 갈라야 한다. 한쪽만 재면 고치는 사람이 다른 쪽을 깨뜨린다.
   */
  it('보간 대조 — 한 요소 안의 보간은 합쳐져 붉는다', () => {
    const code = '03';
    const { container } = render(<p>이 주소는 SET-{code} 에 따라 화면이 없습니다.</p>);
    expect(sweep(container).length).toBeGreaterThan(0);
  });

  it('결합 대조 — 이어붙이면 붉고 노드 단위로는 초록이다', () => {
    const { container } = render(<AdjacentNegativeAmount />);
    const drawn = piecesOf(container);
    expect(forbiddenIn(drawn.join('')).length).toBeGreaterThan(0);
    expect(drawn.flatMap(forbiddenIn)).toEqual([]);
  });

  it('상류 하네스 공고번호는 양성이다 — 그려지면 붉는다', () => {
    const { container } = render(<p>담기: SYN-M9-OPEN-REBID 공고</p>);
    expect(sweep(container).length).toBeGreaterThan(0);
  });
});

describe('문면 게이트 — 이 slice 가 바꾼 문면이 스윕에 닿는다', () => {
  it('도달 단언 — 바꾼 자리의 글자가 그려진 조각에 있다', () => {
    const { container } = renderAll();
    const drawn = piecesOf(container).join('\u0001');
    const changed = [
      resultDisplay.RESULT_PENDING_SLOTS[0]?.why,
      resultDisplay.RESULT_PENDING_SLOTS[1]?.why,
      worksheetDisplay.BID_PRICE_MENU_REASON_DISPLAY.MENU_PATH_NOT_WIRED.label,
      worksheetDisplay.BID_PRICE_MENU_REASON_DISPLAY.MENU_PATH_NOT_WIRED.why,
      myBidResultDisplay.RECONCILIATION_BLOCKER_DISPLAY.OPERATOR_NAME_NORMALIZES_TO_EMPTY.why,
      myBidResultDisplay.RECONCILIATION_BLOCKER_DISPLAY.WINNER_NAME_NORMALIZES_TO_EMPTY.why,
      worksheetDisplay.ML_UNAVAILABLE_REASON_DISPLAY.UNSUPPORTED_SCHEMA.label,
      ...screenSlots.map((slot) => slot.label),
    ];
    expect(changed.filter((one) => one === undefined)).toEqual([]);
    expect(changed.filter((one) => one !== undefined && !drawn.includes(one))).toEqual([]);
  });
});
