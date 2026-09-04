# M1 / 1B — fixture 레인 기록 (fixture-curator)

**운영자 결정 C13 (2026-09-04)** — 「M1 계약 술어 설계」의 소유 slice = 1B.
기준 문서는 `data-extract.md`.

**SHA 둘.** `shared-kernel` 타입 선언은 `d333240` 에서 읽었고, 그 뒤 구현 레인이 세 커밋을 얹어
이 커밋의 부모는 `9a36aa8` 이다. `git diff --stat d333240 9a36aa8 -- shared-kernel/src/main` 이
**비어 있어** 아래 계약 경로는 부모 SHA 에서도 그대로 선다. 얹힌 것 가운데
`CompileFailureHarnessTest` 와 basis 상호 대입 negative fixture 는 **BLOCK-4 를 기계로
뒷받침한다** — 그 조합이 컴파일되지 않음을 구현 레인이 별도로 증명했다.

**이 파일은 fixture 레인 전용이다.** `commands.md`·`checklist.md` 는 구현 레인이 쓴다 —
이 레인은 건드리지 않았다.

## 1. 결론

**되돌린 case 는 0 건이다.** `rate-unit-001`~`005` 와 `money-basis-001`~`006` 열하나 전부
`insufficient-evidence` 인 채로 있고, **기대값·입력 파일·`sha256`·`expected_sha256` 은
하나도 바뀌지 않았다.**

되돌림을 막은 것 다섯. 정본은 `fixtures/manifest.yaml` 의 `m1_contract_binding` 이고
`golden-manifest.json` 의 `blockers` 가 case 별 적용 범위를 갖는다.

| id | 막는 것 | 대상 |
| --- | --- | --- |
| BLOCK-1 | 술어 어휘 동결이 **도구 쪽에서** 안 풀렸다 — 정의 자리는 manifest, 실행 자리는 `manifest_contract.py` 의 `holds()` 이고 뒤엣것이 in_scope 밖이다. 정의만 늘리면 스윕이 멈춘다 | 11 전부 |
| BLOCK-2 | 결과 토큰을 **1B 계약이 다른 이름으로 채택**했다 — 상태는 `Fact`·`Measurement`, 사유는 `ReasonCode` 다 | 11 전부 |
| BLOCK-3 | money-basis 축의 **둘째 자물쇠** — 운영자 명시 승인 부재 | money-basis 6 |
| BLOCK-4 | basis 혼합을 계약이 **컴파일 차단**으로 지어 거부 객체가 생기지 않는다 | money-basis-001·004 |
| BLOCK-5 | 검색 경로(`STR-16`)를 나르는 타입이 계약에 없다 | money-basis-003 |

**BLOCK-2 는 앞선 강등 사유보다 좁은 사실이다.** 2026-09-02 는 `Accepted`·`Rejected` 를
「승인 문면이 없는 자작 토큰」으로 적었다. 1B 계약이 선 지금은 **그 축을 계약이 다른 이름으로
채택했다**가 맞는 진술이고, 그래서 되돌림의 남은 길은 사후 승인이 아니라 **fixture 직렬화
토큰과 계약 이름의 정렬**이다. 정렬은 기대값 파일을 바꾸므로 **되돌림이 아니라 재추출**이며,
이 레인은 하지 않고 운영자·spec-writer 판단으로 올린다.

## 2. 실행한 명령과 결과

출력 전문을 싣지 않는다 — 핵심 결과 한 줄씩.

| # | 명령 | 결과 |
| --- | --- | --- |
| X-1 | `python3 fixtures/tools/mutation_sweep_adversarial.py` | exit 0 · 강등 대상 0 · 잔존 authoritative 18 — **편집 전후 동일** |
| X-2 | `python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml` | exit 0 · reader 와 PyYAML 6.0.3 이 63 case 에서 일치 |
| X-3 | `python3 fixtures/tools/mutation_sweep_targeted.py` | exit 0 · 통과 자리는 `license-006` 대조군(무변이)뿐 — 편집 전후 동일 |
| X-4 | `python3 fixtures/tools/manifest_prose_consistency.py` | exit 0 · 불일치 case id 8 · (블록,case) 11 — **편집 전후 동일**(새 산문이 주장형 덮개를 늘리지 않았다) |
| X-5 | `python3 fixtures/tools/check_legacy_numbers.py` | exit 0 · legacy-number hits 0 |
| X-6 | 11 case 의 `input_file`·`expected_file` SHA-256 재계산 후 manifest 값과 대조 | 불일치 0 |
| X-7 | `git status --short fixtures/input fixtures/expected fixtures/tools` | 출력 0 행 — fixture 실물과 도구 무접촉 |
| X-8 | `Accepted\|Rejected\|Comparable"\|RateUnitUndeclared\|BasisMismatch` 저장소 전수 grep (`fixtures/`·`m0/` evidence·본 evidence 제외) | 0 건 — BLOCK-2 의 실측 근거 |
| X-9 | secret 스캔 `grep -rniE "(api[_-]?key\|secret\|token\|password\|Bearer \|BEGIN (RSA\|EC\|OPENSSH))" reports/evidence/m1/1b/` | 매치 4 — **전부 스캔 명령 문자열 자신**(구현 레인 `commands.md` 3 · 이 파일의 이 행 1). 실제 secret 0. `golden-manifest.json` 은 0 |
| X-10 | §6 rollback 두 명령을 **임시 clone 에서 실제 실행** | 둘 다 exit 0 · `D` 2 · `M` 1 · `git diff 9a36aa8 -- fixtures/manifest.yaml` 0 행 · 구현 레인 경로(`shared-kernel` · `commands.md` · `checklist.md`) 무변경 |

**X-1·X-3·X-4 가 편집 전후 같다는 것이 이 레인의 무해성 증거다.** `contract_binding` 은
문서 포인터이고 계약 술어가 아니라, 분류를 정하는 스윕의 입력에 들지 않는다.

## 3. 개인정보

**추가·변경한 fixture 가 없다.** 이 레인은 manifest 의 분류·문서 필드만 편집했고 입력·기대값
파일을 열어 쓰지 않았다(X-6·X-7). 기존 11 case 는 전부 `privacy.synthetic: true` ·
`contains_personal_data: false` 이고 식별자가 `SYN-` 접두다 — 그 판정은 그대로 둔다.
**masking 전 값을 이 문서에 남기지 않았다** — 새로 읽은 원문이 없다.

## 4. 구현 레인 인계 — case 가 가리키는 타입 경로

`fixtures/manifest.yaml` 각 case 의 `contract_binding` 이 정본이고 이 표는 그 요약이다.
**`does_not_carry` 를 example test 가 단언하면 계약에 없는 이름을 테스트가 굳힌다.**

| case | 계약 경로 (`type_path`) | 계약이 나르는 축 | 단언하면 안 되는 축 |
| --- | --- | --- | --- |
| `rate-unit-001` | `Rate.Companion.ofPercent` | percent→fraction 명시 변환, 제수 `100` | `$.outcome` · `$.preservedSourceUnit` |
| `rate-unit-002` | `Rate.Companion.ofFraction` | 무배율 통과, 정규화가 값 보존 | `$.outcome` · `$.preservedSourceUnit` |
| `rate-unit-003` | `Rate` 의 `internal` 생성자 + `Fact.Absent(ReasonCode.UNIT_NOT_DECLARED)` | 단위 미선언은 **구성상** 만들 수 없다, 크기 추측 경로 부재 | `$.outcome` · `$.reasonCode` 표기 |
| `rate-unit-004` | 위와 같다 | 003 과 같은 이유로 크기가 결과를 가르지 않는다 | 위와 같다 + **case 간 동등 주장 자체** |
| `rate-unit-005` | `Rate.Companion.ofPercent` | 선언이 개연성을 이긴다(상한 밴드 부재, D-4) | `$.outcome` · `$.preservedSourceUnit` |
| `money-basis-001` | `Money`(상위 타입에 `Comparable`·이항 연산 없음) + `Basis` | basis 혼합 비교의 **컴파일 차단** | `$.outcome` · `$.reasonCode` · 입력의 `ESTIMATED_PRICE` |
| `money-basis-002` | `BaseAmount.compareTo` + `Basis.BASE_AMOUNT` | 같은 basis 면 비교 성립, `comparedBases` 토큰이 계약과 **일치** | `$.outcome` · `$.result` |
| `money-basis-003` | **없다** — 검색 경로가 계약에 없다 | 감시 경로 절반만(002 와 같은 자리) | `$.perPath` 전체 |
| `money-basis-004` | `Money`(상위 타입에 이항 연산 없음) | min/max 혼합의 컴파일 차단 | `$.outcome` · `$.reasonCode` · `ESTIMATED_PRICE` |
| `money-basis-005` | `sameKnownVat` + `ReasonCode.VAT_TREATMENT_MISMATCH` | **11 중 개념이 가장 그대로다** — 값이 같아도 `UNKNOWN` 이면 막는 런타임 전건 | `$.outcome` · `$.reasonCode` 표기 |
| `money-basis-006` | `VatTreatment.UNKNOWN` · `Provenance.Undeclared` · `Money` 필수 파라미터 | 기본값이 **없어서** 자동 태깅되지 않는다, 토큰이 계약과 **일치** | `$.eligibleForAuthoritativeCorpus` · `$.reasonCode` |

**층을 계상하지 않는다** — 열하나 전부 `insufficient-evidence` 이므로 example test 의 입력·기대
방향 참고로만 쓰고 「승인된 authoritative corpus 통과」의 증거로 세지 않는다.

## 5. 알려진 제한

1. **되돌림 0 건이라 `milestone-1.md` 의 「승인된 authoritative corpus 전체 통과」는 1B 축에서
   여전히 미충족**이다(1B 도메인의 authoritative case 가 0 건인 상태가 그대로다).
2. **술어 설계는 나왔고 실행은 없다.** `is-present` · `differs-from-path` · `differs-from-case`
   셋을 `m1_contract_binding.predicate_design` 에 정의했으나 **어느 case 에도 달지 않았다** —
   달면 현 도구가 「동결된 어휘 밖의 술어」로 멈춘다. 실행에 필요한 편집은
   `fixtures/tools/manifest_contract.py` 의 `holds()` 와 스윕의 변이체 생성 규칙이고,
   **그 경로를 이 레인이 열지 않았다.**
3. **`money-basis-001`·`004` 는 술어가 갖춰져도 되돌아오지 않는다**(BLOCK-4). 계약이 그 자리를
   컴파일 차단으로 지어 기대값이 적는 거부 객체가 존재하지 않는다. 두 case 를 이 계약 위에서
   쓰려면 기대값 재작성이 필요하고 그것은 **재추출**이라 운영자 판단 대상이다.
4. **`case-fold` 로 못 덮는 표기 차이가 둘 남는다** — `RateUnitUndeclared` ↔ `UNIT_NOT_DECLARED`,
   `VatTreatmentMismatch` ↔ `VAT_TREATMENT_MISMATCH`. 구분자와 단어 경계가 함께 달라
   대소문자 정규화 밖이다.
5. **`scope.md` 는 갱신하지 않았다** — `in_scope` 에 `fixtures/manifest.yaml` 이 이미
   「조건부 — 경로만, 실제 편집은 Phase 3 이며 fixture-curator 소관」으로 들어 있어 이 작업이
   그 문면 안이다. **`fixtures/tools/**` 는 in_scope 에 없고 이 레인이 넣지 않았다.**

## 6. rollback

이 레인의 산출물만 base 로 되돌린다 — 경로를 개별 인자로 넘긴다.

```
git restore --source=9a36aa8 --staged --worktree -- fixtures/manifest.yaml
git rm -f reports/evidence/m1/1b/golden-manifest.json reports/evidence/m1/1b/fixtures.md
```

`--source` 는 이 커밋의 **부모**다. 구현 레인의 세 커밋은 `fixtures/manifest.yaml` 을 건드리지
않았으므로 `d333240` 를 써도 같은 내용이 나온다.

**X-10 이 이 절을 임시 clone 에서 실제로 돌린 결과다.** 확인 지점: `git diff 9a36aa8 -- fixtures/manifest.yaml` 이 비고, 구현 레인 경로
(`shared-kernel/**` · `commands.md` · `checklist.md`)는 HEAD 그대로다.
**fixture 실물(`fixtures/input/**` · `fixtures/expected/**`)은 이 레인이 건드리지 않았으므로
되돌릴 것이 없다.**
