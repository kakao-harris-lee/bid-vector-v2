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

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 3 파일 +
      reports/evidence/m5/5f2/>` 결과 없음.
- [x] scope.md의 acceptance_commands 전부 exit 0으로 commands.md에 기록됨 — `./gradlew
      --no-daemon check`·`./tools/contract-crosslang-smoke.sh` 둘 다 exit 0.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — CI `check` job 전건(GREEN, commands.md).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — fixture 신설 없음(golden-manifest.json
      N/A). 정책 값 근거는 `reports/evidence/m4/4d/policy-values.md` §3·change_history.
- [x] 알려진 제한과 rollback 방법이 기록됨 — 아래 「알려진 제한」·`rollback.md`.
- [x] 비밀값 스캔 통과 — commands.md 「비밀값 스캔」 절, 매치 0.

## RED → GREEN

`MlCallPolicyDataTest`의 anchor test(전체 값 회귀 방지, verifier r1 F-4 관례)와 신설
parity test(Python `SUPPORTED_FEATURE_SCHEMAS` 대조, D-5F2-2)의 기대값을 먼저
`award-rate-features-v2` 로 갱신해 RED 를 만들고(commands.md), 그 뒤 `MlCallPolicyData.kt`
의 값 한 줄을 바꿔 GREEN 으로 옮겼다. 값 리터럴은 `ML_CALL_POLICY` 한 자리에만 있고, 두
test 모두 `ML_CALL_POLICY.resolve(...)`를 통해 그 인스턴스를 대조한다(정책 데이터 두 자리
금지).

## 우회 후보 ↔ 실측 대응표

| # | 우회 | 방어 | 실측 근거 |
| --- | --- | --- | --- |
| 1 | 다른 Kotlin 파일에 옛 리터럴(`bidvector.ml.v1`)이 남음 | 값을 `ML_CALL_POLICY` 한 자리에서만 참조(`placeholderMlCallPolicy` 공용 팩토리) | commands.md 「우회 (1)」 — `grep -rn 'bidvector.ml.v1"' --include='*.kt' .` 매치 0건 |
| 2 | 2D fake 서버 스모크(S-12)가 옛 값을 고정해 이 변경이 스모크를 깨뜨림 | S-12 는 `featureSchemaVersion` 을 참조하지 않는다(스모크가 이 값에 의존하지 않는 설계) | commands.md 「S-12」 — `grep -n featureSchemaVersion adapters/.../CrossLangSmokeTest.kt ml-engine/tests/crosslang_smoke_server.py` 매치 0건, `./tools/contract-crosslang-smoke.sh` exit 0 |

두 후보 모두 「멈추고 보고」 조건(scope.md 위협 모델)에 해당하지 않았다 — slice 를 확장하지
않고 완료했다.

## (2b) 값 획득 축

새 public 표면 0(scope.md 문면 그대로 실측 확인 — `MlCallPolicyData`·`placeholderMlCallPolicy`·
`ML_CALL_POLICY`·`resolveMlCallPolicy`·`ResolvedMlCallPolicy` 시그니처 무변경, 값 리터럴
하나만 교체).

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

## OPEN 처분

`OPEN-5E2-FEATURE-SCHEMA-PARITY` — 이 slice 병합으로 종결(운영자 결정 ② (a) 채택,
`reports/evidence/m4/4d/policy-values.md` change_history 2026-09-16 항목).
