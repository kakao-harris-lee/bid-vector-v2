# M6/G1-SET01 rollback — in_scope 경로 한정 복원

**실측 HEAD: `fb05a786`**(이 slice 의 **마지막 산출물 커밋**, 2026-10-23 rebase 뒤). base 는 rebase 뒤
`a61ddff1`. 아래 ①~⑥ 은 그 커밋을 체크아웃한 **임시 clone** 에서 실제로 돌린 결과다. 뒤따르는 evidence
커밋은 되돌림 대상이 아니다 — verifier 의 대조 술어는 `git diff --name-only fb05a786..<판정 SHA> -- <아래
복원 경로들>` 이 **빈 출력**인가다.

> **다시 잰 이유**(checklist §5 P-22): 앞 세션의 이 문서는 `8fc84e46` 에서 쟀는데 그 뒤 `70926fcb`
> (현 `2c16603d`)가 `CompanyName.kt` 를 바꿔, 문서가 스스로 건 위 대조 술어가 **거짓**이었다. rebase 로
> base 도 바뀌었으므로 마지막 산출물 커밋·새 base 로 전부 다시 쟀다. 옛 수치는 옮기지 않았다.

## 되돌리는 것 — 기계 산출 목록

목록은 손으로 쓰지 않는다. `git diff --name-status a61ddff1..HEAD` 에서 내고 **라운드마다
재산출**한다.

| 상태 | 경로 | 처분 |
|---|---|---|
| A ×18 · D ×1 · M ×1 | `settlement/**` | **디렉터리 통째로** base 복원(신규 18 삭제(main 4 · test 6 · fixture 8) · `ModuleBoundaryAnchor.kt` 되살림 · `build.gradle.kts` 되돌림) |
| M | `config/quality/gate-tests.properties` | base 로 복원(**공유 파일** — 새 키 `gate.tests.settlement` 만 사라진다) |
| M | `docs/discovery/capability-map.md` | base 로 복원(**공유 파일** — SET-01 절의 V2 구현 상태 bullet) |
| M | `reports/evidence/m6/6e1/acceptance-trace.md` | base 로 복원(**공유 파일** — 6E-1 산출물) |

**`settlement` 을 파일이 아니라 디렉터리로 든다.** in_scope 가 파일 하나를 이름으로 들면 같은
디렉터리의 다른 파일이 목록에서 빠지고, 그 누락은 ③(트리 동일성)이 **구조적으로 볼 수 없다**
— 비교 대상 집합이 복원 대상 집합과 같은 목록이기 때문이다(6G-2d 에서 두 번 실측). 디렉터리
pathspec 은 그 구멍을 닫는다.

## 되돌리지 않는 것

- `reports/evidence/m6/g1set01/**` — 이 slice 의 evidence 와 **팀장이 쓴 계약**(`scope.md`).
  관례대로 남긴다. 그 결과 복원한 트리에는 base 에 없는 문서가 남고, 그것이 ⑥-b 의 전제다.
- 하네스 경로(`CLAUDE.md`·`.claude/**`) — 이 range 에 변경 **없음**(`git diff --name-only
  a61ddff1..fb05a786 -- CLAUDE.md .claude` 빈 출력).

## 명령 — 머지 전 전제

이 slice 는 아직 main 에 없다. 그러므로 공유 파일 셋도 **이 브랜치의 base main 커밋에서**
복원한다(움직이는 `origin/main` 을 쓰지 않는다). 머지 **뒤**의 되돌림은 이 명령이 아니라 그 PR 의
머지 커밋 revert 다.

```sh
git restore --source=a61ddff1 --staged --worktree -- \
  settlement \
  config/quality/gate-tests.properties \
  docs/discovery/capability-map.md \
  reports/evidence/m6/6e1/acceptance-trace.md
```

경로는 **개별 인자**로 넘긴다(변수 하나로 묶으면 pathspec 이 하나가 된다). hunk 역적용은 쓰지
않는다 — 공유 파일 셋을 base 트리에서 통째로 가져오므로 남의 줄을 떨어뜨릴 수 없다(그 전제를 아래
③-b 가 잰다). **신규 파일에 별도 `git rm` 이 필요 없다** — `--source` 에 없는 경로는 이 명령이
지운다(실측 ②).

## 실측 — 임시 clone(`fb05a786`)에서 ①~⑥ (2026-10-23)

| # | 무엇 | 결과 |
|---|---|---|
| 전제 | base `a61ddff1` 가 HEAD 의 조상 · base 에 이 slice 의 첫 산출물 커밋(`45fedb17`) 없음 | 둘 다 실행 전에 단언 — **OK** |
| ① | 복원 명령 종료 코드 | **0** |
| ② | 그 명령이 낸 변경 수 | **A 1 · D 18 · M 4** — `ModuleBoundaryAnchor.kt` 되살림 · 신규 18 삭제 · 공유 파일 셋 + `settlement/build.gradle.kts` 복원 |
| ③ | base 대비 복원 경로 diff | **0 줄**(빈 출력) |
| ③-a | **내 표지 0** — 등재 장부의 새 키 | `gate.tests.settlement` **0** |
| ③-a | 추적표의 이 slice 표지 | `M6/G1-SET01` **0** |
| ③-a | capability map 의 이 slice 표지 | `M6/G1-SET01` **0**(신설 OPEN 행도 함께 사라진다) |
| ③-a | `settlement` main 의 `.kt` 수 | **1** == base(anchor 하나) · `ModuleBoundaryAnchor.kt` **존재** |
| ③-a | `settlement/src/test` 디렉터리 | **없음**(base 에 없다) |
| ③-b | **남의 표지 보존** — 등재 장부 줄 수 | **903** == base **903** |
| ③-b | `capability-map.md` 줄 수 | **3573** == base **3573** |
| ③-b | `acceptance-trace.md` 줄 수 | **175** == base **175** |
| ③-c | 되돌리지 않는 것이 base 와 **다른 채로 남는가** | `reports/evidence/m6/g1set01/` 의 넷(`scope.md`·`checklist.md`·`commands.md`·`rollback.md`, 커밋된 판) — 남는다 |
| ④⑤⑥ | 복원한 트리에서 **전건 `check`**(컴파일 · test · 게이트) | **exit 0** · BUILD SUCCESSFUL **10분 3초** · 359 actionable task(224 실행 · 135 캐시) · 잠금 대기 포함 벽시계 07:59:32~08:20:18 · HEAD 끝 `fb05a786` |
| ⑥-b | 그 트리에 이 slice 의 evidence 를 얹고 `leakPatternGate` 만 다시 | **exit 0** · BUILD SUCCESSFUL 14초 · 2026-10-23 10:20 KST — 복원 트리(`settlement` main `.kt` 1)에 `1934061d` 의 evidence 넷을 얹었다(`cmp` 로 넷 다 동일 확인) |

**③-b 가 왜 줄 수인가**: 공유 파일 셋은 다른 slice 의 줄을 담고 있다. 「내 줄이 사라졌다」(③-a)만
재면 남의 줄까지 함께 떨어뜨린 복원도 통과한다 — 두 방향을 모두 재야 복원이 **내 것만** 되돌렸다는
말이 선다. 줄 수가 base 와 정확히 같다는 것이 그 짝이다.

**③-a 의 「`settlement` main `.kt` 수」를 세는 이유**: 복원 목록이 디렉터리 pathspec 이라 파일 누락이
구조적으로 생길 수 없지만, 그 사실 자체를 **수로** 확인한다. 1 이 아니면 신규 파일이 남았거나 anchor
가 돌아오지 않은 것이다.

## 예상 복구 시간

명령 하나(수초) + 확인 명령 다섯. 재빌드가 필요하면 Kotlin `check` 한 번(이 호스트 실측 기준
아래 ④⑤⑥ 의 경과, 잠금 대기 제외).
