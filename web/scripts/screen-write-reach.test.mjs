import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it, vi } from 'vitest';

import {

  QUERIES_MODULE,
  clientBindingImporters,
  elementPropsOf,
  SCREEN_WRITE_POLICY,
  collectScreenWriteReach,
  moduleEdgesOf,
  operationsByExport,
  policyGaps,
  violationsOf,
  writeOperationsOf,
  writeReachProgram,
} from './screen-write-reach.mjs';


/**
 * **이 파일만의 시간 예산**(v M-4). 이 게이트는 별칭을 선언까지 풀어야 해서 호출마다 TS
 * 프로그램을 세운다 — 원문 캐시와 앞 프로그램 재사용을 넣어 한 파일 47초를 13초로 줄였고,
 * 가장 느린 **개별 test 는 0.7초**라 기본 5초 예산 안이다(실측). 그래도 병렬 수집에서 호스트가
 * 붐비면 이 파일이 먼저 밀리므로, **다른 test 를 건드리지 않고** 이 자리만 넉넉히 둔다.
 */
vi.setConfig({ testTimeout: 20_000 });

const WEB_ROOT = resolve(import.meta.dirname, '..');
const SRC = resolve(WEB_ROOT, 'src');
const RESULTS_SCREEN = resolve(SRC, 'screens', 'ResultsScreen.tsx');
const ROUTES = resolve(SRC, 'routes.tsx');
const QUERIES_PATH = resolve(SRC, QUERIES_MODULE);

const read = (path) => readFileSync(path, 'utf8');

/** 출하 트리 그대로의 판정 — 아래 대조들의 기준선이다. */
const shipped = collectScreenWriteReach({ webRoot: WEB_ROOT });

describe('계약이 요구한 주장 — 결과 화면이 닿는 쓰기가 0 이다', () => {
  it('출하 트리에서 위반이 없다', () => {
    expect(violationsOf(shipped)).toStrictEqual([]);
  });

  /**
   * **비공허**: 읽기 전용 화면이 실제로 있고, 그 화면이 질의에 **닿기는 한다**. 둘 다 재지
   * 않으면 「아무것도 보지 않아서 초록」과 구별되지 않는다.
   */
  it('읽기 전용 화면이 있고 그 화면이 질의 operation 에 닿는다', () => {
    expect(shipped.screens.length).toBeGreaterThan(0);
    for (const screen of shipped.screens) {
      expect(screen.moduleCount, `${screen.slotId}: 모듈 폐포가 비었다`).toBeGreaterThan(1);
      expect(screen.operations.length, `${screen.slotId}: operation 에 하나도 닿지 않는다`).toBeGreaterThan(0);
      expect(screen.writes).toStrictEqual([]);
    }
  });

  /**
   * **결과 자리가 `READ_ONLY` 라는 주장을 못박는다.** 이것이 없으면 정책을 `WRITES` 로 돌리는
   * 한 줄로 게이트가 조용해진다 — 입막음에 파일 둘을 건드리게 하는 자리다.
   */
  it('결과 자리의 정책이 READ_ONLY 다', () => {
    expect(SCREEN_WRITE_POLICY.results).toBe('READ_ONLY');
    expect(shipped.screens.map((one) => one.slotId)).toContain('results');
  });

  it('정책 표의 키가 자리 표와 같다 — 새 화면이 의무 밖에 서지 못한다', () => {
    expect(shipped.gaps.undeclared).toStrictEqual([]);
    expect(shipped.gaps.stale).toStrictEqual([]);
  });

  /**
   * `element` 없는 자리의 **수**를 못박는다 — 조용히 늘면 의무가 조용히 준다.
   *
   * 오늘의 하나는 **없는 주소**다. 그 자리의 route 는 `id` 를 식(`notFoundSlot.id`)으로 들어
   * 자리 표의 리터럴과 이어지지 않고, 그래서 이 술어가 모듈을 얻지 못한다. 화면이 아니라
   * 빈 자리 placeholder 이므로 의무가 없는 것이 맞다 — 다만 **수가 움직이면** 여기가 붉는다.
   */
  it('element 없는 자리의 수가 하나다 — 없는 주소뿐이다', () => {
    expect(shipped.elementlessCount).toBe(1);
  });
});

describe('양성 대조 — 심으면 붉는다', () => {
  /** 결과 화면 원문에 수입 한 줄과 그것을 쓰는 자리를 더한다(디스크는 건드리지 않는다). */
  const plantInResults = (importLine, useLine) =>
    collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([[RESULTS_SCREEN, `${importLine}\n${read(RESULTS_SCREEN)}\n${useLine}\n`]]),
    });

  it('결과 화면이 쓰기 질의를 수입하면 붉다', () => {
    const planted = plantInResults(
      "import { requestBidPrice } from '../api/queries';",
      'export const planted = requestBidPrice;',
    );

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /** 별칭으로 가려도 간선이 나르는 것은 **내보낸 이름**이다. */
  it('별칭으로 수입해도 붉다', () => {
    const planted = plantInResults(
      "import { requestBidPrice as planted } from '../api/queries';",
      'export const used = planted;',
    );

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /** 네임스페이스 수입은 **전체 내보내기에 닿는 것**으로 센다(fail-closed). */
  it('네임스페이스로 수입하면 붉다', () => {
    const planted = plantInResults(
      "import * as allQueries from '../api/queries';",
      'export const used = allQueries;',
    );

    expect(violationsOf(planted).length).toBeGreaterThan(0);
  });

  /** 무해한 모듈을 거치는 **재수출 세탁**도 간선이다. */
  it('다른 모듈을 거쳐 재수출해도 붉다', () => {
    const launder = resolve(SRC, 'display', '__planted_launder__.ts');
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [launder, "export { requestBidPrice } from '../api/queries';\n"],
        [
          RESULTS_SCREEN,
          `import { requestBidPrice } from '../display/__planted_launder__';\n${read(RESULTS_SCREEN)}\nexport const used = requestBidPrice;\n`,
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /** 질의층을 **건너뛰고** 클라이언트를 직접 부르는 길(팀장 조건 ①). */
  it('결과 화면이 apiClient 를 직접 수입해 POST 하면 붉다', () => {
    const planted = plantInResults(
      "import { apiClient } from '../api/client';",
      "export const planted = () => apiClient.POST('/api/bid-price-requests', { body: {} });",
    );

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /**
   * **같은 길이라도 메서드가 `GET` 이면 쓰기 축은 조용하다** — 붉는 것은 다른 축, 곧 「이
   * 바인딩을 수입하는 자리는 질의 모듈 하나」라는 등식이다(cr HIGH ② 가 세우라 한 그 등식).
   * 두 축이 **따로 선다**는 것을 이 대조가 가른다: 쓰기 문면은 없고 수입 자리 문면만 있다.
   */
  it('apiClient 직접 수입은 GET 이어도 붉다 — 다만 쓰기 축이 아니라 수입 자리 축에서', () => {
    const lines = violationsOf(
      plantInResults(
        "import { apiClient } from '../api/client';",
        "export const planted = () => apiClient.GET('/api/bid-results');",
      ),
    );

    expect(lines.join('\n')).toContain('바인딩을 수입하는 자리가');
    expect(lines.join('\n'), 'GET 인데 쓰기로 셌다').not.toContain('쓰기 operation 에 닿는다');
  });

  /** 메서드·경로가 리터럴이 아니면 **모르는 것**이고, 모르는 것은 통과가 아니다. */
  it('비리터럴 경로는 모르는 꼴로 붉다', () => {
    const planted = plantInResults(
      "import { apiClient } from '../api/client';",
      'export const planted = (path: string) => apiClient.POST(path as never, { body: {} });',
    );

    expect(violationsOf(planted).join('\n')).toContain('모르는 꼴');
  });

  it('동적 import 의 지정자가 리터럴이 아니면 붉다', () => {
    const planted = plantInResults(
      '',
      'export const planted = (where: string) => import(/* @vite-ignore */ where);',
    );

    expect(violationsOf(planted).join('\n')).toContain('모르는 꼴');
  });

  it('해소되지 않는 상대 지정자는 붉다', () => {
    const planted = plantInResults("import { nothing } from './__no_such_module__';", 'export const used = nothing;');

    expect(violationsOf(planted).join('\n')).toContain('해소되지 않는다');
  });

  /**
   * **별칭 재수출 — verifier 와 code review 가 따로 찾은 살아 있는 우회였다(HIGH ①).**
   * `queries.ts` 에 한 줄을 더하고 그 이름을 수입하면 네 게이트가 전부 초록이었다. 꼴 셋을
   * 전부 심어 **열거가 아니라 해소**로 닫혔는지 잰다.
   */
  it.each([
    ['지역 별칭', 'export { requestBidPrice as readFloorHint };', 'readFloorHint'],
    ['재수출 별칭', "export { requestBidPrice as readFloorHint } from './queries';", 'readFloorHint'],
    ['기본 내보내기', 'export default requestBidPrice;', 'default'],
  ])('%s 로 숨긴 쓰기가 붉는다', (_label, line, name) => {
    const importLine =
      name === 'default'
        ? "import readFloorHint from '../api/queries';"
        : `import { ${name} } from '../api/queries';`;
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [QUERIES_PATH, `${read(QUERIES_PATH)}\n${line}\n`],
        [RESULTS_SCREEN, `${importLine}\n${read(RESULTS_SCREEN)}\nexport const planted = readFloorHint;\n`],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /** 내보내기 전수에 **없는 이름**을 수입하면 모르는 것이라 붉는다(fail-closed). */
  it('질의 모듈이 내보내지 않는 이름을 수입하면 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          RESULTS_SCREEN,
          `import { noSuchExport } from '../api/queries';\n${read(RESULTS_SCREEN)}\nexport const planted = noSuchExport;\n`,
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('내보내기 전수에 없다');
  });

  /** 네임스페이스로 클라이언트를 가져오면 어느 이름이 왔는지 이 축에서 알 수 없다(v L-1). */
  it('클라이언트 모듈을 통째로 가져오면 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          RESULTS_SCREEN,
          `import * as client from '../api/client';\n${read(RESULTS_SCREEN)}\nexport const planted = () => client['apiClient'].POST('/api/bid-price-requests', { body: {} });\n`,
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('통째로');
  });

  /** 별칭으로 가져온 클라이언트도 **심볼**이 같아 같은 자리에 선다(v L-1). */
  it('apiClient 를 별칭으로 가져와 POST 해도 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          RESULTS_SCREEN,
          `import { apiClient as sender } from '../api/client';\n${read(RESULTS_SCREEN)}\nexport const planted = () => sender.POST('/api/bid-price-requests', { body: {} });\n`,
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /**
   * **쓰기를 바깥에서 꽂는 길** — 자리 표가 함수를 prop 으로 내려 주면 화면의 폐포에 그 질의가
   * 없다(v M-1). 싼 값에 닫는 축: 읽기 전용 화면의 element 는 리터럴 아닌 prop 을 받지 않는다.
   */
  it('읽기 화면의 route element 가 리터럴 아닌 prop 을 받으면 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          ROUTES,
          read(ROUTES).replace('element: <ResultsScreen />', 'element: <ResultsScreen requestFloor={undefined} />'),
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('리터럴이 아닌 prop');
  });

  it('클라이언트 바인딩을 다른 자리에서 수입하면 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          resolve(SRC, 'display', '__planted_client__.ts'),
          "import { apiClient } from '../api/client';\nexport const planted = apiClient;\n",
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('바인딩을 수입하는 자리가');
  });

  /** 정책을 돌려 입막음하는 길 — 표를 고치면 **이 파일의 단언**이 함께 붉는다. */
  it('결과 자리를 WRITES 로 돌리면 읽기 전용 화면이 0 이 되어 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      policy: { ...SCREEN_WRITE_POLICY, results: 'WRITES' },
    });

    expect(violationsOf(planted).join('\n')).toContain('공허');
  });

  it('정책 표에서 자리를 빼면 붉다', () => {
    const rest = Object.fromEntries(Object.entries(SCREEN_WRITE_POLICY).filter(([slot]) => slot !== 'results'));
    const planted = collectScreenWriteReach({ webRoot: WEB_ROOT, policy: rest });

    expect(violationsOf(planted).join('\n')).toContain('선언되지 않았다');
  });
});

describe('음성 대조 — 붉음이 쓰기 축에서 왔다', () => {
  /**
   * **이것이 없으면** 「수입 한 줄을 더하면 무엇이든 붉는다」와 구별되지 않는다. 같은 자리에
   * **읽기** 질의를 심으면 초록이어야 한다.
   */
  it('결과 화면에 읽기 질의를 심으면 초록이다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          RESULTS_SCREEN,
          `import { listNotifications } from '../api/queries';\n${read(RESULTS_SCREEN)}\nexport const planted = listNotifications;\n`,
        ],
      ]),
    });

    expect(violationsOf(planted)).toStrictEqual([]);
  });
});

describe('술어 조각 — 간선과 operation 사상', () => {
  it('간선이 내보낸 이름을 나른다 — 지역 이름이 아니다', () => {
    const edges = moduleEdgesOf("import { a as b } from './x';", 'probe.ts');

    expect(edges).toStrictEqual([{ specifier: './x', names: ['a'], dynamic: false }]);
  });

  it('기본 수입·네임스페이스·재수출은 전체 도달이다', () => {
    for (const text of [
      "import x from './x';",
      "import * as x from './x';",
      "export * from './x';",
    ]) {
      expect(moduleEdgesOf(text, 'probe.ts')[0]?.names, text).toBe('*');
    }
  });

  it('쓰기 판정이 메서드에서 나온다 — 경로 이름을 보지 않는다', () => {
    expect(writeOperationsOf(['GET /api/a', 'POST /api/a', 'DELETE /api/b', 'GET /api/bid-price-requests'])).toStrictEqual(
      ['DELETE /api/b', 'POST /api/a'],
    );
  });

  /** 얇은 포장이 쓰기를 감추지 못한다 — 선언 사이 폐포가 그 자리를 닫는다. */
  it('읽기 이름이 속으로 쓰기를 부르면 그 operation 이 함께 실린다', () => {
    const program = writeReachProgram(
      WEB_ROOT,
      new Map([
        [
          QUERIES_PATH,
          [
            "import { apiClient } from './client';",
            "export async function hidden() { return apiClient.POST('/api/x', { body: {} }); }",
            'export async function looksLikeRead() { return hidden(); }',
          ].join('\n'),
        ],
      ]),
    );
    const { operations } = operationsByExport(program, QUERIES_PATH);

    expect([...(operations.get('looksLikeRead')?.operations ?? [])]).toStrictEqual(['POST /api/x']);
  });

  /**
   * **별칭은 선언까지 풀린다** — 꼴을 열거하지 않는다. 이것이 HIGH ① 의 뿌리였다: 앞 판은
   * 최상위 **선언 이름**만 키로 삼아 별칭 이름이 키 집합 밖이었고, 부르는 자리가 그것을
   * operation 0 으로 조용히 통과시켰다.
   */
  it.each([
    ['지역 별칭', 'export { requestBidPrice as readFloorHint };', 'readFloorHint'],
    ['재수출 별칭', "export { requestBidPrice as readFloorHint } from './queries';", 'readFloorHint'],
    ['기본 내보내기', 'export default requestBidPrice;', 'default'],
  ])('%s 도 같은 operation 으로 풀린다', (_label, line, name) => {
    const program = writeReachProgram(WEB_ROOT, new Map([[QUERIES_PATH, `${read(QUERIES_PATH)}\n${line}\n`]]));
    const { operations } = operationsByExport(program, QUERIES_PATH);

    expect([...(operations.get(name)?.operations ?? [])]).toStrictEqual(['POST /api/bid-price-requests']);
  });

  it('출하 질의 모듈의 값 내보내기마다 호출이 많아야 하나다', () => {
    const program = writeReachProgram(WEB_ROOT);
    const { operations, unresolved } = operationsByExport(program, QUERIES_PATH);
    const bearing = [...operations].filter(([, one]) => one.operations.size > 0);

    expect(unresolved).toStrictEqual([]);
    expect(bearing.length, '질의가 하나도 없으면 이 게이트가 공허하다').toBeGreaterThan(0);
    expect(bearing.filter(([, one]) => one.operations.size !== 1).map(([name]) => name)).toStrictEqual([]);
  });

  /** 타입 내보내기는 **키에 남되** operation 이 0 이다 — 「아는 이름」과 「모르는 이름」을 가른다. */
  it('타입 내보내기는 키에 있고 값이 아니다', () => {
    const program = writeReachProgram(WEB_ROOT);
    const { operations } = operationsByExport(program, QUERIES_PATH);

    expect(operations.get('BidCase')?.isValue).toBe(false);
    expect(operations.get('listBidCases')?.isValue).toBe(true);
  });

  /**
   * **`apiClient` 바인딩을 수입하는 자리가 질의 모듈 하나다** — 설계 검토 우회 5 행이 「등식으로
   * 고정한다」고 적은 그 등식이다. 앞 판의 이 자리는 제목이 주장하는 것을 **재지 않았고**(본문이
   * 센 것은 파일이 비지 않았다는 것과 `unresolved` 가 없다는 것뿐이었다), 제목 자체도 글자
   * 그대로는 거짓이었다 — `api/client` **모듈**을 수입하는 자리는 셋 더 있다(`rememberCsrf`).
   * 하나인 것은 모듈이 아니라 **이 바인딩**이다.
   */
  it('apiClient 바인딩을 수입하는 자리 == 질의 모듈 하나', () => {
    expect(shipped.clientBindingImporters).toStrictEqual([QUERIES_MODULE]);
  });

  it('그 모듈 자체를 수입하는 자리는 더 많다 — 등식이 재는 것은 바인딩이다', () => {
    const importers = readdirSync(resolve(SRC), { recursive: true })
      .filter((one) => typeof one === 'string' && /\.tsx?$/u.test(one) && !/\.test\./u.test(one))
      .filter((one) => /from '(\.\.?\/)+api\/client'/u.test(read(resolve(SRC, one))));

    expect(importers.length, '모듈 수입이 하나뿐이면 이 대조가 공허하다').toBeGreaterThan(1);
  });

  it('자리 표의 리터럴 아닌 prop 만 고른다 — 글자 리터럴은 조용하다', () => {
    const text = [
      "import { A } from './A';",
      "export const screenSlots = [",
      "  { id: 'plain', path: '/p', element: <A /> },",
      "  { id: 'literal', path: '/l', element: <A title=\"ok\" /> },",
      "  { id: 'injected', path: '/i', element: <A run={x} /> },",
      '];',
    ].join('\n');

    const found = elementPropsOf(text, 'routes.tsx');

    expect(found.get('injected')).toStrictEqual(['run']);
    expect(found.has('plain'), '빈 element 가 걸렸다').toBe(false);
    expect(found.has('literal'), '글자 리터럴이 걸렸다').toBe(false);
  });

  it('바인딩 수입 집계가 출하 트리에서 질의 모듈 하나다 — 직접 부른 값으로도 같다', () => {
    expect(clientBindingImporters(writeReachProgram(WEB_ROOT), WEB_ROOT)).toStrictEqual([QUERIES_MODULE]);
  });

  it('정책 등식이 양쪽 방향을 다 낸다', () => {
    expect(policyGaps(['results', '새-화면'], { results: 'READ_ONLY', '옛-화면': 'WRITES' })).toStrictEqual({
      undeclared: ['새-화면'],
      stale: ['옛-화면'],
    });
  });
});
