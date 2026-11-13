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

앞 판의 판정은 **내보낸 선언의 서브트리 안에서 어휘 호출을 해소**했고, 그 밖의 여섯 꼴이 의무를
내지 않았다. 잔여 전수와 각각의 처분·우회·대조는 `design-review.md` §(1)(2)(2b) 가 정본이고 여기
반복하지 않는다. 요지만 — 열거였던 세 축(내보내기 꼴 · 어휘에 닿는 길 · 깊이)을 구성으로 옮겨
**다섯을 닫고**, 여섯째(새 어휘 함수)는 어휘 모듈 셋에 대해서만 등식이 받으며 나머지 표시 모듈
축은 **경계 등재**다(r1 F1 · 신설 OPEN).

**마운트 가능성과의 어긋남**: 실측상 마운트 의무는 **5 불변**이고 화면 바구니만 **1 → 2**
(`InboxScreen` 추가)이며 그 짝 test 는 이미 있다 — 그래서 오늘의 production 은 붉어지지 않는다
(팀장 승인). 새 축이 오늘의 화면 디렉터리를 붉히는 자리가 나오면 구현하지 않고 멈춰 보고한다
(계약 「승인 경계」). r1 에서 실제로 그 자리가 나왔고(의무 +2), 팀장 판정으로 경계 등재가 됐다.

## ② 불안정 두 서명 — 따로 재현하고 따로 고친다

**한 원인으로 접지 않는다.** 서명마다 분류 술어가 다르고, 수정 전·후 같은 횟수(52)로 잰다.
로그는 `tee` + `PIPESTATUS`. 원인·수정·측정은 `commands.md`, 처분은 `checklist.md` 가 정본이다.

- **A** 분류 술어: 파일 실패 ∧ `scripts/display-gate-selectors.test.mjs` ∧ `timed out in 5000ms`.
- **B** 분류 술어: 파일 실패 0 ∧ exit ≠ 0 ∧ `window is not defined`.

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

**(4) 커밋 트레일러.** 이 세션의 귀속은 `Claude Opus 5.5` 다. 착수 커밋 다섯은 `Claude Opus 5`
로 적혔고 **이력을 되쓰지 않는다**(팀장 지시) — 사실 선언은 `checklist.md` §트레일러.

## 신설 OPEN — 범위 밖 유지 (팀장 결정 2026-11-13)

**셋**이다 — 모집단 **디렉터리** 축 · 게이트 test **준비 비용** 축 · **파일 사이 어휘 도달** 축
(r1 F1). 정본은 capability map 의 이 slice 절이고, 무엇·사유·받는 자리를 거기 적었다. 여기
반복하지 않는다.

## 갱신 이력

- 2026-11-13 초판(착수 보고와 함께).
- 2026-11-13 팀장 답 고정 — §계약 갱신 (1)~(4) · §신설 OPEN 둘 · ② 측정 설계 승인.
