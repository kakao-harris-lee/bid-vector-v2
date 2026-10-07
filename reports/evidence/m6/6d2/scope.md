# M6/6D-2 — restart 수렴 · redelivery · 재현 등식에 정책·전략 버전 (2026-10-07, 착수 계약 초안, 팀장)

6D 분할(D-6D-1 C-2 (a))이 6F-10 뒤로 넘긴 **test 조립 slice** 다. 6F-10 이 production 으로 낸 relay(`RelayOutboxNotifications` +
`PostgresAdvisoryLockLease` + `EvaluationCommitRun`)와 넓힌 payload(정책 버전·전략 revision)를 **파이프라인 수준**에서 잇는다 — 6D 다섯 축 중
③ 「restart 뒤 outbox/inbox 수렴」· ② 의 「broker redelivery」· ⑤ 「동일 input/policy/model version 재현」의 남은 자리. 완료 조건 **3·4·6** 의
마지막 test-scope 자리.

- base: 이 브랜치가 분기해 나온 **현재** `main` — `git merge-base HEAD origin/main`(고정 SHA 아님, D-6F5-30). 착수 실측값 `f4b4ff91`(PR #63
  6F-10 머지 커밋). 라운드마다 재산출.
- 선행: 6D-1(파이프라인 E2E 다섯 · `PipelineAssembly` · 협력자 출처 단언) · 6F-10(production relay · 임대 · 고아 격리 · 어휘 해석표 · payload
  20 필드 · 커밋 러너) — 전부 `main`.
- worktree `bid-vector-v2-m6-6d2`, 브랜치 `m6-6d2/2026-10-07`. evidence `reports/evidence/m6/6d2/`(6D-1 의 `6d/` 는 닫힌 evidence —
  D-6F10-10 선례대로 고치지 않는다). 결정 ID `D-6D2-n`, 운영자 결정 `B-n`.
- 성격: **production diff 0** 의 test slice(6D-1 D-6D-3 과 같은 규칙). production seam 이 필요해지면 **멈추고 보고**한다.
- Codex: 없음(test 코드만 — 6D-1 C-5 (a) 와 같은 처분, B-4).

## 수취하는 인계

| 항목 | 출처 | 이 slice 의 처분 |
|---|---|---|
| 6D 표 6D-2 행 — 문면은 D-6F10-10 정정본 「claim 중 크래시 → 재기동 → 고아 **격리** → 발송 0 또는 1, **중복 0**(놓침 감수)」 | 6D scope 제안 표 · D-6F10-9/10 | **잰다** — 파이프라인 E2E 에서 production relay 로 (R-1~R-5) |
| 6D 표 「redelivery(같은 entry 두 번 claim → inbox 가 두 번째를 거부)」 | 6D scope 제안 표 | **정의를 바로잡아 잰다** — 전이표에 `Claimed → Pending` 간선이 없고 claim SQL 이 `WHERE state = 'PENDING'` 이라 **같은 entry 는 두 번 claim 될 수 없다**(구조). redelivery 는 「같은 멱등 키의 **다른 entry**」(D-6F7-6: run 마다 행 하나)가 **재기동을 건너** 두 번째 relay 에 닿는 것이다 — inbox 가 거부한다(SkipDuplicate → DELIVERED, 발송 0) |
| 6D-1 알려진 제한 「재현 등식에 정책 버전·전략 revision 없음」(C-4) | 6D-1 · 6F-10 ⓓ | **닫는다** — 등식의 typed 형에 두 값을 넣고 전략 revision 음성 대조를 더한다(정책 버전 음성 대조는 B-3) |
| 6F-10 D-6F10-8 ② 「전략 revision 을 올리면 같은 입력의 payload 가 달라진다」 | 6F-10 | 재현 등식의 **음성 대조 값**으로 쓴다(app 수준 test 가 이미 재는 것을 파이프라인 등식에 넣는다) |
| 6F-10 교훈 「production 조립을 돌리는 test」·「투영 단계」·「함수는 RED 호출 자리는 초록」 | 6F-10 종결 | 설계 검토 (2) 기본 항목으로 적용(아래 D-6D2-2) |
| `OPEN-6F10-CLAIM-OBSERVABILITY` · `OPEN-6F10-SCHEDULER` · `OPEN-STR-12` | 6F-10 · M4 | 변경 없음 — 이 slice 는 열을 더하지 않고 실 sender 를 들이지 않는다 |
| 6D-1 알려진 제한 「rollback 축은 gateway 수준」 | 6D-1 | 변경 없음(이 slice 의 축이 아니다) |

## 착수 실측 (저장소 직접 대조, 2026-10-07)

| 축 | 오늘 저장소 | 6D-2 가 더하는 것 |
|---|---|---|
| 고아 격리 | `RelayDatabaseTest` — 상태 **강제** 1행 → 다음 run 격리 · `CrashAfterDispatch` 로 T1·T2 분리(행 CLAIMED 잔류). 파이프라인(수집→평가→outbox)이 만든 행이 아니고 「재기동」 모양도 없다 | 파이프라인이 만든 행 위에서 **죽은 relay → 새 relay** 두 조립으로 격리·발송 계수·수렴 고정점을 잰다 |
| 임대 | `RelayLeaseLossDatabaseTest` — 임대 연결 절단(`pg_terminate_backend`) 중간 정지 · `PostgresAdvisoryLockLease.withLease` 는 `finally` 에서 unlock + 연결 반납(죽은 relay 의 예외가 임대를 푼다) | 살아 있는 홀더가 있는 동안의 「재기동」은 **Busy** 여야 한다(살아 있는 홀더의 CLAIMED 를 남이 격리하면 안 된다) — R-5 음성 대조 |
| 키 중복 | `PipelineFailureInjectionE2ETest` — 같은 키 2행을 **한 relay run** 이 집어 발송 1·SkipDuplicate 1 · `RelayVocabularyDatabaseTest` — inbox 선시딩 SkipDuplicate | **재기동을 건너**: run 1 이 키 K 전달(DELIVERED+inbox) → 새 조립 → 재평가가 새 행 K → 새 relay 는 발송 없이 DELIVERED(D-1); DELIVERED/ISOLATED 행은 새 relay 가 집지 않는다(claimed 0, D-2) |
| 재현 등식 | `PipelineReproducibilityE2ETest` — DB 되읽은 payload **문자열** 집합 등식 + release 음성 대조. 6F-10 이 payload 를 20 필드로 넓혀 정책 버전·전략 revision 이 문자열에 **암묵적으로** 들어 있다. typed 단언 0 · 전략 revision 음성 대조 0(app `EvaluationCommitRunE2ETest` 는 production 커밋 러너의 payload 하나를 비교) · 시딩 revision = **1**(기본값 함정 — 6F-10 checklist 가 실측한 사각) | 등식의 typed 형(두 run 의 `ladderPolicyVersion` == 판정이 쓴 `EVALUATION_LADDER_POLICY_VERSION` · `strategyRevision` == 시딩값, 시딩값은 비기본값) + 전략 revision 음성 대조(release·시각·상관관계 고정) |
| 정책 버전 음성 대조 | `EVALUATION_LADDER_POLICY_VERSION` 은 `workflow` 상수 — 밖에서 바꿀 자리가 없다 | production seam 없이는 불가 → **B-3** |
| 발송 뒤·T2 전 크래시 + 재평가 | inbox 는 T2 에서만 기록(D-6F10-3) → 크래시 창에서는 키 중복 제거가 서지 않는다 → 재평가가 만든 같은 키 행이 **다시 발송**된다(at-most-once 의 창) | **실측해 사실로 등재**(B-2) — 「중복 0」은 entry 단위·단일 크래시/재기동 문면이다 |
| 게이트 | `gate-tests.properties` 모듈별 모집단 == 등재(D-6G2g-10) | 새 test 클래스 전부 등재(추가만) |
| 호스트 | Gradle daemon 0 · available 14GB · **swap free 1.9GB < 2GB 규칙**(사용자 2026-10-07: 2GB 유지) | 빌드는 swap ≥ 2GB 를 **별도 호출로** 확인한 뒤에만. 미달이면 시작하지 않고 보고 |

## 운영자 결정 — B-1 ~ B-5 (선택지 + 추천; 추천안으로 착수하고 사용자 정정 시 계약을 갱신한다)

- **B-1 「재기동」 모델** — (a) **새 조립 인스턴스**(새 `PipelineAssembly` = 새 lease 세션·새 relay·새 port 인스턴스; 죽은 relay 는 예외로 끝나
  `withLease` 의 `finally` 가 임대를 푼다 — 프로세스 사망의 in-JVM 등가) + **살아 있는 홀더** 대조(첫 relay 가 배치 중간에서 막힌 동안 둘째 조립의
  relay → `Skipped(LeaseBusy)`, 상태 분포 불변) · (b) Spring 컨텍스트 두 번 기동(app test — production 조립은 sender 자리지킴이라 발송 자체가 불가,
  비용만 든다). **추천 (a)**.
- **B-2 발송 뒤·T2 전 크래시 뒤 재평가의 재발송** — (a) **실측해 알려진 제한으로 등재**(production 무변경; ADR 0005 §7 addendum 에 한 줄 —
  「inbox 는 전달 뒤에 쓰이므로 전달과 T2 사이의 크래시 창에서는 같은 키의 다음 entry 가 다시 전달된다; at-most-once 가 막는 것은 **entry 의
  재실행**이지 키의 재발생이 아니다」) · (b) inbox 선기록 — 6D-1 이 좌초·키 소진을 실측한 모양, 불채택 · (c) outbox `idempotency_key` UNIQUE —
  마이그레이션 + D-6F7-6 번복, 범위 밖. **추천 (a)**.
- **B-3 정책 버전 음성 대조** — (a) **경계** — 등식의 typed 형에는 넣되(두 run 이 판정이 쓴 그 인스턴스와 같다) 「바꾸면 깨진다」 대조는 production
  seam 없이 불가하므로 생략하고 알려진 제한으로 등재; 변이(상수 교체 → typed 단언 RED)로 등식이 그 축을 **읽고 있음**은 실측 · (b) 정책 버전
  주입 seam — (2b) 위반(production 공개 표면). **추천 (a)**.
- **B-4 Codex** — (a) **없음**(test 코드만; verifier opus + code-reviewer sonnet 병렬) · (b) 탐. **추천 (a)**.
- **B-5 자리** — (a) **`adapters/src/test/kotlin/bidvector/adapters/e2e/`** 기존 suite 확장(새 클래스 둘 + `PipelineReproducibilityE2ETest` 확장 +
  `PipelineAssembly`·support 의 test seam) · (b) app. **추천 (a)** — production relay 와 fake sender 가 이미 그 조립에 있다.

## in_scope (초안)

- `adapters/src/test/kotlin/bidvector/adapters/e2e/**`(신설 `PipelineRestartConvergenceE2ETest` · `PipelineRedeliveryE2ETest`, 확장
  `PipelineReproducibilityE2ETest`·`PipelineAssembly`·`PipelineE2ESupport`·`PipelineFakes`) · `config/quality/gate-tests.properties`(등재 추가만) ·
  `reports/evidence/m6/6d2/**` · `milestone-6.md`(착수·종결 문단만) · `docs/adr/0005-domain-events-and-outbox.md`(§7 addendum 한 줄, B-2 (a) —
  공유 파일, 별도 커밋).
- **out_scope**: production 코드 전부(`*/src/main/**`) · 마이그레이션 · `.github/**` · `docker/**` · Python · 닫힌 evidence(`reports/evidence/m6/6d/**`·
  `6f10/**`).

## 산출물 (초안 — 설계 검토 D-6D2-2 가 술어를 확정)

| ID | 무엇 | 단언(공허한 초록 불가 — DB 상태·sender 기록) |
|---|---|---|
| **R-1** | claim 커밋 뒤·발송 전 크래시 → 재기동 | 고아 ISOLATED 1 · 발송 0 · 알림 종류 PENDING/CLAIMED 0 · inbox 0 |
| **R-2** | 발송 뒤·T2 전 크래시 → 재기동 → 같은 입력 재평가 → relay | 재기동 run: 고아 ISOLATED 1 · 발송 총 1 · inbox 0. 재평가 뒤: 새 행 DELIVERED · **발송 총 2** · inbox 1 — B-2 의 실측(알려진 제한) |
| **R-3** | N행 배치 중 k행 처리 뒤 크래시 → 재기동 | DELIVERED k + inbox k · ISOLATED N−k · 발송 k · 재기동 run 의 발송 0 |
| **R-4** | 수렴 고정점 | 재기동 뒤 한 번 더 재기동 → 보고 전부 0(claimed·orphansIsolated) · 상태 분포 불변 — 「수렴」의 정의 |
| **R-5** | 살아 있는 홀더 | 첫 relay 가 배치 중간에서 래치로 막힌 동안 둘째 조립 relay → `Skipped(LeaseBusy)` · 첫 relay 의 CLAIMED 가 격리되지 않음 · 풀리면 첫 relay 가 끝까지 전달 |
| **D-1** | 재기동 건너 redelivery | run 1: 키 K DELIVERED + inbox K → 새 조립 재평가(행 2, 같은 키) → 새 relay: claimed 1 · skippedDuplicates 1 · 발송 0 · DELIVERED 2 · inbox 1 |
| **D-2** | 같은 entry 재claim 불가 | 종단 행만 있는 상태에서 새 relay → claimed 0 · 상태 분포 불변(구조: 전이표·`WHERE state='PENDING'` — 변이는 verifier) |
| **P-1** | 등식 typed 형 | 두 run 의 decoded payload: `ladderPolicyVersion == EVALUATION_LADDER_POLICY_VERSION` · `strategyRevision == StrategyRevision(시딩값 ≠ 1)` · 두 run 서로 같음 |
| **P-2** | 전략 revision 음성 대조 | run 1 → revision 올림 → run 2: payload 집합 2 · 상관관계·시각 집합 1(갈린 축이 revision 임이 분리) · 되돌리면 run 3 == run 1 |
| **P-3** | 등식 문면 갱신 | `PipelineReproducibilityE2ETest` KDoc 의 「알려진 제한(C-4)」 삭제, 등식의 입력 = 입력 공고 + 정책 버전 + release 다섯 + 전략 revision |

## acceptance (초안)

CI `check` job 명령 그대로 — `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline`. `:adapters:test` 는 `--rerun` 으로 **실제 실행**
(FROM-CACHE 는 실행 증거가 아니다, 6D-1). **S-20 생략 사유**: Python 무변경(6D-1·6B-2 와 같은 처분, Python 절반은 CI `ml-engine` job 이 정본).
**container job 생략 사유**: production·docker·CI diff 0. 변이: 축마다 ≥1 — 크래시 주입 제거 → R-1/R-2/R-3 RED · 격리 호출 제거(production,
verifier 실측) → R-1 RED · SkipDuplicate 판정 제거 → D-1 RED · revision 올림 제거 → P-2 RED · typed 단언의 상수 교체 → P-1 RED · 래치 제거 → R-5
RED(Busy 가 아니라 격리가 일어난다). 변이 전 커밋, 적용은 `git diff --numstat` 먼저(6F-4). 게이트 결과는 **종료 코드로**(6D-1 교훈).

## rollback (초안)

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`; 공유 파일(`gate-tests.properties`·`milestone-6.md`·ADR 0005)은 커밋
해시 hunk(`--no-merges`, 6F-10 교훈). 목록은 실측 HEAD 에서 `git diff --name-status <base>..HEAD` 기계 산출.

## 하네스 레인 변경 (상시)

- (없음 — 착수 시점)

## 계약 갱신 r1 (2026-10-07, 팀장 — 착수 결정 · 설계 검토)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-1** | **B-1~B-5 추천안으로 착수**(사용자 부재 중 자율 진행 — 정정 지시가 오면 그 시점 계약 갱신으로 반영): B-1 (a) 새 조립 인스턴스 = 재기동, 살아 있는 홀더는 Busy · B-2 (a) 전달·T2 사이 크래시 뒤 키 재발생은 **실측해 알려진 제한 + ADR 0005 §7 한 줄** · B-3 (a) 정책 버전 음성 대조는 경계(typed 등식 + 상수 교체 변이로 「읽고 있음」만 실측) · B-4 (a) Codex 없음 · B-5 (a) adapters e2e | 팀장 2026-10-07 |
| **D-6D2-2** | **설계 검토 요지**(`_workspace/m6-6d2/01_design-review.md`, 세션 모델 직접): (0) 경계 — production 조립 기동(6F-10 자리)·실 발송·키 재발생(B-2)·정책 버전 변경(B-3)·OS 사망·임대 밖 동시성은 밖 · (1) 크래시는 **사건**에 걸고(순번 아님) 재기동은 **새 인스턴스 집합**, 수렴은 **고정점**(재재기동 보고 0 + 분포 Map 등식), 발송 계수는 두 조립 sender **합**, 격리는 production 보고 + DB(상태 강제 helper 금지), 재기동 **전** 중간 상태 단언, typed 등식은 저장 타입 읽어 복원·시딩 revision 비기본값, **relay 를 협력자 출처 그래프에 추가** · (2) 우회 12(주입 미호출 · 같은 인스턴스 · 상태 강제 · sender dedup · 무차별 등식 · 같은 참조 · 변이 미적용 · 투영 공백 · 호출 자리 초록 · R-5 교착 · 재기동 run 의 우연한 발송 · B-2 오독) 각각의 닫는 술어 · (2b) production public 표면 0, test seam 은 `PipelineAssembly` 생성자 기본값 production, 크래시 wrapper 는 정직한 조립 그래프에서 비-MAIN 으로 잡혀야 함(「경계로 처리」 행 실측) · (3) 과잉: Spring 두 번·OS kill·UNIQUE·seam; 미달: 로그·단일 run 중복만·CLAIMED 0 정의·revision 1·문자열만 | 설계 검토 |
| **D-6D2-3** | **production 불변 규칙 + 호스트 규율**: `*/src/main/**`·migration·`.github/**`·`docker/**` diff 0, `internal` 완화 0 — seam 이 필요하면 멈추고 보고. 빌드 전 `pgrep`·`free -m`·`ps` **별도 호출**로 확인, **swap free < 2GB 면 시작하지 않고 보고**(사용자 2026-10-07 결정 — 6F-10 의 1GB 는 1회 예외). 게이트 결과는 종료 코드로, 커밋은 별도 호출로. 변이 전 커밋, 적용은 `git diff --numstat` 먼저. 커밋은 `git add <in_scope 경로>` 개별 인자만 | 6D-1 D-6D-3 · 6F-10 D-6F10-7 · 호스트 규칙 |

## 계약 갱신 r2 (2026-10-07, 팀장 — 호스트 예외)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-4** | **사용자 결정: swap 2GB 규칙 1회 예외 적용**(착수 실측 available 14.6GB · swap free 1.94GB · Gradle daemon 0). 이 slice 안에서 Gradle 은 available ≥ 6GB · swap free ≥ 1GB 면 **하나씩** 허용, swap free 1GB 아래면 즉시 중단·보고. 규칙 자체(2GB)는 유지 — 다음 slice 에는 적용되지 않는다 | 사용자 2026-10-07 |

## 계약 갱신 r3 (2026-10-07, 팀장 — 구현 수령·동결 · 판정 표적)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-5** | **구현 수령·동결.** 레인 커밋 ①~⑧(`c89e01b6`…`8af675c1`·evidence `dcc72cd4`; 보고 `_workspace/m6-6d2/02_implementer_report.md`). 팀장 대조: in_scope 밖 변경 0 · production·migration·CI·docker diff **0** · `internal` 완화 0 · 크기 게이트(산출물 765 줄 ≥ evidence 395 줄) · 새 파일 셋 전부 e2e 디렉터리 안(계약 갱신 불필요) · Gradle daemon 0. **마지막 산출물 커밋 `8af675c1` = rollback 실측 HEAD**. **판정 SHA = 이 r3 커밋**(ADR §7.6 커밋 뒤). 레인 자기 신고 — clean-tree 양성 대조 복원에 `git checkout HEAD --` 1회(금지 위반, 손실 0, 두 번째 측정이 정본) — **사실로 선언**, 이력 되쓰기 없음 | 레인 보고 · 팀장 대조 |
| **D-6D2-6** | **B-2 (a) 등재 완료** — ADR 0005 **§7.6** 「at-most-once 가 막는 것은 entry 의 재실행이지 키의 재발생이 아니다」(R-2 실측 발송 합 2, 공유 파일 별도 커밋). 설계 검토 (2b) 문면 둘은 레인 실측대로 정정 — 주입 조립의 비-MAIN 은 wrapper + hook 람다 둘이고 감싸인 production 경계는 `delegate` 로 **함께 보인다**(「대체해 숨는」 모양이 아니라 「앞에 덧대어 드러나는」 모양). 레인이 만든 `EventTriggeredTransactions` 는 `InterferingTransactions`(호출 순번) 와 다른 사건 술어라 사본이 아니다 — 수용 | 레인 보고 |
| **D-6D2-7** | **verifier 표적**(opus, 판정 SHA 에서): ① acceptance 둘 재실측(`check` 의 `:adapters:test` 실제 실행 확인, FROM-CACHE 불인정) ② 계약이 맡긴 production 변이 둘 — `isolateOrphans` 호출 제거 → R-1 RED · claim SQL `WHERE state` 제거 → D-2 RED — 와 레인 표 여덟 중 **production 둘** 재현(inbox 판정 뒤집기 · 정책 버전 사용 자리 교체) ③ 우회 고안 ≥3(특히: 재기동 run 이 PENDING 을 새로 집어 「발송 ≤ 1」이 우연히 성립하는 모양 · R-5 가 Busy 를 **같은 세션**에서 얻는 모양 · typed 등식이 두 run 을 서로만 비교하는 모양) ④ (2b) 경계 행 — 정직한 조립의 그래프에 비-MAIN 0 · wrapper 조립에서 RED 를 실측 ⑤ rollback 유효성 `git diff --name-only 8af675c1..<판정 SHA> -- adapters/src/test/kotlin/bidvector/adapters/e2e config/quality/gate-tests.properties` 빈 출력 ⑥ 장부(좌표·축어·크기·누출 참조형) ⑦ 호스트 D-6D2-4(available ≥ 6GB · swap ≥ 1GB, 하나씩, 미달 시 중단·보고). **code-reviewer(sonnet) 병렬** — test 설계 결함·공허한 초록·시한 의존·사본 | 하네스 |

## 계약 갱신 r4 (2026-10-07, 팀장 — 판정 r1 수령 · 승인 전 일괄 처분)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-8** | **verifier r1 `ready-for-review` @`41e67c4e`**(`03_verifier_r1.md`): acceptance 둘 exit 0(`check --rerun-tasks`, `:adapters:test` 899 실제 실행) · production·migration·CI·docker diff 0 · 변이: 격리 제거 → R-1·R-2·R-3 RED(**R-4 초록** = R1-M-1) · claim SQL `WHERE state` 제거 → D-2 RED(port 의 MARK_CLAIMED guard 경유; guard 까지 빼면 자기 단언으로 RED) · inbox 판정 뒤집기 → D-1 + 6D-1 같은 공고 두 번 test RED(R1-L-1) · 정책 버전 사용 자리 교체 → P-1 만 RED · 고아 재발송 → R-1~R-4 RED · 출처 탐침 셋(wrapper·전체 교체·JDK Proxy) 전부 RED. non-blocking: **R1-M-1** R-4 고정점이 격리 부재를 수렴으로 받음 · **R1-M-2** 「같은 인스턴스 = 같은 임대 세션」 전제 거짓(`withLease` 가 호출마다 연결을 연다 — 같은 조립 재호출도 Busy; test 결론은 유효, KDoc·checklist 우회 2 행 문면만 거짓) · **R1-M-3** 판정 SHA 에서 rollback ⓪ 등식이 ADR §7.6(`b8ed0785`, 팀장 커밋) 을 못 덮음 · R1-L-1 변이 형태·범위 미기재. **code-reviewer r1**(`04_code_review_r1.md`): high 0 · **G-1** medium(주입 조립 그래프 test 가 `depthLimitHits`·`traversalFailures`·`skippedHolders` 0 과 `leaks` 개수 2 를 잠그지 않음) · low 8(G-2~G-9). 재작업 **0/5**(not-ready 0). verifier 가 남긴 TestKit daemon(PID 3222329, PPID 1, cwd `~/.gradle/daemon`)은 팀장이 호스트 규칙대로 그 PID 만 멈춤(잔존 0) | 두 보고서 |
| **D-6D2-9** | **승인 전 일괄 — 레인 커밋 둘(산출물 1 + evidence 1), 그 뒤 표적 재검증(R-4 술어·G-1 단언 변경 = severity 무관 표적).** 산출물 커밋: ① R1-M-1 — R-4 의 수렴을 「고정점 **그리고** 알림 종류 행이 전부 종단(PENDING·CLAIMED 0)」으로 단언하고 첫 재기동 보고 `orphansIsolated == 1` 을 R-4 에도 둔다(격리 제거 변이에서 R-4 RED 가 되게) ② R1-M-2 — 재기동 클래스 KDoc 과 seam 논증 문면 정정(임대 세션은 인스턴스가 아니라 `withLease` 호출 단위; relay 는 무상태라 새 인스턴스와 재호출이 동치; 「죽은 조립의 임대가 풀렸다」의 증거는 재기동 relay 가 Busy 가 아닌 것뿐) ③ G-1 — 주입 조립 그래프 test 에 건너뜀 신호 셋 0 + `leaks shouldHaveSize 2` ④ G-2(홀더 스레드 실패 원인 합류 뒤 단언)·G-4(도출값을 식으로)·G-5(P-1 KDoc 「사용 자리 교체」로)·G-8(R-2 `orphansIsolated` 단언)·G-9(명명·FQN) 는 고친다; G-3(미사용 seam 파라미터 — 제거 또는 사용)·G-6(시한 상수 사본 — 같은 패키지 `relay` 의 상수를 import 할 수 있으면 재사용, 아니면 등재)·G-7(상태 어휘 자리) 은 레인이 판단하고 결과를 보고에. evidence 커밋: checklist 우회 2 행 정정(R1-M-2) · commands.md 변이 표에 변이 **형태와 실행 범위** 추가 + inbox 변이 RED 목록 정정(R1-L-1) + 일괄 뒤 acceptance 재실측 · rollback.md 에 ADR 0005 를 공유 파일 hunk 절차(`git log --no-merges` 산출)에 넣고 ⓪ 등식을 **판정 SHA 에서** 재산출(R1-M-3; 되돌림 대상이 팀장 커밋이어도 절차는 레인 문서가 든다) · 실측 HEAD 를 새 산출물 커밋으로 옮기고 ①~③ 재실측(④~⑥ 은 트리 동일성 갈음 불가 — evidence 가 누출 게이트 입력 — 이므로 호스트 여력이 있으면 실측, 없으면 「미실측」으로 적고 팀장이 처분). 스코프 외 변경 0 · production diff 0 유지 · 호스트 D-6D2-4 | R1-M-1~3 · L-1 · G-1~G-9 |

## 계약 갱신 r5 (2026-10-07, 팀장 — 일괄 수령·동결 · 표적 재검증)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-10** | **일괄 수령·동결.** 산출물 **`ea07b9a7`**(= rollback 실측 HEAD) · evidence `1bb991af`(`05_implementer_batch.md`). 팀장 대조: 미커밋 0 · in_scope 밖 0(e2e 다섯 + evidence 셋) · production·migration·CI·docker diff 0 · `adapters/src/test/.../relay` 무변경 · 크기 게이트(873 ≥ 452) · daemon 0. 레인 판단 수용: G-3 미사용 seam 파라미터 **제거** · G-6 시한 상수를 e2e 패키지 공용 top-level 하나로 통합(선례 private companion 셋 제거 — `PipelineFailureInjectionE2ETest` 가 그래서 수정됨) · G-7 상태 어휘 넷을 `PipelineE2ESupport` 로. 레인이 적은 사실: 정책 버전·시딩 revision 모두 **정의 자리** 변이는 단언과 생산이 같은 상수를 참조해 초록 — 그래서 변이는 사용 자리에 건다(P-1 KDoc·commands 정정). 격리 no-op 변이가 이제 R-1~R-4 RED. **판정 SHA = 이 r5 커밋** | 레인 보고 · 팀장 대조 |
| **D-6D2-11** | **verifier 표적 재검증**(opus, R-4 술어·G-1 단언 변경 = severity 무관): ① `check`·`qualityBaseline` 판정 SHA 재실측(`:adapters:test` 실제 실행) ② 변이 — production `isolateOrphans` no-op → **R-4 포함** R-1~R-4 RED 재현 · G-1 단언 실측(주입 그래프의 hook 가지 절단 또는 깊이 상한 축소 → seam test RED) · G-6 통합 뒤 시한 상수가 R-5·6D-1 DB conflict 두 자리에서 같은 값을 읽는지 ③ R1-M-2 문면 대조(재기동 KDoc·checklist 우회 2 가 「호출 단위 세션·무상태 relay」로 바뀌었는가) ④ rollback — ⓪ 등식을 판정 SHA 에서 재산출(ADR 포함, `comm` 양방향 빈 출력) · 유효성 `git diff --name-only ea07b9a7..<판정 SHA> -- adapters/src/test/kotlin/bidvector/adapters/e2e config/quality/gate-tests.properties docs/adr/0005-domain-events-and-outbox.md` 빈 출력 ⑤ 장부(좌표·축어·크기·변이 형태 열 존재) ⑥ 호스트 D-6D2-4. 새 finding 없으면 종결 → 종결 문단 → push → PR → `/code-review` → 머지는 사용자 결정 | 하네스 |

## 계약 갱신 r6 (2026-10-07, 팀장 — 종결 판정)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-12** | **표적 재검증 `ready-for-review` @`023326fd`, 새 finding 0**(`06_verifier_targeted.md`): acceptance 둘 `--rerun-tasks` exit 0(`:adapters:test` 899 실행) · 격리 no-op 변이 → R-1~**R-4** RED(R1-M-1 닫힘; 전이 없는 격리 계수 변이도 R-4 종단 단언으로 RED) · G-1 실측(깊이 상한 축소 → seam test + 6D-1 출처 test RED, hook 필드만 조용히 건너뜀 → seam test 의 개수 2 단언만 RED) · G-6 시한 상수 정의 하나(R-5·6D-1 conflict 둘이 읽음) · R1-M-2 문면 정정 확인 · R1-M-3 rollback ⓪ 등식 ADR 포함 양방향 빈 출력(`ea07b9a7`·`023326fd` 둘 다) + 유효성 빈 출력 · R1-L-1 형태 열 · 장부 좌표 0·누출 0·크기 459 ≤ 839. verifier 가 남긴 TestKit daemon(PID 3341620, PPID 1)은 팀장이 호스트 규칙대로 그 PID 만 멈춤(잔존 0). **6D-2 종결** — 재작업 **0/5**(not-ready 0). 다음: milestone 종결 문단 → push → PR → `/code-review` → 조치 → **머지는 사용자 결정** | 표적 재검증 |

## 계약 갱신 r7 (2026-10-07, 팀장 — PR #64 `/code-review` 수령·처분)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-13** | **PR #64 `/code-review`(head `fad3af0f`) finding 8, 정확성 결함 0** — 처분. **레인 조치 라운드: 산출물 1 커밋 + evidence 1 커밋**, 그 뒤 **표적 재검증(조치 커밋만)**. 고친다: **F1** `crashAfterClaimBeforeDispatch` 술어가 「이 run 의 claim 커밋 뒤·발송 전」이 아니라 「CLAIMED 행이 하나라도 있고 발송 0」이라 선재 고아가 있으면 첫 경계 호출(고아 목록)에서 발화 — 술어를 **이 run 이 집은 행**으로 좁힌다(hook 생성 시점 CLAIMED 스냅숏 대비 증가, 또는 claim 반환 entry id) · **F2** R-5 `finally` 가 `shutdownNow()` 뒤 `awaitTermination` 을 안 해 실패 시 다음 test 와 임대 경합 — 합류 시한으로 대기 · **F3** 홀더가 예외 없이 일찍 돌아온 경우(`Skipped(LeaseBusy)` 등) 보고가 버려짐 — `withClue(running.get(0, SECONDS))` 로 단언 메시지에 싣기 · **F4** `lateinit var dying` 자기참조 closure 넷 — seam 을 `(TransactionBoundary, RecordingNotificationSender) -> ConsumerTransactionPort` 로 넓혀 호출부에서 lateinit 제거(test 소스셋 seam, production 0) · **F5** `crashedAfterFirstDispatch` 만 `CrashAfterDispatch` 손배선 — e2e 안에서 `relayBoundaryHook` + 술어 넷째로 통일(`adapters.relay.CrashAfterDispatch` 는 in_scope 밖, 그대로) · `INJECTED_NON_MAIN_COUNT` 등 그래프 단언이 통일 뒤에도 서는지 재실측 · **F7** `PipelineAssembly` 클래스 KDoc 의 낡은 「production 에 relay 를 두지 않는다(D-6D-3)」 문단 → `relay()` KDoc 을 가리키는 한 문장 · **F8** `outboxIdempotencyKeys()` 이중 질의 두 자리 — 한 번 bind. **등재(고치지 않음)**: **F6** 시한 상수 셋째 사본이 `adapters/src/test/.../event/OutboxClaimConcurrencyTest.kt`(6F-10 산출물, **in_scope 밖**) — 지금 PR 은 e2e 상수 주석의 「하나의 자리」 문면을 「e2e 패키지의 자리; event 패키지 사본은 별도」로 정정하고 checklist 알려진 제한에 등재(합치는 것은 그 파일을 만지는 다음 slice). evidence 커밋: checklist(F6 등재 · F1 술어 정의 문면) · commands(변이 표 재실측: hook 미호출·격리 no-op → R-1~R-4 · 그래프 탐침) · rollback 실측 HEAD 를 조치 산출물 커밋으로 옮기고 ⓪~③ 재실측(④~⑥ 은 여력 시). 조치 코멘트는 PR 에 **새로** 단다(판정 코멘트 덮어쓰기 없음) | `/code-review` 64 |

## 계약 갱신 r8 (2026-10-07, 팀장 — 조치 라운드 수령·동결 · 표적 재검증 2)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6D2-14** | **조치 라운드 수령·동결.** 산출물 **`3a3208d1`**(F1~F8; = rollback 실측 HEAD) · evidence `a9eb1c76`(`07_implementer_pr64.md`). 팀장 대조: 미커밋 0 · in_scope 밖 0(e2e 다섯 + evidence 셋) · production·migration·CI·docker diff 0 · 크기 게이트(985 ≥ 484) · 재기동 test 의 `lateinit` 0 · daemon 0. F1 — 술어를 hook 생성 시점 CLAIMED **entry id 집합** 대비 증가로(계수가 아닌 이유: 고아 격리가 선재 CLAIMED 를 태워 수가 그대로) + 자리 잠금 test(선재 고아 + 재평가 둘째 entry, 상태 강제 없음; 앞 판 술어로 되돌리면 **그 test 만** RED, R-1 은 초록 — R-1 의 DB 에 선재 고아가 없어 두 술어가 같은 답) · F5 통일 뒤 `INJECTED_NON_MAIN_COUNT = 2` 그대로 · F6 등재. e2e 24, `:adapters:test` 900 실행. 변이 열 행 RED(새 행: F1 집합 차 → 합). **판정 SHA = 이 r8 커밋** | 레인 보고 · 팀장 대조 |
| **D-6D2-15** | **verifier 표적 재검증 2(조치 커밋만)**: ① `check`·`qualityBaseline` 판정 SHA 재실측 ② F1 — 앞 판 술어(CLAIMED 존재 && 발송 0)로 되돌리는 변이 → 자리 잠금 test RED 재현 · 선재 고아 DB 에서 hook 이 고아 목록 호출에서 안 터지고 claim 뒤에 터지는지 ③ F2/F3 — R-5 실패 경로: 단언을 일부러 깨뜨린 변이에서 홀더 스레드가 합류되고 다음 test 가 `LeaseBusy` 를 받지 않는지(또는 clue 에 보고가 실리는지) ④ F4/F5 — seam 기본값 production 유지 · 정직한 조립 그래프 비-MAIN 0 · 주입 조립 2 ⑤ rollback ⓪ 등식 판정 SHA 재산출 + 유효성 `git diff --name-only 3a3208d1..<판정 SHA> -- <e2e> <gate-tests> <ADR>` 빈 출력 ⑥ 장부 ⑦ 호스트 D-6D2-4. 통과 → 조치 코멘트 새로 → CI → **머지 결정 요청** | 하네스 |
