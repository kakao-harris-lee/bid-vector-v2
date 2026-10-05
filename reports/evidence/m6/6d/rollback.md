# M6/6D-1 — rollback 절차와 실측

실측 HEAD: `e7ef9ffd`(복원·hunk 대상을 마지막으로 건드린 커밋 — 6D-1 종결 문단 `7a946be3` + 빈 줄 `e7ef9ffd`, `milestone-6.md` 만). 두 앵커: **⓪~③d 는 `e7ef9ffd`**(아래 「팀장 재실측」), **①~⑥ 레인 실측은 `697a231b`**(마지막 산출물 커밋 — 복원 12·등재 파일은 그 뒤 이동 0: `git diff --name-only 697a231b..e7ef9ffd -- adapters/src/test/kotlin/bidvector/adapters/e2e config/quality/gate-tests.properties` 빈 출력)
base: `fd4629fe`

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 이 slice 의 in_scope 는 e2e test
디렉터리와 공유 파일 둘(`config/quality/gate-tests.properties` · `milestone-6.md`)이다 — 같은
브랜치의 계약 문서(`scope.md`)는 팀장 레인 소유라 되돌리지 않는다.

## ⓪ 복원 목록 — 기계 산출

```
git diff --name-status fd4629fe..697a231b -- \
  adapters/src/test/kotlin/bidvector/adapters/e2e \
  config/quality/gate-tests.properties milestone-6.md
```

산출: `A` 12건(신규 test 소스) · `M` 2건(공유 파일). 같은 범위의 전체 변경에서 이 목록과
evidence 경로를 뺀 차집합이 **빈 출력**이다 — 범위 밖 혼입 0.

## 공유 파일의 hunk 출처 — `git log` 산출

```
git log --oneline fd4629fe..697a231b -- config/quality/gate-tests.properties   # 커밋 둘
git log --oneline fd4629fe..697a231b -- milestone-6.md                         # 커밋 하나
```

등재 파일은 **두 단계**다(최초 등재와 사다리 test 추가). 역적용은 **최신 커밋부터 거꾸로** 해야
맞물린다. 마일스톤 문서는 6D 분할·착수 문단 한 덩이다.

## 되돌림 절차

1. **신규 파일 12건** — 디렉터리째 제거한다. base 에 없던 경로라 `git restore --source` 의 대상이
   아니다.

```
git rm -r adapters/src/test/kotlin/bidvector/adapters/e2e
```

2. **공유 파일 둘** — 커밋 해시 hunk 격리로 각 커밋의 변경만 역적용한다. 파일 전체를 base 로
   되돌리지 않는다(다른 레인이 같은 파일을 건드렸을 수 있다).

```
git diff efef53e4~1..efef53e4 -- config/quality/gate-tests.properties | git apply -R
git diff 428551f3~1..428551f3 -- config/quality/gate-tests.properties | git apply -R
git diff b42900d5~1..b42900d5 -- milestone-6.md | git apply -R
```

`--3way` 가 자동 해소에 실패하면 수동 절차는 이렇다 — 등재 파일에서는 `bidvector.adapters.e2e.`
로 시작하는 다섯 줄과 그 바로 앞 머리말 문단(이 slice 가 더한 주석)을 지운다. 마일스톤 문서에서는
6D 분할·6D-1 착수 문단 한 덩이를 지운다. 셋 다 **더하기만 한 변경**이라 지우는 것으로 충분하고
남의 줄은 건드리지 않는다.

3. 두 단계 뒤 인덱스를 푼다(`git reset`).

## ①~⑥ 실측 (버릴 clone, `697a231b` 체크아웃)

| 항목 | 결과 |
|---|---|
| ① 명령 exit | `git rm -r` 0 · hunk 역적용 셋 전부 0 |
| ② D/M 수 | D 12 · M 2 — 복원 목록과 같다 |
| ③ diff 빈 것 | `git diff --name-only fd4629fe -- <세 경로>` **빈 출력** — 되돌린 트리가 base 와 같다 |
| ④ compile | `check` 안의 컴파일 전부 통과 |
| ⑤ test | `check` 안의 test 전부 통과 |
| ⑥ 게이트 | `./gradlew --no-daemon check` BUILD SUCCESSFUL(9m 08s) · `./gradlew --no-daemon qualityBaseline` BUILD SUCCESSFUL |

확인은 **양방향**이다 — 「내 줄이 사라졌다」(③ 의 빈 diff)와 「남의 줄이 남았다」(되돌린 뒤 등재
파일의 식별자 줄 수가 base 와 같은 333, 다른 레인의 문서 커밋도 그대로)를 함께 봤다.

갈음 근거는 「HEAD 가 초록」이 아니라 **트리 동일성**이다 — 되돌린 트리의 세 경로가 base 와 바이트
동일함을 ③ 이 보이고, 그 위에서 ④⑤⑥ 을 다시 실측했다.

**갈음을 쓰지 않고 실제로 돌렸다.** 되돌린 트리가 base 와 다른 경로는 이 slice 의 evidence·계약
마크다운 넷뿐이지만, 그중 evidence 디렉터리는 **누출 패턴 게이트의 선언된 입력**이라(`quality-baseline`
규약) 「입력이 같으니 base 결과로 갈음한다」가 성립하지 않는다. 그래서 두 명령을 그대로 다시 돌렸다.

## 비활성화(되돌리지 않고 끄는 법)

이 slice 의 산출물은 test 와 등재뿐이라 **운영 거동을 끄는 스위치가 없다**. 실행만 멈추려면 등재
다섯 줄을 지우면 되지만, 그 순간 등재 등식 게이트가 즉시 붉어진다(모집단 ∖ 제외 ≠ 등재) — 즉
「조용히 끄는 길」이 없다. 끄려면 test 클래스 자체를 지우는 위 되돌림을 쓴다.

## 팀장 재실측 @`e7ef9ffd` (6D-1 종결 문단 커밋, 2026-10-05)

종결 문단 둘(`7a946be3`·`e7ef9ffd`)이 `milestone-6.md`(hunk 대상)를 움직였으므로 버릴 clone 에서 ⓪~③d 를 다시 쟀다. 복원 12·등재 파일은 `697a231b` 뒤 이동 0 이라 ④~⑥ 은 위 레인 실측(되돌린 트리 base 동일 · `check`·`qualityBaseline` exit 0)이 그대로 유효하다.

| # | 확인 | 결과 |
|---|---|---|
| ⓪ | 복원 목록 기계 산출(`git diff --name-status fd4629fe..697a231b -- <e2e 디렉터리>`) | 12 A — 위 목록과 같음 |
| ①② | `git rm -r <e2e 디렉터리>` | exit 0 · D 12 |
| ③b | hunk — `git log --format=%h fd4629fe..HEAD -- <파일>` 산출: `gate-tests.properties` **둘** · `milestone-6.md` **셋**(`e7ef9ffd` → `7a946be3` → `b42900d5`)을 최신부터 역적용 | apply exit 0 · conflict 0 · 두 파일 `git diff fd4629fe` 빈 출력 |
| ③c | 내 줄 사라짐 · 남의 줄 남음 | 6D-1 언급 HEAD 2 → 0(= base) · 6B-2 4/4 · 등재 줄 323/323(= base) |
| ③d | 트리 동일성 | base 와 다른 추적 파일은 `reports/evidence/m6/6d/` 넷뿐(의도) |
