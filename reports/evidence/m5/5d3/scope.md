# Slice 계약 — M5 / 5D-3 · 3계층 소비 라운드(2F wire 표본 축 → 분포 엔진 발주기관/공종/전역 수축)

> **지위**: 운영자 결정 2026-09-16(2F 다음 (a) — 「5D-3 소비 라운드 → 5E」). 2F(PR #16, 승인 태그
> `contracts/v1-approved-2026-09-16`) 가 wire 에 올린 표본 축(`CompetitionSample.agency_id = 8`·
> `category_code = 9`, `AgencyIdFact`/`CategoryCodeFact`)을 5D-2 가 미리 둔 `SegmentedSample.segment`
> 슬롯으로 채워 **발주기관/공종/전역 3계층 수축을 서빙 경로에서** 만든다 — ML-04 ② 「기관 표본이 임계 미만이면
> 수축 가중치가 응답 근거에」 를 wire 구동 경로(`serve_bid_rates`)로 충족한다. 5D-2 알려진 제한 1(`OPEN-5D2-
> SAMPLE-SEGMENT`)·8(슬롯 손으로 채워도 미동작) 해소. 세션 모델 단독 작성. Phase 2.5 설계 검토는
> `_workspace/m5-5d3/02_design-review.md`(세션 모델).
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m5-5d3/2026-09-16`, base = PR #16 병합 커밋. 구현 `ml-implementer`
> (sonnet) → `verifier`(opus). `ml-engine/**` 와 문서만.

```yaml
milestone: m5
slice: 5d3-segmented-shrinkage
base_sha: d09666348c6489c1a8fd32144b91b82937620ef4
in_scope:
  - ml-engine/src/ml_engine/features/facts.py                 # ① 텍스트 fact 판독기 공개 승격(D-5D3-6) — 정규화·EMPTY_KEY·UNSPECIFIED 거부는 그대로, 허용 결측 사유 집합을 인자로
  - ml-engine/src/ml_engine/inference/distribution.py         # ② SampleSegment 를 FactValue[str] 둘로(D-5D3-6) · from_proto 가 표본 축 둘을 채움(D-5D3-2) · 집계: 요청 축과 정규화 문자열 동일 매칭으로 agency/category LevelObservation 구성(D-5D3-1·3) · _resolve_diagnostics 실분기 · agency_sample_count = 매칭 CLEAN 표본 수 · shrinkage_weight = level_weights.agency 실값
  - ml-engine/src/ml_engine/inference/__init__.py             # `SegmentMissing` 재수출 제거(D-5D3-6) — 외부 사용처는 test 뿐(착수 전 grep 실측)
  - ml-engine/src/ml_engine/inference/observations.py         # ③ SampleRejectionReason 값 하나(SEGMENT_REASON_NOT_ALLOWED) — 표본 축 결측 사유가 NOT_COLLECTED_YET 이 아니면 표본 거부(D-5D3-2)
  - ml-engine/tests/inference/test_distribution.py            # 규칙표 test(D-5D3-1~5) · 회귀(표본 축 전부 missing → 5D-2 결과와 동일)
  - ml-engine/tests/inference/test_observations.py            # 사유 수 8→9 · 표본 축 사유 거부
  - ml-engine/tests/inference/test_engine.py                  # ML-04 ② 서빙 경로: golden 011 의 계층 표본 수(5/10/500)와 정책을 wire 요청으로 조립해 serve_bid_rates 경유로 011 expected.diagnostics(DIRECT·5·below true·0.25)와 대조(D-5D3-7)
  - ml-engine/tests/features/test_facts.py                    # 판독기 공개 승격 회귀
  - milestone-5.md                                            # 5D-3 착수·종결 문단(팀장)
  - reports/evidence/m5/5d3/**                                # scope·commands·checklist·rollback·policy-values·reuse
out_of_scope:
  - wire 매핑 함수(5E) · Python servicer · 정책 값(`assessment.agency_sample_threshold` 출하 값은 여전히 `OPEN-5D2-POLICY-VALUES` — test 는 case 정책 주입)
  - Kotlin 송신 어댑터가 표본 축을 실제 값으로 채우는 것(2F `RequestMapping.toProto()` 는 도메인 값이 있으면 실음; 값 공급은 M4 4D-1 후속 `OPEN-2F-DIAGNOSTICS-DOMAIN` 계열)
  - 표본 중복 제거(D-2B-3 식별자 없음) · 어휘(OOV) 검증(5B `Vocabulary` 는 GBM 축 — 분포 엔진은 문자열 동일 매칭만) · 별칭·계층 사전
  - golden corpus 편집(`fixtures/**` 무변경 — 011 은 커널 test 로 유지, 서빙 경로 검증은 test_engine.py 가 011 의 수치를 읽어 조립)
  - K5·K6 산식 · `results.py`(Diagnostics 필드는 5D-2 가 이미 둠 — 변경 없음 목표)
acceptance: 5D-2 와 동일 — CI `ml-engine` job 전건(S-1 uv sync --frozen · S-2 ruff · S-3 mypy strict · S-4 import-linter · S-5 pytest 전건, golden 14/14 · S-6 design ratchet 50/500/dict[str,Any] 0 · S-7 reuse provenance 양방향 · S-8 leak 0 · S-9 contract stub roundtrip) + Kotlin `check` job 은 소스 비중첩(ml-engine/** 만) 이라 축 밖 — 단 evidence 커밋마다 루트 `leakPatternGate` 가 `reports/evidence/` 를 스캔하므로 evidence 편집 커밋의 HEAD 에서 Kotlin `check` 재실측(2026-09-16 규칙).
rollback: in_scope 경로 한정. distribution.py·observations.py·facts.py 는 5D-2/5B 소유 파일 → 이 slice 자기 이력만 착수 경계 기준 단일 역적용(`git diff <base> -- <파일> | git apply -R`, 2026-09-16 규칙), tests 신설 삭제·기존 test 파일은 같은 역적용, evidence 는 남김. 임시 clone(`git clone --no-hardlinks`) 에서 실측 ①~⑥(⑥ = 되돌린 트리에서 ml-engine job 전건 + 루트 leakPatternGate). 되돌리면 5D-2 종결 상태(global-only) 로 복귀 — 5D-2 알려진 제한 1·8 이 다시 유효.
```

## 결정(계약 고정 — 운영자 즉답 불필요, 전부 5D-2·2F·5B 결정의 귀결)

| ID | 판단 | 근거 |
| --- | --- | --- |
| D-5D3-1 | 계층 매칭은 **정규화 문자열 동일**(5B `normalize_feature_key`, 요청 축·표본 축 **같은 함수**)만. 별칭·계층 사전·부분 일치 없음 | 5B D-5B-9 · legacy `_resolve_posterior` 의 `agency_key ==` 동일 비교 · 조사 §5 |
| D-5D3-2 | 표본 축 결측 사유는 `NOT_COLLECTED_YET` 만 수용. `UNSPECIFIED`(5B 와 같이 MALFORMED)·그 외 사유·oneof 미설정·정규화 뒤 빈 키 → **표본 거부**(`SampleRejected(SEGMENT_REASON_NOT_ALLOWED)` / 빈 키·미설정은 5B 판독기의 `FactRejected` 를 같은 사유로 접음) — 요청 전체가 아니라 그 표본만 빠지고 `excluded_observations` 로 계수 | 2F D-2F-1 주석 규약을 수신 측이 집행 · 5D-2 「조용한 drop 금지」 · 2A ⑥ fail-closed |
| D-5D3-3 | 요청 `FeatureInputs.agency_id`/`category_code` 가 `Missing` 이면 그 계층은 매칭 불가 → `None`(전역만, 또는 공종만) — 요청 축 결측을 값으로 가정하지 않는다. 표본 축이 `Present` 여도 요청 축이 `Missing` 이면 그 계층은 비어 있다 | 5D-2 계약 갱신 이력 「요청의 `agency_id` 로 표본을 같은 기관이라 가정하지 않는다」 의 대칭 |
| D-5D3-4 | 계층 집합: agency = CLEAN 표본 중 agency 축 일치, category = CLEAN 표본 중 category 축 일치(agency 일치 표본을 **포함**), global = CLEAN 전체. `agency_sample_count` = agency 집합 크기, `agency_sample_below_threshold` = 그 수 < `assessment.agency_sample_threshold`(임계 미선언은 로더가 거부 — 5D-2 D-5D2-3), `shrinkage_weight` = K5 `level_weights.agency` 실값, `segment_support` = 5D-2 `_resolve_diagnostics` 규칙 그대로(DIRECT/PARENT_CATEGORY/GLOBAL) | golden 011 계층(5 ⊂ 10 ⊂ 500) · legacy `_resolve_posterior` 포함 관계 · ML-04 ② |
| D-5D3-5 | 표본 축이 전부 `Missing`(NOT_COLLECTED_YET) 인 요청은 5D-2 결과와 **비트 동일**(후보 3·fitness·uncertainty·diagnostics 전부) — 회귀 test 로 고정. 가용성 게이트(`distribution_availability`)는 global 기준 그대로(계층 표본 수는 가용성에 관여하지 않음) | 후방 호환 · D-5D2-7 |
| D-5D3-6 | 타입: `SampleSegment(agency: FactValue[str], category: FactValue[str])`(5B `Present[str] \| Missing` 재사용), **`SegmentMissing` 마커 삭제**(둘 다 `Missing` 인 상태가 그 자리를 대신한다 — 불가능한 상태 하나 제거). 판독은 5B `facts` 의 텍스트 fact 판독기를 **공개 승격**(허용 결측 사유 집합 인자화)해 요청 축(`FeatureFacts.from_proto`, 기존 집합 유지)과 표본 축(`{NOT_COLLECTED_YET}`)이 같은 코드로 정규화·거부한다. 분포 엔진 안에 두 번째 판독기를 두지 않는다 | 「불가능한 상태는 타입으로 닫는다」 · 재사용 우선 · import-linter 층(`inference > features`) 허용 방향 |
| D-5D3-7 | ML-04 ② 서빙 경로 검증은 **golden corpus 를 편집하지 않고** `test_engine.py` 가 `ml-kernel-011` 입력의 정책·계층 표본 수를 읽어 wire 요청(합성 표본 — 표본 축만 011 의 수와 같게, 값은 `_sample_support` 재사용)을 조립하고 `serve_bid_rates` 결과의 `diagnostics` 를 011 expected `diagnostics`(segmentSupport DIRECT · agencySampleCount 5 · below true · shrinkageWeight 0.25) 와 대조한다. 커널 test(`test_kernel_golden.py` 011)는 유지하되 「서빙 경로 도달 불가」 문면을 제거한다 | 골든 corpus 승인 범위(2026-09-12) 유지 · 수축 가중치는 계층 표본 **수**만의 함수(011 derivation `w = n/(n+k)`) 라 값 일치 없이 재현 가능 |

## 이 slice 가 하는 일(요지)
① `facts.py` 판독기 공개 승격 · ② `distribution.py`: `from_proto` 가 표본마다 축 둘을 판독(거부는 그 표본을 `SampleRejected` 로 — `_observe_all` 단계에서 계수) → CLEAN 표본에 segment 를 붙여 3집합 구성 → `resolve_assessment_posterior(agency=…, category=…, global_level=…)` 실인자 → `_resolve_diagnostics` 실인자 · ③ `observations.py` 사유 하나 · ④ test 넷 · ⑤ 문서(milestone-5 5D-3 절 · evidence · 5D-2 checklist 는 편집하지 않고 5D-3 checklist 에 「해소」 등재).

## 위협·우회(설계 검토 정본은 `_workspace/m5-5d3/02_design-review.md`)
(1) 표본 축을 요청 축과 다르게 정규화해 매칭 누락/과다 → 같은 함수(D-5D3-6) · (2) 사유 지어낸 표본(`NOT_APPLICABLE` 등) → 거부(D-5D3-2) · (3) 요청 agency 결측인데 표본 agency 로 계층 구성 → D-5D3-3 · (4) 임계 미선언 → 로더 거부(5D-2) · (5) segment 채워도 global-only 와 같은 결과(5D-2 알려진 제한 8) → D-5D3-7 test 가 `DIRECT`·`0.25` 를 잡음 · (6) 비-CLEAN 표본이 계층 집합에 편입 → 집합은 CLEAN 이후에만 구성(D-5D3-4) · (7) agency 일치인데 category 불일치 표본 → agency 집합에는 들고 category 집합에는 안 든다(포함 관계는 요청 축 기준 각각 독립 매칭 — 011 의 5 ⊂ 10 은 데이터의 성질이지 코드가 강제하지 않음, test 로 문면 고정).

## (2b) 값 획득 축
| 표면 | 처분 |
| --- | --- |
| `SampleSegment` 필드 타입 변경(`str` → `FactValue[str]`), `SegmentMissing` **삭제**(`inference/__init__.py` 재수출 포함) | 공개 표면 축소 — 착수 전 grep: src 사용처는 `distribution.py`·`inference/__init__.py` 재수출뿐, 나머지는 `test_distribution.py` |
| `facts.py` 공개 판독기 | 새 public 함수 하나 — 허락하는 것: 임의 `*Fact` 를 정규화된 `Present`/`Missing`/`FactRejected` 로. 값을 지어내지 않음(입력 그대로 정규화) |
| `SampleRejectionReason` 값 +1 | 열거 확장, 계수 축만 |
| `DistributionRequest.from_proto` | 유일 생성 경로 유지, 이제 표본 축을 실제로 채움 — `agency`/`category` 요청 축은 이미 소비 대상 |
| 「경계로 처리」 행 | 없음 — 매칭·집계는 전부 `distribution.py` 내부(private) |

## 종결 조건
D-5D3-7 test 초록(ML-04 ② 서빙 경로) · D-5D3-5 회귀 test 초록 · S-1~S-9 · verifier `ready-for-review` · 사용자 승인. 5D-2 알려진 제한 1·8 해소, `OPEN-5D2-SAMPLE-SEGMENT` 닫힘 등재(5D-2 문서는 편집하지 않고 5D-3 checklist·milestone-5 종결 문단에서 참조).

## 하네스 레인 변경
없음(리뷰 요청 시점에 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 갱신).

## 계약 갱신 이력
| 날짜 | 변경 | 사유 |
| --- | --- | --- |
