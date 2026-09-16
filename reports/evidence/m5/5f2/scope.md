# M5/5F-2 — Kotlin `ML_CALL_POLICY.featureSchemaVersion` → `award-rate-features-v2` (2026-09-16)

```yaml
milestone: M5
slice: 5f2-kotlin-feature-schema-value
base_sha: 845e29b   # PR #21(5E-3) 머지 커밋 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다(4D scope.md 관례)
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/ml/MlCallPolicyData.kt        # 한 줄 — placeholderMlCallPolicy(featureSchemaVersion = "bidvector.ml.v1") → "award-rate-features-v2". KDoc 이 「패키지 식별자」라고 설명하면 그 문장도 갱신
  - adapters/src/test/kotlin/bidvector/adapters/ml/MlCallPolicyDataTest.kt   # 기대값 한 줄 갱신 + 「값이 Python SUPPORTED_FEATURE_SCHEMAS 와 같다」를 잠그는 test 1(값 리터럴은 정책 데이터 한 자리에서만 — test 는 그 상수를 참조)
  - reports/evidence/m4/4d/policy-values.md                                   # §3 행 값 + 근거 문면(「계약 패키지 식별자」→「5B feature schema version, D-5B-1」) + change_history 1행(운영자 결정 2026-09-16 ②) — 4D 문서지만 승인 값의 정본 자리
  - reports/evidence/m5/5f2/**
out_of_scope:
  - ml-engine/**                          # Python 은 무편집 — 별칭 수용(b) 은 거부됨
  - contracts/**                          # proto 무편집(값은 wire 필드의 내용이지 계약 표면이 아님) — Codex 대상 아님
  - 다른 Kotlin 파일                       # 그 값을 읽는 코드는 이미 문자열을 그대로 나른다(4D). 다른 파일에 리터럴이 더 있으면 슬라이스를 멈추고 보고(정책 데이터 두 자리 금지)
  - milestone-5.md, milestone-4.md        # 등재는 종결 판정 브랜치(m5-closure)가 담당
acceptance_commands:   # CI `check` job 그대로
  - "./gradlew --no-daemon check"
  - "./tools/contract-crosslang-smoke.sh"   # S-12 — 값 변경이 2D fake 서버 스모크를 깨지 않음(스모크가 schema 값을 고정했으면 그 자리도 in_scope 로 계약 갱신 후 갱신)
rollback: |
  `git restore --source=845e29b --staged --worktree -- <in_scope 경로 개별 인자>`(evidence 디렉터리 삭제). 되돌리면 Kotlin 은 다시 `bidvector.ml.v1` 을 보내고 Python 은 `UNSUPPORTED_SCHEMA` 를 낸다(5E-2 상태). 임시 clone ①~⑥(⑥ = `check`), 정본 `reports/evidence/m5/5f2/rollback.md`.
```

## 이 slice 가 하는 일(운영자 결정 2026-09-16 ②, `reports/evidence/m5/closure/checklist.md` §3)

Kotlin 이 `CalculateOptimalBid` 요청에 싣는 `feature_schema_version` 은 4D 가 계약 패키지 식별자 `bidvector.ml.v1` 로 승인했다(2026-09-10 — 당시 별도 schema 축이 없었다). 5B 가 `award-rate-features-v2` 를 신설했고(D-5B-1, 별칭 금지) 5E-2 servicer 는 그 집합 밖의 값을 `UNSUPPORTED_SCHEMA` 로 거부한다 — **현 배포 조합은 실 서빙 전량 실패**(`OPEN-5E2-FEATURE-SCHEMA-PARITY`). 운영자 결정 (a): Kotlin 값을 Python 이 지원하는 값으로. 값은 정책 데이터 한 자리(`MlCallPolicyData.kt` 의 `ML_CALL_POLICY`)에만 있고 4D policy-values §3 가 승인 정본이다.

## 계약 고정 결정
| ID | 결정 | 근거 |
| --- | --- | --- |
| D-5F2-1 | 값만 바꾼다 — 코드 경로·타입 무변경 | 4D 가 값을 정책 데이터로 외부화해 둔 목적이 이것 |
| D-5F2-2 | Python 정본(`ml_engine.features.schema.SUPPORTED_FEATURE_SCHEMAS`)과의 동일성은 **문서 대조**(4D policy-values §3 에 Python 정본 경로 명기)로 두고, 교차 언어 실 대조는 6C(`OPEN-5E2-CROSSLANG-REAL-SERVER`) | Kotlin test 가 Python 파일을 읽는 게이트는 만들지 않는다(job 경계 — 두 CI job 이 소스 비중첩) |
| D-5F2-3 | 4D policy-values §3 의 근거 문면을 갱신한다(「패키지 식별자」 논리는 5B 이후 낡음) — change_history 에 4D 승인 값을 대체함을 명시 | 승인 값 정본이 두 말을 하지 않게 |

## 위협 모델 — 5F-2 고유 경계
**방어한다**: 값이 두 자리에 있지 않다(리터럴 grep 이 정책 데이터 한 자리 + 그 test 뿐). **방어하지 않는다**: Python 집합이 나중에 바뀔 때의 자동 동기(6C 실 통합 test 몫).
**우회 후보**: (1) 다른 Kotlin 파일에 옛 리터럴이 남음 → `grep -rn 'bidvector.ml.v1"' --include='*.kt'` 가 0 이어야 한다(evidence 에 명령·건수) (2) 2D 스모크 fixture 가 옛 값을 고정 → S-12 로 실측.

## (2b) 값 획득 축
새 public 표면 0.

## 하네스 레인 변경
`git log --oneline 845e29b..HEAD -- CLAUDE.md .claude/` — 없음(착수 시점).

## OPEN — 수령·처분
| 식별자 | 처분 |
| --- | --- |
| `OPEN-5E2-FEATURE-SCHEMA-PARITY` | 이 slice 병합으로 **종결**((a) 채택). 실 교차 언어 대조는 `OPEN-5E2-CROSSLANG-REAL-SERVER`(6C) 그대로 |

## 계약 갱신 이력
| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 착수 | 초판 — D-5F2-1~3 | 운영자 결정 ②(선택지 답변, 종결 판정 §3) |
