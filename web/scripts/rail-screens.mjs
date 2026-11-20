// **레일을 가진 화면 전수**를 자리 표에서 구성한다(M14/14B 수정 라운드 1 — v HIGH-1).
//
// 왜 필요한가: 레일 축의 비공허가 **합계 하나**(`seen >= 1`)이면, 레일이 둘 이상 선 뒤에는 어느
// 하나가 측정에서 빠져도 나머지가 그 수를 떠받쳐 조용히 지나간다. 이 축이 세워진 계기가 바로
// 「시작하기의 레일이 통째로 사라졌다」인데, 합계 하한으로는 그 회귀를 다시 잡지 못한다.
//
// 그래서 보증을 **화면마다**로 바꾼다. 「어느 화면이 레일을 가져야 하는가」를 손으로 적으면 뒤
// 레인이 자기 줄을 안 적는 만큼 모집단이 뒤처지므로, **소스에서 유도한다**:
//
//   자리 표(`screenSlots` + 표 밖 둘)의 element → 그 JSX 태그가 가리키는 모듈
//     → 그 모듈이 `components/TwoColumn` 의 바인딩을 **JSX 태그로 그리는가**
//
// 그리면 그 자리는 **레일을 그리는 화면**이고, 레일 축은 그 주소에서 레일을 **하나 이상** 봐야
// 한다. 14C 의 워크시트·14D 의 입찰함이 레일을 붙이면 이 집합에 자동으로 들어온다.
//
// **「닿는가」가 아니라 「그리는가」다**(수정 라운드 1 실측). 처음에는 import 폐포로 물었는데
// 그 술어는 **거짓 양성**을 냈다: 오늘의 공고 화면이 시작하기 모듈에서 배너를 들여오므로 폐포가
// `TwoColumn` 에 닿고, 레일을 하나도 그리지 않는 그 화면에 레일 의무가 걸렸다(실측 — 여정 레인이
// `home` 을 붉혔다). 속성을 재귀로 물으면 술어가 묽어진다.
//
// **알려진 제한**: 화면이 레일을 **자기 모듈 밖**(별 파일의 하위 컴포넌트)에서 그리면 이 술어가
// 못 본다. 그 자리는 아래 래칫 목록이 받는다 — 한 줄이면 된다.
//
// **읽기는 AST 다**(텍스트 훑기가 아니다). 그리고 `routes.tsx` 를 **import 하지 않는다**: 들여오면
// 앱 트리 전체가 Playwright 프로세스로 딸려 온다(`journey-screen-table.mjs` 와 같은 사유).
import { existsSync, readFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';

import ts from 'typescript';

const parse = (text, fileName) =>
  ts.createSourceFile(fileName, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);

/** `as const`·`satisfies` 를 벗겨 알맹이 식을 낸다. */
function unwrap(node) {
  let current = node;
  while (
    current !== undefined &&
    (ts.isAsExpression(current) || ts.isSatisfiesExpression(current) || ts.isParenthesizedExpression(current))
  ) {
    current = current.expression;
  }
  return current;
}

function declarationInitializer(source, name) {
  let found;
  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === name) {
      found = unwrap(node.initializer);
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

function stringProperty(object, key) {
  for (const property of object.properties) {
    if (
      ts.isPropertyAssignment(property) &&
      property.name !== undefined &&
      ts.isIdentifier(property.name) &&
      property.name.text === key &&
      ts.isStringLiteralLike(property.initializer)
    ) {
      return property.initializer.text;
    }
  }
  return undefined;
}

/** `element: <Foo />` 의 **태그 이름**. element 가 없거나 꼴이 다르면 `undefined`. */
function elementTagOf(object) {
  for (const property of object.properties) {
    if (
      !ts.isPropertyAssignment(property) ||
      property.name === undefined ||
      !ts.isIdentifier(property.name) ||
      property.name.text !== 'element'
    ) {
      continue;
    }
    const value = unwrap(property.initializer);
    const opening = ts.isJsxSelfClosingElement(value)
      ? value
      : ts.isJsxElement(value)
        ? value.openingElement
        : undefined;
    if (opening !== undefined && ts.isIdentifier(opening.tagName)) {
      return opening.tagName.text;
    }
  }
  return undefined;
}

/** import 로 들어온 이름 → 그 모듈의 지정자(상대 경로). */
function importSpecifiers(source) {
  const byName = new Map();
  for (const statement of source.statements) {
    if (
      !ts.isImportDeclaration(statement) ||
      !ts.isStringLiteral(statement.moduleSpecifier) ||
      statement.importClause === undefined ||
      statement.importClause.isTypeOnly
    ) {
      continue;
    }
    const from = statement.moduleSpecifier.text;
    const clause = statement.importClause;
    if (clause.name !== undefined) {
      byName.set(clause.name.text, from);
    }
    const bindings = clause.namedBindings;
    if (bindings !== undefined && ts.isNamedImports(bindings)) {
      for (const one of bindings.elements) {
        if (!one.isTypeOnly) {
          byName.set(one.name.text, from);
        }
      }
    }
  }
  return byName;
}

/** 상대 지정자를 실제 파일로 — 확장자는 이 트리의 둘(`.tsx` · `.ts`)뿐이다. */
function resolveModule(fromFile, specifier) {
  if (!specifier.startsWith('.')) {
    return undefined;
  }
  const base = resolve(dirname(fromFile), specifier);
  for (const candidate of [`${base}.tsx`, `${base}.ts`, resolve(base, 'index.tsx'), resolve(base, 'index.ts')]) {
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return undefined;
}

/**
 * [file] 이 [target] 의 바인딩을 **JSX 태그로 그리는가**.
 *
 * 들여오기만 하고 안 그리는 모듈은 거짓이다 — 레일 의무의 근거는 「그 화면에 레일이 선다」이지
 * 「그 모듈이 그 심볼을 안다」가 아니다. 타입 전용 import 는 애초에 바인딩 집합에 들지 않는다
 * (그릴 수 없으므로).
 */
function rendersTwoColumn(file, target) {
  const here = resolve(file);
  if (!existsSync(here)) {
    return false;
  }
  const source = parse(readFileSync(here, 'utf8'), here);
  const bindings = new Set();
  for (const [name, specifier] of importSpecifiers(source)) {
    if (resolveModule(here, specifier) === target) {
      bindings.add(name);
    }
  }
  if (bindings.size === 0) {
    return false;
  }
  let drawn = false;
  const visit = (node) => {
    const opening = ts.isJsxSelfClosingElement(node)
      ? node
      : ts.isJsxOpeningElement(node)
        ? node
        : undefined;
    if (opening !== undefined && ts.isIdentifier(opening.tagName) && bindings.has(opening.tagName.text)) {
      drawn = true;
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return drawn;
}

/**
 * 자리 전수 — 자리 표 + 표 밖 둘(로그인 · 없는 주소). `journey-screen-table.mjs` 와 같은 자리를
 * 읽지만 이쪽은 **element 태그**까지 본다.
 */
function allSlots(source) {
  const table = declarationInitializer(source, 'screenSlots');
  if (table === undefined || !ts.isArrayLiteralExpression(table) || table.elements.length === 0) {
    throw new Error('routes.tsx: screenSlots 의 배열 리터럴을 찾지 못했다(또는 비었다)');
  }
  const slots = table.elements.map((element) => {
    if (!ts.isObjectLiteralExpression(element)) {
      throw new Error('routes.tsx: screenSlots 의 원소가 객체 리터럴이 아니다');
    }
    const id = stringProperty(element, 'id');
    const path = stringProperty(element, 'path');
    if (id === undefined || path === undefined) {
      throw new Error('routes.tsx: screenSlots 의 원소에 문자열 리터럴 id·path 가 없다');
    }
    return { id, path, tag: elementTagOf(element) };
  });
  const standalone = ['loginRoute', 'notFoundSlot'].map((name) => {
    const initializer = declarationInitializer(source, name);
    if (initializer === undefined || !ts.isObjectLiteralExpression(initializer)) {
      throw new Error(`routes.tsx: ${name} 의 객체 리터럴을 찾지 못했다`);
    }
    const id = stringProperty(initializer, 'id');
    const path = stringProperty(initializer, 'path');
    if (id === undefined || path === undefined) {
      throw new Error(`routes.tsx: ${name} 에 문자열 리터럴 id·path 가 없다`);
    }
    return { id, path, tag: elementTagOf(initializer) };
  });
  return [...slots, ...standalone];
}

/**
 * **레일을 그리는 자리 전수**. 각 원소는 `{ id, path }` 이고, 그 화면 모듈의 import 폐포가
 * `components/TwoColumn` 에 닿는다.
 *
 * element 를 선언하지 않은 자리(「준비 중」 빈 자리)는 그릴 화면이 없으므로 집합 밖이다.
 * 들여오기만 하고 **안 그리는** 모듈도 집합 밖이다(거짓 양성을 막는 자리다).
 * element 는 있는데 그 태그를 **import 에서 찾지 못하면 던진다** — 조용히 빠지면 그 화면이
 * 레일을 붙여도 모집단에 안 든다.
 */
export const RAIL_SCREEN_FLOOR = ['onboarding'];

/**
 * 구성만으로는 **손으로 짠 두 단**을 막지 못한다(§12.3 (z) ⓓ 가 등재한 OPEN): 화면이
 * `TwoColumn` 을 그만 쓰고 같은 모양을 직접 적으면 그 자리는 위 집합에서 **조용히 빠지고**,
 * 「그 화면의 레일을 하나 이상 봐야 한다」는 의무도 함께 사라진다. 실측으로 확인된 우회다.
 *
 * 그래서 집합에 **래칫**을 건다 — 구성은 **늘어나는 쪽**을 맡고(새 레일 화면은 아무 줄도 안 적고
 * 모집단에 든다), 이 목록은 **줄어드는 쪽**을 막는다. 14C·14D 는 머지 차례에 자기 자리 이름
 * 한 줄만 더한다. 목록은 **올리기만** 한다.
 */
export function railScreenSlots(webRoot) {
  const routesFile = resolve(webRoot, 'src', 'routes.tsx');
  const source = parse(readFileSync(routesFile, 'utf8'), routesFile);
  const imports = importSpecifiers(source);
  const target = resolve(webRoot, 'src', 'components', 'TwoColumn.tsx');
  if (!existsSync(target)) {
    throw new Error(`레일 원시 요소를 찾지 못했다: ${relative(webRoot, target)}`);
  }

  const withRail = [];
  for (const slot of allSlots(source)) {
    if (slot.tag === undefined) {
      continue;
    }
    const specifier = imports.get(slot.tag);
    const moduleFile = specifier === undefined ? undefined : resolveModule(routesFile, specifier);
    if (moduleFile === undefined) {
      throw new Error(`routes.tsx: 자리 ${slot.id} 의 element <${slot.tag} /> 를 import 에서 찾지 못했다`);
    }
    if (rendersTwoColumn(moduleFile, target)) {
      withRail.push({ id: slot.id, path: slot.path });
    }
  }
  return withRail;
}
