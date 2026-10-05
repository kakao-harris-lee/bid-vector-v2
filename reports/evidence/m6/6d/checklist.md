# M6/6D-1 — 리뷰 요청 점검표 (구현 레인)

base `fd4629fe` · 산출물 커밋 `428551f3` · 정본 계약 `scope.md`(D-6D-1~3).

## 리뷰 요청 조건

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋되어 base/head 가 고정됨 | 예 — 산출물 커밋 하나(`428551f3`), in_scope 10 경로 |
| test/lint/type/architecture/contract 명령 통과 | 예 — `check` · `qualityBaseline` 둘 다 SUCCESS(`commands.md`) |
| 변경된 fixture 와 정책 version 의 근거 | fixture 변경 0 — 기존 `contracts/testdata` 골든과 `adapters/ml` fixture 를 읽기만 한다. 정책은 전부 **출하 정본**을 resolve 한다(`OPPORTUNITY_POLICY`·`STRATEGY_POLICY`·`LICENSE_QUALIFICATION_POLICY`·`NOTIFICATION_DELIVERY_POLICY`·`COLLECTION_RANGE_POLICY`) |
| 알려진 제한과 rollback | 아래 절과 `rollback.md` |

## 설계 검토 (2) 우회 ↔ 닫는 술어 대응

| 우회 | 닫는 술어 | 실측 |
|---|---|---|
| 1. fake ML 이 늘 성공해 timeout 이 예산을 안 넘음 | 지연을 `OpportunityPolicyData` 예산과 gateway `deadlineCeiling` 의 `minOf` 에서 **도출**, 지연 0 대조 run 이 같은 배선에서 성공 | 변이 「지연 0」 RED |
| 2. 중복 test 가 inbox 를 안 지남 | outbox 2행 · inbox 1행 · 발송 1건을 함께 단언(발송 횟수만으로는 「애초에 1행」과 구별 안 됨) | 변이 「둘째 평가 제거」 RED |
| 3. 골든 대신 test 안 리터럴 | 두 갈래 모두 `contracts/testdata/prediction` 에서 바이트를 읽는다(부재 시 로딩이 던짐) | 변이 「골든 교체」 RED ×2 |
| 4. 재현 등식이 같은 참조 비교 | 두 run 은 **다른 조립·다른 트랜잭션**이고, 비교 대상은 DB 에서 되읽은 직렬화 문자열. 음성 대조 포함 | 변이 「둘째 release 교체」 RED |
| 5. E2E 가 기본 `check` 밖 | 조건 애노테이션·filter 0, `gate.tests.adapters` 등재(제외 0) — 등재 등식이 양방향 | `check` 안에서 12 test 실행 |
| 6. use case 를 fake 로 대체 | 조립이 **실제로 쥔 객체**의 `CodeSource` 를 본다(main 출력 ↔ test 출력 양방향) | 변이 「협력자 하나를 fake 로」 RED |
| 7. 순차 실행이라 충돌 0 | 래치로 동시성 강제 + 경합 대상 행과 첫 워커의 점유를 각각 변이로 확인 | 변이 둘 RED(래치 제거만은 GREEN — `commands.md` 에 사실로 등재) |

## (2b) 값 획득 축 — 새 production 표면 0

`git diff --name-only fd4629fe..HEAD -- '*/src/main/*'` 빈 출력. production `internal` 완화도
`@VisibleForTesting` 도 없다. 조립이 쓰는 모든 production 생성자는 **이미 public** 이었다.

## 계약 문면과 다르게 택한 자리

**E2E 의 자리를 `app` 의 test 소스셋에서 `adapters` 의 test 소스셋으로 옮겼다.** 계약 in_scope 는
`app` 쪽 신설 패키지를 적었으나, 그 소스셋에서는 지정된 파이프라인을 조립할 수 없다 —

1. **in-process ML 대역을 지을 수 없다.** `app` 의 test classpath 에 `ml-contract` 생성 stub 도
   `grpc-inprocess` 도 없고, `adapters` 가 `ml-contract` 를 `implementation`(비전이)으로만 물어
   전이되지 않는다. ML timeout · 정의 밖 필드 · schema 거부 · EXACT rollback 네 축이 통째로 불가능해진다.
2. **종단 전이 SQL 상수가 보이지 않는다.** 전이 UPDATE 상수는 `adapters` 의 `internal` 이다.

`adapters` 의 test 소스셋은 계약 in_scope 에 이미 포함돼 있고, Testcontainers 하네스 · KONEPS mock ·
ML fixture · 계약 골든 로더가 전부 거기 있어 **재사용**한다. 팀장에게 사유와 함께 보고했고 계약 갱신은
팀장 몫이다. production 불변 규칙(D-6D-3)과 fake 경계 규칙은 그대로 지켰다.

## 알려진 제한

| 제한 | 받는 자리 |
|---|---|
| **종단 전이가 port 메서드를 지나지 않는다.** relay 는 `OutboxPort.markDelivered` 가 아니라 production 전이 SQL 상수를 사본 없이 직접 실행한다 — 전이 명령 타입의 생성자가 `workflow` 의 `internal` 이고, **그 통로를 열어 해결하지 않는다**는 것이 4C-2 의 명시 결정이다(`OPEN-4C2-MARK-UNEXERCISED`) | 6F-10(배달 오케스트레이션) |
| **relay·reclaim·평가→outbox 커밋 경로가 production 에 없다.** 이 slice 의 relay 는 test 소스셋의 소비자 모양이고, 알림 요청은 자기 트랜잭션에서 커밋된다(도메인 write 와의 원자성 미소유) | 6F-10 · `OPEN-6A3-EVALUATION-COMMIT` · D-6F7-11 |
| **재현 등식에 정책 버전·전략 revision 이 없다.** payload 가 그 둘을 싣지 않아 등식이 덮지 못한다(C-4) | 6F-10 payload 확장 → 6D-2 |
| **rollback 축은 gateway 수준에서만 잰다.** 파이프라인이 쓰는 release 선택자는 `OPPORTUNITY_POLICY` 에 `LatestPromoted` 로 고정돼 있고, 그 정책 교체는 production 변경이다 | 6E runbook · ML 배선 결정 |
| **restart 뒤 수렴·redelivery 는 재지 않는다.** CLAIMED 복귀(lease·reclaim) 기제가 없다 | 6D-2 |
| **전략 행을 직접 INSERT 한다.** 여력 상한을 설정하는 HTTP 경로가 없다 | `OPEN-6A3-MAX-ACTIVE-BIDS-EDIT`(6A-2) |
| **요건은 DB 시딩이다.** LLM 추출 체인이 미배선이라 요건이 공고에서 자동으로 서지 않는다 | 추출 체인 배선 slice |
| **발송 채널이 fake 다.** 실 sender 어댑터가 없다 | `OPEN-STR-12` |
| **판정 임계가 test 값이다.** 전략의 승격·검토 임계는 이 test 가 심은 값이고 운영 값이 아니다 | `OPEN-4B1-LADDER-THRESHOLDS` |

## 하네스 레인 변경

없음. `.claude/` 아래 파일을 만들거나 고치지 않았다.
