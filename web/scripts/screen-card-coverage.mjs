// 화면 카드의 **어휘 게이트 모집단**을 소스에서 유도한다(M9/screen-display r1 v M-1).
//
// DOM 게이트(`src/screens/screenVocabulary.test.tsx`)는 카드를 마운트해 「그려진 글자에 금액
// 토큰·명령 토큰·ISO 원문이 0」을 잰다. 그 힘은 **모집단**에 달려 있다 — 손으로 적은 목록은 새
// 카드가 생길 때 조용히 뒤처지고, r1 에서 실제로 그렇게 됐다(화면 E 카드가 목록 밖이라 그 공백에
// 위반이 살아 있었다).
//
// 그래서 **의무 집합을 소스에서 센다**. 네 축이 전부 구성이다.
//  · **무엇이 어휘인가** — 씨앗 목록(`screen-vocabulary-registry.mjs`)에서 출발해 표시층
//    `src/display/**` 안에서 **파일 사이 전이 폐포**를 취한다. 래퍼를 어느 표시 모듈에 두든,
//    새 모듈을 만들든 그래프에 든다(노드 집합을 디렉터리에서 유도하므로).
//  · **무엇이 카드인가** — 타입이다. 합성 앵커의 **JSX 식 자신**에서 얻은 심볼과 반환 타입의
//    갈래를 맞댄다(타입 이름도 선언 경로도 쓰지 않는다). 훅·함수 값 표·컬렉션은 카드가 아니고,
//    그래서 씨앗이 늘어도 **마운트할 수 없는 것**이 의무로 끌려오지 않는다.
//  · **어디를 보는가** — `src/**` 전수다. 카드를 공용 디렉터리에 두는 것으로 의무를 피하지
//    못한다.
//  · **목록이 뒤처지는 축** — 다섯 버킷(`vocabularyRegistryGaps`)이 각각 구조로 센다.
//
// **화면(`*Screen`)은 의무에서 뺀다**: 질의 목이 필요해 이 게이트가 마운트하지 못하고, 각자의
// 화면 test 가 같은 주장을 든다. 그 제외는 **이름 규약 하나에 기대지 않는다** — 뺀 이름마다
// 짝이 되는 test 파일이 실제로 있는지 함께 센다.
import { readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

import ts from 'typescript';

import {
  INTERNAL_ASSEMBLER,
  OTHER_AXIS,
  VOCABULARY_DECLARATIONS,
  VOCABULARY_MODULE_EXCLUSIONS,
} from './screen-vocabulary-registry.mjs';

export { INTERNAL_ASSEMBLER, OTHER_AXIS, VOCABULARY_DECLARATIONS, VOCABULARY_MODULE_EXCLUSIONS };

const SRC_MARKER = '/src/';

/**
 * **카드 술어의 앵커 원문.** JSX 식 하나와 `ReactNode` 하나를 둔다 — 컴파일러가 그 식에 **직접**
 * 주는 타입에서 앵커 심볼을 얻으려는 것이다. 타입 **이름**이나 선언 **경로**를 술어로 쓰면 그
 * 글자 하나로 게이트가 열린다(이 저장소의 「술어는 구조로」 규율).
 *
 * 이 파일은 **오버레이에만** 있다(디스크에 쓰지 않는다) — 출하 트리를 건드리지 않는 자리다.
 * 호출자가 같은 경로를 오버레이로 주면 **호출자가 이긴다**(앵커를 비우는 대조가 그 길로 선다).
 */
const CARD_ANCHOR_SOURCE = `import type { ReactNode } from 'react';
export const jsxAnchor = <span />;
export declare const nodeAnchor: ReactNode;
`;

const anchorPathOf = (webRoot) => resolve(webRoot, 'src', '__card_anchor__.tsx');

/** 프로그램 하나에 한 번만 세우는 것들 — 타입 검사기를 두 번 걷지 않는다. */
const anchorCache = new WeakMap();
const reachCache = new WeakMap();

/**
 * 화면 코드의 TS 프로그램 — **실제 `tsconfig.json`** 으로 연다(사본이 아니다). 타입 검사기가
 * 있어야 호출을 해소할 수 있고, 그것이 이 판의 요점이다.
 *
 * `overlay` 는 **합성 원문**을 실제 트리 위에 얹는다(경로 → 원문). 양성·음성 대조가 진짜 심볼
 * 그래프를 지나게 하려는 것이다 — 합성 파일이 실제 표시 모듈을 import 해 해소된다.
 */
export function screenProgram(webRoot, overlay = new Map()) {
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
  // 앵커를 **먼저** 둔다 — 같은 경로를 호출자가 주면 그것이 이긴다.
  const merged = new Map([[anchorPathOf(webRoot), CARD_ANCHOR_SOURCE], ...overlay]);
  const host = ts.createCompilerHost(parsed.options, true);
  const baseGetSourceFile = host.getSourceFile.bind(host);
  const baseFileExists = host.fileExists.bind(host);
  const baseReadFile = host.readFile.bind(host);
  host.getSourceFile = (fileName, languageVersion, onError, shouldCreate) => {
    const text = merged.get(resolve(fileName));
    return text === undefined
      ? baseGetSourceFile(fileName, languageVersion, onError, shouldCreate)
      : ts.createSourceFile(fileName, text, languageVersion, true, ts.ScriptKind.TSX);
  };
  host.fileExists = (fileName) => merged.has(resolve(fileName)) || baseFileExists(fileName);
  host.readFile = (fileName) => merged.get(resolve(fileName)) ?? baseReadFile(fileName);
  // **디렉터리도 얹어야 모듈 해소가 선다**(실측): 합성 파일끼리 상대 import 를 하면 해소가
  // 먼저 담는 디렉터리의 존재를 묻고, 그 디렉터리는 디스크에 없다 — 그러면 import 가 TS2307 로
  // 떨어져 「재수출 경유」 대조가 **잡는 대신 조용히 빈 집합**을 낸다.
  const overlayDirs = new Set([...merged.keys()].map((one) => dirname(one)));
  const baseDirectoryExists = host.directoryExists?.bind(host);
  if (baseDirectoryExists !== undefined) {
    host.directoryExists = (name) => overlayDirs.has(resolve(name)) || baseDirectoryExists(name);
  }
  return ts.createProgram([...parsed.fileNames, ...merged.keys()], parsed.options, host);
}

const typeParts = (type) => (type.isUnion() || type.isIntersection() ? type.types : [type]);

function aliasResolved(checker, symbol) {
  return symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0
    ? checker.getAliasedSymbol(symbol)
    : symbol;
}

function exportsOf(checker, source) {
  const moduleSymbol = checker.getSymbolAtLocation(source);
  return moduleSymbol === undefined ? [] : checker.getExportsOfModule(moduleSymbol);
}

const typeOfExport = (checker, source, exported) =>
  checker.getTypeOfSymbolAtLocation(aliasResolved(checker, exported), source);

/**
 * **카드의 앵커 심볼** — JSX 원소의 꼴(`type` 과 `props` 를 함께 든 갈래)을 가진 것들.
 *
 * 비면 카드 집합이 비고 **모든 등식이 공허해진다**. 그래서 조용히 빈 집합을 내지 않고 던진다.
 */
export function cardAnchorsOf(program, webRoot) {
  const cached = anchorCache.get(program);
  if (cached !== undefined) {
    return cached;
  }
  const anchorPath = anchorPathOf(webRoot);
  const source = program.getSourceFile(anchorPath);
  if (source === undefined) {
    throw new Error(`카드 앵커 파일이 프로그램에 없다: ${anchorPath}`);
  }
  const checker = program.getTypeChecker();
  const anchors = new Set();
  for (const exported of exportsOf(checker, source)) {
    for (const part of typeParts(typeOfExport(checker, source, exported))) {
      const symbol = part.getSymbol();
      if (symbol !== undefined && part.getProperty('props') !== undefined && part.getProperty('type') !== undefined) {
        anchors.add(symbol);
      }
    }
  }
  if (anchors.size === 0) {
    throw new Error(`카드 앵커가 비었다 — 카드 집합이 비어 모든 등식이 공허해진다: ${anchorPath}`);
  }
  anchorCache.set(program, anchors);
  return anchors;
}

/**
 * 이 타입이 앵커에 **닿는가** — 같은 심볼이거나, **명목 상속 사슬**로 앵커를 기반으로 둔다.
 *
 * 동일성만 보면 안 되는 사유(r1 v H-1): `createElement` 의 반환은 `DetailedReactHTMLElement`·
 * `FunctionComponentElement` 같은 **하위 인터페이스**다. JSX 를 못 쓰는 `.ts` 카드는 그 꼴밖에
 * 쓸 수 없고(그 축은 PR #132 v M-3 이 모집단에 넣었다), 동일성 술어는 그 카드를 의무에서
 * **잃는다** — base 가 잡던 것을 잃는 회귀다.
 *
 * **구조적 대입 가능성은 쓰지 않는다.** 명목 상속만 따라가므로 꼴만 흉낸 반환
 * (`{ type, props, key }`)은 계속 거절된다 — 그 선이 위협 모델 경계다.
 */
function reachesAnchor(checker, anchors, type) {
  const seen = new Set();
  // **타입이 아니라 심볼의 사슬**을 걷는다(실측): `createElement` 의 반환은 제네릭 **타입 참조**라
  // `isClassOrInterface()` 가 거짓이고 `getBaseTypes()` 가 서지 않는다. 선언의 상속 절을 보면
  // 제네릭 인자와 무관하게 사슬이 선다.
  const walk = (symbol) => {
    const resolved = aliasResolved(checker, symbol);
    if (resolved === undefined || seen.has(resolved)) {
      return false;
    }
    seen.add(resolved);
    if (anchors.has(resolved)) {
      return true;
    }
    for (const declaration of resolved.declarations ?? []) {
      if (!ts.isInterfaceDeclaration(declaration) && !ts.isClassDeclaration(declaration)) {
        continue;
      }
      for (const clause of declaration.heritageClauses ?? []) {
        if (clause.types.some((one) => walk(checker.getSymbolAtLocation(one.expression)))) {
          return true;
        }
      }
    }
    return false;
  };
  return typeParts(type).some((part) => walk(part.getSymbol()));
}

/**
 * 이 타입이 **마운트할 수 있는 카드**인가 — 반환 타입의 어느 갈래가 앵커에 닿는가.
 *
 * 클래스 컴포넌트도 **이름 없이** 본다: construct 시그니처의 인스턴스에 앵커를 반환하는 멤버가
 * 있는가로 판정한다. 멤버의 이름을 술어로 쓰면 그 글자가 게이트의 구멍이 된다.
 */
function isCardType(checker, anchors, source, type) {
  const returnsAnchor = (one) =>
    typeParts(one)
      .flatMap((part) => part.getCallSignatures())
      .some((signature) => reachesAnchor(checker, anchors, checker.getReturnTypeOfSignature(signature)));
  if (returnsAnchor(type)) {
    return true;
  }
  for (const signature of typeParts(type).flatMap((one) => one.getConstructSignatures())) {
    for (const property of checker.getReturnTypeOfSignature(signature).getProperties()) {
      if (returnsAnchor(checker.getTypeOfSymbolAtLocation(property, source))) {
        return true;
      }
    }
  }
  return false;
}

/**
 * 「부를 수 있는 것」 — 합집합 전개 ∪ **자기/사상 속성 한 홉**.
 *
 * 경계가 **꼴 열거가 아니라 속성의 출처**인 사유(M12/12B 실측): 속성을 그냥 재귀하면 원시 타입의
 * prototype 메서드가 들어와 표시층의 「부를 수 있는 것」이 41 → 113 이 되고 데이터 표 전부가
 * 장부에 들어온다. 배열·튜플만 빼도 `ReadonlySet` 이 표로 세어진다. **자기 모듈에서 선언된
 * 속성**과 **선언이 없는 속성**(사상 타입이 만든 것)만 보면 그 둘이 함께 사라지고, 오늘 더해지는
 * 것은 진짜 함수 값 표 하나뿐이다.
 *
 * 속성의 타입을 **모듈 소스**에서 묻는 것도 그 때문이다 — 사상 타입의 속성 심볼은 선언이 없어
 * 선언 자리에서 물으면 조용히 건너뛰어진다(앞 판이 함수 값 표를 못 본 첫째 사유).
 */
function isCallableExport(checker, source, type) {
  const callable = (one) => typeParts(one).some((part) => part.getCallSignatures().length > 0);
  if (callable(type)) {
    return true;
  }
  for (const part of typeParts(type)) {
    if ((part.flags & ts.TypeFlags.Object) === 0) {
      continue;
    }
    for (const property of part.getProperties()) {
      const declaration = property.valueDeclaration ?? property.declarations?.[0];
      if (declaration !== undefined && declaration.getSourceFile() !== source) {
        continue;
      }
      if (callable(checker.getTypeOfSymbolAtLocation(property, source))) {
        return true;
      }
    }
  }
  return false;
}

/**
 * 그 선언의 **자리** — `<src 기준 상대경로>#<이름>`.
 *
 * `export default <식>` 은 이름이 없지만 **파일당 하나**라 자리를 갖는다(r1 F2). 앞 판은 그
 * 선언이 `ExportAssignment` 여서 자리를 내지 못했고, 그래서 React 의 표준 꼴
 * (`export default memo(Card)`)이 의무를 하나도 내지 않았다. `export =` 은 모듈 꼴이 달라 대상이
 * 아니다.
 */
function declarationKeyOf(declaration) {
  if (ts.isExportAssignment(declaration)) {
    return declaration.isExportEquals === true ? undefined : relativeKeyOf(declaration, 'default');
  }
  // `export default function (…) {}` · `export default class {}` — 이름이 없어도 **기본
  // 내보내기는 파일당 하나**라 같은 자리를 쓴다(r1 cr F-2).
  if (
    declaration.name === undefined &&
    (ts.getCombinedModifierFlags(declaration) & ts.ModifierFlags.Default) !== 0
  ) {
    return relativeKeyOf(declaration, 'default');
  }
  const named =
    (ts.isArrowFunction(declaration) || ts.isFunctionExpression(declaration)) &&
    declaration.parent !== undefined &&
    ts.isVariableDeclaration(declaration.parent)
      ? declaration.parent
      : declaration;
  const name = named.name !== undefined && ts.isIdentifier(named.name) ? named.name.text : undefined;
  return name === undefined ? undefined : relativeKeyOf(declaration, name);
}

function relativeKeyOf(node, name) {
  const fileName = node.getSourceFile().fileName;
  const marker = fileName.lastIndexOf(SRC_MARKER);
  return marker < 0 ? undefined : `${fileName.slice(marker + SRC_MARKER.length)}#${name}`;
}

/**
 * 이 노드가 **참조**로 읽히는 자리면 심볼을 물을 노드, 아니면 `undefined`.
 *
 * 원소 접근은 **리터럴 키일 때만** 참조다(r1 F3). 접근식 자체에서는 심볼이 나오지 않고
 * (`getSymbolAtLocation` 이 `undefined` — 실측) 그 리터럴 자리에서 속성 심볼이 나온다. 계산된
 * 키는 해소되지 않으며 그것은 위협 모델 경계 밖이다(이름을 런타임에 조립하는 저자).
 */
function referenceNodeOf(node) {
  if (ts.isIdentifier(node) || ts.isPropertyAccessExpression(node)) {
    return node;
  }
  if (ts.isElementAccessExpression(node) && ts.isStringLiteralLike(node.argumentExpression)) {
    return node.argumentExpression;
  }
  return undefined;
}

/**
 * 이 노드가 `wanted` 의 선언에 **닿는가** — 두 길을 함께 본다.
 *
 * ⓐ 호출의 **해소된 시그니처**: 별칭 변수·네임스페이스 접근·재수출·지역 변수 경유를 전부 지나
 * 원 선언을 낸다. ⓑ 맨 **참조**: `ats.map(instantDisplayOf)` 처럼 **부르지 않고 넘기는** 자리는
 * 해소할 호출이 없으므로 ⓐ 가 보지 못한다.
 *
 * import 선언은 걷지 않는다 — 걷는 시작점이 언제나 **최상위 선언 하나**이고 import 는 그 안에
 * 없다. 그래서 「들여온 것」이 아니라 「쓰는 것」만 센다.
 */
function touchesAnyOf(checker, node, wanted) {
  let found = false;
  const hits = (declarations) =>
    declarations.some((one) => {
      const key = declarationKeyOf(one);
      return key !== undefined && wanted.has(key);
    });
  const walk = (child) => {
    if (found) {
      return;
    }
    if (ts.isCallExpression(child)) {
      const signature = checker.getResolvedSignature(child);
      if (signature?.declaration !== undefined && hits([signature.declaration])) {
        found = true;
        return;
      }
    }
    const reference = referenceNodeOf(child);
    if (reference !== undefined) {
      if (hits(aliasResolved(checker, checker.getSymbolAtLocation(reference))?.declarations ?? [])) {
        found = true;
        return;
      }
    }
    ts.forEachChild(child, walk);
  };
  walk(node);
  return found;
}

/**
 * 이 파일의 **최상위 선언** — 함수·클래스·변수·기본 내보내기·구조분해 바인딩. 자리를 키로 든다.
 *
 * 구조분해 바인딩이 노드인 사유(r1 F3): `const { instantDisplayOf: fmt } = display` 뒤에 `fmt` 를
 * **넘기면** 그 참조가 닿는 선언은 바인딩이고, 바인딩이 그래프에 없으면 거기서 끊겼다. 바인딩
 * 자신은 어휘를 직접 쥐므로 노드가 되는 순간 닿기가 선다.
 */
function topLevelDeclarationsOf(source) {
  const byKey = new Map();
  const register = (declaration) => {
    const key = declarationKeyOf(declaration);
    if (key !== undefined) {
      byKey.set(key, declaration);
    }
  };
  const registerBindings = (name) => {
    if (ts.isIdentifier(name)) {
      return;
    }
    for (const element of name.elements) {
      if (ts.isBindingElement(element)) {
        register(element);
        registerBindings(element.name);
      }
    }
  };
  ts.forEachChild(source, (node) => {
    if (ts.isFunctionDeclaration(node) || ts.isClassDeclaration(node)) {
      // 이름이 없어도 **기본 내보내기**면 자리가 나온다 — [declarationKeyOf] 가 판정한다.
      register(node);
      return;
    }
    if (ts.isExportAssignment(node)) {
      register(node);
      return;
    }
    if (ts.isVariableStatement(node)) {
      for (const one of node.declarationList.declarations) {
        if (ts.isIdentifier(one.name)) {
          register(one);
        } else {
          registerBindings(one.name);
        }
      }
    }
  });
  return byKey;
}

/**
 * 이 선언이 가리키는 **그래프의 다른 노드** — 변이다(JSX 태그도 식별자다).
 *
 * **그래프의 범위는 `byKey` 하나가 정한다**: 자리(`<상대경로>#<이름>`)가 파일을 담으므로, 노드
 * 집합이 한 파일이면 변도 그 파일 안이고(카드 파일의 지역 그래프), 표시층 전체면 변도 파일
 * 사이다(표시층 그래프). 앞 판은 여기에 「같은 파일만」 조건을 하나 더 두었는데 변이로 재니
 * **거동이 0 이었다**(그 조건이 `byKey` 의 범위와 겹쳤다) — 죽은 분기라 지우고, 지역 그래프가
 * 파일에 갇힌다는 주장은 test 의 음성 대조(조상 카드)가 든다.
 */
function edgesOf(checker, declaration, byKey, selfKey) {
  const edges = new Set();
  const add = (candidate) => {
    const key = declarationKeyOf(candidate);
    if (key !== undefined && key !== selfKey && byKey.has(key)) {
      edges.add(key);
    }
  };
  const walk = (child) => {
    const reference = referenceNodeOf(child);
    if (reference !== undefined) {
      for (const one of aliasResolved(checker, checker.getSymbolAtLocation(reference))?.declarations ?? []) {
        add(one);
      }
    }
    if (ts.isCallExpression(child)) {
      const signature = checker.getResolvedSignature(child);
      if (signature?.declaration !== undefined) {
        add(signature.declaration);
      }
    }
    ts.forEachChild(child, walk);
  };
  walk(declaration);
  return edges;
}

/**
 * 변을 따라 **고정점**까지. 깊이 상수를 두지 않으므로 「왜 1 인가」라는 매직 넘버가 생기지
 * 않고, 상호 재귀가 있어도 끝난다(이미 든 것은 다시 세지 않는다).
 */
function fixpointOf(edges, seeded) {
  const reaching = new Set(seeded);
  for (let grew = true; grew; ) {
    grew = false;
    for (const [key, next] of edges) {
      if (!reaching.has(key) && [...next].some((one) => reaching.has(one))) {
        reaching.add(key);
        grew = true;
      }
    }
  }
  return reaching;
}

/** 프로그램의 소스 파일 가운데 그 디렉터리 아래 — test 파일과 카드 앵커는 뺀다. */
function sourceFilesUnder(program, webRoot, dir) {
  const anchorPath = anchorPathOf(webRoot);
  return program
    .getSourceFiles()
    .filter(
      (one) =>
        one.fileName.startsWith(`${dir}/`) &&
        one.fileName !== anchorPath &&
        !one.fileName.includes('/node_modules/') &&
        !/\.test\.tsx?$/u.test(one.fileName),
    );
}

const relativeOf = (source, dir) => source.fileName.slice(dir.length + 1);

/**
 * 표시층 안에서 **어휘에 닿는 선언 전수**(M12/12B — `OPEN-M11-SCREEN-COVERAGE-CROSS-FILE-VOCAB`
 * ⓐⓑ).
 *
 * 노드 집합을 `src/display/**` **디렉터리**에서 유도한다: 래퍼를 어느 모듈에 두든, 새 모듈을
 * 만들든 그래프에 든다. 앞 판은 도달을 파일 **안**에서만 세었고 등식의 모집단을 씨앗 목록에서
 * 유도했으므로 그 둘이 함께 구멍이었다.
 *
 * 씨앗이 그래프에 없으면 **던진다** — 씨앗의 이름이나 자리가 바뀌면 폐포가 조용히 좁아진다.
 */
export function displayVocabularyReach(program, webRoot) {
  const cached = reachCache.get(program);
  if (cached !== undefined) {
    return cached;
  }
  const checker = program.getTypeChecker();
  const byKey = new Map();
  for (const source of sourceFilesUnder(program, webRoot, resolve(webRoot, 'src', 'display'))) {
    for (const [key, declaration] of topLevelDeclarationsOf(source)) {
      byKey.set(key, declaration);
    }
  }
  const missing = VOCABULARY_DECLARATIONS.filter((one) => !byKey.has(one));
  if (missing.length > 0) {
    throw new Error(`어휘 씨앗이 표시층 그래프에 없다: ${missing.join(', ')}`);
  }
  const edges = new Map();
  for (const [key, declaration] of byKey) {
    edges.set(key, edgesOf(checker, declaration, byKey, key));
  }
  const reach = fixpointOf(edges, VOCABULARY_DECLARATIONS);
  reachCache.set(program, reach);
  return reach;
}

/**
 * 의무로 나는 **이름**. 보통은 내보낸 이름 그대로인데, **기본 내보내기만 파일로 한정한다**
 * (r1 v R2 · cr N-2).
 *
 * 사유: 의무 집합과 화면 바구니는 **이름으로** 중복을 없앤다. 기본 내보내기의 이름은 파일마다
 * `default` 로 같으므로, 그대로 두면 첫 카드는 게이트를 붉혀 결정을 강제하지만 **둘째부터는
 * 집합에서 접혀 조용해진다**(실측 — 두 파일에 심어도 의무가 하나였다). 자리는 이미 유일하므로
 * 그것을 이름으로 쓴다.
 */
function obligationNameOf(exported, declaration) {
  return exported.getName() === 'default'
    ? (relativeKeyOf(declaration, 'default') ?? 'default')
    : exported.getName();
}

/**
 * 한 파일에서 **표시 어휘를 그리는 카드**의 내보낸 이름.
 *
 * 세 층이 겹친다 — 표시층 **파일 사이** 폐포가 「무엇이 어휘인가」를 넓히고, 이 파일 **안**의
 * 지역 그래프가 비공개 하위 컴포넌트를 지나게 하고, **카드 술어**가 마운트할 수 있는 것만
 * 남긴다. 지역 그래프를 파일 안으로 **가두는** 것이 중요하다: 카드 사이로 넓히면 그것을 그리는
 * 조상 카드가 뿌리까지 전부 의무가 된다.
 *
 * 내보내기를 **심볼로** 받는다(`getExportsOfModule`) — `memo`·`forwardRef` 처럼 초기값이 호출인
 * 꼴, 개명 재수출, 클래스 컴포넌트가 꼴을 묻지 않고 함께 든다.
 */
export function vocabularyComponentsOf(program, source, webRoot) {
  const checker = program.getTypeChecker();
  const anchors = cardAnchorsOf(program, webRoot);
  const reach = displayVocabularyReach(program, webRoot);
  const byKey = topLevelDeclarationsOf(source);
  const edges = new Map();
  const seeded = [];
  for (const [key, declaration] of byKey) {
    edges.set(key, edgesOf(checker, declaration, byKey, key));
    if (touchesAnyOf(checker, declaration, reach)) {
      seeded.push(key);
    }
  }
  const reaching = fixpointOf(edges, seeded);
  const names = new Set();
  for (const exported of exportsOf(checker, source)) {
    if (!isCardType(checker, anchors, source, typeOfExport(checker, source, exported))) {
      continue;
    }
    for (const declaration of aliasResolved(checker, exported).declarations ?? []) {
      const key = declaration.getSourceFile() === source ? declarationKeyOf(declaration) : undefined;
      if (key !== undefined && reaching.has(key)) {
        names.add(obligationNameOf(exported, declaration));
      }
    }
  }
  return [...names];
}

/**
 * 장부의 **구멍 다섯**.
 *
 * `unclassified` 표시층이 내보내는 부를 수 있는 것인데 씨앗도 분류도 아니고 **폐포도 닿지 않는**
 * 것 — 새 어휘 함수가 조용히 의무 밖에 서는 자리다(폐포가 닿는 것은 구조가 설명하므로 행이
 * 필요 없다). `stale` 분류 표에 적혀 있는데 이제 그 모집단의 내보낸 것이 아닌 것. `leaked`
 * [INTERNAL_ASSEMBLER] 로 분류한 것을 표시층 **밖**이 직접 참조하는 자리 — 「표시층 속이라 화면이
 * 안 쓴다」는 사유가 거짓이 된 자리다. `contradicted` 분류한 키가 **폐포에 닿는** 자리 — 「어휘가
 * 아니다」가 구조적으로 거짓이 된 자리이고, 이 의무는 **종류를 가리지 않는다**. `unkeyed` 해소된
 * 선언에서 **자리가 나오지 않는** 내보내기 — 키가 없어 모든 등식에서 조용히 빠지는 자리다.
 *
 * 훑기를 **프로그램에서** 받는다(파일시스템이 아니라) — 합성 원문으로 양성 대조를 세울 수
 * 있어야 하고, 둘이 어긋나지 않는 것은 [collectScreenVocabulary] 가 던져서 지킨다.
 */
export function vocabularyRegistryGaps(program, webRoot) {
  const checker = program.getTypeChecker();
  const reach = displayVocabularyReach(program, webRoot);
  const srcDir = resolve(webRoot, 'src');
  const displayDir = resolve(srcDir, 'display');
  const seeds = new Set(VOCABULARY_DECLARATIONS);
  const classified = new Set(VOCABULARY_MODULE_EXCLUSIONS.map((one) => one.key));
  const population = new Set();
  const unkeyed = [];
  for (const source of sourceFilesUnder(program, webRoot, displayDir)) {
    for (const exported of exportsOf(checker, source)) {
      if (!isCallableExport(checker, source, typeOfExport(checker, source, exported))) {
        continue;
      }
      const declaration = (aliasResolved(checker, exported).declarations ?? [])[0];
      const key = declaration === undefined ? undefined : declarationKeyOf(declaration);
      if (key === undefined) {
        unkeyed.push(`${relativeOf(source, srcDir)}#${exported.getName()}`);
      } else {
        population.add(key);
      }
    }
  }
  const assemblers = new Set(
    VOCABULARY_MODULE_EXCLUSIONS.filter((one) => one.kind === INTERNAL_ASSEMBLER).map((one) => one.key),
  );
  const leaked = [];
  for (const source of sourceFilesUnder(program, webRoot, srcDir)) {
    if (source.fileName.startsWith(`${displayDir}/`)) {
      continue;
    }
    for (const declaration of topLevelDeclarationsOf(source).values()) {
      if (touchesAnyOf(checker, declaration, assemblers)) {
        leaked.push(relativeOf(source, srcDir));
      }
    }
  }
  return {
    unclassified: [...population].filter((one) => !seeds.has(one) && !classified.has(one) && !reach.has(one)).sort(),
    stale: [...classified].filter((one) => !population.has(one)).sort(),
    leaked: [...new Set(leaked)].sort(),
    contradicted: [...classified].filter((one) => reach.has(one)).sort(),
    unkeyed: unkeyed.sort(),
  };
}

/** 게이트가 마운트한다고 **선언한** 목록을 AST 로 읽는다(문자열 grep 은 주석 한 줄로 열린다). */
export function mountedCardsOf(text, path = 'screenVocabulary.test.tsx') {
  const source = ts.createSourceFile(path, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
  let names = null;
  ts.forEachChild(source, (node) => {
    if (!ts.isVariableStatement(node) || (ts.getCombinedModifierFlags(node) & ts.ModifierFlags.Export) === 0) {
      return;
    }
    for (const declaration of node.declarationList.declarations) {
      if (
        ts.isIdentifier(declaration.name) &&
        declaration.name.text === 'GATE_MOUNTED_CARDS' &&
        declaration.initializer !== undefined &&
        ts.isArrayLiteralExpression(declaration.initializer)
      ) {
        names = declaration.initializer.elements.map((one) =>
          ts.isStringLiteral(one) ? one.text : `<리터럴이 아님: ${one.getText()}>`,
        );
      }
    }
  });
  return names;
}

/**
 * [VOCABULARY_DECLARATIONS] 가운데 **그 자리에 실제로 없는** 항목. 목록이 썩는 축을 잰다 —
 * 어휘 함수의 이름을 바꾸거나 모듈을 옮기면 앞 판에서는 의무 집합이 **조용히 줄었다**.
 */
export function missingVocabularyDeclarations(program, webRoot) {
  const checker = program.getTypeChecker();
  const missing = [];
  for (const key of VOCABULARY_DECLARATIONS) {
    const [relative, name] = key.split('#');
    const source = program.getSourceFile(resolve(webRoot, 'src', relative));
    const moduleSymbol = source === undefined ? undefined : checker.getSymbolAtLocation(source);
    const exported =
      moduleSymbol === undefined ? [] : checker.getExportsOfModule(moduleSymbol).map((one) => one.getName());
    if (!exported.includes(name)) {
      missing.push(key);
    }
  }
  return missing;
}

/**
 * 소스 트리의 파일 — **재귀**이고 `.ts` 도 담는다(PR #132 v M-3). 앞 판은 한 겹 평면 훑기에
 * `.tsx` 만이었고, 그래서 하위 디렉터리에 둔 카드와 JSX 를 쓰지 않는 `.ts` 카드가 경계 문장과
 * 달리 **모집단 밖**이었다.
 *
 * 반환은 `dir` 기준 상대 경로다 — 하위 디렉터리의 파일도 자기 자리를 들고 다녀야 짝이 되는
 * test 파일을 **같은 디렉터리에서** 찾을 수 있다.
 */
export function screenFilesIn(dir) {
  const found = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      for (const nested of screenFilesIn(resolve(dir, entry.name))) {
        found.push(join(entry.name, nested));
      }
      continue;
    }
    if (!/\.tsx?$/u.test(entry.name) || /\.test\.tsx?$/u.test(entry.name)) {
      continue;
    }
    found.push(entry.name);
  }
  return found.sort();
}

/**
 * 소스 트리를 훑어 의무 집합과 화면 집합을 가른다.
 *
 * 모집단은 `src/**` 전수다(M12/12B — `OPEN-M11-SCREEN-COVERAGE-POPULATION-DIR`). 앞 판은
 * `src/screens/**` 였고, 그래서 어휘를 그리는 카드를 공용 디렉터리에 두면 의무를 내지 않았다.
 * 「카드인가」는 디렉터리가 아니라 **타입**이 판정하므로, 모집단을 넓혀도 훅·표·설정이 의무로
 * 끌려오지 않는다(그것이 M9 의 「5 → 13」을 되풀이하지 않는 이유다).
 */
export function collectScreenVocabulary(webRoot, overlay = new Map()) {
  const program = screenProgram(webRoot, overlay);
  const srcDir = resolve(webRoot, 'src');
  const obligations = [];
  const screens = [];
  const files = screenFilesIn(srcDir);
  for (const file of files) {
    const source = program.getSourceFile(resolve(srcDir, file));
    if (source === undefined) {
      // 프로그램이 소스 파일을 안 담으면 이 훑기는 **미측정**이다 — 빈 집합으로 초록이 되는
      // 길을 두지 않는다(tsconfig 의 `include` 가 바뀌는 날이 그 자리다).
      throw new Error(`TS 프로그램에 소스 파일이 없다: ${file}`);
    }
    for (const name of vocabularyComponentsOf(program, source, webRoot)) {
      (name.endsWith('Screen') ? screens : obligations).push({ name, file });
    }
  }
  return { program, obligations, screens, files };
}
