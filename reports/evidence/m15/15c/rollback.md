# M15 / 15c — 되돌리기

실측 HEAD: `c4aa73d65818e2685be678ca6f53c8e65fa60dab` — 15B 를 담은 main 을 들인 **병합 커밋**이고,
이 slice 의 **마지막 산출물 커밋**이다. 병합이 산출물 커밋 뒤에 왔으므로 실측 자리는 그 병합 커밋이다.

**복원 원천: `036fbda914bab0d30a3720f3a075ec1fc419495b`** — 이 브랜치에 마지막으로 병합한 main 커밋이고
**고정 값**이다(움직이는 `origin/main` 을 쓰지 않는다). 그 커밋은 15A 의 V28 과 15B 의 V29 를 담는다.

**전제: 이 slice 는 아직 main 에 없다.** 머지 뒤의 되돌림은 아래 절차가 아니라 그 PR 의 머지 커밋
revert 다 — 머지된 트리에서 아래 복원을 돌리면 **아무것도 지우지 않으면서 exit 0** 을 낸다.

## 원천의 단언 셋

| 단언 | 명령 | 결과 |
| --- | --- | --- |
| (a) 원천이 HEAD 의 조상 | `git merge-base --is-ancestor <원천> HEAD` | 참 |
| (b) 원천에 이 slice 의 **첫** 산출물 커밋이 없다 | `git merge-base --is-ancestor 330e8027 <원천>` | **거짓**(= 없다, 참) |
| (c) 복원 대상 blob 이 착수 base 와 새 원천에서 같은가 | 아래 | **다름 10 / 같음 23** |

**(c) 가 비지 않았다** — 15A·15B 가 복원 목록의 열 경로를 바꿨다(어댑터 셋 · app test 둘 ·
마이그레이션 lint · CHECK 수 · 계약 파일 · 승인 목록 · 생성 타입). 그래서 착수 base 기준의 앞
실측은 **버리고** ①~⑥ 전부를 **새 원천 기준으로 다시** 냈다. 아래 수치가 그 재측정이다.

**이 열 경로에 hunk 격리가 필요 없는 이유**: 복원 원천이 **병합한 main** 이므로 그 트리의 내용이
곧 「내 slice 를 뺀 15A·15B 의 현재 값」이다. 착수 base 에서 복원하던 때와 달리 남의 줄을
되돌릴 길이 없다 — 그 보존을 아래 「남의 표지」 계수가 실측한다.

## 되돌리는 방법

in_scope 경로 **한정** 복원이다(range revert 가 아니다). 경로는 **개별 인자**로 넘긴다.
`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다.

```
git restore --source=036fbda914bab0d30a3720f3a075ec1fc419495b --staged --worktree -- <아래 목록>
```

목록은 손으로 쓰지 않는다 — **매 라운드 기계로 재산출**한다. 원천과 실측 자리를 옮기면
**양끝을 함께** 옮긴다:

```
git diff --name-status 036fbda914bab0d30a3720f3a075ec1fc419495b..<실측 HEAD> -- . ':!reports/evidence'
```

**이 slice 의 evidence(`reports/evidence/m15/15c/`)는 되돌리지 않는다** — 그래서 위 명령의 제외
인자와 복원 목록이 같은 집합이다. 하네스 경로(`CLAUDE.md`·`.claude/**`)도 되돌리지 않는다
(이 range 에 그 변경이 **없다**).

## 실측 (임시 clone, 실측 HEAD `c4aa73d65`, 원천 `036fbda91`)

| 단계 | 결과 |
| --- | --- |
| 목록 재산출 | **35 경로**(A 2 · M 33) |
| ① 명령 종료 코드 | `git restore …` exit **0** |
| ② D/M 수 | **D 2 · M 33** |
| ③ 복원 경로의 **원천 대비** diff | **빈 출력**(0 줄) |
| ③b evidence 보존 | `git status --porcelain -- reports/evidence` 빈 출력 |
| ③c **내 표지 0** | 사업자번호 어휘를 담은 파일 **0**(evidence 제외) |
| ③d **남의 표지 보존** | 15A·15B 표지(`SignUpRequest`·`/api/accounts`)를 담은 파일 **8** · 마이그레이션 최대 셋 **V27 · V28 · V29**(결번 0) |
| ④⑤⑥ 컴파일 · test · 게이트 | `./gradlew --no-daemon --no-build-cache check --continue` **BUILD SUCCESSFUL** (exit **0**) |

④⑤⑥ 을 한 명령으로 재는 이유: 이 저장소의 `check` 가 컴파일 · test · 게이트 전부를 든다.
**갈음이 아니라 실행이다** — 되돌린 트리에서 직접 돌렸고, `--no-build-cache` 로 공유 cache 의
낡은 산출물을 배제했다(그 함정의 실측은 `commands.md` 의 15A 병합 라운드 절에 있다).

## 이 실측이 유효한가 — verifier 가 재는 술어

```
git diff --name-only c4aa73d65 ..<판정 SHA> -- <위 복원 목록의 35 경로>
git rev-list --merges c4aa73d65..<판정 SHA>
```

둘 다 **빈 출력**이면 유효하다. `c4aa73d65` 뒤의 커밋은 evidence 전용이고 복원 목록은 evidence 를
제외하므로 첫 술어가 빈 출력으로 선다. 그 뒤에 병합도 없다.

## 라운드마다 다시 한다

수정 라운드가 파일을 늘리거나 main 을 다시 병합하면 위 재산출 명령을 **그 라운드의 마지막 산출물
커밋에서** 다시 돌리고 원천·실측 HEAD·단언 (a)(b)(c) 를 함께 갱신한다. 앞 라운드의 실측을 옮기지
않는다 — 이 slice 에서 실제로 세 번 그렇게 했다(게이트 등재 라운드 · 15A 병합 · 15B 병합).
