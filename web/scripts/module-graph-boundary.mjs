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
export function isNonFileId(id) {
  return id.includes('\0') || !id.startsWith(sep);
}

export function isInsideRoot(candidate, root) {
  return candidate === root || candidate.startsWith(root + sep);
}

/** 허용 뿌리 어디에도 속하지 않으면 위반. 비교는 realpath 끼리 한다(심볼릭 링크 무력화). */
export function violatesBoundary(realPath, allowedRoots) {
  return !allowedRoots.some((root) => isInsideRoot(realPath, root));
}

/** 허용 뿌리: `web/src` · `web/node_modules` · 진입 문서 `web/index.html` 하나. */
export function boundaryRoots(webRootReal) {
  const roots = [resolve(webRootReal, 'src')];
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
      const entryHtml = resolve(webRootReal, 'index.html');
      const offenders = [];

      for (const rawId of this.getModuleIds()) {
        const id = stripQuery(rawId);
        if (isNonFileId(id)) {
          continue;
        }
        let realPath;
        try {
          realPath = realpathSync(id);
        } catch {
          // 디스크에 없는 절대 경로 id — 파일이 아니므로 legacy 파일을 실어 올 수 없다.
          continue;
        }
        if (realPath !== entryHtml && violatesBoundary(realPath, allowedRoots)) {
          offenders.push(`${rawId} → ${realPath}`);
        }
      }

      if (offenders.length > 0) {
        this.error(
          `번들 모듈 그래프가 web/src·web/node_modules·index.html 밖 파일을 담았다 (ADR 0011 D-3):\n` +
            offenders.map((line) => `  - ${line}`).join('\n'),
        );
      }
    },
  };
}
