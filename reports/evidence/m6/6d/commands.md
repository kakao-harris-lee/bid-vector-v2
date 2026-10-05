# M6/6D-1 — 실행 명령과 실측 (구현 레인)

base `fd4629fe` · 브랜치 `m6-6d/2026-10-05` · 산출물 커밋 `428551f3`.

## acceptance — CI `check` job 명령 그대로

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon check` | BUILD SUCCESSFUL (9m 18s 첫 회 ktlint FAIL → 형식 수정 후 1m 45s SUCCESS) |
| `./gradlew --no-daemon qualityBaseline` | BUILD SUCCESSFUL |

E2E 는 기본 `check` 안에서 돈다 — `@EnabledIfSystemProperty` 도 `Test.filter` 제외도 쓰지 않는다
(설계 검토 (2) 우회 5). 새 test 클래스 넷은 `config/quality/gate-tests.properties` 의
`gate.tests.adapters` 에 등재했고 **제외는 0** 이다.

## 표적 실행 — `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.e2e.*'`

| test 클래스 | tests | skipped | failures | errors |
|---|---|---|---|---|
| `PipelineOneLineE2ETest` | 3 | 0 | 0 | 0 |
| `PipelineFailureInjectionE2ETest` | 4 | 0 | 0 | 0 |
| `PipelineContractRejectionE2ETest` | 3 | 0 | 0 | 0 |
| `PipelineReproducibilityE2ETest` | 2 | 0 | 0 | 0 |

## 축별 변이 — 주입을 바꿔치우고 표적 test 재실행, 저장 바이트로 복원

변이는 **더하기가 아니라 바꿔치우기**다. 각 행은 적용 직후 `git diff --numstat` 으로 실제 적용을
확인했고, 복원 뒤 같은 명령이 빈 출력임을 확인했다(`git checkout --` 을 쓰지 않는다).

| 축 | 변이 | 판정 |
|---|---|---|
| ① relay 구간 | `relay()` 호출 제거 | RED — outbox 가 `DELIVERED` 에 닿지 않는다 |
| ① 면허 gate | 막힌 공고의 요건을 **보유 면허**로 교체 | RED — 그 공고가 더 이상 탈락하지 않는다 |
| ① production 조립 | 협력자 목록의 `dispatcher()` 를 test fake 로 교체 | RED — `CodeSource` 가 test 출력이라 잡힌다 |
| ② 중복 공고 | 둘째 `evaluate()` 제거 | RED — outbox 2행·inbox 1행 등식이 깨진다 |
| ② ML timeout | 지연을 `Duration.ZERO` 로 교체 | RED — payload 에 `DeadlineExceeded` 가 없다 |
| ② DB conflict | 경합 대상 행을 만들지 않음(평가 제거) | RED — 첫 워커가 아무것도 집지 못한다 |
| ② DB conflict | 첫 워커가 행을 쥐지 않도록 claim 상한 0 | RED — 둘째 워커가 그 행을 집는다 |
| ② malformed contract | 정의 밖 필드를 붙이지 않은 골든으로 교체 | RED — unknown field 수 단언이 깨진다 |
| ④ schema 거부 | 거부 골든을 성공 응답으로 교체 | RED — `UnsupportedSchema` 가 payload 에 없다 |
| ④ rollback | 응답자가 선택자와 무관하게 LATEST 를 반환 | RED — EXACT 요청이 `Predicted` 가 아니다 |
| ⑤ 재현 | 둘째 run 의 release 를 직전 release 로 교체 | RED — payload 집합 등식이 깨진다 |

**GREEN 이 나온 변이 하나를 그대로 적는다** — DB conflict 축에서 「래치 대기만 제거」는 **잡히지
않았다**(GREEN). 두 워커의 순서가 뒤집혀도 둘째가 집는 행이 없는 것은 같아서, 그 변이는 경합 여부를
가르지 못한다. 그래서 같은 축에 **잡히는 변이 둘**(위 표의 두 행)을 따로 세웠다 — 경합 대상 행의 존재와
첫 워커가 그 행을 쥐었다는 사실이 각각 단언에 실려 있음을 그 둘이 보인다.

## 그 밖의 게이트 실측

| 항목 | 명령 | 결과 |
|---|---|---|
| clean-tree | `git status --porcelain -- <in_scope 개별 인자>` | 빈 출력 |
| clean-tree 양성 대조 | 같은 경로에 비파괴 절삭(한 줄) 후 같은 명령 | `M` 한 줄 — 게이트가 잡는다. 저장 바이트로 복원 |
| 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt <신규 경로들>` | exit 1(일치 0) |
| 범위 혼입 | `git diff --name-status 428551f3~1..428551f3` | in_scope 10 경로뿐 |
| production 불변 | `git diff --name-only fd4629fe..HEAD -- '*/src/main/*'` | 빈 출력 |

## 실행하지 않은 것

실 KONEPS·실 LLM·실 발송·실 ML 서버 호출 0. DB 는 Testcontainers 가 만드는 일회성 컨테이너뿐이고
기존 개발·legacy 컨테이너에는 어떤 명령도 보내지 않았다.
