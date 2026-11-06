# M9/ops-code commands

base `df4976cc` · **병합한 main `3ad4f3f6`**(복원 원천) · 판정 SHA **`e7912022`**(마지막 산출물
커밋). 레인 규율대로 push 하지 않았다.

로컬은 **표적 `--tests` + 그 모듈 lint·게이트 + 변이**만 돌렸다. 전건 `check`·`qualityBaseline`·
container·여정은 PR CI 가 정본이고 명령이 같다. Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew
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

## 2. 표적 실행 — 핵심 결과 한 줄씩(판정 SHA 기준, 병합 뒤)

전부 `--rerun` 이다 — UP-TO-DATE 는 실행이 아니다.

| 명령 | 결과 |
| --- | --- |
| `:procurement:test --rerun`(전건 — sealed 열거 축) | `tests=317 failures=0` |
| `:workflow:test --rerun --tests 'bidvector.workflow.collection.*' --tests '…InboxRecordingNotificationsTest'` | `tests=116 failures=0` |
| `:adapters:test --rerun --tests 'bidvector.adapters.koneps.*' --tests '…NotificationAudienceFailureTest'` | `tests=123 failures=0` |
| `:app:test --rerun --tests 'bidvector.app.architecture.*' --tests '…CollectionWiringTest' --tests '…NotificationAudienceMarkerTest'` | `tests=204 failures=0` |
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

## 5. clean-tree 게이트

`git status --porcelain -- <복원 목록 경로 개별 인자>` **빈 출력**. 양성 대조 1회 — 한 파일에
개행 하나를 붙이면 그 파일 한 줄이 나오고 `truncate` 로 원래 크기로 되돌리면 다시 빈 출력이다
(비파괴 절삭, `checkout --` 를 쓰지 않았다).

## 6. 비밀값 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 축은 CI 의 누출 게이트가 정본이다(이 slice 는 그
어휘를 evidence 에 축어로 적지 않는다). `leakPatternGate` 를 판정 SHA 의 트리에서 실행해 exit 0 을
실측했다. 이 라운드가 더한 문자열에 외부 값이 없다 — 전송 경로는 상태 코드 숫자 하나를 싣고,
청중 표지는 **계수도 주체도 싣지 않는** 고정 문장 하나다.
