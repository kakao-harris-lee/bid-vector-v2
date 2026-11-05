# M9/retention-1y — 실행 명령과 종료 코드

base `79f3e5c4` · 브랜치 `feature/m9-retention-1y` · worktree `bid-vector-v2-m9-retention-1y`.

**acceptance 정의**: `.github/workflows/ci.yml` `check` job 의 명령 그대로(`check` → `qualityBaseline` → S-20
`one-command-check.sh`). `ml-engine`·`web`·`container` job 은 **입력 변경 0** — in_scope 에 `ml-engine/**`·
`web/**`·`docker/**`·`.github/**` 가 없고 이 slice 가 만진 것은 Kotlin 과 문서뿐이다.

**마지막 HEAD 의 결과 정본은 이 파일이 아니다** — verifier 판정과 완료 보고다(하네스 규율). evidence 를
고치는 커밋이 또 HEAD 를 움직이므로 그 뒤의 `check` 결과를 여기 적으려는 시도는 커밋을 하나 더 낳는다.
아래 전건 측정은 **마지막 산출물 커밋 `2aacb574`** 에서 돌았고, 그 뒤 커밋은 이 디렉터리뿐이다. 그
evidence 커밋들이 입력을 바꾸는 게이트는 `leakPatternGate` 하나이고(scanRoot 가 `reports/evidence`) 그
측정은 완료 보고가 든다.

**호스트 규율**: 무거운 명령 전에 확인 셋(`pgrep -af 'GradleWrapperMain|GradleWorkerMain'` · `free -m` ·
`ps -eo pid,rss,args --sort=-rss`)을 **별도 호출로** 먼저 돌렸다. Gradle 은 전부 빌드 자리 진입점
(`bv-build.sh`, 획득 순간 메모리 재확인) 아래에서 `--no-daemon` 으로 돌렸다. 이 레인이 도는 동안 호스트에
다른 레인 다섯이 같은 두 자리를 두고 줄을 섰다 — 그래서 표적을 좁혀 돌리고 전건은 마지막에 한 번 돌렸고,
전건 `check` 의 벽시계(33m47s)는 그 경합을 포함한다.

## RED (구현 전) — 승인된 값이 wire 에 닿지 않는 상태

RED 커밋 `aaa726d2`(test 만). 실패는 전부 **같은 방향**이다: 정책 표가 개정 항목을 모르므로 개정일 기준일이
90 을 답한다.

| # | 명령 | 결과 |
|---|---|---|
| 1 | `:workflow:test --tests '…RetentionPolicyDataTest' --rerun` | **exit 1** · 8 중 **3 FAILED**(개정일·개정 뒤 해소값 · 개정 항목의 유효 시작일/출처). 「개정일 **전날** 90」은 통과 — 그 축은 base 에서도 참이다 |
| 2 | `:app:test --tests '…RetentionRunnerTest' --rerun` | **exit 1** · 9 중 **2 FAILED** — 시작 줄이 `retentionDays=90`(기대 365) · 운반 등식도 90 |

DB 경계(앵커 365 ↔ 정책 90)의 RED 는 **변이 ① 과 같은 측정**이라 따로 돌리지 않았다 — 변이 ① 이 커밋 뒤에
그 상태를 다시 만든다.

## GREEN · acceptance

| # | 명령 | 결과 |
|---|---|---|
| 1 | 표적 GREEN(`--continue --rerun`): `:workflow:test`(retention.* + `PromoteOpeningResultsUseCaseTest`) · `:app:test`(retention.* + `RetentionWiringTest` + `OpeningPromotionRunnerLockTest`) · `:adapters:test`(retention.* + `OpeningPromotionAssemblyTest` + notificationinbox.*) | **exit 0** · XML 계수 클래스 **28** · test **227** · fail 0 · **skip 0**(모듈별 workflow 43 · app 22 · adapters 162) |
| 2 | `./gradlew --no-daemon check` @`2aacb574` | **exit 0** · 33m47s · 401 task(실행 245 · cache 113 · up-to-date 43) · test **4,161** / fail 0 / skip 4 · 게이트 task **150** 수행(`leakPatternGate` 실행 포함) |
| 3 | `./gradlew --no-daemon qualityBaseline` | **exit 0** · 그러나 35 task **전부 up-to-date**(바로 위 `check` 뒤) — 실행된 측정은 다음 행이다 |
| 4 | `./gradlew --no-daemon qualityBaseline --rerun-tasks` | **exit 0** · 45s · **26 task 전부 실행**(up-to-date 0 — 3 행의 「조용한 초록」을 이 행이 메운다) |
| 5 | **S-20** `./tools/one-command-check.sh` | **exit 0** — Kotlin 전건(위 2·3 뒤라 up-to-date) + **Python 전건**(ruff clean · mypy 99 파일 무결 · import-linter 8 계약 유지 · **pytest 1,628 passed** 6m19s · 설계 래칫 · 출처·버전 두 자리 대조 · wheel 재수출 1 passed) + **web 전건**(vitest **674 passed**/34 파일 · 번들 카나리 · Playwright 스모크 2 · **여정 e2e 11 passed · flaky 0 · spec 4/4**) |

2·3 은 **양끝에서 `git rev-parse` 가 같은 SHA** 를 냈다(측정 중 커밋 0). 4·5 도 같은 커밋에서 돌았다.
`ml-engine`·`web` 은 입력 변경 0 이지만 S-20 이 CI 의 `check` job step 이라 **좁히지 않고 돌렸다**
(「한 job 안에서 이 게이트만」은 안 돌린 것과 같다 — 줄일 수 있는 단위는 job 이다).

## 변이 (전부 커밋 뒤 · 원복은 변이 파일의 사본 복구 — `git checkout --` 금지)

| # | 변이 | 기대 | 결과 |
|---|---|---|---|
| ① | 개정 항목 제거(= 「개정이 없던 판」) | 해소값 축과 경계 전부 RED | **KILLED 12** — workflow 3 · app 2 · `RetentionSweepTest` 7. 거동 쪽 핵심: 기한 **안쪽** 행이 함께 사라진다(`ret-new-DELIVERED` 소멸) |
| ② | `retentionDays` 365 → **364** | 경계 RED | **KILLED 9** — workflow 2 · sweep 7. 하루 짧아져 cutoff +1초 행이 기한 밖이 된다(`ret-new-DELIVERED` 소멸) |
| ③ | `retentionDays` 365 → **366** | 경계 RED | **KILLED 8** — workflow 2 · sweep 6. **반대 방향**이다: 기한 넘은 행이 살아남는다(`ret-old-DELIVERED` 잔존) |
| 통제군 | 원복 뒤 같은 표적 | 초록 | 위 GREEN 1 · 원복 직후 `git status --porcelain -- <in_scope 개별 인자>` **빈 출력** |

②③ 이 양방향인 것이 「앵커를 정책에서 읽지 않는다」가 사는 증거다 — 읽어 왔다면 셋 다 초록이다.
`RetentionSweepTest` 의 「경계 전제」 등식은 세 변이 모두에서 RED 였다(그 전제가 먼저 깨지므로 읽기 쉬운
실패가 된다).

## 경계 확인

| 확인 | 결과 |
|---|---|
| in_scope 대조 | `git log --no-merges --format= --name-only 79f3e5c4..HEAD \| sort -u` → **24 경로**, 전부 계약 in_scope 안(retention 네 묶음 · 「90일 문면」 파일 · 문서 넷 · 이 evidence) |
| 수정 라운드 새 파일 | 새 파일은 **이 evidence 디렉터리뿐**(산출물 23 전건 `M`) |
| 새 test 클래스 | **0** — 기존 네 클래스에 test 를 더했다. 그래서 `config/quality/gate-tests.properties` 편집이 **없고** 「새 클래스 미등재」 함정에 해당하지 않는다 |
| 마이그레이션 | `git diff --name-only 79f3e5c4..HEAD -- adapters/src/main/resources/db/migration/` **0 파일**(D-4 — 적용된 `V19`·`V23` 의 주석을 고치지 않았다, checksum) |
| 낡는 좌표 | 이 slice 가 편집하는 파일에 `file:line` **0**. 역방향(편집한 파일 stem 으로 `<stem>:[0-9]` 전수) 유효 참조 **0** — 유일한 매치는 M4 evidence 에 적힌 **grep 명령문 안의 패턴**이고 좌표 참조가 아니다 |
| 비밀값 스캔 | 참조형 `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/retention-1y/` **exit 1(매치 0)**. 산출물 쪽은 **이 slice 가 추가한 줄 307** 을 같은 패턴 파일로 떠서 **매치 0** — 산출물 파일 전체 스캔이 내는 매치는 전부 base 에 이미 있던 줄이고 `leakPatternGate` baseline 이 덮는다(전건 `check` 에서 그 게이트가 실행돼 통과) |
| clean tree | `git status --porcelain -- <in_scope 개별 인자>` **빈 출력**. **양성 대조**: in_scope 문서 하나에 한 줄을 심고 같은 술어를 돌려 ` M` **1행**을 받았고, **비파괴 절삭**(`head -n <원래 줄수>` 로 덮어쓰기 — `git checkout --` 금지)으로 걷은 뒤 다시 빈 출력이었다(줄수 469 → 470 → 469). 변이 ①~③ 도 같은 술어에 잡혔다 |
| 외부 effect | 운영·dev DB(55432) 무접촉 · 실 API·LLM·알림 호출 0 · push/PR/머지 0. DB 축은 전부 Testcontainers(이 slice 는 접속 설정을 만지지 않는다) |
| 마이그레이션 적용 DB | 없음 — 이 slice 는 스키마를 바꾸지 않으므로 적용 창도, forward-fix 도 없다 |

## rollback 실측

`rollback.md` 가 정본이다 — 버릴 clone 에서 ①②③ 과 **트리 동일성**(복원 + evidence 제거 뒤 `git write-tree`
가 base 트리 해시와 같다), ⑥ 은 되돌리지 않는 evidence 디렉터리를 입력으로 삼는 게이트 하나.
