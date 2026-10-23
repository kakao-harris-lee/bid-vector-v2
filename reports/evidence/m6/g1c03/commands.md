# M6/G1-C03 commands — 실행 명령과 실측

출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다. 변이 판정은 `checklist.md` §4-1 에만 둔다.

**호스트 규율** — 모든 Gradle·docker 명령을 `flock ~/.local/bid-vector-build.lock …` 으로 감쌌고, 점검 셋
(pgrep · free · ps)은 매번 별도 호출로 먼저 봤다(가장 늦은 12:30: available 15,561MB · swap free 5,274MB).
00:30~03:00 창 밖에서만 돌렸다.

**앞 라운드 요약** — 배선 전 신설 test 가 붉었고(TDD), 배선 뒤 기존 이어 돌기 test 여덟이 붉어 원인 셋으로
갈랐다(checklist §3). 첫 전건 `check`(2026-10-21)는 `OpeningBudgetE2ETest` 의 끊긴 축 이어 걷기로 붉었고 그것이
D-G1C03-2(창 0)로 갔다.

## acceptance

`web`·`ml-engine` job 은 생략한다 — `git diff --name-only a61ddff1 -- ml-engine web docker openapi contracts .github ml-contract`
가 **빈 출력**이다.

| cmd | HEAD | exit | 핵심 결과 |
|---|---|---|---|
| `./gradlew --no-daemon check --rerun-tasks` | `26beba76` | **0** | 350 task 전부 실행 · 11분 46초(`qualityBaseline` 은 이 그래프 안) |
| `container` job 재생 — `ci.yml` step 을 순서대로, `if:` 를 앞 결과로 평가 | `fd60ce1b`(산출물 = `26beba76`) | **0** | 27 step: run 24 success · runner 전용 `uses` 4(`if:` 만 평가) · skipped 0 |
| `./gradlew --no-daemon check` | `102d1a5f` | **1** | `:procurement:sizeGate` — 원장 test 파일 535 줄(한도 500). 관측 시각 test 넷을 새 클래스로 떼어 `2c3d0e10` |
| `./gradlew --no-daemon check --continue` | `2c3d0e10` | **0** | 350 task · `sizeGate` · 등재·실행 게이트 · `leakPatternGate` 실행, 새 클래스 4 test 실행 |
| 변이 13 | `2c3d0e10` | **0**(스크립트) | 전부 RED(checklist §4-1) |
| 통제군 `… :workflow:test --rerun :procurement:test --rerun :adapters:test --rerun … :app:test --rerun …` | `2c3d0e10` | **0** | 784 실행 · 실패 0 |

container 재생 뒤 공유 태그 넷은 재생 전 id 다(app `94382c1026cf` · web `855171383f43` · proxy `d93e25e04ec8` ·
ml-serving `326e9da38043`). 검토 r1 배치(`a7c5da86` · `2c3d0e10`)는 Kotlin 소스만 바꿨고 이미지 빌드 입력의 형태(Dockerfile ·
스크립트 · 의존)는 그대로라 container 재생은 다시 돌리지 않았다.

rollback 실측은 `rollback.md` §4.
