/**
 * **Kotlin 원문 읽기의 공유 한 자리**(M10/gates-web — `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP`).
 *
 * node 에 Kotlin 파서가 없으므로 web 의 게이트 둘은 Kotlin 정본을 **텍스트로** 읽는다(교차언어
 * 어휘 등식 · 개찰 축 하네스의 오퍼레이션 등식). 두 소비자가 주석 걷기를 **각자** 가지고 있었고
 * 둘 다 부분 구현이었다 — 한 벌은 문자열 안의 `//` 를 주석으로 지워 base URL 상수를 절단했고,
 * 다른 벌은 문자열 안의 블록 주석 글자를 줄이고 raw string 을 절단했다. 절단된 값은 등식을
 * **조용히** 지나간다.
 *
 * **경계 한 문장**: 이 모듈은 값을 **조용히 바꾸지 않는다**. 다룰 수 없는 꼴을 만나면 **던진다**.
 * 「조용히 빗나간 값」과 「던진 읽기」 사이에 제3의 결과가 없다.
 *
 * 그래서 표기를 **열거하지 않는다**(「URL 안의 `//`」·「raw string 안의 `//`」마다 정규식을 더하는
 * 꼴 — 그 목록은 다음 꼴에서 또 뒤처진다). 상태 다섯을 지나는 **단일 패스 주사기**로 짓고, 주석
 * 술어와 문자열 술어가 한 자리에서 갈리게 둔다. 읽기 집합에 없는 꼴(raw string)은 읽기를 넓히지
 * 않고 거절한다 — 그 꼴이 생기는 날 RED 로 사람을 부르는 쪽이 이 층의 성질이다.
 *
 * **문자열 안에도 코드가 있다**(템플릿 식 `${…}`). 그 구간의 따옴표를 문자열의 끝으로 읽으면
 * 바로 이 모듈이 없애려던 절단이 그대로 재발한다 — `"${f("//")}"` 가 `"${f("` 가 된다(cr r0
 * HIGH). 식은 중괄호 깊이로 훑어 통째로 넘기고, 깊이 계수가 깨지는 꼴(식 안의 따옴표·주석
 * 표지)은 거절한다. 따옴표 없는 평범한 식은 읽기 집합에 실제로 있으므로 일률 거절하지 않는다.
 *
 * **공개 표면은 셋이다**(r4): 읽기 · 키 목록 · 걷힌 상태 단언. 걷기 함수는 내부 모듈에 있고,
 * 그 **이름**이 공유 모듈 밖에 나타나는지를 게이트가 **전 추적 파일**에서 센다.
 *
 * **이 모듈이 읽기까지 소유한다**(r2 — verifier r1 M-2). 걷기만 공유 자리에 두면 소비자가 원문을
 * 쥔 채 **자기 사본**을 들일 수 있고, 그 변이 둘(별칭 import + `replaceAll` · 별칭 import +
 * `indexOf('//')` 줄 절단)이 r1 의 텍스트 술어를 **초록으로** 지나갔다. 그래서 경로 표를 이
 * 모듈의 **비공개**로 두고 소비자에게는 **키**만 받는다 — 원문이 없으면 걷을 것이 없다.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// 걷기 본체는 **내부 모듈**이다 — 공개 표면에 없으므로 어떤 import 구문으로도 소비자가
// 얻을 수 없다(r4 — verifier r3 H-1). 이 모듈과 그 단위 test 만 참조한다.
import { withoutKotlinComments } from './kotlin-comment-strip.mjs';

/**
 * **읽기 집합 — `web/` 안에서 `.kt` 경로를 아는 유일한 자리다.**
 *
 * 비공개다. 내보내면 소비자가 그 경로로 원문을 다시 열 수 있어 사본의 길이 그대로 남는다.
 * 여기 전수로 서는 덕에 「읽기 집합 전부가 읽힌다」를 게이트 test 가 들 수 있다 — 앞 판까지
 * 그 목록은 어디에도 전수로 없었고, 확인하려면 손으로 적어야 했다.
 *
 * **일곱이다.** 착수 때는 여덟이었고 그중 명령 축(`BidCommand.kt`)은 10B(#149)가 openapi `enum`
 * 으로 올려 web 이 더 이상 읽지 않는다 — 읽지 않는 파일을 표에 남기면 「읽기 집합」이 거짓이 된다.
 */
const KOTLIN_SOURCES = {
  basis: 'shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt',
  provenance: 'procurement/src/main/kotlin/bidvector/procurement/ResolvedBaseAmount.kt',
  provenanceWire: 'app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt',
  openingEndpoints: 'app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt',
  noticeEndpoints: 'app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt',
  detailAxes: 'workflow/src/main/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCase.kt',
  progressPolicy: 'workflow/src/main/kotlin/bidvector/workflow/opening/OpeningProgressPolicyData.kt',
};

/** 읽기 집합의 **키 전수** — 경로가 아니라 키다(위 표를 내보내지 않는 사유와 같다). */
export const KOTLIN_SOURCE_KEYS = Object.keys(KOTLIN_SOURCES);

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * **이 모듈이 내준 텍스트 전부**(M11 — 팀장 판정 (c)).
 *
 * 비공개다. 아래 단언의 기준이 「걷힌 **성질**」이 아니라 **이 집합의 원소인가**이므로, 소비자가
 * 받은 텍스트에 무엇을 하든 — 사본을 덧씌우든 다듬든 — 그 결과는 여기 없다.
 */
const handedOut = new Set();

/**
 * 읽기 집합의 키 하나를 **걷힌 텍스트**로 읽는다. 소비자가 Kotlin 원문을 얻는 유일한 길이다.
 *
 * @throws 읽기 집합에 없는 키면 — 조용히 빈 문자열을 내면 호출자의 모든 등식이 공허해진다.
 * @throws 걷기가 다룰 수 없는 꼴을 만나면(`withoutKotlinComments` 의 계약 그대로).
 */
export function readKotlinSource(key) {
  const relative = KOTLIN_SOURCES[key];
  if (relative === undefined) {
    throw new Error(`읽기 집합에 없는 키다: ${key} — 아는 키는 ${KOTLIN_SOURCE_KEYS.join('·')}`);
  }
  const text = withoutKotlinComments(readFileSync(resolve(repoRoot, relative), 'utf8'));
  handedOut.add(text);
  return text;
}

/**
 * **받은 텍스트가 이 모듈이 내준 그 텍스트인지 단언하고 그대로 돌려준다.** 소비자 추출 함수가
 * 입력 자리에서 부르는 **유일한** 표면이다(걷기 함수 자체는 소비자가 import 하지 않는다).
 *
 * **기준이 성질에서 출처로 바뀌었다**(M11 — 팀장 판정 (c)). 앞 판은 「다시 걸어 같은가」를 보았고,
 * 그래서 **걷힌 상태를 유지하는 사본**(`replaceAll` 로 주석 표지만 지우는 꼴)이 값을 망가뜨리면서
 * 조용히 지나갔다. 더 나쁜 것은 그 약함이 방어를 **모집단 추적**에 매달았다는 점이다 — 「추출 함수
 * 전수가 단언을 부르는가」를 세려면 소비자 쪽 데이터 흐름을 쫓아야 하고, 그 추적이 보지 못하는
 * 꼴(두 홉 이상·별칭·다듬기·spread·콜백)을 라운드마다 세는 일이 생겼다.
 *
 * 「여는 행위」 층(`kotlin-read-guard.mjs`)이 선 뒤로 Kotlin 원문을 얻는 길은 **이 모듈 하나**다.
 * 그래서 기준을 **내준 텍스트와의 동일성**으로 올린다 — 손댄 텍스트는 **어떤 흐름의 꼴로 와도**
 * 여기서 던지므로, 꼴을 세는 축이 사라진다. 이름을 그대로 두는 것은 사유를 이름에 박아 둔 앞 판의
 * 판단을 잇는 것이다(「중복이니 정리」로 조용히 사라지지 않게).
 *
 * **막지 못하는 것**: 이 단언보다 **뒤**에 덧씌우는 사본. 그 자리는 어떤 층도 보지 못한다.
 *
 * @throws 입력이 이 모듈이 내준 텍스트가 아니면 — 읽기를 거치지 않았거나 받은 뒤에 값이 바뀌었다.
 */
export function assertStrippedKotlin(text) {
  if (!handedOut.has(text)) {
    throw new Error(
      '걷힌 Kotlin 텍스트가 아니다 — 읽기가 내준 텍스트와 다르다(읽기를 거치지 않았거나 받은 뒤 값이 바뀌었다)',
    );
  }
  return text;
}
