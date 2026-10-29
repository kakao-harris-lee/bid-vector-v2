# M7/7E-1 — 되돌림

base **`78d97c59`**(2026-10-29 고정 — 착수 계약의 base 그대로다. 이 slice 의 base 는 움직이지
않았다).

**그러나 `main` 은 실측 뒤에 움직였다**(계수: 6 커밋, PR #101 caddy 직접 빌드). 그 가운데
**`.github/workflows/ci.yml` 이 교집합 한 건**이고 — 나머지 in_scope 경로와의 교집합은 0 이다 —
그 변경은 `container` job 의 주석 hunk 라 이 slice 가 바꾼 `web` job 과 겹치지 않는다(실측).
그래서 **공유 파일의 복원 기준은 base 가 아니라 「병합된 main 의 고정 SHA」다**(아래 복원 절) —
base 로 되돌리면 그 레인의 줄을 함께 지운다. 머지 순서 조정은 팀장 자리다.
**전제**: 그 SHA 에 이 slice 의 산출물이 **없다** — 첫 산출물 커밋이 그 뒤에 온다는 것을 아래
등식(복원 19 가 모두 `M`, 삭제 27 이 모두 `A`)과 되돌린 뒤 남는 차이가 **이 slice 의 계약
문서 하나**뿐이라는 계수로 함께 본다.

**실측 HEAD**: `24fab9ef` — 이 slice 의 **마지막 산출물 커밋 그 자체**다(그 뒤는 evidence
커밋뿐이고 evidence 는 아래 목록에 들어가지 않는다).

앞 라운드(`cd4a245a`)의 실측을 **옮기지 않고 다시 쟀다** — 여정 spec 하나가 더 바뀌었고 그
파일은 삭제 목록에 있으므로, 목록이 그대로여도(복원 19 · 삭제 27, `comm` 차이 없음) 그것은
**재산출해 보아야 아는 것**이다. 두 라운드의 결과는 계수·트리 해시까지 같다.

목록은 **기계 산출**이다: `git diff --name-status 78d97c59..HEAD`(evidence 제외). 라운드마다
재산출한다.

## 되돌림의 경로는 하나다 (마이그레이션·외부 상태가 없다)

이 slice 는 **표를 만들지 않고 외부에 아무것도 보내지 않는다** — 화면 코드·설정·게이트 술어와
e2e 하네스뿐이다. 그래서 되돌림이 배포 전/후로 갈리지 않고 **파일을 되돌리는 것으로 끝난다**.

여정 e2e 가 띄우는 것(버릴 Postgres 컨테이너 · fake KONEPS · 출하 jar 프로세스)은 **실행 중에만**
있고 하네스가 거둔다 — 되돌릴 상태가 남지 않는다(잔존 계수는 `commands.md`).

## 복원 목록 — 수정된 파일 **열아홉** (base 로 복원)

```
git restore --source=78d97c59 --staged --worktree -- \
  .github/workflows/ci.yml tools/one-command-check.sh \
  web/config/gate-tests.txt web/eslint.config.js web/package.json web/package-lock.json \
  web/playwright.config.ts web/tsconfig.json web/vite.config.ts \
  web/scripts/check-src-tree.mjs web/scripts/module-graph-boundary.mjs \
  web/scripts/module-graph-boundary.test.mjs \
  web/src/AppRoot.tsx web/src/AppRoot.test.tsx web/src/AppShell.tsx \
  web/src/LoginScreen.test.tsx web/src/index.css web/src/routes.tsx web/src/routes.test.ts
```

**공유 파일 둘**(`ci.yml` · `one-command-check.sh`)은 **main 의 고정 SHA 에서** 복원한다 —
움직이는 `origin/main` 을 쓰지 않는다. `one-command-check.sh` 는 base 뒤로 움직이지 않았으므로
그 파일의 기준은 base 와 같고, **`ci.yml` 은 움직였으므로 병합된 main 커밋 SHA**를 쓴다(위 실측).
머지 뒤의 되돌림은 PR revert 가 먼저다.

아래 ①~⑦ 실측은 **머지 전 트리**에서 돌았다 — 그 시점에 `ci.yml` 의 base 복원이 아무것도
지우지 않는다(교집합 hunk 가 없다). 머지 뒤에는 같은 복원을 병합 커밋 SHA 기준으로 다시 잰다.

## 삭제 목록 — 새 파일 **스물일곱**

`web/src/display/`(7) · `web/src/screens/`(8) · `web/src/components/`(3) · `web/src/session/`(1) ·
`web/src/api/queries.ts` · `web/src/queryClient.ts` · `web/e2e/harness/`(3, fixture 포함) ·
`web/e2e/journey/journey.spec.ts` · `web/playwright.journey.config.ts` — 전수는 기계 산출 목록이
든다. 지운 뒤 **빈 디렉터리**를 함께 거둔다(git 은 그것을 추적하지 않는다).

## 실측 — 버릴 clone 에서 ①~⑥ (실측 HEAD `cd4a245a`)

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원·삭제 명령의 종료 코드 | `restore` **0** · `rm` **0** |
| ② | 복원·삭제 계수 | 복원 **19**(전부 `M`) · 삭제 **27**(전부 `A`) |
| ③ | 되돌린 트리가 base 와 같은가 | `git diff --quiet 78d97c59 -- web <공유 파일 둘>` **exit 0**. **트리 동일성**: 되돌린 index 의 `web` 하위 트리 해시가 base 의 그것과 **같다**(`cc5bdfc7…` == `cc5bdfc7…`) — 전체 트리 해시만 다르고 그 차이는 아래 ⑦이 센다 |
| ④ | compile | `npm ci` **0** · `typecheck` **0** |
| ⑤ | test | `vitest` **0** — 7A·7D 의 **5 파일 28 test** 로 돌아갔다. 이 slice 가 더한 **81 test**(새 파일 여섯 + 기존 test 파일 셋에 더한 줄)가 그만큼 사라졌다: 11 파일 109 → 5 파일 28 |
| ⑥ | 게이트 | `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` **전부 0**. 7A 의 셸 스모크도 **0**(2 통과) — 되돌린 뒤에도 7A 의 층이 그대로 선다 |
| ⑦ | 되돌린 뒤 base 와 남는 차이 전수 | **한 줄**: `A reports/evidence/m7/7e1/scope.md` — evidence 는 되돌림 대상이 아니다(내 표지 0 · 남의 표지 보존) |

`git status --porcelain` 은 되돌림 **직후** 46 줄이다(= 19 `M` + 27 `D`) — 그것이 기대값이고
「깨끗함」의 지표가 아니다. 이 라운드에서 재는 깨끗함은 ③의 **base 와의 diff 공집합**이다.

## 되돌리지 못하는 것 (없음)

외부 effect 0 · 마이그레이션 0 · 정책 데이터 변경 0 · 수집 호출 0(실 KONEPS 미호출) ·
알림 발송 0. 되돌림 뒤 운영자가 따로 할 일이 없다.
