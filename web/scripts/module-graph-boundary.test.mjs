import { existsSync, realpathSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  apiImporterViolation,
  boundaryRoots,
  assetOffenders,
  importedCssOf,
  isAllowedAssetKind,
  isAllowedModuleKind,
  graphImporters,
  classifyModuleId,
  isInsideRoot,
  stripQuery,
  violatesBoundary,
} from './module-graph-boundary.mjs';

// W-4 는 플러그인이 두 자리에 **등재**됐는지만 본다(빠른 선행 검사). 플러그인이 **활성**인지는
// 카나리 빌드(W-8)가 직접 재고, 판정 자체(무엇을 위반으로 보는가)는 이 test 가 잠근다.
const SRC = '/repo/web/src';
const NODE_MODULES = '/repo/web/node_modules';
const ROOTS = [SRC, NODE_MODULES];

describe('모듈 그래프 경계 판정', () => {
  it('접미 쿼리를 떼고 파일 경로만 남긴다', () => {
    expect(stripQuery('/repo/web/src/a.ts?worker')).toBe('/repo/web/src/a.ts');
    expect(stripQuery('/repo/web/src/a.ts?raw&x=1')).toBe('/repo/web/src/a.ts');
    expect(stripQuery('/repo/web/src/a.ts')).toBe('/repo/web/src/a.ts');
  });

  it('뒤에 파일이 없는 플러그인 공급 모듈만 가상으로 센다', () => {
    expect(classifyModuleId('\0commonjsHelpers.js').kind).toBe('virtual');
    expect(classifyModuleId('\0vite/modulepreload-polyfill.js').kind).toBe('virtual');
    expect(classifyModuleId('virtual:tailwind').kind).toBe('virtual');
  });

  it('절대 경로는 파일 id 다 — POSIX 와 Windows 드라이브 형 둘 다', () => {
    expect(classifyModuleId('/repo/web/src/main.tsx')).toEqual({
      kind: 'file',
      path: '/repo/web/src/main.tsx',
    });
    // Windows 형을 가상 바구니에 넣으면 그 플랫폼에서 게이트가 공허하게 통과한다.
    expect(classifyModuleId('C:/repo/web/src/x.ts').kind).toBe('file');
    expect(classifyModuleId('C:\\repo\\web\\src\\x.ts').kind).toBe('file');
    // Rollup 의 가상 접두가 붙은 **실파일** id(commonjs 변환)도 파일로 본다.
    expect(classifyModuleId('\0/repo/web/node_modules/react/index.js?commonjs-module')).toEqual({
      kind: 'file',
      path: '/repo/web/node_modules/react/index.js',
    });
  });

  it('해석되지 않은 상대 경로는 위반으로 센다 — 모르는 것은 통과시키지 않는다', () => {
    expect(classifyModuleId('../legacy-fe/side.ts').kind).toBe('relative');
    expect(classifyModuleId('./x.ts').kind).toBe('relative');
  });

  it('뿌리 경계는 경로 성분 단위다 — 이름이 접두로 겹치는 이웃은 안쪽이 아니다', () => {
    expect(isInsideRoot('/repo/web/src/a.ts', SRC)).toBe(true);
    expect(isInsideRoot(SRC, SRC)).toBe(true);
    expect(isInsideRoot('/repo/web/src-legacy/a.ts', SRC)).toBe(false);
  });

  it('허용 뿌리 밖 파일은 위반이고 안쪽은 아니다', () => {
    expect(violatesBoundary('/repo/web/src/api/client.ts', ROOTS)).toBe(false);
    expect(violatesBoundary('/repo/web/node_modules/react/index.js', ROOTS)).toBe(false);
    expect(violatesBoundary('/repo/bid-vector/frontend/src/shared/lib.ts', ROOTS)).toBe(true);
    expect(violatesBoundary('/repo/web/legacy-fe/side.ts', ROOTS)).toBe(true);
  });
});

// 경계의 **범위**(허용 뿌리)는 여기서 집합 등식으로 잠근다(code-review r3 H-1). 뿌리에 한 줄을
// 더하면 다른 다섯 층은 그것을 보지 못하고(실측: legacy 뿌리 + `?raw` → 전부 초록 · dist 에
// 원문) 이 test 만 깨진다 — 「판정 술어와 허용 뿌리를 함께 바꿔야 한다」가 그래서 참이 된다.
const WEB_ROOT = realpathSync(resolve(dirname(fileURLToPath(import.meta.url)), '..'));

describe('경계의 허용 뿌리', () => {
  it('설치본이 있는 실제 트리에서 집합이 정확히 셋이다', () => {
    const expected = new Set([
      resolve(WEB_ROOT, 'src'),
      resolve(WEB_ROOT, 'index.html'),
      realpathSync(resolve(WEB_ROOT, 'node_modules')),
    ]);
    expect(existsSync(resolve(WEB_ROOT, 'node_modules'))).toBe(true);
    expect(new Set(boundaryRoots(WEB_ROOT))).toEqual(expected);
  });

  it('설치본이 없는 트리에서는 src 와 진입 문서 둘뿐이다', () => {
    const bare = resolve(WEB_ROOT, 'scripts');
    expect(new Set(boundaryRoots(bare))).toEqual(
      new Set([resolve(bare, 'src'), resolve(bare, 'index.html')]),
    );
  });
});

describe('클라이언트 패키지의 importer 판정', () => {
  const opts = {
    apiPackageDir: '/repo/web/node_modules/openapi-fetch',
    callSitePath: '/repo/web/src/api/client.ts',
  };

  it('승인된 호출 자리와 패키지 내부 import 는 위반이 아니다', () => {
    expect(apiImporterViolation('/repo/web/src/api/client.ts', opts)).toBeUndefined();
    expect(
      apiImporterViolation('/repo/web/node_modules/openapi-fetch/dist/index.js', opts),
    ).toBeUndefined();
  });

  it('그 밖의 자리는 위반이다 — 같은 디렉터리의 이웃도', () => {
    expect(apiImporterViolation('/repo/web/src/api/other.ts', opts)).toBe(
      '/repo/web/src/api/other.ts',
    );
    expect(apiImporterViolation('/repo/web/src/hand.ts', opts)).toBe('/repo/web/src/hand.ts');
  });
});

describe('그래프의 importer 수집', () => {
  it('정적 간선과 동적 간선을 합쳐 센다', () => {
    expect(
      graphImporters({ importers: ['/a.ts'], dynamicImporters: ['/b.js'] }),
    ).toEqual(['/a.ts', '/b.js']);
  });

  it('한쪽이 없거나 모듈 정보가 없어도 빈 배열로 떨어진다', () => {
    expect(graphImporters({ importers: ['/a.ts'] })).toEqual(['/a.ts']);
    expect(graphImporters({ dynamicImporters: ['/b.js'] })).toEqual(['/b.js']);
    expect(graphImporters(undefined)).toEqual([]);
  });
});

/**
 * v M-4 — 모듈 **종류** 술어. 앞 판은 데이터 확장자를 열거했고 `rows.txt?raw` 가 그 열거를
 * 비켜 갔다(실측). 이제 아는 종류만 받으므로 **모르는 종류가 기본 위반**이다.
 */
describe('번들에 들어와도 되는 모듈 종류', () => {
  it('코드와 스타일은 받는다', () => {
    for (const path of ['/w/src/a.ts', '/w/src/a.tsx', '/w/src/a.css', '/w/node_modules/p/i.mjs', '/w/index.html']) {
      expect(isAllowedModuleKind(path), path).toBe(true);
    }
  });

  it('데이터·텍스트·확장자 없음은 **열거하지 않아도** 위반이다', () => {
    for (const path of [
      '/w/src/a.json',
      '/w/src/a.JSON',
      '/w/node_modules/p/data.yaml',
      '/w/src/rows.csv',
      '/w/src/rows.txt',
      '/w/src/notes.md',
      '/w/src/rows',
    ]) {
      expect(isAllowedModuleKind(path), path).toBe(false);
    }
  });

  it('쿼리 접미는 먼저 떼고 종류를 본다', () => {
    expect(isAllowedModuleKind(stripQuery('/w/src/rows.txt?raw'))).toBe(false);
    expect(isAllowedModuleKind(stripQuery('/w/src/a.ts?worker'))).toBe(true);
  });

  it('이름에 데이터 확장자가 들어간 코드 모듈은 받는다', () => {
    expect(isAllowedModuleKind('/w/src/a.json.ts')).toBe(true);
    expect(isAllowedModuleKind('/w/src/jsonHelpers.ts')).toBe(true);
  });
});

/**
 * v r2 M-2 — 모듈 그래프는 `new URL(…, import.meta.url)` 로 실린 파일을 보지 못한다(자산은
 * 모듈 id 가 아니다). 내보내는 쪽에서 같은 술어를 한 번 더 건다.
 *
 * **이 층은 둘째 그물이다**: `src` 안 파일은 W-4 의 파일 종류 허용 목록이 먼저 막으므로 그
 * 경로로는 빌드 수준 양성 대조가 재현되지 않는다(실측). 이 층이 받는 것은 W-4 가 보지 못하는
 * 자리 — 의존성이 내보내는 자산이다. 그래서 술어를 단위로 잠근다.
 */
describe('내보내도 되는 자산 종류', () => {
  it('번들·맵·문서는 종류로 받는다', () => {
    for (const name of ['assets/index-abc.js', 'index.html', 'favicon.ico', 'assets/index-abc.js.map']) {
      expect(isAllowedAssetKind(name), name).toBe(true);
    }
  });

  /** v r4 M-2 — `.css` 는 **종류로** 받지 않는다. 진입 chunk 가 가져온 것만 받는다. */
  it('css 는 종류 허용 목록에 없다 — 그 축은 importedCss 가 진다', () => {
    expect(isAllowedAssetKind('assets/index-abc.css')).toBe(false);
  });

  it('데이터·텍스트·확장자 없음은 받지 않는다', () => {
    for (const name of ['assets/rows-abc.txt', 'assets/data-abc.json', 'assets/x.yaml', 'assets/rows']) {
      expect(isAllowedAssetKind(name), name).toBe(false);
    }
  });
});

/**
 * v r4 M-2 — CSS `url('./rows.css')` 가 12KB JSON 을 `dist/assets/rows-*.css` 로 내보냈고
 * 세 층이 모두 초록이었다(실측): W-4 는 `.css` 를 허용 종류로 보고, 자산 술어도 확장자만 봤고,
 * 여정의 허용 집합은 **그 산출물 자체에서** 파생됐다. 이제 CSS 는 **진입 chunk 가 가져온
 * 것**만 받는다.
 */
describe('CSS 자산은 진입 chunk 가 가져온 것만', () => {
  const entryChunk = (importedCss) => ({
    type: 'chunk',
    fileName: 'assets/index-abc.js',
    viteMetadata: { importedCss: new Set(importedCss) },
  });

  it('chunk 들이 가져온 CSS 를 모은다', () => {
    const bundle = {
      a: entryChunk(['assets/index-abc.css']),
      b: { type: 'chunk', fileName: 'assets/lazy.js', viteMetadata: { importedCss: new Set(['assets/lazy.css']) } },
    };

    expect([...importedCssOf(bundle)].sort()).toStrictEqual(['assets/index-abc.css', 'assets/lazy.css']);
  });

  it('진입이 가져온 CSS 는 받는다', () => {
    const bundle = {
      a: entryChunk(['assets/index-abc.css']),
      b: { type: 'asset', fileName: 'assets/index-abc.css' },
      c: { type: 'asset', fileName: 'index.html' },
    };

    expect(assetOffenders(bundle)).toStrictEqual([]);
  });

  it('가져오지 않은 CSS 는 받지 않는다 — url() 이 가리킨 데이터가 이 자리에 나타난다', () => {
    const bundle = {
      a: entryChunk(['assets/index-abc.css']),
      b: { type: 'asset', fileName: 'assets/index-abc.css' },
      c: { type: 'asset', fileName: 'assets/rows-9f1.css' },
    };

    expect(assetOffenders(bundle)).toStrictEqual(['assets/rows-9f1.css']);
  });

  it('chunk 가 CSS 를 하나도 가져오지 않으면 어떤 CSS 자산도 받지 않는다', () => {
    const bundle = {
      a: entryChunk([]),
      b: { type: 'asset', fileName: 'assets/index-abc.css' },
    };

    expect(assetOffenders(bundle)).toStrictEqual(['assets/index-abc.css']);
  });

  it('데이터 자산은 그대로 받지 않는다', () => {
    const bundle = {
      a: entryChunk(['assets/index-abc.css']),
      b: { type: 'asset', fileName: 'assets/rows-abc.json' },
    };

    expect(assetOffenders(bundle)).toStrictEqual(['assets/rows-abc.json']);
  });
});
