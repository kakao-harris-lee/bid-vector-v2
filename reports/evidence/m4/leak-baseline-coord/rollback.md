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
in_scope 4개 파일(전부 M — base 에 이미 존재)뿐이다. 복원 명령 자체(파일 4개를
`--source=a6ab6a8` 로 되돌리는 것)는 base 와 바이트 단위로 일치함을 재실측했다 — 아래
"검증" 절 참조. **다만 이 문서를 재검증하는 과정에서 새로 발견한 것이 있다**: 코드+
baseline 만 되돌리고 evidence 문서를 그대로 두면 `leakPatternGate`(따라서 `check`)는
그 자리에서 다시 붉어진다 — 아래 "검증" 절과 새 알려진 제한 참조. 낡았던 것은 파일
목록만이 아니라 그 검증 결과의 서술이기도 했다.

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

## 검증 — 임시 clone 실측 (2026-09-12T02:15Z 재실측, 이전 기록의 leakPatternGate 결과를 정정)

```bash
git clone --no-hardlinks <repo> <scratch>/lbc/rollback-clone
cd <scratch>/lbc/rollback-clone && git checkout gate/leak-baseline-coord
git restore --source=a6ab6a8 --staged --worktree -- <위 4개 경로>
git diff --name-status a6ab6a8 -- <위 4개 경로>   # 결과 없음 = base 와 완전 일치
./gradlew :build-logic:compileTestKotlin --no-daemon   # exit 0
./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*' --no-daemon   # exit 0
./gradlew leakPatternGate --no-daemon   # exit 1 — 아래 참조
```

결과: `git diff --name-status a6ab6a8 -- <경로들>` 출력 없음(완전 일치) ·
`compileTestKotlin` exit 0 · 단위 test exit 0. **`leakPatternGate` 는 exit 1** —
`reports/evidence/m4/leak-baseline-coord/checklist.md`·`commands.md` 의 현재 내용이
`new:`로 잡힌다. 이전 기록("test+leakPatternGate exit 0")은 **부정확했다** — 재현해
보니 이 slice의 첫 evidence 커밋(`49c0a03`) 시점부터 이미 실패했다(별도 clone 에서
`49c0a03`·`37de8bb`·이번 라운드 세 시점 모두 같은 실패를 재현). 원인은 코드 결함이
아니라 구조적이다: base(a6ab6a8)의 legacy baseline 에는 이 slice 의 evidence 디렉터리
자체가 **존재하지 않으므로** 그 안의 자기매치(패턴 어휘 인용)에 대응하는 항목이
처음부터 없다 — 어떤 시점의 evidence 내용을 갖다 둬도 코드+baseline 만 되돌리면
`new` 로 잡힌다. 확인 후 clone 삭제.

## 확인 지점 요약

- in_scope 4개 경로의 `git diff <base> -- <경로>` 가 비어 있다.
- 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range 에 애초에 변경이 없어 rollback 대상이 아니다.
- 되돌린 트리에서 `:build-logic:compileTestKotlin` exit 0, `:build-logic:test`
  (`LeakPatternGateChecksTest`) exit 0.
- **`leakPatternGate`(따라서 전건 `check`)는 이 복원 절차만으로는 exit 0 이 되지 않는다**
  — 아래 알려진 제한 참조. "복원 뒤 빌드가 선다"는 compile·단위 test 범위에서만 참이다.

## 알려진 제한 — 이 절차만으로는 `leakPatternGate` 가 다시 통과하지 않는다

이 문서의 되돌리는 방법(4개 in_scope 파일 복원)은 **코드·baseline 형식을 legacy 로
되돌리는 것**만 보장한다. 실제로 게이트가 다시 초록이 되려면 다음 중 하나가 더
필요하다 — 이 slice 는 어느 쪽도 자동화하지 않는다:

- `reports/evidence/m4/leak-baseline-coord/` 전체도 함께 제거·복원한다(이 slice 가
  존재하지 않던 상태로 완전히 되돌린다), 또는
- 그 디렉터리의 현재 자기매치 내용에 대응하는 legacy 형식(`경로:줄번호`) 항목을
  `config/quality/leak-pattern-baseline.txt` 에 손으로 추가한다.

이 한계는 **이 라운드가 만든 것이 아니다** — 첫 evidence 커밋(`49c0a03`)부터 존재했고,
이전 rollback.md 는 이를 검증하지 않은 채 "exit 0"으로 잘못 기록했다(위 "검증" 절).
회귀는 아니다(base 에 이 slice의 evidence 가 없었으니 legacy 항목도 있을 수 없다) —
다만 rollback 절차가 "무엇을 되돌리면 빌드가 다시 서는가"를 compile·단위 test 범위로만
증명했지 게이트까지는 증명하지 못했다는 점은 이 slice 가 닫지 않는다.

## 라운드 증가 시 재실행

이 slice 는 구현 이후 두 라운드(`2684e08` evidence 자기매치 baseline 등재, 이번 라운드의
F-6 코드 시정 + 장부층 정정)를 더 거쳤다 — 그때마다 이 절의 목록이 한 라운드씩 낡았다
(verifier L-2). in_scope 파일 집합 자체는 구현 착수 이후 늘지 않았으므로(항상 같은 4개
파일), 다음 라운드에서도 위 `git diff --name-status a6ab6a8..HEAD` 를 다시 실행해 evidence
문서 개수만 확인하고 이 절차를 다시 밟는다.
