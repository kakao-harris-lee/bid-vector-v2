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

(배선이 끝난 뒤 채운다 — `check` · container job 재생 · rollback ①~⑥.)
