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

**정정(verifier r2 N-7)** — `milestone-3.md`·`scope.md`는 **구현 레인 커밋 0건**이 맞지만
(base..HEAD 26개 커밋 중 `git log --oneline 01ecbba..HEAD -- milestone-3.md
reports/evidence/m3/3d/scope.md`는 3건을 낸다: `884bb91`·`09d6947`·`4762d8a` — 이전 판이
"0건"이라 적은 것은 틀렸다). 이 3건은 전부 **문서/오케스트레이터 레인**의 착수·운영자
결정 반영 커밋(F-7 3A/3B 확장 범위 승인 등)이지 구현 레인 자신의 커밋이 아니다 —
"하네스 레인 변경"(CLAUDE.md/.claude)과 같은 종류의 구분이다. 그래서 대상이 아니라는
결론 자체는 유지되지만, 근거를 "빈 결과"가 아니라 "구현 레인이 낸 커밋이 아님"으로
정정한다. base..HEAD 전체는 26개 커밋(문서/오케스트레이터 레인 3건 + 구현 레인 23건 —
feat/fix/test/build 다수 + 이 레인이 직접 쓰는 evidence docs 커밋 포함).

## 운영 DB에 대하여

이 slice는 **test 컨테이너만** 만든다(Testcontainers PostgreSQL, JVM 종료 시 Ryuk가
자동 제거) — 운영 DB에 적용된 migration이 없다. 되돌릴 운영 데이터가 없다(scope.md
rollback 절 그대로).

## 실측(임시 clone, verifier r2 수정 라운드 2 — N-1·N-2·N-4·N-5 — 뒤 재실측 — 네 번째)

1. 임시 clone에 이 head(`6332158`)를 clone, `main` 체크아웃.
2. 위 `git restore` + `git clean` 두 명령 실행 — 경로 목록 자체는 갱신할 필요가 없었다
   (N-1·N-2·N-4·N-5가 건드린 `Sql.kt`·`NoticeReconstruction.kt`·신설
   `PrecedenceParityTest.kt`·`NoticeReconstructionTest.kt`는 전부 이미 등재된 디렉터리
   경로(`adapters/.../persistence`) 아래라 자동으로 덮인다) — `exit=0`.
3. `git status --short` — 공유·확장 7파일(`adapters/build.gradle.kts`·
   `gradle/libs.versions.toml`·`config/quality/gate-tests.properties`·
   `procurement/.../RawObservation.kt`·koneps 세 파일) `M`, persistence 신설 파일 전부
   (`PrecedenceParityTest.kt`·`NoticeReconstructionTest.kt` 포함) `D`, untracked 잔존 0.
4. 되돌린 트리에서 `./gradlew --no-build-cache clean check` — **SUCCESS**(347 tasks).
5. 임시 clone 삭제.

### 이전 실측 — 결과 동일, 기록 보존

- (세 번째, F-7 3B 몫 이어붙임 뒤, head `782c4ae`) koneps 세 경로 추가 후 SUCCESS(347 tasks).
- (두 번째, F-7 3A·3D 몫 이어붙임 뒤, head `eb82bb8`) `RawObservation.kt` 경로 추가 후
  SUCCESS(347 tasks).
- (첫 번째, verifier r1 수정 라운드 1 F-1~F-8 뒤, head `a79a6d6`) SUCCESS(348 tasks),
  `grep -c "koneps\." config/quality/gate-tests.properties`로 3B 등재분 생존 확인.
