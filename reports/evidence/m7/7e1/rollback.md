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

**실측 HEAD**: `8117a921` — **main 병합 커밋**이고 이 slice 의 마지막 산출물 커밋이다(그 뒤는
evidence 커밋뿐이고 evidence 는 아래 목록에 들어가지 않는다).

실측을 **세 번 돌렸고 앞 라운드의 값을 옮기지 않았다**(`cd4a245a` → `24fab9ef` → `8117a921`).
세 번째는 병합이 `ci.yml` 에 남의 줄을 들여왔기 때문이고, 그 라운드에서 **복원 기준이 둘로
갈렸다**(아래). 계수(복원 19 · 삭제 27)는 세 라운드가 같지만 그것은 **재산출해 보아야 아는
것**이다.

목록은 **in_scope 경로로 한정해** 산출한다(`-- web .github/workflows/ci.yml
tools/one-command-check.sh`) — 병합이 들여온 남의 경로(docker · 이미지·취약점 정책)는 이 slice 의
변경이 아니므로 되돌림 대상이 **아니다**(그 경로 포함 0 건을 계수로 확인했다).

목록은 **기계 산출**이다: `git diff --name-status 78d97c59..HEAD`(evidence 제외). 라운드마다
재산출한다.

## 되돌림의 경로는 하나다 (마이그레이션·외부 상태가 없다)

이 slice 는 **표를 만들지 않고 외부에 아무것도 보내지 않는다** — 화면 코드·설정·게이트 술어와
e2e 하네스뿐이다. 그래서 되돌림이 배포 전/후로 갈리지 않고 **파일을 되돌리는 것으로 끝난다**.

여정 e2e 가 띄우는 것(버릴 Postgres 컨테이너 · fake KONEPS · 출하 jar 프로세스)은 **실행 중에만**
있고 하네스가 거둔다 — 되돌릴 상태가 남지 않는다(잔존 계수는 `commands.md`).

## 복원 목록 — 수정된 파일 **열아홉** (base 로 복원)

```
# 공유 파일은 병합된 main 고정 SHA 에서(남의 줄을 지우지 않는다)
git restore --source=bb6a187e --staged --worktree -- .github/workflows/ci.yml

git restore --source=78d97c59 --staged --worktree -- \
  tools/one-command-check.sh \
  web/config/gate-tests.txt web/eslint.config.js web/package.json web/package-lock.json \
  web/playwright.config.ts web/tsconfig.json web/vite.config.ts \
  web/scripts/check-src-tree.mjs web/scripts/module-graph-boundary.mjs \
  web/scripts/module-graph-boundary.test.mjs \
  web/src/AppRoot.tsx web/src/AppRoot.test.tsx web/src/AppShell.tsx \
  web/src/LoginScreen.test.tsx web/src/index.css web/src/routes.tsx web/src/routes.test.ts
```

**복원 기준이 둘이다**(병합 라운드 뒤):

- 내 파일 **열여덟**은 `--source=78d97c59`(base).
- 공유 파일 **`.github/workflows/ci.yml`** 은 `--source=bb6a187e`(**병합된 main 의 고정 SHA**) —
  병합이 그 파일에 남의 줄(caddy 레인)을 들여왔으므로 base 로 되돌리면 그 줄을 함께 지운다.
  움직이는 `origin/main` 을 쓰지 않는다.
- `tools/one-command-check.sh` 는 base 뒤로 움직이지 않았으므로 base 기준이다.

머지 뒤의 되돌림은 PR revert 가 먼저다.

## 삭제 목록 — 새 파일 **스물일곱**

`web/src/display/`(7) · `web/src/screens/`(8) · `web/src/components/`(3) · `web/src/session/`(1) ·
`web/src/api/queries.ts` · `web/src/queryClient.ts` · `web/e2e/harness/`(3, fixture 포함) ·
`web/e2e/journey/journey.spec.ts` · `web/playwright.journey.config.ts` — 전수는 기계 산출 목록이
든다. 지운 뒤 **빈 디렉터리**를 함께 거둔다(git 은 그것을 추적하지 않는다).

## 실측 — 버릴 clone 에서 ①~⑥ (실측 HEAD `cd4a245a`)

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원·삭제 명령의 종료 코드 | `restore`(base, 18개) **0** · `restore`(병합된 main, 1개) **0** · `rm` **0** |
| ② | 복원·삭제 계수 | 복원 **19**(전부 `M`) · 삭제 **27**(전부 `A`) |
| ③ | 되돌린 트리가 기준과 같은가 | 내 파일: `git diff --quiet 78d97c59 -- <18개>` **exit 0** · 공유 파일: `git diff --quiet bb6a187e -- ci.yml` **exit 0**. **「내 표지 0 · 남의 표지 보존」 계수**: 내 표지(`W-12`·`W-13`·`test:e2e:journey`·`여정 e2e`) **각각 0** · 남의 표지(caddy 모듈 출처) **1**. 느슨한 패턴(`앱 배포물 빌드`)은 `container` job 의 기존 step 과 겹쳐 1 을 센다 — 그것은 **남의 줄**이고 표지로 쓰지 않는다. 앞 라운드의 **트리 동일성**(되돌린 index 의 `web` 하위 트리 해시 == base 의 그것, `cc5bdfc7…`)도 그대로다 |
| ④ | compile | `npm ci` **0** · `typecheck` **0** |
| ⑤ | test | `vitest` **0** — 7A·7D 의 **5 파일 28 test** 로 돌아갔다. 이 slice 가 더한 **81 test**(새 파일 여섯 + 기존 test 파일 셋에 더한 줄)가 그만큼 사라졌다: 11 파일 109 → 5 파일 28 |
| ⑥ | 게이트 | `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` **전부 0**. 7A 의 셸 스모크도 **0**(2 통과) — 되돌린 뒤에도 7A 의 층이 그대로 선다 |
| ⑦ | 되돌린 뒤 base 와 남는 차이 전수 | **한 줄**: `A reports/evidence/m7/7e1/scope.md` — evidence 는 되돌림 대상이 아니다(내 표지 0 · 남의 표지 보존) |

`git status --porcelain` 은 되돌림 **직후** 46 줄이다(= 19 `M` + 27 `D`) — 그것이 기대값이고
「깨끗함」의 지표가 아니다. 이 라운드에서 재는 깨끗함은 ③의 **base 와의 diff 공집합**이다.

## 되돌리지 못하는 것 (없음)

외부 effect 0 · 마이그레이션 0 · 정책 데이터 변경 0 · 수집 호출 0(실 KONEPS 미호출) ·
알림 발송 0. 되돌림 뒤 운영자가 따로 할 일이 없다.
