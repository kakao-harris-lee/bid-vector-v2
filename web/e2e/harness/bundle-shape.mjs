// 여정이 받아 줄 **같은 출처 자산**의 모양(v r4 M-2 · CR r4 L-5·L-10).
//
// 왜 모양을 못박는가: 허용 집합을 산출물 디렉터리에서 그냥 읽으면 자산이 늘 때 **양쪽이 함께
// 늘어** 아무것도 깨지지 않는다(앞 판이 그랬다). 이 화면 다발은 진입 js 하나와 그것이 가져온
// css 하나를 낸다 — 번들에 자산이 하나라도 더 들어오면 **여기서 먼저 멈춘다**. 상한을 올릴
// 일이 생기면 그것은 설계 변경이고, 이 파일을 고치는 diff 로 드러나야 한다.
//
// 모듈이 따로인 이유는 `container-marker.mjs` 와 같다: `journey-up.mjs` 는 적재되면 곧 `main()`
// 을 돌려 test 가 그 술어만 집어 올 수 없다.

/** 이 빌드가 내야 하는 자산의 수 — 종류별로 **정확히** 이만큼이다. */
export const EXPECTED_ASSET_SHAPE = { js: 1, css: 1 };

/**
 * `readdirSync(dist/assets, { withFileTypes: true })` 의 결과를 받아 여정의 허용 경로를 낸다.
 * 모양이 어긋나면 **사유를 적어** 던진다(디렉터리가 섞이면 이름이 경로처럼 들어가므로 먼저 가른다).
 */
export function bundleAssetPaths(entries) {
  const dirNames = entries.filter((one) => !one.isFile()).map((one) => one.name);
  if (dirNames.length > 0) {
    throw new Error(`번들 자산 자리에 디렉터리가 있다(${dirNames.join(', ')}) — 모양 단언이 흐려진다`);
  }

  const names = entries.map((one) => one.name).sort();
  const counted = {
    js: names.filter((one) => one.endsWith('.js')).length,
    css: names.filter((one) => one.endsWith('.css')).length,
  };
  const total = EXPECTED_ASSET_SHAPE.js + EXPECTED_ASSET_SHAPE.css;
  if (names.length !== total || counted.js !== EXPECTED_ASSET_SHAPE.js || counted.css !== EXPECTED_ASSET_SHAPE.css) {
    throw new Error(
      `번들 자산의 모양이 {js ${EXPECTED_ASSET_SHAPE.js} · css ${EXPECTED_ASSET_SHAPE.css}} 이 ` +
        `아니다 — 받은 것: ${names.length === 0 ? '(없음)' : names.join(', ')}. 자산이 늘었다면 ` +
        '그것이 무엇이고 화면이 왜 읽는지 먼저 답해야 한다(ADR 0011 D-2).',
    );
  }
  return names.map((one) => `/assets/${one}`);
}

/**
 * `dist` **루트**가 내야 하는 항목 — 진입 문서 하나와 자산 디렉터리 하나다(M7/7E-2, OPEN ②).
 *
 * 왜 루트까지 세는가: `publicDir` 가 켜지면 그 디렉터리의 파일이 **번들 그래프를 거치지 않고**
 * `dist` 루트로 그대로 복사된다. 자산 모양 단언은 `dist/assets` 만 보므로 그 복사물을 보지 못했다
 * (7E-1 checklist §4 ②). 설정 쪽 술어(Vite 해석)와 **독립된 층**이다 — 설정을 어떻게 비틀어도
 * 산출물에 파일이 생기면 여기서 센다.
 */
export const EXPECTED_DIST_ROOT = ['assets', 'index.html'];

/** `readdirSync(dist, { withFileTypes: true })` 의 결과를 받아 루트 모양을 단언한다. */
export function assertDistRoot(entries) {
  const names = entries.map((one) => one.name).sort();
  const expected = [...EXPECTED_DIST_ROOT].sort();
  if (names.length !== expected.length || names.some((name, index) => name !== expected[index])) {
    throw new Error(
      `dist 루트 항목이 {${expected.join(', ')}} 이 아니다 — 받은 것: ${
        names.length === 0 ? '(없음)' : names.join(', ')
      }. 번들 그래프를 거치지 않고 복사된 파일이 있다면 그것이 무엇이고 화면이 왜 읽는지 먼저 ` +
        '답해야 한다(ADR 0011 D-2).',
    );
  }
  return names;
}
