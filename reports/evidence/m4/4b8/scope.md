# Slice 계약 — M4 / 4B-8 · 대상 공고 요청 축 라벨 + 공종 코드 정규화 정본(`OPEN-4B7-TARGET-LABEL` · `OPEN-4B7-CATEGORY-NORMALIZATION`)

> **지위**: 운영자 결정 2026-09-16 「추천 A 진행」(4B-7 다음 (a)). 두 소형 OPEN 을 한 Kotlin slice 로 닫는다.
> ① 대상 공고의 요청 축 `baseAmountProvenanceLabel` 이 `Unknown` 고정(`predictionRequestFor`)인 것을 4B-7 이 표본에 쓴 것과
> **같은 분류기**(`ProvenanceRules.judgeRow`)로 붙인다 — 개찰 전이라 낙찰 입력은 없다(정직하게 `null`). ② 공종 코드의 Kotlin 측
> 정규화 정본을 `CategoryCode` 생성 한 자리로 두어(strip + lower — Python 5B `normalize_feature_key` 미러, 5D-3 D-5D3-1 과 같은
> 규칙), 표본 조회(SQL 정확 일치)·요청 축·표본 축이 같은 키를 쓰게 한다. 세션 모델 단독 작성. Phase 2.5 설계 검토(값 타입 불변식 —
> 게이트형) `_workspace/m4-4b8/02_design-review.md`. 레인: worktree `bid-vector-v2-m4e`, 브랜치 `m4-4b8/2026-09-16`, base = `origin/main`
> (`845e29b`, 다른 레인의 Python 병합 뒤). 구현 `kotlin-implementer`(sonnet) → `verifier`(opus). Kotlin(`procurement`·`adapters`·`workflow`)과 문서만.

## 착수 조사 실측
| 사실 | 위치 | 귀결 |
| --- | --- | --- |
| `ProvenanceRules` 네 규칙의 입력: `SuspectRatio`(rawBaseAmount·budgetEstimate) · `CleanInteger`(rawBaseAmount) · `DerivedYega`(**winningAmount·winningRate 필수** — 없으면 false) · `DerivedVat`(rawBaseAmount). 순서 suspect → clean → yega → vat, 첫 매치가 라벨, 매치 0 이면 `Unknown` | `decision/ProvenanceRules.kt` | 개찰 전 대상 공고는 `DerivedYega` 만 불가 — 나머지 셋으로 정직한 라벨(D-4B8-1) |
| 4B-7 `provenanceLabelFor(notice, opening, original, policy)` 가 `estimatedAmount`→budgetEstimate, `finalAwardAmount`/`winningRate` 를 `ProvenanceRow` 로 조립 | `workflow/evaluation/SampleConversion.kt` | 개찰 인자를 선택으로 일반화해 대상 공고에도 쓴다(D-4B8-2) |
| `CategoryCode(value)` `init` 은 공백만 거부, 정규화 없음. production 생성부 둘: 수집 canonicalize(`Canonicalize.kt`)·persistence 복원(`NoticeReconstruction.kt`). test 호출부 27(workflow 15·adapters 9·app 2·procurement 1) | `procurement/BusinessCategory.kt` | 정규화는 생성 한 자리(D-4B8-3), test 는 기계적 갱신 |
| Python 정규화 정본 `normalize_feature_key = strip().lower()`(별칭 없음) — 요청 축·표본 축 둘 다 통과(5D-3 D-5D3-6) | `ml-engine/.../features/normalize.py` | Kotlin 은 같은 규칙을 미러(D-4B8-3) — wire 에 이미 정규화된 값을 실어도 Python 이 다시 정규화(멱등) |
| 4B-7 조회 SQL 은 `n.business_category_code = ?` 정확 일치 | `adapters/ml/JdbcCompetitionSampleSource.kt` | 저장 값이 정규화돼 있어야 일치 — 쓰기 경로 정규화(D-4B8-4), 기존 행은 알려진 제한 |
| `@ConsistentCopyVisibility` + `internal`/`private` 생성자 관례 | `shared-kernel/Money.kt`(`BidAmount`)·`Rate` | `CategoryCode` 도 같은 처방(D-4B8-3) |
| `origin/main` 6f9da09 → 845e29b 는 다른 레인 병합(Kotlin 변경 여부는 착수 시 `git diff --name-only` 로 확인해 checklist 등재) | — | 겹침 0 이면 그대로, 있으면 갱신 이력 |

```yaml
milestone: m4
slice: 4b8-target-label-and-category-key
base_sha: 845e29b
in_scope:
  - procurement/src/main/kotlin/bidvector/procurement/BusinessCategory.kt          # ① CategoryCode: private 생성자(@ConsistentCopyVisibility) + `of(raw)` 팩토리(strip+lower, 빈 키는 기존과 같이 require 거부) + `normalizeCategoryKey(raw): String` 단일 함수 — D-4B8-3
  - procurement/src/main/kotlin/bidvector/procurement/Canonicalize.kt              # 수집 경로 `CategoryCode.of(code)` — D-4B8-4
  - adapters/src/main/kotlin/bidvector/adapters/persistence/NoticeReconstruction.kt   # 복원 경로 `CategoryCode.of(code)`(관용 — 기존 행도 읽힘)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/{SampleConversion,PredictionFacts}.kt   # ② provenanceLabelFor 의 개찰 인자 선택화(`opening: OpeningResult?`) + predictionRequestFor 가 대상 공고 라벨을 같은 함수로 — D-4B8-1·2
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityAnalysis.kt   # 필요 시 정책 인자 전달만(라벨 정책은 4B-7 `SAMPLE_PROVENANCE_POLICY` 재사용 — 새 정책 없음)
  - 관련 test(procurement·adapters·workflow·app — `CategoryCode(` 호출부 27 의 `of` 전환 + 정규화 규칙표 + 대상 라벨 규칙표 + 조회 일치 test) · config/quality/gate-tests.properties(신설 test 있을 때)
  - reports/evidence/m4/4b8/** · milestone-4.md · docs/discovery/capability-map.md(OPEN 둘 닫힘 + 백필 OPEN 신설) · docs/discovery/data-dictionary.md(공종 키 정규화 규칙 한 줄 — 팀장)
out_of_scope:
  - 별칭·계층 사전(5B `_NO_ALIASES` 계승) · 기존 저장 행 백필/마이그레이션(`OPEN-4B8-CATEGORY-BACKFILL` — 운영 데이터 없음, M6) · SQL 측 정규화(두 번째 규칙 금지 — 저장 값이 정규화됐다는 전제) · 기관 축(`OPEN-2B-AGENCY-ID`) · 복구 추정치(`recoveryEstimate` 는 계속 `Absent`, 3D 소관) · 예비가 0원 자격(contract-keeper PR #22 비차단 — 엔진이 거름, `OPEN-4B7-POLICY-VALUES` 의 이웃으로 등재만) · 계약 파일 · Python
acceptance: CI Kotlin `check` job 전건(clean check). 마이그레이션 없음.
rollback: in_scope 경로 한정 단일 역적용(2026-09-16 규칙), 임시 clone ①~⑥(⑥ 되돌린 트리 루트 `check` 전건). 되돌리면 라벨 `Unknown`·정규화 없음(4B-7 상태) 복귀.
```

## 결정(계약 고정 — 전부 4B-7·5B·5D-3 결정의 귀결)
| ID | 판단 | 근거 |
| --- | --- | --- |
| D-4B8-1 | 대상 공고 라벨 = `ProvenanceRules.judgeRow(original = 공고 기초금액, row = ProvenanceRow(rawBaseAmount, budgetEstimate = estimatedAmount, winningAmount = null, winningRate = null), Absent, SAMPLE_PROVENANCE_POLICY)` — `DerivedYega` 는 구조적으로 불가(정직), 나머지 셋으로 판정. 기초금액이 없으면 요청 자체가 `ScoreNotProvided`(기존 경로) | 두 번째 분류기 금지 · 개찰 전 입력은 없는 그대로(「모름을 지어내지 않는다」) |
| D-4B8-2 | 4B-7 `provenanceLabelFor` 의 `opening` 인자를 `OpeningResult?` 로 일반화(표본은 있음, 대상은 `null`) — 함수 하나, 분기는 입력 조립부 | 재사용 우선 |
| D-4B8-3 | `CategoryCode` 는 `@ConsistentCopyVisibility` + `private constructor`, 생성은 `CategoryCode.of(raw)` 하나: `normalizeCategoryKey(raw) = raw.trim().lowercase()`(Python `strip().lower()` 미러 — 유니코드 소문자화 규칙 차이는 알려진 제한) 뒤 빈 키는 `require` 거부(기존 동작). `value` 는 항상 정규화된 형태(불변식) | 5D-3 D-5D3-1 「요청 축·표본 축 같은 함수」 · `Rate`/`BidAmount` 생성자 관례 |
| D-4B8-4 | 정규화 적용 자리: 수집 canonicalize(쓰기) + persistence 복원(읽기, 관용) + 그 밖 `of` 호출 전부 — SQL 은 정확 일치 유지(저장 값이 정규화됐다는 전제). 정규화 전 저장 행은 조회에서 빠진다 → `OPEN-4B8-CATEGORY-BACKFILL`(운영 데이터 없음) | 두 번째 규칙 금지 |
| D-4B8-5 | `OpportunityPolicyData`·정책 값 무변경 — 라벨 정책은 4B-7 `SAMPLE_PROVENANCE_POLICY` 그대로(대상·표본이 같은 정책·같은 version 으로 판정) | 잠정값 확장 금지 |

## 위협·우회
(1) 대상 공고를 `Clean` 으로 위조 → 분류기 결과만 실림(공급 측 상수 없음) · (2) `DerivedYega` 를 개찰 없이 흉내 → 입력 `null` 이면 규칙이 false(변이 test) · (3) `CategoryCode` 를 정규화 안 된 값으로 직접 생성 → private 생성자 + `copy` 폐쇄(컴파일) · (4) SQL 에서 `LOWER(TRIM())` 로 두 번째 규칙 → 금지(코드 리뷰·grep) · (5) 정규화 뒤 빈 키(`"  "`) → `require` 거부(기존) · (6) wire 에 정규화 전 값 → `of` 만 생성하므로 불가 · (7) 대소문자만 다른 두 공종이 하나로 접힘 → Python 이 이미 그렇게 하므로 정합(알려진 제한으로 명시) · (8) 라벨 정책 version 이 표본과 대상에서 다름 → 같은 singleton.

## (2b) 값 획득 축
| 표면 | 처분 |
| --- | --- |
| `CategoryCode.of`(public 팩토리)·`normalizeCategoryKey`(public 순수 함수) | 연다 — 값 생성 권한은 기존과 같고 형태만 강제. 생성자 private 으로 표면 축소 |
| `provenanceLabelFor` 시그니처 변경(`opening: OpeningResult?`) | internal 유지 |
| 「`object` 주입」·「경계로 처리」 | 없음 |

## 종결 조건
정규화 규칙표 test(strip·lower·빈 키·유니코드 한 case) · `CategoryCode(` 직접 호출 0(컴파일) · 대상 라벨 규칙표(SuspectRatio·CleanInteger·DerivedVat·Unknown, DerivedYega 불가 변이) · `predictionRequestFor` 라벨이 `Unknown` 상수가 아님을 재는 test · 조회 SQL 무변경 확인 · 전건 `check` · verifier ready · 사용자 승인. `OPEN-4B7-TARGET-LABEL`·`OPEN-4B7-CATEGORY-NORMALIZATION` 닫힘, `OPEN-4B8-CATEGORY-BACKFILL` 등재.

## 하네스 레인 변경
없음(리뷰 요청 시점에 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 갱신).

## 계약 갱신 이력
| 날짜 | 변경 | 사유 |
| --- | --- | --- |
| 2026-09-16 (팀장 문서) | `data-dictionary.md` §6.3.1 「공종·발주기관 키의 정규화 — 한 규칙, 두 언어」 신설(+11줄, 삽입 지점 §6.3 끝). 역방향 파급: 그 파일을 `파일:줄` 로 인용하는 곳 중 삽입 지점 아래는 `reports/evidence/m0/0c/commands.md`·`reports/evidence/m4/4b1/checklist.md` 의 `data-dictionary.md:1551,1554` 둘 — 둘 다 **종결된 slice 의 evidence** 로 4B-1 이 이미 「범위 밖 낡은 좌표」로 등재한 자리라 고치지 않고 알려진 제한 승계(4B-8 checklist 에 등재) | evidence-pack 역방향 파급 규격 |
