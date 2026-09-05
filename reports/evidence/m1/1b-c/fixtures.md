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

**같은 날 계약 정정이 한 번 더 있었다(§9).** ④ 실행자 설계가 「계약이 산출하지 않는 축은
잠그지 않는다」를 요구해 `verified_paths` 를 좁혔다. **분류는 하나도 바뀌지 않았다** — 열이
`authoritative` 그대로이고 잠그는 경로만 줄었다.

**되돌림이 아니라 재추출이다.** 기대값 9 · 입력 4 파일이 실제로 바뀌었고 전부
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
| `6ef8fb5` | **계약 정정** — `verified_paths` 를 1B 계약이 산출하는 축으로 좁힘(§9) |
| `58a3c6e` | **verifier r1 처리** — M-1(006 입력 명시 선언) · L-6(003·004 기대값 형태 근거) |

base `a5ea955` · head `58a3c6e`(이 레인 마지막 **산출물** 커밋).

**레인 혼입 선언 — `51f820c`(이력 되쓰기 없이 적는다).** 이 레인의 evidence 편집 셋
(`fixtures.md`·`golden-manifest.json`·`commands.md`, verifier r1 L-4·L-5 처리와 M-1·L-6 반영)이
**④ 레인의 커밋 `51f820c` 에 실려 들어갔다.** 두 레인이 같은 working tree 와 index 를 공유하는데
이 레인이 `git add` 로 그 셋을 스테이징한 순간과 ④ 레인의 `git commit` 이 겹쳤다 — 그 커밋의
메시지는 runner 작업을 적고 있으나 **내용은 이 레인의 evidence 셋뿐**이고, ④ 레인 자신의
소스 변경은 그 시점에 아직 커밋되지 않은 상태였다. **되쓰지 않는다**(2026-09-02 스테이징 규율:
혼입은 이력 되쓰기 없이 선언한다). 내용 손실은 없다 — 그 커밋의 세 파일이 이 레인이 쓴 것과
바이트로 같다. **1차 책임은 커밋 직전에 `git diff --cached` 로 대조하지 않은 이 레인에 있다.** **하네스 레인 변경 없음** —
`git log --oneline a5ea955..HEAD -- CLAUDE.md .claude/` 이 빈 목록이고, 그 명령이 잡지 못하는
하네스 편집(`agent-workflow.md`·`milestone-*.md` 의 승격 절 밖)도 이 레인은 하지 않았다.

## 3. 실행한 명령과 결과

**정본은 `commands.md` 의 C-5~C-10** 이고 여기서는 한 줄 요약만 든다.

| # | 결과 |
| --- | --- |
| C-5 | exit 0 · 강등 0 · 잔존 authoritative **18 → 28** · 술어 self-check 22 통과 |
| C-6 | exit 0 · reader ↔ PyYAML 63 case 일치 |
| C-7 | exit 0 · 통과 **둘** — `license-006` 대조군 + `money-basis-006` 적격성 단독 뒤집기(§9 의 대가) |
| C-8 | exit 0 · 불일치 8·11 → **7·10**(낡은 덮개 하나 소멸, 새 불일치 0) |
| C-9 | exit 0 · legacy-number hits 0 |
| C-10 | 63 case 126 해시 대조 · 불일치 0 |

**1B 축 밖 18 case 는 집합이 그대로다** — base `a5ea955` 의 authoritative 집합과 대조해
추가 10 · 빠짐 0 을 확인했다(무해성). **다른 도메인 corpus 는 열지도 않았다.**

**TDD 순서를 기록한다** — 술어 self-check 를 먼저 쓰고 **동결 상태의 `holds()` 에서 15 건
실패(RED)** 를 확인한 뒤 구현했고, 구현 뒤 22 건 통과(GREEN)했다. 새 acceptance 명령을
늘리지 않으려고 그 검사를 C-5 안에 넣었다.

## 4. ④ 레인 인계 — case 별 최종 `verified_paths`

**이 표가 runner 의 기대다.** 계약이 산출하지 않는 축은 §9 정정으로 이미 걷어냈으므로
**runner 가 지어낼 것이 없다.** 정본은 `fixtures/manifest.yaml` 각 case 이고
`golden-manifest.json` 의 `dispositions` 가 같은 표를 기계가 읽는 형태로 갖는다.

| case | 분류 | `verified_paths` | runner 가 부르는 것 |
| --- | --- | --- | --- |
| `rate-unit-001` | authoritative | `$.rate.fraction` | `Rate.ofPercent(87.5)` → projection `{fraction}` |
| `rate-unit-002` | authoritative | `$.rate.fraction` | `Rate.ofFraction(0.875)` → 같음 |
| `rate-unit-003` | authoritative | `$.representable` | **컴파일 fixture 12 위임** |
| `rate-unit-004` | authoritative | `$.representable` | **컴파일 fixture 12 위임** |
| `rate-unit-005` | authoritative | `$.rate.fraction` | `Rate.ofPercent(0.875)` → 같음 |
| `money-basis-001` | authoritative | `$.representable` | **컴파일 fixture 11 위임** |
| `money-basis-002` | authoritative | `$.fact` · `$.comparedBases` | `compareKnownVat(BaseAmount, BaseAmount)` → `Fact.Known` |
| `money-basis-003` | insufficient-evidence | 없음 | 대상 아님(이월) |
| `money-basis-004` | authoritative | `$.representable` | **컴파일 fixture 11 위임** |
| `money-basis-005` | authoritative | `$.fact` · `$.reasonCode` | `compareKnownVat(...)` → `Fact.Absent(VAT_TREATMENT_MISMATCH)` |
| `money-basis-006` | authoritative | `$.vatTreatment` · `$.provenance` | 입력이 **선언한** 미상 두 값으로 `BaseAmount` 구성 → 되읽기(구성 왕복) |

**`verified_projections` 는 열하나 어디에도 없다** — §9 정정으로 마지막 하나가 걷혔다.
runner 는 정확 비교만 구현하면 되고, 술어 넷의 실행은 Python 쪽 self-check 가 계속 잰다.

**기대값에 남아 있으나 대조하지 않는 필드**(나르기만): `rate-unit-001`·`002`·`005` 의
`$.fact`·`$.conversionDivisor`·`$.rate.axis` · `money-basis-002` 의 없음 ·
`money-basis-006` 의 `$.autoTaggedFromDefinitionDefault`·`$.eligibleForAuthoritativeCorpus`·
`$.reasonCode`. **이것들을 단언하면 계약에 없는 값을 테스트가 굳힌다** —
`contract_binding.does_not_carry` 가 case 마다 그 목록을 갖는다.

**`money-basis-006` 은 runner 가 상수를 대지 않는다 — verifier r1 M-1 처리.** 입력이 미상을
`"UNKNOWN"`·`"Undeclared"` 로 **선언**하므로 runner 는 `null` 을 접을 것이 없다. **대신 이 case 가
기계로 잠그는 것은 구성 왕복까지**이고 「기본값이 없어서 자동 태깅될 수 없다」는 구성 사실은
`Money.kt` 선언이 진다 — **그것을 재는 회귀 테스트는 오늘 없다**(§7 「기본값 부재를 재는 컴파일 fixture 가 없다」). runner 는 explicit
provenance 를 받도록 고쳐야 한다(앞선 판은 `error()` 로 거부했다).

**컴파일 fixture 위임 넷은 실행 산출과 대조하지 않는다.** `$.representable: false` 는 「그
조합은 표현 불가」의 표기이고, 그것을 증명하는 것은 `CompileFailureHarnessTest` 다 —
fixture 11(basis 교차)은 이미 있고 **fixture 12(단위 미선언 `Rate` 생성)는 ④ 레인이 신설한다.**

## 5. 개인정보

**열하나 전부 synthetic 이고 그 판정은 바뀌지 않았다** — `privacy.synthetic: true` ·
`contains_personal_data: false` · 식별자는 `SYN-` 접두 그대로다. 재작성한 기대값에 **새 원문
값을 하나도 들이지 않았다**(쓴 값은 계약 enum 이름과 기존 수치뿐이고, 입력에 새로 쓴 값은
`ESTIMATED`·`Published` 두 계약 이름이다). **운영 DB·API·로그를 열지 않았고 masking 전 값을
이 문서에 남기지 않았다.** secret 스캔은 §7 에 있다.

## 6. 판단이 갈린 자리 (넷)

기록만 하고 되돌리지 않았다 — 넷 다 승인 문면과 계약 위에서 고른 것이고 근거를 해당 case 의
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
   위협 모델 (3)이 막는 우회다 — 그것은 하지 않았다. **§9 정정이 이 자리를 한 걸음 더 밀었다** —
   그 경로가 1D 로 이관되면서 `is-present` 째로 걷혔다.
4. **§9 정정에서 `$.conversionDivisor` 를 001·005 에서도 뺐다.** 팀장 지시는 002 만 이름으로
   들었으나(`ofFraction` 에 제수가 없으므로), `ofPercent` 의 제수 `100` 도 `Rate.kt` 의
   **`private const`** 라 반환값에 실리지 않는다 — 같은 규칙이 같은 결론을 낸다. 셋을 다르게
   두면 runner 가 001·005 에서만 제수를 지어내야 한다.

## 7. 알려진 제한

1. **「승인된 authoritative corpus 전체 통과」는 아직 실증되지 않았다.** 모집단은 생겼으나
   그 위에서 V2 산출을 대조하는 실행자는 ④ 레인이 만든다 — 이 레인은 만들지 않았다.
2. **`money-basis-003` 은 돌아오지 않았다**(BLOCK-5). `OPEN-1BC-STR16` 이 소유하고 strategy
   slice 가 받는다.
3. **`rate-unit` 의 원문 unit 보존 축에 지금 fixture 가 없다.** 기대값에서 뺐고 새 case 는
   어댑터 계약이 선 뒤에 만든다(`OPEN-1BC-SOURCE-UNIT`) — 기존 case 에 주장을 되돌려 붙이지
   않는다.
4. **네 술어의 사용처가 0 이다**(§9). 계약이 산출하는 축은 이름이 승인돼 있어 정확 비교가 서고,
   산출하지 않는 축은 잠글 대상이 아니다. 그 사이(산출하되 이름이 미승인)에 술어가 설 자리가
   있으나 이 corpus 에는 없다. 정의는 유지하고 self-check 22 건이 실행을 계속 잰다.
5. **B7 셋째 갈래가 다시 열렸다**(§9). 적격성 불리언 단독 변이가 계약에 걸리지 않는다 —
   `OPEN-1BC-ELIGIBILITY` 가 닫힐 때까지다. 과세·provenance 세탁이라는 B7 의 실질은 잡힌다.
6. **`equals-path` 는 여전히 어휘에 없다.** decision 18 이 푼 셋에 **동등** 술어가 없어
   `uncovered_axes` 의 ②는 그대로 살아 있다.
7. **`not-equals` 의 null 구멍을 고치지 않았다.** 동결분의 의미를 바꾸면 그 술어를 단 case 의
   계약이 소급해 달라지므로 새 술어 셋에만 존재·비-null 전건을 세웠다. 현재 `not-equals`
   사용처는 0 이다.
8. **`BaseAmount` 의 기본값 부재를 재는 컴파일 fixture 가 없다**(verifier r1 M-1 처리 중 실측).
   여섯 금액 타입이 `vatTreatment`·`provenance` 를 기본값 없는 필수 파라미터로 받는 것은 선언이
   지고, 인자를 빠뜨린 호출이 컴파일에 실패함을 보이는 음성 fixture 는 없다(있는 넷은 전부 네
   인자를 넘긴다). **fixture 7 은 그 자리가 아니다** — `YegaAmount` 가 **명시 VAT 인자를 거부**함을
   재는 다른 명제다. 메우려면 `BaseAmount(won, currency)` 가 *"no value passed for parameter"* 로
   실패하는 음성 fixture 가 필요하고, **새 fixture 신설은 이 slice 의 `out_of_scope`** 라 만들지
   않고 등재만 한다.
9. **secret 스캔** `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"
   reports/evidence/m1/1b-c/` — **실제 secret 0.** 매치는 **전부 두 부류이고 그 밖의 매치가
   없다는 것이 이 항목의 주장**이다: **(a) 이 절이 인용한 스캔 정규식과 그것을 가리키는 문장
   자신** — 이 절이 자라면 (a)도 함께 늘므로 **수는 낡는다**, 그래서 수가 아니라 부류로 적는다 ·
   **(b) `scope.md` 의 `minimumTokenCount`**(PMD CPD 설정값, `Token` 이 부분 문자열로 걸린다).
   **2026-09-05 실측 5**(a 넷 · b 하나) — verifier r1 L-4 가 앞선 「4」를 낡은 수로 잡았고,
   고친 것은 수가 아니라 **적는 방식**이다.

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
  fixtures/input/money-basis-005.json fixtures/input/money-basis-006.json
git rm -f reports/evidence/m1/1b-c/fixtures.md reports/evidence/m1/1b-c/golden-manifest.json \
  reports/evidence/m1/1b-c/commands.md
```

**base 에 없는 신규 경로는 evidence 셋뿐이라 `git rm` 이 그 셋을 받는다** — `fixtures/` 아래
17 경로는 전부 base 에 있으므로 `git restore` 로 되돌아간다.

**임시 clone 실측** — 위 두 명령을 임시 clone 에서 실제로 돌렸고, 계약 정정(`6ef8fb5`)과
verifier r1 처리(`58a3c6e`) 뒤에 각각 다시 돌렸다: 세 번 다 exit 0 · `git diff a5ea955 -- fixtures/`
0 행 · `scope.md` 와 ④ 레인 경로 무변경. 마지막 회차는 `M` 17 · `D` 3(경로가 하나 늘었다 —
`money-basis-006` 입력).

## 9. 계약 정정 (2026-09-05, `6ef8fb5`)

**규칙**: `verified_paths` 의 정의가 *"M1 소비 테스트는 이 경로만 정확 비교해야 한다"* 이므로,
**runner 가 계약 함수를 불러 만들어 낼 수 없는 경로는 그 목록에 설 수 없다.** 뺀 값은 기대값에
남겨 나르기만 한다 — 「어휘를 나르기만 하는 것은 이 층을 막지 않는다」(운영자 결정 2026-08-31).
근거와 승인 상태는 `scope.md` 「Phase 3 중 계약 정정」이 갖는다(팀장 판단, 운영자 사후 확인 대상).

| case | 정정 | 계약 쪽 사실 |
| --- | --- | --- |
| `rate-unit-003`·`004` | 기대값을 `{"representable": false}` 로 재정의, `verifies` 를 「구성상 만들 수 없다」로 | `Fact.Absent(UNIT_NOT_DECLARED)` 를 내는 main 코드가 저장소에 없다 — 어휘만 `Carrier.kt` 에 선언돼 있다 |
| `rate-unit-001`·`002`·`005` | `verified_paths` → `$.rate.fraction` 하나 | `ofPercent`/`ofFraction` 은 `Rate` 를 직접 내고, 제수는 `private const` 라 반환값에 없다 |
| `money-basis-006` | `verified_paths` → `$.vatTreatment`·`$.provenance`, `is-present` 걷음 | 적격성 판정은 provenance first-match rule 위에 서고 그 rule 은 1D 소유다 |

**이관된 축 둘.** `OPEN-1BC-SOURCE-UNIT` 을 넓혀 **미선언 거부의 방출**(어떤 사유로 거부되는가)을
원문 unit 보존과 함께 받게 했고, `OPEN-1BC-ELIGIBILITY`(소유 1D)를 신설해 적격성 셋을 받게 했다.

**대가 하나를 감추지 않는다.** 적격성 축이 빠지면서 **Codex B7 의 셋째 갈래**(적격성 불리언만
뒤집는 변이)가 다시 열렸다. `mutation_sweep_targeted.py` 의 그 표적을 **지우지 않고 남겨** 매
실행 `PASSES` 로 보이게 했고, C-7 · 해당 case 의 `not_covered` · `OPEN-1BC-ELIGIBILITY` 셋이 그
사실을 적는다. 닫히는 시점은 1D 가 그 판정을 계약으로 세울 때다.

**분류는 하나도 바뀌지 않았다** — C-5 강등 0, 잔존 authoritative 28, 1B 축 밖 18 이 base 집합과
같다(빠짐 0).
