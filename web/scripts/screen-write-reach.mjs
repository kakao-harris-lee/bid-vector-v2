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

/**
 * 파싱한 원문의 **공유 캐시** — 키는 `경로 + 원문`이다.
 *
 * 왜 있나(v M-4): 이 게이트는 호출마다 TS 프로그램을 세우고, 양성 대조가 많아 한 test 파일에서
 * 그것이 수십 번 일어난다. 캐시가 없으면 매번 `src` 전수를 다시 파싱해 **한 파일이 47초**가
 * 되고(실측), 그 비용이 병렬 수집의 다른 파일을 5초 timeout 으로 밀어낸다. 원문이 키라서
 * 오버레이가 바뀌면 그 파일만 다시 파싱된다 — 캐시가 대조를 무디게 하지 않는다.
 */
const sourceCache = new Map();

/**
 * 바로 앞 프로그램 — `createProgram` 에 넘기면 TS 가 **바뀌지 않은 파일의 구조를 재사용**한다.
 * 위 캐시가 같은 원문에 같은 `SourceFile` 객체를 주므로 재사용률이 높다(오버레이가 바꾼 파일만
 * 다시 선다).
 */
let previousProgram;

function cachedSourceFile(fileName, text, languageVersion) {
  const key = `${fileName}\u0000${text}`;
  const found = sourceCache.get(key);
  if (found !== undefined) {
    return found;
  }
  const made = ts.createSourceFile(fileName, text, languageVersion, true, ts.ScriptKind.TSX);
  sourceCache.set(key, made);
  return made;
}

/**
 * 이 게이트의 TS 프로그램 — **실제 `tsconfig.json`** 으로 연다(사본이 아니다). 타입 검사기가
 * 있어야 별칭을 선언까지 풀 수 있고, 그것이 HIGH ① 을 닫은 자리다.
 *
 * `overlay` 는 합성 원문을 실제 트리 위에 얹는다(디스크를 건드리지 않는다).
 */
export function writeReachProgram(webRoot, overlay = new Map()) {
  const configPath = resolve(webRoot, 'tsconfig.json');
  const read = ts.readConfigFile(configPath, ts.sys.readFile);
  if (read.error !== undefined) {
    throw new Error(`tsconfig 을 읽지 못했다: ${configPath}`);
  }
  const parsed = ts.parseJsonConfigFileContent(read.config, ts.sys, webRoot);
  if (parsed.fileNames.length === 0) {
    // 빈 프로그램은 **모든 등식을 공허하게** 만든다 — 조용히 초록이 되는 길을 두지 않는다.
    throw new Error(`TS 프로그램이 비었다: ${configPath}`);
  }
  const host = ts.createCompilerHost(parsed.options, true);
  const baseFileExists = host.fileExists.bind(host);
  const baseReadFile = host.readFile.bind(host);
  host.getSourceFile = (fileName, languageVersion) => {
    const key = resolve(fileName);
    const text = overlay.has(key) ? overlay.get(key) : baseReadFile(fileName);
    return text === undefined ? undefined : cachedSourceFile(fileName, text, languageVersion);
  };
  host.fileExists = (fileName) => overlay.has(resolve(fileName)) || baseFileExists(fileName);
  host.readFile = (fileName) => (overlay.has(resolve(fileName)) ? overlay.get(resolve(fileName)) : baseReadFile(fileName));
  const overlayDirs = new Set([...overlay.keys()].map((one) => dirname(one)));
  const baseDirectoryExists = host.directoryExists?.bind(host);
  if (baseDirectoryExists !== undefined) {
    host.directoryExists = (name) => overlayDirs.has(resolve(name)) || baseDirectoryExists(name);
  }
  previousProgram = ts.createProgram(
    [...parsed.fileNames, ...overlay.keys()],
    parsed.options,
    host,
    previousProgram,
  );
  return previousProgram;
}

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
        // **타입 전용 지정자는 값 간선이 아니다**(cr HIGH ① 의 뿌리 절반). `import type { X }`
        // 와 `import { type X }` 는 런타임에 아무것도 가져오지 않으므로 operation 에 닿을 수
        // 없다. 값과 섞어 두면 「모르는 이름은 위반」을 켜는 순간 출하 트리가 붉는다 — 그래서
        // **먼저** 걷어낸다(고치는 순서가 중요한 자리다).
        if (clause !== undefined) {
          if (ts.isImportClause(clause)) {
            const bindings = clause.namedBindings;
            if (clause.isTypeOnly) {
              names = [];
            } else if (bindings !== undefined && ts.isNamedImports(bindings) && clause.name === undefined) {
              names = bindings.elements
                .filter((one) => one.isTypeOnly !== true)
                .map((one) => (one.propertyName ?? one.name).text);
            }
          } else if (ts.isNamedExports(clause)) {
            names =
              node.isTypeOnly === true
                ? []
                : clause.elements
                    .filter((one) => one.isTypeOnly !== true)
                    .map((one) => (one.propertyName ?? one.name).text);
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
 * 별칭을 끝까지 푼다 — `export { x as y }` · 재수출 · 기본 내보내기 전부 같은 자리로 모인다.
 */
function resolvedSymbol(checker, symbol) {
  return symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0
    ? checker.getAliasedSymbol(symbol)
    : symbol;
}

/**
 * 한 파일 안에서 `apiClient` **심볼**이 내는 (메서드, 경로) 전수.
 *
 * **이름이 아니라 심볼로 고른다**(v L-1). 앞 판은 식별자 글자 `apiClient` 를 봤고, 그래서
 * 별칭 수입(`import { apiClient as c }`)과 네임스페이스 접근(`client['apiClient']`)이 이 축을
 * 비껴갔다. 심볼로 보면 그 둘이 같은 자리로 모인다.
 *
 * 아는 꼴(`<그 심볼>.<METHOD>('<리터럴>')`)이 아니면 `unresolved` 에 담는다 — 모르는 것은
 * 통과가 아니다(`screen-operations` 의 선례와 같은 처방).
 */
export function clientCallsIn(source, checker, clientSymbol) {
  const operations = [];
  const unresolved = [];
  const path = source.fileName;
  const visit = (node) => {
    if (ts.isIdentifier(node) || ts.isStringLiteralLike(node)) {
      const symbol = resolvedSymbol(checker, checker.getSymbolAtLocation(node));
      if (symbol !== undefined && clientSymbol !== undefined && symbol === clientSymbol) {
        const parent = node.parent;
        const isBindingName =
          parent !== undefined &&
          ((ts.isImportSpecifier(parent) && parent.name === node) ||
            (ts.isExportSpecifier(parent) && (parent.name === node || parent.propertyName === node)) ||
            (ts.isVariableDeclaration(parent) && parent.name === node) ||
            (ts.isBindingElement(parent) && (parent.name === node || parent.propertyName === node)));
        if (!isBindingName) {
          // 속성 접근(`c.POST`)과 요소 접근(`client['apiClient'].POST`) 둘 다 받는다 — 요소
          // 접근에서는 이 노드가 **인자 자리**의 문자열이라 호출 자리가 한 단 위에 있다.
          const access = ts.isElementAccessExpression(parent) ? parent.parent : parent;
          if (
            access !== undefined &&
            ts.isPropertyAccessExpression(access) &&
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
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return { operations, unresolved };
}

/** 그 선언을 담은 `queries.ts` 의 최상위 문장. 없으면 `undefined`. */
function topLevelStatementOf(declaration, source) {
  let current = declaration;
  while (current !== undefined && current.parent !== source) {
    current = current.parent;
  }
  return current;
}

/**
 * `queries.ts` 의 **내보내기 → operation 집합** — 키는 **그 모듈이 실제로 내보내는 이름 전수**다.
 *
 * **앞 판은 최상위 선언 이름만 키로 삼았다**(cr·v HIGH ①). 그래서 `export { requestBidPrice as
 * readFloorHint }` 한 줄이 키 집합 밖의 이름을 만들었고, 부르는 자리의 `?? []` 가 그것을
 * **operation 0 으로 조용히** 통과시켰다 — 모듈 머리말의 「모르는 것은 통과시키지 않는다」와
 * 정면으로 어긋나는 자리였다. 이제 키는 **TypeChecker 가 센 내보내기 전수**이고 별칭은
 * `getAliasedSymbol` 로 선언까지 풀린다. 그래서 별칭의 **꼴을 열거하지 않는다**.
 *
 * 선언 **사이 참조**도 따라간다(얇은 포장이 쓰기를 감추지 못하게).
 *
 * 값이 아닌 내보내기(타입·인터페이스)는 `isValue: false` 로 **키에는 남는다** — 그래야 부르는
 * 자리가 「아는 이름인데 operation 이 없다」와 「모르는 이름이다」를 가를 수 있다.
 */
export function operationsByExport(program, queriesPath) {
  const checker = program.getTypeChecker();
  const source = program.getSourceFile(queriesPath);
  if (source === undefined) {
    throw new Error(`질의 모듈이 프로그램에 없다: ${queriesPath}`);
  }
  const moduleSymbol = checker.getSymbolAtLocation(source);
  if (moduleSymbol === undefined) {
    throw new Error(`질의 모듈의 심볼을 얻지 못했다: ${queriesPath}`);
  }
  const clientSymbol = clientExportSymbol(program, checker);

  // 최상위 문장마다: 자기 호출과, 자기가 참조하는 **다른 최상위 선언**.
  const own = new Map();
  const refs = new Map();
  const unresolved = [];
  for (const statement of source.statements) {
    const calls = clientCallsIn(statement, checker, clientSymbol);
    own.set(statement, new Set(calls.operations));
    unresolved.push(...calls.unresolved);
    const referenced = new Set();
    const visit = (node) => {
      if (ts.isIdentifier(node)) {
        const target = resolvedSymbol(checker, checker.getSymbolAtLocation(node));
        for (const declaration of target?.declarations ?? []) {
          if (declaration.getSourceFile() === source) {
            const top = topLevelStatementOf(declaration, source);
            if (top !== undefined && top !== statement) {
              referenced.add(top);
            }
          }
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(statement);
    refs.set(statement, referenced);
  }

  // 선언 사이 **고정점** — 포장이 몇 겹이든 속의 operation 이 바깥 이름에 실린다.
  for (let round = 0; round < own.size + 1; round += 1) {
    let grew = false;
    for (const [statement, operations] of own) {
      for (const referenced of refs.get(statement) ?? []) {
        for (const one of own.get(referenced) ?? []) {
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

  // 내보낸 **이름**마다: 그 심볼이 가리키는 선언의 최상위 문장이 든 operation.
  const operations = new Map();
  for (const exported of checker.getExportsOfModule(moduleSymbol)) {
    const target = resolvedSymbol(checker, exported);
    const isValue = ((target?.flags ?? 0) & ts.SymbolFlags.Value) !== 0;
    const found = new Set();
    for (const declaration of target?.declarations ?? []) {
      if (declaration.getSourceFile() !== source) {
        continue;
      }
      const top = topLevelStatementOf(declaration, source);
      for (const one of (top === undefined ? undefined : own.get(top)) ?? []) {
        found.add(one);
      }
    }
    operations.set(exported.getName(), { operations: found, isValue });
  }
  return { operations, unresolved };
}

/** `client.ts` 가 내보내는 `apiClient` 의 **심볼**. 없으면 `undefined`(부르는 자리가 던진다). */
export function clientExportSymbol(program, checker) {
  const clientSource = program
    .getSourceFiles()
    .find((one) => one.fileName.endsWith(`/src/${CLIENT_MODULE}`));
  const moduleSymbol = clientSource === undefined ? undefined : checker.getSymbolAtLocation(clientSource);
  const exported =
    moduleSymbol === undefined
      ? undefined
      : checker.getExportsOfModule(moduleSymbol).find((one) => one.getName() === CLIENT_EXPORT);
  return resolvedSymbol(checker, exported);
}

/**
 * `apiClient` **바인딩**을 수입하는 파일 전수(`src` 기준 상대 경로).
 *
 * 설계 검토 우회 5 행이 「그 사실을 등식으로 고정한다」고 적은 자리다 — 앞 판의 짝 test 는 그
 * 등식을 **재지 않았다**(cr HIGH ②). 모듈을 수입하는 자리는 셋 더 있고(`rememberCsrf`), 하나인
 * 것은 **모듈이 아니라 이 바인딩**이다.
 */
export function clientBindingImporters(program, webRoot) {
  const checker = program.getTypeChecker();
  const clientSymbol = clientExportSymbol(program, checker);
  const srcRoot = resolve(webRoot, 'src');
  const importers = new Set();
  for (const source of program.getSourceFiles()) {
    if (!source.fileName.startsWith(`${srcRoot}/`) || /\.test\.tsx?$/u.test(source.fileName)) {
      continue;
    }
    for (const statement of source.statements) {
      if (!ts.isImportDeclaration(statement) || statement.importClause === undefined) {
        continue;
      }
      const bindings = statement.importClause.namedBindings;
      const brings =
        bindings !== undefined &&
        (ts.isNamespaceImport(bindings)
          ? // 통째로 가져오면 그 바인딩도 함께 온다.
            resolvedSymbol(checker, checker.getSymbolAtLocation(statement.moduleSpecifier)) !== undefined &&
            statement.moduleSpecifier.getText().includes('client')
          : bindings.elements.some(
              (one) => resolvedSymbol(checker, checker.getSymbolAtLocation(one.name)) === clientSymbol,
            ));
      if (brings === true) {
        importers.add(relative(srcRoot, source.fileName));
      }
    }
  }
  return [...importers].sort();
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
export function reachFrom(entry, { program, readText, srcRoot, queriesPath, clientPath }) {
  const checker = program.getTypeChecker();
  const clientSymbol = clientExportSymbol(program, checker);
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
    // 판정은 **부르는 자리**에서 심볼로 한다. 클라이언트 모듈 자신은 정의 자리라 걷지 않는다 —
    // 거기 `apiClient.use(…)` 같은 자기 배선이 있고, 그것은 화면의 호출이 아니다.
    const source = program.getSourceFile(current);
    if (source === undefined) {
      unresolved.push(`${current}: TS 프로그램에 없는 파일이다`);
    } else {
      const calls = clientCallsIn(source, checker, clientSymbol);
      for (const one of calls.operations) {
        operations.add(one);
      }
      unresolved.push(...calls.unresolved);
    }

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
        // 정의 자리는 걷지 않는다. 그 모듈에서 **무엇을 가져왔는지**만 본다 — 통째로 가져오면
        // 어느 이름이 왔는지 이 축에서 알 수 없으므로 **모르는 것**으로 센다(v L-1).
        if (edge.names === '*') {
          unresolved.push(`${current}: 클라이언트 모듈을 통째로 가져온다`);
        }
        continue;
      }
      if (target.startsWith(srcRoot)) {
        queue.push(target);
      }
    }
  }

  const byExport = operationsByExport(program, queriesPath);
  unresolved.push(...byExport.unresolved);
  const names = wholeQueries ? [...byExport.operations.keys()] : [...queryExports];
  for (const name of names) {
    const known = byExport.operations.get(name);
    if (known === undefined) {
      // **모르는 이름은 통과가 아니다**(cr·v HIGH ①). 앞 판은 `?? []` 로 operation 0 을 주었고,
      // 별칭 재수출 한 줄이 그 길로 쓰기를 숨겼다.
      unresolved.push(`${queriesPath}: 수입한 이름 ${name} 이 내보내기 전수에 없다`);
      continue;
    }
    for (const one of known.operations) {
      operations.add(one);
    }
  }

  return { modules: seen, queryExports: names.sort(), operations: [...operations].sort(), unresolved };
}

/**
 * 자리 → 그 `element` 가 받는 **리터럴이 아닌 prop** 이름들.
 *
 * **쓰기 능력은 import 그래프 밖에서도 꽂힌다**(v M-1): 자리 표가 `<ResultsScreen
 * requestFloor={requestBidPrice} />` 처럼 함수를 내려 주면 화면 모듈의 폐포에는 그 질의가
 * 없다. 그 꼴을 **싼 값에** 닫는다 — 읽기 전용 화면의 element 는 **리터럴이 아닌 prop 을 받지
 * 않는다**. (공유 가변 모듈로 꽂는 길은 이 축이 아니라 설계 검토 (0) 의 경계 밖이다.)
 */
export function elementPropsOf(routesText, routesPath) {
  const source = parse(routesText, routesPath);
  const bySlot = new Map();
  const visit = (node) => {
    if (ts.isObjectLiteralExpression(node)) {
      const id = node.properties.find(
        (one) =>
          ts.isPropertyAssignment(one) &&
          one.name !== undefined &&
          ts.isIdentifier(one.name) &&
          one.name.text === 'id' &&
          ts.isStringLiteralLike(one.initializer),
      );
      const element = node.properties.find(
        (one) =>
          ts.isPropertyAssignment(one) &&
          one.name !== undefined &&
          ts.isIdentifier(one.name) &&
          one.name.text === 'element',
      );
      if (id !== undefined && element !== undefined && ts.isPropertyAssignment(element)) {
        const opening = ts.isJsxSelfClosingElement(element.initializer)
          ? element.initializer
          : ts.isJsxElement(element.initializer)
            ? element.initializer.openingElement
            : undefined;
        const slotId = ts.isPropertyAssignment(id) && ts.isStringLiteralLike(id.initializer) ? id.initializer.text : undefined;
        if (opening !== undefined && slotId !== undefined) {
          const risky = opening.attributes.properties
            .map((attribute) => {
              if (ts.isJsxSpreadAttribute(attribute)) {
                return '…전개';
              }
              // 글자 리터럴만 안전하다 — 식은 무엇이든 담을 수 있다.
              return attribute.initializer !== undefined && !ts.isStringLiteral(attribute.initializer)
                ? attribute.name.getText(source)
                : undefined;
            })
            .filter((one) => one !== undefined);
          if (risky.length > 0) {
            bySlot.set(slotId, risky);
          }
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(source);
  return bySlot;
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
  const injectedProps = elementPropsOf(routesText, routesPath);
  const injected = [];
  const modules = screenModulesOf(routesText, routesPath, readText);
  if (modules.size === 0) {
    // 빈 모집단은 **모든 등식을 공허하게** 만든다 — 조용히 초록이 되는 길을 두지 않는다.
    throw new Error('자리 표에서 화면을 하나도 얻지 못했다');
  }

  // **별칭을 선언까지 푸는 데 타입 검사기가 필요하다**(HIGH ①). 오버레이를 그대로 얹어
  // 양성·음성 대조가 진짜 심볼 그래프를 지나게 한다.
  const program = writeReachProgram(webRoot, normalized);

  const gaps = policyGaps([...modules.keys()], policy);
  const screens = [];
  for (const [slotId, modulePath] of modules) {
    if (modulePath === null || policy[slotId] !== 'READ_ONLY') {
      continue;
    }
    // **쓰기 능력을 바깥에서 꽂는 길 하나를 여기서 닫는다**(v M-1): 읽기 화면의 route
    // `element` 가 리터럴이 아닌 prop 을 받으면 그 값이 어디서 왔는지 이 축에서 알 수 없다.
    for (const one of injectedProps.get(slotId) ?? []) {
      injected.push(`${slotId}: route element 가 리터럴이 아닌 prop ${one} 을 받는다`);
    }
    const reach = reachFrom(modulePath, { program, readText, srcRoot, queriesPath, clientPath });
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
    injected,
    screens,
    clientBindingImporters: clientBindingImporters(program, webRoot),
  };
}

/** 사람이 읽는 위반 목록 — 비어 있으면 통과다. */
export function violationsOf(result) {
  const lines = [];
  for (const one of result.injected ?? []) {
    lines.push(one);
  }
  // 설계 검토 우회 5 행의 전제 — **이 바인딩을 수입하는 자리는 질의 모듈 하나**다(cr HIGH ②).
  // 앞 판은 그 등식을 어디서도 재지 않았다.
  const expected = [QUERIES_MODULE];
  const importers = result.clientBindingImporters ?? [];
  if (importers.length !== expected.length || importers.some((one, at) => one !== expected[at])) {
    lines.push(`${CLIENT_EXPORT} 바인딩을 수입하는 자리가 질의 모듈 하나가 아니다: ${importers.join(' · ')}`);
  }
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
