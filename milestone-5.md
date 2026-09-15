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

**5A 종결 2026-09-11(사용자 승인)** — verifier r1 `not-ready`(high 2: forbidden 계약이 승인 통로의
간접 연쇄까지 막아 패키지가 비었을 때만 초록 · 생성 stub 이 패키지 트리 안이라 우회 import 경로가
존재) → 직접 import 만 금지 + 승인 통로 양성 fixture, 생성 위치를 패키지 트리 밖
`ml-engine/.contracts-generated/`로 옮겨 경로 자체를 제거(D-5A-0 (b) 문면 갱신) → r2
`ready-for-review`(허용 층 경유 간접 유입 셋 전부 붉음, 게이트 test 는 fixture 별 lint 거동 단언).
S-1~S-9 exit 0, pytest 156(기존 125 회귀 0), CI Python job 신설. **알려진 제한·OPEN**: wheel 설치본에서
`ml_engine.contracts` 재수출 불성립 → `OPEN-5A-WHEEL-BUILD-HOOK`(5E 전 빌드 훅, 5E/6C) ·
`OPEN-5A-SERVING-GRPC-EXCEPTION`(5E) · `OPEN-5A-MYPY-ALLOWLIST`(초기 0건, 5D) · `OPEN-5A-PY-CI`(러너
실행은 push 뒤). 정본 `reports/evidence/m5/5a/checklist.md`. **다음은 5B**(feature schema).

**5B 착수 2026-09-12(운영자 결정 2026-09-12 — D-5B-2 (a)·D-5B-7 legacy 값)** — base 는 PR #9 머지 커밋
`cc90f70`. 조사 실측: legacy 에 feature schema version·manifest·checksum 이 **없고**, skew 방지는 「같은 모듈
import + `feature_names` 정확 대조」 둘, 결측 다섯 축은 전부 조용한 접힘, unit/basis 검증 없음 → 5B 는
**대부분 신규 작성**이고 이식은 변환 산식·2단 수축·fail-closed 대조·`sample_scope` 기본값 금지 규율.
결정: 스키마 version `award-rate-features-v2` 신설(D-5B-1) · `denominator_source` 어휘 = wire
`BaseAmountProvenanceLabel` 5값, legacy 4값 enum 이식 없음(D-5B-2, 재학습 동반 — D-M2-10) · 결측·미지는
NaN + provenance, 기초금액·분모 결측은 행 거부(D-5B-3) · `Money` 다섯 성분 검증(D-5B-4) · feature manifest
sha256(canonical JSON)(D-5B-5) · 수축 원시 연산은 최하층 `features` 에 두어 5D 가 재사용(D-5B-6) · κ 둘은
`EncodingPolicy` 인자, 값 12.0·40.0 승인(D-5B-7) · fail-closed 대조·기본값 금지 이식(D-5B-8). acceptance 는
CI `ml-engine` job 전건(하네스 2026-09-12 규율). 정본 `reports/evidence/m5/5b/scope.md`, 설계 검토
`_workspace/m5-5b/02_design-review.md`. fixtures `ml-boundary-003/004` 재평가는 `OPEN-5B-FIXTURE-REEVAL`(curator).

**5B 종결 2026-09-12(사용자 승인)** — verifier r1 `ready-for-review`(medium: manifest 가 배열 순서에 따라 다른
checksum · 관측 0 인코딩이 `global_mean 0.0` 으로 접힘 · rollback 확인 명령 오기) → 생성 시점 정렬 불변식, `EncodingOutcome =
Built | NoObservations` 결과 타입, `FeatureColumn.range` 를 rows test 가 단언 → r2 `ready-for-review`(규칙표 9행 비트 동일
재현, 226 passed). 재작업 1회. 산출물: `ml_engine.features` 8 모듈(이식 4 — normalize·shrinkage·encoding·rows / 신규 4 —
schema·facts·vocabulary·manifest), sentinel 셋 제거. **알려진 제한·OPEN**: Python 가시성 한계(직접 생성 우회 — 5C·5D verifier
표적) · 어휘 전환으로 재학습 필요 · `OPEN-5B-FEATURES-FORBIDDEN`(`features` 에 DB/HTTP forbidden 미적용 → 5C 착수 계약) ·
`OPEN-5B-OBSERVATION-DOMAIN`(관측값 [0,1] 검증 → 5C) · `OPEN-5B-FIXTURE-REEVAL`(curator). **다음은 5D**(inference kernels —
운영자 결정 2026-09-12 (a); 착수 전건 D-M5-7 (a) golden curator 병행·D-M5-8 (a)·D-M5-9 확정).

**5D 착수 2026-09-12(운영자 승인 — D-M5-7 (a)·D-M5-8 (a)·D-M5-9 (a)·D-5D-5~9)** — base 는 PR #10 머지 커밋
`f5020aa`. 초안과 실측이 어긋난 일곱을 처분: 이식 대상 파일에 50줄 초과 함수 없음(분해 대상 0) · K5 에
ML-04 provenance 게이트 신설(`CleanAssessmentSample` 타입, D-5D-5) · ruff `BLE` 추가(D-5D-6) ·
`Diagnostics.shrinkage_weight`·`excluded_observations` 는 Python 결과 타입에만(wire 는 `OPEN-5D-DIAGNOSTICS-WIRE`,
D-5D-7) · 정책 값 실물 `inference-v1.yaml`(legacy 값, D-5D-8) · 5A 정책 표 배정 정정(D-5D-9) · `UnmeasurableReason`
wire 3값 미러 + `detail_code` 닫힌 enum(D-5D-2). `ArtifactManifestV1` 읽기 모델은 5D 소유, 5C 가 그 형태로 쓴다.
golden 은 curator 병행 레인(worktree `bid-vector-v2-m5cur`, `OPEN-5D-GOLDEN`). 정본 `reports/evidence/m5/5d/scope.md`,
설계 검토 `_workspace/m5-5d/02_design-review.md`.

**5D 종결 2026-09-13(사용자 승인)** — verifier r1 `ready-for-review`(medium 4: `LoadedArtifact` 직접 생성·`clamp_min ≤ 0`
정책의 커널 예외 누출·golden 무검증 초록·mypy 죽은 코드) → `_VerifiedBytes` 필수 인자·로더 불변식 확장·`JsonValue`
파싱 + `warn_unreachable`·`ROUND_HALF_UP`(D-5D-10) → r2 `ready`(medium 1: quantize 뒤 0 이 되는 `clamp_min` → `quantize_bid_rate`
단일 출처) → golden 14 case(curator, `ml-kernel-001~014`, 승인) 병합·통합 13 passed·011 skip(분포 엔진) — **golden 008 이
K6 분산 0 접힘을 잡아 `DEGENERATE_VARIANCE` 로 수정**(D-5D-11 `n == draw_count` 예외) → r3 `ready`(low 1). 재작업 2회.
351 tests(마커 분리 347+4), mypy strict + `warn_unreachable`, ruff `BLE`. **범위 판정**: 분포 엔진 조립(K5·K6 → 후보 3)은
5D in_scope 밖 — `OPEN-5D-DISTRIBUTION-ENGINE` → **5D-2**(운영자 결정 2026-09-13 (b) 분포 단독). **알려진 제한·OPEN**:
Python 가시성 2줄 우회 둘 · `std == 0` 통과 · 실 booster 결정성(`OPEN-5D-REAL-BOOSTER`, 5C) · `ArtifactManifestV1` writer(5C) ·
`OPEN-5D-DIAGNOSTICS-WIRE`(2F). 정본 `reports/evidence/m5/5d/checklist.md`.

**5D-2 착수 2026-09-13(운영자 결정 — D-5D2-1 (b) 분포 단독 · D-5D2-2~8 · 임계 값은 (c) OPEN)** — `OPEN-5D-DISTRIBUTION-ENGINE`
을 닫는 소형 slice. base 는 5D 종결 head `cefb19c`. wire `CompetitionSample` → 관문·밴드 정제(결과 타입, 사유별 계수) →
5D `admit_clean` → K6 추첨 분포 → 3계층 → K5 수축 → `predictive_std` → `bid_ratio = median(observed_bid_rate/center)`(legacy
환산 계수 미이식, D-5D2-6) → 후보 3. 가용성 임계(#28·#29)는 조립기와 **같은 함수**(legacy 「직접 호출은 표본 1건도 통과」 결함
제거). 서빙 엔진은 분포 하나 — legacy 실제 기본 경로 `historical` 은 V2 계약에 입력이 없고, M2 가 `CompetitionSample`·
`ReserveDrawObservation` 을 넣은 유일한 사유가 분포 predictor(D-2B-3); GBM 은 5D 이식물 그대로 두되 연결하지 않음(ML-05
게이트 미통과). `Diagnostics.shrinkage_weight`(ML-04 ②)·`agency_sample_count`·`agency_sample_below_threshold`, `IntervalSource.
POSTERIOR_PREDICTIVE`·`DistributionRelease` 는 내부 타입(wire 매핑 `OPEN-5D2-INTERVAL-SOURCE-WIRE`·`OPEN-5D2-RELEASE-FOR-DISTRIBUTION`,
2F/5E). golden 011 skip 해제가 종결 조건. 정본 `reports/evidence/m5/5d2/scope.md`, 설계 검토 `_workspace/m5-5d2/02_design-review.md`.

**5D-2 종결 2026-09-15(사용자 승인)** — verifier r1 `not-ready`(high: 표본 한 건의 빈/비수치 `Rate.fraction` 이
`InvalidOperation` 예외로 요청 전체를 죽임 — 저장소에서 wire `Rate` 를 파싱하는 유일 자리, test 0 · medium:
예비가격 `Money` 가 금액 > 0 만 검사) → `parse_rate` 관문(빈·비수치·비유한 → `BID_RATE_UNPARSEABLE`, 밴드 밖 →
`BID_RATE_OUT_OF_BAND`) + 5B 성분 규칙 넷 재사용(`RESERVE_PRICE_INVALID`) → r2 `ready`(27 입력 예외 0, 5B 규칙 7/7
일치, 402 passed, golden 14/14 skip 0). 재작업 1회. **계약 공백 둘 발견·등재**: ① wire `CompetitionSample` 에
표본별 기관·공종 축이 없어 3계층 수축을 서빙 경로에서 만들 수 없음 → **global-only**(`segment_support = GLOBAL`,
기관 표본 0 사실대로), `SegmentedSample.segment` 슬롯 선설치(미소비), ML-04 ② 는 서빙 경로 도달 불가 —
`OPEN-5D2-SAMPLE-SEGMENT`(2F additive) ② D-2B-8(「`fraction > 1` 위반」, 축 한정 없음) vs 표본 투찰비 밴드 상한 1.5 충돌
— `OPEN-5D2-BID-RATE-UPPER`(2F/5E 정본; 5D-2 는 밴드만, 교집합 통과라 fail-closed). `OPEN-5D-DISTRIBUTION-ENGINE`
해소. **다음은 2F**(M2 v1 additive 묶음: 표본 기관·공종 축 · `Diagnostics` 4 필드 · `IntervalSource.POSTERIOR_PREDICTIVE` ·
분포 엔진 `ModelRelease` 규약 · D-2B-8 축 정본) — 5E 전.

**5D-3 착수 2026-09-16(운영자 결정 — 2F 다음 (a))** — 3계층 소비 라운드. 2F(PR #16, 태그 `contracts/v1-approved-2026-09-16`)
가 올린 표본 축(`CompetitionSample.agency_id`·`category_code`)을 5D-2 의 `SegmentedSample.segment` 슬롯으로 채워 발주기관/공종/
전역 수축을 **서빙 경로(`serve_bid_rates`)** 에서 만든다 — ML-04 ② 충족, 5D-2 알려진 제한 1(`OPEN-5D2-SAMPLE-SEGMENT`)·8 해소.
결정 D-5D3-1~7: 정규화 문자열 동일 매칭(5B 판독기 공개 승격, 요청 축·표본 축 같은 함수) · 표본 축 결측 사유 `NOT_COLLECTED_YET`
만(그 외 표본 거부) · 요청 축 결측이면 그 계층 없음 · `SegmentMissing` 삭제(5B `Present | Missing` 로) · 전부 결측이면 5D-2 와
비트 동일 · golden corpus 무편집(011 수치를 `test_engine.py` 가 wire 요청으로 조립해 대조). base = PR #16 병합 커밋, 브랜치
`m5-5d3/2026-09-16`, `ml-engine/**` 만. 정본 `reports/evidence/m5/5d3/scope.md`, 설계 검토 `_workspace/m5-5d3/02_design-review.md`.

### Slice 5B — feature schema

- versioned feature name/order/type/range
- 기존 feature 변환 코드를 이식해 training/serving 공용 transform으로 단일화
- missing/unknown feature의 명시적 처리
- unit/basis validation
- dataset/feature manifest와 checksum

### Slice 5C — training/evaluation

- 기존 LightGBM training pipeline 이식과 hyperparameter 튜닝
- 시간 누수 없는 split, rolling/group holdout
- ~~기존 calibration 로직 이식~~ → **GBM OOF 잔차 std(후보 폭) 이식**, worst-segment report — **D-5C-1 개정(운영자 확인 대기 2026-09-12)**: legacy 의
  calibration 모듈 둘은 B 계보(ensemble) 의 Platt 확률 보정·group 통계이고 낙찰률 GBM 과 무관하다. Platt 는 자격 라벨을 요구하는데 D-M2-11 (a) 가 그 라벨을
  계약에서 뺐으므로 ml-engine 경계 안에 학습 입력이 없다(`OPEN-ML-02` 남은 물음 그대로 이월). 임의 축소가 아니라 상위 결정의 하류
- deterministic seed와 reproducible environment
- artifact manifest, metric, schema/code/dataset version
- promotion은 측정 결과를 만들 뿐 자동 운영 배포하지 않음

**5C 분할·5C-1 착수 2026-09-12(운영자 결정 대기: D-5C-0·1·2·4·7·9)** — base 는 PR #10 머지 커밋 `f5020aa`(5A·5B 실물, 5D 미포함). 레인
worktree `bid-vector-v2-m5c`·브랜치 `m5-5c/2026-09-12`, 5D(`m5-5d/2026-09-12`)와 병행 — 소스 겹침은 `pyproject.toml`·`tests/conftest.py`·이 문서 셋뿐이고
격리 규칙은 `reports/evidence/m5/5c/scope.md` 「레인 격리」. 조사 실측(legacy `ed4b06c`): 학습 경로가 **둘**(A 낙찰률 GBM / B ensemble)이고 Celery 트리거는 B 에만
있어 **GBM 에는 프로덕션 학습 트리거가 없다** · agency encoding 은 **OOF(학습 행렬)와 전 구간(artifact 표) 둘 다** 쓴다 · 폴드는 무작위(시간 누수는 cutoff·창이
막는다는 legacy 선언) · 5A 표가 5C 로 배정한 정책 값 넷은 A 계보 학습 경로에서 **소비 0** · 재현성은 선언만 있고 「같은 입력 → 같은 artifact」 test 가 없다 ·
평가 계통이 셋인데 승격 게이트는 가장 미성숙한 B 를 읽고, `guardrail/fallback None → 검사 스킵` fail-open 축 둘과 `--skip-promotion-gate` 무조건 우회가 있다.
**결정**: 5C 를 **5C-1**(학습 커널 — dataset 입구·corpus 승인·OOF·booster port·artifact writer·`TrainingSpec`·`policy/training-v1.yaml`)과 **5C-2**(평가·홀드아웃·
승격 측정·evaluation report·`policy/evaluation-v1.yaml`)로 가른다(D-5C-0) · A 계보만 이식(D-5C-1) · 하이퍼파라미터는 코드 선언 `TrainingSpec` + checksum 을
artifact 에(D-5C-2) · OOF 폴드 무작위 유지(D-5C-4, `OPEN-5C-OOF-TIME-DIRECTION`) · 행 거부·라벨 [0,1] 은 회계 + 거부(D-5C-5, `OPEN-5B-OBSERVATION-DOMAIN`
해소) · `min_training_rows 500`(D-5C-7, legacy-declared 미소비) · artifact 바이트 안에 자기 checksum 없음(D-5C-9 — **5D read model 과 조정 필요**,
`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`) · import-linter forbidden 을 `features`·`training`·`evaluation`·`registry` 로 확장(`OPEN-5B-FEATURES-FORBIDDEN` 해소).
acceptance 는 CI `ml-engine` job 전건. 설계 검토 `_workspace/m5-5c/03_design-review.md`. 정책 값 `reports/evidence/m5/5c/policy-values.md`.

**5C-1 종결 2026-09-13(사용자 승인 「승인 push, pr 진행」 — D-5C-0·1·2·4·7·9 추천안 확정, `OPEN-5C-POLICY-VALUES` 종결)** — verifier r1 `not-ready`(high 3:
`write_artifact` 가 release 신원을 호출자 인자에서 읽어 학습 dataset 과 다른 `dataset_id` 선언 artifact 가 나옴 · OOF 누수 test 가 「모든 행」 단언을 빼 30행 중 6행 누수를 통과시킴 ·
신설 import-linter 계약 블록을 지워도 test 전부 초록) → 단일 인자 `write_artifact(trained)`·legacy 두 단언 복원·양성 대조가 실제 `pyproject.toml` 을 읽도록 → r2
`ready-for-review`(세 high 전부 **변이 주입**으로 물음 확인 · `__all__` 54 → 51 추가 0). 재작업 1회. S-1~S-9 전건 exit 0, pytest 233 → 325(skip 0 — 실 LightGBM 재현성
test 실행), 재현성은 `OMP_NUM_THREADS` 네 값에서 sha256 동일. 산출물: `ml_engine.training` 11 모듈(이식 8 — spec·folds·residual·encoding_oof·booster·train·artifact_writer·
release / 신규 3 — corpus·dataset·policy) + `adapters/dataset_files.py`(`file://` 만) + `policy/training-v1.yaml` + import-linter forbidden 확장(`OPEN-5B-FEATURES-FORBIDDEN`
해소) + 라벨 [0,1] 회계(`OPEN-5B-OBSERVATION-DOMAIN` 해소). **알려진 제한·OPEN**: OOF 학습 구간 내부 시간 방향 없음(`OPEN-5C-OOF-TIME-DIRECTION`) · 호스트 간 스레드 수가 다르면
바이트 동일 미보장(D-5C-12) · Python 가시성(`TrainedArtifact` 직접 위조 — 단 신원이 본문과 어긋나는 artifact 는 못 만든다) · 중복 행은 dataset 생성 측 몫 ·
`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`(5D read model 조정 — 5D 세션에 미전달, 운영자 전달) · `OPEN-5C-ARTIFACT-ROUNDTRIP` · `OPEN-5C-5A-TABLE-REASSIGN`(5D 병합 뒤) ·
`OPEN-5C-MATURITY-SOURCE`·`OPEN-5C-CORPUS`(5C-2·curator) · CI(Linux) 그린은 PR 에서 확인. 정본 `reports/evidence/m5/5c/checklist.md`. **다음은 5C-2**(평가·홀드아웃·승격 측정·
evaluation report·`policy/evaluation-v1.yaml` — 조사 노트 02 의 ML-07 보강이 입력).

**5C-1 병합 2026-09-15 — PR #13(`e4abc90`, 사용자 「병합 진행」)** — 5D 병합(PR #12) 뒤 `d4727fc` 로 두 번 rebase(D-5C-9b: 바이트 안 `release.artifact_checksum` 은
blank-canonical sha256 — 5D read model 이 비어 있지 않은 문자열을 요구·등가성은 안 봄, 5D `load_artifact` 왕복 test 6 으로 `OPEN-5C-ARTIFACT-ROUNDTRIP` 종결) → verifier
r3 `not-ready`(high 1: 파생 값 셋 재계산 test 0 — 변이가 조용히 통과) → r4 `ready` → **PR code-reviewer(sonnet) 머지 불가(high 2: 최소 표본 게이트가 `build_row`
탈락 뒤를 재게이트 안 함 — 494/500 결측에서 6행 성공 artifact · 정책 로더가 `yaml.YAMLError` 를 예외로 흘림)** → 시정 → verifier r5 `ready`. 재작업 3회. CI 두 job 초록
(첫 실행은 evidence 의 패턴 어휘 축어 인용으로 `leakPatternGate` 붉음 → 파일 참조로 정정, 계약 S-10). 최종 456→466 passed 1 skipped. 리뷰 판정 셋이 PR 코멘트에.
**추가 OPEN**: `OPEN-5C-REJECT-ACCOUNTING`(재게이트 거부의 사유별 분해 — 5C-2·5E) · `OPEN-5C-YAML-ERROR-5D`(5D `inference/policy.py` 동일 구멍).

**5C-2 착수 2026-09-15(사용자 지시 「5C-2 착수」 · 운영자 확인 대기: D-5C2-1·3·7·9)** — base 는 PR #14 머지 커밋 `c669a71`(5C-1·5D·5D-2 실물). 레인 worktree
`bid-vector-v2-m5c2`·브랜치 `m5-5c2/2026-09-15`, 병행 레인 없음. 조사(02 §1-5·§2-6·§2-7): ML-07 판정식은 한 줄(`model_rmse < baseline_rmse ∧ paired_t < −2.58`, 비교 대상
`category_x_band`) · 임계는 **코드 상수 100%**(`settings` import 0 — 「CLI 를 두지 않는 이유」 성문화) · 유일한 실질 완화 경로는 `WindowPolicy` 주입(세 필드 public) + `--no-stability`·`--seed`
조합 · 「못 쟀다」는 별도 어휘가 아니라 bool 셋 조합 · 가장 성숙한 평가(A 계보)의 판정 소비자가 CLI 콘솔뿐. **결정**: 순수 커널은 `evaluation/`, 창마다 학습을 부르는 실행기만
`training/holdout.py`(layers `training > evaluation`, 둘 다 `inference` 금지 — D-5C2-1) · 성숙도는 **입력** `WeekMaturity(start, end, opened, settled)`(K7 계산은 5D, D-5C2-2) ·
임계 전부 `policy/evaluation-v1.yaml`(legacy 값 무변경, 낱개 인자·CLI·env 없음 — D-5C2-3) · 판정 3값 `Passed | Failed | NotEvaluable(reason, required_rows)` + `UNDERPOWERED`·
`SEED_UNSTABLE` 결과 승격(D-5C2-4) · seed 안정성 끌 수 없음(D-5C2-5) · 승격 측정은 latest-window 하나(D-5C2-6) · 홀드아웃 예측은 booster 직접(서빙 경로 동일성은
`OPEN-5C2-SERVING-PATH-PARITY`, D-5C2-7) · 경계 동시각은 평가측(D-5C2-8) · 세그먼트 축 `category`·`amount_band`, `published_floor` 없음(D-5C2-9) · 비율 분할 API 없음.
acceptance 는 CI `ml-engine` job 전건 + evidence 라운드 Kotlin `check`. 정본 `reports/evidence/m5/5c2/scope.md`, 설계 검토 `_workspace/m5-5c2/03_design-review.md`,
정책 값 `reports/evidence/m5/5c2/policy-values.md`.

### Slice 5D — inference kernels

- model predict adapter
- 기존 순수 커널 이식 — 추첨 분포·계층 수축·정산 성숙도 (수학은 유지, 결합만 제거). **반사 KDE·곡선
  빌더는 D-M5-9 (a)(2026-09-12)로 win-proxy 둘(D-M5-8 (a))과 같은 조건 — 도달 경로가 생길 때 이식**(ADR 0001 §4.1 개정 주석)
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
