import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

import { withKotlinSource } from './kotlin-source.mjs';

/**
 * **내려놓기 둘의 출발 상태·주체를 정본에 묶는다**(M13/13D, 설계 검토 OPEN-3).
 *
 * 등재된 사실: 응답(`BidCase`)에 **열린 명령 집합 칸이 없다.** 그래서 화면은 「어느 상태에서
 * 「나중에」·「건너뜀」이 열리는가」를 **사본**으로 들고, 그 사본이 정본과 갈리면 운영자가 버튼을
 * 눌러 409 를 본다(반대 방향은 열린 전이가 화면에 없는 것이다). 서버가 그 집합을 응답에 실으면
 * 이 게이트는 **필요 없어진다** — 그 길은 승인 경계라 지금 열거를 구조로 닫는다.
 *
 * 선례와 같은 규율이다(교차 언어 어휘 등식): node 에 Kotlin 파서가 없으므로 정본을 **텍스트로**
 * 읽고, 그래서 술어를 「꼴이 바뀌면 **던진다**」로 닫는다. 조용히 빈 집합을 내는 길 — 모든 등식이
 * 공허해지는 길 — 을 두지 않는다. 읽지 못하는 조각을 **버리지 않는다**: 이 게이트가 막으려는
 * 것이 정확히 「정본이 바뀌었는데 화면이 안 붉어진다」이므로 읽기가 그 꼴을 만들어서는 안 된다.
 *
 * **공개 표면이 0 이다.** 추출 커널은 이 파일 안 함수이고 양성·음성 대조가 합성 Kotlin 조각을
 * 그 커널에 직접 먹인다(정본 파일을 고치지 않는다). 원문은 공유 읽기가 **넘긴 함수의 매개변수
 * 하나**로만 들어오므로 이 파일에 원문을 나르는 흐름이 없다.
 *
 * 범위는 **두 명령**이다(팀장 판정). 추출 커널은 명령을 인자로 받으므로 넓히는 일은 뒷날 한 줄
 * 이지만, 기존 전이 넷까지의 확대는 범위 밖이고 OPEN 후보로 등재했다.
 */

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const readWeb = (relative) => readFileSync(resolve(webRoot, relative), 'utf8');

const EXIT_MODULE = 'src/display/bidBoxDisplay.ts';
const STATE_MODULE = 'src/display/caseDisplay.ts';
const GENERATED_MODULE = 'src/api/generated/operator-api.ts';

/** 계약 원문 — 생성 타입이 **값으로 내주지 않는 제약**(글자 수 상한)을 읽는 자리다. */
const contractText = () => readFileSync(resolve(webRoot, '..', 'openapi', 'bidvector-operator-api.yaml'), 'utf8');

/* ── 정본(Kotlin) 쪽 추출 커널 ────────────────────────────────────────────────
   텍스트만 받는다. 공유 읽기가 키를 열고 걷어 이 함수에 먹인다. */

/**
 * `name(` 의 여는 괄호 자리에서 **균형 괄호**로 인자를 쪼갠다.
 *
 * 뒤 쉼표는 Kotlin 의 평범한 표기이고 빈 조각 하나를 낸다 — **맨 뒤의 빈 조각만** 걷고 그 밖의
 * 빈 인자는 던진다(처음 재던 판은 이 처리가 없어 인자 수가 다섯으로 읽혔다).
 */
function argsAt(text, openIndex, what) {
  let depth = 0;
  const parts = [];
  let current = '';
  for (let index = openIndex; index < text.length; index += 1) {
    const character = text[index];
    if (character === '(') {
      depth += 1;
      if (depth === 1) {
        continue;
      }
    } else if (character === ')') {
      depth -= 1;
      if (depth === 0) {
        parts.push(current);
        return withoutTrailingBlank(parts, what);
      }
    }
    if (depth === 1 && character === ',') {
      parts.push(current);
      current = '';
      continue;
    }
    current += character;
  }
  throw new Error(`${what}: 괄호가 닫히지 않았다 — 꼴이 바뀌었다`);
}

function withoutTrailingBlank(parts, what) {
  // 인자가 **하나도 없는** 호출(`listOf()`)은 빈 조각 하나로 읽힌다 — 그것은 「빈 인자」가
  // 아니라 「인자 0 개」다. 둘을 가르지 않으면 빈 열거가 엉뚱한 사유로 거부된다.
  if (parts.length === 1 && parts[0].trim() === '') {
    return [];
  }
  const kept = parts.length > 1 && parts[parts.length - 1].trim() === '' ? parts.slice(0, -1) : parts;
  if (kept.some((one) => one.trim() === '')) {
    throw new Error(`${what}: 빈 인자가 있다 — 꼴이 바뀌었다`);
  }
  return kept;
}

/** `<Prefix>.NAME` 꼴의 토큰 전수. */
function tokensOf(text, prefix) {
  return [...text.matchAll(new RegExp(String.raw`\b${prefix}\.([A-Z][A-Z0-9_]*)`, 'gu'))].map((one) => one[1]);
}

/**
 * 집합 식 하나를 토큰 배열로 — 읽는 꼴은 **둘**이다: 그 자리의 `listOf(…)`·`setOf(…)` 와,
 * **이름으로 가리킨** 같은 꼴의 선언 하나. 그 밖의 꼴(집합 연산·펼치기 등)은 **넓히지 않고
 * 던진다** — 그 꼴이 생기는 날 RED 로 사람을 부르는 쪽이 이 층의 성질이다.
 */
function membersOf(expression, clean, prefix, what) {
  const trimmed = expression.trim();
  if (/^(listOf|setOf)\s*\(/u.test(trimmed)) {
    const pieces = argsAt(trimmed, trimmed.indexOf('('), what);
    const tokens = tokensOf(trimmed, prefix);
    if (tokens.length === 0) {
      throw new Error(`${what}: 열거가 비었다`);
    }
    if (tokens.length !== pieces.length) {
      throw new Error(`${what}: 조각 ${pieces.length} 개인데 ${prefix} 토큰은 ${tokens.length} 개다`);
    }
    return tokens;
  }
  if (/^[A-Z][A-Z0-9_]*$/u.test(trimmed)) {
    return namedMembersOf(clean, trimmed, prefix);
  }
  throw new Error(`${what}: 다룰 수 없는 꼴이다 — ${trimmed.replace(/\s+/gu, ' ').slice(0, 48)}`);
}

/** 이름이 가리키는 `private val NAME = listOf(…)` 를 **정확히 하나** 찾아 푼다. */
function namedMembersOf(clean, name, prefix) {
  const declarations = [...clean.matchAll(new RegExp(String.raw`private\s+val\s+${name}\s*=`, 'gu'))];
  if (declarations.length !== 1) {
    throw new Error(`${name}: 선언이 ${declarations.length} 번이다 — 정본을 가리지 못한다`);
  }
  const rest = clean.slice(declarations[0].index);
  const open = rest.search(/\b(listOf|setOf)\s*\(/u);
  if (open < 0) {
    throw new Error(`${name}: 열거 선언이 아니다`);
  }
  return membersOf(rest.slice(open, rest.indexOf('(', open) + 1 + argsLength(rest, open)), clean, prefix, name);
}

/** 이름 선언의 열거 본문 길이 — 균형 괄호로 센다(줄바꿈 가정을 두지 않는다). */
function argsLength(rest, open) {
  const at = rest.indexOf('(', open);
  let depth = 0;
  for (let index = at; index < rest.length; index += 1) {
    if (rest[index] === '(') depth += 1;
    else if (rest[index] === ')') {
      depth -= 1;
      if (depth === 0) return index - at;
    }
  }
  throw new Error('열거 선언의 괄호가 닫히지 않았다');
}

/** 표를 **조립하는** 식이 이름하는 묶음 전수 — 묶음이 조립에 들지 않으면 그 행은 살아 있지 않다. */
function assembledGroups(clean) {
  const at = clean.indexOf('val BID_TRANSITIONS');
  if (at < 0) {
    throw new Error('정본: 전이표 선언을 찾지 못했다 — 꼴이 바뀌었다');
  }
  const end = clean.indexOf('\n\n', at);
  const groups = new Set(
    [...clean.slice(at, end < 0 ? undefined : end).matchAll(/\b([A-Z][A-Z0-9_]*_ROWS)\b/gu)].map((one) => one[1]),
  );
  if (groups.size === 0) {
    throw new Error('정본: 조립 식이 묶음을 하나도 이름하지 않는다 — 꼴이 바뀌었다');
  }
  return groups;
}

/** 그 호출을 감싼 `private val NAME =` — 호출 자리 앞의 가장 가까운 선언이다. */
function enclosingGroup(clean, index) {
  const hits = [...clean.slice(0, index).matchAll(/private\s+val\s+([A-Z][A-Z0-9_]*)\s*=/gu)];
  if (hits.length === 0) {
    throw new Error('정본: 행을 감싼 묶음 선언을 찾지 못했다');
  }
  return hits[hits.length - 1][1];
}

/**
 * 명령 하나의 **행** — 출발 상태 집합과 주체 집합.
 *
 * anchor 는 **표를 짓는 호출의 둘째 인자**다. 명령 토큰 훑기로는 안 된다(실측: 토큰 전수와 유일
 * 수가 하나 다르고, 그 하나가 표 밖의 비교문이다).
 *
 * 같은 명령의 행 묶음이 **정확히 하나**여야 한다 — 둘이면 살아 있는 집합이 넓어지는데 읽기는 한
 * 묶음만 보게 된다. 그 묶음이 **조립에 들어야** 한다 — 들지 않으면 서버는 전부 거부하는데 화면은
 * 버튼을 계속 보인다.
 */
function transitionRowOf(clean, command) {
  const groups = assembledGroups(clean);
  const rows = [];
  for (const match of clean.matchAll(/\brows\s*\(/gu)) {
    if (/private\s+fun\s+$/u.test(clean.slice(Math.max(0, match.index - 24), match.index))) {
      continue;
    }
    const what = `정본의 ${command} 행`;
    const parts = argsAt(clean, match.index + match[0].length - 1, '행 생성 호출');
    if (parts.length !== 4) {
      throw new Error(`행 생성 호출의 인자가 ${parts.length} 개다 — 꼴이 바뀌었다`);
    }
    if (parts[1].trim() !== `BidCommandTag.${command}`) {
      continue;
    }
    const group = enclosingGroup(clean, match.index);
    if (!groups.has(group)) {
      throw new Error(`${what}: 조립에 없는 묶음(${group})에 있다 — 서버는 그 전이를 받지 않는다`);
    }
    rows.push({
      group,
      from: membersOf(parts[0], clean, 'BidStateTag', `${what}의 출발`),
      actors: membersOf(parts[3], clean, 'ActorKind', `${what}의 주체`),
    });
  }
  if (rows.length !== 1) {
    throw new Error(`정본의 ${command} 행이 ${rows.length} 묶음이다 — 정본을 가리지 못한다`);
  }
  return rows[0];
}

/** 정본에서 읽는다 — 원문은 **넘긴 함수의 매개변수**로만 들어온다. */
const contractRowOf = (command) => withKotlinSource('bidTransitions', transitionRowOf, command);

/* ── 화면·계약 쪽 읽기(AST·앵커) ──────────────────────────────────────────── */

const parse = (text, name) => ts.createSourceFile(name, text, ts.ScriptTarget.ESNext, true);

/** `as const` · `satisfies` 껍질을 벗긴다. */
function unwrap(node) {
  let current = node;
  while (
    current !== undefined &&
    (ts.isAsExpression(current) ||
      (typeof ts.isSatisfiesExpression === 'function' && ts.isSatisfiesExpression(current)))
  ) {
    current = current.expression;
  }
  return current;
}

const nameText = (node) =>
  ts.isIdentifier(node) || ts.isStringLiteralLike(node) ? node.text : null;

/** 그 이름의 변수 선언이 든 객체 리터럴 — **정확히 하나**여야 한다. */
function objectLiteralOf(sourceText, fileName, name) {
  const source = parse(sourceText, fileName);
  const found = [];
  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === name) {
      const initializer = unwrap(node.initializer);
      if (initializer !== undefined && ts.isObjectLiteralExpression(initializer)) {
        found.push(initializer);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  if (found.length !== 1) {
    throw new Error(`${fileName}: ${name} 의 객체 리터럴이 ${found.length} 개다 — 꼴이 바뀌었다`);
  }
  return found[0];
}

/** 그 객체 리터럴의 속성 하나 — 없거나 둘이면 던진다. */
function propertyOf(objectLiteral, key, what) {
  const hits = objectLiteral.properties.filter(
    (one) => ts.isPropertyAssignment(one) && nameText(one.name) === key,
  );
  if (hits.length !== 1) {
    throw new Error(`${what}: ${key} 속성이 ${hits.length} 개다`);
  }
  return hits[0].initializer;
}

/** 화면이 든 내려놓기 출발 집합 — 키마다 문자열 배열이다. */
function screenExitFromStates() {
  const object = objectLiteralOf(readWeb(EXIT_MODULE), EXIT_MODULE, 'EXIT_FROM_STATES');
  const table = {};
  for (const property of object.properties) {
    if (!ts.isPropertyAssignment(property)) {
      throw new Error('화면 표에 속성 대입이 아닌 조각이 있다 — 꼴이 바뀌었다');
    }
    const key = nameText(property.name);
    const value = unwrap(property.initializer);
    if (key === null || value === undefined || !ts.isArrayLiteralExpression(value)) {
      throw new Error(`화면 표의 ${key ?? '(이름 없음)'} 가 배열 리터럴이 아니다`);
    }
    const states = value.elements.map((one) => {
      if (!ts.isStringLiteralLike(one)) {
        throw new Error(`화면 표의 ${key} 에 문자열이 아닌 원소가 있다`);
      }
      return one.text;
    });
    if (states.length === 0) {
      throw new Error(`화면 표의 ${key} 가 비었다`);
    }
    table[key] = states;
  }
  if (Object.keys(table).length === 0) {
    throw new Error('화면 표가 비었다');
  }
  return table;
}

/** 화면이 든 자유 텍스트 상한 — 입력 꼴 표의 그 행이 선언한 값이다. */
function screenSkipMaxChars() {
  const input = objectLiteralOf(readWeb(EXIT_MODULE), EXIT_MODULE, 'ACTION_INPUT');
  const skip = unwrap(propertyOf(input, 'SKIP', '입력 꼴 표'));
  if (skip === undefined || !ts.isObjectLiteralExpression(skip)) {
    throw new Error('입력 꼴 표의 건너뜀 행이 객체 리터럴이 아니다');
  }
  const declared = propertyOf(skip, 'maxChars', '건너뜀 입력 꼴');
  if (!ts.isNumericLiteral(declared)) {
    throw new Error('건너뜀 입력 꼴의 상한이 숫자 리터럴이 아니다');
  }
  return Number.parseInt(declared.text, 10);
}

/** 화면 상태 표의 키 전수 — 상태 전수 루프가 이 집합에서 나온다. */
function screenStateKeys() {
  const object = objectLiteralOf(readWeb(STATE_MODULE), STATE_MODULE, 'BID_CASE_STATE_DISPLAY');
  const keys = object.properties.map((one) => {
    if (!ts.isPropertyAssignment(one)) {
      throw new Error('상태 표에 속성 대입이 아닌 조각이 있다');
    }
    return nameText(one.name);
  });
  if (keys.some((one) => one === null) || keys.length === 0) {
    throw new Error('상태 표의 키를 읽지 못했다');
  }
  return keys;
}

/** 계약이 선언한 상태 어휘 — 생성 타입의 그 칸 유니온이다(W-5 가 문서와 바이트 등식을 든다). */
function contractStateVocabulary() {
  const source = parse(readWeb(GENERATED_MODULE), GENERATED_MODULE);
  const schema = [];
  const visit = (node) => {
    if (
      ts.isPropertySignature(node) &&
      ts.isIdentifier(node.name) &&
      node.name.text === 'BidCase' &&
      node.type !== undefined &&
      ts.isTypeLiteralNode(node.type)
    ) {
      schema.push(node.type);
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  if (schema.length !== 1) {
    throw new Error(`생성 타입: 건 스키마가 ${schema.length} 개다 — 꼴이 바뀌었다`);
  }
  const state = schema[0].members.find(
    (one) => ts.isPropertySignature(one) && ts.isIdentifier(one.name) && one.name.text === 'state',
  );
  if (state?.type === undefined || !ts.isUnionTypeNode(state.type)) {
    throw new Error('생성 타입: 상태 칸이 유니온이 아니다 — 꼴이 바뀌었다');
  }
  const values = state.type.types.map((one) =>
    ts.isLiteralTypeNode(one) && ts.isStringLiteral(one.literal) ? one.literal.text : null,
  );
  if (values.some((one) => one === null) || values.length === 0) {
    throw new Error('생성 타입: 상태 유니온에 문자열이 아닌 원소가 있다');
  }
  return values;
}

/**
 * 계약 원문의 건너뜀 사유 글자 수 상한. **생성 타입에는 값으로 오지 않는다**(설명 주석에만
 * 있고 칸의 타입은 그냥 문자열이다) — 그래서 이 한 축만 원문을 읽는다.
 *
 * 블록을 앵커로 떼고 그 안에서 **정확히 하나**를 요구한다. 그 문서에 같은 수가 여러 칸에 서므로
 * 전역 훑기로는 어느 칸의 값인지 가릴 수 없다.
 */
function contractSkipMaxLength(yamlText = contractText()) {
  const header = '\n    SkipRequest:\n';
  const at = yamlText.indexOf(header);
  if (at < 0 || yamlText.indexOf(header, at + 1) >= 0) {
    throw new Error('계약: 건너뜀 요청 블록을 하나로 찾지 못했다 — 꼴이 바뀌었다');
  }
  const rest = yamlText.slice(at + header.length);
  const end = rest.search(/\n {4}\S/u);
  const block = end < 0 ? rest : rest.slice(0, end);
  const found = [...block.matchAll(/^\s*maxLength:\s*(\d+)\s*$/gmu)];
  if (found.length !== 1) {
    throw new Error(`계약: 건너뜀 요청 블록의 상한 선언이 ${found.length} 개다 — 꼴이 바뀌었다`);
  }
  return Number.parseInt(found[0][1], 10);
}

/* ── 등식 ─────────────────────────────────────────────────────────────────── */

const sorted = (values) => [...values].sort();

describe('내려놓기 둘의 출발 집합 — 화면 표 == 계약 전이표', () => {
  it('「나중에」의 출발 상태가 정본과 같다', () => {
    const contract = contractRowOf('DEFER');
    const screen = screenExitFromStates();

    expect(contract.from.length, '정본의 출발 집합이 비었다 — 등식이 공허해진다').toBeGreaterThan(0);
    expect(sorted(screen.DEFER)).toStrictEqual(sorted(contract.from));
  });

  it('「건너뜀」의 출발 상태가 정본과 같다 — 이름으로 가리킨 선언을 거쳐 푼다', () => {
    const contract = contractRowOf('SKIP');
    const screen = screenExitFromStates();

    expect(contract.from.length, '정본의 출발 집합이 비었다 — 등식이 공허해진다').toBeGreaterThan(0);
    expect(sorted(screen.SKIP)).toStrictEqual(sorted(contract.from));
  });

  /** 화면이 든 키가 정본에서 읽은 명령과 **짝이 맞는가** — 표에 없는 키를 화면이 들면 붉는다. */
  it('화면 표의 키가 내려놓기 둘뿐이다', () => {
    expect(sorted(Object.keys(screenExitFromStates()))).toStrictEqual(['DEFER', 'SKIP']);
  });

  /**
   * **주체 축**(설계 검토 우회 ⑧) — 상태가 맞아도 그 전이가 시스템 전용이면 운영자의 요청은
   * 전부 거부된다. 같은 호출의 넷째 인자라 기계가 같다.
   */
  it('내려놓기 둘을 사용자가 낼 수 있다', () => {
    for (const command of ['DEFER', 'SKIP']) {
      const { actors } = contractRowOf(command);

      expect(actors.length, `${command}: 주체 집합이 비었다`).toBeGreaterThan(0);
      expect(actors, `${command}: 사용자가 낼 수 없는 전이를 화면이 버튼으로 낸다`).toContain('USER');
    }
  });
});

describe('어휘 축 — 읽은 상태가 계약이 아는 상태인가', () => {
  it('정본에서 읽은 출발 상태 전수가 계약 어휘에 든다', () => {
    const vocabulary = new Set(contractStateVocabulary());

    expect(vocabulary.size, '계약 어휘 추출이 비었다').toBeGreaterThan(0);
    for (const command of ['DEFER', 'SKIP']) {
      const { from } = contractRowOf(command);

      expect(from.filter((one) => !vocabulary.has(one)), `${command}: 계약이 모르는 상태다`).toStrictEqual([]);
    }
  });

  /**
   * 상태 전수 루프의 **출처가 계약과 같은가.** 그 루프(카드 test)는 화면 상태 표의 키에서
   * 상태를 뽑으므로, 그 집합이 계약 어휘와 같다는 것을 **여기서 독립으로** 센다 — 루프가 자기
   * 출처를 자기 근거로 삼지 않게 하는 자리다.
   */
  it('화면 상태 표의 키 집합이 계약 어휘와 같다', () => {
    expect(sorted(screenStateKeys())).toStrictEqual(sorted(contractStateVocabulary()));
  });
});

describe('자유 텍스트 상한 — 화면 == 계약 원문', () => {
  it('건너뜀 사유의 글자 수 상한이 계약이 선 값과 같다', () => {
    const contract = contractSkipMaxLength();

    expect(contract, '계약의 상한이 양수가 아니다').toBeGreaterThan(0);
    expect(screenSkipMaxChars()).toBe(contract);
  });

  it('블록을 가리지 못하면 통과가 아니라 거부다', () => {
    expect(() => contractSkipMaxLength('components:\n  schemas:\n    Other:\n      type: object\n')).toThrow(
      /하나로 찾지 못했다/u,
    );
    const twice = '\n    SkipRequest:\n      properties:\n        a:\n          maxLength: 1\n        b:\n          maxLength: 2\n';
    expect(() => contractSkipMaxLength(twice)).toThrow(/상한 선언이 2 개다/u);
  });
});

/* ── 술어 자신의 대조 — 합성 Kotlin 조각을 커널에 직접 먹인다 ────────────────
   정본 파일을 고치지 않는다. 각 조각은 설계 검토의 우회 번호에 대응한다. */

const SYNTHETIC_HEAD = `
private val USER_ONLY = setOf(ActorKind.USER)
private val SYSTEM_ONLY = setOf(ActorKind.SYSTEM)
private val PRE = listOf(BidStateTag.WATCHING, BidStateTag.DEFERRED)
`;

/** 조립 식만 — 머리(주체·이름 선언)는 각 조각이 **한 번만** 붙인다(두 번이면 읽기가 던진다). */
const syntheticTable = (groups) => `
val BID_TRANSITIONS: Map<TransitionKey, TransitionRule> = ${groups}
`;

describe('술어 자신의 대조 — 다룰 수 없는 꼴에 던진다', () => {
  it('음성 대조 — 평범한 꼴 둘(그 자리의 열거 · 이름으로 가리킨 선언)을 읽는다', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS =
    rows(
        listOf(BidStateTag.WATCHING, BidStateTag.PRICE_RECEIVED),
        BidCommandTag.DEFER,
        setOf(BidStateTag.DEFERRED),
        USER_ONLY,
    )
private val B_ROWS = rows(PRE, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED), USER_ONLY)
${syntheticTable('A_ROWS + B_ROWS')}`;

    expect(transitionRowOf(clean, 'DEFER').from).toStrictEqual(['WATCHING', 'PRICE_RECEIVED']);
    expect(transitionRowOf(clean, 'SKIP').from).toStrictEqual(['WATCHING', 'DEFERRED']);
    expect(transitionRowOf(clean, 'DEFER').actors).toStrictEqual(['USER']);
  });

  it('우회 ③ — 같은 명령의 행 묶음이 둘이면 던진다', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(PRE, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
private val C_ROWS = rows(listOf(BidStateTag.BID_PLANNED), BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
${syntheticTable('A_ROWS + C_ROWS')}`;

    expect(() => transitionRowOf(clean, 'DEFER')).toThrow(/2 묶음이다/u);
  });

  it('우회 ④ — 묶음이 조립에 들지 않으면 던진다', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(PRE, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
private val DEAD_ROWS = rows(PRE, BidCommandTag.SKIP, setOf(BidStateTag.SKIPPED), USER_ONLY)
${syntheticTable('A_ROWS')}`;

    expect(() => transitionRowOf(clean, 'SKIP')).toThrow(/조립에 없는 묶음/u);
  });

  it('우회 ⑥ — 출발 집합이 다룰 수 없는 꼴이면 던진다', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(PRE - BidStateTag.DEFERRED, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
${syntheticTable('A_ROWS')}`;

    expect(() => transitionRowOf(clean, 'DEFER')).toThrow(/다룰 수 없는 꼴/u);
  });

  it('우회 ⑧ — 주체가 시스템 전용이면 그 사실을 그대로 낸다(단언이 그것을 붉힌다)', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(PRE, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), SYSTEM_ONLY)
${syntheticTable('A_ROWS')}`;

    expect(transitionRowOf(clean, 'DEFER').actors).toStrictEqual(['SYSTEM']);
  });

  it('조립 식이나 열거를 못 읽으면 던진다 — 조용히 빈 집합을 내지 않는다', () => {
    const noTable = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(PRE, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
`;
    expect(() => transitionRowOf(noTable, 'DEFER')).toThrow(/전이표 선언을 찾지 못했다/u);

    const emptyList = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(listOf(), BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
${syntheticTable('A_ROWS')}`;
    expect(() => transitionRowOf(emptyList, 'DEFER')).toThrow(/열거가 비었다/u);
  });

  it('조각 수와 토큰 수가 다르면 던진다 — 읽지 못한 조각을 버리지 않는다', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(listOf(BidStateTag.WATCHING, somethingElse), BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
${syntheticTable('A_ROWS')}`;

    expect(() => transitionRowOf(clean, 'DEFER')).toThrow(/토큰은 1 개다/u);
  });

  it('모르는 명령은 던진다', () => {
    const clean = `${SYNTHETIC_HEAD}
private val A_ROWS = rows(PRE, BidCommandTag.DEFER, setOf(BidStateTag.DEFERRED), USER_ONLY)
${syntheticTable('A_ROWS')}`;

    expect(() => transitionRowOf(clean, 'NOT_A_COMMAND')).toThrow(/0 묶음이다/u);
  });
});

describe('화면 쪽 읽기의 대조', () => {
  it('표를 찾지 못하면 던진다', () => {
    expect(() => objectLiteralOf('export const other = {};\n', 'x.ts', 'EXIT_FROM_STATES')).toThrow(
      /0 개다/u,
    );
  });

  it('속성이 둘이면 던진다 — 병합이 키를 접은 자리를 잡는다', () => {
    const doubled = objectLiteralOf('const T = { a: 1, a: 2 };\n', 'x.ts', 'T');

    expect(() => propertyOf(doubled, 'a', '대조')).toThrow(/2 개다/u);
  });
});
