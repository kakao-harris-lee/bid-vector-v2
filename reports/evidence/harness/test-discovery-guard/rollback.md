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

**verifier r1 F-5** — D(삭제) 수는 `reports/evidence/harness/test-discovery-guard/` 아래
신규 파일 수에 따라 늘어난다(evidence 라운드마다 새 문서가 생기므로) — 값을 여기 박지
않는다. 코드/설정 경로(신규 6개: `TestShapes.kt`·`TestShapeGateTask.kt`·`TestShapesTest.kt`·
`test-shape-policy.properties`·`breaking-mutations-selftest.sh`)는 고정이고 M(수정 원복,
5개: `kotlin-conventions.gradle.kts`·`quality-baseline.gradle.kts`·`breaking-mutations.sh`·
`capability-map.md`·`m2/2d/checklist.md`)도 고정이다 — 매 라운드 재실측할 것은 evidence
디렉터리의 D 수뿐이다. `contracts/testdata/breaking/**` 는 base 와 내용 동일(S-4 가
무변경으로 실측)이라 이 경로는 diff 에 나타나지 않는다.

**재실측**(2026-09-07, head `a4d5c46` — 이 F-5 수정 직전의 마지막 in_scope 커밋) — 임시
clone 에서 exit 0. `git status --porcelain` 변화: **9D**(evidence 디렉터리 신규 파일
`scope.md`·`commands.md`·`checklist.md`·`rollback.md` 4개 + 코드/설정 신규 5개) **+ 5M**.
다음 리뷰 요청 시점에는 D 수를 다시 재는 것이 정본이다(고정값 아님).

**효과**: `testShapeGate`/`buildLogicTestShapeGate` 가 9 모듈 + 루트 `check` 에서 빠지고
`OPEN-2B-TEST-DISCOVERY-GUARD` 방어가 사라진다(2B 원 사례가 재발해도 다시 무방비).
`breaking-mutations.sh`는 F-21~F-23 수정 전 상태(2D 알려진 제한 11~13)로 되돌아간다.
DB write·외부 API·push/merge 는 이 slice가 하지 않으므로 해당 없음. 예상 복구 시간 —
명령 실행(수 초) + `./gradlew --no-build-cache clean check` 재확인(약 30초, 이번 slice의
S-0/S-1 실측 기준).

**알려진 제한**: `reports/evidence/m2/2d/checklist.md`의 11~13 항목이 되돌아가는 것은
「닫힘」 기록도 함께 사라짐을 뜻한다 — 재적용 시 이 문서를 다시 커밋해야 한다(신규
정보 손실은 아니다, git 이력에 남아 있다).
