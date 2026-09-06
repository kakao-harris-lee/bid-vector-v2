# Slice 계약 — M4 / 4E · notification adapter contract — **초안, 구현 전**

> **지위**: M2 진행 중 세션 모델이 쓴 초안. 착수는 M1~M3 승인 뒤, `prep/m4-prep.md` D-M4-8 답 수령 뒤. M2 와 독립. 4C(outbox)와는 **계약만** 맞춘다 —
> 4E 는 「배달 요청 → 배달 결과」 어댑터 계약과 fake sender, outbox 등록·클레임은 4C.

```yaml
milestone: m4
slice: 4e-notification-adapter-contract
base_sha: 040ab9d   # 초안 앵커 — **M3 승인 뒤 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/notification/**   # port: `NotificationSender`(delivery request → result)·`ContentRenderer`(request → rendered)·배달 경로 판정(정책 vs 환경)·masking·idempotency 규칙·결과 enum
  - workflow/src/test/**                                            # fake sender·dry-run·masking property·판정 enum 전수
  - adapters/src/main/kotlin/bidvector/adapters/notification/**     # fake/dry-run sender 구현만(실 Telegram/email 은 out) — **2A 가 adapters 편집을 끝낸 뒤**
  - adapters/src/test/kotlin/bidvector/adapters/notification/**
  - config/quality/gate-tests.properties                            # 조건부
  - milestone-4.md, reports/evidence/m4/4e/**
out_of_scope:
  - 실제 Telegram/email 발송·webhook 인증(NOTI-07)·라이브 메일(`OPEN-NOTI-04`)   # 사용자 승인 사항·후속
  - outbox 등록·클레임·상태(4C, `OPEN-OPS-10`)                     # 4E 는 outbox 가 부르는 sender 계약까지
  - 피로도 게이트(NOTI-02 `후속`)·채널 fallback(NOTI-11)·앱 알림함 UI(6A)
  - 알림 **내용**의 업무 판정(무엇을 알릴지 — 4B `NotificationRequested` 가 결정)
  - M2 경로·capability-map·data-dictionary 편집
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew :workflow:test --tests 'bidvector.workflow.notification.*' :adapters:test --tests 'bidvector.adapters.notification.*'"   # S-2
  - "grep -rniE '(api[_-]?key|secret|token|Bearer )' reports/evidence/m4/4e/ workflow/src adapters/src/main/kotlin/bidvector/adapters/notification; test $? -eq 1"   # S-3 — secret·raw 식별자 스캔(evidence 규격)
  - "./gradlew qualityBaseline"                                                                        # S-4
rollback: |
    **정본은 `reports/evidence/m4/4e/rollback.md`**(착수 시). notification 패키지 둘을 걷으면 앵커 상태.
```

작성: 2026-09-07, 세션 모델 단독. 근거: `milestone-4.md` 4E · `capability-map.md` NOTI-01·NOTI-03·NOTI-05·NOTI-10·OPS-03 · `ADR 0005` D-3·D-4·D-11 · `OPEN-NOTI-01/02(해소)/04/05/07/08` · `v2-지침서.md` §4.5·§8.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **delivery request 와 rendered content 의 분리** — `DeliveryRequest(idempotencyKey, channel, target: MaskedTarget, contentRef, policyVersion)` ↔ `RenderedContent(channel, body, attachments?)`. 렌더링은 `ContentRenderer` port(표현 계층), sender 는 렌더 결과만 받는다. 요청은 업무 사유 문장을 갖지 않는다(§3.1 — 문장은 렌더 시점) | 4E 「delivery request 와 rendered content 분리」 |
| ② | **배달 경로 판정 enum** — `DeliveryPlan = sealed { Send, Suppressed(reason: SuppressionReason) }`, `SuppressionReason = sealed { ChannelDisabled(policy), DryRun(environment), EnvironmentBlocked(environment), TargetMissing(policy) }`. **정책(보내도 되는가)과 환경(지금 보낼 수 있는가)은 다른 필드**이고 첫 위반이 이긴다(비활성+dry-run 은 비활성). 미배달 환경은 문자열 스니핑이 아니라 선언 집합 멤버십, 알림·메일이 **같은 집합** | NOTI-03 acceptance 셋 · 분류 근거 |
| ③ | **masking·owner isolation** — `MaskedTarget` 은 생성 시 raw 식별자를 마스킹한 값만 보유(원문은 어댑터 설정 안, 도메인·저장·evidence 어디에도 원문 없음). 단일 회사라 owner isolation 은 「다른 owner 의 target 으로 보내는 경로가 타입에 없다」까지 | 4E 「masking 과 owner isolation」 · NOTI-03 「target 라벨은 신뢰할 수 없는 값」 · §8 안전 규칙 |
| ④ | **동일 idempotency key 의 단일 delivery effect** — sender 계약: 같은 키의 재호출은 **효과 0**(fake 가 호출 횟수·효과 횟수를 분리 계수). at-most-once: 결과가 모호(`Unknown`)하면 재시도하지 않고 `Isolated` 로 4C 에 돌려준다(ADR 0005 D-11 표 「채널 배달 — 하지 않는다」) | 4E 「동일 idempotency key 의 단일 delivery effect」 · `OPEN-NOTI-02` 해소 · NOTI-05 「자동 재발송하지 않고 격리」 |
| ⑤ | **결과 enum** — `DeliveryResult = sealed { Delivered(at), Rejected(reason), Unknown(observedAt) }`. **미전달을 완료로 마킹하는 경로가 없다**(`OPEN-NOTI-01` — 의도/버그 판정 없이 구조로 닫음). `Rejected` 는 열거 사유 | `OPEN-NOTI-01` · NOTI-03 「채널 차단 사유가 열거된 status」 |
| ⑥ | **dry-run / fake sender** — `FakeSender`(효과 기록·실패 주입·`Unknown` 주입)와 `DryRunSender`(판정 ② 를 `DryRun` 으로 내고 효과 0) 를 adapters 에. 실 sender 는 없음 | 4E 「dry-run/fake sender」 · `milestone-4.md` 범위 밖 「실제 Telegram/email」 |
| ⑦ | **정책 데이터 슬롯** — 채널 활성·target·가치 게이트 임계(NOTI-01 4종, `OPEN-NOTI-06` 값 미정)는 versioned 정책 슬롯. 값은 운영 관측 뒤 | NOTI-01 「임계값 4개가 코드 상수에 있는 형태는 채택하지 않는다」 |

**만들지 않는 것**: 실 발송 · outbox 상태 기계 · 피로도 게이트 · fallback · 앱 알림함 · 재시도(어느 계층에도) · 사람이 읽는 문장(렌더러는 port, 구현은 6A/후속).

---

## 운영자 결정 필요 — 착수 전(`prep` D-M4-8) · 계약 고정(D-4E-1~4)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4E-1** | 4E 의 port 는 `workflow` 소유(application), 구현은 `adapters` — ADR 0005 D-9·D-10 배치와 동일 | ADR 0006 D-3 | 계약 고정 |
| **D-4E-2** | `Unknown` 결과의 소유는 4C(`Isolated`) — 4E 는 결과를 정직하게 낼 뿐 재시도·격리 판단을 하지 않는다 | ADR 0005 D-11 「한 부작용에 재시도 계층은 하나」 | 계약 고정 |
| **D-4E-3** | 통지 이후 판정 확정(`OPEN-NOTI-08`)은 **새 `DeliveryRequest`**(새 idempotency key, 기존 메시지 갱신 없음) — 채널 측 편집 API 의존을 만들지 않는다. 앱 알림함(NOTI-10)이 이력 | D-M4-8 (a) | 계약 고정 |
| **D-4E-4** | 읽음 상태 되돌림(`OPEN-NOTI-05`)은 4E 밖(앱 알림함 소유, 6A) — 어댑터 계약에 읽음 개념 없음 | D-M4-8 (a) | 계약 고정 |

---

## 위협 모델 — 4E 고유 경계

**방어한다**: (a) raw 식별자·비밀값의 저장·로그·evidence 유출(③ 타입 + S-3 스캔) (b) 정책/환경 사유의 합침(② 필드 둘 + 전수 test) (c) 미전달의 완료 마킹(⑤ 결과 enum) (d) 같은 키의 이중 효과(④ fake 계수) (e) 재시도의 유입(D-4E-2 — 4C 리뷰 항목) (f) 문장(업무 사유)의 요청 유입(① 분리).
**방어하지 않는다**: 실 채널의 거동 · 렌더 내용의 옳음 · outbox 원자성(4C) · 가치 게이트 값 · 사용자 승인 없는 실 발송(agent-workflow §1 — 코드에 실 sender 가 없다).

**우회 후보(≥5)**: (1) `MaskedTarget(raw)` 생성자 공개 → `internal` + 마스킹 factory 만 (2) `DeliveryResult.Delivered` 를 sender 가 실패 뒤에도 반환 → fake 실패 주입 test (3) `Unknown` 을 sender 안에서 재시도 → 호출 횟수 계수 test (4) 사유를 문자열 필드로 → sealed 만 (5) dry-run 이 실 sender 를 감쌈 → `DryRunSender` 는 sender 의존 없음(구조) (6) 정책 임계 리터럴 → 정책 슬롯 부재 시 구성 실패.

---

## 조사 결과 — 이 slice 에 영향을 주는 것

- 조사 (c): **반례** — legacy outbox 행이 렌더된 문자열을 저장(요청/렌더 미분리 → ① 의 근거) · 피로도 게이트가 직접 배달 경로에만 걸려 outbox 경로 우회(게이트는 sender 앞이 아니라 요청 생성 자리 — 4B/4C 참조).
  **재사용 1순위** — 배달 경로 resolver(정책/환경 분리·first-match·status 11종)가 순수 함수 → ② 는 이식(`legacy-behavior` 로 test 정책, 어휘는 4E sealed 로 재선언). 알림 outbox 는 dialect 분기·재시도 0·기본 비활성이라 4C 기준선은 추론 쪽 outbox.
  fixture 0건 → 4E golden 은 ② 전수 표(정책×환경 조합)를 `authored-from-approved-spec` 으로 신설(curator, 착수 시).

---

## OPEN — 수령·신설

| OPEN | 4E 처리 |
| --- | --- |
| `OPEN-NOTI-01` | ⑤ 구조로 닫는 후보(의도/버그 판정 불요) |
| `OPEN-NOTI-02`(해소) | ④ at-most-once 소비 |
| `OPEN-NOTI-04`·`OPEN-NOTI-07` | 범위 밖(`후속`) — D-M4-8 |
| `OPEN-NOTI-05` | D-4E-4 — 4E 밖 |
| `OPEN-NOTI-06` | ⑦ 정책 슬롯, 값은 관측 |
| `OPEN-NOTI-08` | D-4E-3 — 새 요청 |
