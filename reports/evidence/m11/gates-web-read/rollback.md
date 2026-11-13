# M11 / gates-web-read — rollback

되돌리는 방법은 **in_scope 경로 한정 복원**이다. range revert 를 쓰지 않는다 — 이 slice 의 커밋
집합은 range 가 아니라 **in_scope 경로의 변경**이고, 공유 파일(§공유)이 그 range 안에 남의 줄을
담을 수 있다.

```
git restore --source=<원천> --staged --worktree -- <아래 목록>
```

## 복원 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 의 경로 전수다. **라운드마다 재산출한다.**

`git diff --name-status 4bb17677..HEAD` 가 낸 **18 경로**(신설 9 · 수정 9)이고, 디렉터리 하나로
적는 evidence 넷이 그 안에 든다.

```
docs/discovery/capability-map.md
reports/evidence/m11/gates-web-read/
web/config/gate-tests.txt
web/e2e/harness/opening-axis-extract.mjs
web/e2e/harness/opening-axis.mjs
web/e2e/harness/opening-axis.test.mjs
web/eslint.config.js
web/scripts/kotlin-read-guard-setup.mjs
web/scripts/kotlin-read-guard.mjs
web/scripts/kotlin-read-guard.test.mjs
web/scripts/kotlin-source-ownership.test.mjs
web/scripts/kotlin-source.mjs
web/scripts/money-vocab-cross-lang.test.mjs
web/scripts/type-assertion-gate.test.mjs
web/vite.config.ts
```

**등식**: 그 목록에서 in_scope 를 뺀 차집합이 **빈 출력**이다(`comm -23`, 재산출 때마다 확인).
in_scope 밖 경로는 하나도 없다 — 이 slice 는 공유 working tree 에서 경로 한정 커밋만 했다.

**evidence 는 디렉터리 하나로 적는다**(r1 — cr LOW). 앞 판은 그 자리에 파일 이름을 괄호로 적었는데,
복원이 **디렉터리 한정**이라 그 괄호는 복원에 쓰이지 않으면서 **수정 라운드가 evidence 를 더할
때마다 낡는다**. 손으로 세는 계수를 복원 문서에 두지 않는다.

## 원천 — 고정 SHA 여야 한다

움직이는 `origin/main` 을 쓰지 않는다. 원천은 **이 브랜치에 병합된 마지막 main 커밋**이고, 지금
그것은 `4bb17677764d253150a9f608ceeb80ee25655fb61` 다. **전제**: 그 SHA 에 이 slice 가 없다 —
첫 산출물 커밋이 그 조상에 들어 있지 않다.

**셋째 단언 — 원천이 바뀌었으므로 쟀다.** 복원 대상 blob 이 **착수 base 와 새 원천에서 같은가**:

| 복원 대상 | base `94ee445b` ↔ 원천 `4bb17677` |
| --- | --- |
| `docs/discovery/capability-map.md` | **다르다** — main 이 §12.3 에 절 셋을 더했다 |
| 나머지 여덟(수정 대상 전부) | 같다 |

그래서 **그 파일은 새 원천 기준으로 다시 셌다.** 복원이 착수 base 를 가리키고 있었다면 그 한 번이
남의 절 셋을 지웠을 자리다 — 원천을 고정 SHA 로 두고 바뀔 때마다 이 단언을 재는 규율이 막는 것이
정확히 이것이다. 복원 목록도 **원천 기준으로 다시 산출**했다(아래).

머지 **뒤** 되돌림은 이 문서가 아니라 **PR revert** 다.

## 완료 판정 — 종료 코드가 아니다

`git restore` 의 exit 0 은 「복원이 돌았다」일 뿐이다. 완료는 **계수**로 센다.

- **내 표지 0**: `OPEN-M11-KOTLIN-READ-GATE-RESIDUALS-2` 항목 집합이 capability map 에 0 · 신설
  파일 다섯이 부재 · evidence 디렉터리 부재.
- **남의 표지 보존**: §12.3 의 (j) 절과 그 앞 절들이 그대로 · `reports/evidence/m10/` 보존 ·
  (머지 뒤라면) 앞 레인이 더한 §12.3 절과 공유 파일의 줄이 그대로.

항목 집합으로 세고 **어휘 grep 으로 세지 않는다** — 상류가 같은 이름을 쓰면 그 대조가 조용히
공허해진다.

## ①~⑥ 실측

**실측 HEAD: `b37e44bfbe584a123f64ccf6ad52f1bd797cd7ca`** (원천 `4bb17677`). 버릴 clone 에서 돌았고
레인 저장소를 건드리지 않았다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 센다.

| | 측정 | 값 |
| --- | --- | --- |
| ① | 복원 명령 | exit **0** |
| ② | D/M 계수 | **D 9 · M 9**(신설 아홉이 지워지고 수정 아홉이 원천으로 돌아간다) |
| ③ | 복원 경로 한정 `diff <원천>` | **0 줄** |
| ③′ | 내 표지 0 | 절 `(n)` **0** · 신설 ID 잔존 **0** · 신설 파일 다섯 **전부 부재** · 내 evidence 디렉터리 **부재** |
| ③′ | 남의 표지 보존 | §12.3 글자 **a~m**, `(k)(l)(m)` 각 **1** · 남의 evidence 셋 보존 · `gate-tests` **39 등재** · `vite.config` `test:` **4 키** |
| ④ | `npm ci` · `typecheck` · `build` | exit **0 · 0 · 0** |
| ⑤ | `vitest run` | **42 파일 · 945 passed** |
| ⑥ | `lint`·`gate:tests`·`gate:src-tree`·`api:types:check`·`gate:boundary-canary` | exit **0** 다섯 |

⑤ 의 42 파일·945 는 **원천의 수**다 — 복원이 내 게이트 test 둘을 지웠으므로 병합 커밋의
44 파일·987 에서 그만큼 줄어든 값이고, 그 감소 자체가 「내 표지가 사라졌다」의 또 다른 계수다.

**실측 뒤 이 문서만 커밋했다.** 복원 목록 안의 **원천에 존재하는 파일**은 실측 뒤 건드리지
않았다 — 건드리면 재측정이다. 이 파일은 원천에 없는 신설 파일이라 그 예외에 든다.
