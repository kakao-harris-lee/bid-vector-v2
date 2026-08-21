# 마일스톤 5 — 독립 Python ML engine

## 목표

LightGBM/KDE 관련 수학과 모델 생명주기를 새 Python package로 구현한다. 기존 Python
service 코드를 잘라 옮기는 것이 아니라, M0에서 승인된 알고리즘 명세와 M2 계약을 기준으로
training과 serving을 다시 구성한다.

## 선행 조건

- M0의 ML capability·평가 명세 승인
- M2 proto와 provider contract 승인
- ML authoritative/observed corpus 준비

## 구현 대상

### Slice 5A — package와 import boundary

- `contracts`, `features`, `training`, `evaluation`, `inference`, `registry`, `serving`,
  `adapters`
- serving/training dependency 분리
- Ruff, typecheck, pytest, import-linter, size/complexity ratchet
- `serving`의 DB/HTTP 수집/business module import 금지 gate

### Slice 5B — feature schema

- versioned feature name/order/type/range
- training/serving 공용 transform
- missing/unknown feature의 명시적 처리
- unit/basis validation
- dataset/feature manifest와 checksum

### Slice 5C — training/evaluation

- LightGBM training pipeline
- 시간 누수 없는 split, rolling/group holdout
- calibration과 worst-segment report
- deterministic seed와 reproducible environment
- artifact manifest, metric, schema/code/dataset version
- promotion은 측정 결과를 만들 뿐 자동 운영 배포하지 않음

### Slice 5D — inference kernels

- model predict adapter
- KDE density/optimization
- 최소 표본, singular input, NaN/Infinity 처리
- optimization objective별 후보와 diagnostics
- 업무 법정 하한/자격/최종 결정은 구현하지 않음

### Slice 5E — gRPC serving

- M2 generated servicer
- model preload와 readiness
- request validation과 Numpy conversion
- deadline/cancellation/status mapping
- model release/checksum/feature schema 응답
- graceful shutdown과 bounded concurrency

## 완료 조건

- training/serving이 동일 feature transform과 schema를 사용
- clean environment에서 동일 manifest/seed 입력이 재현 가능한 artifact/metric 생성
- serving image/package에 DB driver와 service ORM이 없음
- 금지 import mutation이 CI에서 실패
- artifact checksum 불일치 시 readiness/inference fail-closed
- M2 Kotlin consumer와 provider contract 통과
- 오류·최소 표본이 0점/성공으로 변환되지 않음
- 승인된 ML metric threshold 충족 또는 `not-promotable`로 명시

## Codex 독립 리뷰

- 기존 Python service code가 구조째 복사됐는지
- training-serving skew와 data leakage가 차단됐는지
- feature order/version 검증이 실제 inference path에 연결됐는지
- DB 없는 serving 제약이 선언뿐인지 CI가 강제하는지
- model score를 업무 확률/최종 verdict로 과장하는지
- artifact provenance로 결과를 재현할 수 있는지

## 범위 밖

- Kotlin 업무 판단
- 운영 artifact 승격/배포
- 운영 DB 직접 학습 query
- 기존 predictor 출력과 무조건 동일하게 만들기
