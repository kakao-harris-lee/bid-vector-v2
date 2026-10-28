# M6/G1-DEC10 — rollback

실측 HEAD: **`f116dd02`**(r5 승인 전 일괄의 **마지막 산출물 커밋** — 그 뒤는 evidence 커밋뿐이다).

앞선 `5099b8bb` 실측도 **버렸다**. 일괄이 파일 하나를 더해 목록이 **78 → 79** 로 바뀌었기
때문이다(fixture 12 의 오타 변이 쌍둥이). 「라운드마다 재산출」은 파일이 늘 때만이 아니라
**되돌림 대상의 내용이 바뀔 때도** 다시 돌려야 한다는 뜻이고, 이 slice 에서 **여섯 번째
재산출**이다. 앞 판 실측 여덟(`78c462ef`·`bcde7d01`·`43344b8f`·`332b5cf9`·`f21083a6`·
`32746b4f`·`913eeb4d`·`5099b8bb`)은 버린다. 뒤따르는 evidence 커밋은 되돌림 대상을 건드리지
않는다; 그 사실은 아래 「유효성 확인」 명령이 잰다.

## 복원 기준

- base = **`23837b19`**(PR #93 DEC-05 머지 커밋). 전제 둘을 실측했다 — base 가 이 slice 의 착수
  커밋을 **담지 않고**(`git merge-base --is-ancestor <착수> 23837b19` exit 1), base 가 HEAD 의
  **조상**이다(exit 0). 그래서 공유 문서도 같은 고정 SHA 에서 복원한다(움직이는 `origin/main` 을
  쓰지 않는다). **머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋 revert 다 — 머지 순간
  같은 복원은 아무것도 지우지 않으면서 exit 0 을 낸다(의미가 뒤집힌다).
- 되돌리는 것은 range 가 아니라 **in_scope 경로**다. 하네스 경로(`CLAUDE.md`·`.claude/`)는 이
  range 에 없다(`scope.md` 「하네스 레인 변경」 절이 「없음」).

## 목록 산출 (라운드마다 다시 돌린다)

손으로 쓰지 않는다 — 다음 한 줄이 목록을 낸다. **이 slice 의 evidence 디렉터리는 제외**한다
(그 기록은 되돌리지 않는다).

```
git diff --name-only 23837b19..<실측 HEAD> -- ':!reports/evidence/m6/g1dec10'
```

**실측 79 경로**(`A` 36 · `M` 43). 수정 라운드가 파일을 더하면 이 절차를 다시 돌리고 `in_scope`
와 대조해 계약을 갱신한다 — r5 의 새 파일 둘 중 `tools/floor-trust-allowlist.py` 가 in_scope 밖이어서
**D-DEC10-25 로 계약을 갱신했다**(`checklist.md` 새 파일 표). 승인 전 일괄이 더한 fixture 하나는
`decision/src/test/resources/compile-fixtures/`(design §3) 안이라 계약 갱신이 없다.

## 절차

1. 위 목록 전부를 한 번에 — `git restore --source=23837b19 --staged --worktree -- <경로 개별 인자>`.
   `--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다.
   `git checkout <base> -- <경로>` 는 쓰지 않는다(base 에 없는 경로마다 pathspec 오류).
2. **hunk 역적용은 쓰지 않는다.** 공유 파일(`capability-map.md` · `acceptance-trace.md` ·
   `milestone-6.md` · `config/quality/*` · 두 `policy-values.md` · `CollectionPolicy.kt` ·
   `app/build.gradle.kts` · `NoticeFacts.kt` 는 **무편집**)도 같은 고정 SHA 에서 복원한다 — 그 SHA 가 HEAD 의 조상이고 이 slice 를
   담지 않으므로 **남의 줄을 떨어뜨릴 수 없다**(해시 목록이 필요 없고, 뒤에 붙는 문단도 덮는다).
3. 이 slice 의 evidence(`reports/evidence/m6/g1dec10/`)는 되돌리지 않는다. 그로 인해 게이트가
   붉어지면 그 조건을 알려진 제한에 등재한다 — 실측에서는 붉어지지 않았다(⑥).
4. 목록 갱신 커밋은 **evidence 경로만** 만진다(목록이 자기를 담은 커밋을 가리킬 수 없다).

## 실측 (임시 clone, 실측 HEAD `f116dd02`)

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 exit | **0** |
| ② | `D`/`M` 수 | **D 36 · M 43**(= 목록 79) |
| ③ | `git diff <base> -- <목록>` | **빈 출력**(0 줄) |
| ③′ | 「내 표지 0」 — 되돌린 트리의 production·게이트·`tools` 경로에서 이 slice 의 이름 넷 | **0 건** |
| ③″ | 「남의 줄 보존」 — 되돌리지 않기로 한 evidence 파일 수 | **5 건 그대로** |
| ④ | 되돌린 트리 `compileKotlin`·`compileTestKotlin` exit | **0** |
| ⑤⑥ | 되돌린 트리 **전건 `check`**(test + 게이트 전부) exit | **0** — 아래 판독 |

**⑤⑥ 의 초록을 어떻게 읽어야 하는가(캐시 적중).** 이번 clone 의 `check` 는 29초에 끝났고 네
test task(`:app:` `:decision:` `:workflow:` `:adapters:`)가 모두 **FROM-CACHE** 였다 — 그 초록만
적으면 「돌지 않은 게이트」를 초록으로 적는 것이다. 그래서 둘을 더 쟀다.

1. **캐시 적중 자체가 입력 동일의 증거다.** Gradle 의 캐시 키는 task 입력 해시다. 그 입력으로
   **실제 실행된** 초록은 앞 실측(`5099b8bb`, 둘째 실행 — test 1195 · 게이트 전부)이다.
   되돌린 트리가 그때와 같은 이유는 **이 일괄의 산출물 델타 일곱 경로가 전부 복원 목록 안**이기
   때문이다(기계 대조: 목록 밖 0 건). 즉 복원 뒤에는 둘이 같은 트리로 수렴한다 — 남는 차이는
   evidence `.md` 뿐이고 그것은 어떤 task 의 입력도 아니다(전건 `check` 가 evidence 커밋 HEAD 에서
   474 task 중 test 를 하나도 다시 돌리지 않은 것이 그 측정이다).
2. **그래도 실제 실행을 한 번 더 세웠다** — 되돌린 트리에서 `--no-build-cache` 로
   `:decision:test --rerun` · `:workflow:test --rerun` 을 돌려 **exit 0**(decision 237 · workflow
   585 test, 0 실패). 두 모듈을 고른 것은 이 일괄이 만진 자리이기 때문이다. 복원이 이 slice 의
   test 파일을 **지웠다**는 것도 같은 자리에서 보인다(`FloorTrustCompileFailureHarnessTest` 의
   결과 XML 이 없다 — base 에 그 파일이 없다).

⑤⑥ 을 한 줄로 적는 것은 `check` 가 test 와 게이트를 함께 들기 때문이다(CI `check` job 과 같은
명령). 「되돌렸는데 게이트가 붉다」가 아님을 그 한 번이 센다.

## 유효성 확인 (verifier 대조용)

```
git diff --name-only f116dd02..<판정 SHA> -- <위 목록의 경로들>
```

**빈 출력이면 이 실측이 유효하다.** 한 줄이라도 나오면 그 뒤 커밋이 되돌림 대상을 바꾼 것이므로
실측은 낡았고 이 절은 통과가 아니라 미검증이다. 실측 HEAD 가 판정 SHA 의 조상이 아닌 경우도
미검증이다. (판정 SHA 와 실측 HEAD 가 **같을 수는 없다** — evidence 커밋은 언제나 뒤에 온다.)
