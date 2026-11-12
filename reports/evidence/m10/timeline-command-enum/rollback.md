# M10 / 10B — rollback

실측 HEAD: `55f6df7c` — 이 slice 의 **마지막 산출물 커밋**이다. main 병합은 그보다 앞에 있고
(`git rev-list --merges <실측 HEAD>..<판정 SHA>` 가 빈 출력이다), 뒤의 커밋은 evidence 뿐이다.
**복원 목록에 evidence 가 없으므로**(아래) 그 커밋들이 실측을 낡게 하지 않는다.

복원 원천: **`af9742d2ffda129932bbb541f727790caf08d188`** — 이 브랜치가 실제로 병합한 main 의 고정
SHA(PR #146·#147·#148 머지분). base `8062dcd7` 로 되돌리면 그 사이 main 에 들어온 **남의 변경**
(§12.3 (f) · 하네스 규칙 · `milestone-10.md` 등 10 경로)까지 지운다. 움직이는 ref 는 쓰지 않는다.

## 전제 — 실측으로 확인

- base 와 원천 둘 다 **실측 HEAD 의 조상**이고, 원천에 이 slice 의 산출물이 **없다**(새 계약 test
  파일 부재 · 계약 문서의 명령 enum 표지 0 · `propertyEnum` 선언 0).
- **병합은 하나뿐**이다 — `git rev-list --merges <원천>..<실측 HEAD>` 가 내 병합 커밋 하나를 낸다.
- **원천 교체의 셋째 단언**: 복원 9 경로의 blob 이 base 와 원천에서 같은가
  (`git rev-parse -q --verify <sha>:<path> || echo ABSENT`) → **여덟은 같고 하나가 다르다**. 다른
  하나가 `docs/discovery/capability-map.md` 이고 그것이 **원천을 교체해야 하는 이유**다(main 의
  (f) 가 그 파일에 있다 — base 로 되돌리면 그 절이 사라진다). 나머지 여덟은 교체가 무영향이다.
- **머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다 — 머지 순간 같은 복원은
  아무것도 지우지 않으면서 exit 0 을 낸다.
- 하네스 경로는 되돌리지 않는다. 내 하네스 편집은 0 이고, range 의 하네스 커밋 아홉은 main
  것이다(`scope.md` 하네스 레인 절).

## 복원 목록 — 기계 산출

손으로 쓰지 않는다. 목록의 **양끝이 원천과 실측 HEAD** 다:

```
git log --no-merges --format= --name-only <실측 HEAD> ^<원천> | sort -u
```

산출은 13 경로이고 그 가운데 **이 slice 의 evidence 디렉터리
(`reports/evidence/m10/timeline-command-enum/**`)는 되돌리지 않는다** — 기록은 남긴다. 그래서
목록에서 뺀다(**9 경로**). 이 분리가 「실측 HEAD」를 유효하게 유지하는 장치이기도 하다.
**라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

신규 파일은 원천에 없으므로 삭제된다(`--source` 에 없는 경로는 삭제 — 별도 `git rm` 불필요).

```
git restore --source=<원천> --staged --worktree -- \
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

**hunk 격리가 필요 없다** — 이 브랜치에서 그 파일들을 만진 것은 내 커밋뿐이고, 원천이 main 커밋이라
남의 줄을 떨어뜨릴 수 없다. §12.3 도 예외가 아니다: main 의 (f) 는 **원천에 이미 있으므로** 복원이
그것을 되살린다(위 셋째 단언의 「다른 하나」).

## ①~⑥ 실측 — 버릴 clone, 실측 HEAD 에서

① 복원 명령 exit **0** · ② `git status --porcelain` 계수 **D 1 · M 8** · ③ 복원 9 경로의
`git diff <원천>` **0 줄** · ⑥ `leakPatternGate` exit **0** · ⑥ web 게이트 여덟
(`lint`·`typecheck`·`gate:src-tree`·`api:types:check`·`test`·`gate:tests`·`build`·
`gate:boundary-canary`) 전부 exit **0**.

**④⑤ 와 나머지 Kotlin 게이트는 트리 동일성으로 갈음한다**(「HEAD 초록」이 아니라 트리 동일성뿐).
되돌린 트리를 **원천 트리와 전수 대조**하니 차이가 **내 evidence 네 파일(A)뿐**이다
(`git diff --name-status <원천>` — 되돌리지 않기로 한 디렉터리). 즉 되돌린 트리의 컴파일·test
입력은 **원천 main 트리와 같다**. evidence 를 입력으로 삼는 게이트는 `leakPatternGate` 하나이고
그것만 실제로 돌렸다. **갈음의 앵커는 그 main 커밋의 CI** 이고 내 로컬 전건 실행이 아니다 — 그
구분을 여기 적는다.

**완료 판정은 종료 코드가 아니라 계수다.** 되돌린 트리에서(evidence 제외):

- **내 산출물의 표지 0** — 새 계약 test 파일 **부재** · 그 파일의 등록부 한 줄 **0** · 계약의
  명령 enum 토큰 **0** · 별칭을 거치지 않는 타입 등식 **0** · 그리고 내가 공용 헬퍼에 더한 **선언
  `propertyEnum` 매치 0**. **헬퍼 파일 자체는 내 표지가 아니다** — 그 파일은 원천에 이미 있고
  (D-6A3-20) 10B 는 거기에 선언 하나를 더했다. 그래서 세는 것은 **파일 이름이 아니라 그 선언**이고,
  복원 뒤 파일은 **남아 있어야 한다**(아래 보존 항목).
- **신설 OPEN ID 는 매치 1** 이고 그 자리는 **main 의 `milestone-10.md`** 다 — 팀장이 내 착수
  보고를 그 계약 문서에 등재했기 때문이다. 내가 만진 적 없는 파일이고(`git log HEAD ^<원천> --
  milestone-10.md` 빈 출력) 복원 목록 밖이다. **그래서 「내 표지」를 어휘 grep 으로 세면 0 이
  아니다** — 세는 단위는 내가 소유한 항목 집합이고 이 한 건은 상류 어휘다.
- **남의 표지 보존** — §12.3 하위 절 (a)~(f) **6** · (f) 표의 행 **5** · (h) 절의 행 **0**(내 것) ·
  `milestone-10.md` 보존 · **공용 헬퍼 파일 보존** · 게이트 등록부의 `app.http.OpenApi*` 등재
  **14**(내 한 줄을 뺀 수) · 교차언어 게이트의 금액 축 블록 **1**.

## 측정 함정 하나 — 기록

첫 라운드 ⑥ 의 `gate:src-tree` exit 1 은 **rollback 의 미완이 아니라 내 측정 설정**이었다:
`npm ci` 를 아끼려 clone 의 `web/node_modules` 를 symlink 로 뒀는데 그 게이트가 「`web/` 안
심볼릭 링크 금지」를 단언한다. 실 디렉터리로 재설치해 재측정한 값이 위 ⑥ 이다.
