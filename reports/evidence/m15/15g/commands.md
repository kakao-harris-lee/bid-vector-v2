# M15 / 15g — 실행 명령과 종료 코드

> **마지막 HEAD 의 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의 post-state 를
> 담을 수 없다. 마지막 HEAD 의 게이트 결과는 **verifier 와 PR 조치 코멘트**가 든다. 전건의
> 정본은 PR CI 다섯 job 이고, 아래는 전부 **표적·변이** 실측이다.

## RED 가 먼저였다 — ④ 의 자리

④(멈춤 사유 우선순위)는 **거동을 바꾸지 않는 잠금 test** 라 「구판에서 붉다」가 없다. 그 자리를
대신하는 것이 변이 **M1** 이다 — 잠그려는 순서를 뒤집으면 이 test 하나만 붉는다(아래 표). 변이가
없으면 「원래 통과하는 단언을 더했다」와 구별되지 않는다.

## Kotlin 표적

- cmd: `./gradlew --no-daemon --no-build-cache :app:test --tests 'bidvector.app.evaluation.*'`
- exit **0** · suite 6 · test **41** · 실패 0 (`EvaluationCommitRunnerTest` 가 17 → **18**)

### 라운드 1 뒤 — 품질 게이트 묶음도 돌렸다

표적 test 하나만 돌려 `sizeGate` 를 놓쳤다(`scope.md` 「라운드 1」). 고친 뒤:

- cmd: `./gradlew --no-daemon --no-build-cache --continue :app:sizeGate :app:detekt
  :app:ktlintMainSourceSetCheck :app:ktlintTestSourceSetCheck :app:cpdCheck
  :app:test --tests 'bidvector.app.evaluation.*'`
- exit **0** · `evaluateAccounts` **49 줄**(한도 50) · 평가 test 41 · 실패 0

## web 표적

| 명령 | 결과 |
| --- | --- |
| `npm run typecheck` | exit 0 |
| `npm run lint` | exit 0 |
| `npx vitest run`(설정 · 시작하기 · 표시층) | 3 파일 **133 passed** |
| 여정 `settings.spec.ts` **전건** | **5 passed** |
| 여정 `settings.spec.ts -g 사업자번호` | 2 passed |

## 변이 — 전부 **바꿔치우기**다(더하기 아님)

각 변이는 파일 사본으로 심고 사본으로 되돌렸다(`git checkout --` 을 쓰지 않는다 — 공유 트리다).
적용은 심을 때마다 줄 수로 확인했고, 되돌린 뒤 `git status --porcelain` 이 빈 출력이다.

| 변이 | 결과 |
| --- | --- |
| **M1** `stopCauseFor` 의 `when` 두 가지 **순서 교환**(1 줄 바꿈) | **RED** — 41 중 1 실패, **새 test 하나뿐**. 문면 `expected:<cause=interrupted> but was:<… cause=consecutive-account-failures consecutive=3 remaining=2>` |
| **M2** `causeCodeOf` 의 **SQL 갈래**가 예외 메시지를 싣는다(1 줄 바꿈) | **RED** — 41 중 1 실패, 「감싼 인터럽트…」 하나. **앞 test(다른 갈래)는 초록** → ⑨ 가 더한 두 줄이 그 갈래를 실제로 잠근다 |
| **ATTR-placeholder** 그 속성을 술어의 모집단에서 뺌 | **RED** — 「예외가 그 칸의 placeholder 까지 열었다」 |
| **ATTR-title** 〃 | **RED** — 「… title 까지 열었다」 |
| **ATTR-alt** 〃 | **RED** — 「… alt 까지 열었다」 |
| **ATTR-aria-label** 〃 | **RED** — 「… aria-label 까지 열었다」 |
| **TH** 첫 되돌림(`clearWatchCondition`)이 **던진다** | **RED 이되 사유 문자열로** — `Received: "전역 감시 조건을 되돌리다: 심은 전송 오류"`. **catch 가 돌고 정상 반환했다는 뜻이고, 그러면 다음 줄(둘째 되돌림)이 실행됐다** — ⑦ 이 닫으려던 바로 그 축이다 |
| **TD** 번호 teardown 을 no-op 으로 | **RED** — 「설정 여정이 사업자번호를 남겼다」. 끝 상태 단언이 공허하지 않다 |

**ATTR 넷의 의미**(v F-2): 앞 판에서 이 셋(`placeholder`·`alt`·`aria-label`)은 **생존**했다 —
suite 안에서 재지 않고 있었다. 넷이 술어의 같은 수집 줄에 서는 것은 사실이지만 그것은 구조
추론이고, 대조의 속성 목록을 술어에서 **가져오지 않는** 것이 이 측정이 공허해지지 않는 조건이다.

## 캡처 재촬영

- cmd: `npm run capture:usecase` (출하 하네스 — 실 app.jar · fake KONEPS · 버릴 Postgres · 합성 fixture)
- exit **0** · **16 장** · 합 **1,633,739** 바이트(상한 2MB · 파일 300KB 안 — 스크립트가 잰다)
- 바뀐 파일 **13**(PNG 12 + `manifest.json`). `b-notices-*` · `b2-notices-default-*` 넷은 **바이트 동일**
- **치수가 바뀐 것은 `a-start` 둘뿐**: 데스크톱 1280×1162 → 1280×1278 · 모바일 390×2039 → 390×2171
  (15C 가 더한 「1. 사업자번호 — 비워 둠(선택)」 절과 사업 정보 폼의 「사업자번호 (선택)」 칸)
- `a-start` 밖의 DIFF 는 **화면 변경이 아니다** — 알림함·워크시트·입찰함·결과가 **실행 시각**을
  그리는 자리이고 치수가 전부 같다(육안 확인: 알림함의 시각 칸이 캡처 시각이다)
- 스크립트가 `web/` 의 미커밋 변경을 거부하므로 `manifest.json` 의 `head` 가 **이 캡처의 트리**를
  가리킨다 — 캡처 전에 web 편집 둘을 먼저 커밋했다. 캡처 뒤의 산출물 커밋은 `68dc7a596`
  (Kotlin 주석)과 15g evidence 뿐이라 `head` 가 판정 SHA 와 달라도 화면은 같다

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15g/ reports/evidence/m15/15c/ reports/evidence/m15/15f/`
- exit: **1**(매치 없음 = 통과)
- 육안: 실 사업자의 번호 · Telegram id · 운영 credential **0**. 이 파일이 적는 값은 전부 합성이거나
  게이트 문면이다. 캡처 PNG 는 하네스 화면이라 실 데이터가 0 이고, 스크립트가 찍기 **직전** DOM
  개인정보 단언을 걸어 하나라도 걸리면 파일을 쓰지 않는다

## 낡는 좌표

- cmd: `grep -rnE '(EvaluationCommitRunner|m6-6e-operations|settings\.spec|profileDisplay|usecase/captures)[A-Za-z./-]*:[0-9]+' --include='*.md' --include='*.kt' --include='*.ts' --include='*.tsx' --include='*.properties' --include='*.mjs' --include='*.json' .`
- 추적 대상 결과 **0**. 걸린 셋은 `web/test-results/` · `web/playwright-report/`(로컬 실행 산출물,
  git 이 무시한다)뿐이다
- 이 evidence 는 편집한 파일을 `file:line` 으로 가리키지 않는다(인용문·절 제목·처분 번호로 쓴다)

## clean-tree 게이트 (리뷰 요청 조건)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 출력 없음
- 양성 대조: 변이 라운드가 그 자리다 — 심을 때마다 해당 경로가 `M` 으로 잡혔고 사본 복원 뒤
  빈 출력으로 돌아왔다(`checkout --` 을 쓰지 않았다)

## 되돌리기

`rollback.md` 가 정본이다 — **실측 HEAD `68dc7a596`**(라운드 1 이 복원 목록 안 파일을 다시
건드려 라운드 0 의 측정을 버리고 다시 냈다) · 목록 **22**(A 0 · M 22 · D 0) · 복원 exit 0 ·
원천 대비 diff **0 줄** · ④ 컴파일 · ⑤ test 40/0 · ⑥ `leakPatternGate` 전부 초록 ·
내 표지 0(복원 목록 안) · 남의 표지 보존.
