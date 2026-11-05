import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  collectScreenVocabulary,
  mountedCardsOf,
  vocabularyComponentsOf,
  VOCABULARY_CALLS,
} from './screen-card-coverage.mjs';

/**
 * **DOM 어휘 게이트의 모집단을 소스에서 유도한다**(r1 v M-1 처방 「열거 말고 구조」).
 *
 * r1 의 실제 사고가 이것이다: 게이트는 초록인데 화면 E 카드가 마운트 목록 밖이라 그 카드의
 * 위반(원문 통화)이 살아 있었다. 목록을 손으로 고치는 것만으로는 **다음 카드**에서 같은 일이
 * 반복된다 — 그래서 의무 집합을 세는 자리를 둔다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const screensDir = resolve(webRoot, 'src', 'screens');
const gatePath = resolve(screensDir, 'screenVocabulary.test.tsx');

const { obligations, screens, files } = collectScreenVocabulary(screensDir);
const mounted = mountedCardsOf(readFileSync(gatePath, 'utf8'));

describe('어휘 게이트의 모집단 (r1 v M-1)', () => {
  it('화면 파일을 실제로 읽었다 — 빈 훑기는 모든 등식을 공허하게 만든다', () => {
    expect(files.length).toBeGreaterThan(5);
    expect(VOCABULARY_CALLS.length).toBeGreaterThan(0);
  });

  it('게이트가 마운트 목록을 선언한다', () => {
    expect(mounted, 'GATE_MOUNTED_CARDS 를 AST 로 찾지 못했다').not.toBeNull();
    expect(mounted.every((one) => !one.startsWith('<'))).toBe(true);
  });

  /**
   * **등식이다 — 포함이 아니다.** 포함으로 두면 목록에서 카드를 빼는 변이가 초록이고, 그것이
   * r1 에서 일어난 일의 거울상이다.
   */
  it('표시 어휘를 쓰는 카드 전수 == 게이트가 마운트하는 카드 전수', () => {
    const required = [...new Set(obligations.map((one) => one.name))].sort();

    expect([...mounted].sort(), `의무: ${required.join(', ')}`).toStrictEqual(required);
  });

  /**
   * 화면(`*Screen`)은 질의 목이 필요해 이 게이트가 마운트하지 못한다. 그 제외를 **이름 규약
   * 하나에 기대지 않는다** — 뺀 이름마다 짝이 되는 test 파일이 실제로 있는지 센다.
   */
  it('의무에서 뺀 화면마다 자기 test 파일이 있다', () => {
    for (const { name } of screens) {
      const spec = resolve(screensDir, `${name}.test.tsx`);
      expect(existsSync(spec), `${name} 을 의무에서 뺐는데 ${name}.test.tsx 가 없다`).toBe(true);
    }
  });
});

describe('의무 판정의 양성·음성 대조', () => {
  it('어휘를 쓰는 내보낸 컴포넌트를 집는다', () => {
    const text = `
      import { instantDisplayOf } from '../display/timeDisplay';
      export function PlantedCard({ row }) {
        return <span>{instantDisplayOf(row.occurredAt).text}</span>;
      }
    `;

    expect(vocabularyComponentsOf(text)).toStrictEqual(['PlantedCard']);
  });

  it('화살표 함수로 내보낸 꼴도 집는다 — 선언 꼴 규약에 기대지 않는다', () => {
    const text = `
      export const PlantedArrow = ({ row }) => <span>{currencyDisplayOf(row.currency).label}</span>;
    `;

    expect(vocabularyComponentsOf(text)).toStrictEqual(['PlantedArrow']);
  });

  it('어휘를 안 쓰면 의무가 아니다', () => {
    const text = `export function Plain({ row }) { return <span>{row.title}</span>; }`;

    expect(vocabularyComponentsOf(text)).toStrictEqual([]);
  });

  it('내보내지 않은 내부 컴포넌트는 세지 않는다 — 마운트 대상이 아니다', () => {
    const text = `
      function Inner({ row }) { return <span>{instantDisplayOf(row.at).text}</span>; }
      export function Outer() { return <Inner row={{}} />; }
    `;

    expect(vocabularyComponentsOf(text)).toStrictEqual([]);
  });
});
