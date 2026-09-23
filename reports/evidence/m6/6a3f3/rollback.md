# M6/6A-3+6F-3 rollback

`실측 HEAD: 18fdd72e`(검토 라운드 2 수정의 마지막 산출물 커밋 — 이후 커밋은 evidence 전용)

## 되돌리는 것

`base = febad567`(`git merge-base HEAD origin/main`, 라운드마다 재산출). 목록은 기계 산출:

```
git diff --name-status febad567..18fdd72e -- . ':!reports/evidence' ':!milestone-6.md'
```

신규(A) 26 · 변경(M) 30 = 56개.

## `milestone-6.md` — 공유 파일, 커밋 해시 hunk 격리(verifier r2 LR2-1 시정)

**이전 판의 두 논거를 정정한다.** ① 「`milestone-6.md`가 scope.md `in_scope`에 없어서 안
건드렸다」는 사실과 다르다 — `in_scope` 블록에 `milestone-6.md # 착수·종결 문단(팀장) — 공유
파일`이 **있다**. ② 「`git log --author` 대조로 `39076f6c`가 팀장 저작임을 확인했다」도
성립하지 않는다 — `febad567..18fdd72e` 범위의 모든 커밋이 같은 author(`kakao-harris-lee`)라
author로는 레인을 가를 수 없다.

실제 근거는 **레인**(팀장의 착수·계약 갱신·종결 문단 vs 구현 레인의 산출물 커밋)이지 저자가
아니다. `git log --oneline febad567..18fdd72e -- milestone-6.md`는 `39076f6c`(팀장, "docs(m6):
6A-3+6F-3 착수 문단 등재") 1건만 낸다 — 구현 레인(kotlin-implementer)의 어떤 커밋도 이 파일을
만들거나 고치지 않았다(위 rollback 목록의 기계 산출 `git diff --name-status`에도 이 파일이
없다 — 목록 자체가 그 사실의 증거다). 이 slice의 종결 문단(팀장이 나중에 커밋)이 더해지면
그 커밋 해시도 여기 추가한다.

**만약 이 파일을 되돌려야 하면** range 전체를 `git restore`로 덮지 않는다(병합 뒤에는 다른
레인의 커밋이 같은 range 안에 섞일 수 있어 「이 range 안에서는 이 slice만 만졌다」는 가정이
깨진다, 라운드 1 L-2 지적). 대신 **그 커밋 하나만** 커밋 해시로 hunk 격리한다:

```
git diff 39076f6c~1..39076f6c -- milestone-6.md | git apply -R
```

(이 slice의 rollback 목록에는 애초에 `milestone-6.md`가 없으므로 정상 절차에서는 이 명령이
필요 없다 — 팀장이 착수 문단을 되돌릴 때만 쓰는 절차로 여기 적어 둔다.)

## 공유 파일(`gate-tests.properties`·`architecture-policy.properties`) — 커밋 해시 hunk 격리

**이전 판이 쓰던 「이 range 안에서는 이 slice만 만졌다」는 논거를 더 쓰지 않는다**(verifier
r2 LR2-1 — 그 논거는 병합 전에만 성립한다, D-6A3-23 「규율대로」·라운드 1 L-2 지적). 대신
**이 slice가 실제로 그 파일을 만진 커밋 각각**을 열거하고, 되돌릴 때는 그 커밋들을 **최신
것부터 역순으로** 하나씩 hunk 격리한다(`git diff <sha>~1..<sha> -- <파일> | git apply -R`) —
이렇게 하면 병합 뒤 다른 레인의 커밋이 같은 파일의 같은 range 안에 끼어 있어도 이 slice가
낸 hunk만 정확히 걷어낸다(대상이 커밋 해시로 고정되므로 range 가정에 기대지 않는다).

`config/quality/gate-tests.properties`를 만진 이 slice의 커밋(최신→과거):
`0dd74d19`·`e3666929`·`b1982763`·`1c52a182`·`4213026e`·`aecfce53`·`691f5630`·`5d899951`·
`98a18086`.

`config/quality/architecture-policy.properties`를 만진 이 slice의 커밋(최신→과거):
`86a48b7e`·`b1982763`·`4213026e`.

(목록은 `git log --oneline febad567..18fdd72e -- <파일>` 기계 산출 — 라운드마다 재산출한다.
일반 in_scope 파일과 달리 이 두 파일은 공유 자원이라 `git restore --source=<base>`로 되돌리지
않는다 — 다른 레인이 병합 후 같은 파일에 낸 hunk를 함께 지울 위험이 있다.)

## 명령(공유 파일 둘·`milestone-6.md` 제외 — 나머지 54개)

```
git restore --source=febad567 --staged --worktree -- <위 56개에서 공유 파일 둘을 뺀 54개 경로, 개별 인자>
```

(경로 목록은 `git diff --name-status`가 매 라운드 다시 낸다 — 손으로 옮기지 않는다. 공유 파일
둘은 위 커밋 해시 hunk 격리로, `milestone-6.md`는 이 slice의 rollback 대상이 아니다.)

## 임시 clone 실측(①~⑥, 버릴 worktree, 캐시 우회 `check`는 별도 clone)

실측은 편의상 공유 파일 둘을 포함한 전체 56개 경로에 `git restore --source=<base>`를 적용해
빌드·test·게이트가 되돌린 상태에서도 서는지 확인했다(공유 파일의 실제 되돌림 **절차**는 위
커밋 해시 hunk 격리이지만, 두 절차의 최종 파일 내용은 같다 — `febad567` 시점 값으로 수렴).

| 축 | 결과 |
|---|---|
| ① `git restore` exit | 0 |
| ② D/M 수 | D 26 · M 30 (파일 목록과 일치) |
| ③ `git diff febad567 -- <56경로>` | 0줄(완전 일치) |
| ④ 모듈별 compile(`:strategy`·`:workflow`·`:adapters`·`:app`의 `compileKotlin`+`compileTestKotlin`) | BUILD SUCCESSFUL(1m16s) |
| ⑤ `:strategy:test :workflow:test :adapters:test :app:test` | BUILD SUCCESSFUL(2m10s) |
| ⑥ 되돌린 트리에서 `./gradlew --no-daemon check`(전건, 이 slice가 닿은 게이트 전부 포함) | BUILD SUCCESSFUL(1m12s, 346 tasks) |

①~⑥ 전부 같은 worktree(`git worktree add --detach <scratch> 18fdd72e` 뒤 위 restore 적용)에서
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
git diff --name-only 18fdd72e..<판정 SHA> -- <위 56개 경로>
```

이 slice의 남은 커밋(장부 일괄)은 evidence 파일(`reports/evidence/m6/6a3f3/**`)만 만진다 —
위 56개 경로는 그 안에 없으므로 이 명령은 빈 출력이어야 유효하다. 최종 판정 SHA에서 이 명령을
실행해 빈 출력을 확인하는 것이 verifier의 몫이다(이 문서는 실측 HEAD 시점의 절차 정본이고,
마지막 확인은 그 뒤 커밋이 없다는 사실 하나로 성립한다 — evidence 전용 커밋은 정의상 이
diff에 나타나지 않는다).
