import { expect, test } from '@playwright/test';

import { computeBorderColorsInPage, measureControlBordersInPage } from './journey/controlBorderMeasure';
import { BUTTON_TONES, controlClass } from '../src/components/Button';
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

/** 합성 `danger` 버튼이 입는 **출하 클래스** — 표에서 가져온다(사본을 만들지 않는다). */
const DANGER_CLASS = controlClass('danger', 'md');

/** 허용 클래스 — 게이트와 같은 규칙(컨트롤 선 + 어조 표의 경계 클래스). */
const ALLOWED_PROBE_CLASSES = [
  'border-control-line',
  ...BUTTON_TONES.flatMap((tone) =>
    controlClass(tone, 'md')
      .split(/\s+/u)
      .filter((one) => one.startsWith('border-') && one !== 'border-control-line'),
  ),
];

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

/**
 * **이 대조가 「고정인가」를 재는 자리다**(변이 F-2 가 드러냈다 — 앞 꼴은 타깃이 뷰포트 안에
 * 있어 고정 판정을 늘 참으로 바꿔도 초록이었다). 버튼을 **화면 아래로 밀어낸** 뒤에야 두
 * 갈래가 갈린다: 보통 흐름이면 밀어서 닿고, 고정이면 영원히 닿지 않는다.
 */
test('음성 대조 — 고정이 아닌 아래쪽 내용은 밀면 닿는다', async ({ page: browserPage }) => {
  const result = await measured(browserPage, page({ open: '<div style="height:2000px"></div>' }));

  // **비공허**: 버튼이 실제로 뷰포트 아래에 있다(그러지 않으면 이 대조가 아무것도 가르지 않는다).
  // 게이트가 내는 기록이 아니라 **브라우저에 직접** 묻는다 — 측정 표면을 늘리지 않기 위해서다.
  const belowFold = await browserPage.evaluate(() => {
    const button = document.querySelector('button');
    return button !== null && button.getBoundingClientRect().top > window.innerHeight;
  });
  expect(belowFold).toBe(true);
  expect(result.offscreen).toStrictEqual([]);
});

/**
 * **sticky 는 고정이 아니다**(cr R2-5 · verifier medium). 붙기 전에는 흐름과 함께 스크롤되므로
 * 접힘 아래의 sticky 머리는 밀면 닿는다 — 그 자리를 「영원히 닿지 않는다」로 세면 과차단이고,
 * 13D·13E 의 목록 절 머리가 이유 없이 붉는다.
 */
test('음성 대조 — 접힘 아래의 sticky 머리는 밀면 닿는다', async ({ page: browserPage }) => {
  const result = await measured(
    browserPage,
    page({
      open: '<div style="height:2000px"></div><div style="position:sticky; top:0">',
      close: '</div>',
    }),
  );

  // **비공허**: 그 버튼이 실제로 접힘 아래에 있고, 조상이 실제로 sticky 다.
  const below = await browserPage.evaluate(() => {
    const button = document.querySelector('button');
    const parent = button?.parentElement ?? null;
    return {
      offscreen: button !== null && button.getBoundingClientRect().top > window.innerHeight,
      sticky: parent !== null && getComputedStyle(parent).position === 'sticky',
    };
  });
  expect(below).toStrictEqual({ offscreen: true, sticky: true });
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

/**
 * **가로 축의 대조**(M14/14A — M13 잔여 §12.3 (w) ⓑ). 게이트가 두 변을 함께 보게 됐으므로,
 * 측정 함수가 **가로를 실제로 내는지**를 같은 함수로 잰다. 세로만 키운 꼴이 어떤 값으로
 * 읽히는지가 이 대조의 요점이다 — 앞 판에서 그 값은 아무도 읽지 않았다.
 */
test('세로만 키운 버튼은 가로가 그대로 읽힌다 — 두 변이 따로 선다', async ({ page: browserPage }) => {
  const result = await measured(browserPage, page({ button: 'height:44px; width:30px' }));

  expect(result.targets[0]?.height).toBe(44);
  expect(result.targets[0]?.width).toBe(30);
});

test('음성 대조 — 두 변이 다 넉넉하면 그대로 읽힌다', async ({ page: browserPage }) => {
  const result = await measured(browserPage, page({ button: 'height:48px; width:48px' }));

  expect(result.targets[0]?.height).toBe(48);
  expect(result.targets[0]?.width).toBe(48);
});

/**
 * **컨트롤 경계 색 축의 대조**(M14/14A · v M-1 · cr L-2).
 *
 * 게이트 자신(390px 여정)은 출하 화면을 재고, 이 spec 은 같은 함수를 **합성 페이지**에서 잰다.
 * 여기서 세우는 것이 리뷰가 찾은 바로 그 구멍이다 — 클래스 글자가 **헬퍼**에서 와도 그려진 색은
 * 같으므로, AST 축이 못 보는 꼴을 이 층이 본다.
 */
test('헬퍼가 만든 클래스여도 그려진 경계 색을 본다 — AST 축이 못 보는 꼴', async ({ page: browserPage }) => {
  await browserPage.setContent(
    `<!doctype html><html><head><meta charset="utf-8" /><style>
       .card-line { border: 1px solid #d9d6cf; }
       .control-line { border: 1px solid #8a877f; }
     </style></head><body>
       <input id="a" />
       <input id="b" />
       <script>
         const helper = () => 'card-line';
         document.getElementById('a').className = helper();
         document.getElementById('b').className = 'control-line';
       </script>
     </body></html>`,
  );
  const measured = await browserPage.evaluate(measureControlBordersInPage);

  expect(measured).toHaveLength(2);
  // 앞이 **헬퍼가 만든** 카드 선, 뒤가 컨트롤 선. 둘이 다른 값으로 읽히는 것이 이 축의 전부다 —
  // 게이트는 허용 집합(컨트롤 선 · 어조 색)에 앞엣것이 없어서 붉는다.
  expect(measured.map((one) => one.color)).toStrictEqual(['rgb(217, 214, 207)', 'rgb(138, 135, 127)']);
  expect(measured.every((one) => one.width > 0)).toBe(true);
});

test('테두리를 그리지 않는 컨트롤은 두께 0 으로 읽힌다 — 음성 대조', async ({ page: browserPage }) => {
  await browserPage.setContent(
    '<!doctype html><html><head><meta charset="utf-8" /></head><body><button style="border:none">누르기</button></body></html>',
  );
  const measured = await browserPage.evaluate(measureControlBordersInPage);

  expect(measured).toHaveLength(1);
  expect(measured[0]?.width).toBe(0);
});

test('숨은 컨트롤은 모집단 밖이다', async ({ page: browserPage }) => {
  await browserPage.setContent(
    '<!doctype html><html><head><meta charset="utf-8" /></head><body><input style="display:none" /><input /></body></html>',
  );
  const measured = await browserPage.evaluate(measureControlBordersInPage);

  expect(measured).toHaveLength(1);
});

/**
 * **어조 테두리가 허용 집합에 실제로 든다**(cr M-1 · 리뷰가 실측으로 찾은 자리).
 *
 * 출하 CSS 의 `danger` 테두리는 `color-mix(in oklab, …)` 이라 브라우저가 `oklab(… / 0.4)` 로
 * 돌려준다. 앞 판은 허용 색을 Node 에서 `rgba(…)` 글자로 조립해서 **영영 맞지 않았고**, 그런데도
 * 초록이었다 — `danger` 버튼이 모집단에 **없었기** 때문이다(쓰기 실패 얼굴은 쓰기가 실패해야
 * 선다). 14C·14D 가 그 얼굴을 띄우는 순간 붉었을 자리다.
 *
 * 그래서 **출하 클래스로 그린 `danger` 버튼**을 여기 세운다. 허용 집합도 같은 페이지에서
 * 계산하므로 둘이 같은 파서를 지난다. 앞 판에서는 이 test 가 붉고 지금은 초록이다.
 */
test('어조 테두리(danger)가 허용 집합에 든다 — 같은 파서를 지난다', async ({ page: browserPage }) => {
  // 출하 CSS 가 적재된 문서여야 한다 — `setContent` 로는 어조 테두리가 서지 않는다.
  await browserPage.goto('/');
  await browserPage.evaluate((danger) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.id = 'danger-probe';
    button.className = danger;
    button.textContent = '되돌리기';
    document.querySelector('#main')?.append(button);
  }, DANGER_CLASS);

  const measured = await browserPage.evaluate(measureControlBordersInPage);
  const probe = measured.find((one) => one.name === '되돌리기');
  const allowed = new Set(await browserPage.evaluate(computeBorderColorsInPage, ALLOWED_PROBE_CLASSES));

  expect(probe, 'danger 버튼을 찾지 못했다').toBeTruthy();
  expect(probe?.width, '테두리를 그리지 않았다 — 이 대조가 공허하다').toBeGreaterThan(0);
  // **조립한 글자와 다른 꼴이다**(이 대조의 요점) — 그런데도 허용에 든다.
  expect(probe?.color).toContain('oklab');
  expect(allowed.has(probe?.color ?? ''), `${probe?.color} 가 허용 집합 밖이다`).toBe(true);
});
