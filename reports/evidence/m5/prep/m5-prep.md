# M5 준비 — 독립 Python ML engine · slice 지도와 착수 전 결정 후보 (초안, 구현 전)

> **지위**: M2 진행 중에 세션 모델(Fable 5.1)이 단독으로 쓴 **준비 문서**. 구현·설치·gradle 없음. **M5 착수는 M2 proto·provider contract
> 승인 뒤**(`milestone-5.md` 「선행 조건」) 운영자 지시. **M2 와 독립인 것**: 5A(패키지·경계·툴체인 — M2 가 세운 `ml-engine/` 최소 골격을
> 승계), 5B 의 **변환 코드**(wire 는 2B), 5D(순수 수학 커널 이식). 5C 는 2C 의 artifact/evaluation 참조 형태와 dataset reference 에 걸리고
> 5E 는 2B·2C·2D 그 자체.
>
> 근거: `milestone-5.md` · `v2-지침서.md` §1·§3.2·§5「Python ML」·「크기와 결합도」·§9 · `ADR 0001` §4·D-6 · `ADR 0009` D-1~D-6 ·
> `capability-map.md` ML-02~ML-11·§14.2 `OPEN-ML-05`·`OPEN-ML-06` · `data-dictionary.md` §6.3~6.5 · M2 준비(`reports/evidence/m2/prep/m2-prep.md`
> D-M2-3·D-M2-10, `2a/scope.md`, `2b/scope.md` D-2B-1) · 조사 노트 `_workspace/m5-prep/01_scout_ml_package.md`·`02_python_toolchain.md`.

---

## 1. M5 가 로드맵에서 서는 자리

- **재활용이 기본 전략**(§3.2): 8 수학 커널(`app/domain/` 2,166줄, 전부 stdlib·순수, 래칫 통과 — ML-11.1·11.2)을 **새 패키지 경계로 이식**하고
  ORM·settings·Celery·업무 판정·로깅 결합을 잘라낸다(ML-11.3). 기대값은 legacy 출력이 아니라 승인 명세·authoritative fixture(§3.2 「기존 출력은 정답이 아니다」).
- M2 가 세우는 것: `ml-engine/pyproject.toml` + `contracts/` 임시 생성 + round-trip test(2A D-M2-3 (a)). 5A 는 그 위에 패키지 여덟·경계·툴체인을 완성.
- 소비자: M4 4D(Kotlin client) ↔ 5E(servicer). 5D 의 커널 출력은 2B 의 `Success`(후보 3·`PriceFitness`·`Uncertainty` 성분 셋·`Unmeasurable` 사유)로 5E 가 매핑.

---

## 2. slice 지도

| slice | 하는 일(요지) | M2 의존 | 정본 |
| --- | --- | --- | --- |
| **5A 패키지·import boundary** | `contracts·features·training·evaluation·inference·registry·serving·adapters` 여덟 · serving/training extras 분리 · ruff·typecheck·pytest·import-linter·크기/복잡도 래칫(이식 코드 동일 적용) · `serving` 의 DB/HTTP/business import 금지 gate · 재활용 출처 기록(ADR 0009 (d) 조합) · Python CI job(M2 2D D-2D-4 가 5A 소유로 둠) | 골격만(승계) | `5a/scope.md`(초안 있음) |
| **5B feature schema** | versioned feature name/order/type/range · 변환 코드 이식(`award_rate_features.py` — training/serving 공용 단일화) · missing/unknown 명시 처리 · unit/basis validation · dataset/feature manifest·checksum | **변환·schema 는 독립**, `FeatureInputs`→피처 변환의 입력 어휘는 2B(D-2B-1 (a): wire 는 원 fact, 변환은 ml-engine) · `denominator_source` 축 전환(D-M2-10 → 재학습) | 착수 시 `5b/scope.md` |
| **5C training/evaluation** | LightGBM pipeline 이식+튜닝 · 시간 누수 없는 split · calibration 이식 · deterministic seed·환경 · artifact manifest(schema/code/dataset version·metric·재현성 파라미터) · promotion 은 측정만 | 2C(`training_spec_version`·`DatasetReference`·`ArtifactReference`·`EvaluationReportReference` 형태·`JobFailureCode` 확장) | 착수 시 `5c/scope.md` |
| **5D inference kernels** | model predict adapter(LightGBM) · KDE density/optimization 이식(수학 유지, 결합 제거) · 최소 표본·singular·NaN/Infinity · objective 별 후보·diagnostics · 업무 하한/자격/최종 결정 없음 | 커널은 독립. 출력 어휘(후보 3·성분 셋·`Unmeasurable` 두 사유 구별)는 2B 와 **같은 형태**로 두되 wire 매핑은 5E | `5d/scope.md`(초안 있음) |
| **5E gRPC serving** | 생성 servicer · preload·readiness · validation·Numpy 변환(Decimal→float 한 번) · deadline/cancel/status mapping · release/checksum 응답 · graceful shutdown·bounded concurrency | 2B·2C·2D 전부 | 착수 시 |

**M5 전체 out_of_scope**: Kotlin 업무 판단 · artifact 승격/배포(ML-07·08 은 측정·서명까지) · 운영 DB 직접 학습 query(ADR 0004 D-3 — dataset 은 storage 참조) ·
legacy predictor 출력과의 무조건 동일성 · win-proxy(`OPEN-ML-06` 결정 전).

---

## 3. 착수 전 결정 후보 D-M5-1~8

| ID | 물음 | 선택지 | 추천·근거 |
| --- | --- | --- | --- |
| **D-M5-1** | **패키지 관리·lock** | (a) **`uv`**(lock + 오프라인 wheel 캐시 + extras 분리) (b) pip-tools (c) poetry | 조사 노트 02 결과에 따름 — 잠정 (a): 단일 도구로 lock·가상환경·실행, 리뷰 레인 오프라인 설치는 wheel 디렉터리로 |
| **D-M5-2** | **typecheck 도구·strict 범위** | (a) **mypy strict 를 신규 코드 전건, 이식 커널은 모듈 단위 allowlist 로 점진**(사유·해소 계획 기록 — §5 「예외가 필요하면 allowlist 사유와 해소 계획」) (b) pyright | 조사 02. strict 범위 축소는 allowlist 로만, baseline 완화 금지 |
| **D-M5-3** | **크기·복잡도 래칫 도구** | (a) **legacy `_design_ratchet_*.py` 의 측정 정의(함수 50/파일 500·`dict[str, Any]` 경계 수)를 이식해 pytest 게이트로** — 위반 0 또는 allowlist (b) radon/xenon | **(a)** — 측정 정의가 이미 V2 한도와 같고(ML-11.2) 재활용 대상. baseline allowance 방식(증가만 차단)은 채택하지 않는다(§5 「baseline 을 느슨하게 갱신해서 우회하지 않는다」) |
| **D-M5-4** | **import-linter 계약의 형태** — `serving` 금지 목록 | (a) **layers 계약(`serving → inference → features`, `training → features`)+ forbidden 계약(`serving` 은 `sqlalchemy`·`requests`·`httpx`·`celery`·업무 모듈 import 금지)** (b) 금지 목록만 | **(a)** — §3.2 경계는 방향과 금지 둘 다. M2 2A 가 만든 `contracts/` 는 `serving`·`training` 양쪽이 참조 가능한 최하층 |
| **D-M5-5** | **커널 이식 순서**(5D) | (a) **의존 없는 것부터**: `reserve_draw_distribution` → `assessment_shrinkage` → `award_margin_distribution`(KDE) → `award_rate_features`(5B) → `settlement_maturity`(계산만) → LightGBM predict adapter → `award_landing_*`(D-M5-8 뒤) (b) 응답 경로 순 | **(a)** — 각 커널이 자기 property test 와 함께 들어오고 상위가 하위를 참조. 조사 (a) 의 의존 방향으로 착수 시 확정 |
| **D-M5-6** | **`OPEN-ML-05` 정책 값 33개 분류** | (a) **조사 (b) 의 1차 분류(도메인 정책 vs 환경)를 표로 받아 운영자 승인, 도메인 정책은 versioned policy 데이터(YAML)로 `registry/` 가 로드, 환경은 설정** (b) 전부 설정 | **(a)** — ADR 0006 D-7 「분류가 끝난 뒤 그 데이터가 어느 모듈에 속하는가」. 발주기관별 값 등은 도메인 지식(ML-11.3) |
| **D-M5-7** | **5D 의 authoritative corpus** — `ml-boundary` 6·`verdict` 6 의 layer 는 조사 (f) | (a) **커널 수준 golden(입력→출력 수치, 정밀도 명시)을 `authored-from-approved-spec` 으로 curator 가 신설**(ML-02·03·04 acceptance 문면 승인) (b) legacy 출력을 `legacy-behavior` 로만 | **(a)** — 「기존 출력은 정답이 아니다」. legacy 출력은 회귀 대조(`legacy-behavior`)에 쓰되 판정 근거는 승인 문면 |
| **D-M5-8** | **`OPEN-ML-06` win-proxy 커널 포함 여부** | (a) **5D 밖 — 사용자 도달 경로 없음(ML-06)** (b) 보존 이식 | **(a)** — `capability-map.md` OPEN 문면 「코드 품질은 최상급이나 사용자 도달 경로가 없다」. 이식은 도달 경로(capability)가 생길 때 |

---

## 4. `OPEN` 처리 후보

| OPEN | M5 처리 |
| --- | --- |
| `OPEN-ML-05` | D-M5-6 — 5A 가 분류 표, 5C·5D 가 소비 |
| `OPEN-ML-06` | D-M5-8 (a) — 활성 유지(범위 밖 확정은 운영자) |
| `OPEN-ML-02`·`OPEN-ML-03` | M2 가 계약 수준 처리(D-M2-8·11). 5D 는 `PriceFitness`≠확률 을 타입으로 승계 |
| `OPEN-ADR-10`(닫힘) | 재활용 출처 기록 = (d) 조합 — 5A 가 모듈 docstring 포인터 + `reports/evidence/m5/<slice>/reuse.md` |
| 신설 후보 `OPEN-5A-PY-CI` | Python CI job 의 러너·오프라인 wheel — 조사 02 |

---

## 5. 병행 규칙 (M2 진행 중)

- **M5 코드 착수 금지**. `ml-engine/**` 은 M2 2A 가 만드는 중 — 5A 계약은 그 골격(`pyproject.toml`·`tests/conftest.py`·`contracts/`)을 **승계·확장**하는 형태로만 쓰고 파일을 만들지 않는다.
- 금지 경로: M2 in_scope 전부 + `capability-map.md`·`data-dictionary.md`.

---

## 6. 조사 결과 요약

- `01_scout_ml_package.md`: 대기.
- `02_python_toolchain.md`: 대기.
