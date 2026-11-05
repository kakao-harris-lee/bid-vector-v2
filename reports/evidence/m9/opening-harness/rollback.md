# M9 / opening-harness — 되돌림

- base: main `df4976cc` · **실측 HEAD: `255f7459`**(이 slice 의 마지막 산출물 커밋)
- **복원 원천: `df4976cc`** — 이 브랜치에는 **병합 커밋이 0** 이므로 그 SHA 가 첫 산출물 커밋의
  부모 그 자체다. 움직이는 ref `origin/main` 은 쓰지 않는다.
- 전제(둘 다 실측): **①** `df4976cc` 가 HEAD 의 조상이다 **②** 그 SHA 에 이 slice 의 첫 산출물
  커밋이 **없다**(그 커밋의 부모이므로). 머지된 뒤의 되돌림은 이 절차가 아니라 **그 PR 의
  revert** 다 — 머지 순간 같은 명령은 아무것도 지우지 않으면서 exit 0 이다.

## 재빌드 없는 되돌림 (먼저 쓸 것)

이 slice 는 **하네스와 e2e 뿐**이라 출하 산출물에 되돌릴 거동이 없다. 개찰 축 걸음만 끄려면 그
두 러너를 부르지 않으면 되고, 그 자리는 하네스 한 파일의 걸음 둘이다(출하 jar 는 그 속성이
없으면 배선 전체가 올라오지 않는다 — `@ConditionalOnProperty`).

## 코드 되돌림 — in_scope 경로 한정

목록은 손으로 쓰지 않는다. `git diff --name-status df4976cc..HEAD` 에서 기계로 내고
(`A` = 삭제 대상, `M` = base 로 복원) **라운드마다 다시 낸다**.

```
git restore --source=df4976cc --staged --worktree -- \
  web/config/gate-tests.txt \
  web/e2e/harness \
  web/e2e/journey
```

`git checkout <base> --` 를 쓰지 않는다 — base 에 없는 신규 경로에서 pathspec 오류로 아무것도
적용되지 않는다.

**되돌리지 않는 것**: 이 evidence 디렉터리(복원 목록 **밖**이고, 그래서 아래 실측은 그 디렉터리를
남긴 채 돌렸다). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range 에 커밋이 **0** 이다.

**남의 줄이 같은 파일에 있는가**: 내 비-병합 커밋이 만진 경로와 main 이 만진 경로의 교집합은
이 range 에서 **빈 집합**이다(병합 커밋 0 · 착수 뒤 main 병합 0). 그래서 「병합된 main SHA 에서
복원」이 곧 「이 slice 만 걷어낸 상태」이고 남의 줄은 **구성상** 보존된다. **이 빈 등식을 건전성의
증거로 읽지 않는다** — 물어야 하는 것은 「내가 커밋하지 않아도 이 집합에 경로가 들어오는가」이고
이 slice 의 답은 **들어온다**: 내가 만지는 여섯 공유 파일(`gate-tests.txt` · `shared.ts` ·
`results.spec.ts` · `fake-koneps.mjs` · `notice-list.json` · `journey-up.mjs`)은 다른 레인도 만지는
자리이고, **PR #130 이 그 가운데 셋을 이미 바꾸는 중**이다. 그 셋이 머지되면 원천을 **병합된 main
커밋 SHA** 로 옮겨 이 절차를 다시 산출해야 한다(그러지 않으면 남의 줄까지 지운다).

**확인은 두 계수다** — 「내 표지 0」 **그리고** 「남의 표지 보존」. 종료 코드만 보면 반쯤 되돌린
트리도 exit 0 이다.

## 실측 (버릴 clone, 실측 HEAD `255f7459`)

| 단계 | 결과 |
| --- | --- |
| ① 명령 exit | **0** |
| ② D/M 수 | **D 3 · M 6** (= 내 web 경로 아홉. 새 파일 셋이 삭제, 기존 여섯이 복원) |
| ③ 결과 트리 동일성 | `git diff df4976cc --name-only -- web` **빈 출력** — 되돌린 `web` 트리가 base 와 바이트 동일 |
| 내 표지 계수 | 전부 **0** — 설계 공고 접두 · 설계 모듈 이름 · 맥락 칸 이름 · 여정 문면 표 이름 · fake 의 라우팅 변수 · 업종 유도 함수 · 승격 모드 속성 |
| 남의 표지 보존 | 게이트 등재 `screen-card-coverage` **1** · `shared.ts` 의 `expectedKstText` **1** · `results.spec.ts` 의 `RECONCILED_OUTCOME_LABEL` **2** · 공고 축 fixture 의 `why_fourth_notice` **1** · `journey-up.mjs` 의 `acquireSingleRunLock` **2** |
| ④ compile · ⑤ test · ⑥ 게이트 | 되돌린 트리에서 **직접 실측**: `npm run lint` exit 0 · `npm run typecheck` exit 0 · `npm run test` **762 passed / 38 files**(내 게이트 test 19 가 사라진 수) · `npm run gate:tests` 통과 · `playwright --list` **11 tests / 4 files**(내 test 하나가 사라진 수). Kotlin `check` 는 이 range 에 Kotlin 변경이 **0** 이라 대상이 아니다 |

복원 목록 등식: 내 비-병합 커밋의 경로(`git log --no-merges --format= --name-only HEAD
^df4976cc | sort -u`, **11**)에서 복원 인자가 덮는 것을 빼면 **이 evidence 디렉터리 둘만** 남는다
(= 복원 목록 밖으로 선언한 것). 그 둘을 뺀 아홉이 위 ② 의 D 3 + M 6 과 같다.

## 유효성

verifier 가 볼 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다).
**실측 HEAD 와 판정 SHA 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only 255f7459..<판정 SHA> -- web/config/gate-tests.txt web/e2e/harness web/e2e/journey
```

빈 출력이면 유효하고, 한 줄이라도 나오면 미검증이다. 이 slice 는 실측 뒤 **이 evidence 디렉터리의
파일만** 커밋했고 그 디렉터리는 복원 목록 밖이다.
