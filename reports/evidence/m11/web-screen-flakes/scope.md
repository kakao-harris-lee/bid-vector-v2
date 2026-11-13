# M11 / web-screen-flakes — slice 계약

```yaml
milestone: M11
slice: web-screen-flakes
base_sha: 94ee445b50cafa669af435e651d811cc551c67e8   # 착수 때 다시 기계로 확인한다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/scripts/screen-card-coverage.mjs          # ① 모집단 판정 — 한 겹 호출 그래프
  - web/scripts/screen-card-coverage.test.mjs     # ① 그 판정의 양성·음성 대조
  - web/src/screens/screenVocabulary.test.tsx     # ① 의무 집합이 움직이면 마운트 목록도 (오늘 실측상 불변 전망)
  - web/scripts/display-gate-selectors.test.mjs   # ②A — ESLint 콜드 기동을 첫 칸 밖으로
  - web/src/AppRoot.test.tsx                      # ②B — 질의 클라이언트 teardown 처리
  - web/config/gate-tests.txt                     # 새 게이트 test 파일이 생기면 같은 커밋에서 등재
  - docs/discovery/capability-map.md              # §12.3 하위 절 (o) 만
  - reports/evidence/m11/web-screen-flakes/
out_of_scope:
  - web/src/screens/*.tsx (test 아닌 것) · web/src/display/**   # 출하 코드를 바꾸지 않는다
  - web/eslint.config.js                  # 11D 의 편집 대상(7 · ⑤⑥⑧⑨⑩)
  - web/scripts/kotlin-source*.mjs        # 11D 의 축
  - openapi/bidvector-operator-api.yaml   # 11B
  - adapters/**/*AdapterDependencyTest.kt · config/quality/architecture-policy.properties  # 11C
  - vite.config.ts                        # 수집 범위·격리 설정은 ②의 1안이 아니다(§②A 1안)
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 leak-pattern 게이트의 스캔 뿌리에 들어 경로가 닿는다
rollback: rollback.md
```

## 받는 항목과 처분 (착수 전망 — 팀장 답으로 고정한다)

| # | 항목 | 이 slice 의 처분 |
| --- | --- | --- |
| 6 | `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` | **닫힘** — 간접 렌더(대표 축)와 잔여 넷을 구조로 닫고, 다섯째(새 어휘 함수)는 **등재 등식**으로 닫는다. §① |
| 9 | web `npm run test` 불안정 두 서명 A·B | **닫힘** — 서명마다 다른 원인, 다른 수정. 재현율을 수정 전·후 같은 횟수로 잰다. §② |

## ① 모집단의 간접 렌더 (게이트 — 설계 검토 선행)

오늘의 판정은 **내보낸 선언의 서브트리 안에서 어휘 호출을 해소**한다. 그래서 그 서브트리 밖에
있는 다섯 꼴이 의무를 내지 않는다(원 전수는 `reports/evidence/m9/web-followups/design-review.md`
§③ 「남는 자리」 표).

| 잔여 | 오늘 왜 밖인가 | 이 slice 의 길 |
| --- | --- | --- |
| 간접 렌더 | 내보낸 `Outer` 가 **비공개 `Inner`** 를 그리면 그 파일이 의무를 하나도 안 낸다 | 파일 안 호출·참조 그래프를 **한 겹** 따라간다 |
| 감싸 내보내기(`memo`·`forwardRef`) | `export const X = memo(…)` 의 초기값이 **호출**이라 고려 대상 밖 | 내보낸 **심볼**에서 선언으로 가므로 초기값 꼴을 묻지 않는다 |
| 개명 재수출 | `function Inner…; export { Inner as Card }` — `export` 수식자가 붙은 선언만 본다 | 같은 심볼 경유로 닫힌다 |
| 클래스 컴포넌트 | 함수 선언·표현식만 본다 | 같은 심볼 경유로 닫힌다 |
| 함수 **참조** 전달 | `ats.map(instantDisplayOf)` — 해소할 **호출**이 없다 | 호출뿐 아니라 **참조**가 어휘 선언에 닿는지 본다 |
| 새 어휘 함수 | `VOCABULARY_DECLARATIONS` 가 손목록이다 | 어휘 모듈의 **내보낸 함수 전수 == 등재 ∪ 명시 제외** 등식 |

마지막 줄이 요점이다 — 의무 집합을 「표시 모듈 전부」로 넓히면 5 → 13 이 되고 질의 목이 필요한
패널·훅이 섞인다(M9 실측). 그래서 **집합을 넓히지 않고 목록이 뒤처지는 축만** 등식으로 막는다.

**마운트 가능성과의 어긋남**: 한 겹 따라가기는 `*Screen` 까지 의무로 낼 수 있다. 화면은 오늘도
의무에서 빠지고 **짝이 되는 test 파일이 실제로 있는지** 세는 장치가 이미 있다 — 그 축을 그대로
쓴다. 설계 검토가 「넓어진 의무가 마운트 불가능한 자리를 만드는가」를 판정하고, **오늘의 화면
디렉터리가 새로 붉어지면 구현하지 않고 멈춰 보고한다**(계약 「승인 경계」).

## ② 불안정 두 서명 — 따로 재현하고 따로 고친다

**한 원인으로 접지 않는다.** 서명마다 분류 술어가 다르고, 수정 전·후 같은 횟수(≥ 50회)로 잰다.
로그는 `tee` + `PIPESTATUS`(명령의 종료 코드를 `tee` 가 가리지 않게).

- **A — `scripts/display-gate-selectors.test.mjs` 첫 칸의 5초 timeout.** 그 파일은 모듈 꼭대기에서
  `new ESLint({cwd})` 를 만들고 **첫 `it` 이 처음 `lintText` 를 부르며** flat config 해석·플러그인
  적재(콜드 기동)를 전부 진다. 그 비용이 첫 칸 **하나의** 5초 예산 안에 들어 있다.
  **1안: 콜드 기동을 `beforeAll` 로 옮긴다**(timeout 을 늘리지 않는다 — 늘리면 예산만 커지고
  「첫 칸이 남의 비용을 진다」는 꼴은 그대로다). 분류 술어: 파일 실패 + `timed out in 5000ms`.
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

## 갱신 이력

- 2026-11-13 초판(착수 보고와 함께).
