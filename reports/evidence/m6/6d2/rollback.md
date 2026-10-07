# M6/6D-2 — rollback 절차와 실측

실측 HEAD: `3a3208d1`(PR #64 조치의 산출물 커밋 = 이 slice 의 마지막 산출물 커밋)
base: `f4b4ff91`

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 경로는 셋 — e2e test 디렉터리와
공유 파일 **둘**(`config/quality/gate-tests.properties` · `docs/adr/0005-domain-events-and-outbox.md`)
이다. 같은 브랜치의 `milestone-6.md` 와 계약 문서(`scope.md`)는 **팀장 레인 소유**라 되돌리지 않는다.

**ADR 0005 가 여기 든 이유**(verifier r1 R1-M-3). §7.6 은 이 slice 의 B-2 실측을 등재한 것이고 계약
in_scope 다. 커밋 주체가 팀장이어도 **되돌림 절차는 레인 문서가 든다** — 앞 판은 절차도, 「팀장 소유라
되돌리지 않는다」는 선언도 없어 ⓪ 등식이 판정 SHA 에서 깨졌다.

6D-1 과 다른 점: e2e 디렉터리에 **수정** 파일이 섞여 있다. 디렉터리째 지우면 6D-1 산출물까지
사라지므로 신규와 수정을 가른다.

## ⓪ 복원 목록 — 기계 산출과 양방향 등식

```
BASE=f4b4ff91
IN="adapters/src/test/kotlin/bidvector/adapters/e2e config/quality/gate-tests.properties \
    docs/adr/0005-domain-events-and-outbox.md"
git diff --name-status $BASE..3a3208d1 -- $IN
git diff --name-only $BASE..3a3208d1 | sort > all.txt
git diff --name-only $BASE..3a3208d1 -- $IN milestone-6.md reports/evidence/m6/6d2 | sort > covered.txt
comm -23 all.txt covered.txt     # 전체 ∖ 덮개 — 빈 출력
comm -13 all.txt covered.txt     # 덮개 ∖ 전체 — 빈 출력
```

산출: `A` 3건(신규 test 소스 둘 + 주입 데코레이터) · `M` 7건(e2e 수정 다섯 + 공유 파일 둘).
`comm` **양쪽 다 빈 출력**이다.

**재산출 자리**는 `3a3208d1` 이다. 라운드마다 다시 돌린다 — 목록을 문서에 박지 않는다(하드코드는
바로 낡는다). 이 뒤의 evidence 커밋이 건드리는 것은 이미 덮개에 든 `reports/evidence/m6/6d2/` 뿐이라
등식이 그대로 유지된다. 앞 두 판(`8af675c1` · `ea07b9a7`)에서도 같은 명령이 양방향 빈 출력이었고,
`A` 3 · `M` 7 은 승인 전 일괄 이후 변하지 않았다(이번 조치는 수정 파일 집합을 넓히지 않았다).

## 공유 파일의 hunk 출처 — `git log` 산출, `--no-merges`

```
for f in config/quality/gate-tests.properties docs/adr/0005-domain-events-and-outbox.md; do
  git log --no-merges --format=%h $BASE..3a3208d1 -- "$f"
done
```

`--no-merges` 가 필요한 이유: range 에 `main` 병합 커밋이 들어오면 `git diff <sha>~1..<sha>` 가 그
커밋에서 엉뚱한 범위를 낸다(6F-10 교훈). 지금 산출은 등재 파일 **한 건** · ADR **한 건**이다.

## 되돌림 절차

1. **신규 3건** — 제거한다(base 에 없던 경로라 `git restore --source` 의 대상이 아니다).

```
E=adapters/src/test/kotlin/bidvector/adapters/e2e
git rm $E/EventTriggeredTransactions.kt $E/PipelineRedeliveryE2ETest.kt $E/PipelineRestartConvergenceE2ETest.kt
```

2. **수정 5건** — base 로 되돌린다. 다섯 다 6D-1 산출물이고 그 slice 는 닫혔으므로, 이 브랜치에서 그
   파일을 만진 것은 이 slice 뿐이다(⓪ 의 등식이 그것을 보인다) — 그래서 파일 전체 복원이 맞다.

```
git restore --source=$BASE --staged --worktree -- \
  $E/PipelineAssembly.kt $E/PipelineE2ESupport.kt $E/PipelineFailureInjectionE2ETest.kt \
  $E/PipelineOneLineE2ETest.kt $E/PipelineReproducibilityE2ETest.kt
```

3. **공유 파일 둘** — 각 파일의 커밋을 `git log --no-merges` 로 뽑아 **최신부터** 역적용한다. 파일
   전체를 base 로 되돌리지 않는다(다른 레인이 같은 파일을 건드렸을 수 있다).

```
G=config/quality/gate-tests.properties
A=docs/adr/0005-domain-events-and-outbox.md
for f in $G $A; do
  for c in $(git log --no-merges --format=%h $BASE..3a3208d1 -- "$f"); do
    git diff "$c~1..$c" -- "$f" | git apply -R || { echo "FAIL $f @ $c"; exit 1; }
  done
done
```

`--3way` 도 자동 해소에 실패하므로 **수동 절차**를 미리 적는다.

- 등재 파일: `bidvector.adapters.e2e.PipelineRedeliveryE2ETest` 줄과
  `…PipelineRestartConvergenceE2ETest` 줄, 그리고 `gate.tests.adapters=` 바로 앞에 이 slice 가 더한
  다섯 줄 주석 문단(「M6/6D-2 —」로 시작)을 지운다.
- ADR: **파일 끝에 덧붙인 일곱 줄**(빈 줄 + §7 의 마지막 소절 제목 + 본문 다섯 줄)을 지운다. 그
  절을 가리키는 교차 참조는 문서 안에 **없다**(절 제목 한 자리뿐 — 실측).

둘 다 **더하기만 한 변경**이라 지우는 것으로 충분하고 남의 줄은 건드리지 않는다.

4. 세 단계 뒤 인덱스를 푼다(`git reset`).

## ①~⑥ 실측 (버릴 clone, `3a3208d1` 체크아웃)

| 항목 | 결과 |
|---|---|
| ① 명령 exit | `git rm` 0 · `git restore` 0 · 역적용 루프 0(두 파일, conflict 0) |
| ② D/M 수 | D 3 · M 7 — 복원 목록과 같다 |
| ③ diff 빈 것 | `git diff --name-only f4b4ff91 -- <세 경로>` **빈 출력**(바이트 동일) |
| ④ compile | `check` 안의 Kotlin 컴파일 과제 21건 전부 통과 |
| ⑤ test | 348 클래스 · 2806 test · 실패 0 · 오류 0. e2e 는 **13**(6D-1 의 수)으로 돌아왔다 |
| ⑥ 게이트 | `./gradlew --no-daemon check` exit 0(9m 26s) · `./gradlew --no-daemon qualityBaseline` exit 0 |

확인은 **양방향**이다 — 「내 줄이 사라졌다」(③ 의 빈 diff)와 「남의 줄이 남았다」를 함께 봤다. 뒤쪽은
공유 파일마다 술어를 둔다:

| 술어 | 세는 것 | base | `3a3208d1` | 되돌림 |
|---|---|---|---|---|
| `grep -c '^  bidvector\.'` | 등재 목록의 식별자 줄 | 348 | 350 | 348 |
| `grep -c 'bidvector\.'` | 등재 파일 전체 출현(주석 포함) | 358 | 360 | 358 |
| `grep -c '^### 7\.'` | ADR §7 의 소절 수 | 5 | 6 | 5 |

세 라운드 전부 같은 절차로 돌렸고 결과도 같다(`8af675c1` · `ea07b9a7` · `3a3208d1`). ④~⑥ 은 라운드마다
다시 돌렸다 — 「앞 라운드에서 초록이었다」로 갈음하지 않는다.

갈음 근거는 「HEAD 가 초록」이 아니라 **트리 동일성**이다 — 되돌린 트리의 세 경로가 base 와 바이트
동일함을 ③ 이 보이고, 그 위에서 ④⑤⑥ 을 다시 실측했다.

**갈음을 쓰지 않고 실제로 돌렸다.** 되돌린 트리가 base 와 다른 경로는 이 slice 의 evidence·계약
마크다운뿐이지만, evidence 디렉터리는 **누출 패턴 게이트의 선언된 입력**이라(`quality-baseline` 규약)
「입력이 같으니 base 결과로 갈음한다」가 성립하지 않는다. 그래서 두 명령을 그대로 다시 돌렸다.

## 비활성화(되돌리지 않고 끄는 법)

이 slice 의 산출물은 test 와 등재와 ADR 한 절뿐이라 **운영 거동을 끄는 스위치가 없다** — production
diff 0 이므로 끌 것 자체가 없다. 실행만 멈추려면 등재 두 줄을 지우면 되지만, 그 순간 등재 등식
게이트가 즉시 붉어진다(모집단 ∖ 제외 ≠ 등재) — 즉 「조용히 끄는 길」이 없다. 끄려면 test 클래스
자체를 지우는 위 되돌림을 쓴다.
