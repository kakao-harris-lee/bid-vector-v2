import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import { parseSpecRegistry, verdictOf } from './journey-results.mjs';
import { REGISTRY_PATH, SKIP_NOTE } from './smoke-lane.mjs';

/**
 * 셸 스모크 레인의 **등록부 등식**(M13/13C r2 · v R1-M5).
 *
 * 레인 게이트(`check-smoke-results.mjs`)는 **그 실행이 무엇을 돌렸는지**를 리포터 산출물로 보고,
 * 여기서 잠그는 것은 그보다 앞선 축이다 — **디스크의 spec 집합이 등록부와 같은가**. 둘이 다른
 * 층인 이유: 리포터 축만 있으면 「spec 을 더하고 등록부에 안 적었다」를 e2e 를 다 돌린 뒤에야
 * 알고, 디스크 축만 있으면 「등록부에 있는데 수집에서 빠졌다」(개명·`testIgnore`·부분 실행)를
 * 영원히 모른다.
 */
const here = dirname(fileURLToPath(import.meta.url));
const SPEC_DIR = resolve(here, '..', 'e2e');

const registry = parseSpecRegistry(readFileSync(REGISTRY_PATH, 'utf8'));

/**
 * `e2e/` **바로 아래**의 spec 파일 집합(cr R2-4 — 앞 주석은 설정의 수집 경계와 같다고 적었는데
 * 설정은 재귀에서 `journey/**` 만 뺀다. 오늘 결과가 같은 것은 하위 디렉터리가 `journey/` 하나
 * 라는 우연이다).
 *
 * 그래서 이 싼 축은 **한 단만** 본다: 누가 `e2e/<하위>/foo.spec.ts` 를 만들면 여기서는 보이지
 * 않고 레인 게이트가 e2e 를 다 돌린 뒤에 집합 등식으로 받는다. 그 차이는 알려진 제한이다.
 */
function specsOnDisk() {
  return new Set(readdirSync(SPEC_DIR, { withFileTypes: true })
    .filter((entry) => entry.isFile() && entry.name.endsWith('.spec.ts'))
    .map((entry) => entry.name));
}

describe('셸 스모크 등록부', () => {
  it('비어 있지 않다', () => {
    expect(registry.size).toBeGreaterThan(0);
  });

  it('디스크의 spec 집합과 양방향으로 같다', () => {
    expect([...registry].sort()).toStrictEqual([...specsOnDisk()].sort());
  });
});

describe('셸 스모크 판정 술어', () => {
  /**
   * 리포터 산출물의 **최소 꼴** — 이 레인의 판정이 읽는 칸만 담는다(`status` 는 test 결말이고
   * `expectedStatus` 는 선언된 기대다. 둘을 따로 두는 이유가 `test.fail` 축이다).
   */
  function reportOf(specFile, tests) {
    return {
      suites: [
        {
          file: specFile,
          specs: tests.map((one, index) => ({
            title: `t${index}`,
            tests: [
              {
                status: one.status,
                expectedStatus: one.expectedStatus ?? 'passed',
                results: [{ status: one.status }],
              },
            ],
          })),
        },
      ],
    };
  }

  it('전건이 돌면 통과다', () => {
    const report = reportOf('touch-measure.spec.ts', [{ status: 'expected' }]);
    const verdict = verdictOf(report, new Set(['touch-measure.spec.ts']), SKIP_NOTE);

    expect(verdict.ok).toBe(true);
  });

  it('건너뛴 test 가 있으면 붉고, 이 레인의 사유 문면이 선다', () => {
    const report = reportOf('touch-measure.spec.ts', [{ status: 'skipped' }, { status: 'expected' }]);
    const verdict = verdictOf(report, new Set(['touch-measure.spec.ts']), SKIP_NOTE);

    expect(verdict.ok).toBe(false);
    // **문면이 실제로 쓰이는지**까지 잰다 — 인자가 버려지면 여정 레인의 사유가 여기 선다.
    expect(verdict.problems.join('\n')).toContain('셸 스모크는 건너뛰지 않는다');
  });

  it('등록부의 spec 이 수집에서 빠지면 붉다 — 개명 하나로 사라지는 그 길이다', () => {
    const report = reportOf('touch-measure-renamed.ts', [{ status: 'expected' }]);
    const verdict = verdictOf(report, new Set(['touch-measure.spec.ts']), SKIP_NOTE);

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join('\n')).toContain('등록부의 spec 이 돌지 않았다');
  });

  it('등록부에 없는 spec 이 돌면 붉다', () => {
    const report = reportOf('new.spec.ts', [{ status: 'expected' }]);
    const verdict = verdictOf(report, registry, SKIP_NOTE);

    expect(verdict.ok).toBe(false);
    expect(verdict.problems.join('\n')).toContain('등록부에 없는 spec 이 돌았다');
  });
});
