# M3/3B — rollback.md

정본. **경로 한정** — range revert 가 아니라 in_scope 경로만 base 상태로 되돌린다
(evidence-pack 규격 2026-09-04 개정). **H-1(verifier r1) 뒤 개정** — `config/quality/
gate-tests.properties` 는 하네스 레인과 공유하는 파일이라 base 로 통째 되돌리면 하네스
커밋(`7ee8fed`, `TestShapesTest` 등재)까지 지운다. 그 파일 하나만 별도 명령으로, 3B 가
손대기 **직전** 상태(하네스 커밋)를 소스로 되돌린다.

```
BASE=c9d75632d3acafcaf41f0454e941dc49c62063ea
HARNESS=7ee8fed   # 3B 착수 전 마지막으로 gate-tests.properties 를 고친 하네스 커밋

git restore --source="$BASE" --staged --worktree -- \
  "adapters/src/main/kotlin/bidvector/adapters/koneps" \
  "adapters/src/test/kotlin/bidvector/adapters/koneps" \
  "adapters/build.gradle.kts" \
  "gradle/libs.versions.toml" \
  "procurement/src/main/kotlin/bidvector/procurement/Accounting.kt" \
  "procurement/src/test/kotlin/bidvector/procurement/AccountingTest.kt"

git restore --source="$HARNESS" --staged --worktree -- \
  "config/quality/gate-tests.properties"
```

`reports/evidence/m3/3b/**` 는 되돌리지 않는다(감사 기록 보존). `fixtures/**`·`docs/**`·
`build-logic/**` 는 이 slice 가 건드리지 않았으므로 대상이 아니다. `procurement/**` 는
`Accounting.kt`·`AccountingTest.kt` 두 파일만(H-3 좁은 확장, in_scope) — 다른 procurement
파일은 이 slice 가 건드리지 않았다. `adapters/build.gradle.kts` 를 base 로 되돌려도
M2/2A 가 넣은 줄(`project(":workflow")`·grpc 의존)은 base 시점에 이미 있었으므로 살아남는다.

## 왜 `config/quality/gate-tests.properties` 만 명령을 가르는가 (H-1)

`git log --oneline c9d7563..HEAD -- config/quality/gate-tests.properties` 는 이 파일에
**두 레인**이 손댔음을 보인다 — 3B 커밋(`264f649`, koneps test 3종 등재)과 하네스 커밋
(`7ee8fed`, `TestShapesTest` 등재, 3B 착수 전에 이미 병합됨). base(`c9d7563`)는 `7ee8fed`
**이전** 시점이라, 이 파일 하나를 base 로 되돌리면 3B 가 넣은 줄과 함께 `TestShapesTest`
등재도 지운다 — `testShapeGate` 실행 단언이 조용히 꺼진다(등재 한 줄이 빠져도 `clean
check` 는 여전히 초록이라 이전 판의 실측이 이 손상을 못 봤다). `7ee8fed`(3B 가 이 파일을
건드리기 직전의 마지막 상태)를 소스로 쓰면 하네스 등재는 살아남고 3B 가 넣은 3줄+주석만
없어진다 — 다른 in_scope 파일은 3B 단독 소유라 base 되돌림이 정확하다.

## 실측(임시 clone, 2026-09-07 — verifier r1 수정 라운드 재실측)

1. `/private/tmp/.../rollback-clone-3b-r1` 로 clone, `main` 체크아웃(`head 401a353`).
2. 위 두 명령 실행 — 둘 다 `exit=0`. `git status --short`: koneps 디렉터리 16 파일 삭제(`D`,
   H-3 라운드에서 늘어난 `KonepsTruncationCause.kt` 포함), `adapters/build.gradle.kts`·
   `gradle/libs.versions.toml`·`procurement/.../Accounting.kt`·`procurement/.../
   AccountingTest.kt`·`config/quality/gate-tests.properties` 5 파일이 수정(`M`) 표시.
3. `grep -c TestShapesTest config/quality/gate-tests.properties` → **2**(등재 줄 + 상단
   주석 언급) — 하네스 등재가 살아 있다. `grep -c "koneps\." config/quality/
   gate-tests.properties` → **0** — 3B 가 넣은 줄은 정확히 지워졌다.
4. 되돌린 트리에서 `./gradlew --no-build-cache clean check` — **SUCCESS**(347 tasks 전부
   executed), `buildLogicGateExecutionGate` 가 그 안에서 초록으로 돌았다 — `TestShapesTest`
   실행 단언이 되돌림 뒤에도 여전히 걸려 있다는 뜻이다(H-1 이 요구한 실측).
5. 임시 clone 삭제.

## 왜 `gradle/libs.versions.toml`·`procurement/**` 두 자리도 되돌리는가

`gradle/libs.versions.toml` — scope.md in_scope 목록에 애초 없었으나 `resilience4j-
ratelimiter`·`resilience4j-kotlin` 카탈로그 좌표 추가가 `adapters/build.gradle.kts` 의존의
기계적 선행 조건이라 함께 편집했다(세션 모델이 착수 뒤 in_scope 에 추가). `procurement/
Accounting.kt`·`AccountingTest.kt` — 운영자 결정 2026-09-07(verifier r1 H-3)로 in_scope
에 추가된 3A 좁은 확장(`TruncationCause`·`quotaExceeded`·`backoffSkipped`, 추가만 — 기존
필드·항등식·corpus 27/27 불변). 부분 되돌림은 두 자리 다 orphan 참조·미사용 확장을 남겨,
같은 경계로 되돌린다.
