# M6/6F-7 — rollback.md

실측 HEAD: `08fac0dc69aa3a124578777355849cad28b7f24a`(①~⑥ 전부 이 HEAD에서 재실측 —
직전 실측은 `009ec6bd`였고 그 뒤 우회 4 트립와이어 test 커밋이 붙어 재산출했다.
파일 목록·diff 결과는 무변화, ④⑤⑥ 전부 이 HEAD에서 다시 exit 0 확인)

## base 정의

`$(git merge-base HEAD origin/main)`을 원칙으로 하나, 이 머신에서
`git fetch origin main`이 프록시(`ai-proxy.onkakao.net:3128`)에 연결하지 못해(exit 1,
`Couldn't connect to server`) `origin/main`이 낡아 있다(`48043440`, 6A-1 병합 시점).
**로컬 `main` 브랜치가 이 slice의 실제 분기점**(`86093972`, scope.md `base_sha`와
일치, `git rev-parse main`으로 실측)이라 그것을 base로 쓴다 — 이 저장소의 관례
(`git fetch . <slice>:main`으로 로컬 main을 ff)상 로컬 `main`이 origin/main보다
신선하다.

## 되돌리는 경로(기계 산출, `git diff --name-status <base>..HEAD` 를 in_scope
경로로 좁힌 결과 — 손으로 쓰지 않는다)

```
M  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt
M  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt
A  adapters/src/test/resources/compile-fixtures/negative-7-notification-request-ctor.kt.txt
M  config/quality/gate-tests.properties
A  workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt
A  workflow/src/main/kotlin/bidvector/workflow/event/OutboxNotificationRequestPort.kt
A  workflow/src/test/kotlin/bidvector/workflow/event/OutboxNotificationRequestPortTest.kt
```

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
  workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt \
  workflow/src/main/kotlin/bidvector/workflow/event/OutboxNotificationRequestPort.kt \
  workflow/src/test/kotlin/bidvector/workflow/event/OutboxNotificationRequestPortTest.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt \
  adapters/src/test/resources/compile-fixtures/negative-7-notification-request-ctor.kt.txt \
  config/quality/gate-tests.properties
```

`--source`에 없는 경로(신규 파일 넷)는 자동 삭제된다 — 별도 `git rm` 불필요(실측
확인, 아래).

## 임시 clone 실측(①~⑥, 전부 `/tmp/m6f7-rollback-verify`에서 실행 뒤 삭제)

| # | 확인 | 결과 |
| --- | --- | --- |
| ① 목록 산출 | `git diff --name-status $BASE..HEAD -- <8경로>` | A 4 · M 4(위 목록과 동일) |
| ② restore 실행 | 위 명령 | exit 0 |
| ③ diff 비어 있음 | `git diff $BASE -- <8경로>` | exit 0, 빈 출력 |
| ③′ 신규 파일 삭제 확인 | `ls` 4개 신규 경로 | 전부 "No such file or directory"(삭제됨) |
| ④ compile | `./gradlew --no-daemon :workflow:compileKotlin :adapters:compileKotlin :workflow:compileTestKotlin :adapters:compileTestKotlin` | exit 0 |
| ⑤ test | `./gradlew --no-daemon :workflow:test :adapters:test` | exit 0 |
| ⑥ 게이트 | `./gradlew --no-daemon :workflow:detekt :adapters:detekt :adapters:jarContentGate :adapters:sizeGate :workflow:sizeGate :workflow:runKtlintCheckOverMainSourceSet :workflow:runKtlintCheckOverTestSourceSet :adapters:runKtlintCheckOverMainSourceSet :adapters:runKtlintCheckOverTestSourceSet` | exit 0 |
| ⑥′ 등재 완전성 | `./gradlew --no-daemon :workflow:test --tests "bidvector.workflow.WorkflowGateRegistrationTest" :adapters:test --tests "bidvector.adapters.event.EventGateRegistrationTest"` | exit 0(되돌린 뒤 `gate-tests.properties`와 소스 트리가 다시 일치) |

## 실측 유효성 대조(2026-09-19 정정 규율)

```
git diff --name-only 08fac0dc..<판정 SHA> -- \
  workflow/src/main/kotlin/bidvector/workflow/event/NotificationRequestedPayload.kt \
  workflow/src/main/kotlin/bidvector/workflow/event/OutboxNotificationRequestPort.kt \
  workflow/src/test/kotlin/bidvector/workflow/event/OutboxNotificationRequestPortTest.kt \
  adapters/src/main/kotlin/bidvector/adapters/event/OutboxPayloadCodec.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/OutboxPayloadCodecTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/EventInternalClosureCompileTest.kt \
  adapters/src/test/resources/compile-fixtures/negative-7-notification-request-ctor.kt.txt \
  config/quality/gate-tests.properties
```

`08fac0dc`가 이 evidence 작성 시점의 마지막 산출물 커밋이다. evidence 편집 커밋만
그 뒤에 붙을 수 있고, evidence 커밋은 위 8경로를 건드리지 않으므로(evidence는
`reports/evidence/m6/6f7/**`에만 쓴다) 판정 SHA가 그 이후 어디든 위 명령은 빈
출력이어야 유효하다. 판정 레인은 자신의 판정 SHA로 이 명령을 재실행해 빈 출력을
확인한다.

## 마이그레이션 비대칭

없음 — 이 slice는 스키마를 만들지 않는다(D-6F7-1).
