import { expect, test } from '@playwright/test';

import { measureInPage } from './journey/touchMeasure';

/**
 * **터치 타깃 측정의 대조**(M13/13C — 13B v M-8 · L-4).
 *
 * 게이트 자신(390px 여정)은 출하 화면을 재고, 이 spec 은 같은 함수를 **합성 페이지**에서 잰다.
 * 그래야 「조상이 자르면 보지 못한다」·「손가락으로 밀 수 없는데 예외가 선다」 같은 꼴을
 * 하네스(출하 jar · Postgres · 수집) 없이 반복해서 잴 수 있다. 재는 함수는 **같은 자리**를
 * 지나므로 이 대조가 게이트의 술어를 그대로 말한다.
 */
const VIEWPORT = { width: 390, height: 844 } as const;

test.use({ viewport: { ...VIEWPORT } });

/** 합성 페이지 — 버튼 하나를 주어진 껍질 안에 둔다. */
function page(shell: { readonly open?: string; readonly close?: string; readonly button?: string }): string {
  return `<!doctype html><html><head><meta charset="utf-8" /><style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    button { height: 44px; width: 200px; }
  </style></head><body>${shell.open ?? ''}<button style="${shell.button ?? ''}">누르기</button>${shell.close ?? ''}</body></html>`;
}

async function measured(browserPage: import('@playwright/test').Page, html: string) {
  await browserPage.setContent(html);
  return browserPage.evaluate(measureInPage);
}

test('음성 대조 — 평범한 44px 버튼은 그대로 44px 이고 걸리는 것이 없다', async ({ page: browserPage }) => {
  const result = await measured(browserPage, page({}));

  expect(result.targets).toHaveLength(1);
  expect(result.targets[0]?.height).toBe(44);
  expect(result.partiallyClipped).toStrictEqual([]);
  expect(result.offscreen).toStrictEqual([]);
});

/**
 * v M-8 ① — 조상이 `overflow:hidden` 으로 자르면 **보이는 띠**는 그 높이뿐이다. 앞 판은 타깃
 * 자신의 상자만 보아 44px 로 셌다(verifier 가 Chromium 으로 실측).
 */
test('조상이 overflow 로 자르면 그 띠만큼만 잰다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ open: '<div style="height:12px; overflow:hidden">', close: '</div>' }),
  );

  expect(result.targets[0]?.height).toBe(12);
});

/** v M-8 ③ — `contain: paint` 도 같은 잘라내기다. */
test('조상의 contain:paint 도 같은 잘라내기다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ open: '<div style="height:12px; contain:paint">', close: '</div>' }),
  );

  expect(result.targets[0]?.height).toBe(12);
});

/**
 * v M-8 ② — 조상의 `clip-path` 는 상자로 **잴 수 없다**(모양이 사각형이 아닐 수 있다). 자기
 * 잘라내기와 같은 처분: 재지 못하므로 위반으로 센다.
 */
test('조상의 clip-path 는 잴 수 없는 자리로 센다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ open: '<div style="clip-path: inset(0 0 70% 0)">', close: '</div>' }),
  );

  expect(result.partiallyClipped).toHaveLength(1);
});

/**
 * **스크롤은 잘라내기가 아니다 — 그러나 한 번에 보이는 띠는 그 상자다**(v H-4 로 주장이 바뀐
 * 자리다). 앞 판은 이 꼴을 「온전히 44」로 셌고, verifier 가 20px 스크롤러 안의 44px 버튼에서
 * 30px 지점이 버튼에 닿지 않는 것을 실측했다. test 는 남기고 **주장을 바꾼다**.
 */
test('세로 스크롤러 안의 버튼은 그 상자 높이까지만 한 번에 닿는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="height:20px; overflow-y:auto"><div style="height:400px">',
      close: '</div></div>',
    }),
  );

  expect(result.targets[0]?.height).toBeLessThanOrEqual(20);
  // **0 으로 무너진 것과 가른다**(cr N-8) — 한쪽 부등식만으로는 과잉 절삭이 초록이다.
  expect(result.targets[0]?.height).toBeGreaterThan(0);
  expect(result.partiallyClipped).toStrictEqual([]);
});

/**
 * v L-4 — 가로 스크롤 줄의 예외는 **손가락으로 밀 수 있을 때만** 선다. `touch-action: pan-y` 면
 * 가로로는 밀 수 없는데 앞 판은 예외를 그대로 세웠다.
 */
test('가로로 밀 수 없는 줄은 예외가 서지 않는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="width:300px; overflow-x:auto; touch-action:pan-y"><div style="width:900px; padding-left:500px">',
      close: '</div></div>',
    }),
  );

  expect(result.offscreen).toHaveLength(1);
});

/** 음성 대조 — 같은 줄에서 손가락으로 밀 수 있으면 예외가 **그대로 선다**(앞 판의 거동). */
test('음성 대조 — 밀 수 있는 가로 스크롤 줄은 예외가 선다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="width:300px; overflow-x:auto"><div style="width:900px; padding-left:500px">',
      close: '</div></div>',
    }),
  );

  expect(result.offscreen).toStrictEqual([]);
});

/** 자기 잘라내기는 앞 판 그대로 걸린다(회귀 대조). */
test('자기 clip-path 는 그대로 걸린다', async ({ page: browserPage }) => {
  const result = await measured(browserPage, page({ button: 'clip-path: inset(0 0 70% 0)' }));

  expect(result.partiallyClipped).toHaveLength(1);
});

/** 시각 은폐(표준 꼴)는 모집단에서 빠진다 — 앞 판 그대로. */
test('음성 대조 — 시각 은폐된 자리는 모집단 밖이다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ button: 'position:absolute; clip-path: inset(50%)' }),
  );

  expect(result.targets).toStrictEqual([]);
});

/**
 * M13/13C 수정 라운드 1 — verifier H-4 · M-2. 앞 판은 **세로로 스크롤되는 조상**을 「밀면 닿는다」로
 * 통째로 면제했다. 그런데 스크롤러가 타깃보다 **낮으면** 한 번에 보이는 띠는 스크롤러 높이뿐이고,
 * 손가락으로 세로로 밀 수 없게 막아 둔 자리도 같은 면제를 받았다.
 */
test('세로 스크롤러가 타깃보다 낮으면 그 높이로 막는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="height:12px; overflow-y:auto"><div style="height:400px">',
      close: '</div></div>',
    }),
  );

  expect(result.targets[0]?.height).toBeLessThanOrEqual(12);
  // **0 으로 무너진 것과 가른다**(cr N-8) — 한쪽 부등식만으로는 과잉 절삭이 초록이다.
  expect(result.targets[0]?.height).toBeGreaterThan(0);
});

test('세로로 밀 수 없는 스크롤러는 면제가 서지 않는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="height:12px; overflow-y:auto; touch-action:none"><div style="height:400px">',
      close: '</div></div>',
    }),
  );

  expect(result.targets[0]?.height).toBeLessThanOrEqual(12);
  // **0 으로 무너진 것과 가른다**(cr N-8) — 한쪽 부등식만으로는 과잉 절삭이 초록이다.
  expect(result.targets[0]?.height).toBeGreaterThan(0);
});

/** 음성 대조 — 타깃보다 **높은** 스크롤러는 앞 판 그대로 온전히 잰다. */
test('음성 대조 — 타깃보다 높은 세로 스크롤러는 자르지 않는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="height:200px; overflow-y:auto"><div style="height:400px">',
      close: '</div></div>',
    }),
  );

  expect(result.targets[0]?.height).toBe(44);
  expect(result.partiallyClipped).toStrictEqual([]);
});

/**
 * v M-2 — 유효 `touch-action` 은 **사슬의 교집합**이다. 스크롤러 한 요소만 보면, 안쪽이 전부
 * `pan-y` 인 가로 줄이 예외를 그대로 받는다.
 */
test('안쪽이 가로 밀기를 막으면 가로 스크롤 예외가 서지 않는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="width:300px; overflow-x:auto"><div style="width:900px; padding-left:500px; touch-action:pan-y">',
      close: '</div></div>',
    }),
  );

  expect(result.offscreen).toHaveLength(1);
});

/**
 * M13/13C 수정 라운드 2 — verifier R1-M2. 뷰포트 밖 판정이 **가로만** 봐서, 아래로 걸친 고정
 * 요소를 온전한 높이로 셌다. 고정 배치는 **밀어서 볼 수 없다** — 그 자리가 13B 하단 탭이다.
 */
test('아래로 걸친 고정 요소는 화면 밖으로 센다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ button: 'position:fixed; left:0; bottom:-30px' }),
  );

  expect(result.offscreen).toHaveLength(1);
});

test('음성 대조 — 고정이 아닌 아래쪽 내용은 밀면 닿는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ open: '<div style="height:2000px">', close: '</div>' }),
  );

  expect(result.offscreen).toStrictEqual([]);
});

test('음성 대조 — 뷰포트 안에 든 고정 요소는 그대로다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({ button: 'position:fixed; left:0; bottom:0' }),
  );

  expect(result.offscreen).toStrictEqual([]);
  expect(result.targets[0]?.height).toBe(44);
});
