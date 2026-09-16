## RED — 값 갱신 전 test 실행 (2026-09-16)
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.MlCallPolicyDataTest"`
- exit: 1
- 핵심 결과: 9 tests, 2 failed(기존 anchor test + 신설 parity test, 둘 다 `expected:<award-rate-features-v2> but was:<bidvector.ml.v1>`)

## GREEN — `MlCallPolicyData.kt` 값 갱신 후 재실행 (2026-09-16)
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.MlCallPolicyDataTest"`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 9 tests 전부 통과

## 우회 (1) — 옛 리터럴 잔존 확인 (2026-09-16)
- cmd: `grep -rn 'bidvector.ml.v1"' --include='*.kt' . | grep -v /build/`
- exit: 1
- 핵심 결과: 매치 0건 — 정책 데이터 두 자리 없음

## S-12 — 교차 언어 socket 스모크 (2026-09-16)
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`(스모크는 `featureSchemaVersion` 을 참조하지 않음 — `grep -n featureSchemaVersion adapters/src/test/kotlin/bidvector/adapters/contract/CrossLangSmokeTest.kt ml-engine/tests/crosslang_smoke_server.py` 매치 0건, 값 변경 무영향 확인)

## acceptance — CI `check` job 전건 (2026-09-16)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 346 actionable tasks

## 비밀값 스캔 (2026-09-16)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt reports/evidence/m4/4d/policy-values.md reports/evidence/m5/5f2/`
- exit: 1
- 핵심 결과: 매치 없음(통과)

## 역방향 파급 확인 (2026-09-16)
- cmd: `grep -rn "MlCallPolicyData\|policy-values" --include='*.md' --include='*.kt' --include='*.properties' . | grep -v /build/`
- exit: 0
- 핵심 결과: 편집 파일을 가리키는 인용은 전부 §절 번호·클래스명 참조뿐(`file:line` 없음) — 이번 편집(§3 근거 문면·change_history 텍스트만 변경, 절 구조 무변경)으로 밀리는 좌표 없음

## rollback 임시 clone 실측 (2026-09-16, scratchpad `5f2-rollback-check`)
- cmd: `git restore --source=845e29b --staged --worktree -- <in_scope 3 파일>`
- exit: 0
- cmd: `git diff 845e29b -- <in_scope 3 파일> | wc -l`
- exit: 0
- 핵심 결과: `0`(비어 있음)
- cmd: `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin`
- exit: 0
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.MlCallPolicyDataTest"`
- exit: 0
- 핵심 결과: `@Test` 8개(parity test 소실 = base 상태), `featureSchemaVersion shouldBe "bidvector.ml.v1"` 로 복귀
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 346 tasks — 되돌린 트리도 게이트 전건 통과
