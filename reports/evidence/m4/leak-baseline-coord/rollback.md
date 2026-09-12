# rollback.md — leak-baseline-coord

## 대상 (기계 산출)

`git diff --name-status a6ab6a8..HEAD` (2026-09-12T01:23Z 실행):

```
M	build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateChecks.kt
M	build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateTask.kt
M	build-logic/src/test/kotlin/bidvector/buildlogic/LeakPatternGateChecksTest.kt
A	reports/evidence/m4/leak-baseline-coord/scope.md
M	config/quality/leak-pattern-baseline.txt
```

`reports/evidence/m4/leak-baseline-coord/scope.md`(A)와 이 evidence 디렉터리는 되돌리지
않는다 — 계약·증적 문서이지 이 slice 가 되돌려야 할 "신규 wiring"이 아니다. 대상은
in_scope 4개 파일(전부 M — base 에 이미 존재)뿐이다.

이 range 에 **하네스 레인 커밋 없음**(`git log --oneline a6ab6a8..HEAD -- CLAUDE.md
.claude/` 결과 없음, 2026-09-12 확인 — scope.md 「하네스 레인 변경: 없음」과 일치).
공유 파일(다른 slice 와 겹치는 파일) 없음 — 4개 파일 모두 이 slice 단독 커밋
(`1ef5d73`) 하나에서만 움직였다. hunk 격리 절차 불필요.

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

이 slice 는 커밋을 더 늘리지 않았다(단일 구현 커밋 `1ef5d73`). 수정 라운드가 생겨
in_scope 파일이 늘면, 위 `git diff --name-status a6ab6a8..HEAD` 를 다시 실행해 목록을
갱신하고 이 절차를 다시 밟는다.
