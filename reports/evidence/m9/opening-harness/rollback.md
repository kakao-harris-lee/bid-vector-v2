# M9 / opening-harness — 되돌림

- 착수 base `df4976cc` · **실측 HEAD: `dcb63891`**(main 병합 커밋 — 이 slice 의 마지막 산출물 자리)
- **공유 파일 복원 원천: `8b3b610c`**(이 브랜치에 **실제로 병합한** main 커밋, 고정 SHA — PR #130
  머지). 착수 base `df4976cc` 를 원천으로 두면 **#130 의 줄까지 지운다**. 움직이는 ref
  `origin/main` 은 쓰지 않는다.
- 전제(둘 다 실측): **①** `8b3b610c` 가 HEAD 의 조상이다 **②** 그 SHA 에 이 slice 의 첫 산출물
  커밋(`32f7025e`)이 **없다** — main 으로부터 만든 트리라 남의 줄을 떨어뜨릴 수 없고 커밋 해시
  목록이 필요 없다(뒤에 붙는 커밋까지 덮는다). 머지된 뒤의 되돌림은 이 절차가 아니라 **그 PR 의
  revert** 다(머지 순간 같은 명령은 아무것도 지우지 않으면서 exit 0 이다 — 성공하면서 뜻을 잃는다).

## 재빌드 없는 되돌림 (먼저 쓸 것)

이 slice 는 **하네스와 e2e 뿐**이라 출하 산출물에 되돌릴 거동이 없다. 개찰 축 걸음만 끄려면 그 두
러너를 부르지 않으면 되고, 그 자리는 하네스 한 파일의 걸음 둘이다(출하 jar 는 그 속성이 없으면
배선 전체가 올라오지 않는다 — `@ConditionalOnProperty`).

## 코드 되돌림 — in_scope 경로 한정

목록은 손으로 쓰지 않는다. `git diff --name-status 8b3b610c..HEAD` 에서 기계로 내고
(`A` = 삭제 대상, `M` = 원천으로 복원) **라운드마다 다시 낸다**.

```
git restore --source=8b3b610c --staged --worktree -- \
  web/config/gate-tests.txt \
  web/e2e/harness \
  web/e2e/journey
```

`git checkout <base> --` 를 쓰지 않는다 — base 에 없는 신규 경로에서 pathspec 오류로 아무것도
적용되지 않는다.

**되돌리지 않는 것**: 이 evidence 디렉터리(복원 목록 **밖**이고, 그래서 아래 실측은 그 디렉터리를
남긴 채 돌렸다). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range 에 커밋이 **0** 이다.

**병합 커밋의 해소 줄은 `--no-merges` 목록 밖이다.** 이 slice 의 `02` 사본 갱신과 fixture 모양 검사는
**병합 커밋 안에만** 있으므로 그 목록만 믿으면 빠진다. 그래서 두 집합을 함께 냈다 —
`git log --no-merges --format= --name-only HEAD ^8b3b610c`(**14 경로**)와
`git diff --name-only dcb63891^1 dcb63891 -- web`(**6 경로**)이고, 후자는 전자의 **진부분집합**이라
위 복원 인자 셋이 둘 다 덮는다. 덮는다는 것은 추론이 아니라 아래 ③ 의 트리 동일성으로 실측했다.

**남의 줄이 같은 파일에 있다 — 그래서 원천이 `8b3b610c` 다.** 내 비-병합 커밋이 만진 경로와 main 이
만진 경로의 교집합은 **셋**이다(`comm -12`): `web/e2e/harness/fake-koneps.mjs` ·
`web/e2e/harness/fixtures/notice-list.json` · `web/e2e/harness/journey-up.mjs`. 그 셋을 **병합된 main
SHA 에서 복원**하면 결과가 곧 「이 slice 만 걷어낸 상태」이고 남의 줄은 **구성상** 보존된다 — hunk
격리처럼 고를 필요가 없고, 병합 커밋의 충돌 해소 줄이 걷히지 않는 함정도 없다.
`web/config/gate-tests.txt` 와 `web/e2e/journey/**` 는 main 이 이 range 에서 만지지 않았다(#130 이
만진 게이트 등록부는 Kotlin 쪽 `config/quality/gate-tests.properties` 로 **다른 파일**이다).

**확인은 두 계수다** — 「내 표지 0」 **그리고** 「남의 표지 보존」. 종료 코드만 보면 반쯤 되돌린
트리도 exit 0 이다.

## 실측 (버릴 clone, 실측 HEAD `dcb63891`)

| 단계 | 결과 |
| --- | --- |
| ① 명령 exit | **0** |
| ② D/M 수 | **D 3 · M 6** (= 내 web 경로 아홉. 새 파일 셋이 삭제, 공유 여섯이 복원) |
| ③ 결과 트리 동일성 | `git diff 8b3b610c --name-only -- web` **빈 출력**. 저장소 **전체**로도 남는 차이가 이 evidence 다섯뿐 — 되돌린 트리가 병합한 main 과 바이트 동일하다 |
| 내 표지 계수 | 전부 **0** — 설계 공고 접두 · 설계 모듈 이름 · 맥락 칸 이름 · 여정 문면 표 이름 · fake 의 라우팅 변수 · 업종 유도 함수 · 승격 모드 속성 · 계수 등식 함수 |
| 남의 표지 보존 | #130 의 준비 표지 **3** · 섬긴 집합 표지 **2** · `served.add` **1** · 직접 실행만 듣기 **1** · `journey-up` 의 섬긴 집합 탐침 **3** · 공고 축 fixture 의 `…Cnstwk02` 항목 **4**(= 복원 뒤 항목 수 4) |
| ④ compile · ⑤ test · ⑥ 게이트 | 되돌린 트리에서 **직접 실측**: `npm run lint` exit 0 · `npm run typecheck` exit 0 · `npm run test` **762 passed / 38 files** · `npm run gate:tests` 통과 · `playwright --list` **11 tests / 4 files**(내 추가가 빠진 수). Kotlin `check` 는 이 range 에 **내** Kotlin 변경이 0 이라 대상이 아니다 |

**「내 여덟이 걷히고 #130 의 넷이 남는다」가 이 라운드의 핵심 계수다** — 같은 파일 안에서 두 slice 의
줄이 섞인 첫 라운드이고, 복원이 그 둘을 가른 것을 **항목 수**로 셌다(종료 코드로는 보이지 않는다).

복원 목록 등식: 내 비-병합 커밋의 경로(14)에서 복원 인자가 덮는 것을 빼면 **이 evidence 디렉터리
다섯만** 남는다(= 복원 목록 밖으로 선언한 것). 그 다섯을 뺀 아홉이 위 ② 의 D 3 + M 6 과 같다.
**이 등식이 비어 있다는 것만으로 목록이 건전하다고 읽지 않는다** — 물어야 하는 것은 「내가 커밋하지
않아도 이 집합에 경로가 들어오는가」이고 답은 **들어온다**: 위 교집합 셋이 그 증거이며, 다른 레인이
이 셋을 또 머지하면 원천을 **그 새 병합 SHA** 로 옮겨 이 절차를 다시 산출해야 한다.

## 유효성

verifier 가 볼 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다).
**실측 HEAD 와 판정 SHA 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only dcb63891..<판정 SHA> -- web/config/gate-tests.txt web/e2e/harness web/e2e/journey
```

빈 출력이면 유효하고, 한 줄이라도 나오면 미검증이다. 이 slice 는 실측 뒤 **이 evidence 디렉터리의
파일만** 커밋했고 그 디렉터리는 복원 목록 밖이다.
