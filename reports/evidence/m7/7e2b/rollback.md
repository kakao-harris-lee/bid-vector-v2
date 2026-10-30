# M7/7E-2b — 되돌림

base **`1e387499`**(착수 계약의 base 그대로 — 이 slice 의 base 는 움직이지 않았다).
**전제**: 그 SHA 에 이 slice 가 없다 — `git diff --name-status 1e387499..HEAD -- <in_scope>` 가
첫 산출물 커밋 시점에 `reports/evidence/m7/7e2b/scope.md` 한 줄(착수 계약)뿐이었다.

**공유 파일의 둘째 갈래가 없다.** `.github/workflows/ci.yml` 과 `tools/one-command-check.sh` 를
**고치지 않았다**(기계 확인: `git diff --name-only 1e387499..HEAD -- .github tools` → **0 건**).
여정 ① 이 **이미 있는 명령**(`npm run test:e2e:journey`)의 수집 범위 안에서 돌기 때문이다 —
설정 파일의 `testDir` 가 `e2e/journey` 전부를 집으므로 CI step 을 더할 필요가 없었다.
그리고 이 브랜치는 **main 을 병합하지 않았다** — base 가 곧 main 이므로 복원 기준이 하나다.

다른 slice 와 겹치는 파일 넷(`web/config/gate-tests.txt` · `web/e2e/journey/shared.ts` ·
`web/src/api/queries.ts` · `docs/discovery/m7-screen-data-requirements.md`)은 **남의 줄이 전부
base 안에** 있다. 그래서 base 복원이 남의 줄을 떨어뜨리지 않는다 — hunk 격리가 필요 없고,
그 사실을 ③ 의 「남의 표지」 계수로 잰다.

**실측 HEAD**: `1ff961f2` — 이 slice 의 **마지막 산출물 커밋**이다. 그 뒤 커밋은 evidence 뿐이고
evidence 경로는 아래 목록 **밖**이다. 라운드가 더 붙어 목록 안 파일이 움직이면 이 절차를 다시
돌린다(목록이 낡는 것이 이 결함의 실제 원인이다).

**이 slice 에서 실제로 세 번 낡았다.** ① `3354143d` 에서 ⓪~⑦ 을 다 재고 나서 여정에 개인정보 단언
한 줄을 더했고(`2acd2439`), 그 파일이 **삭제 목록 안**이라 앞 실측이 무효가 됐다. ② r1 승인 전 일괄이
산출물을 더 고쳐(`86dfcbfd` · `fcab9df4` · `2cdcb9fa`) 또 무효가 됐다. ③ r2 가 MEDIUM 회귀를 고치며
또 고쳤다(`1ff961f2`). 세 번 다 값을 옮기지 않고 새 HEAD 에서 전부 다시 쟀다 — 네 번 모두 계수가
같았지만(M 11 · A 12) **같다는 것은 측정 뒤에 아는 사실이다**. r1·r2 모두 **새 파일을 더하지 않고**
목록 안의 파일만 고쳤다(`git diff --name-status 2cdcb9fa..1ff961f2 -- web` 가 `M` 일곱).

목록은 **기계 산출**이다: `git diff --name-status 1e387499..HEAD -- web
docs/discovery/m7-screen-data-requirements.md .github tools`.

## 되돌림의 경로는 하나다 (마이그레이션·외부 상태가 없다)

화면 코드·게이트 술어·e2e spec 뿐이다 — 표를 만들지 않고 외부에 아무것도 보내지 않는다.
OpenAPI 와 Kotlin `src/main` 은 **읽기만** 했다. 여정이 띄우는 것(버릴 Postgres · fake KONEPS ·
출하 jar)은 실행 중에만 있고 하네스가 거둔다.

여정 ① 이 전략·프로필을 바꾸지만 되돌릴 상태가 아니다: 그 DB 는 실행마다 버려지고, 여정 자신이
끝에서 감시 조건을 비우며 `afterEach` 가 그 되돌림을 **단언**한다.

## 복원 목록 — 수정된 파일 **11** (전부 base 기준)

```
git restore --source=1e387499 --staged --worktree -- \
  docs/discovery/m7-screen-data-requirements.md \
  web/config/gate-tests.txt \
  web/e2e/journey/shared.ts \
  web/src/api/approvedOperations.ts \
  web/src/api/queries.ts \
  web/src/routes.test.ts \
  web/src/routes.tsx \
  web/src/screens/ProfileSection.tsx \
  web/src/screens/SettingsScreen.tsx \
  web/src/screens/StrategySection.tsx \
  web/src/screens/TodayNoticesScreen.tsx
```

## 삭제 목록 — 새 파일 **12**

```
rm -f \
  web/e2e/journey/onboarding.spec.ts \
  web/scripts/onboarding-vocabulary.mjs \
  web/scripts/onboarding-vocabulary.test.mjs \
  web/src/display/onboardingDisplay.test.ts \
  web/src/display/onboardingDisplay.ts \
  web/src/screens/OnboardingScreen.test.tsx \
  web/src/screens/OnboardingScreen.tsx \
  web/src/screens/ProfileCapacityCard.tsx \
  web/src/screens/ProfileDetailsCard.tsx \
  web/src/screens/ProfileFactsCard.tsx \
  web/src/screens/profileWrites.tsx \
  web/src/screens/strategyEdit.tsx
```

## 실측 — 버릴 clone 에서 ⓪~⑦ (실측 HEAD 는 위 머리의 값이다)

| # | 무엇 | 결과 |
| --- | --- | --- |
| ⓪ | **장부 블록 ↔ 기계 목록** | 장부 M 11 · 기계 M 11 · 장부 A 12 · 기계 A 12. 위 두 블록은 기계 출력을 그대로 옮긴 것이고 라운드마다 다시 쓴다 |
| ① | 복원·삭제 명령의 종료 코드 | `restore`(11개) **0** · `rm`(12개) **0** |
| ② | 복원·삭제 계수 | porcelain **23** = 11 `M` + 12 `D` — 되돌림 **직후**의 기대값이고 「깨끗함」의 지표가 아니다 |
| ③ | 되돌린 트리가 기준과 같은가 | **트리 해시로 본다**(종료 코드가 아니다): 되돌린 `web` 하위 트리 == base 의 그것 — **동일**. 요구표 blob 도 base 와 **동일**. **내 표지 열둘**(`OnboardingScreen`·`onboardingDisplay`·`profileWrites`·`strategyEdit`·`ProfileFactsCard`·`ProfileDetailsCard`·`ProfileCapacityCard`·`onboarding-vocabulary`·`ONBOARDING_ITEM_ORDER`·`OnboardingBanner`·`clearStrategyTerms`·slice 이름) 각각 **0**. **남의 표지는 남았다**: 7E-2 의 `bidBoxDisplay` **5 파일** · `CASE_TAB_LABEL` **2 파일** · 7C④-3 이 요구표에 쓴 operation 이름 **1 건** |
| ④ | compile | `npm ci` **0** · `typecheck` **0** |
| ⑤ | test | `vitest` **0** — 7A·7D·7E-1·7E-2 의 **406 test** 로 돌아갔다. 이 slice 가 더한 **58 test**(파일 셋)가 그만큼 사라졌다(464 → 406) |
| ⑥ | 게이트 | `lint` · `gate:src-tree` · `api:types:check` · `gate:tests` · `build` · `gate:boundary-canary` **전부 0** · 7A 의 셸 스모크 **0** |
| ⑦ | 되돌린 뒤 base 와 남는 차이 | **in_scope 경로 0 건.** 전체로 남는 것은 `reports/evidence/m7/7e2b/` 넷뿐이다(evidence 는 목록 밖 — 위 머리의 사유) |

**왜 ③ 을 해시로 재는가**: 「되돌린 뒤 게이트가 초록」은 되돌림의 증거가 아니다 — 내 파일이 남아
있어도 초록일 수 있다. 트리 해시 동일성은 **파일 집합과 내용이 base 와 같다**는 직접 계수이고,
그 위에 「내 표지 0 · 남의 표지 보존」을 함께 둬 무엇이 사라지고 무엇이 남았는지까지 센다.

**첫 실측은 하네스 탓으로 흐려졌고 그대로 적는다**: clone 의 `web/node_modules` 를 **심링크**로
두었더니 ⓐ `gate:src-tree` 가 **exit 1**(그 게이트가 `web/` 아래 심링크 0 을 잰다) ⓑ 심링크는
`node_modules/` 무시 규칙(디렉터리)에 걸리지 않아 **`web` 트리 해시까지** 달라졌다. 둘 다 되돌림의
결함이 아니라 측정 장치의 결함이다 — `npm ci` 로 바꿔 다시 쟀고 위 표가 그 값이다. (게이트가 실제로
심링크를 잡는다는 양성 대조이기도 하다.)

## 되돌리지 못하는 것 (없음)

외부 effect 0 · 마이그레이션 0 · 정책 데이터 변경 0 · 수집 호출 0(실 KONEPS 미호출) · 알림 발송 0 ·
OpenAPI 편집 0(읽기만) · 승인 목록은 파일 한 줄이라 복원으로 함께 걷힌다. 되돌림 뒤 운영자가 따로
할 일이 없다.

되돌리면 화면 A 자리가 **「준비 중」 빈 자리로 돌아가고**(7A 의 `ScreenPlaceholder`) 홈의 진입 배너가
사라진다. 설정 화면은 7E-2 의 모양 그대로 선다 — 리팩터도 함께 되돌아가므로 쓰기 실패 얼굴이 다시
프로필 절 머리 하나가 된다(`checklist.md` 알려진 제한 1).
