import { expect, request as apiRequest, test, type BrowserContext, type Page } from '@playwright/test';

import { journeyContext, search, signIn } from './shared';

/**
 * **터치 타깃 게이트**(M13/13B, 계약 「게이트」 절). 모바일 폭에서 화면마다 **보이는**
 * `button` · `a` · `[role="tab"]` · `[role="button"]` **전수**의 실제 상자 높이가 44px 이상이고
 * 가로 넘침이 0 인가.
 *
 * **이름으로 고르지 않는다.** 모집단은 선택자 전수이고, 새 버튼이 생기면 자동으로 든다.
 *
 * 착수 실측(2026-11-16, base a338b55b): 보이는 요소 101개 가운데 44px 이상이 **0** 이었다.
 * 그래서 이 spec 은 이관 전 트리에서 **붉고**, 이관이 끝나야 초록이다.
 */
const VIEWPORT = { width: 390, height: 844 } as const;

test.use({ viewport: { ...VIEWPORT } });

/** 손가락이 닿아야 하는 최소 변(§6.5 — 모바일 우선). */
const MIN_TOUCH_PX = 44;

/**
 * 이 spec **자기 fixture** 의 공고 둘 — 다른 여정이 건을 세우지 않는 자리다(공유 하네스에서
 * 남의 건에 기대면 그 여정이 바뀔 때 이 게이트의 계수가 조용히 흔들린다).
 *
 * 개찰 축 fixture 를 쓰지만 그 축과 **겹치지 않는다**: 개찰 결과는 수집 경로의 읽기이고 입찰 건의
 * 생애주기는 운영자 기록이다. 둘은 서로의 값을 보지 않는다.
 */
const OWN_NOTICES = ['SYN-M9-OPEN-NOT-OPENED', 'SYN-M9-OPEN-REBID'] as const;

/**
 * 도는 화면과 **화면별 계수 하한**(팀장 착수 판정 2026-11-16).
 *
 * 하한이 전수 단언 **앞에** 선다 — 「보이는 것」 필터가 집합을 비우면 전수 단언은 조용히
 * 초록이 된다(공집합의 모든 원소가 44px 이상이다).
 *
 * `slotId` 는 `src/routes.tsx` 의 자리 표와 **등식**으로 묶인다
 * (`scripts/journey-screen-table.test.mjs`) — 새 화면이 자리 표에 들어오면 이 표가 모를 때 붉는다.
 */
export const TOUCH_TARGET_SCREENS = [
  { slotId: 'onboarding', path: '/start', ready: 'screen-onboarding', minCount: 13 },
  { slotId: 'home', path: '/', ready: 'screen-home', minCount: 8 },
  // 경로를 **문자열 리터럴**로 적는다 — 등식 게이트가 AST 로 읽는 자리이고, 식으로 적으면 그
  // 게이트가 값을 읽지 못한다. 씨앗과 어긋나지 않는 것은 아래 첫 단언이 든다.
  { slotId: 'worksheet', path: '/notices/SYN-M9-OPEN-NOT-OPENED/000', ready: 'screen-worksheet', minCount: 7 },
  { slotId: 'bid-box', path: '/bids?tab=closed', ready: 'screen-bid-box', minCount: 11 },
  { slotId: 'results', path: '/results', ready: 'screen-results', minCount: 7 },
  { slotId: 'inbox', path: '/inbox', ready: 'screen-inbox', minCount: 12 },
  { slotId: 'settings', path: '/settings', ready: 'screen-settings', minCount: 27 },
  // 로그인 자리에는 **탐색을 그리지 않는다**(착수 판정) — 그래서 하한이 폼의 버튼 하나다.
  { slotId: 'login', path: '/login', ready: 'screen-login', minCount: 1 },
  { slotId: 'not-found', path: '/no-such-place', ready: 'screen-not-found', minCount: 5 },
] as const;

interface MeasuredTarget {
  readonly tag: string;
  readonly name: string;
  readonly width: number;
  readonly height: number;
}

interface Measurement {
  readonly targets: readonly MeasuredTarget[];
  readonly overflow: number;
  /** 뷰포트 **밖으로 삐져나간** 타깃 — 손가락이 그 상자의 일부에 닿지 못하는 자리다. */
  readonly offscreen: readonly string[];
  /** 부분 잘라내기가 걸린 타깃 — 눌리는 띠를 상자로 잴 수 없다. */
  readonly partiallyClipped: readonly string[];
}

/**
 * 한 화면의 측정 — **상자**로 본다.
 *
 * 「보이지 않는 것」을 빼는 술어가 **computed style** 인 것이 요점이다(착수 판정 ⓘ):
 * `.sr-only` 라는 **클래스 이름**으로 빼면 그 글자 하나로 게이트가 열린다. 시각 은폐의 표준
 * 기법(`clip: rect(0,0,0,0)` · `clip-path: inset(50%)`)을 보면 기법을 쓴 자리만 빠진다.
 *
 * 그 예외가 **진짜 버튼을 숨기는 데** 쓰이면 집합이 줄고, 그때는 화면별 계수 하한이 붉는다.
 */
async function measure(page: Page): Promise<Measurement> {
  return page.evaluate(() => {
    /**
     * 모집단(팀장 판정 2026-11-16 보충) — 계약의 넷에 **누르는 내장 요소 셋**을 더한다:
     * `input[type=button|submit|reset]` · `summary` · `select`, 그리고 **체크박스·라디오**
     * (cr N-1 · 팀장 판정). 손가락이 누르는 자리이고, AST 축이 `input` 을 **무조건** 면제하므로
     * 이쪽에서 받지 않으면 두 게이트 사이에 틈이 생긴다 — 그 틈에 실물이 있던 쪽이 바로
     * 체크박스였다(실측 7자리 13px). `label` 은 **선택자에** 넣지 않는다 — 레이블이 혼자 서면
     * 누르는 자리가 아니고, 감싼 레이블은 아래 `touchBoxOf` 가 그 컨트롤을 통해 집는다.
     */
    const nodes = [
      ...document.querySelectorAll(
        'button, a, [role="tab"], [role="button"], input[type="button"], input[type="submit"], input[type="reset"], input[type="checkbox"], input[type="radio"], summary, select',
      ),
    ];
    /**
     * **누르는 자리가 그 요소 자신이 아닐 수 있다**(cr P-3). 체크박스·라디오를 `<label>` 이
     * **감싸면** 레이블 어디를 눌러도 그 컨트롤이 켜진다 — 손가락이 닿는 상자는 레이블이다.
     * 그래서 그 자리는 레이블을 잰다.
     *
     * **느슨해지지 않는다**: 레이블이 44px 미만이면 그대로 붉고(변이 T), 컨트롤을 키워서 얻던
     * 초록이 **레이블 상자**로 옮겨갈 뿐이다. 감싸지 않은(명시 `htmlFor`) 레이블은 집지 않는다 —
     * 그때는 떨어진 두 상자라 컨트롤 자신이 닿는 자리다.
     */
    const touchBoxOf = (node: Element): Element => {
      if (!(node instanceof HTMLInputElement) || (node.type !== 'checkbox' && node.type !== 'radio')) {
        return node;
      }
      return node.closest('label') ?? node;
    };
    const targets = [];
    const offscreen = [];
    const partiallyClipped = [];
    for (const node of nodes) {
      const measured = touchBoxOf(node);
      const style = getComputedStyle(measured);
      let box = measured.getBoundingClientRect();
      /**
       * **시각 은폐는 위치 지정된 요소에서만 선다**(v M-2). `clip` 은 absolute·fixed 가 아닌
       * 요소에 **적용되지 않는데** computed 값에는 그대로 남는다 — 그 값만 보던 앞 판은 보이고
       * 눌리는 44×29 버튼을 모집단에서 뺐다(실측).
       */
      const positioned = style.position === 'absolute' || style.position === 'fixed';
      const clipped =
        positioned && (style.clip === 'rect(0px, 0px, 0px, 0px)' || style.clipPath === 'inset(50%)');
      /**
       * **상자가 0 이어도 글자가 보일 수 있다**(v M-2). `display: contents` 요소는 자기 상자가
       * 없지만 글자는 그려지고 클릭이 발화한다. 그 자리는 **글자 범위**로 다시 잰다.
       */
      if (!clipped && box.width === 0 && box.height === 0) {
        const range = document.createRange();
        range.selectNodeContents(measured);
        const textBox = range.getBoundingClientRect();
        if (textBox.width > 0 && textBox.height > 0) {
          box = textBox;
        }
      }
      const visible =
        !clipped &&
        style.visibility !== 'hidden' &&
        style.display !== 'none' &&
        Number(style.opacity) > 0 &&
        box.width > 0 &&
        box.height > 0;
      if (!visible) {
        continue;
      }
      const name = (node.getAttribute('aria-label') ?? measured.textContent ?? '').trim().slice(0, 40);
      /**
       * **부분 잘라내기는 잴 수 없으므로 위반이다**(v M-4). `clip-path: inset(0 0 70% 0)` 는 상자를
       * 줄이지 않아 높이 단언을 지나는데, 실제로 눌리는 띠는 그 일부뿐이다. 시각 은폐의 표준
       * 꼴(위 `clipped`)이 아닌 잘라내기가 걸려 있으면 **보수적으로** 센다.
       *
       * **보이는 것만 센다**(cr P-5) — `display:none`·0 상자처럼 안 보이는 자리까지 보고하면
       * 「보이는 타깃」을 재는 게이트가 안 보이는 것 때문에 붉는다. 위 `continue` 뒤라 여기 오는
       * 것은 전부 보이는 자리이고, 그래서 `clipped` 는 이미 거짓이다.
       */
      if (style.clipPath !== 'none' || style.clip !== 'auto') {
        partiallyClipped.push(`${measured.tagName.toLowerCase()} "${name.slice(0, 24)}"`);
      }
      targets.push({
        tag: measured.tagName.toLowerCase(),
        name,
        width: Math.round(box.width * 10) / 10,
        height: Math.round(box.height * 10) / 10,
      });
      /**
       * **가로로 스크롤되는 줄 안의 타깃은 닿을 수 있다** — 운영자가 밀어서 보면 된다. 닿지
       * 못하는 것은 그런 조상이 없는 채 뷰포트 밖에 **고정된** 자리다(변이 H 가 그 꼴이다).
       * 그 둘을 조상의 `overflow-x` 와 실제 스크롤 여지로 가른다.
       */
      let scrollable = false;
      for (let parent = measured.parentElement; parent !== null; parent = parent.parentElement) {
        const overflowX = getComputedStyle(parent).overflowX;
        if (!((overflowX === 'auto' || overflowX === 'scroll') && parent.scrollWidth > parent.clientWidth)) {
          continue;
        }
        const container = parent.getBoundingClientRect();
        /**
         * **그 스크롤 상자 자신이 뷰포트 안**이어야 한다(v M-5). 뷰포트 밖에 고정된 상자에
         * `overflow-x-auto` 한 줄을 더하는 것만으로 진짜 넘침이 면제되던 자리다.
         */
        if (container.left < -0.5 || container.right > window.innerWidth + 0.5) {
          continue;
        }
        // 그리고 타깃이 그 상자의 **스크롤 범위 안**이어야 한다(cr N-6) — 범위 밖은 밀어도 닿지 않는다.
        const start = box.left - container.left + parent.scrollLeft;
        const end = box.right - container.left + parent.scrollLeft;
        if (start >= -0.5 && end <= parent.scrollWidth + 0.5) {
          scrollable = true;
          break;
        }
      }
      // 부분 화소 반올림을 0.5px 로 받는다 — 그보다 큰 삐져나감만 센다.
      if (!scrollable && (box.left < -0.5 || box.right > window.innerWidth + 0.5)) {
        offscreen.push(
          `${measured.tagName.toLowerCase()} [${Math.round(box.left)}, ${Math.round(box.right)}] "${name}"`,
        );
      }
    }
    return {
      targets,
      offscreen,
      partiallyClipped,
      // **문서와 본문 둘 다** 본다 — 한쪽만 보면 넘침이 다른 쪽에만 서는 꼴을 놓친다.
      overflow:
        Math.max(document.documentElement.scrollWidth, document.body.scrollWidth) - window.innerWidth,
    };
  });
}

/**
 * 프로필 한 벌 가운데 **이 spec 이 덮는 축**. 두 쓰기(사업 정보 · 자격 사실)가 통째로 교체하는
 * 절이고, 되돌림은 읽어 둔 이 값들을 그대로 돌려보낸다.
 */
interface ProfileSnapshot {
  readonly businessTypes: readonly string[];
  readonly licensesDeclared: boolean;
  readonly licenseNames: readonly string[];
  readonly regionTerms: readonly string[];
  readonly tradeName?: string | null;
  readonly headquartersRegion?: string | null;
  readonly naraRegistration: string;
  readonly naraRegisteredFields: readonly string[];
  readonly businessCertificateExpiresOn?: string | null;
}

/**
 * 되돌림이 **비교하는** 축 — 없는 값을 `null` 로 모은다(응답은 미설정 필드를 빼기도 하고 `null`
 * 로 주기도 하므로, 두 꼴을 한 값으로 접지 않으면 되돌린 뒤 비교가 꼴 차이로 깨진다).
 *
 * `qualificationDeclared` 는 **여기 없다**: 자격 사실 쓰기는 언제나 행을 세우므로 「미설정」으로는
 * 돌아갈 수 없다(알려진 제한). 값은 전부 돌아가고 잃는 것은 「미설정 ↔ 미신고」의 구분 하나다.
 */
function restorableAxes(profile: ProfileSnapshot): Record<string, unknown> {
  return {
    businessTypes: profile.businessTypes,
    licensesDeclared: profile.licensesDeclared,
    licenseNames: profile.licenseNames,
    regionTerms: profile.regionTerms,
    tradeName: profile.tradeName ?? null,
    headquartersRegion: profile.headquartersRegion ?? null,
    naraRegistration: profile.naraRegistration,
    naraRegisteredFields: profile.naraRegisteredFields,
    businessCertificateExpiresOn: profile.businessCertificateExpiresOn ?? null,
  };
}

/**
 * 이 spec 이 **쓰기 전에** 읽어 둔 프로필과, 되돌림이 쓸 세션. 되돌림은 test 밖(`afterAll`)에서
 * 도는데 그 자리에는 `page` 가 없어, 세션 쿠키를 들고 API 전용 맥락을 따로 세운다.
 */
let savedProfile: ProfileSnapshot | null = null;
let savedStorage: Awaited<ReturnType<BrowserContext['storageState']>> | null = null;

/**
 * 프로필을 **등록 상태로** 채운다 — 그래야 설정·시작하기의 등록 분야 체크박스가 선다.
 *
 * 이 씨앗이 없으면 게이트가 그 체크박스를 **한 번도 보지 못한다**(실측: 설정 27 → 24). cr N-1 이
 * 가리킨 실물이 바로 그 자리라, 모집단에 넣고도 안 그리면 넣은 뜻이 사라진다. 값은 전부 합성이고
 * 사업자등록번호는 넣지 않는다(계약에 칸이 없다).
 *
 * **프로필은 계정에 하나뿐인 전역 상태다**(cr P-2) — `seedCase` 의 공고 둘처럼 자기 fixture 가 될
 * 수 없다. 여정 하네스는 `workers: 1` 이고 **파일마다 DB 를 되돌리는 자리가 없어서**, 남기면 뒤
 * 여정이 남의 잔류를 전제로 깨진다(오늘 깨지지 않는 이유는 spec 이름의 알파벳 순서뿐이었다).
 * 그래서 **쓰기 전에 읽어 두고** 아래 `afterAll` 이 되돌린다.
 */
async function seedProfile(page: Page, csrf: string): Promise<void> {
  const before = await page.request.get('/api/profile');
  expect(before.status(), '되돌릴 프로필을 읽지 못했다').toBe(200);
  savedProfile = (await before.json()) as ProfileSnapshot;
  savedStorage = await page.context().storageState();

  const write = async (path: string, data: unknown): Promise<void> => {
    const response = await page.request.post(path, { headers: { 'X-Operator-Csrf': csrf }, data });
    expect(response.ok(), `${path}: ${response.status()}`).toBe(true);
  };
  await write('/api/profile/business-details', {
    tradeName: '합성건설 주식회사',
    headquartersRegion: 'SEOUL',
    naraRegistration: 'REGISTERED',
    naraRegisteredFields: ['CONSTRUCTION'],
  });
  await write('/api/profile/qualification-facts', {
    businessTypes: ['건축공사업'],
    licensesDeclared: true,
    licenseNames: ['건설업 등록증'],
    regionTerms: ['서울특별시'],
  });
}

/** 공고 하나를 **결과까지** 민다 — 출하 API 로만(여정의 되돌림·씨앗과 같은 규율). */
async function seedCase(page: Page, csrf: string, noticeNumber: string): Promise<void> {
  const write = async (path: string, data: unknown): Promise<unknown> => {
    const response = await page.request.post(path, { headers: { 'X-Operator-Csrf': csrf }, data });
    expect(response.ok(), `${path}: ${response.status()} ${(await response.text()).slice(0, 160)}`).toBe(true);
    return response.status() === 204 ? null : ((await response.json()) as unknown);
  };

  const listed = await page.request.get('/api/notices', { params: { q: noticeNumber } });
  expect(listed.status(), `${noticeNumber} 검색`).toBe(200);
  const rows = (await listed.json()) as readonly { readonly noticeNumber: string; readonly noticeRound: string }[];
  const target = rows.find((one) => one.noticeNumber === noticeNumber);
  // 없으면 **사유를 말하고** 멈춘다 — 하네스 수집이 달라진 것과 화면이 안 그린 것은 다른 일이다.
  expect(target, `하네스 수집에 ${noticeNumber} 가 없다 — 이 spec 의 fixture 가 사라졌다`).toBeDefined();
  const noticeRound = target!.noticeRound;

  const added = (await write('/api/bid-cases', { noticeNumber, noticeRound, bidKind: 'REAL' })) as {
    readonly caseId: number;
    readonly state: string;
  };
  const caseId = added.caseId;
  const iso = (days: number): string => new Date(Date.now() + days * 86_400_000).toISOString();
  const money = { amountWon: 1_010_000_000, vatTreatment: 'INCLUSIVE' };

  /**
   * **담은 직후의 상태가 길을 가른다**(계약 전이표). 마감이 지난 공고는 담는 순간
   * `DEADLINE_PASSED_UNRECORDED` 라 초안을 만들 수 없고(그 상태는 초안 행의 출발 집합에 없다)
   * **소급 기록**으로 바로 들어간다. 마감 전이면 초안을 거친다.
   *
   * 상태를 묻지 않고 한 길만 밀던 앞 판은 그 자리에서 409 `INVALID_TRANSITION` 이었다 — 하네스
   * 공고 열둘 가운데 마감이 남은 것이 넷뿐이고 그 넷은 다른 여정이 쓴다.
   */
  if (added.state === 'WATCHING') {
    await write('/api/bid-price-requests', { noticeNumber, noticeRound, currentActiveBids: 1 });
    await write(`/api/bid-cases/${caseId}/draft`, { ...money, presentedAt: iso(0) });
  } else {
    expect(
      added.state,
      `${noticeNumber}: 담은 직후 상태가 초안·소급 어느 길도 아니다 — fixture 가 달라졌다`,
    ).toBe('DEADLINE_PASSED_UNRECORDED');
  }
  await write(`/api/bid-cases/${caseId}/record`, { ...money, submittedAt: iso(0) });
  await write(`/api/bid-cases/${caseId}/await-opening`, { openingAt: iso(2) });
  await write(`/api/bid-cases/${caseId}/opening`, { provisionalFirst: true });
  await write(`/api/bid-cases/${caseId}/qualification-review`, { documentDueAt: iso(4) });
  await write(`/api/bid-cases/${caseId}/outcome`, { outcome: 'WON' });
}

/**
 * 씨앗을 **한 번만** 세운다. `workers: 1` 이고 이 파일의 test 들이 차례로 도므로, 상태를 만드는
 * 자리를 하나로 둔다.
 */
let seeded = false;

async function prepare(page: Page): Promise<void> {
  await signIn(page);
  if (seeded) {
    return;
  }
  // CSRF 는 **계약이 그 자리를 둔 operation** 으로 받는다(읽기라 CSRF 면제다). 로그인 응답을
  // 다시 읽는 사본을 만들지 않는다.
  const session = await page.request.get('/api/session');
  expect(session.status(), '세션 조회').toBe(200);
  const { csrf } = (await session.json()) as { readonly csrf: string };
  await seedProfile(page, csrf);
  for (const notice of OWN_NOTICES) {
    await seedCase(page, csrf, notice);
  }
  seeded = true;
}

/**
 * **전역 프로필을 되돌린다**(cr P-2). 화면이 아니라 **출하 API 로** 한다 — 실패 뒤 페이지가 어디에
 * 있는지 알 수 없고, 되돌림은 화면의 능력이 아니라 운영자 설정 행위다(`shared.ts` 의 어휘·금액대
 * 되돌림과 같은 규율).
 *
 * 씨앗이 돌기 전에 죽었으면 **되돌릴 것이 없다** — 그때 여기서 또 실패하면 진단이 teardown 으로
 * 옮겨 간다(원래 실패를 덮지 않는 규율).
 */
test.afterAll(async () => {
  const profile = savedProfile;
  const storage = savedStorage;
  if (profile === null || storage === null) {
    return;
  }
  const api = await apiRequest.newContext({ baseURL: journeyContext().previewUrl, storageState: storage });
  try {
    const session = await api.get('/api/session');
    expect(session.status(), '되돌림이 세션을 읽지 못했다').toBe(200);
    const { csrf } = (await session.json()) as { readonly csrf: string };
    const headers = { 'X-Operator-Csrf': csrf };

    const details = await api.post('/api/profile/business-details', {
      headers,
      data: {
        tradeName: profile.tradeName ?? null,
        headquartersRegion: profile.headquartersRegion ?? null,
        naraRegistration: profile.naraRegistration,
        // 등록 분야·인증서 만료일은 **등록 상태일 때만** 실을 수 있고 그때는 필수다(계약).
        ...(profile.naraRegistration === 'REGISTERED'
          ? {
              naraRegisteredFields: profile.naraRegisteredFields,
              businessCertificateExpiresOn: profile.businessCertificateExpiresOn ?? null,
            }
          : {}),
      },
    });
    expect(details.ok(), `사업 정보를 되돌리지 못했다(${details.status()})`).toBe(true);

    const facts = await api.post('/api/profile/qualification-facts', {
      headers,
      data: {
        businessTypes: profile.businessTypes,
        licensesDeclared: profile.licensesDeclared,
        licenseNames: profile.licenseNames,
        regionTerms: profile.regionTerms,
      },
    });
    expect(facts.ok(), `자격 사실을 되돌리지 못했다(${facts.status()})`).toBe(true);

    // **되돌림까지 단언한다** — 보낸 것이 아니라 **서버에 남은 것**을 본다.
    const read = await api.get('/api/profile');
    expect(read.status(), '되돌린 프로필을 읽지 못했다').toBe(200);
    expect(
      restorableAxes((await read.json()) as ProfileSnapshot),
      '이 spec 이 계정 전역 프로필을 남겼다',
    ).toStrictEqual(restorableAxes(profile));
  } finally {
    await api.dispose();
  }
});

test.describe.configure({ mode: 'serial' });

/**
 * **설정이 이 폭으로 돌고 있는가.** 설정 파일을 믿지 않는다 — 데스크톱 폭으로 바뀌면 전수
 * 단언이 통과해 버리고 이 게이트는 아무것도 재지 않는다.
 */
test('이 레인은 모바일 폭으로 돌고, 워크시트 자리가 이 spec 의 씨앗을 가리킨다', async ({ page }) => {
  await page.goto('/login');

  expect(page.viewportSize()).toStrictEqual({ width: VIEWPORT.width, height: VIEWPORT.height });
  // 경로가 리터럴이라 씨앗과 갈릴 수 있다 — 그 한 자리를 여기서 묶는다(워크시트는 **그 건이
  // 있어야** 값이 선다).
  const worksheet = TOUCH_TARGET_SCREENS.find((one) => one.slotId === 'worksheet');
  expect(worksheet?.path).toBe(`/notices/${OWN_NOTICES[0]}/000`);
});

test('화면마다 보이는 터치 타깃 전수가 44px 이상이고 가로 넘침이 0 이다', async ({ page }) => {
  await prepare(page);
  for (const screen of TOUCH_TARGET_SCREENS) {
    await page.goto(screen.path);
    await expect(page.getByTestId(screen.ready)).toBeVisible();
    // 홈은 **검색 결과가 선 상태**로 잰다 — 빈 목록은 카드 안의 타깃을 하나도 내지 않는다.
    // 각 화면을 한 번만 돌므로 「이미 검색했는가」를 기억할 자리가 없다(cr L-6).
    if (screen.slotId === 'home') {
      await search(page, '합성');
    }
    await page.waitForLoadState('networkidle');

    const { targets, overflow, offscreen, partiallyClipped } = await measure(page);

    // ① **비공허** — 보이는 것 필터가 집합을 비우면 아래 전수 단언이 조용히 초록이 된다.
    expect(targets.length, `${screen.slotId}: 보이는 타깃이 하한 ${screen.minCount} 에 못 미친다`).toBeGreaterThanOrEqual(
      screen.minCount,
    );
    // ② 전수 — 이름으로 고르지 않는다.
    const small = targets.filter((one) => one.height < MIN_TOUCH_PX);
    expect(
      small.map((one) => `${one.tag} ${one.height}px "${one.name}"`),
      `${screen.slotId}: 터치 타깃이 ${MIN_TOUCH_PX}px 에 못 미친다(보이는 전수 ${targets.length})`,
    ).toStrictEqual([]);
    /**
     * ③ 가로 넘침 0 — 모바일에서 옆으로 밀리는 화면을 만들지 않는다.
     *
     * **문서 폭만으로는 모자란다**(cr L-8 대조에서 실측): 뷰포트보다 넓은 **고정 배치** 요소는
     * 흐름 밖이라 `scrollWidth` 를 늘리지 않아, 500px 짜리 하단 탐색을 390px 에 심은 변이가
     * 그대로 초록이었다. 그래서 **타깃 상자 자체가 뷰포트 밖으로 나가는지**를 함께 센다 — 그것이
     * 이 게이트가 재려는 것(손가락이 상자 전체에 닿는가)에 더 가깝다.
     */
    expect(offscreen, `${screen.slotId}: 타깃이 뷰포트 밖으로 나간다`).toStrictEqual([]);
    expect(partiallyClipped, `${screen.slotId}: 타깃에 부분 잘라내기가 걸려 상자로 잴 수 없다`).toStrictEqual([]);
    expect(overflow, `${screen.slotId}: 가로로 ${overflow}px 넘친다`).toBeLessThanOrEqual(0);
  }
});

/**
 * **모집단에서 뺀 것을 다른 자리에서 잰다**(착수 판정 ⓘ). 건너뛰기 링크는 시각 은폐 상태라
 * 위 전수에서 빠진다 — 그 링크가 **포커스됐을 때** 손가락·눈에 닿는 크기인지는 여기서 본다.
 */
test('건너뛰기 링크는 포커스되면 44px 이상이다', async ({ page }) => {
  await prepare(page);
  await page.goto('/');
  await expect(page.getByTestId('screen-home')).toBeVisible();

  await page.keyboard.press('Tab');
  const focused = await page.evaluate(() => {
    const node = document.activeElement;
    if (node === null) {
      return null;
    }
    const box = node.getBoundingClientRect();
    return { text: (node.textContent ?? '').trim(), height: Math.round(box.height * 10) / 10 };
  });

  expect(focused?.text, '첫 Tab 이 건너뛰기 링크에 닿지 않았다').toBe('본문으로 건너뛰기');
  expect(focused?.height).toBeGreaterThanOrEqual(MIN_TOUCH_PX);
});
