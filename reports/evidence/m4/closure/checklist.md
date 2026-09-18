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

전건 근거: `./gradlew --no-daemon check` **exit 0 · 1,781 tests · 실패 0 · 건너뜀 4**
(base 에서 재실측, M4 당시 통과 기록을 근거로 쓰지 않았다).

**수치 정정과 측정 규율**(verifier r1 MEDIUM-1·MEDIUM-2): 초판은 「모듈 아홉 1,530 tests·실패 0」
으로 적었는데 **둘 다 틀렸다** — 이 worktree 의 실행은 **337 태스크 중 305 가 up-to-date** 라
test 가 다시 돌지 않았고, 1,530 은 결과 디렉터리 **여덟** 개의 합이었다. 정본은 **버릴 clone 의
깨끗한 전건 실행**이고 그 값이 위 줄이다. **건너뜀 4 를 「실패 0」이 가리지 않게 명시한다** —
그 넷은 조건 ⑥ 이 인용한 실서버 통합 test 이고, 게이트에서 건너뛰므로 그 test 는 조건 ⑥ 의
근거가 되지 못한다.

| # | 조건 | 무엇이 재는가 | 판정 | 왜 |
| --- | --- | --- | --- | --- |
| ① | 상태 전이 property·invalid transition 통과 | `OutboxTransitionTableTest`(6) — 허용 쌍을 **집합 차연산으로** 빼 여집합을 순회하고 종단 셋 × 전 command 를 따로 단언 · `EditSessionTransitionTableTest`(9) · `EditSessionIdempotencyPropertyTest`(5) | **충족** | 게이트가 있고 초록이다. **단 「표 밖 쌍 전부」는 두 test 중 하나에만 참이다**(verifier r1 MEDIUM-3) — 기계 여집합은 outbox 쪽이고, 세션 전이표는 여집합 15 중 **손으로 적은 8** 이며 단언이 없는 쌍 셋(`Applied`+`RequestEdit`·`Cancelled`+`Confirm`·`Cancelled`+`RequestEdit`)과 집합 순회 부재가 있다 |
| ② | use case test 가 DB·network 없이 fake port 로 | `EvaluateCandidatesUseCaseTest`(14) · `EvaluateCandidatesUseCaseIsolationTest`(8, port 호출 횟수 불변식) | **충족** | **재는 것은 fake port test 다. 구조는 그만큼 강하지 않다**(verifier r1 HIGH-1 정정) — 게이트가 기계로 막는 것은 `adapters` **project 링크** 하나이고(변이 실측: 그 의존을 심으면 exit 1), **JDK `java.sql`·`java.net.http` 를 use case 본문에 인라인하면 의존 선언 없이 전건이 exit 0 으로 통과**한다. 금지 group 의 실 드라이버를 application 층에 선언해도 막히지 않는다 |
| ③ | duplicate·redelivery·out-of-order 에서 상태 수렴 | `InboxDedupPropertyTest`(2) — 도착 순서를 뒤집고 중복시켜도(`arrivals.reversed() + arrivals`) **최종 처리 집합과 처리 횟수가 같은 값으로 수렴** · `JdbcInboxPortTest` — 저장 층 dedup | **충족** | 순수 층이 **순서 축을 실제로 단언한다**. **단 「같은 수렴을 실 DB 저장 층에서 재확인」은 과장이다**(verifier r1 MEDIUM-4) — 저장 층 test 는 **같은 키를 두 번** 받고 그 사실을 자기 주석에 적으며, 순서 반전 축은 순수 층에만 있다 |
| ④ | ML timeout 시 thread·connection 미고갈 + 업무 결과 fail-safe | `DeadlineCancellationRetryTest`(3) · `EmbeddingDeadlineCancellationRetryTest`(3) · `UnavailableMlAnalysisTest` | **좁혀진** | **fail-safe 축은 측정된다**(deadline·취소·재시도·미가용 시 판정 축소). **고갈 축은 측정되지 않는다** — 부하·자원 소진 test 가 없다. 「고갈되지 않는다」는 동시성 부하에서만 참이 되는 진술이고 이 저장소에 그 층이 없다 |
| ⑤ | dry-run 에서 실제 Telegram·email 호출 0 | `DeliveryMode`(`Live`·`DryRun`·`Blocked`) · `SenderContractTest`(4, 멱등 계약) · `NotificationBoundaryTest`(3) | **좁혀진** | **조건이 공허하게 참이다 — 부를 대상이 없다.** `NotificationSender` port 의 어댑터 구현이 `adapters` 에 **0건**이다. port 의 KDoc 자신이 「실 sender 가 없어 이 계약은 fake 로만 증명되고 실 어댑터 slice 의 verifier 가 계승한다」고 적는다. dry-run 이 **실 경로를 억제하는지**는 실 sender 가 생기는 slice 에서만 잴 수 있다 |
| ⑥ | trace·correlation id 가 수집→판정→ML→알림 요청까지 유지 | 구간별: `EventEnvelopeTest` · `PredictionFactsTest` · `EvaluateCandidatesUseCaseTest` · `OpportunityAnalysisTest` · adapters 의 계약 왕복·실서버 통합 test | **좁혀진** | **구간은 서고 사슬은 못 잰다.** 조합 지점(`app`)에 correlation id 를 관측하는 test 가 **0건**이다(실측). 사슬을 이으려면 판정 포트의 production 어댑터가 필요한데 그것이 아직 없다 — 이 좁혀짐은 M4 의 결함이 아니라 **배선 부재**다 |
| ⑦ | 한 application 함수에 수집·DB·ML·알림 구현이 함께 들어가지 않음 | 두 층: **(1) 구조** — `workflow` 의 허용 **project** 의존이 domain 층뿐 **(2) 텍스트** — `CompositionBoundaryTest`(4, allow-list + 양성 대조) | **좁혀진** | **(1) 은 「project 의존 축만」 충족이다**(verifier r1 HIGH-1 정정 — 처음에 「컴파일이 강제한다」로 적었고 그것이 틀렸다). `adapters` project 링크는 막지만 **JDK DB·network API 인라인과 application 층의 금지 group 선언은 막지 않는다** — `group.forbidden` 과 외부 allow-list 가 domain 층에만 걸리기 때문이다. **(2) 도 약하다**: 그 test 의 KDoc 이 「이 test 가 유일한 방어선」이라고 스스로 적고, `ALLOWED_ROOTS` 가 `java`·`javax` 를 **설계상 허용**하므로 JDK 경로는 텍스트 층으로도 통과하며, 훑는 범위가 조합 패키지 하나다. **legacy 실패 형태 ⑦ 의 인라인 변종이 정확히 이 경로로 들어온다** |

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

**제한 ⑥ 은 열려 있고, 그 술어 계열이 실제로 뚫린 기록이 base 에 있다.**

**base 에서 확인되는 것만 적는다**(verifier r1 HIGH-2 정정 — 초판은 다른 레인의 미병합
워크스페이스 사실을 「가장 값있는 발견」으로 실었고, 그 근거가 판정 base 의 저장소에 **0건**이었다):

- M4 가 선언한 사각은 **대문자 단일 세그먼트 루트**를 못 잡는다는 것이다.
- base 에 있는 같은 계열 기록은 M6/6B-1 의 것으로, **`import` 없이 전체 한정 좌표로 직접
  참조**하면 소스 텍스트 술어를 통과한다는 실측이다. 그것은 **verifier 가 심은 변이**(MUT-E3)이고
  그 slice 는 술어를 **바이트코드 상수 풀**로 옮겨 닫았다.
- **처방은 그래서 base 근거가 있다** — 같은 축을 상수 풀로 옮기면 슬래시·점 표기가 함께 잡힌다.

**확인 불가**: 「반환형으로만 스치는 좌표를 텍스트 술어가 못 본다」와 그 수치(좌표 감소·게이트
통과 수)는 **아직 병합되지 않은 다른 레인**의 실측이라 이 base 에서 참·거짓을 가릴 수 없다.
그 레인이 병합된 뒤 evidence 좌표를 근거로 달아 다시 등재한다 — **그때까지 이 판정은 그 문장을
사실로 쓰지 않는다.**

조건 ⑦ 의 (2) 층이 이 계열의 술어이므로, 후속 slice 후보는 둘이다 — **(가) 텍스트 술어를 상수
풀로 이전**(base 근거 있음) · **(나) `group.forbidden`·외부 allow-list 를 application 층까지
확대**(HIGH-1 이 드러낸 자리, 아래).

### HIGH-1 이 드러낸 후속 후보 — application 층에 금지 group 게이트가 없다

이 판정이 실측으로 배운 것이다. `ModuleDependencyPolicy` 는 **project 의존**은 층 모델로
막지만, `group.forbidden`(드라이버·HTTP 클라이언트·프레임워크)과 domain 외부 allow-list 는
**domain 층에만** 적용된다. 그래서 application 층은 금지 group 을 선언할 수 있고 JDK 의
DB·network API 는 텍스트 allow-list 가 설계상 허용한다. **완료 조건 ⑦ 이 문면으로 금지한 형태의
인라인 변종이 게이트 전건을 통과한다** — 이 slice 는 판정이므로 고치지 않고 후속 slice 로 등재한다.

## 축 2 — OPEN 배타 처분

정본은 `open-inventory.md` 다(스윕 산출물을 evidence 로 옮긴 것 — `_workspace/` 는 gitignore 라
그대로 두면 사라진다, M5 종결과 같은 처리).

**식별자 115건. 갈래 합이 전수와 같다.**

| 갈래 | 건수 |
| --- | --- |
| 종결 | 36 |
| 이월 | 59 |
| 미결 | **12** |
| 상충 | **7** |
| 참조 상태(게이트가 지킴) | 1 |
| 합 | **115** |

**M4 가 직접 신설한 `OPEN-4*` 43건만 보면** 종결 13 · 이월 21 · 미결 6 · 상충 3 이다.
계열 접두사와 줄바꿈으로 잘린 조각은 식별자가 아니어서 제외했고 그 판정 근거가 재고 §0.3 에 있다.

### 상충 7건의 성격 — **갱신 누락 4 · 그 밖 3**

초판은 「대개 갱신 누락이고 판정이 갈리는 것이 아니다」로 단정했다. **3/7 에서 틀렸다**
(verifier r1 MEDIUM-5). 갱신 누락은 `OPEN-DIC-03`·`OPEN-4C1-TX-CONTRACT-UNVERIFIED`·
`OPEN-4D3-SAMPLE-SUPPLY`·`OPEN-NOTI-05` **넷**이고, 나머지 셋은 다른 성격이다.

- **`OPEN-4B6B-POLICY-VALUES`** — 남의 표가 낡은 것이 아니라 **정본 자신**이 제목·승인 절에서
  「종결」, 해소 조건과 승인 절 말미에서 「활성 유지」를 **함께** 적는다.
- **`OPEN-NOTI-06`** — 담당이 갈리고 **받은 4B slice 가 없다**(소유자 공백).
- **`OPEN-2E-TEXT-SYNTHESIS`** — 닫는 주체가 셋으로 갈린다. 한 표의 갱신 누락으로 설명되지 않는다.

이 문서는 §6.2 병기와 `NOTI-06` 주석으로 **사실을 갖고 있으면서 요약을 단정으로 적었다.** 그
단정이 `milestone-4.md` 로 전파됐고 둘 다 정정했다.

아래 둘은 팀장이 직접 재확인한 갱신 누락 사례다.

- **`OPEN-DIC-03`** — 사전의 계열 표 행만 취소선 없이 원문을 유지하고, **같은 사전의 다른 두 절과
  capability map 행은 「종결(M4/4B-1, 운영자 결정)」** 이다. 갈린 것은 판정이 아니라 **한 표의 갱신
  누락**이다.
- **`OPEN-4D3-SAMPLE-SUPPLY`** — 마일스톤 문서는 착수 결정과 「닫힘(2계층)」을 적고, capability map
  행만 취소선 없이 열려 있다고 적는다. 같은 형태다.

나머지 다섯의 양쪽 문면은 재고 §6.2 에 병기돼 있다.

### 미결 12건 (전수)

`OPEN-4B1-02` · `OPEN-4B1-05` · `OPEN-4B1-06` · `OPEN-4B1-07` · `OPEN-4B1-LADDER-THRESHOLDS` ·
`OPEN-4B2-1` · `OPEN-LEAK-GATE-REPORT-DURABILITY` · `OPEN-DIC-11` · `OPEN-STR-04` ·
`OPEN-STR-12` · `OPEN-NOTI-01` · `OPEN-NOTI-08`

`OPEN-STR-12`(발송 채널)는 완료 조건 ⑤ 의 좁혀짐과 **같은 뿌리**다 — 따로 세지 않는다.

### 이월 59건 중 주의 — 받는 쪽이 이미 닫혔거나 그 축을 다루지 않았다

재고 §5 가 그런 항목 20여 건을 모았다. 두 개가 특히 분명하다.

- **`OPEN-4D2-VECTOR-FORGERY-AT-WIRING`** — 「4B-6 이 닫는다」고 적혀 있으나 4B-6a·4B-6b evidence
  전체에 그 식별자가 **0건**이다.
- **`OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`** — 「M4 4B 가 개찰 fact 를 쓰기 **전에** 판단해야
  한다」로 적혀 있는데 **4B-7 이 개찰 fact 를 이미 소비했다**.

**이월은 받는 쪽이 실제로 받았을 때만 이월이다.** 위 둘은 문면상 이월이지만 실질은 미결에 가깝다 —
운영자 판단이 필요한 자리로 아래에 올린다.

## 운영자 판단이 필요한 것

종결 승인에 앞서 결정이 필요한 묶음 넷이다. 이 slice 는 **판정만 하고 고치지 않는다**.

| # | 묶음 | 필요한 결정 |
| --- | --- | --- |
| 1 | **상충 7건** | 대개 정본 표의 갱신 누락이다. **문면 정정을 이 slice 가 할지, 각 계열 소유 slice 로 보낼지** |
| 2 | **`OPEN-NOTI-06` 소유자 공백** | 받는 4B slice 가 없다. 누가 받는가 |
| 3 | **이월 문면이 빈 둘**(`OPEN-4D2-VECTOR-FORGERY-AT-WIRING`·`OPEN-3E-OPENING-AMOUNT-AUTHORITY-GUARD`) | 실질 미결로 재분류할지, 후속 slice 로 등재할지 |
| 4 | **미결 12건** | M4 종결과 함께 **이월처를 못 박을지**, 아니면 미결로 두고 종결할지(M5 는 이월 14건을 남기고 종결했다 — 전례 있음) |

## acceptance 실측

| 명령 | exit | 핵심 결과 |
| --- | --- | --- |
| `./gradlew --no-daemon check` (C-1, 버릴 clone 깨끗한 실행 — **정본**) | **0** | **1,781 tests · 실패 0 · 건너뜀 4** |
| 〃 (이 worktree) | 0 | **근거로 쓰지 않는다** — 337 태스크 중 305 up-to-date 로 test 가 재실행되지 않았다 |
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
