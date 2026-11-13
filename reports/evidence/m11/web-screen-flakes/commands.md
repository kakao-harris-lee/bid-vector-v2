# M11 / web-screen-flakes — 명령과 실측

## 착수 실측

### ① 모집단 규칙 셋의 집합 비교 (같은 TS 프로그램, `src/screens/**` 전수 22 파일)

| 규칙 | 의무(마운트 대상) | 화면 바구니(짝 test 를 세는 쪽) |
| --- | --- | --- |
| 오늘 | 5 — BidBoxCard · NoticeCard · ResultCard · SuggestionCard · WorksheetTimeline | 1 — WorksheetScreen |
| 내보낸 심볼 + 지역 그래프 **한 겹** + 참조 | 5 (오늘과 같음) | 1 (오늘과 같음) |
| 내보낸 심볼 + 지역 그래프 **전이 폐포** + 참조 | 5 (오늘과 같음) | 2 — WorksheetScreen · **InboxScreen** |

한 겹이 닫지 못하는 이유는 사슬 하나다 — `InboxScreen` → `NotificationGroupBlock` →
`NotificationRow` → 어휘. **두 겹**이다. 오늘 `InboxScreen` 은 의무도 화면 바구니도 아닌 곳에
있다(게이트의 모집단 문장이 이 파일에 대해 아무 말도 하지 않는다).

어휘를 부르는 **비공개** 지역 선언: `InboxScreen.tsx` 의 `NotificationRow` ·
`ResultCard.tsx` 의 `OpeningSection`·`AwardGap`·`MyBidSection`(이 파일의 내보낸 `ResultCard` 는
직접도 부르므로 오늘 이미 의무다).

잔여 다섯의 **오늘 실재 수**: 감싸 내보내기 0 · 개명 재수출 0 · 클래스 컴포넌트 0 · 함수 참조
전달 0(전수 grep) · 간접 렌더 **1**. 어휘 호출은 `src/screens/**`·`src/display/**` **밖에 0**
(전수 grep, exit 1) — 모집단 경계 문장이 오늘은 완전하다.

어휘 모듈의 내보낸 함수 전수 ↔ 등재(7):
`moneyDisplay` 등재 5 · 미등재 2(`currencyUnitOf`·`amountTextOf` — 표시층 내부 조립자, 화면이
참조하지 않는다) · `noticeDisplay` 등재 1 · 미등재 1(`noticeStatusDisplayOf` — 상태 축) ·
`timeDisplay` 등재 1 · 미등재 4(`daysUntil`·`dayCountText`·`localInputToIso`·`readClock`).

### ② 두 서명의 재현율 — 수정 전 52회

`npm run test` 를 52회 돌리고 서명별 술어로 분류했다(로그는 `tee`, 종료 코드는 `PIPESTATUS`).

```
TOTAL=52 A=5 B=5 OTHER_FAIL=0 OTHER_EXIT=0 GREEN=42
```

각 **9.6%**. 같은 run 에서 둘이 함께 난 적은 **0회** — 겹치지 않는 두 결함이다.

- **A** 5회 전부 **같은 칸**(`display-gate-selectors.test.mjs` 의 첫 `it`) ·
  `Error: Test timed out in 5000ms`. 분류 술어: 파일 실패 ∧ 그 파일 이름 ∧ `timed out in 5000ms`.
- **B** 5회 전부 `Test Files 42 passed` ∧ exit 1 ∧ `ReferenceError: window is not defined`.
  스택의 자리: `Timeout._onTimeout` → tanstack 알림 배치 → react-dom 이 `window` 를 찾는다.
  vitest 가 `src/AppRoot.test.tsx` 에서 비롯했다고 적는다. 분류 술어: 파일 실패 0 ∧ exit ≠ 0 ∧
  그 문면.

공유 모듈 전역 `queryClient` 를 쓰는 test 는 셋인데(`AppRoot.test.tsx`·`LoginScreen.test.tsx`·
`queryClient.test.ts`) **root 를 거두지 않는 것은 첫째 하나**다.

## 수정 뒤 재측정 — 같은 횟수 52회

### 중간 판(A 를 `beforeAll` 로) — **고친 것이 아니라 예산 자리를 옮긴 것이었다**

```
TOTAL=52 A=0 B=0 OTHER_FAIL=1 OTHER_EXIT=0 GREEN=51
```

그 1건이 같은 결함이다 — `Hook timed out in 10000ms` · `Test Files 1 failed` ·
**그 파일의 35 칸 전부 skipped**. 칸 예산(5000ms)에서 hook 예산(10000ms)으로 옮겨 9.6% → 1.9%
가 됐을 뿐이고, 게이트의 얼굴은 더 나빠진다(실패한 칸 하나가 아니라 **게이트 전체가 미측정**).

### 최종 판(A 를 모듈 적재 단계로)

```
TOTAL=52 A=0 B=0 OTHER_FAIL=0 OTHER_EXIT=0 GREEN=52
```

세 측정은 모두 같은 호스트·같은 명령·같은 횟수다. 호스트 부하가 고정이 아니므로(다른 레인의
빌드가 같은 장비에 있다) **재현율만으로 닫지 않는다** — 아래 양성·음성 대조와 구판 RED 가
거동을 직접 잰다. `0/52` 는 2% 사건에 대해 충분한 표본이 아니고, A 를 닫는 것은 「예산이 없다」는
구조이지 이 숫자가 아니다.

### 「예산이 없다」의 양성·음성 대조 — 예열에 15s 지연을 **바꿔치워** 심는다

| 꼴 | 결과 |
| --- | --- |
| 적재 단계(최종 판) + 15s | 초록 · 35 passed · `import 95%` · 17.27s — 어떤 예산도 걸리지 않는다 |
| `beforeAll`(중간 판) + 15s | 붉음 · `Hook timed out in 10000ms` · 35 skipped — 위 1건과 같은 서명 |

음성 대조가 서므로 양성 대조가 공허하지 않다. 변이는 되돌렸고 `git status --porcelain --`
해당 두 경로가 **빈 출력**이다.

### A 의 여유 — 같은 명령의 JSON reporter

중간 판에서 첫 실측 칸 **20.5ms**, 뒤따르는 칸 13~18ms(칸 예산 5000ms). 최종 판은 단독 실행의
비용 구성이 `import 68%` 로 바뀐다 — 비용이 칸에서 적재로 옮겨 간 자리가 그대로 보인다.

### B 의 회귀 test 는 구판에서 RED 다

base `94ee445b` 체크아웃에 **단언만** 얹고(수정은 얹지 않음) 돌리면 뒤 칸이 붉다 —
남은 질의 이름 둘이 그대로 나온다(세션 · 온보딩 점검표). 짝이 되는 앞 칸(「그리면 공유 캐시가
실제로 찬다」)은 구판에서도 초록이라 뒤 칸이 공허하지 않다.

## acceptance

(채운다)
