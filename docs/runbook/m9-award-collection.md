# 낙찰자 상호 수집 runbook (M9 / award-ingest)

낙찰정보서비스의 **낙찰 목록**(`getScsbidListSttus{Cnstwk,Servc}`)을 개찰일 창으로 걷어 원문만 적재하는
일회 러너의 운영 절차다. canonical `opening_result` 의 낙찰자 상호·낙찰금액·낙찰률은 **기존 승격 러너**가
채운다 — 이 갈래는 승격 코드를 건드리지 않는다.

이 문서는 **실행 절차와 그 전제**만 담는다. 결정의 근거는 `reports/evidence/m9/award-ingest/scope.md`.

## 0. 실행 전 확인

- 조달청 활용 신청이 **낙찰정보서비스**에 승인돼 있는가(목록 오퍼레이션 둘: 공사·용역).
- 실행 상태 디렉터리가 **저장소 밖**에 있고 **이미 만들어져 있는가**. 러너는 디렉터리를 만들지 않는다.
  개찰결과 수집 갈래(`bidvector.opening-collection`)와 **다른 디렉터리**를 쓴다 — 상한 회계가 그
  디렉터리의 시도 원장에서 seed 되므로 공유하면 이 갈래의 총 상한이 남의 호출로 소진된다.
- 이 호스트에서 다른 일회 러너(수집·승격·추출·relay 등)가 돌고 있지 않은가. 한 프로세스에 둘을 켜면
  **기동이 실패**한다(러너 빈 수 guard). **프로세스를 둘로 띄우는 판은 이 점검이 유일한 방어다** —
  이 갈래는 개찰결과 갈래와 **다른 실행 상태 디렉터리**를 쓰므로 그쪽 잠금이 막아 주지 않는다. 같은
  디렉터리를 가리킨 두 프로세스만 잠금이 종료 코드 3 으로 물러나게 한다.

## 1. 설정

| 키 | 값 | 비고 |
| --- | --- | --- |
| `bidvector.award-collection.mode` | `once` | 이 한 줄이 갈래 전체의 on/off 다 |
| `bidvector.award-collection.from` / `.to` | 개찰일 창(양 끝 포함) | **개찰일**이다(공고일이 아니다) |
| `bidvector.award-collection.categories` | `construction,service` | 물품·외자는 범위 밖 |
| `bidvector.award-collection.calls-per-day` | **제안 200** | 운영자 승인값을 적는다 |
| `bidvector.award-collection.calls-total` | **제안 3,000** | 같은 디렉터리의 누적 호출을 덮는다 |
| `bidvector.award-collection.run-state-dir` | 저장소 밖 경로 | 미리 만들어 둔다 |
| `bidvector.koneps.service-key` | 서비스 키 | 로그·원장에 남지 않는다 |

제안 상한의 근거: 오퍼레이션 둘 × 키당 일 1,000 건 쿼터의 한참 아래. 1년 창을 조각으로 나눠 덮으면
총 1,460 호출 안팎이다(조각마다 쪽 수 여유 포함).

**기본값이 없는 값(기간·상한 둘·실행 상태·서비스 키)은 미설정이면 기동이 실패한다.** 코드가 안전한
기본값을 지어내지 않는다.

## 2. 창을 조각으로 나눈다

한 실행의 창은 **60일 이하**다(그보다 넓으면 기동 거부). 조각 상한이 있는 이유는 이 갈래의 호출 수가
창 길이에 비례하기 때문이다: 일 상한에 걸려 멈춘 스윕은 다음 실행에서 **조각의 처음부터 다시** 걷으므로
(목록 축에는 슬롯 단위 이어 돌기가 없다) 하루 상한을 넘기는 조각은 같은 호출을 두 번 쓴다.

상한은 `to - from`(일 수) 기준이라 **양 끝을 포함하면 최대 61일**이고, 오퍼레이션 둘이면 바닥 호출이
**122**(조각마다 업무당 최소 한 쪽) + 쪽 수만큼 더다 — 제안 일 상한 200 안이다.

1년을 덮으려면 조각 6~7개를 하루 하나씩 돌린다. 조각마다 `from`/`to` 를 옮기고, 종료 코드가 0 인지
확인한 뒤 다음 조각으로 간다(0 이 아니면 위 표의 처방대로 **같은 조각**을 먼저 끝낸다).

## 3. 실행과 종료 코드

```
java -jar app.jar \
  --bidvector.award-collection.mode=once \
  --bidvector.award-collection.from=<개찰일 시작> \
  --bidvector.award-collection.to=<개찰일 끝> \
  --bidvector.award-collection.categories=construction,service \
  --bidvector.award-collection.calls-per-day=<승인값> \
  --bidvector.award-collection.calls-total=<승인값> \
  --bidvector.award-collection.run-state-dir=<저장소 밖 경로>
```

| 코드 | 뜻 | 처방 |
| --- | --- | --- |
| 0 | 조각의 **모든 슬롯을 끝까지 받았다**(`unfinishedSlots=0`) | 다음 조각 |
| 2 + `halted …` 줄 있음 | 멈췄다(예산 또는 쿼터) | 예산이면 다음 날 **같은 조각**부터, 쿼터면 계정 한도를 본 뒤 같은 조각 |
| 2 + `halted` 줄 없음, `unfinishedSlots>0` | 일부 슬롯을 **끝내지 못했다** — 절단이거나 **사유 없는 짧은 걷기**(총수를 말한 원천에서 덜 받고 끝났다) | **그 조각을 다시 돌린다.** 이 갈래에는 슬롯 단위 이어 돌기가 없어 다음 조각으로 넘어가면 그 개찰일의 낙찰 원문이 영영 빠진다. 다시 돌려도 같은 수가 나오면 아래 §3.1 로 사유를 가린다 |
| 3 | 다른 실행이 잠금을 쥐고 있다 | 그 실행이 끝난 뒤 다시 |
| 4 | 잠금을 걸 수 없다 | 디렉터리 경로·권한을 고친다(기다려도 풀리지 않는다) |
| 1 | 실패 | `collection failed cause=…` 토큰으로 원인을 가린다 |

로그 한 줄에는 건수·날짜·업종 이름·열거값만 실린다 — 공고번호도 낙찰자 상호도 서비스 키도 없다.
**슬롯 단위 줄은 이 갈래에 없다**(마침 줄의 계수뿐이다) — 어느 개찰일이 미완인지와 그 사유는 §3.1 의
두 자리에 남는다.

### 3.1 미완 슬롯의 사유를 어디서 읽는가

로그는 실행 하나의 계수만 든다. 슬롯별 사유는 둘 중 한 자리에 있다.

1. **수집 회계 행**(DB) — 슬롯마다 한 행이다. 절단 사유와 총수·받은 수를 함께 든다.

   ```sql
   SELECT reference_date, received, source_total, pages_fetched, truncation_cause
   FROM collection_run
   WHERE source_endpoint = 'OPENING_AWARD_LIST'
     AND reference_date BETWEEN '<from>' AND '<to>'
   ORDER BY reference_date;
   ```

   - `truncation_cause` 가 **있으면** 절단이다. 토큰은 닫힌 어휘(`truncationCodeOf`)이고 자주 보이는
     것은 `TRANSPORT_FAILURE`·`SERVER_ERROR`·`TIMEOUT`·`NOT_RETRYABLE`·`REPEATED_PAGE`·`MAX_PAGES`·
     `INPUT_ERROR`·`STRUCTURE_FAILURE`·`SELF_THROTTLED`·`UNCLASSIFIED` 다(전수는 그 함수가 정본이고
     여기 열거는 그 부분집합이 아니라 **그 함수의 오늘 전수에서 예산·쿼터 둘을 뺀 것**이다 — 그 둘은
     `halted` 줄로 간다).
   - `truncation_cause` 가 **비었는데** `received < source_total` 이면 **짧은 걷기**다(빈 쪽·`NoData`
     로 곱게 멈췄다). 사유 토큰이 없는 것이 정상이고, 그 차이가 결측의 크기다.
   - 둘 다 아니면(`received = source_total`) 그 슬롯은 끝난 것이다.

2. **시도 원장**(실행 상태 디렉터리의 파일) — 호출 단위다. 그 개찰일의 줄에 `FAILED:<CODE>` 가
   남으므로 회계 행이 없는 판(예외로 끝난 슬롯)에도 사유가 있다.

**다시 돌려도 같은 답이 나오는 축**은 따로다 — `MAX_PAGES`·`NOT_RETRYABLE`·`INPUT_ERROR` 셋과
`SELF_THROTTLED`. 앞 셋은 저장소가 **확정 실패**로 가른 축이고(`attemptOutcomeOf`), 고칠 자리는 쪽
상한·서비스 키 승인 상태·요청 파라미터다. `SELF_THROTTLED` 는 우리 속도 보호가 거부한 것이라 호출이
나가지도 않았다 — 조각을 더 작게 나누거나 속도 정책을 고친다. 넷 다 같은 조각을 되돌이로 돌리면 승인
호출만 태운다.

그 밖(`TRANSPORT_FAILURE`·`SERVER_ERROR`·`TIMEOUT`·`STRUCTURE_FAILURE`·`REPEATED_PAGE`·`UNCLASSIFIED`)은
**일시**로 분류된 축이라 같은 조각 재실행이 맞는 처방이다. `UNCLASSIFIED` 가 반복되면 그 응답 형태가
아직 어휘에 없다는 뜻이므로 재실행을 되돌이로 만들지 말고 그 사유를 올린다(분류를 더하는 일이다).
짧은 걷기도 일시 축이다 — 원천이 총수를 말했으므로 다시 걸으면 받을 수 있다.

## 4. 승격(낙찰 칸 채우기)

수집이 끝나면 **기존** 승격 러너를 돌린다. 이 갈래를 위해 바뀐 것이 없다.

```
java -jar app.jar \
  --bidvector.opening-promote.mode=once \
  --bidvector.opening-promote.run-state-dir=<개찰결과 갈래의 실행 상태 경로> \
  --bidvector.opening-promote.reference-date=<기준일>
```

승격 범위는 그 디렉터리의 **확정 표본**이다. 낙찰 목록 원문은 창 전체를 받으므로 표본 밖 공고의 낙찰
원문은 적재만 되고 canonical 로 올라가지 않는다 — 「내 입찰」 공고가 그 표본에 없으면 화면의 낙찰자
상호는 여전히 비어 있다(알려진 제한, 후속 slice).

## 5. 개인정보

- 낙찰자 **사업자등록번호·대표자명**은 적재되지 않는다(계약 미등재 키는 담는 단계에서 빠진다).
- 낙찰자 **상호**는 적재된다(업체 식별 개념). 원문 보존 창이 지나면 마스킹되고, 마스킹된 행은 승격이
  읽지 않는다 — **수집과 승격을 보존 창 안에 끝낸다**. 조각을 다 돌린 뒤 승격을 미루지 않는다.
  창은 2026-11-05 부터 **1년**이다(그 전 적재분은 90일) — 조각을 여러 날에 나눠 돌려도 승격까지 여유가
  있지만, 「나중에 한꺼번에」는 그 창을 믿는 것이므로 조각마다 승격을 함께 돌리는 편이 안전하다.

## 6. 끄기·되돌리기

- 끄는 법: `bidvector.award-collection.mode` 를 주지 않는다(그 한 줄이 없으면 러너도, 서비스 키
  바인딩도, 이 갈래의 어댑터 생성도 일어나지 않는다).
- 적재된 원문을 되돌릴 필요가 있으면 `source_endpoint = 'OPENING_AWARD_LIST'` 행이 대상이다. 원문 표는
  append-only 이므로 삭제는 운영자 승인과 별도 절차로 한다(이 slice 의 rollback 범위 밖).
