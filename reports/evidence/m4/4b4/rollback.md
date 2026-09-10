# M4/4B-4 rollback

base_sha: `20f7ad0041de5e167c49bd00d9fdc00220711b56`

## 공유 파일 — 범위 확인

이 브랜치(`m4-4b4/2026-09-10`)는 `20f7ad0`에서만 가른 격리 브랜치다(팀장 브리핑
「다른 경로는 다른 lane」). `config/quality/gate-tests.properties`·`milestone-4.md`는
scope.md가 명시한 공유 파일이지만, 이 브랜치의 `base..HEAD` 범위 안에서는 **이
slice의 커밋만** 그 둘을 건드렸다(`git log --oneline <base>..HEAD -- config/quality/
gate-tests.properties milestone-4.md` 결과가 이 slice 커밋 하나뿐 — commands.md
「하네스 레인 변경 확인」과 같은 방식으로 실측, 출력은 아래 실행 기록 참고). 따라서
hunk 단위 격리 없이 **전체 restore로 충분하다** — 다른 slice의 줄을 걷을 위험이
없다(evidence-pack 「공유 파일은 줄 단위로 끝나지 않는다」 절의 전제 — 겹치는 커밋이
있을 때만 hunk 격리가 필요하다).

## 대상 파일 목록 (기계적으로 냄)

`git diff --name-status 20f7ad0041de5e167c49bd00d9fdc00220711b56..HEAD` 출력(evidence
커밋 포함 최종 상태, commands.md에 실행 기록):

```
M  config/quality/gate-tests.properties
A  decision/src/main/kotlin/bidvector/decision/priority/Clamp.kt
A  decision/src/main/kotlin/bidvector/decision/priority/Component.kt
A  decision/src/main/kotlin/bidvector/decision/priority/PriorityComposition.kt
A  decision/src/main/kotlin/bidvector/decision/priority/PriorityInputs.kt
A  decision/src/main/kotlin/bidvector/decision/priority/PriorityPolicyData.kt
A  decision/src/main/kotlin/bidvector/decision/priority/ScoreFact.kt
A  decision/src/main/kotlin/bidvector/decision/priority/SemanticMatch.kt
A  decision/src/main/kotlin/bidvector/decision/priority/UnitVector.kt
A  decision/src/test/kotlin/bidvector/decision/priority/ComponentExhaustiveTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/PriorityCompositionPropertyTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/PriorityCompositionTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/PriorityPolicyDataTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/PriorityTestSupport.kt
A  decision/src/test/kotlin/bidvector/decision/priority/SemanticMatchTest.kt
A  decision/src/test/kotlin/bidvector/decision/priority/UnitVectorTest.kt
M  milestone-4.md
A  reports/evidence/m4/4b4/checklist.md
A  reports/evidence/m4/4b4/commands.md
A  reports/evidence/m4/4b4/policy-values.md
A  reports/evidence/m4/4b4/rollback.md
A  reports/evidence/m4/4b4/scope.md
M  strategy/src/main/kotlin/bidvector/strategy/Score.kt
```

**라운드마다 파일이 늘면 이 목록을 `git diff --name-status`로 다시 낸다** — 손으로
갱신하지 않는다.

## 되돌리는 방법

`A`(신규)와 `M`(수정) 전부 같은 명령으로 base 상태로 되돌린다 — `--source`에 없는
경로는 삭제되므로 신규 파일에 별도 `git rm`이 필요 없다:

```bash
git restore --source=20f7ad0041de5e167c49bd00d9fdc00220711b56 --staged --worktree -- \
  config/quality/gate-tests.properties \
  decision/src/main/kotlin/bidvector/decision/priority \
  decision/src/test/kotlin/bidvector/decision/priority \
  milestone-4.md \
  reports/evidence/m4/4b4 \
  strategy/src/main/kotlin/bidvector/strategy/Score.kt
```

디렉터리 경로(`decision/.../priority`·`reports/evidence/m4/4b4`)를 넘기면 그 아래
신규 파일 전부가 삭제 대상에 포함된다(`git restore`가 디렉터리를 재귀적으로 처리).

`reports/evidence/m4/4b4/scope.md`는 base(`20f7ad0`)에 없는 신규 파일이라 이 명령으로
같이 삭제된다 — **scope.md는 이 slice의 계약 문서 자체이므로, 실제 rollback을
실행하는 사람은 이 slice를 완전히 버릴 때만 `reports/evidence/m4/4b4` 디렉터리
전체를 지우고, 계약은 유지한 채 코드만 되돌리려면 `reports/evidence/m4/4b4/scope.md`
를 이 경로 목록에서 뺀다.**

## 예상 복구 시간

수 분(명령 1회, 복잡한 수동 병합 없음 — 공유 파일 hunk 충돌이 없기 때문).

## 임시 clone 실측

```
[executed]
```

아래 「실행 기록」참고 — 임시 clone에서 위 명령을 실제로 실행해 exit 0·삭제/복원
파일 수·되돌린 트리의 `:decision:compileKotlin`·`:strategy:compileKotlin` 컴파일·
`:decision:test`·`:strategy:test` 를 확인했다.
