import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  RETIRED_WORDING,
  SWEEP_DEDUCTIONS,
  SWEEP_ROOTS,
  retiredOccurrences,
  retiredOccurrencesIn,
  sweepFiles,
} from './absence-vocabulary.mjs';

/**
 * 부재 어휘 훑기의 짝 test(M14/14A ④).
 *
 * 이 slice 의 acceptance 는 **측정**이다 — 좁힌 전수가 0. 그 0 이 「막았다」는 뜻이려면 훑기가
 * 실제로 파일을 읽었고, 차감이 넓어지지 않았고, 심은 자리를 찾는다는 것이 함께 서야 한다.
 * 그러지 않으면 0 은 **방어 부재와 미측정이 같은 값**이다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');

describe('오늘의 트리', () => {
  it('좁힌 전수가 0 이다', () => {
    expect(retiredOccurrences(webRoot)).toStrictEqual([]);
  });

  /**
   * **비공허 ①** — 0 이 「읽지 않아서 0」이 아님을 센다. 수를 박지 않고 하한만 둔다(파일이 늘면
   * 따라 오른다). 하한은 착수 실측의 절반 아래라 잡음으로 흔들리지 않는다.
   */
  it('훑기가 실제로 많은 파일을 읽었다', () => {
    expect(sweepFiles(webRoot).length).toBeGreaterThan(100);
  });

  /**
   * **비공허 ②** — 뿌리 집합 자신을 단언한다.
   *
   * 앞 판은 `SWEEP_ROOTS` 를 **돌면서** 「뿌리마다 파일이 있다」를 쟀고, 그래서 뿌리를 하나로
   * 좁히는 변이가 **초록이었다**(실측): 돌 목록이 함께 줄어 루프가 조용히 작아진다. 재는 쪽이
   * 재어지는 쪽에서 모집단을 받으면 그 축은 사라진다.
   */
  it('뿌리가 셋 그대로다 — 좁히면 그 뿌리의 위반이 조용히 빠진다', () => {
    expect(SWEEP_ROOTS).toStrictEqual(['src', 'e2e', 'scripts']);
  });

  /** 뿌리가 **이름만 있고 비어** 있지도 않다 — 위 등식과 다른 축이다. */
  it('뿌리마다 읽은 파일이 있다', () => {
    const swept = sweepFiles(webRoot);

    for (const root of ['src', 'e2e', 'scripts']) {
      expect(swept.filter((one) => one.startsWith(`${root}/`)).length, root).toBeGreaterThan(0);
    }
  });
});

describe('차감', () => {
  /** 차감은 **넓어지는 쪽으로만** 자란다 — 한 줄이 늘면 여기서 멈춘다(설계 검토 우회 A-6). */
  it('차감이 하나뿐이다', () => {
    expect(SWEEP_DEDUCTIONS).toStrictEqual(['src/api/generated']);
  });

  it('차감 아래 파일이 실제로 있고, 훑기 목록에는 없다 — 차감이 공허하지 않다', () => {
    const all = sweepFiles(webRoot, SWEEP_ROOTS, []);
    const deducted = all.filter((one) => one.startsWith('src/api/generated/'));

    expect(deducted.length).toBeGreaterThan(0);
    expect(sweepFiles(webRoot).filter((one) => one.startsWith('src/api/generated/'))).toStrictEqual([]);
  });

  /**
   * 차감이 그 자리를 **실제로 가린다** — 걷어낸 문면이 차감 아래에 **지금 있다**(부분 일치).
   * 이 단언이 없으면 차감이 아무것도 가리지 않는데 남아 있을 수 있다.
   */
  it('차감 아래에 부분 일치가 지금 있다 — 그래서 차감에 뜻이 있다', () => {
    const deducted = sweepFiles(webRoot, SWEEP_ROOTS, []).filter((one) => one.startsWith('src/api/generated/'));
    const found = retiredOccurrencesIn(deducted, (file) => readFileSync(resolve(webRoot, file), 'utf8'));

    expect(found.length).toBeGreaterThan(0);
  });

  /** 경로 성분으로 가른다 — 이름을 접두로 공유하는 이웃까지 함께 빼지 않는다. */
  it('이름을 접두로 공유하는 이웃은 차감되지 않는다', () => {
    const planted = ['src/api/generated/x.ts', 'src/api/generated-notes/x.ts'];
    const found = retiredOccurrencesIn(planted, () => `앞 ${RETIRED_WORDING} 뒤`);

    expect(found.map((one) => one.file)).toStrictEqual(planted);
  });
});

describe('양성·음성 대조 — 술어 자신', () => {
  it('심으면 찾는다', () => {
    const found = retiredOccurrencesIn(['src/screens/Planted.tsx'], () => `<span>${RETIRED_WORDING}</span>`);

    expect(found).toStrictEqual([{ file: 'src/screens/Planted.tsx', line: 1 }]);
  });

  it('줄 번호가 그 줄이다 — 자리를 찾을 수 있다', () => {
    const found = retiredOccurrencesIn(['a.ts'], () => `첫 줄\n둘째 줄\n${RETIRED_WORDING}\n넷째 줄`);

    expect(found).toStrictEqual([{ file: 'a.ts', line: 3 }]);
  });

  /** **부분 일치도 센다** — 그 두 낱말을 품은 더 긴 구가 측정을 0 에 닿지 못하게 만든다. */
  it('더 긴 구 안에 들어 있어도 찾는다', () => {
    const found = retiredOccurrencesIn(['a.ts'], () => `지어낸 기본${RETIRED_WORDING}`);

    expect(found.length).toBe(1);
  });

  it('새 사유 문면은 걸리지 않는다 — 음성 대조', () => {
    const found = retiredOccurrencesIn(['a.ts'], () => '주어지지 않음\n아직 받지 못함');

    expect(found).toStrictEqual([]);
  });

  it('뿌리가 없으면 던진다 — 빈 목록으로 조용히 초록이 되지 않는다', () => {
    expect(() => sweepFiles(webRoot, ['no-such-root'])).toThrow();
  });
});
