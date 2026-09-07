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
  "procurement/src/main/kotlin/bidvector/procurement/RawObservation.kt" \
  "procurement/src/test/kotlin/bidvector/procurement/ObservationKeyTest.kt" \
  "adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsJson.kt" \
  "adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsRawItemMapper.kt" \
  "adapters/src/test/kotlin/bidvector/adapters/koneps/KonepsOpenApiNoticeSourceTest.kt" \
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
`build-logic/**`는 이 구현 레인이 편집하지 않았으므로 대상이 아니다.

**정정(verifier r2 N-7, 재정정 — 수정 라운드 3)** — `milestone-3.md`·`scope.md`는
**구현 레인 커밋 0건**이 맞지만, `git log --oneline 01ecbba..HEAD -- milestone-3.md
reports/evidence/m3/3d/scope.md`는 실측 5건을 낸다: `884bb91`(3D 착수)·`2e10699`
(in_scope 글롭 정정)·`09d6947`(F-7 3A/3B 확장 범위 승인)·`4762d8a`(F-7 3B 확장 범위
재정정)·`9089bd4`(3C 착수 — 다른 slice, milestone-3.md만 공유). 수정 라운드 2가
정정하며 `884bb91`·`09d6947`·`4762d8a` 3건만 들고 `2e10699`를 놓쳤다 — 이번에 전건
재실측으로 바로잡는다. 다섯 전부 **문서/오케스트레이터 레인**(또는 다른 slice) 커밋이지
구현 레인 자신의 커밋이 아니다 — "하네스 레인 변경"(CLAUDE.md/.claude)과 같은 종류의
구분이다. 대상이 아니라는 결론은 유지된다. base..HEAD 전체는 29개 커밋(위 5건 + 구현
레인 24건 — feat/fix/test/build 다수 + 이 레인이 직접 쓰는 evidence docs 커밋 포함).

## 운영 DB에 대하여

이 slice는 **test 컨테이너만** 만든다(Testcontainers PostgreSQL, JVM 종료 시 Ryuk가
자동 제거) — 운영 DB에 적용된 migration이 없다. 되돌릴 운영 데이터가 없다(scope.md
rollback 절 그대로).

## 실측(임시 clone)

1. 임시 clone에 이 head(`15f3325`)를 clone, `main` 체크아웃.
2. 위 `git restore` + `git clean` 두 명령 실행 — 경로 목록 자체는 갱신할 필요가 없었다
   (수정 라운드 3이 건드린 신설 `CleanMigrationTriggerTest.kt`·`PrecedenceLabelColumnTest.kt`
   는 전부 이미 등재된 디렉터리 경로(`adapters/.../persistence`) 아래라 자동으로 덮인다) —
   `exit=0`.
3. `git status --short` — 공유·확장 7파일(`adapters/build.gradle.kts`·
   `gradle/libs.versions.toml`·`config/quality/gate-tests.properties`·
   `procurement/.../RawObservation.kt`·koneps 세 파일) `M`, persistence 신설 파일 전부(신설
   두 파일 포함) `D`, untracked 잔존 0.
4. 되돌린 트리에서 `./gradlew --no-build-cache clean check` — **SUCCESS**(347 tasks).
5. 임시 clone 삭제.
