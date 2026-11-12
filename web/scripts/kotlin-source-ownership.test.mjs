import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

import { KOTLIN_SOURCE_KEYS, readKotlinSource } from './kotlin-source.mjs';

/**
 * **Kotlin 원문 읽기의 소유권을 구조로 잠근다**(M10/gates-web r2 — verifier r1 M-2).
 *
 * 등재된 사실: r1 까지 복제 금지 술어가 **텍스트**였고(`replace(/\/\/…` 정규식을 원문에서 찾는
 * 꼴), verifier 가 변이 **둘을 초록으로** 통과시켰다 — 별칭 import + `replaceAll` 사본 · 별칭
 * import + `indexOf('//')` 줄 절단 사본. 뒤의 것은 이 OPEN 이 처음 지목한 **옛 URL 절단판 그
 * 자체**다. 텍스트 술어는 **쓰는 방법 하나만 바꾸면** 열린다.
 *
 * **그래서 술어를 글자에서 구조로 옮긴다.** 공유 모듈이 읽기까지 소유하고(`readKotlinSource`),
 * 경로 표는 모듈 **비공개**다. 소비자는 **키만** 넘기므로 원문을 얻을 길이 없다 — 사본을 들여도
 * **걷을 것이 없다**. 남는 우회는 「경로를 일부러 조립하는 것」 하나이고 그것은 리뷰 소관으로
 * 등재했다(`design-review.md` §4 미달 ①).
 *
 * 이 파일의 술어는 **AST** 다. 저장소를 훑어(`CONSUMERS` 배열 열거가 아니다 — 새 소비자가 조용히
 * 빠지는 자리였다) 각 파일의 문자열 리터럴을 보고, 값이 `.kt` 로 끝나는 것이 공유 모듈 밖에
 * 있는지 센다.
 */

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = resolve(here, '..');

/** 읽기를 소유하는 자리 — 이 파일만 `.kt` 경로를 안다. */
const OWNER = 'scripts/kotlin-source.mjs';

/** 훑는 뿌리 — 게이트·하네스가 사는 두 디렉터리. 목록이 아니라 뿌리다. */
const ROOTS = ['scripts', 'e2e'];

const SOURCE_SUFFIXES = ['.mjs', '.ts', '.tsx'];

/** 뿌리 아래 원문 파일 전수(재귀). `node_modules` 는 뿌리 밖이라 들어오지 않는다. */
function sourceFilesUnder(root) {
  const found = [];
  const walk = (directory) => {
    for (const entry of readdirSync(join(webRoot, directory), { withFileTypes: true })) {
      const relative = join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(relative);
      } else if (SOURCE_SUFFIXES.some((suffix) => entry.name.endsWith(suffix))) {
        found.push(relative);
      }
    }
  };
  walk(root);
  return found;
}

/**
 * 원문에서 **Kotlin 원문의 경로로 쓸 수 있는 문자열 리터럴**을 낸다 — 글자 검색이 아니라 AST 다.
 * 주석 안의 같은 글자나 문면의 예시는 리터럴이 아니므로 들지 않는다.
 *
 * 「`.kt` 로 끝난다」만으로는 확장자 토큰(`'.kt'`) 하나까지 걸려 술어가 **경로가 아닌 것**을
 * 든다. 재는 것은 **경로**이므로 디렉터리 구분자를 함께 요구한다 — 읽기 집합의 여덟은 전부
 * 모듈 디렉터리 아래에 있다.
 */
export function kotlinPathLiterals(source, fileName = 'probe.mjs') {
  const tree = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, false);
  const found = [];
  const visit = (node) => {
    const isLiteral = ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node);
    if (isLiteral && node.text.endsWith('.kt') && node.text.includes('/')) {
      found.push(node.text);
    }
    ts.forEachChild(node, visit);
  };
  visit(tree);
  return found;
}

describe('Kotlin 원문 읽기의 소유권 — 경로를 아는 자리가 하나다', () => {
  const files = ROOTS.flatMap(sourceFilesUnder);

  /** 훑기 자체가 공허하지 않은지 먼저 센다 — 0 개를 훑고 통과하는 길을 막는다. */
  it('훑기가 파일을 실제로 모은다', () => {
    expect(files.length).toBeGreaterThan(10);
    expect(files).toContain(OWNER);
  });

  it('`.kt` 경로 리터럴은 공유 모듈 밖에 없다', () => {
    const offenders = files
      .filter((relative) => relative !== OWNER)
      .map((relative) => ({
        relative,
        literals: kotlinPathLiterals(readFileSync(resolve(webRoot, relative), 'utf8'), relative),
      }))
      .filter(({ literals }) => literals.length > 0);

    expect(offenders, '이 파일들이 Kotlin 원문을 직접 열 수 있다 — 읽기는 공유 모듈이 소유한다').toStrictEqual(
      [],
    );
  });

  /**
   * **술어 자신의 양성 대조** — 위 단언은 저장소가 깨끗하면 **아무것도 재지 않는다**. 사본이
   * 되살아나는 꼴을 **합성 원문**으로 먹여 술어가 실제로 거기 걸리는지 잰다. 이 줄이 없으면
   * 술어가 퇴화해도(상수 오타·AST 걷기 누락) 음성 단언은 계속 초록이다.
   */
  it('술어가 합성 소비자에서 `.kt` 읽기를 잡는다 — 음성 단언이 공허하지 않다', () => {
    // 이 상수는 **이어 붙여** 만든다. 통째로 적으면 이 파일 자신이 위 음성 단언에 걸린다 —
    // 그리고 그 사실이 곧 등재된 미달 ①(경로 **조립**은 리터럴 술어를 지나간다)의 실물이다.
    // 술어가 막는 것은 사고로 되살아나는 사본이지 일부러 숨긴 경로가 아니다.
    const plantedPath = `shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis${'.kt'}`;
    const planted = [
      `const p = '${plantedPath}';`,
      'const raw = readFileSync(p, "utf8");',
      "const stripped = raw.replaceAll('//', '');",
    ].join('\n');

    expect(kotlinPathLiterals(planted)).toStrictEqual([plantedPath]);
  });

  /** **음성 대조** — `.kt` 를 열지 않는 소비자는 걸리지 않는다(일률 거절이 아니다). */
  it('키로만 읽는 소비자는 걸리지 않는다', () => {
    const clean = [
      "import { readKotlinSource } from './kotlin-source.mjs';",
      "const basis = readKotlinSource('basis');",
      "const ts = readFileSync('src/display/moneyDisplay.ts', 'utf8');",
    ].join('\n');

    expect(kotlinPathLiterals(clean)).toStrictEqual([]);
  });

  /** 주석·산문 안의 같은 글자는 리터럴이 아니다 — AST 술어라 그것을 들지 않는다. */
  it('주석 안의 `.kt` 는 걸리지 않는다 — 글자 검색이 아니다', () => {
    expect(kotlinPathLiterals('// 정본은 Basis.kt 다\n/* CollectionProperties.kt */\n')).toStrictEqual(
      [],
    );
  });

  /** 확장자 토큰 하나는 경로가 아니다 — 술어가 경로를 재는지 그 줄이 잰다. */
  it('확장자 토큰만으로는 걸리지 않는다', () => {
    expect(kotlinPathLiterals(`const ext = '${'.' + 'kt'}';`)).toStrictEqual([]);
  });
});

describe('읽기 집합 — 한 자리에 전수로 선다', () => {
  /**
   * r1 까지 이 여덟은 **어디에도 전수로 없었다** — 「여덟 전부 초록」을 확인하려면 손으로 목록을
   * 만들어야 했고, 그 목록은 만드는 순간 낡는다. 수와 전수를 여기서 든다.
   */
  it('읽기 집합은 여덟이고 키 전수가 읽힌다', () => {
    expect(KOTLIN_SOURCE_KEYS).toHaveLength(8);

    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(() => readKotlinSource(key), `${key} 를 읽지 못한다`).not.toThrow();
      expect(readKotlinSource(key).length, `${key} 가 빈 원문이다`).toBeGreaterThan(0);
    }
  });

  /** 경로를 내보내지 않는다 — 내보내면 소비자가 그것으로 다시 `.kt` 를 열 수 있다. */
  it('키만 내보내고 경로는 내보내지 않는다', () => {
    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(key.endsWith('.kt'), `${key} 가 경로다 — 키여야 한다`).toBe(false);
    }
  });

  /** 모르는 키는 **던진다** — 조용히 빈 문자열을 내면 모든 등식이 공허해진다. */
  it('모르는 키는 던진다', () => {
    expect(() => readKotlinSource('없는키')).toThrow(/읽기 집합/u);
  });

  /**
   * **읽기가 걷기를 떼지 못한다.** 함수 본문이 공유 걷기를 **호출하는지**를 AST 로 들고(문면이
   * 아니라 호출), 거동으로도 KDoc 이 있는 키의 결과에 블록 주석 여는 표지가 **0** 임을 잰다.
   */
  it('읽기는 걷기를 거쳐서만 값을 낸다', () => {
    const owner = readFileSync(resolve(webRoot, OWNER), 'utf8');
    const tree = ts.createSourceFile(OWNER, owner, ts.ScriptTarget.Latest, false);

    let callsStripInsideReader = false;
    const visit = (node) => {
      if (ts.isFunctionDeclaration(node) && node.name?.text === 'readKotlinSource') {
        const inner = (child) => {
          if (ts.isCallExpression(child) && ts.isIdentifier(child.expression)) {
            callsStripInsideReader ||= child.expression.text === 'withoutKotlinComments';
          }
          ts.forEachChild(child, inner);
        };
        ts.forEachChild(node, inner);
      }
      ts.forEachChild(node, visit);
    };
    visit(tree);

    expect(callsStripInsideReader, 'readKotlinSource 가 공유 걷기를 호출하지 않는다').toBe(true);
    expect(readKotlinSource('provenance')).not.toContain('/**');
  });
});
