# 5F-2 rollback

되돌리면 Kotlin 이 다시 `bidvector.ml.v1` 을 보내고, Python 5E-2 servicer 는
`UNSUPPORTED_SCHEMA` 로 거부한다(`OPEN-5E2-FEATURE-SCHEMA-PARITY` 재개).

## 절차

in_scope 경로만 base(`845e29b`)로 되돌린다 — 하네스 경로(`CLAUDE.md`·`.claude/**`)는
대상이 아니다(이 slice 의 range 안에 하네스 커밋이 없음, scope.md 「하네스 레인 변경」 절).

```
git restore --source=845e29b --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt \
  adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt \
  reports/evidence/m4/4d/policy-values.md
rm -rf reports/evidence/m5/5f2
```

`policy-values.md` 는 이 slice 착수 이후 `845e29b..HEAD` 구간을 만진 커밋이 이 slice
뿐이다(`git log --oneline 845e29b..HEAD -- reports/evidence/m4/4d/policy-values.md` —
5f2 커밋 하나만 나옴, 다른 slice 와 겹치지 않음). 그래서 hunk 격리 없이 `base` 전체 복원이
안전하다.

## 목록 — 기계 산출

```
git diff --name-status 845e29b..HEAD
```
결과(M = base 로 restore, A = 삭제 대상):
- M `adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt`
- M `adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt`
- M `reports/evidence/m4/4d/policy-values.md`
- A `reports/evidence/m5/5f2/scope.md`
- A `reports/evidence/m5/5f2/commands.md`
- A `reports/evidence/m5/5f2/rollback.md`
- A `reports/evidence/m5/5f2/golden-manifest.json`(N/A 사유 파일)

라운드가 늘어 파일이 더 붙으면 이 명령을 다시 돌려 목록을 갱신한다.

## 임시 clone 실측 (2026-09-16, 실행 완료)

scratchpad `5f2-rollback-check` 에 `git clone .` 후 위 절차를 실제로 실행했다(dry-run 아님):

1. `git restore --source=845e29b --staged --worktree -- <in_scope 3 파일>` — exit 0
2. `rm -rf reports/evidence/m5/5f2` — exit 0
3. `git diff 845e29b -- <in_scope 3 파일> | wc -l` — `0`(diff 비어 있음)
4. `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` — exit 0(BUILD SUCCESSFUL)
5. `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.MlCallPolicyDataTest"` — exit 0. 되돌린 test 파일은 `@Test` 8개(이 slice 가 더한 parity test 1개가 사라져 8개 — base 상태), `featureSchemaVersion shouldBe "bidvector.ml.v1"` 로 복귀(grep 확인) — base 재현 확인
6. `./gradlew --no-daemon check` — exit 0(BUILD SUCCESSFUL, 346 tasks) — 되돌린 트리도 게이트 전건 통과, 이 slice 는 새 게이트를 추가하지 않아 회귀 없음

## 알려진 제한

되돌리면 4D policy-values.md §3 근거 문면도 「계약 패키지 식별자」 서술로 함께 돌아간다 —
그 서술은 5B 신설 이후 낡은 논리이지만, rollback 은 이 slice 착수 이전 상태로의 복귀이므로
의도된 동작이다(재적용하려면 이 slice 를 다시 진행).
