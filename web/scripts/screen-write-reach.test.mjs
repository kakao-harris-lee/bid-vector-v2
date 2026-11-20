import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  CLIENT_MODULE,
  QUERIES_MODULE,
  SCREEN_WRITE_POLICY,
  collectScreenWriteReach,
  moduleEdgesOf,
  operationsByExport,
  policyGaps,
  violationsOf,
  writeOperationsOf,
} from './screen-write-reach.mjs';

const WEB_ROOT = resolve(import.meta.dirname, '..');
const SRC = resolve(WEB_ROOT, 'src');
const RESULTS_SCREEN = resolve(SRC, 'screens', 'ResultsScreen.tsx');
const QUERIES_PATH = resolve(SRC, QUERIES_MODULE);
const CLIENT_PATH = resolve(SRC, CLIENT_MODULE);

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

  /** 같은 길이라도 **메서드가 GET 이면** 쓰기가 아니다 — 판정이 메서드에서 나온다. */
  it('apiClient 직접 수입이라도 GET 이면 초록이다 — 판정은 메서드가 한다', () => {
    const planted = plantInResults(
      "import { apiClient } from '../api/client';",
      "export const planted = () => apiClient.GET('/api/bid-results');",
    );

    expect(violationsOf(planted)).toStrictEqual([]);
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
    const text = [
      "import { apiClient } from './client';",
      "export async function hidden() { return apiClient.POST('/api/x', { body: {} }); }",
      'export async function looksLikeRead() { return hidden(); }',
    ].join('\n');

    const { operations } = operationsByExport(text, QUERIES_PATH);

    expect([...(operations.get('looksLikeRead') ?? [])]).toStrictEqual(['POST /api/x']);
  });

  it('출하 질의 모듈의 선언마다 호출이 정확히 하나다', () => {
    const { operations, unresolved } = operationsByExport(read(QUERIES_PATH), QUERIES_PATH);
    const bearing = [...operations].filter(([, ops]) => ops.size > 0);

    expect(unresolved).toStrictEqual([]);
    expect(bearing.length, '질의가 하나도 없으면 이 게이트가 공허하다').toBeGreaterThan(0);
    expect(bearing.filter(([, ops]) => ops.size !== 1).map(([name]) => name)).toStrictEqual([]);
  });

  it('클라이언트 모듈을 수입하는 자리가 질의 모듈 하나다 — 그 전제가 이 게이트의 경계다', () => {
    expect(read(CLIENT_PATH).length, '클라이언트 모듈이 비었다').toBeGreaterThan(0);
    expect(shipped.screens.every((one) => one.unresolved.length === 0)).toBe(true);
  });

  it('정책 등식이 양쪽 방향을 다 낸다', () => {
    expect(policyGaps(['results', '새-화면'], { results: 'READ_ONLY', '옛-화면': 'WRITES' })).toStrictEqual({
      undeclared: ['새-화면'],
      stale: ['옛-화면'],
    });
  });
});
