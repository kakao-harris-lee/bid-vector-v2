import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it, vi } from 'vitest';

import {
  SCREEN_WRITE_POLICY,
  collectScreenWriteReach,
  violationsOf,
} from './screen-write-reach.mjs';


/**
 * **주입·정책 축의 양성 대조**(수정 라운드 4): 대조마다 TS 프로그램을 새로 세워야 하고
 * (별칭·재수출을 선언까지 풀려면 검사기가 필요하다) 한 파일에 전부 두면 그 파일 하나가 30초
 * 예산의 절반을 넘는다. 주장과 대조를 **세 파일로** 갈라 각 파일이 예산 안에 선다 — 둘 다
 * 등록부에 있고, 어느 쪽이 수집에서 빠져도 `gate:tests` 가 붉는다.
 *
 * **이 파일만의 시간 예산.** 대조마다 TS 프로그램을 세워야 해서(별칭·재수출을 선언까지
 * 풀려면 검사기가 필요하다) 한 파일에 전부 두면 예산을 넘는다. 주장과 대조를 **세 파일로**
 * 갈라 각 파일이 예산 안에 선다 — 셋 다 등록부에 있고 어느 쪽이 수집에서 빠져도 붉는다.
 */
vi.setConfig({ testTimeout: 20_000 });

const WEB_ROOT = resolve(import.meta.dirname, '..');
const SRC = resolve(WEB_ROOT, 'src');
const RESULTS_SCREEN = resolve(SRC, 'screens', 'ResultsScreen.tsx');
const ROUTES = resolve(SRC, 'routes.tsx');

const read = (path) => readFileSync(path, 'utf8');

/** 출하 트리 그대로의 판정 — 아래 대조들의 기준선이다. */

describe('양성 대조 — 주입과 정책', () => {
  it.each([
    [
      '네임스페이스 태그',
      "import * as RR from 'react-router';\n",
      '<RR.Outlet context={{ load: 1 }} />',
      'Outlet 이',
    ],
    [
      '네임스페이스 훅',
      "import * as RR from 'react-router';\n",
      '{RR.useOutlet({ load: 1 })}',
      'useOutlet 이',
    ],
  ])('%s 도 붉는다', (_label, importLine, element, expected) => {
    const shell = resolve(SRC, 'AppShell.tsx');
    const original = read(shell);
    const patched = `${importLine}${original.replace('<Outlet />', element)}`;

    expect(patched, '심은 글이 원문과 같다').not.toBe(original);

    const planted = collectScreenWriteReach({ webRoot: WEB_ROOT, overlay: new Map([[shell, patched]]) });

    expect(violationsOf(planted).join('\n')).toContain(expected);
  });

  /**
   * **캐시가 두 답을 한 키로 접지 않는다**(cr MEDIUM).
   *
   * 앞 판의 키는 「그 패키지 이름을 담은 오버레이 항목 + 이미 닿는다고 **센** 파일」이었다.
   * 「닿는다」는 **한 번 센 뒤**에야 아는 사실이라 첫 호출에서는 그 좁힘이 더 약했고, 그래서
   * **그 낱말을 담지 않는 중간 shim** 만 달라지는 두 호출이 **같은 키**를 받을 수 있었다 —
   * 뒤 호출이 앞 호출의 답을 그대로 받는 거짓 초록이다.
   *
   * 이 대조는 그 두 호출을 **차례로** 한다: 중간 shim 만 바뀌고 나머지는 같다. 키가 프로그램
   * 객체인 지금은 두 호출이 서로 다른 프로그램이라 각자 센다.
   */
  it('중간 shim 만 다른 두 호출이 서로 다른 답을 낸다', () => {
    const shell = resolve(SRC, 'AppShell.tsx');
    const hop = resolve(SRC, 'session', '__planted_hop__.ts');
    const shim = resolve(SRC, 'session', '__planted_shim__.ts');
    const original = read(shell);
    // 셸과 shim 은 **두 호출에서 같다**. 바뀌는 것은 중간 hop 하나뿐이고 그 글에는 패키지
    // 이름이 없다 — 앞 판의 키가 보지 못하던 자리다.
    const patched = `import { Slot } from './session/__planted_hop__';\n${original.replace('<Outlet />', '<Slot context={{ load: 1 }} />')}`;
    expect(patched, '태그 치환이 빗나갔다').not.toBe(original);

    const shimText = "export { Outlet as Alias } from 'react-router';\nexport const Plain = () => null;\n";
    const ask = (hopText) =>
      violationsOf(
        collectScreenWriteReach({
          webRoot: WEB_ROOT,
          overlay: new Map([
            [shim, shimText],
            [hop, hopText],
            [shell, patched],
          ]),
        }),
      ).join('\n');

    // ① 무해한 hop — `Slot` 이 router 와 무관하다.
    const harmless = ask("export { Plain as Slot } from './__planted_shim__';\n");
    // ② 같은 자리에서 hop 만 바꾼다 — 이제 `Slot` 이 `Outlet` 이다.
    const injected = ask("export { Alias as Slot } from './__planted_shim__';\n");

    expect(harmless, '무해한 hop 인데 주입으로 셌다').not.toContain('Outlet 이');
    expect(injected, '두 답이 한 키로 접혔다').toContain('Outlet 이');
  });

  /** 그 낱말을 **숨긴 재수출**을 거쳐도 심볼이 같다. */
  it('낱말을 숨긴 재수출을 거쳐도 붉는다', () => {
    const shell = resolve(SRC, 'AppShell.tsx');
    const original = read(shell);
    const patched = `import { Slot } from './session/__planted_rr__';\n${original.replace('<Outlet />', '<Slot context={{ load: 1 }} />')}`;

    expect(patched).not.toBe(original);

    const planted = collectScreenWriteReach({
      webRoot: WEB_ROOT,
      overlay: new Map([
        [resolve(SRC, 'session', '__planted_rr__.ts'), "export { Outlet as Slot } from 'react-router';\n"],
        [shell, patched],
      ]),
    });

    expect(violationsOf(planted).join('\n')).toContain('Outlet 이');
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

