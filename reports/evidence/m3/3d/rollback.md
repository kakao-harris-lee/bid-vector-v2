# M3/3D — rollback.md

정본. **경로 한정** — range revert가 아니라 in_scope 경로만 base 상태로 되돌린다
(evidence-pack 규격). `git log 01ecbba..HEAD -- <공유 파일 3종>`을 확인한 결과 이
range 안에서 `config/quality/gate-tests.properties`·`gradle/libs.versions.toml`·
`adapters/build.gradle.kts`를 건드린 커밋은 전부 이 구현 레인 자신(3D)뿐이다 — 3B의
H-1처럼 하네스/다른 slice 커밋과 섞인 자리가 없어 전 경로를 **base 하나**로 되돌리는
단일 명령으로 충분하다.

```
BASE=01ecbba69e21e4b85ee8303416fe06a77b919fd6

git restore --source="$BASE" --staged --worktree -- \
  "adapters/src/main/resources/db/migration" \
  "adapters/src/main/kotlin/bidvector/adapters/persistence" \
  "adapters/src/test/kotlin/bidvector/adapters/persistence" \
  "adapters/build.gradle.kts" \
  "procurement/src/main/kotlin/bidvector/procurement/NoticeRepository.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/OpeningResultRepository.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/QualificationTextRepository.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/RawObservationStore.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/CollectionRunStore.kt" \
  "procurement/src/test/kotlin/bidvector/procurement/ObservationKeyTest.kt" \
  "gradle/libs.versions.toml" \
  "config/quality/gate-tests.properties"

# base에 없던 경로(신규 디렉터리·신규 파일)는 restore가 빈 상태로 되돌리지 못하고
# untracked로 남는다 — 명시적으로 제거한다.
git clean -fd -- \
  "adapters/src/main/resources/db/migration" \
  "adapters/src/main/kotlin/bidvector/adapters/persistence" \
  "adapters/src/test/kotlin/bidvector/adapters/persistence" \
  "procurement/src/main/kotlin/bidvector/procurement/NoticeRepository.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/OpeningResultRepository.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/QualificationTextRepository.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/RawObservationStore.kt" \
  "procurement/src/main/kotlin/bidvector/procurement/CollectionRunStore.kt" \
  "procurement/src/test/kotlin/bidvector/procurement/ObservationKeyTest.kt"
```

`reports/evidence/m3/3d/**`는 되돌리지 않는다(감사 기록 보존). `fixtures/**`·`docs/**`·
`build-logic/**`·`milestone-3.md`·`scope.md`는 이 구현 레인이 편집하지 않았으므로 대상이
아니다(3D는 문서 레인이 착수 커밋 `884bb91`에서 이미 고정했고, 구현 8개 커밋 중 그
파일들을 건드린 것은 0건 — `git log --oneline 884bb91..HEAD -- milestone-3.md
reports/evidence/m3/3d/scope.md`가 빈 결과임을 확인).

## 운영 DB에 대하여

이 slice는 **test 컨테이너만** 만든다(Testcontainers PostgreSQL, JVM 종료 시 Ryuk가
자동 제거) — 운영 DB에 적용된 migration이 없다. 되돌릴 운영 데이터가 없다(scope.md
rollback 절 그대로).

## 실측(임시 clone)

1. `/tmp/.../rollback-clone-3d`에 이 head(`6a1fe68`)를 clone, `main` 체크아웃.
2. 위 `git restore` + `git clean` 두 명령 실행 — `exit=0`.
3. `git status --short` — `adapters/build.gradle.kts`·`gradle/libs.versions.toml`·
   `config/quality/gate-tests.properties` 3개 `M`(base 내용으로 복원), 나머지 신규
   디렉터리·파일 전부 사라짐(작업 트리에서 완전 제거, untracked 잔존 없음).
4. 되돌린 트리에서 `./gradlew --no-build-cache clean check` — **SUCCESS**(3B 상태로
   돌아간 저장소가 여전히 초록임을 확인 — `buildLogicGateExecutionGate`·
   `adapters:moduleDependencyGate` 포함).
5. `grep -c "koneps\." config/quality/gate-tests.properties` → 3B 등재분 생존 확인
   (3D가 그 줄들을 건드리지 않았음).
6. 임시 clone 삭제.
