# M6/6G-2f — rollback

> 되돌림은 range revert 가 아니라 **in_scope 경로 한정**이다. 목록은 손으로 쓰지 않고
> `git diff --name-status <base>..<실측 HEAD>` 에서 기계로 낸다. **라운드마다 그 라운드의 마지막
> 산출물 커밋에서 다시 낸다** — 앞 라운드 실측을 옮기지 않는다.

**실측 HEAD: `a033e80d`**(검토 라운드 뒤 이 slice 의 마지막 산출물 커밋) · base **`9a5aa26a`**.
①②③ 과 보존 확인은 그 HEAD 의 **버릴 clone** 에서 다시 돌린 결과다. ④⑤⑥ 은 그 HEAD 에서 재실측하지
않았다 — 사유와 대신 돌린 것은 아래 「④⑤⑥ 의 자리」에 있다.

## 가장 싼 되돌림은 코드가 아니다

운영 중이라면 되돌릴 것이 없다 — 다음 실행에 `--bidvector.koneps.opening.rows-per-page=100` 인자
하나면 이 slice 전의 쪽 크기다(재빌드·jar 교체 없이). 확정 표본은 쪽 크기와 무관하므로 그 인자가
`sample-list.tsv` 를 건드리지 않는다(쓴 날과 값은 evidence 에 적는다 — checklist 알려진 제한 9).
아래는 **산출물 자체를 걷는** 경로다.

## 되돌림 목록 (기계 산출)

| 상태 | 경로 | 되돌림 |
|---|---|---|
| M | `app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt` | ① base 로 restore |
| M | `app/src/main/kotlin/bidvector/app/wiring/OpeningCollectionWiring.kt` | ① 같음 |
| M | `app/src/test/kotlin/bidvector/app/collection/CollectionRunStateFixtures.kt` | ① 같음 |
| M | `app/src/test/kotlin/bidvector/app/collection/MockOpeningKonepsHttp.kt` | ① 같음 |
| M | `app/src/test/kotlin/bidvector/app/collection/OpeningCollectionE2EHarness.kt` | ① 같음 |
| A | `app/src/test/kotlin/bidvector/app/collection/OpeningPageSizeE2ETest.kt` | ① restore 가 삭제(base 에 없다) |
| A | `app/src/test/kotlin/bidvector/app/collection/OpeningRowsPerPageTest.kt` | ① 같음 |
| M | `docs/runbook/m6-6g-real-collection.md` | ② **공유 파일** — 커밋 해시로 hunk 격리 |
| M | `config/quality/gate-tests.properties` | ② **공유 파일** — 같음 |

`reports/evidence/m6/6g2f/**` 는 **되돌리지 않는다**(이 slice 의 기록이고 `scope.md` 는 착수 커밋의
것이다). 그 선택의 게이트 영향은 ⑥ 에 있다.

## ① 전용 파일 — 한 번에

```
git restore --source=9a5aa26a --staged --worktree -- \
  app/src/main/kotlin/bidvector/app/collection/OpeningCollectionProperties.kt \
  app/src/main/kotlin/bidvector/app/wiring/OpeningCollectionWiring.kt \
  app/src/test/kotlin/bidvector/app/collection/CollectionRunStateFixtures.kt \
  app/src/test/kotlin/bidvector/app/collection/MockOpeningKonepsHttp.kt \
  app/src/test/kotlin/bidvector/app/collection/OpeningCollectionE2EHarness.kt \
  app/src/test/kotlin/bidvector/app/collection/OpeningPageSizeE2ETest.kt \
  app/src/test/kotlin/bidvector/app/collection/OpeningRowsPerPageTest.kt
```

경로는 **개별 인자**다. `git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 신규 파일에서
pathspec 오류로 아무것도 적용되지 않는다. 실측 `exit 0`.

## ② 공유 파일 둘 — hunk 격리

먼저 그 파일을 만진 커밋을 나열한다(라운드마다 다시): `git log --oneline 9a5aa26a..a033e80d -- <파일>`.
실측은 runbook **둘**(`a033e80d` 검토 라운드 · `6150f9ef` 착수 라운드) · 장부 **하나**(`bf8bbac3`)이고,
이 range 에 다른 레인·다른 slice 의 커밋이 그 셋을 만지지 않아 수동 해소가 필요 없다. runbook 은
**나중 커밋부터** 역순으로 간다.

```
git diff a033e80d~1..a033e80d -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff 6150f9ef~1..6150f9ef -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff bf8bbac3~1..bf8bbac3 -- config/quality/gate-tests.properties | git apply -R
```

셋 다 **exit 0** · conflict 0. **목록이 하나라도 늘면 이 절차를 다시 돌린다** — 검토 라운드가 runbook
커밋을 하나 더 만들어 실제로 늘었다. 팀장 레인이 머지 전에 또 만지면 다시 나중 커밋부터 가야 하고,
삽입 지점이 인접하면 `--3way` 도 자동 해소에 실패한다(M4·M6 선례). 그때는 내 몫만 문면으로 되돌리는
수동 절차가 정본이다 — runbook 에서 내 몫은 §0 표의 `OPEN-6G2D-MAX-PAGES-FINAL` 행 · §1 4단계의 `<jar SHA>` 문장 · §2-2 의 불릿 둘 · §5 의 `MAX_PAGES` 줄과 쿼터 줄 · §7 절 전체이고, 장부에서는 `gate.tests.app` 의 이름 둘과 머리 주석 세 줄이다.

## ③ 실측 (버릴 clone, HEAD `a033e80d`)

| 단계 | 결과 |
|---|---|
| ③ 명령 exit · D/M | `restore` 0 · `apply -R` 셋 0. 되돌린 뒤 `git status --porcelain` 이 **D 2 · M 7**(신규 둘 삭제 + 수정 일곱) |
| ③ 트리 동일성 | `git diff 9a5aa26a --name-status -- <되돌린 경로들>` **빈 출력** — 그 경로에서 base 와 같은 트리다 |

## ④⑤⑥ 의 자리

이 HEAD 에서 재실측하지 않았다. 검토 라운드의 변경은 **test 분류 함수·주석과 runbook 문면**뿐이고
출하 바이트는 한 비트도 바뀌지 않았다(배선·설정·정책 무변경). 호스트 규율로 전건 `check` 를 다시
돌리지 않았으므로 **앞 라운드의 ④⑤⑥ 을 이 HEAD 의 값으로 옮겨 적지 않는다**. 가진 것은 둘이다 —
`bf8bbac3` 의 되돌린 트리에서 `check` **exit 0**(`:app:compileTestKotlin` · `:app:test` 전 모듈 2,601 ·
`app` 459 · `leakPatternGate` · `app:sizeGate` · `app:gateExecutionGate` 전부 수행·성공, **그 HEAD 의
측정이다**), 그리고 `a033e80d` 의 **되돌리지 않은** 트리에서 `:app:test --tests
OpeningPageSizeE2ETest` exit 0(3 test) · `:app:ktlintTestSourceSetCheck` · `:app:sizeGate` exit 0.

**⑥ 이 초록인 조건**은 되돌리지 않는 evidence 디렉터리의 누출 어휘 매치가 0 이라는 것이다
(`commands.md`). 0 이 아니면 base 의 허용 목록에 그 매치가 없어 되돌린 트리에서 게이트가 붉는다
(M4 선례). 이 slice 는 그 허용 목록을 만지지 않았다.

## 보존 확인 — 두 축을 둘 다

| 축 | 실측 |
|---|---|
| 내 줄이 사라졌다 | 되돌린 runbook 에 `rows-per-page` **0건** · 장부에 `OpeningPageSizeE2ETest` **0건** |
| 남의 줄이 남았다 | 되돌린 runbook 에 6G-2e 가 세운 `OPEN-6G-BACKTEST-CLI` 행 **1건** |

뒤 축을 안 재면 「공유 파일을 통째로 되돌려 남의 줄까지 걷었다」가 보이지 않는다.

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 마지막 산출물 커밋 뒤에 오므로 둘은
영원히 다르다). 그 사이에 **되돌림 대상이 움직였는가**를 본다 —
`git diff --name-only a033e80d..<판정 SHA> -- <위 목록의 경로들 개별 인자>` 가 빈 출력이면 유효하고,
한 줄이라도 나오면(또는 실측 HEAD 가 판정 SHA 의 조상이 아니면) 미검증이다.

## 되돌리지 않는 것

하네스 경로(`CLAUDE.md` · `.claude/**`)와 `milestone-6.md`. 이 range 의 하네스 레인 커밋은 **없다**
(`git log --oneline 9a5aa26a..a033e80d -- CLAUDE.md .claude/` 빈 출력 — scope.md 「하네스 레인 변경」과
같은 값). `milestone-6.md` 의 확정 제외 문턱 문면(cr L-4)도 그 몫이다.
