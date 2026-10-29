// ADR 0011 D-3 — **번들의 입력 그래프**에 걸는 경계(verifier r1 H-1).
//
// 왜 이 자리인가: 7A 가 처음 세운 게이트는 전부 **TS 프로그램**에 걸려 있었는데 번들의 입력은
// **Vite/Rollup 모듈 그래프**다. 두 그래프가 다르므로, tsc 가 보지 못하는 경로로 web 밖 파일이
// 번들에 들어갔다(실측: `import.meta.glob` · `?raw` · `?worker` · `resolve.alias` + `declare
// module` · `index.html` 의 `<script src="../">` 전부 게이트 넷 exit 0 · dist 에 밖 파일 실재).
// 열거(금지 경로 목록 · `import.meta.glob` 금지 린트)로 막으면 다음 접미·플러그인 하나로 다시
// 열린다. 그래서 **그래프 자체**를 단언한다 — 빌드가 실제로 읽은 모듈 전부가 허용 뿌리 안인가.
//
// 이 플러그인이 `vite.config.ts` 에 등재돼 있는지는 W-4(`check-src-tree.mjs`)가 AST 로 본다 —
// 조용히 떼어내면 게이트가 멈춘다. 아래 순수 함수들은 `*.test.mjs` 가 직접 잠근다(W-6).
import { existsSync, readFileSync, realpathSync } from 'node:fs';
import { dirname, join, resolve, sep } from 'node:path';

/** `?raw`·`?worker`·`?html-proxy` 같은 접미를 떼어 파일 경로만 남긴다. */
export function stripQuery(id) {
  const index = id.indexOf('?');
  return index === -1 ? id : id.slice(0, index);
}

/**
 * 파일이 아닌 id — Rollup 가상 모듈(`\0…`)과 절대 경로가 아닌 bare specifier.
 * legacy 파일은 Rollup 이 **절대 경로로 해석한 뒤** 그래프에 넣으므로, 이 둘을 건너뛰어도
 * 파일로 들어오는 경로는 전부 남는다(플러그인이 내용을 직접 공급하는 가상 모듈은
 * `vite.config.ts` 의 영역이고 diff 에 남는다).
 */
export function isAbsoluteFilePath(path) {
  return path.startsWith(sep) || path.startsWith('/') || /^[A-Za-z]:[\\/]/.test(path);
}

/**
 * 모듈 id 를 세 갈래로 가른다(code-review r2 G-5 — 기본 방향이 **열림**이면 안 된다).
 *
 * - `file`: 디스크의 파일(절대 경로). Rollup 의 가상 접두 `\0` 가 붙은 실파일 id
 *   (`\0/abs/x.js?commonjs-exports` 같은 commonjs 변환 모듈)도 여기다 — realpath 로 경계를 잰다.
 * - `relative`: 해석되지 않은 상대 경로. Rollup 은 보통 절대 경로로 해석해 넣으므로 이 갈래가
 *   나오면 **모르는 것**이고, 모르는 것은 통과시키지 않는다(위반으로 센다).
 * - `virtual`: 뒤에 파일이 없는 플러그인 공급 모듈(`\0commonjsHelpers.js` ·
 *   `\0vite/modulepreload-polyfill.js` — 깨끗한 빌드에서 실측된 둘). 내용이 `vite.config.ts` 의
 *   플러그인에서 오므로 legacy **파일**을 실어 올 수 없고 그 플러그인은 diff 에 남는다.
 *
 * 이전 판은 「절대 경로가 아니면 건너뛴다」여서 Windows 드라이브 형 id 와 상대 경로가 모두
 * 건너뛰기 바구니에 들어갔다 — 게이트가 공허하게 통과할 수 있는 형태였다.
 */
export function classifyModuleId(rawId) {
  const noQuery = stripQuery(rawId);
  const path = noQuery.startsWith('\0') ? noQuery.slice(1) : noQuery;
  if (isAbsoluteFilePath(path)) {
    return { kind: 'file', path };
  }
  if (path === '.' || path === '..' || path.startsWith('./') || path.startsWith('../')) {
    return { kind: 'relative', path };
  }
  return { kind: 'virtual', path };
}

/**
 * 번들 그래프에 들어와도 되는 **모듈 종류**(v M-4). 앞 판은 데이터 확장자를 **열거**했고
 * (`json|json5|ya?ml|csv|xml`), 같은 JSON 을 `rows.txt?raw` 로 들이면 그 열거를 비켜 갔다(실측).
 * 그래서 술어를 **뒤집는다** — 아는 종류만 받고 **모르는 종류는 위반**이다.
 *
 * 코드와 스타일만 번들의 입력이다. 이미지·폰트 같은 자산이 필요해지면 이 목록에 한 줄을
 * 더해야 하고, 그 변경은 diff 에 남는다(열거를 늘리는 쪽과 달리 **빠뜨림이 조용하지 않다**).
 *
 * 왜 이 층에도 두는가: TS 쪽은 `resolveJsonModule` 을 꺼서 `import x from './a.json'` 이
 * TS2732 로 막히고 W-4 가 `src` 안 데이터 파일 0 과 쿼리 접미 import 0 을 센다. 그러나 그 둘은
 * **`node_modules` 안의 데이터**를 보지 못한다 — 그 자리를 이 층이 받는다.
 */
const ALLOWED_MODULE_EXTENSIONS = new Set(['ts', 'tsx', 'mts', 'cts', 'js', 'jsx', 'mjs', 'cjs', 'css', 'html']);

export function isAllowedModuleKind(path) {
  const match = /\.([A-Za-z0-9]+)$/.exec(path);
  if (match === null) {
    // 확장자가 없는 파일은 종류를 알 수 없다 — 모르는 것은 통과시키지 않는다.
    return false;
  }
  return ALLOWED_MODULE_EXTENSIONS.has(match[1].toLowerCase());
}

export function isInsideRoot(candidate, root) {
  return candidate === root || candidate.startsWith(root + sep);
}

/** 허용 뿌리 어디에도 속하지 않으면 위반. 비교는 realpath 끼리 한다(심볼릭 링크 무력화). */
export function violatesBoundary(realPath, allowedRoots) {
  return !allowedRoots.some((root) => isInsideRoot(realPath, root));
}

/**
 * 생성 타입 클라이언트 패키지와 그 **승인된 사용처**(ADR 0011 D-2·D-3).
 *
 * 이 게이트가 증명하는 범위: 「클라이언트 패키지(정체 기준)를 끌어오는 자리 == 이 한 자리」.
 * 「서버를 부르는 경로가 그것 하나뿐」은 **증명하지 않는다** — 브라우저가 HTTP 를 낼 길은
 * 전역·다른 의존·외부 URL·`index.html` 처럼 여럿이고, 그 주장은 보조 lint 와 리뷰 소관이다
 * (7A 검토 r5 · checklist 의 알려진 제한).
 */
export const API_CLIENT_PACKAGE = 'openapi-fetch';
export const API_CALL_SITE = 'src/api/client.ts';

/**
 * 클라이언트 패키지의 모듈을 **패키지 밖에서** 끌어오는 importer 가 승인된 호출 자리인가.
 * 패키지 내부끼리의 import 는 세지 않는다(그 패키지의 자기 구현이다).
 *
 * 왜 이 층에도 두는가(verifier r3 M-7): W-4 의 TS 쪽 집합 등식은 `ts.resolveModuleName` 이
 * 해석하지 못하는 지정자 — `resolve.alias` 로 붙인 이름 + `src` 안 주변 선언
 * (`declare module '@oa'`) — 을 세지 못한다. 그 별칭을 실제로 해석하는 것은 **Vite** 뿐이므로
 * Vite 의 그래프에서 같은 등식을 한 번 더 센다. 별칭이든 접미 지정자든 같은 술어에 걸린다.
 *
 * 방향은 하나뿐이다 — 「승인 자리 밖에서 끌어오지 않는다」. 반대 방향(「승인 자리가 끌어온다」)은
 * 이 층에서 셀 수 없다: 7A 의 `apiClient` 는 아직 아무도 쓰지 않아 트리셰이킹으로 그래프에
 * 들어오지 않는다. 그 방향은 W-4 의 TS 쪽 등식이 진다.
 */
/**
 * 그래프에서 이 모듈을 끌어온 자리 전부 — **정적 간선과 동적 간선의 합집합**(verifier r4 M-8).
 * Rollup 의 `importers` 는 정적 간선만 담고 동적 `import()` 는 `dynamicImporters` 에 따로 있다.
 * 정적만 세던 동안, TS 가 보지 않는 파일(`src/*.js` · `index.html` 인라인 스크립트)의 동적
 * `import('openapi-fetch')` 가 다섯 게이트를 모두 지났다(실측).
 */
export function graphImporters(info) {
  return [...(info?.importers ?? []), ...(info?.dynamicImporters ?? [])];
}

/**
 * 그 파일이 속한 **패키지의 이름** — 위로 올라가며 `name` 이 있는 첫 `package.json` 을 읽는다
 * (패키지 안쪽의 `dist/package.json` 처럼 `name` 없는 것은 지나친다).
 *
 * 왜 이름인가(verifier r4 M-9): 디렉터리 이름으로 식별하면 npm 별칭 의존
 * (`"of2": "npm:openapi-fetch@^0.17.0"`)이 **같은 코드**를 `node_modules/of2` 에 설치해 두 층이
 * 모두 그것을 클라이언트 패키지로 보지 못했다(실측: 다섯 게이트 0 · dist 에 손 경로). 정체는
 * 디렉터리 이름이 아니라 그 패키지가 선언한 이름이다.
 */
export function packageOf(filePath, { stopAt, readJson } = {}) {
  let current = dirname(filePath);
  const limit = stopAt ?? sep;
  for (let depth = 0; depth < 64; depth += 1) {
    const manifest = join(current, 'package.json');
    const parsed = readJson ? readJson(manifest) : readManifest(manifest);
    if (parsed !== undefined && typeof parsed.name === 'string' && parsed.name.length > 0) {
      return { dir: current, name: parsed.name };
    }
    const parent = dirname(current);
    if (parent === current || current === limit) {
      return undefined;
    }
    current = parent;
  }
  return undefined;
}

/** 이름만 필요한 자리를 위한 얇은 포장. */
export function packageNameOf(filePath, options) {
  return packageOf(filePath, options)?.name;
}

function readManifest(manifestPath) {
  try {
    return JSON.parse(readFileSync(manifestPath, 'utf8'));
  } catch {
    return undefined;
  }
}

export function apiImporterViolation(importerRealPath, { apiPackageDir, callSitePath }) {
  if (apiPackageDir !== undefined && isInsideRoot(importerRealPath, apiPackageDir)) {
    return undefined;
  }
  if (importerRealPath === callSitePath) {
    return undefined;
  }
  return importerRealPath;
}

/**
 * 허용 뿌리 — **집합 등식으로 `module-graph-boundary.test.mjs` 가 잠근다**(code-review r3 H-1).
 * 여기 한 줄을 더하면 경계의 **범위**가 조용히 넓어지고 다른 층은 그것을 보지 못한다(실측:
 * legacy 뿌리 한 줄 + `?raw` import → 다섯 층 전부 초록 · dist 에 원문). 그래서 이 함수의
 * 반환 집합 자체가 test 의 단언 대상이다.
 */
export function boundaryRoots(webRootReal) {
  const roots = [resolve(webRootReal, 'src'), resolve(webRootReal, 'index.html')];
  const nodeModules = resolve(webRootReal, 'node_modules');
  if (existsSync(nodeModules)) {
    roots.push(realpathSync(nodeModules));
  }
  return roots;
}

/**
 * 빌드가 **내보내는 자산**(v r2 M-2 · r4 M-2). 모듈 그래프 술어는 `new URL(…, import.meta.url)`
 * 로 실린 파일을 보지 못한다 — 그것은 모듈 id 가 아니라 **자산**이다(실측: `rows.txt` 가
 * `dist/assets/` 로 나갔고 다섯 층이 초록이었다). 내보내는 쪽에서 같은 술어를 한 번 더 건다.
 *
 * **종류 허용 목록에서 `css` 를 걷었다**(v r4 M-2): `.css` 는 허용 종류였고 데이터 판정을 하지
 * 않았으므로, 이미 그래프에 있는 CSS 가 `url('./rows.css')` 로 12KB JSON 을 가리키면 그것이
 * `dist/assets/rows-*.css` 로 나갔다(실측). 이제 CSS 자산은 **진입 chunk 가 가져온 것**
 * (`viteMetadata.importedCss`)만 받는다 — 그 밖의 CSS 는 종류와 무관하게 위반이다.
 */
const ALLOWED_ASSET_EXTENSIONS = new Set(['js', 'mjs', 'map', 'html', 'ico']);

export function isAllowedAssetKind(fileName) {
  const match = /\.([A-Za-z0-9]+)$/.exec(fileName);
  return match !== null && ALLOWED_ASSET_EXTENSIONS.has(match[1].toLowerCase());
}

/** chunk 들이 **가져온** CSS 전수 — Vite 가 chunk 마다 그 집합을 적는다. */
export function importedCssOf(bundle) {
  const names = new Set();
  for (const one of Object.values(bundle)) {
    if (one.type !== 'chunk') {
      continue;
    }
    for (const css of one.viteMetadata?.importedCss ?? []) {
      names.add(css);
    }
  }
  return names;
}

/**
 * 번들이 내보내는 자산 가운데 **받지 않는 것**. CSS 는 진입 chunk 가 가져온 것만,
 * 그 밖은 종류 허용 목록으로 가린다.
 */
export function assetOffenders(bundle) {
  const imported = importedCssOf(bundle);
  return Object.values(bundle)
    .filter((one) => one.type === 'asset')
    .filter((one) =>
      one.fileName.toLowerCase().endsWith('.css')
        ? !imported.has(one.fileName)
        : !isAllowedAssetKind(one.fileName),
    )
    .map((one) => one.fileName);
}

export function moduleGraphBoundary({ webRoot = process.cwd() } = {}) {
  return {
    name: 'bidvector:module-graph-boundary',
    generateBundle(_options, bundle) {
      const offenders = assetOffenders(bundle);
      if (offenders.length > 0) {
        this.error(
          `번들이 **받지 않는 자산**을 내보낸다 — 데이터 모양은 V2 계약에서만 온다 ` +
            `(ADR 0011 D-2). new URL(…, import.meta.url) 로 실린 파일과 CSS url() 이 가리킨 ` +
            `데이터가 이 자리에 나타난다:\n` +
            offenders.map((line) => `  - ${line}`).join('\n'),
        );
      }
    },
    buildEnd() {
      const webRootReal = realpathSync(webRoot);
      const allowedRoots = boundaryRoots(webRootReal);
      const offenders = [];

      // 집합 등식(ADR 0011 D-2)을 이 그래프에서도 센다 — 메인·워커 각각의 하위 빌드에서.
      // 식별은 디렉터리 이름이 아니라 **패키지 이름**이다(r4 M-9).
      const callSitePath = resolve(webRootReal, API_CALL_SITE);
      const apiOffenders = [];
      const dataOffenders = [];

      for (const rawId of this.getModuleIds()) {
        const { kind, path } = classifyModuleId(rawId);
        if (kind === 'virtual') {
          continue;
        }
        if (kind === 'relative') {
          offenders.push(`${rawId} → 해석되지 않은 상대 경로(어느 파일인지 알 수 없다)`);
          continue;
        }
        let realPath;
        try {
          realPath = realpathSync(path);
        } catch {
          // 절대 경로인데 디스크에 없다 — 분류할 수 없으므로 통과시키지 않는다(fail-closed).
          offenders.push(`${rawId} → 디스크에 없는 절대 경로(분류 불가)`);
          continue;
        }
        if (violatesBoundary(realPath, allowedRoots)) {
          offenders.push(`${rawId} → ${realPath}`);
          continue;
        }
        if (!isAllowedModuleKind(realPath)) {
          dataOffenders.push(`${rawId} → ${realPath}`);
          continue;
        }

        // 이 모듈이 클라이언트 패키지 안이면, 그것을 **패키지 밖에서** 끌어온 자리를 전수로 본다.
        const owningPackage = packageOf(realPath, { stopAt: webRootReal });
        if (owningPackage?.name === API_CLIENT_PACKAGE) {
          const apiPackageDir = owningPackage.dir;
          for (const importer of graphImporters(this.getModuleInfo(rawId))) {
            const importerClass = classifyModuleId(importer);
            let importerReal;
            if (importerClass.kind === 'file') {
              try {
                importerReal = realpathSync(importerClass.path);
              } catch {
                importerReal = undefined;
              }
            }
            if (importerReal === undefined) {
              apiOffenders.push(`${importer} → 해석 불가(어느 파일인지 알 수 없다)`);
              continue;
            }
            const violation = apiImporterViolation(importerReal, { apiPackageDir, callSitePath });
            if (violation !== undefined) {
              apiOffenders.push(`${violation} → ${rawId}`);
            }
          }
        }
      }

      if (offenders.length > 0) {
        this.error(
          `번들 모듈 그래프가 web/src·web/node_modules·index.html 밖 파일을 담았다 (ADR 0011 D-3):\n` +
            offenders.map((line) => `  - ${line}`).join('\n'),
        );
      }

      if (dataOffenders.length > 0) {
        this.error(
          `번들 모듈 그래프가 **아는 종류가 아닌 모듈**을 담았다 — 코드·스타일만 번들의 입력이고 ` +
            `데이터 모양은 V2 계약에서만 온다(ADR 0011 D-2). 경로가 node_modules 안이면 의존성 ` +
            `올림을 먼저 보라(그 패키지가 데이터를 import 한다):\n` +
            dataOffenders.map((line) => `  - ${line}`).join('\n'),
        );
      }

      if (apiOffenders.length > 0) {
        this.error(
          `번들 모듈 그래프에서 ${API_CLIENT_PACKAGE} 를 호출 자리(${API_CALL_SITE}) 밖에서 끌어온다 ` +
            `— 집합 등식이 깨졌다 (ADR 0011 D-2):\n` +
            apiOffenders.map((line) => `  - ${line}`).join('\n'),
        );
      }
    },
  };
}
