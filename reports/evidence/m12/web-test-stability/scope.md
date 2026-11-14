# M12 / web-test-stability — slice 계약

```yaml
milestone: M12
slice: web-test-stability
base_sha: 10d208d2664c6bb87cd7082a2853c08c2c0d3236   # 착수 커밋에서 기계로 확인한다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/e2e/journey/journey.spec.ts        # ① 여정 ③ 타임라인 단언의 기준점
  - web/e2e/journey/shared.ts              # ① 「쓰기 뒤에 출발한 읽기」 헬퍼의 자리 (팀장 판정 대기)
  - web/e2e/journey/onboarding.spec.ts     # ① 같은 idiom 의 기존 사본 — 공유 헬퍼로 (팀장 판정 대기)
  - web/scripts/money-vocab-cross-lang.test.mjs      # ② 측정이 처방을 정한다(편집 0 일 수 있다)
  - web/scripts/kotlin-source-ownership.test.mjs     # ② 같음
  - docs/discovery/capability-map.md       # §12.3 하위 절 하나 — 글자는 머지 시점에 정한다
  - reports/evidence/m12/web-test-stability/
out_of_scope:
  - web/src/**                             # 출하 코드 0 — 화면 거동을 바꾸지 않는다
  - web/vite.config.ts                     # 12C 와 공유하는 자리. 예산을 늘리는 것은 처방이 아니다(11E 실측)
  - web/config/gate-tests.txt              # 새 test 파일 0 계획 — 생기면 같은 커밋에서 등재한다
  - web/e2e/journey/results.spec.ts · settings.spec.ts   # 같은 꼴의 다른 자리 — 아래 §새 OPEN 후보
  - web/scripts/screen-card-coverage*.mjs · web/src/screens/screenVocabulary.test.tsx   # 12B
  - web/scripts/kotlin-source.mjs · web/scripts/kotlin-read-guard*.mjs · web/eslint.config.js   # 12C
  - adapters/**/*AdapterDependencyTest.kt · config/quality/architecture-policy.properties   # 12A
  - .github/workflows/ci.yml               # 게이트의 명령·상한을 바꾸지 않는다
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 leak-pattern 게이트의 스캔 뿌리에 들어 경로가 닿는다
rollback: rollback.md
```

## 받는 항목과 처분 (착수 시점의 제안 — 판정은 팀장 답으로 고정한다)

| # | 항목 | 제안 |
| --- | --- | --- |
| 4 | `OPEN-M11-GATE-TEST-WARMUP-BUDGET` | 측정이 정한다 — 부하 조건과 함께 첫 칸 소요를 재고, 여유가 두꺼우면 **이미 닫힘/불요**, 얇으면 11E 처방으로 **닫힘**. §② |
| 6 | 여정 e2e ③ 타임라인 항목 수 경합(OPEN 없음) | **닫힘** — 경합을 재현하는 대조를 먼저 세우고, 단언의 기준점을 「쓰기 응답 **뒤에 출발한** 읽기」로 옮긴다. 주장은 유지한다. §① |

## ① 여정 ③ 타임라인 — 무엇이 어긋났는가

착수 실측(정적)의 귀속은 **한 자리**다. 화면의 쓰기 성공 경로는 건 목록을 먼저 다시 읽고 **그
다음에** 타임라인을 다시 읽는다(`refreshCase` 의 두 `invalidateQueries` 가 순차 `await` 다). spec 은
초안 쓰기 뒤 **건 상태 칸만** 기다리므로, 그 단언이 서는 시점은 초안이 낳은 **타임라인 다시 읽기가
아직 날아오는 중**일 수 있다. 그 뒤 spec 이 「경로가 `/timeline` 이고 200」만 보는 포착을 걸면 그
**초안 때의 응답**이 걸리고, 화면은 그 사이 기록까지 반영해 한 줄 더 그린다 — PR #157 첫 CI 의
「화면 3 ↔ 응답 2」가 그 꼴이다. 포착 술어에 **출발 순서**가 없는 것이 결함이고, 응답 길이가
작은 쪽으로 어긋나는 것이 그 서명이다.

**바퀴를 다시 만들지 않는다.** 같은 결함과 같은 처방이 이미 이 저장소에 있다 — `onboarding.spec.ts`
의 체크리스트 읽기 장부(PR #118 v r1 H-1)가 「**쓰기 응답 뒤에 출발한** 첫 200 읽기」를 요청
장부로 거른다. 그 idiom 을 `shared.ts` 로 올려 **한 자리**가 소유하게 하고 타임라인이 그것을 쓴다
(그래서 in_scope 에 `onboarding.spec.ts` 가 있다 — 사본을 둘로 두지 않는다). 그 확장을 받지 않으면
journey.spec.ts 안에 사본을 하나 더 두게 되는데, **같은 결함 계열이 이미 두 번 물었다**는 사실이
한 소유자 쪽을 가리킨다. 판정은 팀장이 낸다.

단언은 약해지지 않는다 — 「화면이 API 와 같은 사건을 보인다」는 그대로이고, 기준점이 **그 기록을
담은 응답**으로 고정될 뿐이다. 그 응답이 실제로 그 기록을 담았다는 것도 같은 자리에서 센다.

**대조를 먼저 세운다.** 초안의 타임라인 다시 읽기에 지연을 주입해 경합을 재현한다 — 구판 단언이
그 주입 아래 **붉고**(서명: 기대 길이 < 화면 줄 수), 신판이 같은 주입 아래 **초록**인 것을 둘 다
잰다. 주입은 15s 급이라 suite 에 두지 않고 `commands.md` 에 둔다(11E 선례).

## ② 게이트 test 첫 칸 — 측정이 처방을 정한다

후보 둘은 `typescript` 를 **모듈 꼭대기에서** 들인다. 그 import 자체는 vitest 의 모듈 적재
단계이고 칸·hook 예산 밖이므로, 11E 서명 A(`new ESLint()` 는 아무것도 읽지 않고 **첫 `lintText`**
가 파서·플러그인을 적재하던 꼴)와 **같은 꼴이라고 단정할 수 없다**. 착수 시점의 정적 읽기로는
두 파일의 비싼 자리가 서로 다르다:

- `money-vocab-cross-lang.test.mjs` — Kotlin·web 원문 읽기와 열거 추출이 **적재 단계**에서 이미
  끝난다. 칸 안에 남는 TS 작업은 작은 합성 원문 몇 개다.
- `kotlin-source-ownership.test.mjs` — 적재 단계에서 추적 파일 목록만 만들고, **추적 파일 전수를
  AST 로 훑는 칸**이 그 파일의 셋째 칸이다. 즉 「첫 칸이 기동 비용을 진다」보다 「**무거운 칸이
  5000ms 예산을 쓴다**」에 가깝다.

그래서 **측정 없이 고치지 않는다.** 전건 vitest 를 JSON reporter 와 함께 반복해 칸별 소요를 받고,
한가할 때와 **부하 아래**(다른 레인의 빌드와 겹치는 조건을 같은 호스트에서 재현) 둘을 같은
횟수로 잰다. 판정 술어는 「예산 대비 여유」이고, 그 수를 낸 **조건(표적 집합·부하·횟수)을 같은
줄에 적는다**. 여유가 얇은 축이 나오면 11E 처방(측정 아닌 준비를 **모듈 적재 단계**로)을 그
축에만 적용하고, 적재 단계가 실제로 치렀다는 것을 **수로** 잠근다(상수로 바꿔치울 수 없는 값).
여유가 두꺼우면 그 측정이 「이미 닫힘/불요」 판정의 근거가 된다 — 처분은 팀장이 낸다.

## 새 OPEN 후보 (범위 밖 판정은 팀장이 낸다)

1. **같은 포착 술어의 다른 자리.** `results.spec.ts`·`settings.spec.ts`·`shared.ts` 의 「경로 +
   메서드 + 200」 포착 가운데 **쓰기 뒤 다시 읽기**를 기다리는 자리가 있으면 같은 경합을 진다.
   착수 정적 읽기로는 그 자리들이 전부 **탐색 전후**(쓰기 기준점이 없는 꼴)로 보이지만, 전수
   판정은 측정이 필요하다. 구조로 닫는 길(포착을 헬퍼로만 열고 날 `waitForResponse` 를 금지하는
   게이트)은 이 slice 의 범위가 아니다.
2. **예산을 두는 모든 자리가 추첨이라는 사실 자체.** 11E 가 적었듯 칸·hook 예산은 부하 앞에서
   추첨이다. ② 가 닫는 것은 후보 둘이고, vitest 전체의 「무거운 칸」 축은 등재 쪽이다.

## base 확인

`git merge-base --is-ancestor 10d208d2 HEAD` · `git rev-parse 10d208d2` 를 착수 커밋에서 기계로
확인한다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-14 초판(착수 보고와 함께).
