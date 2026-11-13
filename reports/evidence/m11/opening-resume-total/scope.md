# M11 / opening-resume-total — 개찰결과 목록 갈래의 총수 최댓값 보호 (OPEN-M10-OPENING-LIST-RESUME-TOTAL)

- base: main `94ee445b50cafa669af435e651d811cc551c67e8` · 브랜치 `feature/m11-opening-resume-total` ·
  worktree `bid-vector-v2-m11-opening-resume-total`
- 근거: `milestone-11.md` slice 표 **11A** · `docs/discovery/capability-map.md` §12.3 **(g)** 의
  `OPEN-M10-OPENING-LIST-RESUME-TOTAL` 행(M10/10A cr r1 HIGH 신설) · 10A 가 세운 공유 자리
  (`ClaimedSourceTotal.kt` 의 `widerTotalOf`)와 그 evidence(`reports/evidence/m10/notice-resume-total/`).
- 결함의 모양: 쪽 상한(`MaxPages`) 재개로 한 슬롯의 걷기가 배치 둘 이상이 될 때, 공유 누산기는 **마지막**
  배치의 총수를 쓴다(`source.sourceTotal ?: sourceTotal`). 어댑터는 무자료 경로에서 총수 칸을 **0** 으로
  채우므로, 재개 첫 쪽이 「없음」이면 그 0 이 누산기의 `?:` 를 통과해 앞 배치의 총수를 덮는다. 그러면
  `collection_run.source_total` 이 0 이 되어 「받은 수 ≥ 총수」가 되고, 덜 받은 슬롯이 다 받은 것으로 적힌다.

## 착수 실측 — 갈래 전수

모집단은 10A cr r1 이 바로잡은 축 그대로다: **「`SourceAccountingTally` 로 배치를 걷고 `MaxPages` 로
재개하는 자리」**(「`widerTotalOf` 를 부르는 자리」가 아니다 — 그 축이 10A 의 오계수를 낳았다).

| 갈래 | 걷는 자리 | 누산기 | 재개 술어 | 상태 |
| --- | --- | --- | --- | --- |
| 공고 목록 | `CollectNoticesUseCase` 슬롯 걷기 | `SlotTally`(안에 `SourceAccountingTally`) | `RESUME_SAME_SLOT` 지시 + 커서 진전 | **닫힘**(10A) |
| 낙찰 목록 | `CollectAwardListUseCase` 슬롯 쓸기 | `SourceAccountingTally` | `MaxPages` + 커서 진전 | **닫힘**(M9) |
| 개찰결과 목록 | `OpeningSampleFramer` 슬롯 틀 짓기 | `SourceAccountingTally` | `MaxPages` + 커서 진전 | **열림 — 이 slice** |

**넷째는 없다.** 세 축으로 교차 확인했다 — ① `SourceAccountingTally` 생성 자리는 셋이고(`SlotTally` 의
단일 생성 자리를 포함), `SlotTally` 를 만드는 자리도 하나다 ② `PageCursor` 를 쓰는 `*/src/main` 파일은 그
세 use case 와 어댑터·포트 선언뿐이다 ③ 상세 축(`CollectOpeningResultsUseCase` 의 축 팬아웃)은 누산기도
쪽 커서도 없다 — 배치 하나의 회계를 그대로 원장에 적고(`recordDetailRun`), 그 갈래의 「이어 돌기」는 쪽이
아니라 **(공고, 축)** 단위라 모집단 축과 다르다.

**범위 밖 후보 하나**(판정은 팀장): 어댑터의 한 배치 안 쪽 걷기(`walkKonepsNoticePages`)가 `totalCount` 를
`?:` 로 누적하고 끝에서 `?: 0` 으로 채운다 — 이 slice 가 닫는 **배치 경계**와 다른 축이고, 10A 의 알려진
제한 1·낙찰 slice 의 알려진 제한 13 과 **같은** 열린 판이다. 새 OPEN 후보는 **없다**(이 축은 이미 두 slice 의
알려진 제한으로 서 있다).

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | 고치는 자리는 **`OpeningSampleFramer` 의 슬롯 걷기 한 자리**다 — 배치마다 본 총수의 최댓값을 쥐고 `CollectionRunStore` 로 가는 회계에 싣는다. **공유 누산기(`SourceAccountingTally`)는 고치지 않는다**(10A D-1 과 같다 — 최댓값으로 올리면 세 갈래 거동이 함께 바뀐다 · 승인 경계). 그 파일의 diff 가 비는 것이 이 결정의 단언이다. |
| D-2 | 최댓값 함수는 **그대로 부른다** — 새 함수도, 복제도, 이동도 없다(10A D-2 가 세운 공유 자리). 바뀌는 것은 그 파일의 **KDoc 한 문단**뿐이다: 「셋째 갈래는 아직 부르지 않아 같은 결함이 남아 있다」가 이 slice 로 거짓이 되므로 사실을 갱신한다. 그 커밋의 **거동 0 을 바이트코드로 실측**한다(`javap -c` 동일 · 통제군 `javap -l`). |
| D-3 | 잠그는 자리는 **새 test 클래스 하나**(`OpeningListResumeTotalTest`)다. 기존 개찰 대역(`ScriptedOpeningPort`)은 커서를 보지 않고 `next = null` 만 내므로 재개 배치를 지을 수 없다. 새 클래스는 `CollectionTestSupport` 의 `claimedTotalAccounting`·`RecordingRawStore`·`RecordingRunStore`·`rangeOf` 를 **재사용**하고, 커서별 배치를 내는 목록 전용 fake 하나만 자기 자리에 둔다. 만드는 커밋에서 `config/quality/gate-tests.properties` 에 등재한다. |
| D-4 | **보고 경로는 하나다** — 이 갈래가 총수를 나르는 자리는 `runs.record` 로 가는 슬롯 회계 행뿐이고, 틀 짓기 결과(`Framing`)는 총수를 싣지 않는다. 10A V-2(보고가 누산기 값을 따로 나르는 갈림)의 모양이 여기엔 **존재할 수 없고**, 대신 그 한 자리를 「누산기 값을 그대로 적기」 변이로 잰다. |
| D-5 | **거동 축**: 이 갈래의 멈춤·절단 판정은 `truncationCause` 축이라(`SlotOutcome`·`openingDirective`) 이 변경으로 움직이지 않는다. 짧은 걷기를 총수로 재는 술어(`attemptOutcomeOf`)는 **상세 축과 낙찰 갈래**가 쓰고 이 목록 갈래는 쓰지 않는다. 바뀌는 것은 `collection_run.source_total` 값과 수집 줄의 같은 칸 둘이다 — **장부 품질** 변경이고, 계약(`milestone-11.md` 「거동 변경」 절)이 범위 안으로 적은 그것이다. |
| D-6 | 남는 한계(두 형제 갈래와 **같은** 열린 판): **한 배치 안**에서 뒤 쪽이 총수를 줄이면 어댑터 누산기가 마지막 값을 쥐고 걷는 자리는 그 결과만 받는다. 닫히는 범위는 **배치 경계**뿐이다. 이미 0 으로 적힌 과거 행은 고치지 않는다(소급 없음). |

## in_scope

- `workflow/src/main/kotlin/bidvector/workflow/collection/OpeningSampleFramer.kt` — 최댓값 보호(D-1)
- `workflow/src/main/kotlin/bidvector/workflow/collection/ClaimedSourceTotal.kt` — **KDoc 만**(D-2, 거동 0)
- `workflow/src/test/kotlin/bidvector/workflow/collection/OpeningListResumeTotalTest.kt` — **새 파일**, RED(D-3)
- `config/quality/gate-tests.properties` — 그 test 클래스 등재(D-3)
- `docs/discovery/capability-map.md` — §12.3 **끝에 이 slice 의 하위 절 하나를 덧붙임**(절 글자는
  병합 순서가 정한다 — 팀장 계약 변경 2026-11-13)
- `reports/evidence/m11/opening-resume-total/**`

## out_scope

`workflow/src/main/kotlin/bidvector/workflow/collection/SourceAccountingTally.kt`(공유 누산기 — D-1) ·
`CollectNoticesUseCase.kt`·`CollectAwardListUseCase.kt`·`SlotTally.kt`(닫힌 두 갈래) · `procurement/**` ·
`adapters/**`(어댑터 누산기의 한 배치 안 축 — D-6) · `app/**` · 마이그레이션 · runbook · 과거 행 소급 ·
실 호출·실 DB.

## acceptance

- CI Kotlin `check` job 명령 그대로 exit 0 (`./gradlew --no-daemon check`).
- RED 선행: 쪽 상한 재개의 둘째 배치가 총수를 **0** 으로 말해도 슬롯 회계 행에 앞 배치의 총수가 남고,
  총수 칸을 **비운**(`null`) 재개 배치도 같은 보호를 받으며, 둘째 배치가 **더 큰** 총수를 말하면 그 값이
  남는다(최댓값이지 「첫 값 고정」이 아니다).
- `git diff --stat <base>..HEAD -- <out_scope 경로들>` 빈 출력(D-1·out_scope).
- 변이: 공유 최댓값 함수를 「마지막 값 쥐기」로 **바꿔치우면 세 갈래 test 가 함께 RED**(D-2 의 실측) ·
  `batch == null` 갈래를 「null 반환」으로 · 슬롯 회계를 누산기 값 그대로 적기(D-4) · KDoc 커밋 거동 0.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --` + 새 파일 삭제. 목록은
`git diff --name-status <base>..HEAD` 기계 산출(`rollback.md`). 배선 변경이 없어 비활성화 스위치는 없다 —
되돌리면 공유 누산기의 마지막 값 거동으로 돌아간다. 공유 파일(`capability-map.md`·`gate-tests.properties`)의
복원 원천은 머지 뒤 고정 SHA 다(`rollback.md` 가 적는다).

## 하네스 레인 변경

없음(`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 빈 출력 — 리뷰 요청 시점에 재산출).
