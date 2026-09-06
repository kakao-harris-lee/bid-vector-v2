# Slice 계약 — M1 / 1C · Qualification(면허 자격 판정 커널)

```yaml
milestone: m1
slice: 1c-qualification
base_sha: 4a6ca5c4ee5bb666fe786fbe395c5c00be775e75
head_sha: 리뷰 시점의 HEAD
in_scope:
  - qualification/**                          # 도메인 모듈(ADR 0006 D-2·D-4 — shared-kernel 만 의존). 판정 커널·타입·정책 데이터 형태·test
  - config/quality/api-type-policy.properties  # 조건부 — 도메인 API 타입 허용 목록에 1C 값 타입 등재가 필요할 때만(게이트 정의 편집이므로 사유를 evidence 에)
  - config/quality/gate-tests.properties       # 조건부 — 1C 게이트 성격 test(불변식·컴파일 fixture) 등재 시 gate.tests.qualification 키
  - config/quality/architecture-policy.properties  # 조건부 — 「갱신 불필요」가 기본 판정(qualification 은 이미 domain 층 목록에 있다)
  - app/src/test/kotlin/bidvector/app/conformance/**   # corpus 소비 runner 의 dispatch 를 license-* authoritative 로 확장(1B-c 관례, decision 22 (d))
  - app/build.gradle.kts                       # 조건부 — runner 가 qualification 공개 API 를 부르기 위한 testImplementation(project(":qualification")) 한 줄
  - fixtures/manifest.yaml                     # 조건부 — license-* case 의 contract_binding 신설·verified_paths 정정(fixture-curator 소관, 기대값 무변경 원칙)
  - docs/discovery/data-dictionary.md          # 계약 갱신 — §3.2·§9 의 운영자 결정이 실제로 난 행만
  - docs/discovery/capability-map.md           # 계약 갱신 — §14.2 OPEN-DIC-01·OPEN-QUAL-07·OPEN-QUAL-11 행만
  - milestone-1.md                             # 계약 갱신 — 「Slice 1C」 항목만
  - reports/evidence/m1/1c/**
out_of_scope:
  - shared-kernel/src/main/**                  # 1B 승인 산출물. 1C 가 필요로 하는 carrier 가 없으면 멈추고 보고(임의 확장 금지)
  - 자격 게이트(QUAL-02 — 후보 제외)·워크플로·decision·adapters·app main   # 1E·M2·M3. 1C 는 판정 커널만 낸다
  - 요건 원문 파싱(lcnsLmtNm 텍스트 → 면허 이름 목록)                        # 어댑터 소관(M3 3B). 1C 커널 입력은 구조화 요건 행이다 — D-3
  - 지역 자격 **판정**(QUAL-08)·기술부문/협회 축(QUAL-04)·실적(QUAL-10)·capacity(QUAL-11)   # 지역 「조건」의 정책 데이터 형태만 1C(D-5). 판정 커널은 별도 slice
  - 별칭·포괄 코드 테이블의 **내용**(OPEN-QUAL-07)                          # 형태·version 배관만 1C. 내용은 외부 데이터·운영자
  - 유효기간 검증(U-7)                                                       # 「미검증」 표시만
  - fixture 기대값·입력 파일 편집 · 새 case 신설                              # decision 19 관례: 계약 어휘가 fixture 어휘를 따른다(D-2). 어긋나면 멈추고 보고
  - Python ML · bid-vector/ symlink(읽기 전용) · _workspace/**
  - 승인 문서 편집 일체(위 승격 행·항목 밖)
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # Q-0
  - "./gradlew --no-build-cache clean check"                                                          # Q-1 — 1A·1A-b 게이트 전건(sizeGate 멤버 30·typeShapeGate depth 0·CPD 관찰·domainApiTypeGate·domainSourceReferenceGate) 위에서 초록
  - "./gradlew :qualification:test"                                                                    # Q-2 — 1C 도메인 test(property 포함)
  - "./gradlew :qualification:domainApiTypeGate :qualification:domainSourceReferenceGate :qualification:typeShapeGate :qualification:sizeGate"   # Q-3 — 게이트 단독 실행(1B 관례)
  - "./gradlew :app:test --tests '*Conformance*'"                                                      # Q-4 — license-* authoritative 8 이 runner 로 실행·대조
  - "./gradlew qualityBaseline"                                                                        # Q-5
  - "python3 fixtures/tools/mutation_sweep_adversarial.py"                                             # Q-6 — 조건부(manifest 편집 시) — 잔존 authoritative 수·1C 축 밖 무변경
  - "./gradlew :build-logic:test"                                                                     # Q-7 — 1A 승계
rollback: |
    **정본은 `reports/evidence/m1/1c/rollback.md`**(scope.md 파생, 경로 한정 restore + 신규 경로 rm, 임시 clone 실측).
    qualification 모듈은 자리표시자로 돌아간다(base 의 build.gradle.kts 만).
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 착수 근거: 운영자 지시 2026-09-06(1A-b 종결 승인과 함께),
`milestone-1.md` 「Slice 1C」, 1B(값 타입)·1B-c(corpus 실행자)·1A-b(게이트) 종결.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 4a6ca5c4ee5bb666fe786fbe395c5c00be775e75..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**.

---

## 이 slice 가 하는 일

`milestone-1.md` 「Slice 1C」 다섯 문장을 **판정 커널 하나**로 낸다(`v2-지침서.md` §4.2 · `data-dictionary.md` §3.2):

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`LicenseVerdict`** = sealed `{ Eligible(satisfiedGroup), Ineligible(missingByGroup), Uncertain(reason) }` + 동반 산출(요구 면허 전체 집합 · 그룹별 미충족 · 파싱 실패 행 수 · 요건 소스 필드 집합 · 유효기간 미검증 표시). **boolean 하나로 접히지 않는다** | §3.2.1 · §4.2 |
| ② | **결합 규칙 U-8** — 그룹 간 OR · 그룹 내 AND, `lmtGrpNo` 결측 행은 하나의 그룹으로 AND. fold 는 **커널 하나**(R-QUAL-01·02) | §3.2.2 · QUAL-03 |
| ③ | **`UncertainReason` 4값(U-5)** — `RequirementDataAbsent`·`RequirementUnparsable`·`OperatorLicensesNotDeclared`·`CollectionFailed`. **`Uncertain ≠ Ineligible`** — 보유 면허 미기재는 미보유가 아니다 | §3.2.3 · QUAL-01 |
| ④ | **사유는 실제 미보유만** — `missingByGroup ⊆ 미보유 집합`(R-QUAL-06, property). 「요구된 것」과 「부족한 것」은 다른 개념 | QUAL-01 acceptance · `OPEN-DEC-05` |
| ⑤ | **정책 데이터 형태** — 별칭·포괄 코드·지역 조건을 versioned policy data 로(코드 상수 금지, R-QUAL-03). 내용은 비어 있어도 형태·version 배관이 선다(OPEN-QUAL-07). version 은 1B `PolicyVersion`·`EffectiveDatedPolicy` 재사용 | §4.2 · QUAL-05 |
| ⑥ | **`permsnIndstrytyList` 요건 소스 포함** — 허용업종만 보유한 경우는 **사유 있는 `Uncertain`**(OPEN-QUAL-11 임시 처리, 과차단·과추천 어느 쪽으로도 틀리지 않는 쪽). 어느 소스에서 온 요건인지가 결과에 남는다 | §3.2.5 |
| ⑦ | **유효기간 미검증 표시** — 판정 결과에 구조화된 표시로 드러난다(문자열 아님) | §3.2.4 · U-7 |
| ⑧ | **corpus 실행** — runner dispatch 를 license-* authoritative 8(002·003·004·005·006·007·009·012)로 넓혀 `check` 안에서 대조 | 1B-c 관례 |

**만들지 않는 것**: 요건 원문 파서(어댑터), 후보 제외 게이트(QUAL-02), 지역·기술부문·실적·capacity 판정, 두 번째 판정 경로(QUAL-12 폐기), 사람이 읽는 문장(§3.1 — 렌더링 시점).

---

## 운영자 결정 필요 — 착수 전(D-1) · 계약 고정(D-2~D-5, 세션 모델 판단·사후 확인)

| id | 결정 | 선택지 | 추천 | 시점 |
| --- | --- | --- | --- | --- |
| **D-1** | **`OPEN-DIC-01` — 면허 축이 정책 version 을 싣는가.** U-6(수치 판정에만 version)의 제외 절과 QUAL-03 무조건 acceptance·§4.2 「versioned policy data」가 어긋난다 | ① 제외 절이 acceptance 를 무효화 — `LicenseVerdict` 에 version 없음 ② 제외 절은 「U-6 의 `PolicyVersion` 어휘 대상이 아니다」일 뿐, 면허 축은 **자기 축의 versioned policy**(별칭·포괄 코드 테이블 version)를 갖고 판정 결과에 그 version 을 싣는다 | **②** — 별칭 테이블이 바뀌면 같은 입력의 판정이 바뀐다(R-QUAL-03 collapse 가 실물). QUAL-05 「어휘 테이블 version 이 바뀌면 그 이후 판정에만 새 version」은 ② 없이는 성립 불가. fixture(license-001 의 `policyVersion`)도 ② 를 전제한다. 형태는 1B `PolicyVersion` 재사용 | **운영자 결정 2026-09-06: ② 채택**(decision 23 — 1B-c 의 22 를 잇는다). `LicenseVerdict` 는 판정에 쓴 별칭·포괄 코드 정책의 `PolicyVersion` 을 싣는다. U-6 제외 절은 「U-6 가 정한 `PolicyVersion` 어휘의 대상이 아니다」로 읽는다 — 면허 축은 자기 축의 versioned policy 를 갖는다. 정본: `data-dictionary.md` §9 OPEN-DIC-01 행 · `capability-map.md` §14.2 |
| **D-2** | 계약 어휘 = fixture 어휘 | 1C 타입·필드 이름을 §3.2.1 과 license-* 기대값 JSON 의 이름(`verdict`·`satisfiedGroups`·`missingByGroup`·`requiredLicenses`·`unparsableRowCount`·`requirementSourceFields`·`policyVersion`·`uncertainReason`)과 맞춘다 | decision 19 관례(1B-c). 어긋나는 자리는 Phase 2 대조가 낸다 — 계약이 맞추되 승인 문면(§3.2.1)과 fixture 가 서로 다르면 **멈추고 보고** | 계약 고정 |
| **D-3** | 커널 입력 경계 | **구조화 요건 행** — 행마다 (면허 이름 목록, `lmtGrpNo` nullable, 소스 필드 태그 `lcnsLmtNm`/`permsnIndstrytyList`, 파싱 실패 표시) + 운영자 보유 면허 선언(선언 부재 = `OperatorLicensesNotDeclared`). 원문 텍스트 파싱은 어댑터(M3) 소관 — 1C 는 `RequirementUnparsable` variant 와 `unparsableRowCount` carrier 만 만든다. 외부 식별자(`lmtGrpNo`·`lmtSno`)는 **문자열 도메인 타입**(R-QUAL-05 제로패딩) | 커널이 파서를 품으면 QUAL-12 의 「어휘를 판정과 점수가 공유」 구조가 재생산된다 | 계약 고정 |
| **D-4** | `OPEN-QUAL-11` 처리 | 1C 는 §3.2.5 운영자 판정대로 **사유 있는 `Uncertain`** 만 구현한다. 「1C 가 실제 공고로 관측해 닫는다」는 **성립하지 않는다** — 1C 에는 수집 어댑터가 없다(M3 3B). 담당을 **M3 3B 뒤 관측**으로 재조정 제안 | Phase 2 legacy 조사가 legacy 데이터에 관측 사례가 있는지 확인 — 있어도 pinned read-only 코드 저장소이지 운영 데이터가 아니다 | Phase 6 에스컬레이션 |
| **D-5** | 지역 조건 | 「지역 조건을 versioned policy data 로 분리」는 **형태**만 1C(정책 데이터 타입에 지역 조건 자리). 지역 자격 **판정**(QUAL-08 — 구조화 필드 `cmmnSpldmdCorpRgnLmtYn`·`rgnLmtBidLocplcJdgmBssCd` 기반)은 별도 slice — data-dictionary 에 rule 절이 없고 fixture 도 없다 | 판정 커널을 근거 문면 없이 짓지 않는다 | 계약 고정 |

---

## 위협 모델 — 1C 고유 경계 (Phase 2.5, 세션 모델 직접)

**방어한다** — 판정 커널의 **의미 회귀**: (a) 3값이 boolean 으로 접히는 경로(`Uncertain` 이 `Ineligible` 이나 `Eligible` 로 합쳐짐) (b) 결합 규칙 오적용(결측 그룹 OR 흩기·그룹 내 OR) (c) 사유에 보유 면허가 섞임(R-QUAL-06) (d) 두 번째 fold 구현(R-QUAL-01) (e) 별칭·포괄 코드가 코드 상수로 들어옴(R-QUAL-03) (f) 유효기간 미검증 표시 누락. **방어하지 않는다** — 정책 데이터 **내용**의 옳음(OPEN-QUAL-07, 운영자·외부 데이터), 요건 원문 파싱의 정확성(어댑터), 게이트 정의 편집(하네스 저자), 리플렉션.
**경계가 요구 축소가 아닌 이유**: §4.2·§3.2 는 판정의 **형태와 규칙**을 정하고 파싱·내용은 다른 자리(§5.3 필드 계약·OPEN-QUAL-07)에 둔다.

**우회 다섯과 막는 자리**: (1) `Uncertain` 을 `orElse(Ineligible)` 류로 접기 → 접는 API 를 두지 않고 소진 `when` 만(1B carrier 관례), property: 어떤 입력에서도 `Uncertain` 이 게이트 제외로 이어지지 않음(QUAL-02 소비자 계약은 1E 가 받되 1C 가 접는 API 부재로 구성상 막음). (2) 결측 `lmtGrpNo` 행을 각자 그룹으로 → example test(license-005) + property(결측 행 수와 무관하게 그룹 1). (3) `missingByGroup` 에 보유 면허 → property(`missingByGroup ⊆ 요구 − 보유`). (4) 별칭 테이블을 `object` 상수로 → 정책 데이터는 `EffectiveDatedPolicy` 배관으로만 주입, 소스 참조 게이트가 하드코딩 면허 이름 리터럴을 잡는지는 Phase 2 확인(못 잡으면 등재). (5) 판정 결과를 문자열 사유로 → `ReasonCode`/`UncertainReason` enum·sealed 만, 문장 렌더링은 out(§3.1). (6) 유효기간 표시를 기본값 `true` 필드로 두어 조용히 「검증됨」처럼 보이게 → 표시는 기본값 없는 sealed(`NotVerified` 단일 variant, 검증 variant 는 존재하지 않음 — 만들 수 없는 것을 만들지 않는다).

---

## 조사(Phase 2) 결과 — `_workspace/m1-1c/` (2026-09-06)

- **authoritative 8(002·003·004·005·006·007·009·012) 전부 실행 가능** — 구조화 요건 행 + 보유 면허 목록으로 fold·결측 폴딩·사유 파생·U-7 표시가 산출된다. `policyVersion` 은 어느 case 의 verified_paths 에도 없다(값은 있으나 잠기지 않음 — decision 23 이 형태를 정했다).
- **어휘 불일치 7 → 계약 고정(D-6·D-7, 세션 모델 판단·사후 확인)**: ① `satisfiedGroup`(명세 단수) vs fixture 복수 → **명세를 복수로 정정**(§3.2.1, decision 24 — OR 이라 둘 이상 충족 가능) ② `missingByGroup["__ungrouped__"]` → 그룹 id 를 `sealed { Numbered, Ungrouped }` 로, `__ungrouped__` 는 직렬화 표기(§3.2.1 등재) ③ `foldedUngroupedRowsIntoSingleAndGroup` → `Ungrouped` 그룹의 존재에서 파생(projection), 커널 필드 아님 ④ license-011 의 다섯째 `UncertainReason` `PermittedIndustryCombinationRuleUndecided` → **§3.2.3 에 variant 추가**(decision 25, U-5 「필요해지면 더한다」의 첫 적용) ⑤ license-010 의 `requirementsBySourceField` → 1C 는 flat `requirementSourceFields` 만(010 은 OPEN-QUAL-11 로 insufficient 유지) ⑥ U-7 표시(`licenseValidityUnverified`·`expiryEvaluated`) → 판정 봉투의 동반 산출로 sealed 단일 variant `ValidityNotVerified`(검증 variant 없음), 불리언은 projection 이 낸다 ⑦ `policyVersion` 평문 vs `PolicyVersion(effectiveFrom, source)` → projection 이 `source` 를 낸다(잠기지 않는 축).
- **입력 경계(D-3 확인)**: 구조화 행(`lmtGrpNo` nullable·`lmtSno`·`lcnsLmtNm`·`permsnIndstrytyList` optional) + `operatorLicenses`(nullable → `OperatorLicensesNotDeclared`) + 수집 실패 표현(009) + 정책. 커널에 남는 파싱은 면허명 → 비교 키 정규화 하나이고 그것은 정책 데이터(별칭 테이블) 소비다.
- **legacy 알고리즘**(`01_scout_legacy-qualification.md`): 행→요건, 결측은 단일 키 한 그룹 AND, 그룹 0 이면 `unknown`(행 있었으면 파싱불가/없었으면 부재), 보유 비면 `unknown`, 그룹별 요구 ⊆ 보유 → 충족, 하나라도 충족 → eligible. V2 는 같은 규칙을 커널 하나로 재작성(재사용 코드 없음 — service 재작성 갈래).
- **게이트 영향**: `LicenseVerdict`·`UncertainReason`·그룹 id 는 **`sealed interface`**(typeShapeGate depth 래칫 0 — sealed class 는 즉시 위반). `domainApiTypeGate` 는 부동소수만 막아 `String` 면허명 통과. sizeGate 멤버 30 은 fold·사유 파생·표시를 타입/함수로 가르면 여유.
- **OPEN-QUAL-11 관측 불가**(D-4 확정): 저장소 DB 덤프(2026-05-19)에 `eligibility_raw` 컬럼 자체가 없고 goldens 에 `lcnsLmtNm` 캡처 0, 유일 실측 1건은 (a)/(b) 를 구별 못 한다. **담당 재조정(M3 3B 수집 뒤 관측)을 Phase 6 에 올린다.**
- runner 확장: `TARGET_DOMAINS` 에 `license` 추가 + dispatch(executor 는 `qualification` 공개 API 호출 → projection). app 은 최상위라 `testImplementation(project(":qualification"))` 허용(layer).

| id | 결정(세션 모델, 사후 확인) | 내용 |
| --- | --- | --- |
| **D-6** | `satisfiedGroups` 복수 · 그룹 id sealed(`Numbered`/`Ungrouped`) | 명세 §3.2.1 정정(decision 24). 근거 위 ① ② |
| **D-7** | `UncertainReason` 다섯째 값 | 명세 §3.2.3 추가(decision 25). license-011 은 여전히 insufficient(OPEN-QUAL-11) — 값은 커널이 방출하되 corpus 승격은 아니다 |

## OPEN — 수령·신설

| id | 상태 |
| --- | --- |
| `OPEN-DIC-01` | **D-1 — 착수 전 운영자 결정** |
| `OPEN-QUAL-07` | 형태만 1C, 내용은 OPEN 유지 |
| `OPEN-QUAL-11` | 1C 는 임시 처리(`Uncertain`)만 — 관측 담당 재조정 제안(D-4) |
| `OPEN-QUAL-05`(해소) | 「유효기간 미검증」 표시를 1C 가 구현 |
