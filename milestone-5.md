# 마일스톤 5 — 독립 Python ML engine

## 목표

LightGBM/KDE 관련 수학과 모델 생명주기를 독립 Python package로 세운다. 기존 ML 구현은
완성 단계이므로 **재활용이 기본 전략**이다. 성숙한 기존 코드를 새 패키지 경계와 M2 계약으로
이식하고, M0에서 승인된 알고리즘·평가 명세에 맞게 튜닝한다.

- 재활용 대상: LightGBM 학습/추론, KDE density·optimization, feature 변환, 평가·calibration
  로직.
- 이식하지 않는 것: ORM/DB session, service 설정 객체, Celery task, 업무 판정, 알림·로깅 결합.
  결합을 떼어내기 위한 최소 리팩터링은 이식 작업의 일부다.
- 재작성은 재활용이 경계 규칙(serving 순수성, training/serving 공용 transform, 계약 준수)을
  만족시킬 수 없다고 확인됐을 때만 선택하고, 그 근거를 slice 문서에 남긴다.
- 코드를 재활용해도 기존 predictor 출력은 정답이 아니다. 기대값은 승인된 명세와
  authoritative fixture로 판정한다.

## 선행 조건

- M0의 ML capability·평가 명세 승인
- M2 proto와 provider contract 승인
- ML authoritative/observed corpus 준비
- 재활용 대상 ML 모듈과 잘라낼 결합 범위의 M0 조사 결과

## 구현 대상

### Slice 5A — package와 import boundary

- `contracts`, `features`, `training`, `evaluation`, `inference`, `registry`, `serving`,
  `adapters`
- serving/training dependency 분리
- Ruff, typecheck, pytest, import-linter, size/complexity ratchet — 이식한 코드에도 동일 적용
- `serving`의 DB/HTTP 수집/business module import 금지 gate
- 모듈별 재활용 출처(원본 파일 경로, 기준 commit)와 이식 중 수정 내역 기록

**5A 착수 2026-09-11(운영자 결정 2026-09-11 「추천안 대로 진행」 — D-M5-1~4·6 (a), D-5A-0 (b))** —
base 는 4B-6a 병합 뒤 `main`(`d281329`, 2A~2E 골격 포함). `uv`(lock + extras 분리)·mypy strict(이식
모듈만 allowlist, 사유·해소 slice 명시)·legacy 래칫 이식(함수 50/파일 500/`dict[str, Any]` 경계 0,
baseline 완화 없음)·import-linter layers+forbidden(`serving.grpc` 만 grpcio 허용)·정책 값 33 분류 표
승인(정책 23·환경 6·미분류 4 — 값은 5C·5D 가 옮긴다, 정본 `reports/evidence/m5/5a/policy-values.md`)·
생성 stub 은 `ml_engine/contracts/_generated/`(VCS 밖)에 두고 `ml_engine.contracts` 재수출 하나만
import 허용. CI 에 Python job 신설(Kotlin job 무편집). 위협 모델 경계·우회 (1)~(8)·설계 검토는
`reports/evidence/m5/5a/scope.md`·`_workspace/m5-5a/02_design-review.md`. 레인 `m5-5a/2026-09-11`,
Kotlin lane 과 소스 겹침 0.

### Slice 5B — feature schema

- versioned feature name/order/type/range
- 기존 feature 변환 코드를 이식해 training/serving 공용 transform으로 단일화
- missing/unknown feature의 명시적 처리
- unit/basis validation
- dataset/feature manifest와 checksum

### Slice 5C — training/evaluation

- 기존 LightGBM training pipeline 이식과 hyperparameter 튜닝
- 시간 누수 없는 split, rolling/group holdout
- 기존 calibration 로직 이식, worst-segment report
- deterministic seed와 reproducible environment
- artifact manifest, metric, schema/code/dataset version
- promotion은 측정 결과를 만들 뿐 자동 운영 배포하지 않음

### Slice 5D — inference kernels

- model predict adapter
- 기존 KDE density/optimization 커널 이식 (수학은 유지, 결합만 제거)
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
- 이식한 모듈마다 출처(파일·commit)와 수정·튜닝 내역이 기록됨
- 이식한 코드가 신규 코드와 동일한 lint/typecheck/import boundary/래칫을 통과

## Codex 독립 리뷰

> **2026-09-04 운영자 결정:** 아래 관점은 Phase 4 `verifier` 가 적용한다. Codex 리뷰는 코드 slice 의
> 기본 경로가 아니며 운영자가 명시 요청할 때만 건다. 완료 조건의 「Codex `approve`」는
> 「verifier `ready-for-review`」로 읽는다.

- 이식 과정에서 service 결합(ORM, DB session, 설정 객체, Celery task, 업무 판정)이 함께
  딸려 왔는지
- 재활용한 코드가 경계 규칙과 래칫을 우회하는 예외로 처리됐는지
- 재활용 출처 기록이 실제 파일·commit과 맞는지
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
