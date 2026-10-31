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
 * **알림함에는 항목이 선다**(M7/7C③-b · D-7C3B-10): 트리거 배선이 들어와 투찰가 요청(여정 ③)과
 * 결과 전이(이 여정)가 알림함에 기록된다. 우회 fixture 로 항목을 심지 않는 규율은 그대로이고,
 * 항목은 **앞선 여정들이 실제로 한 일**에서 온다 — 그래서 여기서 재는 것은 「화면이 보이는 것이
 * 같은 실행의 서버 응답과 같은가」다(개수도 종류도 응답에서 온다).
 */

const NOTICE = 'SYN-7E3-RESULT';

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

type InboxItem = {
  readonly kind: string;
  readonly noticeNumber: string;
  readonly noticeRound: string;
  readonly read: boolean;
};

test('알림함 — 앞선 여정이 실제로 만든 항목을 서버 값 그대로 보인다', async ({ page }) => {
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
  const items = (await response.json()) as readonly InboxItem[];
  const unreadHeader = response.headers()['x-unread-count'];

  /**
   * **트리거가 실제로 발화한다**(M7/7C③-b). 앞선 여정들이 한 일이 그대로 항목이 된다 —
   * 투찰가 요청(여정 ③)과 개찰·결과 기록(여정 ⑤)이다. 우회 fixture 로 심은 것이 하나도 없다.
   *
   * **종류의 포함으로 단언한다**(전체 집합이 아니라) — 여정이 늘면 종류가 늘고, 그때 이
   * 단언이 「여정을 더했다」는 이유로 붉어지는 것은 이 자리가 재려는 것이 아니다. 개수의
   * 정합은 아래 DOM ↔ 응답 등식이 진다.
   */
  const kinds = new Set(items.map((one) => one.kind));
  expect(items.length, '트리거가 하나도 발화하지 않았다 — 배선이 끊겼다').toBeGreaterThan(0);
  expect(kinds, '여정 ③ 의 투찰가 요청이 알림함에 남지 않았다').toContain('BID_PRICE_REQUEST_RECORDED');
  expect(kinds, '여정 ⑤ 의 개찰 기록이 알림함에 남지 않았다').toContain('OPENING_COMPLETED');
  expect(kinds, '여정 ⑤ 의 결과 확정이 알림함에 남지 않았다').toContain('AWARD_DECIDED');

  // 개찰·결과 항목은 **그 건의 공고**를 싣는다 — 눌러서 워크시트로 갈 수 있는 값이다.
  const mine = items.filter((one) => one.noticeNumber === NOTICE);
  expect(mine.map((one) => one.kind).sort()).toStrictEqual(['AWARD_DECIDED', 'OPENING_COMPLETED']);

  // **빈 얼굴이 아니다** — 양성 표지(행 하나)를 먼저 기다린 뒤 개수를 센다(7E-2b r1 교훈).
  const rows = page.getByTestId(/^notification-/u);
  await expect(rows.first()).toBeVisible();
  await expect(page.getByTestId('inbox-empty')).toHaveCount(0);

  // **개수는 응답에서 온다** — 화면이 센 수가 아니라 서버가 보낸 항목 수와 같아야 한다.
  await expect(rows).toHaveCount(items.length);
  for (const item of mine) {
    await expect(
      page.getByTestId(`notification-${item.kind}-${item.noticeNumber}-${item.noticeRound}`),
    ).toBeVisible();
  }

  // 배지는 **헤더에서만** 온다(본문 행을 세지 않는다).
  const unread = items.filter((one) => !one.read).length;
  expect(unreadHeader, '안읽음 전수는 헤더가 나른다').toBe(String(unread));
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
