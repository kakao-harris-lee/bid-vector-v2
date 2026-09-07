# 리뷰 요청 조건 점검 · 판단이 갈린 지점 · 알려진 제한 — M3 / 3A

F-6(low, verifier r1)에 따라 `commands.md`에 있던 「판단이 갈린 지점」·「알려진 제한」과
병렬 레인 경계 검사를 이 문서로 옮긴다. acceptance 실행 결과·exit code 는 `commands.md`가 갖는다.

## 리뷰 요청 조건

- [x] 구현 diff 커밋, base/head 고정 — base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, 수정
      라운드 1 head `a9383b7593e1f7f723fec094e49566287ad6c7ea`(이 커밋 포함 시 최신 HEAD).
- [x] acceptance_commands 전부 exit 0 — `commands.md` S-0~S-5, S-6 은 해당 없음.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `:procurement:check`(ktlint·detekt·
      testShapeGate·domainApiTypeGate·domainSourceReferenceGate·typeShapeGate·sizeGate·cpdCheck·
      gateExecutionGate·kover 전부 포함) 통과.
- [x] 변경된 fixture와 정책 version 근거 기록 — 이 라운드는 fixture 를 편집하지 않았다(curator
      레인 소유). 정책 슬롯(`unit`) 근거는 `data-dictionary.md` §5.3 서명 인용.
- [x] 알려진 제한과 rollback 방법 기록 — 아래 「알려진 제한」, rollback 은 `rollback.md`(경로
      집합이 이 라운드에서 늘지 않았으므로 갱신 불필요 — 신규 파일 `NoticeFacts.kt`는
      `procurement/` 전체 경로 인자에 이미 포함된다).
- [x] secret 스캔 통과 — `commands.md` 참고, 매치는 전부 벤진(인용문).

## 병렬 레인 경계 검사

수정 라운드 1의 두 커밋(`b72b712`·`a9383b7`)의 파일 목록에 `fixtures/**`·`docs/**`·
`build-logic/**`·curator 소유 `reports/evidence/m3/3a/{policy-values.md,fixtures-koneps-collection.md}`
혼입이 **0건**이다 — `git show --stat` 두 커밋 모두 `procurement/src/**` 파일만 담는다(F-6 반영
전 상태 그대로 유지).

## 판단이 갈린 지점

- **`Ports.kt`의 `suspend` 제거**(r0) — verifier r1 §4(a)가 격리 worktree 재현으로 동의 확인. 유지.
- **decision 모듈 test 파급**(r0) — team-lead 승인, scope.md 정정 커밋 `e2a879a`로 반영됨(verifier
  r1 §4(b) 동의).
- **`canonicalize`를 procurement에 둔 배치**(r0) — verifier r1 §4(c) "배치는 동의, 실행은 미달".
  실행 미달(F-2, 통화·과세 리터럴)은 이번 라운드에서 닫았다. 배치 자체의 문면 정합(scope ⑧
  문장과의 긴장)은 여전히 문서 레인 확인 대상으로 남긴다.
- **F-2·F-3·F-4를 한 커밋으로 묶음**(r1, team-lead 지시와의 편차) — 세 finding이 같은 함수
  (`KonepsFieldContract` 생성자, `evaluateCandidate`, `resolveAmount` 시그니처)를 고쳐 중간
  커밋이 컴파일되지 않는다. F-5도 F-2가 추가한 `unit` 슬롯에 의존(`allocatedBudgetFrom`이
  `contract.unit`을 읽음)해 분리 이득이 작다고 보고 같은 커밋에 포함했다.
- **`FieldUnit`을 `scale`이 전결하는 파생값으로 설계**(r1) — `unit`이 `scale`과 1:1이라 슬롯
  둘을 두는 의미가 옅다는 반론이 있을 수 있다. 승인 명세가 둘을 별도 슬롯으로 요구하고
  (`data-dictionary.md` §5.3 서명), 이후 같은 scale이라도 unit이 갈리는 개념이 생기면 결속표
  하나만 넓히면 되게 설계했다. 값 자체는 생성자 불변식이 검사해 실수로 어긋난 `unit`을
  넣을 수 없다.
- **커밋 메시지 test 수 오기**(r0, 정정 완료) — `feat(m3-3a): procurement 도메인` 커밋이
  "92개 test"라 적었으나 실측은 52였다(당시). `commands.md`가 정본이라는 선언에 변함없다.

## 알려진 제한

- `KONEPS_COLLECTION_POLICY`(main 운영 인스턴스)는 형태 + 최소 내용뿐이다 — `policy-values.md`는
  **도착했으나 미승인**이다(F-6 정정 — r0 evidence의 "아직 미도착"은 그 사이 curator 커밋
  `af0ba6a`·`aecddbb`로 사실과 달라졌다). 필드 계약·resultCode 범주·해석 순서 값은 운영자 승인
  수령 뒤 별도 커밋으로 채운다.
- `koneps-collection` corpus dispatch는 미등록이다(9 case 전부 `insufficient-evidence`, D-M3-8
  curator 승격 선행).
- `Notice`·`OpeningResult`·`QualificationText`는 값 객체까지다 — repository·이벤트 발행·상태
  저장(영속화)은 두지 않는다(3D·4B 소관, 「과잉 금지」는 그대로 유효). `Notice.applyEvent`는
  순수 함수이고 이 slice는 그 결과를 어디에도 쓰지 않는다.
- `parseSourceZonedInstant`(D-3A-4)의 `ASSUME_KST` 매핑은 `ZoneId.of("Asia/Seoul")` 리터럴을
  쓴다 — 규칙 **선택**은 정책 데이터(`dateInterpretation`)지만 규칙의 **의미**(문자열→ZoneId)는
  상수다. `OPEN-3A-SOURCE-TZ`는 활성 남는다.
- `resultCodeCategories`·`baseAmountResolutionOrder` 등 정책 슬롯의 **값**은 curator 승인
  전이라 main 정책은 빈 목록/0 시간(inert placeholder)이다.
- `FieldUnit`은 `WON`·`PERCENT`·`NONE` 세 값뿐이다 — 시공능력평가금액(§5.5 `UnnormalizedFigure`,
  `OPEN-QUAL-10`)처럼 단위 미확정인 필드는 이 slice가 다루지 않는다(수집 형태만, D-3A-8).
