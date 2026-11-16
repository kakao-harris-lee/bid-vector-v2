// M13/13B — 터치 타깃 게이트가 **도는 화면**과 앱의 **자리 표**를 등식으로 묶는 술어.
//
// 게이트의 힘은 모집단에 달려 있다. 요소 축은 선택자 전수가 들지만 **화면 축**은 spec 안의 표라,
// 새 화면이 자리 표에 들어와도 그 표에 적지 않으면 게이트가 그 화면에 대해 아무 말도 하지 않는다
// (저장소 선례 — M9 에서 화면 E 카드가 마운트 목록 밖이라 그 공백에 위반이 살아 있었다).
//
// **AST 로 읽는다**(문자열 훑기가 아니다). 그리고 **import 하지 않는다**: 자리 표는 JSX 라
// 들여오면 앱 트리 전체가 딸려 오고, spec 을 들여오면 vitest 가 Playwright test 를 수집한다.
//
// `web/src` **밖**에 사는 이유는 컴파일 경계다 — `src` 안의 test 가 `e2e/**` 를 들여오면 W-4 의
// 「프로그램 전수가 `web/src` ∪ `web/node_modules` 안」 단언이 붉는다.
import ts from 'typescript';

const parse = (text, fileName) =>
  ts.createSourceFile(fileName, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);

/** `as const`·`satisfies` 를 벗겨 알맹이 식을 낸다 — 표가 어떤 꼴로 적혀도 같은 자리를 읽는다. */
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

/** 객체 리터럴의 문자열 속성 하나 — 리터럴이 아니면 `undefined`(그 자리는 호출자가 붉힌다). */
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

/**
 * 이름이 가리키는 **배열 리터럴**의 원소들에서 고른 키를 읽는다. 배열이 아니거나 비어 있으면
 * 던진다 — 빈 목록은 등식을 공허하게 만든다.
 */
function arrayEntries(source, name, keys, where) {
  const initializer = declarationInitializer(source, name);
  if (initializer === undefined || !ts.isArrayLiteralExpression(initializer)) {
    throw new Error(`${where}: ${name} 의 배열 리터럴을 찾지 못했다`);
  }
  if (initializer.elements.length === 0) {
    throw new Error(`${where}: ${name} 이 비었다`);
  }
  return initializer.elements.map((element) => {
    if (!ts.isObjectLiteralExpression(element)) {
      throw new Error(`${where}: ${name} 의 원소가 객체 리터럴이 아니다`);
    }
    const entry = {};
    for (const key of keys) {
      const value = stringProperty(element, key);
      if (value === undefined) {
        // **조용한 `undefined` 를 내지 않는다.** 값이 템플릿 리터럴·식이면 이 술어가 읽을 수
        // 없고, 그때 등식은 「없는 값끼리 같다」로 초록이 된다 — 그 자리를 던짐으로 바꾼다.
        throw new Error(`${where}: ${name} 의 원소에 문자열 리터럴 ${key} 가 없다`);
      }
      entry[key] = value;
    }
    return entry;
  });
}

/**
 * 앱이 그리는 **자리 전수** — 화면 자리 표 + 표 밖의 두 자리(로그인 · 없는 주소).
 *
 * 표 밖의 둘을 **열거하지 않고** 읽는다: 그 둘도 `id` 를 든 라우트 선언이라 이름으로 찾아
 * 같은 자리에서 꺼낸다. 셋 가운데 하나라도 사라지면 여기서 던진다.
 */
export function appScreenSlots(routesText) {
  const source = parse(routesText, 'routes.tsx');
  const slots = arrayEntries(source, 'screenSlots', ['id', 'path'], 'routes.tsx');
  const standalone = ['loginRoute', 'notFoundSlot'].map((name) => {
    const initializer = declarationInitializer(source, name);
    if (initializer === undefined || !ts.isObjectLiteralExpression(initializer)) {
      throw new Error(`routes.tsx: ${name} 의 객체 리터럴을 찾지 못했다`);
    }
    const entry = { id: stringProperty(initializer, 'id'), path: stringProperty(initializer, 'path') };
    if (entry.id === undefined || entry.path === undefined) {
      // 라우트가 자리 값을 **식으로** 참조하면 이 술어가 읽지 못한다 — 조용한 `undefined` 대신
      // 던진다(그 값으로 등식을 세우면 「없는 값끼리 같다」가 된다).
      throw new Error(`routes.tsx: ${name} 에 문자열 리터럴 id·path 가 없다`);
    }
    return entry;
  });
  return [...slots, ...standalone];
}

/** 터치 타깃 게이트가 도는 화면 표. */
export function gateScreens(specText) {
  return arrayEntries(parse(specText, 'touch-target.spec.ts'), 'TOUCH_TARGET_SCREENS', ['slotId', 'path'], 'spec');
}

/**
 * 자리 경로가 그 spec 경로를 **받는가**. 경로 인자(`:noticeNumber`)는 한 칸을 받고, catch-all
 * (`*`)은 어느 자리에도 닿지 않는 주소를 받는다. 질의 문자열은 자리를 바꾸지 않으므로 뗀다.
 */
export function pathMatches(slotPath, specPath, otherSlotPaths = []) {
  const pathname = specPath.split('?')[0];
  if (slotPath === '*') {
    /**
     * catch-all 은 「**어느 자리도 받지 않는 주소**」다(cr O-1). 앞 판은 `/` 로 시작하기만 하면
     * 참이라 그 행이 `/bids` 를 가리켜도 초록이었다 — 한 축이 통째로 공허했다. 다른 자리의
     * 경로와 **하나도 맞지 않는 것**을 본다.
     */
    return pathname.startsWith('/') && !otherSlotPaths.some((one) => pathMatches(one, pathname));
  }
  const pattern = slotPath
    .split('/')
    .map((one) => (one.startsWith(':') ? '[^/]+' : one.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&')))
    .join('/');
  return new RegExp(`^${pattern}$`, 'u').test(pathname);
}
