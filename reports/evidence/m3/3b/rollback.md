# M3/3B — rollback.md

정본. **경로 한정** — range revert 가 아니라 in_scope 경로만 base 상태로 되돌린다
(evidence-pack 규격 2026-09-04 개정).

```
git restore --source=c9d75632d3acafcaf41f0454e941dc49c62063ea --staged --worktree -- \
  "adapters/src/main/kotlin/bidvector/adapters/koneps" \
  "adapters/src/test/kotlin/bidvector/adapters/koneps" \
  "adapters/build.gradle.kts" \
  "config/quality/gate-tests.properties" \
  "gradle/libs.versions.toml"
```

`reports/evidence/m3/3b/**` 는 되돌리지 않는다(감사 기록 보존). `procurement/**`·`fixtures/**`
는 이 slice 가 건드리지 않았으므로 대상이 아니다. `adapters/build.gradle.kts` 를 base 로
되돌려도 M2/2A 가 넣은 줄(`project(":workflow")`·grpc 의존)은 base 시점에 이미 있었으므로
살아남는다.

## 실측(임시 clone, 2026-09-07)

1. `/private/tmp/.../rollback-clone-3b` 로 clone, `main` 체크아웃.
2. 위 명령 실행 — `exit=0`. `git status --short` 로 확인: koneps 디렉터리 15 파일 삭제(`D`),
   `adapters/build.gradle.kts`·`config/quality/gate-tests.properties`·
   `gradle/libs.versions.toml` 3 파일이 base 상태로 수정(`M`) 표시.
3. 되돌린 트리에서 `./gradlew --no-build-cache clean check` — **SUCCESS**(347 tasks 전부
   executed) — 되돌림이 빌드를 깨지 않는다.
4. 임시 clone 삭제.

## 왜 `gradle/libs.versions.toml` 도 되돌리는가

scope.md in_scope 목록에 이 파일이 명시되지 않았으나(`adapters/build.gradle.kts` 만 명시),
`resilience4j-ratelimiter`·`resilience4j-kotlin` 카탈로그 좌표 추가가 그 파일이 참조하는
`libs.resilience4j.ratelimiter` 의 기계적 선행 조건이라 함께 편집했다 — checklist.md 「판단이
갈린 지점」에 이 판단을 등재한다. 롤백도 같은 경계로 되돌린다(부분 되돌림은 orphan 참조를
남긴다).
