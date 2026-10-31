import { expect, test, type Page } from '@playwright/test';

import {
  ABSENT,
  assertOnlyAllowedOperations,
  CASE_STATE_LABEL,
  CASE_TAB_LABEL,
  CHECKPOINT_LABEL,
  driveToRecorded,
  localDateTimeInput,
  signIn,
  watchRequests,
  type BidCaseRow,
} from './shared';

/**
 * 여정 ⑤ 의 e2e(D-7E3a-9) — 「제출함」 기록된 건을 **결과가 확정될 때까지** 밀고, 화면 E 가
 * 보이는 값이 **같은 실행의 API 응답**과 같은지 본다.
 *
 * **자기 공고를 쓴다**(`SYN-7E3-RESULT`): 결과 단계 전이는 되돌릴 수 없어(착수 실측 — `WON`
 * 에서 `await-opening`·`skip` 둘 다 409) 남의 여정의 건을 밀면 그 출발 단언이 영구히 깨진다.
 * 순서 독립은 되돌림이 아니라 **격리**로 선다.
 *
 * **알림함은 빈 얼굴로 지난다**(D-7E3a-6): 알림 트리거 여섯은 출하 조립에서 발화하지 않는다
 * (쓰기 포트의 빈이 없다 — 착수 실측). 우회 fixture 로 항목을 심지 않으므로 여기서 재는 것은
 * 「빈 얼굴과 배지 0 이 서버 값에서 나온다」까지다.
 */

const NOTICE = 'SYN-7E3-RESULT';

/**
 * 결과 조회 응답에서 **이 여정이 대조하는 칸만** 적는다(M7/7E-3b). 칸 전수는 vitest 가
 * `satisfies` 로 잠그고, 여기서 재는 것은 「DOM 이 **같은 실행의 응답**과 같은가」다.
 */
interface MyBidResultsBody {
  readonly cases: readonly {
    readonly caseId: number;
    readonly submittedAmountWon: number;
    readonly submittedAmountCurrency: string;
    readonly submittedAmountVatTreatment: string;
    readonly submittedAmountOrigin: string;
    readonly reconciledOutcome: string;
    readonly reconciliationBlocker: string | null;
    readonly outcomeAgreement: string;
    readonly gapState: string;
  }[];
  readonly aggregate: {
    readonly sampleSize: number;
    readonly recordedRateState: string;
    readonly recordedAwardRate: string | null;
    readonly recordedWonCount: number;
    readonly recordedDenominator: number;
    readonly reconciledRateState: string;
    readonly reconciledAwardRate: string | null;
    readonly reconciliationBlockers: readonly string[];
  };
}

/**
 * 계약 토큰 → 화면 문면. **화면의 표를 import 하지 않는다**(`CASE_TAB_LABEL` 과 같은 사유) —
 * 같은 출처에서 양쪽을 끌어오면 표가 틀려도 대조가 초록이다.
 */
const RECONCILED_OUTCOME_LABEL: Readonly<Record<string, string>> = {
  WON: '낙찰(대사)',
  LOST: '패찰(대사)',
  FAILED_BID: '유찰',
  REBID: '재입찰',
  UNDECIDABLE: '아직 판정 못 함',
};
const BLOCKER_LABEL: Readonly<Record<string, string>> = {
  NOTICE_ABSENT: '공고를 모름',
  NOT_OPENED_YET: '아직 개찰 전',
  NOT_COLLECTED: '개찰 자료 없음',
  OBSERVED_NOT_PROMOTED: '원문만 적재됨',
  PROGRESS_UNRECOGNIZED: '진행구분 미등재',
  PROGRESS_ABSENT: '진행구분 없음',
  OPERATOR_NAME_NOT_DECLARED: '내 상호 미설정',
  OPERATOR_NAME_NORMALIZES_TO_EMPTY: '내 상호가 빈 값',
  WINNER_NAME_NOT_PUBLISHED: '낙찰자 상호 미공개',
  WINNER_NAME_NORMALIZES_TO_EMPTY: '낙찰자 상호가 빈 값',
};
const AGREEMENT_LABEL: Readonly<Record<string, string>> = {
  AGREE: '내 기록과 같음',
  DISAGREE: '내 기록과 다름',
  NOT_COMPARABLE: '견줄 수 없음',
};
const GAP_STATE_LABEL: Readonly<Record<string, string>> = {
  COMPUTED: '차이 있음',
  AWARD_ABSENT: '확정 낙찰가가 없어 뺄 수 없음',
};
const ORIGIN_LABEL: Readonly<Record<string, string>> = {
  OPERATOR_ENTERED: '직접 입력',
  PRICE_MENU: '추천 메뉴에서 고름',
};
const VAT_LABEL: Readonly<Record<string, string>> = {
  INCLUSIVE: '부가세 포함',
  EXCLUSIVE: '부가세 별도',
  UNKNOWN: '과세 구분 모름',
};
const RATE_STATE_LABEL: Readonly<Record<string, string>> = {
  MEASURED: '잰 값',
  ABSENT_NO_RECORDED_BIDS: '셀 것이 없음',
  ABSENT_NO_DECIDED_CASES: '분모에 드는 건이 없음',
};

/** 자릿점·통화를 걷고 **숫자만** 남긴다 — spec 이 화면의 서식 함수를 복제하지 않으려는 것이다. */
function digitsOf(text: string): string {
  return text.replace(/[^\d-]/gu, '');
}

/**
 * 결과 단계의 전이 하나 — **화면 D 에서** 보낸다(E 는 읽기 화면이라 전이를 내지 않는다).
 *
 * **전역 탐색으로 들어가므로 탭을 먼저 고른다**: 그 입구는 어느 건을 보러 가는지 모르고, D 는
 * 고른 탭의 건만 그린다(그것이 탭의 뜻이다). 화면 E 의 「다음 행동」 링크는 **그 건의 탭을
 * 싣고**(r1 M-2) 그 착지는 위 여정이 탭을 고르지 않고 단언한다 — 둘은 다른 입구다.
 */
async function transitionInBidBox(
  page: Page,
  caseId: number,
  fromState: string,
  button: RegExp,
  fill: (card: ReturnType<Page['getByTestId']>) => Promise<void>,
  urlPart: string,
): Promise<BidCaseRow> {
  await page.getByRole('link', { name: 'D 내 입찰함' }).click();
  await expect(page.getByTestId('screen-bid-box')).toBeVisible();

  const tab = CASE_TAB_LABEL[fromState];
  expect(tab, `상태 ${fromState} 의 탭 문면이 표에 없다`).toBeDefined();
  await page.getByRole('tab', { name: new RegExp(tab!, 'u') }).click();

  // 카드는 **자기 건으로 좁힌다** — 한 하네스를 공유하므로 같은 탭에 남의 건이 앉아 있다.
  const card = page.getByTestId(`bid-case-${caseId}`);
  await expect(card).toBeVisible();
  await card.scrollIntoViewIfNeeded();
  await card.getByRole('button', { name: button }).click();
  await fill(card);

  // **내가 부른 응답을 집는다**(7E-2b r3 교훈): 경로만 보면 앞 화면 전환의 늦은 응답이 걸린다.
  const sent = page.waitForResponse(
    (one) =>
      one.url().includes(`/api/bid-cases/${caseId}/${urlPart}`) && one.request().method() === 'POST',
  );
  await card.getByRole('button', { name: '보내기' }).click();
  const response = await sent;
  expect(response.status(), `${urlPart} 전이`).toBe(200);
  return (await response.json()) as BidCaseRow;
}

test('여정 ⑤ — 제출함 → 개찰 대기 → 개찰 → 심사 → 확정, 그리고 화면 E 가 그것을 보인다', async ({
  page,
}) => {
  const watched = watchRequests(page);
  await signIn(page);

  const recorded = await driveToRecorded(page, NOTICE);
  /**
   * **조건부 skip 을 두지 않는다**(v H-1 · CR H-1). `driveToRecorded` 가 `null` 을 내는 경우는
   * 그 공고의 건이 `WATCHING` 이 아닐 때 하나뿐이고, 하네스는 **실행마다 볼륨 없는 새 Postgres**
   * 를 띄우며(`docker run --rm`) 설정이 `reuseExistingServer: false` · `retries: 0` 이다 —
   * 정상 실행에서 그 조건은 설 수 없다. 그래서 `null` 은 「지역 재실행」이 아니라 **회귀나 오염**
   * 이고, skip 으로 받으면 여정 ⑤ 의 단언이 **전부 사라진 채 CI 가 초록**이다(verifier 재현:
   * 오염 spec 을 앞에 두면 `1 skipped · 2 passed · exit 0`).
   */
  expect(
    recorded,
    '여정 ⑤ 의 건이 WATCHING 에서 출발하지 않았다 — 하네스는 실행마다 버릴 DB 이므로 회귀나 오염이다',
  ).not.toBeNull();
  expect(recorded!.state).toBe('BID_RECORDED');
  const caseId = recorded!.caseId;

  // ── 결과 단계를 끝까지 민다(전이는 전부 화면 D) ────────────────────────────
  const awaiting = await transitionInBidBox(
    page,
    caseId,
    recorded!.state,
    /개찰 대기로/u,
    async (card) => card.getByLabel('개찰 일시').fill(localDateTimeInput(3)),
    'await-opening',
  );
  expect(awaiting.state).toBe('AWAITING_OPENING');
  expect(awaiting.nextCheckpointKind, '그 시각이 다음 확인 시점이 된다(계약)').toBe('OPENING');

  const opened = await transitionInBidBox(
    page,
    caseId,
    awaiting.state,
    /개찰 결과 기록/u,
    async (card) => card.getByRole('radio', { name: CASE_STATE_LABEL.OPENED_PROVISIONAL_FIRST! }).check(),
    'opening',
  );
  expect(opened.state).toBe('OPENED_PROVISIONAL_FIRST');

  // ── 화면 E 가 **잠정 1위 단계**를 보인다 ───────────────────────────────────
  //
  // 목록 응답을 **내가 연 화면의 것으로** 집는다(7E-2b r3 교훈).
  const listed = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  const openingRead = page.waitForResponse(
    (one) =>
      one.url().includes('/api/opening-results') &&
      new URL(one.url()).searchParams.get('noticeNumber') === NOTICE &&
      one.status() === 200,
  );
  await page.getByRole('link', { name: 'E 결과 확인' }).click();
  await expect(page.getByTestId('screen-results')).toBeVisible();

  const rows = (await (await listed).json()) as BidCaseRow[];
  const mine = rows.find((row) => row.caseId === caseId)!;
  expect(mine.state, '화면이 읽은 목록에 내 건이 그 상태로 있다').toBe('OPENED_PROVISIONAL_FIRST');
  /**
   * **출처를 둘로 둔다**(r1 OQ-2). 이 목록 대기는 경로·상태만 보므로 직전 화면 D 의 무효화
   * 재조회를 집을 수 있다(같은 query key). 술어를 「이 클릭 뒤에 뜬 요청」으로 조이면 react-query
   * 의 중복 제거 때문에 **E 가 새 요청을 내지 않는 실행에서 영영 기다린다** — 조이는 쪽이 흔들린다.
   * 대신 전이 응답(`opened`)과 **같은 값인지**를 함께 단언한다: 어느 응답을 집었든 그 둘이
   * 어긋나면 붉다.
   */
  expect(mine.state, '목록과 전이 응답이 어긋난다').toBe(opened.state);
  expect(mine.nextCheckpointKind).toBe(opened.nextCheckpointKind);
  expect(mine.caseVersion).toBe(opened.caseVersion);

  const card = page.getByTestId(`result-case-${caseId}`);
  await expect(card).toBeVisible();
  await expect(card.getByTestId(`result-state-${caseId}`)).toHaveText(
    CASE_STATE_LABEL[mine.state] ?? mine.state,
  );
  // 「없음」은 **답**이다 — 잠정 1위 단계의 다음 확인 시점이 그것이다(착수 실측).
  await expect(card.getByTestId(`result-checkpoint-kind-${caseId}`)).toHaveText(
    CHECKPOINT_LABEL[mine.nextCheckpointKind] ?? mine.nextCheckpointKind,
  );
  if (mine.nextCheckpointAt === null) {
    await expect(card.getByTestId(`result-checkpoint-at-${caseId}`)).toHaveText(ABSENT);
  } else {
    await expect(card.getByTestId(`result-checkpoint-at-${caseId}`)).toHaveText(mine.nextCheckpointAt);
  }

  // 개찰 결과 칸 — **같은 실행의 응답과 대조**한다. 하네스가 개찰 축을 수집하지 않으므로
  // 오늘의 답은 `NOT_OPENED_YET` 이고(D-7E3a-7) 그 사실을 응답에서 읽어 확인한다.
  const openingBody = (await (await openingRead).json()) as { readonly state: string };
  expect(openingBody.state, '하네스에서는 개찰 축이 수집되지 않는다(착수 실측)').toBe('NOT_OPENED_YET');
  await expect(card.getByTestId(`opening-state-${caseId}`)).toContainText('아직 개찰 전');

  // 건 상태가 「잠정 1위」인데 **개찰 결과 축은 여전히 미개찰**이다 — 두 축이 다르다는 사실이
  // 화면에서도 갈려 보여야 한다(운영자 기록 ≠ 수집된 개찰 결과).
  await expect(card.getByTestId(`confirmed-award-${caseId}`)).toHaveText(ABSENT);
  await expect(card.getByTestId(`provisional-award-${caseId}`)).toHaveText(ABSENT);

  // E 는 읽기 화면이다 — 전이 버튼이 **없다**(D-7E3a-8).
  await expect(card.getByRole('button', { name: /기록|대기로|심사/u })).toHaveCount(0);

  /**
   * **「다음 행동」 링크가 그 건이 보이는 자리로 보낸다**(r1 M-2 · D-7E3a-8). 탭을 **고르지
   * 않고** 착지해서 바로 보이는지를 잰다 — 앞 판은 맨 `/bids` 라 첫 탭(관심)이 열렸고 결과
   * 단계 건은 DOM 에 없었다. 이 단언이 그 자리를 지킨다.
   */
  await card.getByRole('link', { name: /입찰함/u }).click();
  await expect(page.getByTestId('screen-bid-box')).toBeVisible();
  await expect(
    page.getByTestId(`bid-case-${caseId}`),
    'E 의 링크가 그 건이 보이지 않는 탭으로 보냈다',
  ).toBeVisible();
  await expect(page.getByRole('tab', { selected: true })).toContainText(CASE_TAB_LABEL[opened.state]!);

  // ── 심사 → 확정 ───────────────────────────────────────────────────────────
  const review = await transitionInBidBox(
    page,
    caseId,
    opened.state,
    /적격심사 중으로/u,
    async (one) => one.getByLabel('서류 기한').fill(localDateTimeInput(5)),
    'qualification-review',
  );
  expect(review.state).toBe('UNDER_QUALIFICATION_REVIEW');
  expect(review.nextCheckpointKind, '서류 기한이 다음 확인 시점이 된다').toBe('DOCUMENT_DUE');

  const decided = await transitionInBidBox(
    page,
    caseId,
    review.state,
    /결과 기록/u,
    async (one) => one.getByRole('radio', { name: CASE_STATE_LABEL.WON! }).check(),
    'outcome',
  );
  expect(decided.state).toBe('WON');

  // 확정 뒤 화면 E — 상태와 「없음」이 그 응답의 값이다.
  const relisted = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  /**
   * 대사·집계의 출처(M7/7E-3b). 이 화면은 질의가 **신선도 0** 이라 들를 때마다 다시 부른다 —
   * 이 클릭이 낳는 응답을 집는다. 앞 방문의 응답은 이미 끝났으므로 여기 걸리지 않는다.
   */
  const resultsRead = page.waitForResponse(
    (one) =>
      one.url().includes('/api/bid-results') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: 'E 결과 확인' }).click();
  const finalRows = (await (await relisted).json()) as BidCaseRow[];
  const settled = finalRows.find((row) => row.caseId === caseId)!;
  expect(settled.state).toBe('WON');

  const finalCard = page.getByTestId(`result-case-${caseId}`);
  await expect(finalCard.getByTestId(`result-state-${caseId}`)).toHaveText(
    CASE_STATE_LABEL[settled.state] ?? settled.state,
  );
  await expect(finalCard.getByTestId(`result-checkpoint-kind-${caseId}`)).toHaveText(
    CHECKPOINT_LABEL.NONE!,
  );

  // ── 대사 절 — DOM 이 **같은 실행의 결과 조회 응답**과 같다(D-7E3b-4) ───────
  const results = (await (await resultsRead).json()) as MyBidResultsBody;
  const mineResult = results.cases.find((one) => one.caseId === caseId);
  expect(mineResult, '결과 조회에 여정 ⑤ 의 건이 없다 — 투찰 기록이 있는 건은 여기 선다').toBeDefined();

  // 금액은 **숫자만** 맞대되 **정확히 같아야** 한다(v r1 L-8) — `toContain` 이면 앞뒤에 숫자가
  // 더 붙어도 지나간다(자릿점을 걷은 뒤라 부분 일치가 쉽게 성립한다).
  expect(digitsOf(await finalCard.getByTestId(`submitted-amount-${caseId}`).innerText())).toBe(
    String(mineResult!.submittedAmountWon),
  );
  await expect(finalCard.getByTestId(`submitted-amount-${caseId}`)).toContainText(
    mineResult!.submittedAmountCurrency,
  );
  // 과세는 **표의 문면**으로 선다(r1 CR M-1) — 원문 토큰이 아니다.
  await expect(finalCard.getByTestId(`submitted-amount-${caseId}`)).toContainText(
    VAT_LABEL[mineResult!.submittedAmountVatTreatment]!,
  );

  // 「고른 안」은 빈칸이 아니라 **출처 상태**다(오늘의 답은 「직접 입력」 — 메뉴가 출하 경로에 없다).
  await expect(finalCard.getByTestId(`chosen-option-${caseId}`)).toContainText(
    ORIGIN_LABEL[mineResult!.submittedAmountOrigin]!,
  );
  await expect(finalCard.getByTestId(`award-gap-${caseId}`)).toContainText(
    GAP_STATE_LABEL[mineResult!.gapState]!,
  );

  /**
   * **판정 불가는 패찰이 아니다.** 운영자는 방금 「낙찰」을 적었는데 대사 축은 아직 판정을 내지
   * 못한다 — 하네스가 개찰 축을 수집하지 않기 때문이다(착수 실측). 두 축이 **다른 칸**으로
   * 서 있는 것이 이 단언의 요점이다.
   */
  await expect(finalCard.getByTestId(`reconciled-outcome-${caseId}`)).toHaveText(
    RECONCILED_OUTCOME_LABEL[mineResult!.reconciledOutcome]!,
  );
  expect(mineResult!.reconciliationBlocker, '판정 불가인데 사유가 비었다').not.toBeNull();
  await expect(finalCard.getByTestId(`reconciliation-blocker-${caseId}`)).toHaveText(
    BLOCKER_LABEL[mineResult!.reconciliationBlocker!]!,
  );
  await expect(finalCard.getByTestId(`outcome-agreement-${caseId}`)).toHaveText(
    AGREEMENT_LABEL[mineResult!.outcomeAgreement]!,
  );

  // ── 집계 두 축 ──────────────────────────────────────────────────────────
  //
  // **계수를 못박지 않는다**: 집계는 계정 전역이고 여정 ③④ 도 같은 하네스에 투찰을 기록한다
  // (실행 차례가 바뀌면 수가 달라진다). 내 것인 주장은 ⓐ DOM == 이 응답 ⓑ 분모 ≥ 1 이다.
  const aggregate = page.getByTestId('results-aggregate');
  await expect(aggregate).toBeVisible();
  await expect(aggregate.getByTestId('sample-size')).toHaveText(String(results.aggregate.sampleSize));

  expect(results.aggregate.recordedRateState, '낙찰을 적었으니 운영자 기록 축은 잰 값이다').toBe('MEASURED');
  expect(results.aggregate.recordedWonCount).toBeGreaterThanOrEqual(1);
  expect(results.aggregate.recordedDenominator).toBeGreaterThanOrEqual(1);
  await expect(aggregate.getByTestId('rate-value-recorded')).toHaveText(results.aggregate.recordedAwardRate!);
  await expect(aggregate.getByTestId('rate-won-recorded')).toHaveText(
    String(results.aggregate.recordedWonCount),
  );
  await expect(aggregate.getByTestId('rate-denominator-recorded')).toHaveText(
    String(results.aggregate.recordedDenominator),
  );

  /**
   * 대사 축은 **부재 + 사유**다. 비율 자리를 「없다」로 재는 이유: 문면 대조는 `0` 을 그리는
   * 변이를 지나보낸다(「0」도 문면이다). 사유는 서버가 준 전수를 그대로 편다 — 특정 사유를
   * 전제하지 않는다(하네스는 `NOT_OPENED_YET`, 운영 환경은 다를 수 있다).
   */
  expect(results.aggregate.reconciledAwardRate, '하네스에서 대사 축이 잰 값이 됐다').toBeNull();
  await expect(aggregate.getByTestId('rate-value-reconciled')).toHaveCount(0);
  await expect(aggregate.getByTestId('rate-state-reconciled')).toHaveText(
    RATE_STATE_LABEL[results.aggregate.reconciledRateState]!,
  );
  expect(results.aggregate.reconciliationBlockers.length, '분모 0 인데 사유가 없다').toBeGreaterThanOrEqual(1);
  for (const blocker of results.aggregate.reconciliationBlockers) {
    await expect(aggregate.getByTestId(`blocker-${blocker}`)).toContainText(BLOCKER_LABEL[blocker]!);
  }

  // 집계 어디에도 퍼센트가 없다 — 화면이 비율을 환산하지 않는다(D-7E3b-8).
  await expect(aggregate).not.toContainText('%');

  // 남은 빈 자리가 **입력칸·버튼 없이** 서 있다(D-7E3a-3). 「준비 중」이던 다섯은 위 절이 받았다.
  const pending = page.getByTestId('results-pending');
  await expect(pending).toBeVisible();
  await expect(pending.getByRole('button')).toHaveCount(0);
  await expect(pending.getByRole('textbox')).toHaveCount(0);
  await expect(page.getByTestId('pending-win-rate'), '낙찰률이 아직 빈 자리로 있다').toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

test('알림함 — 트리거가 발화하지 않는 오늘의 사실을 서버 값으로 보인다', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  // **내가 연 화면의 응답을 집는다** — 그 응답의 헤더가 배지의 출처다.
  const listed = page.waitForResponse(
    (one) =>
      one.url().includes('/api/notifications') && one.request().method() === 'GET' && one.status() === 200,
  );
  await page.getByRole('link', { name: '알림함' }).click();
  await expect(page.getByTestId('screen-inbox')).toBeVisible();

  const response = await listed;
  const items = (await response.json()) as readonly unknown[];
  const unreadHeader = response.headers()['x-unread-count'];

  // 착수 실측 그대로다 — 쓰기 포트의 빈이 출하 조립에 없어 어떤 트리거도 항목을 만들지 않는다.
  expect(items, '알림 트리거가 발화하면 이 단언이 먼저 깨진다(그때 여정을 넓힌다)').toStrictEqual([]);
  expect(unreadHeader, '안읽음 전수는 헤더가 나른다').toBe('0');

  // **양성 표지를 먼저 기다린 뒤** 빈 얼굴을 단언한다(7E-2b r1 교훈) — 적재 중에도 항목이
  // 없으므로, 기다리지 않으면 「아직 안 왔다」를 「비었다」로 읽는다.
  await expect(page.getByTestId('inbox-empty')).toBeVisible();
  await expect(page.getByTestId('inbox-unread-badge')).toHaveText(`안읽음 ${unreadHeader}`);

  // 채널 복제는 빈 자리다(R-4) — 입력칸·버튼을 만들지 않는다.
  const channel = page.getByTestId('pending-notification-channel');
  await expect(channel).toContainText('준비 중');
  await expect(channel.getByRole('button')).toHaveCount(0);
  await expect(channel.getByRole('checkbox')).toHaveCount(0);

  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin).toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});
