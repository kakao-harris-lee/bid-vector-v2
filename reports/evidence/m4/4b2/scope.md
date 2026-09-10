# Slice 계약 — M4 / 4B-2 · application 조합 use case

> **지위**: **착수 계약 2026-09-09.** `milestone-4.md` 4B 의 뒤쪽(4B-1 이 도메인 커널, 4B-2 가 조합).
> M4 완료 조건 일곱 중 **아직 아무도 지지 않은 둘**을 이 slice 가 진다 — 「trace/correlation id 가 수집→판정→ML→알림 요청까지 유지」·
> 「한 application 함수에 수집·DB·ML·알림 구현이 함께 들어가지 않음」.
>
> **입력은 전부 승인·종결된 것들이다**: 4A(편집 상태·`EditSession`) · 4B-1(`Verdict` 사다리) · 4C-1(봉투·outbox port·dedup) · 1C(`LicenseEligibility`) ·
> 1D(provenance·floor) · 1E(`WatchRules`·`OperatorStrategy`) · M3 3A(수집 fact·port). **이 slice 는 그것들을 조합할 뿐 어느 것도 편집하지 않는다.**

```yaml
milestone: m4
slice: 4b2-application-composition
base_sha: 401bc4d5636cd114c8282cf314d837f0d52dbc15
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3).
branch: m4/2026-09-08
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/**   # 조합 use case·평가 결과 어휘(판정에 이른 것/이르지 못한 것)·trace 값·정책 슬롯·port
  - workflow/src/test/**                                        # 후보 단위 격리·실패 주입·trace 관통·중복 계수·전략 스냅샷 property·fake port
  - config/quality/gate-tests.properties                        # `gate.tests.workflow` 확장
  - docs/discovery/capability-map.md                            # `OPEN-4B1-OFF-LADDER-DROPS` 처리 표시만
  - milestone-4.md
  - reports/evidence/m4/4b2/**
  - fixtures/manifest.yaml                                      # 조건부 — 조합 축 case 신설이 필요하면
  - fixtures/input/**  ·  fixtures/expected/**                  # 조건부 — 같은 이유
  - app/src/test/kotlin/bidvector/app/conformance/**            # 조건부 — 위 case 의 실행자
out_of_scope:
  - decision/** · strategy/** · qualification/** · procurement/** · shared-kernel/**   # 승인 종결된 커널. 필요한 타입이 없으면 **멈추고 보고**
  - workflow/src/main/kotlin/bidvector/workflow/strategy/**     # 4A 산출물 — 소비만 한다
  - workflow/src/main/kotlin/bidvector/workflow/event/**        # 4C-1 산출물 — 소비만 한다(`EventSink`·봉투 필드)
  - adapters/** · "**/db/migration/**"                          # 실 저장·스키마는 4C-2/3D. `main` 의 M3 후속 레인 경로이기도 하다
  - 실 ML 호출·gRPC·deadline·breaker                            # 4D. 이 slice 는 **ML 부재 경로만**(`Verdict.Review(MlUnavailable)`)
  - 실 알림 발송·렌더링                                          # 4E. 이 slice 는 **알림 요청을 낳는 자리까지**
  - DB↔outbox 원자성·crash-after-commit                          # 4C-2
  - 감시 run 스케줄·주기 트리거·lease                             # 후속(`OPEN-ADR-12`)
acceptance_commands:
  - "S-0  d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> \"$d/repo\" && (cd \"$d/repo\" && ./gradlew --no-build-cache clean check)"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :workflow:test"
  - "S-3  ./gradlew :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck"
  - "S-3b ./gradlew :workflow:test --tests '*CompositionBoundaryTest*'"
  - "S-4  ./gradlew :app:test"      # 필터 없이 전건 — 필터와 게이트를 **같은 호출**에 넣지 않는다(4C-1 L-7)
  - "S-5  ./gradlew qualityBaseline"
  - "S-6  ./gradlew :app:gateExecutionGate"   # S-4 와 **별도 호출**
rollback: |
    **정본은 `reports/evidence/m4/4b2/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`,
    목록은 `git diff --name-status <base>..HEAD` 로 기계 산출. **공유 파일**(`gate-tests.properties`·`manifest.yaml`·`capability-map.md`·conformance 실행자·`milestone-4.md`)은
    **커밋 해시 hunk 격리**로 자기 몫만 걷는다(evidence-pack 2026-09-09). 확인은 「내 줄 사라짐」과 **「남의 줄 남음」을 둘 다** + 되돌린 트리의 compile·test.
```

작성: 2026-09-09, 세션 모델 단독. 근거: `milestone-4.md` 4B·완료 조건 · `capability-map.md` STR-07·STR-08·NOTI-01·`OPEN-4B1-OFF-LADDER-DROPS` · `ADR 0005` D-2·D-3·D-9 ·
`prep/m4-prep.md` §2 4B 행 · 조사 노트 `_workspace/m4-4b2/01_scout_composition.md`(786행)·`_workspace/m4-4b1/01_scout_verdict_ladder.md`(608행).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 401bc4d5636cd114c8282cf314d837f0d52dbc15..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**(base == HEAD). 리뷰 요청 시점에 갱신한다. rollback 대상 아님.

**갱신(2026-09-10, 종결 시점)**: 같은 range에 하네스 레인 커밋 둘이 붙었다 —
`a70a04f`(`harness(v2-slice-pipeline): (2b) 표를 수정 라운드마다 갱신`) ·
`0034288`(`harness(v2-slice-pipeline): (2b)에 「object 커널 계수」 고정 항목`).
둘 다 오케스트레이터(team-lead)가 붙였고 **slice 산출물이 아니며 in_scope 밖,
rollback 대상 아님**이다.

---

## 이 slice 가 하는 일 — legacy 의 실패 형태를 뒤집는다

조사가 실측한 것이 그대로 요구다. 왼쪽은 전부 **legacy 실물**(좌표는 조사 노트).

| # | legacy 실물(실측) | 4B-2 가 세우는 것 | 승인 문면 |
| --- | --- | --- | --- |
| ① | **판정이 공고 하나에 두 번 돈다**(스캔 시 표시용 1회 · 영속 시 저장용 1회). preview 의 action 과 monitor 의 action 이 갈릴 수 있고, 저장된 `probability_score`(1회차)와 `action`(2회차)이 한 행에 나란히 앉는다 | **공고 하나당 판정 1회.** use case 가 판정을 한 번 내고 그 결과를 표시·저장·이벤트가 **공유**한다. 두 번 도는 경로가 타입에 없다 | 4B 「decision 후보 조립」 · §3.1 |
| ② | **부분 실패가 성공한 후보를 전멸시킨다** — `_handle_monitor_failure` 첫 줄이 `db.rollback()` 이라 성공 후보의 판정·알림이 전부 사라지고 실패 공고 1행만 남는다. 그것도 태그된 예외일 때만이고 **스캔 루프에서 죽으면 한 행도 안 남는다** | **후보 단위 격리.** 한 공고의 실패가 다른 공고의 결과를 지우지 않는다. 실패는 **그 공고의 결과**로 남는다(전역 rollback 아님) | 4B 「transaction 경계와 실패 시 상태를 명시한다」 |
| ③ | **성공처럼 계속하는 자리 열** — 실패 증거 기록의 실패가 **로그 한 줄도 안 남고**(C-2), `_mark_run_failed` 의 `bool` 을 호출부가 버리고(C-3), 모델 로드 실패가 플래그로 굳어 폴백으로 계속되며 그 사실이 **영속 전에 버려진다**(C-5) | **실패는 결과 타입에 남는다.** catch-all 없음. 삼킨 실패를 만들 수 있는 자리를 타입으로 없앤다 — 각 단계 결과가 성공/실패 갈래를 갖고 호출부가 **소진 `when`** 으로 소비한다 | 4B 「catch-all exception 으로 성공처럼 계속하지 않는다」 |
| ④ | **trace 축이 0건** — correlation/causation/aggregate version/trace_id 각 0. 한 실행을 묶는 실물은 `monitor_run_id` FK 넷(셋 nullable)뿐이고 **로그로 사후에 묶을 수단이 없으며 ML 호출 경계를 넘지 않는다** | **`correlationId` 가 use case 진입에서 나서 모든 산출물에 실린다** — 판정 결과·이벤트 봉투(4C-1 필드)·알림 요청·ML 요청까지. 값 타입이라 누락이 컴파일에서 걸린다 | **M4 완료 조건** 「trace/correlation id 가 수집→판정→ML→알림 요청까지 유지」 |
| ⑤ | **run 안에서 전략을 매번 다시 읽는다** — 도중 편집이 앞뒤 공고를 **다른 임계로** 판정하게 하고 run 은 그것을 기록하지 않는다(preview 경로는 정반대로 보수적 스탬프를 명시적으로 잡는다) | **run 시작에 전략을 한 번 읽어 고정**하고 그 `StrategyRevision` 을 결과에 싣는다. 재사용 1순위 — preview 의 보수적 스탬프 규율을 조합 경로로 올린다 | STR-07 · 4A ⑤(낙관적 동시성)와 같은 갈래 |
| ⑥ | **사다리 밖 조용한 드롭 열셋** — `continue`/`break`/쿼리 제외로 사라지고 `Decision` 도 사유도 안 남는다. 감시 필터는 **사유를 만들어 놓고 `reasons=[]` 로 버린다** | **판정에 이르지 못한 것도 결과가 있다.** 각 단계의 탈락이 **관측 가능한 값**으로 남는다(단계·사유). 이미 있는 축은 그 축이 소유한다(`WatchVerdict`·`LicenseVerdict`·`NoticeStatus`) — 새 어휘를 복제하지 않는다 | `OPEN-4B1-OFF-LADDER-DROPS` · STR-04 「선정과 통지의 혼합」 `폐기` |
| ⑦ | **한 함수가 수집·DB·ML·알림을 함께 한다** | **port 뒤로 가른다.** use case 는 조합만 하고 수집·저장·ML·알림은 각각 port. 이 경계를 **소스 스캔 test**(S-3b)가 단언한다 | **M4 완료 조건** 마지막 줄 · ADR 0005 D-9 |
| ⑧ | **용량을 행 수로 센다**(`distinct` 없음) + run 마다 새 레코드 → `planned` 공고가 run N번에 용량 N 을 먹는다 | 용량 계수는 **명시 정책**이고 중복 계수가 불가능한 형태로 센다. 값은 정책 슬롯(운영자 승인) | 4B-1 사다리의 `currentActiveBids` 입력 계약 |

**만들지 않는 것**: 실 저장·스키마 · 실 ML 호출 · 실 알림 발송·렌더링 · 스케줄·lease · 도메인 커널 편집 · 사람이 읽는 문장.

---

## 위협 모델 — 4B-2 고유 경계

**방어한다**: (a) 같은 공고를 두 번 판정해 결과가 갈리는 것 (b) 한 공고의 실패가 다른 공고의 결과를 지우는 것 (c) 삼킨 실패(로그로만 남거나 사라지는 것)
(d) trace 가 중간에 끊기는 것 (e) run 도중 전략 변경이 앞뒤를 다르게 판정하는 것 (f) 탈락이 아무 흔적 없이 사라지는 것 (g) 조합 함수가 수집·저장·ML·알림 구현을 직접 갖는 것 (h) 용량 중복 계수.

**방어하지 않는다**: 실 저장의 원자성(4C-2) · ML 호출의 timeout·breaker(4D) · 실 발송(4E) · 스케줄·lease(후속) · 도메인 커널의 옳음(1C·1D·1E·4B-1 이 각각 종결) ·
**빌드 스크립트를 임의로 쓰는 저자**(2026-09-03 경계).

**우회 후보 — 값 위조 축**: (1) 평가 결과를 use case 밖에서 직조 → 결과 타입 생성자 `internal` + `@ConsistentCopyVisibility`(4A·4B-1 관례) (2) `correlationId` 없이 산출물 생성 → 필수 필드
(3) 실패를 성공 갈래로 변환 → 갈래 사이 변환 함수 부재 (4) 전략을 단계마다 다시 읽음 → 스냅샷을 값으로 받고 port 재호출 경로 없음 (5) 탈락을 `null`·빈 목록으로 접음 → 탈락도 값.

**우회 후보 — 값 획득 축 (하네스 (2b), 「경계로 처리」 행도 실측 대상)**: 이 slice 가 새로 public 으로 내놓는 타입·함수·반환값을 전수하고 각각이 밖에 허락하는 것을 설계 검토가 표로 낸다.
**4A 의 결론이 기본값**(커널은 `internal`, port 만 public) — 다만 corpus 실행자가 필요하면 **use case 경유**로 몬다(4A 선례). **「연다」·「경계로 처리」로 판정한 행도 실측 목록에 넣는다.**

---

## OPEN — 수령·신설

| OPEN | 4B-2 처리 |
| --- | --- |
| `OPEN-4B1-OFF-LADDER-DROPS` | ⑥ 이 수령 — 열셋의 어휘 소유를 정한다(기존 축 재사용 우선, 새 어휘 최소) |
| `OPEN-4C1-TX-CONTRACT-UNVERIFIED` | 유지 — 실 저장이 없어 이 slice 도 강제하지 못한다 |
| `OPEN-ADR-12`(lease) | 범위 밖 — 후속 |
| 조사 신설 `OPEN-4B2-*` 여섯(자격 이중 반영 · run item 유니크 제약 · 용량 잠식 정책 · 다중 레코드 용량 · 실 운영 outbox 설정 · `running` 잔존 빈도) | 착수 시 `capability-map.md` 에 등재 여부를 설계 검토가 판정한다 — **이 slice 가 답할 수 있는 것과 운영 관측이 필요한 것을 가른다** |
