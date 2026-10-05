# M6/6D-1 — rollback 절차와 실측

실측 HEAD: `bc7f37d6`
base: `fd4629fe`

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 이 slice 의 in_scope 는 e2e test
디렉터리와 공유 파일 둘(`config/quality/gate-tests.properties` · `milestone-6.md`)이다 — 같은
브랜치의 계약 문서(`scope.md`)는 팀장 레인 소유라 되돌리지 않는다.

## ⓪ 복원 목록 — 기계 산출

```
git diff --name-status fd4629fe..bc7f37d6 -- \
  adapters/src/test/kotlin/bidvector/adapters/e2e \
  config/quality/gate-tests.properties milestone-6.md
```

산출: `A` 12건(신규 test 소스) · `M` 2건(공유 파일). 같은 범위의 전체 변경에서 이 목록과
evidence 경로를 뺀 차집합이 **빈 출력**이다 — 범위 밖 혼입 0.

## 공유 파일의 hunk 출처 — `git log` 산출

```
git log --oneline fd4629fe..bc7f37d6 -- config/quality/gate-tests.properties   # 커밋 둘
git log --oneline fd4629fe..bc7f37d6 -- milestone-6.md                         # 커밋 하나
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

## ①~⑥ 실측 (버릴 clone, `bc7f37d6` 체크아웃)

| 항목 | 결과 |
|---|---|
| ① 명령 exit | `git rm -r` 0 · hunk 역적용 셋 전부 0 |
| ② D/M 수 | D 12 · M 2 — 복원 목록과 같다 |
| ③ diff 빈 것 | `git diff --name-only fd4629fe -- <세 경로>` **빈 출력** — 되돌린 트리가 base 와 같다 |
| ④ compile | `check` 안의 컴파일 전부 통과 |
| ⑤ test | `check` 안의 test 전부 통과 |
| ⑥ 게이트 | `./gradlew --no-daemon check` BUILD SUCCESSFUL(9m 12s) · `./gradlew --no-daemon qualityBaseline` BUILD SUCCESSFUL |

확인은 **양방향**이다 — 「내 줄이 사라졌다」(③ 의 빈 diff)와 「남의 줄이 남았다」(되돌린 뒤 등재
파일의 식별자 줄 수가 base 와 같은 333, 다른 레인의 문서 커밋도 그대로)를 함께 봤다.

갈음 근거는 「HEAD 가 초록」이 아니라 **트리 동일성**이다 — 되돌린 트리의 세 경로가 base 와 바이트
동일함을 ③ 이 보이고, 그 위에서 ④⑤⑥ 을 다시 실측했다.

## 비활성화(되돌리지 않고 끄는 법)

이 slice 의 산출물은 test 와 등재뿐이라 **운영 거동을 끄는 스위치가 없다**. 실행만 멈추려면 등재
다섯 줄을 지우면 되지만, 그 순간 등재 등식 게이트가 즉시 붉어진다(모집단 ∖ 제외 ≠ 등재) — 즉
「조용히 끄는 길」이 없다. 끄려면 test 클래스 자체를 지우는 위 되돌림을 쓴다.
