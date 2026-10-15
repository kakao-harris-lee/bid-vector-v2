import { describe, expect, it } from 'vitest';

import {
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
