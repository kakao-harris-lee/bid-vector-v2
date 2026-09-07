# rollback.md — harness/test-discovery-guard

되돌림은 in_scope 경로 한정이다(range revert 아님). 하네스 경로(`CLAUDE.md`·`.claude/**`)는
이 range 에 없으므로 되돌릴 것도 없다(commands.md 「하네스 레인 변경」 확인 참고).

```
git restore --source=7581106ecf7c52bbf8bb032adc233e90a8f1eb9b --staged --worktree -- \
  build-logic/src/main/kotlin/bidvector/buildlogic/TestShapes.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/TestShapeGateTask.kt \
  build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts \
  build-logic/src/main/kotlin/bidvector.quality-baseline.gradle.kts \
  build-logic/src/test/kotlin/bidvector/buildlogic/TestShapesTest.kt \
  config/quality/test-shape-policy.properties \
  contracts/tools/breaking-mutations.sh \
  contracts/tools/breaking-mutations-selftest.sh \
  contracts/testdata/breaking \
  reports/evidence/harness/test-discovery-guard \
  reports/evidence/m2/2d/checklist.md \
  docs/discovery/capability-map.md
```

**임시 clone 실측**(2026-09-07, head `6752791`) — exit 0. `git status --porcelain` 변화:
6D(신규 파일 삭제 — `TestShapes.kt`·`TestShapeGateTask.kt`·`TestShapesTest.kt`·
`test-shape-policy.properties`·`breaking-mutations-selftest.sh`·evidence
`scope.md`) + 5M(수정 파일 원복 — `kotlin-conventions.gradle.kts`·`quality-baseline.gradle.kts`·
`breaking-mutations.sh`·`capability-map.md`·`m2/2d/checklist.md`). `contracts/testdata/breaking/**`
는 base 와 내용 동일(S-4 가 무변경으로 실측)이라 이 경로는 diff 에 나타나지 않는다.

**효과**: `testShapeGate`/`buildLogicTestShapeGate` 가 9 모듈 + 루트 `check` 에서 빠지고
`OPEN-2B-TEST-DISCOVERY-GUARD` 방어가 사라진다(2B 원 사례가 재발해도 다시 무방비).
`breaking-mutations.sh`는 F-21~F-23 수정 전 상태(2D 알려진 제한 11~13)로 되돌아간다.
DB write·외부 API·push/merge 는 이 slice가 하지 않으므로 해당 없음. 예상 복구 시간 —
명령 실행(수 초) + `./gradlew --no-build-cache clean check` 재확인(약 30초, 이번 slice의
S-0/S-1 실측 기준).

**알려진 제한**: `reports/evidence/m2/2d/checklist.md`의 11~13 항목이 되돌아가는 것은
「닫힘」 기록도 함께 사라짐을 뜻한다 — 재적용 시 이 문서를 다시 커밋해야 한다(신규
정보 손실은 아니다, git 이력에 남아 있다).
