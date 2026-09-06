# checklist — M1 / 1C (Qualification, 면허 자격 판정 커널)

레인(kotlin-implementer)이 슬라이스 마감에 쓴다. 출력 전문·라운드 이력 절 없음
(`evidence-pack` 규격). 종결 = **verifier ready-for-review + 사용자 승인**(Codex 없음,
CLAUDE.md 운영자 지시 2026-09-04).

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
| 1 | `Uncertain` 을 `orElse(Ineligible)` 류로 접기 | 접는 API 미제공, 소진 `when` 만 | `LicenseEligibilityTest.LicenseVerdict은 소진 when으로만 소비된다` + property `보유 선언 부재는...접히지 않는다` |
| 2 | 결측 `lmtGrpNo` 행을 각자 그룹으로 흩기 | `groupRows` 가 `null` 을 전부 `Ungrouped` 하나로 폴딩 | example `lmtGrpNo 결측 행은...하나의 AND 그룹으로 폴딩된다` + property `...개수와 무관하게...` (변이 실측 ①) |
| 3 | `missingByGroup` 에 보유 면허 포함 | `evaluateGroup.missing` 이 `filterNot { held }` 로만 구성 | property `missingByGroup은...부분집합이다 — R-QUAL-06` (변이 실측 ③) |
| 4 | 별칭 테이블을 `object` 상수로 하드코딩 | `licenseComparisonKey` 가 `LicenseAliasTable`(정책 데이터) 하나만 소비, 코드 리터럴 별칭 없음 | `domainSourceReferenceGate` 통과(하드코딩 좌표 없음) + example `별칭 미등재 면허는 원문 정규화 키로 보존되고 collapse되지 않는다` |
| 5 | 판정 결과를 문자열 사유로 | `UncertainReason`/`RequirementGroupId` 전부 sealed, 문장 렌더링 없음 | `typeShapeGate` 통과(전부 `sealed interface`) |
| 6 | 유효기간 표시를 기본값 `true` 필드로 | `LicenseValidity` 는 `NotVerified` 단일 variant, 검증 variant 부재 | example `유효기간은 항상 미검증으로 표시되고 검증 variant는 존재하지 않는다` + license-012 runner 대조(`licenseValidityUnverified=true`·`expiryEvaluated=false`) |

## Decision 23(D-1) — `LicenseVerdict` 가 `PolicyVersion` 을 싣는다

`LicenseJudgement.policyVersion` 필드로 구현됐다(`LicenseEligibility.kt` 전 분기가
호출부가 준 `policyVersion` 을 그대로 실어 낸다). **authoritative 8 어느 case 도
`policyVersion` 을 `verified_paths` 에 걸지 않는다**(scope.md 조사 결과 §2, `OPEN-DIC-01`
양쪽 읽기가 이 corpus 를 깨지 않음) — 그래서 이 필드는 형태·배관만 잠기고 값 자체는 이
slice 의 acceptance 로 잠기지 않는다. 알려진 제한으로 아래 등재.

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
   생기고 version 이 실제로 갈릴 때 재검증이 필요하다.
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

## acceptance 전건 (`commands.md` 상세)

Q-0(격리 worktree `clean check`) · Q-1(`clean check`, `--no-build-cache`) · Q-2
(`:qualification:test`) · Q-3(게이트 넷 단독) · Q-4(`:app:test --tests '*Conformance*'`) ·
Q-5(`qualityBaseline`) · Q-7(`:build-logic:test`) 전부 exit 0. Q-6(스윕)은 manifest 편집이
없어 생략(scope.md 조건).
