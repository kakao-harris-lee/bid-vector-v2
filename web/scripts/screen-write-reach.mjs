// 「읽기 화면은 쓰기를 부르지 않는다」(M14/14D · 계약 「게이트」 절).
//
// **막는 것**: 읽기 화면이 자기가 그릴 값을 얻으려고 쓰기 operation 을 부르는 것. 계약이 든
// 실례는 결과 확인 화면이 하한율을 얻으려 투찰가 요청(POST)을 부르는 것이고, 그 호출은
// **알림함에 기록을 낳는다** — 운영자가 보기만 했는데 기록이 생긴다.
//
// **오늘의 승인 쌍 등식이 이것을 못 잡는다**(계획 리뷰 r2 실측): 그 등식은 `queries.ts` 를 **한
// 집합**으로 재서 「어느 화면이 무엇에 닿는가」를 나누지 않는다. 그래서 이 층이 **화면 모듈 →
// 질의 import 그래프**를 세운다.
//
// 네 축이 전부 **구성**이다(열거가 아니다):
//  · 화면 모집단 — `routes.tsx` 의 자리 표에서 유도한다(`journey-screen-table.mjs` 와 같은 출처).
//  · 어느 화면이 읽기 전용인가 — **이것만 선언**이다(사람의 판단). 대신 **집합 등식**을 건다:
//    선언 표의 키 == 유도한 자리 id. 새 화면은 선언하지 않으면 붉는다.
//  · 무엇이 쓰기인가 — **메서드에서 유도**한다. 이름 술어(「그 함수 금지」)면 개명 한 번에 열린다.
//  · 도달 — 폐포 **둘**이 겹친다. 모듈 사이(간선이 **내보낸 이름**을 나른다)와 `queries.ts`
//    **안**(얇은 포장이 쓰기를 감추지 못하게).
//
// **모르는 것은 통과시키지 않는다.** 해소되지 않는 지정자·비리터럴 경로·네임스페이스 수입은
// 전부 위반이다(fail-closed).
import { readFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';

import ts from 'typescript';

import { appScreenSlots } from './journey-screen-table.mjs';

/**
 * 화면의 **쓰기 정책** — 이 slice 가 선언하는 유일한 표다.
 *
 * 「읽기 화면인가」는 코드에서 유도되지 않는 **제품 판단**이다. 그래서 선언으로 두되, 아래
 * `policyGaps` 가 이 표의 키와 자리 표를 **집합 등식**으로 묶는다 — 새 화면이 조용히 의무 밖에
 * 서지 못한다.
 *
 * `READ_ONLY` 로 바꾸는 것은 **더 센 주장**이라 언제나 안전하다. 반대로 `WRITES` 로 돌리는 것은
 * 주장을 버리는 것이고, 짝 test 가 **결과 자리가 `READ_ONLY` 임을 못박아** 그 변경이 파일 둘을
 * 건드리게 한다(계약이 요구한 바로 그 주장이다).
 */
export const SCREEN_WRITE_POLICY = {
  onboarding: 'WRITES',
  home: 'WRITES',
  worksheet: 'WRITES',
  'bid-box': 'WRITES',
  // 계약 「게이트」 절이 이름으로 든 자리 — E 는 읽기 화면이고 전이는 D 가 가진다(D-7E3a-8).
  results: 'READ_ONLY',
  inbox: 'WRITES',
  settings: 'WRITES',
  // 자리 표는 로그인과 없는 주소도 든다(`appScreenSlots`). 로그인은 세션을 **만드는** 화면이고,
  // 없는 주소는 `element` 가 없어 의무가 서지 않는다 — 그래도 선언은 든다(등식이 전수다).
  login: 'WRITES',
  'not-found': 'READ_ONLY',
};

/** 부수효과 없는 메서드 — 그 **밖은 전부 쓰기**다(목록을 늘리지 않는다). */
const SAFE_METHODS = new Set(['GET']);

/** 질의가 사는 자리와, 그 질의가 쓰는 클라이언트의 자리. 둘 다 `src` 기준 상대 경로다. */
export const QUERIES_MODULE = 'api/queries.ts';
export const CLIENT_MODULE = 'api/client.ts';
/** 클라이언트 객체의 **내보낸 이름** — 이것에 닿는 것 자체가 질의층을 건너뛰는 꼴이다. */
export const CLIENT_EXPORT = 'apiClient';

const HTTP_METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE']);

const parse = (text, path) =>
  ts.createSourceFile(path, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);

/** 모든 import·export 지정자가 붙는 확장자 후보. 디렉터리 색인도 받는다. */
const SUFFIXES = ['', '.ts', '.tsx', '/index.ts', '/index.tsx'];

/**
 * 상대 지정자를 실제 파일로. **찾지 못하면 `undefined`** 이고 부르는 자리가 그것을 위반으로
 * 센다(조용히 건너뛰지 않는다).
 */
function resolveSpecifier(fromFile, specifier, readText) {
  if (!specifier.startsWith('.')) {
    return undefined;
  }
  const base = resolve(dirname(fromFile), specifier);
  for (const suffix of SUFFIXES) {
    const candidate = base + suffix;
    if (readText(candidate) !== undefined) {
      return candidate;
    }
  }
  return undefined;
}

/**
 * 한 파일이 내는 간선 전수 — `import`·`export … from`·동적 `import()`.
 *
 * 간선이 나르는 것은 **내보낸 이름**(`propertyName`)이다. 지역 이름으로 세면 별칭
 * (`import { requestBidPrice as r }`) 한 줄로 열린다.
 *
 * `names` 가 `'*'` 면 **그 모듈의 내보내기 전부에 닿는다**는 뜻이다 — 네임스페이스 수입,
 * `export * from`, 기본 수입, 동적 `import()` 가 그 꼴이다(fail-closed).
 */
export function moduleEdgesOf(text, path) {
  const source = parse(text, path);
  const edges = [];
  const visit = (node) => {
    if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) {
      const specifier = node.moduleSpecifier;
      if (specifier !== undefined && ts.isStringLiteralLike(specifier)) {
        const clause = ts.isImportDeclaration(node) ? node.importClause : node.exportClause;
        let names = '*';
        if (clause !== undefined) {
          if (ts.isImportClause(clause)) {
            const bindings = clause.namedBindings;
            if (bindings !== undefined && ts.isNamedImports(bindings) && clause.name === undefined) {
              names = bindings.elements.map((one) => (one.propertyName ?? one.name).text);
            }
          } else if (ts.isNamedExports(clause)) {
            names = clause.elements.map((one) => (one.propertyName ?? one.name).text);
          }
        }
        edges.push({ specifier: specifier.text, names, dynamic: false });
      }
    }
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword) {
      const first = node.arguments[0];
      edges.push({
        specifier: first !== undefined && ts.isStringLiteralLike(first) ? first.text : null,
        names: '*',
        dynamic: true,
      });
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return edges;
}

/**
 * 한 파일 안에서 `apiClient` 가 내는 **(메서드, 경로)** 전수.
 *
 * 아는 꼴(`apiClient.<METHOD>('<리터럴>')`)이 아니면 `unresolved` 에 담는다 — 모르는 것은
 * 통과가 아니다(`screen-operations` 의 선례와 같은 처방).
 */
export function clientCallsIn(text, path) {
  const source = parse(text, path);
  const operations = [];
  const unresolved = [];
  const visit = (node) => {
    if (ts.isIdentifier(node) && node.text === CLIENT_EXPORT) {
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
        const access = parent;
        if (
          access !== undefined &&
          ts.isPropertyAccessExpression(access) &&
          access.expression === node &&
          HTTP_METHODS.has(access.name.text) &&
          access.parent !== undefined &&
          ts.isCallExpression(access.parent) &&
          access.parent.expression === access
        ) {
          const first = access.parent.arguments[0];
          if (first !== undefined && ts.isStringLiteralLike(first) && !ts.isTemplateExpression(first)) {
            operations.push(`${access.name.text} ${first.text}`);
          } else {
            unresolved.push(`${path}: ${CLIENT_EXPORT} 호출의 경로가 리터럴이 아니다`);
          }
        } else {
          unresolved.push(`${path}: ${CLIENT_EXPORT} 의 모르는 사용 꼴`);
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return { operations, unresolved };
}

/** 최상위 선언의 이름 — 함수·변수 둘 다. 이름을 못 얻으면 `undefined`. */
function topLevelNameOf(statement) {
  if (ts.isFunctionDeclaration(statement) && statement.name !== undefined) {
    return statement.name.text;
  }
  if (ts.isVariableStatement(statement)) {
    const first = statement.declarationList.declarations[0];
    if (first !== undefined && ts.isIdentifier(first.name)) {
      return first.name.text;
    }
  }
  return undefined;
}

/**
 * `queries.ts` 의 **내보내기 → operation 집합**.
 *
 * 선언 **사이 참조**를 따라간다(얇은 포장이 쓰기를 감추지 못하게): 읽기 이름이 속으로 쓰기
 * 함수를 부르면 그 operation 이 읽기 이름의 집합에 든다.
 */
export function operationsByExport(text, path = QUERIES_MODULE) {
  const source = parse(text, path);
  const own = new Map();
  const refs = new Map();
  const unresolved = [];

  for (const statement of source.statements) {
    const name = topLevelNameOf(statement);
    if (name === undefined) {
      continue;
    }
    const { operations, unresolved: badShapes } = clientCallsIn(statement.getText(source), path);
    own.set(name, new Set(operations));
    unresolved.push(...badShapes);
    const referenced = new Set();
    const visit = (node) => {
      if (ts.isIdentifier(node) && node.text !== name) {
        referenced.add(node.text);
      }
      ts.forEachChild(node, visit);
    };
    visit(statement);
    refs.set(name, referenced);
  }

  // 선언 사이 **고정점** — 포장이 몇 겹이든 속의 operation 이 바깥 이름에 실린다.
  const closure = new Map([...own].map(([name, operations]) => [name, new Set(operations)]));
  for (let round = 0; round < closure.size + 1; round += 1) {
    let grew = false;
    for (const [name, operations] of closure) {
      for (const referenced of refs.get(name) ?? []) {
        for (const one of closure.get(referenced) ?? []) {
          if (!operations.has(one)) {
            operations.add(one);
            grew = true;
          }
        }
      }
    }
    if (!grew) {
      break;
    }
  }
  return { operations: closure, unresolved };
}

/** `GET` 이 아닌 것이 쓰기다 — 목록이 아니라 메서드에서 유도한다. */
export function writeOperationsOf(operations) {
  return [...operations].filter((one) => !SAFE_METHODS.has(one.split(' ')[0])).sort();
}

/**
 * `routes.tsx` 에서 자리 → **화면 모듈 경로**. 자리 id 는 `appScreenSlots` 가 내고(출처가 하나),
 * 모듈은 그 자리의 `element` JSX 태그 이름을 그 파일의 import 선언으로 되짚어 얻는다.
 *
 * **해소되지 않으면 던진다**(팀장 조건 ②) — 모집단이 조용히 줄면 등식이 공허해진다. `element`
 * 가 아예 없는 자리(준비 중 빈 자리)는 `null` 이고, 그 **수**를 짝 test 가 못박는다.
 */
export function screenModulesOf(routesText, routesPath, readText) {
  const slots = appScreenSlots(routesText);
  const source = parse(routesText, routesPath);

  const importedFrom = new Map();
  for (const statement of source.statements) {
    if (!ts.isImportDeclaration(statement) || !ts.isStringLiteralLike(statement.moduleSpecifier)) {
      continue;
    }
    const clause = statement.importClause;
    if (clause === undefined) {
      continue;
    }
    const specifier = statement.moduleSpecifier.text;
    if (clause.name !== undefined) {
      importedFrom.set(clause.name.text, specifier);
    }
    const bindings = clause.namedBindings;
    if (bindings !== undefined && ts.isNamedImports(bindings)) {
      for (const element of bindings.elements) {
        importedFrom.set(element.name.text, specifier);
      }
    }
  }

  /** 자리 하나의 `element` 가 그리는 컴포넌트 이름. 없으면 `undefined`. */
  const elementTagOf = (slotId) => {
    let tag;
    const visit = (node) => {
      if (ts.isObjectLiteralExpression(node)) {
        const idOf = node.properties.find(
          (one) =>
            ts.isPropertyAssignment(one) &&
            one.name !== undefined &&
            ts.isIdentifier(one.name) &&
            one.name.text === 'id' &&
            ts.isStringLiteralLike(one.initializer) &&
            one.initializer.text === slotId,
        );
        const element = node.properties.find(
          (one) =>
            ts.isPropertyAssignment(one) &&
            one.name !== undefined &&
            ts.isIdentifier(one.name) &&
            one.name.text === 'element',
        );
        if (idOf !== undefined && element !== undefined && ts.isPropertyAssignment(element)) {
          const opening = ts.isJsxSelfClosingElement(element.initializer)
            ? element.initializer
            : ts.isJsxElement(element.initializer)
              ? element.initializer.openingElement
              : undefined;
          if (opening !== undefined && ts.isIdentifier(opening.tagName)) {
            tag = opening.tagName.text;
          }
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
    return tag;
  };

  const modules = new Map();
  for (const slot of slots) {
    const tag = elementTagOf(slot.id);
    if (tag === undefined) {
      // `element` 가 없는 자리 — 「준비 중」 빈 자리다. 의무가 없고, 그 **수**를 짝 test 가 센다.
      modules.set(slot.id, null);
      continue;
    }
    const specifier = importedFrom.get(tag);
    if (specifier === undefined) {
      throw new Error(`routes.tsx: ${slot.id} 의 element <${tag}> 를 수입한 자리를 찾지 못했다`);
    }
    const resolved = resolveSpecifier(routesPath, specifier, readText);
    if (resolved === undefined) {
      throw new Error(`routes.tsx: ${slot.id} 의 ${specifier} 가 어느 파일인지 해소되지 않는다`);
    }
    modules.set(slot.id, resolved);
  }
  return modules;
}

/**
 * 한 화면 모듈이 닿는 **operation 집합**과 **해소 불가 목록**.
 *
 * 모듈 폐포를 돌며 `queries.ts` 의 어느 내보내기에 닿는지 모으고, 그 이름들의 operation 을
 * 합친다. `apiClient` 에 **직접** 닿는 모듈이 있으면 그 파일의 호출도 함께 센다(팀장 조건 ①) —
 * 질의층을 건너뛰는 길이 그 자리다.
 */
export function reachFrom(entry, { readText, srcRoot, queriesPath, clientPath }) {
  const seen = new Set();
  const queue = [entry];
  const queryExports = new Set();
  const unresolved = [];
  const operations = new Set();
  let wholeQueries = false;

  while (queue.length > 0) {
    const current = queue.pop();
    if (seen.has(current)) {
      continue;
    }
    seen.add(current);
    const text = readText(current);
    if (text === undefined) {
      unresolved.push(`${current}: 파일을 읽지 못했다`);
      continue;
    }
    if (current === queriesPath) {
      // 질의 모듈 **안쪽**은 아래에서 내보내기 단위로 센다(모듈 통째로 삼키지 않는다).
      continue;
    }

    // `apiClient` 를 **직접** 수입해 쓰는 모듈 — 질의층 밖의 호출이다(팀장 조건 ①).
    // 판정은 **부르는 자리**에서 한다. 클라이언트 모듈 자신은 정의 자리라 걷지 않는다 — 거기
    // `apiClient.use(…)` 같은 자기 배선이 있고, 그것은 화면의 호출이 아니다.
    const calls = clientCallsIn(text, current);
    for (const one of calls.operations) {
      operations.add(one);
    }
    unresolved.push(...calls.unresolved);

    for (const edge of moduleEdgesOf(text, current)) {
      if (edge.specifier === null) {
        unresolved.push(`${current}: 동적 import 의 지정자가 리터럴이 아니다`);
        continue;
      }
      const target = resolveSpecifier(current, edge.specifier, readText);
      if (target === undefined) {
        // 패키지 수입은 이 층의 관심 밖이다(번들 경계 게이트가 든다). 상대 지정자인데
        // 해소되지 않는 것만 **모르는 것**으로 센다.
        if (edge.specifier.startsWith('.')) {
          unresolved.push(`${current}: ${edge.specifier} 가 어느 파일인지 해소되지 않는다`);
        }
        continue;
      }
      if (target === queriesPath) {
        if (edge.names === '*') {
          wholeQueries = true;
        } else {
          for (const one of edge.names) {
            queryExports.add(one);
          }
        }
      }
      if (target === clientPath) {
        // 정의 자리는 걷지 않는다. 그 모듈에서 **무엇을 가져왔는지**만 보고, 가져온 것이
        // `apiClient` 면 부르는 자리의 호출이 위에서 이미 세어졌다.
        continue;
      }
      if (target.startsWith(srcRoot)) {
        queue.push(target);
      }
    }
  }

  const queriesText = readText(queriesPath);
  if (queriesText === undefined) {
    throw new Error(`질의 모듈을 읽지 못했다: ${queriesPath}`);
  }
  const byExport = operationsByExport(queriesText, queriesPath);
  unresolved.push(...byExport.unresolved);
  const names = wholeQueries ? [...byExport.operations.keys()] : [...queryExports];
  for (const name of names) {
    for (const one of byExport.operations.get(name) ?? []) {
      operations.add(one);
    }
  }

  return { modules: seen, queryExports: names.sort(), operations: [...operations].sort(), unresolved };
}

/** 정책 표와 자리 표의 **집합 등식** — 어느 쪽이 더 많아도 그 이름을 낸다. */
export function policyGaps(slotIds, policy = SCREEN_WRITE_POLICY) {
  const declared = new Set(Object.keys(policy));
  const slots = new Set(slotIds);
  return {
    undeclared: [...slots].filter((one) => !declared.has(one)).sort(),
    stale: [...declared].filter((one) => !slots.has(one)).sort(),
  };
}

/**
 * 게이트 본체 — 읽기 전용 화면마다 닿는 쓰기 operation 을 센다.
 *
 * `overlay` 는 **합성 원문**을 실제 트리 위에 얹는다(디스크를 건드리지 않는다) — 양성·음성
 * 대조가 진짜 그래프를 지나게 하려는 것이다.
 */
export function collectScreenWriteReach({ webRoot, overlay = new Map(), policy = SCREEN_WRITE_POLICY }) {
  const srcRoot = resolve(webRoot, 'src');
  const routesPath = resolve(srcRoot, 'routes.tsx');
  const queriesPath = resolve(srcRoot, QUERIES_MODULE);
  const clientPath = resolve(srcRoot, CLIENT_MODULE);

  const normalized = new Map([...overlay].map(([path, text]) => [resolve(path), text]));
  const readText = (path) => {
    const key = resolve(path);
    if (normalized.has(key)) {
      return normalized.get(key);
    }
    try {
      return readFileSync(key, 'utf8');
    } catch {
      return undefined;
    }
  };

  const routesText = readText(routesPath);
  if (routesText === undefined) {
    throw new Error(`자리 표를 읽지 못했다: ${routesPath}`);
  }
  const modules = screenModulesOf(routesText, routesPath, readText);
  if (modules.size === 0) {
    // 빈 모집단은 **모든 등식을 공허하게** 만든다 — 조용히 초록이 되는 길을 두지 않는다.
    throw new Error('자리 표에서 화면을 하나도 얻지 못했다');
  }

  const gaps = policyGaps([...modules.keys()], policy);
  const screens = [];
  for (const [slotId, modulePath] of modules) {
    if (modulePath === null || policy[slotId] !== 'READ_ONLY') {
      continue;
    }
    const reach = reachFrom(modulePath, { readText, srcRoot, queriesPath, clientPath });
    screens.push({
      slotId,
      module: relative(webRoot, modulePath),
      moduleCount: reach.modules.size,
      queryExports: reach.queryExports,
      operations: reach.operations,
      writes: writeOperationsOf(reach.operations),
      unresolved: reach.unresolved,
    });
  }

  return {
    slotIds: [...modules.keys()],
    elementlessCount: [...modules.values()].filter((one) => one === null).length,
    gaps,
    screens,
  };
}

/** 사람이 읽는 위반 목록 — 비어 있으면 통과다. */
export function violationsOf(result) {
  const lines = [];
  for (const one of result.gaps.undeclared) {
    lines.push(`자리 ${one} 의 쓰기 정책이 선언되지 않았다`);
  }
  for (const one of result.gaps.stale) {
    lines.push(`정책 표의 ${one} 이 자리 표에 없다`);
  }
  if (result.screens.length === 0) {
    lines.push('읽기 전용 화면이 하나도 없다 — 이 게이트가 공허하다');
  }
  for (const screen of result.screens) {
    for (const one of screen.unresolved) {
      lines.push(`${screen.slotId}: 모르는 꼴 — ${one}`);
    }
    for (const one of screen.writes) {
      lines.push(`${screen.slotId}(${screen.module}) 가 쓰기 operation 에 닿는다: ${one}`);
    }
  }
  return lines;
}
