// 화면 카드의 **어휘 게이트 모집단**을 소스에서 유도한다(M9/screen-display r1 v M-1).
//
// DOM 게이트(`src/screens/screenVocabulary.test.tsx`)는 카드를 마운트해 「그려진 글자에 금액
// 토큰·명령 토큰·ISO 원문이 0」을 잰다. 그 힘은 **모집단**에 달려 있다 — 손으로 적은 목록은 새
// 카드가 생길 때 조용히 뒤처지고, r1 에서 실제로 그렇게 됐다(화면 E 카드가 목록 밖이라 그 공백에
// 위반이 살아 있었다).
//
// 그래서 **의무 집합을 소스에서 센다**: `src/screens/**` 에서 내보낸 컴포넌트 가운데 표시 어휘
// 함수를 쓰는 것이 의무다. 그 집합과 게이트의 마운트 목록에 **등식**을 건다 — 새 카드는 등식을
// 깨고, 목록에서 카드를 빼는 것도 등식을 깬다.
//
// **「쓴다」의 판정은 TS 심볼 그래프다**(M9/web-followups — `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH`).
// 앞 판은 **호출 식별자의 이름**을 보았고, 그래서 어휘 함수를 별칭으로 부르거나(`as fmt`) 다른
// 모듈로 재수출하거나 네임스페이스로 접근하면(`display.instantDisplayOf(…)`) 그 컴포넌트가 의무
// 집합에 들지 않았다(실측 — 전부 초록). 이제 호출을 **해소해** 원 선언의 자리(모듈 + 내보낸
// 이름)를 보므로 호출 지점의 글자가 무엇이든 같은 선언에 닿는다.
//
// **화면(`*Screen`)은 의무에서 뺀다**: 질의 목이 필요해 이 게이트가 마운트하지 못하고, 각자의
// 화면 test 가 같은 주장을 든다. 그 제외는 **이름 규약 하나에 기대지 않는다** — 뺀 이름마다
// 짝이 되는 test 파일이 실제로 있는지 함께 센다.
import { readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

import ts from 'typescript';

/**
 * 표시 어휘를 화면에 내는 함수들 — **원 선언의 자리**로 적는다(`<src 기준 상대경로>#<이름>`).
 * 이 가운데 하나라도 부르면 그 컴포넌트는 금액·시각 문면을 그린다는 뜻이고, 따라서 「원문 0」
 * 단언의 대상이다.
 *
 * 호출 지점의 이름이 아니라 **선언**을 적는 것이 앞 판과의 차이다: 선언은 그 함수의 유일한
 * 신원이라 별칭·재수출·네임스페이스 접근으로 바뀌지 않는다. 목록이 썩는 축(이름을 바꿨는데
 * 여기가 옛 이름)은 [missingVocabularyDeclarations] 가 잰다.
 *
 * **남는 구멍**: 표시 모듈에 **새 어휘 함수**를 더해 그것만 쓰면 이 목록 밖이다. 「표시 모듈의
 * 내보낸 함수 전부」로 넓히는 길은 실측하면 의무 집합이 5 → 13 으로 늘고 그 가운데 질의 목이
 * 필요한 패널·훅이 섞인다 — 알려진 제한에 등재한다.
 */
export const VOCABULARY_DECLARATIONS = [
  'display/moneyDisplay.ts#amountBasisDisplayOf',
  'display/moneyDisplay.ts#amountProvenanceDisplayOf',
  'display/moneyDisplay.ts#amountQualifierTextOf',
  'display/moneyDisplay.ts#currencyDisplayOf',
  'display/moneyDisplay.ts#vatTreatmentDisplayOf',
  'display/noticeDisplay.ts#amountDisplayOf',
  'display/timeDisplay.ts#instantDisplayOf',
];

const SRC_MARKER = '/src/';

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
  const host = ts.createCompilerHost(parsed.options, true);
  const baseGetSourceFile = host.getSourceFile.bind(host);
  const baseFileExists = host.fileExists.bind(host);
  const baseReadFile = host.readFile.bind(host);
  host.getSourceFile = (fileName, languageVersion, onError, shouldCreate) => {
    const text = overlay.get(resolve(fileName));
    return text === undefined
      ? baseGetSourceFile(fileName, languageVersion, onError, shouldCreate)
      : ts.createSourceFile(fileName, text, languageVersion, true, ts.ScriptKind.TSX);
  };
  host.fileExists = (fileName) => overlay.has(resolve(fileName)) || baseFileExists(fileName);
  host.readFile = (fileName) => overlay.get(resolve(fileName)) ?? baseReadFile(fileName);
  // **디렉터리도 얹어야 모듈 해소가 선다**(실측): 합성 파일끼리 상대 import 를 하면 해소가
  // 먼저 담는 디렉터리의 존재를 묻고, 그 디렉터리는 디스크에 없다 — 그러면 import 가 TS2307 로
  // 떨어져 「재수출 경유」 대조가 **잡는 대신 조용히 빈 집합**을 낸다.
  const overlayDirs = new Set([...overlay.keys()].map((one) => dirname(one)));
  const baseDirectoryExists = host.directoryExists?.bind(host);
  if (baseDirectoryExists !== undefined) {
    host.directoryExists = (name) => overlayDirs.has(resolve(name)) || baseDirectoryExists(name);
  }
  return ts.createProgram([...parsed.fileNames, ...overlay.keys()], parsed.options, host);
}

function isExported(node) {
  return (ts.getCombinedModifierFlags(node) & ts.ModifierFlags.Export) !== 0;
}

/** 그 선언의 **자리** — `<src 기준 상대경로>#<이름>`. 이름이 없으면 어휘일 수 없다. */
function declarationKeyOf(declaration) {
  const named =
    (ts.isArrowFunction(declaration) || ts.isFunctionExpression(declaration)) &&
    declaration.parent !== undefined &&
    ts.isVariableDeclaration(declaration.parent)
      ? declaration.parent
      : declaration;
  const name = named.name !== undefined && ts.isIdentifier(named.name) ? named.name.text : undefined;
  if (name === undefined) {
    return undefined;
  }
  const fileName = declaration.getSourceFile().fileName;
  const marker = fileName.lastIndexOf(SRC_MARKER);
  return marker < 0 ? undefined : `${fileName.slice(marker + SRC_MARKER.length)}#${name}`;
}

/**
 * 이 호출이 **닿는 선언들**. 시그니처 해소가 먼저다 — 그것이 별칭 변수·네임스페이스 접근·
 * 재수출을 전부 지나 원 선언을 낸다. 해소가 서지 않는 자리(타입 오류가 있는 합성 원문 등)는
 * 심볼의 알리아스 사슬로 한 번 더 본다.
 */
function calledDeclarations(checker, call) {
  const found = [];
  const signature = checker.getResolvedSignature(call);
  if (signature !== undefined && signature.declaration !== undefined) {
    found.push(signature.declaration);
  }
  let symbol = checker.getSymbolAtLocation(call.expression);
  if (symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0) {
    symbol = checker.getAliasedSymbol(symbol);
  }
  for (const declaration of symbol?.declarations ?? []) {
    found.push(declaration);
  }
  return found;
}

/**
 * 한 파일에서 **내보낸 함수 선언** 가운데 표시 어휘를 쓰는 것의 이름.
 *
 * 화살표 함수를 `export const` 로 내보내는 꼴도 받는다 — 이 저장소의 화면 파일은 함수 선언을
 * 쓰지만, 규약 하나에 기대면 그 규약을 벗는 날 게이트가 조용해진다.
 */
export function vocabularyComponentsOf(program, source) {
  const checker = program.getTypeChecker();
  const wanted = new Set(VOCABULARY_DECLARATIONS);
  const found = [];
  const consider = (name, node) => {
    if (name === undefined) {
      return;
    }
    let uses = false;
    const walk = (child) => {
      if (!uses && ts.isCallExpression(child)) {
        for (const declaration of calledDeclarations(checker, child)) {
          const key = declarationKeyOf(declaration);
          if (key !== undefined && wanted.has(key)) {
            uses = true;
            break;
          }
        }
      }
      ts.forEachChild(child, walk);
    };
    walk(node);
    if (uses) {
      found.push(name);
    }
  };
  ts.forEachChild(source, (node) => {
    if (ts.isFunctionDeclaration(node) && isExported(node)) {
      consider(node.name?.text, node);
      return;
    }
    if (ts.isVariableStatement(node) && isExported(node)) {
      for (const declaration of node.declarationList.declarations) {
        if (
          ts.isIdentifier(declaration.name) &&
          declaration.initializer !== undefined &&
          (ts.isArrowFunction(declaration.initializer) || ts.isFunctionExpression(declaration.initializer))
        ) {
          consider(declaration.name.text, declaration);
        }
      }
    }
  });
  return found;
}

/** 게이트가 마운트한다고 **선언한** 목록을 AST 로 읽는다(문자열 grep 은 주석 한 줄로 열린다). */
export function mountedCardsOf(text, path = 'screenVocabulary.test.tsx') {
  const source = ts.createSourceFile(path, text, ts.ScriptTarget.ESNext, true, ts.ScriptKind.TSX);
  let names = null;
  ts.forEachChild(source, (node) => {
    if (!ts.isVariableStatement(node) || !isExported(node)) {
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

/** 화면 디렉터리를 훑어 의무 집합과 화면 집합을 가른다. */
export function collectScreenVocabulary(webRoot, overlay = new Map()) {
  const program = screenProgram(webRoot, overlay);
  const screensDir = resolve(webRoot, 'src', 'screens');
  const obligations = [];
  const screens = [];
  const files = readdirSync(screensDir).filter((one) => one.endsWith('.tsx') && !one.endsWith('.test.tsx'));
  for (const file of files.sort()) {
    const source = program.getSourceFile(resolve(screensDir, file));
    if (source === undefined) {
      // 프로그램이 화면 파일을 안 담으면 이 훑기는 **미측정**이다 — 빈 집합으로 초록이 되는
      // 길을 두지 않는다(tsconfig 의 `include` 가 바뀌는 날이 그 자리다).
      throw new Error(`TS 프로그램에 화면 파일이 없다: ${file}`);
    }
    for (const name of vocabularyComponentsOf(program, source)) {
      (name.endsWith('Screen') ? screens : obligations).push({ name, file });
    }
  }
  return { program, obligations, screens, files };
}
