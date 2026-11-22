# M15 / 15I — rollback

**실측 HEAD: `4d29ba8d37ae82fdd564566c2dde1f93ba49ef17`**(이 slice 의 마지막 산출물 커밋 —
수정 라운드 1 의 고침 커밋). 앞 라운드의 실측을 옮기지 않고 **다시 돌렸다**.

## 전제

**이 slice 는 아직 `main` 에 없다.** 복원 원천은 착수 base `ff8e0cad42c5c114ee30b42ca4318252bbf35c5f`
이고 이 브랜치는 그 뒤로 main 을 병합하지 않았다(`git rev-list --merges <base>..<실측 HEAD>` 빈
출력). 움직이는 `origin/main` 을 쓰지 않는다. 단언 둘: base 가 HEAD 의 조상이다 · base 에 이
slice 의 첫 산출물 커밋이 없다(브랜치를 그 SHA 에서 끊었다). 복원 원천을 바꾸지 않았으므로
「셋째 단언」은 대상이 아니다. **머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 머지 커밋 revert**다.

**공유 파일에 남의 줄이 없다** — `git log --oneline <base>..HEAD -- <공유 파일 다섯>` 이 이
slice 의 커밋 넷만 낸다. hunk 격리가
필요한 자리가 아니다. 하네스 경로(`CLAUDE.md`·`.claude/**`)는 목록 밖이고 range 안에 그 커밋이 없다.

## 목록과 명령

목록은 손으로 쓰지 않는다 — `git diff --name-status <base>..HEAD` 가 낸다(A = 복원이 삭제 ·
M = base 로 되돌림). **라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

```
git restore --source=ff8e0cad42c5c114ee30b42ca4318252bbf35c5f --staged --worktree -- \
  build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateTask.kt \
  config/quality/leak-pattern-baseline.txt config/quality/leak-patterns.txt \
  docs/discovery/capability-map.md tools/db-rehearsal.sh \
  web/config/gate-tests.txt web/e2e/journey/privateValues.ts \
  web/e2e/journey/settings.spec.ts web/e2e/journey/onboarding.spec.ts \
  web/e2e/harness/journey-teardown.test.mjs web/e2e/journey/teardown.ts \
  reports/evidence/m15/15i
```

경로는 **개별 인자**다. `--source` 에 없는 경로는 삭제되므로 신규 파일에 `git rm` 이 필요 없다 —
`git checkout <base> -- <경로>` 는 쓰지 않는다. **예상 복구 시간**: 복원 1 분 + web job 재실행.

되돌아가는 것: 예외 칸 대조가 「얻는 쪽」과 「둘 다 빈 목록」에서 다시 조용해지고 · teardown 둘이
다시 메시지만 남기며 예외가 되돌림 순서를 끊고 · ⑤ 가 다시 첫 붉음에서 멈추고 · 사업자등록번호 꼴 축이 스캔에서 빠진다. 영속 상태도 설정 변경도
없으므로 데이터 정리가 따르지 않는다.

## 임시 clone 실측 — ①~⑥

저장소 밖 clone 을 실측 HEAD 에 두고 끝까지 실행했다. 트리 동일성(`write-tree` == base 트리)이
성립하지만 **그것으로 갈음하지 않고** ④⑤⑥ 을 실제로 돌렸다.

| # | 무엇 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 종료 코드 | **0** |
| ② | D/M 수 | **D 5 · M 9**(목록과 같다) |
| ③ | 복원 경로의 `git diff <base>` | **빈 출력**(0 줄) |
| ④ | 되돌린 트리의 compile — `npm run typecheck` | **0** |
| ⑤ | 되돌린 트리의 test — `npm run test` | **0**(75 파일 · 2028 test — 이 slice 의 새 test 파일이 빠진 수다) |
| ⑥ | 되돌린 트리의 게이트 — `npm run lint` · `npm run gate:tests` · `leakPatternGate` | **전부 0** |

⑥ 에 `leakPatternGate` 를 넣는 이유: 이 slice 가 그 게이트의 입력 둘(정책·허용 목록)과 KDoc 을 바꾼다. 되돌린 트리에서
evidence 디렉터리도 함께 사라지므로 base 의 허용 목록과 어긋나지 않는다(M4 가 밟은 자리).

## §4 술어 — 실측 HEAD 와 판정 SHA 사이

`git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 복원 경로들>` 이 빈 출력이면 유효하다. evidence
커밋은 언제나 뒤에 오므로 두 SHA 는 같을 수 없다 — 보는 것은 **되돌림 대상이 움직였는가**다.
움직인 경로가 나오면, 그것은 **전부 착수 원천(base)에 없는 신설 파일**이다(이 slice 의 evidence
디렉터리). 열거와 원천 부재 확인은 verifier 가 한다. `git rev-list --merges <실측 HEAD>..<판정 SHA>`
도 빈 출력이어야 한다.
