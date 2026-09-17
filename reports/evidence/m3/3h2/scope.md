# Slice 계약 — M3 / 3H-2 · 발주기관 요청·표본 축 조립 + 표본 축 결측 사유(`OPEN-2B-AGENCY-ID` 2/2)

> **지위**: 운영자 결정 2026-09-17 「추천대로」 — ① 채움률 실호출은 공사 목록 1페이지(≤100건) read-only, 비율만 기록 ② 엔진 표본 축 결측 사유 확장(D-3H2-3)은 **이 레인**이 `ml-implementer` 로(M6 세션과 소스 겹침 0 실측) ③ Codex 모델 핀 `gpt-5.5`(하네스 `ff061e2`) ④ 환경 파일 생성 → 프로브 → 결과가 낮지 않으면 즉시 구현. 환경 파일 자리는 `config/` 아래 gitignore 된 디렉터리(운영자 결정, `5b362b7` — 경로·변수 이름은 그 커밋의 예시 파일이 정본). D-3H2-3 의 「확인 대기」는 **결정**으로 읽는다. 세션 모델 단독 작성.
> 3H-1 종결 기록: PR #30 머지 `e101a0c`(2026-09-17, 운영자 「pr #30 머지」). **Codex r1 `request_changes`(high 1 — 적용 DB rollback 경로)의 조치 `0f56131` 뒤 재심 r2 는 운영자 결정으로 생략** — v2-slice-pipeline 정지선의 「종결은 운영자 개별 명시 결정(예외 기록)」 에 따른 기록(PR #30 코멘트에도 남김).
> 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m3-3h2/2026-09-17`, base = `origin/main`(PR #30 병합 뒤). 구현 `kotlin-implementer`(sonnet) + (②가 이 레인이면) `ml-implementer`(sonnet) → `verifier`(opus) → 리뷰 레인(`code-reviewer`; wire·계약 파일 무변경이라 contract-keeper 는 `RequestMapping` 호출부 변경 시에만). 마이그레이션 없음 → Codex 대상 아님.

## 착수 조사 실측(2026-09-17)
| 사실 | 위치 | 귀결 |
| --- | --- | --- |
| 요청 조립 `predictionRequestFor` 는 `agencyId = null` 고정, 표본 조립 `sampleOf` 도 `agencyId = null` 고정(D-4B7-9) — 3H-1 뒤 `Notice.demandAgency` 가 있어도 읽지 않는다 | `workflow/evaluation/PredictionFacts.kt` · `SampleConversion.kt` | 두 자리를 `notice.demandAgency?.code` 로 — 본체 ① |
| wire 매핑: 요청 축 `null → UNKNOWN`, 표본 축 `null → NOT_COLLECTED_YET`(`toSampleAgencyIdFact`) — `RequestMapping` 은 사유를 값의 부재에서만 결정한다 | `adapters/ml/RequestMapping.kt` | 3H-1 뒤 표본의 `null` 은 「수집했으나 원천에 없음」이 되어 `NOT_COLLECTED_YET` 이 거짓 — 본체 ② |
| 엔진 표본 축 허용 결측 사유는 `{NOT_COLLECTED_YET}` 닫힌 집합(D-5D3-2·6), 그 밖은 `SampleRejected(SEGMENT_REASON_NOT_ALLOWED)` 로 **표본 하나** 거부(요청 사망 아님). 요청 축은 선언된 셋(`UNKNOWN`·`NOT_APPLICABLE`·`NOT_COLLECTED_YET`) 수용 | `ml-engine/.../inference/distribution.py` `_is_segment_missing_reason_allowed` · `observations.py` · 5D-3 scope D-5D3-2 | 표본 축이 `UNKNOWN` 을 받아야 코드 없는 표본이 공종·전역 계층에 남는다 — 본체 ③(`OPEN-3H-SAMPLE-MISSING-REASON`) |
| 4B-7 표본 조회는 키만 SELECT 하고 `NoticeRepository.find` 로 복원(N+1) — 3H-1 의 `NoticeReconstruction` 이 `demandAgency` 를 복원하므로 SQL 무변경 | `adapters/ml/JdbcCompetitionSampleSource.kt` | 표본 축은 복원된 `Notice` 에서 흐른다 |
| 표본 축 `agency` 는 정규화 문자열 완전 동등으로만 매치(`_matches`), `Present` 끼리만 — 요청·표본이 같은 `AgencyCode.of` 값을 실으면 `segment_support = DIRECT` 가 처음 도달 가능 | `distribution.py` `_resolve_segment` | 엔진 교차 실측이 종결 조건 |
| 코드 채움률은 미측정(D-3H-8) — read-only 실호출 1회 승인(2026-09-16). 서비스 키는 저장소 밖 파일(3A §1.9.7 선례), 값·담당자 정보는 저장·인용하지 않는다 | 3H scope D-3H-8 | 착수 조건: 채움률 표본 측정 → D-3H-2(코드 키) 재확인. 채움률이 낮으면 운영자에게 (b) 이름 키를 다시 올린다 |
| 3H-1 이전 저장 행은 기관 컬럼이 비어 있다(운영 데이터 0) — 사유는 「수집 전」이나 3H-1 뒤에는 「원천에 없음」과 구별할 컬럼이 없다 | `OPEN-3H-AGENCY-BACKFILL` | 표본 축 사유는 **`UNKNOWN` 하나로 통일**(구별 컬럼을 두지 않는다 — 백필이 되면 값이 채워질 뿐) — D-3H2-2 |

```yaml
milestone: M3
slice: 3h2-agency-request-sample-axis
base_sha: e101a0c33adc52b1a5688afcaab6a39bfe777a66
head_sha: 2542e3262a56e8cdb3f217cd5b00c601aceffb7b
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/PredictionFacts.kt           # predictionRequestFor agencyId = demandAgency code
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleConversion.kt          # sampleOf agencyId = 표본 공고 demandAgency code
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/**                           # PredictionFactsTest·SampleEligibilityTest·OpportunityAnalysisTest(요청·표본 축 값·역할 폴백 0)
  - adapters/src/main/kotlin/bidvector/adapters/ml/RequestMapping.kt                    # 표본 축 null → UNKNOWN(D-3H2-2)
  - adapters/src/test/kotlin/bidvector/adapters/ml/**                                   # RequestMapping 왕복·엔진 교차 실측 test
  - ml-engine/src/ml_engine/inference/distribution.py                                   # (②) 표본 축 허용 결측 사유 {NOT_COLLECTED_YET, UNKNOWN}
  - ml-engine/src/ml_engine/inference/observations.py                                   # (②) 갱신 1 — 표본 축 허용 집합 문면(docstring) 정정, 코드 무변경
  - ml-engine/src/ml_engine/features/facts.py                                            # (②) 갱신 1 — 「표본 축만 닫힌 집합 하나」 문면 정정, 코드 무변경
  - contracts/proto/bidvector/ml/v1/features.proto                                        # 갱신 2 — CompetitionSample.agency_id 주석의 허용 결측 사유 선언({NOT_COLLECTED_YET} → {NOT_COLLECTED_YET, UNKNOWN}) — 주석만, 필드·타입·번호 무변경(buf breaking 무영향), contract-keeper 대상
  - workflow/src/main/kotlin/bidvector/workflow/prediction/BidPredictionRequest.kt       # 갱신 2 — CompetitionSample KDoc 의 같은 문면(도메인층 사본) 정정
  - ml-engine/tests/**                                                                   # (②) 허용 집합 test·회귀 golden 불변 확인
  - reports/evidence/m5/5d3/scope.md                                                    # (②) 계약 갱신 이력 — D-5D3-2 표본 축 집합 확장(팀장)
  - docs/discovery/data-dictionary.md                                                   # §6.3.2 결측 행 갱신(팀장)
  - docs/discovery/capability-map.md                                                    # OPEN-2B-AGENCY-ID 닫힘·OPEN-3H-SAMPLE-MISSING-REASON 닫힘(팀장)
  - milestone-3.md                                                                      # 3H-2 착수·종결(팀장)
  - reports/evidence/m3/3h2/**
out_of_scope:
  - procurement/** · adapters/persistence/** · db/migration/**                           # 3H-1 산출물 소비만, 마이그레이션 없음
  - contracts/** 의 필드·타입·번호·승인 태그                                               # wire 인코딩 무변경(MissingReason 값은 이미 있다) — 단 features.proto 의 허용 사유 **주석**은 갱신 2 로 in_scope(verifier r1 F-1)
  - 표본 조회 SQL(기관 필터·인덱스 — OPEN-4B7-QUERY-INDEX) · 이름 키 · 계층 · 백필
  - 채움률 실호출의 값 저장·인용(구조 관측·비율만 evidence 에)
acceptance_commands:
  - ./gradlew --no-build-cache --no-daemon clean check
  - (②) cd ml-engine && uv run pytest
rollback: in_scope 경로 한정 restore. 공유 파일(5d3 scope·data-dictionary·capability-map·milestone-3)은 hunk 격리
```

## 결정(초안)
| ID | 결정 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-3H2-1** | 요청 축·표본 축 `agencyId` = `notice.demandAgency?.code` 의 값(`AgencyId(code.value)`). 공고기관으로 폴백하지 않는다(D-3H-2). 코드 없는 공고는 요청 축 `UNKNOWN` 그대로 | 3H D-3H-2 | 계약 |
| **D-3H2-2** | 표본 축 결측 사유를 `NOT_COLLECTED_YET` → **`UNKNOWN`** 으로(`toSampleAgencyIdFact`). 「수집 전」과 「원천에 없음」을 구별하는 컬럼을 두지 않는다 — 두 경우 모두 오늘 값이 없다는 사실만 참이고, 백필은 값을 채울 뿐 사유를 바꾸지 않는다 | 정직 규율(4B-3 미가용의 이름) · `OPEN-3H-AGENCY-BACKFILL` | 계약 |
| **D-3H2-3** | (②) 엔진 표본 축 허용 결측 사유 집합을 `{NOT_COLLECTED_YET, UNKNOWN}` 으로 — `UNSPECIFIED`·`NOT_APPLICABLE`·미지 정수는 그대로 거부. 5D-3 계약 갱신 이력에 등재. D-5D3-5 회귀(전부 `Missing` 인 요청은 5D-2 와 비트 동일)는 `UNKNOWN` 으로도 성립해야 한다 | `OPEN-3H-SAMPLE-MISSING-REASON` · 거부가 죽이는 것 = 코드 없는 표본 전부(공종·전역 계층에서도 탈락) | **운영자·M5 레인 확인 대기** |
| **D-3H2-4** | 엔진 교차 실측: 요청·표본이 같은 수요기관코드를 실은 fixture 로 `serve_bid_rates` 를 돌려 `segment_support = DIRECT`·`agency_sample_count ≥ 1` 을 처음 관측(ML-04 ② 「기관 표본이 임계 미만이면 수축 가중치가 응답 근거에 실린다」 의 Kotlin→엔진 왕복). 4B-7 교차 실측 관례 | ML-04 ② acceptance | 계약 |
| **D-3H2-5** | 채움률 실호출(D-3H-8)은 구현 전에 — 결과(표본 n 건 중 `dminsttCd` 채움 k, 공고기관과 동일 비율)를 evidence 에 비율만 적는다. k/n 이 낮으면 D-3H-2 재결정을 운영자에게 올리고 구현을 멈춘다 | 「측정 전 잠정」 | **충족(2026-09-17)** — 공사 목록 100건: 코드·이름 넷 전부 100/100, 수요=공고 91/100, 길이 {7}, 영문자 포함 29/100(`commands.md`). 코드 키 확정 |

## 위협 모델 경계·우회·(2b)(초안 — 확정 시 갱신)
**방어하는 것**: 역할 혼용(공고기관 코드가 `agency_id` 로), 사유 거짓(`NOT_COLLECTED_YET` 잔존), 엔진 거부로 코드 없는 표본이 공종·전역 계층에서도 탈락하는 것, 이름이 키로 새는 것.
| # | 우회 | 닫힘 |
| --- | --- | --- |
| (1) | `predictionRequestFor`/`sampleOf` 가 `noticeAgency` 로 폴백 | test: 수요기관 없음·공고기관 있음 → `agencyId == null` |
| (2) | 표본 축에 `NOT_COLLECTED_YET` 잔존 | `RequestMapping` test: 표본 `null` → `UNKNOWN`, `NOT_COLLECTED_YET` 생성 경로 0(grep) |
| (3) | 엔진이 `UNKNOWN` 표본을 거부 | (②) 엔진 test + Kotlin 교차 실측(코드 없는 표본이 `excluded_observations` 에 안 잡힘) |
| (4) | 이름을 `AgencyId` 로 싣는다 | `AgencyId` 는 `AgencyCode.value` 에서만 — grep + test |
| (5) | 요청·표본 정규화 불일치로 `DIRECT` 미도달 | 같은 `AgencyCode.of` 값(3H-1) · 교차 실측 D-3H2-4 |
(2b): `AgencyId` 생성 자리 둘(기존 타입, 새 표면 0) · (②) Python 허용 집합 상수 — 새 public 표면 0.

## 종결 조건
채움률 실측 등재 · 요청·표본 축 값 test · 사유 `UNKNOWN` test · (②) 엔진 허용 집합 test + golden 불변 · 교차 실측 `DIRECT` 관측 · 전건 `check`(+ pytest) · verifier ready · `OPEN-2B-AGENCY-ID`·`OPEN-3H-SAMPLE-MISSING-REASON` 닫힘 · 사용자 승인.

## 하네스 레인 변경(리뷰 요청 시점 `git log --oneline e101a0c..HEAD -- CLAUDE.md .claude/ docs/harness/`)
slice 산출물이 아니며 in_scope 밖, 운영자 승인 하에 같은 range 에 있다. rollback 대상이 아니다.
- `ff061e2` `.claude/skills/codex-review-gate/SKILL.md` — Codex 심판 모델 핀 `gpt-5.5`(운영자 결정 2026-09-17 「추천대로」 ③).
- `7a230bb` `docs/harness/change-history.md` — 위 모델 핀 + 환경 파일 자리 두 행(같은 커밋이 이 scope 지위 문단도 확정 — 그 부분은 slice 산출물).
- **운영자 결정 변경(하네스는 아니나 slice 산출물도 아님)**: `5b362b7` `.gitignore` + `config/` 아래 예시 환경 파일 하나 — 환경 파일 자리(운영자 지시 2026-09-17 「환경 파일 등은 기본적으로 config/ 에 두자」, 경로·변수 이름은 그 커밋이 정본). D-3H-8 프로브가 이 자리를 읽었다. rollback 대상 아님.

## 계약 갱신 이력
| # | 일시 | 갱신 | 사유 |
| --- | --- | --- | --- |
| 1 | 2026-09-17 | in_scope 에 `ml-engine/.../inference/observations.py`·`features/facts.py`(docstring 문면 정정만, 코드 무변경) 추가 | 구현 레인 보고 — 허용 집합 확장으로 두 모듈 docstring 의 「`{NOT_COLLECTED_YET}` 하나」 문면이 낡았다. in_scope 손 열거 누락(3H-1 갱신 3 과 같은 갈래) |
| 2 | 2026-09-17 | in_scope 에 `contracts/proto/bidvector/ml/v1/features.proto`(`CompetitionSample.agency_id` 주석의 허용 결측 사유 선언 — 주석만, 필드·타입·번호·태그 무변경) + `workflow/prediction/BidPredictionRequest.kt`(같은 문면의 KDoc 사본) 추가. out_of_scope 의 「`contracts/**` 무변경」을 「필드·타입·번호·태그 무변경」으로 좁힘. PR 리뷰 레인에 **contract-keeper** 추가. 계약 파일 변경이라 Codex 심사 대상 여부는 운영자 결정(주석만) | verifier r1 **F-1(high, 계약층)** — 계약 문면이 「허용 사유는 `NOT_COLLECTED_YET` 하나뿐, 송신 어댑터가 다른 사유를 지어내지 않는다」로 선언하는데 이 slice 가 송신·수신 양쪽에 `UNKNOWN` 을 넣었다. `buf breaking` 은 주석을 안 보고 `leakPatternGate` 는 evidence 만 봐 게이트가 못 잡는다. 원 계약의 「wire 무변경」 제외 사유는 인코딩 호환만 덮고 허용값 선언을 안 덮었다 |
