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
- M2 가 세우는 것(실물): `ml-engine/pyproject.toml` + `tests/conftest.py`(pytest 가 `grpc_tools.protoc` 로 **임시 생성**, 네임스페이스 `bidvector.ml.v1.*_pb2`, VCS 밖) + `tests/test_contract_roundtrip.py`(2A D-M2-3 (a)). **`src/`·`ml_engine` 패키지·`contracts/` 디렉터리는 없다.** 5A 는 conftest 의 생성 배선을 승계하고(5A D-5A-0) 패키지 일곱·경계·툴체인을 완성.
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

## 3. 착수 전 결정 후보 D-M5-1~9

| ID | 물음 | 선택지 | 추천·근거 |
| --- | --- | --- | --- |
| **D-M5-1** | **패키지 관리·lock** | (a) **`uv`**(lock + 오프라인 wheel 캐시 + extras 분리) (b) pip-tools (c) poetry | 조사 노트 02 결과에 따름 — 잠정 (a): 단일 도구로 lock·가상환경·실행, 리뷰 레인 오프라인 설치는 wheel 디렉터리로 |
| **D-M5-2** | **typecheck 도구·strict 범위** | (a) **mypy strict 를 신규 코드 전건, 이식 커널은 모듈 단위 allowlist 로 점진**(사유·해소 계획 기록 — §5 「예외가 필요하면 allowlist 사유와 해소 계획」) (b) pyright | 조사 02. strict 범위 축소는 allowlist 로만, baseline 완화 금지 |
| **D-M5-3** | **크기·복잡도 래칫 도구** | (a) **legacy `_design_ratchet_*.py` 의 측정 정의(함수 50/파일 500·`dict[str, Any]` 경계 수)를 이식해 pytest 게이트로** — 위반 0 또는 allowlist (b) radon/xenon | **(a)** — 측정 정의가 이미 V2 한도와 같고(ML-11.2) 재활용 대상. baseline allowance 방식(증가만 차단)은 채택하지 않는다(§5 「baseline 을 느슨하게 갱신해서 우회하지 않는다」) |
| **D-M5-4** | **import-linter 계약의 형태** — `serving` 금지 목록 | (a) **layers 계약(`serving → inference → features`, `training → features`)+ forbidden 계약(`serving` 은 `sqlalchemy`·`requests`·`httpx`·`celery`·업무 모듈 import 금지)** (b) 금지 목록만 | **(a)** — §3.2 경계는 방향과 금지 둘 다. 생성 stub(`bidvector.ml.v1`, 2A 임시 생성)은 `serving`·`training` 양쪽이 참조 가능한 최하층이되 외부 패키지로 취급 — 5A D-5A-0 (b) 채택 시 `ml_engine.contracts` 재수출이 layers 의 항이 된다 |
| **D-M5-5** | **커널 이식 순서**(5D) | (a) **의존 없는 것부터**: `reserve_draw_distribution` → `assessment_shrinkage` → `award_rate_features`(5B, `reliable_base` enum 만 들어올림) → `settlement_maturity`(계산만) → LightGBM predict adapter → (D-M5-8 결정 뒤) `award_margin_distribution`(KDE)·`award_landing_curve_builders`·`award_landing_distribution`·`award_landing_curve` (b) 응답 경로 순 | **(a)** — 각 커널이 자기 property test 와 함께 들어오고 상위가 하위를 참조. **조사 (a) 실측**: KDE 와 곡선 빌더는 win-proxy 체인 안에서만 소비되므로 D-M5-8 뒤로 밀린다 |
| **D-M5-6** | **`OPEN-ML-05` 정책 값 33개 분류** | (a) **조사 (b) 의 1차 분류(도메인 정책 vs 환경)를 표로 받아 운영자 승인, 도메인 정책은 versioned policy 데이터(YAML)로 `registry/` 가 로드, 환경은 설정** (b) 전부 설정 | **(a)** — ADR 0006 D-7 「분류가 끝난 뒤 그 데이터가 어느 모듈에 속하는가」. 발주기관별 값 등은 도메인 지식(ML-11.3) |
| **D-M5-7** | **5D 의 authoritative corpus** — `ml-boundary` 6·`verdict` 6 의 layer 는 조사 (f) | (a) **커널 수준 golden(입력→출력 수치, 정밀도 명시)을 `authored-from-approved-spec` 으로 curator 가 신설**(ML-02·03·04 acceptance 문면 승인) (b) legacy 출력을 `legacy-behavior` 로만 | **(a)** — 「기존 출력은 정답이 아니다」. legacy 출력은 회귀 대조(`legacy-behavior`)에 쓰되 판정 근거는 승인 문면 |
| **D-M5-8** | **`OPEN-ML-06` — win-proxy 커널 둘**(`award_landing_curve`·`award_landing_distribution`, OPEN 문면·ADR 0001 §4.1 이 지목한 대상)의 5D 포함 여부 | (a) **5D 밖 — 사용자 도달 경로 없음(ML-06)** (b) 보존 이식 | **(a)** — `capability-map.md` OPEN 문면 「코드 품질은 최상급이나 사용자 도달 경로가 없다」. 이식은 도달 경로(capability)가 생길 때 |
| **D-M5-9** | **`OPEN-ML-01` 하류 「이식 대상 8개」 목록의 개정** — 조사 (a) 실측: 반사 KDE(`award_margin_distribution`)와 곡선 빌더(`award_landing_curve_builders`)는 **win-proxy 체인 안에서만 소비**된다(둘 합쳐 D-M5-8 과 함께 1,296/2,166줄). 둘은 `OPEN-ML-06` 의 문면 대상이 **아니고** ADR 0001 §4.1 의 8개 확정 목록 안이다. `milestone-5.md` 5D 문면 「기존 KDE density/optimization 커널 이식」도 이 둘을 가리킨다 | (a) **8개 목록을 개정해 둘을 D-M5-8 과 같은 조건(도달 경로 생길 때)으로 미룸** — ADR 0001 §4.1 ·`milestone-5.md` 5D 문면의 **개정(운영자 승인)** 동반 (b) 8개 목록 유지 — 둘을 소비자 없이 이식(래칫 아래 두되 호출 경로 없음) (c) KDE 만 이식하고 곡선 빌더는 미룸 | **(a)** — 소비자 없는 코드를 래칫 아래 두는 것은 §7 과 어긋나고, 상위 문서를 고치지 않고 범위를 줄이면 「임의 축소」(agent-workflow 금지)다. (b)·(c) 는 도달 경로 없는 이식. **대가**: ADR 0001 §4.1 표와 milestone-5 5D 문면 개정이 착수 전건 — 5D scope 의 「만들지 않는 것」에 milestone 문면 항목 하나가 빠진다는 사실을 명시(5D ①). ML-04(예정가 분포)는 `reserve_draw_distribution`+`assessment_shrinkage` 로 서고 KDE 는 낙찰률 마진 분포(win-proxy 입력) — 착수 시 간선으로 재확인 |

---

## 4. `OPEN` 처리 후보

| OPEN | M5 처리 |
| --- | --- |
| `OPEN-ML-05` | D-M5-6 — 5A 가 분류 표, 5C·5D 가 소비 |
| `OPEN-ML-06` | D-M5-8 (a) — 활성 유지(범위 밖 확정은 운영자). KDE·곡선 빌더는 이 OPEN 밖 — D-M5-9(8개 목록 개정, ADR 0001 §4.1·milestone-5 5D 문면 동반) |
| `OPEN-ADR-10`(닫힘)의 D-6 검사 | 5A ⑦ `reuse_provenance_check.py` — 첫 이식 slice 가 형태를 정한다(ADR 0009 D-6.2) |
| `base_sha` 표기 | 여섯 scope 초안의 7자 앵커는 착수 시 **40자**로 재고정(evidence-pack 규격) |
| `OPEN-ML-02`·`OPEN-ML-03` | M2 가 계약 수준 처리(D-M2-8·11). 5D 는 `PriceFitness`≠확률 을 타입으로 승계 |
| `OPEN-ADR-10`(닫힘) | 재활용 출처 기록 = (d) 조합 — 5A 가 모듈 docstring 포인터 + `reports/evidence/m5/<slice>/reuse.md` |
| 신설 후보 `OPEN-5A-PY-CI` | Python CI job 의 러너·오프라인 wheel — 조사 02 |

---

## 5. 병행 규칙 (M2 진행 중)

- **M5 코드 착수 금지**. `ml-engine/**` 은 M2 2A 가 만드는 중 — 5A 계약은 그 골격(`pyproject.toml`·`tests/conftest.py`·`contracts/`)을 **승계·확장**하는 형태로만 쓰고 파일을 만들지 않는다.
- 금지 경로: M2 in_scope 전부 + `capability-map.md`·`data-dictionary.md`.

---

## 6. 조사 결과 요약

- `01_scout_ml_package.md`(legacy `ed4b06c`, 2026-09-07):
  - **`OPEN-ML-06` 이 끄는 커널은 둘이 아니라 넷** — AST import 간선으로 반사 KDE 모듈(`award_margin_distribution`)과 곡선 빌더가 **win-proxy 체인 안에서만** 소비된다.
    win-proxy 를 미루면 2,166줄 중 1,296줄(59.8%)이 같이 미뤄진다. ADR 0001 §4.1 은 둘만 지목하고 KDE·곡선 빌더는 8개 확정 목록 안 → **D-M5-8(OPEN-ML-06 둘)과 D-M5-9(8개 목록 개정)로 결정을 갈랐다**(5D ① 정정).
  - **corpus 수치 정정**: `ml-boundary` 4(authoritative 2)·`verdict` 4 — 브리프의 6·6 은 오기. 어떤 authoritative 도 커널 수치 거동을 고정하지 않으며 `ml-boundary-003` 의 `not_covered`
    가 그 공백을 스스로 적는다 → **D-M5-7 (a) 확정 근거**: 5D 는 fixture 를 소비하는 slice 가 아니라 **만드는** slice.
  - 커널 8 은 V2 래칫을 **이미 통과**(3rd-party import 0·50줄 초과 0·`dict[str, Any]` 경계 0·legacy baseline 등재 0). ADR 0001 §4.2 수치 재현(56 파일·50줄 초과 29·100줄 초과 4·최대 491줄).
    8 중 7 이 이미 mypy strict 섬 — `settlement_maturity` 만 `ignore_errors` 와일드카드 아래 → D-M5-2 allowlist 는 사실상 하나.
  - 이식이 끌고 오는 모듈 셋 중 진짜 절단은 하나 — feature 커널이 `reliable_base` 의 4값 enum 하나를 import 하는데 그 모듈이 295줄 services 로 이어진다 → **enum 만 들어올리고 사슬은 끊는다**(D-M2-10 의 `denominator_source` 축과 같은 자리, 5B).
  - serving 에는 이미 ORM 0·Celery 0(`app/ai/predictors/` 아래 DB import 0, 앵커 셋에 Celery import 0) → 5A 게이트는 결합을 **되돌리는** 것이 아니라 **사실을 고정**하는 것.
  - 설정값 33 전부 열거(기본값·좌표) + 1차 분류 **정책 23 · 환경 6 · 미분류 4** → D-M5-6 표의 입력.
  - `OPEN-ML-05` 는 5A 를 막지 않으나 **5D 를 막는다**(표본 수 임계 넷 이상이 추론 술어에 직접 들어감). `OPEN-ML-06` 은 5D 범위를 막고 5A 는 자유.
- `02_python_toolchain.md`(PyPI 메타데이터 실측, 2026-09-07):
  - **고정 후보**: Python 3.12.14 · numpy **2.5.2**(1.26.2 → 승격 권고, lightgbm numpy2 이슈 2024-06 종결 — **설치+학습+추론 스모크 1회로 확정**, 미확인) · lightgbm **4.7.0**(legacy 동일) ·
    scipy 는 lightgbm 의 강제 전이 의존(항상 딸려옴 — 「미사용」이 아니라 「직접 import 0」) · **pandas·scikit-learn 불필요**(legacy 는 native Booster API + 자체 numpy 반사 KDE) ·
    ruff 0.16.6 · mypy 2.3.1 · pytest 9.1.1 · hypothesis 6.167.1 · import-linter 2.15 · grpcio/grpcio-tools 1.83.1·protobuf 7.36.1(M2 고정값 그대로, 충돌 없음).
  - 래칫은 legacy `scripts/_design_ratchet_*.py`(자체 AST 스캐너) 재사용 — radon/xenon/wily 는 유지보수 끊김 → **D-M5-3 (a) 확정**.
  - 패키징 **uv**(`uv.lock` + optional-dependencies extras), 오프라인 리뷰 레인은 `uv sync --frozen --no-index --find-links=<wheelhouse>` → **D-M5-1 (a) 확정**.
  - CI 에 Python job 없음 — 5A 가 최초 추가, M2 `ml-engine/pyproject.toml` 골격 위.
  - 함정: ruff 0.16 의 default 규칙 확장 — explicit `select` 만 안전(legacy 가 그렇다) · import-linter forbidden 예시는 **gRPC 진입점 예외** 없이는 채택 불가(5A ③ 에 반영) ·
    macOS libomp(lightgbm) · grpcio-tools 번들 protoc 버전(M2 와 공유 미확인).
