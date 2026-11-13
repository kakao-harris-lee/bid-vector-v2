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

## ① 구판 RED — 회귀 test 를 **구판 구현 위에서** 돌린다

base `94ee445b` 체크아웃에 새 대조만 얹고(등재 등식 절은 구판에 술어가 없으므로 뺀다) 돌렸다.

```
Tests  7 failed | 17 passed (24)
```

일곱 전부 `AssertionError: expected [] to strictly equal [ … ]` 다 — 구판은 그 여섯 꼴에 대해
**의무를 하나도 내지 않았다**. 음성 대조 둘(속이 어휘를 안 쓰는 간접 렌더 · 타입만 내보내기)은
구판에서도 초록이라 이 라운드가 넓힌 범위를 재지 않는다.

## ① 바꿔치우기 변이 — 축마다 하나씩, 전부 치환

커밋 뒤 적용하고, 치환 전후 계수로 적용 여부를 먼저 단언한다(원문이 남으면 더하기이므로 실패).
되돌림은 경로 한정이고 매번 `git status --porcelain --` 가 빈 출력이다.

| 변이(치환) | 붉은 칸 |
| --- | --- |
| 폐포 → **진짜 한 겹**(직접에서 한 홉) | 2 — 두 겹 합성 대조 · **실제 트리 바구니 lock** |
| 폐포 → 직접만(앞 판) | 4 — 위 둘 + 한 겹 합성 대조 + 상호 재귀 |
| 심볼 경유 → 함수꼴 조건 | 2 — 감싸 내보내기 · 클래스 |
| 심볼 경유 → 선언의 `export` 수식자 조건(앞 판의 실제 조건) | 1 — 개명 재수출 |
| 호출 ∪ 참조 → 호출만(앞 판) | 1 — 넘기기 |
| 제외 한 줄 삭제 | 2 — 실제 트리 구멍 0 · 양성 대조 셋 |
| 조립자 누출 술어 끄기 | 1 — 양성 대조 셋 |

**기각한 씨앗 하나를 남긴다** — 고정점 루프를 「한 바퀴」로 줄이는 변이는 **초록**(28 passed)
이었다. 그 초록은 방어 부재가 아니라 **씨앗이 결함이 아니었다**는 뜻이다: 선언이 앞에서 뒤로
놓인 사슬에서는 한 바퀴가 이미 폐포다(Map 순회 중에 `reaching` 이 자란다). 그래서 씨앗을 「직접
닿는 것을 떠 두고 거기서 한 홉」으로 다시 심었고, 그때 비로소 깊이 축이 붉어졌다.

## acceptance

ci.yml `web` job 전건을 그 job 의 명령 그대로 `web/` 에서 돌린다(W-1 은 착수 때 이미 돌았다).

| step | 명령 | exit |
| --- | --- | --- |
| W-2 | `npm run lint` | 0 |
| W-3 | `npm run typecheck` | 0 |
| W-4 | `npm run gate:src-tree` | 0 |
| W-5 | `npm run api:types:check` | 0 |
| W-6 | `npm run test` | 0 — 42 파일 · 928 칸(base 913 → ② 3 · ① 12) |
| W-7 | `npm run gate:tests` | 0 |
| W-8 | `npm run build` | 0 |
| W-9 | `npm run gate:boundary-canary` | 0 |
| W-10 | `npx playwright install chromium` | 0 |
| W-11 | `npm run test:e2e` | 0 |
| W-12 | `./gradlew --no-daemon :app:bootJar` | (채운다) |
| W-13 | `npm run test:e2e:journey` | (채운다) |
| — | `./gradlew --no-daemon check` | (채운다) |

W-12·W-13·Kotlin `check` 는 빌드 자리 하나를 잡고(`bv-build.sh`) 돌린다.

## 측정의 부하 조건

같은 호스트에 다른 프로젝트·다른 레인의 빌드가 있어 **부하가 고정이 아니다**. 세 재현율 측정의
조건을 적는다 — ② 를 닫는 근거가 숫자가 아닌 이유가 여기 있다.

| 측정 | 시작 loadavg(1분) | 같은 호스트의 다른 빌드 |
| --- | --- | --- |
| 수정 전 52회 | 15.7 | 다른 프로젝트 Gradle test 실행 중 |
| 중간 판 52회 | 15.4 | 같음 |
| 최종 판 52회 | 9.1 | 같음(앞 둘보다 가벼움) |
