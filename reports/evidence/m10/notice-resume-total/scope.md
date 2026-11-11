# M10 / notice-resume-total — 공고 목록 갈래의 총수 최댓값 보호 (OPEN-M9-NOTICE-LIST-RESUME-TOTAL)

- base: main `8062dcd784cf81905c50a2c8706d8bbd3ea5263e` · 브랜치 `feature/m10-notice-resume-total` · worktree `bid-vector-v2-m10-notice-resume-total`
- 근거: `milestone-10.md` slice 표 **10A** · `docs/discovery/capability-map.md` §12.3 (b) 의 `OPEN-M9-NOTICE-LIST-RESUME-TOTAL` 행(M9/award-ingest r4 신설 — PR #128 cr open question · 팀장 결정 2026-11-06) · 낙찰 갈래의 같은 보호(`CollectAwardListUseCase` 의 「원천이 한 번 말한 총수는 뒤 쪽이 지우지 못한다」 절, v r3 L-1).
- 결함의 모양: 쪽 상한(`MaxPages`) 재개로 한 슬롯이 배치 둘 이상이 될 때, 공유 누산기는 **마지막** 배치의 총수를 쓴다(`source.sourceTotal ?: sourceTotal`). 어댑터는 배치마다 새 누산기이고 총수를 못 본 배치에 **0** 을 채우므로, 재개 첫 쪽이 「없음」이면 그 슬롯의 `collection_run.source_total` 에 0 이 적힌다.

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | 고치는 자리는 **`CollectNoticesUseCase` 의 슬롯 걷기 한 자리**다 — 배치마다 본 총수의 최댓값을 쥐고 `CollectionRunStore` 로 가는 회계에 싣는다. **공유 누산기(`SourceAccountingTally`)는 고치지 않는다**(그 쪽을 최댓값으로 바꾸면 두 갈래 거동이 함께 바뀐다 — 승인 경계). 그 파일의 diff 가 비는 것이 이 결정의 단언이다. |
| D-2 | 최댓값 함수는 낙찰 갈래에 이미 있다 — **복제하지 않고** 같은 패키지의 공유 `internal` 함수로 옮긴다(v2-지침서 §5 중복 금지). 낙찰 갈래는 **순수 이동**이라 거동이 바뀌지 않고, 두 갈래가 같은 한 자리를 부르므로 공유 누산기를 최댓값으로 올리는 날 걷을 자리도 하나다. |
| D-3 | test 쪽도 같다 — 「총수와 받은 수가 어긋난 회계」 helper 가 낙찰 test 안에 private 으로 있다. 공유 support 로 옮겨 두 갈래 test 가 같은 자리를 쓴다(값은 그대로, 이름만 모양을 말하는 것으로). |
| D-4 | **거동 축**: 이 갈래의 종료 코드 술어는 `truncated` 축이라(수집 러너의 종료 코드 함수) 이 변경으로 움직이지 않는다 — 낙찰 갈래의 `attemptOutcomeOf`(짧은 걷기를 총수로 재는 술어)는 이 갈래가 쓰지 않는다. 바뀌는 것은 **회계 행의 `source_total` 값과 수집 줄의 같은 칸** 둘이고, 그 값이 바로 운영 판독(`received < source_total`)이 서는 자리다. 그래서 **장부 품질** 변경이고 거동 변경 승인 대상이 아니다. |
| D-5 | 남는 한계(낙찰 갈래와 **같은** 열린 판): **한 배치 안**에서 뒤 쪽이 총수를 줄이면 어댑터 누산기가 마지막 값을 쥐고 use case 는 그 결과만 받는다. 쪽 상한을 넘지 않는 정상 슬롯은 배치가 하나라 이 보호가 뜻을 갖지 않는다 — 닫히는 범위는 **배치 경계**뿐이다. |

## in_scope

- `workflow/src/main/kotlin/bidvector/workflow/collection/CollectNoticesUseCase.kt` — 최댓값 보호(D-1)
- `workflow/src/main/kotlin/bidvector/workflow/collection/` 의 **새 파일 하나** — 두 갈래가 공유하는 최댓값 함수와 그 근거(D-2)
- `workflow/src/main/kotlin/bidvector/workflow/collection/CollectAwardListUseCase.kt` — 그 함수의 **이동**(거동 0)
- `workflow/src/test/kotlin/bidvector/workflow/collection/CollectNoticesUseCaseTest.kt` — RED
- `workflow/src/test/kotlin/bidvector/workflow/collection/CollectionTestSupport.kt` · `.../CollectAwardListUseCaseTest.kt` — test helper 이동(D-3)
- `docs/discovery/capability-map.md` — §12.3 에 이 slice 의 하위 절 하나(처분 등재)
- `reports/evidence/m10/notice-resume-total/**`

## out_scope

`workflow/src/main/kotlin/bidvector/workflow/collection/SourceAccountingTally.kt`(공유 누산기) · `procurement/**` · `adapters/**`(어댑터 누산기의 한 배치 안 축 — D-5) · `app/**` · 마이그레이션 · runbook · 실 호출·실 DB.

## acceptance

- CI Kotlin `check` job 명령 그대로 exit 0 (`./gradlew --no-daemon check`).
- RED 선행: 쪽 상한 재개의 둘째 배치가 총수를 **0** 으로 말해도 회계 행에 앞 배치의 총수가 남고, 둘째 배치가 **더 큰** 총수를 말하면 그 값이 남는다(최댓값이지 「첫 값 고정」이 아니다).
- `git diff --stat <base>..HEAD -- workflow/src/main/kotlin/bidvector/workflow/collection/SourceAccountingTally.kt procurement adapters app` 빈 출력(D-1·out_scope).
- 변이: 공유 최댓값 함수를 「마지막 값 쥐기」로 **바꿔치우면** 두 갈래 test 가 함께 RED.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --` + 새 파일 삭제. 목록은 `git diff --name-status <base>..HEAD` 기계 산출(`rollback.md`). 배선 변경이 없어 비활성화 스위치는 없다 — 되돌리면 공유 누산기의 마지막 값 거동으로 돌아간다.

## 하네스 레인 변경

없음(`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 빈 출력 — 리뷰 요청 시점에 재산출).
