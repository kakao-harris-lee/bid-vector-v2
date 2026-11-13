# M11 / gates-web-read — 명령과 실측

실행 자리는 `web/`(명시가 없으면)이고 base 는 `94ee445b50cafa669af435e651d811cc551c67e8` 다.

## 착수 실측

**(7) 타입 단언 규칙 — 오늘 위반 0 인가.** 규칙
(`@typescript-eslint/consistent-type-assertions`, `assertionStyle: 'as'` ·
`objectLiteralTypeAssertions: 'never'`)을 `ESLint` 의 `overrideConfig` 로 `src/**` 에 얹어
합성 탐침과 저장소 전수를 함께 쟀다.

| 입력 | 매치 |
| --- | --- |
| `src/**/*.ts(x)` 전수 (저장소 오늘) | **0** |
| `{…} as const satisfies Record<…>` — 오늘의 표 꼴 | **0** |
| `[…] as const` | **0** |
| `{…} as Record<…>` — 이 항목이 막으려는 꼴 | **1** (`unexpectedObjectTypeAssertion`) |
| `<string>"a"` — 각괄호 꼴 | **1** (`as`) |
| `"a" as string` — 객체 리터럴이 아닌 단언 | **0** |
| `{…} satisfies T` | **0** |

즉 규칙은 **오늘의 표를 하나도 붉히지 않으면서** 그 표를 단언으로 바꿔치우는 꼴만 든다.
`as const` 가 면제라는 것이 이 항목이 서는 전제이고, 그것을 추측이 아니라 돌려서 쟀다.

**⑩ 데이터 파일 처방 — 오탐과 참 양성.** `git ls-files web` 추적 파일 **138** 가운데 AST 대상이
아닌 **데이터 파일 11** 에 처방(본문에서 `#` 로 시작하는 줄 제외 → allowlist 값 차감 → 단어 토큰)을
적용했다.

| 대상 | 매치 |
| --- | --- |
| 데이터 파일 11 전수 (처방 그대로) | **0** — 오탐 없음 |
| 같은 전수에서 `#` 줄 제외만 뺌 | **1** — 등록부의 주석 산문 한 줄 |
| P6 확장자 단독 값 | **1** |
| P7 별표 glob | **1** |
| P7b 중괄호 glob | **2** |
| P7c 확장자 목록 값 | **2** |
| 기존에 이미 들던 `이름.kt` 꼴 | **1** (회귀 없음) |

verifier r6 의 수치가 이 트리에서 그대로 선다.

**⑤ 런타임 층 — 원형이 서는가.** vitest setup 에서 읽기 API 를 감싸 **해석된 실경로**가 Kotlin
확장자(대소문자 무시)면 호출자를 보고, 소유 모듈이 아니면 던지는 원형을 세워 쟀다.

| 입력 | 결과 |
| --- | --- |
| 소유 모듈의 읽기 (`readKotlinSource('basis')`) | **통과** |
| 소비자가 절대 경로로 직접 읽기 | **던진다** |
| 별칭 import(`import { readFileSync as x } from 'fs'`) | **던진다** |
| 심볼릭 링크(`…/policy-source.txt` → Kotlin 원문) — ⑧ | **던진다** |
| 그 밖의 읽기(`package.json`) | **통과** |
| **전건에 얹음** — `vitest run` 42 파일 | **913 passed · 오탐 0** |

비용: 전건 **16.45s → 18.97s**(원형 기준). 통제군(같은 트리, 층 없음)과 같은 실행 수다.

**⑥ 모집단 — 한 홉인가.** 오늘의 모집단 수집은 변수 수집이 `readKotlinSource(…)` 를 **초기화식
그 자체**로 가진 선언만 담고, 인자 판정이 호출의 **최상위 인자**만 본다. 즉 두 홉 이상·별칭·
`.trim()`·spread·콜백은 모집단 밖이다 — 구현을 읽어 확인했고 `design-review.md`(M10) §4 (3)
미달 ⑥ 의 실측과 같다. **⑤ 층은 이 축에 닿지 않는다**(여는 호출이 그 사이에 없다).

## acceptance (전건)

라운드마다 결과만 적는다.
</content>
