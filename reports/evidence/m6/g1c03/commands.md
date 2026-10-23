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
| `container` job 재생 — `ci.yml` step 을 순서대로, `if:` 를 앞 결과로 평가 | `fd60ce1b`(산출물 = `26beba76`) | **0** | 27 step: run 23 success · runner 전용 `uses` 4(`if:` 만 평가) · skipped 0 |
| `./gradlew --no-daemon check` | `102d1a5f` | **1** | `:procurement:sizeGate` — 원장 test 파일 535 줄(한도 500). 관측 시각 test 넷을 새 클래스로 떼어 `2c3d0e10` |
| `./gradlew --no-daemon check --continue` | `2c3d0e10` | **0** | 350 task · `sizeGate` · 등재·실행 게이트 · `leakPatternGate` 실행, 새 클래스 4 test 실행 |
| 변이 13 | `2c3d0e10` | **0**(스크립트) | 전부 RED(checklist §4-1) |
| 통제군 `… :workflow:test --rerun :procurement:test --rerun :adapters:test --rerun … :app:test --rerun …` | `2c3d0e10` | **0** | 784 실행 · 실패 0 |
| `container` job 재생(D-G1C03-4) | `d653e8bf`(산출물 = `2c3d0e10`) | **0** | run 23 success · uses 4 · skipped 0 — 판정 SHA 를 움직이지 않으려고 커밋하지 않았던 측정 |
| `./gradlew --no-daemon check --continue` | `d1f87bf8` | **0** | 359 task · 아키텍처 게이트 새 규칙 둘 포함 |
| 변이 V1 · V2 · MT | `d1f87bf8` | V1 **1** · V2 **1** · MT **1** | 각각 아키텍처 게이트 1 · 게이트 1 · 비충돌 test 1(checklist §4-1) |
| 통제군 `--rerun`(위 다섯 + 아키텍처 test 둘) | `d1f87bf8` | **0** | 837 실행 · 실패 0 |
| L9 구판 표적 `:workflow:test --tests '…OpeningDetailFetchGateTest'` | `bdcd1459` + F-2 test 둘 | **1** | 7 중 1 실패(checklist §4-1) |
| `container` job 재생 | `d1f87bf8` | **0** | 27 step: run 23 success · uses 4 · skipped 0. app 이미지가 새로 구워졌다(jar 변경) |
| 변이 Q1 · Q2 · Q3 · V1 · V2 | `788af8ca` | 다섯 모두 **1** | 각각 아키텍처 게이트 test 1 · `bidvector.workflow.collection.*` 83 초록(checklist §4-1) |
| 통제군 `--rerun`(위 다섯 + 아키텍처 test 둘) | `788af8ca` | **0** | 838 실행 · 실패 0 |
| `./gradlew --no-daemon check --continue` | `788af8ca`(+ 미커밋 evidence md) | **0** | 350 task |
| `container` job 재생 | `788af8ca` | 생략 | `git diff --name-only d1f87bf8 788af8ca -- '*/src/main/*'` 가 **빈 출력** — r3 은 test 소스·위반 표본만 바꿔 app jar 입력이 `d1f87bf8` 재생과 같다 |

재생마다 공유 태그 넷을 재생 전 id 로 되돌렸다(app `94382c1026cf` · web `855171383f43` · proxy `d93e25e04ec8` ·
ml-serving `326e9da38043`). compose 잔여 0 · 6G 개발 DB 손대지 않음.

rollback 실측은 `rollback.md` §4.
