# ADR 0001 — 목표 아키텍처: Kotlin modular application + Python ML engine

- **상태**: 제안됨 (M0 slice 0D) — 사용자 승인과 Codex `approve` 대기
- **작성일**: 2026-08-28
- **대응**: `milestone-0.md` §"Slice 0D" 결정 **1** (Kotlin modular application + Python ML
  engine, 그리고 service 재작성 / ML 재활용 경계)
- **legacy 기준 commit**: `ed4b06c` (인용은 전부 이 commit에서 직접 열었다)
- **관련 ADR**: 0006(모듈 경계) · 0003(두 runtime 사이의 전송) · 0007(래칫) · 0009(재활용 출처 기록)

---

## 1. 맥락

### 1.1 승인된 전략

`v2-지침서.md` §1이 레이어별로 다른 전략을 규정한다.

> **service/업무 레이어 — 재작성.** … 재작성의 목적은 **유지보수성 향상과 회귀 감소**이며,
> 기능 확장이나 언어 교체 자체가 아니다.
>
> **Python ML 엔진 — 재활용.** 기존 ML 구현은 완성 단계이고 활용 가능하다. 새로
> 설계하지 않고 기존 코드를 §3.2의 패키지 경계와 M2 계약으로 **이식·정리하고 필요한
> 튜닝을 수행**하는 것이 기본 전략이다.

이 ADR은 그 전략을 **아키텍처 결정으로 고정**하고, 경계를 어디에 긋는지와 그 경계가
무엇을 금지하는지를 적는다.

### 1.2 legacy에는 경계가 없다

legacy는 service와 ML이 **같은 프로세스 안에서 import로 연결**돼 있다.

```
git -C bid-vector grep -l 'from app.ai.predictors' ed4b06c -- app | wc -l   → 31
```

(재현: `commands.md` **C-5.4**)

프로세스 분리는 코드 경계가 아니라 **의존성 설치 여부**로 흉내 내고 있고, legacy가
그 이유를 자기 파일에 적어 두었다.

> `requirements/ml-training.txt:3-7` — 주석 블록 전체 (`git show ed4b06c:requirements/ml-training.txt`)
> ```
> # 낙찰률 GBM(Phase 2) 학습·추론. 런타임(requirements/runtime.txt)에는 넣지 않는다 —
> # API 프로세스가 ML 을 로드하면 상주 메모리가 다시 붙는다(#321 이 그 경로를 제거해
> # 1.07GiB → 172MiB 를 만들었다). 학습은 training-worker, 추론은 ML 큐 워커에서만 돈다.
> # 이 패키지가 없는 프로세스에서는 predictor 가 unavailable 로 떨어져 historical 로
> # 폴백하므로, 미설치는 장애가 아니라 선언된 상태다.
> ```

두 가지가 동시에 읽힌다. **경계가 필요하다는 실측**(1.07GiB → 172MiB)과, 그 경계를
**의존성 부재로 구현했을 때 생긴 조용한 품질 저하**(패키지가 없으면 예측기가 사라지고
historical로 폴백하는데, 그것이 "선언된 상태"로 정당화된다)다. 후자는
`v2-지침서.md` §4의 판정 계약이 `request_changes` 사유로 열거한 *"V2 Kotlin과 ML engine
사이에 업무 규칙이 중복되거나 **숨은 fallback**이 존재함"*의 실물이다.

### 1.3 이미 확정된 운영자 결정

**`OPEN-ML-01` — 결정 완료** (`reports/evidence/m0/0a2/decisions.md`, 운영자 2026-08-26):

> **결정**: 8개 커널 전부 Python `ml-engine`으로 보낸다. 경계 규칙은
> "수학 커널 = ml-engine / 업무 판정 = Kotlin".

같은 절이 근거를 적는다 — 8개가 전부 순수·stdlib 전용이고, 분리하면 학습·추론이 같은
피처 모듈을 import하는 **training-serving skew 방지 구조가 깨지며**, KDE는 손수 구현한
반사 보정 KDE라 라이브러리 대체가 불가하다.

그 skew 방지 구조는 실측으로 확인된다(`commands.md` **C-5.5**).

> `app/ai/predictors/award_rate_gbm.py:60-66` (서빙)
> ```
> from app.domain.award_rate_features import (
>     AWARD_RATE_FEATURE_NAMES,
>     SERVING_DENOMINATOR_SOURCE,
>     AgencyTargetEncoding,
>     AwardRateFeatureSpace,
>     normalize_feature_key,
> )
> ```
>
> `app/services/ml_training/award_rate_gbm.py:51-59` (학습 — 같은 모듈)
> ```
> from app.domain.award_rate_features import (
>     AWARD_RATE_FEATURE_NAMES,
>     CATEGORICAL_AWARD_RATE_FEATURES,
>     AgencyTargetEncoding,
>     AwardRateFeatureSpace,
>     AwardRateObservation,
>     build_agency_target_encoding,
>     normalize_feature_key,
> )
> ```

`P-ML-01`(`docs/discovery/regression-ledger.md` §8)이 이것을 **회귀가 아니라 유지할
예방책**으로 등재했다.

---

## 2. 결정

### D-1. 두 개의 runtime으로 시작한다

`v2-지침서.md` §3의 구조를 채택한다 — **Kotlin modular application** 하나와
**Python ml-engine** 하나. 업무별 배포 서비스로 미리 쪼개지 않는다.

Kotlin은 **하나의 Gradle 멀티모듈 애플리케이션**이다. 모듈 목록과 의존 방향은
**ADR 0006**이 소유한다.

### D-2. 경계 규칙 — 수학 커널은 ml-engine, 업무 판정은 Kotlin

`OPEN-ML-01` 결정을 그대로 채택한다. 파생되는 계약:

- Python 응답은 **후보·점수·불확실성·모델 근거**만 반환한다. 최종 `bid`/`review`/`skip`은
  Kotlin이 결정한다(`v2-지침서.md` §3.2).
- Kotlin이 이미 판정한 **자격·법정 하한을 Python이 다시 판정하지 않는다**(같은 절).
- 같은 규칙이 두 runtime에 존재하면 그중 하나는 회귀 지점이다 — 중복은 경계 위반이며
  ADR 0006의 architecture test가 표현할 수 있는 범위에서 기계로 막는다.

**성숙도 커널이 이 규칙의 판례다**: 성숙도 **계산**(곡선·임계 산출)은 ml-engine이
소유하고, 성숙도 값을 받아 내리는 **업무 판정**(embargo 적용 여부, 게이트 통과 여부)은
Kotlin이 소유한다(`decisions.md` `OPEN-ML-01` 절).

### D-3. service는 재작성, ML은 재활용

- **service/업무 레이어**: 기존 Python의 파일·클래스·함수 구조를 옮기지 않는다. 조사에서
  얻은 도메인 지식과 실패 사례로 **새 도메인 모델과 계약**을 만든다.
- **ML 레이어**: `v2-지침서.md` §3.2의 패키지 경계로 **이식·정리하고 튜닝**한다. 재작성은
  재활용이 경계 규칙을 만족시킬 수 없다고 **근거로 확인됐을 때만** 선택하며, 그 판단은
  slice 문서에 남긴다.

### D-4. 재활용해도 legacy 출력은 정답이 아니다

`v2-지침서.md` §1: *"코드 재활용과 기대값 판정은 별개 문제다. ML 코드를 재활용해도 그
출력이 정답이 되지 않는다."* 기대값은 **승인된 명세와 authoritative fixture**로 판정하고,
Python과 V2가 다르면 `Python defect` / `V2 defect` / `intentional redesign` /
`insufficient evidence` 중 하나로 판정해 근거를 남긴다(§6).

### D-5. serving 프로세스의 금지 목록

ml-engine의 serving 경로에는 **SQLAlchemy·DB driver·`requests` 기반 외부 수집·업무
entity가 없다**(`v2-지침서.md` §3.2). training과 serving은 **같은 feature schema와 변환
코드**를 사용한다 — §1.3의 skew 방지 구조를 새 경계에서도 유지한다는 뜻이다.

### D-6. ML 미가용은 조용한 폴백이 아니라 관측 가능한 상태다

§1.2가 인용한 legacy의 "미설치는 장애가 아니라 선언된 상태다"를 **승계하지 않는다.**
ML이 응답하지 못하는 상황은 Kotlin이 **명시적 상태로 표현**하고, 그 상태가 추천 품질의
조용한 저하로 나타나지 않아야 한다. 근거는 `v2-지침서.md` §9(*"KONEPS 수집 장애와 ML
장애가 업무 상태를 오염시키지 않음"*)와 `milestone-4.md`의 Codex 리뷰 항목(*"ML 장애가
위험한 추천으로 fail-open하는지"*)이다.

> **이 ADR이 정하지 않는 것**: 그 상태의 **이름과 하류 처리**(거부인지 `Unmeasurable`인지
> 저신뢰 표시인지)는 도메인 어휘의 문제이며 **0C 데이터 사전과 M2 계약**이 소유한다.
> 여기서 정하는 것은 *"조용한 폴백을 두지 않는다"* 하나다.

### D-7. 이식 코드에도 같은 규율을 적용한다

`v2-지침서.md` §3.2: *"이식한 코드는 신규 코드와 동일한 lint, typecheck, import boundary,
크기·복잡도 래칫을 적용받는다. '원래 코드라서' 예외를 두지 않는다."* 임계와 도구와
allowlist 절차는 **ADR 0007**이 소유한다.

### D-8. 재활용 출처를 기록한다 — 기록 위치는 미결

`v2-지침서.md` §3.2가 *"각 모듈에 재활용 출처(원본 파일 경로와 기준 commit)와 수행한
튜닝·수정 내용을 기록한다"*를 요구한다. **기록 의무는 이 ADR이 확정**하고,
**기록 위치는 ADR 0009의 `OPEN-ADR-10`**이 소유한다 — 운영자 결정 사항이다.

---

## 3. 대안

| # | 대안 | 판정 | 사유 |
| --- | --- | --- | --- |
| **A-1** | **단일 Python runtime 유지** — service를 재작성하지 않고 리팩터링만 한다 | **불채택** | `v2-지침서.md` §2가 관찰을 근거로 배제했다 — `app/**/*.py` 88,588줄, 500줄 초과 서비스 다수(최대 971줄), basis·rate·법정 하한 계열의 연속 수정. *"service 레이어는 파일 이동이나 언어 직역으로 복잡도가 줄지 않음"*. 이 판단은 사용자 승인된 지침서의 것이며 이 ADR이 새로 내리는 것이 아니다 |
| **A-2** | **단일 Kotlin runtime** — ML 수학도 JVM으로 재구현 | **불채택** | `OPEN-ML-01` 운영자 결정(2026-08-26)이 8개 커널 전부를 ml-engine으로 보냈다. 근거는 그 결정 절에 있다 — KDE가 **손수 구현한 반사 보정 KDE**라 라이브러리 대체가 불가하고, 옮기면 학습·추론이 같은 피처 모듈을 import하는 skew 방지 구조(§1.3)가 깨진다 |
| **A-3** | **업무별 배포 서비스로 분해(MSA)** | **불채택** | `v2-지침서.md` §3이 *"과도한 MSA가 아닌 두 개의 명확한 runtime으로 시작한다"*고 규정한다. 운영 형태가 **1인**으로 확정됐고(`decisions.md` `OPEN-OPS-05` 선행 질문 2), 분산 운영의 측정된 필요가 없다(§7) |
| **A-4** | **ML을 Kotlin 프로세스에 임베드**(JVM 안에서 Python 실행) | **불채택** | legacy가 같은 형태의 비용을 실측했다 — API 프로세스가 ML을 로드해 상주 메모리 **1.07GiB**였고 경로 제거로 **172MiB**가 됐다(§1.2 인용). 또한 `v2-지침서.md` §3.2의 serving 금지 목록을 프로세스 경계 없이 강제할 수단이 없다. **임베드 런타임 자체(GraalPy 등)는 이번에 조사하지 않았다** — 기각 근거는 legacy 실측과 경계 강제 가능성이지 그 런타임의 성능이 아니다 |
| **A-5** | **커널 일부만 Kotlin으로** (혼합 경계) | **불채택** | `OPEN-ML-01`이 (a)로 확정됐다 — 8개 **전부** ml-engine. 이 ADR은 그 결정을 인용할 뿐 재확정하지 않는다 |

---

## 4. 결과

### 4.1 M5 이식 대상이 고정된다

`OPEN-ML-01`이 지목한 8개 커널이 이식 대상의 핵심이다. **여덟 개 전부가
`app/domain/` 아래에 있다**(`commands.md` **C-5.3**).

| 커널 | 경로 (`ed4b06c`) |
| --- | --- |
| 반사 보정 KDE + Silverman | `app/domain/award_margin_distribution.py` |
| win-proxy 곡선·제약 argmax | `app/domain/award_landing_curve.py` |
| GBM feature space (학습·서빙 공용) | `app/domain/award_rate_features.py` |
| win-proxy 추정량 | `app/domain/award_landing_distribution.py` |
| 사정률 계층 수축 사후분포 | `app/domain/assessment_shrinkage.py` |
| 4/15 추첨 분포 닫힌식 | `app/domain/reserve_draw_distribution.py` |
| 정산 성숙도 커널 | `app/domain/settlement_maturity.py` |
| 곡선 빌더 | `app/domain/award_landing_curve_builders.py` |

### 4.2 래칫 사전 조사 — 결정을 막는 것은 없다

`milestone-0.md` §"추가 조사 항목"이 요구한 조사를 수행했다. 대상은 `app/ai/predictors/`,
`app/services/ml_training/`, `app/services/ml_release/`이고, 기준은 `v2-지침서.md` §5의
**함수 50줄 · 파일 500줄**이다. 수치는 전부 `ed4b06c`에 대한 것이며
`commands.md` **C-5**가 그 수를 내는 명령과 출력을 담는다. 계측 정의(데코레이터 제외,
docstring 포함, `end_lineno - lineno + 1`)도 같은 자리에 있다.

| 측정 | 세 디렉터리 | 8개 커널 |
| --- | ---: | ---: |
| 파일 수 | 56 | 8 |
| **파일 500줄 초과** | **0** | **0** |
| **함수 50줄 초과** | **29** | **0** |
| 최대 파일 줄 수 | 491 | 460 |

**따라서 파일 축 위반이 없고, 판단이 필요한 것은 함수 축 29건뿐이다.** 그 29건의 전수와
파일별 결합 지점, M5의 「이식 시 분해 / allowlist」 양쪽 재료는 **C-5.2**에 있다.

**M5에 넘기는 판단 순서**(이 ADR의 권고이며 결정이 아니다): ① 이식 대상 목록 확정 →
② 결합 제거 → ③ 그래도 남은 함수에 대해서만 분해/allowlist 판정. 근거는 29건 중 상당수가
**이식 여부가 먼저 정해지면 크기 문제가 소멸하는 인프라·배포 자동화 코드**라는 실측이다
(`app/services/ml_release/rollout.py`가 `subprocess`·`urllib`·SQLAlchemy `Session`·업무
서비스 지연 import를 동시에 갖는 것이 그 예다 — C-5.2). **순서를 뒤집으면 이식하지도 않을 코드에
allowlist 사유를 쓰게 된다.**

### 4.3 파일 래칫만으로는 크기를 강제하지 못한다

legacy가 **mixin 합성으로 파일 한도를 우회한 형태**를 스스로 문서화한다.

> `app/services/ml_release/__init__.py:1-10` — 모듈 docstring 전체
> ```
> """Manifest-backed ML artifact promotion — public surface.
>
> ``MLReleasePromotionService``, ``MLReleasePromotionRequest`` and
> ``RemoteObjectStorageClient`` keep their historical import path
> (``from app.services.ml_release import ...``). The service body was
> decomposed into responsibility mixins (base / signing / gate / preflight /
> manifest / rollout); this ``__init__`` composes them. The split is a pure
> move — every method body is the original ``MLReleasePromotionService``
> member, relocated verbatim, so signing, gate judgement and sha256
> verification are byte-identical and behaviour is unchanged."""
> ```

합성된 클래스의 실제 크기는 파일 한도를 크게 넘는다(C-5.2). **이 관찰은 ADR 0007로
넘긴다** — `OPEN-ADR-06`(래칫 축에 클래스/타입 크기를 넣는가).

### 4.4 서비스 계약과 검증

- **전송**: 두 runtime 사이의 계약과 전송은 **ADR 0003**이 소유한다.
- **모듈 경계**: Kotlin 내부 모듈과 의존 방향은 **ADR 0006**이 소유한다.
- **완료 판정**: `v2-지침서.md` §9의 완료 정의가 이 ADR의 acceptance다 — 특히 *"Python
  serving은 DB와 업무 규칙 없이 모델 추론만 수행"*, *"재활용한 ML 코드가 새 패키지 경계·
  M2 계약·래칫을 통과하고, 출처와 튜닝 내역이 기록됨"*.

---

## 5. 이 ADR이 등록하는 `OPEN`

`capability-map.md` §12에 등록하지 않는다 — 그 파일은 이 slice의 `out_of_scope`다.
`regression-ledger.md`의 `OPEN-REG-01`~`05`와 같은 형태로 이 문서에만 있으며, §12 통합은
후속 slice의 일이다(`reports/evidence/m0/0d/scope.md`).

### `OPEN-ADR-01` · Spring Boot 세대

- **결정 필요 사항**: V2가 어느 Spring Boot 라인으로 간다고 전제하는가.
- **왜 미결인가**: `v2-지침서.md` §5는 *"Kotlin 2.x와 **현재 지원되는** Spring Boot 3.x
  조합을 M1에서 버전 고정한다"*고 적었으나, **조사 시점에 그 조건을 만족하는 3.x가
  없다** — Spring Boot 3.5의 OSS 지원은 2026-06-30에 종료됐고(endoflife.date/spring-boot),
  실제로 2026-08-20~21 릴리스 웨이브에 4.1.1·4.0.8은 있고 3.5.x는 없다(마지막 3.5.16이
  2026-06-25, GitHub Releases API). 3.4 이하는 더 먼저 끝났다.
  **endoflife.date는 Spring 공식 출처가 아니다** — 릴리스 관측이 이를 보강하지만 공식
  릴리스 캘린더 페이지는 표가 본문에 렌더링되지 않아 조사에서 직접 인용하지 못했다.
- **선택지**: (a) OSS 지원이 끝난 Boot 3.5로 고정 (b) Boot 4.x로 이동 (c) 상용 지원 전제.
- **선택을 미뤄도 되는 근거**: 후보 라이브러리 9종 중 **Boot 3.x·4.x 양쪽 아티팩트를
  제공하는 것이 4종**(JobRunr · db-scheduler · ShedLock · Resilience4j)이고 Spring
  Modulith는 라인을 분리해 양쪽을 유지한다. **이 선택을 미뤄도 후보 집합이 바뀌지 않는다.**
- **소유**: M1 버전 고정. 이 ADR은 조합이 성립하는지에 대한 사실만 남긴다.

**부수 사실(이 ADR에 기록해 두는 것)**: Boot 3.5.x BOM의 관리 Kotlin은 **1.9.25**이고
Boot 자체 빌드가 Kotlin 2.0.0-Beta1 이상을 `prohibit`한다(*"it exceeds our baseline"*).
**그럼에도 Kotlin 2.x는 Boot 3.5의 공식 경로다** — Boot 3.5 문서가 *"requires at least
Kotlin 1.7.x"*를 규정하고 Gradle에서 *"the Spring Boot plugin automatically aligns the
`kotlin.version` with the version of the Kotlin plugin"*이라 명시한다.
**따라서 "Boot 3.x가 Kotlin 2.x를 검증했다"고 쓰면 사실이 아니다** — 애플리케이션이
상향 정렬한 구성이다.

### `OPEN-ADR-02` · `v2-지침서.md` §2.1의 ML 앵커

- **결정 필요 사항**: §2.1의 ML 조사 앵커에 `app/domain/`을 추가하는가.
- **근거**: §2.1의 ML 행은 `app/ai/predictors/`, `app/services/ml_training/`,
  `app/services/ml_release/` 셋이고 `milestone-0.md`의 추가 조사 범위도 같다. 그런데
  §4.1의 **8개 커널이 전부 그 셋 밖**이다(`app/domain/`). 재활용 가치가 가장 높은
  2,166줄이 계속 앵커 밖에 남는다.
- **선택지**: (a) §2.1에 `app/domain/` 추가 (b) 앵커는 탐색 시작점일 뿐이므로 그대로 둔다.
- **소유**: 지침서 개정 권한은 운영자에게 있다.

### `OPEN-ADR-03` · "업무 판정"이 ML 승격 게이트를 포함하는가

- **결정 필요 사항**: `v2-지침서.md` §3.2가 이식 경계에서 잘라내라고 한 **"업무 판정"**이
  `app/services/ml_release/gate.py`의 **모델 승격 게이트 판정**을 포함하는가.
- **관찰**: 그 게이트는 pass/fail과 `reasons`를 만든다. 그러나 그것은 **입찰 업무 판정이
  아니라 ML-ops 판정**이다(`capability-map.md` ML-07이 소유하는 축).
- **파급**: 포함하면 `gate.py`가 이식 대상에서 빠지고, §4.2의 함수 축 29건 중 3건이
  그와 함께 소멸한다.
- **소유**: 0C 데이터 사전 또는 M5 이식 계약.

### `OPEN-ADR-04` · 규칙표 4파일은 수학 커널인가 정책 데이터인가

- **대상**: `app/ai/predictors/legal_floor_spec.py` · `procurement_band_rules.py` ·
  `rate_band_spec.py` · `blend_tables.py`.
- **관찰**: 넷 다 외부 import가 사실상 없는 **순수 규칙표/spec**이다(C-5.2). 수학이 아니라
  **정책 데이터**로 보인다.
- **파급**: 정책 데이터면 `v2-지침서.md` §5(*"도메인 정책 값은 versioned policy
  데이터로"*)에 따라 **Kotlin `decision` 모듈의 정책 테이블**로 가고 ml-engine에 남지
  않는다. D-2의 경계 규칙이 이 분류에 답을 주지 않는다 — 규칙표는 커널도 판정도 아니다.
- **소유**: 0C 데이터 사전(정책 데이터 축) 또는 M5.

### `OPEN-ADR-05` · 학습 코퍼스 필터가 §3.2 위반인가

- **대상**: `app/services/ml_training/award_landing_ladder.py`.
- **관찰**: 이 파일이 `is_floor_judgeable`·`resolve_floor_applicability`·
  `is_plausible_assessment_rate`·`plausible_published_floor_rate`·`get_reliable_base` 등
  **업무 술어를 직접 부른다**(import와 호출 지점은 C-5.2). 파일 docstring은 그것이
  의도라고 적는다 — *"사다리는 신설하지 않고 재사용한다 … 각 단계는 이미 이 저장소가
  소유한 단일 출처 술어를 그대로 부른다"*.
- **왜 판정이 갈리는가**: `v2-지침서.md` §3.2는 *"Kotlin이 이미 판정한 자격·법정 하한을
  Python이 다시 판정하지 않는다"*고 하는데, 여기서의 용도는 **학습 코퍼스 필터**(관측을
  남길지 버릴지)이지 사용자에게 내리는 판정이 아니다. **같은 술어가 두 목적에 쓰인다.**
- **선택지**: (a) 학습 파이프라인의 필터는 §3.2의 "업무 판정"이 아니다 — 단, 술어의 단일
  출처가 Kotlin이면 학습 시점에 그 값을 **데이터로 받아야** 한다 (b) 위반이므로 이식
  대상에서 제외하고 학습 코퍼스 구성을 Kotlin이 소유한다.
- **소유**: M2 계약(학습 데이터셋 계약) 또는 M5.

---

## 6. 확인하지 않은 것

- **8개 커널의 import 결합을 실측하지 않았다.** 이번 조사는 그 여덟에 대해 파일·함수
  크기만 쟀다. *"8개 전부 순수·stdlib 전용"*은 `decisions.md` `OPEN-ML-01` 절과 0A ML
  조사 노트의 주장이며 이 slice가 재확인하지 않았다.
- **`tests/design_ratchet_baseline.json`과 교차 검증하지 않았다.** §4.2의 수치는 AST
  재측정 단독 근거다.
- **분해 후 예상 줄 수는 전부 추정이다** — 실제 분해를 수행해 잰 값이 아니다(C-5.2).
- **결합도 축을 재지 않았다.** `v2-지침서.md` §5는 fan-in/fan-out, public API 수, 순환
  의존, duplicate mechanical helper를 함께 재라 요구한다. 이번에 잰 것은 줄 수와 import
  결합뿐이다.
- **임베드 런타임(GraalPy 등)을 조사하지 않았다** — A-4의 기각 근거는 legacy 실측과 경계
  강제 가능성이며 그 런타임의 성능 측정이 아니다.
