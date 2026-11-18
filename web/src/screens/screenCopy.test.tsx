// @vitest-environment jsdom
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { QueryClient, QueryClientProvider, useQuery, type UseQueryResult } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
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

import * as bidBoxDisplay from '../display/bidBoxDisplay';
import * as caseDisplay from '../display/caseDisplay';
import * as editSessionDisplay from '../display/editSessionDisplay';
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
import { WorksheetTimeline } from './WorksheetTimeline';

/**
 * **문면 게이트의 렌더 층**(M13/13A). 소스 층(`scripts/screen-copy-vocabulary.test.mjs`)은 `src/**`
 * 전수의 문자열을 보고, 이 층은 **그려진 결과**를 본다 — 중간 변수로 비켜 가도, 값을 런타임에
 * 조립해도, 그려진 글자에 개발 코드가 있으면 여기서 멈춘다.
 *
 * 읽는 방식이 이 층의 요점이다. **텍스트 노드 단위**로 모으고 `title`·`aria-label` 을 **따로**
 * 담는다 — 이어붙이면 「…차이 금액 R」 요소 다음의 「-4,000원」이 붙어 **가짜 요구표 행 ID** 가
 * 선다(아래 「결합 대조」가 그 둘을 같은 자리에서 잰다).
 *
 * 금지 꼴은 **두 층이 같은 바이트를 읽는다**(`config/screen-copy-shapes.txt`). 사본을 두지 않는
 * 이유는 두 층이 한 모듈을 import 할 수 없기 때문이다 — W-4 가 `src` 밖 import 를 막는다.
 */
afterEach(cleanup);

/**
 * 등록부의 자리를 **vitest 의 작업 디렉터리**(`web/`)에서 푼다. `import.meta.url` 은 쓸 수 없다 —
 * Vite 가 그것을 `file:` 가 아닌 주소로 바꿔 놓는다(실측). 없으면 **던진다**: 조용히 빈 집합으로
 * 내려가면 모든 판정이 공허해진다.
 */
const SHAPES_PATH = resolve(process.cwd(), 'config/screen-copy-shapes.txt');
if (!existsSync(SHAPES_PATH)) {
  throw new Error(`금지 꼴 등록부를 찾지 못했다(작업 디렉터리 ${process.cwd()}): ${SHAPES_PATH}`);
}

interface Shape {
  readonly name: string;
  readonly source: string;
}

function shapesOf(): readonly Shape[] {
  const shapes: Shape[] = [];
  for (const raw of readFileSync(SHAPES_PATH, 'utf8').split('\n')) {
    const line = raw.trim();
    if (line === '' || line.startsWith('#')) {
      continue;
    }
    const at = line.indexOf(' :: ');
    if (at < 0) {
      throw new Error(`금지 꼴 등록부의 줄을 읽지 못했다: ${line}`);
    }
    shapes.push({ name: line.slice(0, at).trim(), source: line.slice(at + 4).trim() });
  }
  if (shapes.length === 0) {
    throw new Error('금지 꼴 등록부가 비었다');
  }
  return shapes;
}

const SHAPES = shapesOf();

/** 한 조각에서 걸린 꼴 전수. 조각 **하나씩** 본다 — 그것이 「텍스트 노드 단위」의 구현이다. */
function forbiddenIn(piece: string): readonly string[] {
  return SHAPES.flatMap((shape) => {
    const hits = piece.match(new RegExp(shape.source, 'gu'));
    return hits === null ? [] : [`[${shape.name}] ${[...new Set(hits)].join(' ')}`];
  });
}

/** 눈에 보이는 글자와 **보이지 않는 사유**(`title`·`aria-label`)를 조각으로 모은다. */
function piecesOf(root: ParentNode): readonly string[] {
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
  walk(root);
  return pieces;
}

const sweep = (root: ParentNode): readonly string[] =>
  piecesOf(root).flatMap((piece) => forbiddenIn(piece).map((found) => `${found} :: ${piece.trim().slice(0, 70)}`));

/* ── 표시층 표 전수 — 모듈 집합을 디렉터리에서 유도하고 소스 층이 등식을 건다 ───────── */

const DISPLAY_MODULES: Readonly<Record<string, Readonly<Record<string, unknown>>>> = {
  'bidBoxDisplay.ts': bidBoxDisplay,
  'caseDisplay.ts': caseDisplay,
  'editSessionDisplay.ts': editSessionDisplay,
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
    command: 'WATCH',
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
        <BidBoxCard bidCase={bidCaseRow()} onSend={noop} />
      </ul>
      <ResultCardHarness />
      <WorksheetTimeline entries={timelineRows()} />
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
  it('금지 꼴 · 표 행 · 그린 조각의 하한', () => {
    expect(SHAPES.length).toBeGreaterThanOrEqual(5);
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

describe('문면 게이트 — 양성·음성 대조', () => {
  it('심은 문면 하나가 그려지면 붉는다', () => {
    const { container } = render(<p>{`사유는 ${'SET'}-03 이다`}</p>);
    expect(sweep(container).length).toBeGreaterThan(0);
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
