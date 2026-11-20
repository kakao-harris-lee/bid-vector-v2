// **레일을 그리는 자리 전수**를 자리 표에서 구성한다(M14/14B — v HIGH-1 · HIGH-1′).
//
// 왜 필요한가: 레일 축의 비공허가 **합계 하나**(`seen >= 1`)이면, 레일이 둘 이상 선 뒤에는 어느
// 하나가 측정에서 빠져도 나머지가 그 수를 떠받쳐 조용히 지나간다. 이 축이 세워진 계기가 바로
// 「시작하기의 레일이 통째로 사라졌다」인데, 합계 하한으로는 그 회귀를 다시 잡지 못한다.
//
// 그래서 보증을 **화면마다**로 바꾼다. 「어느 화면이 레일을 가져야 하는가」를 손으로 적으면 뒤
// 레인이 자기 줄을 안 적는 만큼 모집단이 뒤처지므로, **소스에서 유도한다**:
//
//   자리 표(`screenSlots` + 표 밖 둘)의 element → 그 태그가 가리키는 **화면 모듈**
//     → 그 모듈이 `TwoColumn` 을 **JSX 태그로 그리는가**
//
// 그리면 그 자리는 **레일을 그리는 화면**이고, 레일 축은 그 주소에서 레일을 **하나 이상** 봐야
// 한다. 14C 의 워크시트·14D 의 입찰함이 레일을 붙이면 이 집합에 자동으로 들어온다.
//
// **「닿는가」가 아니라 「그리는가」다**(실측). 처음에는 import 폐포로 물었는데 그 술어는 **거짓
// 양성**을 냈다: 오늘의 공고 화면이 시작하기 모듈에서 배너를 들여오므로 폐포가 `TwoColumn` 에
// 닿고, 레일을 하나도 그리지 않는 그 화면에 레일 의무가 걸렸다(여정 레인이 `home` 을 붉혔다).
// 속성을 재귀로 물으면 술어가 묽어진다.
//
// **신원은 심볼이다**(HIGH-1′). 이름 글자로 맞대던 판은 두 꼴을 놓쳤다 — `import * as L` 뒤의
// `<L.TwoColumn/>` 과 `import { TwoColumn as TC }` 뒤의 `<TC/>`. 둘 다 그 화면을 의무에서 **조용히**
// 빼냈다. 이제 타입 검사기가 푼 심볼을 그 모듈의 수출 심볼과 **같은 것인지** 비교하므로 이름을
// 어떻게 바꿔 부르든 같은 답이 나온다. 화면 모듈을 찾는 쪽도 같은 검사기를 쓰므로 배럴 index 를
// 지나는 import 도 따라간다.
import { resolve } from 'node:path';

import ts from 'typescript';

import { screenProgram } from './screen-card-coverage.mjs';

/**
 * **집합이 줄어드는 쪽을 막는 래칫.** 구성은 **늘어나는 쪽**을 맡고(새 레일 화면은 아무 줄도 안
 * 적고 모집단에 든다), 이 목록은 줄어드는 쪽을 막는다.
 *
 * 짝 test 가 **집합 등식**으로 든다(포함이 아니다). 포함으로 두면 한쪽 방향이 조용해진다 —
 * 실제로 그랬다: `arrayContaining` 이던 판에서 14D 의 입찰함 두 단을 표지 없이 손으로 짜자 그
 * 자리가 유도 집합에서 빠지고 **세 게이트가 모두 초록**이었다. 등식이면 그 순간 붉는다.
 *
 * **머지 차례의 한 줄**: 14C·14D 가 레일을 붙이면 유도 집합이 늘어 등식이 깨진다 — 먼저 머지하는
 * 쪽이 자기 자리 이름을 여기 더한다. 이름순으로 둔다(사람이 읽는 목록이고 차례가 뜻을 갖지 않는다).
 */
export const RAIL_SCREEN_FLOOR = Object.freeze(['onboarding', 'worksheet']);

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

/** `element: <Foo />` 의 **태그 노드**(글자가 아니다 — 심볼을 풀어야 하므로 자리가 필요하다). */
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
    if (opening !== undefined) {
      return opening.tagName;
    }
  }
  return undefined;
}

const aliasResolved = (checker, symbol) =>
  symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0 ? checker.getAliasedSymbol(symbol) : symbol;

/** 그 자리에 선 이름이 가리키는 **심볼** — 별칭·네임스페이스를 끝까지 푼다. */
const symbolAt = (checker, node) => aliasResolved(checker, checker.getSymbolAtLocation(node));

/**
 * 레일 원시 요소의 **수출 심볼**. 못 찾으면 던진다 — 비교 대상이 없으면 아래 모든 판정이
 * 조용히 거짓이 된다.
 */
function railSymbolOf(program, checker, webRoot) {
  const file = resolve(webRoot, 'src', 'components', 'TwoColumn.tsx');
  const source = program.getSourceFile(file);
  if (source === undefined) {
    throw new Error(`레일 원시 요소를 프로그램에서 찾지 못했다: ${file}`);
  }
  const moduleSymbol = checker.getSymbolAtLocation(source);
  const exported = moduleSymbol === undefined ? [] : checker.getExportsOfModule(moduleSymbol);
  const found = exported.find((one) => one.name === 'TwoColumn');
  if (found === undefined) {
    throw new Error('TwoColumn 수출을 찾지 못했다 — 레일 신원의 기준이 사라졌다');
  }
  return aliasResolved(checker, found);
}

/**
 * [source] 가 [railSymbol] 을 **JSX 태그로 그리는가**.
 *
 * 들여오기만 하고 안 그리는 모듈은 거짓이다 — 레일 의무의 근거는 「그 화면에 레일이 선다」이지
 * 「그 모듈이 그 심볼을 안다」가 아니다. 비교가 **심볼 동일성**이라 `<TwoColumn/>` · `<TC/>` ·
 * `<L.TwoColumn/>` 이 모두 같은 답을 낸다.
 */
function drawsRail(source, checker, railSymbol) {
  let drawn = false;
  const visit = (node) => {
    if (drawn) {
      return;
    }
    const opening = ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node) ? node : undefined;
    if (opening !== undefined && symbolAt(checker, opening.tagName) === railSymbol) {
      drawn = true;
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return drawn;
}

/** 자리 전수 — 자리 표 + 표 밖 둘(로그인 · 없는 주소). */
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
 * **레일을 그리는 자리 전수**. 각 원소는 `{ id, path }` 이고, 그 자리의 화면 모듈이
 * `components/TwoColumn` 을 JSX 로 그린다.
 *
 * element 를 선언하지 않은 자리(「준비 중」 빈 자리)는 그릴 화면이 없으므로 집합 밖이다.
 * 들여오기만 하고 **안 그리는** 모듈도 집합 밖이다(거짓 양성을 막는 자리다).
 *
 * element 는 있는데 그 태그의 **심볼을 풀지 못하면 던진다** — 조용히 빠지면 그 화면이 레일을
 * 붙여도 모집단에 안 든다.
 *
 * **알려진 제한**: 화면이 레일을 **자기 모듈 밖**(별 파일의 하위 컴포넌트)에서 그리면 이 술어가
 * 못 본다. 그 자리는 [RAIL_SCREEN_FLOOR] 의 등식이 받는다 — 유도 집합에서 빠지는 순간 등식이
 * 깨진다.
 */
export function railScreenSlots(webRoot) {
  const program = screenProgram(webRoot);
  const checker = program.getTypeChecker();
  const routesFile = resolve(webRoot, 'src', 'routes.tsx');
  const routes = program.getSourceFile(routesFile);
  if (routes === undefined) {
    throw new Error(`routes.tsx 를 프로그램에서 찾지 못했다: ${routesFile}`);
  }
  const railSymbol = railSymbolOf(program, checker, webRoot);

  const withRail = [];
  for (const slot of allSlots(routes)) {
    if (slot.tag === undefined) {
      continue;
    }
    /**
     * 선언이 **여럿일 수 있다**(cr LOW): 같은 이름이 여러 파일에 선언되는 꼴(선언 병합·오버로드)
     * 에서 첫 선언만 보면 레일을 그리는 쪽을 놓친다. 전수를 돌고, **하나라도** 그리면 의무다.
     */
    const declarations = symbolAt(checker, slot.tag)?.declarations ?? [];
    if (declarations.length === 0) {
      throw new Error(`routes.tsx: 자리 ${slot.id} 의 element 태그가 가리키는 화면 모듈을 찾지 못했다`);
    }
    const screens = new Set(declarations.map((one) => one.getSourceFile()));
    if ([...screens].some((one) => drawsRail(one, checker, railSymbol))) {
      withRail.push({ id: slot.id, path: slot.path });
    }
  }
  return withRail;
}
