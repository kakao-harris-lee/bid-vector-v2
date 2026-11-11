# M10 / 10B — rollback

실측 HEAD: `326acf37` — 이 slice 의 **마지막 산출물 커밋**이다. 뒤의 커밋은 evidence 뿐이고
**복원 목록에 evidence 가 없으므로**(아래) 그 커밋들이 이 실측을 낡게 만들지 않는다.

복원 원천: **base `8062dcd784cf81905c50a2c8706d8bbd3ea5263e`**.

## 전제 — 실측으로 확인

- base 는 HEAD 의 **조상**이다(`git merge-base --is-ancestor` exit 0).
- **이 브랜치는 main 을 병합하지 않았다**(`git log --merges HEAD ^base` 빈 출력). 그래서 「합친
  main 의 고정 SHA」가 곧 base 이고, 움직이는 ref(`origin/main`)를 쓰지 않는다.
- base 는 이 slice 의 **첫 산출물을 담지 않는다**: 새 계약 test 파일이 없고(`git cat-file -e`
  실패), base 의 계약 문서에 명령 enum 표지가 **0** 이다.
- 하네스 레인 변경 **없음**(`git log --oneline base..HEAD -- CLAUDE.md .claude/` 빈 출력) —
  되돌리지 않을 하네스 경로가 애초에 없다.
- **머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다 — 머지 순간 같은 복원은
  아무것도 지우지 않으면서 exit 0 을 낸다.

## 복원 목록 — 기계 산출

손으로 쓰지 않는다. 두 산출이 **같은 집합**이다(`comm -23`·`comm -13` 둘 다 빈 출력):

```
git diff --name-status <base>..HEAD
git log --no-merges --format= --name-only HEAD ^<base> | sort -u
```

산출은 10 경로이고 그 가운데 **이 slice 의 evidence 디렉터리
(`reports/evidence/m10/timeline-command-enum/**`)는 되돌리지 않는다** — 기록은 남긴다. 그래서
목록에서 뺀다(**9 경로**). 이 분리가 위 「실측 HEAD」를 유효하게 유지하는 장치이기도 하다.
**라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

신규 파일은 원천에 없으므로 삭제된다(`--source` 에 없는 경로는 삭제 — 별도 `git rm` 불필요).

```
git restore --source=<base> --staged --worktree -- \
  app/src/test/kotlin/bidvector/app/http/OpenApiBidCaseContractTest.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiSpecSupport.kt \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  openapi/bidvector-operator-api.yaml \
  web/scripts/money-vocab-cross-lang.test.mjs \
  web/src/api/generated/operator-api.ts \
  web/src/display/caseDisplay.test.ts \
  web/src/display/caseDisplay.ts
```

**공유 파일에 hunk 격리가 필요 없다** — 이 브랜치에서 그 파일들을 만진 것은 내 커밋뿐이고
(`git log --oneline base..HEAD -- <파일>`), 원천이 main 커밋이라 남의 줄을 떨어뜨릴 수 없다.

## ①~⑥ 실측 — 버릴 clone, 실측 HEAD 에서

① 복원 명령 exit **0** · ② `git status --porcelain` 계수 **D 1 · M 8** · ③ 복원한 9 경로의
`git diff <base>` **0 줄** · ④⑤⑥ 되돌린 트리에서 `./gradlew --no-daemon check` exit **0**
(BUILD SUCCESSFUL 35m 34s — 컴파일·test·Kotlin 게이트가 한 명령에 든다) · ⑥ web 게이트 여덟
(`lint`·`typecheck`·`gate:src-tree`·`api:types:check`·`test`·`gate:tests`·`build`·
`gate:boundary-canary`) 전부 exit **0**.

**완료 판정은 종료 코드가 아니라 계수다.** 되돌린 트리에서(evidence 제외):

- **내 표지 0** — 신설 OPEN ID · 새 test 클래스 이름 · 공용 헬퍼 이름 · 이 slice 표기, 넷 다
  매치 파일 **0**.
- **남의 표지 보존** — §12.3 하위 절 (a)~(e) **5** · 게이트 등록부의 계약 test 등재 **14**
  (내 한 줄을 뺀 수) · 교차언어 게이트의 금액 축 블록 **1** · evidence 디렉터리 보존.

## 측정 함정 하나 — 기록

첫 ⑥ 실측에서 `gate:src-tree` 가 exit 1 이었고, **원인은 rollback 의 미완이 아니라 내 측정
설정**이었다: `npm ci` 를 아끼려고 clone 의 `web/node_modules` 를 symlink 로 뒀는데 그 게이트가
「`web/` 안 심볼릭 링크 금지」를 단언한다. 실 디렉터리로 다시 설치해 재측정하니 여덟 전부 exit 0
이다. 위 ⑥ 수치는 **재측정 값**이다. (변이 실측 쪽 clone 도 symlink 를 썼지만 거기서 돌린 것은
`typecheck`·`api:types:check` 둘이라 그 술어에 닿지 않는다.)
