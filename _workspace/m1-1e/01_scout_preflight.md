# M1/1E preflight — Strategy 와 상태 (읽기 전용 조사)

- legacy: `bid-vector/` pinned `ed4b06c` (`ed4b06cbb886…`, 2026-08-14). 아무것도 수정하지 않았다.
- V2: 작업트리 HEAD `4a4d1aa`(조사 시작 시점). 1D 구현 레인이 같은 트리에서 진행 중이라
  `decision/**`·`app/**` 는 읽기만 했다.
- 층 표기: `observed`(파일에서 직접 읽음) · `legacy-behavior`(기존 동작, 정답 아님) · `OPEN`.
- legacy 좌표는 `bid-vector/` 상대경로 + 줄 번호(pinned 라 낡지 않는다). V2 문서는 절 제목·결정 id 로 가리킨다.

**MISSING-ANCHOR 2건**

| 앵커 | 실측 |
| --- | --- |
| `regression-ledger.md` 의 `R-STR-*` | **0건.** ledger 의 id 접두는 `R-BASIS`·`R-RATE`·`R-PROV`·`R-FLOOR`·`R-QUAL`·`R-COL`·`R-ASYNC` 뿐이다. 전략 축 회귀는 `R-BASIS-01`(감시 예산 필터)·`R-BASIS-02`(검색 예산 필터) 둘이 받는다 — §3 이 그 둘로 조사했다 |
| `data-dictionary.md` §2.2 의 「전략 편집·저장·감시 실행」 전이표 | **없다.** §2.2 의 전이표는 `NoticeStatus`·`DecisionState`·`TenderOutcome`·`AwardOutcome` 넷 + `outbox`(형태 요구만)이고 **전략/스냅샷/감시 run 축은 §2.2 에 자리가 없다.** §2 가 그 대조를 낸다 |

`fixtures/manifest.yaml` 에 **strategy·watch 도메인은 없다** — 도메인 11종은 `license`(12) ·
`koneps-collection`(9) · `money-basis`(6) · `floor-shortfall`(6) · `rate-unit`(5) ·
`floor-applicability`(5) · `base-amount-provenance`(5) · `capacity-gate`(4) · `ml-boundary`(4) ·
`verdict`(4) · `floor-threshold`(3), 합 63 case 다.

---

## 1. (a) `OperatorStrategy` 실물

### 1.1 필드 — 저장 형태와 기본값 (`observed`)

정본은 `app/models/models.py:211-236`(ORM) · `app/schemas/operator_strategy.py:10-27`(입력 검증) ·
`app/core/single_user.py:25-26`(임계 기본값 상수) 셋이다.

| 필드 | 컬럼 타입 | 기본값 | 입력 검증(pydantic) | 비고 |
| --- | --- | --- | --- | --- |
| `focus_categories` | `Text` | `""` | `List[str]`, 내용 검증 없음 | 저장은 **쉼표 결합 문자열**(`join_multi_value_text`) |
| `focus_regions` | `Text` | `""` | 같음 | 같음 |
| `exclude_regions` | `Text` | `""` | 같음 | 같음 |
| `required_keywords` | `Text` | `""` | 같음 | 같음 |
| `exclude_keywords` | `Text` | `""` | 같음 | 같음 |
| `min_budget_estimate` | `Float` | `0.0` | `ge=0.0` | **`0` = 「제한 없음」 sentinel** |
| `max_budget_estimate` | `Float` | `0.0` | `ge=0.0` | 같음 |
| `minimum_match_score` | `Float` | `0.6` | `0.0≤x≤1.0` | 분석 **후** 필터 |
| `minimum_probability_score` | `Float` | `0.55` | `0.0≤x≤1.0` | 분석 **후** 필터 |
| `bid_now_threshold` | `Float` | `0.7` | `0.0≤x≤1.0` | 액션 분기 |
| `review_threshold` | `Float` | `0.45` | `0.0≤x≤1.0` | 액션 분기 |
| `auto_workload_penalty_multiplier` | `Float` | `1.0` | `0.0≤x≤2.0` | clamp 도 별도 존재 |
| `category_priority_overrides` | `Text` | `"{}"` | `Dict[str,float]` | **JSON 을 문자열 컬럼에** |
| `notify_only_high_priority` | `Boolean` | `True` | `bool` | 이름과 실제 효과가 다르다(§1.5) |
| `max_recommended_candidates` | `Integer` | `10` | `1≤x≤100` | 표시 상한 + 분석 예산 겸용 |

다중값 문자열의 파싱은 `app/core/single_user.py:30-36` 하나다 — `;` 를 `,` 로 바꾼 뒤
`split(",")` + `strip()`, 빈 조각 제거. 순서·중복은 보존한다.

기본값이 **세 곳에 문자 그대로 중복**된다(`legacy-behavior`):
`models.py:224-231`(컬럼 default) · `notifications/telegram_strategy_fields.py:277-292`
(`default_value_for`) · `api/operator_strategy.py:77-84`(`strategy_configured` 판정) —
그리고 넷째로 `opportunity_monitoring/candidates.py:305-329`(`_has_configured_watch_rules`)가
같은 목록을 다시 적는다. `0.6`·`0.55`·`1.0`·`10` 은 상수 이름조차 없이 리터럴이다.

### 1.2 validation — **같은 규칙이 네 자리, 실패 거동 세 가지, 경계 두 가지** (`legacy-behavior`)

| # | 자리 | 규칙 | 경계 | 실패 시 거동 |
| --- | --- | --- | --- | --- |
| 1 | `app/api/operator_strategy.py:88-93` | `review > bid_now` 금지 | **등호 허용**(`review == bid_now` 통과) | `HTTPException 400` |
| 2 | `app/services/notifications/telegram_strategy_fields.py:249-259` | 같음 | 같음 | **문자열 반환**(`"review_threshold는 bid_now_threshold보다 클 수 없습니다."`) — 예외 아님 |
| 3 | `app/services/decision_experiments/application.py:39-45` | 같음 | 같음 | **조용한 clamp** — `review_threshold = bid_now_threshold` 로 값을 고쳐 읽는다. 거부하지 않는다 |
| 4 | `app/services/decision_experiments/application.py:342-345` | 실험 추천값의 상한 | **`bid_now − 0.01`** — 위 셋과 **다른 경계** | 조용한 clamp(`min(proposed, upper_bound)`) |

- `min_budget > max_budget` 검사는 **텔레그램 경로에만 있다**
  (`telegram_strategy_fields.py:261-265`, 둘 다 `> 0` 일 때만). REST PUT 경로에는 없다 —
  `api/operator_strategy.py` 의 `_validate_strategy_thresholds` 는 임계 쌍만 본다.
- pydantic 은 **범위만** 본다(`schemas/operator_strategy.py:16-27`). 교차 필드 불변식은 없다.
- `category_priority_overrides` 는 파싱 실패 시 **조용히 빈 매핑으로 degrade** 한다
  (`operator_strategy_tuning.py:50-75` — `load_stored_json_object` 가 `None` 이면 `{}`).
  값은 `[-0.2, +0.2]` 로 clamp(`:35-47`), 비수치는 `0.0`.
- `auto_workload_penalty_multiplier` 는 비수치 입력을 **기본값 `1.0` 으로 대체**한다(`:20-32`).

### 1.3 `matches` 실물 — `_apply_strategy_filters` (`observed`)

정본은 `app/services/opportunity_monitoring/filters.py:21-75` 하나이고,
`watch_rules.py:67-74` 의 `matches_strategy_watch_rules` 가 그것을 그대로 위임한다.
평가 순서는 **선언 순서 = 판정 순서**이며 첫 탈락에서 즉시 `matched=False` 를 낸다.

| 순 | 규칙 | 대상 텍스트 | 결합 | 경계·정규화 | 줄 |
| --- | --- | --- | --- | --- | --- |
| 1 | 중점 카테고리 | `project.category` 단일 필드 | **완전일치 집합 멤버십**(부분일치 아님) | 양쪽 `strip().lower()` | `:32-38` |
| 2 | 중점 지역 | `project_text`(title+**description**+requirements+category) | 부분문자열 **OR** — 하나라도 걸리면 통과 | 양쪽 lower, `in` 연산 | `:40-45` |
| 3 | 제외 지역 | `project_text` | 하나라도 걸리면 **탈락** | 같음 | `:47-49` |
| 4 | 필수 키워드 | `keyword_text`(title+requirements+category, **description 제외**) | 부분문자열 **OR** | 같음 | `:51-56` |
| 5 | 제외 키워드 | `keyword_text` | 하나라도 걸리면 **탈락** | 같음 | `:58-60` |
| 6 | 예산 하한 | `project.budget_estimate`(**추정가격**) | `min > 0 and budget < min` → 탈락 | **등호 통과**(`budget == min` 은 통과) | `:62-66` |
| 7 | 예산 상한 | 같음 | `max > 0 and budget > max` → 탈락 | **등호 통과** | `:67-68` |

- **텍스트 범위 비대칭(STR-02)의 실물**: 두 헬퍼가 나눈다 —
  `_build_project_text`(`:77-83`, 네 필드) vs `_build_keyword_text`(`:85-98`, 세 필드).
  `description` 제외 사유가 주석에 있다 — *"KONEPS collectors store metadata (공고기관/공고번호/URL)
  there, so 기관명 like "해양수산부" must NOT satisfy a required keyword"*(`:88-90`).
  **규율은 호출 규약뿐이고 타입이 없다** — 규칙 시그니처가 `str` 하나를 받는다.
- 매칭 항목 수집(`_matched_terms`, `:100-111`)은 **중복 제거 + 사용자 순서 보존**이고,
  통과 사유 문자열은 상위 두세 개만 싣는다(`:45`·`:56` 의 `[:2]`·`[:3]`).
- 통과 사유는 **한국어 문장 리스트**(`StrategyFilterResult.reasons`, `base.py:26-31`)이고
  아무것도 안 걸리면 `"기본 전략 조건 통과"` 를 넣는다(`:72-73`).
- **「게이트 없음」과 「전부 통과」의 구분**은 별도 함수가 든다 — `watch_rules.py:46-64`
  `has_watch_rules`. 그 docstring 이 이유를 적는다 — *"A strategy with every watch field empty
  passes every notice … treat this as "no gate" rather than "all notices match""*.
  대상 필드 목록을 **데이터로** 선언한 것(`WATCH_RULE_TEXT_FIELDS`·`WATCH_RULE_BUDGET_FIELDS`,
  `:28-35`)이 legacy 안에서 드문 좋은 형태다. 다만 `candidates.py:305-329` 의
  `_has_configured_watch_rules` 가 **임계까지 포함한 다른 목록**을 손으로 다시 적는다 —
  「감시 범위를 좁히는 규칙이 있는가」와 「전략이 설정됐는가」가 두 구현으로 갈려 있다.

### 1.4 액션 임계치 — 게이트 사다리 (`observed`)

`app/services/allocation_core.py:126-181` 의 `decide(signals, thresholds)`.
docstring 이 스스로 순수성과 순서를 적는다 — *"the ladder keeps its first-match ordering:
capacity-hold -> force-bid/bid_now -> review -> skip"*(`:130-131`).

| 순 | 분기 | 조건 | 경계 | 임계 출처 |
| --- | --- | --- | --- | --- |
| 1 | capacity hold(= `skip`) | `current_active_bids >= max_active_bids` **AND** `priority_score < capacity_hold_priority_threshold` | 활성 건수는 **등호 포함**, 우선순위는 **엄격 미만** | `settings.ALLOCATION_CAPACITY_HOLD_PRIORITY_THRESHOLD` |
| 2 | `bid_now` | `priority_score >= bid_now_threshold` **OR** (`probability >= 0.8` **AND** `matched >= 0.7`) | 셋 다 **등호 포함** | 앞은 **persisted 전략**, 뒤 둘은 `settings.ALLOCATION_FORCE_BID_*`(`app/core/config.py:401-402`) |
| 3 | `review` | `priority_score >= review_threshold` | 등호 포함 | **persisted 전략** |
| 4 | `skip` | else | — | — |

- **`OPEN-STR-03` 의 force-bid 우회가 여기 있다** — 2번 분기의 `or` 절이 운영자 임계를
  건너뛴다. `Decision(action, reasons)` 의 `reasons` 는 한국어 문장뿐이라
  **우회가 걸렸는지가 산출물에 남지 않는다**(`:169-174`).
- `AllocationThresholds.from_settings`(`:44-58`)가 **persisted 둘 + settings 셋** 을 한
  경계 스냅샷으로 모으고, 그 덕에 `decide` 자신은 I/O 가 없다. **1E 가 그대로 쓸 수 있는 형태다.**
- 분석 **후** 필터 둘은 다른 자리에 있다 — `candidates.py:192-194`
  (`matched_score < minimum_match_score` · `probability_score < minimum_probability_score`,
  둘 다 **엄격 미만이면 탈락** = 등호 통과).

### 1.5 나머지 노브

| 노브 | 실물 | 순수? | 관찰 |
| --- | --- | --- | --- |
| `notify_only_high_priority` | `candidates.py:196` → `_is_high_priority_candidate`(`filters.py:113-116`, `pursue_bid and action == "bid_now"`) | 순수 | **이름은 알림, 실제는 후보 선정 자체를 자른다** — preview 목록과 결정 레코드가 함께 사라진다 |
| `category_priority_overrides` | `operator_strategy_tuning.py:104-113` `resolve_category_priority_override` — 카테고리 **대소문자 무시 완전일치**, 미지 카테고리는 `0.0` | 순수(입력이 이미 파싱된 전략 객체일 때) | 소비처는 `opportunity_analysis/orchestration.py:282` 한 곳 |
| `max_recommended_candidates` | 표시 상한: `runs.py:202` `max(1, min(limit or strategy.max_recommended_candidates or 10, 100))` | 순수 clamp | **분석 예산과 결합**: `candidates.py:281-283` `_preview_scan_limit = clamp(limit×12, 30, 250)`, `:296-301` `_schedule_scan_limit = clamp(max(설정, limit×12, 30), …, 400)` — 뒤는 `settings` 를 읽어 **순수하지 않다** |
| 정렬 기준 | `candidates.py:376-382` `sort_key = (−priority, −probability, −matched, −budget_estimate, +project.id)` | 순수 | 전부 내림차순 + id 오름차순 tie-break. `budget_estimate` 가 정렬 키에도 들어간다 |

### 1.6 순수성 요약

| 판정 | 순수 함수인가 | 근거 |
| --- | --- | --- |
| watch 규칙 일곱(§1.3) | **예** — DB·시계·설정 접근 0 | `filters.py:1` 모듈 docstring *"(no scoring, no ML, no DB)"* · `watch_rules.py:70-73` |
| `has_watch_rules` | **예** | `watch_rules.py:56-64`, `getattr` 만 |
| `matches_strategy_watch_rules` | **예(효과 기준)** — 다만 `lru_cache(maxsize=1)` 싱글턴 서비스를 거친다(`watch_rules.py:38-43`). 그 서비스의 `__init__` 은 세 서비스와 `SessionLocal` 을 붙잡는다(`base.py:111-116`) | 판정 자체는 I/O 를 안 하지만 **호출이 전역 상태를 만든다** |
| 액션 사다리 `decide` | **예** | `allocation_core.py:34-35` *"the three gate literals come from settings and are collected here so `decide` stays I/O-free"* |
| 분석 후 임계 둘 | **예**(비교만) — 입력이 ML 산출 dict | `candidates.py:192-194` |
| 값 validation 넷(§1.2) | **1·2·4 는 예, 3 은 예**(전부 값 연산) | 다만 1 은 `HTTPException` 을 던져 **웹 프레임워크에 묶여 있다** |
| 후보 상한 | 표시 상한은 예, **분석 예산은 아니다**(`settings` 읽기) | `candidates.py:298` |
| 카테고리 가중 | 예 | `operator_strategy_tuning.py:104-113` |

### 1.7 예산 basis — `OPEN-STR-01` 해소 문면과의 대조

| 축 | legacy | 운영자 결정 |
| --- | --- | --- |
| 감시 경로 비교 대상 | `Project.budget_estimate` = **추정가격**(`filters.py:62`) | **기초금액**(`OPEN-STR-01` 답: *"예산 필터에 적은 값은 기초금액이다"*) → **`legacy-defect`** |
| 검색 경로 비교 대상 | 같은 컬럼(`app/api/projects.py:112-116`, 파라미터 설명이 컬럼명을 그대로 노출 — `:71-80`) | 같음. `STR-16` acceptance 가 basis 일치를 고정 |
| capture 경로 | **이미 고쳐졌다** — commit `0755695`(#354). 선행 `4645ce4`(#162) · `c4ec93b`(#262) | — |

즉 **같은 결함이 세 경로에 있었고 하나만 고쳐졌으며**, `0755695` 가 감시 모듈의 다른 파일
(`opportunity_monitoring/base.py`·`candidates.py`)은 건드리면서 `filters.py` 의 예산 guard 는
건드리지 않았다. 남은 둘이 `R-BASIS-01`·`R-BASIS-02` 다.

---

## 2. (b) 상태 전이

### 2.1 legacy 의 상태 축 — **셋이고 서로 독립이다** (`observed`)

legacy 에 「전략 편집」 상태 기계는 **없다.** 전략 행은 그냥 갱신되고 `updated_at` 이 찍힐 뿐이다
(`models.py:233`, `onupdate=utc_now`). 상태 값 집합이 있는 축은 셋이다.

**축 A — 감시 run (`OperatorStrategyRun.status`)**

| 현재 | 이벤트 | 다음 | 근거 |
| --- | --- | --- | --- |
| (없음) | 비동기 생성 | `queued` | `models/pipeline.py:31`(컬럼 default), `runs.py:38` |
| (없음) · `queued` | 실행 시작 | `running` | `runs.py:226`(신규 생성 경로) · `:239`(기존 행 전이) |
| `running` | 정상 종료 | `completed` | `runs.py:275` |
| `running` | 예외 | `failed` | `runs.py:339` |
| — | 취소 | `cancelled` | **어휘만 있다** — `schemas/operator_strategy.py:193` 의 `Literal` 에는 있으나 `status = "cancelled"` 대입이 서비스 코드에 없다 |

- **전이표 선언이 없다.** 전이는 네 개의 대입문이고, `status` 는 제약 없는 `String(50)` 이다.
- `failed` 로 닫는 경로가 **세션 두 벌**을 쓴다(`runs.py:320-330` → `_write_run_failure`,
  실패 시 `_write_run_failure_on_new_session`) — 고아 `running` 을 남기지 않으려는 방어.

**축 B — run item lineage (`OperatorStrategyRunItem.stage`)**

`candidate_persistence.py` 가 대입으로만 진행한다:
`selected`(`:48`) → `workload`(`:77`) → `decision`(`:86`) → `notification`(`:101`) → `completed`(`:136`).
`status` 는 `processing`(`:47`) → `completed`(`:135`) 둘. 예외 경로는 `stage` 를 예외 객체에
얹어 상위가 읽는다(`execution.py:144`) — `project_reload`·`persistence` 라는 **표에 없는 stage 문자열**이
그 자리에서 새로 생긴다(`candidate_persistence.py:73`, `execution.py:144`).

**축 C — preview 스냅샷 (`OperatorPreviewSnapshot.status`)**

| 현재 | 이벤트 | 다음 | 근거 |
| --- | --- | --- | --- |
| (없음) | 최초 조회 | `idle` | `preview_snapshot.py:85` |
| `idle`·`failed`·(회수 대상 `running`) | 클레임 성공 | `running` | `:98-140` — `UPDATE … WHERE (status != running OR updated_at <= 회수컷오프)` 의 rowcount 판정 |
| `running` | 계산 완료 | `idle` (+ `computed_at` 갱신) | `:354-358` |
| `running` | 계산 실패 | `failed` | `:364-370` |

- **`stale` 은 상태가 아니라 파생 술어다** — `_snapshot_is_stale`(`:460-468`) =
  `_computed_age_exceeds`(설정 초 경과) **OR** `_strategy_changed_since_scan`.
  뒤쪽은 스냅샷 payload 에 심어 둔 **계산 시작 시점의 `strategy.updated_at`** 과
  현재 값을 비교한다(`:426-451`, 키 상수 `PAYLOAD_STRATEGY_UPDATED_AT_KEY`).
  **이 설계는 유지 가치가 있다** — "편집 전에 시작돼 편집에 눈먼 스캔"을 시간 TTL 없이 잡는다.
- `computed_at is None` 인 행은 **stale 이 아니라 부트스트랩**이다(`:465`) — 「없음」과 「낡음」을 가른다.
- 실패 쿨다운(`_failure_cooldown_active`, `:470-482`)이 **자동 디스패치만** 억제하고 명시 갱신·전략 쓰기는 우회한다.

### 2.2 전이 트리거 — 무효화 디스패치의 실제 호출부 (`observed`)

`PreviewSnapshotService.dispatch_for_strategy_write`(`preview_snapshot.py:192-215`)를 부르는 자리 넷:

| 쓰기 경로 | 부르는가 | 근거 |
| --- | --- | --- |
| REST `PUT /operator/strategy` | 예 | `api/operator_strategy.py:241` |
| Telegram 편집 | 예 | `notifications/telegram_strategy.py:223` |
| 실험 임계 적용 | 예 | `decision_experiments/lifecycle.py:298` |
| 실험 튜닝 적용 | 예 | `decision_experiments/lifecycle.py:362` |
| **온보딩 확정 반영** | **아니다** | `onboarding/apply.py` 가 `strategy` 행에 `setattr` 후 commit(반영 루프 `:383-394`, commit `:399-401`)하는데 `preview_snapshot` 을 **import 조차 하지 않는다**(전체 grep 매치 0) |

→ **STR-07 의 결함이 HEAD 에 그대로 있다.** 무효화가 호출자 규율에 맡겨져 있고
**새 write 경로가 늘 때마다 재발한다**는 것이 구조적 문제다.

### 2.3 `data-dictionary.md` §2.2 전이표와의 대응 — **행 대 행 대응이 없다**

| §2.2 의 표 | 1E 의 세 축과 대응하는가 |
| --- | --- |
| §2.2.1 `NoticeStatus`(`Open`·`Renoticed`·`Closed`·`Awarded`·`Failed`·`Cancelled`) | **없음.** 다만 legacy 의 watch 스캔 모수가 이 축을 쓴다 — `ACTIVE_PROJECT_STATUSES = {"open","re_notice"}`(`app/core/constants.py:156`)이고 §2.2.1 의 파생 술어 `isBiddable(notice, now)` 가 정확히 그 자리다. **전략 필터의 전건이지 전략의 상태가 아니다** |
| §2.2.2 `DecisionState`(`Proposed`·`UnderReview`·`Submitted`·`Skipped`) | **부분.** legacy 의 `action`(`bid_now`/`review`/`skip`)은 §2.2.2 가 `Recommendation = Verdict`(시스템 산출)로 분리한 축이고 `DecisionState` 가 아니다. §2.2.2 가 *"권고 어휘(`bid_now`)와 제출 어휘(`submit`)가 다른 문자열인데 둘 다 `action` 이라는 같은 이름의 필드로 나른다"* 를 이미 적는다 |
| §2.2.3 `TenderOutcome` / §2.2.4 `AwardOutcome` | **없음** — 개찰·정산 축 |
| §2.2.5 outbox | **없음** — 어휘 자리만, `OPEN-OPS-10` 소유 |
| 축 A(run) · 축 B(lineage) · 축 C(스냅샷) | **§2.2 에 대응 행이 하나도 없다.** 승인 명세에 이 세 축의 상태 어휘가 선언된 자리가 없다 |

**`uncovered_axes` 가 이 공백을 이미 등재한다** — *"axis: 상태 전이표 · 정산 관측 시각 · 성숙도 /
domain: (미착수) / … 이 slice 는 이 축에 손대지 않았다"*, 소유 `OPEN` 은
`OPEN-SET-04`·`05`·`06`·`10` 넷이다(그러나 그 넷은 **정산 축**이라 전략 상태의 소유자가 아니다).

### 2.4 순수 판정 가능성

| 전이 | 입력만으로 판정 가능한가 | 근거 |
| --- | --- | --- |
| 축 A `queued → running → completed/failed` | **예** — (현재 상태, 이벤트) → 다음 상태. 값만으로 표현된다 | 대입 넷(`runs.py:226,239,275,339`)에 조건이 없다 |
| 축 A 의 「직전 **완료** run」 선택 | **아니다** — DB 질의(`runs.py:262-266`, `status == "completed"` + `created_at desc`) | STR-08 의 중복 억제 입력이 **실행 이력의 부작용**이다 |
| 축 B stage 진행 | **예**(순서는 고정) — 그러나 예외 경로가 표 밖 문자열을 만든다 | `execution.py:144` |
| 축 C `idle ↔ running ↔ failed` | **아니다** — 클레임이 `UPDATE … rowcount` 라 **DB 원자성이 판정의 일부**다 | `preview_snapshot.py:126-140` |
| 축 C `stale` 판정 | **부분 순수** — `_strategy_changed_since_scan` 은 (스탬프, 현재 `updated_at`) 두 값의 비교라 **순수**하다. `_computed_age_exceeds` 는 **`now()` 와 설정**을 읽어 순수가 아니다 | `:426-458` |
| 축 C 회수(고아 `running`) | **아니다** — 시계 + 설정 창 | `:98-140` |

→ **1E 가 순수 커널로 가져갈 수 있는 것**: 축 A 의 (상태, 이벤트) → 상태 표,
축 C 의 「전략 스탬프 대 현재 값」 비교, 축 B 의 stage 순서. **못 가져가는 것**:
단일비행 클레임, 고아 회수, 「직전 완료 run」 선택, 시간 경과 stale.

---

## 3. (c) 회귀 사례

`R-STR-*` 는 없다(MISSING-ANCHOR). 전략 축을 실제로 받는 ledger 항목 둘과, 조사 중 확인한
capability-map 등재 회귀 셋을 함께 낸다.

| id | 무엇이 깨졌나 | 재현 fixture | 1E 커널이 잡는가 |
| --- | --- | --- | --- |
| `R-BASIS-01` | 운영자 예산 필터가 **추정가격**과 비교. 경계 근처 공고가 갈리고 운영자에게는 「없는 것」과 구별되지 않는다 | **없다.** ledger 의 「검증 방법」이 요구하는 basis 태그 상이 쌍이 `money-basis-003` 인데 그 case 는 `insufficient-evidence` 이고 `OPEN-1BC-STR16`(검색 경로를 나르는 타입 부재)에 막혀 있다 | **잡을 수 있다** — 예산 필터를 `BaseAmount` 대 `BaseAmount` 비교로 두면 `compareKnownVat` 오버로드가 basis 교차를 **컴파일 시점에** 막는다(§4.3). fixture 없이도 컴파일 fixture 로 표현 가능 |
| `R-BASIS-02` | 검색 API(`GET /projects`)의 `budget_min/max` 가 같은 결함. 파라미터 설명이 내부 컬럼명을 노출할 뿐 basis 를 말하지 않는다 | 같음(`money-basis-003`) | **1E 밖.** 검색 경로는 `STR-16` 소유이고 계약은 API 층(M2 이후)이다. 1E 는 **감시 경로 쪽 타입만** 세운다 |
| STR-07 무효화 누락 | 온보딩 확정이 스냅샷 재계산을 디스패치하지 않는다. 같은 결함이 `5fd658c` 리뷰에서 한 번 지적됐고 새 write 경로마다 재발 | 없음 | **부분.** 「전략이 바뀌면 스냅샷이 stale」이라는 **판정**은 순수 술어로 잡을 수 있다(축 C, §2.4). 「모든 write 경로가 이벤트를 낸다」는 배선 불변식이라 **M3/M4** 다 |
| STR-08 중복 억제 | 억제 기준이 「직전 **완료** run payload 의 존재」라는 부작용. 직전 run 이 실패·취소면 전부 재발송 | 없음 | **1E 밖.** 억제 입력을 선언된 정책으로 만드는 것은 run 이력·알림함(NOTI-10)이 필요하다 — M3/M4 |
| STR-09 스케줄 파라미터 | 스케줄 실행이 요청 파라미터를 전부 **설정값**에서 만들어 운영자 전략의 limit/high_priority 를 무시한다(`strategy_scheduler.py:48-58`) | 없음 | **부분.** 「트리거는 언제만 정하고 무엇을은 전략이 정한다」를 **타입으로** 표현하면(트리거 출처와 전략 값을 다른 타입에) 1E 가 구조를 잡는다. 실제 스케줄 배선 검증은 M4 |
| STR-05 degrade-to-empty | `category_priority_overrides` 파싱 실패가 조용히 `{}` — 정책이 소리 없이 사라진다 | 없음 | **잡을 수 있다** — `Fact`/`Resolution` 어휘로 「파싱 실패」를 사유 있는 값으로 낸다. 다만 STR-05 는 `후속` 분류라 1E scope 인지 `OPEN` |
| STR-06 상한 결합 | 표시 상한이 ML 분석 예산(`limit × 12`, `[30,250]`)을 결정 | 없음 | **잡을 수 있다** — 두 값을 다른 타입/다른 소유로 분리. 「독립적으로 관측된다」가 acceptance |
| STR-15 무승인 갱신 | 실험이 성공하면 전략 임계를 **직접 갱신**한다(`lifecycle.py:285`·`:346`). 가드는 `outcome == "success"` 와 `force` 뿐 | 없음 | **1E 밖** — `OPEN-STR-04` 활성, 담당은 **M4 4A** |

**측정치 하나만 있다**: watch 통과율 — 열린 공고 5,382건 중 43건(≈0.8%), commit `9efcf1c`.
측정일이 문서에 없다(capability-map §11 이 *"(측정일 미기재)"* 로 등재).

---

## 4. (d) 재사용 조사

### 4.1 JVM 라이브러리 — **없음**

| 후보 갈래 | 판정 | 이유 |
| --- | --- | --- |
| 규칙 엔진(Drools · Easy Rules · Evrete) | **불가** | `config/quality/architecture-policy.properties` 의 `external.allowed.domain` 이 **`kotlin-stdlib` + `org.jetbrains:annotations` 둘뿐**이다. domain 모듈에 어떤 좌표도 더할 수 없다 |
| 상태 기계(Spring StateMachine · Tinder StateMachine · akka-fsm) | **불가 + 불필요** | Spring 계열은 `group.forbidden` 에 명시. 그리고 §2 가 낸 표는 상태 5·이벤트 4 규모라 sealed + `when` 이 더 짧고 소진 검사가 공짜다 |
| 검증(Konform · Valiktor · jakarta.validation) | **불가** | `jakarta.validation` 은 `group.forbidden`. Konform 은 외부 좌표라 allow-list 밖 |
| 텍스트 매칭(Aho-Corasick · Lucene) | **불필요** | 규칙이 `contains` 부분문자열이고 `OPEN-STR-05` 가 **부분문자열 유지**로 확정됐다. 어휘 규모가 31개(`MARINE_REQUIRED_KEYWORDS`) 수준 |

→ **1E 는 라이브러리를 도입할 수 없고 도입할 필요도 없다.** 1D 조사가 같은 결론에 이르렀다.

### 4.2 legacy 재활용 후보 (순수 함수만)

| legacy | 이식 가치 | 주의 |
| --- | --- | --- |
| `filters.py:100-111` `_matched_terms` | **높음** — 중복 제거 + 사용자 순서 보존 + lower 정규화가 한 자리 | Kotlin `String.lowercase()` 는 `Locale` 을 참조해 아키텍처 T-C 에 걸린다(1C 실측, milestone-1 1C 문단). 1C 가 코드 쪽에서 푼 관례를 따른다 |
| `filters.py:77-98` 두 텍스트 빌더 | **개념만** — 두 텍스트 범위의 구분은 유지, **호출 규율 대신 타입**으로 | STR-02 「V2 대체」가 그렇게 요구한다 |
| `allocation_core.py:44-58` `AllocationThresholds.from_settings` | **높음** — persisted + settings 를 경계에서 한 번 모아 커널을 I/O-free 로 만드는 형태 | 1E 는 이것을 「정책 데이터 주입」으로 바꾼다(1C·1D 의 `EffectiveDatedPolicy` 관례) |
| `allocation_core.py:126-181` `decide` 사다리 | **구조만** — first-match 순서와 네 분기 | `reasons` 한국어 문장은 **폐기**(§3.1 *"사람이 읽는 문장은 렌더링 시점에 생성하고 영속하지 않는다"*) |
| `watch_rules.py:28-35` `WATCH_RULE_*_FIELDS` | **높음** — 「게이트가 있는가」의 대상 목록을 **데이터로** 선언한 것 | 그러나 `candidates.py:305-329` 의 두 번째 목록과 통합해야 한다 |
| `preview_snapshot.py:426-451` 전략 스탬프 대조 | **높음** — 시간 TTL 과 독립된 「스캔이 편집에 눈멀었다」 판정 | 순수 부분만(`_strategy_changed_since_scan`). `_computed_age_exceeds` 는 시계 |
| `operator_strategy_tuning.py:35-47` clamp 둘 | 중간 | clamp 범위는 매직넘버 — 정책 데이터로 외부화해야 한다 |
| `scripts/seed_marine_gate.py:60-79` 제외 어휘 주석 | **fixture 후보** — 관찰된 오탐 여섯(해양·수산→기관명, 해상→해상보험, 매립→폐기물매립지, 등대→등대운영관리, 수중→수중생태조사, 준설→하수관로/저수지) | 어휘 자체는 `authoritative` 근거가 아니다 — legacy 주석이다 |
| `tests/test_strategy_keyword_filter.py` | **회귀 케이스 원본** — `_apply_strategy_filters` 를 DB 없이 부르는 순수 단위 테스트 | 기대값은 legacy 동작이지 정답이 아니다 |

### 4.3 V2 에 이미 있는 것 — 중복 위험과 재사용

| V2 자산 | 1E 와의 관계 |
| --- | --- |
| `Money` sealed + `BaseAmount`(shared-kernel) | **예산 필터의 정답 형태.** `OPEN-STR-01` 이 「값 + basis 태그」를 요구하고 `BaseAmount` 가 그 자체다 |
| `compareKnownVat(left: BaseAmount, right: BaseAmount): Fact<Int>` | **비교의 유일한 공개 경로.** 타입별 오버로드 여섯이라 `BaseAmount` 대 `EstimatedAmount` 는 **어떤 오버로드에도 맞지 않아 컴파일되지 않는다** — `R-BASIS-01` 을 구조적으로 막는다. ⚠ 전건 둘: `Provenance.Undeclared` 면 `UNDECLARED_PROVENANCE`, VAT 가 다르거나 `UNKNOWN` 이면 `VAT_TREATMENT_MISMATCH` (§7.2) |
| `Money.amount` 가 `internal` | shared-kernel 밖에서 원 단위 값을 읽을 수 없다. 주석이 *"`R-BASIS-01` 컴파일 차단의 필요조건"* 이라 적는다. `export(): AmountRecord` 가 유일한 공개 export |
| `Fact<T>`(`Known`/`Absent(reason)`) · `Measurement<T>` | validation 결과·부재 표현의 기존 어휘. `orElse`·`getOrDefault` 가 **없어** 접기가 불가능 |
| `Resolution<T>`(`Resolved(value, version)`/`NotApplicable(reason)`) + `EffectiveDatedPolicy<T>` | 1C `LICENSE_QUALIFICATION_POLICY` 가 쓴 정책 배관. 1E 의 임계·밴드·clamp 범위가 같은 자리에 들어간다 |
| `ReasonCode` enum(11 값) | 1E 가 사유를 여기에 더할지, 축을 나눌지가 `OPEN` — 1D 가 `FloorUnmeasurableReason` 을 **별도 축**으로 두었다(decision 28 계열). 어휘 소유는 DEC-06 |
| 1C `UncertainReason` sealed · `LicenseVerdict` | **중복 위험 없음** — 자격 축이고 `strategy` 는 `qualification` 을 참조할 수 없다(ADR 0006 D-4). 다만 `capacity-gate-001` 의 기대값이 `RequirementDataAbsent` 를 쓴다 = 그 도메인은 **1C 어휘**다(§5.3) |
| 1D `ProvenancePolicyData`·`FloorShortfallPolicyData` | 형태 선례 — 정책 데이터를 모듈 안 `*PolicyData.kt` 에 두고 `EffectiveDatedPolicy` 로 감싼다 |
| `Rate` + 축 타입 넷 | **1E 의 임계에는 맞지 않는다.** 축 넷은 `AssessmentRate`·`AwardRate`·`FloorRate`·`BidRate` 이고 「매칭 점수」·「우선순위 점수」 축이 없다. `Rate.ofFraction` 은 공개지만 축 없는 raw `Rate` 를 점수에 쓰는 것이 옳은지는 `OPEN`(§7.3) |

---

## 5. (e) fixture 어휘 대조

### 5.1 case 인벤토리 — **1E 가 실행할 authoritative 는 최대 1건이고, 전략 축은 0건이다**

| id | 도메인 | 분류 | `source.kind` | `verified_paths` |
| --- | --- | --- | --- | --- |
| `verdict-001` | verdict | insufficient-evidence | `m0-derived-rule` | 없음 |
| `verdict-002` | verdict | insufficient-evidence | `m0-derived-rule` | 없음 |
| `verdict-003` | verdict | insufficient-evidence | `m0-derived-rule` | 없음 |
| `verdict-004` | verdict | insufficient-evidence | `operator-decision` | **걷혔다**(2026-09-02) |
| `capacity-gate-001` | capacity-gate | insufficient-evidence | `m0-derived-rule` | 없음 |
| `capacity-gate-002` | capacity-gate | insufficient-evidence | `m0-derived-rule` | 없음 |
| `capacity-gate-003` | capacity-gate | **authoritative** | `operator-decision` | `$.qualificationVerdict` · `$.awardedContractLimitEmittedAsQualification` · `$.suitabilityAxisAffected` |
| `capacity-gate-004` | capacity-gate | insufficient-evidence | `m0-derived-rule` | 없음 |

**전략·watch 도메인의 fixture 는 0건이다.** `contract_binding` 은 여덟 case 모두 없다.
`verified_projections` 도 없다.

**`verdict` 도메인은 authoritative 가 0 이다.** `uncovered_axes` 가 그 자리를 축어로 적는다 —
*"승인 부재로 authoritative 덮개를 잃은 축 — verdict (`SkipReason` 구체 어휘) … 지금 이 축에
`authoritative` 덮개가 없다"*. 막는 것은 미결이 아니라 **운영자 승인 부재**이고,
`unblocks_when` 이 요구하는 것은 `CapacityHold`·`LowPriority` 두 어휘(+ `verdict-003` 은
`BidNow` 와 `OPERATOR_STRATEGY_FLAG`)의 **명시 승인**이다.

**`capacity-gate-003` 은 전략 축이 아니다.** 그 case 의 근거는 `OPEN-QUAL-08` 분할 확정
축어이고, 입력의 `licenseVerdict`·기대값의 `qualificationVerdict` 는 **1C 의 `LicenseVerdict`
어휘**다(`Eligible`). `capacity-gate-001` 의 `uncertainReason: "RequirementDataAbsent"` 도
1C `UncertainReason` 의 실재 variant 다. 즉 **capacity-gate 도메인은 qualification 축이고,
1E 가 그것을 `TARGET_DOMAINS` 에 넣으면 자격 축 코드를 strategy 모듈에서 실행하게 된다.**
→ 소유 slice 재확인이 필요하다(`OPEN`, §7.5).

### 5.2 어휘 불일치 목록 — **여덟**

1. **`skipReason: "CapacityHold"` / `"LowPriority"`** — `data-dictionary.md` §3.6 의 sealed
   `SkipReason` variant 이름. **V2 코드에 `SkipReason` 도 `Verdict` 도 아직 없다.** 그리고
   그 두 어휘 자체가 **미승인**이라 골든으로 고정할 수 없다(위 `uncovered_axes` 축).
2. **`verdict: "Skip"` / `"BidNow"`** — §3.6 sealed `Verdict` variant 이름. legacy 는
   `"skip"`·`"bid_now"`·`"review"` **소문자 스네이크**다(`allocation_core.py:163-176`).
   fixture 는 PascalCase 를 쓴다 — **투영 규칙이 필요하고 명세에 그 규칙이 없다.**
3. **`override.source: "OPERATOR_STRATEGY_FLAG"`** — **M0 산출 문서 어디에도 없는 리터럴**이다.
   `uncovered_axes` 가 *"뒤의 것은 M0 산출 문서에도 없어 **새로 정해야 한다**"* 로 적는다.
   legacy 의 실제 우회 출처는 `settings.ALLOCATION_FORCE_BID_*`(환경 설정)이지
   운영자 전략 플래그가 아니다 — **fixture 의 이름과 legacy 의 기제가 어긋난다.**
4. **`reasonCode: "OverrideOutsidePlausibilityBand"`**(`verdict-004`) — shared-kernel
   `ReasonCode` enum 11 값에 **없다**. 1D 가 `FloorUnmeasurableReason` 을 별도 축으로 둔 것과
   같은 갈림이 여기서도 열린다.
5. **`overrideOutcome: "Rejected"` · `plausibilityBand.inclusivity: "min-inclusive-max-inclusive"`**
   — 경계 포함성을 **문자열**로 나른다. `data-dictionary.md` §1.4.3 이 *"경계 포함성(이상/초과)을
   값과 함께 선언한다"* 를 요구하지만 그 **타입 이름을 정하지 않는다.**
6. **`basis` 값 둘이 enum 밖이다** — `"AWARDED_CONTRACT_LIMIT"`(`capacity-gate-003` 입력) ·
   `"CONSTRUCTION_CAPACITY"`(`-002`·`-004` 입력). shared-kernel `Basis` enum 은 여섯 값
   (`BASE_AMOUNT`·`ESTIMATED`·`YEGA`·`BID`·`ALLOCATED_BUDGET`·`AWARD`)이고 `Money` 구현도
   여섯뿐이라 **도급한도도 시공능력평가금액도 나를 `Money` 타입이 없다.** 같은 입력이
   `evaluationYear: 2025`(기준 시점)도 나르는데 그 축은 활성 `OPEN-DIC-02`(시공능력평가액
   공시의 갱신 주기가 역년 경계인가) 소유다. 그 case 들을 실행하려면 basis 둘 + `Money` 구현 둘을
   세우거나, 그 값을 `Money` 가 아닌 다른 타입으로 나른다.
7. **판정 어휘 넷이 더 있다** — `capacityAxisVerdict: "Neutral"` · `overallQualificationVerdict: "Eligible"` ·
   `otherAxisVerdictChanged`(`capacity-gate-004`) · `operatorHoldingState: "Known"`(`-002`).
   `Neutral` 은 `LicenseVerdict` 셋(`Eligible`·`Ineligible`·`Uncertain`)에 **없는 넷째 값**이고,
   「축별 판정」과 「종합 판정」을 두 자리로 나르는 형태도 1C 의 `LicenseJudgement` 봉투에 없다.
8. **감사 boolean** — `operatorHoldingFoldedToZero` · `treatedAsShortfall` ·
   `awardedContractLimitEmittedAsQualification` · `suitabilityAxisAffected` ·
   `renderedSentencePersisted` · `distinguishableWithoutSentence` · `overrideHidden` ·
   `silentlyDropped` · `silentlyAccepted` · `observableInResult`. **전부 「이 규칙을 위반하지
   않았다」를 말하는 부정형 boolean 이고 명세의 어느 서명에도 없다.** 1C 불일치 3·1D 불일치 9 와
   같은 갈래다. 그중 `awardedContractLimitEmittedAsQualification`·`suitabilityAxisAffected` 는
   `capacity-gate-003` 의 `verified_paths` 에 들어 있어 **산출이 반드시 내야 한다.**

추가 관찰(불일치는 아니나 계약에 영향):

- `verdict-003` 입력의 `declaredBy: "SYN-OPERATOR-0001"` 는 **운영자 식별자 자리**다.
  `OPEN-STR-07`(단일 회사) 결정과 함께 읽으면 이 필드가 V2 에 필요한지가 열려 있다.
- `verdict-004` 의 `plausibilityBand` note 가 *"SYNTHETIC — legacy B6 값이 아니다. 이 case의
  기대값은 밴드 값이 아니라 「밖이면 사유와 함께 거부」 규칙에만 의존한다"* 라고 적는다 —
  **밴드 값(`0.25`~`0.99`)을 코드에 넣으면 안 된다.**
- **`capacity-gate-001` 과 `-002` 의 `uncertainReason` 은 같다** — 둘 다
  `"RequirementDataAbsent"` 다(기대값 파일 실측). 그런데 `-002` 의 `expected_reasoning` 은
  *"비교 결과는 두 case 모두 `Uncertain`이지만 **사유가 다르다**"* 라고 적는다. 실제로 다른 것은
  `uncertainReason` 이 아니라 **형제 case 가 서로 다른 필드 집합을 낸다**는 것이다 —
  `-001` 은 `operatorHoldingFoldedToZero`·`treatedAsShortfall`, `-002` 는
  `operatorHoldingState`·`operatorHoldingAmount`·`distinctFromAbsent`. 1D 불일치 4
  (형제 case 가 같은 값을 다른 깊이에 둠)와 같은 갈래이고, 여기서는 **깊이가 아니라 필드 집합이
  갈린다.** 둘 다 `insufficient-evidence` 라 계약에는 걸리지 않는다.

### 5.3 corpus runner 에 미치는 영향

`SharedKernelCorpusConformanceTest` 는 `TARGET_DOMAINS ∩ classification == "authoritative"`
만 실행하고, `dispatch 표 밖의 authoritative case 가 없다` test 가 누락을 막는다.
`gate-tests.properties` 의 `gate.tests.app` 이 그 class 의 실행을 단언한다.

- **`verdict` 를 `TARGET_DOMAINS` 에 넣으면 대상이 0건이다** — 표에 아무것도 더하지 않고
  통과한다. 「넣었는데 아무것도 안 돈다」가 조용히 성립하므로, 1E 는 **넣지 않고 그 사실을
  scope 에 선언하거나**, 넣더라도 「0건임을 단언하는 test」를 함께 둔다(1B-c·1C 의
  「insufficient-evidence 이월은 …뿐이다」 test 관례).
- **`capacity-gate` 를 넣으면 `-003` 1건이 돈다** — 그러나 그 executor 는 자격 어휘를 낸다(§5.1).

---

## 6. (f) `OPEN-STR-*` — 1E 를 막는가

| id | 상태 | 내용 | 1E 를 막는가 |
| --- | --- | --- | --- |
| `OPEN-STR-01` | **해소**(2026-08-26) | (c) + 기초금액 — basis 를 1급 필드로 승격하고 값·태그를 함께 저장·비교. 기존 값은 기초금액으로 해석 | **막지 않는다. 오히려 1E 의 근거다** — 예산 필터를 `BaseAmount` 비교로 만드는 결정이 이미 서 있다 |
| `OPEN-STR-07` | **해소**(2026-08-26) | 단일 회사. 다중 operator 를 V2 범위에 두지 않는다 | **막지 않는다.** 다만 하류 주의가 있다 — *"per-operator 스키마를 단일 회사 전제로 단순화할지는 별개 설계 결정이며 0C·M1 소관"*. `data-dictionary.md` §2.1 이 *"소유권 경계 자체는 타입으로 유지한다"*(근거: commit `fc291c7`)를 요구하므로 **1E 가 operator 식별자를 지울 수는 없다** |
| `OPEN-STR-02` | **활성** | 온보딩 확정 직후 미리보기 노출 창의 실제 크기. **미측정**, 담당 slice 미지목(M4/M6) | **막지 않는다** — 측정치이지 규칙이 아니다. 1E 의 stale 술어는 창 **크기**에 의존하지 않는다(전략 스탬프 비교는 순수, §2.4) |
| `OPEN-STR-04` | **활성** | 실험이 운영자 전략을 무승인 갱신하는 현행이 의도인가. 담당 **M4 4A**, 답할 주체 = 운영자 | **막지 않는다(범위 밖).** 다만 1E 가 「전략 값의 마지막 변경 주체」를 타입에 넣을지가 이 결정에 걸린다 — 넣으면 미결을 선점하고, 안 넣으면 M4 가 타입을 넓힌다. **`OPEN` 으로 남긴다** |
| `OPEN-STR-12` | **활성** | 대화형 채널(Telegram) 전략 편집을 V2 capability 로 채택할 것인가. 담당 **M4 4A**, 답할 주체 = 운영자(범위) | **막지 않는다.** 1E 는 편집 **유스케이스**가 아니라 값 타입과 validation 을 만든다. B-10 이 *"채널 채택 여부와 무관하게 편집 유스케이스는 필요하다"* 로 이미 갈랐다 |

인접 활성 `OPEN` 둘도 1E 를 스친다:

- **`OPEN-DIC-03`**(`SkipReason` 어휘의 전수성) — `verdict` 축의 전수성을 소유. 그러나
  `uncovered_axes` 가 *"전수성 이전에 어휘 자체가 미승인이고, 두 축은 이제 겹쳐 있다"* 로 적는다.
- **`OPEN-DEC-05`**(해소) — 「하나의 `Skip` + **필수** reason code」는 결정 기록에 실재한다.
  **규칙은 서고 어휘만 미승인**이다.

---

## 7. 구조적 막힘

### 7.1 `strategy` 모듈은 앵커뿐이고 배선은 1C·1D 복제로 충분하다

`strategy/src/main/kotlin/bidvector/strategy/ModuleBoundaryAnchor.kt` 하나(`internal class`).
`strategy/build.gradle.kts` 는 `plugins { id("bidvector.kotlin-conventions") }` 두 줄이다.

`qualification/build.gradle.kts` 와 `decision/build.gradle.kts` 는 **동일**하다 —
`implementation(project(":shared-kernel"))` + `testImplementation(libs.kotlinx.coroutines.core)` +
`kotest.proptest.default.seed` 시스템 프로퍼티. `strategy` 도 domain 층이라
`layer.domain.shareable=shared-kernel` 하나만 걸 수 있다(ADR 0006 D-4). **그대로 복제한다.**

`app/build.gradle.kts` 쪽 배선(corpus runner 가 `strategy` 를 보게 하는 것)은 1C·1D 가
`qualification`·`decision` 에 대해 한 것과 같은 자리다 — 다만 §5.3 의 판단이 먼저다.

### 7.2 `compareKnownVat` 의 전건 둘 — **예산 필터를 막는다**

`compareKnownVat` 의 성공 조건(`Money.kt` `compareSameType`):

1. 양쪽 `provenance != Provenance.Undeclared` — 아니면 `Fact.Absent(UNDECLARED_PROVENANCE)`
2. `sameKnownVat(left, right)` = `left == right && left != UNKNOWN` — 아니면 `Fact.Absent(VAT_TREATMENT_MISMATCH)`

운영자가 입력한 예산 하한/상한의 `vatTreatment` 는 **무엇인가.**
`data-dictionary.md` §1.2.1 이 *"선언을 만들 수 없으면 `Unknown`"* 이고
*"`Unknown` 금액은 다른 과세 처리의 금액과 산술 비교에 들어갈 수 없다(타입 차단)"* 라고 적는다.
곧 **운영자 예산 값을 `UNKNOWN` 으로 두면 예산 필터는 항상 `Absent` 를 내고 판정이 성립하지 않는다.**

세 갈래가 있고 **1E 가 고를 문제다**:
(a) 운영자 입력 UI 가 과세 처리를 함께 받는다 — 그러면 `INCLUSIVE`/`EXCLUSIVE` 를 실을 수 있다.
(b) 기초금액의 정의상 기본값 `INCLUSIVE`(U-1b)를 운영자 입력에도 적용한다 — 그러나 §1.2.1 이
    *"legacy 유래 행은 정의상 기본값으로 **자동 태깅하지 않는다**"* 라고 적는다(legacy 유래 행 한정이라
    신규 운영자 입력에 걸리는지는 문면이 답하지 않는다).
(c) 예산 필터의 결과 타입 자체가 `Fact<…>` 라 `Absent(VAT_TREATMENT_MISMATCH)` 가 **정상 산출**이다.
→ **`OPEN`** — 조사가 판정하지 않는다.

`provenance` 쪽은 막힘이 아니다 — `Provenance.OperatorDeclared` 가 이미 있고
`capacity-gate-003` 입력이 그것을 쓴다.

### 7.3 점수 축의 타입이 없다

`minimum_match_score`·`minimum_probability_score`·`bid_now_threshold`·`review_threshold`·
`priority_score` 는 전부 `[0,1]` 실수인데:

- `Rate` 의 축 타입 넷(`AssessmentRate`·`AwardRate`·`FloorRate`·`BidRate`)에 **점수 축이 없다**.
  `data-dictionary.md` §1.4.2 는 *"한 축의 율을 다른 축에 대입할 수 없게 타입을 나눈다"* 이고
  점수는 율이 아니다.
- `Rate.ofFraction` 은 공개 companion 이라 raw `Rate` 로 감쌀 수는 있다. 그러나 `Rate` 는
  **축을 나르지 않으므로** 매칭 점수와 확률 점수가 같은 타입이 되어 서로 대입된다 —
  §1.4.2 가 막으려는 바로 그 형태다.
- `config/quality/api-type-policy.properties` 가 `kotlin.Double`·`Float`·`Number` 를
  **public domain API 에서 금지**한다. 그래서 `Double` 로 두는 선택지가 없다.
→ **1E 가 점수 타입을 신설해야 한다**(예: `Score` 값 타입 + 축별 뉴타입). `shared-kernel` 에
   둘지 `strategy` 에 둘지는 `OPEN` — 1D 가 `FloorShortfall` 을 **자기 모듈 소유 sealed** 로 둔 선례가 있다.

### 7.4 텍스트 입력 타입을 `strategy` 가 스스로 만들어야 한다

watch 규칙의 입력은 공고 텍스트 넷(title·description·requirements·category)인데
**`procurement` 는 아직 앵커뿐이고**, 설령 채워져도 `strategy` 는 `procurement` 를
참조할 수 없다(ADR 0006 D-4). → 1E 는 `strategy` 모듈 안에 자기 입력 타입을 정의한다.
STR-02 의 「V2 대체」가 요구하는 **작업 서술 텍스트 / 행정 메타데이터 텍스트의 타입 분리**가
정확히 그 자리다. 중복이 아니라 **경계다** — 다만 M3 에서 `procurement` 의 `Notice` 가 서면
그 둘을 잇는 매핑이 `workflow`/`adapters` 에 생긴다.

### 7.5 corpus 도메인 소유 — `OPEN`

`capacity-gate` 4 case 는 근거·어휘가 전부 **자격 축**이다(§5.1). 1C 는 이 도메인을
`TARGET_DOMAINS` 에 넣지 않았고 `_workspace/m1-1c/` 노트에도 언급이 없다(grep 매치 0).
**어느 slice 가 `capacity-gate` 를 소유하는지가 문서에 없다.** `uncovered_axes` 는 그 축의
미해결을 `OPEN-QUAL-09`·`OPEN-QUAL-10`·`OPEN-DIC-02` 로 돌린다 — 전부 자격 축 `OPEN` 이다.

### 7.6 shared-kernel 확장이 필요한가 — **현재 조사로는 필요 없다**

1D 가 발견한 종류의 막힘(`Rate.fraction` `internal` 읽기)에 해당하는 것을 1E 축에서는
찾지 못했다. `Money.amount` 는 `internal` 이지만 **1E 는 원 단위 값을 읽을 필요가 없다** —
`compareKnownVat` 가 비교를 내주고, 렌더링은 표현 계층이다. §7.2·§7.3 의 둘은
「shared-kernel 을 열어야 하는가」가 아니라 **「새 타입을 어디에 세우는가」** 의 문제다.

---

## 8. 게이트 영향

| 게이트 | 정책 | 1E 가 걸릴 자리 |
| --- | --- | --- |
| `api.forbidden.types` | `Double`·`Float`·`DoubleArray`·`FloatArray`·`Number`(+ 박싱) 를 public domain API 에서 금지 | **높다.** 임계·점수·가중치가 전부 실수 축이다. `BigDecimal` 백킹 값 타입이 필요하다(§7.3) |
| `limit.type.members=30`(main 한정, PSI 기준) | 주 생성자 `val` + 본문 프로퍼티 + 함수 + 보조 생성자 + `init` | **높다.** `OperatorStrategy` 를 필드 15개짜리 한 타입으로 옮기면 **주 생성자만 15** 이고 validation·접근자가 붙으면 30 에 근접한다. **축별로 쪼개는 것이 게이트가 요구하는 방향**(watch 규칙 / 임계 / 알림 / 표시 상한) |
| `ratchet.type.inheritance-depth.max=0` · `ratchet.type.interfaces.max=1` | 바이트코드 기준, main 한정, **증가 금지** | **중간.** 현재 저장소 전체 상속 깊이가 0 이다 — sealed **인터페이스** + `data class`/`data object` 만 쓰면 안전하다. sealed **클래스** 계층이나 인터페이스 둘을 함께 구현하면 래칫을 넘고 그 편집은 ADR 0007 개정 대상이다 |
| `limit.file.lines=500` · `limit.function.lines=50` | 본문 갖는 선언 전부(람다·접근자·`init` 포함) | **중간.** watch 규칙 일곱 + 임계 사다리 넷을 한 함수에 넣으면 50 을 넘는다. rule table 로 분리하면 데이터는 파일 축만 받는다 |
| CPD `mode=fail`, `fail.source-sets=main`, `minimumTokenCount=50` | main 중복이 build 를 깨뜨린다 | **높다.** §1.2 가 보인 「같은 규칙 네 자리」를 1E 가 그대로 옮기면 **곧바로 걸린다.** 반대로 게이트가 legacy 의 핵심 결함을 구조적으로 막아 준다 |
| 아키텍처 T-C(`java.lang`·`java.util` 클래스 allow-list) | 목록에 없으면 금지. `java.util.Locale` 은 `effect.surface.classes` | **높다.** `String.lowercase()`(무인자)가 `Locale` 을 참조해 1C 가 실제로 걸렸다. **키워드 정규화가 이 게이트를 정면으로 만난다** — 1C 가 코드 쪽에서 푼 관례를 따른다 |
| 아키텍처 T-B(정확 패키지) | `java.util.regex` 가 **없다** | **높다.** 정규식을 못 쓴다. 다행히 legacy 도 `in` 부분문자열이라 필요 없다 |
| `package.segment.forbidden` | `util,utils,helper,helpers,impl,common,misc,base,service,manager,dto,vo` | 중간 — `bidvector.strategy.service` 같은 이름을 못 만든다 |
| `module.expected-source-sets=main,test` | `testFixtures` 금지 | 낮음 |
| detekt(`buildUponDefaultConfig=true`) + 명시 활성 | `CognitiveComplexMethod`·`ComplexInterface`·`MethodOverloading`·`TooGenericExceptionCaught`·`SwallowedException`·`UnsafeCallOnNullableType` + 기본값의 `TooManyFunctions` | **중간~높다.** 1C 가 `TooManyFunctions` 에 걸렸다(milestone-1 1C 문단). `ComplexInterface` 는 멤버 많은 sealed 인터페이스에 걸린다 |
| `external.allowed.domain` = kotlin-stdlib + annotations | domain main 의존 둘뿐 | 라이브러리 도입 불가(§4.1) |
| `gate.tests.strategy`(신설 필요) | `gateExecutionGate` 가 읽는다. 줄이 없는 모듈은 단언 대상 없음 | 1E 가 커널 test 를 등재해야 실행 증거가 선다 |

---

## 9. 결론 — 「1E 계약이 답해야 할 것」 다섯에 대한 조사 사실

### (1) 커널 경계

- **순수하게 표현 가능**: watch 규칙 일곱(카테고리 완전일치 · 지역/키워드 부분문자열 OR ·
  제외 · 예산 상하한), 액션 사다리 넷(first-match), 분석 후 임계 둘, 값 validation 넷,
  카테고리 가중, 표시 상한 clamp, run 상태 전이표, 「전략 스탬프 대 현재 `updated_at`」 비교.
- **순수하지 않음**: 스냅샷 단일비행 클레임(DB rowcount), 고아 회수(시계+설정),
  「직전 완료 run」 선택(질의), 분석 예산(`settings`), 시간 경과 stale.
- legacy 도 그 선을 이미 그어 두었다 — `filters.py` 모듈 docstring *"(no scoring, no ML, no DB)"* ·
  `allocation_core.py` *"so `decide` stays I/O-free"*.
- **조사 사실이 가리키는 방향**: 커널 경계를 legacy 의 두 자리(`_apply_strategy_filters`·`decide`)와
  같은 곳에 두면 이식 위험이 가장 작고, 축 C(스냅샷)는 순수 술어 하나만 떼어 온다.

### (2) validation 결과 타입

- legacy 는 **같은 규칙을 네 자리에, 실패 거동 세 가지(예외/문자열/조용한 clamp), 경계 두 가지
  (`≤ bid_now` 대 `≤ bid_now − 0.01`)로** 구현했다(§1.2). capability-map STR-03 이
  *"편집 경로마다 불변식을 재구현하는 형태는 채택하지 않는다"* 를 이미 못 박는다.
- V2 에 이미 있는 결과 어휘는 `Fact<T>`(`Known`/`Absent(reason)`) · `Measurement<T>` ·
  `Resolution<T>` 셋이고, 셋 다 **접는 API 가 없다.**
- `milestone-1.md` 「구현 규칙」이 *"business control flow 에 exception 을 쓰지 않는다"* 이므로
  legacy 의 `HTTPException` 경로는 그대로 옮길 수 없다.
- **방향**: 결과 타입을 하나 정하고 편집 경로가 그것 하나를 통과하게 하면 CPD main-fail 게이트가
  네 자리 재구현을 **구조적으로** 막는다.

### (3) 최소 state/event 기준

- 승인 명세(`data-dictionary.md` §2.2)에 **전략·스냅샷·감시 run 축의 상태 어휘가 없다**(§2.3).
  `uncovered_axes` 의 「상태 전이표」 축은 `(미착수)` 이고 소유 `OPEN` 넷은 전부 정산 축이다.
- legacy 실물은 축 셋 — run 5값(그중 `cancelled` 는 **어휘만, 대입 없음**) · lineage stage 5값 +
  status 2값(예외 경로가 표 밖 문자열 둘을 더 만든다) · 스냅샷 3값 + 파생 술어 `stale`.
- `v2-지침서.md` §4.5 는 *"상태 전이는 명시적 state/event table 로 표현한다"* 만 요구하고
  **어느 축을 M1 에서 세우는지는 말하지 않는다.**
- **방향**: 「필요한 최소」의 하한은 **1E 자신이 만드는 판정의 상태**뿐이다 — 감시 run 과
  스냅샷은 M3/M4 의 실행 축이라 지금 세우면 미결(`OPEN-SET-*`·`OPEN-OPS-10`)을 선점한다.

### (4) 예산 basis 를 타입으로 나르는 형태

- **이미 있다.** `BaseAmount`(basis 파생값·`copy()` 로 못 바꿈) + `compareKnownVat` 여섯 오버로드
  (LUB 추론이 일어날 자리가 없어 `BaseAmount` 대 `EstimatedAmount` 가 **컴파일되지 않는다**) +
  `Money.amount` 가 `internal`(주석이 *"`R-BASIS-01` 컴파일 차단의 필요조건"* 이라 적는다).
- **미결은 basis 가 아니라 과세 처리다** — 운영자 입력 예산의 `vatTreatment` 가 `UNKNOWN` 이면
  `compareKnownVat` 가 항상 `Absent(VAT_TREATMENT_MISMATCH)` 다(§7.2).
- `OPEN-STR-01` 이 *"기존 저장 값은 기초금액으로 일괄 태깅"* 을 정했으나 **과세 처리는 정하지 않았고**,
  그 축은 활성 `OPEN-REG-05` 가 소유한다.
- **방향**: 타입은 `BaseAmount` 로 확정적이고, 1E 가 실제로 정할 것은 **운영자 입력의 `vatTreatment`
  와 `Provenance`** 그리고 비교 실패가 정상 산출인지 오류인지다.

### (5) authoritative 부재 시 완료 조건 읽기

- `milestone-1.md` 완료 조건은 *"승인된 authoritative corpus 전체 통과"* 다.
- 실측: **`verdict` 0건**, `capacity-gate` 1건(그마저 자격 축 어휘, §5.1). **전략·watch 도메인 0건.**
- 선례: 1B-c·1C·1D 는 각각 「이 축의 insufficient-evidence 이월은 …뿐이다」 test 를 두어
  **비어 있음을 기계로 고정**했다(`SharedKernelCorpusConformanceTest` 의 두 test).
  `dispatch 표 밖의 authoritative case 가 없다` test 가 누락을 별도로 막는다.
- `uncovered_axes` 의 verdict 축 `unblocks_when` 은 **운영자의 어휘 승인 하나**를 지목하고,
  그 승인이 없으면 기대값을 바꾸지 않는 한 case 는 올라오지 않는다.
- **방향**: 「전체 통과」는 **공집합에 대해 공허하게 참**이므로, 그것만으로는 1E 의 완료를 재지
  못한다. 1B-c 이후 세 slice 가 쓴 관례(축의 공집합·이월 목록을 test 로 고정 + 커널 불변식
  test 를 `gate.tests.<module>` 에 등재)가 그 자리를 대신해 왔다.

---

### 이 조사가 판정하지 않은 것 (`OPEN`)

1. 운영자 입력 예산의 `vatTreatment`·`Provenance` 와, 비교 실패(`Absent`)가 정상 산출인가(§7.2).
2. 점수 축 타입을 신설할 것인가, `Rate` 를 재사용할 것인가, 어느 모듈에 둘 것인가(§7.3).
3. `capacity-gate` 도메인의 소유 slice — 어휘가 자격 축인데 1C 가 다루지 않았다(§7.5).
4. `verdict` 축을 `TARGET_DOMAINS` 에 넣을 것인가(대상 0건, §5.3).
5. 1E 가 세울 상태 축의 범위 — run/스냅샷을 지금 세우면 `OPEN-SET-*`·`OPEN-OPS-10` 선점(§9-3).
6. `OPEN-STR-04` 대비 「전략 값의 마지막 변경 주체」를 타입에 넣을 것인가(§6).
7. 1E 가 내는 사유를 `ReasonCode` 에 더할 것인가 별도 축으로 둘 것인가 — 1D 가 `FloorUnmeasurableReason`
   을 별도 축으로 둔 선례가 있고 어휘 소유는 DEC-06 이다(§5.2-4).
8. STR-05(카테고리 가중, `후속` 분류)·STR-06(상한 결합)이 1E scope 인가.
