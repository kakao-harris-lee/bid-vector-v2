# M12 / gates-web-read-3 — slice 계약

```yaml
milestone: M12
slice: gates-web-read-3
base_sha: 10d208d2664c6bb87cd7082a2853c08c2c0d3236   # M11 종결 머지 (`git rev-parse` 실측)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다(관례)
in_scope:                          # ★ 표시는 설계 검토 판정을 기다리는 후보다
  - web/scripts/kotlin-source.mjs                 # ⓐ — 원문을 얻는 길을 키+추출 함수 하나로
  - web/scripts/kotlin-source.test.mjs            # 그 모듈의 단위 대조
  - web/scripts/kotlin-source-ownership.test.mjs  # ⓐ 모집단 기계 제거 · ⓕ 데이터 파일 술어 둘
  - web/e2e/harness/opening-axis.mjs              # 입구 넷이 키를 받는다
  - web/e2e/harness/opening-axis.test.mjs         # 그 입구·커널 대조
  - web/e2e/harness/opening-axis-extract.mjs      # 커널의 허용 호출자 한 자리 변경
  - web/e2e/harness/journey-up.mjs                # 입구 호출 한 줄
  - web/scripts/money-vocab-cross-lang.test.mjs   # 같은 갈래(이 파일 안 커널 둘)
  - web/scripts/kotlin-read-guard.mjs             # ⓑ③ — 저장소 밖 분기 제거
  - web/scripts/kotlin-read-guard.test.mjs        # ⓑ③⑤ 양성·음성 대조
  - web/vite.config.ts                            # ★ ⓑ⑤ — 층을 vite 메인 프로세스에 — §공유 파일
  - web/playwright.config.ts                      # ★ ⓑ⑥ — 층을 spec 프로세스에
  - web/playwright.journey.config.ts              # ★ ⓑ⑥ — 같음(여정 하네스)
  - web/package.json                              # ★ ⓑ⑥ — `node` 게이트 스크립트 진입점
  - web/config/gate-tests.txt                     # 새 게이트 test 등재 — §공유 파일
  - docs/discovery/capability-map.md              # §12.3 의 새 하위 절 하나 — §절 글자
  - reports/evidence/m12/gates-web-read-3/
out_of_scope:
  - web/src/**                             # 출하 코드를 바꾸지 않는다
  - web/scripts/kotlin-comment-strip.mjs   # 걷기 본체. 걷기 알고리즘은 이 slice 의 축이 아니다
  - web/scripts/display-gate-selectors.test.mjs   # 12B 의 자리
  - web/e2e/journey/journey.spec.ts        # 12D 의 자리(여정 ③ 경합)
  - adapters/**, workflow/**, app/**, shared-kernel/**   # Kotlin 무편집
  - openapi/**                             # 무편집
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # §acceptance 근거 — Kotlin 무편집인데도 돈다
rollback: rollback.md
```

## 위협 모델 경계 — M11 계약을 그대로 승계한다

계약(`milestone-12.md` 12C 행)이 **M11 계약의 「11D 위협 모델 경계」 절을 그대로 쓴다**고 적는다.

**방어하는 것:** web test·스크립트가 **평범한 관용구로** Kotlin 원문을 읽게 되는 표류 — 새 test 가
경로 문자열·glob·데이터 파일·심볼릭 링크·대소문자 차이로 원문을 여는 것. 「저자가 게이트를
의식하지 않고 쓴 코드」가 기준이다.

**방어하지 않는 것:** 게이트를 알고 **의도적으로 숨기는** 저자 — 경로를 런타임에 조립해
난독화하거나, 게이트 파일·vitest setup·eslint 설정 자체를 고치는 것. 이것은 **등재로 답하고
수정 라운드를 열지 않는다.**

## 받는 항목 — `OPEN-M11-KOTLIN-READ-GATE-RESIDUALS-2` ⓐ~ⓕ (착수 실측)

수치의 정본은 `commands.md` 「착수 실측」이다. **처분 후보는 후보다** — 판정은 팀장이 낸다.

| 축 | 오늘 실측 | 처분 후보 |
| --- | --- | --- |
| **ⓐ** 단언을 아예 부르지 않는 새 추출 함수 | 두 홉으로 받는 꼴에서 모집단 등식이 **30 passed**(조용하다). 같은 함수를 **직접 인자**로 바꾸면 **1 failed** | **닫음** — 모집단을 쫓지 않고 **원문을 얻는 길 자체**를 키 + 추출 함수로 바꾼다 |
| **ⓑ①** 자식 프로세스 `grep` | vitest 안에서 Kotlin **99 줄**이 읽혔다 | **등재 유지** — node 안에 가로챌 자리가 없고 경로 리터럴도 필요 없다 |
| **ⓑ②** `git grep` | 같은 자리에서 **99 줄** | **등재 유지** — 같은 사유 |
| **ⓑ③** 저장소 밖으로 복사한 뒤 읽기 | 읽혔다(**6553 자**). 층에서 **분기 하나를 지우면 거부된다** — 전건 44 파일·987 test 초록, 소요가 기준과 잡음 안 | **닫음** |
| **ⓑ④** `worker_threads` | worker 안에서 읽혔다(**6553 자**). `--import` 는 worker 에 서지 않는다 — **기본 상속도 명시 `execArgv` 도** 읽혔고 같은 층이 **부모에서는 거부**했다 | **등재 유지** — 구조로 닫을 수 없음을 실측했다 |
| **ⓑ⑤** 번들러 적재 — 확장자 토큰 없는 glob + `?raw` | **42 모듈**이 적재됐다(정적 술어 밖). 층을 vite 설정에서 세우면 적재가 **실패**하고 전건은 초록, 통제군(저장소 밖 비 Kotlin `?raw`)은 그대로 적재 | **닫음**(제한: 표면에 뜨는 오류 문면이 vite 의 ENOENT 다) |
| **ⓑ⑥** vitest 밖 프로세스 | playwright spec 프로세스에서 읽혔다(**6553 자**). config 에서 `NODE_OPTIONS` 로 층을 실으면 **같은 spec 이 거부**한다 | **팀장 판정 필요** — 「닫음(진입점 의무를 구성으로)」 vs 「등재 유지」. §ⓑ⑥ |
| **ⓒ** 단언보다 뒤에 덧씌우는 사본 | 어떤 층도 보지 못한다(M10 미달 ② 이월) | **등재 유지** — ⓐ 의 처방이 자리를 좁히지만 없애지 못한다. §ⓒ |
| **ⓓ** 층·게이트 설정 자체 수정 · 바인딩 선점 | — | **등재 유지**(경계 밖 — 팀장 승인 2026-11-13) |
| **ⓔ** `eslint-disable` 한 줄 | 추적 파일의 그 주석은 **0 건** | **등재 유지**(경계 밖 — 같은 승인) |
| **ⓕ-1** 대문자 `*.KT` | 데이터 파일 술어에 `i` 를 붙이면 오늘 트리의 **새 적중 0**(오탐 0) | **닫음** — 런타임 층과 대소문자 성질을 맞춘다 |
| **ⓕ-2** `#` 로 시작하는 **데이터** 줄 | `#` 제외가 오늘 거르는 산문은 **한 자리 1 건**이고, 그 자리는 `#` 가 실제로 주석인 형식이다 | **정적 층 보강** — 주석 규약을 **형식의 성질**로 본다 |
| **ⓕ-3** 두 글자 토큰의 미래 오탐 | 오늘 오탐 0 | **등재 유지** — 탈출구는 allowlist 차감(이미 등재) |

## ⓑ⑥ — 하나뿐인 「닫을 수 있는데 비용이 있는」 축

M11 은 이 축을 「진입점마다 미리 적재하는 것은 **진입점 열거**라 고르지 않았다」로 등재했다.
착수 실측이 그 전제를 둘로 가른다.

1. **기구는 하나다.** `node --import <층>` 한 가지가 평범한 node 프로세스와 playwright spec
   프로세스(config 의 `NODE_OPTIONS`)를 **둘 다** 막는다(실측). 꼴마다 처방이 다른 축이 아니다.
2. **진입점 집합은 구성으로 뽑을 수 있다.** `package.json` 의 `scripts` 와 playwright config 가
   선언이므로, 새 진입점이 층 없이 생기면 게이트가 붉어지는 쪽으로 의무를 세울 수 있다 —
   손 목록이 아니다(선례: 12A 가 양성 표본을 `settings.gradle.kts` 에서 구성으로 뽑는다).

**대가**: `vite build`·`vite preview`·여정 e2e(W-13, `:app:bootJar` + 컨테이너)가 층 아래서 돌게
되므로 그 셋의 비용·부작용을 재야 한다. 그리고 `package.json`·config 둘이 in_scope 로 들어온다.

**추천**: ⓐ·ⓑ③·ⓑ⑤·ⓕ 를 먼저 닫고, ⓑ⑥ 은 **같은 slice 에서 마지막에** 붙인다(앞 넷이 초록인
HEAD 를 먼저 세워 두면 ⓑ⑥ 의 측정이 깨져도 그 커밋만 되돌릴 수 있다). 판정은 팀장이 낸다.

## ⓒ — ⓐ 의 처방이 자리를 좁히지만 닫지 못한다

ⓐ 를 「원문을 얻는 길이 **키와 추출 함수**뿐」으로 바꾸면 원문 문자열을 **이름 붙일 자리**가
소비자 쪽에서 사라진다. 그래도 넘긴 추출 함수 안에서는 받은 텍스트를 바꿀 수 있다 — 추출은
텍스트를 손에 쥐어야 성립하므로 그 이음매는 **추출의 성질**이다. 닫는 길은 둘뿐이고 둘 다
버린 자리다: 값 동일성(합성 입력 대조를 깬다 — M11 이 그래서 커널을 갈랐다)과 데이터 흐름 추적
(M10/10D 가 상한을 넘긴 축). **좁혀진 꼴을 실측과 함께 등재**하고, 좁혀진 뒤에도 남는 꼴이
경계 안인지 밖인지는 설계 검토에서 보고한다.

## 공유 파일

| 공유 자리 | 이 slice 의 편집 | 병합 때 확인 |
| --- | --- | --- |
| `web/vite.config.ts` | ★ 층을 세우는 import **한 줄**(`test:` 블록은 건드리지 않는다) | `test:` 블록의 키 수 · `setupFiles` 원소 수 · import 줄 수 |
| `web/config/gate-tests.txt` | 새 게이트 test 등재(수는 설계 검토가 정한다) | 줄 수와 `npm run gate:tests` 판정 |
| `web/package.json` | ★ `scripts` 의 `node` 진입점에 층 적재 | `scripts` 키 수 · 각 값의 층 적재 유무 |
| `docs/discovery/capability-map.md` §12.3 | 새 하위 절 하나 | §절 글자 |

12B 는 `web/src/display/**`·화면 게이트, 12D 는 `journey.spec.ts`·게이트 test 예열이 자리다 —
위 넷 말고 겹치는 파일은 없다(착수 시점 확인).

## §12.3 절 글자 — slice 에 고정하지 않는다

이 PR 이 main 을 **마지막으로 병합할 때** 그 시점 §12.3 의 마지막 하위 절 **다음 글자**를 써서
절을 **끝에 덧붙인다**. 그래서 이 문서는 글자를 적지 않는다(상호 참조에도 쓰지 않는다).

## 하네스 레인 변경

`git log --oneline 10d208d2..HEAD --no-merges -- CLAUDE.md .claude/` — **없음**. 재산출 때마다
귀속을 기계로 확인한다.

## 갱신 이력

- 2026-11-14 초판(착수 보고와 함께). ★ 표시 넷은 팀장 판정을 기다리는 in_scope 후보다.
