# M7/7C④ — rollback (Phase 2b)

실측 HEAD: **`4a5108d7`**(이 라운드의 마지막 산출물 커밋 — 뒤의 둘은 장부다). base: **`46ac0949`**
(2a PR #90 이 머지된 main).

**2a 는 이 절차의 대상이 아니다.** 이미 main 에 있으므로 2a 를 되돌리는 길은 PR #90 의 머지 커밋 revert 다.
아래는 **2b 만** 되돌린다 — 되돌린 트리에는 V22 와 2a 커널이 그대로 남는다(그것이 base 이기 때문이다).

전제 둘을 실측했다.
- base 가 실측 HEAD 의 조상이다(`git merge-base --is-ancestor` exit 0).
- base 가 이 라운드의 **첫 커밋** `081a0554` 를 포함하지 않는다(같은 명령 역방향 exit 1).

이 라운드는 아직 `main` 에 없다(브랜치 `m7-7c4b/2026-10-26`, push 0). 머지 뒤의 되돌림은 이 절차가 아니라
PR 의 머지 커밋 revert 다. 머지 전에 base 가 다시 바뀌면(7C ② 머지 뒤 병합) 이 실측을 다시 한다.

## 되돌림 목록 (기계 산출)

`git diff --name-status 46ac0949..4a5108d7` 에서 **이 slice 의 장부**(`reports/evidence/m7/7c4/`)를 뺀 것이
전부다 — **A 14 · M 24, 경로 38**. 복원 명령이 그 출력에서 바로 경로를 받는다(손으로 옮겨 적지 않는다).
남의 장부는 이 라운드가 건드리지 않았다(`reports/` 아래 다른 경로가 diff 에 0 건) — 건드렸다면 필터 밖이라
목록에 들어온다.

- `A` 14(복원이 삭제한다): `workflow` 의 use case 파일과 그 test · `adapters` 의 V12 저장 어댑터 ·
  `app` 의 HTTP 표면 다섯과 요청 값 변환 · 조립 하나 · test 넷(표면 · 조립 경계 · 이중체 조립 · 분리한 이중체).
- `M` 24(base 내용으로 되돌린다): 2a 의 port · 값 타입 · 격리 test · 7B 의 입찰함 HTTP 넷(401 공용화 호출부) ·
  오류 코드 표 · 계정 전용 뿌리 선언 · 계정 경계 구조 test 셋 · 게이트 계약 둘 · OpenAPI 와 생성 타입 ·
  HTTP test 지원 · 실 DB test 둘.

공유 파일(게이트 계약 둘 · 구조 test 셋 · 7B 의 HTTP 넷 · OpenAPI · 생성 타입)은 다른 레인의 줄도 담은
파일이다. base 가 그 줄을 이미 담고 있으므로 복원은 **이 라운드의 줄만** 지운다 — 아래 ⑦ 이 계수로 확인한다.
하네스 경로는 이 range 에 커밋이 0 이다(`git log --oneline 46ac0949..4a5108d7 -- CLAUDE.md .claude/` 빈 출력).

## 명령

```
mapfile -t P < <(git diff --name-only 46ac0949..4a5108d7 | grep -v '^reports/evidence/m7/7c4/')
git restore --source=46ac0949 --staged --worktree -- "${P[@]}"
```

## 비활성화 경로 (되돌리지 않고 끄는 법)

2b 가 연 것은 **HTTP 표면 여섯**이다. 코드를 그대로 두고 끄려면 `ProfileWiring` 의 조립을 빼면 된다 —
controller 두 개가 받는 use case 빈이 사라져 기동이 실패하므로, 끄는 단위는 **그 조립과 두 controller 파일**이
함께다(그 둘이 이 라운드의 유일한 production 진입점이다). 그보다 작은 단위로 끄는 길은 두지 않았다 —
「일부만 열린 표면」이 계약과 어긋나기 때문이다.

DB 쪽은 **바뀐 것이 없다**: 2b 는 마이그레이션을 더하지 않았고 V22 는 base 에 있다. 읽기·쓰기 경로가 늘었을
뿐이라 되돌림에 데이터 복구가 없다.

## ①~⑦ 실측 (임시 clone `scratchpad/7c4b/rb`, 2026-10-26 18:21~18:45 KST)

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원 명령 exit | 0 |
| ② | `git status --short` 의 D · M 수 | **D 14 · M 24**(목록의 A 14 · M 24 와 같다) |
| ②′ | 목록과 실제로 바뀐 경로의 `comm -3` | **0 줄**(같은 집합) |
| ③ | `git diff --quiet 46ac0949 -- <목록>` · 같은 명령 `--cached` | 둘 다 exit 0(복원된 경로가 base 와 바이트까지 같다) |
| ④⑤⑥ | 되돌린 트리에서 `./gradlew --no-daemon check --continue --no-build-cache` | **exit 0** · `BUILD SUCCESSFUL in 24m 35s` · **400 task 전부 실행**(이 실측 HEAD 에서 직접) |
| ⑥′ | 되돌린 트리에 이 라운드의 test 결과가 있는가 | **0 건**(2b 결과 XML 없음 — 초록이 base 의 초록이고 내 test 가 섞인 초록이 아니다) |
| ⑦ | 공유 파일 표지 계수 | 내 표지 **전부 0**(`ProfileQueryController` · `ProfileCommandController` · `ProfileWiring` · `ReadProfileUseCase` · `JdbcOperatorProfileWriter` · `CAPACITY_ROWS_EXCEEDED` · `StorageWriteOutcome` · `RequestValuesKt` · `ClockTestDoubles` · `/api/profile`) · 남의 표지 보존(`BidCaseWiring` 8 · `SuggestionQuery` 10 · `listBidCases` 4 · `InMemoryBidCaseStore` 4 · `BidCaseRouting` 7) |

**⑦ 의 보정 하나.** `NaraRegistration` 을 표지로 세면 7 파일이 남는다 — 전부 **`NaraRegistrationField`**(2a 가
세운 타입, base 에 있다)이고 부분 문자열이 겹친 것이다. 접미를 뺀 대조에서 홀로 선 `NaraRegistration` 은
**0 건**이다. 계수 술어가 양쪽으로 틀릴 수 있다는 자리라 보정 전후를 둘 다 남긴다.

## 실측 유효성 대조 (verifier 용)

실측 HEAD 와 판정 SHA 는 다르다(장부 커밋이 늘 뒤에 온다). 볼 것은 **그 사이에 되돌림 대상이 움직였는가**다.

```
git diff --name-only 4a5108d7..<판정 SHA> -- "${P[@]}"
```

빈 출력이면 이 실측이 판정 SHA 에 대해 유효하다 — 실측 뒤의 커밋은 `reports/evidence/m7/7c4/`(목록 밖)만
고친다.

## 복구 시간

명령 하나(수 초). 기동 중인 서비스가 없고 영속 데이터 복구도 없다.
