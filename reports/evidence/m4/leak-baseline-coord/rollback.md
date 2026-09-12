# rollback.md — leak-baseline-coord

## 대상 (기계 산출)

`git diff --name-status a6ab6a8..HEAD` (2026-09-12T02:10Z 재실행 — verifier L-2, 이전
기록은 신규 evidence 문서 3개가 늘어난 라운드를 반영하지 못했다):

```
M	build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateChecks.kt
M	build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateTask.kt
M	build-logic/src/test/kotlin/bidvector/buildlogic/LeakPatternGateChecksTest.kt
M	config/quality/leak-pattern-baseline.txt
A	reports/evidence/m4/leak-baseline-coord/checklist.md
A	reports/evidence/m4/leak-baseline-coord/commands.md
A	reports/evidence/m4/leak-baseline-coord/rollback.md
A	reports/evidence/m4/leak-baseline-coord/scope.md
```

`reports/evidence/m4/leak-baseline-coord/` 아래 4개 evidence 문서(전부 A)는 되돌리지
않는다 — 계약·증적 문서이지 이 slice 가 되돌려야 할 "신규 wiring"이 아니다. 대상은
in_scope 4개 파일(전부 M — base 에 이미 존재)뿐이다. **복원 명령 자체는 이전 기록과
동일하게 유효**하다(파일 전체 복원이라 그 파일이 몇 개 커밋에 걸쳐 움직였는지와 무관) —
낡은 것은 목록이지 명령이 아니었다.

`config/quality/leak-pattern-baseline.txt` 는 이 range 안에서 **두 커밋**(`1ef5d73` 키
형식 전환, `2684e08` evidence 자기매치 등재)에 걸쳐 움직였다(이전 기록은 "단일 커밋
`1ef5d73`"으로 적어 낡아 있었다 — verifier L-2). 이 slice 를 마무리하는 현재 라운드는
자기매치 신규분이 없어 baseline 파일을 추가로 건드리지 않았다(`git diff --name-status
a6ab6a8 -- config/quality/leak-pattern-baseline.txt` 는 여전히 M 하나).

이 range 에 **하네스 레인 커밋 없음**(`git log --oneline a6ab6a8..HEAD -- CLAUDE.md
.claude/` 결과 없음, 2026-09-12 확인 — scope.md 「하네스 레인 변경: 없음」과 일치).
공유 파일(다른 slice 와 겹치는 파일) 없음 — in_scope 4개 파일 모두 이 slice 의 커밋들
(`1ef5d73`·`2684e08`·이번 라운드 커밋)에서만 움직였다. hunk 격리 절차 불필요.

## 되돌리는 방법

```bash
git restore --source=a6ab6a8 --staged --worktree -- \
  build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateChecks.kt \
  build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateTask.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/LeakPatternGateChecksTest.kt \
  config/quality/leak-pattern-baseline.txt
```

4개 파일 전부 base(a6ab6a8)에 이미 존재하는 M 이므로 `--source` 만으로 충분하고 별도
`git rm` 이 필요 없다. **코드와 baseline 은 반드시 같은 명령으로 함께 되돌린다** — 한쪽만
되돌리면 키 형식이 불일치해(코드는 신형 키를 기대하는데 baseline 은 구형, 또는 그
반대) 전 항목이 `new` 로 잡혀 게이트가 전면 실패한다.

## 예상 복구 시간

수 초 — 위 명령 실행 후 커밋 하나로 되돌리면 끝난다(빌드 재실행 시간 별도).

## 검증 — 임시 clone 실측 (2026-09-12T01:23Z)

```bash
git clone --no-hardlinks <repo> <scratch>/lbc/rollback-clone
cd <scratch>/lbc/rollback-clone && git checkout gate/leak-baseline-coord
git restore --source=a6ab6a8 --staged --worktree -- <위 4개 경로>
git diff --name-status a6ab6a8 -- <위 4개 경로>   # 결과 없음 = base 와 완전 일치
./gradlew :build-logic:compileTestKotlin --no-daemon   # exit 0
./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' leakPatternGate --no-daemon   # exit 0
```

결과: `git diff --name-status a6ab6a8 -- <경로들>` 출력 없음(완전 일치) ·
`compileTestKotlin` exit 0 · `test`+`leakPatternGate` exit 0(되돌린 옛 코드 + 옛 baseline
조합이 정상 동작 — 좌표 키 체계로 완전 복귀). 확인 후 clone 삭제.

## 확인 지점 요약

- in_scope 4개 경로의 `git diff <base> -- <경로>` 가 비어 있다.
- 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range 에 애초에 변경이 없어 rollback 대상이 아니다.
- 되돌린 트리에서 `:build-logic:compileTestKotlin` exit 0, `:build-logic:test`
  (`LeakPatternGateChecksTest`) 와 `leakPatternGate` 둘 다 exit 0.

## 라운드 증가 시 재실행

이 slice 는 구현 이후 두 라운드(`2684e08` evidence 자기매치 baseline 등재, 이번 라운드의
F-6 코드 시정 + 장부층 정정)를 더 거쳤다 — 그때마다 이 절의 목록이 한 라운드씩 낡았다
(verifier L-2). in_scope 파일 집합 자체는 구현 착수 이후 늘지 않았으므로(항상 같은 4개
파일), 다음 라운드에서도 위 `git diff --name-status a6ab6a8..HEAD` 를 다시 실행해 evidence
문서 개수만 확인하고 이 절차를 다시 밟는다.
