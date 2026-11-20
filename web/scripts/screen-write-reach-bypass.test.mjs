import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it, vi } from 'vitest';

import {
  QUERIES_MODULE,
  collectScreenWriteReach,
  violationsOf,
} from './screen-write-reach.mjs';


/**
 * **양성 대조를 따로 두는 까닭**(수정 라운드 4): 대조마다 TS 프로그램을 새로 세워야 하고
 * (별칭·재수출을 선언까지 풀려면 검사기가 필요하다) 한 파일에 전부 두면 그 파일 하나가 30초
 * 예산의 절반을 넘는다. 주장과 대조를 **두 파일로** 갈라 각 파일이 예산 안에 선다 — 둘 다
 * 등록부에 있고, 어느 쪽이 수집에서 빠져도 `gate:tests` 가 붉는다.
 *
 * **이 파일만의 시간 예산**(v M-4). 이 게이트는 별칭을 선언까지 풀어야 해서 호출마다 TS
 * 프로그램을 세운다 — 원문 캐시와 앞 프로그램 재사용을 넣어 한 파일 47초를 13초로 줄였고,
 * 가장 느린 **개별 test 는 0.7초**라 기본 5초 예산 안이다(실측). 그래도 병렬 수집에서 호스트가
 * 붐비면 이 파일이 먼저 밀리므로, **다른 test 를 건드리지 않고** 이 자리만 넉넉히 둔다.
 */
vi.setConfig({ testTimeout: 20_000 });

const WEB_ROOT = resolve(import.meta.dirname, '..');
const SRC = resolve(WEB_ROOT, 'src');
const RESULTS_SCREEN = resolve(SRC, 'screens', 'ResultsScreen.tsx');
const QUERIES_PATH = resolve(SRC, QUERIES_MODULE);

const read = (path) => readFileSync(path, 'utf8');

/** 출하 트리 그대로의 판정 — 아래 대조들의 기준선이다. */

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
   * **`export *` barrel 을 네임스페이스로 쥐는 꼴**(v H-3r). 앞 판은 네임스페이스를 「그 모듈의
   * **문장** 전수」로 밀었는데, barrel 의 문장은 재수출 하나뿐이라 함수 선언이 씨앗이 되지
   * 못했다. 이제 네임스페이스는 **검사기가 세는 내보내기 집합**이고 재수출이 평탄화된다.
   */
  it.each([
    [
      '셈한 키',
      "import * as api from '../session/__planted_barrel__';",
      "const key = ['request', 'BidPrice'].join('');\nexport const plantedWrite = (api as Record<string, unknown>)[key];",
    ],
    [
      '동적 수입 + 셈한 키',
      '',
      "const key = ['request', 'BidPrice'].join('');\nexport const plantedWrite = async () =>\n  (await import('../session/__planted_barrel__') as Record<string, unknown>)[key];",
    ],
    [
      'Object.values 로 훑기',
      "import * as api from '../session/__planted_barrel__';",
      'export const plantedWrite = Object.values(api);',
    ],
  ])('barrel 을 %s 로 쥐어도 붉는다', (_label, importLine, useLine) => {
    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [resolve(SRC, 'session', '__planted_barrel__.ts'), "export * from '../api/queries';\n"],
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
    // **두 치환을 따로 단언한다**(cr LOW): 이어 붙이면 한쪽이 빗나가도 다른 쪽 때문에
    // 「바뀌었다」가 참이 되어, 공허를 막으려던 단언이 스스로 공허해진다.
    const renamed = original.replace(from, to);
    expect(renamed, '수입 자리 치환이 빗나갔다').not.toBe(original);

    const patched =
      _label === '별칭으로 가려도' ? renamed.replace('<Outlet />', '<Slot context={{ load: 1 }} />') : renamed;
    if (_label === '별칭으로 가려도') {
      expect(patched, '태그 치환이 빗나갔다').not.toBe(renamed);
    }

    const planted = collectScreenWriteReach({ webRoot: WEB_ROOT, overlay: new Map([[shell, patched]]) });

    expect(violationsOf(planted).join('\n')).toContain(expected);
  });

  /**
   * **네임스페이스 접근과 그 낱말을 숨긴 재수출**(v M). 식별자만 보던 앞 판은 셋 다 초록이었다 —
   * 이제 **태그·호출 대상 식 전체**의 심볼로 본다.
   */
});
