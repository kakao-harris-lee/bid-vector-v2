# 리뷰 요청 조건 점검 · 판단이 갈린 지점 · 알려진 제한 — M3 / 3A

F-6(low, verifier r1)에 따라 `commands.md`에 있던 「판단이 갈린 지점」·「알려진 제한」과
병렬 레인 경계 검사를 이 문서로 옮겼다. 이 갱신은 수정 라운드 2(N-1~N-7, verifier r2) 반영판이다.
acceptance 실행 결과·exit code는 `commands.md`가 갖는다.

## 리뷰 요청 조건

- [x] 구현 diff 커밋, base/head 고정 — base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, 수정
      라운드 2 head `74cf7b6f33950ca7c09306f73aad1f3e9081c8b1`(이 evidence 커밋 자신은 항상
      한 칸 뒤진다, N-7).
- [x] acceptance_commands 전부 exit 0 — `commands.md` S-0~S-5, S-6은 해당 없음.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `:procurement:check`(ktlint·detekt·
      testShapeGate·domainApiTypeGate·domainSourceReferenceGate·typeShapeGate·sizeGate·cpdCheck·
      gateExecutionGate·kover 전부 포함) 통과, `gateExecutionGate --rerun`으로 `NoticeTest`
      등재(N-4) 뒤 표적 재확인.
- [x] 변경된 fixture와 정책 version 근거 기록 — 이 라운드도 fixture를 편집하지 않았다(curator
      레인 소유). 정책 슬롯(`unit`) 근거는 `data-dictionary.md` §5.3 서명 인용, N-3의
      `sourceZone` 배선 근거는 D-3A-4.
- [x] 알려진 제한과 rollback 방법 기록 — 아래 「알려진 제한」, rollback은 `rollback.md`(N-6
      정정 + 이번 head로 재측정 완료).
- [x] secret 스캔 통과 — `commands.md` 참고, 이번 라운드 코드 diff에 벤진 매치조차 없음.

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

- `KONEPS_COLLECTION_POLICY`(main 운영 인스턴스)는 형태 + 최소 내용뿐이다 — `policy-values.md`는
  도착했으나 미승인이다. 필드 계약·resultCode 범주·해석 순서 값은 운영자 승인 수령 뒤 별도
  커밋으로 채운다.
- `koneps-collection` corpus dispatch는 미등록이다(9 case 전부 `insufficient-evidence`, D-M3-8
  curator 승격 선행).
- `Notice`·`OpeningResult`·`QualificationText`는 값 객체까지다 — repository·이벤트 발행·상태
  저장(영속화)은 두지 않는다(3D·4B 소관). `Notice.applyEvent`는 순수 함수이고 이 slice는
  그 결과를 어디에도 쓰지 않는다.
- `Notice.collected()`는 procurement 밖에서도 호출 가능하다(의도적 — 위 「판단이 갈린
  지점」). `internal`인 것은 `copy(status=…)`뿐이다.
- `parseSourceZonedInstant`(D-3A-4)는 이제 `canonicalize`에 배선됐지만(N-3), `ASSUME_KST`
  매핑 자체는 `ZoneId.of("Asia/Seoul")` 리터럴을 쓴다 — 규칙 **선택**은 정책 데이터
  (계약의 `sourceZone`)지만 규칙의 **의미**(문자열→ZoneId)는 상수다. `OPEN-3A-SOURCE-TZ`는
  활성 남는다.
- `resultCodeCategories`·`baseAmountResolutionOrder` 등 정책 슬롯의 **값**은 curator 승인
  전이라 main 정책은 빈 목록/0 시간(inert placeholder)이다.
- `FieldUnit`은 `WON`·`PERCENT`·`NONE` 세 값뿐이고 `scale`이 전결한다(verifier r2 §1 "슬롯
  축약" 지적, 차단 아님) — §5.5 `UnnormalizedFigure`(단위 미확정, `OPEN-QUAL-10`)처럼 단위
  자체가 미확정인 필드는 이 slice가 다루지 않는다(수집 형태만, D-3A-8).
- **`AmountResolutionOutcome.Resolved.unit`은 현재 데이터로는 항상 `WON`이다** — `resolveAmount`가
  `WON_INTEGER` scale 계약만 성공 경로로 흘려보내므로(다른 scale은 전부 `SCALE` 위반으로
  거부), unit 값이 다른 경로로 갈리는 실제 시나리오가 이 슬라이스 안에 없다. N-2와 같은
  방식의 "unit 리터럴 회귀 가드"는 그래서 이 축에 대해서는 무의미하다(항상 같은 값이라
  회귀와 정상을 구별할 표본이 만들어지지 않는다) — vatTreatment처럼 여러 실제 값이 흐르는
  축이 아니다.
