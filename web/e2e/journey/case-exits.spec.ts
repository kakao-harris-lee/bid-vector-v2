import { expect, test, type Locator, type Page } from '@playwright/test';

import {
  assertOnlyAllowedOperations,
  CASE_STATE_LABEL,
  ensureInBidBox,
  localDateTimeInput,
  search,
  signIn,
  watchRequests,
  type BidCaseRow,
} from './shared';

/**
 * **건을 내려놓는 여정 둘**(M13/13D) — 화면이 「나중에」를 처음 만드는 길이다.
 *
 * ① 나중에 → 실행 취소 → 원상태 ② 나중에 → 다시 보기.
 *
 * 둘 다 **관심으로 되돌려** 끝난다. 한 하네스를 공유하므로 건의 상태가 전역이고(설정의
 * `workers: 1`), 되돌리지 않으면 뒤에 도는 여정·게이트가 내가 남긴 상태를 본다. 그래서 전역
 * 상태를 **되돌림까지** 단언한다.
 *
 * **자기 공고를 쓴다**(`SYN-13D-EXITS`) — 마감이 남은 다른 공고 다섯은 모두 다른 여정·게이트의
 * 자기 건이고, 마감이 지난 공고는 담는 순간 「마감 지남」이라 이 전이가 409 다.
 *
 * **「건너뜀」은 여정에 없다.** 유일한 종단이라 한 번 보내면 그 건이 영구히 그 상태이고, 같은 DB
 * 에 두 번 돌리는 지역 실행에서 이 파일이 설 수 없게 된다(여정 ⑤ 가 같은 이유로 공고를 가른다).
 * 그 축은 확인 단계·사유 거르기와 함께 vitest 가 든다 — 알려진 제한에 적었다.
 *
 * 단언의 규율은 이 레인의 것 그대로다: **DOM 에서 읽은 값을 같은 실행의 API 응답과 대조한다.**
 * 기대 상태를 spec 에 적지 않는다.
 */
const OWN_NOTICE = 'SYN-13D-EXITS';

/** 내 건을 입찰함에 세우고 그 카드와 건을 돌려준다 — 두 여정이 같은 걸음으로 출발한다. */
async function openOwnCase(page: Page): Promise<{ readonly row: BidCaseRow; readonly card: Locator }> {
  const rows = await search(page, OWN_NOTICE);
  expect(rows.map((one) => one.noticeNumber), `검색이 ${OWN_NOTICE} 를 내지 않았다`).toContain(OWN_NOTICE);
  await ensureInBidBox(page, page.getByTestId(`notice-card-${OWN_NOTICE}`));

  // 셸이 자리 표에서 만든 링크로 간다 — 주소를 손으로 적지 않는다. 이름은 **안정된 쪽**으로만
  // 고른다(머리 글자는 다른 slice 가 걷을 수 있는 문면이다).
  //
  // **이 화면 전체 locator 가 하나로 풀리는지 먼저 센다**(13A 레인이 밟은 자리): 이름 일치는
  // 부분 문자열이고 셸이 탐색을 둘 그리므로, 라벨이 짧아지면 같은 이름이 링크 둘에 걸려
  // strict mode 로 죽는다. 그 실패는 vitest 가 초록인 채 **여정에서만** 난다. 계수를 먼저 재면
  // 라벨이 어느 쪽이든 사유가 문면으로 남는다.
  const toBidBox = page.getByRole('link', { name: /내 입찰함/u });
  await expect(toBidBox, '탐색 링크가 하나로 풀리지 않는다 — 라벨 문면이 바뀌었는지 보라').toHaveCount(1);

  const listed = page.waitForResponse(
    (one) => one.url().includes('/api/bid-cases?') && one.request().method() === 'GET' && one.status() === 200,
  );
  await toBidBox.click();
  await expect(page.getByTestId('screen-bid-box')).toBeVisible();

  const cases = (await (await listed).json()) as BidCaseRow[];
  expect(cases.map((one) => one.noticeNumber), '입찰함에 이 여정의 건이 없다').toContain(OWN_NOTICE);
  const row = cases.find((one) => one.noticeNumber === OWN_NOTICE)!;

  // 단언과 클릭을 **내 카드 안으로 좁힌다** — 화면 전체를 가리키면 남의 여정의 건을 집는다.
  return { row, card: page.getByTestId(`bid-case-${row.caseId}`) };
}

/**
 * 그 상태의 문면 — **폴백을 두지 않는다**(이 레인의 규율). `?? state` 로 접으면 표에 없는 상태의
 * 기대값이 **wire 토큰**이 되고, 그 단언은 화면이 그 토큰을 그대로 보일 때 — 즉 **화면이 틀렸을
 * 때** — 맞는다. 표에 없으면 사유를 말하고 멈춘다.
 */
function stateLabel(state: string): string {
  const label = CASE_STATE_LABEL[state];
  expect(label, `${state}: 여정 라벨 표에 그 상태의 문면이 없다`).toBeDefined();
  return label!;
}

/** 그 카드의 상태 배지가 **그 응답이 말한 상태**를 보이는가. */
async function expectState(card: Locator, caseId: number, state: string): Promise<void> {
  await expect(card.getByTestId(`state-${caseId}`)).toHaveText(stateLabel(state));
}

/** 「나중에」를 보낸다 — 리마인드 시각은 **운영자 입력**이고 지금에서 센다(시각 조작 0). */
async function defer(page: Page, card: Locator, caseId: number): Promise<BidCaseRow> {
  await card.getByTestId(`exit-DEFER-${caseId}`).click();
  await card.getByLabel('리마인드 시각').fill(localDateTimeInput(2));
  const sent = page.waitForResponse(
    (one) => one.url().includes('/defer') && one.request().method() === 'POST',
  );
  await card.getByTestId('action-send').click();
  const response = await sent;

  expect(response.status()).toBe(200);
  return (await response.json()) as BidCaseRow;
}

test('여정 — 나중에 → 실행 취소 → 원상태', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const { row, card } = await openOwnCase(page);
  expect(row.state, '이 여정의 출발은 관심이다 — 회귀나 오염이면 여기서 멈춘다').toBe('WATCHING');
  await expectState(card, row.caseId, row.state);

  const deferred = await defer(page, card, row.caseId);

  expect(deferred.state, '서버가 말한 도착 상태').toBe('DEFERRED');
  // 리마인드 시각이 **다음 확인 시점**이 된다(계약) — 「보류」라는 상태가 없는 이유다.
  expect(deferred.nextCheckpointKind).toBe('REMINDER');
  expect(deferred.caseVersion, '쓰기가 버전을 올린다').toBeGreaterThan(row.caseVersion);
  await expectState(card, row.caseId, deferred.state);

  // **실행 취소는 되돌림 전이가 있어서 서 있다**(D-7E-5). 그 되돌림이 `resume` 이다.
  const toast = page.getByTestId('toast');
  await expect(toast).toBeVisible();
  const undone = page.waitForResponse(
    (one) => one.url().includes('/resume') && one.request().method() === 'POST',
  );
  await toast.getByRole('button', { name: '실행 취소' }).click();
  const restored = (await (await undone).json()) as BidCaseRow;

  expect(restored.state, '되돌림이 관심으로 돌린다').toBe('WATCHING');
  await expectState(card, row.caseId, restored.state);
  // **전역 상태를 되돌려 끝낸다** — 뒤에 도는 여정·게이트가 내가 남긴 상태를 보지 않는다.
  expect(restored.caseId).toBe(row.caseId);

  await expect(page.getByTestId('write-failure')).toHaveCount(0);
  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});

test('여정 — 나중에 → 다시 보기', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const { row, card } = await openOwnCase(page);
  expect(row.state, '앞 여정이 건을 관심으로 되돌려 놓는다').toBe('WATCHING');

  const deferred = await defer(page, card, row.caseId);
  expect(deferred.state).toBe('DEFERRED');
  await expectState(card, row.caseId, deferred.state);

  /*
   * 「나중에」 상태의 **다음 행동 하나**가 「다시 보기」다 — 내려놓기 축이 아니라 primary 자리다.
   * 그래서 이 걸음은 토스트의 실행 취소와 **다른 길**이고, 둘이 같은 전이를 부른다는 사실이
   * 이 여정의 요점이다(운영자가 토스트를 닫은 뒤에도 되돌릴 수 있다).
   */
  await page.getByTestId('toast').getByRole('button', { name: '닫기' }).click();
  await expect(page.getByTestId('toast')).toHaveCount(0);

  const resumed = page.waitForResponse(
    (one) => one.url().includes('/resume') && one.request().method() === 'POST',
  );
  await card.getByRole('button', { name: '다시 보기' }).click();
  const back = (await (await resumed).json()) as BidCaseRow;

  expect(back.state).toBe('WATCHING');
  expect(back.caseVersion, '되돌림도 쓰기다 — 버전이 오른다').toBeGreaterThan(deferred.caseVersion);
  await expectState(card, row.caseId, back.state);

  await expect(page.getByTestId('write-failure')).toHaveCount(0);
  expect(watched.outside).toStrictEqual([]);
  expect(watched.unexpectedSameOrigin, '문서·번들 자산 밖의 같은 출처 요청').toStrictEqual([]);
  assertOnlyAllowedOperations(watched.apiPaths);
});
