import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

/**
 * **wire 어휘를 손으로 복제한 자리들을 Kotlin 정본에 묶는다**(M9/web-followups —
 * `OPEN-M9-MONEY-VOCAB-CROSS-LANG`).
 *
 * 등재된 사실: 금액 basis·provenance·통화·과세와 타임라인 명령의 어휘는 Kotlin 이 정본이고
 * (`Basis`·`ProvenanceKind`·`Currency`·`VatTreatment`·`BidCommandTag`), openapi 가 그 칸들을
 * `string` 으로 내므로 web 은 생성 타입에서 유니온을 끌어올 수 없다. 그래서 web 의 표와 e2e 의
 * 라벨 표가 **그것을 따라 적는다** — 그리고 **Kotlin 에 값이 늘면 web 은 어디서도 붉어지지
 * 않았다**(「알 수 없는 상태(…)」로 degrade 했다). 이 파일이 그 등식을 든다.
 *
 * **문면은 대상이 아니다.** e2e 의 한국어 라벨은 **일부러** 산출물에서 끌어오지 않는다(표에서
 * 뽑으면 그 표가 틀려도 대조가 초록이다 — `vat-label-source.test.mjs` 와 같은 관례). 여기서
 * 재는 것은 **키 집합**이다.
 *
 * **Kotlin 원문을 텍스트로 읽는다**(node 에 Kotlin 파서가 없다). 그래서 술어를 「꼴이 바뀌면
 * **던진다**」로 닫는다 — 헤더가 정확히 한 번 나와야 하고, 열거가 비면 예외다. 조용히 빈 집합을
 * 내는 길(= 모든 등식이 공허해지는 길)을 두지 않고, 그 성질 자체를 합성 원문으로 대조한다.
 */
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(webRoot, '..');

const readRepo = (relative) => readFileSync(resolve(repoRoot, relative), 'utf8');
const readWeb = (relative) => readFileSync(resolve(webRoot, relative), 'utf8');

/** 주석을 먼저 걷는다 — 주석 안의 같은 글자가 헤더 계수를 흔들지 않게. */
function withoutKotlinComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//gu, '').replace(/\/\/[^\n]*/gu, '');
}

/** 균형 중괄호로 본문을 떼어 낸다 — 중첩 블록이 있는 enum 도 같은 기계로 읽는다. */
function balancedBodyAfter(source, from, what) {
  const open = source.indexOf('{', from);
  if (open < 0) {
    throw new Error(`${what} 의 본문 여는 중괄호를 찾지 못했다 — 꼴이 바뀌었다`);
  }
  let depth = 0;
  for (let index = open; index < source.length; index += 1) {
    if (source[index] === '{') {
      depth += 1;
    } else if (source[index] === '}') {
      depth -= 1;
      if (depth === 0) {
        return source.slice(open + 1, index);
      }
    }
  }
  throw new Error(`${what} 의 본문이 닫히지 않았다 — 꼴이 바뀌었다`);
}

/**
 * Kotlin `enum class <name>` 의 **열거 전수**. 한 줄 열거·뒤 쉼표·인자를 받는 엔트리·본문 멤버
 * 선언(`;` 뒤)을 모두 받되, **헤더가 하나가 아니거나 열거가 비면 던진다**.
 */
export function kotlinEnumEntries(source, name) {
  const stripped = withoutKotlinComments(source);
  const header = new RegExp(String.raw`\benum\s+class\s+${name}\b`, 'gu');
  const matches = [...stripped.matchAll(header)];
  if (matches.length !== 1) {
    throw new Error(`enum class ${name} 이 ${matches.length} 번 나온다 — 정본을 가리지 못한다`);
  }
  // enum 본문은 `;` 로 열거와 멤버 선언이 갈린다 — 열거는 그 앞까지다.
  const body = balancedBodyAfter(stripped, matches[0].index, `enum class ${name}`).split(';')[0];
  const entries = body
    .split(',')
    .map((one) => /^[A-Z][A-Z0-9_]*/u.exec(one.trim()))
    .filter((one) => one !== null)
    .map((one) => one[0]);
  if (entries.length === 0) {
    throw new Error(`enum class ${name} 의 열거가 비었다 — 꼴이 바뀌었다`);
  }
  return entries;
}

/** Kotlin `const val <name>: String = "…"` 의 값. 없으면 던진다. */
export function kotlinStringConst(source, name) {
  const stripped = withoutKotlinComments(source);
  const found = new RegExp(String.raw`\bconst\s+val\s+${name}\s*:\s*String\s*=\s*"([^"]+)"`, 'u').exec(stripped);
  if (found === null) {
    throw new Error(`const val ${name}: String 을 찾지 못했다 — 꼴이 바뀌었다`);
  }
  return found[1];
}

/**
 * Kotlin 함수 본문의 **문자열 리터럴 전수**. `provenanceLabel` 처럼 wire 로 나가는 글자를
 * `when` 으로 내는 자리를 읽는다 — 실제로 wire 에 실리는 것은 그 리터럴이다.
 */
export function kotlinFunctionStringLiterals(source, name) {
  const stripped = withoutKotlinComments(source);
  const header = new RegExp(String.raw`\bfun\s+${name}\s*\(`, 'gu');
  const matches = [...stripped.matchAll(header)];
  if (matches.length !== 1) {
    throw new Error(`fun ${name} 이 ${matches.length} 번 나온다 — 정본을 가리지 못한다`);
  }
  const body = balancedBodyAfter(stripped, matches[0].index + matches[0][0].length, `fun ${name}`);
  const literals = [...body.matchAll(/"([^"]*)"/gu)].map((one) => one[1]);
  if (literals.length === 0) {
    throw new Error(`fun ${name} 의 문자열 리터럴이 없다 — 꼴이 바뀌었다`);
  }
  return literals;
}

function tsSource(path, text) {
  return ts.createSourceFile(path, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
}

/** `as const` · `satisfies` 를 벗긴다 — 표의 꼴이 그 둘을 쓴다. */
function unwrap(node) {
  let current = node;
  while (current !== undefined && (ts.isAsExpression(current) || ts.isSatisfiesExpression(current))) {
    current = current.expression;
  }
  return current;
}

/** 내보낸 상수 하나의 **객체 리터럴 키 전수**. 찾지 못하면 던진다. */
export function tsTableKeys(text, name, path = 'module.ts') {
  const source = tsSource(path, text);
  let keys = null;
  const visit = (node) => {
    if (ts.isVariableStatement(node)) {
      for (const declaration of node.declarationList.declarations) {
        if (!ts.isIdentifier(declaration.name) || declaration.name.text !== name) {
          continue;
        }
        const initializer = unwrap(declaration.initializer);
        if (initializer === undefined || !ts.isObjectLiteralExpression(initializer)) {
          throw new Error(`${name} 의 초기값이 객체 리터럴이 아니다 — 꼴이 바뀌었다`);
        }
        keys = initializer.properties.map((property) => {
          if (!ts.isPropertyAssignment(property)) {
            throw new Error(`${name} 에 속성 배정이 아닌 원소가 있다 — 꼴이 바뀌었다`);
          }
          if (ts.isIdentifier(property.name)) {
            return property.name.text;
          }
          if (ts.isStringLiteral(property.name)) {
            return property.name.text;
          }
          throw new Error(`${name} 에 리터럴이 아닌 키가 있다 — 꼴이 바뀌었다`);
        });
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  if (keys === null) {
    throw new Error(`${name} 을 찾지 못했다 — 표가 옮겨 갔다`);
  }
  if (keys.length === 0) {
    throw new Error(`${name} 이 비었다 — 빈 표는 모든 등식을 공허하게 만든다`);
  }
  return keys;
}

/** 문자열 리터럴 유니온 타입의 **멤버 전수**(한 갈래뿐인 꼴도 받는다). */
export function tsUnionMembers(text, name, path = 'module.ts') {
  const source = tsSource(path, text);
  let members = null;
  const literalOf = (node) => {
    if (!ts.isLiteralTypeNode(node) || !ts.isStringLiteral(node.literal)) {
      throw new Error(`${name} 에 문자열 리터럴이 아닌 갈래가 있다 — 꼴이 바뀌었다`);
    }
    return node.literal.text;
  };
  const visit = (node) => {
    if (ts.isTypeAliasDeclaration(node) && node.name.text === name) {
      members = ts.isUnionTypeNode(node.type) ? node.type.types.map(literalOf) : [literalOf(node.type)];
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  if (members === null) {
    throw new Error(`type ${name} 을 찾지 못했다 — 유니온이 옮겨 갔다`);
  }
  return members;
}

const KOTLIN = {
  basis: 'shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt',
  provenance: 'procurement/src/main/kotlin/bidvector/procurement/ResolvedBaseAmount.kt',
  provenanceWire: 'app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt',
  command: 'bidding/src/main/kotlin/bidvector/bidding/BidCommand.kt',
};

const basisKt = readRepo(KOTLIN.basis);
const commandKt = readRepo(KOTLIN.command);

const kotlinBasis = kotlinEnumEntries(basisKt, 'Basis');
const kotlinCurrency = kotlinEnumEntries(basisKt, 'Currency');
const kotlinVat = kotlinEnumEntries(basisKt, 'VatTreatment');
const kotlinProvenance = kotlinEnumEntries(readRepo(KOTLIN.provenance), 'ProvenanceKind');
const kotlinCommand = [
  ...kotlinEnumEntries(commandKt, 'BidCommandTag'),
  kotlinStringConst(commandKt, 'OPEN_CASE_COMMAND'),
];

const moneyTs = readWeb('src/display/moneyDisplay.ts');
const caseTs = readWeb('src/display/caseDisplay.ts');
const journeyTs = readWeb('e2e/journey/shared.ts');
const resultsTs = readWeb('e2e/journey/results.spec.ts');

const sorted = (values) => [...values].sort();

describe('Kotlin 정본을 실제로 읽었다 — 빈 읽기는 모든 등식을 공허하게 만든다', () => {
  it('다섯 열거가 전부 비어 있지 않다', () => {
    expect(kotlinBasis.length).toBeGreaterThan(1);
    expect(kotlinCurrency.length).toBeGreaterThan(0);
    expect(kotlinVat.length).toBeGreaterThan(1);
    expect(kotlinProvenance.length).toBeGreaterThan(1);
    expect(kotlinCommand.length).toBeGreaterThan(1);
  });
});

describe('금액 어휘의 키 집합 == Kotlin 정본 (OPEN-M9-MONEY-VOCAB-CROSS-LANG)', () => {
  it('basis — web 표 · web 유니온 · e2e 라벨 표가 모두 Kotlin `Basis` 와 같다', () => {
    const expected = sorted(kotlinBasis);

    expect(sorted(tsTableKeys(moneyTs, 'AMOUNT_BASIS_DISPLAY'))).toStrictEqual(expected);
    expect(sorted(tsUnionMembers(moneyTs, 'AmountBasis'))).toStrictEqual(expected);
    expect(sorted(tsTableKeys(journeyTs, 'AMOUNT_BASIS_LABEL'))).toStrictEqual(expected);
  });

  /**
   * provenance 의 wire 값은 `provenanceLabel` 이 내는 **리터럴**이다. 그 집합이
   * `ProvenanceKind` 와 갈리면 web 이 맞춰야 할 쪽이 둘이 된다 — 그 등식도 여기서 든다.
   */
  it('provenance — wire 리터럴과 `ProvenanceKind` 가 같고, web·e2e 가 그것과 같다', () => {
    const expected = sorted(kotlinProvenance);
    const wire = sorted(new Set(kotlinFunctionStringLiterals(readRepo(KOTLIN.provenanceWire), 'provenanceLabel')));

    expect(wire, 'wire 리터럴과 ProvenanceKind 가 갈렸다').toStrictEqual(expected);
    expect(sorted(tsTableKeys(moneyTs, 'AMOUNT_PROVENANCE_DISPLAY'))).toStrictEqual(expected);
    expect(sorted(tsUnionMembers(moneyTs, 'AmountProvenance'))).toStrictEqual(expected);
    expect(sorted(tsTableKeys(journeyTs, 'AMOUNT_PROVENANCE_LABEL'))).toStrictEqual(expected);
  });

  it('통화 — web 표 · web 유니온 · e2e 라벨 표가 Kotlin `Currency` 와 같다', () => {
    const expected = sorted(kotlinCurrency);

    expect(sorted(tsTableKeys(moneyTs, 'AMOUNT_CURRENCY_DISPLAY'))).toStrictEqual(expected);
    expect(sorted(tsUnionMembers(moneyTs, 'AmountCurrency'))).toStrictEqual(expected);
    expect(sorted(tsTableKeys(journeyTs, 'AMOUNT_CURRENCY_LABEL'))).toStrictEqual(expected);
  });

  /**
   * 과세는 web 쪽이 **이미 계약에 묶여 있다**(요청 스키마에 enum 이 있어 생성 타입에서 끌어온다)
   * — 그래서 유니온 대조가 없다. 아무 계약에도 묶이지 않은 자리는 **e2e 의 축어 표**이고, 그것이
   * 이 행의 실제 대상이다.
   */
  it('과세 — web 표와 e2e spec 의 라벨 표가 Kotlin `VatTreatment` 와 같다', () => {
    const expected = sorted(kotlinVat);

    expect(sorted(tsTableKeys(moneyTs, 'VAT_TREATMENT_DISPLAY'))).toStrictEqual(expected);
    expect(sorted(tsTableKeys(resultsTs, 'VAT_LABEL'))).toStrictEqual(expected);
  });
});

/**
 * 명령 어휘도 같은 자리에서 든다(`OPEN-M9-TIMELINE-COMMAND-WORDING` 의 재발 방지). `satisfies` 는
 * 표와 유니온을 **함께** 줄이는 변이를 지나가므로, 그 축을 이 등식이 받는다.
 */
describe('타임라인 명령 어휘의 키 집합 == Kotlin 정본', () => {
  it('web 표 · web 유니온이 `BidCommandTag` + 여는 명령과 같다', () => {
    const expected = sorted(kotlinCommand);

    expect(sorted(tsTableKeys(caseTs, 'BID_COMMAND_DISPLAY'))).toStrictEqual(expected);
    expect(sorted(tsUnionMembers(caseTs, 'BidCommandToken'))).toStrictEqual(expected);
  });
});

/**
 * **읽기 자신의 대조.** Kotlin 을 텍스트로 읽으므로 「꼴이 바뀌면 조용히 빈 집합」이 가장 무서운
 * 길이다 — 그 길이 **예외**임을 여기서 잰다.
 */
describe('Kotlin 읽기의 양성·음성 대조', () => {
  it('한 줄 열거·뒤 쉼표·주석 섞인 꼴을 읽는다', () => {
    const oneLine = 'enum class Probe { A, B, C }';
    const trailing = 'enum class Probe {\n    A,\n    B,\n}\n';
    const commented = 'enum class Probe {\n    // 주석의 B\n    A,\n    /* 블록 */ B,\n}\n';

    expect(kotlinEnumEntries(oneLine, 'Probe')).toStrictEqual(['A', 'B', 'C']);
    expect(kotlinEnumEntries(trailing, 'Probe')).toStrictEqual(['A', 'B']);
    expect(kotlinEnumEntries(commented, 'Probe')).toStrictEqual(['A', 'B']);
  });

  it('본문에 멤버 선언이 있는 꼴도 열거만 읽는다', () => {
    const withMembers = 'enum class Probe {\n    A,\n    B;\n\n    fun label(): String = name\n}\n';

    expect(kotlinEnumEntries(withMembers, 'Probe')).toStrictEqual(['A', 'B']);
  });

  it('인자를 받는 엔트리는 머리만 읽는다', () => {
    expect(kotlinEnumEntries('enum class Probe { A(1), B(2) }', 'Probe')).toStrictEqual(['A', 'B']);
  });

  /** **음성 대조 넷** — 조용한 초록이 아니라 **예외**가 된다. */
  it('찾지 못하거나 비었거나 둘이면 던진다', () => {
    expect(() => kotlinEnumEntries('class Probe', 'Probe')).toThrow(/0 번/u);
    expect(() => kotlinEnumEntries('enum class Probe {\n}\n', 'Probe')).toThrow(/비었다/u);
    expect(() => kotlinEnumEntries('enum class Probe { A }\nenum class Probe { B }', 'Probe')).toThrow(/2 번/u);
    expect(() => kotlinEnumEntries('enum class Probe', 'Probe')).toThrow(/중괄호/u);
  });

  it('주석 안의 헤더는 세지 않는다 — 계수를 흔들지 않는다', () => {
    const commented = '// enum class Probe { X }\nenum class Probe { A }\n';

    expect(kotlinEnumEntries(commented, 'Probe')).toStrictEqual(['A']);
  });

  it('문자열 상수와 함수 리터럴도 못 찾으면 던진다', () => {
    expect(() => kotlinStringConst('val X: String = "a"', 'X')).toThrow(/찾지 못했다/u);
    expect(() => kotlinFunctionStringLiterals('fun f(x: Int): String = x.toString()', 'f')).toThrow(/중괄호/u);
  });
});

/** TS 읽기도 같은 성질이어야 한다 — 표가 옮겨 가거나 비면 예외다. */
describe('TS 읽기의 음성 대조', () => {
  it('표나 유니온을 못 찾으면 던진다', () => {
    expect(() => tsTableKeys('export const OTHER = { A: 1 };', 'WANTED')).toThrow(/찾지 못했다/u);
    expect(() => tsTableKeys('export const WANTED = {};', 'WANTED')).toThrow(/비었다/u);
    expect(() => tsUnionMembers("export type Other = 'A';", 'Wanted')).toThrow(/찾지 못했다/u);
  });

  it('객체 리터럴이 아닌 초기값은 던진다', () => {
    expect(() => tsTableKeys('export const WANTED = buildTable();', 'WANTED')).toThrow(/객체 리터럴이 아니다/u);
  });

  it('`as const satisfies` 를 벗겨 읽는다', () => {
    const text = "export const WANTED = { A: 1, B: 2 } as const satisfies Record<string, number>;";

    expect(tsTableKeys(text, 'WANTED')).toStrictEqual(['A', 'B']);
  });
});
