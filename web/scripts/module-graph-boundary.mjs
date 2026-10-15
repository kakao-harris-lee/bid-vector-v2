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
import { existsSync, realpathSync } from 'node:fs';
import { resolve, sep } from 'node:path';

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

export function isInsideRoot(candidate, root) {
  return candidate === root || candidate.startsWith(root + sep);
}

/** 허용 뿌리 어디에도 속하지 않으면 위반. 비교는 realpath 끼리 한다(심볼릭 링크 무력화). */
export function violatesBoundary(realPath, allowedRoots) {
  return !allowedRoots.some((root) => isInsideRoot(realPath, root));
}

/** 생성 타입 클라이언트 패키지와 그 **유일한** 호출 자리(ADR 0011 D-2·D-3). */
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

export function moduleGraphBoundary({ webRoot = process.cwd() } = {}) {
  return {
    name: 'bidvector:module-graph-boundary',
    buildEnd() {
      const webRootReal = realpathSync(webRoot);
      const allowedRoots = boundaryRoots(webRootReal);
      const offenders = [];

      // 집합 등식(ADR 0011 D-2)을 이 그래프에서도 센다 — 메인·워커 각각의 하위 빌드에서.
      const apiPackageRoot = resolve(webRootReal, 'node_modules', API_CLIENT_PACKAGE);
      const apiPackageDir = existsSync(apiPackageRoot) ? realpathSync(apiPackageRoot) : undefined;
      const callSitePath = resolve(webRootReal, API_CALL_SITE);
      const apiOffenders = [];

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

        // 이 모듈이 클라이언트 패키지 안이면, 그것을 **패키지 밖에서** 끌어온 자리를 전수로 본다.
        if (apiPackageDir !== undefined && isInsideRoot(realPath, apiPackageDir)) {
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
