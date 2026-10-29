# M7/7C④ — rollback (Phase 2b, 셋째 병합 라운드 판)

실측 HEAD: **`fe26fe25`**(셋째 병합 커밋 — 이 라운드의 마지막 산출물 커밋이고, 뒤의 것은 장부다).
base: **`bb6a187e`**(DEC-10 PR #99 · Go CVE #100 · Caddy/베이스 #101 이 머지된 main — 고정 SHA 다.
움직이는 `origin/main` 을 쓰지 않는다).

**2a 는 이 절차의 대상이 아니다.** 이미 main 에 있으므로 2a 를 되돌리는 길은 PR #90 의 머지 커밋
revert 다. 아래는 **2b 만** 되돌린다 — 되돌린 트리에는 V22 · V23 과 2a 커널, 그리고 7B · 7C② ·
7C③ · 7C⑤-a · DEC-05 · DEC-10 이 그대로 남는다(그것이 base 이기 때문이다).

전제 둘을 실측했다.
- base 가 실측 HEAD 의 조상이다(`git merge-base --is-ancestor` exit 0).
- base 가 이 라운드의 **첫 커밋** `081a0554` 를 포함하지 않는다(같은 명령 역방향 exit 1).

이 라운드는 아직 `main` 에 없다(브랜치 `m7-7c4b/2026-10-26`, push 0). 머지 뒤의 되돌림은 이 절차가
아니라 PR 의 머지 커밋 revert 다.

## 되돌림 목록 (기계 산출)

`git diff --name-status bb6a187e..fe26fe25` 에서 **이 slice 의 장부**(`reports/evidence/m7/7c4/`)를
뺀 것이 전부다 — **A 17 · M 29, 경로 46**. 복원 명령이 그 출력에서 바로 경로를 받는다.

둘째 병합 라운드(base `a45105a5`)와 **같은 집합**이다(`comm -3` 빈 출력) — main 이 들여온 130 경로 중
이 복원 목록과 겹치는 것이 없어 목록이 움직이지 않았다. 그 앞 라운드(base `46ac0949` · 42 경로)에서
넷 늘었던 사유는 그대로 유효하다.
- `app/.../http/HttpTestApplication.kt` (**M**, 새로 들어옴): main 이 그 class 를 `HttpTestSupport.kt`
  에서 **제 파일로 갈랐다**. 이 slice 의 `@Import` 한 줄이 그 새 파일로 따라가 M 이 됐다.
- `app/.../http/ProfileRequestsTest.kt` (**A**): r3 수정 라운드가 만든 신설 test(전송 표면 허용 집합
  축). `gate-tests.properties` 에 같은 커밋에서 등재됐다.
- `docs/discovery/capability-map.md` · `docs/discovery/m7-screen-data-requirements.md` (**M**):
  `OPEN-7C4-AWAITING-CHECKLIST` 등재(운영자 결정 2026-10-28).

**빠진 경로는 0** 이다(`comm -23` 빈 출력).

남의 장부는 이 라운드가 건드리지 않았다(`reports/` 아래 다른 경로가 목록에 **0 건**). 하네스 경로도
이 range 에 커밋이 0 이다(`git log --oneline bb6a187e..fe26fe25 -- CLAUDE.md .claude/` 빈 출력).

## 명령

```
mapfile -t P < <(git diff --name-only bb6a187e..fe26fe25 | grep -v '^reports/evidence/m7/7c4/')
git restore --source=bb6a187e --staged --worktree -- "${P[@]}"
```

## 비활성화 경로 (되돌리지 않고 끄는 법)

2b 가 연 것은 **HTTP 표면 여섯**이다. 코드를 그대로 두고 끄려면 `ProfileWiring` 의 조립을 빼면 된다 —
controller 두 개가 받는 use case 빈이 사라져 기동이 실패하므로, 끄는 단위는 **그 조립과 두 controller
파일**이 함께다. 그보다 작은 단위로 끄는 길은 두지 않았다(「일부만 열린 표면」이 계약과 어긋난다).

DB 쪽은 **바뀐 것이 없다**: 이 라운드는 마이그레이션 변경이 **0 건**이고(`git diff` 빈 출력) V22 는
base 에 있다. 읽기·쓰기 경로가 늘었을 뿐이라 되돌림에 데이터 복구가 없다.

## ①~⑦ 실측 (임시 clone `scratchpad/7c4/rb3`, 2026-10-29 KST · base `bb6a187e`)

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원 명령 exit | 0 |
| ② | `git status --short` 의 D · M 수 | **D 17 · M 29**(목록의 A 17 · M 29 와 같다) |
| ②′ | 목록과 실제로 바뀐 경로의 `comm -3` | **0 줄**(같은 집합) |
| ③ | `git diff --quiet bb6a187e -- <목록>` · 같은 명령 `--cached` | 둘 다 exit 0(복원된 경로가 base 와 바이트까지 같다) |
| ④⑤⑥ | 되돌린 트리에서 `check --continue --rerun-tasks --no-build-cache` | **exit 0** · `BUILD SUCCESSFUL in 30m 3s` · **401 task 전부 실행** · `FAILED` 0 · `FROM-CACHE` **0** · **게이트 up-to-date 0** · 루트 게이트 셋 |
| ⑥′ | 되돌린 트리에 이 라운드의 test 결과가 있는가 | **0 건**(그 run 의 결과 XML 487 개 중 프로필 test 결과 0 — 초록이 base 의 초록이고 내 test 가 섞인 초록이 아니다) |
| ⑦ | 공유 파일 표지 계수 | 내 표지 **전부 0**(아래 보정 둘 제외) · 남의 표지 보존 |

`④⑤⑥` 뒤 되돌린 트리의 상태는 그대로였다(`D 17 · M 29`) — 빌드가 추적 파일을 바꾸지 않았다.

**⑦ 의 술어**: 추적 파일의 소스 확장자만, `reports/` 를 뺀 **1333 파일**이 모수다(`git ls-files` 라
`build/**` · `.gradle/**` 가 들어오지 않는다 — 앞 라운드에서 그것들이 섞여 남의 표지 수가 라운드마다
달라졌던 자리다). 셸의 `grep` 은 래퍼라 `xargs /usr/bin/grep` 으로 돌린다.

| 표지 | 되돌린 트리의 계수 |
|---|---|
| 내 것(`ProfileQueryController` · `ProfileCommandController` · `ReadProfileUseCase` · `JdbcOperatorProfileWriter` · `CAPACITY_ROWS_EXCEEDED` · `StorageWriteOutcome` · `RequestValuesKt` · `ProfileOwnershipE2ETest` · `ProfileResponseTest` · `ProfileRequestsTest` · `requiringName` · `QUALIFICATION_TERMS_MAX` · `/api/profile`) | **전부 0** |
| 남의 것 | `NotificationInboxWiring` 4 · `/api/notifications` 6 · `BidCaseWiring` 6 · `SuggestionQuery` 6 · `OpeningResultQuery` 9 · `BidPriceRequestFactory` 7 · **`FloorTrust` 20**(DEC-10) · **`BidMenuKernel` 5**(DEC-05) — **보존** |

**양성 대조**: 남의 표지 여덟이 모두 0 이 아닌 것이 이 술어가 실제로 찾는다는 증거다 — 그중 둘은
**main 이 이 라운드에 새로 들여온 축**이라 복원이 남의 신규 코드도 지우지 않았음을 함께 보인다. 음성 대조로
없는 이름 하나를 같은 술어에 걸어 **0** 을 확인했다(래퍼 때문에 전부 0 이 나오는 실패 모드를 가른다).

**표지 보정 둘** — 내 표지인데 0 이 아닌 자리가 둘 있고, **둘 다 main 쪽 문면이 내 산출물을 이름으로
가리킨 것**이다(내 줄이 아니다. base 에 이미 있음을 `git show bb6a187e:<파일>` 로 확인했다).
- 면제 래칫 test 의 KDoc 이 「7C④-2b 하나(`ProfileWiring`)」라고 **앞질러 적어 두었다**.
- 7C② 가 가른 전략 편집 이중체 파일의 머리 주석이 「시계 축(`ClockTestDoubles.kt`)은 7C④ 가
  가져갔다」고 적어 두었다. 그래서 되돌린 트리에서도 그 두 이름이 **문면으로는** 남는다.

접미가 겹치는 이름 보정(앞 라운드와 같은 자리): `NaraRegistration` 을 표지로 세면 7 파일이 남는데
전부 **`NaraRegistrationField`**(2a 가 세운 타입, base 에 있다)이고, 접미를 뺀 대조에서 홀로 선
`NaraRegistration` 은 **0 종**이다.

## 실측 유효성 대조 (verifier 용)

실측 HEAD 와 판정 SHA 는 다르다(장부 커밋이 늘 뒤에 온다). 볼 것은 **그 사이에 되돌림 대상이
움직였는가**다.

```
git diff --name-only fe26fe25..<판정 SHA> -- "${P[@]}"
```

빈 출력이면 이 실측이 판정 SHA 에 대해 유효하다 — 실측 뒤의 커밋은 `reports/evidence/m7/7c4/`
(목록 밖)만 고친다.

## 복구 시간

명령 하나(수 초). 기동 중인 서비스가 없고 영속 데이터 복구도 없다.
