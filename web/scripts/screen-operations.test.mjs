import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  APPROVED_PATHS,
  collectCalledPaths,
  collectContractPaths,
  compareSets,
} from './screen-operations.mjs';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const queriesText = readFileSync(resolve(webRoot, 'src', 'api', 'queries.ts'), 'utf8');
const generatedText = readFileSync(resolve(webRoot, 'src', 'api', 'generated', 'operator-api.ts'), 'utf8');

/**
 * v M-3 — 화면이 부를 수 있는 operation 의 **전부**가 계약 목록 안인지 잰다. W-4 가 「호출 능력을
 * 쥐는 자리 == `queries.ts`」를 세우므로 그 파일의 호출 집합이 곧 화면의 능력이다.
 */
describe('화면이 부르는 operation 집합', () => {
  const called = collectCalledPaths(queriesText).paths;

  it('비리터럴 경로가 없다 — 게이트가 못 보는 호출을 두지 않는다', () => {
    expect(collectCalledPaths(queriesText).unresolved).toStrictEqual([]);
  });

  it('호출 집합이 계약 목록과 같다 — 목록 밖 0 · 목록에만 있는 것 0', () => {
    const { outsideApproved, approvedButUnused } = compareSets(called, [...APPROVED_PATHS]);

    expect(outsideApproved, '계약 목록 밖 operation 을 부른다').toStrictEqual([]);
    expect(approvedButUnused, '목록에 있으나 부르지 않는다 — 목록이 사실보다 넓다').toStrictEqual([]);
  });

  /** 목록의 오타를 잡는다 — 승인 목록이 **계약에 실재하는 경로**인지 생성 타입에서 본다. */
  it('목록의 경로가 전부 계약에 있다', () => {
    const contract = new Set(collectContractPaths(generatedText));

    expect(contract.size, '생성 타입에서 경로 키를 읽지 못하면 이 단언이 공허해진다').toBeGreaterThan(10);
    expect([...APPROVED_PATHS].filter((one) => !contract.has(one))).toStrictEqual([]);
  });
});

/** 술어 자체의 양성·음성 — AST 가 무엇을 세고 무엇을 세지 않는지 고정한다. */
describe('호출 경로 수집 술어', () => {
  it('클라이언트 호출의 경로 리터럴을 센다', () => {
    const { paths } = collectCalledPaths(`
      const a = await apiClient.GET('/api/notices');
      const b = await apiClient.POST('/api/bid-cases', { body: {} });
    `);

    expect(paths).toStrictEqual(['/api/bid-cases', '/api/notices']);
  });

  it('주석·문서 문자열의 경로는 세지 않는다 — 문자열 grep 과 다른 자리다', () => {
    const { paths } = collectCalledPaths(`
      // apiClient.GET('/api/strategy') 라고 적힌 주석
      /** @see apiClient.POST('/api/opening-results') */
      const a = await apiClient.GET('/api/notices');
    `);

    expect(paths).toStrictEqual(['/api/notices']);
  });

  it('다른 객체의 같은 이름 호출은 세지 않는다', () => {
    const { paths } = collectCalledPaths(`const a = await other.GET('/api/strategy');`);

    expect(paths).toStrictEqual([]);
  });

  it('경로가 리터럴이 아니면 모름으로 센다 — 통과시키지 않는다', () => {
    const { paths, unresolved } = collectCalledPaths(`
      const p = '/api/' + name;
      const a = await apiClient.GET(p);
    `);

    expect(paths).toStrictEqual([]);
    expect(unresolved).toHaveLength(1);
  });
});
