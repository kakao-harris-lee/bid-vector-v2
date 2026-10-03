# M6/6G-2f — rollback

> 되돌림은 range revert 가 아니라 **in_scope 경로 한정**이다. 목록은 손으로 쓰지 않고
> `git diff --name-status <base>..<실측 HEAD>` 에서 기계로 낸다. **라운드마다 그 라운드의 마지막
> 산출물 커밋에서 다시 낸다** — 앞 라운드 실측을 옮기지 않는다.

**실측 HEAD: `12d89ab1`**(`/code-review` 조치 라운드 뒤 마지막 산출물 커밋) · base **`9a5aa26a`**.
①②③ 과 보존 확인은 그 HEAD 의 **버릴 clone** 에서 돌린 결과다. ④⑤⑥ 은 그 HEAD 에서 재실측하지
않았다 — 사유와 대신 돌린 것은 「④⑤⑥ 의 자리」에 있다.

## 되돌림이 싼 경로는 「거부를 만나기 전」에만 있다

실행 중이라면 다음 실행에 `--bidvector.koneps.opening.rows-per-page=100` 인자 하나로 이 slice 전의 쪽
크기가 된다(재빌드·jar 교체 없이). 확정 표본은 쪽 크기와 무관하므로 그 인자가 `sample-list.tsv` 를
건드리지 않는다. **다만 그 인자는 이미 정착한 것을 복구하지 않는다** — 게이트웨이가 999 를 거부하면
분류된 코드(`INPUT_ERROR`·`NOT_RETRYABLE`)로 와서 그 축이 `FinalFailure` 로 **영구 정착**하고 재시도도
재걷기도 없다. 그래서 값싼 방어는 되돌림이 아니라 runbook §7 의 사전 확인(0항)과 첫 기동 노출
상한(2항)이다. 인자를 쓴 날과 값은 evidence 에 적는다(checklist 알려진 제한 9). 아래는 **산출물 자체를
걷는** 경로다.

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
| M | `config/quality/gate-tests.properties` | ② 같음 |
| M | `milestone-6.md` | ② 같음 — **in_scope 밖 · 팀장 레인 커밋**이지만 이 slice 의 문단이라 절차에 둔다 |

`reports/evidence/m6/6g2f/**` 는 **되돌리지 않는다**(이 slice 의 기록이고 `scope.md` 는 착수·계약 갱신
커밋의 것이다). 그 선택의 게이트 영향은 ⑥ 에 있다.

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

## ② 공유 파일 셋 — hunk 격리, 나중 커밋부터

먼저 그 파일을 만진 커밋을 나열한다(라운드마다 다시): `git log --oneline 9a5aa26a..12d89ab1 -- <파일>`.
실측은 runbook **셋** · 장부 **둘** · `milestone-6.md` **하나**다.

```
git diff 12d89ab1~1..12d89ab1 -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff a033e80d~1..a033e80d -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff 6150f9ef~1..6150f9ef -- docs/runbook/m6-6g-real-collection.md | git apply -R
git diff 98261a05~1..98261a05 -- config/quality/gate-tests.properties | git apply -R
git diff bf8bbac3~1..bf8bbac3 -- config/quality/gate-tests.properties | git apply -R
git diff 79525a77~1..79525a77 -- milestone-6.md | git apply -R
```

여섯 전부 **exit 0** · conflict 0. **`milestone-6.md` 에는 팀장 커밋이 더 붙는다**(종결 문단 갱신) —
그 SHA 는 종결 시 채우고, 채운 뒤에도 **나중 것부터** 역적용한다. **목록이 하나라도 늘면 이 절차를 다시
돌린다**: 이 slice 에서 runbook 은 라운드마다 한 커밋씩 늘어 셋이 됐다. 삽입 지점이 인접하면 `--3way`
도 자동 해소에 실패하므로(M4·M6 선례) 그때는 내 몫만 문면으로 되돌리는 수동 절차가 정본이다 — runbook
에서 내 몫은 §0 표의 `OPEN-6G2D-MAX-PAGES-FINAL` 행 · §1 4단계의 `<jar SHA>` 문장 · §2-2 의 불릿 셋 ·
§5 의 `MAX_PAGES` 줄과 쿼터 줄 · §7 절 전체, 장부에서는 `gate.tests.app` 의 이름 둘과 머리 주석 세 줄,
`milestone-6.md` 에서는 6G-2f 착수·종결 문단과 `OPEN-6G2D-MAX-PAGES-FINAL` 문면이다.

## ③ 실측 (버릴 clone, HEAD `12d89ab1`)

| 단계 | 결과 |
|---|---|
| ③ 명령 exit · D/M | `restore` 0 · `apply -R` 여섯 0. 되돌린 뒤 `git status --porcelain` 이 **D 2 · M 8** |
| ③ 트리 동일성 | `git diff 9a5aa26a --name-status -- <되돌린 경로들>` **빈 출력** — 그 경로에서 base 와 같은 트리다 |

## ④⑤⑥ 의 자리

이 HEAD 에서 재실측하지 않았다. 조치 라운드의 변경은 **test 코드·주석과 문서**뿐이고 출하 바이트는
바뀌지 않았다(배선·설정 기본값·정책 무변경). 호스트 규율로 전건 `check` 를 다시 돌리지 않았으므로
**앞 라운드의 ④⑤⑥ 을 이 HEAD 의 값으로 옮겨 적지 않는다**. 가진 것은 둘이다 — `bf8bbac3` 의 되돌린
트리에서 `check` **exit 0**(`:app:compileTestKotlin` · `:app:test` 전 모듈 2,601 · `app` 459 ·
`leakPatternGate` · `app:sizeGate` · `app:gateExecutionGate` 전부 수행·성공, **그 HEAD 의 측정이다**),
그리고 `12d89ab1` 의 **되돌리지 않은** 트리에서 하네스 공유 셋 `:app:test --tests
'*OpeningPageSizeE2ETest' --tests '*OpeningCollectionE2ETest' --tests '*OpeningBudgetE2ETest'`
exit 0(3 + 4 + 11 = 18 test).

**⑥ 이 초록인 조건**은 되돌리지 않는 evidence 디렉터리의 누출 어휘 매치가 0 이라는 것이다
(`commands.md`). 0 이 아니면 base 의 허용 목록에 그 매치가 없어 되돌린 트리에서 게이트가 붉는다
(M4 선례). 이 slice 는 그 허용 목록을 만지지 않았다.

## 보존 확인 — 두 축을 둘 다

| 축 | 실측 |
|---|---|
| 내 줄이 사라졌다 | 되돌린 runbook 에 `rows-per-page` **0건** · 장부에 `OpeningPageSizeE2ETest` **0건** · `milestone-6.md` 에 6G-2f 문단 **0건** |
| 남의 줄이 남았다 | 되돌린 runbook 에 6G-2e 가 세운 `OPEN-6G-BACKTEST-CLI` 행 **1건** · `milestone-6.md` 에 6G-2e 종결 문단 **1건** |

뒤 축을 안 재면 「공유 파일을 통째로 되돌려 남의 줄까지 걷었다」가 보이지 않는다.

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 마지막 산출물 커밋 뒤에 오므로 둘은
영원히 다르다). 그 사이에 **되돌림 대상이 움직였는가**를 본다 —
`git diff --name-only 12d89ab1..<판정 SHA> -- <위 목록의 경로들 개별 인자>` 가 빈 출력이면 유효하다.
한 줄이라도 나오거나 **판정 SHA 가 실측 HEAD 의 자손이 아니면**(실측이 판정과 다른 가지에서 났으면)
미검증이다.

## 되돌리지 않는 것

하네스 경로(`CLAUDE.md` · `.claude/**`). 이 range 의 하네스 레인 커밋은 **없다**
(`git log --oneline 9a5aa26a..12d89ab1 -- CLAUDE.md .claude/` 빈 출력 — scope.md 「하네스 레인 변경」과
같은 값).
