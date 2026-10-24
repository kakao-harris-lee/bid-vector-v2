# M7/7C④ — rollback (Phase 2a)

실측 HEAD: `1b3ade0b` (이 slice 의 마지막 커밋 — §3 레인 재개 문서). base: **병합된 main 의 고정 SHA `fc15975c`**.

전제 둘을 실측했다.
- base 가 실측 HEAD 의 조상이다(`git merge-base --is-ancestor fc15975c 1b3ade0b` exit 0).
- base 가 이 slice 의 **첫 커밋** `b6d6137b` 를 포함하지 않는다(같은 명령 역방향 exit 1).

이 slice 는 아직 `main` 에 없다(브랜치 `m7-7c4/2026-10-22`, push 0). **머지 뒤**의 되돌림은 이 절차가 아니라 PR 의 머지 커밋
revert 다. PR 직전에 base 가 다시 바뀌면(7B 머지 뒤 rebase · 팀장 update-branch) 이 실측을 다시 한다 — 2026-10-24 재고정이
그 사례다(앞 base `bee7c36f` 의 실측은 버렸다).

## 되돌림 목록 (기계 산출)

`git diff --name-status fc15975c..1b3ade0b` 에서 `reports/evidence/m7/7c4/` 를 뺀 것이 전부다 — **A 15 · M 10, 경로 25**.
복원 명령이 그 출력에서 바로 경로를 받는다(손으로 옮겨 적지 않는다). 수는 evidence 에서 이 자리에만 적는다.

- `A` 15(복원이 삭제한다): `workflow` 의 `profile` 패키지(값 타입 · port · 입력 정책 · 체크리스트 판정 · 판정 대기 축)와 그
  test 셋 · `adapters` 의 V22 마이그레이션 · SQL 문장 · JDBC store · 그 test 둘(실 DB · 스키마 권한) · 열 명세 둘.
- `M` 10(base 내용으로 되돌린다): 보존 레지스트리와 그 fixture · `CleanMigration*Test` 다섯 · 어댑터 의존 게이트 test ·
  게이트 test 계약 · M6 추적표(QUAL-11 행).

공유 파일(게이트 test 계약 · 보존 fixture · `CleanMigration*` · 추적표)은 다른 레인의 줄도 담은 파일이다. base 가 그 줄을
이미 담고 있으므로 복원은 **이 slice 의 줄만** 지운다 — 아래 ⑦ 이 계수로 확인한다.
하네스 경로는 이 range 에 커밋이 0 이다(`git log --oneline fc15975c..1b3ade0b -- CLAUDE.md .claude/` 빈 출력).

## 명령

```
mapfile -t P < <(git diff --name-only fc15975c..1b3ade0b | grep -v '^reports/evidence/m7/7c4/')
git restore --source=fc15975c --staged --worktree -- "${P[@]}"
```

## 비활성화 경로 (되돌리지 않고 끄는 법)

2a 에는 **HTTP 표면이 없다** — production 의 어느 경로도 V22 의 두 표를 읽거나 쓰지 않는다(알려진 제한 6, `app` 에 이
port 를 받는 빈이 없다). 그래서 끌 대상이 없고, 코드를 그대로 둔 채 두어도 거동이 바뀌지 않는다.

DB 쪽: 이 slice 는 **어떤 상주 DB 에도 마이그레이션을 적용하지 않았다**(실행 0 — `CleanMigration*Test` 가 버릴 컨테이너에
적용해 검사한다). 이미 적용한 DB 를 되돌리려면 두 표를 `DROP` 하고 Flyway 이력 행을 함께 지워야 한다(파일만 지우면
`validate()` 가 「적용됐는데 파일이 없다」로 깨진다). 두 표는 추가뿐이고 기존 표·V12 를 건드리지 않으므로 그 밖의 데이터
복구는 없다.

## ①~⑦ 실측 (임시 clone `scratchpad/7c4/rb`, 2026-10-24 18:3x~18:51 KST)

| # | 확인 | 결과 |
|---|---|---|
| ① | 복원 명령 exit | 0 |
| ② | `git status --short` 의 D · M 수 | **D 15 · M 10** |
| ②′ | 목록과 실제로 바뀐 경로의 `comm -3` | **0 줄**(같은 집합) |
| ③ | `git diff --quiet fc15975c -- <목록>` · 같은 명령 `--cached` | 둘 다 exit 0(복원된 경로가 base 와 바이트까지 같다) |
| ④⑤⑥ | 되돌린 트리에서 `./gradlew --no-daemon check --continue --no-build-cache` | **exit 0** · `BUILD SUCCESSFUL in 12m` · 365 task 전부 실행 · 루트 게이트 둘(`leakPatternGate` · `contractGate`) 실행 |
| ⑥′ | 되돌린 트리에 이 slice 의 test 결과가 있는가 | **0 건**(`profile` 결과 XML 없음 — 초록이 base 의 초록이고 내 test 가 섞인 초록이 아니다) |
| ⑦ | 공유 파일 표지 계수 | 내 표지 0(`ACCOUNT_SETTING` · `account_business_profile` · `account_construction_capacity` · `workflow.profile` · `BusinessProfile`) · 남의 표지 보존(`G1-SET01` 3 · `OpeningPromotion` 9 · `Suggestion` 7 · `7C①b` 6) |

⑦ 에서 `7C④` 문자열이 한 건 남는데, 그 줄은 **base 에도 있다**(추적표 SET-01 행이 입력 홀더로 7C④ 를 가리킨다 — g1set01 의
줄이다). 내 표지가 아니므로 보존이 맞다. 「내 줄 사라짐」과 「남의 줄 남음」을 둘 다 센 결과다.

## 실측 유효성 대조 (verifier 용)

`git diff --name-only 1b3ade0b..<판정 SHA> -- <위 목록>` 이 **빈 출력**이면 이 실측이 유효하다. 실측 뒤의 커밋은
`reports/evidence/m7/7c4/`(목록 밖)만 고친다.

## 복구 시간

명령 하나(수 초). 기동 중인 서비스가 없고 영속 데이터 복구도 없다.
