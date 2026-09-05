# M1 / 1B-c — fixture 레인 기록 (fixture-curator, ①②③)

기준 문서는 `data-extract.md`. 계약은 `scope.md`, 처분의 정본은 그 문서의 **D4 표**와
**운영자 결정 2026-09-05 decision 18~21** 이다.

**이 파일은 corpus 레인(①②③) 전용이다.** ④ 소비 테스트는 별도 레인이고 `checklist.md`·
`rollback.md` 는 그 레인이 마지막에 쓴다 — 이 레인의 몫(커밋 목록·알려진 제한·되돌림)은
여기 둔다.

## 1. 결론

**열하나 가운데 열이 `authoritative` 로 돌아왔다.** 남은 하나는 `money-basis-003` 이고
**이월**(decision 21)이다 — 검색 경로를 나르는 타입이 계약에 없고 그것은 이 slice 의
`out_of_scope` 다.

착수 시점에 열하나를 막던 다섯 자물쇠(1B `fixtures.md` BLOCK-1~5)의 처분:

| id | 막던 것 | 처분 |
| --- | --- | --- |
| BLOCK-1 | 술어 어휘 동결이 도구 쪽에서 안 풀렸다 | **해소** — decision 18 로 셋을 어휘에 더하고 `holds()`·두 스윕에 실행을 넣었다 |
| BLOCK-2 | 결과 토큰을 1B 계약이 다른 이름으로 채택했다 | **해소** — decision 19 로 계약 이름을 정본으로 삼고 **재추출**로 정렬했다 |
| BLOCK-3 | money-basis 축의 운영자 명시 승인 부재 | **해소** — decision 20(case 별) + 21(처분 표 승인) |
| BLOCK-4 | basis 혼합이 컴파일 차단이라 거부 객체가 없다 | **재정의로 우회** — 기대값을 「표현 불가」로 다시 썼다(decision 21) |
| BLOCK-5 | 검색 경로 타입이 계약에 없다 | **남았다** — `money-basis-003` 이월, `OPEN-1BC-STR16` 신설 |

**되돌림이 아니라 재추출이다.** 기대값 9 · 입력 3 파일이 실제로 바뀌었고 전부
`previous_expected_sha256`/`previous_sha256` 을 해당 case 의 `change_history` 에 실었다.
**기대값은 승인 문면에서만 나왔다** — 각 case 의 `expected_reasoning` 이 `v2-지침서.md` §4.1 ·
`data-dictionary.md` §1.1·§1.2.1·§12.1 · `capability-map.md` DEC-02 · decision 19·21 을 인용하고,
**V2 산출을 복사하거나 재생성 명령으로 golden 을 만들지 않았다**(위협 모델 우회 (5)).

## 2. 커밋

| SHA | 일 |
| --- | --- |
| `0ab22a5` | ① 술어 어휘 셋 확장 — `schema.extensions` 정의 + `holds()` + 두 스윕의 갈래·가드 + self-check |
| `fb8d4c6` | ② `rate-unit` 5 재추출 — 기대값 5, manifest 5 case, 스윕 `ASSERTED`/`NULL_ASSERTED` |
| `ed87da6` | ③ `money-basis` 6 처분 — 기대값 4 · 입력 3, manifest 6 case, `uncovered_axes` 두 축 |

base `a5ea955` · head `ed87da6`. **하네스 레인 변경 없음** —
`git log --oneline a5ea955..HEAD -- CLAUDE.md .claude/` 이 빈 목록이고, 그 명령이 잡지 못하는
하네스 편집(`agent-workflow.md`·`milestone-*.md` 의 승격 절 밖)도 이 레인은 하지 않았다.

## 3. 실행한 명령과 결과

**정본은 `commands.md` 의 C-5~C-10** 이고 여기서는 한 줄 요약만 든다.

| # | 결과 |
| --- | --- |
| C-5 | exit 0 · 강등 0 · 잔존 authoritative **18 → 28** · 술어 self-check 22 통과 |
| C-6 | exit 0 · reader ↔ PyYAML 63 case 일치 |
| C-7 | exit 0 · 통과는 `license-006` 대조군뿐 · B7·B8 세탁 변이체가 전부 caught 로 전환 |
| C-8 | exit 0 · 불일치 8·11 → **7·10**(낡은 덮개 하나 소멸, 새 불일치 0) |
| C-9 | exit 0 · legacy-number hits 0 |
| C-10 | 63 case 126 해시 대조 · 불일치 0 |

**1B 축 밖 18 case 는 집합이 그대로다** — base `a5ea955` 의 authoritative 집합과 대조해
추가 10 · 빠짐 0 을 확인했다(무해성). **다른 도메인 corpus 는 열지도 않았다.**

**TDD 순서를 기록한다** — 술어 self-check 를 먼저 쓰고 **동결 상태의 `holds()` 에서 15 건
실패(RED)** 를 확인한 뒤 구현했고, 구현 뒤 22 건 통과(GREEN)했다. 새 acceptance 명령을
늘리지 않으려고 그 검사를 C-5 안에 넣었다.

## 4. ④ 레인 인계 — projection 이 내야 하는 필드

`fixtures/manifest.yaml` 각 case 의 `verified_paths`·`verified_projections` 가 정본이고
`golden-manifest.json` 의 `dispositions` 가 그 표다. **`does_not_carry` 를 단언하면 계약에
없는 이름을 테스트가 굳힌다.**

**대조 전에 읽어야 할 세 가지.**

1. **`rate-unit` 다섯은 계약에 그 상태를 방출하는 함수가 없다.** `Rate.ofPercent`·`ofFraction`
   은 `Rate` 를 직접 내고 `Fact` 로 감싸지 않으며, 미선언 입력을 받는 공개 경로는 아예 없다.
   `$.fact` 가 잠그는 것은 **상태 축에 계약이 채택한 어휘**이고 그 상태를 실제로 방출하는
   자리는 어댑터(1C 이후)다. runner 의 testFixtures projection 이 그 자리를 표현해야 하며
   **계약에 없는 함수를 지어내면 안 된다.**
2. **`money-basis-001`·`004` 는 실행 산출과 대조할 수 없다.** 대조 대상이 컴파일 실패이고,
   실행자는 이미 있는 `CompileFailureHarnessTest` fixture 11 이다. runner 는 그 판정을 받아야
   한다 — 기대값 `{"representable": false}` 는 그 사실의 표기이지 실행 산출이 아니다.
3. **`money-basis-006` 의 `$.eligibleForAuthoritativeCorpus` 는 계약 산출이 아니다.** corpus
   운영 규칙의 판정이라 값 타입이 내지 않는다 — 잠그되 runner 가 계약 산출과 대조하지 않는다.

바로 대조 가능한 자리는 **`money-basis-002`·`005`** 둘이다 — `compareKnownVat(BaseAmount,
BaseAmount)` 가 `Fact.Known` / `Fact.Absent(VAT_TREATMENT_MISMATCH)` 를 실제로 낸다.

## 5. 개인정보

**열하나 전부 synthetic 이고 그 판정은 바뀌지 않았다** — `privacy.synthetic: true` ·
`contains_personal_data: false` · 식별자는 `SYN-` 접두 그대로다. 재작성한 기대값에 **새 원문
값을 하나도 들이지 않았다**(쓴 값은 계약 enum 이름과 기존 수치뿐이고, 입력에 새로 쓴 값은
`ESTIMATED`·`Published` 두 계약 이름이다). **운영 DB·API·로그를 열지 않았고 masking 전 값을
이 문서에 남기지 않았다.** secret 스캔은 §7 에 있다.

## 6. 판단이 갈린 자리 (셋)

기록만 하고 되돌리지 않았다 — 셋 다 승인 문면과 계약 위에서 고른 것이고 근거를 해당 case 의
`change_history` 가 갖는다.

1. **`money-basis-005` 의 입력을 바꿨다.** D4 표는 기대값만 말하나, 승인된 계약에서
   `compareKnownVat` 는 provenance 를 VAT 보다 **먼저** 본다. 앞선 입력은 둘째 금액이
   `Undeclared` 이자 `UNKNOWN` 이라 두 전건이 함께 깨져 있어 **이 case 가 주장하는 사유가 나올
   수 없었다.** case 의 의미(과세 축 하나)를 지키려고 입력을 계약에 맞췄다 — 반대로 사유를
   바꾸면 decision 21 이 승인한 처분과 어긋난다.
2. **표가 든 술어 대신 정확 비교를 쓴 자리가 있다.** D4 표는 `rate-unit-001` 에
   `differs-from-case`(거울 003) + `is-present` 를 들었으나, **decision 19 로 `Fact.Known`/
   `Absent` 가 승인 어휘가 돼** 정확 비교가 미승인 리터럴을 잠그지 않게 됐다. 그러면
   `schema.extensions` 의 도출 규칙 ①이 정확 비교를 요구하고, 그것은 표의 술어가 잡는 변이를
   **전부 포함하면서 더 강하다**(쌍 관계도 양쪽을 잠그면 성립한다). 표의 술어 지정은
   decision 19 이전 상태를 겨눈 설계였다. 결과로 `differs-from-path`·`differs-from-case` 는
   **정의만 서고 사용처가 0** 이다 — 동결분에 그랬던 것과 같은 이유로 지우지 않았다.
3. **`money-basis-006` 의 `$.reasonCode` 를 스윕 `ASSERTED` 에서 뺐다.** (a′) 값 변이 갈래는
   「그 값을 잠글 자격이 있는 경로」를 전제하는데 `LegacyOriginNotPromotable` 은 `ReasonCode`
   enum 밖이라 정확 비교가 **금지된** 자리다. 존재 주장은 `is-present` 가 (a)(b)로 **더 넓게**
   지므로 적대 집합이 줄지 않는다. **값을 잠글 자격이 있는 경로를 빼는 것은 다른 일이고**
   위협 모델 (3)이 막는 우회다 — 그것은 하지 않았다.

## 7. 알려진 제한

1. **「승인된 authoritative corpus 전체 통과」는 아직 실증되지 않았다.** 모집단은 생겼으나
   그 위에서 V2 산출을 대조하는 실행자는 ④ 레인이 만든다 — 이 레인은 만들지 않았다.
2. **`money-basis-003` 은 돌아오지 않았다**(BLOCK-5). `OPEN-1BC-STR16` 이 소유하고 strategy
   slice 가 받는다.
3. **`rate-unit` 의 원문 unit 보존 축에 지금 fixture 가 없다.** 기대값에서 뺐고 새 case 는
   어댑터 계약이 선 뒤에 만든다(`OPEN-1BC-SOURCE-UNIT`) — 기존 case 에 주장을 되돌려 붙이지
   않는다.
4. **`equals-path` 는 여전히 어휘에 없다.** decision 18 이 푼 셋에 **동등** 술어가 없어
   `uncovered_axes` 의 ②는 그대로 살아 있다.
5. **`not-equals` 의 null 구멍을 고치지 않았다.** 동결분의 의미를 바꾸면 그 술어를 단 case 의
   계약이 소급해 달라지므로 새 술어 셋에만 존재·비-null 전건을 세웠다. 현재 `not-equals`
   사용처는 0 이다.
6. **secret 스캔** `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
   reports/evidence/m1/1b-c/` — 매치 **4**. 셋은 이 파일의 스캔 명령 문자열 자신이고 하나는
   `scope.md` 의 `minimumTokenCount`(CPD 설정값, `Token` 이 부분 문자열로 걸린다). **실제
   secret 0.**

## 8. rollback (이 레인 몫)

이 레인의 산출물만 base 로 되돌린다 — 경로를 **개별 인자**로 넘긴다. ④ 레인의 경로
(`shared-kernel/**`·`app/**`·`config/**`)는 건드리지 않는다.

```
git restore --source=a5ea955 --staged --worktree -- \
  fixtures/manifest.yaml \
  fixtures/tools/manifest_contract.py \
  fixtures/tools/mutation_sweep_adversarial.py \
  fixtures/tools/mutation_sweep_targeted.py \
  fixtures/expected/rate-unit-001.json fixtures/expected/rate-unit-002.json \
  fixtures/expected/rate-unit-003.json fixtures/expected/rate-unit-004.json \
  fixtures/expected/rate-unit-005.json \
  fixtures/expected/money-basis-001.json fixtures/expected/money-basis-002.json \
  fixtures/expected/money-basis-004.json fixtures/expected/money-basis-005.json \
  fixtures/input/money-basis-001.json fixtures/input/money-basis-004.json \
  fixtures/input/money-basis-005.json
git rm -f reports/evidence/m1/1b-c/fixtures.md reports/evidence/m1/1b-c/golden-manifest.json \
  reports/evidence/m1/1b-c/commands.md
```

**base 에 없는 신규 경로는 evidence 셋뿐이라 `git rm` 이 그 셋을 받는다** — `fixtures/` 아래
16 경로는 전부 base 에 있으므로 `git restore` 로 되돌아간다.

**임시 clone 실측 결과는 §3 의 표 아래에 두지 않고 여기 둔다** — 위 두 명령을 임시 clone 에서
실제로 돌렸다: 둘 다 exit 0 · `M` 16 · `D` 3 · `git diff a5ea955 -- fixtures/` 0 행 ·
`scope.md` 와 ④ 레인 경로 무변경.
