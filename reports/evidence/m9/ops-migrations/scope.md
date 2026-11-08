# M9 / ops-migrations — 정책 version 저장 · outbox 실패 사유 · 실행 행 릴리스 식별자
- base: main `df4976cc` · 공통 규율: `/tmp/claude-1000/-home-deploy-project-bid-vector-v2/b843a5c1-93ae-42d9-82cf-0a5222fc9820/scratchpad/m9-lane-rules.md`
- 사용자 지시 2026-11-06: 「남은 묶음도 지금 같이 병렬로」(2026-11-05 「남은 항목이 없도록 전체 병렬」의 연속). 결정 지점은 추천안으로 진행(전역 규칙), 외부 비용·운영 결정이 필요한 축은 만들지 않고 등재.
- rollback: 공통 규율대로(원천 `df4976cc`, 내 커밋만, 결과 트리 동일성). 하네스 레인 변경: 계약이 명시한 것 외 없음.
- **DB 마이그레이션 slice** — 착수 번호는 V26·V27·V28 이었고 **갱신으로 V26·V27 둘**이 됐다(아래 갱신 절, V25 는 paper-bid 레인 몫). 리뷰는 verifier + code-reviewer + migration 관점. Codex 는 운영자 요청 시에만.

## 대상
| capability | 처방 |
| --- | --- |
| ~~**DEC-08**~~ **제외**(갱신 ①) 분류 정책 version 저장 | `ProvenanceEvidence.policyVersion` 이 메모리에만 — 결과 행에 정책 version 칸(V26) + production 호출부가 근거를 버리지 않게 |
| **OPS-05** outbox 실패 사유 | outbox 에 종단 사유 칸(닫힌 어휘, V26) — 「왜 ISOLATED/FAILED 인가」를 DB 로 답한다. 시간 기반 janitor 는 등재만(임대 기반 회수가 현행 설계) |
| **OPS-10** 실행 행 릴리스 식별자 | `collection_run`·`notice`·`opening_result`·`outbox` 에 릴리스 칸(V27) + 수집 설정의 기본값 `unversioned` 제거(기본값 없음 → 기동 실패 관례) |

## in_scope
`adapters/src/main/resources/db/migration/V26__*.sql`·`V27__*.sql` · 관련 adapters·workflow·app 코드와 test · GRANT·privacy 등재 test · `config/quality/**` · `docs/discovery/data-dictionary.md` 해당 절 · capability map 행 · runbook(릴리스 식별자 주입 — 운영 compose 가 이미 넘기는지 확인) · evidence
## acceptance
각 마이그레이션 clean-migrate · CHECK·GRANT 등식 · 되돌림 리허설 두 갈래(미적용=파일 삭제 / 적용=새 V) · 기본값 제거로 깨지는 배선(운영 compose·dev compose·test) 전수 갱신 · 변이 RED. 전건 PR CI.

## 계약 갱신 2026-11-06 (팀장)

- **DEC-08 을 이 slice 에서 뺀다(①).** 출하 조립의 `MlAnalysisPort` 는 `UnavailableMlAnalysis`
  고정이고 의존 게이트가 그 집합을 잠근다 — 분류가 출하에서 **0 회** 돈다. e2e 조립만 쓰는 표를
  마이그레이션으로 들이면 「칸만 만들고 아무도 안 쓴다」의 변형이고, 남는 것은 되돌리기 어려운
  스키마다. ML 출하 배선은 사용자가 보류한 결정이라 이 slice 가 열 수 없다. 받는 자리는
  `OPEN-ML-ANALYSIS-WIRING` — capability map DEC-08 행이 그 포인터를 적는다.
- **번호를 당긴다**: OPS-05 = **V26** · OPS-10 = **V27**(V25 는 paper-bid 레인). 마이그레이션 결번
  0 게이트(gates-build 레인)가 서는 중이라 번호 구멍을 남기지 않는다.
- **래칫은 증분으로 적는다** — paper-bid 레인의 V25 가 같은 `CleanMigration*Test` 의 다른 행을
  바꾼다. 기준선 대비 증분을 주석에 남겨 병합이 계수로 합쳐지게 한다.
- **레인 간 겹침 셋**(이 slice 는 셋보다 **나중에** 머지한다 — 컴파일 강제가 남의 새 코드를 잡게):
  ① `EvaluationCommitRun`(ops-code 레인의 알림함 생산자 포트) — 주입 자리를 분리 ② `OutboxCommand`
  종단 생성자(PR #127 Telegram 의 relay·sender) — 그쪽 dispatch 결과 → 종단 전이 자리가 사유 인자를
  받아야 한다 ③ `release-sha` 키 제거(PR #128 의 `AwardCollectionProperties` 에 release 키가 있는지
  병합 때 확인) · 운영 env 키 등식(`prod-env-keys-check`)은 표적 확인.

## 계약 갱신 2026-11-06 (팀장, 재전달분 반영)

- 위 절의 ①(DEC-08 제외)·번호 당김은 **`9efabe7a` 에서 이미 반영됐다**(역적용 커밋 `58cb4c26` ·
  번호 커밋 `8e31b260` · 삭제 반쪽 보정 `41358ce1`). 데이터 사전은 DEC-08 절을 새로 들이지 않았고
  §4.2 행만 「저장 자리 미정」으로 돌렸다. `OPEN-M9-CLASSIFICATION-RELEASE` 는 **걷었다**(표가 없으면
  그 질문도 받는 slice 가 저장 자리와 같은 자리에서 정한다).
- **판단 둘 승인됨**: V27 을 표 둘(`collection_run`·`outbox`)로 한정(조인 경로 근거 +
  `ExecutionRowReleaseTest`) · 설정 키 `bidvector.release.sha` 통일.
- **in_scope 밖 다섯 승인됨** — 전부 「기본값 제거로 깨지는 배선」이다:
  | 파일 | 무엇을 |
  | --- | --- |
  | `docker/compose.yaml` | app 서비스가 `BIDVECTOR_RELEASE_SHA` 를 받는다(값은 `BIDVECTOR_RELEASE_TAG`) — 운영 compose 는 이 파일을 상속하므로 `compose.prod.yaml` 편집 0 |
  | `docker/prod.env.template` | `BIDVECTOR_RELEASE_TAG` 안내 문구에 「두 자리에 쓰인다」 — **키 집합 불변**(등식 검사 exit 0, 키 18) |
  | `tools/local-tls-env.sh` | 로컬 기본값 = `git rev-parse --short HEAD` |
  | `.github/workflows/ci.yml` | compose 스모크 job env 하나 + 거부 스모크 `-e` 하나 |
  | `web/e2e/harness/journey-up.mjs` | `COMMON_PROPERTIES` 한 줄 — 그 하네스가 띄우는 jar 기동 셋(collect·account·server)을 덮는다. **수정 라운드 4 에서 이 표에 올렸다**(앞 판은 아래 머지 순서 문장에만 있어 승인 표에 빠져 있었다 — v L-2) |
- **적용 DB 되돌림 리허설: 로컬 Testcontainers 1회 허용**(버릴 Postgres · `bv-build` · 실행 전
  `free -m`). 두 갈래(새 V26·V27)의 실측은 `rollback.md` §5 가 적는다.

## 계약 갱신 2026-11-07 (팀장 지시 — 마지막 라운드)

- **사용자가 재작업 상한을 넘어 한 라운드를 승인했다**(2026-11-07). 다음 표적 재검증이 not-ready 면
  머지하지 않고 사용자에게 돌아간다.
- **잠금 상한을 migrate 연결에 건다**(v r6 V-1). `app/.../PersistenceWiring.kt` 는 in_scope 문면
  「관련 … app 코드와 test」 안이고, 새 test `MigrationLockBoundTest` 와 그 게이트 등재도 같다.
- **`docs/runbook/m6-6b3-retention.md` §4 ⑥ 한 줄은 팀장이 지시한 자리다**(cr r3 L-2). 그 파일은
  이미 이 slice 의 경로다(라운드 1 이 보존 cron 래퍼에 릴리스 키를 넣었다). 다만 **이번 줄의 축은
  릴리스 식별자 주입이 아니라 잠금 실패 처분**이라 in_scope 문면(「runbook(릴리스 식별자 주입 …)」)
  보다 넓다 — cr 이 「범위를 넘는다고 보면 등재가 맞다」고 적은 자리이고, 팀장 지시로 고쳤으므로
  여기 등재한다.

## 계약 갱신 2026-11-08 (팀장 지시 — 승인 전 일괄)

- **`docs/runbook/m6-6b2-backup-restore.md` 한 줄은 in_scope 문면보다 넓다.** 그 줄의 축은 릴리스
  식별자 주입이 아니라 **Flyway 구성 전제**(「`PersistenceWiring.migrate()` 가 전부 기본값으로
  부른다」)이고, 이 slice 의 `initSql` 이 그 문장을 거짓으로 만들었다 — cr r4 가 「이 변경이 거짓으로
  만든 유일한 문장」으로 지목했고 팀장 지시로 고쳤으므로 여기 등재한다(`m6-6b3-retention.md` §4 ⑥
  과 같은 처분).
- **main `3101035f` 병합**(팀장 지시, #142 CVE · #127 STR12 telegram · #136 pitest). **내 병합은
  둘**이 된다. 충돌 일곱은 전부 합집합으로 해소했고(relay 조립의 `release` ↔ `channels`·`clock`),
  `EXPECTED_EXEMPT_COUNT` 는 양쪽 수를 버리고 tier 목록에서 기계로 다시 셌다. **충돌 0 으로 들어와
  접힌 자리 셋**(main 의 새 `NotificationRelayRun(` 호출이 `release` 없이 조립)도 함께 고쳤다 —
  그 셋은 표지가 서지 않아 컴파일만이 잡는다.
- **lint 술어는 바꾸지 않았다**(팀장 지시). cr r4 M-1 의 「값 등식」 처방은 게이트 술어 변경이라
  재검증을 부르므로 `commands.md` 알려진 제한으로 등재했다.

## 계약 갱신 2026-11-06 (verifier not-ready 반영)

- **승격된 행의 릴리스는 조인이 다른 질문에 답한다**(v M-1). `observation_key` 조인이 주는 것은
  「그 **원문을 수집한** 릴리스」이고, `opening_result`·`opening_reserve_price` 는 **승격 러너**
  (별 프로세스·다른 릴리스일 수 있다)가 쓰므로 「이 canonical 행을 **쓴** 릴리스」가 아니다.
  `notice` 는 수집 run 이 원문과 canonical 을 **같은 run** 에 쓰므로 조인이 정확하다.
  `qualification_text` 는 출하 쓰기 경로가 아직 없다. 칸 추가는 이번에 **하지 않는다** —
  승격 경로의 `UPDATE` 가 V2 의 판 번호·감사 트리거를 깨우는 비용이 그대로이기 때문이고,
  그 결정과 범위는 `OPEN-M9-PROMOTED-ROW-RELEASE` 가 받는다.
- **머지 순서**: **V25(paper-bid) → 이 slice** 가 먼저고(팀장이 순서를 지킨다), 그 뒤로 ops-code ·
  PR #127(Telegram) · PR #128(낙찰 수집) 보다 **나중**이다 — 컴파일 강제가 남의 새 코드를 잡게.
  PR #135(개찰 하네스)는 `web/e2e/harness/journey-up.mjs` 를 크게 바꾸므로 **나중에 머지되는
  쪽이 합친다**(이 slice 는 그 파일에 한 줄만 더한다).
- **그 순서대로 됐다**(라운드 6, 2026-11-06): paper-bid #137 이 머지돼 main 이 `56bd33ea` 가 된 뒤
  이 브랜치가 그 SHA 를 합쳤다. #135 의 `journey-up.mjs` 도 이쪽이 합쳤다(main 판 위에 내 네 줄).
  **컴파일 강제가 실제로 남의 새 코드를 잡았다** — main 의 `KonepsRollbackArgumentsTest` 가 릴리스
  빈 없이 배선을 띄워 전건 둘이 붉었고, 배선을 바꾼 내 쪽이 고쳤다. rollback 복원 원천도 그
  병합 SHA 로 다시 고정했다.
