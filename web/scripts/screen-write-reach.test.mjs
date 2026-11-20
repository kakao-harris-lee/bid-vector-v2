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
 * **이 파일만의 시간 예산.** 대조마다 TS 프로그램을 세워야 해서(별칭·재수출을 선언까지
 * 풀려면 검사기가 필요하다) 한 파일에 전부 두면 예산을 넘는다. 주장과 대조를 **세 파일로**
 * 갈라 각 파일이 예산 안에 선다 — 셋 다 등록부에 있고 어느 쪽이 수집에서 빠져도 붉는다.
 */
vi.setConfig({ testTimeout: 20_000 });

const WEB_ROOT = resolve(import.meta.dirname, '..');
const SRC = resolve(WEB_ROOT, 'src');
const RESULTS_SCREEN = resolve(SRC, 'screens', 'ResultsScreen.tsx');

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
