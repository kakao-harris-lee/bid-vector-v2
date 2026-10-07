# M6/6E-1 — 체크리스트 · 알려진 제한 · OPEN 처분 (구현 레인)

base `80dc33b3` · 브랜치 `m6-6e1/2026-10-07` · 산출물 `C-1`·`C-2~C-5`·`C-9`·`G-4`.
`C-6`(차이 목록, 팀장)·`C-7`(ledger 연결표, 다른 레인)은 이 레인의 몫이 아니다.

## 산출물 점검

| ID | 자리 | 상태 | 등식 |
|---|---|---|---|
| **C-1** | `reports/evidence/m6/6e1/acceptance-trace.md` | 완료 — 61 행 · ⓐ29/ⓑ2/ⓒ30 | E-1·E-2·E-3 |
| **C-2** | `docs/runbook/m6-6e-operations.md` §1~§2 | 완료 | E-4·E-5·E-6·E-7a·E-7b |
| **C-3** | 같은 파일 §3 | 완료 | E-8·E-9·E-10·E-14 |
| **C-4** | 같은 파일 §4 | 완료 | E-15 |
| **C-5** | 같은 파일 §5 | 완료 | E-11·E-12 |
| **C-9** | 이 파일 아래 절 | 완료 | — |
| **G-4** | `.github/workflows/ci.yml` `container` job S-23b | 완료 | E-13 + container job 실측 |

## `OPEN-5E-POLICY-VALUES` 처분 (C-9 · 운영자 결정 E-3 (a))

**판정: 6E 에서 실측 불가 → 잠정 유지, 재승인 조건을 M7 운영 반입 실측으로 이전한다.**

정본은 `reports/evidence/m5/5e/policy-values.md` §1 이고 값은 일곱이다. 잠정 승인(2026-09-16)의 재승인
조건이 「M6 6C/6E 실측」이었다 — 6C 는 지났고 6E 가 마지막 자리였으나, **이 slice 에서 그 일곱 가운데
어느 것도 실측할 수 없다.**

| 값 | 재승인에 필요한 측정 | 6E 에서 불가한 이유 |
|---|---|---|
| `max_workers` | 판정 RPC 동시 처리량 아래의 스레드 풀 포화 | serving 에 **부하를 걸 운영 트래픽이 없다**. 오늘 호출자는 test·CI 스모크뿐이고 동시성 1 이다 |
| `max_concurrent_rpcs` | `RESOURCE_EXHAUSTED` 가 처음 나는 동시 요청 수 | 같은 이유 — 큐잉 한계를 보려면 동시 요청을 만들어야 한다 |
| `shutdown_grace_seconds` | 종료 신호 뒤 진행 중 RPC 가 실제로 끝나는 시간 분포 | 진행 중 RPC 가 없는 환경에서는 유계 10s 가 참인지 거짓인지 갈리지 않는다 |
| `job_workers` | 동시 학습 job 의 코어 경합 | **학습 job 을 운영에서 돌린 적이 없다** |
| `idempotency_key_max_chars` | 실제 키 길이 분포 | 운영 키가 없다(발행자가 V2 test 뿐) |
| `embedding_text_max_chars` | 실 공고 텍스트 길이 분포에서의 절단율 | 6G 실수집 표본은 **백테스트용**이고 임베딩 절단율을 재는 산출이 아니다 |
| `dataset_uri_schemes` | object storage 채택 여부 | `OPEN-2C-DATASET-URI-SCHEME` 미결 — **결정이 선행**한다 |

**이전되는 재승인 조건**: 「운영 반입 뒤 **실 트래픽**에서 ① 동시 RPC 분포 ② 종료 시 진행 중 RPC 지속
시간 ③ 임베딩 입력 길이 분포를 측정하고, 그 분포가 위 값들의 보수성을 뒤집으면 값을 고친다」. 소유는
**M7**(운영 반입)이고 6E 는 **값을 바꾸지 않는다** — 측정 없이 숫자를 고치는 것이 지금 가장 나쁜 선택이다.

**근거 축 하나는 6E 가 닫았다** — `shutdown_grace_seconds` 가 파생된 4D-1 `deadlineCeiling=5s` 와의
2배 관계는 코드에서 여전히 성립한다(값 변경 0). 그 파생 관계가 깨졌는지만 이 slice 가 확인했다.

## 알려진 제한

### 문서가 약속하지 않는 것 (runbook §6 의 요약 — 여기서는 **왜 못 하는가**)

1. **SLO·임계값을 쓰지 않았다.** `OPEN-OPS-03`·`OPEN-OPS-04` 가 미결이고, 숫자를 발명하면 승인되지
   않은 값이 운영 문서를 통해 기대값으로 승격된다(설계 검토 (3) 「과잉」).
2. **metric 계측·구조화 로그·trace·대시보드를 만들지 않았다.** 6E-2 이고, 특히 `metrics` 노출은
   `MANAGEMENT_SURFACE_LOCK` 의 술어를 바꾸는 변경이라 설계 검토가 선행한다.
3. **커넥션 풀을 도입하지 않았다**(`OPEN-6A1-CONNECTION-POOL`). runbook 은 그 부재를 **경고로** 적었다.
4. **model rollback 의 운영자 경로를 만들지 않았다.** runbook §4.2 는 「경로가 없다」를 사실로 적고
   오늘 가능한 유일한 수단(승격 되돌리기)만 절차로 남겼다. 선택자 주입은 범위 밖이다.
5. **live read probe 를 실행하지 않았다.** runbook §5.4 는 절차만이고 실행은 사용자 승인 대상이다.
   실 KONEPS·LLM·발송 호출 **0**.
6. **`capability-map.md`·`regression-ledger.md` 를 고치지 않았다**(읽기만). C-1 이 드러낸 미구현은
   분류 변경 사유가 아니다 — 분류는 「V2 범위」이고 C-1 은 「측정 상태」다. 두 축을 섞지 않았다.

### C-1 의 판정이 틀릴 수 있는 자리 (verifier 표적 후보)

1. **ⓐ 29 건의 「무조건 항목 전부」 판정은 사람의 독해다.** 기계 대조는 ⓐ 의 **식별자 존재**만 잰다 —
   그 test 가 **그 bullet 을** 재는지는 재지 못한다. 표본 대조가 필요하고, 가장 위험한 행은
   `ML-02`·`ML-04`·`ML-05`·`ML-07`·`SET-06` 다섯이다(식별자가 CI step 하나이고 재는 모듈 경로는
   본문에만 있다).
2. **ⓑ 2 건이 가장 센 우회 경로다.** `COL-05`(사업 집계 개체 부재)·`NOTI-07`(인바운드 webhook 표면
   부재)은 「없으니 만족한다」 꼴이다. 둘 다 부재를 잠그는 자리를 적었으나(식별자 타입 · 표면 전수
   게이트), 「그 부재가 요구를 만족시킨다」는 판단 자체는 독해다.
3. **ⓒ 30 건 가운데 「부분 측정」 서술이 실제보다 후하게 적혔을 수 있다.** 특히 `OPS-03`·`OPS-05` 는
   부분 측정이 매우 강해 ⓒ 로 두는 것이 과한지, 아니면 비어 있는 항목이 결정적인지 판단이 갈릴 수 있다.
4. **`QUAL-02` 는 production 분기와 test 의 틈을 보고한 행이다** — 요건 미달이 아닌 `Uncertain` 이
   게이트를 통과하는 분기가 production 에 있으나 그 분기를 고정하는 test 가 없다. 이 slice 는 test 를
   더하지 않았다(코드 축은 6E-2·후속). **구현 레인의 자진 보고**로 둔다.
5. **`COL-04` 를 ⓒ 로 내린 판단**(수집 대상 정렬 부재)은 다른 ⓐ 행들보다 엄격한 적용일 수 있다.
   같은 기준을 ⓐ 29 건에 다시 대면 몇 건이 더 내려갈 수 있다 — 그 재적용이 verifier 의 가장 값싼 표적이다.

### G-4 의 제한

1. **dry-run 이 전략을 바꾼다**(여력 상한 `MAX_ACTIVE_BIDS=5` 를 세운다). dry-run 자체는 쓰기가 없으나
   **그 전제를 만드는 step 이 쓰기**이므로, S-23b 뒤의 DB 상태는 앞 판과 다르다(`revision` 이 1 더 오르고
   `max_active_bids` 가 채워진다). 뒤따르는 S-23c 는 `operator_strategy` 를 **읽어** 비교하므로 영향이
   없다(값을 하드코딩하지 않는다) — 그래도 S-23c 의 비교 대상 문면이 `candidate_limit/revision` 이라는
   사실은 바뀌지 않았다.
2. **outbox 행 수 등식은 「전후가 같다」만 잰다.** dry-run 이 outbox 에 쓰고 같은 수를 지웠다면 통과한다
   — 그 경로는 권한으로 닫혀 있다(E-14: 애플리케이션 역할에 outbox DELETE 권한이 없다)가, 등식 자체가
   그것을 재지는 않는다.
3. **candidateCount 가 0 인 환경에서 돈다.** compose DB 에 공고 행이 없으므로 dry-run 은 빈 분포를
   낸다 — 「판정이 실제로 돌았다」를 재는 것이 아니라 「endpoint 가 인증 뒤 200 을 내고 outbox 를
   건드리지 않는다」를 잰다. 판정 내용 축은 Kotlin E2E(`EvaluationDryRunE2ETest` 계열)가 진다.

### 6E-2 로 넘기는 넷 (문면만 등재 — 이 slice 는 착수하지 않았다)

| ID | 무엇 | 선행 결정 |
|---|---|---|
| **G-1** | SBOM 생성 + CVE 스캔 + 차단 정책(`OPEN-6C-IMAGE-VULN-SCAN`, 완료 조건 8 나머지 절반) | 도구 채택 |
| **G-2** | 커넥션 풀 도입(`OPEN-6A1-CONNECTION-POOL`, 「운영 반입 가능」의 전제) | 범위·위험 승인 — 기동 실패가 즉시화되고 호환 표면이 늘어난다 |
| **G-3** | production metric 계측(ADR 0005 D-7·D-8, `OPEN-OPS-03` 임계) | 임계 결정 + `MANAGEMENT_SURFACE_LOCK` 술어 변경 설계 검토 |
| **G-7** | 판정·투찰 기록 표(`OPEN-6F3-BID-RECORD`) | 「투찰 사실을 무엇으로 아는가」 — 마이그레이션이므로 되돌리기 어려운 경로 |

G-5(자격증명 원문 경계, `OPEN-6A1-CREDENTIAL-RAW-REINTRODUCTION`)·G-6(멀티아키, `OPEN-6C-MULTIARCH`)는
범위·배포 대상 결정 뒤다.

## OPEN 에스컬레이션 — 홀더 없는 미구현 10

C-1 이 드러낸 자리다. `V2 필수`인데 acceptance 의 무조건 항목이 비어 있고, 그 사실을 들고 있는
OPEN·후속 ID 가 `capability-map.md` §12/§14 에도 `milestone-6.md` OPEN 목록에도 **없다**.
**이 slice 는 OPEN 을 신설하지 않고 보고한다**(명세에 없는 것을 구현하지 않는다 — 신설은 계약 갱신 몫).

| capability | 비어 있는 것 |
|---|---|
| `STR-06` | 후보 표시 표면 · 표시 건수와 분석 예산의 독립 |
| `STR-07` | 후보 스냅숏 · `stale` 표시 · 재계산 디스패치 |
| `STR-10` | 재계산 요청 표면 · 진행/실패 상태 구분 · 고아 running 회수 |
| `STR-16` | 입찰 목록 검색 operation · 금액 범위 검색의 basis 선언 |
| `QUAL-08` | 구조화 지역제한 필드로 판정하는 자격 축 |
| `ML-08` | 아티팩트 서명(생성·검증) |
| `DEC-05` | 투찰가 메뉴 타입 — (태도, 리스크 노트, 근거) 세 칸 |
| `DEC-10` | `floor_implausible` 계수 · 게이트 걸린 값의 타입 분리 |
| `OPS-08` | 발송 예산 층 · 외부 호출 간 최소 간격 |
| `OPS-13` | 복잡도 래칫 · 실행 환경 스니핑 금지 게이트 |

**닫힌 OPEN 을 홀더로 쓰지 않았다** — `OPEN-1BC-STR16`(M1/1E 로 닫힘)·`OPEN-6F2-CANDIDATE-BOUND`(닫힘)은
`STR-16`·`STR-06` 과 주제가 인접하지만 **그 미구현을 들고 있지 않다**.

## 자기 점검

| 항목 | 결과 |
|---|---|
| production·migration·docker·build·`config/quality` diff | **0** (`git diff --name-only 80dc33b3..HEAD` 가 네 경로를 내지 않는다) |
| `capability-map.md`·`regression-ledger.md` diff | **0** (읽기만) |
| 비밀값 스캔(참조형) | `grep -rniE -f config/quality/leak-patterns.txt` → 이 slice 가 만든 파일에서 매치 **0** |
| 호스트 사용자 경로 원문 | 없음(`$HOME`·`<...>` 로만 적었다) |
| 실 KONEPS·LLM·발송 호출 | **0** |
| differential / golden | **N/A** — 문서 + CI step slice 라 산출 데이터가 없다(fixture 변경 0, 골든 변경 0) |
