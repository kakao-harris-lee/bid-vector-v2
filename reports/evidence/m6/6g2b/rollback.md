# M6/6G-2b — rollback

> 되돌림은 range revert 가 아니라 **in_scope 경로 한정**이다. 목록은
> `git diff --name-status <base>..<실측 HEAD>` 에서 기계로 낸다. **라운드마다 그 라운드의 마지막 산출물
> 커밋에서 다시 낸다** — 앞 라운드 실측을 옮기지 않는다.

**실측 HEAD `cc3fd1c7`**(A-2 집행 뒤 마지막 산출물 커밋) · base **`1745a3e2`**. ①~⑥ 전부 그 HEAD 의
**버릴 clone** 에서 돌렸다. A-2 가 전용 파일 하나와 공유 정책 hunk 하나를 더했으므로 목록·절차·실측을 다시
냈다(앞 라운드 값을 옮기지 않았다).

## 되돌림이 싼 이유

이 slice 는 test·정책 파일만 바꾼다. 출하 바이트·수집 코드·실행 상태 형식·원장 형식이 그대로이므로
되돌림이 실수집을 건드리지 않는다 — 돌아가는 것은 **게이트의 민감도**뿐이다(전송 축은 6G 의 두 게이트로,
반사 축은 뿌리 `workflow`·`app` 과 전역 `getName` 허용으로 복귀). 되돌린 뒤 production 은 여전히 깨끗하다
(실제 우회 호출 0 · 등재 밖 반사 0, 착수 실측).

## 되돌림 목록 (기계 산출) — 전체 경로는 ① 명령의 인자가 정본

| 상태 | 파일 | 되돌림 |
|---|---|---|
| M | `ArchitecturePolicy.kt` · `CollectionArchitectureRules.kt` · `CollectionArchitectureGateTest.kt` · `CollectionArchitectureGateCatchesViolationsTest.kt`(app test) | ① base 로 restore |
| A | `TransportSurfaceRules.kt` · `TransportSurfaceGateTest.kt` · `TransportSurfaceGateCatchesViolationsTest.kt`(app test) | ① restore 가 삭제(base 에 없다) |
| A | `TransportBypassSamples.kt` · `TransportRegressionSamples.kt`(`archfixture/violating/transport`) | ① 같음 |
| A | `RogueAdapterReflectionPeek.kt`(`archfixture/violating/adapters`, A-2) | ① 같음 |
| M | `config/quality/architecture-policy.properties` | ② **공유 파일** — 커밋 해시로 hunk 격리, **두 커밋** |
| M | `config/quality/gate-tests.properties` | ② 같음, 한 커밋 |
| M | `milestone-6.md` | ② 같음 — in_scope 의 「착수·종결 문단만」 조항이고 지금 있는 것은 팀장의 착수 문단 하나다 |

`reports/evidence/m6/6g2b/**` 는 **되돌리지 않는다**(이 slice 의 기록, `scope.md` 는 계약 커밋의 것).
그 선택의 게이트 영향은 ⑥ 에 있다.

## ① 전용 파일 — 한 번에

```
git restore --source=1745a3e2 --staged --worktree -- \
  app/src/test/kotlin/bidvector/app/architecture/ArchitecturePolicy.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateCatchesViolationsTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureRules.kt \
  app/src/test/kotlin/bidvector/app/architecture/TransportSurfaceGateCatchesViolationsTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/TransportSurfaceGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/TransportSurfaceRules.kt \
  app/src/test/kotlin/bidvector/archfixture/violating/adapters/RogueAdapterReflectionPeek.kt \
  app/src/test/kotlin/bidvector/archfixture/violating/transport/TransportBypassSamples.kt \
  app/src/test/kotlin/bidvector/archfixture/violating/transport/TransportRegressionSamples.kt
```

경로는 **개별 인자**다. `git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 신규 파일에서
pathspec 오류로 아무것도 적용되지 않는다. 실측 `exit 0`.

## ② 공유 파일 셋 — hunk 격리, 나중 커밋부터

커밋 목록을 먼저 낸다(라운드마다 다시): `git log --oneline 1745a3e2..cc3fd1c7 -- <파일>`. 실측은 아키텍처
정책 **둘**(A-2 반사 축 · 전송 축) · 장부 **하나** · `milestone-6.md` **하나**다.

```
git diff cc3fd1c7~1..cc3fd1c7 -- config/quality/architecture-policy.properties | git apply -R
git diff 3e670054~1..3e670054 -- config/quality/gate-tests.properties | git apply -R
git diff 96c6f73d~1..96c6f73d -- config/quality/architecture-policy.properties | git apply -R
git diff 7938ae8d~1..7938ae8d -- milestone-6.md | git apply -R
```

넷 전부 **exit 0** · conflict 0. 나중 것부터 역적용한다 — 정책 파일의 두 hunk 는 **A-2 쪽이 먼저**다
(전송 축 커밋을 먼저 되돌리면 A-2 hunk 의 문맥이 사라진다). **목록이 늘면 이 절차를 다시 돌린다** — 종결
문단 커밋과 수정 라운드의 공유 파일 커밋이 각각 한 줄씩 늘린다.

삽입 지점이 인접하면 `--3way` 도 자동 해소에 실패하므로(M4·M6 선례) 그때는 내 몫만 문면으로 되돌리는
**수동 절차**가 정본이다.

- 아키텍처 정책 — 셋이다. (a) `policy.version` 을 8 → 7 로 되돌리고 그 위 v8 주석 여섯 줄 삭제.
  (b) `collection.transport.*` 여섯 키와 그 머리 주석 전체를 삭제한 뒤 그 자리에 base 의
  `collection.http-client.*` 셋 · `collection.transport-bypass.*` 셋과 각자의 주석을 복원.
  (c) `collection.reflection.roots`·`type-pairs`·`class-member-pairs` 와 A-2 주석 단락 둘을 삭제한 뒤
  `collection.reflection.allowed-referencers=`(빈 값) · `class-allowed-members=getName` 두 줄과 base 의
  `(i)` 머리 주석을 복원. 셋 다 base 파일에서 그 블록을 떠 온다.
- 장부: `gate.tests.app` 의 이름 둘(`TransportSurfaceGateTest`·`TransportSurfaceGateCatchesViolationsTest`)과
  머리 주석 일곱 줄 삭제.
- `milestone-6.md`: 6G-2b 착수 문단 한 단락(그리고 종결 문단이 생기면 그것도) 삭제.

## ③ 실측 (버릴 clone, HEAD `cc3fd1c7`)

`restore` exit 0 · `apply -R` 넷 exit 0. 되돌린 뒤 `git status --porcelain` 이 **D 6 · M 7** 이고,
`git diff 1745a3e2 --name-status -- <되돌린 열세 경로>` 는 **빈 출력**이다 — 그 경로에서 base 와 **트리
동일**이다(갈음을 「HEAD 초록」이 아니라 트리 동일성으로 한다).

## ④⑤⑥ 실측 (같은 clone, 되돌린 트리)

`./gradlew --no-daemon check` **exit 0**(9m 1s · 348 task).

| 축 | 값 |
|---|---|
| ④ compile | 전 모듈 compile 성공(`:app:compileTestKotlin` 포함) |
| ⑤ test | 전 모듈 **2,607** test · 실패 0 · skip 4 — **base 의 수와 같다**(HEAD 는 2,623) |
| ⑥ 게이트 | `leakPatternGate` · 모듈별 `sizeGate` · 모듈별 `gateExecutionGate`(`:app:gateExecutionGate` 포함) 수행·성공 |

⑤의 2,607 이 ③의 트리 동일성을 독립적으로 확인한다 — 되돌린 트리의 test 수가 base 의 수이고, 더한 16 이
정확히 빠졌다.

**⑥ 이 초록인 조건**은 되돌리지 않는 evidence 의 누출 어휘 매치가 0 이라는 것이다(`commands.md`). 0 이
아니면 base 의 허용 목록에 없어 되돌린 트리에서 게이트가 붉는다(M4 선례). 그 목록은 만지지 않았다.
이 clone 의 트리에는 앞 라운드의 evidence 셋과 `scope.md` 가 있었고 그 상태로 ⑥ 이 초록이다 — 이 라운드가
더하는 줄의 매치가 0 인 것은 `commands.md` 의 줄 단위 스캔이 든다.

## 보존 확인 — 두 축을 둘 다

| 축 | 확인 |
|---|---|
| **내 줄이 사라졌다** | 정책에 `collection.transport.surface` 0 · `collection.transport.holders` 0 · `collection.reflection.roots` 0 · `collection.reflection.class-member-pairs` 0 · `policy.version=8` 0 / 장부에 `TransportSurface` 0 / `milestone-6.md` 에 「6G-2b 착수 2026-10-03」 0 |
| **남의 줄이 남았다** | 정책에 `collection.http-client.type` 1 · `collection.transport-bypass.types` 1 · `collection.reflection.allowed-referencers` 1 · `collection.reflection.class-allowed-members` 1 · `policy.version=7` 1 / 장부에 6G-2f 의 `OpeningPageSizeE2ETest` 1 / `milestone-6.md` 에 「6G-2f 착수·종결」 1 |

뒤 축을 안 재면 「통째로 되돌려 남의 줄까지 걷었다」가 보이지 않는다. 고정 문자열로 센다 —
`collection.transport.` 를 정규식으로 쓰면 `.` 이 와일드카드라 복원된 `collection.transport-bypass` 를
내 줄로 잘못 세고 「사라졌다」가 거짓이 된다(실측에서 한 번 그렇게 나왔다).

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋이 언제나 뒤에 오므로 둘은 영원히 다르다). 보는 것은
그 사이에 되돌림 대상이 움직였는가다 — `git diff --name-only cc3fd1c7..<판정 SHA> -- <위 열세 경로 개별
인자>` 가 빈 출력이면 유효하다. 한 줄이라도 나오거나 **판정 SHA 가 실측 HEAD 의 자손이 아니면**
미검증이다.

이 slice 는 **게이트 술어를 바꾸므로** 수정 라운드의 모든 커밋이 severity 와 무관하게 표적 재검증 대상이고,
그 커밋이 위 열세 경로에 닿으면 이 절의 대조가 깨져 ①~⑥ 을 다시 돌려야 한다(A-2 라운드가 그 사례다).

## 되돌리지 않는 것

하네스 경로(`CLAUDE.md`·`.claude/**`) — 이 range 의 하네스 레인 커밋은 **없다**
(`git log --oneline 1745a3e2..cc3fd1c7 -- CLAUDE.md .claude/` 빈 출력).
