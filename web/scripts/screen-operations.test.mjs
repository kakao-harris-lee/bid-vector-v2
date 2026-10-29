import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  collectApprovedOperations,
  collectCalledOperations,
  collectContractOperations,
  compareSets,
} from './screen-operations.mjs';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const queriesText = readFileSync(resolve(webRoot, 'src', 'api', 'queries.ts'), 'utf8');
const generatedText = readFileSync(resolve(webRoot, 'src', 'api', 'generated', 'operator-api.ts'), 'utf8');
const approvedText = readFileSync(resolve(webRoot, 'src', 'api', 'approvedOperations.ts'), 'utf8');
const APPROVED = collectApprovedOperations(approvedText);

/**
 * v M-3 — 화면이 부를 수 있는 operation 의 **전부**가 계약 목록 안인지 잰다. W-4 가 「호출 능력을
 * 쥐는 자리 == `queries.ts`」를 세우므로 그 파일의 호출 집합이 곧 화면의 능력이다.
 */
describe('화면이 부르는 operation 집합', () => {
  const called = collectCalledOperations(queriesText).operations;

  it('비리터럴 경로가 없다 — 게이트가 못 보는 호출을 두지 않는다', () => {
    expect(collectCalledOperations(queriesText).unresolved).toStrictEqual([]);
  });

  it('승인 목록을 그 파일에서 읽는다 — 이 파일이 목록을 복제하지 않는다', () => {
    expect(APPROVED.length).toBeGreaterThan(5);
    expect(APPROVED).toContain('GET /api/notices');
  });

  /** 쌍이어야 한다 — 경로만 적힌 목록은 읽기 경로에 쓰기를 보내는 꼴을 막지 못한다(v r3 M-1). */
  it('목록의 원소가 전부 (메서드, 경로) 쌍이다', () => {
    for (const one of APPROVED) {
      expect(one, '메서드가 없는 원소는 그 경로의 모든 메서드를 허용한다').toMatch(
        /^(GET|POST|PUT|PATCH|DELETE) \/api\//,
      );
    }
  });

  it('호출 집합이 계약 목록과 같다 — 목록 밖 0 · 목록에만 있는 것 0', () => {
    const { outsideApproved, approvedButUnused } = compareSets(called, [...APPROVED]);

    expect(outsideApproved, '계약 목록 밖 operation 을 부른다').toStrictEqual([]);
    expect(approvedButUnused, '목록에 있으나 부르지 않는다 — 목록이 사실보다 넓다').toStrictEqual([]);
  });

  /**
   * 목록의 오타를 잡는다 — 승인 쌍이 **계약에 실재하는가**를 생성 타입에서 본다. 경로만 보던
   * 앞 판은 「그 경로에 그 메서드가 있는가」를 묻지 못했다.
   */
  it('목록의 쌍이 전부 계약에 있다', () => {
    const contract = new Set(collectContractOperations(generatedText));

    expect(contract.size, '생성 타입에서 쌍을 읽지 못하면 이 단언이 공허해진다').toBeGreaterThan(10);
    expect([...APPROVED].filter((one) => !contract.has(one))).toStrictEqual([]);
  });

  /** 술어가 메서드를 **실제로** 가리는지 — 승인 경로의 미승인 메서드는 목록 밖이다. */
  it('승인 경로의 미승인 메서드는 목록 밖으로 센다', () => {
    const { outsideApproved } = compareSets(['DELETE /api/session'], [...APPROVED]);

    expect(outsideApproved).toStrictEqual(['DELETE /api/session']);
  });
});

/** 술어 자체의 양성·음성 — AST 가 무엇을 세고 무엇을 세지 않는지 고정한다. */
describe('호출 경로 수집 술어', () => {
  it('클라이언트 호출의 메서드와 경로를 센다', () => {
    const { operations } = collectCalledOperations(`
      const a = await apiClient.GET('/api/notices');
      const b = await apiClient.POST('/api/bid-cases', { body: {} });
    `);

    expect(operations).toStrictEqual(['GET /api/notices', 'POST /api/bid-cases']);
  });

  it('같은 경로의 다른 메서드는 다른 원소다', () => {
    const { operations } = collectCalledOperations(`
      const a = await apiClient.GET('/api/session');
      const b = await apiClient.DELETE('/api/session');
    `);

    expect(operations).toStrictEqual(['DELETE /api/session', 'GET /api/session']);
  });

  it('주석·문서 문자열의 경로는 세지 않는다 — 문자열 grep 과 다른 자리다', () => {
    const { operations } = collectCalledOperations(`
      // apiClient.GET('/api/strategy') 라고 적힌 주석
      /** @see apiClient.POST('/api/opening-results') */
      const a = await apiClient.GET('/api/notices');
    `);

    expect(operations).toStrictEqual(['GET /api/notices']);
  });

  it('다른 객체의 같은 이름 호출은 세지 않는다', () => {
    const { operations } = collectCalledOperations(`const a = await other.GET('/api/strategy');`);

    expect(operations).toStrictEqual([]);
  });

  it('경로가 리터럴이 아니면 모름으로 센다 — 통과시키지 않는다', () => {
    const { operations, unresolved } = collectCalledOperations(`
      const p = '/api/' + name;
      const a = await apiClient.GET(p);
    `);

    expect(operations).toStrictEqual([]);
    expect(unresolved).toHaveLength(1);
  });
});

/**
 * CR r2 M-2 — 앞 판은 호출 꼴을 **열거**했고 아래 셋이 `paths`·`unresolved` 어느 쪽에도 걸리지
 * 않고 지나갔다(실측). 이제 **모르는 꼴은 `unresolved`** 다.
 */
describe('모르는 사용 꼴은 통과시키지 않는다', () => {
  it.each([
    ['계산 접근', `const a = await apiClient['GET']('/api/strategy');`],
    ['구조 분해', `const { GET } = apiClient; const a = await GET('/api/strategy');`],
    ['별칭', `const c = apiClient; const a = await c.GET('/api/strategy');`],
    ['인자로 넘김', `wrap(apiClient);`],
    ['재수출 아닌 참조', `export const x = apiClient;`],
    ['캐스팅 쓰기', `await (apiClient as unknown as { DELETE: (p: string) => void }).DELETE('/api/session');`],
  ])('%s 은 모름으로 센다', (_label, code) => {
    const { operations, unresolved } = collectCalledOperations(code);

    expect(operations).toStrictEqual([]);
    expect(unresolved.length).toBeGreaterThan(0);
  });

  it('아는 꼴은 그대로 쌍을 센다', () => {
    const { operations, unresolved } = collectCalledOperations(
      `const a = await apiClient.GET('/api/notices');`,
    );

    expect(operations).toStrictEqual(['GET /api/notices']);
    expect(unresolved).toStrictEqual([]);
  });
});
