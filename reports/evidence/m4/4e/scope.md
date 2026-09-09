# Slice 계약 — M4 / 4E · notification adapter contract

> **지위**: **착수 계약 2026-09-09.** 초안(2026-09-07, M2 진행 중 작성)을 아래 계약 고정 결정(D-4E-1~8)으로 고정하고 base 를 재고정했다.
> 4C-1 의 `IdempotencyKey`·4A 의 `OperatorId` 를 **소비**하고, 「배달 요청 → 배달 결과」 어댑터 계약과 배달 경로 판정 커널을 세운다.
>
> **레인 격리 — 병렬 lane.** 다른 세션이 worktree `/Users/harris/Development/private/bid-vector-v2-m4`(브랜치 `m4/2026-09-08`)에서
> 4A(verifier r3 ready-for-review)·4B-1(수정 라운드 1 뒤 재검증 대기)·4C-1(verifier r2 ready-for-review, 승인 대기)을 진행 중이다. 이 slice 는
> 그 브랜치의 HEAD(`f600909`)에서 가른 **별도 worktree + 브랜치**에서 산다 — worktree `/Users/harris/Development/private/bid-vector-v2-m4e`,
> 브랜치 `m4-4e/2026-09-09`. 같은 working tree 에 두 세션이 살면 커밋 혼입이 구조적으로 난다(2026-09-02·09-08 규율의 사유).
> **`m4/2026-09-08` 로의 병합은 slice 종결 뒤 사용자 승인 사항**이다(agent-workflow.md 1절). 병합 시 겹치는 파일은 둘뿐이다 —
> `config/quality/gate-tests.properties`(`gate.tests.workflow` 블록에 줄 추가)와 `milestone-4.md`(종결 문단). 소스 경로는 겹치지 않는다
> (`git log --name-only 9948c6e..f600909` 실측 — 진행 중 slice 셋은 `workflow/strategy`·`workflow/event`·`decision`·`fixtures`·`app/conformance` 만 만진다).
>
> **4D 를 고르지 않은 이유**: 4D(ML gateway)는 M2 완료(2026-09-07)로 선행이 풀렸으나 D-M4-6 의 `Verdict.Review(MlUnavailable)` 자리가
> `decision` 모듈 — 4B-1 이 지금 만지는 파일 — 이라 병렬이 아니다. 4D 는 4B-1 종결 뒤.

```yaml
milestone: m4
slice: 4e-notification-adapter-contract
base_sha: f600909fcde1c08bb83abaf749cfa681539831a0   # m4/2026-09-08 의 HEAD(4B-1 M-1 재실측 커밋). 이 slice 브랜치의 분기점
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3).
branch: m4-4e/2026-09-09   # worktree /Users/harris/Development/private/bid-vector-v2-m4e
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/notification/**   # port(NotificationSender·ContentRenderer·RouteDirectory)·값 타입(RouteKey·MaskedTarget·DeliveryRequest·RenderedContent)·배달 경로 판정 커널(DeliveryPlan)·결과 enum(DeliveryResult)·dispatch use case·정책 데이터 슬롯
  - workflow/src/test/kotlin/bidvector/workflow/notification/**   # 전수 표(정책×환경)·shape property·fake sender(계약 증명)·dry-run 호출 0·같은 키 효과 1·Unknown 무재시도·owner isolation·allow-list 소스 스캔
  - config/quality/leak-patterns.txt                               # S-3 스캔 패턴 정책 파일(신설 — 패턴을 계약 문서 밖에 둔다). 이름에 패턴 단어를 넣지 않는다(commands.md 가 이 경로를 인용해도 S-3c 자기매치가 없게)
  - config/quality/gate-tests.properties                           # `gate.tests.workflow` 에 notification test 등재만(다른 키 무편집)
  - milestone-4.md                                                 # 4E 종결 문단만(종결 승인 시점, 팀장)
  - reports/evidence/m4/4e/**
out_of_scope:
  - adapters/**                                                    # D-4E-5 — 이 slice 는 adapters 를 편집하지 않는다(실 sender 없음, dry-run 은 커널 판정)
  - fixtures/** · app/src/test/kotlin/bidvector/app/conformance/**  # D-4E-6 — corpus 신설 없음(전수 표는 test 가 갖는다). 병렬 lane 의 공유 파일
  - workflow/src/main/kotlin/bidvector/workflow/strategy/** · workflow/src/main/kotlin/bidvector/workflow/event/**   # 4A·4C-1 산출물 — 소비만, 편집 0
  - 실제 Telegram/email 발송·webhook 인증(NOTI-07)·라이브 메일(`OPEN-NOTI-04`)   # 사용자 승인 사항·후속
  - outbox 등록·클레임·상태(4C-1 `OutboxPort`)                      # 4E 는 outbox 가 부르는 sender 계약까지. `Unknown` 의 격리 판단은 4C(D-4E-2)
  - 피로도 게이트(NOTI-02 `후속`)·채널 fallback(NOTI-11)·앱 알림함 UI(6A)
  - 알림 **내용**의 업무 판정(무엇을 알릴지 — 4B `NotificationRequested`) · 가치 게이트 임계 4종(NOTI-01, 4B 소유 — D-4E-8)
  - 앱 알림함 **기록**의 생성(NOTI-01 「억제되어도 기록 1건」)   # 4B 소유 — 4E 의 `Suppressed` 는 채널 억제일 뿐 기록을 만들지도 막지도 않는다
  - 렌더러 **구현**(port 만) · 실행 환경 값을 읽는 배선(`ENVIRONMENT` → `RuntimeEnvironment`, app/M6) · 채널 설정 저장(RouteDirectory 구현, 3D/후속)
  - M2 경로·capability-map·data-dictionary 편집
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.notification.*'"                # S-2
  - "grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/notification workflow/src/test/kotlin/bidvector/workflow/notification; test $? -eq 1"   # S-3 — 비밀값·raw 식별자 단어 스캔. 패턴은 파일 밖(정책 파일). exit 1 = 매치 0 = 통과, exit 2(경로 없음)는 실패. 대상은 notification 패키지 둘 — 4A·4C-1 소스는 이 slice 의 산출물이 아니다
  - "d=$(mktemp -d) && printf 'val t = \"Bearer abc\"\\n' > \"$d/Leak.kt\" && grep -rniE -f config/quality/leak-patterns.txt \"$d\"; test $? -eq 0"   # S-3b — 양성 대조: 심은 표본이 매치돼 exit 0
  - "grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m4/4e/ --exclude=scope.md; test $? -eq 1"   # S-3c — evidence 스캔(evidence-pack 규격). `scope.md` 는 S-3b 표본 문자열을 담는 계약 문서라 영구 제외하고 **사람 리뷰 대상**(checklist 육안 항목)
  - "./gradlew --no-daemon :workflow:gateExecutionGate"                                               # S-4 — 등재한 notification test 가 실제로 돌았고 실패·건너뜀 0
  - "./gradlew --no-daemon :workflow:test --tests '*NotificationBoundaryTest*'"                       # S-5 — allow-list 소스 스캔 + 양성 대조(4A `EditSessionImportBoundaryTest` 술어 재사용)
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m4/4e/rollback.md`**. notification 패키지 둘(main·test)과 `leak-patterns.txt` 를 걷고
    `gate-tests.properties` 의 4E 몫 줄만 **줄 단위**로 걷으면 base(`f600909`) 상태. 브랜치 자체가 격리돼 있어 병합 전에는
    「브랜치를 버린다」가 곧 rollback 이다(병합 뒤에는 위 경로 한정 restore).
```

작성: 2026-09-09, 세션 모델 단독(운영자 지시 2026-09-04). 근거: `milestone-4.md` 4E · `capability-map.md` NOTI-01·NOTI-03·NOTI-05·NOTI-10·OPS-03 ·
`ADR 0005` D-3·D-4·D-9·D-11 · `OPEN-NOTI-01/02(해소)/04/05/06/07/08` · `v2-지침서.md` §4.5·§8 · `prep/m4-prep.md` §2·D-M4-8 · 조사 노트
`_workspace/m4-prep/01_scout_workflow.md` (c) · legacy 실물 `bid-vector/app/services/notifications/telegram_delivery_plan.py`·`manager.py`(읽기 전용).

---

## 하네스 레인 변경 (상시 절)

`git log --oneline f600909fcde1c08bb83abaf749cfa681539831a0..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**, 종결 승인(2026-09-09) 시점 재실행도
**없음**(0건, 이 slice 종결까지 재현). 이 브랜치는 격리돼 있어 하네스 레인 커밋이 섞일 자리가 없다. `m4/2026-09-08` 쪽 하네스 커밋은 병합 때
들어오며 이 slice 산출물이 아니다.

---

## 운영자 결정 — 착수 가정 (D-M4-8, 확정은 종결 승인 시)

**확정 2026-09-09 (a).** 사용자 승인으로 아래 착수 가정이 그대로 D-M4-8 의 확정 결정이 됐다(`reports/evidence/m4/4e/checklist.md`
「사용자 승인」 절 ②). (b) 로의 뒤집힘은 없었다.

`prep/m4-prep.md` D-M4-8 은 「운영자 즉답 대상」이나 이 lane 은 자율 진행이라 **추천안 (a) 를 착수 가정으로 둔다**: 메일 라이브 송신(`OPEN-NOTI-04`)·
채널 fallback(`OPEN-NOTI-07`)은 4E 밖(`후속`), 읽음 상태 되돌림(`OPEN-NOTI-05`)은 「안 한다」, 통지 이후 판정 확정의 재통지(`OPEN-NOTI-08`)는
「기존 메시지 갱신 없이 새 항목」. 넷 다 **코드에 값이 아니라 경계로만** 나타나므로(무엇을 만들지 않는가) (b) 로 바뀌어도 이 slice 의 산출물이
무효가 되지 않는다 — 추가 slice 가 생길 뿐이다. 종결 승인 시 운영자가 확정하거나 뒤집는다.

---

## 병합 결정 (사용자 승인 2026-09-09 ⑤)

`m4/2026-09-08` 로의 병합은 지금 하지 않는다 — 4B-1·4C-1 종결 뒤 한 번에 병합한다(위 「레인 격리」 절이 예고한 절차 그대로,
`reports/evidence/m4/4e/checklist.md` 「사용자 승인」 절 ⑤).

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **delivery request 와 rendered content 의 분리** — `DeliveryRequest(idempotencyKey, owner, channel, route: RouteKey, contentRef)` ↔ `RenderedContent(channel, body)`. 렌더는 `ContentRenderer` port(표현 계층), sender 는 요청과 렌더 결과를 **둘 다** 받되 요청 쪽엔 사람이 읽는 문장이 없다(요청의 필드는 전부 값 타입 — 자유 `String` 필드 0) | 4E 「delivery request 와 rendered content 분리」 · 조사 (c-2) 반례(legacy outbox 가 렌더된 문자열을 저장) |
| ② | **배달 경로 판정 커널** — `resolveDeliveryPlan(policy: ChannelPolicyFacts, environment: EnvironmentFacts): DeliveryPlan`. `DeliveryPlan` 은 **정책 판정과 환경 판정을 다른 필드**로 갖고 결과(`Send | Suppressed(reason)`)는 그 둘에서 **계산**된다(생성자 `internal`, 결과를 손으로 못 만든다). `SuppressionReason = sealed { ChannelDisabled, TargetMissing, EnvironmentBlocked(environment), DryRun }` — **첫 위반이 이긴다**: 정책(비활성 → route 없음) 다음 환경(차단 → dry-run). 비활성+dry-run 은 비활성. 미배달 환경은 문자열 스니핑이 아니라 `RuntimeEnvironment`(enum) → `DeliveryMode` **전사상(policy data, 전 값 매핑 강제)** 이고 채널 무관(알림·메일 같은 사상) | NOTI-03 acceptance 셋 · 분류 근거(첫 위반 승·정책/환경 분리·선언 집합) · 조사 (c-1) 재사용 1순위(legacy resolver 의 순서·분리를 이식, 어휘는 sealed 로 재선언) |
| ③ | **masking·owner isolation** — (i) 요청이 나르는 target 은 `RouteKey` 하나이고 **shape 로 닫힌다**(`^[a-z][a-z0-9_-]{0,80}$`; 숫자만·`:`·대문자·`@` 를 담는 값 — 채팅 id·봇 비밀값·메일 주소 — 은 생성 자체가 거부) — 원문 식별자는 어댑터 설정 안에만 산다(`RouteDirectory` 구현, 4E 밖). (ii) 결과가 나르는 target 표시는 `MaskedTarget` 뿐 — `internal constructor` + `MaskedTarget.mask(raw, policy)` factory 가 **끝 n 자만 남기고** 원문을 버린다(원문 accessor 없음, `toString` 도 마스킹 값). (iii) owner isolation 은 「다른 owner 의 route 로 보내는 경로가 API 에 없다」 — dispatch 의 유일한 route 획득 경로가 `RouteDirectory.routesFor(request.owner)` 이고 `DeliveryRequest` 생성자는 `internal` | 4E 「masking 과 owner isolation」 · NOTI-03 「target 라벨은 신뢰할 수 없는 값」·「원문이 어디에도 노출되지 않는다」 · §8 안전 규칙 · 조사 (c-5) |
| ④ | **동일 idempotency key 의 단일 delivery effect** — `NotificationSender.send(request, content): DeliveryResult` 계약: 같은 `idempotencyKey` 의 재호출은 **효과 0**(앞 결과를 돌려준다). `FakeNotificationSender`(test)가 호출 횟수·효과 횟수를 **분리 계수**해 계약을 증명하고, 실패·`Unknown` 주입 슬롯을 갖는다. at-most-once: dispatch 는 `Unknown` 을 **재시도하지 않고**(호출 횟수 1) 그대로 위로 올린다 — 격리(`Isolated`)는 4C 소유 | 4E 「동일 idempotency key 의 단일 delivery effect」 · `OPEN-NOTI-02` 해소 · ADR 0005 D-11 「채널 배달 — 하지 않는다」 · NOTI-05 「자동 재발송하지 않고 격리」 |
| ⑤ | **결과 enum** — `DeliveryResult = sealed { Delivered(at, target: MaskedTarget), Rejected(reason: RejectionReason), Unknown(observedAt) }`, `RejectionReason` 은 sealed 열거(자유 문자열 필드 0). **미전달을 완료로 마킹하는 경로가 없다** — `Delivered` 는 sender 만 만들고 dispatch 는 `Suppressed` 를 `Delivered` 로 바꿀 방법이 없다(`DeliveryOutcome = Suppressed(plan) | Attempted(result)` 로 위로 올린다) | `OPEN-NOTI-01` 구조 종결 후보 · NOTI-03 「채널 차단 사유가 열거된 status」 · 조사 (c) NOTI-04 결함 후보(`sent: false` 정상 반환) |
| ⑥ | **dry-run 은 커널 판정** — `DeliveryMode.DryRun` 이면 ② 가 `Suppressed(DryRun)` 을 내고 dispatch 는 **sender port 를 호출하지 않는다**(호출 시 throw 하는 sender 로 test). 실 sender 는 없다. 「fake sender」 는 test 의 `FakeNotificationSender`(④) | 4E 「dry-run/fake sender」 · M4 완료 조건 「dry-run 에서 실제 Telegram/email 호출 0」 · `milestone-4.md` 범위 밖 「실제 Telegram/email」 |
| ⑦ | **정책 데이터 슬롯** — `NotificationDeliveryPolicyData(environmentModes: Map<RuntimeEnvironment, DeliveryMode>, maskedSuffixLength)` 를 `EffectiveDatedPolicy` 로(4A `EDIT_SESSION_POLICY` 관례). 생성 불변식: 매핑이 **enum 전 값을 덮는다**(빠진 환경 = 생성 실패, 「모르는 환경은 보낸다」 경로 없음) · suffix ≥ 1. **값은 착수 시 placeholder**(`policy-values.md` 에 추천값 등재, 승인은 종결 시) | NOTI-01 「임계값이 코드 상수에 있는 형태는 채택하지 않는다」(같은 규율) · `OPEN-NOTI-06` 과 같은 「값은 관측 뒤」 |

**만들지 않는 것**: 실 발송 · outbox 상태 기계 · 피로도 게이트 · fallback · 앱 알림함 · **재시도(어느 계층에도)** · 사람이 읽는 문장(렌더러는 port) ·
채널별 `dry_run_only` 플래그(legacy c-6 — 환경 모드가 그 목적을 덮는다, 설계 검토 (3)) · provider 오류 문자열의 결과 탑재(설계 검토 (3)) ·
corpus(D-4E-6) · adapters 편집(D-4E-5).

---

## 계약 고정 결정 (D-4E-1~8)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4E-1** | 4E 의 port 는 `workflow` 소유(application), 구현은 `adapters`(이 slice 밖) — ADR 0005 D-9·D-10 배치와 동일 | ADR 0006 D-3 | 계약 고정 |
| **D-4E-2** | `Unknown` 결과의 소유는 4C(`Isolated`) — 4E 는 결과를 정직하게 낼 뿐 재시도·격리 판단을 하지 않는다 | ADR 0005 D-11 「한 부작용에 재시도 계층은 하나」 | 계약 고정 |
| **D-4E-3** | 통지 이후 판정 확정(`OPEN-NOTI-08`)은 **새 `DeliveryRequest`**(새 idempotency key, 기존 메시지 갱신 없음) — 채널 측 편집 API 의존을 만들지 않는다. 앱 알림함(NOTI-10)이 이력 | D-M4-8 (a) 착수 가정 | 계약 고정 |
| **D-4E-4** | 읽음 상태 되돌림(`OPEN-NOTI-05`)은 4E 밖(앱 알림함 소유, 6A) — 어댑터 계약에 읽음 개념 없음 | D-M4-8 (a) 착수 가정 | 계약 고정 |
| **D-4E-5** | **adapters 편집 0.** 초안의 `DryRunSender` 는 「보내지 않고 `Delivered` 를 내는 sender」가 되거나(⑤ 위반) 「환경 사실을 내는 것」이 되는데 후자는 sender 가 아니라 ② 의 입력이다. dry-run 은 커널 판정(⑥)으로 두고 sender 구현은 실 채널 slice(후속, 사용자 승인 사항)에 맡긴다. 부수 효과로 병렬 lane 의 `adapters/**`(main 의 M3 후속이 만진 경로)와 겹칠 자리가 사라진다 | ⑤·⑥ 정합 · 병렬 lane 충돌 0 | 계약 고정 |
| **D-4E-6** | **corpus 신설 0.** ② 의 전수 표(정책 3 × 환경 3 = 9행)는 `DeliveryPlanTableTest` 가 갖는다. 이유 둘 — (i) `fixtures/manifest.yaml`·`app/conformance/**` 는 4A·4B-1 이 지금 만지는 공유 파일이라 이 lane 이 만지면 병합 충돌이 확실하다 (ii) 4B-1 verifier H-1 이 세운 잣대(승인 문서에 없는 식별자를 `verified_paths` 로 잠그면 `authoritative` 가 아니다)에 ② 의 sealed 이름(`ChannelDisabled` 등)이 걸린다 — NOTI-03 이 승인한 것은 네 **범주**(설정 없음/채널 꺼짐/dry-run/환경 차단)이지 식별자가 아니다. **corpus 승격은 `m4` 병합 뒤 별도 curator 작업으로 `OPEN-4E-CORPUS` 등재**(운영자가 원하면) | 병렬 lane · 4B-1 H-1 | 계약 고정 — 종결 시 운영자 재확인 |
| **D-4E-7** | 식별자 재사용 — `IdempotencyKey` 는 4C-1 `bidvector.workflow.event` 의 것, `OperatorId` 는 4A `bidvector.workflow.strategy` 의 것을 **그대로 import** 한다(같은 모듈, 파일 편집 0). 4C-1 이 승인 대기 중이라 이름이 바뀌면 병합 때 따라간다 | 어휘 중복 금지 | 계약 고정 |
| **D-4E-8** | NOTI-01 가치 게이트 임계 4종은 4E 정책 슬롯이 **아니다** — 「보낼 것인가」는 게이트(4B `NotificationRequested` 생성 자리)의 정책이고 4E 는 「어디로·보낼 수 있는가」만 판정한다. 초안 ⑦ 의 그 항목을 뺀다 | m4-prep §2 4B 행(앱 알림함 기록·채널 배달 요청은 게이트 통과 시에만 outbox 로) | 계약 고정 |

---

## 위협 모델 — 4E 고유 경계

**방어한다**: (a) raw 식별자·비밀값의 요청·결과·evidence 유출(③ shape + `MaskedTarget` + S-3 스캔) (b) 정책/환경 사유의 합침(② 필드 둘 + 전수 표)
(c) 미전달의 완료 마킹(⑤ — `Delivered` 는 sender 만, dispatch 는 `Suppressed` 를 바꾸지 못함) (d) 같은 키의 이중 효과(④ fake 분리 계수) (e) 재시도의
유입(④ `Unknown` 호출 횟수 1 · D-4E-2) (f) 문장(업무 사유)의 요청 유입(① 자유 `String` 필드 0) (g) dry-run 에서 sender 호출(⑥ throw sender)
(h) 다른 owner 의 route 사용(③ (iii)) (i) 채널 라이브러리 타입의 계약 유입(S-5 allow-list).
**방어하지 않는다**: 실 채널의 거동(실 sender 없음) · 렌더 내용의 옳음 · outbox 원자성·격리(4C) · 가치 게이트 값(4B) · 환경 값을 **읽는** 배선의 옳음
(app 이 `RuntimeEnvironment` 를 잘못 만들면 커널은 그 사실을 믿는다 — legacy 와 같은 경계, c-1 「호출부가 계산한 사실을 받는다」) · `RouteDirectory`
구현이 다른 owner 의 route 를 돌려주는 결함(어댑터 결함, 4E 밖) · 사용자 승인 없는 실 발송(agent-workflow §1 — 코드에 실 sender 가 없다) ·
빌드 스크립트를 임의로 쓰는 저자(2026-09-03 운영자 채택 경계).

**승인 문면과의 대조 — 경계가 요구 축소가 아님**: `milestone-4.md` 4E 는 넷(분리 · dry-run/fake · masking·owner isolation · 단일 effect)을 요구하고
(a)~(i) 가 넷을 덮는다. outbox 원자성·격리는 같은 문서가 4C 에, 가치 게이트는 4B 에 배정한 문면이다. M4 완료 조건 가운데 4E 몫은 「dry-run 에서 실제
Telegram/email 호출 0」 하나이고 (g) 가 그것이다.

**우회 후보(≥5)**: (1) `MaskedTarget(raw)` 생성자 공개 → `internal` + `mask` factory 만 (2) sender 가 실패 뒤에도 `Delivered` → 계약 KDoc + fake 의
실패 주입 test(실 어댑터의 계약 준수는 그 slice 의 verifier 몫, 알려진 제한) (3) `Unknown` 을 dispatch 안에서 재시도 → 호출 횟수 계수 test (4) 사유를
문자열 필드로 → sealed 만, 자유 `String` 0 (5) dry-run 이 sender 를 감쌈 → `Suppressed` 경로는 sender port 에 닿지 않는다(throw sender) (6) 정책 매핑에서
환경 하나를 빠뜨려 「모르는 환경은 Live」 → 전 값 덮음 생성 불변식 (7) raw 채팅 id 를 `RouteKey` 로 밀어 넣기 → shape `init` 거부(property test)
(8) `DeliveryPlan` 의 결과를 필드와 어긋나게 손으로 조립 → `internal constructor` + 계산된 결과 (9) Telegram/mail 타입을 FQN 으로 참조 → S-5.

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- 조사 (c-1) **재사용 1순위** — legacy `telegram_delivery_plan.py` 의 순수 resolver: 정책(`route_send_allowed`)/환경(`can_send`) 분리 · **첫 위반 승**
  (비활성 → dry-run → 비 canonical → 미해결 route) · 판정은 값 테이블로 검증. ② 는 그 **구조**를 이식한다. 어휘 11종은 옮기지 않는다 — canonical/
  synthetic operator 구분은 단일 회사 V2 에 없고(§1.2), legacy-settings fallback(채널 행 없는 canonical 운영자에게 레거시 chat 으로 배달)은 「설정 없음 =
  보내지 않는다」로 재설계(`TargetMissing`). 이 둘은 `intentional-redesign` 이고 근거는 이 절이다(differential.json 은 case 가 없어 N/A).
- 조사 (c-2) **반례** — legacy outbox 행이 렌더된 문자열을 저장 → ① 의 근거. (c-3) 직접 경로에 멱등 키 없음 → ④. (c-5) 마스킹이 정규식 넷의 「관행」이라
  타입 경계 강제가 아니고 5~19자 영숫자는 안 잡힌다 → ③ 은 fragment 인식이 아니라 **끝 n 자 외 전부** 로 단순화(모양을 몰라도 샌다는 경로가 없다).
  (c-6) `NON_DELIVERING_ENVIRONMENTS = {"test"}` 하나뿐 → ⑦ 의 전사상 매핑(dev/staging 을 dry-run 으로 두는 추천값은 `policy-values.md`).
- 라이브러리 조사: 마스킹·멱등 키·경로 판정에 외부 라이브러리 후보 없음(값 타입·순수 함수 수준). `workflow/build.gradle.kts` 무편집.
- 기존 V2 코드와의 중복: 4C-1 `IdempotencyKey`·4A `OperatorId` 재사용(D-4E-7). `adapters/koneps/KonepsIdentifierMasking.kt`(main, M3/3B-2)는 KONEPS 식별자
  allow-list 마스킹이라 목적이 다르고 이 브랜치(`f600909`)에는 없다 — 재사용 대상 아님.

---

## OPEN — 수령·신설

| OPEN | 4E 처리 |
| --- | --- |
| `OPEN-NOTI-01` | ⑤ 구조로 닫는 후보(의도/버그 판정 불요) — 종결 시 capability-map 갱신은 4E 밖(병합 뒤 일괄) |
| `OPEN-NOTI-02`(해소) | ④ at-most-once 소비 |
| `OPEN-NOTI-04`·`OPEN-NOTI-07` | 범위 밖(`후속`) — D-M4-8 (a) 착수 가정 |
| `OPEN-NOTI-05` | D-4E-4 — 4E 밖 |
| `OPEN-NOTI-06` | 4B 소유로 이관(D-4E-8) |
| `OPEN-NOTI-08` | D-4E-3 — 새 요청 |
| **`OPEN-4E-CORPUS`** | ② 전수 표의 corpus 승격 — **재확인 2026-09-09**(사용자 승인 ④), 여전히 열림. `m4` 병합 뒤 curator, 운영자가 원할 때(D-4E-6) |
| ~~`OPEN-4E-POLICY-VALUES`~~(**종결 2026-09-09**) | ⑦ 의 값(환경 → 모드 매핑, suffix 길이) — 사용자 승인 2026-09-09 로 확정(`policy-values.md` §1·§2, 값은 착수 placeholder에서 무변경) |
