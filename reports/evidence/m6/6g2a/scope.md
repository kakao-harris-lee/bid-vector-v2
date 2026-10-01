# M6/6G-2a — 정책 값 민감도 게이트 보강 (계약, 착수 2026-10-01)

> **지위: 착수(2026-10-01).** base `30c6659e`(PR #51 6G-2d 머지 뒤 `main`), worktree `bid-vector-v2-m6-6g2a`, 브랜치 `m6-6g2a/2026-10-01`. 운영자 결정: A-1 즉시 착수(2026-09-30 「다음 로드맵 진행」) · A-2 「실제 박힌 수 발견 시 멈추고 보고」 사전 동의(2026-09-30). 초안 이력: `docs/m6-6g2-contracts`.
> **초안 시점 문면:** 6G 가 머지되기 전에 미리 쓴 계약이다.
> 수령하는 OPEN: **`OPEN-6G-SENSITIVITY-HARDENING`**(6G 계약 D-6G-75, verifier r5 H-2, code-review r5 L-9).
> 저자 인수인계 메모: `_workspace/m6-6g/open-sensitivity-hardening-handover.md`.

- base: **`30c6659e`**.
- 레인: `ml-implementer` 하나. Kotlin 변경 없음.

## 왜 이 slice 인가

6G 의 D-6G-63 은 「판정에 닿는 수는 정책 파일에서만 온다」를 **거동으로** 재기로 했다 — 정책 값을 바꾸면 판정이 바뀌어야 한다.
verifier r5 가 그 test 가 계약이 요구한 것을 재지 않음을 실측했다.

- 윗층은 판정 JSON 을 통째로 비교한다. `policy_checksum` 은 정책 파일 전체의 sha256 이라 **어떤 값을 흔들어도 바뀐다**. 그 칸과 `policy_version` 을
  빼면 정책 값 39 중 20 이 판정문을 움직이지 않는다.
- 아랫층(판정 투영)이 실제로 재는 판정 입력은 `verdict.alpha` · `verdict.min_window_count` · `verdict.min_window_rows` 셋이다.
- 합성 스냅숏에서 **어떤 후보도 기준선을 이기지 못한다**(승률 0 · 상대 개선 −1.0 · p 1.0). 그래서 통과 갈래를 한 번도 지나지 않고, 판정 입력 셋
  (Bonferroni 분모 · 상대 개선 하한 · 비열등 여유)이 「못 움직인다」로 등재돼 있다.
- 판정 경로에 넣은 변이 여덟이 초록이다: r4 의 N2·N3·N5·N6·N7, 새 E1(Bonferroni 분모 상수) · E2(상대 개선 하한 상수) · E4(검정력 상수).

**출하 코드는 정책 값을 읽는다** — 6G 실수집의 판정은 맞다. 비어 있는 것은 누군가 판정식에 상수를 넣었을 때 그것을 잡을 자물쇠다.

## 실수집·백테스트와의 관계

- 이 slice 는 **실수집과 백테스트 실행을 막지 않는다**(D-6G-65 게이트 하드닝 분류).
- 백테스트 판정 보고에는 판정을 낸 코드의 커밋 SHA 를 적는다. 그 SHA 가 6G 머지 커밋이면 verifier r5 가 본 트리다.
- 이 slice 가 **출하 코드에서 실제로 박힌 수**를 찾으면 성격이 바뀐다 — 멈추고 데이터 정확성 결함으로 보고한다(D-6G2a-9).

## 착수 실측 (착수 시 채운다)

| 항목 | 값 |
|---|---|
| base SHA | `30c6659e` |
| 정책 파일 키 수 · 로더 구조 필드 수 | **39 · 39** — 레인이 착수 때 재산출(일치) |
| checksum·version 을 뺀 투영에서 판정문을 움직이는 값 수 | **19**(판정문 불변 20) — 레인이 착수 때 재산출, r5 와 일치 |
| 판정 투영을 움직이는 값 | 극단 흔들기에서 `verdict.alpha` · `verdict.min_window_count` · `verdict.min_window_rows` 셋(r5 와 일치) · 작은 흔들기에서는 여덟 |
| 판정 경로에서 읽는 자리가 0 인 키 | **0**(AST 전수 — 부류 ⓒ 가 비는 근거) |
| `pytest tests -q` | 1,209 passed(6G-2d verifier 3차 확인, main `30c6659e` 와 ml-engine 동일 트리) |

상세는 `commands.md` 「착수 실측」·「읽는 자리 전수」.

## 재사용 조사 (Phase 2)

| 후보 | 판정 | 근거 |
|---|---|---|
| `tests/app/test_backtest_policy_sensitivity.py` 의 흔들기(`_perturb`)·정책 쓰기·스키마 등식 | **채택** | 대상 집합을 정책 스키마에서 도출하는 등식은 맞게 서 있다. 고칠 것은 비교 투영과 판이다 |
| `tests/evaluation/_backtest_fixture.py` 합성 스냅숏 생성기 | **확장** | 새 판을 같은 생성기로 짓는다. 생성기를 하나 더 만들지 않는다(6G r5 「manifest 생성기 둘」 제한을 늘리지 않는다) |
| verifier r5 probe `test_r5_probe_sensitivity.py` · 변이 스크립트 `r5_pymut.py` | **참고** | 재현 절차와 변이 정의를 그대로 옮긴다. scratchpad 가 사라졌으면 `verifier-r5.md` 의 기술로 다시 쓴다 |
| 리터럴 게이트 `test_evaluation_no_stray_numeric_literals.py` | **무변경** | D-6G-63 「그대로 두고 확장하지 않는다」를 승계한다 |
| hypothesis 같은 property 라이브러리 | 기각 | 값마다 「한 번 흔들어 뒤집히는가」면 충분하다. 의존 추가 없음 |

## 결정

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2a-1** | **비교 투영에서 `policy_checksum` · `policy_version` 을 뺀다.** 판정 JSON 산출물에는 **남긴다** — 재현성 공시다. 빼는 자리는 test 의 비교 함수 하나다 | 가장 강한 메아리를 빼먹은 것이 윗층이 빈 원인이다 |
| **D-6G2a-2** | **정책 값마다 세 부류 중 정확히 하나.** ⓐ **판정 입력** — 값을 흔들면 어느 판에서든 전략별 결말(Passed/Failed/NotEvaluable)이 바뀐다 ⓑ **산출 입력** — 결말은 그대로이고 판정문의 비메아리 칸이 바뀐다(예: 필요 표본 수 · 적합도) ⓒ **판독 밖** — 이 Python 판정이 읽지 않는다(예: Kotlin 수집만 쓰는 값). 세 부류의 합집합 == 정책 스키마에서 도출한 키 집합(**등식**) | 「39개 전부」라는 한 문장이 20 개의 사각을 가렸다. 부류를 나누면 사각이 목록으로 드러난다 |
| **D-6G2a-3** | **부류마다 실측이 다르다.** ⓐ 는 결말이 바뀜을 단언 · ⓑ 는 결말 불변 **그리고** 비메아리 칸 변화를 단언 · ⓒ 는 **아무것도 바뀌지 않음**을 단언하고 사유를 등재. ⓒ 의 값이 무언가를 움직이기 시작하면 RED(목록이 낡지 않는다) | 「못 잰다」는 선언을 「쓰이지 않는다」는 측정으로 바꾼다 |
| **D-6G2a-4** | **판정 입력마다 경계 위의 판.** 판정 입력 일곱(`verdict.alpha` · `verdict.primary_hypothesis_count` · `verdict.min_relative_improvement` · `verdict.ineligibility_noninferiority_margin` · `verdict.target_power` · `verdict.min_window_count` · `verdict.min_window_rows`) 각각에 대해, 그 값을 한 방향으로 흔들면 결말이 뒤집히는 합성 스냅숏을 둔다. 적어도 한 판에서는 후보가 기준선을 **실제로 이겨** 통과 갈래를 지난다. 판의 수는 구현이 정한다(한 판이 여러 입력의 경계를 겸해도 된다) | 세 값이 얼어 있던 것은 게이트 술어가 아니라 판의 문제다 |
| **D-6G2a-5** | **`_DECISION_FROZEN` 등재를 없앤다.** 판정 입력인데 움직이지 못하는 값은 등재가 아니라 판 보강으로 닫는다. 끝내 판을 지을 수 없는 값이 있으면 멈추고 사유와 함께 보고한다(운영자 결정) | 등재가 사각을 영구화했다 |
| **D-6G2a-6** | **쓰이는 자리마다 잰다.** 한 값이 두 자리에 쓰이면 자리마다 따로 단언한다. 확정 대상: `verdict.min_window_count`(표본 결정식 · `_not_evaluable` 의 창 수 문턱). 다른 값은 착수 실측으로 전수한다(정책 속성 읽기 자리를 AST 로 센다) | 한 자리만 움직여도 「바뀌었다」가 되면 다른 자리의 상수는 안 보인다 |
| **D-6G2a-7** | **변이 열을 acceptance 증거로.** N1~N7(r4) · E1·E2·E4(r5) · 착수 시 고안한 새 우회 ≥3 을 판정 경로에 넣고 **checksum 을 뺀 투영에서** RED 를 잰다. 순서: 먼저 변이 없는 기준선이 초록임을 확인 → 변이 적용(`git diff --numstat` 확인) → RED. 동치 변이는 사유와 함께 제외 | 6G r5 의 「일곱 다 RED」는 checksum 에 기대고 있었을 수 있다 |
| **D-6G2a-8** | **6G evidence 의 해당 행은 고치지 않고 이 slice evidence 에서 정정을 선언한다.** 6G `commands.md` 의 N1′~N7′ 행 · 「39개 전부」 행이 어느 전제 위의 측정이었는지 한 문단 | 닫힌 slice 의 evidence 를 되쓰지 않는다 |
| **D-6G2a-9** | **출하 코드 변경은 0 이 기대값이다.** `ml-engine/src/**` 의 diff 가 생기는 경우는 둘뿐이다: ⓐ 이 slice 가 판정 경로에서 **실제로 박힌 수**를 찾았다 — 멈추고 데이터 정확성 결함으로 보고, 백테스트 판정이 이미 나왔으면 그 판정의 재실행 여부를 운영자에게 묻는다 ⓑ 판을 짓기 위한 test 지원 코드가 `src` 에 필요하다 — 허용하지 않는다(`tests/**` 안에서 해결) | 게이트 slice 가 제품 거동을 바꾸면 범위가 샌다 |

## 계약 갱신 r0-b (2026-10-01, 팀장 — Python 레인 완료 보고 수령: HEAD `464f0a44` · OPEN 셋 · 변이 GREEN 하나)

| ID | 결정 |
|---|---|
| **D-6G2a-10** | **수용**: ① 기준선 먼저(19/20 분할이 vr r5 H-2 를 독립 재현) → 부류 ⓐ 29 · ⓑ 9 · ⓒ 1(`version`, 메아리에만 읽힘 — 산출물에 남는 것을 별 test 가 잠금) · 판 열다섯, `pass`·`pass-primary` 가 실제 `StrategyPassed` ② 변이 24 중 **23 RED**, GREEN 하나(적합도 bin 수 자리 — 그 자리가 내는 두 스칼라가 참조 표본과 bin 수에 동시에 의존하고 한 정책 값이 둘을 정해 입력으로 분리 불가) → **`OPEN-6G2A-FIT-BIN-COUNT-SITE`**(사유 등재, 운영자 가시) ③ **`OPEN-6G2A-SEED-COUNT-NOT-PINNED`** — 안정성 seed 마지막 키를 뺀 정책이 네 개로 조용히 로드된다(색인 목록 판독기가 연속성만 요구, 길이 미요구; 승인 문면은 다섯). 로더는 out_scope → 양성 대조와 함께 예외 등재, 닫는 자리는 6G-2c 또는 정책 로더 slice ④ rollback 은 hunk 역적용 대신 **문단 삭제 절차**(착수 hunk 에 팀장의 운영자 결정 문단이 함께 있어 그 문단은 남긴다 — 표지 계수 0/1 로 확인) ⑤ D-6G2a-9 ⓐ 미발동(판정 경로에 박힌 수 없음) |
| **D-6G2a-11** | **r1 판정 SHA 는 이 갱신 커밋.** verifier 표적: 기준선 재산출(checksum 뺀 투영에서 19/20) · 부류 등식(합집합 == 스키마 키 집합) · ⓒ 하나가 정말 안 쓰이는지 · 판정 입력 일곱의 판에서 결말이 실제로 뒤집히는지(양방향) · 변이 23 RED 재실측 + 새 우회 ≥3 고안 · GREEN 하나의 분리 불가 논증 검증 · seed 네 개 로드 재현 · acceptance `ml-engine` job 열한 단계 · rollback 문서 절차 그대로 · `src` diff 0 · 새 public 표면 0. code-reviewer(sonnet): 정적, test 공허 여부·부류 분류의 타당성 |

## 계약 갱신 r1 (2026-10-01, 팀장 — verifier r1 not-ready H-1·M-1~4·L · code-reviewer r1 H-1·H-2·M 6·L 6 수령)

판정 SHA `c042bac1`. 통과: 기준선 19/20 독립 재현 · 부류 합집합 == 키 집합(키 삭제·미분류 키 RED) · `pass`·`pass-primary` 실제 통과 · 변이 22/23 RED · acceptance 열 단계 exit 0(1,296) · rollback 절차 그대로 ①~⑥ · `src` 0 · 표면 0. 막는 것 하나, 고칠 것 여섯. **재작업 1/5.**

| ID | 결정 |
|---|---|
| **D-6G2a-12** | **(vr H-1 · cr H-1·H-2 — 게이트 하드닝) 명단은 읽기가 아니라 「쓰임」이다.** 6G r5 의 E1(Bonferroni 분모 상수를 `passes_window` **한 자리**에만)이 여전히 초록이고, alpha 의 통과 test 자리(V1n) · `stability_seeds[0]` 의 적합도 seed 자리(V4n) · `min_window_count` 의 **세 번째** 자리(`_sampling_record` 최소 표본식, cr H-1) · 제외 ⑮ 의 기관 상수 자리(cr H-2)도 열려 있다. 손으로 쓴 명단이 원인이다. 처방: **쓰임 명단을 AST 로 생성**(`evaluation/backtest/**`·`app/backtest_job.py` 에서 정책 속성 접근 경로마다 둘러싼 함수 — 키 × 자리) → 등식 test 「자리마다 전용 probe 또는 변이 행이 있다」. 전용 probe: `passes_window` 를 두 alpha 사이의 p 로 직접 호출(E1 r5 형 · V1n) · seed 비민감 판에서 적합도 칸 이동(V4n) · `sample-floor` 판에서 `SAMPLE_SIZE_BELOW_MINIMUM`(cr H-1) · 두 정책으로 `admit_rows` 직접 비교(cr H-2). 변이 E1(r5 형)·V1n·V4n·cr H-1·cr H-2 전부 → RED |
| **D-6G2a-13** | **(vr M-1) ⓒ 검사는 그 값의 자리가 닿는 모든 판에서** — 출하 판 하나로는 ⓐ 값 19 를 ⓒ 로 옮겨도 초록이다. ⓐ 값을 ⓒ 로 옮기는 변이 → RED(값마다는 아니어도 표본 셋) |
| **D-6G2a-14** | **(vr M-2) `OPEN-6G2A-FIT-BIN-COUNT-SITE` 는 이 slice 에서 닫는다** — 참조 표본을 고정하면 bin 편차(0.0421→0.0648)가 움직인다. P9b → RED |
| **D-6G2a-15** | **(vr M-3 · cr M) 한쪽 우회**: 판정 입력 일곱은 양방향 필수(`min_window_rows` 하향 방향 추가) · 그 밖 ⓐ 입력은 판이 있으면 반대 방향 probe(V5n 예산 상한 `min()` · V9n 적합도 alpha `max()` → RED), 없으면 값마다 사유 등재. 위협 모델 문장 4 를 그 범위로 정확히 |
| **D-6G2a-16** | **(vr M-4) seed 노출은 「하나만 있어도 로드」** — `OPEN-6G2A-SEED-COUNT-NOT-PINNED` 문면 갱신(seed 안정성 레그가 항상 참이 되는 조건 명시). 로더는 out_scope 그대로 → **6G-2e 에 「seed 수 = 5 를 정책 스키마가 고정」 항목으로 넘긴다**(팀장이 2e 초안에 등재) |
| **D-6G2a-17** | **장부 일괄**: N2~N7 설명을 r5 자리(`_not_evaluable` 창 수)로 · 변이 계수 22/23 로 세 문서 일치 · `_strip_echo` 는 전역 값 삭제가 아니라 **메아리 경로만** 제거(cr M — 수치 키의 ⓒ 공허 방지) · milestone 문단의 OPEN 수 셋 · ⓐ 중 「사유만 움직이는」 셋은 ⓑ 로 재분류하거나 ⓐ 정의(결말 부류 변화)를 만족하게 판 보강(cr M) |
| **D-6G2a-18** | **r2 는 표적**: D-12 자리별 probe + 변이 다섯 · D-13 변이 · D-14 P9b · D-15 네 변이 · D-17 · acceptance `ml-engine` job · rollback 재실측. code-reviewer 는 수정 diff |

## 위협 모델 — 6G-2a 고유 경계 (Phase 2.5 (0))

**방어하는 것**: 저자가 `ml_engine.evaluation.backtest/**` 와 `ml_engine.app.backtest_*` 의 판정 경로에 정책 값 대신 **수를 박는 것** — 그 수를 어떤
형태로 지었든(리터럴 · 산술 · `len` · `int.from_bytes` · f-string · `math` · `enum.auto` · 생성식). 정책 로더가 값을 상수로 돌려주는 것도 포함한다.

**방어하지 않는 것(경계 밖)**: 민감도 test · 합성 판 · 정책 파일 자신을 고치는 저자 · 분포 엔진(`inference-v1.yaml`)의 값 — S2 는 출하 정책 그대로
호출하고 그 동등성은 `OPEN-5C2-SERVING-PATH-PARITY` 소관이다 · Kotlin 수집 쪽의 값.

이 경계는 6G 의 D-6G-63 문면(「코드에 박힌 수가 판정에 닿으면 … RED」)을 줄이지 않는다 — 같은 요구를 실제로 재게 만든다.

### (1) 열거인가 구성인가

대상 값 집합은 정책 스키마에서 도출한다(등식, 기존). 부류 셋의 합집합도 등식이다. 판정 입력 일곱은 **이름으로 든다** — 판정식이 읽는 값이라
열거가 맞고, 빠뜨리면 그 값은 ⓑ 나 ⓒ 의 단언에서 걸린다(결말이 바뀌므로 「결말 불변」이 RED).

### (2) 우회 — 다섯 이상 (착수 시 실측으로 갱신)

1. 판정식에 상수를 넣고 정책 값은 판정문에 **공시만** 한다(메아리). ← 비교 투영이 메아리를 지우고 결말을 본다.
2. 한 값이 쓰이는 두 자리 중 한 자리만 상수로 바꾼다. ← D-6G2a-6.
3. 통과 갈래에서만 쓰이는 값을 상수로 바꾼다(합성 판이 통과하지 않으면 안 보인다). ← D-6G2a-4.
4. 정책 값을 읽되 `max(policy.x, 상수)` 처럼 **한쪽으로만** 따른다. ← 흔드는 방향을 **양쪽**으로 한다(값을 올린 판 · 내린 판).
5. 로더가 특정 키에 기본값을 채운다(파일에서 빼도 같은 값). ← 키를 **제거한** 정책이 로더에서 거부되는지 단언(fail-closed).
6. 값을 부류 ⓒ 로 옮겨 단언을 피한다. ← ⓒ 는 「아무것도 안 바뀜」을 단언하므로 실제로 쓰이는 값은 RED.

### (2b) 값 획득 축

새 public 표면은 **없어야 한다**(`src` diff 0). test 지원 헬퍼는 `tests/**` 안의 비공개 이름으로 둔다.

### (3) 과잉·미달

- 과잉 아님: 판정 입력마다 판을 두는 것은 6G 계약이 이미 요구한 측정의 이행이다.
- 미달 경계: 분포 엔진 내부 값의 민감도는 재지 않는다(경계 밖).

## 운영자 승인 필요 (착수 전)

- **A-1 착수 시점**: 6G 머지 뒤 아무 때나. 실수집과 병행 가능(Python test 만 돈다, 외부 호출 0). 다만 호스트 빌드 규율상 실수집 실행과 무거운 pytest 가
  겹치지 않게 한다.
- **A-2 D-6G2a-9 ⓐ 의 귀결 사전 동의**: 실제로 박힌 수가 발견되면 멈추고 보고한다.

## in_scope

- `ml-engine/tests/app/test_backtest_policy_sensitivity.py`
- `ml-engine/tests/evaluation/_backtest_fixture.py` · `ml-engine/tests/evaluation/_backtest_support.py`(판 생성 보강)
- `ml-engine/tests/evaluation/fixtures/**`(합성 판을 파일로 둘 때 — golden `m6-6g-golden/` 은 **무변경**)
- `reports/evidence/m6/6g2a/**` · `milestone-6.md`(착수·종결 문단만)

**out_scope**: `ml-engine/src/**`(D-6G2a-9) · `ml-engine/policy/**`(정책 값·version 무변경) · 리터럴 게이트 파일 · golden fixture · Kotlin 전부 ·
`reports/evidence/m6/6g/**`.

## acceptance

CI `ml-engine` job 명령 그대로(`.github/workflows/ci.yml`). 덧붙여 `commands.md` 에 변이 열의 RED/GREEN 표와 부류별 값 수를 적는다.
clean-tree 게이트와 누출 스캔은 evidence-pack 규격대로.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 공유 파일 `milestone-6.md` 는 문단 블록 삭제. 버릴 clone 에서 ①~⑥ 실측.

## 리뷰 레인

`verifier`(변이 재실측 · 새 우회 고안 · 부류 ⓒ 의 값이 정말 안 쓰이는지) + `code-reviewer`(sonnet). Codex 없음 — 되돌리기 어려운 경로가 아니다.
privacy-gate 는 합성 판을 파일로 커밋할 때만(식별자 부재 확인).

## 하네스 레인 변경

**리뷰 요청 1차(2026-10-01) — 하네스 파일 변경 0.** `.claude/**` 를 한 줄도 고치지 않았다
(이 slice 의 in_scope 밖이다).

**다음 하네스 편집에 올릴 후보 하나**(이 slice 가 실측으로 얻은 규율):

> **다중 자리 값은 「사유」로 넘기지 말고 자리마다 변이를 돌려 그 사유를 검증한다.**
> 설계 검토 (2) 의 우회 열거만으로는 「두 자리가 함께 돈다」 같은 사유가 **틀렸는지** 알 수
> 없다. 이 slice 에서 그 사유로 자리별 단언을 넘긴 세 값 중 **둘이 실제로 열려 있었다** —
> 한 자리에만 상수를 박은 변이가 초록이었고(배제 비율의 제외 단계 · 제도 상수의 적합도 기준
> 표본), 다른 자리가 정책을 계속 따라 판정문이 움직였기 때문이다. 사유를 쓰는 자리마다
> **그 사유를 반증하는 변이 하나**를 같이 돌리는 것이 비용 20초 안쪽이다.

이 후보는 `v2-slice-pipeline` Phase 2.5 (2) 또는 Phase 4 의 변이 절에 들어갈 문장이고, 이
slice 에서는 **스킬 파일을 고치지 않는다**(저작과 하네스 편집을 같은 커밋에 섞지 않는다).

## OPEN 수령·신설 (예상)

| OPEN | 처분 |
|---|---|
| `OPEN-6G-SENSITIVITY-HARDENING` | **이 slice 가 닫는다** |
| (신설 가능) `OPEN-6G2A-INFERENCE-POLICY-SENSITIVITY` | 분포 엔진 정책 값의 민감도 — 경계 밖으로 둔 것을 등재만 |
