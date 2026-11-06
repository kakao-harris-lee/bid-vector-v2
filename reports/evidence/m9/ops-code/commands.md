# M9/ops-code commands

base `df4976cc` · **병합한 main `3ad4f3f6`**(복원 원천) · 판정 SHA **`ae248e3f`**(마지막 산출물
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

## 2. 표적 실행 — 핵심 결과 한 줄씩(판정 SHA 기준, 병합 뒤)

| 명령 | 결과 |
| --- | --- |
| `:workflow:test --tests 'bidvector.workflow.collection.*' --tests '…InboxRecordingNotificationsTest'` | `tests=115 failures=0` |
| `:adapters:test --tests 'bidvector.adapters.koneps.*' --tests '…NotificationAudienceFailureTest'` | `tests=120 failures=0` |
| `:app:test --tests 'bidvector.app.architecture.*' --tests '…CollectionWiringTest'` | `tests=201 failures=0` |
| `:procurement:test --rerun`(전건 — sealed 열거 축) | `tests=317 failures=0` |
| `:app:compileTestKotlin` | BUILD SUCCESSFUL |
| `gateRegistrationGate` — workflow · adapters · app · procurement | 전부 통과(새 test 클래스 셋 등재 포함) |
| ktlint(main·test)·detekt — workflow · adapters · app · procurement | 전부 BUILD SUCCESSFUL |

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

세 묶음으로 돌렸다. 한 묶음의 변이들은 **서로 다른 test 클래스**를 겨눈다 — 같은 클래스를 겨누는
둘은 서로의 RED 를 가릴 수 있어 나눴다(간격 `ZERO` 와 「간격을 상한 앞으로」가 그 짝이다: 간격이
0 이면 상한에 막힌 호출도 기다리지 않아 뒤 변이의 RED 가 사라진다).

| 변이 | RED(클래스 · 메서드 수) | 같은 run 에서 초록으로 남은 통제군 |
| --- | --- | --- |
| 개찰 처분 표에서 차단을 「계속」으로 | `OpeningAccessBlockedHaltTest` 3/5 | `CollectOpeningResultsUseCaseTest` 24/24 · `OpeningQuotaHaltResumptionTest` 1/1 |
| **출하** 표에서 403 행 제거 | `KonepsShippedPolicyWireTest` 2/5 | `KonepsStatusDispositionTest` 6/6 — 좁힌 test 정책은 상수 표를 그대로 쓰므로 **출하 값을 보는 자리만** 붉어진다 |
| 청중 장애를 빈 목록으로 접기 | `NotificationAudienceFailureTest` 2/2 | — |
| **출하** `minimumCallInterval = ZERO` | `KonepsShippedPolicyWireTest` 2/5 | `KonepsCallPacerTest` 7/7 — 그 test 는 자기 값으로 돌아 출하 값을 재지 않는다(v H-1 이 지목한 빈 자리) |
| 알림함 기록을 채널 뒤로 | `InboxRecordingNotificationsTest` 1/8 | 같은 클래스의 결말 단언 둘(채널 실패에도 기록 1건 · 기록 실패에도 채널 1회)이 **초록으로 남는다** — v L-1 ① 이 지적한 그 사실이다 |
| `awaitTurn` 을 상한 검사 앞으로 | `KonepsShippedPolicyWireTest` 1/5 | `KonepsCallGateTest` 3/3 · `KonepsCallPacerTest` 7/7 — 앞 판에서 이 변이가 초록이던 이유다 |
| 청중 질의를 후보마다(지연 계산 제거) | `InboxRecordingNotificationsTest` 1/8 | — |

통제군이 요점이다. ⓐ 출하 값을 겨눈 변이 둘은 **좁힌 정책으로 도는 test 를 붉히지 않는다** — 그
비대칭이 「출하 값이 wire 에 닿는가」를 이 라운드가 새로 쟀다는 증거다. ⓑ 순서·대기 변이는 앞 판
test 를 초록으로 두고 새 단언만 붉힌다 — 지적이 가리킨 빈 자리가 정확히 거기였다.

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
