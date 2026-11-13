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
import { dirname, join, resolve } from 'node:path';

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

/**
 * 어휘 모듈이 내보내는 함수 가운데 **어휘가 아닌 것**과 그 사유.
 *
 * [VOCABULARY_DECLARATIONS] 는 손목록이고, 그것이 앞 판의 마지막 구멍이었다 — 표시 모듈에 어휘
 * 함수를 더해 그것만 쓰면 목록 밖이라 의무가 안 난다. 목록을 「표시 모듈의 내보낸 함수 전부」로
 * 넓히는 길은 M9 에서 실측됐고 의무 집합이 5 → 13 이 되며 질의 목이 필요한 패널·훅을 삼킨다.
 *
 * 그래서 **집합을 넓히지 않고 목록이 뒤처지는 축만** 등식으로 막는다 — 어휘 모듈의 내보낸 함수
 * 전수 == 등재 ∪ 이 표. 새 함수는 분류될 때까지 붉다.
 *
 * **이 등식의 모집단은 [VOCABULARY_MODULES] 셋뿐이다**(r1 F1). `src/display/` 에는 표시 모듈이
 * 열넷 있고, 나머지 열하나에 어휘를 감싼 함수를 더해 화면이 그것을 그리면 이 등식도 의무 집합도
 * 그것을 보지 못한다 — 그 축은 `OPEN-M11-SCREEN-COVERAGE-CROSS-FILE-VOCAB` 로 **경계 등재**했다
 * (닫는 길은 파일 사이 폐포이고, 실측하면 오늘의 마운트 목록 밖에 의무 둘을 낳아 승인 경계에
 * 걸린다).
 *
 * 이 표 자신이 다시 열거이므로 종류 하나에는 **구조 의무**가 붙는다([INTERNAL_ASSEMBLER]):
 * 화면이 그것을 **직접** 참조하면 사유가 거짓이 되고 게이트가 붉어진다. 「직접」이 그 의무의
 * 범위다 — 표시층 다른 모듈을 지나는 경로는 위 OPEN 의 축이고 이 게이트 밖이다.
 */
export const INTERNAL_ASSEMBLER = 'INTERNAL_ASSEMBLER';
export const OTHER_AXIS = 'OTHER_AXIS';

export const VOCABULARY_MODULE_EXCLUSIONS = [
  {
    key: 'display/moneyDisplay.ts#currencyUnitOf',
    kind: INTERNAL_ASSEMBLER,
    why: '통화 코드 → 단위 글자. 표시층이 금액 글자를 조립할 때 쓰는 속이고 화면이 직접 부르지 않는다(그 사실만 구조로 잰다)',
  },
  {
    key: 'display/moneyDisplay.ts#amountTextOf',
    kind: INTERNAL_ASSEMBLER,
    // **화면이 직접 부르지 않는다는 것이 「화면에 닿지 않는다」는 뜻은 아니다**(r1 F1 실측):
    // `editSessionDisplay` 의 비공개 `budgetView` 가 이 함수로 금액 글자를 만들고, 그 글자가
    // `STRATEGY_FIELD_VALUE` 를 지나 `screens/strategyEdit.tsx` 에 그려진다. 그 화면 파일은
    // 오늘 의무도 화면 바구니도 아니다 — 그 축이 위 머리 주석의 OPEN 이다.
    why: '숫자와 단위를 한 글자로. 위와 같은 속이고 화면이 직접 부르지 않는다. 표시층 다른 모듈을 지나 화면에 닿는 경로는 이 게이트 밖이다',
  },
  {
    key: 'display/noticeDisplay.ts#noticeStatusDisplayOf',
    kind: OTHER_AXIS,
    why: '공고 상태 축 — 이 게이트가 금지하는 어휘(금액 토큰·명령 토큰·시각 원문)가 아니다',
  },
  {
    key: 'display/timeDisplay.ts#daysUntil',
    kind: OTHER_AXIS,
    why: '달력일 차를 수로 낸다. 글자를 만들지 않는다',
  },
  {
    key: 'display/timeDisplay.ts#dayCountText',
    kind: OTHER_AXIS,
    why: 'D-day 문면. 시각 원문을 그리는 자리가 아니다',
  },
  {
    key: 'display/timeDisplay.ts#localInputToIso',
    kind: OTHER_AXIS,
    why: '쓰기 입력 → ISO 변환. 화면 글자가 아니라 요청 본문으로 간다',
  },
  {
    key: 'display/timeDisplay.ts#readClock',
    kind: OTHER_AXIS,
    why: '지금을 얻는 승인된 한 입구. 값을 그리지 않는다(D-1)',
  },
];

/** 어휘 선언과 제외가 사는 모듈 — 등식의 모집단이다. */
const VOCABULARY_MODULES = [...new Set(VOCABULARY_DECLARATIONS.map((one) => one.split('#')[0]))];

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

/**
 * 그 선언의 **자리** — `<src 기준 상대경로>#<이름>`.
 *
 * `export default <식>` 은 이름이 없지만 **파일당 하나**라 자리를 갖는다(r1 F2). 앞 판은 그
 * 선언이 `ExportAssignment` 여서 자리를 내지 못했고, 그래서 React 의 표준 꼴
 * (`export default memo(Card)`)이 의무를 하나도 내지 않았다 — 「심볼 경유는 내보내기의 꼴을
 * 묻지 않는다」가 그 꼴에 대해 거짓이었다. `export =` 은 모듈 꼴이 달라 대상이 아니다.
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

function aliasResolved(checker, symbol) {
  return symbol !== undefined && (symbol.flags & ts.SymbolFlags.Alias) !== 0
    ? checker.getAliasedSymbol(symbol)
    : symbol;
}

/**
 * 이 노드가 `wanted` 의 선언에 **닿는가** — 두 길을 함께 본다.
 *
 * ⓐ 호출의 **해소된 시그니처**: 별칭 변수·네임스페이스 접근·재수출·지역 변수 경유를 전부 지나
 * 원 선언을 낸다. ⓑ 맨 **참조**: `ats.map(instantDisplayOf)` 처럼 **부르지 않고 넘기는** 자리는
 * 해소할 호출이 없으므로 ⓐ 가 보지 못한다(앞 판의 잔여 하나).
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

/** 이 선언이 가리키는 **같은 파일의 다른 최상위 선언** — 그래프의 변이다(JSX 태그도 식별자다). */
function localEdgesOf(checker, declaration, source, byKey) {
  const edges = new Set();
  const selfKey = declarationKeyOf(declaration);
  const walk = (child) => {
    if (ts.isIdentifier(child)) {
      for (const one of aliasResolved(checker, checker.getSymbolAtLocation(child))?.declarations ?? []) {
        const key = one.getSourceFile() === source ? declarationKeyOf(one) : undefined;
        if (key !== undefined && key !== selfKey && byKey.has(key)) {
          edges.add(key);
        }
      }
    }
    ts.forEachChild(child, walk);
  };
  walk(declaration);
  return edges;
}

/**
 * 어휘에 닿는 최상위 선언 — 직접이거나, **같은 파일의 다른 선언을 지나** 닿는다.
 *
 * 깊이 상수를 두지 않고 **고정점**까지 간다(M11/11E, 팀장 결정 2026-11-13). 「한 겹」은 오늘
 * 실재하는 유일한 간접 렌더를 닫지 못한다 — 그 사슬이 화면 → 행 묶음 → 행 → 어휘로 **두 겹**
 * 이다. 상호 재귀가 있어도 끝난다(이미 든 것은 다시 세지 않는다).
 */
function reachingVocabulary(checker, source, byKey, wanted) {
  const edges = new Map();
  const reaching = new Set();
  for (const [key, declaration] of byKey) {
    edges.set(key, localEdgesOf(checker, declaration, source, byKey));
    if (touchesAnyOf(checker, declaration, wanted)) {
      reaching.add(key);
    }
  }
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

/**
 * 한 파일에서 **내보낸 이름** 가운데 표시 어휘를 그리는 것.
 *
 * 내보내기를 **심볼로** 받는다(`getExportsOfModule`) — 앞 판은 `export` 수식자가 붙은 함수 선언·
 * 화살표 초기값만 열거했고, 그래서 감싸 내보내기(`memo`·`forwardRef` — 초기값이 호출이다) ·
 * 개명 재수출(`export { Inner as Card }`) · 클래스 컴포넌트가 전부 의무를 내지 않았다. 심볼
 * 경유는 내보내기의 **꼴을 묻지 않는다**.
 *
 * 나는 이름은 **내보낸 이름**이다 — DOM 게이트가 마운트할 이름이 그것이고, 개명 재수출에서
 * 선언의 이름과 갈린다.
 *
 * 비공개 선언 자신은 목록에 들지 않는다(마운트 대상이 아니다). 바뀐 것은 「그것을 그리는 겉이
 * 의무가 되는가」이고, 그 답이 이제 예다.
 */
export function vocabularyComponentsOf(program, source) {
  const checker = program.getTypeChecker();
  const byKey = topLevelDeclarationsOf(source);
  const reaching = reachingVocabulary(checker, source, byKey, new Set(VOCABULARY_DECLARATIONS));
  const moduleSymbol = checker.getSymbolAtLocation(source);
  const names = new Set();
  for (const exported of moduleSymbol === undefined ? [] : checker.getExportsOfModule(moduleSymbol)) {
    for (const declaration of aliasResolved(checker, exported).declarations ?? []) {
      const key = declaration.getSourceFile() === source ? declarationKeyOf(declaration) : undefined;
      if (key !== undefined && reaching.has(key)) {
        names.add(exported.getName());
      }
    }
  }
  return [...names];
}

/**
 * 그 어휘 모듈이 내보내는 **부를 수 있는 것**의 자리 전수.
 *
 * **꼴이 아니라 타입을 묻는다**(r1 cr F-1). 앞 판은 「함수 선언 또는 화살표·함수 표현식 초기값」
 * 이라는 **열거**였고, 그래서 `export const f = build('x')` 같은 커링·감싸기가 등식 밖으로 샜다
 * (통제군이 서는 실측 — 화살표 꼴은 잡히고 커링은 안 잡혔다). 그것은 이 slice 가 화면 쪽에서
 * 바로 지운 조건이라 같은 자리에 남겨 둘 수 없다. 호출 시그니처가 하나라도 있으면 「부를 수
 * 있는 것」이고, `memo(…)`·커링·`as` 단언 꼴이 함께 들어온다.
 */
function exportedFunctionKeysOf(checker, program, webRoot, relative) {
  const source = program.getSourceFile(resolve(webRoot, 'src', relative));
  if (source === undefined) {
    // 모듈이 프로그램에 없으면 등식이 **공허하게** 통과한다 — 그 길을 두지 않는다.
    throw new Error(`TS 프로그램에 어휘 모듈이 없다: ${relative}`);
  }
  const moduleSymbol = checker.getSymbolAtLocation(source);
  const keys = [];
  for (const exported of moduleSymbol === undefined ? [] : checker.getExportsOfModule(moduleSymbol)) {
    const type = checker.getTypeOfSymbolAtLocation(aliasResolved(checker, exported), source);
    if (type.getCallSignatures().length > 0) {
      keys.push(`${relative}#${exported.getName()}`);
    }
  }
  return keys;
}

/**
 * 등재 표의 **구멍 셋**(M11/11E — 새 어휘 함수 축).
 *
 * `unclassified` 어휘 모듈이 내보내는 함수인데 등재도 제외도 아닌 것 — 새 어휘 함수가 조용히
 * 의무 밖에 서는 자리다. `stale` 제외 표에 적혀 있는데 이제 그 모듈의 내보낸 함수가 아닌 것 —
 * 사유가 가리키는 대상이 사라진 자리다. `leaked` [INTERNAL_ASSEMBLER] 로 제외한 것을 화면이
 * **직접 참조**하는 자리 — 「표시층 속이라 화면이 안 쓴다」는 사유가 거짓이 된 자리다.
 *
 * 화면 훑기를 **프로그램에서** 받는다(파일시스템이 아니라) — 합성 원문으로 양성 대조를 세울 수
 * 있어야 하고, 둘이 어긋나지 않는 것은 [collectScreenVocabulary] 가 던져서 지킨다.
 */
export function vocabularyRegistryGaps(program, webRoot) {
  const checker = program.getTypeChecker();
  const classified = new Set([...VOCABULARY_DECLARATIONS, ...VOCABULARY_MODULE_EXCLUSIONS.map((one) => one.key)]);
  const exported = new Set(
    VOCABULARY_MODULES.flatMap((relative) => exportedFunctionKeysOf(checker, program, webRoot, relative)),
  );
  const assemblers = new Set(
    VOCABULARY_MODULE_EXCLUSIONS.filter((one) => one.kind === INTERNAL_ASSEMBLER).map((one) => one.key),
  );
  const screensDir = resolve(webRoot, 'src', 'screens');
  const leaked = [];
  for (const source of program.getSourceFiles()) {
    if (!source.fileName.startsWith(`${screensDir}/`) || /\.test\.tsx?$/u.test(source.fileName)) {
      continue;
    }
    for (const declaration of topLevelDeclarationsOf(source).values()) {
      if (touchesAnyOf(checker, declaration, assemblers)) {
        leaked.push(source.fileName.slice(screensDir.length + 1));
      }
    }
  }
  return {
    unclassified: [...exported].filter((one) => !classified.has(one)).sort(),
    stale: VOCABULARY_MODULE_EXCLUSIONS.map((one) => one.key)
      .filter((one) => !exported.has(one))
      .sort(),
    leaked: [...new Set(leaked)].sort(),
  };
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

/**
 * 화면 디렉터리의 소스 파일 — **재귀**이고 `.ts` 도 담는다(PR #132 v M-3). 앞 판은 한 겹
 * 평면 훑기에 `.tsx` 만이었고, 그래서 하위 디렉터리에 둔 카드와 JSX 를 쓰지 않는 `.ts` 카드가
 * 경계 문장(`src/screens/**`)과 달리 **모집단 밖**이었다.
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

/** 화면 디렉터리를 훑어 의무 집합과 화면 집합을 가른다. */
export function collectScreenVocabulary(webRoot, overlay = new Map()) {
  const program = screenProgram(webRoot, overlay);
  const screensDir = resolve(webRoot, 'src', 'screens');
  const obligations = [];
  const screens = [];
  const files = screenFilesIn(screensDir);
  for (const file of files) {
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
