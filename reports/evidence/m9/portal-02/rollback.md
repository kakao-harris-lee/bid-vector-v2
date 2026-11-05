# M9 / portal-02 — 되돌림

- base: main `79f3e5c4` · **실측 HEAD: `7f51bdc0`**(main 병합 커밋 — 이 slice 의 마지막 산출물 자리)
- **공유 파일 복원 원천: `319fe212`**(이 브랜치에 **실제로 병합한** main 커밋, 고정 SHA). 팀장 지시는
  `4953d743` 이었으나 `fetch` 와 `merge` 사이에 공유 ref `origin/main` 이 움직여 `319fe212`(PR #124
  머지)가 병합됐다. 원천을 `4953d743` 으로 두면 #124 의 줄까지 지운다 — 그래서 **병합된 SHA** 를 쓴다.
  움직이는 ref `origin/main` 은 쓰지 않는다.
- 전제(둘 다 실측): **①** `319fe212` 가 HEAD 의 조상이다 **②** `319fe212` 에 이 slice 의 첫 산출물
  커밋(`c6d492e9`)이 **없다** — main 으로부터 만든 트리라 남의 줄을 떨어뜨릴 수 없고 해시 목록이 필요
  없다(뒤에 붙는 커밋까지 덮는다). 머지된 뒤의 되돌림은 이 절차가 아니라 **그 PR 의 revert** 다(머지
  순간 같은 명령은 아무것도 지우지 않으면서 exit 0 이다 — 성공하면서 뜻을 잃는다).

## 재빌드 없는 되돌림 (먼저 쓸 것)

전환은 설정 값이라 **인자만으로** 옛 서비스로 돌아간다(옛 URL 이 공지일 기준 90일 — ≈2027-02-01 —
동안 살아 있을 때만 참이다).

**인자 목록의 정본은 `app/src/test/resources/rollback/koneps-portal-02-rollback-args.txt`** 이고
`docs/runbook/m6-6g-real-collection.md` §8-4 와 `KonepsRollbackArgumentsTest` 가 **그 한 파일**을 읽는다.
목록을 여기 베껴 적지 않는다 — 베끼면 한쪽만 바뀌는 날이 오고, 그 결함이 바로 아래 적는 것이다.

**행 전수여야 한다(cr H-1).** 앞 판의 이 절은 여섯 인자를 적었고 그 가운데 넷이
`…operations.<업종>.<칸 하나>` 꼴이었다. `operations` 두 축은 값 객체의 Map 이고 행 타입의 칸이 전부
필수라, 한 칸만 주면 Spring 이 그 행을 그 칸만으로 구성해 나머지가 null 이 되고 **프로세스가 뜨지
않는다**. 실측(그 목록 그대로 기동): `parameter division` 과 `parameter openingResultListPath` 가
null 이라는 바인딩 실패. 그래서 낙찰정보서비스 경로 둘도 **같은 값으로 다시** 적는다 — 그 축이
바뀌는 것이 아니라 행 전체가 교체되기 때문이다. `KonepsRollbackArgumentsTest` 가 ⓐ 정본 목록으로 두
배선이 뜨고 여섯 경로가 옛 이름인지 ⓑ 행의 한 칸만 주는 꼴이 기동 실패인지를 함께 잠근다.

## 코드 되돌림 — in_scope 경로 한정

목록은 손으로 쓰지 않는다. `git diff --name-status 79f3e5c4..HEAD` 에서 기계로 내고
(`A` = 삭제 대상, `M` = base 로 복원), **라운드마다 다시 낸다** — 파일이 늘면 이 절차를 재실행한다.

```
git restore --source=319fe212 --staged --worktree -- \
  app/src/main/kotlin/bidvector/app/collection/CollectionProperties.kt \
  app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt \
  app/src/test/kotlin/bidvector/app/collection \
  app/src/test/kotlin/bidvector/app/wiring \
  adapters/src/test/kotlin/bidvector/adapters/koneps \
  config/quality \
  docs/runbook/m6-6g-real-collection.md \
  docs/discovery/capability-map.md \
  web/e2e/harness
```

`git checkout <base> --` 를 쓰지 않는다 — base 에 없는 신규 경로에서 pathspec 오류로 아무것도 적용되지 않는다.

**되돌리지 않는 것**: 이 evidence 디렉터리(`reports/evidence/m9/portal-02/`). 복원 목록 **밖**이고,
그래서 아래 실측은 그 디렉터리를 남긴 채 돌렸다(`leakPatternGate` 가 `reports/evidence` 를 선언된
입력으로 잡으므로 「base 결과로 갈음」이 서지 않는다). 하네스 경로(`CLAUDE.md`·`.claude/**`)는 이 range
에 커밋이 없다.

**공유 파일에 남의 줄이 있다 — 그래서 원천이 `319fe212` 다.** 내 비-병합 커밋이 만진 경로와 main 이
만진 경로의 교집합은 **둘**이다(`comm -12`): `config/quality/gate-tests.properties` ·
`docs/discovery/capability-map.md`. 그 둘을 **병합된 main SHA 에서 복원**하면 결과가 곧 「이 slice 만
걷어낸 상태」이고 남의 줄은 **구성상** 보존된다 — hunk 격리처럼 고를 필요가 없고, 병합 커밋의 충돌
해소 줄이 `--no-merges` 목록 밖이라 걷히지 않는 함정도 없다. 내 세 web 하네스 파일을 main 이 만진
커밋은 0 이라 그 파일들에는 남의 줄이 애초에 없다.

**그래서 확인은 두 계수다** — 「내 표지 0」 **그리고** 「남의 표지 보존」. 종료 코드만 보면 반쯤
되돌린 트리도 exit 0 이다.

## 실측 (버릴 clone, 실측 HEAD `7f51bdc0`)

| 단계 | 결과 |
| --- | --- |
| ① 명령 exit | 0 |
| ② D/M 수 | D 5 · M 12 |
| ③ 결과 트리 동일성 | `git diff 319fe212 --name-only -- app adapters config docs web` **빈 출력** — 되돌린 트리가 병합한 main 과 바이트 동일 |
| 내 표지 계수 | 전부 0/없음 — 기본값 `BidPublicInfoService02` · 라우팅 표 파일 · 게이트 등재 `KonepsEndpointDefaultsTest` · capability map `OPEN-M9-PORTAL-02-SPLIT-OPERATIONS` · fixture `Cnstwk02` · 준비 대기 표지 `FAKE_KONEPS_IDENTITY_HEADER` |
| 남의 표지 보존 | #124 의 게이트 등재 `StrategyBudgetClearEndpointTest` **1** · #124 의 capability map `OPEN-6F4-STR02-REQUIREMENT` **2** · #125 의 `web/e2e/journey/shared.ts` **있음** · 옛 probe 이름 **1**(복원됨) |
| ④ compile · ⑤ test · ⑥ 게이트 | **트리 동일성으로 갈음**(팀장 결정 2026-11-05, 호스트 빌드 큐 포화). 위 ③ 이 빈 출력이므로 되돌린 트리는 **병합한 main `319fe212` 그 자체**이고(차이는 이 evidence 디렉터리뿐), 그 SHA 의 게이트는 main 의 것이다 — 측정 시점에 `web` **success**, `check`·`container`·`ml-engine` 은 **진행 중**이었다(이 slice 밖의 신호라 이 레인이 기다리지 않는다). 앞 라운드에서는 같은 복원을 `24bd2803` 에서 돌려 `./gradlew --no-daemon check` **exit 0**(30m 37s)을 **직접** 실측했다 — 그때의 되돌린 트리는 당시 base 와 동일했고, 지금 다른 것은 main 이 그 뒤 움직였다는 사실뿐이다. evidence 디렉터리를 입력으로 잡는 게이트는 `leakPatternGate` 하나이고 이 slice 의 evidence 는 참조형 누출 스캔에서 0건이다 |

복원 목록 등식: 내 **비-병합** 커밋의 경로(`git log --no-merges --format= --name-only 79f3e5c4..HEAD
^319fe212`)에서 복원 인자가 덮는 것을 빼면 **이 evidence 디렉터리 넷만** 남는다(= 복원 목록 밖으로
선언한 것). **병합 뒤에는 범위 diff 로 내 경로를 낼 수 없다** — `^<병합한 main SHA>` 를 더해야 한다.
그걸 빼먹으면 남의 slice 경로 열아홉이 내 목록에 섞인다(이 slice 에서 실제로 한 번 섞였고, 커밋
목록을 눈으로 보고 잡았다). **이 등식이 비어 있다는 것만으로 목록이 건전하다고
읽지 않는다** — 물어야 하는 것은 「내가 커밋하지 않아도 이 집합에 경로가 들어오는가」다. 이 slice 의
답은 **들어온다**: 위 공유 파일 셋을 다른 레인이 머지하면 들어오므로, 그 발동 조건의 처분을 위에 미리 적었다.

## 유효성

verifier 가 볼 것은 「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 온다). **실측 HEAD 와
판정 SHA 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only 7f51bdc0..<판정 SHA> -- <위 복원 인자들>
```

빈 출력이면 유효하고, 한 줄이라도 나오면 미검증이다. 이 slice 는 실측 뒤 evidence 세 파일만 커밋했다.
