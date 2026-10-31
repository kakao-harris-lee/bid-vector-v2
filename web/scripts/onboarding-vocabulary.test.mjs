import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  collectContractItemOrder,
  collectDeclaredItemOrder,
  collectTableKeys,
} from './onboarding-vocabulary.mjs';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const generatedText = readFileSync(resolve(webRoot, 'src', 'api', 'generated', 'operator-api.ts'), 'utf8');
const displayText = readFileSync(resolve(webRoot, 'src', 'display', 'onboardingDisplay.ts'), 'utf8');

/**
 * **도구 양성 대조 먼저**(M7/7E-2b): 추출기가 정말 순서를 읽는지를 합성 입력으로 본다. 이것 없이
 * 아래 등식만 두면 「둘 다 같은 고정값을 냈다」와 구분되지 않는다.
 */
describe('추출기 — 합성 입력에서 선언 순서를 읽는다', () => {
  const synthetic = `
export interface components {
  schemas: {
    Other: { x: ("A" | "B")[] };
    OnboardingChecklist: {
      completed: ("SECOND" | "FIRST" | "THIRD")[];
      pending: ("SECOND" | "FIRST" | "THIRD")[];
    };
  };
}
`;

  it('계약 쪽 추출기가 유니온의 차례를 그대로 낸다', () => {
    expect(collectContractItemOrder(synthetic)).toStrictEqual(['SECOND', 'FIRST', 'THIRD']);
  });

  it('스키마가 없으면 null 이다 — 빈 배열로 접지 않는다(공허한 등식을 만들지 않게)', () => {
    expect(collectContractItemOrder('export interface components { schemas: { Other: { x: string } } }')).toBeNull();
  });

  it('상수 쪽 추출기가 as const · satisfies 껍질을 벗긴다', () => {
    const source = `
export const ONBOARDING_ITEM_ORDER = ['B', 'A'] as const satisfies readonly string[];
`;
    expect(collectDeclaredItemOrder(source)).toStrictEqual(['B', 'A']);
  });

  it('표 키 추출기가 객체 리터럴의 키를 낸다', () => {
    const source = `export const T = { A: 'x', B: 'y' } as const satisfies Record<string, string>;`;
    expect(collectTableKeys(source, 'T')).toStrictEqual(['A', 'B']);
  });
});

describe('체크리스트 항목 어휘 — 계약과 화면이 같은 순서를 든다', () => {
  const contract = collectContractItemOrder(generatedText);

  it('계약에서 어휘를 읽지 못하면 아래 등식이 공허하다 — 먼저 그 사실을 센다', () => {
    expect(contract).not.toBeNull();
    expect(contract.length).toBeGreaterThan(1);
  });

  /**
   * **순서까지** 같다. 타입은 유니온의 차례를 런타임에 남기지 않으므로 이 축은 여기서만 선다 —
   * 어긋나면 화면의 목록이 서버가 정한 차례와 다른 차례로 뜬다(D-7C4C-8 「항목 순서는 커널
   * 선언 순서」).
   */
  it('순서 상수가 계약의 선언 순서 그대로다', () => {
    expect(collectDeclaredItemOrder(displayText)).toStrictEqual(contract);
  });

  it('단계 표와 문면 표가 어휘를 전수로 든다', () => {
    const sorted = [...contract].sort();
    expect([...collectTableKeys(displayText, 'ONBOARDING_ITEM_STEP')].sort()).toStrictEqual(sorted);
    expect([...collectTableKeys(displayText, 'ONBOARDING_ITEM_DISPLAY')].sort()).toStrictEqual(sorted);
  });
});
