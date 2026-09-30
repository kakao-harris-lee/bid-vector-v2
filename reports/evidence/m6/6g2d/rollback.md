# M6/6G-2d — rollback

실측 HEAD: `0e8b957a` (재작업 1 의 마지막 산출물 커밋)

base `c357e437`. 되돌림은 **range revert 가 아니라 경로 한정**이다 — 같은 range 의 팀장 레인 커밋
(마일스톤 문단)까지 걷지 않는다.

## 목록은 손으로 쓰지 않는다

```
git diff --name-status c357e437..0e8b957a
```
에서 기계적으로 낸다. `reports/evidence/m6/6g2d/**`(되돌리지 않는다) 와 공유 파일 둘(아래 hunk 격리)
을 빼면 **A 5 · M 35**. 라운드마다 파일이 늘면 이 절차를 다시 돌린다 — 목록이 낡는 것이 이 결함의
실제 원인이다(재작업 1 에서 A 가 1 → 5 로 늘었다: 금액 계약 test · 형식 판별 production·test · 공통
하네스 · 원장 줄 형태 test). **새 파일 다섯은 전부 in_scope** 경로다.

## ① Kotlin 경로 한정 복원

```
git restore --source=c357e437 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/snapshot \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsCallGate.kt \
  adapters/src/main/kotlin/bidvector/adapters/koneps/KonepsPageUriBuilder.kt \
  adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcCollectedAxisStore.kt \
  adapters/src/test/kotlin/bidvector/adapters/snapshot \
  adapters/src/test/kotlin/bidvector/adapters/koneps \
  adapters/src/test/kotlin/bidvector/adapters/persistence/OpeningCollectionLedgerTest.kt \
  app/src/main/kotlin/bidvector/app/collection/SnapshotExtractionRunner.kt \
  app/src/test/kotlin/bidvector/app/collection \
  app/src/test/kotlin/bidvector/app/conformance/KonepsCollectionAccountingExecutors.kt \
  app/src/test/kotlin/bidvector/app/wiring/OpeningCollectionWiringTest.kt \
  procurement/src/main/kotlin/bidvector/procurement \
  procurement/src/test/kotlin/bidvector/procurement \
  workflow/src/main/kotlin/bidvector/workflow/collection \
  workflow/src/test/kotlin/bidvector/workflow/collection
```

경로는 **개별 인자**다(변수 하나로 묶으면 pathspec 이 하나가 되어 아무것도 매치하지 않는다).
`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다 — `git checkout <base> --`
를 쓰지 않는 이유도 그것이다(base 에 없는 경로마다 pathspec 오류로 아무것도 적용되지 않는다).

**하네스 경로(`CLAUDE.md`·`.claude/**`)와 `reports/evidence/**` 는 되돌리지 않는다.** 이 range 의
하네스 레인 커밋은 **없다**(scope.md 「하네스 레인 변경」 절과 같은 값).

## ② 공유 파일 — 커밋 해시 hunk 격리

두 파일은 다른 slice 의 줄을 담고 있어 `base..HEAD` 통째 역적용을 쓰지 않는다. 먼저 그 파일을 만진
커밋을 나열한다(`git log --oneline c357e437..bfad2201 -- <파일>`), 그다음 자기 커밋만 역적용한다.

```
git diff de50a997~1..de50a997 -- milestone-6.md | git apply -R
git diff 9a411591~1..9a411591 -- config/quality/architecture-policy.properties | git apply -R
```

- `milestone-6.md` — 이 slice 가 만진 커밋은 **착수 문단 하나**(`de50a997`)다.
- `config/quality/architecture-policy.properties` — 이 slice 가 만진 커밋은 **등재 해제 한 줄**
  (`9a411591`)이다. 역적용은 그 한 줄을 **되살린다**.

**`--3way` 도 자동 해소에 실패할 수 있다**(같은 삽입 지점에 다른 slice 의 문단이 붙은 경우). 수동
해소 절차: `milestone-6.md` 는 「6G-2d 착수」 문단 **전체**를 지우고 그 앞뒤 문단은 남긴다(실측: 남은 6G 언급
넷은 base 의 것이다).
`architecture-policy.properties` 는 use case 의 procurement 허용 목록에서 축 결말 타입 한 줄을
**되살리고**(알파벳 순서가 아니라 원래 자리 — 목록 머리의 시도 갈래 타입 바로 뒤) 다른 줄은 손대지
않는다. 확인은 **둘 다** 본다: 「내 줄이 사라졌다」와 「남의 줄이 남았다」.

## ①~⑥ 실측 (버릴 clone, 실측 HEAD `0e8b957a`)

| 축 | 결과 |
|---|---|
| ① `git restore …` | exit 0 |
| ② D/M 수 | **D 5 · M 35** — 위 기계 목록과 같다. 남은 untracked 0 |
| ② 공유 파일 hunk 역적용 둘 | 각각 exit 0 · conflict 0(`--3way` 없이) |
| ③ `git diff c357e437 -- <복원 경로들 + 공유 파일 둘>` | **0 줄**(트리 동일) |
| ③ 남의 줄 남음 | `milestone-6.md` 에 base 의 6G 언급 넷 그대로 · 허용 목록의 축 결말 타입 한 줄 **되살아남** |
| ④ `./gradlew --no-daemon compileTestKotlin` | exit 0 |
| ⑤⑥ `./gradlew --no-daemon check --no-build-cache` | exit 0 · `test` 2,505 · skipped 4 · failures 0 · errors 0 · `compatibilitySmokeTest` 8 |

갈음은 「HEAD 가 초록이다」가 아니라 **트리 동일성**으로만 한다 — 되돌린 트리의 파일이 base 와 같은지
(③)를 재고, 그 위에서 ④⑤⑥ 을 돌린다.

## 실측이 유효한가 (verifier 대조)

```
git diff --name-only 0e8b957a..<판정 SHA> -- <위 ① 의 경로들> milestone-6.md \
  config/quality/architecture-policy.properties
```
**빈 출력이면 유효하다.** 한 줄이라도 나오면 그 뒤 커밋이 되돌림 대상을 바꾼 것이므로 이 실측은
낡았고 그 절은 통과가 아니라 미검증이다. 대조 경로는 **이 문서 자신의 목록**이다 — 되돌림이 덮는
집합과 술어의 집합을 같게 둔다(evidence 커밋과 팀장 레인 문서 편집은 그래서 잡히지 않는다).

## 되돌린 뒤 남는 것

- `reports/evidence/m6/6g2d/**` 넷(scope·commands·rollback 과 이 문단). 되돌리지 않으므로 그 문서가
  가리키는 코드가 base 상태라는 불일치가 남는다 — **의도**다(무엇을 되돌렸는지의 기록이 함께
  사라지면 안 된다).
- 그 남은 evidence 가 base 의 누출 스캔 allowlist 에 없어 해당 게이트를 붉힐 수 있다(M4 에서 실측된
  구조적 한계). **이 slice 에서는 붉지 않았다** — ⑤⑥ 의 `check` 가 evidence 셋을 남긴 채 exit 0 이다
  (아래 「실측」 의 두 번째 줄). 이 slice 의 evidence 는 누출 어휘를 축어로 담지 않아 그 자기매치가
  생기지 않는다.

## 비활성화 (되돌리지 않고 멈추는 길)

이 slice 는 새 배선·새 flag 를 만들지 않는다 — 전부 기존 경로의 거동 변경이다. 그래서 「끄는」 길은
없고, 되돌리는 길만 있다. 실수집이 시작되기 **전**에는 되돌림 비용이 값싸다: 실행 상태 디렉터리가
하나도 없으므로 형식 version 변경이 기존 디렉터리를 막을 일이 없다(알려진 제한 (f)).

## 실측

- 버릴 clone(`--no-hardlinks`)에서 `0e8b957a` 를 checkout 한 뒤 위 ①②③④⑤⑥ 을 순서대로 돌렸다 —
  전부 위 표의 값이다. 갈음은 「HEAD 가 초록이다」가 아니라 **③ 의 트리 동일성**이다.
- ⑥ 재실측(r0 에서, evidence 커밋 checkout → 같은 ①② 절차): evidence 셋을 **남긴 채** `check` 가
  exit 0 이었다 — 되돌리지 않기로 한 문서가 base 의 게이트를 붉히지 않는다. 재작업 1 은 evidence 파일을
  늘리지 않았고(같은 셋을 고쳤다) 누출 어휘를 축어로 담지 않는 규율도 그대로라, 그 성질은 유지된다.
- 이 줄 자신을 더하는 커밋은 실측 뒤에 오므로, 마지막 HEAD 의 판정은 verifier 와 PR 조치 코멘트가
  정본이다(evidence-pack 규격).
