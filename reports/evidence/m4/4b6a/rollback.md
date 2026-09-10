# M4/4B-6a rollback

base_sha: `786febe39469835a45e9a1c6d6e8081226777364`

## 공유 파일 — 범위 확인

이 브랜치(`m4-4b6a/2026-09-11`)는 `786febe3`에서만 가른 격리 브랜치다(팀장 브리핑
「다른 경로는 다른 lane」). `config/quality/gate-tests.properties`·`milestone-4.md`는
scope.md가 명시한 공유 파일이지만, `base..HEAD` 범위 안에서는 **이 slice(팀장의 scope.md
착수 커밋 포함)의 커밋만** 그 둘을 건드렸다:

```
git log --oneline 786febe39469835a45e9a1c6d6e8081226777364..HEAD -- \
  config/quality/gate-tests.properties milestone-4.md
```

결과 — 이 브랜치의 커밋(`40997a1` 하나)만 나온다. 따라서 hunk 단위 격리 없이 **전체
restore로 충분하다**(4B-5 rollback.md와 같은 판단 근거).

## 하네스 레인 변경 확인

`git log --oneline 786febe39469835a45e9a1c6d6e8081226777364..HEAD -- CLAUDE.md .claude/`
— **없음**(scope.md 「하네스 레인 변경」 절과 일치).

## 대상 파일 목록(기계적으로 냄)

`git diff --name-status 786febe39469835a45e9a1c6d6e8081226777364..HEAD` 출력(evidence
커밋 포함 최종 상태 기준):

```
M  config/quality/gate-tests.properties
M  milestone-4.md
A  reports/evidence/m4/4b6a/checklist.md
A  reports/evidence/m4/4b6a/commands.md
A  reports/evidence/m4/4b6a/policy-values.md
A  reports/evidence/m4/4b6a/rollback.md
A  reports/evidence/m4/4b6a/scope.md
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/KeywordHitsCounter.kt
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/ProfilePorts.kt
A  workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt
A  workflow/src/test/kotlin/bidvector/workflow/evaluation/KeywordHitsCounterTest.kt
A  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt
A  workflow/src/test/kotlin/bidvector/workflow/evaluation/ProfilePortsTest.kt
A  workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt
```

`A` 12 · `M` 2. `reports/evidence/m4/4b6a/scope.md`는 팀장이 착수 계약으로 먼저 커밋한
파일이라(이 레인이 작성하지 않음) 목록에 있지만, 코드만 되돌리고 계약은 유지하려면
아래 명령에서 그 파일 하나만 빼면 된다.

## 되돌리는 방법

`A`(신규)와 `M`(수정) 전부 같은 명령으로 base 상태로 되돌린다 — `--source`에 없는
경로는 삭제되므로 신규 파일에 별도 `git rm`이 필요 없다:

```bash
git restore --source=786febe39469835a45e9a1c6d6e8081226777364 --staged --worktree -- \
  config/quality/gate-tests.properties \
  milestone-4.md \
  reports/evidence/m4/4b6a \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/KeywordHitsCounter.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/ProfilePorts.kt \
  workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/KeywordHitsCounterTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/ProfilePortsTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt
```

`reports/evidence/m4/4b6a` 디렉터리 경로를 넘기면 그 아래 신규 파일 다섯(`scope.md`
포함) 전부가 삭제 대상에 포함된다 — 계약(`scope.md`)은 유지한 채 코드·evidence 넷만
되돌리려면 이 디렉터리 경로 대신 `reports/evidence/m4/4b6a/checklist.md`·
`reports/evidence/m4/4b6a/commands.md`·`reports/evidence/m4/4b6a/policy-values.md`·
`reports/evidence/m4/4b6a/rollback.md` 네 파일을 개별로 나열한다.

**4B-5 산출물(`decision/src/main/kotlin/bidvector/decision/priority/derive/*.kt` 등)은
이 restore 경로에 포함되지 않는다** — 이 slice는 그 파일들을 편집하지 않았다(scope.md
out_of_scope, 4B-6b·4D-2도 마찬가지로 건드리지 않음).

## 예상 복구 시간

수 분(명령 1회, 신설 파일 열두 개 삭제뿐 — 공유 파일 hunk 충돌 없음).

## 임시 clone 실측(마지막 코드 커밋 뒤, 이 문서 커밋 직전 상태로 재확인)

- `git clone --no-hardlinks . <tmp-dir> && cd <tmp-dir> && git checkout m4-4b6a/2026-09-11`
- 위 restore 명령 실행(디렉터리 경로 버전) → `git status --porcelain` `D` 12(main 4·test
  4·evidence 4, `scope.md`는 팀장 파일이라 유지)/`M` 2(`gate-tests.properties`·
  `milestone-4.md`) — 위 「대상 파일 목록」과 일치(디렉터리 대신 evidence 넷을 개별
  나열한 버전으로 재현, `scope.md` 보존 확인).
- `git diff 786febe39469835a45e9a1c6d6e8081226777364 -- <같은 경로들>` — 0줄(base와
  완전 일치, `scope.md` 제외 경로에서).
- 되돌린 트리에서 `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin
  :workflow:test :workflow:gateExecutionGate` — `BUILD SUCCESSFUL`, 기존 evaluation
  패키지 test(4B-1·4B-2·4B-3 관련) 전부 통과, 신설 25 test 소멸(`No tests found`가 아니라
  파일 자체가 없어 대상에서 빠짐을 컴파일 성공으로 확인). 임시 clone은 실측 뒤 삭제.
