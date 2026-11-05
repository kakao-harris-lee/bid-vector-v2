# M9/ops-code commands

base `df4976cc` · 판정 SHA **`fe17d7d6`**(마지막 산출물 커밋). 레인 규율대로 push 하지 않았다.

로컬은 **표적 `--tests` + 그 모듈 lint + 변이**만 돌렸다. 전건 `check`·`qualityBaseline` 은 PR CI
가 정본이고 명령이 같다. Gradle 은 전부 `~/.local/bin/bv-build.sh ./gradlew --no-daemon …`(빌드
자리 둘 · 획득 순간 메모리 재확인)로 돌렸고 레인이 남긴 daemon 은 없다(`--no-daemon`, 종료 뒤
`ps` 로 확인 — 남은 java 는 다른 레인의 것이고 `/proc/<pid>/cwd` 로 귀속을 확인했다).

## 1. 커밋

| SHA | 무엇 |
| --- | --- |
| `c724e1ee` | slice 계약 |
| `7532008c` | OPS-09 — 상태 코드 축의 재시도 처분 |
| `0d172b1b` | OPS-08 — 연속 호출 사이의 최소 간격 |
| `d5f8c0b6` | NOTI-01 — 알림함 생산자(커널·포트) |
| `8aa35d79` | evidence 1차(판정 SHA `d5f8c0b6` 기준 — `rollback.md` 는 2차에서 다시 쟀다) |
| `b54bf047` | 계약 갱신(팀장 승인 — in_scope 일곱 경로, 기각한 대안 둘) |
| `0569e407` | NOTI-01 production 배선(실 DB 측정 포함) |
| `fe17d7d6` | 측정 상태 장부 — acceptance-trace 세 행 |

## 2. 표적 실행 — 핵심 결과 한 줄씩

| 명령 | 결과 |
| --- | --- |
| `:adapters:test --tests 'bidvector.adapters.koneps.*'` | BUILD SUCCESSFUL(새 test 클래스 둘 포함) |
| `:adapters:test --tests 'bidvector.adapters.evaluation.*'` | BUILD SUCCESSFUL(새 실 DB test 포함 · 의존 게이트 재실행) |
| `:adapters:test --tests 'bidvector.adapters.notificationinbox.*'` | BUILD SUCCESSFUL |
| `:procurement:test --tests '…CollectionAttemptLedgerTest' --tests '…AccountingTest'` | BUILD SUCCESSFUL |
| `:workflow:test --tests 'bidvector.workflow.collection.*'` | BUILD SUCCESSFUL |
| `:workflow:test --tests '…evaluation.InboxRecordingNotificationsTest'` | `tests=5 failures=0` |
| `:adapters:test --tests '…evaluation.EvaluationCommitInboxProductionTest'` | `tests=3 failures=0`(실 DB) |
| `:app:test --tests 'bidvector.app.evaluation.*'` | BUILD SUCCESSFUL(커밋 E2E·러너·차분 전건) |
| `:app:test --tests 'bidvector.app.wiring.*'` | BUILD SUCCESSFUL |
| `:app:test --tests 'bidvector.app.architecture.*'` | BUILD SUCCESSFUL — 절단 사유 분류 게이트와 dry-run/커밋 분리 게이트 포함 |
| `leakPatternGate` | BUILD SUCCESSFUL |
| ktlint(main·test)·detekt — adapters · procurement · workflow · app | 전부 BUILD SUCCESSFUL |

새 test 클래스 넷은 만드는 커밋에서 `config/quality/gate-tests.properties` 의 해당 블록에 사전순
으로 등재했다 — 표적 실행은 그 등재 게이트를 돌리지 않으므로 등재를 뒤로 미루지 않았다.

## 3. 실 Spring 컨텍스트 — OPS-08 의 비용 실측

간격이 전송 정책 값이라 실 컨텍스트를 띄우는 수집 E2E 가 그 값을 쓴다. 같은 HEAD 에서 **간격만
0 으로 둔 통제군**과 비교했다(`--rerun`, cache 적중 아님).

| test | 간격 1초 | 통제군(간격 0) |
| --- | --- | --- |
| `CollectionRunnerE2ETest` | 61.9s · 실패 0 | 41.0s · 실패 0 |
| `OpeningPageSizeE2ETest` | 92.0s · 실패 0 | 87.1s · 실패 0 |
| `OpeningCollectionE2ETest` | 97.4s · 실패 0 | — |
| `SnapshotExtractionE2ETest` | 63.6s · 실패 0 | — |
| `CollectionWiringTest` · `OpeningCollectionWiringTest` | 0.4s · 0.2s · 실패 0 | — |

**+40~60s 를 PR CI 에 더한다**(네 E2E 클래스 합산 추정, 측정된 두 클래스의 +26s 를 근거로).
팀장 승인을 받았고 알려진 제한에 등재했다. 간격을 Spring 설정으로 옮기면 E2E 가 0 으로 덮을 수
있지만 그때는 기존 배포 설정 전부가 값을 넣어야 기동하고 그 경로는 out_scope 다.

## 4. 변이 — 바꿔치우기, 커밋 뒤, 변이 파일만 원복

| 변이 | RED |
| --- | --- |
| `receivedStep` 을 앞 판 꼴로(429 만 예외, 나머지는 봉투) | `403 은 재시도 가능 봉투를…` · `401 도 같은 차단 축이다` — 2/6 |
| 처분 표의 429 행을 `ACCESS_BLOCKED` 로 뒤집기 | `429 는 여전히 재시도되고 회복된다`(통제군) · `봉투 해석을 건너뛰는 상태는 정확히 셋이다` — 2/6 |
| 관문에서 `pacer.awaitTurn()` 호출 제거 | `관문을 지난 호출들의 원장 시각이…` — 1/7 |
| pacer 가 하한만 적고 기다리지 않게 | 간격 거동 셋 — 3/7 |
| 커밋 조립에서 알림함 감싸기 제거(앞 판 꼴) | `가치 게이트를 통과한 후보가…` · `같은 공고로 두 번 돌려도…` — 2/3(실 DB) |
| 청중 질의가 아무 계정도 내지 않게(`WHERE false`) | 같은 둘 — 2/3(실 DB) |

두 축이 요점이다. ⓐ 둘째 변이가 **통제군을 죽인다** — 「상태 코드를 봤더니 전부 비재시도」라는
과잉 처방이 초록으로 지나가지 않는다. ⓑ 다섯째 변이는 D-6F10 R1-H-1 이 겨눈 바로 그 구멍(「커널은
맞는데 조립이 그것을 안 쓴다」)을 재현한 것이고, 두 변이에서 「청중이 비면 기록 0 건」 test 는
**초록으로 남는다**(음성 대조 — 그 test 는 기록 부재를 재므로 기록이 사라지면 여전히 참이다).

변이는 모두 커밋 **뒤에** 적용했고 원복은 변이 파일만 `git restore --source=HEAD --worktree --`
로 했다(미커밋 산출물을 지우는 `checkout --` 금지).

## 5. clean-tree 게이트

`git status --porcelain -- <복원 목록 경로 개별 인자>` **빈 출력**. 양성 대조 1회 — 한 파일에 개행
하나를 붙이면 그 파일 한 줄이 나오고, `truncate` 로 원래 크기로 되돌리면 다시 빈 출력이다
(비파괴 절삭, `checkout --` 를 쓰지 않았다).

## 6. 비밀값 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 축은 CI 의 누출 게이트가 정본이다(이 slice 는 그
어휘를 evidence 에 축어로 적지 않는다). `leakPatternGate` 를 HEAD 에서 실행해 exit 0 을 실측했다.
전송 경로에 새로 생긴 문자열은 상태 코드 숫자 하나(`HTTP <status>`)이고 본문·URI 는 싣지 않는다 —
기존 `describeTransport` 와 같은 접기다. 청중 질의는 `account` 의 **id 열만** 읽는다(로그인
식별자·해시·잠금 시각을 읽지 않는다).
