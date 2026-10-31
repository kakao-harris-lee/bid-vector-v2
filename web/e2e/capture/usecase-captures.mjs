// 활용사례 등록용 화면 캡처(M7/7E-3b, D-7E3b-10) — **수동 실행**이다(`npm run capture:usecase`).
//
// **여정 레인과 분리돼 있다**: 이 파일은 `playwright.journey.config.ts` 의 `testDir`
// (`e2e/journey`) **밖**이고 이름도 `*.spec.ts` 가 아니라, 여정 러너가 구조적으로 수집하지
// 못한다(이름 규약 하나에 기대지 않는다). CI 에도 걸지 않는다 — 캡처는 포털 제출 때 손으로
// 새로 뜨는 산출물이고, 매 PR 에 브라우저로 12 장을 뜨는 비용을 CI 가 물 이유가 없다.
//
// 찍는 것은 **하네스**다(실 app.jar · fake KONEPS · 버릴 Postgres · 합성 fixture). 실 데이터·
// 실 계정이 0 인 것이 그 선택의 요점이다(D-7E-6).
//
// **개인정보 단언이 캡처보다 먼저다.** PNG 는 텍스트로 검사할 수 없으므로 찍기 **직전** DOM 에
// 같은 질문을 던지고, 하나라도 걸리면 **파일을 쓰지 않고 멈춘다** — 찍고 나서 지우는 순서가
// 아니다(지우기 전에 프로세스가 죽으면 파일이 남는다).
import { spawn, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from '@playwright/test';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..', '..');
const repoRoot = resolve(webRoot, '..');
const OUT_DIR = resolve(repoRoot, 'docs', 'usecase', 'captures');
/**
 * **성공한 실행만 산출물을 건드린다**(r1 CR L-2 · v L-5). 앞 판은 `OUT_DIR` 를 먼저 비우고
 * 화면마다 썼다 — 네 번째 화면에서 던지면 **커밋돼 있던 열두 장은 이미 지워진 뒤**이고 앞 세
 * 장만 manifest 없이 남았다(실측). 스테이징에 전부 쓰고 **마지막에 한 번** 바꿔 끼운다.
 *
 * 같은 파일시스템에 둔다 — `rename` 은 경계를 넘지 못한다.
 */
const STAGING_DIR = `${OUT_DIR}.staging`;
const CONTEXT_PATH = resolve(tmpdir(), 'bidvector-7e1-journey-context.json');

/**
 * 크기 상한(D-7E3b-10). 저장소가 들이는 **첫 이미지 바이너리**라 상한을 명시하지 않으면 다음
 * 라운드가 조용히 키운다. 넘으면 **실패**다 — 초록인 채로 큰 파일이 들어오지 않게.
 */
const MAX_FILE_BYTES = 300 * 1024;
const MAX_TOTAL_BYTES = 2 * 1024 * 1024;

/** 데스크톱·모바일 각 하나(§6.5 「모바일 우선 반응형 하나」를 보이는 것이 캡처의 일이다). */
const VIEWPORTS = [
  { id: 'desktop', width: 1280, height: 800 },
  { id: 'mobile', width: 390, height: 844 },
];

/** 공고 하나로 워크시트·입찰함·결과를 채운다 — 빈 화면만 찍으면 활용사례로 쓸 수 없다. */
const NOTICE = 'SYN-7E3-RESULT';
/** 다른 탭에 설 건의 공고 — 상태 탭이 무엇을 하는 자리인지 캡처가 보이려면 계수가 둘 이상이어야 한다. */
const SECOND_NOTICE = 'SYN-7E2-BIDBOX';

/**
 * 찍는 화면 여섯. **설정은 없다**(D-7E3b-10) — 「화면 다섯 + 알림함」 밖이고 프로필 화면은
 * 사업 정보 표면이라 캡처 대상으로 두지 않는다.
 *
 * **항목마다 `positive` 가 필수다**(r1 CR H-2). `ready` 는 「화면이 섰다」일 뿐 **빈 얼굴에도
 * 오류 얼굴에도 선다** — 실제로 화면 D 가 빈 탭인 채 상한·개인정보 술어·`ready` 를 모두 지나
 * 커밋됐다(r1 H-1). `positive` 는 **내용이 닿았을 때만** 서는 표지이고, 없으면 아래 단언이
 * 스크립트를 거부한다(다음 화면이 추가될 때 같은 빈틈이 생기지 않게).
 *
 * 알림함의 `positive` 가 **빈 얼굴**인 것이 정직한 선택이다 — 트리거가 오늘 발화하지 않으므로
 * 「항목이 있다」가 아니라 「빈 얼굴이 섰다」가 이 화면의 내용이다(서비스 설명 §5 와 같은 사실).
 */
const SCREENS = [
  { id: 'a-start', path: '/start', ready: 'screen-onboarding', positive: () => 'onboarding-items' },
  { id: 'b-notices', path: '/', ready: 'screen-home', positive: () => `notice-card-${NOTICE}` },
  {
    id: 'c-worksheet',
    path: `/notices/${NOTICE}/000`,
    ready: 'screen-worksheet',
    positive: () => 'worksheet-title',
  },
  {
    id: 'd-bid-box',
    // **그 건이 보이는 탭으로** 간다(r1 H-1 · v M-2). 맨 `/bids` 는 첫 탭(관심)을 열고 씨앗 건은
    // 결과까지 밀려 「종료」에 있다 — 앞 판의 캡처는 「이 묶음에 건이 없습니다」였다. 화면 E 의
    // 링크가 이미 같은 규율을 지킨다(`?tab=${tabOf(state)}`); 캡처만 그 밖에 있었다.
    path: '/bids?tab=closed',
    ready: 'screen-bid-box',
    positive: (seed) => `bid-case-${seed.wonCaseId}`,
  },
  {
    id: 'e-results',
    path: '/results',
    ready: 'screen-results',
    positive: (seed) => `result-case-${seed.wonCaseId}`,
  },
  { id: 'inbox', path: '/inbox', ready: 'screen-inbox', positive: () => 'inbox-empty' },
];

const missingPositive = SCREENS.filter((one) => typeof one.positive !== 'function').map((one) => one.id);
if (missingPositive.length > 0) {
  throw new Error(
    `양성 표지가 없는 화면: ${missingPositive.join(', ')} — ` +
      '`ready` 는 빈 얼굴에도 서므로 그것만으로는 「내용이 닿았다」를 재지 못한다(r1 CR H-2).',
  );
}

function log(line) {
  process.stdout.write(`capture: ${line}\n`);
}

function head() {
  const shown = spawnSync('git', ['-C', repoRoot, 'rev-parse', 'HEAD'], { encoding: 'utf8' });
  if (shown.status !== 0) {
    throw new Error('HEAD 를 읽지 못했다 — 캡처가 어느 판의 화면인지 적을 수 없다');
  }
  return shown.stdout.trim();
}

async function waitFor(label, probe, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    if (await probe()) {
      return;
    }
    if (Date.now() > deadline) {
      throw new Error(`${label} 를 ${timeoutMs}ms 안에 기다리지 못했다`);
    }
    await new Promise((done) => setTimeout(done, 400));
  }
}

/** 하네스를 띄운다 — Playwright 의 `webServer` 가 여정 레인에서 하는 일과 같은 자리다. */
async function bringUpHarness() {
  /**
   * **맥락 파일을 여기서 지우지 않는다**(r1 v M-4 — 재현 확인). 그 파일은 공유 경로이고,
   * 하네스 자신이 「락을 얻었는가 — 공유 경로를 정리할 자격이 이것 하나다」로 둔다. 락을 잡기
   * **전**에 지우면 다른 레인의 여정 실행 맥락이 사라진다(그 실행은 ENOENT 로 전건 실패한다).
   * 하네스가 락을 얻은 뒤 스스로 지우고 다시 쓴다 — 이 자리는 **기다리기만** 한다.
   */
  const startedAt = Date.now();
  const child = spawn(process.execPath, [resolve(webRoot, 'e2e', 'harness', 'journey-up.mjs')], {
    cwd: webRoot,
    stdio: ['ignore', 'inherit', 'inherit'],
  });
  let died = null;
  child.on('exit', (code) => {
    died = code;
  });
  await waitFor(
    '하네스 맥락',
    async () => {
      if (died !== null) {
        throw new Error(`하네스가 먼저 죽었다(exit=${died})`);
      }
      // 지우지 않으므로 **묵은 파일을 이 실행의 것으로 읽을 수 있다** — 기동 뒤에 쓰인 것만 받는다.
      return existsSync(CONTEXT_PATH) && statSync(CONTEXT_PATH).mtimeMs >= startedAt;
    },
    300_000,
  );

  /**
   * **맥락 파일이 준비의 전부가 아니다**(실측): 하네스는 `vite preview` 를 **띄우고** 맥락을
   * 적으므로, 그 파일이 생긴 순간 포트는 아직 안 열려 있을 수 있다. 여정 레인은 Playwright 의
   * `webServer.url` 이 이 기다림을 대신 해 줬는데 이 스크립트에는 그 자리가 없어, 첫 이동이
   * `ERR_CONNECTION_REFUSED` 로 죽었다.
   */
  const { previewUrl } = JSON.parse(readFileSync(CONTEXT_PATH, 'utf8'));
  await waitFor(
    'preview 응답',
    async () => {
      if (died !== null) {
        throw new Error(`하네스가 먼저 죽었다(exit=${died})`);
      }
      try {
        return (await fetch(previewUrl, { signal: AbortSignal.timeout(2000) })).ok;
      } catch {
        return false;
      }
    },
    60_000,
  );
  return child;
}

/**
 * 출하 API 로만 민다 — 캡처가 DB 에 직접 넣지 않는다(여정과 같은 규율).
 *
 * **모든 답의 상태를 본다.** 보지 않았던 앞 판은 CSRF 헤더가 빠져 담기가 403 이었는데도 끝까지
 * 돌아 `caseId` 가 `undefined` 인 채 빈 화면을 찍었다(실측) — 조용한 실패가 **초록인 산출물**이
 * 됐다. 쓰기는 그 헤더를 요구하므로 로그인 응답이 준 값을 싣는다.
 */
function apiCall(context, baseUrl, csrf) {
  return async (method, path, body) => {
    const response = await context.request.fetch(`${baseUrl}${path}`, {
      method,
      headers: method === 'GET' ? {} : { 'X-Operator-Csrf': csrf },
      ...(body === undefined ? {} : { data: body }),
    });
    if (!response.ok()) {
      throw new Error(`${method} ${path}: ${response.status()} ${(await response.text()).slice(0, 200)}`);
    }
    return response;
  };
}

async function seedCase(call) {
  const rows = await (await call('GET', `/api/notices?q=${encodeURIComponent(NOTICE)}`)).json();
  const notice = rows.find((one) => one.noticeNumber === NOTICE);
  if (notice === undefined) {
    throw new Error(`fixture 공고 ${NOTICE} 가 검색에 없다 — 하네스 수집이 달라졌다`);
  }
  const round = notice.noticeRound;

  const added = await (
    await call('POST', '/api/bid-cases', { noticeNumber: NOTICE, noticeRound: round, bidKind: 'REAL' })
  ).json();
  const caseId = added.caseId;
  if (typeof caseId !== 'number') {
    throw new Error(`담기 응답에 caseId 가 없다: ${JSON.stringify(added).slice(0, 200)}`);
  }
  const iso = (days) => new Date(Date.now() + days * 86_400_000).toISOString();

  await call('POST', '/api/bid-price-requests', {
    noticeNumber: NOTICE,
    noticeRound: round,
    currentActiveBids: 1,
  });
  const money = { amountWon: 1_350_000_000, vatTreatment: 'INCLUSIVE' };
  await call('POST', `/api/bid-cases/${caseId}/draft`, { ...money, presentedAt: iso(0) });
  await call('POST', `/api/bid-cases/${caseId}/record`, { ...money, submittedAt: iso(0) });
  await call('POST', `/api/bid-cases/${caseId}/await-opening`, { openingAt: iso(3) });
  await call('POST', `/api/bid-cases/${caseId}/opening`, { provisionalFirst: true });
  await call('POST', `/api/bid-cases/${caseId}/qualification-review`, { documentDueAt: iso(5) });
  await call('POST', `/api/bid-cases/${caseId}/outcome`, { outcome: 'WON' });
  log(`fixture 건 ${caseId} 를 결과까지 밀었다`);
  return caseId;
}

/**
 * 화면 D 의 다른 탭에도 건을 하나 둔다(r1 v M-2 「가능하면 진행 중 상태의 건을 하나 더」) —
 * 탭 계수가 전부 0 이면 상태 탭이 무엇을 하는 자리인지 캡처가 보이지 못한다. 결과까지 밀지
 * 않으므로 「투찰함」에 선다.
 */
async function seedSecondCase(call, notice) {
  const rows = await (await call('GET', `/api/notices?q=${encodeURIComponent(notice)}`)).json();
  const found = rows.find((one) => one.noticeNumber === notice);
  if (found === undefined) {
    throw new Error(`fixture 공고 ${notice} 가 검색에 없다`);
  }
  const added = await (
    await call('POST', '/api/bid-cases', {
      noticeNumber: notice,
      noticeRound: found.noticeRound,
      bidKind: 'REAL',
    })
  ).json();
  const money = { amountWon: 980_000_000, vatTreatment: 'INCLUSIVE' };
  const now = new Date().toISOString();
  await call('POST', '/api/bid-price-requests', {
    noticeNumber: notice,
    noticeRound: found.noticeRound,
    currentActiveBids: 2,
  });
  await call('POST', `/api/bid-cases/${added.caseId}/draft`, { ...money, presentedAt: now });
  await call('POST', `/api/bid-cases/${added.caseId}/record`, { ...money, submittedAt: now });
  log(`fixture 건 ${added.caseId} 를 제출함까지 밀었다(다른 탭)`);
  return added.caseId;
}

/**
 * 화면 A 를 **채운 상태로** 찍는다(r1 CR L-3). 0/8 짜리 빈 설정은 새 운영자가 보는 정직한
 * 화면이지만 활용사례로는 흐름을 보이지 못한다.
 *
 * 값은 전부 **합성**이고 사업자등록번호는 넣지 않는다 — 계약에 그 칸이 없다(D-7E2-7). 이 호출
 * 뒤 상호가 입력칸에 실리므로 **개인정보 술어가 폼 값까지 보도록 고친 뒤에야** 의미가 있다
 * (CR M-2 가 L-3 의 선행인 이유).
 */
async function seedProfile(call) {
  await call('POST', '/api/profile/business-details', {
    tradeName: '합성건설 주식회사',
    headquartersRegion: 'SEOUL',
    naraRegistration: 'REGISTERED',
    naraRegisteredFields: ['CONSTRUCTION'],
  });
  await call('POST', '/api/profile/qualification-facts', {
    businessTypes: ['건축공사업'],
    licensesDeclared: true,
    licenseNames: ['건설업 등록증'],
    regionTerms: ['서울특별시'],
  });
  log('프로필 합성값을 채웠다(화면 A)');
}

/**
 * 캡처 **직전** DOM 에 거는 단언(D-7E-6). 걸리면 그 자리에서 던진다 — 파일은 쓰이지 않는다.
 *
 * 사업자번호 꼴을 **정규식으로** 보는 이유: 화면에 그 값이 설 자리는 계약상 없지만(입력칸이
 * 없다), 「없을 것이다」는 측정이 아니다. **비밀 문구는 이 실행의 난수**이고 로그인 이름은
 * 하네스의 고정값이다(r1 v L-7 정정) — 둘 다 캡처에 실리면 그대로 산출물에 남는다.
 */
async function assertNoPrivateData(page, screenId, secrets) {
  const body = await page.locator('body').innerText();
  /**
   * **`innerText` 만으로는 모자란다**(r1 CR M-2). 그것은 입력칸의 `value` 와 `placeholder` ·
   * `title` · `alt` · `aria-label` 을 **담지 않는데 스크린샷은 담는다** — 술어와 산출물이 보는
   * 것이 달랐다. 화면 A 를 채워 찍는 순간 그 빈틈이 실물이 된다(상호가 PNG 에 남고 술어는 초록).
   */
  /* global document -- 아래 콜백들은 **브라우저 안**에서 돈다(이 파일의 나머지는 Node 다). */
  const attributes = await page.evaluate(() =>
    [...document.querySelectorAll('input, textarea, select, [title], [alt], [aria-label], [placeholder]')]
      .flatMap((element) => [
        element.value ?? '',
        element.getAttribute('placeholder') ?? '',
        element.getAttribute('title') ?? '',
        element.getAttribute('alt') ?? '',
        element.getAttribute('aria-label') ?? '',
      ])
      .join('\n'),
  );
  const cookie = await page.evaluate(() => document.cookie);
  // **보이는 글과 폼 값·속성을 한 문자열로** 묶어 같은 네 질문을 던진다.
  const shown = `${body}\n${attributes}`;
  const problems = [];
  if (/\b\d{3}-\d{2}-\d{5}\b/u.test(shown)) {
    problems.push('사업자등록번호 꼴(3-2-5)이 화면에 있다');
  }
  if (shown.includes(secrets.passphrase)) {
    problems.push('실행 비밀 문구가 화면에 있다');
  }
  if (shown.includes(secrets.loginName)) {
    problems.push('로그인 이름이 화면에 있다');
  }
  if (cookie.includes('bidvector_session')) {
    problems.push('세션 쿠키를 스크립트가 읽을 수 있다(HttpOnly 가 아니다)');
  }
  if (problems.length > 0) {
    throw new Error(`${screenId}: 캡처를 멈춘다 — ${problems.join(' · ')}`);
  }
}

async function main() {
  const harness = await bringUpHarness();
  const browser = await chromium.launch();
  try {
    const run = JSON.parse(readFileSync(CONTEXT_PATH, 'utf8'));
    const secrets = { loginName: run.loginName, passphrase: run.passphrase };

    // 스테이징만 비운다 — 커밋된 산출물은 **성공한 뒤에** 바뀐다(r1 CR L-2 · v L-5).
    rmSync(STAGING_DIR, { recursive: true, force: true });
    mkdirSync(STAGING_DIR, { recursive: true });

    const files = [];
    let seed = null;
    for (const viewport of VIEWPORTS) {
      const context = await browser.newContext({
        viewport: { width: viewport.width, height: viewport.height },
        baseURL: run.previewUrl,
      });
      const page = await context.newPage();

      await page.goto('/login');
      await page.fill('input[name="loginName"]', run.loginName);
      await page.fill('input[name="passphrase"]', run.passphrase);
      const [session] = await Promise.all([
        page.waitForResponse((one) => one.url().includes('/api/session') && one.request().method() === 'POST'),
        page.getByRole('button', { name: '로그인' }).click(),
      ]);
      // 계약이 **201** 로 낸다(실측) — 한 값으로 못박지 않고 2xx 를 받는다.
      if (!session.ok()) {
        throw new Error(`로그인이 ${session.status()} 다 — 하네스 자격이 달라졌다`);
      }
      const { csrf } = await session.json();

      // 씨앗은 **첫 폭에서 한 번**만 — 같은 DB 를 두 폭이 공유한다.
      if (seed === null) {
        const call = apiCall(context, run.previewUrl, csrf);
        await seedProfile(call);
        const wonCaseId = await seedCase(call);
        await seedSecondCase(call, SECOND_NOTICE);
        seed = { wonCaseId };
      }

      for (const screen of SCREENS) {
        await page.goto(screen.path);
        await page.getByTestId(screen.ready).waitFor({ state: 'visible' });
        // 화면 B 는 **검색 결과**를 찍는다 — 그것이 이 화면의 일이고, 빈 홈은 흐름을 못 보인다.
        if (screen.id === 'b-notices') {
          await page.fill('input[name="q"]', NOTICE);
          await page.getByRole('button', { name: '검색' }).click();
        }
        // 적재 중 화면을 찍지 않는다 — 활용사례에 스켈레톤을 내보낼 이유가 없다.
        await page.waitForLoadState('networkidle');

        /**
         * **양성 표지를 먼저 기다린다**(r1 CR H-2). `ready` 는 빈 얼굴에도 서므로 그것만으로는
         * 「내용이 닿았다」를 재지 못한다 — 실제로 화면 D 가 빈 탭인 채 모든 술어를 지났다.
         * 표지는 화면마다 다르고 `SCREENS` 가 든다.
         */
        await page.getByTestId(screen.positive(seed)).waitFor({ state: 'visible' });

        await assertNoPrivateData(page, `${screen.id}-${viewport.id}`, secrets);

        const name = `${screen.id}-${viewport.id}.png`;
        const bytes = await page.screenshot({ fullPage: true });
        if (bytes.length > MAX_FILE_BYTES) {
          throw new Error(
            `${name} 가 ${bytes.length} 바이트로 상한 ${MAX_FILE_BYTES} 를 넘는다 — 상한을 올리는 것은 설계 변경이다`,
          );
        }
        writeFileSync(resolve(STAGING_DIR, name), bytes);
        files.push({
          path: name,
          screen: screen.id,
          viewport: viewport.id,
          width: viewport.width,
          height: viewport.height,
          bytes: bytes.length,
          sha256: createHash('sha256').update(bytes).digest('hex'),
        });
        log(`${name} (${bytes.length} 바이트)`);
      }
      await context.close();
    }

    const total = files.reduce((sum, one) => sum + one.bytes, 0);
    if (total > MAX_TOTAL_BYTES) {
      throw new Error(`합 ${total} 바이트가 상한 ${MAX_TOTAL_BYTES} 를 넘는다`);
    }

    /**
     * 손으로 바꿔치운 PNG 는 해시가 어긋나고, 화면이 바뀐 뒤의 낡음은 `head` 로 읽는다. 이미지
     * 차분 게이트는 두지 않는다 — 캡처는 제출 때 새로 뜨는 산출물이고, 그 비용을 CI 가 물
     * 이유가 없다(알려진 제한).
     */
    writeFileSync(
      resolve(STAGING_DIR, 'manifest.json'),
      `${JSON.stringify({ capturedAt: new Date().toISOString(), head: head(), totalBytes: total, files }, null, 2)}\n`,
    );

    // **여기서 처음으로** 커밋된 산출물을 건드린다 — 위에서 던졌다면 그것은 그대로 남아 있다.
    rmSync(OUT_DIR, { recursive: true, force: true });
    renameSync(STAGING_DIR, OUT_DIR);
    log(`끝 — ${files.length} 장 · 합 ${total} 바이트`);
  } finally {
    // 실패했으면 스테이징만 치운다(산출물은 건드리지 않았다).
    rmSync(STAGING_DIR, { recursive: true, force: true });
    await browser.close();
    harness.kill('SIGTERM');
  }
}

await main();
