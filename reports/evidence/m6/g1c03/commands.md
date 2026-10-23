# M6/G1-C03 commands — 실행 명령과 실측

출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이고 감사자는 명령을 다시 돌린다. 변이 실측의 바꿔치기·
판정은 `checklist.md` 의 표에 둔다(같은 사실을 두 자리에 적지 않는다).

**호스트 규율** — 레인이 병렬이라 모든 Gradle 명령을 호스트 전역 잠금(`flock ~/.local/bid-vector-build.lock …`)
으로 감쌌다. 점검 셋은 별도 호출로 먼저 봤다(2026-10-21 16:4x KST): 도는 빌드 **없음** · available
**16,448MB** · swap free **7,055MB** · RSS 상위에 Gradle 없음. 00:30~03:00 창 밖에서만 돌렸다.

## 배선 전 RED(TDD) — 거동 test 가 **지금 코드에서** 붉은가

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.collection.OpeningDetailFetchGateTest'` | **1** | 2 tests, **1 failed** — 「재조회 게이트 미만이면 그 축을 다시 부르지 않는다」가 `size 1 but has size 2`(게이트 창 안인데 그 축을 다시 불렀다). 「정확히 1 회」 쪽은 이 시점에 **초록**이다: 배선 전에는 매 스윕 부르므로 통과한다 — 그래서 둘을 함께 둔다(한쪽만으로는 양쪽 실패를 다 못 잡는다) |

## 배선 뒤 — 드러난 기존 거동과 그 처분

| cmd | exit | 핵심 결과 |
|---|---|---|
| `… :workflow:test --tests 'bidvector.workflow.collection.*'`(1차) | **1** | 77 tests, **8 failed** — 전부 「미완 축을 다음 스윕이 이어받는다」 계열. 원인 둘로 갈렸다(checklist §3 ①②) |
| 같은 명령(관문 거부를 창에서 뺀 뒤) | **1** | 77 tests, **1 failed** — 쿼터·예산·속도 보호 계열 전부 복구 |
| 같은 명령(이번 실행이 닫은 라운드를 창에서 뺀 뒤) | **1** | 77 tests, **1 failed** — 크래시 계열 넷 복구. 남은 하나가 **진짜 거동 변경**(checklist §3 ③) |
| 같은 명령(심는 시각을 창 밖으로 옮긴 뒤) | **0** | BUILD SUCCESSFUL |
| `… :workflow:test` | **0** | 모듈 전건 초록 |
| `… :procurement:test :workflow:test` | **0** | 원장 판독 질의 신설분 포함 전건 초록 |
| `… :adapters:compileTestKotlin :app:compileTestKotlin` | **0** | 보고 타입에 칸이 하나 늘었어도 아래 층 호출부는 그대로 선다 |
| `… :app:test --tests 'bidvector.app.architecture.*'` | **0** | 허용 목록에 더한 타입이 **관측 집합과 같다**(양방향 등식) |

## 변이 실측 — 구현된 절반

판정 표는 `checklist.md` §4-1 에 둔다. 명령은 전부
`… :workflow:test --tests 'bidvector.workflow.collection.OpeningDetailFetchGateTest'`(①만 패키지 전체)이고,
②는 `… :procurement:test` 도 함께 봤다(정책 값 고정이 거기 산다). 복원 뒤 통제군
`… :workflow:test :procurement:test` **exit 0**.

## acceptance

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check`(1차, 2026-10-21 22:12~22:22) | **1** | 10분 07초 · 172 task. 실패 **둘** — ① ktlint 「Needless blank line(s)」(helper 를 걷어낸 자리, 코드 변경 없이 해소) ② `OpeningBudgetE2ETest` 의 끊긴 축 이어 걷기(659 중 1) = checklist §3-2 의 재판정 대상 |

`web`·`ml-engine` job 은 생략한다 — **입력 변경 0** 을 path-limited diff 로 보였다:
`git diff --name-only bee7c36f -- ml-engine web docker openapi contracts .github ml-contract` 가 **빈 출력**이다
(rebase 뒤 base `a61ddff1` 로 다시 재도 빈 출력, 2026-10-23).
`container` job 은 app 이미지 입력(`app/**`)이 바뀌었으므로 `if:` 를 평가하는 재생으로 돌린다 — 재생 전
`bidvector/app:local` 은 `94382c1026cf` 이고, 끝나면 그 id 로 **태그를 되돌린다**(레인끼리 공유하는 태그).

## 판정 뒤 재실행 (2026-10-23, base `a61ddff1` 위로 rebase 한 산출물 HEAD `26beba76`)

점검 셋은 매번 별도 호출로 먼저 봤다(가장 늦은 10:36: available 14,418MB · swap free 7,434MB). 전부 잠금 아래다.

| cmd | exit | 핵심 결과 |
|---|---|---|
| `./gradlew --no-daemon check`(07:56) | **0** | 350 task 중 31 실행 · 나머지 up-to-date |
| 같은 명령 `--rerun-tasks`(07:58~08:10) | **0** | 350 task **전부 실행** · 11분 46초. `qualityBaseline` 은 이 그래프 안에서 돈다 |
| `container` job 재생 — `.github/workflows/ci.yml` 의 step 을 순서대로, `if:` 를 앞 결과로 평가(10:31~10:36, HEAD `fd60ce1b` = 산출물 트리 동일) | **0** | 27 step: run 24 **success** · runner 전용 `uses` 4(미실행, `if:` 는 평가) · **skipped 0**. 조건부 넷(`scan_app` · `scan_web` · `scan_proxy` · SBOM)의 `if:` 가 참으로 평가됐다 |
| 변이 ①~⑥ + 통제군(10:36~10:46) | **0**(스크립트) | 여섯 전부 RED · 통제군 exit 0 · 772 중 실패 0 — 판정 표는 `checklist.md` §4-1 |

container 재생 뒤 공유 태그를 재생 전 id 로 되돌렸다(app `94382c1026cf` · web `855171383f43`; proxy `d93e25e04ec8` ·
ml-serving `326e9da38043` 은 재생이 같은 id 를 냈다). 10:15 에 시작한 앞 재생은 lead 세션 중단으로 step 13 에서
끊겼다 — 그 결과는 쓰지 않았고, 그 재생이 남긴 위생 검사 컨테이너 하나를 지운 뒤 처음부터 다시 돌렸다.

rollback 실측은 `rollback.md` §4.
