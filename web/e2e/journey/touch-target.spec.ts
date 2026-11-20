import { expect, test, type Page } from '@playwright/test';

import { BUTTON_TONES, controlClass } from '../../src/components/Button';

import { journeyContext, search, signIn } from './shared';
import { computeBorderColorsInPage, measureControlBordersInPage } from './controlBorderMeasure';
import { measureInPage, type Measurement } from './touchMeasure';

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
/**
 * 컨트롤 경계로 **허용되는 클래스**(v M-1 · cr L-2 · cr M-1) — 색이 아니라 **클래스**를 모은다.
 *
 * 색을 Node 에서 글자로 조립하던 앞 판은 틀렸다(cr M-1 실측): 출하 CSS 의 어조 테두리는
 * `color-mix(in oklab, …)` 이고 브라우저는 그것을 `oklab(… / 0.4)` 로 돌려준다 — 조립한
 * `rgba(…)` 와 **영영 맞지 않는다**. 그래서 허용 색은 **브라우저가 계산하게** 한다(아래
 * `computeBorderColorsInPage`). 재는 쪽과 재어지는 쪽이 같은 파서를 지난다.
 *
 * 허용은 둘이다 — **컨트롤 선**(중립 경계)과 **공유 버튼의 어조 표가 실제로 쓰는 경계 클래스**
 * (`danger` 의 경고 테두리). **카드 선은 들어 있지 않다** — 그것이 이 축이 잡으려는 값이다.
 * 어조 글자색 **전수**를 받지도 않는다(v r2-low — `accent-ink` 는 흰색이라 보이지 않는 경계다).
 */
function allowedBorderClasses(): readonly string[] {
  const classes = ['border-control-line'];
  let fromTable = 0;
  for (const tone of BUTTON_TONES) {
    for (const token of controlClass(tone, 'md').split(/\s+/u)) {
      if (!token.startsWith('border-') || token === 'border-control-line') {
        continue;
      }
      classes.push(token);
      fromTable += 1;
    }
  }
  if (fromTable === 0) {
    throw new Error('어조 표에서 경계 클래스를 하나도 읽지 못했다 — 허용 집합이 공허하다');
  }
  return classes;
}

const ALLOWED_BORDER_CLASSES = allowedBorderClasses();

/** 측정은 **공용 자리**를 지난다(`touchMeasure.ts`) — 합성 대조가 같은 함수를 본다. */
const measure = async (page: Page): Promise<Measurement> => page.evaluate(measureInPage);

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
  /**
   * 홈은 **두 상태로** 선다(M14/14B — M13 잔여 §12.3 (w) ⓗ).
   *
   * 앞 판은 측정 **전에** 검색을 걸어 **검색 결과 상태만** 쟀다. 그런데 운영자가 매일 여는 것은
   * 기본 상태(제안 목록)이고, 그 상태의 카드·칩은 이 게이트의 눈 밖이었다 — 거기에 하한에 못
   * 미치는 컨트롤이 있어도 아무도 붉지 않았다.
   *
   * 화면 모집단은 자리 표와 **집합 등식**이라(`journey-screen-table.test.mjs`) 행을 더할 수 없다.
   * 그래서 **한 행 안에서 두 상태**를 돈다 — `minCount` 는 기본 상태의 하한이고, `searchState` 가
   * 그 뒤에 이어 도는 둘째 상태와 그 하한을 든다. 검색 상태의 하한 8 은 **앞 판 그대로**다
   * (하한은 올리기만 한다 — 실측 49 라 여유가 넉넉하고, 그 수를 하한으로 박으면 카드 속내가
   * 바뀔 때마다 이 줄이 흔들린다).
   *
   * 기본 상태의 하한 10 은 **실측 12 보다 낮게** 둔다(`borderedControls` 하한과 같은 판단).
   * 이 수가 하는 일은 「보이는 것」 필터가 집합을 비우지 않았음을 보는 것이고, 제안 목록은
   * 계정의 감시 조건에 매여 **한 장 늘고 주는** 자리라 실측값을 박으면 잡음으로 붉는다.
   * 셸의 하단 탭 다섯과 검색 폼만으로도 이 하한을 넘으므로 공허해지지 않는다.
   */
  {
    slotId: 'home',
    path: '/',
    ready: 'screen-home',
    minCount: 10,
    searchState: { minCount: 8, query: '합성' },
  },
  // 경로를 **문자열 리터럴**로 적는다 — 등식 게이트가 AST 로 읽는 자리이고, 식으로 적으면 그
  // 게이트가 값을 읽지 못한다. 씨앗과 어긋나지 않는 것은 아래 첫 단언이 든다.
  // M13/13C — 금액 복사 버튼이 늘어 **다시 조였다**(실측 8 · 여유 0). 하한은 올리기만 한다.
  { slotId: 'worksheet', path: '/notices/SYN-M9-OPEN-NOT-OPENED/000', ready: 'screen-worksheet', minCount: 8 },
  { slotId: 'bid-box', path: '/bids?tab=closed', ready: 'screen-bid-box', minCount: 11 },
  { slotId: 'results', path: '/results', ready: 'screen-results', minCount: 7 },
  { slotId: 'inbox', path: '/inbox', ready: 'screen-inbox', minCount: 12 },
  { slotId: 'settings', path: '/settings', ready: 'screen-settings', minCount: 27 },
  // 로그인 자리에는 **탐색을 그리지 않는다**(착수 판정) — 그래서 하한이 폼의 버튼 하나다.
  { slotId: 'login', path: '/login', ready: 'screen-login', minCount: 1 },
  { slotId: 'not-found', path: '/no-such-place', ready: 'screen-not-found', minCount: 5 },
] as const;

/**
 * 화면 하나가 **서는 상태** — 들어가는 걸음과 그 상태의 하한.
 *
 * 상태가 둘인 화면은 자기 행이 `searchState` 를 **선언한다**. 루프가 자리 이름을 열거하지 않는
 * 것이 요점이다 — 뒤 레인이 다른 화면에 둘째 상태를 붙여도 루프를 고치지 않는다.
 */
interface ScreenState {
  /** 실패 메시지에 붙는 이름. 상태가 하나뿐인 화면은 빈 글자라 앞 판과 같은 메시지가 선다. */
  readonly id: string;
  readonly minCount: number;
  readonly enter: (page: Page) => Promise<void>;
}

function statesOf(screen: (typeof TOUCH_TARGET_SCREENS)[number]): readonly ScreenState[] {
  const base: ScreenState = {
    id: 'searchState' in screen ? '기본' : '',
    minCount: screen.minCount,
    enter: async () => undefined,
  };
  if (!('searchState' in screen)) {
    return [base];
  }
  const { minCount, query } = screen.searchState;
  return [
    base,
    {
      id: '검색 결과',
      minCount,
      // 빈 목록은 카드 안의 타깃을 하나도 내지 않는다 — 그래서 둘째 상태는 **검색을 지난다**.
      enter: async (page) => {
        await search(page, query);
      },
    },
  ];
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
 * 이 spec 이 **쓰기 전에** 읽어 둔 프로필.
 *
 * **세션은 들고 다니지 않는다**: 로그인은 그 계정의 **살아 있는 세션을 폐기한다**(서버의 로그인
 * 완료가 세션 등록·자격 확인·기존 세션 폐기를 한 트랜잭션으로 묶는다). 그래서 앞 test 에서 떠
 * 둔 쿠키는 **뒤 test 의 로그인이 죽인다** — 그 쿠키로 되돌리려던 앞 판이 `afterAll` 에서
 * 401 이었다(실측). 되돌림은 자기 로그인을 새로 한다.
 */
let savedProfile: ProfileSnapshot | null = null;

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
test.afterAll(async ({ browser }) => {
  const profile = savedProfile;
  if (profile === null) {
    return;
  }
  // 되돌림의 **자기 자리**를 연다 — 이 hook 에는 test 의 `page` 가 없고, 앞 test 의 쿠키는
  // 뒤 test 의 로그인이 이미 죽였다. 설정의 기준 주소는 이 hook 에 실리지 않으므로 하네스가
  // 적어 둔 값을 그대로 준다.
  const page = await browser.newPage({ baseURL: journeyContext().previewUrl });
  try {
    await signIn(page);
    const api = page.request;
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
    await page.close();
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
  /**
   * 경계를 **그리는** 컨트롤의 전수(v M-1 의 비공허). 화면마다 세지 않는다 — 입찰함처럼
   * 테두리 없는 버튼만 선 화면이 있어서 화면별 하한은 거짓으로 붉는다. 모집단 전체로 센다.
   */
  let borderedControls = 0;
  /**
   * **그려진 경계에 실제로 맞은 허용 색**(cr M-1). 앞 판은 「표에서 읽은 색 수」를 셌는데, 그
   * 수는 화면을 한 번도 보지 않고도 선다 — 허용이 틀려도(색 꼴이 어긋나도) 알 수 없었다.
   */
  let matchedAllowed = new Set<string>();
  for (const screen of TOUCH_TARGET_SCREENS) {
    for (const state of statesOf(screen)) {
    const where = `${screen.slotId}${state.id === '' ? '' : `/${state.id}`}`;
    await page.goto(screen.path);
    await expect(page.getByTestId(screen.ready)).toBeVisible();
    await state.enter(page);
    await page.waitForLoadState('networkidle');

    const { targets, overflow, offscreen, partiallyClipped } = await measure(page);

    /**
     * **컨트롤의 경계가 그려진 색**(v M-1 · cr L-2). AST 축은 className **리터럴**만 보므로
     * 클래스 글자가 상수나 헬퍼에 들어가면 아무 말도 하지 않는다 — 이 층은 브라우저가 실제로
     * 그린 색을 본다. 허용 집합은 `index.css` 에서 **유도한다**(값을 여기 복사하지 않는다):
     * 컨트롤 선이거나 어조 자신의 글자색이다. 카드 선은 그 집합에 없다.
     */
    // 허용 색은 **그 페이지에서** 계산한다 — 출하 CSS 와 같은 파서를 지난다(cr M-1).
    const allowed = new Set([
      ...(await page.evaluate(computeBorderColorsInPage, ALLOWED_BORDER_CLASSES)),
      'rgba(0, 0, 0, 0)',
      'transparent',
    ]);
    const borders = await page.evaluate(measureControlBordersInPage);
    const drawn = borders.filter((one) => one.width > 0);
    borderedControls += drawn.length;
    matchedAllowed = new Set([...matchedAllowed, ...drawn.map((one) => one.color).filter((one) => allowed.has(one))]);
    const wrong = drawn.filter((one) => !allowed.has(one.color));
    expect(
      wrong.map((one) => `${one.tag}[${one.testId}] ${one.color} "${one.name}"`),
      `${where}: 컨트롤 경계가 허용 토큰 밖의 색이다(카드 선을 쓴 자리일 수 있다)`,
    ).toStrictEqual([]);

    // ① **비공허** — 보이는 것 필터가 집합을 비우면 아래 전수 단언이 조용히 초록이 된다.
    expect(targets.length, `${where}: 보이는 타깃 ${targets.length} 이 하한 ${state.minCount} 에 못 미친다`).toBeGreaterThanOrEqual(
      state.minCount,
    );
    /**
     * ② 전수 — 이름으로 고르지 않는다.
     *
     * **두 변을 함께 잰다**(M14/14A — M13 잔여 §12.3 (w) ⓑ). 앞 판은 **세로만** 보았고, 측정
     * 함수는 가로도 내는데 아무도 단언하지 않았다. 짧은 라벨의 알약 버튼은 세로만 키우면
     * 44×40 꼴이 되어 손가락이 옆으로 빗나간다 — 하한은 「최소 **변**」이므로 두 변 모두다.
     * 이 축을 더하는 것은 하한을 **올리는** 변경이다(계약 「올리기만」).
     */
    const small = targets.filter((one) => one.height < MIN_TOUCH_PX || one.width < MIN_TOUCH_PX);
    expect(
      small.map((one) => `${one.tag} ${one.width}x${one.height}px "${one.name}"`),
      `${where}: 터치 타깃의 변이 ${MIN_TOUCH_PX}px 에 못 미친다(보이는 전수 ${targets.length})`,
    ).toStrictEqual([]);
    /**
     * ③ 가로 넘침 0 — 모바일에서 옆으로 밀리는 화면을 만들지 않는다.
     *
     * **문서 폭만으로는 모자란다**(cr L-8 대조에서 실측): 뷰포트보다 넓은 **고정 배치** 요소는
     * 흐름 밖이라 `scrollWidth` 를 늘리지 않아, 500px 짜리 하단 탐색을 390px 에 심은 변이가
     * 그대로 초록이었다. 그래서 **타깃 상자 자체가 뷰포트 밖으로 나가는지**를 함께 센다 — 그것이
     * 이 게이트가 재려는 것(손가락이 상자 전체에 닿는가)에 더 가깝다.
     */
    expect(offscreen, `${where}: 타깃이 뷰포트 밖으로 나간다`).toStrictEqual([]);
    expect(partiallyClipped, `${where}: 타깃에 부분 잘라내기가 걸려 상자로 잴 수 없다`).toStrictEqual([]);
    expect(overflow, `${where}: 가로로 ${overflow}px 넘친다`).toBeLessThanOrEqual(0);
    }
  }

  /**
   * **비공허(v M-1)** — 경계 색 단언이 **무엇인가를 실제로 봤는가**. 모집단 전체로 센다:
   * 화면마다 세면 테두리 없는 버튼만 선 화면(입찰함)에서 거짓으로 붉는다(실측).
   * 하한은 오늘의 수보다 넉넉히 낮게 두어 화면이 바뀔 때 잡음으로 흔들리지 않는다.
   */
  expect(borderedControls, '경계를 그리는 컨트롤을 하나도 보지 못했다 — 색 단언이 공허하다').toBeGreaterThan(10);
  /**
   * **허용 색이 실제로 쓰였는가**(cr M-1). 이 수가 0 이면 허용 집합이 그려진 경계와 한 번도
   * 맞지 않았다는 뜻이고, 그때 위 전수 단언은 「전부 위반」이거나 「허용이 공허」 둘 중 하나다.
   */
  expect(matchedAllowed.size, '허용 색이 그려진 경계와 한 번도 맞지 않았다').toBeGreaterThan(0);
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
