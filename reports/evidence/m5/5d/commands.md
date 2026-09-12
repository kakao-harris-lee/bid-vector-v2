# M5/5D — commands.md

base `f5020aa982e6c5ade01f1660c0568dcd75c2fa7e`(PR #10 머지 = origin/main, 5A·5B 실물 포함)
· 계약 `2bf3bd0`(착수 계약 고정) → `619cc5a`(verifier r1 반영 지시) → `b9a4e4b`(verifier r2
반영 지시, F-1~F-4) · 코드 커밋 `2a1bae7`(최초) → `145847c`(verifier r1 M-1/M-2/M-4·L-1/
L-2/L-3 수정) → `4ad2de7`(verifier r2 F-1 — `rounding.py` 신설, `policy.py` quantize 뒤
`clamp_min>0` 불변식) → `3a40bc3`(golden 통합 M-3 — `ml-kernel-001~014` 소비 + K6
degenerate-variance 발견 수정) → 이 커밋(golden 어댑터 식별자 개명 — 아래 「golden 식별자
개명」 참고, 게이트 술어 변경 아님). golden corpus 는 curator 병행 레인이 승인·병합
(`5ef6eb8`). 로컬: `uv`(pyenv `3.12.2`, `.python-version` 3.12 과 `requires-python`
두 자리 일치 — S-9). 전건 재실행(부분 게이트 없음) — M-3 은 게이트 술어(K6 admission
기준) 변경이라 표 전체를 다시 돌렸다.

## S-1 ~ S-9 (scope.md acceptance_commands 순서, M-3 뒤 재실행)

| # | 명령(요약) | exit | 핵심 한 줄 |
| --- | --- | --- | --- |
| S-1 | `uv sync --frozen --all-extras` | 0 | `Audited 33 packages`(변경 없음) |
| S-1b | `uv sync --frozen --extra serving --no-dev` + 5개 import 실패 확인 + 복구 | 0 | sqlalchemy·psycopg·requests·httpx·celery 전부 부재 확인, 복구 후 all-extras 정상 |
| S-2 | `uv run ruff check .` + `uv run ruff format --check .` | 0 | `All checks passed!` · `71 files already formatted`(golden 어댑터·test 신설로 +1) |
| S-3 | `uv run mypy --strict src/ml_engine` | 0 | `Success: no issues found in 27 source files`(파일 수 불변 — golden 은 `tests/` 라 S-3 스캔 범위 밖, `_verify_checksum`·`_VerifiedBytes` private import 는 test 쪽에서만 발생) |
| S-4 | `uv run lint-imports` | 0 | `Contracts: 5 kept, 0 broken`(45 files, 125 dependencies) |
| S-5(scope.md 문면, 마커 없음) | `uv run python -m pytest tests -q` | 0 | **351 passed, 1 skipped**(`ml-kernel-011`, `OPEN-5D-DISTRIBUTION-ENGINE`) — base 336 + golden 13 case·1 manifest-존재 test(신규 14) + reserve_draw 회귀 2 + 기존 golden skip test 1건 제거(케이스별 test 로 대체) |
| S-5(판정 기준) | `uv run python -m pytest tests -q -m "not legacy_parity"` | 0 | **347 passed, 1 skipped, 4 deselected** |
| S-8(관측 전용) | `uv run python -m pytest tests -q -m legacy_parity` | 0 | **4 passed**(legacy 산식 재현 관측 — 판정 근거 아님, 무변경) |
| S-6 | `uv run python tools/design_ratchet.py --check` | 0 | 설계 래칫 위반 없음(allowlist 편집 없음 — golden 어댑터·test 도 함수 50줄 이내) |
| S-7 | `reuse_provenance_check.py` + 양성 대조(`reuse-mismatch.md`) | 0 / 1 | 정상: 위반 0(`_adapter.py`·golden test 는 신규 작성, `Reuse:` 포인터 대상 아님) · 양성: exit 1(회귀 없음) |
| S-9 | python 버전 두 자리 대조 | 0 | assert 통과(출력 없음) |

## golden 통합(M-3) case 별 결과

| case | 결과 | 비고 |
| --- | --- | --- |
| `ml-kernel-001` | passed(001·002 합본 test) | `segment_availability(0, policy)` → `Unmeasurable(UNTRAINED_SEGMENT, NEVER_TRAINED)` |
| `ml-kernel-002` | passed(001·002 합본 test) | `segment_availability(23, policy[threshold=24])` → `Unmeasurable(INSUFFICIENT_SAMPLES, SHALLOW_SEGMENT)`, detail 이 001 과 다름을 대조 |
| `ml-kernel-003` | passed | `segment_availability(24, policy[threshold=24])` → `Available()`(경계 `>=`) |
| `ml-kernel-004` | passed | `gbm_min_category_rows=0` 선언 → `max(1,0)=1` 클램프, rows=0 은 여전히 거부·rows=1 은 통과 |
| `ml-kernel-005` | passed | `_verify_checksum`(private, 알려진 제한 13) — intact 는 `_VerifiedBytes`, 1바이트 변조는 `ArtifactRejected`. 두 probe 의 바이트 길이 동일·차이 1바이트 직접 계산해 대조 |
| `ml-kernel-006` | passed | `verify_feature_names` — 재배열(`NameMismatch`)·진부분집합(`NameMismatch`) 모두 거부, 원순서만 accepted |
| `ml-kernel-007` | passed | `require_declared`+`resolve_schema` — 지원/미지원/미선언 세 probe 모두 판정 일치 |
| `ml-kernel-008` | passed | K6 경계 7 종 전부 `Unmeasurable`, 예외 0, 값 미방출 0 — **degenerate-variance probe 가 코드 수정을 유발**(아래 참고) |
| `ml-kernel-009` | passed | `build_scenario_candidates`(synthetic z=1.25·클램프 0.6/1.3·digits 4) 두 번(스레드 수 1·4 대응, 순수 함수라 자명하게 동일) → `"0.8700"`/`"0.9200"`/`"0.9700"` 문자열 완전 일치 |
| `ml-kernel-010` | passed | `admit_clean`(6 표본 중 3 비-CLEAN 제외) → `aggregate_level_observation`의 mean `quantize_bid_rate(mean,4) == Decimal("1.0100")` |
| `ml-kernel-011` | **skipped**(명시) | `reason="OPEN-5D-DISTRIBUTION-ENGINE — 5D-2 가 소비"` — 팀장 지시대로 부분 단언 없이 skip |
| `ml-kernel-012` | passed | `build_weekly_maturity` — 03-16 주(개찰 0) 는 표에 없음(`NoObservation`), 03-09 주(0/2) 는 `Observed(ratio=0.0)`로 값·표 등재가 갈림 |
| `ml-kernel-013` | passed | `exact_draw_mean_distribution`·`draw_mean_moments` 두 경로 평균·분산이 정확히 같음(`sourceCount=15`·`supportSize=1365`), `std == float(긴 십진 문자열)` 정확 일치(둘 다 `math.sqrt(1100)`의 올바르게 반올림된 double) |
| `ml-kernel-014` | passed | `week_start_utc` 네 순간 — KST 월요일 00:00 경계, 반개구간(`[start,end)`) 방향 확인 |

## K6 degenerate-variance 수정(golden 유발, `ml-kernel-008`)

`draw_mean_moments([1_000_000.0]*5, draw_count=4)`(모분산 0, `n=5 != k=4`) — 수정 전:
`(1000000.0, 0.0)`(legacy 그대로, 조용히 값 방출) → golden 기대값과 불일치 확인. 수정 후:
`Unmeasurable(INSUFFICIENT_SAMPLES, DEGENERATE_VARIANCE)`. `n == draw_count`(전부 뽑는
경우, 조합이 하나뿐이라 분산 0 이 구조적으로 항상 참) 은 예외로 유지:
`draw_mean_moments([1_000_000.0]*4, draw_count=4) == (1000000.0, 0.0)`(정상값, 거부 아님)
— 둘 다 `test_reserve_draw.py`에 회귀 test 로 고정.

## golden 식별자 개명(팀장 지시, 이 evidence 와 같은 커밋의 코드 변경)

`_adapter.py`·`test_kernel_golden.py`의 식별자 셋이 루트 `leakPatternGate`(Kotlin `check`
job, `reports/evidence/` 스캔)가 쓰는 게이트 어휘 중 하나와 겹쳐 새 매치를 냈다(값 노출이
아니라 이름 자체의 겹침). 부호 표기(`"NEGATIVE"`/`"NEUTRAL"`/`"POSITIVE"`) 변환 함수,
provenance 라벨 변환 함수, `test_kernel_golden.py`의 예비가격 리터럴(`"NaN"`/`"Infinity"`
문자열 토큰 포함) 변환 함수 — 이 셋의 이름에서 겹치는 낱말만 제거하고 의미는 그대로
`sign_marker_to_int`·`provenance_from_label`·`_reserve_price_literal`로 개명했다. 재확인:
`grep -rniE -f config/quality/leak-patterns.txt ml-engine/tests/inference` → exit 1(매치
0). pytest `tests/inference/golden` 13 passed·1 skipped(개명 뒤 회귀 없음).

## verifier r2 재검증 세부(F-1·F-2, 회귀 없음 재확인)

- **F-1** 재현 YAML 둘 모두 `PolicyRejected` 확인 — `scenario.bid_rate_digits=1` +
  `scenario.clamp_min=0.04` → `PolicyRejected("scenario.clamp_min(0.04)은 scenario.
  bid_rate_digits(1) 자리로 quantize 한 뒤에도 0보다 커야 한다: quantize 결과 0.0")`.
  `scenario.bid_rate_digits=4`(출하 기본) + `scenario.clamp_min=0.00001` → 같은 형태의
  `PolicyRejected`(quantize 결과 `0.0000`). 출하 정책(`clamp_min=0.7`, `digits=4`)은
  `quantize_bid_rate(Decimal("0.7"), 4) = Decimal("0.7000") > 0`이라 영향 없음.
- **F-2** `LoadedArtifact(manifest, _VerifiedBytes(b"anything"))`(모듈 밖 `_VerifiedBytes`
  직접 import) — `mypy --strict` 재실행 clean(거부 없음) · 런타임 실행: `LoadedArtifact`
  정상 생성(체크섬 검증 0 회). checklist.md (2b) 행·알려진 제한 12 에 등재.

## verifier r1 재검증 세부(M-1·M-2·M-4·L-1~L-3, 회귀 없음 재확인)

- **M-1** `LoadedArtifact(some_manifest)` — 1 인자만 주면 런타임 `TypeError`. mypy strict
  도 `error: Missing positional argument "verified" in call to "LoadedArtifact"
  [call-arg]`로 정적 거부.
- **M-2** `scenario.clamp_min = -1.0` 로드 → `PolicyRejected`(정책 로드 단계에서 막힘).
  음수 weight(합 1 유지)·`min_predictive_std=0.0` 도 각각 `PolicyRejected`.
- **M-4** `warn_unreachable=true` 하 `mypy --strict` clean — `JsonScalar`로 임시 되돌리면
  `feature_names` 목록 검사 분기에서 unreachable 1건 재현 확인 후 원복.
- **L-1** `Decimal("0.87465")` 동점 — `ROUND_HALF_UP`→`0.8747`, `ROUND_HALF_EVEN`→`0.8746`.
- **L-2** K5 global 표본 0 → `NO_GLOBAL_SAMPLES`(K6 축과 분리).
- **L-3** `residual_std=NaN/Inf` → registry 층에서 즉시 `ArtifactRejected`.

## rollback.md 갱신(M-3 이후 — 파일 집합 변경)

M-3 이 `tests/inference/golden/_adapter.py`(신규)를 더해 in_scope 파일 집합이 이전
라운드(`4ad2de7`)와 달라졌다. `rollback.md`를 코드 마지막 커밋 `3a40bc3` 기준으로 다시
산출하고 새 임시 clone에서 재실측했다(세부는 rollback.md 자체 참고) — in_scope diff
**0** · `pytest tests -q` **233 passed**(base) · `mypy --strict` **18 source files
clean** · `lint-imports` **5 kept, 0 broken**.

## 비고

- leak 스캔(재확인, 식별자 개명 뒤): `grep -rniE -f config/quality/leak-patterns.txt
  reports/evidence/m5/5d reports/evidence/m5/5d-golden ml-engine/src/ml_engine/inference
  ml-engine/src/ml_engine/registry ml-engine/tests/inference ml-engine/tests/registry
  --exclude=scope.md` → **exit 1, 매치 0**. 이전 라운드는 golden 어댑터 식별자 셋이
  게이트 어휘 중 하나와 겹쳐 매치 11건(그중 3건은 이 evidence 문서 자신의 서술 문장)을
  냈다 — 값 노출이 아니라 이름 자체의 겹침이었으므로 그 식별자 셋을 개명해 겹침을 없앴다
  (코드 커밋 세부는 아래 「golden 식별자 개명」 참고).
- 하네스 레인 변경: `git log --oneline f5020aa982e6c5ade01f1660c0568dcd75c2fa7e..HEAD --
  CLAUDE.md .claude/` → 빈 목록(이번 라운드까지 하네스 레인 변경 없음).
- `fixtures/**`·`reports/evidence/m5/5d-golden/**`는 curator 레인 소유 — 이번 라운드
  무편집(읽기만). `pyproject.toml`도 이번 라운드 무편집.
- 설계 래칫 — golden 어댑터·test 추가로도 함수 50줄·파일 500줄 한도 안(각 case 별 test
  함수로 분리했다) — allowlist 편집 없음.
