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
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { chromium } from '@playwright/test';

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..', '..');
const repoRoot = resolve(webRoot, '..');
const OUT_DIR = resolve(repoRoot, 'docs', 'usecase', 'captures');
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

/**
 * 찍는 화면 여섯. **설정은 없다**(D-7E3b-10) — 「화면 다섯 + 알림함」 밖이고 프로필 화면은
 * 사업 정보 표면이라 캡처 대상으로 두지 않는다.
 */
const SCREENS = [
  { id: 'a-start', path: '/start', ready: 'screen-onboarding' },
  { id: 'b-notices', path: '/', ready: 'screen-home' },
  { id: 'c-worksheet', path: `/notices/${NOTICE}/000`, ready: 'screen-worksheet' },
  { id: 'd-bid-box', path: '/bids', ready: 'screen-bid-box' },
  { id: 'e-results', path: '/results', ready: 'screen-results' },
  { id: 'inbox', path: '/inbox', ready: 'screen-inbox' },
];

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
  rmSync(CONTEXT_PATH, { force: true });
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
      return existsSync(CONTEXT_PATH);
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
async function seed(context, baseUrl, csrf) {
  const call = async (method, path, body) => {
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
 * 캡처 **직전** DOM 에 거는 단언(D-7E-6). 걸리면 그 자리에서 던진다 — 파일은 쓰이지 않는다.
 *
 * 사업자번호 꼴을 **정규식으로** 보는 이유: 화면에 그 값이 설 자리는 계약상 없지만(입력칸이
 * 없다), 「없을 것이다」는 측정이 아니다. 로그인 이름·비밀 문구는 이 실행의 난수라 캡처에
 * 실리면 그대로 산출물에 남는다.
 */
async function assertNoPrivateData(page, screenId, secrets) {
  const body = await page.locator('body').innerText();
  /* global document -- 아래 콜백은 **브라우저 안**에서 돈다(이 파일의 나머지는 Node 다). */
  const cookie = await page.evaluate(() => document.cookie);
  const problems = [];
  if (/\b\d{3}-\d{2}-\d{5}\b/u.test(body)) {
    problems.push('사업자등록번호 꼴(3-2-5)이 화면에 있다');
  }
  if (body.includes(secrets.passphrase)) {
    problems.push('실행 비밀 문구가 화면에 있다');
  }
  if (body.includes(secrets.loginName)) {
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

    rmSync(OUT_DIR, { recursive: true, force: true });
    mkdirSync(OUT_DIR, { recursive: true });

    const files = [];
    let seededCaseId = null;
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
      if (seededCaseId === null) {
        seededCaseId = await seed(context, run.previewUrl, csrf);
      }

      for (const screen of SCREENS) {
        await page.goto(screen.path);
        await page.getByTestId(screen.ready).waitFor({ state: 'visible' });
        // 적재 중 화면을 찍지 않는다 — 활용사례에 스켈레톤을 내보낼 이유가 없다.
        await page.waitForLoadState('networkidle');

        /**
         * **양성 표지를 먼저 기다린다.** 씨앗이 조용히 실패하면 화면은 비어 있고 캡처는 그대로
         * 초록이다(앞 판 실측 — CSRF 가 빠져 담기가 403 이었는데 빈 화면 12 장이 나왔다).
         * 결과 화면에 그 건의 카드가 서는 것이 「씨앗이 닿았다」의 표지다.
         */
        if (screen.id === 'e-results') {
          await page.getByTestId(`result-case-${seededCaseId}`).waitFor({ state: 'visible' });
        }

        await assertNoPrivateData(page, `${screen.id}-${viewport.id}`, secrets);

        const name = `${screen.id}-${viewport.id}.png`;
        const bytes = await page.screenshot({ fullPage: true });
        if (bytes.length > MAX_FILE_BYTES) {
          throw new Error(
            `${name} 가 ${bytes.length} 바이트로 상한 ${MAX_FILE_BYTES} 를 넘는다 — 상한을 올리는 것은 설계 변경이다`,
          );
        }
        writeFileSync(resolve(OUT_DIR, name), bytes);
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
      resolve(OUT_DIR, 'manifest.json'),
      `${JSON.stringify({ capturedAt: new Date().toISOString(), head: head(), totalBytes: total, files }, null, 2)}\n`,
    );
    log(`끝 — ${files.length} 장 · 합 ${total} 바이트`);
  } finally {
    await browser.close();
    harness.kill('SIGTERM');
  }
}

await main();
