# M11 / gates-web-read — rollback

되돌리는 방법은 **in_scope 경로 한정 복원**이다. range revert 를 쓰지 않는다 — 이 slice 의 커밋
집합은 range 가 아니라 **in_scope 경로의 변경**이고, 공유 파일(§공유)이 그 range 안에 남의 줄을
담을 수 있다.

```
git restore --source=<원천> --staged --worktree -- <아래 목록>
```

## 복원 목록 — 기계 산출

`git diff --name-status <base>..HEAD` 의 경로 전수다. **라운드마다 재산출한다.**

```
docs/discovery/capability-map.md
reports/evidence/m11/gates-web-read/            (commands.md · design-review.md · rollback.md · scope.md)
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

## 원천 — 고정 SHA 여야 한다

움직이는 `origin/main` 을 쓰지 않는다. **머지 전**에는 **이 브랜치에 병합된 마지막 main 커밋**이
원천이고, 오늘 그것은 착수 base `94ee445b50cafa669af435e651d811cc551c67e8` 다(main 을 아직 병합하지
않았다). **전제**: 그 SHA 에 이 slice 가 없다 — 첫 산출물 커밋이 그 조상에 들어 있지 않다.

**원천이 바뀌면 셋째 단언을 잰다**(머지 차례에 그렇게 된다): 복원 대상 blob 이 **착수 base 와 새
원천에서 같은가**. 다르면 새 원천 기준으로 목록과 계수를 **다시** 센다. 이 slice 의 복원 대상
열다섯 가운데 **`docs/discovery/capability-map.md`·`web/config/gate-tests.txt`·`web/vite.config.ts`
셋이 공유 자리**이므로 그 셋이 이 단언의 실질이다.

머지 **뒤** 되돌림은 이 문서가 아니라 **PR revert** 다.

## 완료 판정 — 종료 코드가 아니다

`git restore` 의 exit 0 은 「복원이 돌았다」일 뿐이다. 완료는 **계수**로 센다.

- **내 표지 0**: `OPEN-M11-KOTLIN-READ-GATE-RESIDUALS-2` 항목 집합이 capability map 에 0 · 신설
  파일 다섯이 부재 · evidence 디렉터리 부재.
- **남의 표지 보존**: §12.3 의 (j) 절과 그 앞 절들이 그대로 · `reports/evidence/m10/` 보존 ·
  (머지 뒤라면) 앞 레인이 더한 §12.3 절과 공유 파일의 줄이 그대로.

항목 집합으로 세고 **어휘 grep 으로 세지 않는다** — 상류가 같은 이름을 쓰면 그 대조가 조용히
공허해진다.

## ①~⑥ 실측 — 머지 차례에 한다

실측은 **버릴 clone** 에서 ① 복원 명령 exit · ② D/M 계수 · ③ 복원 경로 한정 diff 가 빈 것 ·
③′ 「내 표지 0 · 남의 표지 보존」 계수 · ④ 설치·컴파일(`npm ci`·`typecheck`·`build`) ·
⑤ test(`vitest run`) · ⑥ 게이트(`lint`·`gate:tests`·`gate:src-tree`·`api:types:check`·
`gate:boundary-canary`)를 돈다. 갈음은 「HEAD 초록」이 아니라 **트리 동일성**으로만 센다.

**아직 돌지 않았다.** 머지 순서가 **11E → 11B → 11D** 이고 앞 둘이 공유 자리 셋을 만지므로,
지금 재면 그 실측은 머지 뒤에 **무효**가 된다(원천이 바뀌고 셋째 단언이 다시 서야 한다). 그래서
**이 slice 의 마지막 산출물 커밋 = main 병합 커밋**에서 한 번 재고, 그때 `실측 HEAD: <sha>` 를
이 절에 적는다. 그 전까지 이 문서의 상태는 **미실측**이다.
