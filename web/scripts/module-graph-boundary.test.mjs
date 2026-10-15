import { describe, expect, it } from 'vitest';

import {
  isInsideRoot,
  isNonFileId,
  stripQuery,
  violatesBoundary,
} from './module-graph-boundary.mjs';

// W-4 는 플러그인이 `vite.config.ts` 에 **등재**됐는지만 본다. 판정 자체(무엇을 위반으로 보는가)는
// 이 test 가 잠근다 — 본문을 비워 통과시키려면 두 층을 함께 바꿔야 한다.
const SRC = '/repo/web/src';
const NODE_MODULES = '/repo/web/node_modules';
const ROOTS = [SRC, NODE_MODULES];

describe('모듈 그래프 경계 판정', () => {
  it('접미 쿼리를 떼고 파일 경로만 남긴다', () => {
    expect(stripQuery('/repo/web/src/a.ts?worker')).toBe('/repo/web/src/a.ts');
    expect(stripQuery('/repo/web/src/a.ts?raw&x=1')).toBe('/repo/web/src/a.ts');
    expect(stripQuery('/repo/web/src/a.ts')).toBe('/repo/web/src/a.ts');
  });

  it('가상 모듈과 bare specifier 는 파일이 아니다', () => {
    expect(isNonFileId('\0vite/preload-helper')).toBe(true);
    expect(isNonFileId('virtual:tailwind')).toBe(true);
    expect(isNonFileId('/repo/web/src/main.tsx')).toBe(false);
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
