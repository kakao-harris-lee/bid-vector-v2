# M4 종결 판정 — checklist

판정 대상 base `8652893`(= `origin/main`, 6B-1 병합 이후). 판정 어휘는 M5 종결과 같다 —
**충족**(재는 게이트가 있고 초록) · **좁혀진**(부분만 재고 나머지는 구조적으로 못 잰다) ·
**미측정**(재는 것이 없다).

## 판정

**완료 조건 7 — 충족 3 · 좁혀진 4 · 미측정 0 · 미충족 0.**

**M4 는 닫을 수 있다.** 좁혀진 넷은 전부 **소유자가 있는** 좁혀짐이고(아래 표의 「누가 받는가」),
그중 둘(⑤⑥)은 M4 가 만든 한계가 아니라 **아직 존재하지 않는 배선**에 걸린 것이다 — M4 는 port
까지가 경계였고 그 경계를 지켰다.

## 축 1 — 완료 조건 7 대조

전건 근거: `./gradlew --no-daemon check` **exit 0**, 모듈 아홉 합계 **1,530 tests · 실패 0**
(base 에서 재실측, M4 당시 통과 기록을 근거로 쓰지 않았다).

| # | 조건 | 무엇이 재는가 | 판정 | 왜 |
| --- | --- | --- | --- | --- |
| ① | 상태 전이 property·invalid transition 통과 | `EditSessionTransitionTableTest`(9) · `EditSessionIdempotencyPropertyTest`(5) · `OutboxTransitionTableTest`(6) — 표 밖 (state, command) 쌍 전부 거부 + 종단 셋의 나가는 전이 0 | **충족** | 표와 그 여집합을 둘 다 관측한다 |
| ② | use case test 가 DB·network 없이 fake port 로 | `EvaluateCandidatesUseCaseTest`(14) · `EvaluateCandidatesUseCaseIsolationTest`(8, port 호출 횟수 불변식) | **충족** | test 보다 **구조**가 강하다 — `workflow` 는 application 층이라 허용 project 의존이 domain 층뿐이고, DB·network 구현은 `adapters` 에 산다. 링크 자체가 불가능하다(컴파일 강제) |
| ③ | duplicate·redelivery·out-of-order 에서 상태 수렴 | `InboxDedupPropertyTest`(2) — 도착 순서를 뒤집고 중복시켜도 **최종 처리 집합과 처리 횟수가 같은 값으로 수렴** · `JdbcInboxPortTest` — **같은 수렴을 실 DB 저장 층에서** 재확인 | **충족** | 순수 층과 영속 층 양쪽에서 잰다. 순서 축이 단언문에 명시돼 있다 |
| ④ | ML timeout 시 thread·connection 미고갈 + 업무 결과 fail-safe | `DeadlineCancellationRetryTest`(3) · `EmbeddingDeadlineCancellationRetryTest`(3) · `UnavailableMlAnalysisTest` | **좁혀진** | **fail-safe 축은 측정된다**(deadline·취소·재시도·미가용 시 판정 축소). **고갈 축은 측정되지 않는다** — 부하·자원 소진 test 가 없다. 「고갈되지 않는다」는 동시성 부하에서만 참이 되는 진술이고 이 저장소에 그 층이 없다 |
| ⑤ | dry-run 에서 실제 Telegram·email 호출 0 | `DeliveryMode`(`Live`·`DryRun`·`Blocked`) · `SenderContractTest`(4, 멱등 계약) · `NotificationBoundaryTest`(3) | **좁혀진** | **조건이 공허하게 참이다 — 부를 대상이 없다.** `NotificationSender` port 의 어댑터 구현이 `adapters` 에 **0건**이다. port 의 KDoc 자신이 「실 sender 가 없어 이 계약은 fake 로만 증명되고 실 어댑터 slice 의 verifier 가 계승한다」고 적는다. dry-run 이 **실 경로를 억제하는지**는 실 sender 가 생기는 slice 에서만 잴 수 있다 |
| ⑥ | trace·correlation id 가 수집→판정→ML→알림 요청까지 유지 | 구간별: `EventEnvelopeTest` · `PredictionFactsTest` · `EvaluateCandidatesUseCaseTest` · `OpportunityAnalysisTest` · adapters 의 계약 왕복·실서버 통합 test | **좁혀진** | **구간은 서고 사슬은 못 잰다.** 조합 지점(`app`)에 correlation id 를 관측하는 test 가 **0건**이다(실측). 사슬을 이으려면 판정 포트의 production 어댑터가 필요한데 그것이 아직 없다 — 이 좁혀짐은 M4 의 결함이 아니라 **배선 부재**다 |
| ⑦ | 한 application 함수에 수집·DB·ML·알림 구현이 함께 들어가지 않음 | 두 층: **(1) 구조** — `workflow` 의 허용 project 의존이 domain 층뿐 **(2) 텍스트** — `CompositionBoundaryTest`(4, allow-list + 양성 대조) | **좁혀진** | **(1) 은 충족**이고 컴파일이 강제한다 — 어댑터 구현이 use case 함수 안에 들어갈 수 없다. **(2) 가 약하다**: domain 게이트 가족이 `workflow` 에 걸리지 않아(그 test 의 KDoc 이 「이 test 가 유일한 방어선」이라고 스스로 적는다) **제3자 라이브러리를 조합 패키지에서 직접 쓰는 축**은 소스 텍스트 스캔 하나가 막는다. 그 술어의 사각은 M4 가 이미 선언했고(아래 「제한 ⑥」), **같은 술어 계열이 다른 레인에서 실제 위반을 놓친 것이 실측됐다**(아래 「축 3 뒤」) |

### 좁혀진 넷 — 누가 받는가

| 좁혀짐 | 받는 자리 |
| --- | --- |
| ④ 고갈 축 미측정 | 부하·자원 축은 **6D**(장애 주입)가 소관이다 — milestone-6 「Slice 6D」의 「ML timeout」 항목이 그 자리다 |
| ⑤ 실 sender 부재 | `OPEN-STR-12`(발송 채널) — **6F-7**(알림 요청 → outbox) 뒤 |
| ⑥ 사슬 미측정 | **6F 군**(배선 어댑터) + **6A**(app 조립) 뒤 **6D**(E2E)가 잰다 |
| ⑦ 텍스트 술어 사각 | 후속 slice 후보 — 처방은 알려져 있다(아래) |

## 축 3 — M4 가 종결 시점에 선언한 알려진 제한 일곱의 현재 상태

M4 는 4A 종결 시점에 제한 일곱을 선언했다. **그 뒤 M5·M6 가 무엇을 닫았는지**가 이 축의 물음이다.

| 제한 | 현재 | 근거 |
| --- | --- | --- |
| ① 이벤트 발행·세션 전진의 원자성 부재 | **닫힘** | 4C 트랜잭션 outbox 로 이관됐고 그 slice 가 종결됐다(claim 경합·전이 SQL·dedup 실측 존재) |
| ② 세션 영속 실 구현 부재 + `sessionVersion` 낙관적 동시성 미검증 | **닫힘 — M6/6B-1 이 닫았다** | 세션 표와 JDBC 어댑터가 생기고 낙관적 동시성이 전제조건 UPSERT 로 잠겼다. 그 slice 의 verifier 가 종단 상태 무효화(HIGH-1)·불량 값 기본값 채움(HIGH-2)·정당한 저장의 과잉 거부(HIGH-3)를 잡아 닫았고 Codex 2차가 `approve` 했다 |
| ③ 만료 트리거(sweep) 배선 부재 | **열림** | `expireIfDue`·`expire()` 는 순수 함수로 있으나 `app` 에 스케줄러 배선이 **없다**(실측). 6A 조립 또는 전용 slice 소관 |
| ④ `System` actor 확인 경로 미구현 | **열림** | `STR-15` 후속으로 남아 있다 |
| ⑤ Telegram 어댑터 없음 | **열림** | `OPEN-STR-12` 활성. 완료 조건 ⑤ 의 좁혀짐과 **같은 뿌리**다 |
| ⑥ 텍스트 술어가 대문자 단일 세그먼트 루트를 못 잡음 | **열림 — 그리고 사각이 실증됐다** | 아래 |
| ⑦ 4A port 를 거치지 않는 자체 persistence 는 4A 코드로 막을 수 없음 | **이월(명시)** | 3D·4C 로 넘긴 인계가 문면에 있다. 두 slice 다 종결 |

**제한 ⑥ 이 이 종결 판정의 가장 값있는 발견이다.** M4 는 그 술어가 특정 이름 형태를 놓친다는
것만 선언했는데, **그 술어 계열의 더 깊은 사각이 그 뒤 실제 위반으로 드러났다** — M6 의 한
배선 slice 에서, 소스 텍스트 `import` 스캔형 게이트가 **반환형으로만 스치는 좌표**를 구조적으로
보지 못했고(그 파일은 그 타입을 import 하지 않는다) **바이트코드 상수 풀 게이트**가 그것을 잡았다.
심은 변이가 아니라 **아무도 심지 않은 진짜 위반**이었다.

조건 ⑦ 의 (2) 층이 바로 그 계열의 술어다. **처방은 이미 저장소 안에 실증돼 있다** — 같은 축을
바이트코드 상수 풀로 옮기면 슬래시·점 표기와 반환형 참조가 함께 잡힌다(그 레인이 실측: 좌표
8 → 0, 게이트 3/3). 이것을 후속 slice 후보로 등재한다.

## 축 2 — OPEN 배타 처분

정본은 `open-inventory.md` 다. M4 문서군에서 식별자를 기계 수집했고(전수 스윕), 갈래는
`종결 / 이월 / 미결 / 상충` **배타 넷**이며 갈래별 개수의 합이 전수와 같아야 한다.

**이 절은 재고가 완성된 뒤 판정 수치를 싣는다** — 수집이 진행 중이면 이 문서는 아직 종결 판정을
주장하지 않는다(축 1·3 만 확정이다).

## acceptance 실측

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `./gradlew --no-daemon check` (C-1) | **0** | 모듈 아홉 1,530 tests · 실패 0 |
| C-2·C-3·C-4 표적(조건 ②①③④) | — | **재측정했다.** 첫 실행에서 파이프 뒤 `$?` 를 읽어 exit 를 잘못 포착했고(그 값은 `tail` 것이다), 필터 `--rerun-tasks` 가 같은 모듈의 결과 디렉터리를 **덮어써** 전건 산출물을 지웠다. 정본은 전건 재실행 뒤의 클래스별 XML 수치이며 위 축 1 표의 괄호 숫자가 그것이다 |
| clean-tree (C-5) | — | 리뷰 요청 시점에 in_scope 개별 인자로 재측정 |

**측정 실패를 사실로 남긴다** — 위 두 줄(파이프 exit 오독 · 필터 실행이 산출물을 덮음)은 이
판정의 절차 결함이고, 고친 방법은 「전건을 한 번 더 돌려 XML 을 일관되게 만든 뒤 클래스 단위로
센다」다.

## 크기 게이트

문서 slice 이므로 「evidence ≤ 산출물」 비교 대상이 없다(M0 문서 slice N/A 규칙과 같은 자리).

## 종결 조건 대응

코드 slice 가 아니므로 `code-reviewer` 는 대상이 아니다(코드 diff 0). **verifier 판정 + 사용자
승인**이 종결 조건이다. 게이트 셋(개인정보·계약·마이그레이션)은 해당 없음 — 인증·계약 파일·
마이그레이션 무편집.
