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

/**
 * **다시 연 워크시트의 빈 자리가 사유를 든다**(M14/14F · v H-1 high).
 *
 * 받은 투찰가 응답은 **그것을 받은 화면에서만** 산다 — 계약에 다시 읽는 operation 이 없고 상태를
 * 민 뒤에는 요청 경로도 없다. 앞 판은 그 자리를 **빈칸으로 접었고**, 운영자는 「하한 대비 여유
 * 표시가 없다」를 「문제없다」로 읽는다(해를 입는 자리가 투찰 금액이다).
 *
 * **왜 「나중에」로 재는가**: 이 여정이 **이미 만들고 되돌리는** 상태라 새 fixture 도 남는 상태
 * 변화도 없다. 그 상태에서 재는 것은 **자격 자리가 사유를 들고 그 사유가 다음 걸음을 말한다**는
 * 것이다 — 「다시 보기」가 이 화면의 다음 행동이므로 한 번 누르면 요청 경로가 열리고, 그래서
 * 그 자리는 「다시 못 받는다」가 아니라 **그 걸음**을 말해야 한다(r1 cr HIGH 가 바로잡은 자리다).
 *
 * **하한 여유 자리는 이 상태에서 서지 않는다**: 아직 물을 길이 있는 축을 「재지 못함」으로
 * 그리면 화면이 묻지 않은 것에 답한다(13C 의 판단). 그 자리가 **사유를 드는** 갈래(투찰 예정 ·
 * 투찰가 받음)는 상태 전수 × 자리 셋을 단위 레인이 든다 — 그 상태를 여정으로 만들려면 되돌릴
 * 길이 없는 전이를 공유 하네스에 남겨야 해서, 전용 fixture 공고가 필요하다(알려진 제한).
 *
 * **음성 대조가 같은 test 안에 있다**: 되돌린 뒤(관심) 자격 사유의 글자가 **바뀐다** — 되돌림
 * 한 걸음이 사라졌으므로 「다시 보기로 되돌린 뒤」가 빠진다. 더하기만 한 변경이면 그 단언이 붉는다.
 */
test('여정 — 다시 요청할 수 없는 건의 워크시트가 사유를 든다', async ({ page }) => {
  const watched = watchRequests(page);
  await signIn(page);

  const { row, card } = await openOwnCase(page);
  expect(row.state, '앞 여정이 건을 관심으로 되돌려 놓는다').toBe('WATCHING');

  const deferred = await defer(page, card, row.caseId);
  expect(deferred.state).toBe('DEFERRED');

  /*
   * 그 건의 워크시트로 간다 — 주소는 **카드의 링크에서 읽고**(경로를 손으로 적지 않는다) 이동은
   * `goto` 로 한다.
   *
   * **그 링크를 누르지 않는 까닭**(실측): 쓰기 응답이 돌아오면 목록이 다시 그려지고 카드의 노드가
   * 바뀐다 — 그 사이에 잡아 둔 노드를 누르면 「element was detached from the DOM」으로 죽는다
   * (13C 가 확인 단계에서 밟은 것과 같은 자리다). 주소를 읽어 두면 그 경합이 없다.
   */
  const href = await card.getByRole('link').first().getAttribute('href');
  expect(href, '카드에 워크시트 링크가 없다').not.toBeNull();
  await page.goto(href!);
  await expect(page.getByTestId('screen-worksheet')).toBeVisible();
  await page.waitForLoadState('networkidle');

  // 자격 자리: 카드가 걷히는 대신 **사유**가 선다 — H-1 이 가리킨 빈칸이 여기다.
  const why = page.getByTestId('qualification-absent-why');
  await expect(page.getByTestId('qualification-absent'), '자격 자리가 사유 없이 사라졌다').toHaveCount(1);
  await expect(page.getByTestId('qualification'), '응답 없이 자격 카드가 섰다').toHaveCount(0);

  /*
   * 사유가 **이 화면의 다음 걸음**을 말한다. 그 걸음(「다시 보기」)이 같은 화면의 primary 자리에
   * 서 있고, 아래에서 실제로 그것을 눌러 길이 열리는 것을 잰다 — 문면과 거동이 같은 test 에서
   * 맞물린다.
   */
  await expect(why).toContainText('다시 보기로 되돌린 뒤');
  await expect(why, '물을 길이 있는데 못 묻는다고 말한다').not.toContainText('다시 요청할 수 없어');

  /*
   * 아직 물을 길이 있는 축은 **자리 자체가 없다**(13C 의 판단). 하한 여유도 투찰가 카드의 사유도
   * 이 상태에서는 서지 않는다.
   */
  await expect(page.getByTestId('floor-margin'), '묻지 않은 축을 재지 못함으로 그렸다').toHaveCount(0);
  await expect(page.getByTestId('price-card-absent'), '같은 자리에서 다른 주장을 한다').toHaveCount(0);

  // 되돌린다 — 그 상태의 다음 행동 하나가 「다시 보기」다(머리 자리).
  const resumed = page.waitForResponse(
    (one) => one.url().includes('/resume') && one.request().method() === 'POST',
  );
  await page.getByTestId('worksheet-primary').click();
  const back = (await (await resumed).json()) as BidCaseRow;
  expect(back.state, '되돌림이 관심으로 돌린다').toBe('WATCHING');

  /**
   * **음성 대조** — 되돌림 한 걸음이 사라졌으므로 사유의 글자가 **바뀐다**. 같은 글자가 두
   * 상태에 서 있으면(더하기만 한 변경) 이 단언이 붉는다.
   */
  await expect(why).toContainText('투찰가를 요청하면');
  await expect(why, '되돌림이 끝났는데 그 걸음을 아직 말한다').not.toContainText('다시 보기로 되돌린 뒤');
  await expect(page.getByTestId('floor-margin'), '관심 상태에 여유 자리가 생겼다').toHaveCount(0);

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
