# M15 / 15c — 되돌리기

실측 HEAD: `e5914053` — **낡았다.** 수정 라운드 1 이 복원 목록의 파일을 더 바꿨고(여정 술어 ·
화면 · 계약 · 생성 타입 · 두 test), 그래서 아래 ①~⑥ 은 **이 라운드의 판정 SHA 에 대해 미검증**이다.
이 slice 는 15A·15B 머지 뒤에 main 을 병합하므로 그때 **복원 원천이 착수 base 에서 병합한 main
SHA 로 옮겨 간다**(2026-11-22 에 15A 를 들여 `d2c0513db` 를 한 번 병합했고, **15B 뒤에 한 번 더**
병합한다 — 재측정은 그 마지막 병합에서 한 번만 한다) — 목록 재산출의 양끝과 ①~⑥ 을 그 자리에서 **통째로 다시 잰다**(앞 라운드의
실측을 옮기지 않는다). 그 재측정에는 셋째 단언(복원 대상 blob 이 두 원천에서 같은가)과 공유 파일의
hunk 격리가 함께 든다 — 15A·15B 가 아래 「공유 파일」 절이 말하는 경로들을 만진다.

**전제: 이 slice 는 아직 main 에 없다.** 머지 뒤의 되돌림은 아래 절차가 아니라 그 PR 의 머지 커밋
revert 다 — 머지된 트리에서 아래 복원을 돌리면 **아무것도 지우지 않으면서 exit 0** 을 낸다.

**복원 원천은 착수 base `99f2fb25989078d7ab1640de0dc8e399f5d1688d` 다**(움직이는 `origin/main` 을
쓰지 않는다). 이 브랜치는 main 을 병합하지 않았으므로 원천을 옮길 일이 없었다 —
`git merge-base --is-ancestor 99f2fb25 HEAD` 가 참이고, 그 커밋에 이 slice 의 첫 산출물 커밋
(`330e8027`)이 들어 있지 않다.

## 되돌리는 방법

in_scope 경로 **한정** 복원이다(range revert 가 아니다). 경로는 **개별 인자**로 넘긴다.
`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다.

```
git restore --source=99f2fb25989078d7ab1640de0dc8e399f5d1688d --staged --worktree -- <아래 목록>
```

목록은 손으로 쓰지 않는다 — **매 라운드 기계로 재산출**한다:

```
git diff --name-status 99f2fb25989078d7ab1640de0dc8e399f5d1688d..<실측 HEAD> -- . ':!reports/evidence'
```

**이 slice 의 evidence(`reports/evidence/m15/15c/`)는 되돌리지 않는다** — 그래서 위 명령의 제외
인자와 복원 목록이 같은 집합이다. 하네스 경로(`CLAUDE.md`·`.claude/**`)도 되돌리지 않는다
(이 range 에 그 변경이 **없다**).

## 공유 파일 — **머지 전인 지금은** 격리가 필요 없고, 병합 뒤에는 필요하다

지금 이 브랜치는 origin/main 에서 갈라져 나온 뒤 이 slice 의 커밋만 쌓였고, 복원 목록의 어느
경로도 다른 slice 가 같은 range 에서 만지지 않았다(`git log --oneline 99f2fb25..HEAD --no-merges
-- <경로>` 가 전부 이 slice 의 커밋뿐이다).

**그 문장은 main 병합 뒤에 거짓이 된다**(r1 v 「남은 일」). 15A 는 `JdbcBusinessProfileStore.kt` ·
`CleanMigrationCheckTest` · `MigrationLockTimeoutTest` · `BusinessProfileSchemaGuardTest` ·
`JdbcBusinessProfileStoreTest` · `MyBidResultEndpointTest` · `ProfileWiringTradeNameTest` · 계약
파일을, 15B 는 `MigrationLockTimeoutTest` · 계약 파일 · `approvedOperations.ts` · 생성 타입을
만진다. 병합 라운드의 재측정은 그 경로들에 **커밋 해시 hunk 격리**를 쓰고, 「내 표지 0 · 남의 표지
보존」을 **계수로** 확인한다(종료 코드로 갈음하지 않는다).

## 실측 (임시 clone, 실측 HEAD `e5914053` — 위 머리말대로 **이 라운드에는 낡았다**)

| 단계 | 결과 |
| --- | --- |
| ① 명령 종료 코드 | `git restore …` exit **0** |
| ② D/M 수 | **D 2 · M 33** (신규 둘이 지워지고 서른셋이 base 로 돌아갔다) |
| ③ 복원 경로의 base 대비 diff | **빈 출력** — `git diff 99f2fb25 --name-only -- . ':!reports/evidence'` 가 0 줄 |
| ③b evidence 보존 | `git status --porcelain -- reports/evidence` 빈 출력(HEAD 그대로) |
| ④⑤⑥ 컴파일 · test · 게이트 | `./gradlew --no-daemon check` **BUILD SUCCESSFUL** (exit **0**) |

④⑤⑥ 을 한 명령으로 재는 이유: 이 저장소의 `check` 가 컴파일 · test · 게이트 전부를 든다
(`ci.yml` 의 그 job 주석). **갈음이 아니라 실행이다** — 되돌린 트리에서 직접 돌렸다.

되돌린 트리에서 `migrationOrderGate` 가 초록인 것은 V30 이 함께 사라져 번호 등식이 다시 서기
때문이다(scope.md 알려진 제한 6 의 뒷면).

## 이 실측이 유효한가 — verifier 가 재는 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 복원 목록의 경로들>
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 **빈 출력**이면 실측이 유효하다. 수정 라운드 1 의 판정 SHA 에 대해서는 **첫 술어가 비지
않는다**(위 머리말) — 그래서 이 라운드에 대해 ①~⑥ 은 미검증이고, 병합 라운드가 새 원천·새 실측
HEAD 로 다시 잰다. 그 사실을 숨기지 않는 것이 이 절의 몫이다.

## 라운드마다 다시 한다

수정 라운드가 파일을 늘리면 위 재산출 명령을 **그 라운드의 마지막 산출물 커밋에서** 다시 돌리고
실측 HEAD 를 갱신한다. 앞 라운드의 실측을 옮기지 않는다 — 이 slice 에서 실제로 한 번 그랬고
(게이트 등재 커밋이 복원 목록의 파일 셋을 더 바꿨다), 그래서 ①~⑥ 을 통째로 다시 쟀다.
