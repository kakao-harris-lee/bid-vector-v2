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
  adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt \
  reports/evidence/m4/4d/policy-values.md
rm -rf reports/evidence/m5/5f2
```

`RequestMappingTest.kt` 는 계약 갱신 (2)로 in_scope 에 더해졌다(D-5F2-4) — 이 slice 가
그 파일에 더한 것은 test 메서드 하나뿐이고, `base(845e29b)..HEAD` 구간에서 이 파일을 만진
커밋은 이 slice 뿐이라(`git log --oneline 845e29b..HEAD -- adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt`
— 이 slice 커밋만 나옴) hunk 격리 없이 `base` 전체 복원이 안전하다.
`policy-values.md` 도 같은 이유로 안전하다(이 slice 착수 이후 그 파일을 만진 커밋이 이
slice 뿐).

## 목록 — 기계 산출

```
git diff --name-status 845e29b..HEAD
```
결과(M = base 로 restore, A = 삭제 대상):
- M `adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt`
- M `adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt`
- M `adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt`
- M `reports/evidence/m4/4d/policy-values.md`
- A `reports/evidence/m5/5f2/scope.md`
- A `reports/evidence/m5/5f2/checklist.md`
- A `reports/evidence/m5/5f2/commands.md`
- A `reports/evidence/m5/5f2/rollback.md`
- A `reports/evidence/m5/5f2/golden-manifest.json`(N/A 사유 파일)

verifier r1 M-1 — 이전 판이 `checklist.md`(A 5개 중 하나)를 이 목록에서 빠뜨렸다. 절차
자체(`rm -rf reports/evidence/m5/5f2`)는 디렉터리를 통째로 지워 그 누락과 무관하게 항상
`checklist.md`까지 지웠다 — 깨진 것은 「이 목록이 그 명령의 출력과 같다」는 서술이었지 실행
결과가 아니다. 이번 판은 위 `git diff --name-status` 를 다시 돌려 낸 값 그대로다.

라운드가 늘어 파일이 더 붙으면 이 명령을 다시 돌려 목록을 갱신한다.

## 임시 clone 실측 (2026-09-16, 실행 완료)

scratchpad `5f2-rollback-check` 에 `git clone .` 후 위 절차를 실제로 실행했다(dry-run 아님):

1. `git restore --source=845e29b --staged --worktree -- <in_scope 3 파일>` — exit 0
2. `rm -rf reports/evidence/m5/5f2` — exit 0
3. `git diff 845e29b -- <in_scope 3 파일> | wc -l` — `0`(diff 비어 있음)
4. `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` — exit 0(BUILD SUCCESSFUL)
5. `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.MlCallPolicyDataTest"` — exit 0. 되돌린 test 파일은 `@Test` 8개(이 slice 가 더한 parity test 1개가 사라져 8개 — base 상태), `featureSchemaVersion shouldBe "bidvector.ml.v1"` 로 복귀(grep 확인) — base 재현 확인
6. `./gradlew --no-daemon check` — exit 0(BUILD SUCCESSFUL, 346 tasks) — 되돌린 트리도 게이트 전건 통과, 이 slice 는 새 게이트를 추가하지 않아 회귀 없음

## 승인 전 일괄(2) — 재확인 범위 축소 근거

목록이 `RequestMappingTest.kt`·`checklist.md` 로 늘었지만, in_scope 4 파일의 롤백 목표는
여전히 정확히 `base(845e29b)` 상태다 — 이번 라운드가 그 위에 test 를 더 얹거나 지웠어도
**되돌린 뒤의 파일 내용은 base 커밋의 파일 내용과 byte 단위로 같다**(git 이 보장하는
`--source=845e29b` 의미 그대로). 즉 이번 라운드가 되돌리는 트리는 위 「임시 clone 실측」이
이미 컴파일·targeted test 까지 실측한 트리와 **파일 SHA 가 동일**하다(rollback 은 항상
같은 base 좌표로 수렴하므로 그 사이에 얹은 편집 횟수와 무관하다). 그래서 ①②③(restore·
rm·diff 0)과 ④⑤(compile·targeted test)는 재실행하지 않고 — 그 결과가 이미 참임을 SHA
동일성이 보장한다 — ⑥(`check` 전건)만 다시 돌려 이 저장소의 게이트 자체가(다른 파일의
변화로) 되돌린 트리를 붉히지 않는지만 확인한다. 「HEAD 가 초록이니 rollback 도 초록일
것」이 아니라 「되돌린 트리가 r1 이 이미 실측한 트리와 동일하다」가 갈음 근거다.

- cmd: `git log --oneline 845e29b..HEAD -- adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt reports/evidence/m4/4d/policy-values.md`
  결과: 이 slice 커밋만 나옴(다른 slice 와 겹치지 않음 — hunk 격리 불필요, base 전체 복원 유효)
- cmd(scratchpad clone, ⑥만): `./gradlew --no-daemon check` — commands.md 「승인 전 일괄(2) —
  rollback ⑥ 만 재실행」 참조

## 알려진 제한

되돌리면 4D policy-values.md §3 근거 문면도 「계약 패키지 식별자」 서술로 함께 돌아간다 —
그 서술은 5B 신설 이후 낡은 논리이지만, rollback 은 이 slice 착수 이전 상태로의 복귀이므로
의도된 동작이다(재적용하려면 이 slice 를 다시 진행). 되돌리면 `RequestMappingTest.kt` 의
D-5F2-4 test 도 함께 사라진다 — 요청 proto 무보호 공백이 재개된다(재적용하려면 이 slice 를
다시 진행).
