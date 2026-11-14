import { execFileSync } from 'node:child_process';
import {
  Dirent,
  Stats,
  closeSync,
  createReadStream,
  globSync,
  mkdirSync,
  mkdtempSync,
  openSync,
  readFileSync,
  realpathSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { readFileSync as aliasedRead } from 'fs';
import { readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, relative as relativePath, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

import { isKotlinSourcePath } from './kotlin-read-guard.mjs';
import { KOTLIN_SOURCE_KEYS, withKotlinSource } from './kotlin-source.mjs';

/**
 * **「여는 행위」를 런타임에 재는 층**(M11/gates-web-read — `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS` ⑤).
 *
 * 등재된 사실: 앞 판의 게이트는 **적힌 토큰**만 보았다. 그래서 추적되는 심볼릭 링크(⑧)·대소문자
 * (⑨)·데이터 파일의 확장자 단독 값과 glob(⑩)이 지나갔다 — 코드에 토큰이 남지 않거나 술어의
 * 글자와 달랐기 때문이다. 이 층은 **표기를 보지 않는다**: 열린 경로를 **해석된 실경로**로 바꿔
 * 확장자를 대소문자 무시로 보고, 호출자가 소유 모듈이 아니면 **던진다**.
 *
 * 그래서 아래 대조는 두 묶음이다 — **어떤 API·표기로 열어도 걸린다**(양성)와 **읽기 집합의 소유
 * 모듈과 그 밖의 파일은 그대로 돈다**(음성). 이 층이 **설치되지 않으면 양성 묶음 전부가 RED** 이고,
 * 그것이 `vite.config.ts` 의 `setupFiles` 한 줄을 지우는 변이의 자리다.
 *
 * **범위**(M12 — ⓑ⑤·ⓑ⑥): 세우는 자리가 셋이다 — vitest `setupFiles` · 추적된 뿌리 `*.config.*`
 * 전수의 적재 · `package.json` 의 `node --import` 전수. 아래 묶음 셋이 그것을 각각 잰다(배선을
 * 세는 의무 게이트 둘 · 전파가 자식에 닿는지 재는 칸 하나)이고, spec 프로세스가 실제로 층
 * 아래인지는 `e2e/kotlin-read-guard.spec.ts` 가 잰다. 남는 자리(`worker_threads` · 자식 프로세스
 * 텍스트 검색)는 capability map §12.3 의 신설 OPEN 이 등재한다.
 */

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = resolve(webRoot, '..');

/** 읽기 집합의 실제 Kotlin 정본 하나 — 경로는 `git` 에게 물어 **이 파일에 적지 않는다**. */
const shippedKotlinFile = () => {
  const tracked = execFileSync('git', ['ls-files', '--', 'shared-kernel/src/main/kotlin'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
    .split('\n')
    .filter((line) => isKotlinSourcePath(line));
  expect(tracked.length, 'Kotlin 정본을 찾지 못했다 — 이 대조가 공허하다').toBeGreaterThan(0);
  return resolve(repoRoot, tracked[0]);
};

describe('여는 행위 — 소유 모듈 밖에서 Kotlin 원문을 열면 던진다', () => {
  const target = shippedKotlinFile();

  /** 어떤 읽기 API 로 열어도 걸린다 — 층이 `fs` 표면 **전수**를 감싸므로 API 선택이 술어 밖이다. */
  it('읽기 API 를 바꿔도 걸린다', async () => {
    expect(() => readFileSync(target, 'utf8')).toThrow(/kotlin-read-guard/u);
    expect(() => aliasedRead(target, 'utf8')).toThrow(/kotlin-read-guard/u);
    expect(() => openSync(target, 'r')).toThrow(/kotlin-read-guard/u);
    expect(() => createReadStream(target)).toThrow(/kotlin-read-guard/u);
    await expect(readFile(target, 'utf8')).rejects.toThrow(/kotlin-read-guard/u);
  });

  /**
   * **⑧ — 추적되는 심볼릭 링크.** 링크의 확장자는 Kotlin 이 아니고 코드에도 토큰이 남지 않는다.
   * 실경로를 보므로 그 표기 축이 사라진다.
   */
  it('심볼릭 링크로 가려도 걸린다', () => {
    const scratch = resolve(webRoot, 'test-results');
    mkdirSync(scratch, { recursive: true });
    const dir = mkdtempSync(resolve(scratch, 'guard-'));
    const link = resolve(dir, 'policy-source.txt');
    // 링크는 **층 밖에서** 만든다. 실제 ⑧ 은 커밋된 링크를 **읽는** 꼴이고, 이 층이 재는 것도
    // 읽기다 — 층은 Kotlin 원문을 가리키는 링크를 **만드는** 호출도 거부하므로(넓은 쪽으로
    // 고른 범위이고 알려진 제한에 등재) 탐침의 준비는 `ln` 으로 한다.
    execFileSync('ln', ['-s', target, link]);

    try {
      expect(() => readFileSync(link, 'utf8')).toThrow(/kotlin-read-guard/u);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  /**
   * **ⓑ③ — 저장소 밖으로 복사한 뒤 읽는다**(M12). 앞 판은 열리는 경로가 저장소 안인지 **묻고**
   * 밖이면 건너뛰었다. 그래서 디렉터리째 밖으로 복사하면(복사 자체는 디렉터리 인자라 지나간다)
   * 그 사본을 그대로 읽을 수 있었다 — 실측으로 읽혔다.
   *
   * 지금은 **위치를 묻지 않는다**. 남는 예외는 `node_modules` 하나이고, 그 분기의 값은 문면이
   * 아니라 **측정**이다(아래 음성 대조 · `commands.md`).
   *
   * 준비는 `cp` 로 한다 — 층은 Kotlin 원문을 **인자로 받는 복사**도 거부하므로(넓은 쪽으로 고른
   * 범위) 탐침이 자기 준비에 걸리면 안 된다. 실제 ⓑ③ 은 **디렉터리째** 복사하는 꼴이다.
   */
  it('저장소 밖으로 복사한 사본도 걸린다 — 위치를 묻지 않는다', () => {
    const dir = mkdtempSync(resolve(tmpdir(), 'guard-outside-'));
    const copy = resolve(dir, 'copied-source.txt');
    const kotlinCopy = resolve(dir, `X.${'k'}${'t'}`);
    execFileSync('cp', [target, kotlinCopy]);
    execFileSync('cp', [target, copy]);

    try {
      // 이름이 보존된 사본 — **읽을 때** 실경로의 확장자가 선다.
      expect(() => readFileSync(kotlinCopy, 'utf8')).toThrow(/kotlin-read-guard/u);
      // 이름을 바꾼 사본은 Kotlin 원문이 **아니다**(확장자가 성질이다). 그 꼴을 받는 자리는
      // 복사하는 순간이다 — 원본 인자가 Kotlin 실경로라 층이 거기서 거부한다.
      expect(() => readFileSync(copy, 'utf8')).not.toThrow();
      expect(() => readFileSync(target, 'utf8')).toThrow(/kotlin-read-guard/u);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  /**
   * **⑩ — 데이터 파일의 glob.** 경로가 코드에 없고 **별표 glob** 으로만 적혀도, 여는 순간
   * 실경로가 서므로 걸린다. 정적 술어는 이 꼴을 데이터 파일 본문에서 따로 받는다(두 층).
   */
  it('glob 으로 찾아 열어도 걸린다 — 코드에 경로 토큰이 없다', () => {
    const pattern = `shared-kernel/src/main/kotlin/bidvector/sharedkernel/*.${'k'}${'t'}`;
    // **찾기는 지나간다** — 패턴은 열리는 파일이 아니다(해석되지 않는 경로는 층의 대상이 아니다).
    // 막는 자리는 **찾은 것을 여는** 순간이고, 그래서 경로를 데이터 파일에서 얻어도 같은 답이다.
    const matches = globSync(pattern, { cwd: repoRoot });

    expect(matches.length, 'glob 이 아무것도 찾지 못했다 — 이 대조가 공허하다').toBeGreaterThan(0);
    expect(() => readFileSync(resolve(repoRoot, matches[0]), 'utf8')).toThrow(/kotlin-read-guard/u);
  });
});

/**
 * **층이 서는 자리가 둘이다**(M12 — ⓑ⑤). 하나는 vitest **소비자 프로세스**(`setupFiles`), 다른
 * 하나는 **vite 메인 프로세스**(`vite.config.ts` 의 적재). 번들러 적재(`import.meta.glob` + `?raw`)는
 * 파일을 **메인 프로세스**가 열므로 앞의 층이 보지 못했고, 패턴에 확장자 토큰이 없으면 정적 술어도
 * 보지 못했다(착수 실측: 42 모듈 적재).
 */
describe('여는 행위 — 번들러 적재도 층 아래다 (ⓑ⑤)', () => {
  /**
   * 쥐는 자리를 **동적**으로 둔다 — 정적으로 import 하면 이 test 파일 자체가 적재되지 못한다.
   * 거절의 **문면은 층의 것이 아니다**(vite 가 층의 거부를 잡고 상대 경로로 다시 열어 `ENOENT` 를
   * 낸다 — 알려진 제한). 그래서 「거절한다」까지만 재고, 그 거절이 **Kotlin 축에서만** 일어남은
   * 아래 통제군이 든다.
   */
  it('확장자 토큰 없는 `?raw` glob 은 적재되지 않는다', async () => {
    await expect(import('./kotlin-raw-glob-probe.mjs')).rejects.toThrow();
  });

  /**
   * **통제군** — 저장소 밖의 **비 Kotlin** 파일을 같은 꼴로 끌어오면 그대로 적재된다. 이것이
   * 통과해야 위 거절이 「glob 이 통째로 막혔다」가 아니라 **Kotlin 축**임이 선다.
   */
  it('저장소 밖 비 Kotlin 파일의 같은 glob 은 적재된다', () => {
    const loaded = import.meta.glob('../../openapi/*', { query: '?raw', import: 'default', eager: true });

    expect(Object.keys(loaded).length, '통제군이 아무것도 적재하지 않았다 — 대조가 공허하다').toBeGreaterThan(
      0,
    );
  });
});

describe('여는 행위 — 음성 대조', () => {
  /**
   * 소유 모듈은 읽기 집합 전수를 그대로 읽는다 — 일률 거절이 아니다.
   *
   * 읽기는 **추출 함수를 넘겨받는다**(M12 — ⓐ). 여기서 넘기는 것은 길이를 재는 함수다: 원문을
   * 그대로 돌려주면 읽기가 **거부하므로**(항등 함수 후조건) 이 대조도 추출을 해야 한다.
   */
  it('소유 모듈의 읽기는 통과한다', () => {
    for (const key of KOTLIN_SOURCE_KEYS) {
      expect(
        () => withKotlinSource(key, (text) => text.length),
        `${key} 를 읽지 못한다`,
      ).not.toThrow();
    }
  });

  /** Kotlin 원문이 아닌 파일은 층을 지나간다. 이것이 붉으면 test 레인 전체가 멈춘다. */
  it('그 밖의 파일 읽기는 통과한다', () => {
    const fd = openSync(resolve(webRoot, 'package.json'), 'r');
    closeSync(fd);

    expect(readFileSync(resolve(webRoot, 'package.json'), 'utf8').length).toBeGreaterThan(0);
  });

  /**
   * **남는 위치 예외는 `node_modules` 하나다**(M12 — ⓑ③ 의 대가).
   *
   * 앞 판은 이 자리에서 「저장소 밖 경로는 층의 대상이 아니다」를 주장했는데, 재는 입력이
   * **저장소 안의** 비 Kotlin 파일이라 그 주장을 재지 못했다. 그 칸은 위 양성 묶음이 받았고
   * (위치를 묻지 않는다) 이 칸은 **남은 예외 하나**를 재는 자리로 주장을 바꾼다.
   *
   * 지우지 않는 이유는 **값이 있기 때문**이다: 이 분기가 없으면 모든 모듈 적재가 실경로 호출을
   * 타고 vitest 전건이 느려진다(같은 조건 3회 중위값 — `commands.md` 「위치 분기 셋 갈래」).
   * 그래서 「`node_modules` 아래로 복사한 뒤 읽기」는 열려 있고, 그 꼴은 평범한 관용구가 아니라
   * **의도적 은닉**이라 경계 밖으로 등재한다.
   */
  it('node_modules 아래의 Kotlin 사본은 층을 지나간다 — 값이 있는 예외 하나', () => {
    const dir = mkdtempSync(resolve(webRoot, 'node_modules', '.guard-probe-'));
    const inside = resolve(dir, `X.${'k'}${'t'}`);
    execFileSync('cp', [shippedKotlinFile(), inside]);

    try {
      expect(() => readFileSync(inside, 'utf8')).not.toThrow();
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  /** 저장소 밖의 **비 Kotlin** 파일은 그대로 지나간다 — 일률 거절이 아니다. */
  it('저장소 밖의 비 Kotlin 파일 읽기는 통과한다', () => {
    const dir = mkdtempSync(resolve(tmpdir(), 'guard-plain-'));
    const plain = resolve(dir, 'notes.txt');
    writeFileSync(plain, 'plain\n');

    try {
      expect(readFileSync(plain, 'utf8')).toBe('plain\n');
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });
});

/**
 * **층이 원래 계약을 바꾸지 않는다**(r1 — cr H-2). `fs` 의 함수 값에는 **생성자**가 섞여 있어,
 * 앞 판처럼 평범한 함수로 감싸면 `instanceof` 가 거짓이 되고 ES class 는 `new` 가 던졌다. 그
 * 깨짐은 **오늘의 전건이 밟지 않는 잠복**이라 초록으로 지나갔다 — 그래서 그 자리를 test 로 든다.
 */
describe('여는 행위 — 감싸기가 생성·동일성 계약을 바꾸지 않는다', () => {
  it('`instanceof` 가 유지된다', () => {
    const stats = statSync(resolve(webRoot, 'package.json'));

    expect(stats instanceof Stats, 'statSync 결과가 Stats 가 아니다').toBe(true);
  });

  it('ES class 를 `new` 로 부를 수 있다', () => {
    const entry = new Dirent('x', 1, '/tmp');

    expect(entry instanceof Dirent).toBe(true);
    expect(entry.name).toBe('x');
  });

  it('함수에 붙은 속성이 보존된다', () => {
    expect(typeof realpathSync.native).toBe('function');
  });

  /** 그 셋이 통과하는 동안에도 **거부는 그대로 발화한다** — 계약 보존이 층을 끄는 것이 아니다. */
  it('계약을 지키면서도 거부는 발화한다', () => {
    expect(() => readFileSync(shippedKotlinFile(), 'utf8')).toThrow(/kotlin-read-guard/u);
  });
});

/**
 * **공개 표면이 셋으로 고정된다**(r1 — cr MEDIUM). 층이 자라면서 내보내는 것이 늘면 그만큼
 * 「밖에서 쓸 수 있는 것」이 늘고, 그 증가는 문면으로만 두면 **조용히** 일어난다. 공유 읽기 모듈이
 * 같은 자리에서 그렇게 열렸던 선례가 있어(그 모듈의 export 집합 등식) 같은 꼴을 여기에도 둔다.
 *
 * 늘리거나 줄이면 이 줄이 먼저 붉어지고, 사람이 그 표면이 무엇을 내주는지 보고 판단한다.
 */
describe('여는 행위 — 공개 표면이 셋으로 고정된다', () => {
  it('export 집합이 정확히 셋이다', async () => {
    const namespace = await import('./kotlin-read-guard.mjs');

    expect(Object.keys(namespace).sort(), '층의 공개 표면이 바뀌었다 — 늘었는지 줄었는지 보고 판단하라').toStrictEqual([
      'callerOutside',
      'installKotlinReadGuard',
      'isKotlinSourcePath',
    ]);
  });
});

/**
 * **층이 서야 하는 자리를 선언에서 뽑는다**(M12 — ⓑ⑥).
 *
 * M11 은 이 축을 「진입점마다 미리 적재하는 것은 **진입점 열거**라 고르지 않았다」로 등재했다.
 * 그 전제를 둘로 가른 것이 이 slice 의 실측이다. ① **기구가 하나**다 — `node --import <층>` 이
 * 평범한 node 프로세스를 막고, config 가 `NODE_OPTIONS` 에 같은 것을 실으면 그 도구가 띄우는
 * 프로세스(playwright spec 워커·`webServer`)까지 막는다(실측: 같은 spec 이 거부됐다). ② **의무의
 * 모집단을 선언에서 뽑을 수 있다** — 손 목록이 아니다.
 *
 * 모집단이 둘이고 **기구가 다른 이유는 저장소 코드가 프로세스에 들어가는 길이 다르기 때문**이다.
 * - **뿌리 config 모듈**: 도구가 그 파일을 적재한다 → 그 파일이 층을 **import** 한다.
 * - **`node` 진입 스크립트**: 셸이 그 파일을 띄운다 → 그 명령이 층을 **`--import`** 한다.
 *
 * 새 config 파일이나 새 `node` 명령이 층 없이 생기면 아래 둘이 붉어진다. `tsc`·
 * `openapi-typescript` 는 저장소가 쓴 코드를 적재하지 않아 모집단 밖이다(등재).
 */
describe('여는 행위 — 층이 선 자리를 선언에서 센다 (ⓑ⑥)', () => {
  /** 층을 세우는 **유일한 모듈** — 두 모집단의 의무가 같은 파일을 가리킨다. */
  const SETUP = 'scripts/kotlin-read-guard-setup.mjs';

  const tracked = () =>
    execFileSync('git', ['ls-files', '-z', '--', '.'], { cwd: webRoot, encoding: 'utf8' })
      .split('\0')
      .filter((relative) => relative.length > 0);

  /** 뿌리 config 전수 — 이름 목록이 아니라 **추적 파일의 꼴**이다. */
  const rootConfigs = () => tracked().filter((relative) => /^[^/]+\.config\.[cm]?[jt]s$/u.test(relative));

  /**
   * 그 파일이 층을 import 하는가. **지정자를 해석해서** 본다 — 표기(`./scripts/…` · `../web/…`)를
   * 세지 않는다. 정적 import·동적 import·재수출을 모두 든다.
   */
  const importsSetup = (relative) => {
    const source = readFileSync(resolve(webRoot, relative), 'utf8');
    const tree = ts.createSourceFile(relative, source, ts.ScriptTarget.Latest, false);
    const fromDir = dirname(resolve(webRoot, relative));
    let found = false;
    const take = (specifier) => {
      if (specifier.startsWith('.')) {
        found ||= relativePath(webRoot, resolve(fromDir, specifier.split('?')[0])) === SETUP;
      }
    };
    const visit = (node) => {
      if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) && node.moduleSpecifier !== undefined) {
        if (ts.isStringLiteral(node.moduleSpecifier)) {
          take(node.moduleSpecifier.text);
        }
      }
      if (
        ts.isCallExpression(node) &&
        node.expression.kind === ts.SyntaxKind.ImportKeyword &&
        node.arguments[0] !== undefined &&
        ts.isStringLiteral(node.arguments[0])
      ) {
        take(node.arguments[0].text);
      }
      ts.forEachChild(node, visit);
    };
    visit(tree);
    return found;
  };

  /** `package.json` 의 `scripts` 에서 **`node` 로 시작하는 명령 전수**를 뽑는다. */
  const nodeCommands = () => {
    const { scripts } = JSON.parse(readFileSync(resolve(webRoot, 'package.json'), 'utf8'));
    return Object.entries(scripts).flatMap(([name, value]) =>
      value
        .split('&&')
        .map((part) => part.trim())
        .filter((part) => /^node\s/u.test(part))
        .map((command) => ({ name, command })),
    );
  };

  it('뿌리 config 전수가 층을 import 한다', () => {
    const configs = rootConfigs();

    expect(configs.length, '뿌리 config 를 하나도 찾지 못했다 — 이 등식이 공허하다').toBeGreaterThan(2);
    expect(
      configs.filter((relative) => !importsSetup(relative)),
      '이 config 가 적재되는 프로세스는 층 밖이다',
    ).toStrictEqual([]);
  });

  it('`node` 진입 명령 전수가 층을 먼저 적재한다', () => {
    const commands = nodeCommands();

    expect(commands.length, '`node` 명령을 하나도 찾지 못했다 — 이 등식이 공허하다').toBeGreaterThan(3);
    // **자리까지 본다**: node 는 스크립트 앞의 플래그만 읽으므로 뒤에 붙은 `--import` 는 인자다.
    const flag = new RegExp(`^node\\s+--import[= ]\\./${SETUP}\\s`, 'u');
    expect(
      commands.filter(({ command }) => !flag.test(command)).map(({ name }) => name),
      '이 명령이 띄우는 프로세스는 층 밖이다',
    ).toStrictEqual([]);
  });

  /**
   * **전파가 혼자 덮는 자리를 잰다** — 설정을 적재하지 않는 **node 자식**이다. 위 둘은 *배선*을
   * 세고, 이 칸은 그 배선이 실제로 **자식 프로세스에 닿는가**를 잰다(전파 블록을 층 모듈에서 빼는
   * 변이가 위 둘을 초록으로 지나가고 이 칸에서 붉어진다).
   *
   * 자식에게 경로를 **인자로** 넘긴다 — `-e` 본문에 경로를 적으면 이 파일이 소유권 게이트의
   * 경로 술어에 걸린다.
   */
  it('전파가 node 자식 프로세스까지 닿는다', () => {
    const target = shippedKotlinFile();
    const code =
      'const { readFileSync } = require("node:fs");' +
      'try { readFileSync(process.argv[1], "utf8"); console.log("READ"); }' +
      'catch (error) { console.log(String(error.message).includes("kotlin-read-guard") ? "REFUSED" : "OTHER"); }';
    const run = (env) =>
      execFileSync(process.execPath, ['-e', code, target], { encoding: 'utf8', env }).trim();

    // 양성 — 이 프로세스의 `NODE_OPTIONS` 를 그대로 물려받은 자식은 거부한다.
    expect(run(process.env)).toBe('REFUSED');
    // 음성 — 그 변수를 비우면 같은 자식이 읽는다(대조가 공허하지 않다는 증거이고, 전파가
    // **그 변수로** 일한다는 사실의 측정이다).
    expect(run({ ...process.env, NODE_OPTIONS: '' })).toBe('READ');
  });

  /** 술어의 양성 대조 — 층을 import 하지 않는 합성 config 는 걸린다(일률 통과가 아니다). */
  it('술어가 층 없는 config 와 명령을 잡는다', () => {
    expect(importsSetup('package.json')).toBe(false);
    const flag = new RegExp(`^node\\s+--import[= ]\\./${SETUP}\\s`, 'u');
    expect(flag.test('node scripts/x.mjs')).toBe(false);
    expect(flag.test(`node scripts/x.mjs --import ./${SETUP} `)).toBe(false);
    expect(flag.test(`node --import ./${SETUP} scripts/x.mjs`)).toBe(true);
  });
});

describe('여는 행위 — 확장자 술어는 실경로를 대소문자 무시로 본다 (⑨)', () => {
  it('Kotlin 확장자는 표기와 무관하게 참이다', () => {
    const extension = `${'k'}${'t'}`;
    for (const suffix of [extension, extension.toUpperCase(), `${extension}s`, `${extension.toUpperCase()}S`, `K${extension.slice(1)}`]) {
      expect(isKotlinSourcePath(`/a/b/X.${suffix}`), `.${suffix} 를 놓친다`).toBe(true);
    }
  });

  /** 음성 대조 — 비슷한 글자는 Kotlin 원문이 아니다(일률 참이 아니다). */
  it('Kotlin 확장자가 아닌 것은 거짓이다', () => {
    const extension = `${'k'}${'t'}`;
    for (const path of [`/a/X.${extension}x`, `/a/X.txt`, `/a/${extension}`, `/a/X.${extension}.bak`, '/a/X.mjs']) {
      expect(isKotlinSourcePath(path), `${path} 를 Kotlin 원문으로 본다`).toBe(false);
    }
  });
});
