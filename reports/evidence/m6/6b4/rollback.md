# M6/6B-4 되돌림

실측 HEAD: `e118ced780294b49480327de6fed03ed68465c4e` (이 slice 의 **마지막 산출물 커밋**)

base: `e2232a75`. 브랜치 `m6-6b4/2026-10-13`.

**전제 둘(실행 전 단언).**
- 이 brach 는 base 이후 **main 을 병합하지 않았다** — `git log --merges --oneline e2232a75..HEAD`
  빈 출력. 그래서 `base..HEAD` 의 모든 커밋이 이 slice 의 것이고(비병합 커밋 수는 라운드마다
  늘어난다 — 아래 목록은 **커밋 수가 아니라 경로 집합**으로 산출한다),
  공유 파일에도 **다른 slice 의 줄이 없다**. 「병합된 main SHA 에서 복원」(PR #69 규칙)이
  필요한 조건이 성립하지 않으므로 전부 `--source=e2232a75` 로 복원한다. 병합이 생기면 이 절을
  다시 산출해야 한다.
- 되돌림은 **머지 전**을 전제한다. 머지 뒤의 되돌림은 그 PR 의 머지 커밋 revert 다 — 같은
  복원 명령은 머지 순간 아무것도 지우지 않으면서 exit 0 을 낸다.

**하네스 경로는 되돌리지 않는다.** `git log --oneline e2232a75..HEAD -- CLAUDE.md .claude/` 가
**빈 출력**이다(이 range 에 하네스 레인 커밋 없음) — 되돌릴 것도 없다.

## 목록 — 기계 산출(손으로 쓰지 않는다)

```
git diff --name-status e2232a75..HEAD
```

**라운드마다 파일이 늘면 이 절차를 다시 돌린다.** 목록이 낡는 것이 이 결함의 실제 원인이다.
base 가 움직이면(rebase·흡수 병합) 재산출 기준도 함께 움직인다.

실측 HEAD(`e118ced7`)에서의 산출: **35 경로** — `A` 12(삭제 대상) · `M` 23(base 로 복원).
`A` 열둘: 저널 값·코덱 모듈 하나 · 저널 **파일** 모듈 하나 · Python test 다섯 · runbook 하나 ·
이 slice 의 evidence 넷(scope·commands·checklist·rollback).
`M` 스물셋: Kotlin 계약 규칙·계약 test · proto · docker 둘 · ADR · 정책 YAML · Python src 여섯 ·
Python test 일곱 · `policy-values.md`.

**앞 라운드(실측 HEAD `18069139`)의 목록은 34 였다.** 늘어난 하나는
`ml-engine/src/ml_engine/training/jobs/journal_file.py`(vr M-1 수정이 설계 래칫 500줄을 넘겨
파일 접근을 떼어낸 모듈)이고 in_scope 안이다. 「라운드마다 재산출」을 이 자리에서 실제로 돌린
결과다 — 앞 실측을 옮기지 않았다.

**이 slice 의 evidence 디렉터리도 되돌림 대상에 넣는다**(`reports/evidence/m6/6b4/**`). 남겨
두면 base 의 누출 어휘 baseline 에 없는 문서가 트리에 남아 `leakPatternGate` 가 붉어진다
(M4 실측 계열). 전부 되돌리면 그 조건이 생기지 않는다 — 아래 ⑥ 가 그것을 실측했다.

## 명령 — in_scope 경로 한정(range revert 가 아니다)

```
git restore --source=e2232a75 --staged --worktree -- <위 목록의 35 경로를 개별 인자로>
git commit -m "revert(m6-6b4): in_scope 경로를 base 로 되돌림" -- <같은 35 경로>
```

**복원과 커밋이 한 쌍이다** — 미커밋 복원은 `contracts/` 의 작업 트리를 더럽혀 `contractGate`
를 붉힌다(아래 ⑥ 보완 경로의 실측).

- 목록을 변수 하나에 담지 않는다(pathspec 이 하나가 되어 아무것도 매치하지 않는다).
- `git checkout <base> -- <경로>` 를 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로
  exit 1 이고 아무것도 적용되지 않는다.
- 신규 파일에 별도 `git rm` 이 **필요 없다** — `--source` 에 없는 경로는 `restore` 가 지운다
  (아래 ② 의 `D` 12 가 그 실측이다).

## 되돌린 뒤 남는 것 — 저널 파일

저널 형식은 이 slice 가 처음 만든 파일이라, 되돌린 코드에는 그것을 읽는 자리가 없다(모듈
둘 — 값·코덱과 파일 — 이 함께 사라진다).
런타임 디렉터리(`<ML_ENGINE_ARTIFACT_OUT_DIR>/_journal/`)에 남은 저널 파일은 **무시된다** —
저장소가 프로세스 로컬 dict 로 복귀하고(복원 뒤 `InMemoryJobStore` 가 다시 존재함을 실측),
그 파일을 읽지도 쓰지도 않는다. 지우지 않아도 무해하다. compose 볼륨도 되돌림 뒤에는 선언이
사라지므로 다음 `up` 에서 그 경로가 컨테이너 로컬로 돌아간다(기존 거동).

## 임시 clone 실측 ①~⑥

저장소 밖 clone(`--shared`)에서 실측 HEAD 를 checkout 한 뒤 위 명령을 **그대로 실행**했다.
dry-run 성립 확인은 실행 가능성을 증명하지 않는다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **exit 0** |
| ② | 되돌린 뒤 base 대비 `D`/`M` 수 | **D 12 · M 23**(목록의 A 12 · M 23 과 일치) |
| ③ | in_scope 경로의 `git diff <base> -- <경로들>` | **빈 출력**(0 줄) |
| ④⑤⑥ | 되돌린 트리의 compile·test·게이트 | **트리 동일성으로 갈음** — 아래 절 |

### ④~⑥ 의 갈음은 「HEAD 초록」이 아니라 **트리 동일성**이다

복원+커밋 뒤 되돌린 트리의 해시가 **base 의 트리 해시와 같다**:

```
git rev-parse HEAD^{tree}        # 되돌린 트리
git rev-parse e2232a75^{tree}    # base 트리
```

실측: 둘 다 `c8d8f211c7d25c8b708c2393d8792ce0fc084360` — **같은 트리**. 앞 두 라운드(실측 HEAD
`54bbef81`·`18069139`)의 되돌린 트리도 같은 해시였고, 그 트리에서 ④~⑥ 을 **실제로 돌려** 아래
값을 얻었다. 세 라운드의 되돌린 트리가 같은 객체이므로 그 결과가 그대로 유효하다(종료 코드를
옮긴 것이 아니라 **대상 트리가 동일**). 되돌림이 덮는 경로가 늘었어도(34 → 35) 늘어난 경로가
`A` 라서 되돌린 트리는 바뀌지 않는다 — 그 사실이 해시 일치로 다시 보인다.

앞 라운드에서 그 트리에 대해 실측한 값 — `mypy --strict src/ml_engine` **exit 0**(97 files,
저널 모듈이 사라져 하나 줄었다) · `pytest tests -q` **exit 0** 1420 passed ·
`ruff check`+`format --check`·`lint-imports`·`design_ratchet --check`·재활용 출처 대조
**전부 exit 0** · Kotlin `./gradlew --no-daemon check` 첫 실행 **exit 1**(아래 보완 경로) →
보완 뒤 **exit 0**, BUILD SUCCESSFUL 9m 2s.

### ⑥ 의 보완 경로 — **복원을 커밋해야 초록이 된다**

첫 실행은 `:contractGate` 로 떨어졌다: 그 게이트가 `contracts/` 의 **작업 트리가 비어 있을
것**을 요구하는데, `git restore` 는 변경을 **미커밋 상태로** 남기므로 복원 자체가 그 게이트를
붉힌다(`M contracts/proto/bidvector/ml/v1/training.proto`). 게이트의 결함이 아니라 절차의
미완이다 — 되돌림은 미커밋 편집이 아니라 **커밋**으로 끝나야 한다.

그래서 절차에 한 단계를 더한다(실측 완료):

```
git commit -m "revert(m6-6b4): in_scope 경로를 base 로 되돌림" -- <같은 목록>
```

커밋 뒤 `git status --porcelain` **빈 출력** · 되돌린 트리 해시 = base 트리 해시
(`c8d8f211…`) · Kotlin `check` **exit 0**. 갈음의 판정은 「HEAD 가 초록」이 아니라
**트리 동일성**이고, 그 해시 일치가 그것이다. 이 보완 단계는 **세 라운드 모두에서** 실제로
실행했다(각각 exit 0).

`contracts/` 를 만지는 slice 의 되돌림은 전부 이 단계를 필요로 한다 — 복원만 하고 `check` 를
돌리면 **되돌림이 맞았는데도** 붉다.

**「내 줄이 사라졌다」와 「남의 줄이 남았다」 둘 다**: 이 range 에 다른 slice 의 커밋이 없어
(위 전제) 후자의 대상이 **없다**. 그래서 계수는 전자뿐이다 — ③ 의 빈 diff 가 그것이고,
**빈 diff 가 곧 건전성의 증거는 아니므로** ② 의 D/M 수가 목록과 **같은 수**인지를 함께 세었다
(목록이 낡아 경로가 빠지면 D/M 수가 모자라게 나온다).

**목록이 in_scope 를 넘지 않는지도 함께 세었다**(수정 라운드가 만든 새 파일 ↔ in_scope 대조):
35 경로 전부가 `scope.md` in_scope 패턴 안이고 밖은 **0** 이다. 기계 대조 — 목록의 각 경로를
in_scope 패턴에 넣어 매치 없는 줄을 출력하게 했고 출력이 비었다.

## 실측 유효성 대조(verifier 용)

실측 HEAD 와 판정 SHA 가 다른 것은 구조적이다(evidence 커밋은 언제나 마지막 산출물 커밋 뒤에
온다). 대조할 것은 **그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 목록의 35 경로>
```

빈 출력이면 유효, 한 줄이라도 나오면 미검증. 제외 목록을 따로 관리하지 않고 **이 문서의 목록
자신**을 대조 경로로 쓴다(되돌림이 덮는 집합과 술어의 집합이 같아야 한다).

**이 술어는 빈 출력이 아닐 수 있다**(verifier r2 L-4 가 앞 라운드에서 실측했다): evidence
커밋이 `reports/evidence/m5/5e/policy-values.md` 처럼 **되돌림 대상인 공유 파일**을 함께
고치면 그 파일이 출력에 나온다. 그때 미검증으로 끝내지 않고 **판정 SHA 에서 다시 재는** 것이
절차다 — 되돌림은 경로 집합이 같으면 같은 트리를 만들므로, 재측정의 결론은 트리 해시
동일성이다. 아래 ①~⑥ 은 이 라운드의 **마지막 산출물 커밋**에서 돌린 결과이고, 그 뒤 evidence
커밋이 무엇을 고쳤는지는 이 절의 명령이 그대로 보여 준다.

## 복구 시간

in_scope 복원은 수 초. 되돌린 트리의 확인까지 포함하면 Python 축 약 7분(test 포함) + Kotlin
`check` 약 10분.
