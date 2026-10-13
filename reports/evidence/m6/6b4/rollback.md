# M6/6B-4 되돌림

실측 HEAD: `54bbef8182ca747ddb3dc1780f36792762977610` (이 slice 의 **마지막 산출물 커밋**)

base: `e2232a75`. 브랜치 `m6-6b4/2026-10-13`.

**전제 둘(실행 전 단언).**
- 이 brach 는 base 이후 **main 을 병합하지 않았다** — `git log --merges --oneline e2232a75..HEAD`
  빈 출력. 그래서 `base..HEAD` 의 모든 커밋이 이 slice 의 것이고(아래 range 커밋 여덟),
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

실측 HEAD 에서의 산출: **30 경로** — `A` 8(삭제 대상) · `M` 22(base 로 복원).
`A` 여덟: 저널 모듈 하나 · Python test 넷 · runbook 하나 · 이 slice 의 evidence 둘.
`M` 스물둘: Kotlin 계약 규칙·계약 test · proto · docker 둘 · ADR · 정책 YAML · Python src 여섯 ·
Python test 여섯 · `policy-values.md`.

**이 slice 의 evidence 디렉터리도 되돌림 대상에 넣는다**(`reports/evidence/m6/6b4/**`). 남겨
두면 base 의 누출 어휘 baseline 에 없는 문서가 트리에 남아 `leakPatternGate` 가 붉어진다
(M4 실측 계열). 전부 되돌리면 그 조건이 생기지 않는다 — 아래 ⑥ 가 그것을 실측했다.

## 명령 — in_scope 경로 한정(range revert 가 아니다)

```
git restore --source=e2232a75 --staged --worktree -- <위 목록의 30 경로를 개별 인자로>
git commit -m "revert(m6-6b4): in_scope 경로를 base 로 되돌림" -- <같은 30 경로>
```

**복원과 커밋이 한 쌍이다** — 미커밋 복원은 `contracts/` 의 작업 트리를 더럽혀 `contractGate`
를 붉힌다(아래 ⑥ 보완 경로의 실측).

- 목록을 변수 하나에 담지 않는다(pathspec 이 하나가 되어 아무것도 매치하지 않는다).
- `git checkout <base> -- <경로>` 를 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로
  exit 1 이고 아무것도 적용되지 않는다.
- 신규 파일에 별도 `git rm` 이 **필요 없다** — `--source` 에 없는 경로는 `restore` 가 지운다
  (아래 ② 의 `D` 8 이 그 실측이다).

## 되돌린 뒤 남는 것 — 저널 파일

저널 형식은 이 slice 가 처음 만든 파일이라, 되돌린 코드에는 그것을 읽는 자리가 없다.
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
| ② | 되돌린 뒤 base 대비 `D`/`M` 수 | **D 8 · M 22**(목록의 A 8 · M 22 와 일치) |
| ③ | in_scope 경로의 `git diff <base> -- <경로들>` | **빈 출력**(0 줄) |
| ④ | 되돌린 트리의 타입 검사(Python 의 compile 축) — `mypy --strict src/ml_engine` | **exit 0**(97 files — 저널 모듈이 사라져 하나 줄었다) |
| ⑤ | 되돌린 트리의 test — `pytest tests -q` | **exit 0**, 1420 passed |
| ⑥ | 되돌린 트리의 게이트 — Python 축 | `ruff check`+`format --check` **exit 0** · `lint-imports` **exit 0** · `design_ratchet --check` **exit 0** · 재활용 출처 대조 **exit 0** |
| ⑥ | 되돌린 트리의 게이트 — Kotlin `./gradlew --no-daemon check` | 첫 실행 **exit 1**(아래 보완 경로) → 보완 뒤 **exit 0**, BUILD SUCCESSFUL 9m 2s |

### ⑥ 의 보완 경로 — **복원을 커밋해야 초록이 된다**

첫 실행은 `:contractGate` 로 떨어졌다: 그 게이트가 `contracts/` 의 **작업 트리가 비어 있을
것**을 요구하는데, `git restore` 는 변경을 **미커밋 상태로** 남기므로 복원 자체가 그 게이트를
붉힌다(`M contracts/proto/bidvector/ml/v1/training.proto`). 게이트의 결함이 아니라 절차의
미완이다 — 되돌림은 미커밋 편집이 아니라 **커밋**으로 끝나야 한다.

그래서 절차에 한 단계를 더한다(실측 완료):

```
git commit -m "revert(m6-6b4): in_scope 경로를 base 로 되돌림" -- <같은 30 경로>
```

커밋 뒤 `git status --porcelain` **빈 출력** · `git diff --stat e2232a75..HEAD` **빈 출력**
(= base 와 **트리가 동일**) · Kotlin `check` **exit 0**. 갈음의 판정은 「HEAD 가 초록」이 아니라
**트리 동일성**이고, 그 빈 diff 가 그것이다.

`contracts/` 를 만지는 slice 의 되돌림은 전부 이 단계를 필요로 한다 — 복원만 하고 `check` 를
돌리면 **되돌림이 맞았는데도** 붉다.

**「내 줄이 사라졌다」와 「남의 줄이 남았다」 둘 다**: 이 range 에 다른 slice 의 커밋이 없어
(위 전제) 후자의 대상이 **없다**. 그래서 계수는 전자뿐이다 — ③ 의 빈 diff 가 그것이고,
**빈 diff 가 곧 건전성의 증거는 아니므로** ② 의 D/M 수가 목록과 **같은 수**인지를 함께 세었다
(목록이 낡아 경로가 빠지면 D/M 수가 모자라게 나온다).

## 실측 유효성 대조(verifier 용)

실측 HEAD 와 판정 SHA 가 다른 것은 구조적이다(evidence 커밋은 언제나 마지막 산출물 커밋 뒤에
온다). 대조할 것은 **그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 목록의 30 경로>
```

빈 출력이면 유효, 한 줄이라도 나오면 미검증. 제외 목록을 따로 관리하지 않고 **이 문서의 목록
자신**을 대조 경로로 쓴다(되돌림이 덮는 집합과 술어의 집합이 같아야 한다).

## 복구 시간

in_scope 복원은 수 초. 되돌린 트리의 확인까지 포함하면 Python 축 약 7분(test 포함) + Kotlin
`check` 약 10분.
