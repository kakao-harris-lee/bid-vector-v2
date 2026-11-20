import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

import ts from 'typescript';

import { describe, expect, it, vi } from 'vitest';

import {

  CLIENT_MODULE,
  QUERIES_MODULE,
  clientBindingImporters,
  elementPropsOf,
  SCREEN_WRITE_POLICY,
  collectScreenWriteReach,
  outletContextSites,
  policyGaps,
  reachFrom,
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

  /**
   * **H-1r — `queries.ts` 밖에 선언된 쓰기**(v 가 찾은 세 꼴). 앞 판은 그 모듈을 닫힌 세계로
   * 보아 셋 다 **전부 초록**이었다. 축을 한 심볼 그래프로 바꾼 뒤를 꼴마다 잰다.
   */
  it.each([
    [
      '포장 함수 재수출',
      'api',
      "import { requestBidPrice } from './queries';\nexport function floorWriter(...args: Parameters<typeof requestBidPrice>) {\n  return requestBidPrice(...args);\n}\n",
      "import { floorWriter } from './__planted_floor__';\nexport async function readFloorPreview(...args: Parameters<typeof floorWriter>) {\n  return floorWriter(...args);\n}\n",
    ],
    [
      '별칭 재수출(`export … from`)',
      'api',
      "import { requestBidPrice } from './queries';\nexport function floorWriter(...args: Parameters<typeof requestBidPrice>) {\n  return requestBidPrice(...args);\n}\n",
      "export { floorWriter as readFloorPreview } from './__planted_floor__';\n",
    ],
    [
      '표시층에 사는 쓰기',
      'display',
      "import { requestBidPrice } from '../api/queries';\nexport function floorWriter(...args: Parameters<typeof requestBidPrice>) {\n  return requestBidPrice(...args);\n}\n",
      "export { floorWriter as readFloorPreview } from '../display/__planted_floor__';\n",
    ],
  ])('%s 로 질의 모듈 밖에 숨긴 쓰기가 붉는다', (_label, dir, writerText, queriesLine) => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [resolve(SRC, dir, '__planted_floor__.ts'), writerText],
        [QUERIES_PATH, `${read(QUERIES_PATH)}\n${queriesLine}`],
        [
          RESULTS_SCREEN,
          `import { readFloorPreview } from '../api/queries';\n${read(RESULTS_SCREEN)}\nexport const planted = readFloorPreview;\n`,
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /**
   * **부작용 수입** — `import './x'` 는 이름을 가져오지 않지만 그 모듈의 최상위가 통째로
   * 실행된다. 식별자 축만으로는 닿지 않으므로 전수를 민다.
   */
  it('이름 없이 수입한 모듈의 최상위 호출도 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [
          resolve(SRC, 'api', '__planted_effect__.ts'),
          "import { apiClient } from './client';\nvoid apiClient.POST('/api/bid-price-requests', { body: {} });\n",
        ],
        [RESULTS_SCREEN, `import '../api/__planted_effect__';\n${read(RESULTS_SCREEN)}`],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /**
   * **적재 때 도는 쓰기와 값으로 건너가는 쓰기**(v H-2r 가 네 꼴로 쟀다). 라운드 2 가 선언
   * 단위로 바꾸면서 앞의 둘이 **회귀**로 열렸다 — 모듈이 그래프에 들어오면 그 최상위는 이미
   * 실행된 것인데 그 사실을 잃었다.
   */
  it.each([
    [
      '단축 속성으로 건너간다',
      "import { requestBidPrice } from '../api/queries';\nexport const helpers = { requestBidPrice };\n",
      "import { helpers } from '../session/__planted_load__';",
      'export const plantedWrite = helpers.requestBidPrice;',
    ],
    [
      '적재 때 돈다 — 쓰는 이름을 수입하지 않아도',
      "import { requestBidPrice } from '../api/queries';\nexport const sideLabel = '합성';\nvoid Promise.resolve()\n  .then(() => requestBidPrice({ noticeNumber: 'x', noticeRound: '0' }))\n  .catch(() => undefined);\n",
      "import { sideLabel } from '../session/__planted_load__';",
      'export const plantedWrite = sideLabel;',
    ],
    [
      '적재 때 객체 속성에 꽂는다',
      "import { requestBidPrice } from '../api/queries';\nexport const floorRegistry: { load?: typeof requestBidPrice } = {};\nfloorRegistry.load = requestBidPrice;\n",
      "import { floorRegistry } from '../session/__planted_load__';",
      'export const plantedWrite = floorRegistry.load;',
    ],
    [
      '리터럴 동적 수입을 셈한 키로 읽는다',
      "export const dynamicKey = 'requestBidPrice';\n",
      "import { dynamicKey } from '../session/__planted_load__';",
      "export const plantedWrite = async (key: string) => {\n  const mod: Record<string, unknown> = await import('../api/queries');\n  return [mod[key], dynamicKey];\n};",
    ],
  ])('%s — 그 쓰기가 붉는다', (_label, writerText, importLine, useLine) => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [resolve(SRC, 'session', '__planted_load__.ts'), writerText],
        [RESULTS_SCREEN, `${importLine}\n${read(RESULTS_SCREEN)}\n${useLine}\n`],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /**
   * **단축 속성이 유일한 간선인 자리.** 위의 네 꼴 가운데 단축 속성 꼴은 그 모듈의 **수입
   * 문장**이 같은 선언을 가리켜서, 값 심볼을 묻지 않아도 붉었다(실측). 그래서 간선이 단축
   * 속성 **하나뿐**인 자리를 따로 세운다 — 쓰기와 그것을 담는 객체가 **같은 모듈**에 있어
   * 수입 간선이 아예 없는 꼴이다.
   */
  it('단축 속성이 유일한 간선이어도 붉다', () => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [QUERIES_PATH, `${read(QUERIES_PATH)}\nexport function makeFloorHelpers() {\n  return { requestBidPrice };\n}\n`],
        [
          RESULTS_SCREEN,
          `import { makeFloorHelpers } from '../api/queries';\n${read(RESULTS_SCREEN)}\nexport const plantedWrite = makeFloorHelpers;\n`,
        ],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
  });

  /**
   * **router 가 값을 아래로 내리는 자리** — 철자가 아니라 **심볼**로 센다(cr HIGH · v M).
   * 앞 판은 JSX 태그 글자만 보아 별칭과 훅 꼴이 그대로 지나갔다.
   */
  it.each([
    ['그대로', '<Outlet />', '<Outlet context={{ load: 1 }} />', 'Outlet 이'],
    [
      '별칭으로 가려도',
      "import { NavLink, Outlet, useLocation, useMatches, useNavigate } from 'react-router';",
      "import { NavLink, Outlet as Slot, useLocation, useMatches, useNavigate } from 'react-router';",
      'Outlet 이',
    ],
  ])('Outlet 이 %s 값을 내리면 붉다', (_label, from, to, expected) => {
    const shell = resolve(SRC, 'AppShell.tsx');
    const original = read(shell);
    const patched =
      _label === '별칭으로 가려도'
        ? original.replace(from, to).replace('<Outlet />', '<Slot context={{ load: 1 }} />')
        : original.replace(from, to);

    // **바꿔치운 것이 실제로 바뀌었는가**(cr MEDIUM) — `.replace` 가 빗나가면 이 대조가 공허하다.
    expect(patched, '심은 글이 원문과 같다').not.toBe(original);

    const planted = collectScreenWriteReach({ webRoot: WEB_ROOT, overlay: new Map([[shell, patched]]) });

    expect(violationsOf(planted).join('\n')).toContain(expected);
  });

  /** 받는 쪽도 센다 — 그 훅이 읽는 값이 어디서 왔는지 이 축에서 알 수 없다. */
  it('useOutletContext 로 받으면 붉다', () => {
    const original = read(RESULTS_SCREEN);
    const patched = `import { useOutletContext } from 'react-router';\n${original}\nexport const plantedWrite = () => useOutletContext();\n`;

    expect(patched).not.toBe(original);

    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([[RESULTS_SCREEN, patched]]),
    });

    expect(violationsOf(planted).join('\n')).toContain('useOutletContext');
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

    expect(violationsOf(planted).join('\n')).toContain('어느 선언도 가리키지 않는다');
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

    // 두 축이 **함께** 든다: 바인딩 수입 등식과, 통째로 민 전수에서 드러나는 그 호출.
    expect(violationsOf(planted).join('\n')).toContain('바인딩을 수입하는 자리가');
    expect(violationsOf(planted).join('\n')).toContain('/api/bid-price-requests');
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
    const plantedRoutes = read(ROUTES).replace(
      'element: <ResultsScreen />',
      'element: <ResultsScreen requestFloor={undefined} />',
    );

    // **바꿔치운 것이 실제로 바뀌었는가**(cr MEDIUM).
    expect(plantedRoutes, '심은 글이 원문과 같다').not.toBe(read(ROUTES));

    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [ROUTES, plantedRoutes],
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
  it('쓰기 판정이 메서드에서 나온다 — 경로 이름을 보지 않는다', () => {
    expect(writeOperationsOf(['GET /api/a', 'POST /api/a', 'DELETE /api/b', 'GET /api/bid-price-requests'])).toStrictEqual(
      ['DELETE /api/b', 'POST /api/a'],
    );
  });

  /**
   * **얇은 포장이 쓰기를 감추지 못한다 — 파일 경계를 넘어서도.** 앞 판은 `queries.ts` 를
   * **닫힌 세계**로 보아, 그 모듈 밖에 선언된 것이 끼면 operation 0 으로 조용히 지나갔다
   * (v H-1r). 이제 축은 **한 심볼 그래프**이고 `queries.ts` 는 그 위의 한 마디일 뿐이다.
   */
  it('다른 모듈의 포장을 거쳐도 호출 자리까지 따라간다', () => {
    const writer = resolve(SRC, 'api', '__planted_writer__.ts');
    const program = writeReachProgram(
      WEB_ROOT,
      new Map([
        [writer, "import { apiClient } from './client';\nexport const planted = () => apiClient.POST('/api/x', { body: {} });\n"],
        [
          RESULTS_SCREEN,
          `import { planted } from '../api/__planted_writer__';\n${read(RESULTS_SCREEN)}\nexport const used = planted;\n`,
        ],
      ]),
    );
    const reach = reachFrom(RESULTS_SCREEN, { program, srcRoot: SRC });

    expect(reach.operations).toContain('POST /api/x');
  });

  it('쓰지 않는 선언은 따라가지 않는다 — 모듈을 통째로 삼키지 않는다', () => {
    const program = writeReachProgram(WEB_ROOT);
    const reach = reachFrom(RESULTS_SCREEN, { program, srcRoot: SRC });

    // 결과 화면은 질의 모듈을 수입하지만 그 안의 쓰기 선언을 **부르지 않는다**.
    expect(reach.operations.length, '닿는 operation 이 0 이면 이 대조가 공허하다').toBeGreaterThan(0);
    expect(writeOperationsOf(reach.operations)).toStrictEqual([]);
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

  /**
   * **등식이 재는 것은 모듈이 아니라 바인딩이다**(cr HIGH ② 의 제목 정정). 그 **모듈**을
   * 수입하는 자리는 더 많다(`rememberCsrf`) — 이 대조가 없으면 위 등식이 「모듈을 수입하는
   * 자리가 하나」라는 거짓 주장으로 읽힌다.
   */
  it('그 모듈 자체를 수입하는 자리는 더 많다', () => {
    const program = writeReachProgram(WEB_ROOT);
    const clientPath = resolve(SRC, CLIENT_MODULE);
    const importers = program
      .getSourceFiles()
      .filter((one) => one.fileName.startsWith(`${SRC}/`) && !/\.test\.tsx?$/u.test(one.fileName))
      .filter((one) =>
        one.statements.some(
          (statement) =>
            ts.isImportDeclaration(statement) &&
            ts.isStringLiteralLike(statement.moduleSpecifier) &&
            resolve(dirname(one.fileName), `${statement.moduleSpecifier.text}.ts`) === clientPath,
        ),
      );

    expect(importers.length, '모듈 수입이 하나뿐이면 이 대조가 공허하다').toBeGreaterThan(1);
  });

  /** 오늘 `Outlet` 은 아무것도 내리지 않는다 — 그 사실이 위 양성 대조를 공허하지 않게 한다. */
  it('출하 트리의 Outlet 은 값을 내리지 않는다', () => {
    expect(outletContextSites(writeReachProgram(WEB_ROOT), WEB_ROOT)).toStrictEqual([]);
  });

  it('정책 등식이 양쪽 방향을 다 낸다', () => {
    expect(policyGaps(['results', '새-화면'], { results: 'READ_ONLY', '옛-화면': 'WRITES' })).toStrictEqual({
      undeclared: ['새-화면'],
      stale: ['옛-화면'],
    });
  });
});
