// 시작하기 체크리스트 **항목 어휘와 그 선언 순서**를 계약에서 읽는 술어(M7/7E-2b, D-7E2b-7).
//
// 어휘의 **완전성**은 타입이 든다(`ONBOARDING_ITEM_STEP` 이 `satisfies Record<OnboardingItem, …>`
// 라 항목이 늘면 컴파일이 멈춘다). 타입이 못 드는 것은 **순서**다 — 유니온의 차례는 런타임에
// 남지 않으므로, 화면의 순서 상수가 계약의 선언 순서와 어긋나도 `tsc` 는 조용하다. 그 축을
// 여기서 맞댄다.
//
// 읽는 자리는 **생성 타입**이다(`src/api/generated/operator-api.ts`): W-5 가 그것과 OpenAPI 문서의
// 바이트 등식을 이미 들고 있으므로, 생성 타입은 계약의 선언 순서를 그대로 나르는 자리다.
//
// 술어는 **AST** 다 — 문자열 grep 은 주석·문서 문자열로 열린다(하네스 「게이트 술어는 문자열이
// 아니라 구조로 닫는다」).
import ts from 'typescript';

/** `as const` · `satisfies` 껍질을 벗긴다 — 표현식의 알맹이가 배열·객체 리터럴이다. */
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

function schemaTypeLiteral(source, schemaName) {
  let found = null;
  const visit = (node) => {
    if (found !== null) {
      return;
    }
    if (
      ts.isPropertySignature(node) &&
      ts.isIdentifier(node.name) &&
      node.name.text === schemaName &&
      node.type !== undefined &&
      ts.isTypeLiteralNode(node.type)
    ) {
      found = node.type;
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return found;
}

/** `(A | B | …)[]` 의 원소 유니온을 **선언 순서 그대로** 문자열 배열로. */
function arrayLiteralUnion(typeNode) {
  if (typeNode === undefined || !ts.isArrayTypeNode(typeNode)) {
    return null;
  }
  const element = ts.isParenthesizedTypeNode(typeNode.elementType) ? typeNode.elementType.type : typeNode.elementType;
  const members = ts.isUnionTypeNode(element) ? element.types : [element];
  const values = members.map((one) =>
    ts.isLiteralTypeNode(one) && ts.isStringLiteral(one.literal) ? one.literal.text : null,
  );
  return values.some((one) => one === null) ? null : values;
}

/**
 * 계약이 선언한 항목 어휘와 그 순서 — `OnboardingChecklist.completed` 의 원소 유니온이다.
 * 세 목록이 같은 어휘를 쓰므로(`pending`·`awaiting[].item`) 그 가운데 하나로 충분하고,
 * 셋이 갈리는 것은 계약 test 가 Kotlin enum 과의 등식으로 따로 든다.
 */
export function collectContractItemOrder(generatedText) {
  const source = ts.createSourceFile('operator-api.ts', generatedText, ts.ScriptTarget.ESNext, true);
  const schema = schemaTypeLiteral(source, 'OnboardingChecklist');
  if (schema === null) {
    return null;
  }
  const completed = schema.members.find(
    (member) => ts.isPropertySignature(member) && ts.isIdentifier(member.name) && member.name.text === 'completed',
  );
  return completed === undefined ? null : arrayLiteralUnion(completed.type);
}

/** 화면이 든 순서 상수(`ONBOARDING_ITEM_ORDER`) — 배열 리터럴의 문자열을 차례대로. */
export function collectDeclaredItemOrder(displayText) {
  const source = ts.createSourceFile('onboardingDisplay.ts', displayText, ts.ScriptTarget.ESNext, true);
  let order = null;
  const visit = (node) => {
    if (order !== null) {
      return;
    }
    if (
      ts.isVariableDeclaration(node) &&
      ts.isIdentifier(node.name) &&
      node.name.text === 'ONBOARDING_ITEM_ORDER'
    ) {
      const initializer = unwrap(node.initializer);
      if (initializer !== undefined && ts.isArrayLiteralExpression(initializer)) {
        const values = initializer.elements.map((one) => (ts.isStringLiteral(one) ? one.text : null));
        order = values.some((one) => one === null) ? null : values;
      }
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return order;
}

/** 이름난 객체 리터럴 상수의 **키 집합** — 표가 어휘를 전수로 드는지 보는 자리다. */
export function collectTableKeys(displayText, constantName) {
  const source = ts.createSourceFile('onboardingDisplay.ts', displayText, ts.ScriptTarget.ESNext, true);
  let keys = null;
  const visit = (node) => {
    if (keys !== null) {
      return;
    }
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.name.text === constantName) {
      const initializer = unwrap(node.initializer);
      if (initializer !== undefined && ts.isObjectLiteralExpression(initializer)) {
        keys = initializer.properties
          .filter((one) => ts.isPropertyAssignment(one))
          .map((one) => (ts.isIdentifier(one.name) || ts.isStringLiteral(one.name) ? one.name.text : null))
          .filter((one) => one !== null);
      }
      return;
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return keys;
}
