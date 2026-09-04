# checklist — M1 / 1B (Phase 1 골격)

**이 파일은 Phase 1 산출물이다.** 「증명 수단」 열은 전부 **예정**이고, 구현이 진행되면서
Phase 4(검증)에서 실측 결과로 채운다 — 지금 채우면 실행 없는 주장이 된다.

## M1 완료 조건 대조 (`milestone-1.md` 「완료 조건」, 1B 가 지는 몫)

`milestone-1.md` 의 완료 조건은 M1 전체의 것이다. 아래는 조사 C §2 「완료 조건 대조」가
1B 로 지목한 몫이다 — 1A 판정은 `reports/evidence/m1/1a/checklist.md` 가 정본이므로
여기서 되풀이하지 않는다.

| 조건 (`milestone-1.md` 축어) | 1B 가 지는 몫 | 증명 수단 (예정) |
| --- | --- | --- |
| `./gradlew check` 통과 | 1B 코드가 들어와도 유지 | 예정 — `scope.md` `B-1` |
| 금지 import와 순환 의존을 일부러 넣은 test fixture가 실제로 실패 | `shared-kernel` 이 Spring·JPA·JSON·HTTP 를 import 하지 않음(`ADR 0002` D-9)이 기존 게이트에 걸림 | 예정 — `scope.md` `B-2` + 1A 의 의도적 위반 fixture 승계 |
| 승인된 authoritative corpus 전체 통과 | **1B 가 첫 소비자.** `golden-manifest.json` 신설 | 예정 — `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS` 미결 상태에 따라 「N/A + 사유」로 판정될 수 있음(`scope.md` OPEN 표) |
| 중요 rule mutation이 생존하지 않음 | `M-1`(금액 basis 자리 바꿔치기) · `M-2`(rate scale 변환 지점 이동·중복) · `M-3`(측정 불가/부재를 숫자로 접기)가 1B 대상 | 예정 — 도구는 `OPEN-ADR-07` 로 미결(1A 관례 승계, 카탈로그 좌표만) |
| raw `Double` 금액/rate가 public domain API에 없음 | **값 타입 투입 시 `domainApiTypeGate` 가 committed fixture 가 아니라 실제 도메인 API 위에서 돈다** | 예정 — `scope.md` `B-6` |
| `Uncertain`/`Unmeasurable`가 성공 또는 0으로 합쳐지지 않음 | **carrier 는 1B 가 만든다** — `Absent(reason)`·`Unmeasurable(reason)`·`Measured<T>`. `Uncertain` variant 자체는 1C·1D | 예정 — property test(조사 C §5 P-4) + 타입 차단 |
| 신규 파일/함수 예산 위반 없음 | 1A 가 건 두 한도(함수 50·파일 500)를 유지 | 예정 — `qualityBaseline`(`scope.md` `B-5`) |

### 1A 가 1B 로 넘긴 비-완료조건 항목 둘

| 항목 | 1B 의 몫 |
| --- | --- |
| coverage 임계 | 1B 가 첫 도메인 코드와 함께 정한다(1A checklist 「알려진 제한」 1) — 값 자체는 `OPEN-1B-COVERAGE` |
| `duplicate mechanical helper` 측정 정의 | 도구 조사는 1B, 결정은 운영자(`ADR 0007` D-5·`OPEN-ADR-16`) |

## evidence 최소 목록 (`agent-workflow.md` §6)

| 파일 | 상태 |
| --- | --- |
| `scope.md`·`rollback.md`·`commands.md`·`checklist.md` | 있다(Phase 1) |
| `differential.json` | 예정 — 1B 는 Python 대비 판정 대상이 있을 수 있다(축 1~7의 회귀 계열). Phase 4 에서 작성 여부 판단 |
| `golden-manifest.json` | 예정 — `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS` 미해소 시 「N/A + 사유」 후보 |
| `codex-review-*.json` | 심판 레인 소유. 이 레인이 만들지 않는다 |

## 알려진 제한 — 1A 에서 승계

전문은 `reports/evidence/m1/1a/checklist.md` 가 정본이다. 여기는 1B 착수에 직접 걸리는
번호만 가리킨다 — 되풀이하지 않는다.

- **1A 「알려진 제한」 1**(coverage 임계 부재) — 1B 가 첫 도메인 코드와 함께 정한다. 값은
  `OPEN-1B-COVERAGE`.
- **1A 「알려진 제한」 15**(allow-list 과잉 도달 — `java.net` 미개방, 다만 **`java.math.BigDecimal`
  과 `Charsets` 는 이미 열려 있음을 실측**) — 1B 의 금액·`Rate` 백킹 타입이 안전하다는 확인.
- **1A 「알려진 제한」 25**(`String#toLowerCase` forbidden — 대소문자 정규화 경로 없음) —
  `OPEN-1B-CASEFOLD` 가 1B 안에서 이 자리를 여는지 판단한다.
- **1A 「1B 인계 목록」 표 전체**(`reports/evidence/m1/1a/checklist.md` 말미) — 비결정성 정책
  (시계 port), coverage 임계, `java.net.URI` 등 허용 확장, mutation 도구, 모듈 내부 패키지
  순환, `class.allowed` 실사용 확장 여섯 항목. 1B 가 실제로 필요로 하는 것만 이 slice 안에서
  닫고 나머지는 그대로 후속 slice 로 다시 넘긴다.

## 리뷰 요청 조건 (`evidence-pack` SKILL) — Phase 1 시점 상태

| 항목 | 상태 |
| --- | --- |
| 구현 diff 가 커밋되어 base/head 고정 | **N/A(Phase 1)** — 코드 diff 없음. evidence 문서 커밋만 있다 |
| `acceptance_commands` 전부 exit 0 | **예정** — Phase 4 |
| test/lint/type/architecture 통과 | **예정** — Phase 4 |
| 변경된 fixture 와 정책 version 의 근거 | **예정** — `RoundingPolicy` 정책 version 이 생기면 근거를 여기 기록 |
| 알려진 제한과 rollback | `rollback.md` 있음. 알려진 제한은 이 문서 위 절 + Phase 4 에서 추가 |
| 비밀값 스캔 | **예정** — Phase 4 커밋 시점에 `commands.md` 에 기록 |
