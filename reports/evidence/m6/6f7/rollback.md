# M6/6F-7 — rollback.md

실측 HEAD: `6f5ae4f8c07f52f723a7c38bcfddd3897afe93a5`(수정 라운드 1 — D-6F7-12
codec 이스케이프 수정 + sink KDoc OPEN 재작성분 이관, ①~⑥ 전부 이 HEAD에서 재실측)

## base 정의

`$(git merge-base HEAD origin/main)`이 원칙이다. **이번 라운드는 `git fetch origin
main`이 성공했다**(이전 라운드의 프록시 연결 실패는 해소됨). 그러나
`origin/main`(`48043440`)은 여전히 로컬 `main`(`86093972`)보다 한 커밋 낡다 —
`git merge-base --is-ancestor 48043440 86093972` 참으로 확인, 둘의 유일한 차이는
`milestone-6.md`(`git diff --name-status 48043440..86093972`) 하나뿐이고 그 파일은
in_scope 밖(팀장 소유 문서)이다. 그러므로 **base를 `48043440`으로 쓰든 로컬
`main`(`86093972`)으로 쓰든 in_scope 8경로의 diff·rollback 절차는 동일하다**(아래
①에서 두 base 모두로 재실측해 동일 목록임을 확인). scope.md `base_sha`와 일치하는
로컬 `main`을 이번에도 절차 표기에 쓴다.

## 되돌리는 경로(기계 산출, `git diff --name-status <base>..HEAD` 를 in_scope
경로로 좁힌 결과 — 손으로 쓰지 않는다)

```
M  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt
M  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt
A  adapters/src/test/resources/compile-fixtures/negative-7-notification-request-ctor.kt.txt
M  config/quality/gate-tests.properties
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPort.kt
A  workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt
A  workflow/src/test/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPortTest.kt
```

**D-6F7-7로 경로 둘이 바뀌었다** — sink(main)·sink test 가 `workflow.event`에서
`workflow.evaluation`으로 옮겨졌다(순 diff는 `base..HEAD`에서 새 경로만 보인다 —
옛 경로는 base·HEAD 어느 쪽에도 없어 목록에 안 잡힌다, git의 정상 동작).

**계약 갱신 (3) D-6F7-8과 대조** — 팀장이 `scope.md`의 `in_scope`에 sink main·
sink test·compile fixture 세 경로를 추가했다(착수 판이 D-6F7-7의 패키지 이동을
`in_scope` glob에 반영하지 않았던 것을 바로잡음). **위 8경로 목록은 그 갱신 전
라운드부터 이미 이 세 경로를 포함하고 있었다** — rollback.md는 `scope.md`의
문자열이 아니라 `git diff --name-status <base>..HEAD`(기계 산출)로 목록을
만들기 때문에 처음부터 실제 변경분을 옳게 잡았다. 수정 라운드 1에서 갱신된
`in_scope` glob으로 같은 명령을 다시 돌려 위 §「이 slice가 닿는 개별 gate」
재대조 결과 **8경로 완전 일치**(추가·누락 없음) — 팀장이 지적한 대로 **rollback
쪽이 처음부터 옳았다.**

**`scope.md`·`milestone-6.md`는 되돌리지 않는다** — 팀장(하네스/문서 레인) 소유
문서다. `scope.md`는 이 slice의 계약 자체이고, `milestone-6.md`의 6F-7 관련 문단도
팀장이 커밋한다(in_scope 표의 주석). 라운드마다 파일이 늘면 위 명령을 재실행해
목록을 재산출한다.

**공유 파일 겹침 확인** — `config/quality/gate-tests.properties`를 만진 다른 slice
커밋은 없다(scope.md 「병행 레인」: 이 시점에 열린 다른 slice 레인이 없음). 그래서
hunk 격리 없이 전체 복원으로 안전하다.

## 절차

```bash
BASE=$(git rev-parse main)   # = 86093972570d016ddc25810872d65137c5e11ef6

git restore --source="$BASE" --staged --worktree -- \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPort.kt \
  workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPortTest.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt \
  adapters/src/test/resources/compile-fixtures/negative-7-notification-request-ctor.kt.txt \
  config/quality/gate-tests.properties
```

`--source`에 없는 경로(신규 파일 넷)는 자동 삭제된다 — 별도 `git rm` 불필요(실측
확인, 아래).

## 임시 clone 실측(①~⑥, 전부 버릴 clone 에서 실행 뒤 삭제, 수정 라운드 1 —
실측 HEAD `6f5ae4f8`)

| # | 확인 | 결과 |
| --- | --- | --- |
| ① 목록 산출 | `git diff --name-status $BASE..HEAD -- <8경로>`(base=merge-base(HEAD, origin/main)=로컬 main과 동일 결과) | A 4 · M 4(위 목록과 동일, 팀장 D-6F7-8 갱신 뒤에도 불변) |
| ② restore 실행 | 위 명령 | exit 0 |
| ③ diff 비어 있음 | `git diff $BASE -- <8경로>` | exit 0, 빈 출력 |
| ③′ 신규 파일 삭제 확인 | `ls` 4개 신규 경로 | 전부 "No such file or directory"(삭제됨) |
| ④ compile | `./gradlew --no-daemon :workflow:compileKotlin :adapters:compileKotlin :workflow:compileTestKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ test | `./gradlew --no-daemon :workflow:test :adapters:test :app:test --rerun-tasks`(동결 UP-TO-DATE 함정 회피, verifier 지적 반영) | exit 0, 46/46 actionable 전부 실행(`app:test`의 `ArchitectureGateTest` 포함 — 순환 없음 재확인) |
| ⑥ 게이트 | `./gradlew --no-daemon :workflow:detekt :adapters:detekt :adapters:jarContentGate :adapters:sizeGate :workflow:sizeGate :workflow:runKtlintCheckOverMainSourceSet :workflow:runKtlintCheckOverTestSourceSet :adapters:runKtlintCheckOverMainSourceSet :adapters:runKtlintCheckOverTestSourceSet` | exit 0 |
| ⑥′ 등재 완전성 | `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.WorkflowGateRegistrationTest" :adapters:test --tests "bidvector.adapters.event.EventGateRegistrationTest" --rerun-tasks` | exit 0(되돌린 뒤 `gate-tests.properties`와 소스 트리가 다시 일치) |

**D-6F7-12 이스케이프 자체의 변이 실측**(rollback과 별도 축, `commands.md` 「수정
라운드 1」 절에 상세) — 버릴 clone에서 `excludedSamplesField`를 수정 전 형태로
되돌리면(numstat `1  2`, 정확히 수정 diff의 역) 신설 왕복 test가 수정 전과 동일한
예외(`excludedSamples 항목 형식이 아니다: A=B;C=4`)로 RED. 원복 확인 후 clone 삭제.

## 실측 유효성 대조(2026-09-19 정정 규율)

```
git diff --name-only 6f5ae4f8..<판정 SHA> -- \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPort.kt \
  workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OutboxNotificationRequestPortTest.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt \
  adapters/src/test/resources/compile-fixtures/negative-7-notification-request-ctor.kt.txt \
  config/quality/gate-tests.properties
```

`6f5ae4f8`가 이 evidence 작성 시점(수정 라운드 1)의 마지막 산출물 커밋이다.
evidence 편집 커밋만 그 뒤에 붙을 수 있고, evidence 커밋은 위 8경로를 건드리지
않으므로(evidence는 `reports/evidence/m6/6f7/**`에만 쓴다) 판정 SHA가 그 이후
어디든 위 명령은 빈 출력이어야 유효하다. 판정 레인은 자신의 판정 SHA로 이 명령을
재실행해 빈 출력을 확인한다.

## 마이그레이션 비대칭

없음 — 이 slice는 스키마를 만들지 않는다(D-6F7-1).
