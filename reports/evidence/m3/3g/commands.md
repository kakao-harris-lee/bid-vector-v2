# commands.md — M3 / 3G

worktree `/Users/harris/Development/private/bid-vector-v2-m4`, branch `m4/2026-09-08`.
base `6e3aea4`, head(이 문서 시점) `e6a6200`(코드 커밋 `be9daa4` + 문서 커밋 `e6a6200`).

## 2026-09-10T10:15:33Z
- cmd: `./gradlew :adapters:test --tests "bidvector.adapters.persistence.CleanMigrationTest" --no-daemon`
- exit: 0
- 핵심 결과: 6 tests, 0 failed — 기대 행렬(테이블 열둘, `flyway_schema_history` 포함)이 첫 실행에서
  DB 실측값과 일치(재작업 없음, `flyway_schema_history` 축 일곱 전부 false 실측 확인).

## 2026-09-10T10:18Z — mutation 넷(임시 probe, 커밋에 없음)
- cmd: `./gradlew :adapters:test --tests "bidvector.adapters.persistence.CleanMigrationTest" --no-daemon`
  (임시로 zmut a~d 4개 test 추가한 상태)
- exit: 1
- 핵심 결과: 10 tests, 4 failed — 넷 다 의도한 축에서 낙제.
  - `zmut a`(outbox `GRANT DELETE ... TO PUBLIC`) → `Values differed at keys outbox`(DELETE 축, PUBLIC 경유)
  - `zmut b`(notice 역할 직접 `GRANT TRUNCATE`, 초과) → `Values differed at keys notice`(TRUNCATE 축)
  - `zmut c`(notice `REVOKE UPDATE`, 부족) → `Values differed at keys notice`(UPDATE 축)
  - `zmut d`(신규 테이블 `zzz_mutation_probe`, 기대 행렬 미선언) → 키 집합 불일치
    (`zzz_mutation_probe`가 discovered에는 있고 expected에는 없음)
  - 넷 다 원복(REVOKE/GRANT/DROP TABLE, try/finally)

## 2026-09-10T10:19Z — 원복 뒤 컨테이너 재기동 확인
- cmd: `./gradlew :adapters:test --tests "bidvector.adapters.persistence.CleanMigrationTest" --no-daemon --rerun-tasks`
  (mutation probe 제거, 클린 상태로 복귀한 파일)
- exit: 0
- 핵심 결과: 6 tests, 0 failed, 29/29 actionable tasks executed(캐시 재사용 아님 — 실 컨테이너
  재기동으로 GRANT/REVOKE 잔존 없음을 확인).

## 2026-09-10T10:20Z — S-1
- cmd: `./gradlew --no-build-cache clean check --no-daemon`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 345 actionable tasks(320 executed·25 up-to-date). ktlint 위반
  (`TablePrivileges(...)` 인자를 한 줄에 여러 개)을 `:adapters:ktlintTestSourceSetFormat`으로
  자동 정리한 뒤 통과.

## 2026-09-10T10:21Z — S-2
- cmd: `./gradlew :adapters:test --no-daemon`
- exit: 0
- 핵심 결과: UP-TO-DATE(S-1의 clean check가 이미 같은 실행을 포함).

## 2026-09-10T10:21Z — S-3
- cmd: `./gradlew :adapters:sizeGate :adapters:cpdCheck --no-daemon`
- exit: 0
- 핵심 결과: 둘 다 UP-TO-DATE(S-1 안에서 이미 실행·통과). `CleanMigrationTest.kt` 402줄(500 한도 안).

## 2026-09-10T10:21Z — S-4
- cmd: `./gradlew :app:test --no-daemon`
- exit: 0
- 핵심 결과: UP-TO-DATE.

## 2026-09-10T10:22Z — S-5
- cmd: `./gradlew qualityBaseline --no-daemon`
- exit: 0
- 핵심 결과: UP-TO-DATE.

## 2026-09-10T10:22Z — S-6
- cmd: `./gradlew :app:gateExecutionGate --no-daemon`
- exit: 0
- 핵심 결과: UP-TO-DATE(S-4와 별도 호출, S-4 뒤 재호출).

## 2026-09-10T10:24Z — S-0
- cmd: `git clone . <scratchpad>/3g-s0-clone && cd <scratchpad>/3g-s0-clone && ./gradlew --no-build-cache clean check --no-daemon`
  (head `e6a6200`)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 354 actionable tasks 354 executed(임시 clone, `--no-build-cache`
  이므로 전건 새로 실행). 정리: `rm -rf` 로 clone 제거.

## secret 스캔
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3g/`
- exit: 1 (매치 없음 = 통과)
- cmd: `git show be9daa4 e6a6200 | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 1 (매치 없음 = 통과)

## clean-tree 게이트
- cmd: `git status --porcelain -- adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt milestone-3.md docs/discovery/capability-map.md reports/evidence/m3/3g`
- exit: 0, 출력은 `?? reports/evidence/m3/3g/commands.md`(이 문서 자신, 작성 중이라 미커밋) 한 줄뿐 —
  코드·문서 in_scope 경로는 전부 커밋됨(경로 개별 인자로 전달).
- **양성 대조**: `CleanMigrationTest.kt`에 임시 한 줄을 append해 같은 명령을 재실행 →
  ` M adapters/.../CleanMigrationTest.kt` 잡힘(exit 0, 출력 있음 — 게이트가 실제로 본다).
  `sed -i '' '$ d'`로 심은 줄만 비파괴 절삭 후 재확인 — `wc -l` 402(원상태)·`git status --porcelain`
  출력 없음(commands.md 신규 파일 제외)·`git diff --stat` 빈 결과로 원상 복구 확인
  (`git checkout --`는 쓰지 않았다 — evidence-pack 규격, 다른 레인의 미커밋 편집을 지울 위험).
