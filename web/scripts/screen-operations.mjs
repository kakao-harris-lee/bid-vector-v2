// 화면이 부르는 operation **(메서드, 경로) 쌍** 집합을 계약 목록과 등식으로 잠그는 술어(v M-3 · r3 M-1).
//
// W-4 가 「서버 호출 능력을 쥐는 자리 == `src/api/queries.ts`」를 세우므로, **그 파일이 부르는
// operation 집합**이 곧 화면이 부를 수 있는 전부다. 그 집합을 계약의 목록(scope.md 「화면 B·C 가
// 쓰는 operation」)과 등식으로 둔다 — 목록 밖 경로를 부르면 등식이 깨지고, 목록에 적힌 경로가
// 계약(생성 타입의 `paths` 키)에 없으면 오타가 그 자리에서 드러난다.
//
// 술어는 **AST** 다. 문자열 grep 은 주석·문서 문자열로 열린다(하네스 「게이트 술어는 문자열이
// 아니라 구조로 닫는다」).
import ts from 'typescript';

/**
 * 승인 목록은 **`src/api/approvedOperations.ts` 한 자리**에서 읽는다(CR r2 M-1). 앞 판은 이
 * 파일이 같은 목록을 **복제**했고, 그러면 클라이언트 타입을 좁히는 자리와 게이트가 갈릴 수 있다.
 *
 * 원소는 **(메서드, 경로) 쌍**이다(v r3 M-1) — 경로만 보면 읽기 경로에 쓰기를 보내는 꼴이
 * 다섯 층을 모두 지난다(실측: `DELETE /api/session`).
 */
export function collectApprovedOperations(approvedSourceText) {
  const source = ts.createSourceFile('approvedOperations.ts', approvedSourceText, ts.ScriptTarget.ESNext, true);
  const operations = [];
  const visit = (node) => {
    if (
      ts.isVariableDeclaration(node) &&
      ts.isIdentifier(node.name) &&
      node.name.text === 'APPROVED_OPERATIONS'
    ) {
      const initializer =
        node.initializer !== undefined && ts.isAsExpression(node.initializer)
          ? node.initializer.expression
          : node.initializer;
      if (initializer !== undefined && ts.isArrayLiteralExpression(initializer)) {
        for (const element of initializer.elements) {
          if (!ts.isObjectLiteralExpression(element)) {
            continue;
          }
          const field = (key) => {
            for (const property of element.properties) {
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
          };
          const method = field('method');
          const path = field('path');
          if (method !== undefined && path !== undefined) {
            operations.push(`${method} ${path}`);
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return operations.sort();
}

const CLIENT_OBJECT = 'apiClient';
const HTTP_METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE']);

/**
 * `apiClient` **참조 전수**를 보고 경로 리터럴을 모은다.
 *
 * **앞 판은 호출 꼴을 열거했다**(`apiClient.<METHOD>(…)` 하나). 그래서 `apiClient['GET'](…)` ·
 * `const { GET } = apiClient` · `const c = apiClient; c.GET(…)` 가 `paths` 에도 `unresolved` 에도
 * 걸리지 않고 **아무 흔적 없이** 지나갔다(실측 셋). 열거를 늘리는 대신 뒤집는다 — 그 식별자가
 * 나타나는 **모든 자리**를 세고, 아는 꼴(속성 접근 + HTTP 메서드 + 경로 리터럴)이 아니면
 * `unresolved` 로 밀어 넣는다. **모르는 것은 통과시키지 않는다.**
 *
 * 이 술어가 둘째 층인 이유: 첫째 층은 **타입**이다(`client.ts` 가 승인 목록으로 좁혀져 있어
 * 목록 밖 경로는 어느 꼴로 불러도 `tsc` 가 멈춘다). 이 층은 그 전제가 조용히 넓어지는 것을
 * 잡는다 — 목록이 늘었는데 화면이 실제로 그만큼 쓰지 않는 경우까지 등식으로 센다.
 */
export function collectCalledOperations(sourceText, fileName = 'queries.ts') {
  const source = ts.createSourceFile(fileName, sourceText, ts.ScriptTarget.ESNext, true);
  const operations = new Set();
  const unresolved = [];

  /** 이 참조가 **아는 호출 꼴**의 일부인가 — `apiClient.GET('<리터럴>')` 의 `apiClient` 자리. */
  const knownCallShape = (reference) => {
    const access = reference.parent;
    if (access === undefined || !ts.isPropertyAccessExpression(access) || access.expression !== reference) {
      return undefined;
    }
    if (!HTTP_METHODS.has(access.name.text)) {
      return undefined;
    }
    const call = access.parent;
    if (call === undefined || !ts.isCallExpression(call) || call.expression !== access) {
      return undefined;
    }
    return { call, method: access.name.text };
  };

  const visit = (node) => {
    if (ts.isIdentifier(node) && node.text === CLIENT_OBJECT) {
      // 선언·import 의 **이름 자리**만 참조가 아니다(그 자리는 W-4 의 간선 술어가 본다).
      // 초기값 자리(`const c = apiClient`)는 **참조다** — 앞 판은 부모 종류만 보고 그것까지
      // 제외해 별칭·구조 분해가 조용히 지나갔다(이 test 가 그 자리를 잡았다).
      const parent = node.parent;
      const isBindingName =
        parent !== undefined &&
        ((ts.isImportSpecifier(parent) && parent.name === node) ||
          (ts.isExportSpecifier(parent) && (parent.name === node || parent.propertyName === node)) ||
          (ts.isVariableDeclaration(parent) && parent.name === node) ||
          (ts.isBindingElement(parent) && (parent.name === node || parent.propertyName === node)) ||
          (ts.isPropertyAssignment(parent) && parent.name === node) ||
          (ts.isPropertySignature(parent) && parent.name === node));
      if (!isBindingName) {
        const shape = knownCallShape(node);
        if (shape === undefined) {
          unresolved.push(`${CLIENT_OBJECT} 의 모르는 사용 꼴(${ts.SyntaxKind[node.parent?.kind ?? 0]})`);
        } else {
          const first = shape.call.arguments[0];
          if (first !== undefined && ts.isStringLiteralLike(first) && !ts.isTemplateExpression(first)) {
            operations.add(`${shape.method} ${first.text}`);
          } else {
            unresolved.push(`${CLIENT_OBJECT} 호출의 경로가 리터럴이 아니다`);
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return { operations: [...operations].sort(), unresolved };
}

/**
 * 생성 타입이 실제로 가진 **(메서드, 경로) 쌍** 전수. `paths` 의 각 키 아래에서 메서드 자리가
 * `never` 가 아닌 것만 센다 — 생성기는 없는 메서드도 키로 적고 타입을 `never` 로 둔다.
 */
export function collectContractOperations(generatedText) {
  const source = ts.createSourceFile('operator-api.ts', generatedText, ts.ScriptTarget.ESNext, true);
  const operations = new Set();
  const visit = (node) => {
    if (ts.isInterfaceDeclaration(node) && node.name.text === 'paths') {
      for (const member of node.members) {
        if (
          !ts.isPropertySignature(member) ||
          member.name === undefined ||
          !ts.isStringLiteralLike(member.name) ||
          member.type === undefined ||
          !ts.isTypeLiteralNode(member.type)
        ) {
          continue;
        }
        for (const methodMember of member.type.members) {
          if (
            ts.isPropertySignature(methodMember) &&
            methodMember.name !== undefined &&
            ts.isIdentifier(methodMember.name) &&
            HTTP_METHODS.has(methodMember.name.text.toUpperCase()) &&
            methodMember.type !== undefined &&
            methodMember.type.kind !== ts.SyntaxKind.NeverKeyword
          ) {
            operations.add(`${methodMember.name.text.toUpperCase()} ${member.name.text}`);
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return [...operations].sort();
}

/** 목록 밖으로 나간 쌍과 목록에만 있고 안 쓰는 쌍을 가른다. */
export function compareSets(called, approved) {
  const approvedSet = new Set(approved);
  const calledSet = new Set(called);
  return {
    outsideApproved: called.filter((one) => !approvedSet.has(one)),
    approvedButUnused: approved.filter((one) => !calledSet.has(one)),
  };
}
