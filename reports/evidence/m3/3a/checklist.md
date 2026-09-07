# 리뷰 요청 조건 점검 · 판단이 갈린 지점 · 알려진 제한 — M3 / 3A

F-6(low, verifier r1)에 따라 `commands.md`에 있던 「판단이 갈린 지점」·「알려진 제한」과
병렬 레인 경계 검사를 이 문서로 옮겼다. 이 갱신은 verifier r3(`ready-for-review`,
`_workspace/m3-3a/04_verifier_report_r3.md` N3-1~N3-9) 반영판이다. acceptance 실행
결과·exit code는 `commands.md`가 갖는다.

## verifier r3 N3-1~N3-9 — 등재/수정 결과

r3 는 blocker·high 0 으로 `ready-for-review` 판정을 냈다(medium 4·low 5, 전부 차단 아님).
team-lead 지시대로 재검증 없이 한 커밋으로 처리한다 — N3-1 만 `gateExecutionGate` 초록
한 줄을 남긴다.

- **N3-1**(medium, 게이트 장부) — `gate.tests.procurement` 가 11 class 였는데 실제는 13
  class(`CollectionPolicyTest`·`ParseDelimitedFigureListTest` 누락). `config/quality/
  gate-tests.properties` 에 두 class 를 더했다. **`gateExecutionGate` 가 이 방향(등재
  누락)을 못 잡는 이유**: 이 게이트는 「등재된 class 가 실제로 돌았는가」만 검사하는
  단방향 술어다(등재 ⊆ 실행이면 통과) — 「실행된 모든 class 가 등재됐는가」(실행 ⊆ 등재)
  는 재지 않는다. 알려진 제한에 등재한다(아래). 재실행: `./gradlew :procurement:
  gateExecutionGate --rerun` → `BUILD SUCCESSFUL`.
- **N3-2**(medium, corpus 결속) — 002·003/004·026 corpus dispatch 가 `verified_paths`
  로는 production 변이를 판별하지 못한다(002 는 executor 가 밴드를 fixture 에서 직접
  조립, 003/004 는 유일한 판별값 `reason` 이 `verified_paths` 밖, 026 은 판별값이 아예
  전부 상수 경로). 판별은 procurement 단위 test(`ResolveAmountTest`·`RawObservationTest`·
  `CanonicalizeTest`)가 진다 — verifier 의 변이 실측(M4·M5·M6)이 이를 확인했다. **읽는
  법**: 「27/27 dispatch」는 「27 case 가 production 거동을 잠근다」로 읽지 않는다 — 아래
  「알려진 제한」에 등재한다. curator 결함이 아니다(`policy-values.md` 가 2026-09-01
  규칙으로 그 어휘를 의도적으로 뺐다). 어휘 승인 후속을 016 의 P-7 요청과 함께 curator
  레인에 요청한다.
- **N3-3**(medium, 장부) — manifest `contract_binding` 8 건(002·003·004·014·016·018·023·
  026)이 v2-defect 수정 뒤에도 「미배선」 산문 그대로였다 — production 이 실제로 무엇을
  고쳤는지로 갱신했다(`fixtures/manifest.yaml`, `contract_binding` 필드만, 이 커밋의
  `git diff` 로 대조 가능). 002 의 일본어 조각(`のどこも`)도 정정했다.
- **N3-4**(medium, 장부) — `rollback.md` 에 `fixtures/manifest.yaml` 이 되돌림 대상이
  아닌 이유와 그 결과(되돌린 뒤에도 `contract_binding` 산문이 삭제된 타입을 가리킴)를
  명시했다 — 수동 되돌림 절차도 적었다.
- **N3-5**(low) — 아래 「알려진 제한」에 등재(과세 미확정 vs `UNKNOWN` 표기 층위 차이).
- **N3-6**(low) — `CollectionPolicy.kt` 의 `estimatedPriceResolutionOrder` 옆과 아래
  「판단이 갈린 지점」에 좁힘 판단 근거를 남겼다.
- **N3-7**(low) — `NoticeId.kt` KDoc 과 아래 「알려진 제한」에 비 ASCII 공백(전각 공백
  등) 미흡수를 명시했다(직접 JVM 실측으로 재확인: `"SYN　NTC".replaceAll("\\s+","-")`
  결과가 원문 그대로).
- **N3-8**(low) — `NoticeId.kt` 의 legacy 좌표를 `parsing.py:270-273`(줄 번호)에서
  `parsing.py`(심볼 `normalize_notice_number`)로 정정했다(`policy-values.md` §0 규약).
- **N3-9**(low) — `commands.md`·아래 「알려진 제한」의 "필드 계약 10" 표기를 실제 값
  "11"로 정정했다(018 수정이 `cnstrtnAbltyEvlAmtList` 를 더했다).

## 3A 잔여 일괄 — 판정 필요 case 7건 → v2-defect 수정으로 전건 해소

team-lead 판정(2026-09-07, 「7건 전부 v2-defect 로 동의」)에 따라 6개 결함을 production
수정으로 닫고 27 case 전건을 dispatch 했다(`commands.md` 「v2-defect 수정」 절 참고). 아래
표는 각 판정이 실제로 어떻게 닫혔는지의 기록이다 — 더는 미해결 판정표가 아니다.

| case | 판정 | 닫은 방법 | 커밋 |
| --- | --- | --- | --- |
| 002 | v2-defect(해소) | `RangeBand`·`ContractViolationAxis.RANGE` 신설, `resolveAmount` 의 amount 축에 배선. 운영 정책의 `rangeBands` 는 여전히 빈 표(`sucsfbidLwltRate` B6 밴드는 `legacy-behavior`, 값 확정은 활성 `OPEN-DEC-10` 소유) — 메커니즘만 실장, 실값은 미확정 | `3b247da` |
| 003·004 | v2-defect(해소) | `RawValue`(`Present`/`ExplicitNull`)·`FieldPresence`(`Present`/`ExplicitNull`/`Missing`) 신설, `RawNoticeObservation.presenceOf` | `3e387b0` |
| 016 | v2-defect(해소) | `DocumentedVocabulary` 신설, `policy-values.md` §1.5(조달청 OpenAPI 참고자료 인용)의 「물품·용역·공사·외자」를 옮김. **후속 필요** — 아래 「판단이 갈린 지점」 | `3b247da` |
| 018 | v2-defect(해소) | `FieldScale.DELIMITED_LIST`·`UnnormalizedFigure`·`parseDelimitedFigureList` 신설(D-3A-8, §5.5 「수집 형태만」) — 단위·과세 정규화는 여전히 안 한다(`OPEN-QUAL-10` 소유) | `3b247da` |
| 023 | v2-defect(해소) | `NoticeNumber.of` 정규화를 trim 넘어 ASCII 대문자화 + 내부 공백→`-` 로 확장(팀리드 결정 「구분자 처리는 case 기대값대로」) | `2e1b051` |
| 026(신규 발견) | v2-defect(해소, 우선순위 높음이었음) | `DateTimePatternId` 신설(선택은 정책 데이터, 의미는 고정 파서), `policy-values.md` §1.4 authoritative 형식("YYYY-MM-DD HH:MM:SS")을 옮김. `instantFrom` 이 `InstantResolutionOutcome`(`Resolved`/`Absent`/`ParseFailed`)을 내 파싱 실패를 조용한 null 이 아니라 `CollectionParseFailure(DATE_TIME)`으로 회계한다(음성 test 로 확인) | `3b247da` |

**026은 team-lead 가 사전 지정한 6건에 들지 않았다** — dispatch 배선 중 신규 발견했다. 이
발견 자체가 이 일괄 작업의 목적(fixture 를 실제 production 함수 위에서 실행)이 작동했다는
증거다: 4개 case(013·014·022·027)는 실제 procurement 함수를 호출하지 않고 문서·회계
사실만 echo 한다는 사실도 함께 드러났다 — 이 넷은 값이 어긋나지는 않았지만(불일치가
아니라 「호출 자체가 없음」), `contract_binding.does_not_carry` 에 정직하게 등재했다(아래
「판단이 갈린 지점」 참고).

## 3A 잔여 일괄 — 판단이 갈린 지점(신규)

- **case013·014·022 를 procurement 함수 호출 없이도 dispatch 유지**(vs 판정 필요로 강등) —
  이 셋은 fixture 가 검증하는 축(문서 선언 사실, 페이지 순회 산술)이 애초에 procurement
  런타임 함수의 범위 밖이고(3B/3D 소관이거나 문서 그 자체가 주장), 값 불일치가 없다 —
  「능력 공백」(002·003·004·016·018·023·026)과는 다른 성질이라 판정표에 넣지 않았다. 대신
  `contract_binding.type_path`에 "procurement 함수 호출 없음"을 명시해 검증 가능하게
  남겼다. 이견이 있으면 team-lead 판단으로 재분류 가능.
- **case027 에 검증 밖(verified_paths 밖) 필드를 추가**(`policyCodeSetMatchesDocument`) —
  27번은 원래 문서 사실만 echo 하는 case 였으나, `REAL_POLICY.resultCodeCategories`
  코드 집합과 문서 표를 실제로 대조할 수 있는 저비용 기회를 발견해 추가했다. 이 필드는
  fixture 가 요구하지 않고(verified_paths 밖), 값을 맞추기 위한 것도 아니다 — 실 정책 값
  위에서 계산되는 추가 배선일 뿐이라 「값을 맞추지 않는다」 원칙과 충돌하지 않는다고
  판단했다.
- **(정정, 이전 라운드 판단 번복) 026 을 이번 라운드에서 실제로 고쳤다** — 직전 라운드는
  「값을 맞추려 production 을 손대지 않는다」는 원칙에 따라 `parseSourceZonedInstant` 를
  고치지 않고 판정만 보고했다. team-lead 가 그 보고를 받아 026 을 포함한 7건 전부를
  v2-defect 로 확정하고 명시적으로 수정을 지시했다(2026-09-07, 「값을 맞추려 runner 를
  손대지 말고 production 을 고친 뒤 dispatch 예외 목록에서 빼라」) — 그 지시를 받은
  뒤에는 production 수정이 이 slice 의 정당한 범위다. 이전 판단(「고치지 않는다」)은
  team-lead 승인 이전의 자기 판단이었고, 지금 판단(「고친다」)은 그 승인을 반영한다 —
  두 판단이 다른 시점의 다른 권한 상태를 반영하는 것이지 번복 자체가 임의는 아니다.
- **016 정책 값의 출처가 P-1~P-6 급 「운영자 승인」 항목이 아니다** —
  `policy-values.md` §1.5 는 이미 「물품·용역·공사·외자」를 `authoritative`(문서 인용)로
  적고 있으나, 그 자리는 일반 필드 조사 표(§1.1~§1.6)이지 §6 의 P-1~P-6 승인 결정문
  형식이 아니다. team-lead 지시대로 이 값은 그대로 채택하되(값을 지어낸 것이 아니라
  문서 인용을 그대로 옮겼다), **policy-values.md 에 이 값을 별도 승인 결정 항목(예:
  P-7)으로 명시 등재하는 후속을 curator 레인에 요청한다** — 지금은 §1.5 표 자체가
  근거이고 그 근거가 이미 `authoritative` 층이라 즉시 구성을 막지는 않지만, 다른 여섯
  P-값과 같은 층위로 정합시키는 것이 일관적이다.

- **`String.uppercase()`(인자 없음)가 domain 모듈에서 컴파일되지만 런타임 아키텍처
  게이트에 걸린다** — Kotlin stdlib 의 무인자 `uppercase()`는 내부적으로
  `java.util.Locale.ROOT` 를 참조하는 바이트코드를 만든다. `:procurement:compileKotlin`·
  `:procurement:domainSourceReferenceGate`(소스 레벨 import 검사)는 이것을 잡지 못했고,
  `app` 모듈의 `ArchitectureGateTest`(ArchUnit 바이트코드 스캔, 9 모듈 조합 시점에만 존재)
  가 `clean check`(S-1) 재실행에서 처음 잡았다 — **모듈 단독 `:procurement:check` 만으로는
  이 결함이 안 보인다.** `LicensePolicy.kt` 의 `stripAndLowercase` 선례(같은 이유로 이미
  CharArray 기법을 쓰고 있었다)를 그대로 따라 ASCII 전용 대문자화를 손으로 짰다.

## 리뷰 요청 조건(3A 잔여 일괄 v2-defect 수정, head `04d103c`)

- [x] 구현 diff 커밋, base/head 고정 — base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, head
      `04d103c5d0efa332516c9e24061f54be7777c07c`(이 evidence 커밋 자신은 항상 한 칸 뒤진다).
      `git status --porcelain -- <in_scope 경로 개별 인자>` — evidence 두 파일(이 커밋
      대상) 외 결과 없음.
- [x] acceptance_commands 전부 exit 0 — `commands.md` 「v2-defect 수정 — acceptance_commands
      재실행」 S-0~S-6 전건.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check`(S-1, S-0 격리
      worktree 포함) 통과. 이 재실행에서 `ArchitectureGateTest` 실패(`NoticeNumber.of`의
      `uppercase()` → `Locale.ROOT`)를 잡아 닫았다(`commands.md` S-1 항목, 위 「판단이
      갈린 지점」).
- [x] 변경된 fixture와 정책 version 근거 기록 — 이번 라운드는 `fixtures/manifest.yaml`을
      편집하지 않았다(production 코드만). 신설 정책 슬롯(`rangeBands`·
      `businessCategoryDocumentedLabels`·`dateTimePatterns`·`listComponentSeparator`)의
      근거는 각각 `policy-values.md` §1.4·§1.5(`3b247da` 커밋 메시지에 축어 인용) 또는
      명시적으로 빈 표(`rangeBands` — `OPEN-DEC-10` 미해소).
- [x] 알려진 제한과 rollback 방법 기록 — 아래 「알려진 제한」(갱신). rollback.md 의
      `app/src/test/.../conformance/` 디렉터리 전체 복원 방식은 이번 라운드의 신규 파일
      (`KonepsCollectionDefectFixExecutors.kt`)도 그대로 담는다(경로가 아니라 디렉터리
      단위라 재검증 불요, 이전 라운드에서 이미 clone 실측 완료).
- [x] secret 스캔 통과 — `git diff eaf4d8b..HEAD -- procurement/ app/src/test/kotlin/bidvector/app/conformance/ | grep -niE "(api[_-]?key|secret|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
      (`token` 을 이번엔 뺐다 — 상시 false-positive 로 이미 별도 등재돼 있어 매 라운드
      재확인할 정보가 없다) — 매치 0(exit 1).

## 병렬 레인 경계 검사

수정 라운드 2의 세 커밋(`b86af3c`·`4e992d2`·`74cf7b6`)의 파일 목록에 `fixtures/**`·`docs/**`·
`build-logic/**`·curator 소유 `reports/evidence/m3/3a/{policy-values.md,fixtures-koneps-collection.md}`
혼입이 **0건**이다(`commands.md`의 `git show --stat` 실측). 이 라운드 작업 중 그 curator 파일
둘이 공유 working tree에 **미커밋 상태로 수정돼 있는 것을 관측**했다(다른 세션의 진행 중
작업) — 내 clean-tree 점검은 그 경로를 감시 대상에 넣지 않으므로 스테이징하지 않았고 영향도
없다.

**v2-defect 수정 라운드(`3e387b0`·`2e1b051`·`3b247da`·`04d103c`)** — `git show --stat`
으로 네 커밋 전부 확인, 파일 목록이 `procurement/src/**`·
`app/src/test/kotlin/bidvector/app/conformance/**` 밖을 한 줄도 안 나간다(`fixtures/**`·
`docs/**`·`build-logic/**`·curator 소유 evidence 혼입 0건).

## 판단이 갈린 지점

- **`Ports.kt`의 `suspend` 제거**(r0) — verifier r1 §4(a) 동의. 유지.
- **decision 모듈 test 파급**(r0) — team-lead 승인, scope.md 정정 커밋 `e2a879a`로 반영.
- **`canonicalize`를 procurement에 둔 배치**(r0) — verifier r1 §4(c) "배치는 동의, 실행은
  미달", 실행 미달(F-2)은 라운드 1에서 닫았다. 문면 정합(scope ⑧ 문장과의 긴장)은 문서
  레인 확인 대상으로 남긴다.
- **F-2·F-3·F-4를 한 커밋으로 묶음**(r1) — 세 finding이 같은 함수를 고쳐 중간 커밋이
  컴파일되지 않는다.
- **`FieldUnit`을 `scale`이 전결하는 파생값으로 설계**(r1) — verifier r2 §1이 "승인 문면과
  어긋나지 않는다"고 확인, 다만 "슬롯이 축약(degenerate)"이라는 지적을 받았다. §5.5
  `UnnormalizedFigure`(단위 미확정)를 표현할 자리가 없다는 뜻이라 아래 「알려진 제한」에
  다시 등재했다 — 차단 대상은 아니라는 것이 r2 판정이다.
- **N-1의 실제 폐쇄 경계를 `Notice`에도 그대로 적용**(r2) — `Notice.collected()`는 의도적으로
  `internal`화하지 않았다. 이 팩토리는 항상 `NoticeStatus.Open`만 내고 임의 상태를 조립할
  방법이 없어(우회 대상이 아니다), `internal`로 닫으면 3D·4B가 이 타입을 전혀 쓸 수 없게
  된다 — 과잉 폐쇄다. 실제로 막아야 하는 것은 `copy(status=…)`(우회 5)뿐이고 그것은
  `internal constructor` + `@ConsistentCopyVisibility`가 이미 닫는다. 격리 worktree에서
  둘 다(`collected()` 성공, `copy()` 실패) 재현해 이 구분을 실측으로 확인했다.
- **N-2 표본을 curator 표 실값(`presmptPrce=EXCLUSIVE`)에 맞춤**(r2) — 임의 값 대신 이미
  승인 검토 중인 실제 도메인 값을 표본으로 써서, 나중에 curator 승인값이 들어와도 test가
  같은 값을 재확인하게 했다.
- **datetime 축을 `DEADLINE_AT`·`OPENING_SCHEDULED_AT` 둘 다 배선**(r2) — verifier가 이름을
  둘 다 지목했고, 하나만 배선하면 같은 형태의 미배선 지적이 다음 라운드에 재발할 것으로
  판단해 함께 처리했다.
- **커밋 메시지 test 수 오기**(r0, 정정 완료) — `commands.md`가 정본이라는 선언 유지.
- **추정가격 해석 순서를 legacy 4키 폴백에서 `presmptPrce` 하나로 좁힘**(3A 잔여 일괄,
  verifier r3 N3-6) — `policy-values.md` P-3 는 기초금액 순서의 legacy 불채택만 결정하고
  추정가격 순서 자체는 「기초금액 키 없음 성질 유지」만 요구해 좁힘의 채택 여부를 명언하지
  않는다. `presmptAmt`(legacy 둘째 후보)는 이 저장소 문서에 없는 미등재 키이고, 배정예산
  둘(`asignBdgtAmt`·`bdgtAmt`)을 추정가격 폴백으로도 쓰면 승인 표(§1.1)가 비판하는 legacy
  `KEY_BASIS` 접힘(서로 다른 개념 넷을 한 basis 로 접음)이 되살아난다고 판단해 `presmptPrce`
  하나만 남겼다 — `CollectionPolicy.kt` 주석에도 같은 근거를 남겼다.

## 알려진 제한

- **(3A 잔여 일괄로 해소)** `KONEPS_COLLECTION_POLICY`는 이제 운영자 승인 2026-09-07 값을
  담는다(필드 계약 **11**·resultCode 16·해석 순서 둘·gate, `018` 수정이
  `cnstrtnAbltyEvlAmtList` 를 더해 10→11 이 됐다 — verifier r3 N3-9) —
  `CollectionPolicyTest`가 승인 표와 실값을 대조한다. **age/recheck-gate(24h/48h)는
  여전히 「측정 전 잠정값」**(P-5, `policy-values.md`)이고, 「미확정」 칸(`bssAmt`·
  `bssAmtPurcnstcst`·`presmptAmt`·`usefulAmt`·율 밴드 둘·개찰·예비가격 17건)은
  인스턴스화하지 않았다.
- **「미확정」 과세와 `UNKNOWN` 이 같은 토큰으로 접힌다**(verifier r3 N3-5) — 승인 표는
  `asignBdgtAmt`/`bdgtAmt`의 **`UNKNOWN`**(문서가 과세를 아예 선언하지 않음)과 `bssamt`의
  **미확정**(`OPEN-REG-05`, 상반 방증 있음 — 다른 층위)을 다른 칸으로 적는데, 세 필드
  인스턴스 모두 `VatTreatment.UNKNOWN`이다. 행 단위로 읽으면 어긋나지 않는다(`bssamt`
  행 자체는 단위가 authoritative 라 채택 대상이고, `UNKNOWN`은 주장의 부재라 값을
  지어낸 것도 아니다) — `CollectionPolicy.kt`의 `bssamt` 행 주석이 이미 그 구분을
  적고 있다. `OPEN-REG-05`가 닫히면 이 셋이 다른 값으로 갈릴 수 있다.
- **002·003/004·026 corpus dispatch 는 production 변이를 판별하지 못한다**(verifier r3
  N3-2) — 27/27 dispatch 는 27 case 가 실행되고 `verified_paths` 가 통과한다는 뜻이지,
  27 case **전부**가 production 코드 변경을 잡아낸다는 뜻이 아니다. 002 는 executor 가
  fixture 가 선언한 밴드를 직접 조립해 `RangeBand.violates`만 부르고 `resolveAmount`를
  지나지 않는다. 003·004 는 유일한 판별값(`reason`: `ExplicitNull`/`KeyMissing`)이
  `verified_paths` 밖이라 두 case 가 corpus 안에서 서로 구별되지 않는다. 026 은
  `verified_paths`(`interpretedZone`·`instantDerived`·`assumedUtc`)가 전부 파싱 성공
  여부의 상수 경로로도 나올 수 있는 값이라 진짜 판별력이 약하다. **판별은 procurement
  단위 test 가 진다**(`ResolveAmountTest`·`RawObservationTest`·`CanonicalizeTest`) —
  verifier 의 변이 실측(M4·M5·M6)이 이를 직접 확인했다. curator 결함이 아니다 — 어휘
  승인 후속을 016 의 P-7 요청과 함께 curator 레인에 요청한다.
- **(v2-defect 수정으로 해소)** `koneps-collection` corpus 는 이제 **27/27 case 전건이
  dispatch** 된다 — `KONEPS_COLLECTION_PENDING_CAPABILITY`는 빈 집합이다. 그중 4건
  (013·014·022·027)은 procurement 함수를 호출하지 않고 문서·회계 사실만 대조한다는
  성질은 그대로다(위 판정표, 능력 공백이 아니라 애초에 procurement 런타임 범위 밖).
- `Notice`·`OpeningResult`·`QualificationText`는 값 객체까지다 — repository·이벤트 발행·상태
  저장(영속화)은 두지 않는다(3D·4B 소관). `Notice.applyEvent`는 순수 함수이고 이 slice는
  그 결과를 어디에도 쓰지 않는다.
- `Notice.collected()`는 procurement 밖에서도 호출 가능하다(의도적 — 위 「판단이 갈린
  지점」). `internal`인 것은 `copy(status=…)`뿐이다.
- **(v2-defect 026 수정으로 해소)** `parseSourceZonedInstant`는 이제 정책이 든
  `DateTimePatternId` 목록(현재 `KONEPS_SPACE_DELIMITED_19` 하나, policy-values.md §1.4
  authoritative 형식)으로 KONEPS 실제 wire 형식을 파싱한다. `ASSUME_KST` **매핑 자체**
  (`ZoneId.of("Asia/Seoul")`)는 여전히 상수다 — 규칙 **선택**은 정책 데이터(계약의
  `sourceZone`)지만 규칙의 **의미**(문자열→ZoneId)는 그대로 코드 상수다(`DateTimePatternId`
  와 같은 원칙). `OPEN-3A-SOURCE-TZ`는 활성 남는다.
- `FieldUnit`은 `WON`·`PERCENT`·`NONE` 세 값이던 것이 v2-defect 018 수정으로 **네 값**
  (`DELIMITED_LIST`도 `NONE`)이 됐다 — `scale`이 여전히 `unit`을 전결한다(verifier r2 §1
  "슬롯 축약" 지적, 차단 아님). `cnstrtnAbltyEvlAmtList`는 이제 §5.5 `UnnormalizedFigure`
  로 **수집 형태(레코드→성분 분해)까지는** 열렸지만, 단위·과세 정규화(Money 변환)는
  여전히 하지 않는다(`OPEN-QUAL-10` 소유 — 그 OPEN 이 닫히기 전까지 의도된 제한).
- **(v2-defect 002 수정으로 부분 해소)** `ExpectedRangeKey`가 참조하는 밴드를 실제로
  강제하는 `RangeBand`/`ContractViolationAxis.RANGE` 메커니즘이 `resolveAmount`의 amount
  축에 배선됐다 — 그러나 운영 정책의 `rangeBands`는 **여전히 빈 표**다.
  `sucsfbidLwltRate`의 B6 밴드 값은 `legacy-behavior`이고 확정은 활성 `OPEN-DEC-10` 소유라,
  그 OPEN 이 닫히기 전까지 실 데이터에서 이 메커니즘은 **아무 필드에도 걸리지 않는다**
  (inert). floor-rate 개념(`floorRateFrom`) 자체에는 아직 배선하지 않았다 — 현재 이 개념의
  어떤 실제 필드도 `expectedRange`를 갖지 않기 때문이다(합성 sanity test 로만 증명).
- **(v2-defect 016 수정으로 해소, 후속 남음)** 업무구분명 문서 열거 어휘(`DocumentedVocabulary`,
  물품·용역·공사·외자)가 이제 procurement 타입으로 존재한다 — 다만 그 값의 출처가
  `policy-values.md`의 P-1~P-6 급 「운영자 승인」 항목이 아니라 §1.5 일반 조사 표라, curator
  레인에 별도 승인 항목 등재를 요청했다(위 「판단이 갈린 지점」).
- **(v2-defect 003·004 수정으로 해소)** `RawNoticeObservation`이 이제 `presenceOf`로 값
  부재의 사유(`ExplicitNull` vs `Missing`)를 구분한다 — 기존 `valueOf`(하위호환)는 여전히
  그 둘을 `null`로 접는다.
- **(v2-defect 023 수정으로 해소, ASCII 한정 — verifier r3 N3-7)** `NoticeNumber.of`의
  정규화가 trim 을 넘어 ASCII 대문자화 + 내부 ASCII 공백→`-` 로 넓어졌다 — legacy
  `normalize_notice_number`의 리터럴(공백 완전 제거)과는 다른 결과다(팀리드 결정, 위
  「판단이 갈린 지점」). **비 ASCII 공백(전각 공백 `U+3000` 등)은 흡수하지 않는다** —
  Java `\s`(기본 문자 클래스)는 ASCII 전용이라 `"SYN　NTC"`(전각 공백)와 `"SYN NTC"`
  (반각 공백)가 다른 `NoticeNumber`로 남는다(`"SYN　NTC".replaceAll("\\s+","-")`를 직접
  JVM 실측해 원문 그대로 나옴을 확인). 한글 문자 자체의 대소문자 접기도 다루지 않는다
  (한글은 대소문자가 없어 대상이 없다). 공고번호 형식이 문서상 라틴·숫자·구분자만이라는
  관측(`policy-values.md` §1.1)을 전제로 운영 위험을 낮게 판단했다 — `NoticeId.kt` KDoc
  에도 이 한계를 명시했다.
- **`AmountResolutionOutcome.Resolved.unit`은 현재 데이터로는 항상 `WON`이다** — `resolveAmount`가
  `WON_INTEGER` scale 계약만 성공 경로로 흘려보내므로(다른 scale은 전부 `SCALE` 위반으로
  거부), unit 값이 다른 경로로 갈리는 실제 시나리오가 이 슬라이스 안에 없다. N-2와 같은
  방식의 "unit 리터럴 회귀 가드"는 그래서 이 축에 대해서는 무의미하다(항상 같은 값이라
  회귀와 정상을 구별할 표본이 만들어지지 않는다) — vatTreatment처럼 여러 실제 값이 흐르는
  축이 아니다.
