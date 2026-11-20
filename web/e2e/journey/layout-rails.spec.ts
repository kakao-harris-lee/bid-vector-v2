import { expect, test, type Page } from '@playwright/test';

import { railScreenSlots } from '../../scripts/rail-screens.mjs';
import { measureRails, railViolations, shippedAddresses } from '../railMeasure';

import { signIn } from './shared';

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
 * **의무 집합은 소스에서 구성한다**(`railScreenSlots`): 자리 표의 element → 그 모듈의 import
 * 폐포 → `components/TwoColumn` 에 닿는 자리들. 14C·14D 가 레일을 붙이면 **아무 줄도 안 적고**
 * 이 의무에 들어온다. 그 집합이 **줄어드는** 쪽은 짝 test 의 래칫이 막는다(손으로 짠 두 단은
 * 구성에서 조용히 빠지기 때문이다 — §12.3 (z) ⓓ).
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
  expect(RAIL_SLOT_IDS).toContain('onboarding');
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
