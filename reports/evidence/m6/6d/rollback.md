# M6/6D-1 — rollback 절차와 실측

실측 HEAD: `98d3a430`(마지막 산출물 커밋)
base: `fd4629fe`

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 이 slice 의 in_scope 는 e2e test
디렉터리와 공유 파일 둘(`config/quality/gate-tests.properties` · `milestone-6.md`)이다 — 같은
브랜치의 계약 문서(`scope.md`)는 팀장 레인 소유라 되돌리지 않는다.

## ⓪ 복원 목록 — 기계 산출과 양방향 등식

```
IN="adapters/src/test/kotlin/bidvector/adapters/e2e config/quality/gate-tests.properties milestone-6.md"
git diff --name-status fd4629fe..98d3a430 -- $IN
git diff --name-only fd4629fe..98d3a430 | sort > all.txt
git diff --name-only fd4629fe..98d3a430 -- $IN reports/evidence/m6/6d | sort > covered.txt
comm -23 all.txt covered.txt     # 전체 ∖ 덮개 — 빈 출력
comm -13 all.txt covered.txt     # 덮개 ∖ 전체 — 빈 출력
```

산출: `A` 12건(신규 test 소스) · `M` 2건(공유 파일). `comm` **양쪽 다 빈 출력** — 목록이 덮지 못한
변경도, 실재하지 않는 목록 항목도 없다.

## 공유 파일의 hunk 출처 — `git log` 산출

```
git log --format=%h fd4629fe..98d3a430 -- config/quality/gate-tests.properties   # 최신부터
git log --format=%h fd4629fe..98d3a430 -- milestone-6.md                         # 최신부터
```

**목록을 문서에 박지 않는다** — 라운드마다 커밋이 늘어 하드코드는 바로 낡는다(그래서 한 번
`patch does not apply` 가 났다). 지금 산출은 등재 파일 **셋**, 마일스톤 문서 **넷**이다.

## 되돌림 절차

1. **신규 파일 12건** — 디렉터리째 제거한다. base 에 없던 경로라 `git restore --source` 의 대상이
   아니다.

```
git rm -r adapters/src/test/kotlin/bidvector/adapters/e2e
```

2. **공유 파일 둘** — 각 파일의 커밋을 `git log` 로 뽑아 **최신부터** 역적용한다. 파일 전체를 base 로
   되돌리지 않는다(다른 레인이 같은 파일을 건드렸을 수 있다).

```
for f in config/quality/gate-tests.properties milestone-6.md; do
  for c in $(git log --format=%h fd4629fe..98d3a430 -- "$f"); do
    git diff "$c~1..$c" -- "$f" | git apply -R || { echo "FAIL $f @ $c"; exit 1; }
  done
done
```

`--3way` 가 자동 해소에 실패하면 수동 절차는 이렇다 — 등재 파일에서는 `bidvector.adapters.e2e.`
로 시작하는 다섯 줄과 그 바로 앞 머리말 문단(이 slice 가 더한 주석)을 지운다. 마일스톤 문서에서는
6D 분할·6D-1 착수 문단, **6D-1 종결 문단과 그 정정, 그리고 그 사이에 더해진 빈 줄**을 지운다.
전부 **더하기만 한 변경**이라 지우는 것으로 충분하고 남의 줄은 건드리지 않는다.

3. 두 단계 뒤 인덱스를 푼다(`git reset`).

## ①~⑥ 실측 (버릴 clone, `98d3a430` 체크아웃)

| 항목 | 결과 |
|---|---|
| ① 명령 exit | `git rm -r` 0 · 두 파일의 역적용 루프 전부 0(conflict 0) |
| ② D/M 수 | D 12 · M 2 — 복원 목록과 같다 |
| ③ diff 빈 것 | `git diff --name-only fd4629fe -- <세 경로>` **빈 출력** — 되돌린 트리가 base 와 같다 |
| ④ compile | `check` 안의 컴파일 전부 통과 |
| ⑤ test | `check` 안의 test 전부 통과 |
| ⑥ 게이트 | `./gradlew --no-daemon check` exit 0(9m 14s) · `./gradlew --no-daemon qualityBaseline` exit 0 |

확인은 **양방향**이다 — 「내 줄이 사라졌다」(③ 의 빈 diff)와 「남의 줄이 남았다」를 함께 봤다. 뒤쪽은
등재 파일에서 **두 술어**로 센다:

| 술어 | 세는 것 | base | 되돌림 |
|---|---|---|---|
| `grep -c '^  bidvector\.'` | 등재 목록의 식별자 줄 | 323 | 323 |
| `grep -c 'bidvector\.'` | 주석까지 포함한 파일 전체 출현 | 333 | 333 |

갈음 근거는 「HEAD 가 초록」이 아니라 **트리 동일성**이다 — 되돌린 트리의 세 경로가 base 와 바이트
동일함을 ③ 이 보이고, 그 위에서 ④⑤⑥ 을 다시 실측했다.

**갈음을 쓰지 않고 실제로 돌렸다.** 되돌린 트리가 base 와 다른 경로는 이 slice 의 evidence·계약
마크다운 넷뿐이지만, 그중 evidence 디렉터리는 **누출 패턴 게이트의 선언된 입력**이라(`quality-baseline`
규약) 「입력이 같으니 base 결과로 갈음한다」가 성립하지 않는다. 그래서 두 명령을 그대로 다시 돌렸다.

## 비활성화(되돌리지 않고 끄는 법)

이 slice 의 산출물은 test 와 등재뿐이라 **운영 거동을 끄는 스위치가 없다**. 실행만 멈추려면 등재
다섯 줄을 지우면 되지만, 그 순간 등재 등식 게이트가 즉시 붉어진다(모집단 ∖ 제외 ≠ 등재) — 즉
「조용히 끄는 길」이 없다. 끄려면 test 클래스 자체를 지우는 위 되돌림을 쓴다.
