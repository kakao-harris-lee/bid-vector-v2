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
 * **공개 표면은 둘이다**(M12 — ⓐ): 읽기 · 키 목록. 걷기 함수는 내부 모듈에 있고, 그 **이름**이
 * 공유 모듈 밖에 나타나는지를 게이트가 **전 추적 파일**에서 센다.
 *
 * **읽기가 원문을 내주지 않는다**(M12 — ⓐ). 앞 판은 걷힌 **문자열**을 돌려주고 소비자가 추출
 * 함수에 넘기기 전에 출처 단언을 **부르기로 약속**했다. 약속은 **잊을 수 있는 호출**이고, 그래서
 * 방어가 「추출 함수 전수가 그 단언을 부르는가」라는 **모집단 추적**에 매달렸다 — 두 홉으로 받는
 * 함수를 그 추적이 보지 못한 것이 이 slice 가 받은 축이다. 홉을 더 쫓는 길은 끝나지 않는다
 * (M10/10D 가 그 자리에서 재작업 상한을 넘겼다).
 *
 * 그래서 **방향을 뒤집는다**: 소비자가 원문을 가져가는 것이 아니라 **추출 함수를 넘긴다**. 읽기가
 * 열고 걷고 그 함수를 부르므로 「단언을 부르는가」라는 질문이 **사라진다** — 부를 자리가 없으면
 * 텍스트도 없다. 모집단이라는 개념이 없어지므로 꼴을 세는 축이 함께 사라진다.
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
// 악수 슬롯은 층 모듈에 있다 — 읽기와 커널이 **둘 다** 그 층을 이미 쓴다(호출자 판정).
import { runWithInFlightText } from './kotlin-read-guard.mjs';

/**
 * **읽기 집합 — `web/` 안에서 `.kt` 경로를 아는 유일한 자리다.**
 *
 * 비공개다. 내보내면 소비자가 그 경로로 원문을 다시 열 수 있어 사본의 길이 그대로 남는다.
 * 여기 전수로 서는 덕에 「읽기 집합 전부가 읽힌다」를 게이트 test 가 들 수 있다 — 앞 판까지
 * 그 목록은 어디에도 전수로 없었고, 확인하려면 손으로 적어야 했다.
 *
 * **여덟이다.** M10 때 일곱으로 줄었다 — 착수 때 여덟이었고 그중 명령 축(`BidCommand.kt`)은
 * 10B(#149)가 openapi `enum` 으로 올려 web 이 더 이상 읽지 않는다(읽지 않는 파일을 표에 남기면
 * 「읽기 집합」이 거짓이 된다). M13/13D 가 **전이표**를 더해 다시 여덟이다: 내려놓기 둘의 출발
 * 상태와 주체를 계약이 응답으로 내주지 않으므로(응답에 열린 명령 집합 칸이 없다) 화면이 사본을
 * 들고, 그 사본을 정본에 묶는 등식이 이 읽기를 쓴다.
 */
const KOTLIN_SOURCES = {
  basis: 'shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt',
  provenance: 'procurement/src/main/kotlin/bidvector/procurement/ResolvedBaseAmount.kt',
  provenanceWire: 'app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt',
  openingEndpoints: 'app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt',
  noticeEndpoints: 'app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt',
  detailAxes: 'workflow/src/main/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCase.kt',
  progressPolicy: 'workflow/src/main/kotlin/bidvector/workflow/opening/OpeningProgressPolicyData.kt',
  bidTransitions: 'bidding/src/main/kotlin/bidvector/bidding/BidTransitions.kt',
};

/** 읽기 집합의 **키 전수** — 경로가 아니라 키다(위 표를 내보내지 않는 사유와 같다). */
export const KOTLIN_SOURCE_KEYS = Object.keys(KOTLIN_SOURCES);

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

/**
 * **읽기 집합의 키 하나를 열어 걷고, 넘겨받은 추출 함수에 그 텍스트를 먹인다.**
 *
 * 소비자가 Kotlin 원문에 닿는 **유일한** 길이고, 그 닿음은 **넘긴 함수의 매개변수 하나**로
 * 끝난다 — 원문이 소비자 쪽에 이름으로 남지 않는다.
 *
 * `rest` 는 추출 함수에 그대로 전달된다(어휘 등식의 `name` 처럼 추출이 받는 두 번째 인자가
 * 있다). 추출이 여럿을 내야 하면 **한 번 열어 한 함수가 여럿을 돌려주는** 꼴로 쓴다 — 같은
 * 키를 세 번 여는 것보다 읽기도 한 번이다.
 *
 * **결과가 원문 자체이면 던진다.** 항등 함수(`(text) => text`)로 원문을 밖으로 빼내는 **가장
 * 평범한** 길을 좁히는 자리다. 다만 이 후조건이 보는 것은 **반환값 자체의 문자열 동일성**뿐이다.
 * 값이 같은 사본(`slice(0)` · 템플릿 · `String()`)은 들지만, **원문을 담아 내보내는 꼴은 지나간다**
 * (r1 — v M-1 실측 넷: `(t) => [t]` · `(t) => ({ source: t })` · `async (t) => t` · `(t) => () => t`).
 * 그릇·promise·클로저를 열거하는 길은 끝나지 않으므로 **열거하지 않고 등재한다** — 추출은 텍스트를
 * 쥐어야 성립하므로 그 이음매는 추출의 성질이다.
 *
 * @throws 읽기 집합에 없는 키면 — 조용히 빈 문자열을 내면 호출자의 모든 등식이 공허해진다.
 * @throws 추출 함수를 받지 못하면 — 인자를 빠뜨린 호출이 `undefined` 로 조용히 지나가지 않는다.
 * @throws 걷기가 다룰 수 없는 꼴을 만나면(`withoutKotlinComments` 의 계약 그대로).
 * @throws 추출이 원문을 그대로 돌려주면.
 */
export function withKotlinSource(key, extract, ...rest) {
  const relative = KOTLIN_SOURCES[key];
  if (relative === undefined) {
    throw new Error(`읽기 집합에 없는 키다: ${key} — 아는 키는 ${KOTLIN_SOURCE_KEYS.join('·')}`);
  }
  if (typeof extract !== 'function') {
    throw new Error(`추출 함수를 받지 못했다: ${key} — 원문을 가져가는 길이 아니라 함수를 넘기는 길이다`);
  }
  const text = withoutKotlinComments(readFileSync(resolve(repoRoot, relative), 'utf8'));
  // **추출을 악수 안에서 부른다**(r2 — cr HIGH): 그 텍스트를 걸어 두면, `bind` 로 프레임을 세탁해
  // 커널에 손댄 값을 먹이는 길이 커널 쪽 단언에서 막힌다.
  const extracted = runWithInFlightText(text, () => extract(text, ...rest));
  // **비동기 추출은 받지 않는다**(r2 — 팀장 지시로 실측하고 정했다). 악수와 호출자 프레임은 둘 다
  // **그 동기 호출 구간**에만 선다. `await` 뒤에 커널을 부르면 둘 다 이미 닫혀 있어 **문면이
  // 원인을 잘못 말하는 거부**가 났다(실측: 「직접 불렀다」). 거부를 여기로 올려 원인을 말하게 하고,
  // 덤으로 M-1 의 우회 꼴 하나(`async (t) => t` — 실측으로 원문 251 자가 나왔다)가 닫힌다.
  //
  // 병행은 막지 않는다 — 읽기 구간이 동기라 두 읽기가 **겹치지 않는다**(실측: 동시 둘 통과).
  if (typeof extracted?.then === 'function') {
    // 떠도는 거절을 잠재운다(우리가 값을 버리므로 처리자가 없다).
    extracted.then(undefined, () => {});
    throw new Error(
      `비동기 추출은 받지 않는다: ${key} — 악수와 호출자 판정이 **동기 구간**에만 서므로 \`await\` 뒤의 커널 호출은 막힌다(동기 추출로 적어라)`,
    );
  }
  if (extracted === text) {
    throw new Error(
      `추출이 원문을 그대로 돌려줬다: ${key} — **반환값으로는** 원문이 나가지 않는다(추출한 값을 돌려라)`,
    );
  }
  return extracted;
}
