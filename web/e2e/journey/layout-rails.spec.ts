import { expect, test } from '@playwright/test';

import { measureRails, railViolations, shippedAddresses } from '../railMeasure';

import { signIn } from './shared';

/**
 * **출하 레일의 자리 — 세션을 쥔 모집단**(M14/14B · 팀장 추가 지시 2026-11-20).
 *
 * 스모크 레인(`e2e/layout.spec.ts`)이 같은 축을 **세션 없이** 잰다. 두 레인의 차이는 모집단
 * 하나다: 거기서는 백엔드가 없어 **데이터가 있어야 서는 레일**이 그려지지 않고, 여기서는
 * 서버와 씨앗이 있어 그려진다.
 *
 * **왜 둘 다 필요한가**(착수 실측 2026-11-20): 오늘은 두 레인이 같은 그림을 본다 — 아홉 자리
 * 전부 자기 `screen-*` 를 그리고 로그인 얼굴은 0 이며, 레일은 `/start` 하나다. 차이가 생기는
 * 것은 **뒤 레인이 레일을 데이터 뒤에 둘 때**다(워크시트의 카운트다운·타임라인 레일이 그럴
 * 꼴이다). 그때 스모크 레인은 그 레일을 **영영 보지 못하면서** 비공허 하한은 `/start` 하나로
 * 만족되어, 「뒤 레인의 레일이 자동으로 든다」가 조용히 거짓이 된다. 이 레인이 그 자리를 받는다.
 *
 * **술어는 공유한다**(`railMeasure`). 재는 법과 위반의 정의를 spec 마다 적으면 두 레인이
 * 갈리고, 갈린 날 어느 쪽이 맞는지 아무도 모른다.
 *
 * **열거가 아니라 구성이다.** 도는 주소는 `src/routes.tsx` 의 자리 표에서 온다 — 14C·14D 의
 * 레일은 머지되는 순간 이 spec 을 고치지 않아도 든다.
 *
 * **이 spec 은 아무것도 쓰지 않는다.** 읽기만 하므로 자기 fixture 도 되돌림도 없다 — 공유
 * 하네스에 남기는 상태가 0 이다(`touch-target.spec.ts` 가 프로필을 되돌려야 했던 것과 다르다).
 */
const WIDE = { width: 1280, height: 900 } as const;
const NARROW = { width: 390, height: 844 } as const;

/**
 * **세션을 쥐고 본 출하 레일의 하한.** 오늘 `/start` 하나다. 뒤 레인이 레일을 붙이면 이 수를
 * 올린다 — 하한은 올리기만 한다(계약).
 */
const SHIPPED_RAIL_FLOOR = 1;

const SHIPPED_ADDRESSES = shippedAddresses();

test.describe.configure({ mode: 'serial' });

test('자리 표를 실제로 읽었다 — 빈 목록은 아래 단언을 공허하게 만든다', () => {
  expect(SHIPPED_ADDRESSES.length).toBeGreaterThan(5);
  expect(SHIPPED_ADDRESSES.every((one) => one.startsWith('/'))).toBe(true);
});

test('세션을 쥐고 본 출하 레일이 두 폭에서 제자리다', async ({ page }) => {
  await signIn(page);

  /** 폭마다 **따로** 센다 — 한쪽에서만 레일을 본 채 지나가는 자리를 두지 않는다. */
  const seen = { wide: 0, narrow: 0 };
  for (const [viewport, size] of [
    ['wide', WIDE],
    ['narrow', NARROW],
  ] as const) {
    await page.setViewportSize({ ...size });
    for (const address of SHIPPED_ADDRESSES) {
      await page.goto(address);
      await expect(page.getByTestId('app-shell')).toBeVisible();
      // 레일의 내용이 질의 뒤에 오므로, **그려질 기회를 준 뒤** 잰다.
      await page.waitForLoadState('networkidle');

      const groups = await measureRails(page);
      seen[viewport] += groups.length;
      expect(railViolations(groups, viewport), `${address} (${viewport}): 레일의 자리`).toStrictEqual([]);
    }
  }

  // **비공허** — 레일을 하나도 보지 못했으면 위 루프는 아무것도 재지 않았다.
  expect(seen.wide, '넓은 화면에서 출하 레일을 하나도 찾지 못했다').toBeGreaterThanOrEqual(SHIPPED_RAIL_FLOOR);
  expect(seen.narrow, '좁은 화면에서 출하 레일을 하나도 찾지 못했다').toBeGreaterThanOrEqual(SHIPPED_RAIL_FLOOR);
});
