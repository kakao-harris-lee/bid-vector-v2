# M10 / gates-web — 되돌림

실측 HEAD: `90044035` (main `67ff54b1` 병합 커밋 — 되돌림이 적용되는 트리가 그것이다)

**원천을 교체했다.** 10B(#149)가 먼저 머지돼 main `67ff54b1` 을 병합했으므로, 복원 원천은 착수
base 가 아니라 **`67ff54b1`**(고정 기재 — 움직이는 `origin/main` 을 쓰지 않는다)이다.

**셋째 단언을 쟀다** — 복원 대상 아홉이 착수 base 와 새 원천에서 blob 동일한가:
- **같다 넷** — `gate-tests.txt` · `journey-up.mjs` · `opening-axis.mjs` · `opening-axis.test.mjs`.
- **원천에 없다 셋** — 내가 신설한 `kotlin-source.mjs`·`kotlin-source.test.mjs`·
  `kotlin-source-ownership.test.mjs`(base 에도 `67ff54b1` 에도 없다 → 복원이 **지운다**).
- **다르다 둘** — `capability-map.md` · `money-vocab-cross-lang.test.mjs` 를 **10B 가 만졌다**.
  그래서 이 둘은 새 원천 기준으로 다시 셌다(아래 계수).

**그래도 hunk 격리가 필요 없다.** 근거는 blob 동일성이 아니라 **원천에 내 것이 없다**는 것이다 —
이 slice 의 산출물 커밋 여섯이 `67ff54b1` 의 조상인지 전수로 물어 **전부 미포함**이고, `67ff54b1`
에 내 표지(capability map `(j)` 절 · 등록부의 공유 읽기 줄)가 **0** 이다. 즉 파일 단위 복원이
**내 줄만** 지우고 10B 의 줄은 그 원천에 그대로 들어 있다(계수로 확인했다).

## 복원 목록 (기계 산출 — 손으로 쓰지 않는다)

`git diff --name-status 8062dcd7..HEAD` 에서 **자기 evidence 를 뺀** 아홉이다.

```
M docs/discovery/capability-map.md
M web/config/gate-tests.txt
M web/e2e/harness/journey-up.mjs
M web/e2e/harness/opening-axis.mjs
M web/e2e/harness/opening-axis.test.mjs
A web/scripts/kotlin-source-ownership.test.mjs
A web/scripts/kotlin-source.mjs
A web/scripts/kotlin-source.test.mjs
M web/scripts/money-vocab-cross-lang.test.mjs
```

```
git restore --source=8062dcd784cf81905c50a2c8706d8bbd3ea5263e --staged --worktree -- \
  docs/discovery/capability-map.md web/config/gate-tests.txt \
  web/e2e/harness/journey-up.mjs web/e2e/harness/opening-axis.mjs \
  web/e2e/harness/opening-axis.test.mjs web/scripts/kotlin-source-ownership.test.mjs \
  web/scripts/kotlin-source.mjs web/scripts/kotlin-source.test.mjs \
  web/scripts/money-vocab-cross-lang.test.mjs
```

**목록은 r2 에서 여섯 → 아홉이 되고 그 뒤로는 자라지 않았다**(verifier r3 L-1 정정 — r3·r4 의 새
파일은 복원 목록 **안**이다. 낡은 것은 목록이 아니라 **①~⑥ 실측값과 계수**다). 재산출을 앞 목록과
**양방향으로 대조**했다: 새로 들어온 것 **셋**(`journey-up.mjs` · `opening-axis.test.mjs` ·
`kotlin-source-ownership.test.mjs`), 빠진 것 **0**. 셋 가운데 하나는 **게이트가 찾아낸 소비자**이고
(`scope.md` §r2), 목록을 손으로 들고 있었다면 그 파일은 되돌림에서 **빠진 채** 남았을 것이다.

`A` 셋은 원천에 없으므로 복원이 **지운다**(별도 `git rm` 이 필요 없다). **hunk 격리가 필요 없다**
— 공유 파일 둘(`capability-map.md`·`gate-tests.txt`)에 다른 slice 의 줄이 들어온 적이 없다
(main 병합 0 · 이 브랜치의 커밋만). 그래서 파일 단위 복원이 남의 줄을 떨어뜨릴 수 없다.

**되돌리지 않는 것**: 이 slice 의 evidence(`reports/evidence/m10/gates-web/`) · 하네스 경로
(`CLAUDE.md`·`.claude/**`, 이 range 에 **없다**).

## 실측 — 버릴 clone, `90044035` (원천 `67ff54b1`)

| 걸음 | 결과 |
| --- | --- |
| ① 복원 명령 | **exit 0** |
| ② D/M 수 | **D 3 · M 6** (목록과 같다) |
| ③ **원천** 대비 diff | **0 줄** |
| ④ 컴파일(web `typecheck`) | **exit 0** |
| ⑤ test(`vitest run`) | **40 files · 858 tests passed** — 병합 뒤 판정 SHA 가 42 files · 909 tests 이므로 이 slice 가 더한 **2 files · 51 tests** 가 정확히 빠진 수다 |
| ⑥ 게이트 | web job 여덟(`lint`·`typecheck`·`gate:src-tree`·`api:types:check`·`test`·`gate:tests`·`build`·`gate:boundary-canary`) **전부 exit 0** · Kotlin `leakPatternGate` 는 **미측정**(아래) |

clone 에서 `npm ci` 로 **실제 설치**했다 — 심볼릭 링크는 `gate:src-tree` 를 거짓으로 붉힌다.

**⑥ 의 Kotlin 몫은 아직 재지 않았다.** 판정 SHA 의 Kotlin `check` 전건이 호스트 경쟁(다른 레인의
전건 빌드와 동시)으로 길어져 그 뒤에 돌 차례였다. **미측정은 초록이 아니다** — 그 두 수치(판정 SHA
의 `check`, 복원 트리의 `leakPatternGate`)는 **PR 조치 코멘트**가 진다.

**완료는 종료 코드가 아니라 계수로 판정한다**(공유 파일 둘 — 이번엔 10B 의 줄이 실제로 들어 있다).

| 계수 | 결과 |
| --- | --- |
| capability map 의 **내** 하위 절 `(j)` | **0** (내 표지 사라짐) |
| capability map 의 **남의** 절 `(a)`~`(h)` | **7** (10B 의 `(f)`·`(h)` 포함, 보존) |
| 10B `(h)` 절 본문 표지 | **2** (보존) |
| 등록부의 **내** 줄 | **0** |
| 등록부 항목 전수 | **37** == 원천의 **37** |
| `money-vocab` 의 `it(` 수 | **18** == 원천의 **18** (내가 더한 한 줄만 빠졌다) |

## 원천을 교체해야 하는 조건

10B(`feature/m10-timeline-command-enum`)가 먼저 머지되고 이 브랜치가 main 을 병합하면, 공유 파일에
**남의 줄이 들어온다**. 그때 원천을 **그 병합한 main 커밋 SHA**(고정 기재 — `origin/main` 금지)로
바꾸고 ①~⑥ 과 위 계수를 **다시** 낸다. 교체 시 단언을 하나 더 잰다: **원천의 내 복원 대상 아홉이
착수 base 와 blob 동일**(그 사이 남의 slice 가 내 복원 대상을 만지지 않았음) — 그것이 hunk 격리
불필요의 근거다. 실측 자리도 **그 병합 커밋**이 된다(되돌림이 적용되는 트리가 그것이다).

## §4 술어 — 실측이 유효한가

```
git diff --name-only 7758e17f..<판정 SHA> -- <위 아홉 경로>
```

**빈 출력이면 유효하다.** `7758e17f` 뒤에 오는 커밋은 evidence 뿐이고 evidence 는 복원 목록 밖이라
이 술어가 빈 출력으로 선다. 한 줄이라도 나오면 되돌림 대상이 움직인 것이므로 실측은 낡았고 그 절은
통과가 아니라 **미검증**이다. 실측 HEAD 가 판정 SHA 의 조상이 아닌 경우도, 그 사이에 머지 커밋이
있는 경우도 미검증이다.
