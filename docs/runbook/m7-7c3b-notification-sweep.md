# 알림 훑기 러너 (M7/7C③-b)

시간이 지나서 생기는 알림 — 투찰 마감 D-1 · D-0, 1순위 서류 제출 기한 D-2, 담은 공고의 정정 — 을 한 번 훑어
알림함에 적는 **일회 러너**다. 상주 스케줄러가 아니다(`OPEN-6F10-SCHEDULER`) — 주기는 외부 cron 이 진다.

이 문서는 **절차만** 적는다. 창 폭·모집단 같은 규칙의 정본은 코드(`NOTIFICATION_WINDOW_POLICY` ·
`sweepNotificationsFor`)이고, slice 의 근거는 `reports/evidence/m7/7c3b/` 다.

## 1. 아직 운영에 걸지 않았다

**운영 host 의 cron 등록은 이 slice 밖이다**(D-7C3B-8 — 운영 반입은 7F 에서 별도 승인). 출하 조립의 기본값은
꺼짐이고, compose 는 `bidvector.notification-sweep.mode` 를 설정하지 않는다. 그래서 지금 배포된 상태에서는
**시간 기반 알림 셋이 발화하지 않는다**. 요청 경로 둘(투찰가 요청 기록 · 결과 전이 되비춤)은 러너와 무관하게
웹 프로세스에서 발화한다.

## 2. 거는 법

```
java -jar app.jar \
  --bidvector.notification-sweep.mode=once \
  --bidvector.persistence.jdbc-url=... --bidvector.persistence.username=... --bidvector.persistence.credential=...
```

- **다른 일회 러너와 같은 프로세스에 켜지 않는다.** `OneShotRunnerGuard` 가 `ApplicationRunner` 빈이 둘 이상이면
  기동을 실패시킨다 — 먼저 끝난 러너가 JVM 을 끝내 나머지가 조용히 안 돌기 때문이다.
- **임대를 쥐지 않는다.** 훑기는 멱등이라(NOTI-10 의 PK — 같은 키 재기록은 항목을 늘리지 않고 갱신한다) 두 run 이
  겹쳐도 알림함이 늘지 않는다. 보존 러너가 임대를 쥐는 이유(파기는 되돌릴 수 없다)가 여기에는 없다.
- 자격 헤더·KONEPS 서비스 키를 요구하지 않는다(외부 호출 0).

## 3. 주기

**하루 한 번으로는 모자란다.** 마감 두 창은 각각 **그날 하루**다(D-1 은 「마감의 KST 달력일이 내일」, D-0 은
「오늘」) — 그 창을 한 번도 보지 못하고 지나면 그날 몫의 알림을 잃는다. 서류 기한 창만 폭이 있어(D-2 부터 당일까지)
하루를 빠뜨려도 다음 run 이 같은 항목을 세운다.

권장: **하루 두세 번**, KST 이른 아침과 낮. 창이 달력일 단위라 한 창 안에서 여러 번 돌아도 항목은 하나다
(두 번째부터는 `updated` 로 센다).

## 4. 종료 코드

| 코드 | 이름 | 뜻 | 처방 |
|---|---|---|---|
| 0 | `COMPLETE` | 창에 든 건을 전부 적었다 | 없음 |
| 1 | `FAILED` | 실행이 예외로 끝났다 | 로그의 `cause=` 코드로 본다. 앞서 적힌 항목은 남고 다음 run 이 이어받는다 |
| 2 | `INCOMPLETE` | 적지 못한 알림이 있다(`failed>0`) | 저장 쪽을 본다. 마감 창이 지나기 전이면 다음 run 이 회수한다 |

## 5. 로그

```
notification-sweep start bidDeadlineImminent=1..1 bidDeadlineToday=0..0 documentDeadline=0..2
notification-sweep finish casesRead=12 created=3 updated=4 failed=0 exit=COMPLETE(0)
```

- `casesRead` 는 질의가 좁혀 온 **건 수**이고 아래 셋의 합이 아니다 — 건 하나가 알림 0~2 개를 낳는다.
- `updated` 가 `created` 보다 큰 것은 **정상**이다(같은 창을 다시 본 run).
- **계정 주체도 공고명도 싣지 않는다.** 계수와 창 폭뿐이다.

## 6. 안 도는 것처럼 보일 때

1. `casesRead=0` 인가 — 창에 든 건이 없다는 뜻이다(마감이 모레 이후거나 전부 투찰 기록됨). 알림함이 비는 것이 맞다.
2. **페이퍼 건만 있는가** — 훑기는 실투찰만 본다. 중복 억제 키에 투찰 종류 축이 없어 페이퍼가 실투찰 항목을 덮기
   때문이고, 이것은 설정이 아니라 설계다.
3. **공고 행이 파기됐는가**(6B-3 90일) — 그 건의 유효 마감은 담을 때의 사본으로 떨어지고, 공고 개정 축은 사라진다.
4. 알림함 자체가 비었는지 `GET /api/notifications` 로 본다 — 러너가 적었는데 화면이 안 보이는 것이라면 그쪽 문제다.

## 7. 끄는 법

`--bidvector.notification-sweep.mode` 를 주지 않으면 러너도, 후보 질의도 서지 않는다(배선 전체가 조건부다).
이미 적힌 항목은 남는다 — 지우는 경로는 보존 파기(90일)뿐이고 앱 역할에는 DELETE 권한이 없다.
