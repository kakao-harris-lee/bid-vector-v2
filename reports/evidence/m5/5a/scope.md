# Slice 계약 — M5 / 5A · 패키지와 import boundary — **초안, 구현 전**

> **지위**: M2 진행 중 세션 모델이 쓴 초안. 착수는 M2 승인 뒤(2A 가 `ml-engine/` 최소 골격을 만든 상태를 base 로 재고정), `prep/m5-prep.md`
> D-M5-1~4·6 답 수령, `milestone-5.md` 착수 문단.

```yaml
milestone: m5
slice: 5a-package-and-import-boundary
base_sha: 040ab9d   # 초안 앵커 — **M2 승인 뒤 재고정(2A 골격 포함 HEAD)**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - ml-engine/pyproject.toml                          # 2A 골격 확장: 패키지 메타·extras(serving/training/dev)·도구 설정(ruff·mypy·pytest·import-linter)
  - ml-engine/uv.lock (또는 D-M5-1 의 lock 파일)       # lock 파일 — 리뷰 레인 오프라인 설치의 정본
  - ml-engine/src/ml_engine/{contracts,features,training,evaluation,inference,registry,serving,adapters}/__init__.py   # 여덟 패키지 자리(코드 없음 — 5B~5E 가 채움). contracts 는 2A 것 그대로
  - ml-engine/importlinter.toml (또는 pyproject 절)    # layers + forbidden 계약(D-M5-4)
  - ml-engine/tests/gates/**                          # 래칫 게이트(pytest): 함수 50/파일 500·dict[str,Any] 경계 수·import 계약 실행·serving 금지 import 양성 대조
  - ml-engine/tools/design_ratchet.py                 # legacy `_design_ratchet_*.py` 이식(재활용 출처 기록) — D-M5-3
  - ml-engine/README.md                               # 패키지 경계·재활용 출처 기록 규약(ADR 0009 (d): 모듈 docstring 포인터 + evidence 상세)
  - .github/workflows/ci.yml                          # Python job 신설(2D D-2D-4 (a) 가 5A 소유로) — setup-python·오프라인 wheel 캐시·`uv sync --frozen`·pytest
  - milestone-5.md, reports/evidence/m5/5a/**
out_of_scope:
  - 커널·변환·학습·serving 코드                          # 5B~5E
  - ml-engine/src/ml_engine/contracts/** 의 내용        # M2 소유(2A~2D)
  - contracts/**, ml-contract/**, build-logic/**, adapters/**   # M2·Kotlin 쪽
  - 정책 값 33개의 **값**                                # D-M5-6 승인 대상 — 5A 는 분류 표와 로드 자리(`registry/`)만
  - legacy bid-vector/ 편집
acceptance_commands:
  - "(cd ml-engine && uv sync --frozen --all-extras)"                                                # S-1 — lock 재현(오프라인 wheel 캐시에서)
  - "(cd ml-engine && ruff check . && ruff format --check .)"                                          # S-2
  - "(cd ml-engine && mypy --strict src/ml_engine)"                                                   # S-3 — allowlist 는 pyproject 의 per-module override 로만, 사유 주석 필수
  - "(cd ml-engine && lint-imports)"                                                                  # S-4 — layers·forbidden 계약 통과
  - "(cd ml-engine && python -m pytest tests -q)"                                                     # S-5 — 게이트 test 포함(양성 대조: serving 에 금지 import 를 임시로 넣은 fixture 가 실패함)
  - "(cd ml-engine && python tools/design_ratchet.py --check)"                                        # S-6 — 위반 0 또는 allowlist(exit 0/1/2 스윕 규약)
  - "./gradlew --no-build-cache clean check"                                                          # S-7 — Kotlin 쪽 무영향 확인(ml-engine 은 Gradle 밖, ADR 0006 D-8)
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
| ① | **패키지 여덟** — `contracts`(2A)·`features`·`training`·`evaluation`·`inference`·`registry`·`serving`·`adapters`. 각 `__init__.py` 에 경계 한 줄과 재활용 출처 포인터 규약(ADR 0009 D-5 (d): 모듈 docstring 은 원본 경로·commit 포인터, 상세는 evidence) | 5A 「패키지」 · §3.2 패키지 트리 · ADR 0009 |
| ② | **extras 분리** — `[project.optional-dependencies]` 의 `serving`(grpcio·protobuf·numpy·lightgbm 런타임)·`training`(+ storage adapter 의존)·`dev`(도구). **serving 에 SQLAlchemy·DB driver·requests 없음**은 lock 의 의존 그래프 test 로 단언 | 5A 「serving/training dependency 분리」 · §3.2 「serving process 에는 SQLAlchemy, DB driver, requests 기반 외부 수집, 업무 entity 가 없다」 · M5 완료 조건 「serving image/package 에 DB driver 와 service ORM 이 없음」 |
| ③ | **import-linter 계약** — layers: `serving > inference > features > contracts`, `training > evaluation > features > contracts`, `registry` 는 `features`·`contracts` 만; forbidden: `serving`·`inference` 가 `sqlalchemy`·`psycopg`·`requests`·`httpx`·`celery`·`ml_engine.training`·`ml_engine.adapters` 를 import 금지. **양성 대조**: 금지 import 를 넣은 임시 모듈로 `lint-imports` 가 실패함을 test | 5A 「`serving` 의 DB/HTTP 수집/business module import 금지 gate」 · 완료 조건 「금지 import mutation 이 CI 에서 실패」 |
| ④ | **품질 도구** — ruff(lint+format), mypy strict(이식 모듈은 per-module allowlist + 사유·해소 계획), pytest(+hypothesis), 크기·복잡도 래칫(D-M5-3 이식 스크립트, 함수 50/파일 500/`dict[str, Any]` 경계 0 — 계약이 있으므로 원천 차단, ML-11.2) | §5 「Ruff, strict typecheck 범위, pytest, import boundary, size ratchet 를 CI 에」 |
| ⑤ | **정책 값 로드 자리** — `registry/policy.py` 가 versioned YAML 을 읽는 형태(값 없음). D-M5-6 분류 표는 evidence 에 | ADR 0006 D-7 · `OPEN-ML-05` |
| ⑥ | **CI Python job** — `.github/workflows/ci.yml` 에 job 추가: setup-python 3.12, 오프라인 wheel 캐시(`uv sync --frozen`), S-2~S-6. Kotlin job 과 독립 | 2D D-2D-4 (a) 「Python CI 는 5A 소유」 |
| ⑦ | **재활용 출처 기록 규약** — `reports/evidence/m5/<slice>/reuse.md` 표(원본 파일·commit `ed4b06c`·이식 대상·수행한 수정·튜닝) + 모듈 docstring 포인터. 5A 는 규약과 `design_ratchet.py` 자신의 기록만 | ADR 0009 D-1·D-5 · §3.2 「각 모듈에 재활용 출처와 튜닝 내용을 기록」 |

**만들지 않는 것**: 커널 코드 · 변환 코드 · servicer · 정책 값 · Kotlin 쪽 변경 · Docker 이미지(M6 6C).

---

## 운영자 결정 필요 — 착수 전(`prep` D-M5-1~4·6) · 계약 고정(D-5A-1~4)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-5A-1** | Python 3.12 고정(M2 조사와 정합), 버전 리터럴은 `pyproject` `requires-python` 한 자리 + `.python-version` | 조사 02 · 매직넘버 한 자리 | 계약 고정 |
| **D-5A-2** | mypy allowlist 는 **이식 모듈 단위·사유·해소 slice 명시**, 신규 모듈은 strict 예외 없음 | §5 「allowlist 사유와 해소 계획」 | 계약 고정 |
| **D-5A-3** | 래칫은 **위반 0 또는 명시 allowlist** — baseline allowance(증가만 차단) 불채택 | §5 「baseline 을 느슨하게 갱신해서 우회하지 않는다」 · ML-11.2 | 계약 고정 |
| **D-5A-4** | 도구 버전은 lock 이 정본, `pyproject` 의 dev extras 는 `==` 핀(재현성 — codex 바이너리 핀·M2 도구 assertion 과 같은 사유) | 2D ① 도구 버전 assertion | 계약 고정 |

---

## 위협 모델 — 5A 고유 경계

**방어한다**: (a) serving 에 DB/HTTP/업무 모듈 유입(③ 계약 + 양성 대조 + ② lock 그래프 test) (b) 래칫 baseline 완화(D-5A-3 — allowlist 만) (c) strict 예외의 조용한 확대(D-5A-2 사유 필수 — 리뷰 항목) (d) 도구 버전 드리프트(lock·핀) (e) 재활용 출처 미기록(⑦ 규약 — 5B~5E 의 리뷰 항목).
**방어하지 않는다**: 커널의 수학적 옳음(5D) · 정책 값 내용(승인) · CI 러너 환경(6C) · `contracts/` 내용(M2).

**우회 후보(≥5)**: (1) `serving` 이 `adapters` 를 함수 안에서 지연 import → import-linter 는 정적 — `tests/gates` 가 `serving` 패키지의 모든 모듈을 import 한 뒤 `sys.modules` 에 금지 모듈 부재를 단언 (2) `# type: ignore` 산포 → ruff `PGH003`(사유 없는 ignore 금지) (3) 래칫 allowlist 에 와일드카드 → 스크립트가 정확 경로만 허용 (4) `--frozen` 없이 sync → CI 명령이 고정 (5) extras 를 합쳐 하나로 → lock 그래프 test 가 serving 그래프에 금지 패키지 부재 단언 (6) 이식 모듈 docstring 에 출처 없음 → 5B~5E 리뷰 체크리스트(5A 는 규약).

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- 대기(조사 노트 01 (b)·(e), 02 전체).

---

## OPEN — 수령·신설

| OPEN | 5A 처리 |
| --- | --- |
| `OPEN-ML-05` | 분류 표(D-M5-6) — 5A 가 표와 로드 자리, 값은 승인 |
| `OPEN-ADR-10`(닫힘) | ⑦ 규약으로 소비 |
| 신설 후보 `OPEN-5A-PY-CI` | CI 러너·오프라인 wheel 캐시의 자리 — 조사 02 |
| 신설 후보 `OPEN-5A-MYPY-ALLOWLIST` | 이식 모듈 strict 예외 목록과 해소 slice — 5B~5E 가 갱신 |
