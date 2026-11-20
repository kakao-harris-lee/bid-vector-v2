import { expect, test, type Page } from '@playwright/test';

import { RAIL_SCREEN_FLOOR, railScreenSlots } from '../../scripts/rail-screens.mjs';
import {
  computedDisplaysOf,
  measureCells,
  measureRails,
  railViolations,
  rowCountOf,
  shippedAddresses,
} from '../railMeasure';

import { search, signIn } from './shared';

/**
 * **출하 레일의 자리 — 화면마다의 보증**(M14/14B 수정 라운드 1 · v HIGH-1).
 *
 * 스모크 레인(`e2e/layout.spec.ts`)이 같은 술어를 **데이터 없이** 건다. 거기서는 preview 서버가
 * 모든 `/api/*` 에 `200 text/html` 를 돌려주어(SPA 폴백) 화면이 401 이 아니라 **파싱 실패**를
 * 받고, **데이터가 있어야 서는 레일**이 영영 그려지지 않는다. 그래서 그 레인은 자리 판정과
 * 「적어도 하나는 봤다」까지만 지고, **화면마다의 의무는 여기**가 진다.
 *
 * **왜 합계 하한으로는 안 되는가**(v HIGH-1 실측): 레일이 둘 이상 선 뒤에는 어느 하나가 측정에서
 * 빠져도 나머지가 합계를 떠받친다. 이 축이 세워진 계기가 「시작하기의 레일이 통째로 사라졌다」인데
 * 합계로는 그 회귀를 다시 못 잡는다 — 실제로 14D 를 머지한 트리에서 `/start` 를 망가뜨린 변이가
 * **초록**이었다.
 *
 * **의무 집합은 소스에서 구성한다**(`railScreenSlots`): 자리 표의 element → 그 태그가 가리키는
 * 화면 모듈 → 그 모듈이 `TwoColumn` 을 **JSX 로 그리는가**(신원은 심볼이라 별칭·네임스페이스도
 * 같은 답이다). 뒤 레인이 레일을 붙이면 **아무 줄도 안 적고** 이 의무에 들어온다. 그 집합이
 * **줄어드는** 쪽은 짝 test 의 **등식 래칫**이 막는다(손으로 짠 두 단은 구성에서 조용히 빠지기
 * 때문이다 — §12.3 (z) ⓓ).
 *
 * **늘어날 때는 한 줄을 적는다**: 등식이라 유도 집합이 늘면 그 순간 붉는다 — 머지하는 쪽이
 * `RAIL_SCREEN_FLOOR` 에 자기 자리 이름을 더한다(14C 의 워크시트가 그렇게 들어왔다).
 *
 * **쓰기는 자기 공고 하나뿐이다.** 경로 인자를 쥔 자리(워크시트)는 **건이 있어야** 레일이 서므로
 * 이 spec 이 자기 공고에 건을 하나 연다. 다른 여정이 쓰지 않는 공고를 고르고(공유 하네스 규율),
 * 담기만 한다 — 상태를 밀지 않는다. 계약에 건을 지우는 operation 이 없어 되돌림은 없다
 * (`touch-target.spec.ts` 의 씨앗과 같은 판단: 건은 전역 프로필과 달리 **공고별** 상태다).
 */
const WIDE = { width: 1280, height: 900 } as const;
const NARROW = { width: 390, height: 844 } as const;

/**
 * 이 spec **자기 공고** — 다른 여정 spec 이 쓰지 않는 하네스 공고다(이름 어휘가 아니라 **항목
 * 집합**으로 골랐다: 다른 spec 들이 쓰는 이름의 여집합).
 */
const OWN_NOTICE = 'SYN-M9-OPEN-UNPROMOTED';

/** 레일을 **그려야 하는** 자리 전수 — 소스에서 구성된다(이 파일에 이름을 적지 않는다). */
const RAIL_SLOT_IDS: readonly string[] = railScreenSlots(
  new URL('../../', import.meta.url).pathname,
).map((one: { readonly id: string }) => one.id);

/** 자기 공고의 차수를 찾아 건을 연다 — 출하 API 로만(여정의 씨앗과 같은 규율). */
async function seedOwnCase(page: Page): Promise<string> {
  const session = await page.request.get('/api/session');
  expect(session.status(), '세션 조회').toBe(200);
  const { csrf } = (await session.json()) as { readonly csrf: string };

  const listed = await page.request.get('/api/notices', { params: { q: OWN_NOTICE } });
  expect(listed.status(), `${OWN_NOTICE} 검색`).toBe(200);
  const rows = (await listed.json()) as readonly { readonly noticeNumber: string; readonly noticeRound: string }[];
  const target = rows.find((one) => one.noticeNumber === OWN_NOTICE);
  // 없으면 **사유를 말하고** 멈춘다 — 하네스 수집이 달라진 것과 레일이 안 그려진 것은 다른 일이다.
  expect(target, `하네스 수집에 ${OWN_NOTICE} 가 없다 — 이 spec 의 fixture 가 사라졌다`).toBeDefined();
  const noticeRound = target!.noticeRound;

  const added = await page.request.post('/api/bid-cases', {
    headers: { 'X-Operator-Csrf': csrf },
    data: { noticeNumber: OWN_NOTICE, noticeRound, bidKind: 'REAL' },
  });
  // 이미 담겨 있으면 409 다 — 그것도 「건이 있다」는 뜻이라 받아들인다(`--repeat-each` 둘째 회차).
  expect([200, 201, 409], `담기: ${added.status()}`).toContain(added.status());
  return noticeRound;
}

test.describe.configure({ mode: 'serial' });

test('의무 집합을 소스에서 **실제로** 읽었다 — 빈 집합은 아래 단언을 공허하게 만든다', () => {
  expect(RAIL_SLOT_IDS.length).toBeGreaterThan(0);
  // 이름을 **사본으로 적지 않는다**(cr N-4): 래칫 목록이 한 자리이고 여기는 그것을 읽는다.
  expect([...RAIL_SLOT_IDS].sort()).toStrictEqual([...RAIL_SCREEN_FLOOR].sort());
});

test('레일을 그리는 자리마다 레일이 두 폭에서 하나 이상 제자리다', async ({ page }) => {
  await signIn(page);
  const noticeRound = await seedOwnCase(page);
  const addresses = shippedAddresses({ noticeNumber: OWN_NOTICE, noticeRound });

  // 자리 표와 의무 집합이 **같은 이름을 쓰는가** — 짝이 안 맞으면 아래 루프가 영영 0 을 센다.
  const known = new Set(addresses.map((one) => one.id));
  expect(
    RAIL_SLOT_IDS.filter((id) => !known.has(id)),
    '의무 집합에 자리 표가 모르는 이름이 있다',
  ).toStrictEqual([]);

  for (const [viewport, size] of [
    ['wide', WIDE],
    ['narrow', NARROW],
  ] as const) {
    await page.setViewportSize({ ...size });
    /** 자리마다의 계수 — 합계가 아니라 **이름별**이다(합계는 한 자리의 소실을 가린다). */
    const bySlot = new Map<string, number>();
    for (const { id, address } of addresses) {
      await page.goto(address);
      await expect(page.getByTestId('app-shell')).toBeVisible();
      // 레일의 내용이 질의 뒤에 오므로, **그려질 기회를 준 뒤** 잰다.
      await page.waitForLoadState('networkidle');

      const groups = await measureRails(page);
      bySlot.set(id, (bySlot.get(id) ?? 0) + groups.length);
      expect(railViolations(groups, viewport), `${address} (${viewport}): 레일의 자리`).toStrictEqual([]);
    }

    /**
     * **화면마다의 비공허.** 레일을 그려야 하는 자리가 레일을 하나도 안 내면 붉는다 — 다른
     * 자리의 레일이 그 수를 떠받치지 못한다.
     */
    expect(
      RAIL_SLOT_IDS.filter((id) => (bySlot.get(id) ?? 0) < 1),
      `${viewport}: 레일을 그려야 하는 자리가 레일을 내지 않았다`,
    ).toStrictEqual([]);
  }
});

/**
 * **압축 금액은 좁은 화면의 것이다**(팀장 판정 ⑤ · v MEDIUM).
 *
 * jsdom 은 CSS 를 풀지 않아 그 자리를 **클래스 이름**으로 쟀는데, 그 단언은 스타일 변종을
 * 지나보냈다(실측): `md:hidden` 을 그대로 둔 채 `md:inline-flex!` 를 더하면 출하 CSS 가
 * `display:inline-flex!important` 를 내고 넓은 화면에서 압축이 선다. 반대쪽도 같다 — 정확 금액
 * `dl` 에서 `hidden` 을 떼면 좁은 화면에서 둘이 겹쳐 선다.
 *
 * 그래서 **브라우저가 계산한 값**을 두 폭에서 본다. 여기(여정 레인)에 두는 까닭은 금액이 실린
 * 카드가 필요하기 때문이다 — 검색 결과가 그 카드를 낸다.
 */
test('압축 금액과 정확 금액이 폭에 따라 하나씩만 그려진다', async ({ page }) => {
  await signIn(page);
  await page.setViewportSize({ ...WIDE });
  await page.goto('/');
  await search(page, '합성');
  await page.waitForLoadState('networkidle');

  const wideCompact = await computedDisplaysOf(page, 'notice-amount-compact-box');
  const wideFacts = await computedDisplaysOf(page, 'notice-facts');

  // **비공허가 비교 앞에 선다** — 카드를 하나도 못 찾았으면 아래 단언은 공집합 위에서 참이다.
  expect(wideCompact.length, '압축 금액 묶음을 하나도 찾지 못했다').toBeGreaterThan(0);
  expect(wideFacts.length, '정확 금액 묶음을 하나도 찾지 못했다').toBeGreaterThan(0);
  expect(wideCompact.filter((one) => one !== 'none'), '넓은 화면에서 압축 금액이 그려졌다').toStrictEqual([]);
  expect(wideFacts.filter((one) => one === 'none'), '넓은 화면에서 정확 금액이 숨었다').toStrictEqual([]);

  await page.setViewportSize({ ...NARROW });
  await page.waitForLoadState('networkidle');

  const narrowCompact = await computedDisplaysOf(page, 'notice-amount-compact-box');
  const narrowFacts = await computedDisplaysOf(page, 'notice-facts');

  expect(narrowCompact.length, '좁은 화면에서 압축 금액 묶음을 찾지 못했다').toBeGreaterThan(0);
  expect(narrowFacts.length, '좁은 화면에서 정확 금액 묶음을 찾지 못했다').toBeGreaterThan(0);
  expect(narrowCompact.filter((one) => one === 'none'), '좁은 화면에서 압축 금액이 숨었다').toStrictEqual([]);
  expect(narrowFacts.filter((one) => one !== 'none'), '좁은 화면에서 정확 금액이 그려졌다').toStrictEqual([]);
});

/**
 * **요약 타일은 좁은 화면에서 줄을 바꾼다**(M14/14F · cr D2 · v M-2).
 *
 * 앞 판은 타일 넷이 390px 에서 **한 줄에 눌려** 칸 하나가 ≈70px 였고, 「서버가 적격 계수를 내지
 * 않습니다」가 다섯 줄로 쪼개졌다. `flex-wrap` 은 있었지만 타일이 0 까지 줄어들 수 있어 **wrap 이
 * 발화하지 않았다**.
 *
 * 그 꼴은 **기존 배치 단언 어디에도 걸리지 않는다** — 줄어드는 flex 자식은 가로로 넘치지도
 * 두 단이 되지도 않는다. 그래서 **상자 좌표**로 줄 수를 센다(클래스 글자로 재면 스타일 변종
 * 하나가 지나간다 — 바로 위 test 가 그 함정을 실측한 자리다).
 *
 * 타일은 **집계 응답이 서면** 그려지므로(건이 0 건이어도 선다) 이 test 는 씨앗을 심지 않는다.
 */
test('결과 요약 타일이 좁은 화면에서 2×2 로 선다', async ({ page }) => {
  await signIn(page);

  await page.setViewportSize({ ...WIDE });
  await page.goto('/results');
  await expect(page.getByTestId('results-tiles')).toBeVisible();
  await page.waitForLoadState('networkidle');

  const wide = await measureCells(page, 'results-tiles');
  // **비공허가 비교 앞에 선다** — 칸을 하나도 못 찾으면 아래 줄 세기는 공집합 위에서 참이다.
  expect(wide.length, '넓은 화면에서 타일 넷이 아니다').toBe(4);
  expect(rowCountOf(wide), '넓은 화면에서는 한 줄이다').toBe(1);

  await page.setViewportSize({ ...NARROW });
  await page.waitForLoadState('networkidle');

  const narrow = await measureCells(page, 'results-tiles');
  expect(narrow.length, '좁은 화면에서 타일 넷이 아니다').toBe(4);
  expect(rowCountOf(narrow), '좁은 화면에서 타일이 2×2 로 서지 않았다').toBe(2);
  /**
   * **줄 수만으로는 모자라다**: 칸이 0 까지 줄어든 채 두 줄이 되는 꼴도 2 를 낸다. 그래서 칸의
   * **폭 하한**도 함께 본다 — 390px 에서 2열이면 한 칸이 적어도 화면의 3분의 1 은 된다.
   */
  const tooNarrow = narrow.filter((one) => one.width < NARROW.width / 3);
  expect(tooNarrow, `좁은 화면에서 타일이 눌렸다: ${JSON.stringify(narrow)}`).toStrictEqual([]);
});
