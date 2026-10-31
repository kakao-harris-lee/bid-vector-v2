import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

/**
 * 과세 라벨의 **「한 출처」를 원문으로 잠근다**(M7/7E-3b r2 — CR L-1).
 *
 * 값 대조로는 설 수 없다(실측): 같은 글자를 입력 폼에 손으로 적는 변이가 **667 passed** 였다.
 * 같은 문면을 양쪽에 적으면 **어떤 값 등식도 참**이기 때문이다 — 이 slice 가 여정 spec 에서
 * 피했던 「같은 출처에서 양쪽을 끌어오는 공허한 등식」이 그 test 안에 있었다.
 *
 * 그래서 술어를 값이 아니라 **원문의 부재**로 둔다: 라벨 문면은 표가 있는 모듈에만 있고 그것을
 * 쓰는 화면 모듈에는 없다. 이 자리가 `src/` 밖인 이유는 TS 프로그램이 `src` 만 담고 node 내장
 * 모듈 타입이 없기 때문이다(ADR 0011 D-3) — 원문을 읽는 다른 게이트들과 같은 자리다.
 */
const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..');

/** 표가 선언하는 라벨 — **축어**다(표에서 뽑으면 이 검사도 공허해진다). */
const VAT_LABELS = ['부가세 포함', '부가세 별도', '과세 구분 모름'];

/** 라벨 문면이 **있어야 하는** 유일한 자리. */
const TABLE_MODULE = 'src/display/moneyDisplay.ts';

/** 표를 읽어 쓰는 자리 — 여기에 문면이 있으면 출처가 둘이 된다. */
const CONSUMER_MODULES = ['src/screens/WorksheetRecord.tsx', 'src/screens/ResultCard.tsx'];

const read = (relative) => readFileSync(resolve(webRoot, relative), 'utf8');

describe('과세 라벨은 한 자리에만 적혀 있다 (r2 CR L-1)', () => {
  it('표 모듈이 세 문면을 전부 든다', () => {
    const source = read(TABLE_MODULE);

    for (const label of VAT_LABELS) {
      expect(source, `${TABLE_MODULE} 에 ${label} 이 없다 — 표가 비었거나 옮겨 갔다`).toContain(label);
    }
  });

  /**
   * **이 단언이 이 파일의 요점이다.** 같은 문면을 소비 모듈에 적는 변이가 여기서 멈춘다 —
   * 값 등식은 그것을 보지 못한다.
   */
  it.each(CONSUMER_MODULES)('%s 에 라벨 문면이 없다 — 표에서만 온다', (relative) => {
    const source = read(relative);

    for (const label of VAT_LABELS) {
      expect(source, `${relative} 에 ${label} 이 직접 적혀 있다 — 표에서 끌어와야 한다`).not.toContain(
        label,
      );
    }
  });
});
