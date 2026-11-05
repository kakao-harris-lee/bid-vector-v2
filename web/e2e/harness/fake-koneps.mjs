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

import { openingAxisDesign, openingAxisRouteTable } from './opening-axis.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const fixturePath = resolve(here, 'fixtures', 'notice-list.json');
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));

const port = Number.parseInt(process.argv[2] ?? '', 10);
if (Number.isNaN(port)) {
  process.stderr.write('fake-koneps: 포트를 인자로 주십시오\n');
  process.exit(64);
}

const KST_OFFSET_MINUTES = 9 * 60;
const MILLIS_PER_DAY = 86_400_000;

/** KONEPS 일시 형식 — `"YYYY-MM-DD HH:MM:SS"`(KST, offset 표기 없음 — policy-values §1.4). */
function konepsDateTime(offsetDays) {
  const instant = new Date(Date.now() + offsetDays * MILLIS_PER_DAY + KST_OFFSET_MINUTES * 60_000);
  return instant.toISOString().replace('T', ' ').slice(0, 19);
}

/**
 * 개찰 축 라우팅(M9/opening-harness) — 표와 행 생성은 `opening-axis.mjs` 하나에 있고 여기서는
 * **그 표를 꽂을 뿐**이다. 공고 축(`fixture.items`)의 라우팅 구조는 건드리지 않는다: 둘을 한
 * 표로 합치면 상세 축이 요청의 공고번호로 걸러야 하는 성질(공고 축에 없는 성질)이 공고 축까지
 * 번진다.
 */
const openingDesign = openingAxisDesign(
  JSON.parse(readFileSync(resolve(here, 'fixtures', 'opening-axis.json'), 'utf8')),
);
const openingRoutes = openingAxisRouteTable(openingDesign, { dateTime: konepsDateTime });

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

const server = createServer((request, response) => {
  requestCount += 1;
  const operation = new URL(request.url ?? '/', 'http://127.0.0.1').pathname.replace(/^\//u, '');
  const known = fixture.items.some((item) => item.operation === operation);
  // 개찰 축 상세는 **요청의 공고번호**로 걸러야 해서 질의를 본다. 위의 `operation` 줄을 고치지
  // 않고 여기서 한 번 더 푸는 이유는 그 줄이 공고 축 라우팅의 자리이고 이 slice 가 그것을
  // 바꾸지 않기 때문이다(파싱 한 번의 비용은 fake 에서 재지 않는다).
  const openingRoute = openingRoutes.get(operation);
  if (!known && openingRoute === undefined) {
    response.writeHead(404).end();
    return;
  }
  const items =
    openingRoute === undefined
      ? itemsFor(operation)
      : openingRoute(new URL(request.url ?? '/', 'http://127.0.0.1').searchParams);
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

server.listen(port, '127.0.0.1', () => {
  process.stdout.write(`fake-koneps: 127.0.0.1:${port} 에서 공고 ${fixture.items.length}건 준비\n`);
});

function shutdown() {
  process.stdout.write(`fake-koneps: 요청 ${requestCount}건 받고 종료\n`);
  server.close(() => process.exit(0));
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
