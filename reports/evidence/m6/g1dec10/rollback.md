# M6/G1-DEC10 — rollback

실측 HEAD: **`b22efb74`**(main `260d0c99` 병합 커밋 — 이 slice 의 **마지막 산출물 커밋**이고 그 뒤는
evidence 커밋뿐이다).

**base 가 다시 옮겨갔다** — `c4e3fd1c` → **`260d0c99`**(PR #100 Go stdlib CVE chore 머지 커밋).
전제 둘을 새 base 에서 다시 실측했다: base 가 이 slice 의 착수 커밋을 **담지 않고**
(`git merge-base --is-ancestor <착수> 260d0c99` exit 1), base 가 HEAD 의 **조상**이다(exit 0).
앞선 `fd505b5d` 실측은 버린다 — base 가 바뀌면 복원 기준이 바뀌고, 그 실측은 머지 전 장부 일괄이
되돌림 대상 둘(`capability-map.md`·생성기)을 움직여 이미 낡아 있었다(그 사실을 앞 판에 선언했고
이 재실측이 그것을 갈음한다). 이 slice 에서 **여덟 번째 재산출**이다.

## 복원 기준

- base = **`260d0c99`**(PR #100 머지 커밋 — 이 브랜치가 병합한 main 의 고정 SHA). 움직이는
  `origin/main` 을 쓰지 않는다. **머지 뒤**의 되돌림은 이 절차가 아니라 그 PR 의 머지 커밋
  revert 다 — 머지 순간 같은 복원은 아무것도 지우지 않으면서 exit 0 을 낸다(의미가 뒤집힌다).
- 되돌리는 것은 range 가 아니라 **in_scope 경로**다. 하네스 경로(`CLAUDE.md`·`.claude/`)는 이
  range 에 없다(`scope.md` 「하네스 레인 변경」 절이 「없음」).
- **병합 커밋은 되돌림 대상이 아니다** — 복원은 경로 한정이고, 그 경로들을 `260d0c99` 의 내용으로
  되돌리면 main 이 들여온 줄(7C②·7C③·6G-rc·CVE 등재)은 그대로 남는다. 다섯 교집합 파일이 그 성질을
  시험하는 자리이고, 아래 ③″ 가 「남의 줄 보존」을 센다.

## 목록 산출 (라운드마다 다시 돌린다)

손으로 쓰지 않는다 — 다음 한 줄이 목록을 낸다. **이 slice 의 evidence 디렉터리는 제외**한다
(그 기록은 되돌리지 않는다).

```
git diff --name-only 260d0c99..<실측 HEAD> -- ':!reports/evidence/m6/g1dec10'
```

**실측 79 경로**(`A` 36 · `M` 43 — 새 base 기준 재산출, 수가 같은 것은 목록이 이 slice 의
자기 발자국이고 main 의 변경은 base 로 들어갔기 때문이다). 수정 라운드가 파일을 더하면 이 절차를 다시 돌리고 `in_scope`
와 대조해 계약을 갱신한다 — r5 의 새 파일 둘 중 `tools/floor-trust-allowlist.py` 가 in_scope 밖이어서
**D-DEC10-25 로 계약을 갱신했다**(`checklist.md` 새 파일 표). 승인 전 일괄이 더한 fixture 하나는
`decision/src/test/resources/compile-fixtures/`(design §3) 안이라 계약 갱신이 없다.

## 절차

1. 위 목록 전부를 한 번에 — `git restore --source=260d0c99 --staged --worktree -- <경로 개별 인자>`.
   `--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다.
   `git checkout <base> -- <경로>` 는 쓰지 않는다(base 에 없는 경로마다 pathspec 오류).
2. **hunk 역적용은 쓰지 않는다.** 공유 파일(`capability-map.md` · `acceptance-trace.md` ·
   `milestone-6.md` · `config/quality/*` · 두 `policy-values.md` · `CollectionPolicy.kt` ·
   `app/build.gradle.kts` · `NoticeFacts.kt` 는 **무편집**)도 같은 고정 SHA 에서 복원한다 — 그 SHA 가 HEAD 의 조상이고 이 slice 를
   담지 않으므로 **남의 줄을 떨어뜨릴 수 없다**(해시 목록이 필요 없고, 뒤에 붙는 문단도 덮는다).
3. 이 slice 의 evidence(`reports/evidence/m6/g1dec10/`)는 되돌리지 않는다. 그로 인해 게이트가
   붉어지면 그 조건을 알려진 제한에 등재한다 — 실측에서는 붉어지지 않았다(⑥).
4. 목록 갱신 커밋은 **evidence 경로만** 만진다(목록이 자기를 담은 커밋을 가리킬 수 없다).

## 실측 (임시 clone, 실측 HEAD `b22efb74`)

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령 exit | **0** |
| ② | `D`/`M` 수 | **D 36 · M 43**(= 목록 79) |
| ③ | `git diff <base> -- <목록>` | **빈 출력**(0 줄) |
| ③′ | 「내 표지 0」 — 되돌린 트리의 production·게이트·`tools` 경로에서 이 slice 의 이름 넷 | **0 건** |
| ③″ | 「남의 줄 보존」 — 되돌리지 않기로 한 evidence 파일 수 | **5 건 그대로** |
| ④ | 되돌린 트리 `compileKotlin`·`compileTestKotlin` exit | **0** |
| ⑤⑥ | 되돌린 트리 **전건 `check`** exit | **0**(`BUILD SUCCESSFUL in 39s` — 캐시 적중, 아래 보정) |

**⑤⑥ 의 초록을 캐시로 받지 않았다.** 전건 `check` 는 486 task 중 212 가 캐시·최신이고 네 test
task 가 모두 FROM-CACHE 였다(복원된 트리의 Kotlin 입력이 앞 실측 때와 같다 — CVE chore 가 더한
`vuln-allowlist.properties` 는 `container` job 의 입력이고 Kotlin task 의 입력이 아니다). 그래서
**같은 clone 에서 test 를 강제 실행해 따로 셌다**:

- `:decision:test --rerun` · `:workflow:test --rerun` · `:adapters:test --rerun`(한 실행,
  `--no-build-cache`) → **exit 0**, 237 · 606 · 1248 test, 0 실패.
- `:app:test --rerun` → **exit 0**(`BUILD SUCCESSFUL in 25m 16s`), **885 test 0 실패**.
- 복원이 이 slice 의 test 를 **지웠다**는 것도 같은 자리에서 보인다 — `FloorTrustGateTest` ·
  `FloorBoundaryAtGateTest` 의 결과 XML 이 없다(base 에 그 파일들이 없다).

**실패 이력 하나** — app 강제 실행의 첫 시도는 `NoSuchFileException: …/test-results/test/binary/
in-progress-results-generic.bin` 으로 exit 1 이었다. test 실패가 아니라 **한 clone 에서 빌드를
겹쳐 돌린 레인의 실수**다(앞 실행이 끝나기 전에 다음 실행을 띄웠다). 결과 디렉터리를 지우고
**단독으로** 다시 돌려 위 초록을 얻었다. 겹쳐 돌리지 않는 것이 규율이고, 그 실패를 적는 것이
「초록만 적어 측정을 깨끗해 보이게」 하지 않는 자리다.

⑤⑥ 을 한 줄로 적는 것은 `check` 가 test 와 게이트를 함께 들기 때문이다(CI `check` job 과 같은
명령). 「되돌렸는데 게이트가 붉다」가 아님을 그 한 번이 센다.

## 유효성 확인 (verifier 대조용)

```
git diff --name-only b22efb74..<판정 SHA> -- <위 목록의 경로들>
```

**빈 출력이면 이 실측이 유효하다.** 한 줄이라도 나오면 그 뒤 커밋이 되돌림 대상을 바꾼 것이므로
실측은 낡았고 이 절은 통과가 아니라 미검증이다. 실측 HEAD 가 판정 SHA 의 조상이 아닌 경우도
미검증이다. (판정 SHA 와 실측 HEAD 가 **같을 수는 없다** — evidence 커밋은 언제나 뒤에 온다.)
