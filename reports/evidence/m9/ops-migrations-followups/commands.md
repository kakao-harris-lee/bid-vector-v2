# M9 / ops-migrations-followups — 명령과 종료 코드

acceptance 정본은 `scope.md` 의 두 줄(CI Kotlin job 명령 그대로). 아래는 실행 순서대로의 실측이다.
**마지막 HEAD 의 게이트 결과 정본은 evidence 가 아니라 verifier 와 PR 조치 코멘트**다(evidence 는
자기 마지막 커밋의 post-state 를 담을 수 없다).

빌드는 전부 `~/.local/bin/bv-build.sh` 경유(호스트 동시 둘)이고, 시작 전 `free -m` ·
`pgrep -af 'GradleWrapperMain|GradleWorkerMain'` 를 **별도 호출로 먼저** 봤다.

## 1. 고정점 test 의 양성 대조가 공허했다 — 쌍 둘은 이미 평평하다

| 실측 | 결과 |
| --- | --- |
| `:adapters:test` 표적 둘 `--rerun`(쌍 **둘**로 심은 양성 대조) | **BUILD FAILED** — 고정점 test 2 중 1 실패. 심은 셋이 **하나도** 잡히지 않았다(관측 `[]`) |

이 실패가 이 slice 의 첫 진단이다. 버릴 postgres 컨테이너에서 렌더링을 직접 재서 사유를 가렸다
(`docker run --rm --network none postgres:16.4` + `psql`, 세 번):

| 심은 식 | 렌더링이 자기 고정점인가 |
| --- | --- |
| 쌍 **둘**의 행 값 비교(CHECK) | **예** — 저장 트리가 인자 둘의 OR 하나라 렌더링이 이미 평평하다 |
| 쌍 **셋**의 행 값 비교 — CHECK | **아니오** — 왼쪽 깊은 중첩으로 찍히고 재파싱이 평평하게 접는다 |
| 쌍 **셋** — EXCLUDE 의 `WHERE` | **아니오** |
| 쌍 **셋** — 트리거의 `WHEN` | **아니오** |
| 손으로 중첩해 쓴 OR 사슬 · 스칼라 `IN` 목록 | 예(파서가 텍스트의 중첩을 먼저 평평하게 접는다 — 접힐 식을 만드는 것은 **행 값 비교의 전개**다) |

그래서 양성 대조의 목록을 **쌍 셋**으로 고쳤다. 쌍 둘로 둔 대조는 「심었는데 아무것도 안 잡힌다」로
**조용히 공허**해지고, 그 공허는 모집단이 빠진 것과 구별되지 않는다. V26 이 CI `container` job 에서
걸린 자리는 쌍 일곱이었다.

## 2. 표적 test

| 실측 | 결과 |
| --- | --- |
| (채움) | |

## 3. 변이 — 새 판이 구판이 놓친 결함을 잡는가

버릴 clone(`git clone --no-hardlinks`, detached)에서 재고 **worktree 는 읽기만** 했다.

| 실측 | 결과 |
| --- | --- |
| (채움) | |

## 4. acceptance 전건

| 실측 | 결과 |
| --- | --- |
| (채움) | |

## 5. 비밀값 스캔 · clean-tree

| 실측 | 결과 |
| --- | --- |
| (채움) | |
