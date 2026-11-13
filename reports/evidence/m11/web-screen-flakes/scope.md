# M11 / web-screen-flakes — slice 계약

```yaml
milestone: M11
slice: web-screen-flakes
base_sha: 94ee445b50cafa669af435e651d811cc551c67e8   # 착수 때 다시 기계로 확인한다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/scripts/screen-card-coverage.mjs          # ① 모집단 판정 — 파일 안 전이 폐포
  - web/scripts/screen-card-coverage.test.mjs     # ① 그 판정의 양성·음성 대조
  - web/src/screens/screenVocabulary.test.tsx     # ① 의무 집합이 움직이면 마운트 목록도 (실측상 불변)
  - web/scripts/display-gate-selectors.test.mjs   # ②A — ESLint 콜드 기동을 예산 밖으로
  - web/src/AppRoot.test.tsx                      # ②B — 공유 질의 클라이언트 teardown
  - web/config/gate-tests.txt                     # 11D 와 공유 — 새 게이트 test 파일이 생기면 같은 커밋에서 등재
  - docs/discovery/capability-map.md              # §12.3 의 하위 절 하나 — 글자는 머지 시점에 정한다(§계약 갱신 (1))
  - reports/evidence/m11/web-screen-flakes/
out_of_scope:
  - web/src/screens/*.tsx (test 아닌 것) · web/src/display/**   # 출하 코드를 바꾸지 않는다
  - web/eslint.config.js                  # 11D 의 편집 대상(7 · ⑤⑥⑧⑨⑩)
  - web/scripts/kotlin-source*.mjs        # 11D 의 축
  - openapi/bidvector-operator-api.yaml   # 11B
  - adapters/**/*AdapterDependencyTest.kt · config/quality/architecture-policy.properties  # 11C
  - web/vite.config.ts                    # 11D 와 공유하는 자리 — ② 의 수정은 test 파일 안에서 한다(팀장 계약 변경 (2))
  - web/src/components/**                 # 모집단 디렉터리 축 — 범위 밖 유지 + 신설 OPEN 등재(팀장 결정)
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 leak-pattern 게이트의 스캔 뿌리에 들어 경로가 닿는다
rollback: rollback.md
```

## 받는 항목과 처분 (팀장 답으로 고정 2026-11-13)

| # | 항목 | 이 slice 의 처분 |
| --- | --- | --- |
| 6 | `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` | **닫힘** — 간접 렌더(대표 축)와 잔여 넷을 구조로 닫고, 다섯째(새 어휘 함수)는 **등재 등식**으로 닫는다. §① |
| 9 | web `npm run test` 불안정 두 서명 A·B | **닫힘** — 서명마다 다른 원인, 다른 수정, 다른 커밋. 전·후 같은 횟수(52)로 재고, **닫는 근거는 재현율이 아니라 거동**이다(A 는 「예산이 없다」의 양성·음성 대조, B 는 구판 RED). §② |

## ① 모집단의 간접 렌더 (게이트 — 설계 검토 선행)

오늘의 판정은 **내보낸 선언의 서브트리 안에서 어휘 호출을 해소**한다. 그래서 그 서브트리 밖에
있는 다섯 꼴이 의무를 내지 않는다(원 전수는 `reports/evidence/m9/web-followups/design-review.md`
§③ 「남는 자리」 표).

| 잔여 | 오늘 왜 밖인가 | 이 slice 의 길 |
| --- | --- | --- |
| 간접 렌더 | 내보낸 `Outer` 가 **비공개 `Inner`** 를 그리면 그 파일이 의무를 하나도 안 낸다 | 파일 안 지역 선언 그래프의 **전이 폐포**(§계약 갱신 (2)) |
| 감싸 내보내기(`memo`·`forwardRef`) | `export const X = memo(…)` 의 초기값이 **호출**이라 고려 대상 밖 | 내보낸 **심볼**에서 선언으로 가므로 초기값 꼴을 묻지 않는다 |
| 개명 재수출 | `function Inner…; export { Inner as Card }` — `export` 수식자가 붙은 선언만 본다 | 같은 심볼 경유로 닫힌다 |
| 클래스 컴포넌트 | 함수 선언·표현식만 본다 | 같은 심볼 경유로 닫힌다 |
| 함수 **참조** 전달 | `ats.map(instantDisplayOf)` — 해소할 **호출**이 없다 | 호출뿐 아니라 **참조**가 어휘 선언에 닿는지 본다 |
| 새 어휘 함수 | `VOCABULARY_DECLARATIONS` 가 손목록이다 | 어휘 모듈의 **내보낸 함수 전수 == 등재 ∪ 명시 제외** 등식 |

마지막 줄이 요점이다 — 의무 집합을 「표시 모듈 전부」로 넓히면 5 → 13 이 되고 질의 목이 필요한
패널·훅이 섞인다(M9 실측). 그래서 **집합을 넓히지 않고 목록이 뒤처지는 축만** 등식으로 막는다.

**마운트 가능성과의 어긋남**: 그래프를 따라가면 `*Screen` 까지 의무로 낼 수 있다. 화면은 오늘도
의무에서 빠지고 **짝이 되는 test 파일이 실제로 있는지** 세는 장치가 이미 있다 — 그 축을 그대로
쓴다. 실측상 마운트 의무는 **5 불변**이고 화면 바구니만 **1 → 2**(`InboxScreen` 추가)로 늘며,
그 짝 test 는 이미 있다 — 그래서 오늘의 production 은 붉어지지 않는다(팀장 승인, §계약 갱신 (2)).
새 축이 오늘의 화면 디렉터리를 붉히는 자리가 나오면 구현하지 않고 멈춰 보고한다(계약 「승인 경계」).

## ② 불안정 두 서명 — 따로 재현하고 따로 고친다

**한 원인으로 접지 않는다.** 서명마다 분류 술어가 다르고, 수정 전·후 같은 횟수(≥ 50회)로 잰다.
로그는 `tee` + `PIPESTATUS`(명령의 종료 코드를 `tee` 가 가리지 않게).

- **A — `scripts/display-gate-selectors.test.mjs` 첫 칸의 5초 timeout.** 그 파일은 모듈 꼭대기에서
  `new ESLint({cwd})` 를 만들고 **첫 `it` 이 처음 `lintText` 를 부르며** flat config 해석·플러그인
  적재(콜드 기동)를 전부 진다. 그 비용이 첫 칸 **하나의** 5초 예산 안에 들어 있다.
  1안(`beforeAll`)은 **측정으로 기각됐다** — 같은 결함이 hook 예산에서 1/52 로 다시 났다.
  채택한 길은 비용을 **모듈 적재 단계**로 옮기는 것이다(칸·hook 예산이 없는 자리 · 상한은 CI
  job 의 `timeout-minutes`). 분류 술어: 파일 실패 + 그 파일 이름 + `timed out in 5000ms`.
- **B — 전부 통과인데 exit != 0.** `src/AppRoot.test.tsx` 가 실화면(`/start` = 화면 A)을 라우트
  트리째 그린다. 셸이 **모듈 전역 `queryClient`** 를 제공하므로 화면의 `useQuery` 들이 뜨고,
  test 는 그 root 를 **unmount 하지 않는다**. jsdom teardown 뒤 tanstack 의 알림 배치가 깨어나
  `window` 를 참조한다. **1안: 그 test 가 만든 root 를 거두고 공유 클라이언트를 정리한다.**
  분류 술어: 파일 실패 0 + 종료 코드 != 0 + 로그에 `window is not defined`.

## base 확인

`git merge-base --is-ancestor 94ee445b HEAD` · `git rev-parse 94ee445b` 를 착수 커밋에서 기계로
확인한다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신 (팀장 2026-11-13)

**(1) §12.3 의 절 글자를 slice 에 고정하지 않는다.** 브리프의 `(o)` 배정은 폐기됐다. 이 PR 이
main 을 **마지막으로 병합할 때** 그 시점의 다음 글자로 절을 **끝에 덧붙인다**. 그래서 이 문서와
등재 절은 글자를 축어로 쓰지 않는다.

**(2) 간접 렌더를 닫는 깊이: 「한 겹」 → 「파일 안 전이 폐포」.** 착수 실측이 근거다 — 오늘
실재하는 유일한 간접 렌더가 **두 겹**이라(`InboxScreen` → `NotificationGroupBlock` →
`NotificationRow` → 어휘) 한 겹은 그것을 닫지 못한다. 폐포는 계약의 한 겹보다 **넓을 뿐 좁지
않으므로** 계약 위반이 아니다. 화면 바구니가 **1 → 2**(`InboxScreen`)로 바뀌는 것도 승인 경계
안이다(짝 test 가 이미 있어 오늘의 production 을 붉히지 않는다). 폐포가 **마운트 의무 집합**을
바꾸는지는 설계 검토에서 TS 로 다시 재고, 바뀌면 구현 전에 멈춰 보고한다.

**(3) ② 의 수정 자리.** 서명 B 는 `web/vite.config.ts` 의 test 블록이 아니라 `AppRoot.test.tsx`
**안에서** 한다(그 설정 파일은 11D 와 공유하는 자리다). `web/config/gate-tests.txt` 도 11D 와
공유한다 — 이 slice 는 새 test **파일**을 만들지 않으므로 오늘 계획상 편집 0 이다.

**(4) 커밋 트레일러.** 이 세션의 귀속은 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
다. 착수 커밋 넷(`e0b121a0`·`9d6e3cb3`·`bc9374cf`·`556aa3b3`)과 `89f6ee38` 은 `Claude Opus 5` 로
적혔고 **이력을 되쓰지 않는다**(팀장 지시). 이 줄이 그 사실의 선언이다.

## 신설 OPEN — 범위 밖 유지 (팀장 결정 2026-11-13)

둘 다 등재 절에 쓴다. 사유와 받는 자리는 팀장이 정한 그대로다.

| 축 | 사유 | 받는 자리 |
| --- | --- | --- |
| 모집단 **디렉터리** — 카드를 `src/screens/**` 밖(`src/components/**`)에 두면 의무를 안 낸다 | ①의 축(내보낸 겉의 서브트리)과 **다른 축**이고 오늘 실재 0(어휘 호출 전수 grep) | 다음 화면 slice |
| 게이트 test 의 **준비 비용이 칸·hook 예산 안**에 있는 자리 | 서명 A 가 실제로 재현된 파일은 **하나**다. 후보 둘은 `web/scripts/money-vocab-cross-lang.test.mjs` · `web/scripts/kotlin-source-ownership.test.mjs`(둘 다 `typescript` 를 직접 들인다) | 그 test 파일에서 A 서명이 재현되는 날의 slice |

## 갱신 이력

- 2026-11-13 초판(착수 보고와 함께).
- 2026-11-13 팀장 답 고정 — §계약 갱신 (1)~(4) · §신설 OPEN 둘 · ② 측정 설계 승인.
