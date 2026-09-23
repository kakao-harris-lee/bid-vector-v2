# M6/6A-3+6F-3 rollback

`실측 HEAD: 535a48ce`

## 되돌리는 것

`base = febad567`(`git merge-base HEAD origin/main`). 목록은 기계 산출:

```
git diff --name-status febad567..535a48ce -- . ':!reports/evidence' ':!milestone-6.md'
```

신규(A) 20 · 변경(M) 29 = 49개. `milestone-6.md`는 팀장 레인 전용 문단(착수·계약 갱신 넷)이라
되돌리지 않는다 — 이 slice의 어떤 커밋도 그 파일을 건드리지 않았다(`git log --oneline
febad567..535a48ce -- milestone-6.md` 결과 0건).

## 공유 파일 — hunk 격리가 필요 없음을 실측으로 확인

`config/quality/gate-tests.properties`·`config/quality/architecture-policy.properties` 둘 다
`git log --oneline febad567..535a48ce -- <파일>`로 **이 slice의 커밋만** 만졌음을 확인했다(다른
레인과 겹치는 줄 없음). 그래서 이 두 파일도 다른 M 파일과 같은 `git restore --source=<base>`로
복원한다 — 커밋 해시별 hunk 격리가 필요한 경우(같은 파일을 다른 slice가 같은 range 안에서
동시에 만진 경우)가 아니다.

## 명령

```
git restore --source=febad567 --staged --worktree -- <위 49개 경로, 개별 인자>
```

(경로 목록은 `git diff --name-status`가 매 라운드 다시 낸다 — 손으로 옮기지 않는다.)

## 임시 clone 실측(①~⑥, 버릴 worktree, 캐시 우회 `check`는 별도 clone)

| 축 | 결과 |
|---|---|
| ① `git restore` exit | 0 |
| ② D/M 수 | D 20 · M 29 (파일 목록과 일치) |
| ③ `git diff febad567 -- <49경로>` | 0줄(완전 일치) — `milestone-6.md`는 535a48ce와도 0줄(무변경 확인) |
| ④ 모듈별 compile(`:strategy`·`:workflow`·`:adapters`·`:app`의 `compileKotlin`+`compileTestKotlin`) | BUILD SUCCESSFUL |
| ⑤ `:strategy:test :workflow:test :adapters:test :app:test` | BUILD SUCCESSFUL |
| ⑥ 되돌린 트리에서 `./gradlew --no-daemon check`(전건, 이 slice가 닿은 게이트 전부 포함) | BUILD SUCCESSFUL(346 tasks) |

①~⑥ 전부 같은 worktree(`git worktree add --detach <scratch> 535a48ce` 뒤 위 restore 적용)에서
순서대로 실측했다 — 초록 갈음은 「HEAD 초록」이 아니라 이 트리 동일성(③)과 빌드 가능성(④⑤⑥)
둘 다로 선다.

## V16 마이그레이션 — 되돌림 두 갈래

- **미적용 DB**(로컬 개발·이 slice의 test 컨테이너처럼 아직 V16이 안 돈 상태) — 위 `git restore`로
  `V16__operator_strategy_max_active_bids.sql` 파일이 삭제되면 Flyway가 그 버전을 아예 모른다.
  파일 삭제만으로 충분하다.
- **적용된 DB**(운영 반입 이후) — 파일 삭제로 되돌리지 않는다(6F-4 규율). 새 `V<N>__drop_max_active_bids.sql`
  로 `ALTER TABLE operator_strategy DROP COLUMN max_active_bids; ALTER TABLE operator_strategy_revision
  DROP COLUMN max_active_bids;`를 낸다 — 이 slice의 어떤 파일도 앞의 갈래를 전제하지 않는다(두 표 다
  nullable, DEFAULT 없음이라 DROP이 다른 열에 영향 없음).

## 그 사이 되돌림 대상이 움직였는가 (실측 HEAD ↔ 판정 SHA)

```
git diff --name-only 535a48ce..<판정 SHA> -- <위 49개 경로>
```

이 slice의 남은 커밋은 evidence 파일(`reports/evidence/m6/6a3f3/**`)만 만진다 — 위 49개 경로는
그 안에 없으므로 이 명령은 빈 출력이어야 유효하다. 최종 판정 SHA에서 이 명령을 실행해 빈 출력을
확인하는 것이 verifier의 몫이다(이 문서는 실측 HEAD 시점의 절차 정본이고, 마지막 확인은 그 뒤
커밋이 없다는 사실 하나로 성립한다 — evidence 전용 커밋은 정의상 이 diff에 나타나지 않는다).
