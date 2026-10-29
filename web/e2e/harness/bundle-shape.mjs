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
