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

## evidence 커밋(`c170035`) HEAD 재실측 — 자기참조 방지 (2026-09-16)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(`leakPatternGate` 포함) — evidence 문서가 스캔 어휘를 축어로
  담지 않고 파일 참조·간접 표현만 쓴다는 것을 이 커밋 HEAD 에서 직접 확인

## 승인 전 일괄(2) — D-5F2-4 신설 test, 값 갱신 전 실행 (2026-09-16)
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.RequestMappingTest"`
- exit: 0
- 핵심 결과: 6 tests(신설 test 포함) 전부 통과 — 값은 이미 GREEN 상태이므로 이 test 는
  최초부터 통과, 방어력은 아래 변이 B 로 확인

## 승인 전 일괄(2) — D-5F2-4 변이 B 재현 (2026-09-16, 이 worktree 실측 편집→원복)
- cmd: `PredictionEnvelopeMapping.kt` 의 `.setFeatureSchemaVersion(featureSchemaVersion)` →
  `.setFeatureSchemaVersion(featureSchemaVersion.reversed())` 로 편집 후
  `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.RequestMappingTest"`
- exit: 1
- 핵심 결과: `6 tests completed, 1 failed` — 신설 test 단독 실패
  (`expected:<award-rate-features-v2> but was:<2v-serutaef-etar-drawa>`), 나머지 5개는 그대로 초록
- cmd: 원복 후 `git diff -- adapters/src/main/kotlin/bidvector/adapters/ml/PredictionEnvelopeMapping.kt | wc -l`
- exit: 0
- 핵심 결과: `0`(diff 없음 — 완전 원복 확인)

## 승인 전 일괄(2) — 중복 test 삭제·source 라벨 갱신 뒤 재검증 (2026-09-16)
- cmd: `./gradlew --no-daemon :adapters:test --tests "bidvector.adapters.ml.*"`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — `MlCallPolicyDataTest`(중복 제거 뒤 8개)·`RequestMappingTest`
  (6개)·`EmbeddingCallPolicyTest`·`GrpcBidPredictionGatewayTest` 등 `ml` 패키지 전체 통과

## 승인 전 일괄(2) — 커밋 전 비밀값 스캔 (2026-09-16)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt adapters/src/test/kotlin/bidvector/adapters/ml/RequestMappingTest.kt reports/evidence/m5/5f2/`
- exit: 1
- 핵심 결과: 매치 없음(통과)

## 승인 전 일괄(2) — 커밋(`dc34ae7`) — rollback ⑥ 만 재실행 (2026-09-16, scratchpad `5f2-rollback-check3`)
근거는 `rollback.md`「재확인 범위 축소 근거」 — 되돌린 트리가 verifier r1 이 이미 실측한
트리와 파일 SHA 동일(`git restore --source=845e29b`가 항상 같은 base 좌표로 수렴).
- cmd: `git restore --source=845e29b --staged --worktree -- <in_scope 4 파일>`
- exit: 0
- cmd: `rm -rf reports/evidence/m5/5f2`
- exit: 0
- cmd: `git diff 845e29b -- <in_scope 4 파일> | wc -l`
- exit: 0
- 핵심 결과: `0`
- cmd: `./gradlew --no-daemon check` (되돌린 clone)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 346 tasks — r1 이 실측한 346 tasks 와 동일

## 승인 전 일괄(2) — 커밋(`dc34ae7`) HEAD 최종 acceptance 재확인 (2026-09-16)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- cmd: `./tools/contract-crosslang-smoke.sh`
- exit: 0
- 핵심 결과: `== 교차 언어 socket 스모크 통과 ==`. `git status --porcelain` 결과 없음(clean)

## evidence 커밋(`a8b0a4a`) HEAD 재실측 — 자기참조 방지 (2026-09-16)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(`leakPatternGate` 포함) — 이번 라운드가 더한 evidence 문서도
  스캔 어휘를 축어로 담지 않는다는 것을 이 커밋 HEAD 에서 직접 확인

