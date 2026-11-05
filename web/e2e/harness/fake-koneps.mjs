// 여정 e2e 의 fake KONEPS(D-7E-1 ③) — **별 프로세스의 loopback HTTP 서버**다. 출하 app 은
// `bidvector.koneps.base-url` 로 이 주소를 받을 뿐이고 production 코드에 fake 분기가 0 이다
// (`requireSafeKonepsBaseUri` 가 loopback http 를 이미 허용한다 — Phase 1 실측).
//
// 응답은 `fixtures/notice-list.json` 에서 만든다(출처·해시는 그 파일의 `_provenance`).
// **마감·개찰 일시는 실행 시각 상대**로 계산한다(D-7E-2 — 시각을 조작하지 않는다).
//
// 요청줄은 **로그로 내지 않는다**: 서비스 키가 쿼리로 실려 오므로 그것을 적으면 e2e 산출물에
// 비밀값이 남는다(D-7E-6).
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const fixturePath = resolve(here, 'fixtures', 'notice-list.json');
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));

const KST_OFFSET_MINUTES = 9 * 60;
const MILLIS_PER_DAY = 86_400_000;

/**
 * 준비 대기의 표지(M9/portal-02) — `journey-up.mjs` 가 **오퍼레이션 이름을 모른 채** 「이 포트를 잡은
 * 것이 fake 인가」를 가르는 데 쓴다. 이름으로 준비를 재면 조달청이 오퍼레이션명을 바꿀 때마다 그
 * 대기가 영구 404 로 죽는다 — `02` 개편에서 실제로 그랬다.
 */
export const FAKE_KONEPS_IDENTITY_HEADER = 'x-fake-koneps';

/**
 * **실제로 응답한 오퍼레이션 집합**(cr M-1) — 모든 응답에 실린다. 수집이 「조용히 0건」인 경우를
 * 여정이 이름 붙은 실패로 만드는 자리다: 앞 라운드의 붉음은 「30000ms 안에 기다리지 못했다」였고
 * 그것은 수집이 아니라 준비 대기였다 — 원인을 말하지 않는 문면이 오진 비용을 만들었다.
 * 기본 표만 바뀌어 fixture 와 갈리면 이 집합이 **빈 채로** 남는다.
 */
export const FAKE_KONEPS_SERVED_HEADER = 'x-fake-koneps-served';

/** KONEPS 일시 형식 — `"YYYY-MM-DD HH:MM:SS"`(KST, offset 표기 없음 — policy-values §1.4). */
function konepsDateTime(offsetDays) {
  const instant = new Date(Date.now() + offsetDays * MILLIS_PER_DAY + KST_OFFSET_MINUTES * 60_000);
  return instant.toISOString().replace('T', ' ').slice(0, 19);
}

function itemsFor(operation) {
  return fixture.items
    .filter((item) => item.operation === operation)
    .map((item) => ({
      ...item.fields,
      bidClseDt: konepsDateTime(item.deadlineOffsetDays),
      opengDt: konepsDateTime(item.openingOffsetDays),
    }));
}

let requestCount = 0;
/** 표에 있어 **응답을 낸** 오퍼레이션들 — 404 로 끝난 이름은 들어오지 않는다. */
const served = new Set();

const server = createServer((request, response) => {
  requestCount += 1;
  const operation = new URL(request.url ?? '/', 'http://127.0.0.1').pathname.replace(/^\//u, '');
  const known = fixture.items.some((item) => item.operation === operation);
  if (known) {
    served.add(operation);
  }
  // **모든 응답**에 싣는다(404 에도) — 준비 대기가 보는 표지라서 모르는 이름의 응답에도 있어야 한다.
  response.setHeader(FAKE_KONEPS_IDENTITY_HEADER, 'journey');
  response.setHeader(FAKE_KONEPS_SERVED_HEADER, [...served].sort().join(','));
  if (!known) {
    response.writeHead(404).end();
    return;
  }
  const items = itemsFor(operation);
  const body = JSON.stringify({
    response: {
      header: { resultCode: '00', resultMsg: 'NORMAL SERVICE.' },
      body: { items, numOfRows: 100, pageNo: 1, totalCount: items.length },
    },
  });
  response.writeHead(200, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
  });
  response.end(body);
});

function shutdown() {
  process.stdout.write(`fake-koneps: 요청 ${requestCount}건 받고 종료\n`);
  server.close(() => process.exit(0));
}

// **직접 실행일 때만 듣는다**(M9/portal-02) — `journey-up.mjs` 가 [FAKE_KONEPS_IDENTITY_HEADER] 하나를
// import 하려면 이 모듈을 불러도 포트를 요구하거나 서버를 띄우지 않아야 한다. 표지 이름을 양쪽에
// 베껴 적으면 한쪽만 바뀌는 날 준비 대기가 영구 404 로 죽는다 — 이 slice 가 고친 결함과 같은 부류다.
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const port = Number.parseInt(process.argv[2] ?? '', 10);
  if (Number.isNaN(port)) {
    process.stderr.write('fake-koneps: 포트를 인자로 주십시오\n');
    process.exit(64);
  }
  server.listen(port, '127.0.0.1', () => {
    process.stdout.write(`fake-koneps: 127.0.0.1:${port} 에서 공고 ${fixture.items.length}건 준비\n`);
  });
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}
