# 리뷰 요청 조건 점검 · 판단이 갈린 지점 · 알려진 제한 — M3 / 3A

F-6(low, verifier r1)에 따라 `commands.md`에 있던 「판단이 갈린 지점」·「알려진 제한」과
병렬 레인 경계 검사를 이 문서로 옮겼다. 이 갱신은 3A 잔여 일괄(head `649866f`, verifier r3
전) 반영판이다. acceptance 실행 결과·exit code는 `commands.md`가 갖는다.

## 3A 잔여 일괄 — 판정 필요 case 7건

`koneps-collection` 27 case 중 7건은 procurement 능력 공백이라 dispatch 하지 않는다(값을
맞추기 위해 production 코드를 고치지 않았다). 근거·재현 방법은
`KonepsCollectionExecutors.kt`/`KonepsCollectionAccountingExecutors.kt` 머리 문서와 각 case 의
manifest `contract_binding.does_not_carry` 에도 있다 — 여기는 판정 요약표다.

| case | 판정 | 능력 공백 | 후속 제안 |
| --- | --- | --- | --- |
| 002 | v2-defect | `ExpectedRangeKey`(FieldContract.kt)가 데이터 슬롯만 있고 어디서도 강제되지 않는다 — `synFloorRt` 같은 선언된 범위(0~1) 위반 raw 값을 거부하는 코드가 없다 | 밴드 정책 데이터(§5.3 규율 2 「단일 출처」)와 강제 지점을 별도 slice 로 신설하거나, 운영자가 이 축을 3A 범위 밖으로 재확인 |
| 003 | v2-defect | `RawNoticeObservation`이 값 부재만 나르고 부재의 **사유**(`ExplicitNull` vs `KeyMissing`)를 나르지 않는다 | `RawKey`→값 맵의 값 타입을 `String?`(키 존재+null) vs 키 자체 부재로 구분하는 표현으로 확장할지 결정 필요 — 값 크기의 변경이라 별도 설계 검토 대상 |
| 004 | v2-defect | 003 과 같은 능력 공백(짝 case) | 003 과 같은 후속 |
| 016 | v2-defect | 업무구분명(`bsnsDivNm`) 문서 열거값(물품/용역/공사/외자) 자체를 나르는 procurement 타입이 없다 | 문서 열거 어휘를 정책 데이터로 인스턴스화하고 `BusinessCategory`/`CategoryLabel` 판정에 연결할지 결정 필요 |
| 018 | v2-defect | D-3A-8(§5.5)이 지정한 `UnnormalizedFigure` + 필드 계약(수집 형태만)이 아직 미신설 — `cnstrtnAbltyEvlAmtList`(시공능력평가금액목록) 파싱 함수가 없다 | D-3A-8 착수(별도 커밋/slice) — 이 배치 범위 밖으로 이미 scope.md 가 표시한 축 |
| 023 | v2-defect | `NoticeNumber.of`는 `trim()`만 하고 대소문자·구분자 정규화를 하지 않는다 — `" syn-ntc-2301 "`·`"SYN-NTC-2301"`·`"SYN NTC 2301"` 세 표기가 값으로 같아지지 않는다(NoticeId.kt KDoc이 이 한계를 명시한다) | 정규화 규칙 확장(대소문자 접기·구분자 통일)의 승인 여부를 운영자에게 확인 — 확장하면 `source_url` 동일성 불변식(COL-05)의 재검증이 필요 |
| 026(신규 발견) | v2-defect | `parseSourceZonedInstant`가 `LocalDateTime.parse`(ISO `T` 구분자)만 받는데 KONEPS 실제 wire 형식(공식 문서, "YYYY-MM-DD HH:MM:SS")은 **공백** 구분자다 — 직접 JVM 실측(`LocalDateTime.parse("2026-05-20 10:00:00")` → `DateTimeParseException`)으로 확인. 실제 응답에서 이 함수는 **항상 null**을 낸다 | **우선순위 높음** — `deadlineAt`/`openingScheduledAt`이 실제 KONEPS 데이터에서 전부 null 로 떨어진다는 뜻이라 3B 착수 전에 고쳐야 한다. 고정 포맷 파서(`DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`) 채택 여부는 별도 커밋(3A 잔여 일괄 범위 밖)으로 운영자 확인 요청 |

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
- **26을 6건 사전 지정 목록에 추가**(vs 값을 어떻게든 맞춰 dispatch) — `parseSourceZonedInstant`
  를 이 배치에서 고쳐 26을 통과시킬 수도 있었으나(포맷 문자열 하나만 바꾸면 되는 작은
  수정), team-lead 의 명시 지시("기대값이 어긋나면 값을 맞추지 말고... 멈춰 보고")를
  문면 그대로 따라 고치지 않았다 — 이 함수를 고치는 것은 「27 case 전건에 대한 판정」이
  아니라 「production 코드 수정」이고, 4개 커밋 단위 어디에도 속하지 않는 범위 확장이다.

## 리뷰 요청 조건(3A 잔여 일괄, head `649866f`)

- [x] 구현 diff 커밋, base/head 고정 — base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, 3A
      잔여 일괄 head `649866fc55b365c9e786dd5610f8f13b082b8df2`(이 evidence 커밋 자신은 항상
      한 칸 뒤진다).
- [x] acceptance_commands 전부 exit 0 — `commands.md` S-0~S-6 전건(이번 배치는 manifest 를
      편집해 S-6 이 조건부로 활성화됨, 편집 전후 둘 다 exit 0·강등 대상 0).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check`(S-1, S-0 격리
      worktree 포함) 통과. 재실행에서 발견한 sizeGate·detekt·ktlint 위반 셋은 `649866f`로
      닫고 재확인.
- [x] 변경된 fixture와 정책 version 근거 기록 — `fixtures/manifest.yaml`은 koneps-collection
      27 case 의 `contract_binding` 필드만 편집했다(scope.md 명시 예외). 정책 값(①)의
      근거는 `policy-values.md` 「운영자 승인 2026-09-07」 절이고, `CollectionPolicyTest`
      가 그 표와 실값을 대조한다(`commands.md` 「정책 값 비교 결과」).
- [x] 알려진 제한과 rollback 방법 기록 — 아래 「알려진 제한」(신규 항목 추가). `rollback.md`의
      명령이 app 쪽 경로를 파일 개별 나열로 두고 있어 이번 배치의 신규 파일
      (`KonepsCollectionExecutors.kt`·`KonepsCollectionAccountingExecutors.kt`)과 편집
      (`app/build.gradle.kts`)을 못 담는 것을 발견해 그 목록을 디렉터리 전체
      (`app/src/test/kotlin/bidvector/app/conformance/`) + `app/build.gradle.kts`로
      고쳤다 — 임시 clone 실측으로 exit 0·`git diff <base>` 0줄을 재확인했다(rollback.md
      「임시 clone 실측」). `fixtures/manifest.yaml`은 rollback 대상이 아니다(curator 소유
      경로, scope.md `out_of_scope`) — `contract_binding` 필드 되돌림이 필요하면 curator
      레인이 별도로 판단한다.
- [x] secret 스캔 통과 — `git diff 74cf7b6..HEAD -- procurement/ app/src/test/kotlin/bidvector/app/conformance/ fixtures/manifest.yaml | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
      매치 15건은 전부 `token`(도메인 어휘 — `equalToUnpaddedToken`·`provenanceToken`·
      `detailFetchDecisionToken`·`tokenComparison` 등, `fixtures-koneps-collection.md`
      F-8 이 이미 등재한 상시 false-positive 바닥). `token` 제외 패턴은 매치 0(exit 1).

## 병렬 레인 경계 검사

수정 라운드 2의 세 커밋(`b86af3c`·`4e992d2`·`74cf7b6`)의 파일 목록에 `fixtures/**`·`docs/**`·
`build-logic/**`·curator 소유 `reports/evidence/m3/3a/{policy-values.md,fixtures-koneps-collection.md}`
혼입이 **0건**이다(`commands.md`의 `git show --stat` 실측). 이 라운드 작업 중 그 curator 파일
둘이 공유 working tree에 **미커밋 상태로 수정돼 있는 것을 관측**했다(다른 세션의 진행 중
작업) — 내 clean-tree 점검은 그 경로를 감시 대상에 넣지 않으므로 스테이징하지 않았고 영향도
없다.

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

## 알려진 제한

- **(3A 잔여 일괄로 해소)** `KONEPS_COLLECTION_POLICY`는 이제 운영자 승인 2026-09-07 값을
  담는다(필드 계약 10·resultCode 16·해석 순서 둘·gate) — `CollectionPolicyTest`(8 test)가
  승인 표와 실값을 대조한다. **age/recheck-gate(24h/48h)는 여전히 「측정 전 잠정값」**
  (P-5, `policy-values.md`)이고, 「미확정」 칸(`bssAmt`·`bssAmtPurcnstcst`·`presmptAmt`·
  `usefulAmt`·율 밴드 둘·개찰·예비가격 17건)은 인스턴스화하지 않았다.
- **(3A 잔여 일괄로 부분 해소)** `koneps-collection` corpus 는 27 case 중 20건이
  dispatch 됐다(그중 4건, 013·014·022·027은 procurement 함수를 호출하지 않고 문서·회계
  사실만 대조 — 위 판정표 참고). 나머지 7건(002·003·004·016·018·023·026)은 procurement
  능력 공백으로 dispatch 하지 않는다 — 판정·근거·후속 제안은 위 「판정 필요 case 7건」 표.
- `Notice`·`OpeningResult`·`QualificationText`는 값 객체까지다 — repository·이벤트 발행·상태
  저장(영속화)은 두지 않는다(3D·4B 소관). `Notice.applyEvent`는 순수 함수이고 이 slice는
  그 결과를 어디에도 쓰지 않는다.
- `Notice.collected()`는 procurement 밖에서도 호출 가능하다(의도적 — 위 「판단이 갈린
  지점」). `internal`인 것은 `copy(status=…)`뿐이다.
- **`parseSourceZonedInstant`가 KONEPS 실제 wire 형식(공백 구분자)을 파싱하지 못한다**
  (3A 잔여 일괄 신규 발견, koneps-collection-026 판정 — v2-defect, 위 판정표 「우선순위
  높음」). `ASSUME_KST` 매핑 자체는 `ZoneId.of("Asia/Seoul")` 리터럴을 쓴다는 기존 제한도
  그대로다 — 규칙 **선택**은 정책 데이터(계약의 `sourceZone`)지만 규칙의 **의미**(문자열→
  ZoneId)는 상수다. `OPEN-3A-SOURCE-TZ`는 활성 남는다.
- `FieldUnit`은 `WON`·`PERCENT`·`NONE` 세 값뿐이고 `scale`이 전결한다(verifier r2 §1 "슬롯
  축약" 지적, 차단 아님) — §5.5 `UnnormalizedFigure`(단위 미확정, `OPEN-QUAL-10`)처럼 단위
  자체가 미확정인 필드는 이 slice가 다루지 않는다(수집 형태만, D-3A-8 — koneps-collection-018
  이 그 공백을 구체적으로 드러낸다, 위 판정표).
- `ExpectedRangeKey`(FieldContract.kt)는 여전히 데이터 슬롯뿐이고 어디서도 강제되지 않는다
  (§5.3 규율 2 「단일 출처」 미착수, koneps-collection-002 가 드러낸 공백, 위 판정표).
- `RawNoticeObservation`은 값 부재의 **사유**(명시 null vs 키 자체 부재)를 나르지 않는다
  (koneps-collection-003·004, 위 판정표).
- `NoticeNumber.of`의 정규화는 trim 뿐이다 — 대소문자·구분자 통일은 하지 않는다
  (koneps-collection-023, 위 판정표. `NoticeId.kt` KDoc이 이 한계를 이미 명시하고 있었다).
- **`AmountResolutionOutcome.Resolved.unit`은 현재 데이터로는 항상 `WON`이다** — `resolveAmount`가
  `WON_INTEGER` scale 계약만 성공 경로로 흘려보내므로(다른 scale은 전부 `SCALE` 위반으로
  거부), unit 값이 다른 경로로 갈리는 실제 시나리오가 이 슬라이스 안에 없다. N-2와 같은
  방식의 "unit 리터럴 회귀 가드"는 그래서 이 축에 대해서는 무의미하다(항상 같은 값이라
  회귀와 정상을 구별할 표본이 만들어지지 않는다) — vatTreatment처럼 여러 실제 값이 흐르는
  축이 아니다.
