# M6/6G-2d — rollback

실측 HEAD: `509aa9fe` (3차 리뷰 대응의 마지막 산출물 커밋)

base `c357e437`. 되돌림은 **range revert 가 아니라 경로 한정**이다 — 같은 range 의 팀장 레인 커밋
(마일스톤 문단)까지 걷지 않는다.

## 목록은 손으로 쓰지 않는다

```
git diff --name-status c357e437..509aa9fe
```
에서 기계적으로 낸다. `reports/evidence/m6/6g2d/**`(되돌리지 않는다) 와 공유 파일 셋(아래 hunk 격리)
을 빼면 **A 14 · M 39**. 라운드마다 파일이 늘면 이 절차를 다시 돌린다 — 목록이 낡는 것이 이 결함의
실제 원인이다(A 가 라운드마다 1 → 5 → 6 → 7 → 10 → 12 → 14 로 늘었다: 금액 계약 test · 형식 판별 production·test ·
공통 하네스 · 원장 줄 형태 test · 절단 사유 게이트 test · 로그 줄 값 test · 추출 계수 판과 그 공통 대역 ·
상한 회계 판 · 내구 원시연산 · 장부 판독 · 내구 test · 러너 사유 코드 test). **새 파일 열넷은 전부
in_scope** 경로다 — 갱신을 빠뜨리면 ① 이 파일 하나를 남기고 ④ compile 이 사라진 심볼을 가리켜 깨진다
(vr r3 실측: 그 디렉터리를 빼고 돌리면 게이트 test 파일이 **남고** ④ 가 `Unresolved reference` 로 exit 1 이었다).
**같은 결함을 3차 리뷰 대응에서 한 번 더 실측했다**: 러너의 사유 코드 파일(`OpeningCollectionLines.kt`)이
목록에 없어 되돌린 트리가 어댑터에서 사라진 함수를 부르고 ④⑤⑥ 이 exit 1 이었다(③ 은 0 줄이었다 —
대조 집합이 같은 목록이라 그 구멍을 보지 못한다. ④ 가 있어야 보이는 자리다).
**3차 리뷰 대응(D-6G2d-48)이 더한 새 파일은 둘**이다 — 내구 원시연산의 test 와 러너 사유 코드의 test.
그 앞 라운드가 더한 둘은 내구 원시연산(`RunStateDurability.kt`)과 장부 판독(`RunStateFacts.kt`). 둘 다 이미 복원 목록에 든 경로(`adapters/src/main/.../snapshot`)라 목록의
**인자는 그대로**이고, 바뀐 것은 지워질 파일 수뿐이다. 앞 라운드가 더한 셋은 계수 배선 판과 그 공통
대역, 상한 회계 판이다(파일 500 줄 한도에서 갈렸다). 둘 다 이미 복원 목록에 든 경로(`adapters/src/test/.../snapshot`)라 목록의 **인자는
그대로**이고, 바뀐 것은 지워질 파일 수뿐이다(대조: `git diff --name-status <base>..HEAD | grep '^A'`).

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
  app/src/main/kotlin/bidvector/app/collection/OpeningCollectionLines.kt \
  app/src/test/kotlin/bidvector/app/architecture \
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

세 파일은 다른 slice 의 줄을 담고 있어 `base..HEAD` 통째 역적용을 쓰지 않는다. 먼저 그 파일을 만진
커밋을 나열한다(`git log --oneline c357e437..<실측 HEAD> -- <파일>` — 범위의 끝은 위 「실측 HEAD」이고
라운드마다 그 값으로 다시 돌린다, vr r1 L-4), 그다음 자기 커밋만 역적용한다.

```
git diff ec7e144f~1..ec7e144f -- milestone-6.md | git apply -R
git diff a3843716~1..a3843716 -- milestone-6.md | git apply -R
git diff f714e3f5~1..f714e3f5 -- milestone-6.md | git apply -R
git diff de50a997~1..de50a997 -- milestone-6.md | git apply -R
git diff 9a411591~1..9a411591 -- config/quality/architecture-policy.properties | git apply -R
git diff 645c9ea7~1..645c9ea7 -- config/quality/gate-tests.properties | git apply -R
git diff ff808399~1..ff808399 -- config/quality/gate-tests.properties | git apply -R
git diff 4c09e390~1..4c09e390 -- config/quality/gate-tests.properties | git apply -R
git diff 19746e2b~1..19746e2b -- config/quality/gate-tests.properties | git apply -R
```

- `milestone-6.md` — 이 slice 가 만진 커밋은 **넷**: 착수 문단(`de50a997`)과 종결 문단(`f714e3f5`, D-6G2d-35)과 종결 문단의 A-3 문면 정정 둘(`ec7e144f` · `a3843716` — 이 목록 갱신 커밋은 문단 커밋 뒤에 따로 온다). 역적용은 **정정 → 종결 → 착수** 순서(새 커밋부터). 종결 문단은 착수 문단 아래 같은 자리에 있고 문단 단위로 지운다.
- `config/quality/architecture-policy.properties` — 이 slice 가 만진 커밋은 **등재 해제 한 줄**
  (`9a411591`)이다. 역적용은 그 한 줄을 **되살린다**.
- `config/quality/gate-tests.properties` — 이 slice 가 만진 커밋은 **넷**(`19746e2b`·`4c09e390`·
  `ff808399`·`645c9ea7`)이고 각각 등재 한 줄이다(`ff808399` 은 `gate.tests.workflow` 쪽이라 목록이 다르다). 역적용은 **새 커밋부터**(위 순서) 돌린다 — 뒤 커밋의 줄이 남은 상태에서 앞
  커밋을 되돌리면 컨텍스트가 어긋난다. 되돌린 트리에는 그 게이트 test 파일들이 없으므로 등재도 없어야
  한다.

**`--3way` 도 자동 해소에 실패할 수 있다**(같은 삽입 지점에 다른 slice 의 문단이 붙은 경우). 수동
해소 절차: `milestone-6.md` 는 「6G-2d 착수」 문단 **전체**를 지우고 그 앞뒤 문단은 남긴다(실측: 남은 6G 언급
넷은 base 의 것이다).
`architecture-policy.properties` 는 use case 의 procurement 허용 목록에서 축 결말 타입 한 줄을
**되살리고**(알파벳 순서가 아니라 원래 자리 — 목록 머리의 시도 갈래 타입 바로 뒤) 다른 줄은 손대지
않는다. `gate-tests.properties` 는 app 목록에서 두 줄(절단 사유 게이트 · 로그 줄 값)과 workflow 목록에서 한
줄(상한 회계 판)을 지운다 — 그 사이의 다른 slice 줄은 남긴다. 확인은 **둘 다** 본다: 「내 줄이 사라졌다」와 「남의 줄이 남았다」.

## ①~⑥ 실측 (버릴 clone, 실측 HEAD `509aa9fe`)

| 축 | 결과 |
|---|---|
| ① `git restore …` | exit 0 |
| ② D/M 수 | **D 14 · M 39** — 위 기계 목록과 같다. 남은 untracked 0 |
| ② 공유 파일 hunk 역적용 **아홉** | 각각 exit 0 · conflict 0(`--3way` 없이) |
| ③ `git diff c357e437 -- <복원 경로들 + 공유 파일 셋>` | **0 줄**(트리 동일) |
| ③ 남의 줄 남음 | `milestone-6.md` 에 base 의 6G 언급 넷 그대로 · 허용 목록의 축 결말 타입 한 줄 **되살아남** · 게이트 등재 파일에 이 slice 의 두 줄 **없음**, 그 사이 다른 slice 줄은 남음 |
| ④ `./gradlew --no-daemon compileTestKotlin` | exit 0 |
| ⑤⑥ `./gradlew --no-daemon check --no-build-cache` | exit 0 · `test` 2,505 · skipped 4 · failures 0 · errors 0 · `compatibilitySmokeTest` 8 |
| ③ 남의 줄 남음(게이트 등재) | 이 slice 가 더한 네 줄(절단 사유 게이트 · 로그 줄 값 · 상한 회계 · 러너 사유 코드) **0 건**, 목록의 다른 줄은 그대로 |

갈음은 「HEAD 가 초록이다」가 아니라 **트리 동일성**으로만 한다 — 되돌린 트리의 파일이 base 와 같은지
(③)를 재고, 그 위에서 ④⑤⑥ 을 돌린다.

## 실측이 유효한가 (verifier 대조)

```
git diff --name-only 509aa9fe..<판정 SHA> -- <위 ① 의 경로들> milestone-6.md \
  config/quality/architecture-policy.properties config/quality/gate-tests.properties
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

- 버릴 clone(`--no-hardlinks`)에서 `509aa9fe` 를 checkout 한 뒤 위 ①②③④⑤⑥ 을 **이 문서에 적힌
  명령 그대로** 순서대로 돌렸다 —
  전부 위 표의 값이다. 갈음은 「HEAD 가 초록이다」가 아니라 **③ 의 트리 동일성**이다.
- ⑥ 는 evidence 셋을 **남긴 채** exit 0 이다 — 되돌리지 않기로 한 문서가 base 의 게이트를 붉히지
  않는다. 이 slice 는 라운드마다 evidence 파일 셋을 늘리지 않고(같은 셋을 고친다) 누출 어휘를 축어로
  담지 않는 규율도 그대로라, 그 성질이 라운드를 건너 유지된다.
- 되돌린 트리의 `test` 2,505 는 base 의 값이다 — HEAD 의 값(acceptance 절)과의 차가 이 slice 가 더한
  test 수다. 갈음의 근거는 이 수가 아니라 ③ 의 트리 동일성이고, 수는 그 위에서 읽는 확인이다.
- 이 줄 자신을 더하는 커밋은 실측 뒤에 오므로, 마지막 HEAD 의 판정은 verifier 와 PR 조치 코멘트가
  정본이다(evidence-pack 규격).
