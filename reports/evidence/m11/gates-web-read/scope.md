# M11 / gates-web-read — slice 계약

```yaml
milestone: M11
slice: gates-web-read
base_sha: 94ee445b50cafa669af435e651d811cc551c67e8   # M10 종결 머지
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다(관례).
in_scope:
  - web/scripts/kotlin-read-guard.mjs            # 신설 — 「여는 행위」를 런타임에 재는 층 (⑤)
  - web/scripts/kotlin-read-guard.test.mjs       # 신설 — 그 층의 양성·음성 대조
  - web/scripts/kotlin-source-ownership.test.mjs # 데이터 파일 술어를 측정된 처방으로 (⑩)
  - web/scripts/type-assertion-gate.test.mjs     # 신설 — (7) 양성·음성 대조 + 덮어쓰기 측정
  - web/eslint.config.js                         # (7) consistent-type-assertions
  - web/vite.config.ts                           # setupFiles 등재 (⑤ 층의 입구)
  - web/config/gate-tests.txt                    # 새 게이트 test 둘 등재
  - docs/discovery/capability-map.md             # §12.3 하위 절 (n) 만
  - reports/evidence/m11/gates-web-read/
out_of_scope:
  - web/src/**                             # 출하 코드를 바꾸지 않는다 — (7) 오늘 위반 0 실측
  - web/scripts/kotlin-comment-strip.mjs   # 걷기 본체. 걷기 알고리즘은 이 slice 의 축이 아니다
  - web/scripts/display-gate-selectors.test.mjs  # 11E 의 서명 A 대상 — §공유 파일
  - web/e2e/**                             # 소비자. ⑥ 처분이 (a) 일 때만 계약 갱신으로 들어온다
  - adapters/**, workflow/**, app/**       # Kotlin 무편집
  - openapi/bidvector-operator-api.yaml    # 11B 의 편집 대상
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # §acceptance 근거 — Kotlin 무편집인데도 돈다
rollback: rollback.md
```

## 받는 항목과 처분 후보 (착수 실측)

| 항목 | 오늘 실측 | 처분 후보 |
| --- | --- | --- |
| ⑤ 여는 행위를 재는 층이 없다 | 원형 층을 세워 vitest 전건(42 파일·913 test)에 얹으니 **전부 통과**하고 소비자의 직접 읽기·별칭 import·심볼릭 링크가 **던진다** | **닫힘** |
| ⑥ 단언 전수의 데이터 흐름이 한 홉 | 모집단 수집이 `readKotlinSource(…)` 를 **직접 인자**로 받는 호출과 **한 단계 중간 변수**까지다 | **⑤ 가 닫지 않는다** — 아래 §⑥ |
| ⑧ 추적되는 심볼릭 링크 | 링크를 실제로 걸어 재니 ⑤ 층이 **던진다**(해석된 실경로를 본다) | **닫힘(⑤ 가 함께)** |
| ⑨ 대소문자 | ⑤ 층의 확장자 판정은 **해석된 실경로 + 대소문자 무시**라 표기 축이 사라진다 | **닫힘(⑤ 가 함께)** |
| ⑩ 데이터 파일 | 처방(`#` 줄 제외 + allowlist 차감 + 단어 토큰)을 오늘 트리에 돌려 **오탐 0** · P6·P7·P7b·P7c 는 각각 1·1·2·2 매치 | **닫힘** |
| (7) `as Record<…>` 우회 | 규칙을 얹어 `src/**` 전수를 재니 **위반 0** · `as const satisfies` 는 **0**(대조군) · `{…} as Record<…>` 는 **1** | **닫힘** |

수치의 정본은 `commands.md` 「착수 실측」이다.

## ⑥ — 1안이 닫지 않는 축 (팀장 답 대기)

계약 행과 capability map §12.3 (j) 는 ⑤ 층이 「⑥⑧⑨ 를 함께 닫는다」고 적는다. **⑧⑨ 는 닫는다.
⑥ 은 다른 축이다** — ⑤ 는 *여는 행위*를 재고, ⑥ 은 *이미 읽힌 텍스트*가 단언 없이 추출 함수로
흘러드는 자리다. 런타임 층이 볼 수 있는 호출이 그 사이에 없다. 처방의 정본(`design-review.md`
§4 (3) 미달 ⑤)도 그 층이 닫는 것을 **「표기 축」**(글자 조립·glob·JSON)으로 적는다 — 그것이
⑧⑨⑩ 이고 ⑥ 은 그 목록에 없다.

처분 후보 둘을 올린다. **판정은 팀장이 낸다.**

- **(a) 고정점으로 바꾼다(추천)** — 꼴을 열거하지 않고 ① 파일 안에서 「Kotlin 값을 담은 변수」를
  **고정점까지** 전파하고 ② 호출의 **인자 부분트리 전체**에서 그 값을 찾는다. 두 홉 이상·`.trim()`·
  spread·별칭 import 가 한 번에 들어온다. 「죽은 `assertStrippedKotlin('')`」은 단언의 인자가 그
  함수의 **입력 파라미터**인지를 보아 닫는다. 남는 꼴(메서드 피호출자·콜백 **참조** 전달)은 설계
  검토에서 **등재**한다. `web/e2e/**` 가 in_scope 로 들어올 수 있다(모집단 등식이 움직이면).
- **(b) 경계 밖으로 등재** — 고정한 위협 모델 경계가 「`.kt` 를 **여는** 표류」이므로 ⑥ 은 그 문장
  밖이라고 읽고 `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS` 의 잔여로 남긴다.

## 범위 밖 후보 — 미달 ② 의 절반 (팀장 답 대기)

받는 항목에 없지만 ⑤ 층을 세우면서 **싼값에** 닫히는 자리가 하나 있다. `assertStrippedKotlin` 이
지금 「다시 걸어 같은가」만 보므로 **줄을 끊지 않는 사본**(`replaceAll('//','')`)이 단언 **앞**에
와도 조용하다(`design-review.md` §4 (3) 미달 ② 의 표). 읽기가 내준 텍스트를 소유 모듈이 쥐고
단언을 **동일성**으로 올리면 그 칸이 닫힌다. **범위 확장이라 구현하지 않고 보고만 한다** — 받으면
`web/scripts/kotlin-source.mjs` 가 in_scope 로 들어온다.

## acceptance 근거 — Kotlin 무편집인데 `./gradlew check` 를 돈다

Kotlin 소스는 한 줄도 만지지 않는다. 그런데도 `check` 가 acceptance 에 드는 이유는 **evidence 가
`LeakPatternGateTask` 의 스캔 루트**(`reports/evidence/`)**에 들기 때문**이다 — 그 task 는
`check` 에 배선돼 있고 baseline 밖의 새 매치를 실패시킨다. 선례는 M10/gates-web 의 같은 줄이다.

## 공유 파일 — `display-gate-selectors.test.mjs` (11E 와 겹친다)

(7) 의 lint 게이트 test 는 **새 파일**(`scripts/type-assertion-gate.test.mjs`)로 짓는다. 기존
lint 게이트 test 를 늘리는 쪽이 싸지만 그 파일이 **11E 의 서명 A 대상**이라(첫 칸 5초 timeout)
같은 파일을 두 레인이 만진다. 새 파일은 만드는 커밋에서 `web/config/gate-tests.txt` 에 등재한다.

## 하네스 레인 변경

`git log --oneline 94ee445b..HEAD --no-merges -- CLAUDE.md .claude/` — **없음**. 재산출 때마다
귀속을 기계로 확인한다.

## 갱신 이력

- 2026-11-13 초판(착수 보고와 함께).
