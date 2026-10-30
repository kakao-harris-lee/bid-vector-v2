# M7/7E-2 — 되돌림

base **`fa5ca096`**(착수 계약의 base 그대로 — 이 slice 의 base 는 움직이지 않았다).

**공유 파일이 없다.** `.github/workflows/ci.yml` 과 `tools/one-command-check.sh` 를 **고치지
않았고**(기계 확인: `git diff --name-only fa5ca096..HEAD -- .github tools` → **0 건**), 그래서
7E-1 이 썼던 「공유 파일은 병합된 main 고정 SHA 에서 복원」 갈래가 이 slice 에는 **없다**.
복원 기준이 하나(base)다. 그렇게 된 이유는 여정 둘이 **이미 있는 명령**
(`npm run test:e2e:journey`)의 수집 범위 안에서 돌기 때문이다 — 그 명령이 설정 파일의 `testDir`
전부를 집으므로 CI step 을 더할 필요가 없었다.

**`main` 은 그 뒤 움직였다**(base..`87cf6a06` 20 커밋, M7/7C②-b). 그 가운데 이 slice 의
in_scope 와 겹치는 것은 **`web/src/api/generated/operator-api.ts` 하나**이고 이 slice 는 그
파일을 **읽기만 했다**(복원·삭제 목록에 없다). 그래서 병합이 되돌림의 기준을 바꾸지 않는다.

**실측 HEAD**: `ac12aebd`. 이 slice 의 **마지막 산출물 커밋은 `16c4384f`** 이고 그 뒤
(`16c4384f..ac12aebd`)의 **산출물 경로 변경이 0 건**이라, 어느 쪽을 기준으로 봐도 되돌림 대상이
움직이지 않았다(그 뒤는 evidence 커밋뿐이고 evidence 는 아래 목록에 들어가지 않는다).

**실측을 세 번 돌렸고 앞 값을 옮기지 않았다**(`d9e19097` → `c89219a6` → `ac12aebd`). 둘째는
등록부(`web/config/gate-tests.txt` — 복원 목록 안의 파일)에 한 줄이 더해져서, 셋째는 **r1 수정
라운드**가 파일을 더하고 고쳐서다. 계수가 라운드마다 바뀌었다: 복원 11 → 11 → **13** · 삭제
16 → 16 → **17**(새 파일은 `web/src/api/queryKeys.ts` 하나, 고쳐진 둘은 `TodayNoticesScreen`·
`WorksheetScreen` — 질의 키를 한 자리로 모으며 7E-1 의 화면 둘도 그 함수를 쓰게 됐다).
**라운드마다 재산출하는 이유가 이것이다.**

목록은 **기계 산출**이다: `git diff --name-status fa5ca096..HEAD -- web .github tools`
(evidence 제외). 라운드마다 재산출한다.

## 되돌림의 경로는 하나다 (마이그레이션·외부 상태가 없다)

이 slice 는 **표를 만들지 않고 외부에 아무것도 보내지 않는다** — 화면 코드·게이트 술어·e2e
하네스와 fixture 뿐이다. 그래서 되돌림이 배포 전/후로 갈리지 않고 **파일을 되돌리는 것으로
끝난다**. 여정이 띄우는 것(버릴 Postgres · fake KONEPS · 출하 jar 프로세스)은 실행 중에만 있고
하네스가 거둔다(잔존 계수 0 — `commands.md`).

설정 여정이 **전략을 바꾸지만** 그것도 되돌릴 상태가 아니다: 여정이 쓰는 DB 는 실행마다
버려지고, 여정 자신이 끝에서 감시 조건을 비운다.

## 복원 목록 — 수정된 파일 **열셋** (전부 base 기준)

```
git restore --source=fa5ca096 --staged --worktree -- \
  web/config/gate-tests.txt \
  web/e2e/harness/bundle-shape.mjs \
  web/e2e/harness/bundle-shape.test.mjs \
  web/e2e/harness/fixtures/notice-list.json \
  web/e2e/harness/journey-up.mjs \
  web/e2e/journey/journey.spec.ts \
  web/scripts/check-src-tree.mjs \
  web/src/api/approvedOperations.ts \
  web/src/api/queries.ts \
  web/src/routes.test.ts \
  web/src/routes.tsx \
  web/src/screens/TodayNoticesScreen.tsx \
  web/src/screens/WorksheetScreen.tsx
```

## 삭제 목록 — 새 파일 **열일곱**

```
rm -f \
  web/e2e/journey/settings.spec.ts \
  web/src/api/queryKeys.ts \
  web/e2e/journey/shared.ts \
  web/src/display/bidBoxDisplay.test.ts \
  web/src/display/bidBoxDisplay.ts \
  web/src/display/editSessionDisplay.test.ts \
  web/src/display/editSessionDisplay.ts \
  web/src/display/profileDisplay.test.ts \
  web/src/display/profileDisplay.ts \
  web/src/screens/BidBoxCard.tsx \
  web/src/screens/BidBoxScreen.test.tsx \
  web/src/screens/BidBoxScreen.tsx \
  web/src/screens/ProfileSection.test.tsx \
  web/src/screens/ProfileSection.tsx \
  web/src/screens/SettingsScreen.test.tsx \
  web/src/screens/SettingsScreen.tsx \
  web/src/screens/StrategySection.tsx
```

## 실측 — 버릴 clone 에서 ⓪~⑦ (실측 HEAD 는 위 머리의 값이다)

| # | 무엇 | 결과 |
| --- | --- | --- |
| ⓪ | **장부 블록 ↔ 기계 목록** | 장부 M 13 · 기계 M 13 · 장부 A 17 · 기계 A 17 · **네 차집합 전부 빈 출력**(양방향 M·A). 이 대조가 측정의 일부다 — 계수만 기계이고 블록이 손이면 둘이 갈린다(7E-1 r3 이 그 흠을 잡았다) |
| ① | 복원·삭제 명령의 종료 코드 | `restore`(13개) **0** · `rm`(17개) **0** |
| ② | 복원·삭제 계수 | 복원 **13**(전부 `M`) · 삭제 **17**(전부 `A`) · porcelain **30**(= 13 `M` + 17 `D`) |
| ③ | 되돌린 트리가 기준과 같은가 | **트리 해시로 본다**(종료 코드가 아니다): 삭제를 index 에 반영한 뒤 `write-tree` 의 `web` 하위 트리 == **base 의 그것**(`f37d3922…`) — 동일. **내 표지 열둘**(`BidBoxScreen`·`SettingsScreen`·`bidBoxDisplay`·`editSessionDisplay`·`profileDisplay`·`StrategySection`·`ProfileSection`·`assertDistRoot`·`specifierResolvesTo`·`SYN-7E2-BIDBOX`·`strategy-row`·`settings.spec`) **각각 0**. **남의 표지 축은 비어 있다** — 공유 파일 변경이 0 건이라 되돌릴 경로에 남의 줄이 없다 |
| ④ | compile | `npm ci` **0** · `typecheck` **0** |
| ⑤ | test | `vitest` **0** — 7A·7D·7E-1 의 **14 파일 230 test** 로 돌아갔다. 이 slice 가 더한 **164 test**(6 파일)가 그만큼 사라졌다(20 파일 394 → 14 파일 230) |
| ⑥ | 게이트 | `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` **전부 0** · 7A 의 셸 스모크 **0** |
| ⑦ | 되돌린 뒤 base 와 남는 차이 | **in_scope 경로 0 건.** 전체로 남는 **다섯**은 전부 evidence 다 — 이 slice 의 넷(`scope.md`·`checklist.md`·`commands.md`·이 파일)과 7E-1 checklist 의 종결 표시(evidence 는 목록 밖 — 위 머리의 사유) |

**왜 ③ 을 해시로 재는가**: 「되돌린 뒤 게이트가 초록」은 되돌림의 증거가 아니다 — 내 파일이
남아 있어도 초록일 수 있다. 트리 해시 동일성은 **파일 집합과 내용이 base 와 같다**는 직접
계수이고, 그 위에 「내 표지 0」을 함께 둬 **무엇이 사라졌는지**까지 센다.

`git status --porcelain` 은 되돌림 **직후** 30 줄이다(= 13 `M` + 17 `D`) — 그것이 기대값이고
「깨끗함」의 지표가 아니다. 이 라운드에서 재는 깨끗함은 ③ 의 **트리 해시 동일성**이다.

## 되돌리지 못하는 것 (없음)

외부 effect 0 · 마이그레이션 0 · 정책 데이터 변경 0 · 수집 호출 0(실 KONEPS 미호출) · 알림
발송 0 · OpenAPI 편집 0(읽기만). 되돌림 뒤 운영자가 따로 할 일이 없다.

되돌리면 **`OPEN-7E-GATE-RESIDUAL` 다섯이 다시 열린다**(게이트 술어가 함께 되돌아간다). 그
사실은 7E-1 evidence 의 그 절이 다시 유효해지는 것으로 읽는다.
