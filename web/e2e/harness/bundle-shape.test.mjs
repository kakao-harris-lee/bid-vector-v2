import { describe, expect, it } from 'vitest';

import { assertDistRoot, bundleAssetPaths, EXPECTED_ASSET_SHAPE } from './bundle-shape.mjs';

/**
 * v r4 M-2 · CR r4 L-5 — 여정의 자산 허용 집합이 **산출물에서 그냥 파생**되면 자산이 늘 때
 * 양쪽이 함께 늘어 아무것도 깨지지 않는다(실측: `url('./rows.css')` 가 낸 데이터 자산을 그
 * 집합이 받아 줬다). 그래서 모양을 못박았고, 그 술어를 여기서 잠근다.
 */
const file = (name) => ({ name, isFile: () => true });
const dir = (name) => ({ name, isFile: () => false });

describe('번들 자산의 모양', () => {
  it('진입 js 하나 · css 하나면 그 경로를 낸다', () => {
    expect(bundleAssetPaths([file('index-abc.js'), file('index-abc.css')])).toStrictEqual([
      '/assets/index-abc.css',
      '/assets/index-abc.js',
    ]);
  });

  it('자산이 하나 늘면 **거기서 멈춘다** — 이 단언이 증가를 잡는 자리다', () => {
    expect(() => bundleAssetPaths([file('index-abc.js'), file('index-abc.css'), file('rows-9f1.css')])).toThrow(
      /번들 자산의 모양이/u,
    );
  });

  it('css 가 없으면 멈춘다 — 스타일이 사라진 것도 모양 변화다', () => {
    expect(() => bundleAssetPaths([file('index-abc.js')])).toThrow(/번들 자산의 모양이/u);
  });

  it('빈 디렉터리는 멈추고 사유를 적는다 — 전수가 0 이면 아래 단언이 공허해진다', () => {
    expect(() => bundleAssetPaths([])).toThrow(/\(없음\)/u);
  });

  it('디렉터리가 섞이면 먼저 가른다 — 이름이 경로처럼 들어간다', () => {
    expect(() => bundleAssetPaths([file('index-abc.js'), file('index-abc.css'), dir('nested')])).toThrow(
      /디렉터리가 있다/u,
    );
  });

  it('기대 모양이 바뀌면 그것은 설계 변경이다 — 수를 여기서 고정한다', () => {
    expect(EXPECTED_ASSET_SHAPE).toStrictEqual({ js: 1, css: 1 });
  });
});

/**
 * `dist` 루트 모양(M7/7E-2, OPEN ②) — `publicDir` 복사물이 뜨는 자리를 센다. 자산 모양 단언이
 * `dist/assets` 만 보던 사각이다.
 */
describe('dist 루트 모양', () => {
  const file = (name) => ({ name, isFile: () => true });
  const dir = (name) => ({ name, isFile: () => false });

  it('진입 문서와 자산 디렉터리 둘이면 통과한다', () => {
    expect(assertDistRoot([file('index.html'), dir('assets')])).toStrictEqual(['assets', 'index.html']);
  });

  it('복사된 파일이 하나라도 있으면 그 이름을 적어 멈춘다', () => {
    expect(() => assertDistRoot([file('index.html'), dir('assets'), file('rows.json')])).toThrow(/rows\.json/u);
  });

  it('복사된 디렉터리도 센다', () => {
    expect(() => assertDistRoot([file('index.html'), dir('assets'), dir('static')])).toThrow(/static/u);
  });

  it('빈 루트는 통과하지 않는다 — 빌드가 아무것도 내지 않은 것도 사실이다', () => {
    expect(() => assertDistRoot([])).toThrow(/없음/u);
  });
});
