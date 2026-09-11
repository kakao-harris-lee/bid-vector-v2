# M4/4B-5 rollback

base_sha: `9eddf7525b74f89ce279acbb3adf4948f05c25f1`

## 공유 파일 — 범위 확인

이 브랜치(`m4-4b5/2026-09-10`)는 `9eddf75`에서만 가른 격리 브랜치다(팀장 브리핑
「다른 경로는 다른 lane」). `config/quality/gate-tests.properties`·`milestone-4.md`·
`docs/discovery/capability-map.md`(계약 갱신 5 로 in_scope 편입, verifier r1 F-4)는
scope.md가 명시한 공유 파일이지만, `base..HEAD` 범위 안에서는 **이 slice의 커밋만**
그 셋을 건드렸다(`git log --oneline 9eddf7525b74f89ce279acbb3adf4948f05c25f1..HEAD --
config/quality/gate-tests.properties milestone-4.md docs/discovery/capability-map.md`
— commands.md 「하네스 레인 변경 확인」과 같은 방식으로 실측, 이 절은 코드 편집 자체가
아니라 그 세 파일의 커밋 이력을 잰다). 따라서 hunk 단위 격리 없이 **전체 restore로
충분하다**.

## 대상 파일 목록(기계적으로 냄, verifier r1 F-4 반영 — capability-map.md 추가)

`git diff --name-status 9eddf7525b74f89ce279acbb3adf4948f05c25f1..HEAD` 출력(evidence
커밋 포함 최종 상태 기준 — 라운드마다 이 목록을 다시 낸다, 손으로 갱신하지 않는다):

```
M  config/quality/gate-tests.properties
A  decision/src/main/kotlin/bidvector/decision/priority/derive/Band.kt
A  decision/src/main/kotlin/bidvector/decision/priority/derive/ComplexityDerivation.kt
A  decision/src/main/kotlin/bidvector/decision/priority/derive/DerivationAbsence.kt
A  decision/src/main/kotlin/bidvector/decision/priority/derive/DerivationInputs.kt
A  decision/src/main/kotlin/bidvector/decision/priority/derive/DerivationPolicyData.kt
A  decision/src/main/kotlin/bidvector/decision/priority/derive/Derivations.kt
A  decision/src/main/kotlin/bidvector/decision/priority/derive/MarginDerivation.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/BandTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/BudgetCaptureDerivationTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/DerivationPolicyDataTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/DerivationTestSupport.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/ExecutionComplexityDerivationTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/ExpectedMarginDerivationTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/LoadRatioDerivationTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/UrgencyDerivationTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/derive/WorkloadDerivationTest.kt
M  docs/discovery/capability-map.md
M  milestone-4.md
A  reports/evidence/m4/4b5/checklist.md
A  reports/evidence/m4/4b5/commands.md
A  reports/evidence/m4/4b5/policy-values.md
A  reports/evidence/m4/4b5/rollback.md
A  reports/evidence/m4/4b5/scope.md
```

`A` 21 · `M` 3 (verifier r1 실측 D 21/M 3 과 일치). evidence 넷(`checklist`·`commands`·
`policy-values`·`rollback.md`)이 이번엔 목록에 실제로 들어 있다 — 「이후 낼 목록에
추가된다」예고가 이번 재산출로 실현됐다.

## 되돌리는 방법

`A`(신규)와 `M`(수정) 전부 같은 명령으로 base 상태로 되돌린다 — `--source`에 없는
경로는 삭제되므로 신규 파일에 별도 `git rm`이 필요 없다:

```bash
git restore --source=9eddf7525b74f89ce279acbb3adf4948f05c25f1 --staged --worktree -- \
  config/quality/gate-tests.properties \
  decision/src/main/kotlin/bidvector/decision/priority/derive \
  decision/src/test/kotlin/bidvector/decision/priority/derive \
  docs/discovery/capability-map.md \
  milestone-4.md \
  reports/evidence/m4/4b5
```

디렉터리 경로(`decision/.../derive`·`reports/evidence/m4/4b5`)를 넘기면 그 아래 신규
파일 전부가 삭제 대상에 포함된다. `docs/discovery/capability-map.md`는 파일 하나라
`restore`가 그 파일의 diff hunk(+1줄, `OPEN-4B5-COMPETITIVENESS` 행)만 base 로 되돌린다
— 그 문서의 다른 슬라이스 편집과 섞이지 않는다(hunk 자체가 다른 커밋과 겹치지 않음,
`git log` 로 이미 확인). `reports/evidence/m4/4b5/scope.md`는 이 slice의 계약 문서
자체이므로, 계약은 유지한 채 코드만 되돌리려면 `reports/evidence/m4/4b5`를 이 경로
목록에서 빼고 `scope.md`만 남긴다.

**4B-4 산출물(`decision/src/main/kotlin/bidvector/decision/priority/*.kt`, `derive` 아래
아님)은 이 restore 경로에 포함되지 않는다** — 이 slice는 그 파일들을 편집하지 않았다
(scope.md out_of_scope).

## 예상 복구 시간

수 분(명령 1회, 신설 패키지 디렉터리 삭제뿐 — 공유 파일 hunk 충돌 없음).

## 임시 clone 실측(verifier r1 F-4 반영 뒤 재실측 — 마지막 코드 커밋 뒤, commands.md 기록)

- `git clone --no-hardlinks . <tmp-dir> && cd <tmp-dir> && git checkout m4-4b5/2026-09-10`
- 위 restore 명령 실행 → `git status --porcelain` D 21(main 7·test 9·evidence 5)/M 3
  (`gate-tests.properties`·`capability-map.md`·`milestone-4.md`) — 위 「대상 파일 목록」과
  일치.
- `git diff 9eddf7525b74f89ce279acbb3adf4948f05c25f1 -- <같은 경로들>` — 0줄(base 와
  완전 일치), `derive` 디렉터리 자체 소멸(`ls` `No such file or directory`).
- 되돌린 트리에서 `./gradlew --no-daemon :decision:compileKotlin :decision:test
  :decision:gateExecutionGate` — `BUILD SUCCESSFUL`, 기존 test(4B-1·4B-3·4B-4) 전부
  통과. 임시 clone은 실측 뒤 삭제.
