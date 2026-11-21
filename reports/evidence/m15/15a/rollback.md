# M15 / 15A — 되돌리기

실측 HEAD: `9fe69e4c591116364c70c2c14d7ccd9fa9fdea6a`

> **전제: 이 slice 는 아직 `main` 에 없다.** 머지 **뒤**의 되돌림은 아래 절차가 아니라 그 PR 의
> 머지 커밋 revert 다 — 같은 복원은 머지 순간 아무것도 지우지 않으면서 exit 0 을 낸다.

## 무엇을 끄는가

이 slice 가 켜는 것은 **스키마와 서명**이라 feature flag 로 끌 수 없다. 되돌림은 in_scope 경로를
착수 base 로 복원하는 것 하나이고, DB 는 V28 이 적용된 뒤라면 **되돌릴 수 없다**(아래 「DB」).

## 복원 목록 — 기계로 낸다

```
git diff --name-status 99f2fb25989078d7ab1640de0dc8e399f5d1688d..HEAD -- ':!reports/evidence'
```

`A` 는 삭제 대상, `M` 은 base 로 restore 다. **라운드마다 이 절차를 다시 돌린다** — 수정
라운드가 파일을 더하면 목록이 낡는다(목록이 낡는 것이 이 결함의 실제 원인이다. r1 리뷰가 바로
그것을 잡았다: 앞 판의 195 는 그 라운드가 더한 넷을 세지 않은 수였다).

실측 시점의 목록은 **206 경로**(`A` 8 · `M` 198)이고, `A` 여덟은 V28 SQL · 공유 커널의
`AccountId` 와 그 test · 계정별 run 공장 · 억제 채널 · 출하 E2E 세션 지원 · 마이그레이션 갈래
test · `PinnedLicenseGate` test 다.

```
git restore --source=99f2fb25989078d7ab1640de0dc8e399f5d1688d --staged --worktree -- <위 목록의 모든 경로>
```

`--source` 에 없는 경로(`A`)는 그 명령이 **삭제**하므로 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — 신규 경로마다 pathspec 오류로 exit 1 이다.

## 되돌리지 않는 것

- `reports/evidence/m15/15a/**` — 이 slice 의 evidence 다.
- `CLAUDE.md` · `.claude/**` — 하네스 레인의 것이고 in_scope 밖이다(scope.md 「하네스 레인 변경」).

## 공유 파일

이 slice 가 만지는 파일 가운데 **다른 slice 의 줄이 있는 것은 다섯**이다 —
`config/quality/gate-tests.properties`(새 test class 둘 등재) ·
`config/quality/architecture-policy.properties`(허용 뿌리 · 주입/2층 목록 · 어댑터 멤버 서술자) ·
`.github/workflows/ci.yml`(컨테이너 스모크의 인증 축) · `tools/local-tls-smoke.sh` ·
`tools/db-rehearsal.sh`. 뒤 셋은 r1 라운드에서 in_scope 에 들어왔다(scope.md 「갱신 이력」 (4)).
이 slice 가 main 에 없는 동안에는 위 단일 `git restore` 로 base 복원하면 다른 slice 의 줄이
함께 사라질 수 있으므로, **main 을 병합한 뒤**라면 그 병합 커밋 SHA 를 복원 원천으로 바꾸고
아래 단언 셋을 다시 잰다(`evidence-pack` rollback 절). 오늘은 병합이 없어 착수 base 가 원천이다.

- 단언 ①: 착수 base 가 HEAD 의 조상인가 — `git merge-base --is-ancestor <base> HEAD` (exit 0).
- 단언 ②: 원천에 이 slice 의 **첫** 산출물 커밋이 없는가 — 착수 base 이므로 자명하다.
- 단언 ③(원천을 바꿀 때만): 복원 목록의 `M`·`D` 경로가 두 원천에서 같은 blob 인가.

## DB — 되돌릴 수 없다

V28 은 `operator_strategy.id` · `operator_profile.id` 를 **DROP COLUMN** 하고, 계정이 0 인
DB 에서는 네 표의 남은 행을 **버린다**. 적용된 DB 를 V27 로 되돌리는 down 스크립트는 없다
(이 저장소에 down 마이그레이션 관례가 없다). 운영 반입 전 백업은 반입 스크립트가 뜬다
(계약 「승인 경계」 · runbook §7.3) — 그 백업이 유일한 복구 수단이다.

**코드만 되돌리고 DB 를 두면 기동이 실패한다**: 복원된 `Sql.kt` 가 `WHERE id = 1` 을 쓰는데
그 열이 없다. 그래서 되돌림은 **코드와 DB 를 함께** 되돌리는 것이고, DB 쪽은 백업 복원이다.

## 실측 ①~⑥ (임시 clone, 실측 HEAD 에서)

| # | 재는 것 | 명령 | 결과 |
| --- | --- | --- | --- |
| ① | 복원 명령의 종료 코드 | 위 `git restore` | exit 0 |
| ② | 되돌린 파일 수 | 복원 뒤 `git status --porcelain` 의 D/M 수 | **206**(D 8 · M 198) — 복원 목록과 같다 |
| ③ | in_scope 가 base 와 같은가 | `git diff <base> -- ':!reports/evidence'` | **빈 출력**(0 줄) |
| ④ | 컴파일 | `./gradlew --no-daemon :app:compileTestKotlin` | exit 0 (BUILD SUCCESSFUL) |
| ⑤ | test | `./gradlew --no-daemon :adapters:test --rerun` | exit 0 (BUILD SUCCESSFUL, 3m25s) |
| ⑥ | 게이트 | `./gradlew --no-daemon :app:test --rerun check --continue` | exit 0 (BUILD SUCCESSFUL, 31m54s) |

**⑤⑥ 에 `--rerun` 을 붙인 이유**: 복원한 트리는 base 와 **바이트가 같아** 빌드 캐시가 두 test
task 를 `FROM-CACHE`·`UP-TO-DATE` 로 집었다(첫 시도 실측: `check` 35초). 캐시 적중은 「같은
입력이 전에 성공했다」는 사실이고 틀린 답은 아니지만 **이 라운드에서 돈 것은 아니다** — 이
문서가 「실행했다」라고 적으려면 실제로 돌아야 한다. `--rerun` 으로 두 task 를 강제 실행한
수치가 위 표다.

**④⑤⑥ 을 실행 대신 갈음하는 근거는 트리 동일성뿐이다** — 「HEAD 가 초록이다」는 근거가 아니다.
여기서는 갈음하지 않고 **셋 다 실행했다.**

복원 뒤 base 와 다른 경로는 `reports/evidence/m15/15a/` 의 셋뿐이다(③ 의 출력이 그것을 말한다).

## §4 술어 (verifier 가 대조한다)

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 복원 목록의 경로들>
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 실측이 유효하다. 실측 HEAD 뒤의 커밋은 **evidence 문서뿐**이고 그 경로는
복원 목록 밖이므로(`':!reports/evidence'`) 첫 술어는 비어야 한다. 첫 출력이 비지 않을 때
유효로 읽는 예외는 하나뿐이다 — 나온 경로가 **전부 복원 원천에 없는 신설 파일**일 때다
(복원이 그 파일을 내용과 무관하게 지우므로 그 사이의 편집이 되돌린 트리를 바꾸지 못한다).
그 열거와 `git cat-file -e <원천>:<경로>` 실패 확인은 verifier 가 판정 SHA 에서 한다.
이 예외를 쓸 때 ② 는 판정 SHA 에서 다시 센다.

## 알려진 제한

1. **DB 는 코드와 함께 되돌아가지 않는다**(위 「DB」). 이것이 이 slice 의 유일한 비가역 축이다.
2. 앞 판이 적어 둔 「되돌린 트리에서 `leakPatternGate` 가 붉을 수 있다」는 **실측으로 서지
   않았다** — ⑥ 이 evidence 디렉터리를 남긴 채 exit 0 이다. 예측을 측정으로 바꿨으므로 그
   제한을 내린다(보완 경로였던 「evidence 도 함께 지운다」는 필요 없다).
