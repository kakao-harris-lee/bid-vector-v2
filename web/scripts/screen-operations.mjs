// 화면이 부르는 operation 집합을 **계약 목록과 등식으로** 잠그는 술어(v M-3).
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
 * 계약이 승인한 목록 — `reports/evidence/m7/7e1/scope.md` 「화면 B·C 가 쓰는 operation」 그대로다.
 * 여정 e2e 의 런타임 대조(`ALLOWED_API`)와 **같은 집합**이고, 두 자리가 갈리면 이 술어가 아니라
 * 리뷰가 잡는다(런타임 쪽은 메서드까지 본다).
 */
export const APPROVED_PATHS = Object.freeze([
  '/api/session',
  '/api/notices',
  '/api/suggestions',
  '/api/bid-cases',
  '/api/bid-cases/{caseId}',
  '/api/bid-cases/{caseId}/timeline',
  '/api/bid-cases/{caseId}/draft',
  '/api/bid-cases/{caseId}/record',
  '/api/bid-cases/{caseId}/skip',
  '/api/bid-cases/{caseId}/defer',
  '/api/bid-cases/{caseId}/resume',
  '/api/bid-price-requests',
]);

const CLIENT_OBJECT = 'apiClient';
const HTTP_METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE']);

/**
 * `apiClient.<METHOD>('<path>', …)` 의 **경로 리터럴 전수**를 모은다. 리터럴이 아닌 첫 인자는
 * 「모름」이므로 통과시키지 않고 `unresolved` 로 센다(fail-closed).
 */
export function collectCalledPaths(sourceText, fileName = 'queries.ts') {
  const source = ts.createSourceFile(fileName, sourceText, ts.ScriptTarget.ESNext, true);
  const paths = new Set();
  const unresolved = [];

  const visit = (node) => {
    if (
      ts.isCallExpression(node) &&
      ts.isPropertyAccessExpression(node.expression) &&
      ts.isIdentifier(node.expression.expression) &&
      node.expression.expression.text === CLIENT_OBJECT &&
      HTTP_METHODS.has(node.expression.name.text)
    ) {
      const first = node.arguments[0];
      if (first !== undefined && ts.isStringLiteralLike(first)) {
        paths.add(first.text);
      } else {
        unresolved.push(`${node.expression.name.text}(비리터럴 경로)`);
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return { paths: [...paths].sort(), unresolved };
}

/** 생성 타입의 `paths` 인터페이스 키 전수 — 계약이 실제로 가진 경로다. */
export function collectContractPaths(generatedText) {
  const source = ts.createSourceFile('operator-api.ts', generatedText, ts.ScriptTarget.ESNext, true);
  const keys = new Set();
  const visit = (node) => {
    if (ts.isInterfaceDeclaration(node) && node.name.text === 'paths') {
      for (const member of node.members) {
        if (ts.isPropertySignature(member) && member.name !== undefined && ts.isStringLiteralLike(member.name)) {
          keys.add(member.name.text);
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return [...keys].sort();
}

/** 목록 밖으로 나간 경로와 목록에만 있고 안 쓰는 경로를 가른다. */
export function compareSets(called, approved) {
  const approvedSet = new Set(approved);
  const calledSet = new Set(called);
  return {
    outsideApproved: called.filter((one) => !approvedSet.has(one)),
    approvedButUnused: approved.filter((one) => !calledSet.has(one)),
  };
}
