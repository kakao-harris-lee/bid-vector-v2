# M6/6D-1 — rollback 절차와 실측

실측 HEAD: `428551f3`
base: `fd4629fe`

이 slice 의 산출물 커밋은 **하나**(`428551f3`)이고, 그 커밋의 변경 전수가 곧 되돌림 대상이다.
range revert 가 아니라 **경로 한정** 복원이다 — 같은 브랜치의 앞선 문서 커밋(계약·마일스톤)은
건드리지 않는다.

## ⓪ 복원 목록 — 기계 산출

```
git diff --name-status fd4629fe..428551f3 -- \
  adapters/src/test/kotlin/bidvector/adapters/e2e config/quality/gate-tests.properties
```

산출: `A` 9건(신규 test 소스) · `M` 1건(공유 등재 파일). 같은 커밋의 전체 변경
(`git diff --name-status 428551f3~1..428551f3`)과 **양방향 `comm` 이 빈 출력** — 범위 밖 혼입 0.

## 되돌림 절차

1. **신규 파일 9건** — 디렉터리째 제거한다. base 에 없던 경로라 `git restore --source` 의 대상이
   아니다.

```
git rm -r adapters/src/test/kotlin/bidvector/adapters/e2e
```

2. **공유 파일 1건** — 커밋 해시 hunk 격리로 이 커밋의 변경만 역적용한다. 다른 레인이 같은 파일을
   건드렸을 수 있으므로 파일 전체를 base 로 되돌리지 않는다. hunk 출처 목록은
   `git log --oneline fd4629fe..428551f3 -- config/quality/gate-tests.properties` 로 산출하며,
   이 slice 에서는 `428551f3` 하나다.

```
git diff 428551f3~1..428551f3 -- config/quality/gate-tests.properties | git apply -R
```

`--3way` 가 자동 해소에 실패하면 수동 절차는 이렇다 — 해당 파일에서 `gate.tests.adapters` 값의
`bidvector.adapters.e2e.` 로 시작하는 네 줄과 그 바로 앞 머리말 문단(이 slice 가 더한 네 줄
주석)을 지운다. 두 덩이 다 **더하기만 한 변경**이라 지우는 것으로 충분하고, 남의 줄은 건드리지
않는다.

3. 두 단계 뒤 인덱스를 푼다(`git reset`).

## ①~⑥ 실측 (버릴 clone, `428551f3` 체크아웃)

| 항목 | 결과 |
|---|---|
| ① 명령 exit | `git rm -r` exit 0 · hunk 역적용 exit 0 |
| ② D/M 수 | D 9 · M 1 — 복원 목록과 같다 |
| ③ diff 빈 것 | `git diff --name-only fd4629fe -- <두 경로>` **빈 출력** — 되돌린 트리가 base 와 같다 |
| ④ compile | `check` 안의 컴파일 전부 통과 |
| ⑤ test | `check` 안의 test 전부 통과 |
| ⑥ 게이트 | `./gradlew --no-daemon check` BUILD SUCCESSFUL · `./gradlew --no-daemon qualityBaseline` BUILD SUCCESSFUL |

확인은 **양방향**이다 — 「내 줄이 사라졌다」(③ 의 빈 diff)와 「남의 줄이 남았다」(되돌린 뒤에도
`gate.tests.adapters` 의 나머지 등재와 머리말 이력 문단이 그대로, 다른 레인의 문서 커밋도 그대로)를
함께 봤다.

갈음 근거는 「HEAD 가 초록」이 아니라 **트리 동일성**이다 — 되돌린 트리의 두 경로가 base 와
바이트 동일함을 ③ 이 보이고, 그 위에서 ④⑤⑥ 을 다시 실측했다.

## 비활성화(되돌리지 않고 끄는 법)

이 slice 의 산출물은 test 와 등재뿐이라 **운영 거동을 끄는 스위치가 없다**. 실행만 멈추려면 등재
네 줄을 지우면 되지만, 그 순간 등재 등식 게이트가 즉시 붉어진다(모집단 ∖ 제외 ≠ 등재) — 즉
「조용히 끄는 길」이 없다. 끄려면 test 클래스 자체를 지우는 위 되돌림을 쓴다.
