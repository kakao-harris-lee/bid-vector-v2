// @vitest-environment jsdom
import { QueryClient, QueryClientProvider, useQuery, type UseQueryResult } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import type {
  BidCase,
  BidTimelineEntry,
  MyBidCase,
  NoticeSummary,
  OpeningResultView,
  SuggestedNotice,
} from '../api/queries';
import {
  AMOUNT_BASIS_DISPLAY,
  AMOUNT_PROVENANCE_DISPLAY,
  MONEY_TOKENS,
  VAT_TREATMENT_DISPLAY,
  type AmountBasis,
  type AmountProvenance,
  type VatTreatment,
} from '../display/moneyDisplay';

import { BidBoxCard } from './BidBoxCard';
import { NoticeCard, SuggestionCard } from './NoticeCard';
import { ResultCard } from './ResultCard';
import { WorksheetTimeline } from './WorksheetTimeline';

/**
 * **D-4 의 「DOM 에 원문 0」 게이트.** lint 층(`scripts/display-gate-selectors.test.mjs`)은
 * 보간의 **꼴**을 보고, 이 층은 **렌더한 결과**를 본다 — 중간 변수로 비켜 가도, 새 화면이
 * 자기 분기를 새로 적어도, 그려진 글자에 원문이 있으면 여기서 멈춘다.
 *
 * 금지 목록을 **표에서 유도한다**(`MONEY_TOKENS`) — 손으로 적은 목록은 어휘가 늘 때 조용히
 * 뒤처진다. 시각 쪽은 fixture 의 ISO 원문 자신이 금지 문자열이다.
 */
afterEach(cleanup);

/** 서식을 지나면 **글자가 달라지는** 값을 쓴다 — 같은 글자면 이 단언이 공허하다. */
const ISO_DEADLINE = '2026-11-04T15:30:00Z';
const ISO_CHECKPOINT = '2026-12-31T16:05:00Z';
const KST_CHECKPOINT = '2027-01-01 01:05';
const ISO_OCCURRED = '2026-07-15T15:00:00Z';
const KST_OCCURRED = '2026-07-16 00:00';
/** 화면 E 의 여섯 칸을 **전부 비-null 로** 채운다(r1 v M-1 — null 만 주면 그 칸이 미측정이다). */
const ISO_OPENED = '2026-09-30T14:59:59.999Z';
const KST_OPENED = '2026-09-30 23:59';
const ISO_OBSERVED = '2026-10-01T00:00:00Z';
const ISO_SUBMITTED = '2026-10-31T03:20:31.073Z';
const ISO_CONFIRMED = '2026-10-31T03:20:31.082Z';
const ISO_PROVISIONAL = '2026-10-02T06:07:08Z';
/** **읽히지 않는 값 하나**(r1 CR LOW) — `InstantText` 의 셋째 얼굴이 어떤 화면 test 에서도 그려지지 않았다. */
const UNREADABLE_AT = '어제쯤';

const RAW_INSTANTS: readonly string[] = [
  ISO_DEADLINE,
  ISO_CHECKPOINT,
  ISO_OCCURRED,
  ISO_OPENED,
  ISO_OBSERVED,
  ISO_SUBMITTED,
  ISO_CONFIRMED,
  ISO_PROVISIONAL,
];

const BASIS_VALUES = Object.keys(AMOUNT_BASIS_DISPLAY) as readonly AmountBasis[];
const PROVENANCE_VALUES = Object.keys(AMOUNT_PROVENANCE_DISPLAY) as readonly AmountProvenance[];
const VAT_VALUES = Object.keys(VAT_TREATMENT_DISPLAY) as readonly VatTreatment[];

/**
 * 어휘 전수를 **한 화면에** 올린다 — basis 여섯과 provenance 여섯을 짝지어 여섯 장을 그리고
 * 과세 셋을 돌려 쓴다. 조합 108 장을 그리는 것이 목적이 아니라 **모든 토큰이 한 번은 그려지는
 * 것**이 목적이다.
 */
function noticeRows(): readonly NoticeSummary[] {
  return BASIS_VALUES.map((basis, index) => ({
    noticeNumber: `SYN-M9-${index}`,
    noticeRound: '000',
    status: 'Open',
    title: `합성 공고 ${index}`,
    demandAgencyName: '합성 수요기관',
    noticeAgencyName: null,
    baseAmountWon: 1_200_000_000,
    baseAmountCurrency: 'KRW',
    baseAmountBasis: basis,
    baseAmountVatTreatment: VAT_VALUES[index % VAT_VALUES.length],
    baseAmountProvenance: PROVENANCE_VALUES[index],
    deadlineAt: ISO_DEADLINE,
  }));
}

function suggestionRows(): readonly SuggestedNotice[] {
  return noticeRows().map((notice) => ({
    ...notice,
    licenseVerdict: 'ELIGIBLE' as const,
    unjudgedAxes: [],
  }));
}

function bidCaseRow(): BidCase {
  return {
    caseId: 11,
    noticeNumber: 'SYN-M9-CASE',
    noticeRound: '000',
    bidKind: 'REAL',
    state: 'WATCHING',
    caseVersion: 3,
    nextCheckpointKind: 'DEADLINE',
    nextCheckpointAt: ISO_CHECKPOINT,
    overdue: false,
    reconciliationCandidate: false,
  };
}

function timelineRows(): readonly BidTimelineEntry[] {
  return [
    {
      caseVersion: 1,
      fromState: null,
      toState: 'WATCHING',
      command: 'ADD_TO_BID_BOX',
      actorKind: 'USER',
      retroactive: false,
      occurredAt: ISO_OCCURRED,
    },
    {
      caseVersion: 2,
      fromState: 'WATCHING',
      toState: 'DRAFT_BID',
      command: 'DRAFT_BID',
      actorKind: 'USER',
      retroactive: false,
      // **읽히지 않는 값** — `InstantText` 의 셋째 얼굴을 그리게 하는 유일한 자리다(r1 CR LOW).
      occurredAt: UNREADABLE_AT,
    },
  ];
}

/**
 * 화면 E 카드의 fixture — **모든 `*At` 과 금액 부속 칸을 비-null 로** 채운다(r1 v M-1). null 만
 * 주면 그 칸이 「없음」 분기만 그려져 원문 보간이 미측정으로 남는다(그 공백에 실제 위반이 살아
 * 있었다 — r1 H-1).
 */
function openingView(): OpeningResultView {
  return {
    noticeNumber: 'SYN-M9-CASE',
    noticeRound: '000',
    state: 'OPENED',
    participantCount: 7,
    actualOpeningAt: ISO_OPENED,
    observedAt: ISO_OBSERVED,
    confirmedAwardAmountWon: 1_350_000_000,
    confirmedAwardAmountCurrency: 'KRW',
    confirmedAwardAmountBasis: 'AWARD',
    confirmedAwardAmountVatTreatment: 'INCLUSIVE',
    confirmedAwardAmountProvenance: 'PUBLISHED',
    provisionalRankOneState: 'OBSERVED',
    provisionalRankOneDuplicateCount: null,
    provisionalRankOneBidAmountWon: 1_310_000_000,
    provisionalRankOneBidAmountCurrency: 'KRW',
    provisionalRankOneBidAmountVatTreatment: 'EXCLUSIVE',
    provisionalRankOneBidRateFraction: '0.87745',
    provisionalRankOneObservedAt: ISO_PROVISIONAL,
  };
}

function myBidCaseRow(): MyBidCase {
  return {
    caseId: 11,
    noticeNumber: 'SYN-M9-CASE',
    noticeRound: '000',
    recordedState: 'DECIDED',
    submittedAmountWon: 1_350_000_000,
    submittedAmountCurrency: 'KRW',
    submittedAmountVatTreatment: 'INCLUSIVE',
    submittedAmountOrigin: 'OPERATOR_ENTERED',
    chosenOptionRef: null,
    submittedAt: ISO_SUBMITTED,
    confirmedAt: ISO_CONFIRMED,
    openingState: 'OPENED',
    confirmedAwardAmountWon: 1_350_000_000,
    confirmedAwardAmountCurrency: 'KRW',
    confirmedAwardAmountBasis: 'BASE_AMOUNT',
    confirmedAwardAmountVatTreatment: 'INCLUSIVE',
    confirmedAwardAmountProvenance: 'OPERATOR_DECLARED',
    reconciledOutcome: 'WON',
    reconciliationBlocker: null,
    outcomeAgreement: 'AGREED',
    // 차이 칸(`AwardGap` COMPUTED) — r1 H-1 의 둘째 자리라 **반드시 그려지게** 둔다.
    gapState: 'COMPUTED',
    gapWon: -40_000_000,
    gapCurrency: 'KRW',
  };
}

/**
 * `ResultCard` 는 `UseQueryResult` 를 받는다. **손으로 지어내지 않는다**(캐스팅은 이 저장소가
 * 거부하는 꼴이다) — 실제 `useQuery` 를 fixture 로 풀어 그 값을 그대로 넘긴다.
 */
function ResultCardHarness() {
  const opening: UseQueryResult<OpeningResultView> = useQuery({
    queryKey: ['m9-gate-opening'],
    queryFn: () => Promise.resolve(openingView()),
  });
  if (!opening.isSuccess) {
    return <p data-testid="gate-opening-pending">적재 중</p>;
  }
  return (
    <ul>
      <ResultCard bidCase={bidCaseRow()} opening={opening} result={{ kind: 'FOUND', result: myBidCaseRow() }} />
    </ul>
  );
}

/** 카드들은 `<Link>` 를 쓴다 — 라우터 안에서 그린다(실화면과 같은 해석). */
function renderCards() {
  const tree = (
    <>
      <ul>
        {noticeRows().map((notice) => (
          <NoticeCard key={notice.noticeNumber} notice={notice} now={new Date(ISO_DEADLINE)} action={null} />
        ))}
        {suggestionRows().map((suggestion) => (
          <SuggestionCard
            key={`s-${suggestion.noticeNumber}`}
            suggestion={suggestion}
            now={new Date(ISO_DEADLINE)}
            action={null}
          />
        ))}
        <BidBoxCard bidCase={bidCaseRow()} onSend={() => undefined} />
      </ul>
      <ResultCardHarness />
      <WorksheetTimeline entries={timelineRows()} />
    </>
  );
  const router = createMemoryRouter([{ path: '/', element: tree }], { initialEntries: ['/'] });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

/**
 * **게이트가 마운트하는 카드 전수**(r1 v M-1 · cr H-1 ③). 손목록이지만 **비어 가는 것을 막는
 * 장치가 있다** — `scripts/screen-card-coverage.test.mjs` 가 `src/screens/**` 에서 표시 어휘를
 * 쓰는 내보낸 카드를 AST 로 모아 이 목록과 **집합 등식**을 건다. 새 카드가 생기면 그 게이트가
 * 먼저 붉어진다.
 */
export const GATE_MOUNTED_CARDS: readonly string[] = [
  'NoticeCard',
  'SuggestionCard',
  'BidBoxCard',
  'WorksheetTimeline',
  'ResultCard',
];

/** 화면 E 카드는 질의 결과를 받으므로 적재가 끝나기를 기다린 뒤 글자를 읽는다. */
async function renderAll() {
  const view = renderCards();
  await waitFor(() => expect(screen.queryByTestId('gate-opening-pending')).toBeNull());
  return view;
}

/**
 * 토큰이 **홀로 선 자리**만 센다. 단순 포함으로 재면 세 글자 토큰 `BID` 가 다른 식별자의 조각에
 * 걸린다 — 이 게이트의 첫 실행이 타임라인의 `ADD_TO_BID_BOX`(**명령 토큰**, 이 slice 의 축이
 * 아니라 알려진 제한에 등재)에서 그것을 보였다. 그래서 양옆이 식별자 글자가 아닌 경우만 센다.
 */
export function standaloneOccurrences(text: string, token: string): number {
  const matches = text.match(new RegExp(`(?<![A-Z0-9_])${token}(?![A-Z0-9_])`, 'g'));
  return matches === null ? 0 : matches.length;
}

describe('홀로 선 토큰만 센다 — 술어 자신의 대조', () => {
  it('홀로 선 토큰은 센다', () => {
    expect(standaloneOccurrences('기초금액 · BID · 공고 게시값', 'BID')).toBe(1);
    expect(standaloneOccurrences('BID', 'BID')).toBe(1);
  });

  it('다른 식별자의 조각은 세지 않는다', () => {
    expect(standaloneOccurrences('ADD_TO_BID_BOX', 'BID')).toBe(0);
    expect(standaloneOccurrences('BIDDING', 'BID')).toBe(0);
  });
});

describe('화면이 그린 글자에 금액 토큰 원문이 없다 (D-4)', () => {
  it('네 표의 키 전수가 DOM 에 0 번 나온다', async () => {
    const { container } = await renderAll();
    const text = container.textContent ?? '';

    expect(MONEY_TOKENS.length).toBeGreaterThan(0);
    for (const token of MONEY_TOKENS) {
      expect(standaloneOccurrences(text, token), `${token} 이 화면에 그대로 그려졌다`).toBe(0);
    }
  });

  /**
   * **음성 대조** — 토큰이 사라진 것이 「아무것도 안 그렸다」가 아님을 잰다. 금지 단언만 두면
   * 카드를 빈 채로 그려도 초록이다.
   */
  it('같은 자리에 표의 문면이 그려져 있다', async () => {
    const { container } = await renderAll();
    const text = container.textContent ?? '';

    for (const basis of BASIS_VALUES) {
      expect(text, `${basis} 의 문면이 없다`).toContain(AMOUNT_BASIS_DISPLAY[basis].label);
    }
    for (const provenance of PROVENANCE_VALUES) {
      expect(text, `${provenance} 의 문면이 없다`).toContain(AMOUNT_PROVENANCE_DISPLAY[provenance].label);
    }
    for (const vat of VAT_VALUES) {
      expect(text, `${vat} 의 문면이 없다`).toContain(VAT_TREATMENT_DISPLAY[vat].label);
    }
  });
});

describe('화면이 그린 글자에 시각 원문이 없다 (D-4)', () => {
  it('fixture 의 ISO 원문이 DOM 글자에 0 번 나온다', async () => {
    const { container } = await renderAll();
    const text = container.textContent ?? '';

    for (const raw of RAW_INSTANTS) {
      expect(text, `${raw} 이 화면에 그대로 그려졌다`).not.toContain(raw);
    }
  });

  it('같은 자리에 KST 표기가 그려져 있다', async () => {
    const { container } = await renderAll();
    const text = container.textContent ?? '';

    expect(text).toContain(KST_CHECKPOINT);
    expect(text).toContain(KST_OCCURRED);
    // 화면 E 의 여섯 칸도 같은 모듈을 지난다(r1 v M-1 — 앞 판은 이 카드가 마운트되지 않았다).
    expect(text).toContain(KST_OPENED);
    // 공고 카드의 마감 칸은 **D-day 만** 그린다(요구표 ⓐ 행) — 절대 시각을 함께 그리는 자리는
    // 워크시트이고 그 표기는 `WorksheetScreen.test.tsx` 가 잰다.
    expect(text).toContain('D-0');
  });

  /**
   * **기계 가독 칸은 원문을 든다** — 그것이 `<time>` 의 일이다. 금지는 **보이는 글자**에
   * 걸리고 속성에 걸리지 않는다는 것을 이 자리에서 잰다(다음 라운드가 속성까지 지우지 않게).
   */
  /**
   * **읽히지 않는 값의 얼굴**(r1 CR LOW) — 순수 함수의 세 갈래는 전수인데 컴포넌트의 세 얼굴은
   * 둘만 그려지고 있었다. D-1 이 「값이 보이게 — 조용한 빈칸 금지」를 요구하는 쪽이 그 분기다.
   */
  it('읽히지 않는 값은 보이는 표지로 서고 기계 칸에 실리지 않는다', async () => {
    const { container } = await renderAll();
    const text = container.textContent ?? '';

    expect(text).toContain('알 수 없음');
    expect(text, '읽지 못한 원문이 화면에 섰다').not.toContain(UNREADABLE_AT);
    const machine = [...container.querySelectorAll('time')].map((one) => one.getAttribute('datetime'));
    expect(machine, '읽지 못한 값이 기계 칸에 실렸다').not.toContain(UNREADABLE_AT);
  });

  it('기계 가독 칸에는 원문이 남는다', async () => {
    const { container } = await renderAll();
    const machine = [...container.querySelectorAll('time')].map((one) => one.getAttribute('datetime'));

    expect(machine).toContain(ISO_CHECKPOINT);
    expect(machine).toContain(ISO_OCCURRED);
  });
});
