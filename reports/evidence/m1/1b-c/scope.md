# Slice 계약 — M1 / 1B-c · corpus 계약 정렬

```yaml
milestone: m1
slice: 1b-c-corpus-contract-alignment
base_sha: a5ea955c44a935c3245288e6a72c830eeeee64b8
head_sha: 리뷰 시점의 HEAD
  # slice 산출물은 in_scope 경로의 변경이다. range 에 하네스 레인 커밋이 섞일 수 있고
  # 아래 「하네스 레인 변경」 절이 가른다(evidence-pack SKILL, 1A·1B 관례 승계).
in_scope:
  - fixtures/tools/manifest_contract.py          # ① 술어 어휘 확장 — holds() 에 is-present · differs-from-path · differs-from-case
  - fixtures/tools/mutation_sweep_adversarial.py # ① 새 술어마다 (a)(a′)(b)(c) 변이체 생성 규칙
  - fixtures/tools/mutation_sweep_targeted.py    # ① 같은 이유(표기 변이체)
  - fixtures/tools/manifest_prose_consistency.py # ① 조건부 — 새 술어 키를 산문 대조가 읽어야 하면
  - fixtures/manifest.yaml                        # ①②③ schema.extensions 술어 정의 · rate-unit 5 · money-basis 6 의
                                                  #      classification/verified_*/contract_binding/change_history/uncovered_axes
  - fixtures/expected/rate-unit-00[1-5].json      # ② 재추출 — 기대값 토큰을 1B 계약 어휘로(case 별 운영자 승인 뒤)
  - fixtures/expected/money-basis-00[1-6].json    # ③ 처분 결정에 따라 — 재추출 또는 무변경(폐기는 파일 삭제가 아니라 classification 변경)
  - fixtures/input/rate-unit-00[1-5].json         # ② 조건부 — 입력 토큰 정렬(ESTIMATED_PRICE → ESTIMATED 류)이 필요할 때만
  - fixtures/input/money-basis-00[1-6].json       # ③ 같은 조건
  # ~~shared-kernel/src/testFixtures/** · shared-kernel/build.gradle.kts~~ — D5 기제 변경 2026-09-05(아래 「Phase 3 중 계약 정정」 둘째):
  #   testFixtures 는 하네스 게이트 셋(packageOwnershipGate·sourceSetLayoutGate·ArchUnit 프로덕션 스캔)을 깨는 것이 실측돼 쓰지 않는다.
  #   shared-kernel 은 build.gradle.kts 포함 무접촉(아래 src/test 의 fixture 12 만 예외).
  - app/src/test/**                               # ④ corpus 소비 테스트(conformance runner) — 공개 API 만, JSON/YAML 역직렬화는 여기서만(app 은 domain 층 밖)
  - app/build.gradle.kts                          # ④ `testImplementation(libs.snakeyaml)` 한 줄(testFixtures 의존은 없다)
  - gradle/libs.versions.toml                     # ④ 계약 갱신 2026-09-05(팀장) — `snakeyaml` 카탈로그 등재 한 줄 한정(버전은 Boot BOM,
                                                  #   app testCompileClasspath 에 이미 transitively 해석되는 좌표를 카탈로그 관례대로 명시할 뿐).
                                                  #   runner 가 manifest.yaml 을 읽기 위함. 다른 카탈로그 편집은 out
  - config/quality/gate-tests.properties          # ④ `gate.tests.app` 에 소비 테스트 FQCN 등재(gateExecutionGate 실행 강제)
  - shared-kernel/src/test/**                     # ④ 계약 갱신 2026-09-05(팀장, 아래 「Phase 3 중 계약 정정」) — 컴파일 fixture 12
                                                  #   (negative/positive/mutant-12-rate-undeclared-unit) + CompileFailureHarnessTest 등재 한정.
                                                  #   rate-unit-003·004 의 실행자다. 다른 test 편집은 out
  - docs/discovery/data-dictionary.md             # 계약 갱신 — 운영자 결정이 실제로 난 항목의 해당 행만(§9 OPEN 표)
  - docs/discovery/capability-map.md              # 계약 갱신 — §14.2 OPEN-1B-CONTRACT · OPEN-1B-CORPUS 행만
  - milestone-1.md                                # 계약 갱신 — 「Slice 1B-c」 항목만
  - reports/evidence/m1/1b-c/**
out_of_scope:
  - shared-kernel/src/main/**            # 1B 승인 산출물. 계약 자체를 바꾸지 않는다 — fixture 를 계약에 맞춘다, 반대가 아니다
  - fixtures/tools 밖의 새 fixture 도구 · 새 case 신설(11 case 처분만)   # corpus 재추출은 기존 11 에 한정
  - 다른 도메인 corpus(license · base-amount-provenance · floor-shortfall · koneps · ml)의 재분류
  - money-basis-003 이 요구하는 검색 경로 타입(STR-16)   # strategy slice 소유 — 이월만
  - Python ML · bid-vector/ symlink(읽기 전용) · _workspace/**
  - 승인 문서 편집 일체(위 in_scope 로 개별 승격한 행·항목 밖)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # C-0 (= 1B B-0)
  - "./gradlew --no-build-cache clean check"                                                          # C-1
  - "./gradlew :shared-kernel:test :app:test"                                                          # C-2 — 컴파일 fixture 12(shared-kernel) + 소비 테스트 runner(app, decision 22 (b′)) 둘 다
  - "./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate"              # C-3 — main 무변경 확인(전건 exit 0 유지)
  - "./gradlew qualityBaseline"                                                                        # C-4
  - "python3 fixtures/tools/mutation_sweep_adversarial.py"                                             # C-5 — 새 술어 위에서 위반 변이체 통과 0
  - "python3 fixtures/tools/mutation_sweep_adversarial.py --crosscheck-pyyaml"                         # C-6
  - "python3 fixtures/tools/mutation_sweep_targeted.py"                                                # C-7
  - "python3 fixtures/tools/manifest_prose_consistency.py"                                             # C-8
  - "python3 fixtures/tools/check_legacy_numbers.py"                                                   # C-9
  - "11 case 의 input_file·expected_file SHA-256 재계산 후 manifest 값과 대조"                            # C-10 — 재추출 case 는 previous_* 이력 동반
rollback: |
    **정본은 `reports/evidence/m1/1b-c/rollback.md`**(구현 레인이 scope.md 파생으로 쓴다, 1B 관례).
    경로 한정 `git restore --source=<base> --staged --worktree -- <in_scope 경로 개별 인자>` +
    base 에 없는 신규 경로는 `git rm`. 임시 clone 실측 의무(evidence-pack 2026-09-04).
```

작성: 2026-09-04, 세션 모델(Fable 5.1) 단독 — 기획 문서 레인(운영자 지시 2026-09-04).
착수 근거: 1B 사용자 승인 2026-09-04(`reports/evidence/m1/1b/checklist.md` 「리뷰 요청 조건」),
`milestone-1.md` 「Slice 1B-c」(decision 15), 운영자 착수 지시 2026-09-04.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline a5ea955c44a935c3245288e6a72c830eeeee64b8..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**. 목록이 생기면
SHA·경로·목적 한 줄씩 등재하고 「slice 산출물이 아니며 in_scope 밖, 운영자 승인 하에 같은
range 에 있다」를 명시한다. 그 명령이 잡지 못하는 하네스 편집(`agent-workflow.md`·
`milestone-*.md` 의 승격 절 밖 부분)도 같은 절에 손으로 적는다(1B r4 L-4 교훈).

**계약 갱신 커밋(세션 모델, 2026-09-05)**: `977083b`(scope.md decision 18~22) · `8b393ce`(ADR 0007
§5 OPEN-ADR-06/16 해소 +37줄, capability-map §14.2 행) · `94009f4`(milestone-1.md 「Slice 1A-b」 신설
+27줄, 「Slice 1B-c」 착수 문단). 셋 다 in_scope 의 계약 갱신 절이다.

**역방향 파급 검사(evidence-pack 규격, stem 기준 grep)**: ADR 0007 삽입(258행 이후)을 가리키는
`0007:<line>` 좌표 — **0건**. milestone-1.md 삽입(67행 이후)을 가리키는 `milestone-1.md:<line>` 좌표 —
**4건, 전부 `capability-map.md` §14.2 의 M0 작성 좌표**(`:80`·`:82`·`:98`·`:99` — 완료 조건·구현
규칙 절을 가리키며 이제 +27 밀렸다). 그 행들은 이 slice 의 승격 절(OPEN-1B-CONTRACT·OPEN-1B-CORPUS
·OPEN-ADR-06 행) 밖이라 **고치지 않고 등재한다**(1B 관례 — M0 evidence 의 낡은 좌표는 범위 밖
기존 부채). 1A-b 착수 시 그 slice 가 capability-map §14.2 를 승격하면 함께 정정한다.

---

## 이 slice 가 하는 일

1B 는 타입·계약 형태를 완성했으나 1B 축 corpus(`rate-unit` 5 · `money-basis` 6)는 열하나 전부
`insufficient-evidence` 로 남아 M1 완료 조건 「승인된 authoritative corpus 전체 통과」의 1B 모집단이
비어 있다(`reports/evidence/m1/1b/fixtures.md` §1, BLOCK-1~5). 이 slice 는 그 간극을 **넷**으로 닫는다.

| # | 일 | 막고 있던 것 | 레인 |
| --- | --- | --- | --- |
| ① | **술어 어휘 확장** — `schema.extensions.verified_projections` 에 `is-present`·`differs-from-path`·`differs-from-case` 를 정의하고 `manifest_contract.py` `holds()` 와 두 스윕의 변이체 생성 규칙을 같은 어휘로 넓힌다. 설계는 `m1_contract_binding.predicate_design` 이 이미 갖고 있다 — 이 slice 는 그것을 **구현**한다 | BLOCK-1(도구 쪽 동결) | fixture-curator(Python) |
| ② | **`rate-unit` 5 재추출** — 기대값의 자작 토큰(`Accepted`/`Rejected`·`RateUnitUndeclared`·`preservedSourceUnit`)을 1B 계약 어휘(`Fact.Known`/`Absent` · `ReasonCode.UNIT_NOT_DECLARED` · 계약에 자리 없는 축은 **기대값에서 제거**)로 다시 쓴다. 기대값 파일이 바뀌므로 되돌림이 아니라 **재추출**이고 `previous_expected_sha256`·`change_history` 를 동반한다. case 별 운영자 승인이 전건이다(D4) | BLOCK-2(토큰 어휘) | fixture-curator |
| ③ | **`money-basis` 6 처분** — case 마다 「재추출 / 계약에 맞춰 재정의 / 다른 slice 이월」 중 하나를 운영자 결정(D3·D4)으로 정하고 실행한다. 001·004 는 basis 혼합이 컴파일 차단이라 거부 객체가 없고(BLOCK-4), 003 은 검색 경로 타입이 계약에 없다(BLOCK-5), 002 의 `contract_binding` 은 `BaseAmount.compareTo` 를 가리켜 이미 낡았다(1B Codex #2 가 `compareKnownVat` 로 바꿈) | BLOCK-3·4·5 | fixture-curator |
| ④ | **corpus 소비 테스트(conformance runner)** — authoritative 로 되돌아온 1B case 를 실제 V2 계약 위에서 실행해 `verified_paths`·`verified_projections` 로 대조하는 Kotlin 테스트. 지금은 Python 스윕(계약의 강건성)만 있고 **V2 산출을 기대값과 대조하는 실행자가 없다** — 「통과」를 말하려면 이것이 있어야 한다 | 실행자 부재(1B 가 example test 를 입력 참고로만 썼다) | kotlin-implementer |

**하지 않는 것**: `shared-kernel/src/main` 을 건드리지 않는다. fixture 가 계약과 어긋나면 fixture 를
고친다. 계약 쪽 결함이 드러나면 이 slice 를 멈추고 1B 재개(별도 계약)로 올린다.

---

## 운영자 결정 필요 — 착수 전(D1~D3) · 도중(D4~D5)

| id | 결정 | 선택지 | 오케스트레이터 추천 | 시점 |
| --- | --- | --- | --- | --- |
| **D1** | 술어 어휘 **동결 해제**(운영자 결정 2026-09-02 의 개정). `not-equals` 하나였던 어휘에 셋을 더한다 | (a) `predicate_design` 의 셋 그대로 해제 (b) `differs-from-case` 만(가장 강한 하나) (c) 해제 안 함 → 이 slice 성립 불가 | **(a)** — 셋은 서로 다른 변이 갈래를 잡고(`is-present`=삭제·null, `differs-from-*`=값 뒤집기), 미승인 토큰을 문면에 담지 않는다는 원 결정의 취지를 그대로 지킨다 | 착수 전 |
| **D2** | **결과 토큰 어휘** — fixture 직렬화가 쓸 상태·사유 이름을 1B 계약 이름으로 채택하는가 | (a) 1B 계약 어휘 그대로(`Fact.Known`/`Absent` · `Measurement.Measured`/`Unmeasurable` · `ReasonCode` enum 이름 · `Basis.ESTIMATED`) (b) fixture 어휘 유지 + 매핑 표 (c) 새 중립 어휘 | **(a)** — 계약이 채택한 이름을 그대로 쓰면 「승인 문면 없는 자작 토큰」 문제가 정의상 사라지고 매핑 층(낡는 자리)이 생기지 않는다. `uncovered_axes` 「승인된 피연산자 부재」 축의 `unblocks_when`(결과 토큰 어휘 명시 승인)이 이 결정으로 충족된다 | 착수 전 |
| **D3** | **BLOCK-3 해제** — money-basis 축의 둘째 자물쇠(운영자 명시 승인 부재) | (a) 축 전체 승인 (b) case 별 승인(D4 로 흡수) (c) 축 유지 → money-basis 6 은 이 slice 밖 | **(b)** — 001·004·003 은 처분이 다르므로 축 단위 승인은 그 차이를 덮는다 | 착수 전 |
| **D4** | **case 별 처분** — 아래 처분 표 | 표 참조 | 표 참조 | 재추출 직전(Phase 3 시작), 처분 표를 evidence 로 올려 승인 |
| **D5** | **소비 테스트의 위치와 JSON 읽기** — **Phase 2 조사(2026-09-04, `_workspace/m1-1b-c/01_scout_preflight.md` §1)가 (a)를 반증했다**: 1차 게이트 `moduleDependencyGate` 가 domain 모듈의 **test 의존까지** `group.forbidden`(jackson·kotlinx-serialization·gson·org.json 전부)으로 막고(실측 FAILED), ADR 0002 D-9 문면도 main/test 를 구분하지 않는다 | (a) ~~shared-kernel test + jackson~~ — 게이트 정책·ADR D-9 개정 없이 불가 · (a′) ADR D-9 와 게이트에 test-scope 예외를 명시 개정 · **(b′) shared-kernel `testFixtures`(java-test-fixtures) 에 JSON 무의존 projection(값→`Map`)을 두고, runner 는 `app/src/test` 에서 `testFixtures(project(":shared-kernel"))` + jackson 으로 JSON 을 읽어 대조** — `internal`(`Rate.fraction`) 은 testFixtures 가 main 과 associate 되면 보인다 · (c) JSON 파서 없이 manifest→Kotlin 소스 생성 | **(b′)** — 커널의 어느 scope 에도 JSON 이 들어가지 않아 D-9 를 문면 그대로 지키고, 게이트·ADR 개정이 없다. projection 이 「계약이 나르는 축」(`contract_binding.carries`)을 코드로 고정하는 부수 효과도 있다. (a′) 는 게이트 정의 축소라 위협 모델상 피하고, (c) 는 생성 코드 유지가 corpus 보다 커진다. **전건 실측 셋 통과(2026-09-04, `01_scout_preflight.md` §8)**: ① `java-test-fixtures` 만 붙이면 KGP 가 testFixtures 를 main 과 associate 해 `Rate.fraction` 접근이 추가 설정 없이 컴파일된다 ② testFixturesCompileClasspath 는 shared-kernel 자신 + kotlin-stdlib 뿐이고, app 의 testFixtures 의존도 `moduleDependencyGate` 를 통과한다 ③ `gate.tests.app` 에 FQCN 을 더해 실행을 강제할 수 있다. **알려진 제한으로 등재할 것**: 1차 게이트의 declared 판정이 `testFixtures*` 의존 버킷을 보지 않아, 뒷날 `testFixturesImplementation` 으로 금지 group 을 넣어도 지금은 걸리지 않는다 — 이 slice 는 그 버킷에 외부 의존을 하나도 넣지 않는 것으로 답하고 게이트 확장은 하네스 slice 로 이월한다(`OPEN-1BC-TESTFIXTURES-GATE`) | **운영자 승인 대기 — 실측 조건은 해제됨** |

### D4 — case 처분 표(초안, 운영자 승인 대상)

| case | 지금 막는 것 | 추천 처분 | 되돌아오면 계약 경로 |
| --- | --- | --- | --- |
| rate-unit-001 | `outcome: Accepted`·`preservedSourceUnit` | **재추출** — `fact: Known`, `rate.fraction`·`conversionDivisor` 유지, `preservedSourceUnit` 제거(어댑터 층 축, 1C+). 술어: `differs-from-case`(거울 003) + `is-present`(`$.rate.fraction`) **⚠ 조사 대조 「바뀜」** — `verifies` 가 「원문 unit 이 보존된다」를 명시 주장한다. 재추출은 `verifies` 를 「percent→fraction 명시 변환」으로 **좁히고** 보존 축을 `uncovered_axes`(`OPEN-1BC-SOURCE-UNIT`, 1C+ 어댑터 소유)로 **명시 이관**한다 — 주장을 지우는 것이 아니라 소유를 옮기는 것이며, 그 이관 자체가 운영자 승인 대상이다 | `Rate.ofPercent` |
| rate-unit-002 | 같음 | **재추출** — 같은 형태(거울 없음 → `is-present` + `verified_paths` 의 `$.rate.fraction` 정확 비교) | `Rate.ofFraction` |
| rate-unit-003 | `Rejected`·`RateUnitUndeclared` | **재추출** — `fact: Absent`, `reasonCode: UNIT_NOT_DECLARED`(승인 어휘, D2), `guessAttempted` 제거(구성상 불가는 컴파일 fixture 가 증명) | `Rate` internal 생성자 + `Fact.Absent` |
| rate-unit-004 | 같음 + case 간 동등 주장 | **재추출** — 003 과 같은 형태. 「크기가 결과를 가르지 않는다」는 `differs-from-case` 가 아니라 003 과의 **같은 기대값**으로 표현(주장 자체는 `verifies` 문면) | 같음 |
| rate-unit-005 | `Accepted` | **재추출** — 001 과 같은 형태, 상한 밴드 부재(2.0 보존)는 `$.rate.fraction` 정확 비교로 잠금 | `Rate.ofPercent` |
| money-basis-001 | BLOCK-4(컴파일 차단) | **재정의** — 기대값을 「표현 불가」 형태로: `{"representable": false}` 하나 + `is-present`. 실행자는 `CompileFailureHarnessTest` 의 basis 교차 negative fixture 를 이 case 의 `type_path` 로 등재(이미 존재, 1B fixture 11). 폐기하지 않는다 — 계약이 지키는 사실이고 corpus 에 남아야 한다 **⚠ 조사 대조 「바뀜」** — `verifies` 가 「사유와 함께 거부된다」(런타임 거부 객체)를 주장하고 계약은 컴파일 차단이다. 승인된 계약(1B)이 이기므로 `verifies` 를 「basis 가 다른 두 금액의 비교는 **표현 불가**(컴파일 차단)」로 다시 쓴다. 입력 파일의 `ESTIMATED_PRICE` → `ESTIMATED` 정렬 동반 | 컴파일 fixture |
| money-basis-002 | 술어 부재 · `contract_binding` 낡음 | **재추출** — `fact: Known`, `comparedBases` 유지, `result` 제거(경계 포함성은 이 case 가 잠그지 않음). `contract_binding` 을 `compareKnownVat(BaseAmount, BaseAmount)` 로 정정 | `compareKnownVat` |
| money-basis-003 | BLOCK-5(검색 경로 타입 부재) | **이월** — strategy slice(STR-16 소유)로. 이 slice 는 `uncovered_axes` 에 소유 OPEN 을 달고 classification 유지 | — |
| money-basis-004 | BLOCK-4 | **재정의** — 001 과 같은 형태(min/max 혼합의 표현 불가) **⚠ 조사 대조 「불명」** — `verifies` 는 「들어갈 수 없다」만 주장해 컴파일 차단도 형식상 만족하나 재구성 방식이 001 과 같다. **001 과 묶어 한 결정**으로 올린다. 입력 `ESTIMATED_PRICE` 정렬 동반 | 컴파일 fixture |
| money-basis-005 | 표기(`VatTreatmentMismatch`) | **재추출** — `fact: Absent`, `reasonCode: VAT_TREATMENT_MISMATCH`, `operationPerformed` 제거 | `compareKnownVat` / `sameKnownVat` |
| money-basis-006 | 술어 부재 | **재추출** — 토큰은 이미 일치(`UNKNOWN`·`Undeclared`). `eligibleForAuthoritativeCorpus` 는 corpus 메타이지 계약 산출이 아니라 제거, `is-present`/정확 비교로 잠금 **⚠ 조사 대조 「바뀜」 — 처분 정정**: `not_covered` 문면이 `$.eligibleForAuthoritativeCorpus` 를 이미 「실질이 덮이는 세 축」의 하나로 적어 두었다. 제거하지 않고 **유지**하며 `verified_paths` 정확 비교로 잠근다 | `Money` 필수 파라미터 |

처분 표는 Phase 2 조사(2026-09-04)로 대조됐다 — **유지 7 · 바뀜 3(rate-unit-001·money-basis-001·006) · 불명 1(money-basis-004)**. 바뀜·불명 넷은 위 ⚠ 표기대로 `verifies`/`not_covered` 문면 재작성이 따른다. **운영자 승인 2026-09-05(decision 21) — 전체 승인, 바뀜 3·불명 1 포함.**

---

## 운영자 결정 (2026-09-05, decision 18~22 — 1B 의 17 을 잇는다)

| # | 결정 | 내용 | 정본 자리 |
| --- | --- | --- | --- |
| **18** | D1 술어 어휘 동결 해제 | **(a) 셋 전부** — `is-present`·`differs-from-path`·`differs-from-case` 를 `schema.extensions.verified_projections` 어휘에 더한다. 2026-09-02 동결 결정의 개정. `operand` 의미는 술어별로 갈린다(`is-present` 없음 · `differs-from-path` 경로 · `differs-from-case` case id, 조사 §2) | `fixtures/manifest.yaml` `schema.extensions`(Phase 3 ①) |
| **19** | D2 결과 토큰 어휘 | **(a) 1B 계약 어휘 그대로** — 상태 `Fact.Known`/`Absent`·`Measurement.Measured`/`Unmeasurable`, 사유 `ReasonCode` enum 이름, `Basis.ESTIMATED`. 자작 토큰(`Accepted`·`Rejected`·`Comparable`·`Uncertain`·`RateUnitUndeclared`·`VatTreatmentMismatch`·`BasisMismatch`·`ESTIMATED_PRICE`)은 재추출로 사라진다. `uncovered_axes` 「승인된 피연산자 부재」 축의 `unblocks_when` 충족 | `fixtures/manifest.yaml` `uncovered_axes`(Phase 3 ②) · `data-dictionary.md` §9 해당 행 |
| **20** | D3 BLOCK-3 | **(b) case 별 승인** — D4 처분 표가 흡수 | 이 문서 D4 표 |
| **21** | D4 case 처분 | **전체 승인**(바뀜 3·불명 1 포함) — 위 처분 표가 정본. money-basis-003 은 strategy 이월(`OPEN-1BC-STR16`), rate-unit-001 보존 축은 `OPEN-1BC-SOURCE-UNIT` 로 이관 | 이 문서 D4 표 · `fixtures/manifest.yaml` 각 case `change_history` |
| **22** | D5 소비 테스트 | **(b′)** — shared-kernel `testFixtures` 의 JSON 무의존 projection + `app/src/test` runner(jackson) + `gate.tests.app` 등재. testFixtures 버킷에 외부 의존 0. 사각은 `OPEN-1BC-TESTFIXTURES-GATE` 로 1A-b 이월 | 이 문서 in_scope · `docs/adr/0002` 는 개정 없음(D-9 문면 그대로 충족) |

### Phase 3 중 계약 정정 (2026-09-05, 팀장 판단 — 운영자 사후 확인 요청)

curator 레인의 ④ 인계가 계약 사실 하나를 드러냈다: **1B 계약에는 `Rate` 의 상태(`Fact`)나 단위 미선언의
사유(`UNIT_NOT_DECLARED`)를 방출하는 함수가 없고**(`ofPercent`·`ofFraction` 은 `Rate` 를 직접 낸다, 미선언
입력을 받는 공개 경로 자체가 없다), money-basis-006 의 적격성 축(`autoTaggedFromDefinitionDefault`·
`eligibleForAuthoritativeCorpus`·`reasonCode`)은 1D(provenance first-match rule) 소유다. 소비 테스트가 그
값을 만들어 붙이면 **방출자를 지어내는 것**이라 위협 모델 우회 (5)와 같은 갈래가 된다. 그래서 decision 21 의
처분 표를 「실행 가능성」 축에서 정정한다 — 승인된 처분의 **취지**(1B 계약 어휘로 정렬, 컴파일 차단은 표현
불가로)는 그대로고 바뀌는 것은 **어느 경로를 잠그는가**다:

| case | 정정 | 근거 |
| --- | --- | --- |
| rate-unit-003·004 | `{"representable": false}` 형태(money-basis-001 과 같은 가족) + **컴파일 fixture 12**(모듈 밖 `Rate(BigDecimal)` 생성자 호출 컴파일 실패) 를 실행자로. `UNIT_NOT_DECLARED` 방출은 1C+ 어댑터 소유로 `uncovered_axes` 등재 | 「구성상 불가」는 런타임 `Absent` 보다 강한 사실이고 이미 계약이 지키는 것 |
| rate-unit-001·002·005 | `$.fact` 를 verified_paths 에서 뺀다(기대값에는 남김 — 「어휘를 나르기만 하는 것은 이 층을 막지 않는다」, 운영자 결정 2026-08-31). 002 의 `conversionDivisor` 도 계약 산출이 아니면 같은 처리 | 값 산출은 `$.rate.fraction` 정확 비교가 잠근다 |
| money-basis-006 | verified_paths → `$.vatTreatment`·`$.provenance`(입력 다섯 성분으로 만든 `Money` 가 실제로 내는 둘). 적격성 셋은 나르기만, `OPEN-1BC-ELIGIBILITY`(소유 1D) 신설 | 1B 는 적격성을 판정하지 않는다 |
| money-basis-001·002·004·005 | 변경 없음 | 002·005 는 `compareKnownVat` 가 `Fact` 를 실제로 낸다, 001·004 는 fixture 11 |

**in_scope 갱신**: `shared-kernel/src/test/**` 를 fixture 12 한정으로 승격(위 YAML 주석). **acceptance C-2 보강**:
`:app:test` 추가(runner 가 app 에 산다). **runner 설계**: case id → executor 명시 dispatch(미등재 authoritative
case = 실패), executor 는 value(계약 함수 호출 → JSON 무의존 projection → verified_paths 정확 비교 + 술어 넷)와
compile-fixture 위임(`$.representable == false` + fixture 파일 실재 단언, 컴파일 실패 단언은
`CompileFailureHarnessTest` 가 `gate.tests.shared-kernel` 로 강제) 둘.

**둘째 정정 — D5 기제 변경 (b′) → (d), 2026-09-05(팀장 판단, 운영자 사후 확인 요청).** runner 레인이
C-1 전체 `clean check` 에서 실측: shared-kernel 에 `java-test-fixtures` 를 켜면 하네스 게이트 셋이 깨진다 —
`packageOwnershipGate`·`sourceSetLayoutGate` 의 `expectedSourceSets = {main, test}` 하드코딩 둘, ArchUnit
`DoNotIncludeTests()` 가 `testFixtures` 세그먼트를 거르지 않아 testFixtures 산출물이 프로덕션 바이트코드로
스캔됨(Phase 2 §8 은 컴파일·1차 게이트만 봤다). 셋 다 `build-logic` 편집이라 게이트 정의 = 하네스 slice 소유.
**testFixtures 가 필요했던 유일한 이유는 `Rate.fraction`(internal) 읽기**이고 그것은 공개 API 로 대체된다:
`Rate` 는 data class 라 `Rate.ofFraction(기대값) == 실제값` 동등 비교가 값을 잠근다(P-1a 왕복 성질). 나머지
경로(`compareKnownVat` 의 `Fact<Int>`·`basis.name`·`vatTreatment.name`·`Provenance` variant 는 소진 `when`)는
원래 공개다. 그래서 **(d) app/src/test runner, 공개 API 만, testFixtures·projection 계층 없음** — shared-kernel 은
fixture 12 밖에서 무접촉으로 돌아간다. decision 22 의 취지(커널 어느 scope 에도 JSON 없음·게이트/ADR 개정 없음)는
그대로고 강화됐다. 부수 사실: verifier r1 L-4(모듈 밖에서 율 값을 읽을 수단 0)는 동등 비교로 우회된다 —
1C~1E 가 율 값을 소비할 때의 결정은 여전히 열려 있다. `OPEN-1BC-TESTFIXTURES-GATE` 는 「1차 게이트 사각」에서
**「하네스 게이트 넷이 testFixtures 를 모른다(1차 declared 판정·expectedSourceSets 둘·ArchUnit 필터)」** 로 문면을
넓혀 1A-b 에 이월한다 — testFixtures 를 쓰는 slice 가 생기기 전에 게이트가 그 source set 을 어떻게 다룰지
(순수성 규칙 적용/면제) 결정해야 한다.

**같은 날 함께 난 결정(이 slice 밖, 등재만)**: `OPEN-ADR-06` **(a)** — sizeGate 에 타입 멤버 수 상한 **30**(PSI 소스 기준) + 상속 깊이·인터페이스 수는 baseline(2·1) 대비 증가 금지 래칫 · `OPEN-ADR-16` **(a)** — PMD CPD(`de.aaschmid.cpd`, `toolVersion` 으로 엔진 독립), **관찰 모드** 시작(리포트만, 1C 종료 시 실패 모드 전환 결정), `minimumTokenCount` **50**(도구 기본값 유지), 첫 배선 acceptance 에 Gradle 9.6.1 스모크(실패 시 (b) 자체 구현 후퇴). 둘의 배선은 **하네스 slice `1A-b` 신설**(`OPEN-1BC-TESTFIXTURES-GATE` 도 같은 자리). 정본은 `docs/adr/0007` §5 · `capability-map.md` §14.2 · `milestone-1.md` 「Slice 1A-b」.

---

## OPEN — 이관 수령과 신설

| id | 요지 | 상태 |
| --- | --- | --- |
| `OPEN-1B-CONTRACT` | 계약 술어 설계·실행 | **이 slice 가 소유**(1B 로부터 이관, decision 15). ① 이 닫는다. 정본 `capability-map.md` §14.2 |
| `OPEN-1B-CORPUS` | 1B 축 authoritative 0건과 M1 완료 조건 | **이 slice 가 소유.** ②③④ 가 닫는다 — 닫힘 조건은 「1B 축 authoritative ≥ 1 이고 소비 테스트가 `check` 안에서 그 전건을 통과」 |
| `OPEN-1BC-STR16`(신설 예정) | money-basis-003 의 검색 경로 타입 | strategy slice 로 이월. `fixtures/manifest.yaml` `uncovered_axes` 에 등재 |
| `OPEN-1BC-TESTFIXTURES-GATE`(신설 예정) | 하네스 게이트 넷이 `testFixtures` source set 을 모른다 — 1차 `moduleDependencyGate` declared 판정이 `testFixtures*` 의존 버킷을 안 봄(조사 §8) · `packageOwnershipGate`·`sourceSetLayoutGate` 의 `expectedSourceSets={main,test}` 하드코딩 · ArchUnit `DoNotIncludeTests()` 가 testFixtures 산출물을 프로덕션으로 스캔(runner 레인 C-1 실측, 4 failures) | 하네스 slice 1A-b 로 이월. **이 slice 는 testFixtures 를 쓰지 않는 것으로 답한다**(D5 기제 변경 (d)). testFixtures 를 도입하는 slice 가 생기기 전에 게이트가 그 source set 에 순수성 규칙을 적용할지 면제할지 결정 필요 |
| `OPEN-1BC-SOURCE-UNIT`(신설 예정) | `preservedSourceUnit`(원문 unit 보존)의 소유 층 — 1C+ 어댑터 계약 | 재추출 시 기대값에서 빼는 축. 어댑터 slice 가 받는다 |

---

## 위협 모델 — 1B-c 고유 경계 (Phase 2.5 를 세션 모델이 직접 채운다)

**방어한다** — ⑴ 기대값의 **변이**(주장 필드 삭제 · null 치환 · 값 뒤집기 · 다른 확정 토큰)가 계약을
통과하지 못한다(Python 스윕, 「authoritative = 위반 변이체 통과 0」). ⑵ V2 산출이 기대값의 `verified`
경로에서 어긋나면 `check` 가 실패한다(Kotlin 소비 테스트). ⑵ 는 지금까지 없던 축이다.
**방어하지 않는다** — 기대값·manifest·도구 자체를 고치는 저자(1B 위협 모델과 같은 경계 — 게이트의
정의를 바꾸는 편집은 모델 밖), 기대값의 **의미** 오류(그것은 `source.kind`·`expected_reasoning` 의 승인
근거와 운영자 승인이 막는 층이지 기계가 아니다), 소비 테스트가 덮지 않는 `does_not_carry` 축.
**경계가 요구 축소가 아닌 이유**: `data-extract.md` 절대 규칙(기대 동작은 승인 명세·authoritative fixture 에서)과
`milestone-1.md` 완료 조건(「승인된 authoritative corpus 전체 통과」)은 둘 다 **승인된 기대값 위에서의 통과**를
말하고, 기대값의 승인 자체는 운영자 행위다.

**우회 다섯과 막는 자리**: (1) 새 술어에 미승인 토큰을 `operand` 로 넣기 → `operand_source` 필수 + D2 어휘
밖 토큰은 `ManifestFormatError`. (2) `differs-from-case` 의 거울을 자기 자신으로 지정 → 도구가 자기 참조를
거부. (3) `is-present` 만 달아 값 변이(a′)를 놓치기 → 값을 주장하는 case 는 `verified_paths` 정확 비교 또는
`differs-from-*` 동반을 스윕 (a′) 갈래가 강제(통과하면 강등). (4) 소비 테스트를 실행 집합에서 빼기 →
`gateExecutionGate`(`gate-tests.properties` 등재, D5 뒤). (5) 재추출로 기대값을 V2 현재 산출에 맞춰 버리기
(golden 자동 생성 금지 위반) → 재추출 case 의 `expected_reasoning` 이 **승인 문면**을 인용해야 하고 V2 출력을
근거로 들면 리뷰에서 거부 — 이것은 기계가 아니라 승인 층이 막는다고 위에 명시했다.

---

## 조사(Phase 2) 항목

1. 1차 게이트(`external.allowed.domain`)·kotlin-conventions 가 **test 소스 집합**을 덮는지 실측(D5 전건).
2. 스윕 두 스크립트의 변이체 생성이 술어 종류에 어떻게 의존하는지 — 새 술어 셋의 (a)(a′)(b)(c) 생성 규칙 설계.
3. 11 case 의 `verifies`·`expected_reasoning` 대 처분 표 대조 — 재작성이 주장을 바꾸지 않는지.
4. `manifest_prose_consistency.py` 가 새 키를 읽어야 하는지.
5. 1B 축 밖 authoritative 18 case 의 스윕 결과가 술어 확장 뒤에도 동일한지(무해성 — 1B fixtures.md X-1 관례).
