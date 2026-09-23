# M6/6A-3+6F-3 rollback

`실측 HEAD: 0dd74d19`(검토 라운드 1 수정의 마지막 산출물 커밋 — 이후 커밋은 evidence 전용)

## 되돌리는 것

`base = febad567`(`git merge-base HEAD origin/main`, 라운드마다 재산출). 목록은 기계 산출:

```
git diff --name-status febad567..0dd74d19 -- . ':!reports/evidence' ':!milestone-6.md'
```

신규(A) 25 · 변경(M) 30 = 55개.

**`milestone-6.md` 사실 정정(verifier r1 L-2 시정)** — 이전 판은 「`git log febad567..535a48ce
milestone-6.md` 0건」이라고 적었으나 거짓이었다. `git log --oneline febad567..0dd74d19 --
milestone-6.md`는 `39076f6c`(팀장, "docs(m6): 6A-3+6F-3 착수 문단 등재") 1건을 낸다 — 이 slice
범위 안에서 `milestone-6.md`가 실제로 바뀌었다는 사실 자체는 있다. 그래도 되돌리지 않는다 —
그 이유는 "커밋이 없어서"가 아니라 **팀장 레인 전용 문단(착수·계약 갱신)이라 이 slice의 되돌림
대상 정의(scope.md `in_scope`)에 `milestone-6.md`가 없기 때문**이다. 이 slice의 어떤 커밋도
그 파일을 만들거나 고치지 않았다(`git log --author` 대조로 39076f6c가 팀장 저작임을 확인) —
「이 slice가 그 파일을 안 건드렸다」와 「그 파일이 범위 안에서 안 바뀌었다」는 다른 문장이고,
전자만 참이다.

## 공유 파일 — hunk 격리가 필요 없음을 재실측으로 확인

`config/quality/gate-tests.properties`·`config/quality/architecture-policy.properties` 둘 다
`git log --oneline febad567..0dd74d19 -- <파일>`로 **이 slice(`m6-6a3f3` 브랜치)의 커밋만**
만졌음을 재확인했다(다른 레인과 겹치는 줄 없음 — 매 라운드 다시 실측, 검토 라운드 1의 신규
커밋 다섯도 전부 `m6-6a3f3` 저작). 그래서 이 두 파일도 다른 M 파일과 같은
`git restore --source=<base>`로 복원한다 — 커밋 해시별 hunk 격리가 필요한 경우(같은 파일을
다른 slice가 같은 range 안에서 동시에 만진 경우)가 아니다.

## 명령

```
git restore --source=febad567 --staged --worktree -- <위 55개 경로, 개별 인자>
```

(경로 목록은 `git diff --name-status`가 매 라운드 다시 낸다 — 손으로 옮기지 않는다.)

## 임시 clone 실측(①~⑥, 버릴 worktree, 캐시 우회 `check`는 별도 clone)

| 축 | 결과 |
|---|---|
| ① `git restore` exit | 0 |
| ② D/M 수 | D 25 · M 30 (파일 목록과 일치) |
| ③ `git diff febad567 -- <55경로>` | 0줄(완전 일치) — `milestone-6.md`는 `0dd74d19`와도 0줄(무변경 확인, 위 사실 정정과 별개로 이 slice 커밋이 그 파일을 안 만졌다는 것의 재확인) |
| ④ 모듈별 compile(`:strategy`·`:workflow`·`:adapters`·`:app`의 `compileKotlin`+`compileTestKotlin`) | BUILD SUCCESSFUL |
| ⑤ `:strategy:test :workflow:test :adapters:test :app:test` | BUILD SUCCESSFUL |
| ⑥ 되돌린 트리에서 `./gradlew --no-daemon check`(전건, 이 slice가 닿은 게이트 전부 포함) | BUILD SUCCESSFUL |

①~⑥ 전부 같은 worktree(`git worktree add --detach <scratch> 0dd74d19` 뒤 위 restore 적용)에서
순서대로 실측했다 — 초록 갈음은 「HEAD 초록」이 아니라 이 트리 동일성(③)과 빌드 가능성(④⑤⑥)
둘 다로 선다.

## V16 마이그레이션 — 되돌림 두 갈래(migration-reviewer 처방)

- **미적용 DB**(로컬 개발·이 slice의 test 컨테이너처럼 아직 V16이 안 돈 상태) — 위
  `git restore`로 `V16__operator_strategy_max_active_bids.sql` 파일이 삭제되면 Flyway가 그
  버전을 아예 모른다. 파일 삭제만으로 충분하다.
- **적용된 DB**(운영 반입 이후) — **파일 삭제로 되돌리지 않는다.** Flyway는 이미 적용된
  migration 파일이 DB의 `flyway_schema_history`에서 사라지면 `validate`(기본 활성) 단계에서
  "checksum mismatch"가 아니라 **"applied migration not resolved locally"** 로 기동 자체를
  실패시킨다 — 파일 삭제를 적용된 DB에 그대로 쓰면 다음 배포가 막힌다(migration-reviewer
  처방). 대신 새 `V<N>__drop_max_active_bids.sql`로
  `ALTER TABLE operator_strategy DROP COLUMN max_active_bids; ALTER TABLE
  operator_strategy_revision DROP COLUMN max_active_bids;`를 낸다 — 이 slice의 어떤 파일도
  앞의 갈래(파일 삭제)를 전제하지 않는다(두 표 다 nullable, DEFAULT 없음이라 DROP이 다른
  열에 영향 없음). **그래서 위 restore 목록에서 V16 파일은 "적용 DB" 운영 절차의 대상이
  아니다** — restore 명령 자체는 미적용 DB(이 slice의 모든 test 컨테이너를 포함) 기준이고,
  적용 DB 갈래를 택할 때는 그 파일을 restore 대상에서 제외하고 위 `DROP COLUMN` 마이그레이션을
  별도로 추가한다.

## 그 사이 되돌림 대상이 움직였는가 (실측 HEAD ↔ 판정 SHA)

```
git diff --name-only 0dd74d19..<판정 SHA> -- <위 55개 경로>
```

이 slice의 남은 커밋은 evidence 파일(`reports/evidence/m6/6a3f3/**`)만 만진다 — 위 55개 경로는
그 안에 없으므로 이 명령은 빈 출력이어야 유효하다. 최종 판정 SHA에서 이 명령을 실행해 빈 출력을
확인하는 것이 verifier의 몫이다(이 문서는 실측 HEAD 시점의 절차 정본이고, 마지막 확인은 그 뒤
커밋이 없다는 사실 하나로 성립한다 — evidence 전용 커밋은 정의상 이 diff에 나타나지 않는다).
