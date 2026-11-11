# M10 / gates-web — 되돌림

실측 HEAD: `9a70b525`

**전제**: 이 slice 는 **main 에 없다**(머지 전). 그래서 공유 파일의 복원 원천은 착수 base
`8062dcd784cf81905c50a2c8706d8bbd3ea5263e` 다 — 움직이는 `origin/main` 을 쓰지 않는다. 단언 둘을
명령으로 쟀다: base 가 HEAD 의 **조상**이다 · base 가 이 slice 의 **첫 산출물 커밋을 포함하지
않는다**. 머지 **뒤**의 되돌림은 이 절차가 아니라 그 PR 의 **머지 커밋 revert** 다(머지 순간 같은
복원은 아무것도 지우지 않으면서 exit 0 을 낸다).

## 복원 목록 (기계 산출 — 손으로 쓰지 않는다)

`git diff --name-status 8062dcd7..HEAD` 에서 **자기 evidence 를 뺀** 여섯이다(되돌리지 않기로 한
것은 목록 밖). **라운드마다 이 산출을 다시 돌린다** — 목록이 낡는 것이 이 결함의 실제 원인이다.

```
M docs/discovery/capability-map.md
M web/config/gate-tests.txt
M web/e2e/harness/opening-axis.mjs
A web/scripts/kotlin-source.mjs
A web/scripts/kotlin-source.test.mjs
M web/scripts/money-vocab-cross-lang.test.mjs
```

```
git restore --source=8062dcd784cf81905c50a2c8706d8bbd3ea5263e --staged --worktree -- \
  docs/discovery/capability-map.md web/config/gate-tests.txt \
  web/e2e/harness/opening-axis.mjs web/scripts/kotlin-source.mjs \
  web/scripts/kotlin-source.test.mjs web/scripts/money-vocab-cross-lang.test.mjs
```

`A` 두 개는 원천에 없으므로 복원이 **지운다**(별도 `git rm` 이 필요 없다). **hunk 격리가 필요
없다** — 공유 파일 둘(`capability-map.md`·`gate-tests.txt`)에 다른 slice 의 줄이 들어온 적이
없다(main 병합 0 · 이 브랜치의 커밋만). 그래서 파일 단위 복원이 남의 줄을 떨어뜨릴 수 없다.

**되돌리지 않는 것**: 이 slice 의 evidence(`reports/evidence/m10/gates-web/`) · 하네스 경로
(`CLAUDE.md`·`.claude/**`, 이 range 에 **없다**).

## 실측 — 버릴 clone, `9a70b525`

| 걸음 | 결과 |
| --- | --- |
| ① 복원 명령 | **exit 0** |
| ② D/M 수 | **D 2 · M 4** (목록과 같다) |
| ③ base 대비 diff | **0 줄** |
| ④ 컴파일(web `typecheck`) | **exit 0** |
| ⑤ test(`vitest run`) | **40 files · 857 tests passed** — 이 slice 가 더한 1 file·21 tests 가 정확히 빠진 수다 |
| ⑥ 게이트 | web job 일곱(`lint`·`typecheck`·`gate:src-tree`·`api:types:check`·`gate:tests`·`build`·`gate:boundary-canary`) **전부 exit 0** · Kotlin `leakPatternGate` **exit 0**(실행 — cache 표식 없음) |

**완료는 종료 코드가 아니라 계수로 판정한다**(공유 파일 둘).

| 계수 | 결과 |
| --- | --- |
| capability map 의 **내** 하위 절 | **0** (내 표지 사라짐) |
| capability map 의 **남의** 절 `(a)`~`(e)` | **5** (보존) |
| 등록부의 **내** 줄 | **0** |
| 등록부 항목 전수 | **37** == base 의 **37** (남의 줄 보존) |
| evidence 디렉터리 | 남아 있다(목록 밖) — 그 상태로 `leakPatternGate` 가 초록이다 |

⑥ 의 첫 측정에서 `gate:src-tree` 가 **exit 1** 이었는데 **되돌림의 미완이 아니라 측정 하네스의
자취**였다: clone 의 `web/node_modules` 를 심볼릭 링크로 걸어 두어 그 게이트의 「`web/` 안 심볼릭
링크 0」 술어에 걸렸다. 링크를 치우고 `npm ci` 로 실제 설치한 뒤 재측정해 위 값을 얻었다.

## 원천을 교체해야 하는 조건

10B(`feature/m10-timeline-command-enum`)가 먼저 머지되고 이 브랜치가 main 을 병합하면, 공유 파일에
**남의 줄이 들어온다**. 그때 원천을 **그 병합한 main 커밋 SHA**(고정 기재 — `origin/main` 금지)로
바꾸고 ①~⑥ 과 위 계수를 **다시** 낸다. 교체 시 단언을 하나 더 잰다: **원천의 내 in_scope 여섯이
착수 base 와 blob 동일**(그 사이 남의 slice 가 내 복원 대상을 만지지 않았음) — 그것이 hunk 격리
불필요의 근거다. 실측 자리도 **그 병합 커밋**이 된다(되돌림이 적용되는 트리가 그것이다).

## §4 술어 — 실측이 유효한가

```
git diff --name-only 9a70b525..<판정 SHA> -- <위 여섯 경로>
```

**빈 출력이면 유효하다.** `9a70b525` 뒤에 오는 커밋은 evidence 뿐이고 evidence 는 복원 목록 밖이라
이 술어가 빈 출력으로 선다. 한 줄이라도 나오면 되돌림 대상이 움직인 것이므로 실측은 낡았고 그 절은
통과가 아니라 **미검증**이다. 실측 HEAD 가 판정 SHA 의 조상이 아닌 경우도 미검증이다.
