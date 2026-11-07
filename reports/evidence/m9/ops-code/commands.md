# M9/ops-code commands

base `df4976cc` · **병합한 main `56bd33ea`**(복원 원천 — 앞서 `3ad4f3f6`·`97ed49c4` 도 병합했다) ·
판정 SHA **`f577e69d`**(마지막 산출물 커밋). 레인 규율대로 push 하지 않았다.

로컬에서 돌린 것은 **`:app:check` 전건**(31m 26s · exit 0 — 그 모듈의 게이트·test 전부, container
여정 포함) + 나머지 세 모듈의 표적 `--tests`·lint·등재 게이트 + 변이다. `qualityBaseline` 과 다른
모듈의 전건 `check` 는 PR CI 가 정본이고 명령이 같다. **앞 판의 「그 모듈 게이트」 문면은 거짓이었다**
(v r4 L-3) — 그때 `:app:sizeGate` 가 붉었고 표적 세트가 그것을 안 돌렸다. Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew
--no-daemon …`(빌드 자리 둘 · 획득 순간 메모리 재확인)로 돌렸고 레인이 남긴 java 프로세스는 0 이다
(종료 뒤 `pgrep` 로 확인).

## 1. 커밋

| SHA | 무엇 |
| --- | --- |
| `c724e1ee` | slice 계약 |
| `7532008c` | OPS-09 — 상태 코드 축의 재시도 처분 |
| `0d172b1b` | OPS-08 — 연속 호출 사이의 최소 간격 |
| `d5f8c0b6` | NOTI-01 — 알림함 생산자(커널·포트) |
| `8aa35d79` | evidence 1차 |
| `b54bf047` | 계약 갱신(팀장 승인 — in_scope 일곱 경로, 기각한 대안 둘) |
| `0569e407` | NOTI-01 production 배선(실 DB 측정 포함) |
| `fe17d7d6` | 측정 상태 장부 — acceptance-trace 세 행 |
| `dc71ce34` · `5758e303` | evidence 2차 · rollback 유효성 술어 |
| `637dcc20` | 계약 갱신 2차(팀장) |
| `60c020af` | v B-1 — 절단 사유 전수 목록에 `AccessBlocked` |
| `98b4ed7a` | cr HIGH · v M-1 — 개찰 걷기도 차단에 멈춘다(소진 `when` 한 자리) |
| `258f5486` | v H-1 · cr MEDIUM — 출하 정책 값이 wire 에 닿는 것을 잠그는 test |
| `1429e934` | cr MEDIUM · v M-2 — 청중 조회 실패가 평가 run 을 멈추지 않는다(+ v L-1 ① · 질의 계수) |
| `db7a3909` | cr low 넷 + 낡은 주석 둘 |
| `e7fd0f3d` | cr MEDIUM · v L-2 — capability-map 세 행 · 장부 문면 정정 |
| `766d98c9` | main `3ad4f3f6` 병합 |
| `ae248e3f` | 게이트·lint 실측 셋(전송 표면 게이트가 `java.util.concurrent` 를 거부) |
| `2d1db76d` | evidence 3차(판정 SHA `ae248e3f`) |
| `ce4279ed` | cr r2 MEDIUM 둘 — 걷기 처분이 값을 나르는 sealed · 모집단 클래스 집합 등식 |
| `187ea814` | cr r2 MEDIUM · v r2 L-1 — 청중 장애가 예외 클래스 이름을 나르고 그 표지를 test 가 든다 |
| `cf629235` | v r2 M-1 1차 — 배선 운반 단계 단언(벽시계) |
| `ec02a75a` | cr r2 low 셋 + v r2 L-2 — 과수면 · 은퇴한 엔트리 · 낡은 문면 넷 |
| `e54c0d7a` | 장부 OPS-08 — 운반 단계를 재는 자리 |
| `e7912022` | v r2 M-1 정정 — 벽시계 단언이 계수 축과 섞여 **MW1 을 통과했다**(실측), 두 축으로 가름 |
| `cfcf315f` | evidence 4차(판정 SHA `e7912022`) |
| `908b197a` | main `97ed49c4`(#134) 병합 — 겹침 둘, 충돌 해소 줄 0 |
| `9286765d` | evidence 5차(판정 SHA `908b197a`) |
| `3ad0d342` | v r3 M-1 — production 이 넘기는 기다림 함수의 두 고리(거동 · 상수 풀 형태) |
| `d3aad2aa` | evidence 6차(판정 SHA `3ad0d342`) |
| `0817df3b` | main `56bd33ea`(#137 paper-bid V25) 병합 — 투찰 종류 축을 `NONE` 으로 싣는다(컴파일이 강제) |
| `702b7346` | evidence 7차(판정 SHA `0817df3b`) |
| `f577e69d` | v r4 B-1 — 간격 축을 `CollectionWiringSleeperTest` 로 가르고(sizeGate) · L-1 — **쓰이는 전송**으로 잰다 |

## 2. 표적 실행 — 핵심 결과 한 줄씩(판정 SHA 기준, 병합 뒤)

전부 `--rerun` 이다 — UP-TO-DATE 는 실행이 아니다.

| 명령 | 결과 |
| --- | --- |
| `:procurement:test --rerun`(전건 — sealed 열거 축) | `tests=317 failures=0` |
| `:workflow:test --rerun --tests 'bidvector.workflow.collection.*' --tests '…InboxRecordingNotificationsTest'` | `tests=116 failures=0` |
| `:adapters:test --rerun --tests 'bidvector.adapters.koneps.*' --tests '…NotificationAudienceFailureTest' --tests '…EvaluationAdapterDependencyTest'` | `tests=135 failures=0` |
| **`:app:check`**(전건 — 그 모듈의 게이트·test 전부, container 여정 포함) | BUILD SUCCESSFUL · 31m 26s · exit 0. `sizeGate` 포함(앞 판에서 붉던 자리) |
| `:adapters:test --rerun --tests 'bidvector.adapters.notificationinbox.*' --tests 'bidvector.adapters.evaluation.*'`(**실 DB** — 팀장 지시) | `tests=241 failures=0`(koneps 포함 합) · `EvaluationCommitInboxProductionTest` 3/0 · `NotificationInboxBidKindTest` 4/0 · `NotificationInboxPairConstraintTest` 5/0 |
| `gateRegistrationGate` — workflow · adapters · app · procurement | 전부 통과(새 test 클래스 넷 등재 포함) |
| ktlint(main·test)·detekt — workflow · adapters · app · procurement | 전부 BUILD SUCCESSFUL |
| `leakPatternGate` | BUILD SUCCESSFUL |

**app 표적에 architecture 전건이 들어가는 이유가 둘 늘었다**: 리플렉션 쌍 하나와 전송 표면 서명
하나를 갱신했고(아래 §4 ⓒ) 그 둘을 재는 게이트가 거기 있다.

`:app:gateRegistrationGate` 는 그 모듈 `test` task 의 제외 목록을 입력으로 받아 **`test` 뒤에
선다** — 표적 실행에서도 그 task 가 함께 돌아야 하므로 app 은 architecture 전건 + 배선 test 로
걸었다(container 여정은 걸지 않았다, PR CI 가 정본).

**앞 라운드가 놓친 둘을 이 라운드가 실측했다.** `:procurement:test` 는 **UP-TO-DATE 가 실행이
아니므로** `--rerun` 으로 돌렸고, 새 test 클래스 셋의 등재는 표적 `--tests` 가 재지 않으므로 네
모듈의 등재 게이트를 함께 걸었다.

## 3. 실 Spring 컨텍스트 — OPS-08 의 비용(앞 라운드 실측, 재측정하지 않았다)

| test | 간격 1초 | 통제군(간격 0) |
| --- | --- | --- |
| `CollectionRunnerE2ETest` | 61.9s · 실패 0 | 41.0s · 실패 0 |
| `OpeningPageSizeE2ETest` | 92.0s · 실패 0 | 87.1s · 실패 0 |
| `OpeningCollectionE2ETest` | 97.4s · 실패 0 | — |
| `SnapshotExtractionE2ETest` | 63.6s · 실패 0 | — |
| `CollectionWiringTest` · `OpeningCollectionWiringTest` | 0.4s · 0.2s · 실패 0 | — |

**+40~60s 를 PR CI 에 더한다**(네 E2E 클래스 합산 추정, 측정된 두 클래스의 +26s 를 근거로).
팀장 승인을 받았고 알려진 제한에 등재했다. 측정된 +20.9s·+4.9s 는 **간격이 프로세스 전역**이라는
성질의 값이기도 하다 — 운영 쪽 영향은 checklist 알려진 제한 ⑩ 이 든다.

## 4. 변이 — 바꿔치우기, 커밋 뒤, 변이 파일만 원복

묶음마다 변이들이 **서로 다른 test 클래스**를 겨눈다 — 같은 클래스를 겨누는 둘은 서로의 RED 를
가릴 수 있어 나눴다(간격 `ZERO` 와 「간격을 상한 앞으로」가 그 짝이다).

| 변이 | RED(클래스 · 메서드 수) | 같은 run 에서 초록으로 남은 통제군 |
| --- | --- | --- |
| 개찰 처분 표에서 차단을 「계속」으로 | `OpeningAccessBlockedHaltTest` 4/5 | `CollectNoticesUseCaseTest` 17/17(공고 갈래는 자기 표를 읽는다) · `CollectionWiringTest` 21/21 |
| **출하** 표에서 403 행 제거 | `KonepsShippedPolicyWireTest` 2/5 | `KonepsStatusDispositionTest` 6/6 — 좁힌 test 정책은 상수 표를 그대로 쓰므로 **출하 값을 보는 자리만** 붉어진다 |
| **출하** `minimumCallInterval = ZERO` | `KonepsShippedPolicyWireTest` 2/5 | `KonepsCallPacerTest` — 자기 값으로 돌아 출하 값을 재지 않는다(v H-1 이 지목한 빈 자리) |
| **배선**이 pacer 에 `Duration.ZERO` 를 넘긴다 | `CollectionWiringTest` 1/21 | `KonepsShippedPolicyWireTest` 6/6 · `KonepsCallPacerTest` 8/8 — 표의 값과 관문의 기계는 그대로라 **운반 단계만** 붉어진다 |
| 배선의 기다림이 잠들지 않는다(식으로 바꿈) | `CollectionWiringTest` 1/21 | 같은 class 의 「넘기는 값」 단언은 **초록** — 두 축을 따로 재기 때문이다 |
| **기본값** 기다림 함수를 잠들지 않는 람다로(MW3) | `CollectionWiringSleeperTest` 1/4 — 「배선이 세운 소스 둘은…」 | 같은 class 의 값·잠듦·자리 단언 셋 **초록** · `CollectionWiringTest` 19/19 |
| 호출 자리가 **자기 sleeper 를 명시**(MW4) | 같은 단언 1/4 | 같은 class 셋 초록 · `CollectionWiringTest` 19/19 |
| 선언 파일 **밖**에 호출자를 하나 더(MW4c) | 「전송을 만드는 production 자리는 둘뿐이다」 1/4 | 거동 단언 초록(그 자리는 쓰이지 않는다). 같은 run 에서 기존 게이트 둘(`AppHttpDependencyGateTest`·`CollectionArchitectureGateTest`)도 그 fixture 의 자격 타입 참조를 잡는다 |
| 5인자 호출을 **버리고** 선언 파일 안 헬퍼의 잠들지 않는 전송을 쓴다(MW4d) | 거동 단언 1/4 | **앞 판에서 이 변이가 초록이었다**(v r4 L-1) — 참조 형태를 세던 술어를 **쓰이는 인스턴스**로 바꾼 것이 이 RED 다 |
| pacer 가 다음 하한을 기다리기 **전** 시각에서 센다 | `KonepsCallPacerTest` 1/8 | `KonepsCallGateTest` 3/3 · 같은 class 의 나머지 7 — 늦음이 **한 번뿐**인 판에서만 갈린다 |
| 청중 장애를 빈 목록으로 접기 | `NotificationAudienceFailureTest` 2/2 | — |
| 청중 실패에서 다시 던지기 | `InboxRecordingNotificationsTest` 2/9 | `CollectNoticesUseCaseTest` 17/17 |
| 청중 표지 로그 제거(`debug("")`) | `NotificationAudienceMarkerTest` 1/1 | `CollectionWiringTest` 21/21 |
| 표지에서 **예외 클래스 이름** 제거 | `NotificationAudienceMarkerTest` 1/1 | `OpeningAccessBlockedHaltTest`·`InboxRecordingNotificationsTest` 의 같은 run 다른 변이와 분리 계수 |
| 알림함 기록을 채널 뒤로 | `InboxRecordingNotificationsTest` 1/9 | 결말 단언 둘(채널 실패에도 기록 1건 · 기록 실패에도 채널 1회)이 **초록으로 남는다** — v L-1 ① 이 지적한 그 사실 |
| `awaitTurn` 을 상한 검사 앞으로 | `KonepsShippedPolicyWireTest` 1/5 | `KonepsCallGateTest` 3/3 · `KonepsCallPacerTest` 7/7 |
| 사유 모집단에서 차단을 빼기(test 대역) | `OpeningAccessBlockedHaltTest` 1/5 | 나머지 4 — **모집단 등식이 비는 쪽도 붉어진다**는 대리 측정이다(새 `data class` 사유를 지어낼 수는 없다) |

세 축이 요점이다. ⓐ 출하 값을 겨눈 변이는 **좁힌 정책으로 도는 test 를 붉히지 않는다** — 그
비대칭이 「출하 값이 wire 에 닿는가」의 증거다. ⓑ 배선 변이 둘은 **서로 다른 단언**을 붉힌다(값과
기다림을 따로 재기 때문이다) — 한 단언으로 둘을 덮으려던 앞 판은 **MW1 을 통과했다**: 계수 축
(rate limiter 1 permit/1초)이 같은 크기의 지연을 만들어 벽시계에서 두 축이 섞였다(실측 1.088s,
초록). 그래서 그 단언을 지우고 둘로 갈랐다. ⓒ 순서·대기 변이는 앞 판 test 를 초록으로 두고 새
단언만 붉힌다.

**게이트 둘이 이 라운드의 코드를 거부했고 그 거부가 측정이다**: `javaClass.simpleName` 은 수집
리플렉션 게이트가(선례와 같은 자리에 쌍 하나 등재), 주입 인자가 생긴 `konepsTransportFor` 는 전송
표면 게이트(3층)의 **양방향 등식**이 낡은 서명으로 거부했다(서명 하나 갱신). 둘 다 열거를 넓히는
것이 아니라 관측과 맞추는 편집이고, 양방향이라 쓰이지 않는 등재도 붉어진다.

변이는 모두 커밋 **뒤에** 적용했고 원복은 변이 파일만 `git restore --source=HEAD --worktree --`
로 했다(미커밋 산출물을 지우는 `checkout --` 금지). 원복 뒤 작업 트리에 남은 변경이 evidence
뿐임을 `git status --porcelain` 으로 확인했다.

## 4a. 둘째 병합(main `97ed49c4`) — 겹침을 계수로 본다

충돌 해소 줄이 **0**(`git show --cc` 빈 출력)이라 자동 병합이고, 그래서 **합집합 함정을 계수로
쟀다**(충돌 0 이 「접히지 않았다」를 뜻하지 않는다).

| 겹친 경로 | 내 변경 / main 변경 | 병합 결과 |
| --- | --- | --- |
| `config/quality/gate-tests.properties` | +8 / +8 −2(전부 주석) | 등재 **530 = main 522 + 레인 8**, 즉 내 것 ∪ main(누락 0 · 초과 0 · 중복 0). main 이 지운 등재는 **없다**(집합 차 빈 것으로 확인) |
| `adapters/…/evaluation/EvaluationAdapterDependencyTest.kt` | +13 / +105 | `git diff <원천>..HEAD` 가 **내 13 줄만** 낸다(삭제 0) — main 의 105 줄이 그대로 남았고 내 줄이 그 위에 얹혔다 |

그 Kotlin 파일은 **전문을 읽었다**. main 이 더한 것은 `NoticeWatchSubjectPort` 축의 **필드 참조
허용 목록**과 그 양성·음성 대조이고, 내 줄은 **패키지 단위 허용 루트**(`workflow.notificationinbox`)와
그 대조 둘이다 — 두 축이 **다른 집합**이라 한쪽이 다른 쪽을 느슨하게 만들지 않는다. 병합 뒤 그
class 가 `tests=12 failures=0` 이다(앞 판보다 늘어난 수가 main 의 축이다).

`NoticeWatchSubjectPort.kt`(#134 가 바꾼 main 소스)와 persistence test 둘은 **내 경로가 아니다** —
겹침 목록이 그것을 계수로 말한다.

## 4b. 셋째 병합(main `56bd33ea`, #137 paper-bid V25) — 의미 축이 겹쳤다

**충돌 둘이 났고 양쪽을 보존해 풀었다**(앞 두 병합은 자동이었다).

| 겹친 경로 | 내 변경 / main 변경 | 해소 |
| --- | --- | --- |
| `config/quality/gate-tests.properties` | +8 / +3 | 두 쪽이 **같은 사전순 자리**에 넣어 충돌 — 합집합으로. **등재 집합 등식이 참이다**(누락 0 · 초과 0 · 중복 0). 수를 적을 때의 계수법은 `grep -oE '\bbidvector\.[A-Za-z0-9_.]+Test\b' … | sort -u | wc -l`(고유 FQN) 하나로 고정한다(v r4 L-2 — 앞 두 라운드가 다른 계수법으로 523·530 을 적었다): main 519 ∪ 레인 527 = **528**(이 라운드의 새 class 포함) |
| `app/…/wiring/NotificationInboxWiring.kt` | +38 / +8 −3 | main 의 표지 줄(`bidKind=…`)과 이 레인의 청중 표지 함수가 **같은 꼬리**에 붙어 충돌 — 둘 다 남겼다 |
| `workflow/…/notificationinbox/NotificationInboxPorts.kt` | +51 / +22 −8 | 자동 병합. 두 쪽 모두 보존(줄 수 189 = main 그대로 + 내 청중 타입) |
| `config/quality/architecture-policy.properties` | +2 −1 / +2 | 자동 병합. 키 139 개를 합집합과 대조해 **불일치 1** — 그 하나는 내 쪽이 **치환**한 전송 서명이고(낡은 서명이 사라지는 것이 맞다) 양방향 등식 게이트가 초록으로 그것을 확인한다 |
| `docs/discovery/capability-map.md` | +3 −3 / +5 −4 | 자동 병합. 줄 수 3972 = main 그대로(내 편집은 줄 수 불변 치환) |

**의미 축**: #137 이 알림함 키에 투찰 종류(`NotificationBidKind`, 기본값 없음)를 더했고, 계약의
「나중에 머지되는 쪽이 맞춘다」가 이 레인이다. **컴파일이 예고한 한 자리에서 멈췄다** — 생산자의
record 생성 한 줄(「actual type is NoticeId」). 새 후보는 건이 생기기 **전**의 소식이라 축이 없고,
#137 의 종류 전수 표가 그 자리를 `NONE` 으로 정한다(`NEW_CANDIDATE` → `PER_NOTICE`).

**두 층에서 단언한다**: 커널 test 가 기록된 묶음의 축을, **실 DB test 가 되읽은 `bid_kind` 열**을
본다. 변이 `NONE` → `REAL` 은 커널 `require` 가 먼저 막아 생산자를 지나는 test 전부를 붉힌다
(단위 6/9 · 실 DB 2/3) — 통제군으로 #137 자신의 `NotificationInboxBidKindTest` 4/4 가 초록이다
(그 test 는 이 생산자를 지나지 않는다). 저장 층 짝 CHECK 축은 #137 의
`NotificationInboxPairConstraintTest` 5/5 가 든다.

## 5. clean-tree 게이트

`git status --porcelain -- <복원 목록 경로 개별 인자>` **빈 출력**. 양성 대조 1회 — 한 파일에
개행 하나를 붙이면 그 파일 한 줄이 나오고 `truncate` 로 원래 크기로 되돌리면 다시 빈 출력이다
(비파괴 절삭, `checkout --` 를 쓰지 않았다).

## 6. 비밀값 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 축은 CI 의 누출 게이트가 정본이다(이 slice 는 그
어휘를 evidence 에 축어로 적지 않는다). `leakPatternGate` 를 판정 SHA 의 트리에서 실행해 exit 0 을
실측했다. 이 라운드가 더한 문자열에 외부 값이 없다 — 전송 경로는 상태 코드 숫자 하나를 싣고,
청중 표지는 **계수도 주체도 싣지 않는** 고정 문장 하나다.
