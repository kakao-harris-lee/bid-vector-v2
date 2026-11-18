# M13 / ux-worksheet (13C) — 되돌리기

**실측 HEAD: `9cc200a4`**(이 slice 의 **마지막 산출물 커밋** — 팀장 조건을 받아 라운드가 한 번 더
돌았고, 앞 판의 실측 HEAD `a9889db8` 은 그 라운드로 낡았다).
뒤에 오는 커밋은 evidence 뿐이고, 그 처분은 아래 「전칭식」에 있다.

## 되돌리는 방법 — range revert 가 아니라 **경로 한정 복원**

```
git restore --source=bd5cd039 --staged --worktree -- \
  web/src/ web/config/ web/e2e/ web/scripts/ reports/evidence/m13/ux-worksheet/
```

원천은 **착수 base 커밋**이다. 이 slice 는 아직 병합되지 않았으므로 원천이 움직이지 않는다
(머지 뒤에 되돌릴 일이 생기면 PR revert 가 그 자리다).

## 복원 목록 — 기계 산출

`git diff --name-status bd5cd039 9cc200a4` 가 낸 **28 경로**다(손으로 적지 않는다 · 라운드마다
재산출한다). 신설 **11**(`A`) · 수정 **17**(`M`) 이고, 전부 위 다섯 경로 아래에 있다 — in_scope
밖은 **0** 이다(같은 명령으로 대조했다).

신설 열하나: 하한 여유 모듈과 그 짝 test · 훑기 뿌리 게이트와 그 짝 test · 측정 공용 모듈 ·
합성 대조 spec · evidence 다섯.

## 실측 ①~⑥ — 버린 clone 에서

저장소 밖 clone 에 **실측 HEAD 를 checkout** 하고 위 명령을 그대로 돌렸다.

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령의 종료 코드 | **0** |
| ② | 되돌린 것의 계수 | **D 11 · M 17**(= 목록 28 과 같다) |
| ③ | base 와의 diff | **0 바이트**(트리 동일성 — 「HEAD 가 초록이다」로 갈음하지 않는다) |
| ④ | 컴파일 | `typecheck` 0 · `build` 0 · `lint` 0 |
| ⑤ | test | **51 파일 / 1169 통과**(산출물 HEAD 의 53 / 1301 과 갈린다 — 되돌림이 실제로 일어났다는 계수다) |
| ⑥ | 게이트 | `gate:tests` 0 · `gate:src-tree` 0 · `gate:boundary-canary` 0 |

Playwright 레인(셸 스모크 · 여정)은 이 clone 에서 돌리지 않았다. 복원된 트리가 base 와 **바이트로
같고**(③), base 는 CI 5/5 초록으로 병합된 main 커밋이다.

## 전칭식 — 실측 뒤에 움직인 것

실측 HEAD 이후 판정 SHA 까지 **복원 경로 안에서 바뀐 파일은 evidence 뿐**이고, 그 파일들은 전부
**원천(`bd5cd039`)에 없는 신설 파일**이다. 그래서 복원이 지울 뿐 되살릴 base 판이 없고, 이 측정은
그대로 유효하다. 열거와 「원천 부재」 확인은 verifier 가 한다.

그 사이에 **병합 커밋은 0** 이다(`git rev-list --merges 9cc200a4..<판정 SHA>` 가 빈 출력이다).

## 되돌림이 건드리지 않는 것

- `docs/usecase/captures/` — 캡처는 이 slice 가 **커밋하지 않았다**(앞·뒤 묶음은 레인 scratch 에
  있고, 저장소의 캡처는 실행 뒤 트리를 되돌려 손대지 않았다).
- 서버(Kotlin) · OpenAPI · 의존성 — 변경 0 이라 되돌릴 것이 없다.
