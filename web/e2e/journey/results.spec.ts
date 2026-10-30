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
 * 결과 단계의 전이 하나 — **화면 D 에서** 보낸다(E 는 읽기 화면이라 전이를 내지 않는다).
 *
 * **탭을 먼저 고른다**: D 는 고른 탭의 건만 그리므로(그것이 탭의 뜻이다) 상태에 맞는 탭을
 * 고르지 않으면 카드가 DOM 에 아예 없다. 그 탭은 **지금 상태**에서 나오고, 화면을 떠났다
 * 돌아오면 첫 탭(관심)으로 돌아가므로 걸음마다 다시 고른다.
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
  // 같은 DB 에 두 번 미는 지역 실행에서는 `null` 이다 — 그때는 이 여정이 잴 것이 없다.
  test.skip(recorded === null, '이 건은 이미 제출 뒤다(지역 재실행) — 결과 단계를 새로 밀 수 없다');
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
  await expect(card.getByRole('link', { name: /입찰함/u })).toHaveAttribute('href', '/bids');

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

  // 빈 자리 일곱이 **입력칸·버튼 없이** 서 있다(D-7E3a-3).
  const pending = page.getByTestId('results-pending');
  await expect(pending).toBeVisible();
  await expect(pending.getByRole('button')).toHaveCount(0);
  await expect(pending.getByRole('textbox')).toHaveCount(0);
  // 집계 자리에 숫자가 없다 — 목록을 세어 낙찰률을 짓지 않는다.
  await expect(page.getByTestId('pending-win-rate')).not.toContainText('%');

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
