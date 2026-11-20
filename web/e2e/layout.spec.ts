import { expect, test, type Page } from '@playwright/test';

import {
  LAYOUT_SLOT_ATTRIBUTE,
  TWO_COLUMN_ATTRIBUTE,
  TWO_COLUMN_BODY_CLASS,
  TWO_COLUMN_BODY_SLOT,
  TWO_COLUMN_CONTAINER_CLASS,
  TWO_COLUMN_RAIL_CLASS,
  TWO_COLUMN_RAIL_SLOT,
} from '../src/components/twoColumnShape';

/**
 * **배치 게이트**(M14/14A, 계약 「게이트」 절). 1280px 에서 사이드바와 본문이 나란하고, 레일이
 * 있는 묶음은 레일이 본문 오른쪽이다. 390px 에서는 한 단이고 가로 넘침이 0 이다.
 *
 * **상자 좌표로 잰다.** class 이름으로 고르면 그 글자 하나로 게이트가 열린다(13B 알려진 제한 27).
 * 모집단은 **표지를 단 요소 전수**이고 화면 이름을 적지 않는다 — 뒤 레인이 레일을 붙이면 그
 * 순간 자동으로 든다.
 *
 * 이 레인은 **출하 번들**을 `vite preview` 로 띄우고 백엔드가 없다. 세션이 없어도 셸과 화면이
 * 서므로(기존 스모크가 그것을 든다) 셸 축은 실화면에서 잴 수 있다.
 *
 * **레일 축은 합성 마운트로 잰다**(알려진 제한). 레일을 쓰는 출하 화면이 14A 시점에 없기
 * 때문이다 — 화면 배치는 14B·14C·14D 소유다. 합성은 **같은 문서**(출하 CSS 가 적재된 상태)에
 * 꽂고 클래스는 출하 모듈에서 가져온다(사본을 만들지 않는다). 출하 화면의 레일은 표지 전수라
 * 뒤 레인이 붙이면 같은 단언이 그것을 함께 든다.
 */
const WIDE = { width: 1280, height: 900 } as const;
const NARROW = { width: 390, height: 844 } as const;

interface Box {
  readonly left: number;
  readonly right: number;
  readonly top: number;
  readonly width: number;
}

/**
 * **어느 탐색이 어느 폭에서 서는가**(cr M-1). 이 slice 의 머리 주장은 「같은 자리 집합이 폭에
 * 따라 두 모양으로 갈린다」인데, 앞 판의 단언은 본문 상자와 넘침뿐이라 그 주장이 걸리지 않았다.
 *
 * 실측으로 확인된 두 구멍: 사이드바를 390px 에서도 서게 해도 초록이었고, 하단 탭의 `md:hidden`
 * 을 걷어 1280px 에 고정 바가 서도 초록이었다. 뒤엣것이 더 아프다 — 본문은 `md` 이상에서
 * 바닥 여백이 **없어서**, 그 한 줄이 빠지면 모든 화면의 바닥이 고정 바에 덮인다.
 */
async function displayOf(page: Page, selector: string, index: number): Promise<string | null> {
  return page.evaluate(
    ({ one, at }) => {
      const nodes = [...document.querySelectorAll(one)];
      const node = nodes[at];
      return node === undefined ? null : getComputedStyle(node).display;
    },
    { one: selector, at: index },
  );
}

/** 탐색 landmark 두 개 — 문서 순서로 사이드바가 앞, 하단 탭이 뒤다(셸의 DOM 이 그렇다). */
const SIDEBAR_NAV = 0;
const BOTTOM_NAV = 1;

async function navCount(page: Page, selector: string): Promise<number> {
  return page.evaluate((one) => document.querySelectorAll(one).length, selector);
}

/** 보이는 요소 하나의 상자 — 없으면 `null` 이라 **찾지 못한 것**과 폭 0 을 가를 수 있다. */
async function boxOf(page: Page, selector: string): Promise<Box | null> {
  return page.evaluate((one) => {
    const node = document.querySelector(one);
    if (node === null) {
      return null;
    }
    const rect = node.getBoundingClientRect();
    return { left: rect.left, right: rect.right, top: rect.top, width: rect.width };
  }, selector);
}

/**
 * **가로 넘침은 요소의 상자로 잰다**(설계 검토 우회 B-5). 문서 폭으로 재면 넘치는 요소를
 * `overflow:hidden` 조상 안에 둔 꼴이 0 으로 읽힌다 — 보이는 요소 전수의 오른쪽 끝 최댓값이
 * 그 자리를 받는다.
 */
async function overflowOf(page: Page, viewportWidth: number): Promise<readonly string[]> {
  return page.evaluate((width) => {
    const over: string[] = [];
    for (const node of document.querySelectorAll('body *')) {
      const style = getComputedStyle(node);
      if (style.display === 'none' || style.visibility === 'hidden') {
        continue;
      }
      const rect = node.getBoundingClientRect();
      if (rect.width === 0 && rect.height === 0) {
        continue;
      }
      // 반올림 한 칸의 여유 — 1px 테두리의 소수 자리가 거짓 양성을 만든다.
      if (rect.right > width + 1) {
        over.push(`${node.tagName.toLowerCase()} right=${Math.round(rect.right)} ${node.className}`);
      }
    }
    return over;
  }, viewportWidth);
}

/** 본문이 **비어 있지 않다** — 빈 본문에서는 「한 단이다」가 늘 참이라 단언이 공허해진다. */
async function mainIsFilled(page: Page): Promise<boolean> {
  return page.evaluate(() => {
    const main = document.querySelector('#main');
    return main !== null && main.getBoundingClientRect().height > 0 && main.querySelectorAll('*').length > 3;
  });
}

const SIDEBAR_SELECTOR = 'nav[aria-label="화면"]';

test.describe('셸 — 사이드바와 본문', () => {
  test('1280px 에서 사이드바와 본문이 나란하다', async ({ page }) => {
    await page.setViewportSize({ ...WIDE });
    await page.goto('/');
    await expect(page.getByTestId('app-shell')).toBeVisible();

    const sidebar = await boxOf(page, SIDEBAR_SELECTOR);
    const main = await boxOf(page, '#main');

    // **비공허가 비교 앞에 선다** — 찾지 못했거나 폭이 0 이면 좌표 비교는 뜻이 없다.
    expect(sidebar, '사이드바 노드를 찾지 못했다').not.toBeNull();
    expect(main, '본문 노드를 찾지 못했다').not.toBeNull();
    expect(sidebar!.width, '사이드바 폭이 0 이다').toBeGreaterThan(0);
    expect(main!.width, '본문 폭이 0 이다').toBeGreaterThan(0);
    expect(await mainIsFilled(page), '본문이 비었다').toBe(true);

    // 나란하다 = 사이드바의 오른쪽 끝이 본문의 왼쪽 끝보다 뒤에 있지 않고, 세로가 겹친다.
    expect(sidebar!.right).toBeLessThanOrEqual(main!.left + 1);
    expect(Math.abs(sidebar!.top - main!.top)).toBeLessThan(WIDE.height);
  });

  test('1280px 에서 가로 넘침이 0 이다', async ({ page }) => {
    await page.setViewportSize({ ...WIDE });
    await page.goto('/');
    await expect(page.getByTestId('app-shell')).toBeVisible();

    expect(await overflowOf(page, WIDE.width)).toStrictEqual([]);
  });

  /**
   * **두 탐색이 함께 그려지고 가리는 것은 CSS 다**(셸의 설계). 그러니 「어느 쪽이 서는가」는
   * computed `display` 로만 설 수 있다 — 상자 좌표는 숨은 쪽도 0 으로 읽혀 두 사실을 가르지
   * 못한다.
   */
  test('1280px 에서는 사이드바가 서고 하단 탭이 숨는다', async ({ page }) => {
    await page.setViewportSize({ ...WIDE });
    await page.goto('/');
    await expect(page.getByTestId('app-shell')).toBeVisible();

    // 비공허: 탐색 landmark 가 **둘 다 DOM 에 있다**(하나뿐이면 아래 단언이 엉뚱한 것을 잰다).
    expect(await navCount(page, SIDEBAR_SELECTOR), '탐색 landmark 가 둘이 아니다').toBe(2);
    expect(await displayOf(page, SIDEBAR_SELECTOR, SIDEBAR_NAV), '사이드바가 숨었다').not.toBe('none');
    expect(await displayOf(page, SIDEBAR_SELECTOR, BOTTOM_NAV), '하단 탭이 넓은 화면에서 섰다').toBe('none');
  });

  /**
   * 390px 에서 거꾸로다. 하단 탭이 **고정 바**라 본문 바닥을 덮는데, 그 여백(`pb-28`)은 `md`
   * 이상에서 없어진다 — 그래서 어느 쪽이 서는지가 배치의 사실이다.
   */
  test('390px 에서는 하단 탭이 서고 사이드바가 숨는다', async ({ page }) => {
    await page.setViewportSize({ ...NARROW });
    await page.goto('/');
    await expect(page.getByTestId('app-shell')).toBeVisible();

    expect(await navCount(page, SIDEBAR_SELECTOR), '탐색 landmark 가 둘이 아니다').toBe(2);
    expect(await displayOf(page, SIDEBAR_SELECTOR, SIDEBAR_NAV), '사이드바가 좁은 화면에서 섰다').toBe('none');
    expect(await displayOf(page, SIDEBAR_SELECTOR, BOTTOM_NAV), '하단 탭이 숨었다').not.toBe('none');
  });

  test('390px 에서는 한 단이고 사이드바가 서지 않는다', async ({ page }) => {
    await page.setViewportSize({ ...NARROW });
    await page.goto('/');
    await expect(page.getByTestId('app-shell')).toBeVisible();

    expect(await mainIsFilled(page), '본문이 비었다').toBe(true);

    const main = await boxOf(page, '#main');
    expect(main).not.toBeNull();
    // 한 단 = 본문이 가로를 거의 다 쓴다. 사이드바가 서 있으면 이 값이 줄어든다.
    expect(main!.width).toBeGreaterThan(NARROW.width * 0.9);
    expect(await overflowOf(page, NARROW.width)).toStrictEqual([]);
  });
});

/** 합성 두 단을 **출하 CSS 가 적재된 문서**에 꽂는다 — 클래스는 출하 모듈에서 온다. */
async function mountTwoColumn(
  page: Page,
  options: { readonly railFirst?: boolean; readonly plantWide?: boolean } = {},
): Promise<void> {
  await page.evaluate(
    ({ attribute, slotAttribute, bodySlot, railSlot, container, body, rail, railFirst, plantWide }) => {
      const host = document.createElement('div');
      const bodyNode = `<div ${slotAttribute}="${bodySlot}" class="${body}"><p>본문 내용</p>${
        plantWide === true ? '<div style="width:4000px">넓은 것</div>' : ''
      }</div>`;
      const railNode = `<aside ${slotAttribute}="${railSlot}" class="${rail}"><p>레일 내용</p></aside>`;
      host.innerHTML = `<div ${attribute}="" class="${container}">${
        railFirst === true ? railNode + bodyNode : bodyNode + railNode
      }</div>`;
      document.querySelector('#main')?.append(host);
    },
    {
      attribute: TWO_COLUMN_ATTRIBUTE,
      slotAttribute: LAYOUT_SLOT_ATTRIBUTE,
      bodySlot: TWO_COLUMN_BODY_SLOT,
      railSlot: TWO_COLUMN_RAIL_SLOT,
      container: TWO_COLUMN_CONTAINER_CLASS,
      body: TWO_COLUMN_BODY_CLASS,
      rail: TWO_COLUMN_RAIL_CLASS,
      railFirst: options.railFirst,
      plantWide: options.plantWide,
    },
  );
}

/** 표지를 단 묶음 **전수**의 두 칸 상자 — 화면 이름을 적지 않는다. */
async function twoColumnBoxes(page: Page): Promise<readonly { body: Box; rail: Box }[]> {
  return page.evaluate(
    ({ attribute, slotAttribute, bodySlot, railSlot }) => {
      const read = (node: Element) => {
        const rect = node.getBoundingClientRect();
        return { left: rect.left, right: rect.right, top: rect.top, width: rect.width };
      };
      const found: { body: ReturnType<typeof read>; rail: ReturnType<typeof read> }[] = [];
      for (const group of document.querySelectorAll(`[${attribute}]`)) {
        const body = group.querySelector(`[${slotAttribute}="${bodySlot}"]`);
        const rail = group.querySelector(`[${slotAttribute}="${railSlot}"]`);
        if (body === null || rail === null) {
          throw new Error('두 단 묶음에 칸이 빠졌다');
        }
        found.push({ body: read(body), rail: read(rail) });
      }
      return found;
    },
    {
      attribute: TWO_COLUMN_ATTRIBUTE,
      slotAttribute: LAYOUT_SLOT_ATTRIBUTE,
      bodySlot: TWO_COLUMN_BODY_SLOT,
      railSlot: TWO_COLUMN_RAIL_SLOT,
    },
  );
}

test.describe('두 단 — 레일의 자리', () => {
  test('1280px 에서 레일이 본문 오른쪽이다', async ({ page }) => {
    await page.setViewportSize({ ...WIDE });
    await page.goto('/');
    await mountTwoColumn(page);

    const groups = await twoColumnBoxes(page);

    // **비공허**: 묶음이 하나라도 있고 두 칸의 폭이 0 이 아니다.
    expect(groups.length, '두 단 묶음을 하나도 찾지 못했다').toBeGreaterThan(0);
    for (const group of groups) {
      expect(group.body.width, '본문 칸의 폭이 0 이다').toBeGreaterThan(0);
      expect(group.rail.width, '레일 칸의 폭이 0 이다').toBeGreaterThan(0);
      expect(group.rail.left).toBeGreaterThanOrEqual(group.body.right - 1);
    }
  });

  test('양성 대조 — 레일을 본문 앞에 두면 붉다', async ({ page }) => {
    await page.setViewportSize({ ...WIDE });
    await page.goto('/');
    await mountTwoColumn(page, { railFirst: true });

    const groups = await twoColumnBoxes(page);

    expect(groups.length).toBeGreaterThan(0);
    expect(groups.some((group) => group.rail.left < group.body.right - 1), '뒤집었는데 좌표가 그대로다').toBe(true);
  });

  test('390px 에서는 한 단이고 레일이 본문 아래다', async ({ page }) => {
    await page.setViewportSize({ ...NARROW });
    await page.goto('/');
    await mountTwoColumn(page);

    const groups = await twoColumnBoxes(page);

    expect(groups.length).toBeGreaterThan(0);
    for (const group of groups) {
      expect(group.rail.width).toBeGreaterThan(0);
      expect(group.rail.top).toBeGreaterThan(group.body.top);
      // 한 단 = 두 칸이 같은 왼쪽 끝에서 시작한다.
      expect(Math.abs(group.rail.left - group.body.left)).toBeLessThan(2);
    }
    expect(await overflowOf(page, NARROW.width)).toStrictEqual([]);
  });

  test('양성 대조 — 뷰포트보다 넓은 요소를 심으면 넘침이 잡힌다', async ({ page }) => {
    await page.setViewportSize({ ...NARROW });
    await page.goto('/');
    await mountTwoColumn(page, { plantWide: true });

    expect(await overflowOf(page, NARROW.width)).not.toStrictEqual([]);
  });
});
