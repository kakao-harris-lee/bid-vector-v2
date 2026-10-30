# M7/7E-3a 되돌림

**실측 HEAD: `43718a14`**(수정 라운드 3 뒤 이 slice 의 마지막 산출물 커밋 — 라운드마다 재산출·
재실측한다). 이 뒤의 커밋은 evidence 뿐이고 **복원 목록 안의 경로를 건드리지 않는다** —
유효성 확인은 아래 §4.

복원 기준 **`003ec91d`**(main, PR #107 7E-2b 머지). 전제 「그 SHA 에 이 slice 없음」: 이 브랜치의
첫 커밋이 `553715f7`(착수 계약)이고 그 부모가 `003ec91d` 다 — base 에 이 slice 의 커밋이 하나도
없다. base 는 **머지된 main 의 고정 SHA** 이므로 공유 파일(요구표 · `gate-tests.txt` ·
`journey.spec.ts` · `shared.ts` · `routes*` · `api/*`)도 같은 자리에서 복원한다(움직이는
`origin/main` 을 쓰지 않는다).

## 1. 복원 목록 — 기계 산출

```
git diff --name-status 003ec91d..HEAD | grep -v $'\t'reports/evidence/
```

**37 경로 = 복원(M) 18 + 삭제(A) 19.** `reports/evidence/m7/7e3a/**` 는 되돌림 대상이 아니다
(그 안의 `scope.md` 는 팀장 파일이다).

**r1 에서 여덟이 늘었다**(22 → 30). 새 파일 셋은 여정 레인 게이트(`scripts/journey-results.mjs` ·
`check-journey-results.mjs` · `journey-results.test.mjs`)이고, 새로 **고친** 파일 다섯은
`web/package.json` · `web/playwright.journey.config.ts`(게이트 배선) 와 `web/src/display/bidBoxDisplay.ts` ·
`web/src/screens/BidBoxScreen.tsx` · `web/src/screens/BidBoxScreen.test.tsx`(탭 deep link, r1 M-2)다.
뒤 셋은 **화면 D 의 파일**이라 이 slice 가 처음 건드리는 공유 자리이지만, base `003ec91d` 가
머지된 main 의 고정 SHA 이므로 복원 자리는 같다.

**r3 에서 둘이 더 늘었다**(35 → 37) — 둘 다 **고친** 파일이고 새 파일은 0 이다:
`web/src/api/apiError.ts`(`isUnauthorized` 가 UI 모듈에서 옮겨 온 자리)와
`web/src/components/WriteFailure.tsx`(그 재수출). 앞 라운드의 설명은 아래에 둔다.

**r2 에서 다섯이 늘었다**(30 → 35). 새 파일 넷은 게이트의 축 둘이 들인 것
(`scripts/journey-run-begin.mjs` · `journey-report-path.mjs` · `config/journey-specs.txt`)과
재시도 술어 test(`src/queryClient.test.ts`)이고, 새로 **고친** 파일 하나는 `src/queryClient.ts`
(401 재시도 금지를 전역 기본에 둔 자리)다 — 그것도 공유 파일이고 같은 base 에서 복원한다.

| 복원(M) 15 |
| --- |
| `docs/discovery/m7-screen-data-requirements.md` |
| `web/config/gate-tests.txt` |
| `web/package.json` · `web/playwright.journey.config.ts` |
| `web/e2e/harness/fixtures/notice-list.json` |
| `web/e2e/journey/journey.spec.ts` · `shared.ts` |
| `web/src/api/approvedOperations.ts` · `queries.ts` · `queryKeys.ts` |
| `web/src/display/bidBoxDisplay.ts` |
| `web/src/queryClient.ts` |
| `web/src/api/apiError.ts` |
| `web/src/components/WriteFailure.tsx` |
| `web/src/routes.test.ts` · `routes.tsx` |
| `web/src/screens/BidBoxScreen.tsx` · `BidBoxScreen.test.tsx` |

| 삭제(A) 15 |
| --- |
| `web/e2e/journey/results.spec.ts` |
| `web/scripts/journey-results.mjs` · `check-journey-results.mjs` · `journey-results.test.mjs` |
| `web/scripts/journey-run-begin.mjs` · `journey-report-path.mjs` |
| `web/config/journey-specs.txt` |
| `web/src/queryClient.test.ts` |
| `web/src/display/notificationDisplay.ts` · `.test.ts` |
| `web/src/display/openingResultDisplay.ts` · `.test.ts` |
| `web/src/display/resultDisplay.ts` · `.test.ts` |
| `web/src/screens/InboxScreen.tsx` · `.test.tsx` |
| `web/src/screens/ResultCard.tsx` |
| `web/src/screens/ResultsScreen.tsx` · `.test.tsx` |

**목록 ↔ 기계 산출 등식**: `comm -23` · `comm -13` 둘 다 **빈 출력**(실측). 이 등식이 공허하지
않은 근거는 목록을 손으로 적지 않고 같은 명령에서 뽑았다는 것이 아니라, **evidence 를 뺀 뒤의 두
집합을 서로 대조**한다는 것이다 — 내가 커밋하지 않아도 경로가 들어오면 `comm -13` 이 그것을 낸다.

## 2. 되돌리는 명령

```bash
BASE=003ec91d
git diff --name-status "$BASE"..HEAD | grep -v $'\t'reports/evidence/ > /tmp/rb-all.txt
awk -F'\t' '$1=="M"{print $2}' /tmp/rb-all.txt | sort > /tmp/rb-modified.txt
awk -F'\t' '$1=="A"{print $2}' /tmp/rb-all.txt | sort > /tmp/rb-added.txt

xargs -a /tmp/rb-modified.txt git restore --source="$BASE" --staged --worktree --
xargs -a /tmp/rb-added.txt git rm -f --
```

새 파일은 `rm` 이 아니라 **`git rm`** 이다 — `rm` 만 하면 인덱스에 남아 트리가 base 와 같아지지
않는다(첫 판의 오측: 작업 트리 diff 는 0 인데 `git status` 가 삭제 표지 12 를 냈다).

마이그레이션 0 · 배포물 0 · 외부 호출 0 이므로 **배포 뒤 되돌림 경로가 따로 없다**. 이 slice 는
`web/` 과 문서만 건드린다.

`.github/workflows/ci.yml` 과 `tools/one-command-check.sh` 는 **세 라운드 어디에서도 건드리지
않았다** — 여정 레인 게이트는 `npm run test:e2e:journey` **안에서** 이어 돌므로(r2 에서 걸음이
셋으로 늘었어도 그 script 이름이 같다) 두 파일의 축어 일치가 그대로다. 그래서 복원 목록에도
들지 않는다.

## 3. 버릴 clone 에서의 ①~⑥ 실측

`git clone --no-hardlinks` 로 뜬 트리에서 위 명령을 돌리고 **`npm ci` 로 의존을 진짜 설치**했다.

| 축 | 결과 |
| --- | --- |
| ① 명령 exit | `git restore` **0** · `git rm` **0** |
| ② 계수 | 복원 **18** · 삭제 **19** · `comm` 양쪽 빈 출력 |
| ③ 트리 동일성 | 작업 트리 vs base diff **0 줄** · **인덱스** vs base diff **0 줄** |
| ④ compile | `tsc --noEmit` exit **0** |
| ⑤ test | `vitest run` exit **0** — **472 passed**(HEAD 의 606 에서 이 slice 의 134 가 빠진 수) |
| ⑥ 게이트 | `gate:tests` **0** · `gate:src-tree` **0** · `api:types:check` **0** · `gate:boundary-canary` **0** · `eslint --max-warnings 0` **0** |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** 했다 — ③ 의 두 줄이 그것이고, ④⑤⑥ 은 그
동일한 트리가 실제로 서는지의 확인이다.

### 첫 판의 오측 둘 (고쳐서 다시 쟀다)

1. **`gate:src-tree` 가 붉었다** — 되돌림과 무관했다. clone 에 `node_modules` 를 **심볼릭 링크**로
   걸었는데 그 게이트가 「`web/` 안에 심볼릭 링크가 있다」로 멈춘다(ADR 0011 D-3). 같은 clone 에서
   **되돌리기 전에도** 같은 문면으로 실패하는 것을 통제군으로 확인하고, `npm ci` 로 바꿔 다시 쟀다.
2. **③ 의 술어가 틀렸다** — `rm` 뒤 `git status --porcelain` 이 삭제 표지 12 를 내는 것을 「남은
   새 파일」로 읽었다. 그것은 인덱스와 작업 트리의 차이이지 base 와의 차이가 아니다. 술어를
   **base 대비 두 축**(작업 트리 · 인덱스)으로 바꿨다.

## 4. 실측 HEAD 가 아직 유효한가

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」는 영원히 성립하지 않는다. 볼 것은
**그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only 43718a14..<판정 SHA> -- <위 37 경로>
```

이 출력이 비어 있으면 위 실측이 그대로 유효하다. evidence 커밋은 `reports/evidence/m7/7e3a/**`
만 건드리므로 비어 있어야 하고, 한 줄이라도 나오면 **재측정이다**.
