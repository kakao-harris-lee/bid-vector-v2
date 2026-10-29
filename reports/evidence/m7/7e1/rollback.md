# M7/7E-1 — 되돌림

base **`78d97c59`**(2026-10-29 고정 — 착수 계약의 base 그대로다. 이 slice 의 base 는 움직이지
않았다).

**그러나 `main` 은 실측 뒤에 움직였다**(계수: 6 커밋, PR #101 caddy 직접 빌드). 그 가운데
**`.github/workflows/ci.yml` 이 교집합 한 건**이고 — 나머지 in_scope 경로와의 교집합은 0 이다 —
그 변경은 `container` job 의 주석 hunk 라 이 slice 가 바꾼 `web` job 과 겹치지 않는다(실측).
그래서 **공유 파일의 복원 기준은 base 가 아니라 「병합된 main 의 고정 SHA」다**(아래 복원 절) —
base 로 되돌리면 그 레인의 줄을 함께 지운다. 머지 순서 조정은 팀장 자리다.
**전제**: 그 SHA 에 이 slice 의 산출물이 **없다** — 첫 산출물 커밋이 그 뒤에 온다는 것을 아래
등식(복원 **22** 가 모두 `M`, 삭제 **36** 이 모두 `A`)과 되돌린 뒤 남는 차이가 **남의 경로와 이
slice 의 evidence** 뿐이라는 계수로 함께 본다.

**실측 HEAD**: `4fd61acc` — 머지 전 마지막 일괄(r5)의 **마지막 산출물 커밋**이다(그 뒤는 evidence
커밋뿐이고 evidence 는 아래 목록에 들어가지 않는다).

실측을 **여덟 번 돌렸고 앞 라운드의 값을 옮기지 않았다**(`cd4a245a` → `24fab9ef` → `8117a921` →
`ed827b22` → `aab938e7` → `8eb48df8` → `fb9cf54a` → `4fd61acc`). 세 번째는 병합이 `ci.yml` 에 남의 줄을 들여왔기 때문이고
— 그 라운드에서 **복원 기준이 둘로 갈렸다**(아래) — 그 뒤는 수정 라운드가 파일을 더했거나
**이미 있던 파일을 새로 건드렸기** 때문이다. 계수가 라운드마다 바뀌었다: 복원
19 → 20 → 21 → **22** · 삭제 27 → 31 → 34 → **36**(r5 는 둘 다 그대로다 — 고친 파일이 전부 이미 목록 안이었다). 라운드마다 재산출하는 이유가 이것이다.

**이 라운드의 재산출이 장부의 흠 하나를 드러냈다**: 앞 판은 계수를 **21** 로 적었는데 아래
복원 블록은 **스물**만 열거했다 — `web/src/api/client.ts` 가 빠져 있었다(계수만 기계
산출이고 블록은 손으로 적혀 둘이 갈렸다). 이 라운드는 블록을 기계
목록과 대조해 **양쪽 차집합이 빈 것**을 확인했다(그 대조가 이 흠을 잡았다).

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

## 복원 목록 — 수정된 파일 **스물둘** (공유 파일 하나는 병합된 main 기준)

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
  web/src/LoginScreen.tsx web/src/LoginScreen.test.tsx web/src/index.css \
  web/src/routes.tsx web/src/routes.test.ts \
  web/src/api/client.ts web/src/api/client.test.ts
```

전수는 **기계 산출 목록**이 든다(`git diff --name-status 78d97c59..HEAD -- web ci.yml one-command`).
라운드마다 더한 것을 **귀속대로** 적는다(CR r5 L-5 — 앞 판은 새 파일 하나를 이 복원 절
문맥에 두었다). **이 복원 목록(`M`)에 더해진 것**은 r1 의 `web/src/LoginScreen.tsx`(로그인
호출을 `queries.ts` 로 옮겼다)와 r3 의 `web/src/api/client.{ts,test.ts}`(승인 목록을
(메서드, 경로) 쌍으로 좁혔다)뿐이다. `web/playwright.journey.config.ts`(r2)와
`e2e/harness/bundle-shape.{mjs,test.mjs}`(r4)는 **새 파일**이라 아래 삭제 목록에 있다 —
**블록과 기계 목록의 차집합은 양쪽 다 비어 있다**(⓪ 행에서 실측).

**복원 기준이 둘이다**(병합 라운드 뒤):

- 내 파일 **스물하나**는 `--source=78d97c59`(base).
- 공유 파일 **`.github/workflows/ci.yml`** 은 `--source=bb6a187e`(**병합된 main 의 고정 SHA**) —
  병합이 그 파일에 남의 줄(caddy 레인)을 들여왔으므로 base 로 되돌리면 그 줄을 함께 지운다.
  움직이는 `origin/main` 을 쓰지 않는다.
- `tools/one-command-check.sh` 는 base 뒤로 움직이지 않았으므로 base 기준이다.

머지 뒤의 되돌림은 PR revert 가 먼저다.

## 삭제 목록 — 새 파일 **서른여섯**

디렉터리별 **기계 계수**다(요약이 합과 어긋나지 않게 계수에서 뽑았다 — v r2 L-3):
`web/src/screens/`(8) · `web/src/display/`(8) · `web/e2e/harness/`(6, `fixtures/`(1) 별도) ·
`web/src/components/`(4) · `web/src/api/`(3) · `web/scripts/`(2) · `web/src/session/`(1) ·
`web/src/`(1) · `web/e2e/journey/`(1) · `web/`(1). 전수는 기계 산출 목록이 든다. 지운 뒤 **빈
디렉터리**를 함께 거둔다(git 은 그것을 추적하지 않는다).

r1·r2 가 더한 일곱: `apiError.ts` · `WriteFailure.tsx` · `screen-operations.{mjs,test.mjs}` ·
`approvedOperations.ts` · `container-marker.{mjs,test.mjs}`. r4 가 더한 둘:
`e2e/harness/bundle-shape.{mjs,test.mjs}`(자산 모양 술어와 그 잠금).

## 실측 — 버릴 clone 에서 ①~⑦ (실측 HEAD 는 위 머리의 값이다)

| # | 무엇 | 결과 |
| --- | --- | --- |
| ⓪ | **장부 블록 ↔ 기계 목록** | 장부 22 · 기계 22 · **양쪽 차집합 빈 출력**(r5 재측정에서도 같다). 이 행은 r4 에서 실측 스크립트 안으로 들어왔다 — r3 이 손 목록의 흠(한 자리 누락)을 잡은 뒤, 목록을 기계에서 뽑고 장부와 대조하는 것을 **측정의 일부**로 뒀다 |
| ① | 복원·삭제 명령의 종료 코드 | `restore`(base, 21개) **0** · `restore`(병합된 main, 1개) **0** · `rm`(36개) **0** |
| ② | 복원·삭제 계수 | 복원 **22**(전부 `M`) · 삭제 **36**(전부 `A`) · porcelain 58(= 22 `M` + 36 `D`) |
| ③ | 되돌린 트리가 기준과 같은가 | **트리 해시로 본다**(종료 코드가 아니다): 삭제를 index 에 반영한 뒤 `write-tree` 의 **`web` 하위 트리 == base** 의 그것(`cc5bdfc7…`) · `ci.yml` blob **== 병합된 main** 의 그것 · `tools/one-command-check.sh` blob **== base** 의 그것. **「내 표지 0 · 남의 표지 보존」**: 내 표지(`W-12`·`W-13`·`test:e2e:journey`·`여정 e2e`) **각각 0** · 남의 표지(caddy) **2** |
| ④ | compile | `npm ci` **0** · `typecheck` **0** |
| ⑤ | test | `vitest` **0** — 7A·7D 의 **5 파일 28 test** 로 돌아갔다. 이 slice 가 더한 **196 test** 가 그만큼 사라졌다(14 파일 224 → 5 파일 28) |
| ⑥ | 게이트 | `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` **전부 0**. 7A 의 셸 스모크도 **0**(2 통과) |
| ⑦ | 되돌린 뒤 base 와 남는 차이 | **내 산출물 경로 0 건**. 전체 24 건은 병합된 main 이 들여온 남의 경로(caddy 레인 · 그 레인의 evidence · `ci.yml`)와 이 slice 의 evidence 넷이다 |

**실측 스크립트의 흠 하나를 r3 에서 고쳤다**: ③ 을 처음엔 `git ls-tree HEAD web` 으로 쟀는데
그것은 **HEAD 커밋의 트리**라 되돌림과 무관하게 다르다(삭제가 worktree 에만 반영돼 index 와
갈린다). 되돌림의 트리는 삭제를 index 에 반영한 뒤 `write-tree` 로 봐야 한다. 「초록이니
같다」가 아니라 **해시로** 본다는 규율이 그 자리에서 값을 했다.

`git status --porcelain` 은 되돌림 **직후** 58 줄이다(= 22 `M` + 36 `D`) — 그것이 기대값이고
「깨끗함」의 지표가 아니다. 이 라운드에서 재는 깨끗함은 ③의 **트리 해시 동일성**이다.

## 되돌리지 못하는 것 (없음)

외부 effect 0 · 마이그레이션 0 · 정책 데이터 변경 0 · 수집 호출 0(실 KONEPS 미호출) ·
알림 발송 0. 되돌림 뒤 운영자가 따로 할 일이 없다.
