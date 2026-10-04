# M6/6G-2c — rollback

> 되돌림은 range revert 가 아니라 **in_scope 경로 한정**이다. 목록은
> `git diff --name-status <base>..<실측 HEAD>` 에서 기계로 낸다. **라운드마다 그 라운드의 마지막 산출물
> 커밋에서 다시 낸다** — 앞 라운드 실측을 옮기지 않는다.

**실측 HEAD `82c8b809`**(팀장 종결 문단 커밋 — 복원·hunk 대상을 마지막으로 건드린 커밋; 레인의 마지막 산출물 커밋은
K `51b4d960` · P `fa7483cb`) · base **`ecdc9d9f`**. ①~⑥ 전부 그 커밋의 **버릴 clone**(`git clone --no-hardlinks`, 공유 worktree 무편집)에서 돌렸다.

## 되돌림이 무엇을 건드리는가

이 slice 는 production 코드를 바꾼다(adapters `snapshot` 자물쇠·원장 가드·계수, app 수집 러너·배선의 사유 어휘, Python 백테스트 CLI·판정).
그러나 **실행 상태 디렉터리의 바이트·판독 술어는 바꾸지 않는다**(D-18 — verifier 가 base↔판정 양방향 다섯 사례 × 두 사본으로 실측, 원장·표본 파일
바이트 동일). 되돌려도 돌아가는 `m6-6g` 수집 디렉터리는 그대로 열린다. 돌아가는 것은 **잠금의 분류**(`Unlockable`/exit 4 → 전부 `Busy`/exit 3) ·
**close 뒤 원장 거부**(사라짐) · **원인 세 칸 로그**(사라짐) · **표지 셋**(`COVERED`·`UNDERPOWERED`·`ABSENT` → 둘) · **`verdict.json` 덮어쓰기
거부**(사라짐) · **앱 HTTP import 계약**(사라짐)이다. 복구 절차: 되돌린 뒤 수집은 같은 디렉터리로 그대로 재기동한다 — 실행 상태 디렉터리를 새로
시작할 이유가 없다(D-18). 판정 JSON 어휘가 둘로 돌아가므로 **백테스트 판정 보고 뒤**라면 되돌리지 않는다(A-2 결정의 전제).

## 되돌림 목록 (기계 산출) — 전체 경로는 ① 명령의 인자가 정본

| 상태 | 묶음 | 되돌림 |
|---|---|---|
| M | adapters `snapshot` production 둘(`JdbcSnapshotSource.kt`·`RunStateDirectory.kt`) · test 넷 | ① base 로 restore |
| A | adapters `snapshot` 신설 넷(`RunStateLock.kt`·`RunStateLedgerGuards.kt`·`RunStateLockTest.kt`·`UnusableRawRowsTest.kt`) | ① restore 가 삭제(base 에 없다) |
| M | app `collection`·`wiring` production 일곱 · test 열하나 | ① 같음 |
| A | app test 신설 셋(`RunStateLockHolderProcess.kt`·`CollectionLedgerSurfaceTest.kt`·`FixedClockHarnessTest.kt`) | ① 같음 |
| M | workflow `SampleSelectionTest.kt` | ① 같음 |
| M/A | `ml-engine` 스물(소스 열 · test 여섯 · fixture 미니 프로젝트 셋 · `pyproject.toml`) | ① 같음 |
| M | `config/quality/architecture-policy.properties` | ② **공유 파일** — 커밋 해시로 hunk 격리, **두 커밋**(`51b4d960`·`ec07df09`) |
| M | `config/quality/gate-tests.properties` | ② 같음, **네 커밋**(`93789f5c`·`2554bfbb`·`df54d4bb`·`2c176baa`) |
| M | `workflow/build.gradle.kts` | ② 같음, 한 커밋(`d65e8a80` — test 입력 선언 블록) |
| M | `reports/evidence/m6/6g/snapshot-schema.md` | ② 같음, 한 커밋(`ae1077e4` — §2 어휘 문장, 팀장) |
| M | `docs/runbook/m6-6g-real-collection.md` | ② 같음, **두 커밋**(`8ff4d1ec`·`f21d83c7` — 계수 문장 둘, 팀장) |
| M | `milestone-6.md` | ② 같음 — 종결 문단(`82c8b809`)과 착수 문단(`429d4bbc`) 문단 단위 둘 |

`reports/evidence/m6/6g2c/**` 는 **되돌리지 않는다**(이 slice 의 기록, `scope.md` 는 계약 커밋의 것). 그 선택의 게이트 영향은 ⑥ 에 있다.

## ① 전용 파일 — 한 번에 (경로 47: A 11 · M 36)

```
git diff --name-status ecdc9d9f..82c8b809 \
  | awk '{print $NF}' \
  | grep -v '^reports/evidence/m6/6g2c/' \
  | grep -vE '^(reports/evidence/m6/6g/snapshot-schema\.md|milestone-6\.md|config/quality/architecture-policy\.properties|config/quality/gate-tests\.properties|workflow/build\.gradle\.kts|docs/runbook/m6-6g-real-collection\.md)$' \
  | xargs git restore --source=ecdc9d9f --staged --worktree --
```

경로는 **개별 인자**로 들어간다(`xargs`). `git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 신규 파일에서 pathspec 오류로
아무것도 적용되지 않는다. 실측 `exit 0`(복원 M 36 · 삭제 A 11).

## ② 공유 파일 — 커밋 해시 hunk 역적용, 파일마다 뒤 커밋부터

```
for f in config/quality/architecture-policy.properties config/quality/gate-tests.properties \
         workflow/build.gradle.kts reports/evidence/m6/6g/snapshot-schema.md \
         docs/runbook/m6-6g-real-collection.md milestone-6.md; do
  for sha in $(git log --format=%h ecdc9d9f..82c8b809 -- "$f"); do
    git diff "$sha~1..$sha" -- "$f" | git apply -R --index --
  done
done
```

커밋 목록은 **손으로 적지 않고 그 자리에서 `git log` 로 낸다** — 손 목록으로 돌리면 뒤 라운드가 더한 커밋(가령 승인 전 일괄이 `gate-tests` 에 등재한
`UnusableRawRowsTest`)의 문맥에서 앞 커밋의 patch 가 `does not apply` 로 선다. `--3way` 는 쓰지 않는다. 한 hunk 가 실패하면 그 파일만 `git checkout
HEAD -- <파일>` 로 되살린 뒤 목록을 다시 내어 처음부터 돈다 — 이 slice 의 공유 파일 변경은 전부 **추가**(정책 쌍 다섯 · 등재 열 · 입력 선언 블록 ·
문장 하나 · 문장 둘 · 문단 둘)라 수동 보정은 지울 줄만 있다. 실측 `exit 0` **열두 hunk 전부**.

## ③ 트리 대조

`git diff --name-status ecdc9d9f` 에 남는 것은 `reports/evidence/m6/6g2c/**` 셋뿐. 복원 47 + 공유 6 = **53 경로의 파일 SHA 가 base 와 같다**
(`git diff --name-only ecdc9d9f -- <53 경로>` 빈 출력). `ml-engine/**` 는 base 와 **동일**(0 경로 차이) — Python job 은 그 트리에서 base 의 결과
(`main` CI, PR #58 머지)가 그대로 적용되므로 **트리 동일성으로 갈음**한다. Kotlin 은 evidence 디렉터리가 게이트 입력이라 갈음하지 않고 ④⑤⑥ 을 돈다.

## ④⑤⑥ — 되돌린 트리에서 `./gradlew --no-daemon check`

| 축 | 결과 |
|---|---|
| ④ compile | `check` exit **0**, 349 task(214 실행 · 135 캐시) — 전 모듈 compile·compileTest 통과(test 실행의 전제) |
| ⑤ test | 전 모듈 **2,638** test · 실패 0 · skip 4 — HEAD 의 2,665 에서 이 slice 가 더한 27 이 정확히 빠졌다(트리 53 경로가 base 와 같으므로 base 의 수와 같다) |
| ⑥ 게이트 | `leakPatternGate` · 모듈별 `sizeGate` · 모듈별 `gateExecutionGate`(`:app`·`:adapters` 포함) · `qualityBaseline` 전부 수행·성공 — 되돌리지 않은 evidence 셋이 든 트리에서 초록. 이 파일은 실측 뒤 커밋이라 그 트리에 없다 — 참조형 누출 스캔 0 은 이 파일까지 돌렸고, 이 파일을 담은 HEAD 의 `check` 정본은 PR 조치 코멘트다 |

**⑥ 이 초록인 조건**은 되돌리지 않는 evidence 의 누출 어휘 매치가 0 이라는 것이다(`commands.md` K·P 두 절의 참조형 스캔 0). 0 이 아니면 base 의
허용 목록에 없어 되돌린 트리에서 게이트가 붉는다(M4 선례). 그 목록은 만지지 않았다.

## 보존 확인 — 두 축을 둘 다

| 축 | 확인 |
|---|---|
| **내 줄이 사라졌다** | 정책에 `RunStateLockKt->` 0 / 등재에 snapshot·wiring 신설 test 이름 일곱 0 / `workflow/build.gradle.kts` 에 `snapshot-schema.md` 0 / 스키마 문서에 `D-6G2c-17` 0 / runbook 에 「계수 열둘」·「당시 코드」 0 / `milestone-6.md` 에 「6G-2c 착수」·「6G-2c 종결」 0 |
| **남의 줄이 남았다** | 정책에 `collection.transport.roots` 1 · `policy.version=8` 1(6G-2b) / 등재에 `TransportSurfaceGateTest` 2 / 스키마 문서의 `UNDERPOWERED` **3**(base 에 이미 셋 있다 — 그래서 「내 줄」 표지는 이 낱말이 아니라 `D-6G2c-17` 이다) / runbook 의 「계수 아홉」 2 / `milestone-6.md` 에 「6G-2b 착수·종결」 2 |

뒤 축을 안 재면 「통째로 되돌려 남의 줄까지 걷었다」가 보이지 않는다. 세는 **형태**도 조심해야 한다 — `UNDERPOWERED` 로 「내 줄 사라짐」을 세면
base 의 셋이 남아 거짓 경보가 된다(이 실측에서 겪었다).

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 **아니다**. 보는 것은 그 사이에 되돌림 대상이 움직였는가다 —
`git diff --name-only 82c8b809..<판정 SHA> -- <① 경로 47 + ② 파일 여섯>` 이 빈 출력이면 유효하다. 이 slice 의 판정 SHA(K `51b4d960` · P `fa7483cb`)는
실측 HEAD 의 **조상**이다 — 실측 HEAD 가 뒤에 오는 것은 종결 문단 커밋이 `milestone-6.md`(hunk 대상)를 건드리기 때문이고(6G-2b PR #58 리뷰가 세운
순서), 그 사이 커밋이 K·P 경로를 건드리지 않았음은 재동결 대조(`git diff --name-only 51b4d960..82c8b809 -- adapters app workflow config` ·
`git diff --name-only fa7483cb..82c8b809 -- ml-engine`)가 빈 출력으로 보였다. **종결 커밋 뒤에 복원·hunk 경로가 다시 움직이면**(PR 조치 라운드) ①~③ 을
그 커밋에서 다시 재고 실측 HEAD 를 **별도 커밋**으로 올린다.

## 되돌리지 않는 것

- `reports/evidence/m6/6g2c/**` — 기록.
- 실행 상태 디렉터리 `m6-6g` 의 바이트 — 이 slice 가 건드리지 않았다(D-18).
- 6G-2g 로 넘긴 OPEN 일곱과 6G-2c-형식 다섯 — 계약 D-27·D-28.
