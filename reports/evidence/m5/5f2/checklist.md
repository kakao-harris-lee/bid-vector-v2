# checklist.md — M5/5F-2 리뷰 준비도

## 이 slice가 한 일

`ML_CALL_POLICY.featureSchemaVersion`(정책 데이터 한 자리,
`adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt`)의 값을 4D 승인 값
`bidvector.ml.v1`(계약 패키지 식별자)에서 5B 가 신설한 `award-rate-features-v2`(D-5B-1)로
바꿨다. 사유는 Python 5E-2 servicer 가 옛 값을 `UNSUPPORTED_SCHEMA` 로 거부해 실 서빙이
전량 실패하는 상태였기 때문이다(`OPEN-5E2-FEATURE-SCHEMA-PARITY`, 운영자 결정 2026-09-16
②). 코드 경로·타입은 무변경(D-5F2-1) — 값 한 줄과 그 값을 승인하는 문서(4D
policy-values.md §3)만 바뀌었다.

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 4 파일 +
      reports/evidence/m5/5f2/>` 결과 없음(계약 갱신 (2)로 `RequestMappingTest.kt` 추가).
- [x] scope.md의 acceptance_commands 전부 exit 0으로 commands.md에 기록됨 — `./gradlew
      --no-daemon check`·`./tools/contract-crosslang-smoke.sh` 둘 다 exit 0.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — CI `check` job 전건(GREEN, commands.md).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — fixture 신설 없음(golden-manifest.json
      N/A). 정책 값 근거는 `reports/evidence/m4/4d/policy-values.md` §3·change_history.
- [x] 알려진 제한과 rollback 방법이 기록됨 — 아래 「알려진 제한」·`rollback.md`.
- [x] 비밀값 스캔 통과 — commands.md 「비밀값 스캔」 절, 매치 0.

## RED → GREEN

`MlCallPolicyDataTest`의 anchor test(전체 값 회귀 방지, verifier r1 F-4 관례) 기대값을 먼저
`award-rate-features-v2` 로 갱신해 RED 를 만들고(commands.md), 그 뒤 `MlCallPolicyData.kt`
의 값 한 줄을 바꿔 GREEN 으로 옮겼다. 값 리터럴은 `ML_CALL_POLICY` 한 자리에만 있고, test
는 `ML_CALL_POLICY.resolve(...)`를 통해 그 인스턴스를 대조한다(정책 데이터 두 자리 금지).

**승인 전 일괄 (2) — 중복 parity test 삭제.** 착수 라운드가 더했던 신설 parity test
(`featureSchemaVersion 은 5B가 신설한 award-rate-features-v2 와 같다...`)는 anchor test 와
같은 인스턴스(`ML_CALL_POLICY.resolve(...)`)의 같은 필드를 같은 리터럴로 재확인할 뿐이라
판별력이 0 이었다(verifier r1 L-2, code-reviewer MEDIUM — 두 test 를 값을 되돌리는 실측으로
동시에 붉혀 확인). anchor test 하나만 남기고 삭제했다.

**승인 전 일괄 (2) — D-5F2-4 요청 proto 실측 test(RED 아님, 신규 방어).** verifier r1 이
변이 B(`PredictionEnvelopeMapping.setFeatureSchemaVersion`을 `.reversed()`로 바꿔도 `check`
전건이 초록)로 「정책 값이 요청 proto 에 실리는 경로에 test 가 없다」(L-1)는 것을 실측해
`RequestMappingTest.kt`에 test 하나를 더했다(in_scope 로 계약 갱신). 이 test 는 `mapRequest`
를 실 정책(`ML_CALL_POLICY.resolve(...)`, `testMlCallPolicy()` 아님)으로 호출해
`CalculateOptimalBidRequest.envelope.featureSchemaVersion`이 그 정책 값과 같은지 잰다.
**변이 B 를 이 worktree 에서 재현**했다 — `PredictionEnvelopeMapping.kt`를 실제로
`.reversed()`로 고쳐 이 test 단독으로 RED(다른 5개 test 는 그대로 초록)가 됨을 확인한 뒤
원복해 `git diff` 0 을 확인했다(commands.md 「D-5F2-4 변이 B」).

## 우회 후보 ↔ 실측 대응표

| # | 우회 | 방어 | 실측 근거 |
| --- | --- | --- | --- |
| 1 | 다른 Kotlin 파일에 옛 리터럴(`bidvector.ml.v1`)이 남음 | 값을 `ML_CALL_POLICY` 한 자리에서만 참조(`placeholderMlCallPolicy` 공용 팩토리) | commands.md 「우회 (1)」 — `grep -rn 'bidvector.ml.v1"' --include='*.kt' .` 매치 0건 |
| 2 | 2D fake 서버 스모크(S-12)가 옛 값을 고정해 이 변경이 스모크를 깨뜨림 | S-12 는 `featureSchemaVersion` 을 참조하지 않는다(스모크가 이 값에 의존하지 않는 설계) | commands.md 「S-12」 — `grep -n featureSchemaVersion adapters/.../CrossLangSmokeTest.kt ml-engine/tests/crosslang_smoke_server.py` 매치 0건, `./tools/contract-crosslang-smoke.sh` exit 0 |
| 3(verifier r1 변이 B) | `PredictionEnvelopeMapping`이 요청 조립 중 정책 값을 조용히 다른 문자열로 바꿔도 아무 test 도 안 잡음 | `RequestMappingTest.kt` 신설 test(D-5F2-4) — 실 정책으로 만든 요청의 `envelope.featureSchemaVersion`을 정책 값과 직접 대조 | commands.md 「D-5F2-4 변이 B」 — `.setFeatureSchemaVersion(featureSchemaVersion.reversed())` 로 바꾼 이 worktree 에서 신설 test 단독 RED, 원복 후 `git diff` 0 |

세 후보 모두 「멈추고 보고」 조건(scope.md 위협 모델)에 해당하지 않았다 — slice 를 확장하지
않고 완료했다.

## (2b) 값 획득 축 — 승인 전 일괄 뒤 재확인

새 public 표면 0. `RequestMappingTest.kt`에 더한 test 하나는 `internal fun mapRequest`
(기존 시그니처 무변경)를 호출하는 test 코드일 뿐 production 표면이 아니고,
`MlCallPolicyData.kt`의 `source` 라벨 문면 변경도 타입·시그니처가 아니다. `MlCallPolicyData`·
`placeholderMlCallPolicy`·`ML_CALL_POLICY`·`resolveMlCallPolicy`·`ResolvedMlCallPolicy`
전부 시그니처 무변경 — 승인 전 일괄이 새 표면을 만들지 않았다.

## 새 파일 ↔ in_scope 대조

승인 전 일괄이 편집한 파일 전부가 scope.md in_scope 목록에 있다 — 새 파일은 없다.
`RequestMappingTest.kt`는 계약 갱신 (2)에서 in_scope 로 이미 등재됐다(D-5F2-4 근거 포함).

## 판단이 필요했던 자리

- **신설 parity test 의 형태** — scope.md 는 "리터럴은 정책 데이터 한 자리에만, test 는 그
  상수를 참조"라고만 지시했다. Python 파일을 읽는 게이트는 만들지 않는다는 D-5F2-2 제약 아래,
  `ML_CALL_POLICY.resolve(...)`로 실제 운영 인스턴스를 가져와 대조하는 형태로 구현했다 —
  Python 정본과의 실 대조가 아니라 "이 값이 무엇이라고 우리가 문서로 주장하는가"를 코드로
  고정하는 test 다(실 교차 언어 대조는 6C 몫, D-5F2-2).
- **policy-values.md §3 근거 문면 갱신 범위** — "계약 패키지 식별자" 논리를 완전히 들어내는
  대신, 그 논리가 왜 낡았는지(5B 축 신설)와 새 근거(5B D-5B-1, Python 정본)를 남겨 이력이
  끊기지 않게 했다.

## 알려진 제한

- **`OPEN-5E2-CROSSLANG-REAL-SERVER`(6C) 는 이 slice 로 닫히지 않는다.** 이 slice 가 고정한
  것은 "Kotlin 값 = 문서가 주장하는 Python 지원 값"이라는 문서 대조뿐이다(D-5F2-2). Python
  `SUPPORTED_FEATURE_SCHEMAS` 집합이 나중에 바뀌면 이 test 는 그것을 자동으로 못 잡는다 —
  6C 의 실 교차 언어 통합 test 가 그 몫이다.
- `EmbeddingCallPolicy.kt` 의 `featureSchemaVersion = "text-synthesis-v1"` 은 이 slice 의 대상이
  아니다(별개 값, scope.md out_of_scope "다른 Kotlin 파일"은 `bidvector.ml.v1` 리터럴만
  가리킨다 — 실측 결과 그 리터럴은 `MlCallPolicyData.kt` 한 곳뿐이었다).
- ~~**요청 proto 경로 무보호**~~ **D-5F2-4 로 닫힘(승인 전 일괄).** verifier r1 변이 B가
  실측한 공백(정책 값이 실제 `CalculateOptimalBidRequest.envelope.feature_schema_version`
  에 실리는 경로에 test 부재)은 `RequestMappingTest.kt` 신설 test 로 방어된다 — 이 worktree
  에서 같은 변이를 재현해 그 test 단독으로 RED 가 됨을 확인했다(위 「RED → GREEN」).
- **세 자리 선언 — 이 slice 의 범위 밖 부채로 등재.** 저장소는 이 값을 세 곳에서 각자
  선언한다: Kotlin 운영 정책(`ML_CALL_POLICY`, 이 slice 가 갱신), Python 지원 집합
  (`ml_engine.features.schema.SUPPORTED_FEATURE_SCHEMAS`), 그리고 2F 계약 testdata
  (`contracts/testdata/prediction/*.binpb` 등이 선언하는 `award-rate-v1`, Python servicer 가
  test 에서 그 값을 `award-rate-features-v2` 로 덮어써 우회). 5E-2 계약이 이미 testdata 값을
  "예시값이지 규약이 아니다"로 분류해 뒀고, 이 어긋남은 이 slice 이전(Kotlin 이
  `bidvector.ml.v1`이던 시절)부터 있었다 — 이 slice 가 만든 결함이 아니라 verifier r1 참고
  ②가 실측한 기존 부채다. 세 선언을 한 자리에 모으는 문서는 아직 없다(닫는 자리는 6C 또는
  2F 후속).

## OPEN 처분

`OPEN-5E2-FEATURE-SCHEMA-PARITY` — 이 slice 병합으로 종결(운영자 결정 ② (a) 채택,
`reports/evidence/m4/4d/policy-values.md` change_history 2026-09-16 항목).

## 승인 전 일괄(2) — finding 처분

| finding | 심각도 | 처분 |
| --- | --- | --- |
| M-1(verifier) | MEDIUM | `rollback.md` 「목록 — 기계 산출」에 `checklist.md` 추가, `git diff --name-status` 재실측값으로 갱신 |
| M-2(verifier) | MEDIUM | `RequestMappingTest.kt` 에 D-5F2-4 test 신설 — 변이 B 재현으로 방어 확인, 알려진 제한에서 「닫힘」으로 갱신 |
| L-1(verifier) | LOW | M-2 와 같은 test 로 해소(요청 proto 경로 test 신설) |
| L-2(verifier)·MEDIUM(reviewer) | LOW/MEDIUM | 중복 parity test 삭제, anchor test 만 유지 |
| L-3(verifier) | LOW | `ML_CALL_POLICY.source` 라벨에 「featureSchemaVersion 운영자 결정 2026-09-16 ②」추가 |
| L-4(verifier) | LOW | scope.md 계약 갱신 이력에 이번 일괄 행 추가는 팀장 커밋 `c51d455`가 이미 반영(계약 갱신 (2) 절) |
| L-5(verifier) | LOW | 크기 게이트 초과 보고 — 「멈추고 보고」로 이미 완료된 항목(구조적 최소 다섯 파일), 추가 조치 없음 |
