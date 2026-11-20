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
//  · 무엇이 쓰기인가 — **`apiClient` 호출 자리**에서 심볼 동일성과 메서드로 정한다. 이름
//    술어(「그 함수 금지」)면 개명 한 번에 열린다.
//  · 도달 — **폐포 둘이 겹친다**(수정 라운드 3). ⓐ **적재 폐포**: 값 수입으로 닿는 모듈 전수의
//    **적재 때 도는 최상위 문장**이 씨앗이다(모듈이 그래프에 들면 그 최상위는 이미 실행된 것이다).
//    ⓑ **호출 그래프**: 식별자가 가리키는 선언을 **파일 경계를 묻지 않고** 따라간다. 특별한
//    경계가 없으므로 `queries.ts` 는 그래프 위의 한 마디일 뿐이다.
//
// **모르는 것은 통과시키지 않는다.** 해소되지 않는 지정자 · 비리터럴 경로·메서드 · 네임스페이스
// 수입 · 리터럴 동적 수입 · 어느 선언도 가리키지 않는 수입 이름은 전부 위반이다(fail-closed).
//
// **이 게이트가 닿지 않는 자리**는 `design-review.md` 의 「건전성 경계」 표가 정본이다 — 거기
// 적히지 않은 꼴을 발견하면 그 표를 먼저 고친다.
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
/**
 * **이 파일의 캐시 넷은 디스크 변화에 무효가 되지 않는다**(cr LOW): 원문 캐시 · 앞 프로그램 ·
 * router 심볼 · Outlet 축의 답. 한 프로세스 안의 게이트 실행을 위한 것이고, 그 동안 디스크는
 * 바뀌지 않는다는 전제에 선다(변이는 프로세스를 새로 띄워 잰다). 키가 **내용**이라 오버레이
 * 쪽 변화는 그대로 반영된다.
 */
const SOURCE_CACHE_LIMIT = 4096;
const sourceCache = new Map();

/**
 * 바로 앞 프로그램 — `createProgram` 에 넘기면 TS 가 **바뀌지 않은 파일의 구조를 재사용**한다.
 * 위 캐시가 같은 원문에 같은 `SourceFile` 객체를 주므로 재사용률이 높다(오버레이가 바꾼 파일만
 * 다시 선다).
 */
let previousProgram;

/** 오버레이가 **없는** 프로그램 — 출하 트리 판정과 여러 단위 test 가 같은 것을 쓴다. */
let basePrograms = new Map();

function cachedSourceFile(fileName, text, languageVersion) {
  const key = `${fileName}\u0000${text}`;
  const found = sourceCache.get(key);
  if (found !== undefined) {
    return found;
  }
  const made = ts.createSourceFile(fileName, text, languageVersion, true, ts.ScriptKind.TSX);
  // **한도를 둔다**(cr LOW): 오버레이마다 키가 달라 캐시가 끝없이 자란다(실측 608MB). 가장
  // 오래된 것부터 버린다 — 바로 앞 프로그램이 쓰는 원문은 최근 쪽에 있다.
  if (sourceCache.size >= SOURCE_CACHE_LIMIT) {
    const oldest = sourceCache.keys().next();
    if (oldest.done !== true) {
      sourceCache.delete(oldest.value);
    }
  }
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
  if (overlay.size === 0) {
    const cached = basePrograms.get(webRoot);
    if (cached !== undefined) {
      return cached;
    }
  }
  const configPath = resolve(webRoot, 'tsconfig.json');
  const read = ts.readConfigFile(configPath, ts.sys.readFile);
  if (read.error !== undefined) {
    throw new Error(`tsconfig 을 읽지 못했다: ${configPath}`);
  }
  const parsed = ts.parseJsonConfigFileContent(read.config, ts.sys, webRoot);
  // **test 파일은 뿌리에서 뺀다.** 이 게이트가 보는 것은 **출하 모듈의 그래프**이고
  // `clientBindingImporters` 도 test 를 세지 않는다 — 모집단을 술어가 쓰는 범위에 맞춘다.
  // (속도를 노린 것이 아니다: 재어 보니 프로그램 비용은 거의 그대로였다.)
  parsed.fileNames = parsed.fileNames.filter((one) => !/\.test\.tsx?$/u.test(one));
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
  if (overlay.size === 0) {
    basePrograms.set(webRoot, previousProgram);
  }
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
  // 문장을 받기도 하므로 **그 문장이 사는 파일**을 묻는다(`source.fileName` 은 문장에 없다).
  const file = ts.isSourceFile(source) ? source : source.getSourceFile();
  const path = file.fileName;
  /**
   * **정의 자리에서는 「모르는 꼴」을 세지 않는다.** 클라이언트 모듈 자신은 그 값을 만들고
   * 미들웨어를 붙인다(`apiClient.use(…)`) — 화면의 호출이 아니다. HTTP 메서드 호출이라면
   * 아래에서 operation 으로 여전히 잡힌다.
   */
  const definitionSite = path.endsWith(`/src/${CLIENT_MODULE}`);
  const visit = (node) => {
    if (ts.isIdentifier(node) || ts.isStringLiteralLike(node)) {
      const symbol = resolvedSymbol(checker, checker.getSymbolAtLocation(node));
      if (symbol !== undefined && symbol === clientSymbol) {
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
          } else if (!definitionSite) {
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
 * `client.ts` 가 내보내는 `apiClient` 의 **심볼**.
 *
 * 없으면 **던진다**(cr LOW — 앞 판 주석은 「부르는 자리가 던진다」고 적었는데 던지는 자리가
 * 없었다). 그 심볼이 없는 채로 도는 것은 **호출 축 전체가 조용해지는** 것이고, 조용한 초록은
 * 이 게이트가 가장 피하려는 꼴이다.
 */
const clientSymbolCache = new WeakMap();

export function clientExportSymbol(program, checker) {
  const cached = clientSymbolCache.get(program);
  if (cached !== undefined) {
    return cached;
  }
  const clientSource = program
    .getSourceFiles()
    .find((one) => one.fileName.endsWith(`/src/${CLIENT_MODULE}`));
  const moduleSymbol = clientSource === undefined ? undefined : checker.getSymbolAtLocation(clientSource);
  const exported =
    moduleSymbol === undefined
      ? undefined
      : checker.getExportsOfModule(moduleSymbol).find((one) => one.getName() === CLIENT_EXPORT);
  const symbol = resolvedSymbol(checker, exported);
  if (symbol === undefined) {
    throw new Error(`${CLIENT_MODULE} 의 ${CLIENT_EXPORT} 심볼을 얻지 못했다 — 호출 축이 조용해진다`);
  }
  clientSymbolCache.set(program, symbol);
  return symbol;
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
  const clientSource = program.getSourceFile(resolve(webRoot, 'src', CLIENT_MODULE));
  const clientModuleSymbol =
    clientSource === undefined ? undefined : checker.getSymbolAtLocation(clientSource);
  if (clientModuleSymbol === undefined) {
    throw new Error(`${CLIENT_MODULE} 의 모듈 심볼을 얻지 못했다 — 네임스페이스 갈래가 조용해진다`);
  }
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
      // **글자가 아니라 심볼로 가른다**(v low · cr M). 앞 판은 지정자 원문에 `client` 가
      // 들어가는지 보았다 — 관계없는 `__clientside_utils__` 에 거짓으로 걸리고, 다른 이름의
      // 게이트웨이는 놓쳤다. 네임스페이스 수입은 **그 모듈의 심볼**이 클라이언트 모듈인지 본다.
      const brings =
        bindings !== undefined &&
        (ts.isNamespaceImport(bindings)
          ? resolvedSymbol(checker, checker.getSymbolAtLocation(statement.moduleSpecifier)) === clientModuleSymbol
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
 * **한 심볼 그래프다 — 특별한 경계가 없다**(v H-1r). 앞 판은 `queries.ts` 를 **닫힌 세계**로
 * 두고 그 모듈의 내보내기만 operation 으로 접었다. 그래서 그 모듈 밖에 선언된 쓰기를 한 줄로
 * 가져다 재수출하면 네 게이트가 모두 초록이었다(실측 세 꼴).
 *
 * 이제 진입 모듈의 최상위 문장에서 출발해 **식별자가 가리키는 선언**을 따라간다 — 파일 경계를
 * 묻지 않으므로 `queries.ts` 는 그래프 위의 한 마디일 뿐이다. 쓰기인지는 **`apiClient` 호출
 * 자리**에서 심볼 동일성과 메서드로 정한다.
 *
 * 선언 단위라 **모듈을 통째로 삼키지 않는다**: 결과 화면이 질의 모듈을 수입해도 부르지 않는
 * 쓰기 선언은 그래프에 들지 않는다.
 */
/**
 * 그 문장이 **적재 때 실행되는가**.
 *
 * 함수 선언과 타입 선언은 실행되지 않는다 — 몸통은 **불릴 때** 돈다(그 자리는 호출 그래프가
 * 든다). 초기값이 전부 함수인 변수 선언(`export const f = () => …`)도 같다. 그 밖(변수
 * 초기값 · 식 문장 · 클래스의 static 초기화 · 수입)은 **적재가 곧 실행**이다.
 *
 * 이 구별이 없으면 둘 중 하나가 된다: 전부 씨앗으로 두면 질의 모듈의 쓰기 함수 몸통까지
 * 읽혀 출하 트리가 거짓으로 붉고, 아무것도 안 두면 **적재 때 도는 쓰기**를 놓친다(v H-2r).
 */
function runsAtLoad(statement) {
  if (
    ts.isFunctionDeclaration(statement) ||
    ts.isInterfaceDeclaration(statement) ||
    ts.isTypeAliasDeclaration(statement) ||
    ts.isModuleDeclaration(statement)
  ) {
    return false;
  }
  if (ts.isVariableStatement(statement)) {
    // 초기값이 **전부** 함수면 선언일 뿐이다. 하나라도 아니면 그 식이 적재 때 돈다.
    return !statement.declarationList.declarations.every(
      (one) => one.initializer !== undefined && ts.isFunctionLike(one.initializer),
    );
  }
  return true;
}

/**
 * 한 화면 모듈이 닿는 **operation 집합**과 **해소 불가 목록**.
 *
 * **폐포가 둘이다**(수정 라운드 3 · v H-2r).
 *  ⓐ **적재 폐포** — 값 수입 간선으로 닿는 모듈 전수의 **적재 때 도는 최상위 문장**을 씨앗으로
 *    둔다. 모듈이 그래프에 들어오면 그 최상위는 **이미 실행된 것**이다. 라운드 2 가 선언
 *    단위로 바꾸면서 이 사실을 잃었고, 그래서 적재 때 도는 쓰기 둘이 모든 게이트를 지났다.
 *  ⓑ **호출 그래프** — 식별자가 가리키는 선언을 파일 경계를 묻지 않고 따라간다. 함수 몸통은
 *    불릴 때 도므로 이쪽이 든다.
 *
 * 쓰기인지는 **`apiClient` 호출 자리**에서 심볼 동일성과 메서드로 정한다 — `queries.ts` 는
 * 그래프 위의 한 마디일 뿐이다.
 */
export function reachFrom(entry, { program, srcRoot }) {
  const checker = program.getTypeChecker();
  const clientSymbol = clientExportSymbol(program, checker);
  const source = program.getSourceFile(entry);
  if (source === undefined) {
    throw new Error(`화면 모듈이 프로그램에 없다: ${entry}`);
  }

  const seen = new Set();
  const modules = new Set();
  /**
   * **적재한 모듈** — `modules`(닿은 모듈)와 **다른 집합**이다. 둘을 한 집합으로 쓰면, 어떤
   * 선언을 먼저 따라가다 그 모듈을 「닿았다」로 표시하는 순간 **적재 씨앗이 통째로 건너뛰어진다**
   * (실측: 적재 때 도는 쓰기가 그 길로 사라졌다). 큐가 LIFO 라 그 순서가 실제로 일어난다.
   */
  const loaded = new Set();
  const operations = new Set();
  const unresolved = [];
  const queue = [];

  const ours = (file) => file.fileName.startsWith(`${srcRoot}/`);

  /** 그 모듈을 **적재한다** — 적재 때 도는 최상위 문장 전수가 씨앗이 된다. */
  const load = (file) => {
    if (!ours(file) || loaded.has(file.fileName)) {
      return;
    }
    loaded.add(file.fileName);
    modules.add(file.fileName);
    for (const statement of file.statements) {
      if (runsAtLoad(statement)) {
        queue.push(statement);
      }
    }
  };

  /** 그 선언이 **우리 트리 안**이면 그것을 담은 최상위 문장을 큐에 민다. */
  const follow = (declaration) => {
    const file = declaration.getSourceFile();
    if (!ours(file)) {
      // 의존 패키지의 선언 — 이 축의 밖이다(번들 경계 게이트가 든다).
      return;
    }
    if (ts.isSourceFile(declaration)) {
      followNamespace(declaration);
      return;
    }
    const top = topLevelStatementOf(declaration, file);
    if (top !== undefined) {
      queue.push(top);
    }
  };

  /**
   * **네임스페이스 = 그 모듈이 내보내는 것 전부.**
   *
   * `import * as q` 와 `await import(리터럴)` 이 손에 쥐어 주는 것은 **그 모듈의 내보내기
   * 집합**이고, 어느 이름을 꺼낼지는 이 축에서 알 수 없다(셈한 키·`Object.values`). 그래서
   * **검사기가 세는 그 집합**(`getExportsOfModule`)을 전부 따라간다.
   *
   * 그 집합을 쓰는 것이 요점이다(v H-3r). 앞 판은 「그 모듈의 **문장** 전수」를 밀었는데,
   * `export * from …` 한 줄짜리 barrel 에서는 그 문장이 재수출 하나뿐이라 **적재 때 도는 것만**
   * 씨앗이 됐다 — 함수 선언은 적재 씨앗이 아니므로 쓰기가 그대로 빠져나갔다. 내보내기 집합은
   * 재수출을 **이미 평탄화해** 준다.
   *
   * 모듈의 최상위는 따로 **적재**한다 — 내보내지 않는 부작용도 실행되기 때문이다.
   */
  const followNamespace = (file) => {
    if (!ours(file)) {
      return;
    }
    load(file);
    const moduleSymbol = checker.getSymbolAtLocation(file);
    if (moduleSymbol === undefined) {
      unresolved.push(`${file.fileName}: 모듈 심볼을 얻지 못했다 — 네임스페이스가 조용해진다`);
      return;
    }
    for (const exported of checker.getExportsOfModule(moduleSymbol)) {
      const target = resolvedSymbol(checker, exported);
      for (const declaration of target?.declarations ?? []) {
        follow(declaration);
      }
    }
  };

  /** 수입 지정자가 가리키는 모듈을 적재한다(타입 전용은 적재가 아니다). */
  const loadFrom = (specifier, isTypeOnly) => {
    if (isTypeOnly === true || !ts.isStringLiteralLike(specifier)) {
      return;
    }
    const target = resolvedSymbol(checker, checker.getSymbolAtLocation(specifier));
    for (const declaration of target?.declarations ?? []) {
      if (ts.isSourceFile(declaration)) {
        load(declaration);
      }
    }
  };

  // 진입 모듈은 **전수**가 씨앗이다 — 그 화면의 코드는 전부 그 화면의 것이다.
  loaded.add(source.fileName);
  modules.add(source.fileName);
  queue.push(...source.statements);

  while (queue.length > 0) {
    const statement = queue.pop();
    if (seen.has(statement)) {
      continue;
    }
    seen.add(statement);
    modules.add(statement.getSourceFile().fileName);

    // 이 문장이 **직접** 내는 호출.
    const calls = clientCallsIn(statement, checker, clientSymbol);
    for (const one of calls.operations) {
      operations.add(one);
    }
    unresolved.push(...calls.unresolved);

    const visit = (node) => {
      // **값 수입은 그 모듈을 적재한다**(부작용 수입도 이 길로 든다).
      if (ts.isImportDeclaration(node)) {
        loadFrom(node.moduleSpecifier, node.importClause?.isTypeOnly);
      }
      if (ts.isExportDeclaration(node) && node.moduleSpecifier !== undefined) {
        loadFrom(node.moduleSpecifier, node.isTypeOnly);
      }
      if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword) {
        const first = node.arguments[0];
        if (first === undefined || !ts.isStringLiteralLike(first)) {
          unresolved.push(`${statement.getSourceFile().fileName}: 동적 import 의 지정자가 리터럴이 아니다`);
        } else {
          // **리터럴 동적 수입은 네임스페이스다**(v H-2r): 어느 이름을 꺼낼지 이 축에서 알 수
          // 없으므로 정적 `import * as` 와 **같은 정의**를 쓴다.
          const target = resolvedSymbol(checker, checker.getSymbolAtLocation(first));
          for (const declaration of target?.declarations ?? []) {
            if (ts.isSourceFile(declaration)) {
              followNamespace(declaration);
            }
          }
        }
      }
      // 상대 지정자가 해소되지 않으면 **모르는 것**이다.
      if (
        (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
        node.moduleSpecifier !== undefined &&
        ts.isStringLiteralLike(node.moduleSpecifier) &&
        node.moduleSpecifier.text.startsWith('.') &&
        checker.getSymbolAtLocation(node.moduleSpecifier) === undefined
      ) {
        unresolved.push(
          `${statement.getSourceFile().fileName}: ${node.moduleSpecifier.text} 가 어느 파일인지 해소되지 않는다`,
        );
      }
      // **수입한 이름이 아무것도 가리키지 않으면 모르는 것이다**(fail-closed).
      if (ts.isImportSpecifier(node) || ts.isImportClause(node)) {
        const name = node.name;
        const bound = name === undefined ? undefined : resolvedSymbol(checker, checker.getSymbolAtLocation(name));
        if (name !== undefined && (bound?.declarations ?? []).length === 0) {
          unresolved.push(
            `${statement.getSourceFile().fileName}: 수입한 이름 ${name.text} 이 어느 선언도 가리키지 않는다`,
          );
        }
      }
      if (ts.isIdentifier(node)) {
        // **단축 속성**(`{ requestBidPrice }`)은 이름이 가리키는 것이 속성 심볼이다(v H-2r) —
        // 값 심볼을 따로 묻지 않으면 그 간선이 통째로 사라진다.
        const shorthand =
          node.parent !== undefined && ts.isShorthandPropertyAssignment(node.parent)
            ? checker.getShorthandAssignmentValueSymbol(node.parent)
            : undefined;
        const target = resolvedSymbol(checker, shorthand ?? checker.getSymbolAtLocation(node));
        for (const declaration of target?.declarations ?? []) {
          follow(declaration);
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(statement);
  }

  return {
    modules,
    declarations: seen.size,
    operations: [...operations].sort(),
    unresolved,
  };
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

/** router 가 값을 아래로 내리는 자리의 이름들 — 심볼로 고르므로 **철자에 기대지 않는다**. */
const ROUTER_OUTLET_EXPORTS = ['Outlet', 'useOutlet'];
const ROUTER_CONTEXT_HOOK = 'useOutletContext';
/** 위 이름들을 내보내는 **모듈 심볼 집합**을 담는 자리(이름과 섞이지 않는 키). */
const ROUTER_MODULES = Symbol('router-modules');

/**
 * 프로그램마다 한 번만 세는 router 심볼 — `getExportsOfModule` 을 호출마다 돌면 그 자체가
 * 가장 비싼 자리였다(실측 1.2초/호출 가운데 대부분).
 */
const routerSymbolCache = new WeakMap();

/** 위 축의 답 — 키는 [outletCacheKey] 가 짓는다. */
const outletCache = new Map();

/**
 * 그 축의 답을 바꿀 수 **있는** 오버레이 항목만 추린 키.
 *
 * **건전성 조건**: 어떤 파일이 router 의 그 심볼들에 닿으려면 ⓐ `react-router` 를 **이름으로**
 * 수입하거나(패키지 이름이 그 모듈의 정체다 — 경로 별칭 설정이 0 이다) ⓑ 그것을 재수출하는
 * 우리 모듈을 수입해야 한다. 그래서 키는 **그 패키지 이름을 담은 항목**과 **우리 트리에서 그
 * 심볼을 노출하거나 닿는 파일의 경로**를 담은 항목으로 충분하다.
 *
 * ⓑ 의 재수출 모듈이 **오버레이 밖에 이미 있고** 오버레이가 그것을 새로 수입하는 경우만
 * 이 키가 놓친다. 오늘 그런 모듈은 0 이고(출하 트리의 노출 모듈은 패키지 자신뿐이다), 생기면
 * 그 파일이 곧 ⓐ 를 담으므로 같은 커밋에서 키에 든다.
 */
function outletCacheKey(overlay, srcRoot) {
  return [...overlay]
    .filter(([path, text]) => {
      const body = typeof text === 'string' ? text : '';
      return body.includes(ROUTER_PACKAGE) || outletSensitivePaths.has(path) || !path.startsWith(`${srcRoot}/`);
    })
    .map(([path, text]) => `${path}\u0000${typeof text === 'string' ? text : ''}`)
    .sort()
    .join('\u0001');
}

/** 그 축에 닿는 **출하 파일**의 경로 — 축을 한 번 셀 때 함께 적어 둔다. */
const outletSensitivePaths = new Set();

/** 그 패키지를 가리키는 지정자인가 — 패키지 이름은 그 모듈의 **정체**다(별칭 경로 설정 0). */
const ROUTER_PACKAGE = 'react-router';
const routerSpecifier = (text) => text === ROUTER_PACKAGE || text.startsWith(`${ROUTER_PACKAGE}/`);

/**
 * 그 이름들의 **심볼**과, 그것을 내보내는 **모듈 심볼**.
 *
 * 패키지를 **우리 트리의 수입 자리에서** 해소한다 — `node_modules` 전수를 훑으면 프로그램마다
 * 0.6초가 들고(실측) 이 게이트는 대조마다 프로그램을 새로 세운다. 공개 진입점의 심볼은 그
 * 수입 지정자가 그대로 준다.
 *
 * 우리 트리에 그 수입이 하나도 없으면 **던진다** — 이 축이 조용히 비는 길을 두지 않는다.
 */
function routerSymbols(program, checker, names, srcRoot) {
  const key = names.join('·');
  const perProgram = routerSymbolCache.get(program) ?? new Map();
  const cached = perProgram.get(key);
  if (cached !== undefined) {
    return cached;
  }
  const found = new Map();
  const modules = new Set();
  for (const source of program.getSourceFiles()) {
    if (!source.fileName.startsWith(`${srcRoot}/`)) {
      continue;
    }
    for (const statement of source.statements) {
      const specifier =
        (ts.isImportDeclaration(statement) || ts.isExportDeclaration(statement)) &&
        statement.moduleSpecifier !== undefined &&
        ts.isStringLiteralLike(statement.moduleSpecifier)
          ? statement.moduleSpecifier
          : undefined;
      if (specifier === undefined || !routerSpecifier(specifier.text)) {
        continue;
      }
      const moduleSymbol = resolvedSymbol(checker, checker.getSymbolAtLocation(specifier));
      if (moduleSymbol === undefined) {
        continue;
      }
      for (const exported of checker.getExportsOfModule(moduleSymbol)) {
        if (names.includes(exported.getName())) {
          found.set(exported.getName(), resolvedSymbol(checker, exported));
          modules.add(moduleSymbol);
        }
      }
    }
  }
  if (modules.size === 0) {
    throw new Error(`${ROUTER_PACKAGE} 의 ${names.join('·')} 를 우리 트리의 수입 자리에서 찾지 못했다`);
  }
  found.set(ROUTER_MODULES, modules);
  perProgram.set(key, found);
  routerSymbolCache.set(program, perProgram);
  return found;
}

/**
 * router 가 **리터럴이 아닌 값을 아래로 내리는** 자리 전수(`src` 기준 상대 경로).
 *
 * 왜 자리 표와 따로 서나(v M-1r): router context 는 route `element` 의 prop 이 아니라 **셸의
 * `<Outlet context={…} />`** 로 내려가고 받는 쪽은 `useOutletContext()` 다. 그래서 읽기 화면의
 * import 폐포에도, element prop 축에도 걸리지 않는다.
 *
 * **철자가 아니라 심볼로 고른다**(cr HIGH · v M). 앞 판은 JSX 태그 글자가 `Outlet` 인지 보았고,
 * 그래서 `import { Outlet as Slot }` 과 훅 꼴(`useOutlet`)이 그대로 지나갔다(실측 초록).
 *
 * 받는 쪽(`useOutletContext`)도 **쓰는 것만으로** 센다 — 그 훅이 읽는 값은 이 축에서 어디서
 * 왔는지 알 수 없다. 오늘 그 둘 다 출하 트리에 0 이다.
 */
export function outletContextSites(program, webRoot, overlay = new Map()) {
  /**
   * 캐시 키는 [outletCacheKey] 가 짓는다 — 그 주석이 건전성 조건을 적는다.
   *
   * **디스크가 바뀌면 이 캐시는 무효가 되지 않는다**(cr LOW). 한 프로세스 안의 게이트 실행을
   * 위한 것이고, 디스크를 고치는 쪽은 새 프로세스에서 다시 잰다.
   */
  const key = outletCacheKey(overlay, resolve(webRoot, 'src'));
  const cached = outletCache.get(key);
  if (cached !== undefined) {
    return cached;
  }
  const checker = program.getTypeChecker();
  const srcRoot = resolve(webRoot, 'src');
  const outlets = routerSymbols(program, checker, ROUTER_OUTLET_EXPORTS, srcRoot);
  const receivers = routerSymbols(program, checker, [ROUTER_CONTEXT_HOOK], srcRoot);
  if (outlets.size <= 1 || receivers.size <= 1) {
    // 심볼을 못 얻으면 이 축이 **조용히** 비어 버린다 — 조용한 초록을 두지 않는다.
    throw new Error('react-router 의 Outlet·useOutletContext 심볼을 얻지 못했다 — 주입 축이 조용해진다');
  }
  /** 식 하나의 심볼 — 별칭을 끝까지 풀고 `RR.Outlet` 같은 속성 접근도 같은 자리로 모은다. */
  const symbolOf = (node) => resolvedSymbol(checker, checker.getSymbolAtLocation(node));
  const outletSymbols = new Set([...outlets].filter(([key]) => key !== ROUTER_MODULES).map(([, one]) => one));
  const receiverSymbols = new Set([...receivers].filter(([key]) => key !== ROUTER_MODULES).map(([, one]) => one));
  const sites = [];

  /**
   * **그 심볼을 노출하는 모듈 집합** — react-router 와, 그것을 (이름을 바꿔서라도) 재수출하는
   * 우리 모듈들이다. 글자가 아니라 **내보내기 집합**으로 센다.
   */
  const exposing = new Set([...(outlets.get(ROUTER_MODULES) ?? []), ...(receivers.get(ROUTER_MODULES) ?? [])]);
  // 그것을 (이름을 바꿔서라도) **재수출하는 우리 모듈**도 같은 자리다.
  for (const source of program.getSourceFiles()) {
    if (!source.fileName.startsWith(`${srcRoot}/`)) {
      continue;
    }
    const moduleSymbol = checker.getSymbolAtLocation(source);
    if (moduleSymbol === undefined || exposing.has(moduleSymbol)) {
      continue;
    }
    for (const exported of checker.getExportsOfModule(moduleSymbol)) {
      const symbol = resolvedSymbol(checker, exported);
      if (symbol !== undefined && (outletSymbols.has(symbol) || receiverSymbols.has(symbol))) {
        exposing.add(moduleSymbol);
        break;
      }
    }
  }

  /**
   * **그 파일이 그 심볼에 닿을 수 있는가** — 닿으려면 노출 모듈 가운데 하나를 **수입**해야
   * 한다(이름 그대로든, 별칭이든, 네임스페이스든). 수입 문장만 보므로 싸고, 판정은 여전히
   * 심볼이 한다. 글자 훑기(`includes('Outlet')`)는 그 낱말이 남지 않는 꼴에 **건전하지
   * 않아서** 걷었다(v M).
   */
  const canReach = (source) =>
    source.statements.some((statement) => {
      if (!ts.isImportDeclaration(statement) || !ts.isStringLiteralLike(statement.moduleSpecifier)) {
        return false;
      }
      const target = resolvedSymbol(checker, checker.getSymbolAtLocation(statement.moduleSpecifier));
      return target !== undefined && exposing.has(target);
    });

  for (const source of program.getSourceFiles()) {
    if (!source.fileName.startsWith(`${srcRoot}/`) || /\.test\.tsx?$/u.test(source.fileName)) {
      continue;
    }
    if (!canReach(source)) {
      continue;
    }
    outletSensitivePaths.add(source.fileName);
    const where = relative(srcRoot, source.fileName);
    const visit = (node) => {
      if (ts.isIdentifier(node) || ts.isPropertyAccessExpression(node)) {
        const symbol = symbolOf(node);
        if (symbol !== undefined && receiverSymbols.has(symbol)) {
          sites.push(`${where}: ${ROUTER_CONTEXT_HOOK} 로 내려온 값을 받는다`);
        }
      }
      // `<Outlet …>` · `<RR.Outlet …>` — **태그 식 전체**의 심볼이 그 내보내기인가(v M).
      const opening = ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node) ? node : undefined;
      if (opening !== undefined) {
        const symbol = symbolOf(opening.tagName);
        if (symbol !== undefined && outletSymbols.has(symbol)) {
          for (const attribute of opening.attributes.properties) {
            const risky = ts.isJsxSpreadAttribute(attribute)
              ? '…전개'
              : attribute.initializer !== undefined && !ts.isStringLiteral(attribute.initializer)
                ? attribute.name.getText(source)
                : undefined;
            if (risky !== undefined) {
              sites.push(`${where}: Outlet 이 ${risky} 를 아래로 내린다`);
            }
          }
        }
      }
      // `useOutlet(…)` · `RR.useOutlet(…)` — **호출 대상 식 전체**의 심볼로 본다.
      if (ts.isCallExpression(node)) {
        const symbol = symbolOf(node.expression);
        if (symbol !== undefined && outletSymbols.has(symbol) && node.arguments.length > 0) {
          sites.push(`${where}: useOutlet 이 값을 아래로 내린다`);
        }
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }
  const answer = [...new Set(sites)].sort();
  outletCache.set(key, answer);
  return answer;
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
    const reach = reachFrom(modulePath, { program, readText, srcRoot });
    screens.push({
      slotId,
      module: relative(webRoot, modulePath),
      moduleCount: reach.modules.size,
      declarationCount: reach.declarations,
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
    outletContext: outletContextSites(program, webRoot, normalized),
  };
}

/** 사람이 읽는 위반 목록 — 비어 있으면 통과다. */
export function violationsOf(result) {
  const lines = [];
  for (const one of result.injected ?? []) {
    lines.push(one);
  }
  for (const one of result.outletContext ?? []) {
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
