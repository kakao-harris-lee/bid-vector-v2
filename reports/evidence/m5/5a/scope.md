# Slice 계약 — M5 / 5A · 패키지와 import boundary — **초안, 구현 전**

> **지위**: M2 진행 중 세션 모델이 쓴 초안. 착수는 M2 승인 뒤(2A 가 `ml-engine/` 최소 골격 — `pyproject.toml`·`tests/conftest.py`·`tests/test_contract_roundtrip.py`
> — 을 만든 상태를 base 로 재고정, 40자 SHA), `prep/m5-prep.md` D-M5-1~4·6 과 아래 D-5A-0 답 수령, `milestone-5.md` 착수 문단. **import 경계 게이트·래칫을
> 세우는 slice 이므로 Phase 2.5 설계 검토 대상**이다 — 아래 「위협 모델」·「우회 후보」 절은 저작 레인이 쓴 **검토 입력**이지 검토 결과가 아니다.
> **2A 가 만드는 Python 쪽 실물(리뷰 r1 실측)**: `src/` 도 `ml_engine` 패키지도 없고, 생성 stub 은 pytest 세션이 `grpc_tools.protoc` 로 만드는 임시 사본이며
> 네임스페이스는 **`bidvector.ml.v1.*_pb2`** 다(`ml_engine.contracts` 가 아니다). conftest 가 「5A 가 이 생성 배선을 이어받는다」고 예고했다.

```yaml
milestone: m5
slice: 5a-package-and-import-boundary
base_sha: 040ab9d   # 초안 앵커 — **M2 승인 뒤 재고정(2A 골격 포함 HEAD)**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/pyproject.toml                          # 2A 골격 확장: 패키지 메타·extras(serving/training/dev)·도구 설정(ruff·mypy·pytest·import-linter)
  - ml-engine/uv.lock (또는 D-M5-1 의 lock 파일)       # lock 파일 — 리뷰 레인 오프라인 설치의 정본
  - ml-engine/src/ml_engine/{features,training,evaluation,inference,registry,serving,adapters}/__init__.py   # 일곱 패키지 자리(코드 없음 — 5B~5E 가 채움). `contracts` 패키지는 D-5A-0 이 정한다
  - ml-engine/tests/conftest.py                       # 2A 의 생성 배선 승계(conftest 예고) — D-5A-0 의 결과에 따라 임시 생성 유지 또는 재수출 패키지 생성
  - ml-engine/importlinter.toml (또는 pyproject 절)    # layers + forbidden 계약(D-M5-4) — 최하층은 생성 stub 네임스페이스 `bidvector.ml.v1`(외부 패키지로 취급)
  - ml-engine/tests/gates/**                          # 래칫 게이트(pytest): 함수 50/파일 500·dict[str,Any] 경계 수·import 계약 실행·serving 금지 import 양성 대조·재활용 출처 두 자리 대조(ADR 0009 D-6)
  - ml-engine/tools/design_ratchet.py                 # legacy `_design_ratchet_*.py` 이식(재활용 출처 기록) — D-M5-3
  - ml-engine/tools/reuse_provenance_check.py         # ADR 0009 D-6 검사 — 모듈 docstring 의 출처 포인터(원본 경로·commit)와 `reports/evidence/m5/*/reuse.md` 가 같은 모듈에 대해 같은 값을 말하는지
  - ml-engine/README.md                               # 패키지 경계·재활용 출처 기록 규약(ADR 0009 (d): 모듈 docstring 포인터 + evidence 상세)
  - .github/workflows/ci.yml                          # Python job 신설(2D D-2D-4 (a) 가 5A 소유로) — setup-python·오프라인 wheel 캐시·S-1b·S-2~S-7(D-6 검사 포함)
  - milestone-5.md, reports/evidence/m5/5a/**
out_of_scope:
  - 커널·변환·학습·serving 코드                          # 5B~5E
  - 생성 stub(`bidvector.ml.v1.*_pb2`)의 내용            # M2 소유(2A~2D) — VCS 밖, pytest 임시 생성
  - contracts/**, ml-contract/**, build-logic/**, adapters/**   # M2·Kotlin 쪽
  - 정책 값 33개의 **값**                                # D-M5-6 승인 대상 — 5A 는 분류 표와 로드 자리(`registry/`)만
  - legacy bid-vector/ 편집
acceptance_commands:
  - "(cd ml-engine && uv sync --frozen --all-extras --no-index --find-links=<wheelhouse>)"            # S-1 — lock 재현(오프라인 wheel 캐시에서). 이 명령은 extras 분리를 증명하지 않는다(S-1b)
  - "(cd ml-engine && uv sync --frozen --extra serving --no-dev --no-index --find-links=<wheelhouse> && for m in sqlalchemy psycopg requests celery; do ! uv run python -c \"import $m\" || exit 1; done)"   # S-1b — serving extras 만 설치한 환경에 금지 패키지 **넷 각각** 부재(하나의 import 문이면 하나만 없어도 통과하므로 개별로) — lock 그래프 실측
  - "(cd ml-engine && uv run ruff check . && uv run ruff format --check .)"                            # S-2
  - "(cd ml-engine && uv run mypy --strict src/ml_engine)"                                            # S-3 — allowlist 는 pyproject 의 per-module override 로만, 사유 주석 필수
  - "(cd ml-engine && uv run lint-imports)"                                                           # S-4 — layers·forbidden 계약 통과
  - "(cd ml-engine && uv run python -m pytest tests -q)"                                              # S-5 — 게이트 test 포함(양성 대조: serving 에 금지 import 를 임시로 넣은 fixture 가 실패함)
  - "(cd ml-engine && uv run python tools/design_ratchet.py --check)"                                 # S-6 — 위반 0 또는 allowlist(exit 0/1/2 스윕 규약)
  - "(cd ml-engine && uv run python tools/reuse_provenance_check.py && ! uv run python tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md)"   # S-7 — ADR 0009 D-6 두 자리 대조 + 양성 대조(어긋난 evidence 표본이면 실패)
  - "./gradlew --no-build-cache clean check"                                                          # S-8 — Kotlin 쪽 무영향 확인(ml-engine 은 Gradle 밖, ADR 0006 D-8)
rollback: |
    **정본은 `reports/evidence/m5/5a/rollback.md`**(착수 시). 2A 골격 상태로 경로 한정 restore. CI Python job 은 workflow 파일에서 걷는다.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-5.md` 5A·완료 조건 · `v2-지침서.md` §3.2·§5 · `ADR 0006` D-8 · `ADR 0009` D-1~D-6 · `capability-map.md` ML-11.2·11.3 ·
M2 `2a/scope.md`(골격)·`2d/scope.md` D-2D-4 · 조사 노트 02.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **패키지 일곱 + 계약 stub 자리(D-5A-0)** — `features`·`training`·`evaluation`·`inference`·`registry`·`serving`·`adapters`. `contracts` 는 §3.2 트리의 자리이되 **실물은 2A 가 만든 임시 생성 stub(`bidvector.ml.v1`)** 이라 D-5A-0 이 「재수출 패키지를 둘지」를 정한다. 각 `__init__.py` 에 경계 한 줄과 재활용 출처 포인터 규약(ADR 0009 D-5 (d): 모듈 docstring 은 원본 경로·commit 포인터, 상세는 evidence) | 5A 「패키지」 · §3.2 패키지 트리 · ADR 0009 · 2A `tests/conftest.py` 인계 문면 |
| ② | **extras 분리** — `[project.optional-dependencies]` 의 `serving`(grpcio·protobuf·numpy·lightgbm 런타임)·`training`(+ storage adapter 의존)·`dev`(도구). **serving 에 SQLAlchemy·DB driver·requests 없음**은 lock 의 의존 그래프 test 로 단언 | 5A 「serving/training dependency 분리」 · §3.2 「serving process 에는 SQLAlchemy, DB driver, requests 기반 외부 수집, 업무 entity 가 없다」 · M5 완료 조건 「serving image/package 에 DB driver 와 service ORM 이 없음」 |
| ③ | **import-linter 계약** — layers: `serving > inference > features`, `training > evaluation > features`, `registry` 는 `features` 만(생성 stub `bidvector.ml.v1` 은 **외부 패키지**로 취급 — `serving`·`training` 이 참조 가능한 최하층이되 layers 계약의 항이 아니다; D-5A-0 (b) 채택 시 `ml_engine.contracts` 재수출 패키지가 최하층 항으로 들어간다); forbidden: `serving`·`inference` 가 `sqlalchemy`·`psycopg`·`requests`·`httpx`·`celery`·`ml_engine.training`·`ml_engine.adapters` 를 import 금지 — 단 **`serving.grpc`(진입점) 만 `grpcio` 허용**(조사 02 — 예외 없이는 servicer 가 설 수 없다; 예외는 그 모듈 하나로 좁힌다). **양성 대조**: 금지 import 를 넣은 임시 모듈로 `lint-imports` 가 실패함을 test | 5A 「`serving` 의 DB/HTTP 수집/business module import 금지 gate」 · 완료 조건 「금지 import mutation 이 CI 에서 실패」 |
| ④ | **품질 도구** — ruff(lint+format), mypy strict(이식 모듈은 per-module allowlist + 사유·해소 계획), pytest(+hypothesis), 크기·복잡도 래칫(D-M5-3 이식 스크립트, 함수 50/파일 500/`dict[str, Any]` 경계 0 — 계약이 있으므로 원천 차단, ML-11.2) | §5 「Ruff, strict typecheck 범위, pytest, import boundary, size ratchet 를 CI 에」 |
| ⑤ | **정책 값 로드 자리** — `registry/policy.py` 가 versioned YAML 을 읽는 형태(값 없음). D-M5-6 분류 표는 evidence 에 | ADR 0006 D-7 · `OPEN-ML-05` |
| ⑥ | **CI Python job** — `.github/workflows/ci.yml` 에 job 추가: setup-python 3.12, 오프라인 wheel 캐시(`uv sync --frozen`), **S-1b·S-2~S-7 전부**(ADR 0009 D-6 「CI 가 확인한다」 — S-7 의 두 자리 대조와 S-1b 의 extras 분리가 CI 안). Kotlin job 과 독립 | 2D D-2D-4 (a) · ADR 0009 D-6 「Python CI 는 5A 소유」 |
| ⑦ | **재활용 출처 기록 규약 + 두 자리 대조 검사** — `reports/evidence/m5/<slice>/reuse.md` 표(원본 파일·commit `ed4b06c`·이식 대상·수행한 수정·튜닝) + 모듈 docstring 포인터. **ADR 0009 D-6 의 검사**를 5A 가 구현한다(D-6.2 가 「M5 의 이식 slice 가 형태를 정한다」로 위임): `tools/reuse_provenance_check.py` 가 (ㄱ) 이식 모듈마다 docstring 포인터가 **있음** (ㄴ) 그 원본 경로·commit 이 `reuse.md` 의 같은 모듈 행과 **같음**을 재고, 어긋나면 exit 1(S-7, 양성 대조 표본 동반). 형태: docstring 첫 줄 `Reuse: <원본 경로>@<commit>`, `reuse.md` 는 같은 두 값을 표의 열로. 5A 는 규약·검사·`design_ratchet.py` 자신의 기록 | ADR 0009 D-1·D-5·**D-6**(「CI 가 확인한다」·D-6.2 형태 위임·D-6.3) · §3.2 「각 모듈에 재활용 출처와 튜닝 내용을 기록」 |

**만들지 않는 것**: 커널 코드 · 변환 코드 · servicer · 정책 값 · Kotlin 쪽 변경 · Docker 이미지(M6 6C).

---

## 운영자 결정 필요 — 착수 전(`prep` D-M5-1~4·6 + D-5A-0) · 계약 고정(D-5A-1~4)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5A-0** | **Python 생성 stub 의 영구 자리.** 2A 실물은 pytest 임시 생성(`bidvector.ml.v1.*_pb2`, VCS 밖). 선택지: (a) **임시 생성 유지** — `tests/conftest.py` 배선을 패키지 빌드 훅(`uv build`/`hatch` build hook)으로 승격해 설치 시에도 같은 네임스페이스를 만들고, `ml_engine.contracts` 패키지는 두지 않는다 (b) `ml_engine/contracts/__init__.py` **재수출 패키지**(생성물 자체는 여전히 VCS 밖, 생성 위치만 그 아래) (c) 생성물 커밋 | 추천 **(b)** — §3.2 트리의 `contracts/` 자리를 실물로 채우고 import-linter layers 의 최하층 항이 되며, serving 이 `bidvector.ml.v1` 을 직접 import 하는 경로를 막는다(재수출 하나만 허용 → 경계가 한 자리). (a) 는 §3.2 트리와 어긋나고 (c) 는 2A D-2A-0 (c) 와 정면 충돌. **대가**: 생성 디렉터리가 `src/` 안(gitignore)이라 ruff·mypy 제외 목록이 필요 | 착수 전 |
| **D-5A-1** | Python 3.12 고정(M2 조사와 정합), 버전 리터럴은 `pyproject` `requires-python` 한 자리 + `.python-version` | 조사 02 · 매직넘버 한 자리 | 계약 고정 |
| **D-5A-2** | mypy allowlist 는 **이식 모듈 단위·사유·해소 slice 명시**, 신규 모듈은 strict 예외 없음 | §5 「allowlist 사유와 해소 계획」 | 계약 고정 |
| **D-5A-3** | 래칫은 **위반 0 또는 명시 allowlist** — baseline allowance(증가만 차단) 불채택 | §5 「baseline 을 느슨하게 갱신해서 우회하지 않는다」 · ML-11.2 | 계약 고정 |
| **D-5A-4** | 도구 버전은 lock 이 정본, `pyproject` 의 dev extras 는 `==` 핀(재현성 — codex 바이너리 핀·M2 도구 assertion 과 같은 사유) | 2D ① 도구 버전 assertion | 계약 고정 |

---

## 위협 모델 — 5A 고유 경계

**방어한다**: (a) serving 에 DB/HTTP/업무 모듈 유입(③ 계약 + 양성 대조 + S-1b lock 그래프 실측) (b) 래칫 baseline 완화(D-5A-3 — allowlist 만) (c) strict 예외의 조용한 확대(D-5A-2 사유 필수 — 리뷰 항목) (d) 도구 버전 드리프트(lock·핀) (e) 재활용 출처 미기록·두 자리 불일치(⑦ D-6 검사 — 5B~5E 의 이식 모듈에 자동 적용) (f) serving 이 생성 stub 을 우회 경로로 import(D-5A-0 (b) — 재수출 하나만).
**방어하지 않는다**: 커널의 수학적 옳음(5D) · 정책 값 내용(승인) · CI 러너 환경(6C) · `contracts/` 내용(M2).

**우회 후보(≥5)**: (1) `serving` 이 `adapters` 를 함수 안에서 지연 import → import-linter 는 정적 — `tests/gates` 가 `serving` 패키지의 모든 모듈을 import 한 뒤 `sys.modules` 에 금지 모듈 부재를 단언 (2) `# type: ignore` 산포 → ruff `PGH003`(사유 없는 ignore 금지) (3) 래칫 allowlist 에 와일드카드 → 스크립트가 정확 경로만 허용 (4) `--frozen` 없이 sync → CI 명령이 고정 (5) extras 를 합쳐 하나로 → lock 그래프 test 가 serving 그래프에 금지 패키지 부재 단언 (6) 이식 모듈 docstring 에 출처 없음 → 5B~5E 리뷰 체크리스트(5A 는 규약).

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- 조사 02(툴체인): Python 3.12.14 · numpy 2.5.2(스모크 1회 뒤 확정) · lightgbm 4.7.0 · scipy 는 전이 의존 · pandas/sklearn 불채택 · ruff 0.16.6 · mypy 2.3.1 · pytest 9.1.1 ·
  hypothesis 6.167.1 · import-linter 2.15 · uv(`uv sync --frozen --no-index --find-links=<wheelhouse>` 로 오프라인) · CI Python job 없음(5A 최초) · ruff 0.16 default 확장 → **explicit `select`** ·
  import-linter forbidden 은 **gRPC 진입점(`serving.grpc`)만 `grpcio` 허용** 예외 필요(③ 에 반영).
- 조사 01(패키지): serving 경로에 ORM 0·Celery 0 이라 ③ 게이트는 사실 고정 · 8 커널 중 7 이 이미 strict 섬(`settlement_maturity` 만 allowlist 후보 — `OPEN-5A-MYPY-ALLOWLIST` 초기 1건) ·
  legacy 래칫 baseline 에 커널 등재 0 → D-5A-3 위반 0 출발 가능 · 설정 33 = 정책 23·환경 6·미분류 4(⑤ 의 분류 표 입력).

---

## OPEN — 수령·신설

| OPEN | 5A 처리 |
| --- | --- |
| `OPEN-ML-05` | 분류 표(D-M5-6) — 5A 가 표와 로드 자리, 값은 승인 |
| `OPEN-ADR-10`(닫힘) | ⑦ 규약으로 소비 |
| 신설 후보 `OPEN-5A-PY-CI` | CI 러너·오프라인 wheel 캐시의 자리 — 조사 02 |
| 신설 후보 `OPEN-5A-MYPY-ALLOWLIST` | 이식 모듈 strict 예외 목록과 해소 slice — 5B~5E 가 갱신 |
