# checklist — M1 / 1C (Qualification, 면허 자격 판정 커널)

레인(kotlin-implementer)이 슬라이스 마감에 쓴다. 출력 전문·라운드 이력 절 없음
(`evidence-pack` 규격). 종결 = **verifier ready-for-review + 사용자 승인**(Codex 없음,
CLAUDE.md 운영자 지시 2026-09-04).

## verifier r1 수정 라운드 처리 (재작업 누계 1/5)

`_workspace/m1-1c/04_verifier_report.md` not-ready(F-1 high) 뒤 finding 별로 처리했다.
finding 요지는 검증 리포트가 정본이다 — 여기는 처리 결과만 적는다.

| id | sev | 처리 |
| --- | --- | --- |
| F-1 | high | `restrictedSatisfied` 가 제한 면허 행이 실제로 있을 때만 참(공허 충족 제거). 허용업종 전용 그룹은 보유 여부와 무관하게 항상 `Uncertain`(`isPermsnOnlyGroup`). PROBE A·D 재실측 `Uncertain`. |
| F-2 | medium | `RequirementRow.Parsed.init { require(licenseNames.isNotEmpty()) }` — 빈 목록 `Parsed` 를 구성 시점에 거부. |
| F-3 | medium(장부) | 아래 우회표 4행 정정 — `domainSourceReferenceGate` 는 M7(하드코딩 별칭 `mapOf`)을 막지 못한다. 이 레인이 직접 재현해 확인(알려진 제한 ⑦). |
| F-4 | medium | `KEY_STRIP_CHARS` 를 legacy `_KEY_NOISE_RE` 와 정확히 정합(탭/개행·나카구로·대괄호·전각 괄호 추가). PROBE F2·F3 test 추가. |
| F-5 | medium | `judge` 가 `policyData`+`policyVersion` 대신 `Resolution.Resolved<LicenseQualificationPolicyData>` 하나만 받는다 — decision 23 이 구조로 강제된다. |
| F-6 | medium | `$.expiryEvaluated` 를 runner 리터럴에서 `LicenseValidity` 소진 `when` 투영으로. |
| F-7 | low | runner `sourceField` 하드코딩 제거, `permsnIndstrytyList` 를 실제로 읽어 두 번째 행을 만든다(물리 행 하나가 두 필드를 동시에 가질 수 있음, 조사 §3). |
| F-8 | low | `LocalDate.now()` + 강제 캐스트를 고정 기준일 + 소진 `when` 으로(F-5 와 같은 코드 자리라 동봉). |
| F-9 | low(장부) | 아래 head 선언을 이 문서 최종 커밋으로 갱신(정정 이력은 남기지 않는다 — 다음 값이 정본). |
| F-10 | low(장부) | `data-dictionary.md` §3.2.5 의 "1C 가 실제 공고로 관측해 닫는다" 문면은 여전히 낡아 있다(D-4 로 관측 불가 확정, 운영자 결정 전이라 이 slice 가 편집하지 않음) — 재조정 제안이 실행되지 않은 채로 이 evidence 에 등재해 둔다. |

## verifier r2 표적 재검증 처리 (verdict: ready-for-review — 재작업 아님)

`_workspace/m1-1c/05_verifier_report_r2.md` 가 r1 finding 10건 전부 닫힘/부분 닫힘을
확인하고 `ready-for-review` 를 냈다. 남은 medium 2·low 5 는 차단 문턱 아래라 **재검증
없이** 한 커밋으로 처리한다(Phase 4 규정).

| id | sev | 처리 |
| --- | --- | --- |
| N-1 | medium | `foldGroups` 의 분기 순서(실충족 그룹 → `Eligible` 이 ambiguous 보다 먼저)를 example `실제 충족 그룹이 있으면...PROBE A3`로 고정. 분기를 맞바꾸면 그 test 가 즉시 빨개짐을 실측(원복 확인). |
| N-2 | medium | `KEY_STRIP_CHARS`(리터럴 공백 넷)를 `Char.isWhitespace()`(Java `Character.isWhitespace`∪`isSpaceChar`)로 교체 — legacy Python `\s`(유니코드 공백 전체)와 정확히 정합. 전각 공백·NBSP·얇은 공백·수직탭·폼피드 test 넷 추가(원복 시 3건 빨감 확인). |
| N-3 | low | `judge` KDoc·아래 Decision 23 절의 "타입으로 강제된다" 문면을 "어긋남 표면이 사라진다(호출 규약) — 구조 강제는 shared-kernel 변경이라 1C 범위 밖"으로 낮춤. OPEN 후보로 보고에 올린다. |
| N-4 | low | 코드 변경 없음 — 알려진 제한 ⑨ 등재(permsn runner 경로가 `check` 안에서 무커버, 010·011 이 insufficient-evidence 인 동안 구조적). |
| N-5 | low | 코드 변경 없음 — 알려진 제한 ⑩ 등재(허용업종 전용 그룹이 보유 0에서도 `Uncertain` — 결정적으로 `Ineligible`인 자리에서도 미룸, 안전한 방향의 정밀도 손실). |
| N-6 | low(장부) | 알려진 제한 ①에 fixture `policyVersion`(입력 문자열 반영)과 커널이 싣는 값(별칭 정책 자신의 `source`)이 **서로 다른 개념**이 됐다는 사실 추가 — fixture-curator 인계. |
| N-7 | low(장부) | 아래 우회표 5행 실측 칸 정정 — `typeShapeGate` 는 "사유가 문자열이 아니다"를 재지 않는다(sealed 계층 형태만 본다). |

## M1 완료 조건 대조 — 「승인된 authoritative corpus 전체 통과」의 1C 축

`milestone-1.md` 「완료 조건」 — *"승인된 authoritative corpus 전체 통과"*.

- **license 도메인 authoritative 8 / insufficient-evidence 4**(001·008·010·011 — 이유는
  scope.md 「조사 결과」와 `SharedKernelCorpusConformanceTest.kt` 의 전용 filter test).
- **8 case 전부 `check` 안에서 실제 실행되고 대조된다** —
  `bidvector.app.conformance.SharedKernelCorpusConformanceTest`(`gate.tests.app` 등재,
  `gateExecutionGate` 강제). `LicenseEligibility.judge` 공개 API 를 직접 호출한다
  (testFixtures 아님, 1B-c 관례 그대로).
- **`Uncertain` 이 성공/0 으로 합쳐지지 않는다** — `LicenseVerdict` 는 `Eligible`·
  `Ineligible`·`Uncertain` sealed 셋뿐이고 `orElse`·`isEligible(): Boolean` 류의 접는 API 를
  두지 않는다. 소진 `when` 하나로만 소비된다(`LicenseEligibilityTest.kt` 공개 API 표면 test).
- **판정: 1C 축은 「승인된 authoritative corpus 전체 통과」를 충족한다.** 아래 「알려진 제한」의
  사각은 이 판정 밖의 별도 축이고 각자의 이관처가 닫는다.

## 위협 모델 우회 6과 막는 자리 (scope.md 대응표)

| # | 우회 | 막는 자리 | 실측 |
| --- | --- | --- | --- |
| 1 | `Uncertain` 을 `orElse(Ineligible)` 류로 접기 | 접는 API 미제공, 소진 `when` 만 | `LicenseEligibilityTest.LicenseVerdict은 소진 when으로만 소비된다` + property `보유 선언 부재는...접히지 않는다` + property `보유 면허가 0이면...Eligible이 나오지 않는다`(verifier r1 F-1) |
| 2 | 결측 `lmtGrpNo` 행을 각자 그룹으로 흩기 | `groupRows` 가 `null` 을 전부 `Ungrouped` 하나로 폴딩 | example `lmtGrpNo 결측 행은...하나의 AND 그룹으로 폴딩된다` + property `...개수와 무관하게...` (변이 실측 ①) |
| 1a | (verifier r1 F-1 추가) 허용업종 전용 그룹의 공허 충족으로 `Eligible` 위조 | `restrictedSatisfied` 가 제한 면허 행 존재를 전제, 전용 그룹은 항상 ambiguous | example PROBE A·D(모두 `Uncertain`) + property `보유 면허가 0이면...` |
| 1b | (verifier r2 N-1 추가) `foldGroups` 분기 순서가 안 잠겨 실충족 그룹이 있어도 ambiguous 판정이 먼저 걸릴 수 있었음(잠재) | `satisfiedGroups.isNotEmpty()` 분기가 `ambiguous` 분기보다 먼저 오는 순서를 example 로 고정 | example `실제 충족 그룹이 있으면...PROBE A3`(분기 맞바꾸면 즉시 빨감, 변이 실측) |
| 3 | `missingByGroup` 에 보유 면허 포함 | `evaluateGroup.missing` 이 `filterNot { held }` 로만 구성 | property `missingByGroup은...부분집합이다 — R-QUAL-06` (변이 실측 ③) |
| 4 | 별칭 테이블을 `object` 상수로 하드코딩 | **어떤 게이트도 막지 않는다 — 방어는 코드 리뷰뿐이다.** | verifier r1 M7 실측(이 레인도 재확인): `licenseComparisonKey` 본문에 `mapOf("종합건설업" to "토목공사업", …)` 를 끼워 비교 키 경로를 먼저 보게 해도 `clean check` exit 0. `domainSourceReferenceGate` 는 **소스 참조 좌표**(패키지·클래스 이름)만 보고 함수 본문 안 리터럴 `Map` 의 **내용**은 보지 않는다 — 이전 판이 "통과 = 방어"로 잘못 적었다(F-3). scope.md 우회 (4)가 예고한 "못 잡으면 등재"가 여기다: 알려진 제한 ⑦ 등재로 대신한다 |
| 5 | 판정 결과를 문자열 사유로 | `UncertainReason`/`RequirementGroupId` 전부 sealed, 문장 렌더링 없음 | **정정(verifier r2 N-7)**: `typeShapeGate` 는 "사유가 문자열이 아니다"를 재지 않는다(sealed 계층 형태·상속 깊이만 본다). 실제 방어는 타입 선언 자체(문자열 필드 부재) + 코드 리뷰다. |
| 6 | 유효기간 표시를 기본값 `true` 필드로 | `LicenseValidity` 는 `NotVerified` 단일 variant, 검증 variant 부재 | example `유효기간은 항상 미검증으로 표시되고 검증 variant는 존재하지 않는다` + license-012 runner 대조(`licenseValidityUnverified=true`·`expiryEvaluated=false`) |

## Decision 23(D-1) — `LicenseVerdict` 가 `PolicyVersion` 을 싣는다

`LicenseJudgement.policyVersion` 필드로 구현됐다. **verifier r1 F-5 수정 뒤로 어긋남
표면이 사라졌다** — `judge` 가 `policyData`/`policyVersion` 을 독립 인자로 받지 않고
`Resolution.Resolved<LicenseQualificationPolicyData>` 하나만 받아, 값과 그 값을 해석한
version 이 같은 호출 하나에서 나온다(값 따로 · version 따로 조립하는 경로가 없어졌다).
**정정(verifier r2 N-3)**: 이것이 「타입으로 강제」는 아니다 — `Resolution.Resolved` 는
shared-kernel 의 public data class 라 호출부가 `Resolution.Resolved(임의 정책값, 무관한
version)` 을 여전히 조립할 수 있다(실측: `judge(…, Resolution.Resolved(빈정책,
PolicyVersion(Initial, "BOGUS")))` 가 그대로 컴파일·실행된다). 그 생성자를 좁히는 것은
shared-kernel 변경이라 1C `out_of_scope` — scope 안에서 가능한 최선(호출 규약 정합)까지가
이 slice 의 몫이고, 구조 강제는 OPEN 후보로 보고에 올린다. **authoritative 8 어느 case 도
`policyVersion` 을 `verified_paths` 에 걸지 않는다**(scope.md 조사 결과 §2, `OPEN-DIC-01`
양쪽 읽기가 이 corpus 를 깨지 않음) — 그래서 이 필드는 형태·배관·호출 규약은 잠기고 값
자체는 이 slice 의 acceptance 로 잠기지 않는다. 알려진 제한으로 아래 등재.

## OPEN 처리

- **`OPEN-QUAL-07`**: 형태만 1C(`LicenseAliasTable`·`LicenseQualificationPolicyData`·
  `regionalConditions` 자리). 내용(별칭 실제 항목·지역 조건 실제 규칙)은 여전히 OPEN.
- **`OPEN-QUAL-11`**: 1C 는 임시 처리(`UncertainReason.PermittedIndustryCombinationRuleUndecided`)
  만 구현했다. license-011(비authoritative)로 규칙 설계를 검증했으나(evidence 「조사
  결과」 참고) 관측을 통한 해소는 D-4 제안대로 M3 3B(수집 어댑터) 뒤로 재조정 제안 —
  scope.md Phase 6 에스컬레이션 그대로 유효, 이 slice 가 닫지 않는다.
- **`OPEN-QUAL-05`(해소 집행)**: `LicenseValidity.NotVerified` 로 U-7 요구(「유효기간
  미검증」 노출)를 구현했다 — 이 slice 가 그 구현이다.

## 알려진 제한

1. **`policyVersion` 값 자체는 corpus 로 잠기지 않는다**(위 D-1 참고) — 별칭 정책 내용이
   생기고 version 이 실제로 갈릴 때 재검증이 필요하다. **추가(verifier r2 N-6)**: fixture
   `license-*` 기대값의 `policyVersion` 은 입력 `licenseGroupingPolicyVersion` 문자열을
   되돌려 받는 형태인데, 커널이 실제로 싣는 것은 **별칭 정책 자신의 `source`**(decision
   23)라 **이제 두 값은 서로 다른 개념**이다. 지금은 어느 case 도 `$.policyVersion` 을
   검증하지 않아 무해하지만, 승격되면 그 자리에서 바로 어긋난다 — fixture-curator 인계.
2. **`OPEN-QUAL-11` 조합 규칙은 여전히 임시 처리** — 규칙 자체(과차단/과추천 우선순위)는
   운영 관측 전까지 확정 아님.
3. **`requirementsBySourceField`(license-010) 는 만들지 않았다** — §3.2.1 이 요구하는
   것은 flat `requirementSourceFields` 집합뿐이라 010 은 여전히 insufficient-evidence.
4. **지역 조건(`regionalConditions`) 내용은 빈 리스트 자리표시자** — QUAL-08(별도 slice)
   이 채운다.
5. **CPD 관찰(비차단)** — `qualification:cpdCheck` 가 `LicenseEligibilityTest.kt` 안
   유사 test 셋업 중복을 보고한다(관찰 모드, 실패 아님 — `architecture-policy.properties`
   "CPD: 관찰 모드뿐이라 실패 없음"). 판정 로직(fold) 자체의 중복은 아니다.
6. **`milestone-1.md` 「Slice 1C」 항목 갱신은 이 evidence 에 포함하지 않았다** — scope.md
   의 「계약 갱신」 버킷은 세션 모델(오케스트레이터) 저작 대상이라(CLAUDE.md 기획 문서
   레인) 구현 레인이 손대지 않는다. 완료 보고를 오케스트레이터에 전달한다.
7. **하드코딩 별칭(코드 상수)은 어떤 게이트도 잡지 못한다**(verifier r1 F-3/M7, 이
   레인이 재확인) — `domainSourceReferenceGate` 는 소스 참조 좌표(패키지·클래스 이름)만
   보고 함수 본문 안 리터럴 `Map` 의 내용은 보지 않는다. R-QUAL-03(코드 상수 별칭 금지)의
   방어는 **코드 리뷰뿐**이다 — scope.md 우회 (4)가 예고한 "못 잡으면 등재"가 이것이다.
8. **`data-dictionary.md` §3.2.5 문면이 낡아 있다**(verifier r1 F-10) — "M1 자격
   slice(1C)가 실제 공고로 관측해 닫는다"고 적혀 있으나 D-4(scout 조사)로 관측 불가가
   확정됐다. 운영자 결정 전이라 이 slice 가 문면을 편집하지 않는다 — 재조정 제안(위 OPEN
   처리 절)이 아직 승인 전임을 등재만 해 둔다.
9. **`permsnIndstrytyList` runner 경로가 `check` 안에서 무커버다**(verifier r2 N-4) —
   `requirementRowsFrom` 이 그 필드를 실제로 읽지만(F-7), 그 필드를 가진 입력은
   license-010·011 둘뿐이고 둘 다 insufficient-evidence 라 authoritative corpus 로
   실행되지 않는다. 커널 쪽 permsn 분기는 example·property 로 덮이지만 JSON→행 매핑
   자체는 무커버다 — 010/011 기대값과 현재 커널 거동이 손으로는 일치함을 확인했다(scope.md
   「조사 결과」). fixture 승격은 fixture-curator 소관.
10. **허용업종 전용 그룹은 보유 0에서도 `Uncertain` 이다**(verifier r2 N-5, 정밀도 손실) —
    이 경우 (a)·(b) 두 읽기가 **둘 다 `Ineligible`** 이라 답이 결정적인데도 미결로 미룬다.
    그 그룹 하나가 공고 안에 있으면 다른 그룹의 확정 `Ineligible` 도 가려 전체가 `Uncertain`
    이 된다(PROBE MIX2). 안전한 방향(과추천 없음)이고 §3.2.5 임시 처리 의도와 일치하므로
    결함이 아니라 정밀도 손실로만 등재한다 — `OPEN-QUAL-11` 해소 시 함께 정밀화될 수 있다.

## 사용자 승인 — 2026-09-06, slice 1C 종결

verifier r2 `ready-for-review`(HEAD `f3a9dba`, 잔여 non-blocker 일괄 `f9d80e2`, milestone 문단 `269c522`)
위에서 **사용자 승인 2026-09-06**. 재작업 1/5(verifier r1 not-ready — F-1 high 공허 충족). D-2~D-7 사후
확인 포함. 같은 자리의 운영자 결정 셋: **`OPEN-QUAL-11` 담당을 M3 3B 수집 뒤 관측으로 재조정**(D-4),
**`data-dictionary.md` §3.2.5 문면을 구현 규칙으로 갱신**(decision 26 — 알려진 제한 8 해소), **PMD CPD
를 main source set 한정 실패 모드로 전환**(1A-b 이월, 하네스 레인 별도 커밋). 알려진 제한 1·7·9·10 과
N-3(`Resolution.Resolved` 생성자 좁힘 = shared-kernel 변경) 은 등재 유지. 지역 판정 slice(D-5)·license-*
`contract_binding` 신설은 후속 지시 대상.

## acceptance 전건 (`commands.md` 상세)

Q-0(격리 worktree `clean check`) · Q-1(`clean check`, `--no-build-cache`) · Q-2
(`:qualification:test`) · Q-3(게이트 넷 단독) · Q-4(`:app:test --tests '*Conformance*'`) ·
Q-5(`qualityBaseline`) · Q-7(`:build-logic:test`) 전부 exit 0. Q-6(스윕)은 manifest 편집이
없어 생략(scope.md 조건).
