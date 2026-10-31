# M7/7C③-b — 알림 트리거 배선 (착수 계약, 2026-10-31, 팀장)

- **근거**: 운영자 지시 2026-10-31 「알림 트리거 배선 slice 도 이어서 착수해」. M7/7E-3a 착수 실측(D-7E3a-6): 출하 조립에 `NotificationInboxPort`
  쓰기 빈이 없고 `NotificationFact` 를 짓는 production 코드가 0 이라 알림함이 늘 빈 얼굴이다(`OPEN-7E3A-NOTIFICATION-TRIGGER-WIRING`).
  7C③ 계약(「트리거는 원천 slice 가 이 포트를 부르는 것으로 닫는다 — 이 slice 는 어휘와 키 규칙만 선언」)이 남긴 절반이다. `milestone-7.md`
  완료 조건 G2 — NOTI-10 · NOTI-04 · STR-08 「표면·기록이 닫힌다」에 걸린다.
- **base**: main **`9463f6e1`** · worktree `bid-vector-v2-m7-7c3b` · 브랜치 `m7-7c3b/2026-10-31`. rollback 복원 기준 `9463f6e1`.
- **입력 정본**: 7C③ 계약 `reports/evidence/m7/7c3/scope.md`(포트 · 종류 어휘 · 중복 억제 키 규칙 · R-4) · 요구표 「알림함」 트리거 여섯 ·
  capability map NOTI-10 · NOTI-04 · STR-08 · BID-01 · SET-02 · COL-01 · 7B 계약(입찰 건 상태 기계 · 다음 확인 시점) · 7C⑤-0/-a(개찰 승격) ·
  7C② 계약(투찰가 요청의 `inboxRecordState`·`INBOX_WRITE_PORT_NOT_MERGED`) · 7E-3a 계약 D-7E3a-6.
- **레인**: 구현 하나(`kotlin-implementer`). 판정 `verifier`(opus) + `code-reviewer` 병렬. **마이그레이션이 생기면** 되돌리기 어려운 경로 →
  `migration-reviewer` 관점을 verifier 표적에 더하고, Codex 는 운영자가 범위·비용을 승인할 때만(프로젝트 CLAUDE.md). 마이그레이션 0 을 목표로 한다.

## 팀장 결정 (추천안 — 착수 보고 실측이 반박하면 먼저 묻는다)

| ID | 결정 |
| --- | --- |
| **D-7C3B-1 원천이 있는 것만** | 알림 종류 어휘(OpenAPI `kind`) 각각에 대해 **main 에 원천이 있는가**를 착수 보고에서 실측 표로 낸다(원천 위치 · 발화 시점 · 중복 억제 키 · 계정 주체를 어디서 얻는가). 원천이 있는 것만 배선한다. 원천이 없는 것(예: 감시 실행 이력 없이 「새 후보」를 지어내기, 상호 수집 없이 낙찰/패찰 대사)은 **흉내 내지 않고** OPEN 으로 남긴다(가짜 원천 금지). |
| **D-7C3B-2 쓰기 빈 하나** | 출하 조립에 `NotificationInboxPort` 쓰기 빈을 **한 자리**에 세우고 원천 slice 들의 조립이 그것을 받는다. 쓰기 포트는 HTTP 표면에 없다(7C③ 아키텍처 게이트 그대로). 투찰가 요청의 `inboxRecordState` 가 `NOT_RECORDED_YET`·`INBOX_WRITE_PORT_NOT_MERGED` 를 내던 자리는 기록 결과를 정직하게 내도록 바꾼다(계약 enum 문면 갱신 — OpenAPI 편집은 이 자리만). |
| **D-7C3B-3 시간 기반 트리거** | 마감 D-1/D-0 · 서류 기한 D-2 처럼 **시각이 지나서** 생기는 트리거는 발화 장치가 필요하다. 착수 보고에서 기존 장치(마감 정산 `DeadlineSettlement` 의 읽기 시 정산 · 수집 러너 · cron) 중 무엇에 얹을지 실측해 추천하라. 새 상주 스케줄러를 들이는 것은 범위를 바꾸는 결정이라 먼저 묻는다. 「읽을 때 기록」은 읽기가 쓰기를 낳는 자리라 그 사실을 계약에 드러낸다. |
| **D-7C3B-4 멱등 · 경합** | 같은 사실이 두 번 발화해도 항목 하나(NOTI-10 키 — 7C③ 의 upsert). 원천 트랜잭션과 알림 기록의 원자성(같은 트랜잭션인가, 실패 시 원천을 되돌리는가 · 알림만 잃는가)을 착수 보고에서 정하고 test 로 잠근다. 알림 기록 실패가 원천 쓰기(투찰 기록·결과 기록)를 실패시키지 않게 하는 것이 기본 추천 — 그 경우 잃는 사실을 로그/표지로 남긴다. |
| **D-7C3B-5 개인정보** | 알림 본문은 구조 필드만(사업자번호·담당자·연락처·다른 업체 상호 0 — 7C③ 그대로). |
| **D-7C3B-6 e2e** | 원천이 하네스에서 발화하는 트리거가 생기면 7E-3a 여정 ⑤ 의 「알림함 빈 얼굴」 단언이 바뀐다 — 이 slice 는 **Kotlin 과 계약**까지이고, 여정의 기대 변화는 착수 보고에 적어 7E-3b 와 어느 쪽이 고칠지 팀장이 정한다(같은 파일을 두 레인이 만지지 않는다). |

## 설계 검토 (Phase 2.5 — 착수 보고, 구현 전)

- (0) 방어: 가짜 원천으로 알림을 지어냄 · 다른 계정에 기록 · 중복 증식 · 알림 실패가 원천 쓰기를 깸 · 읽기가 몰래 쓰기를 낳음 · 쓰기 포트 HTTP 노출 · PII.
  방어 안 함: 원천 판정의 옳고 그름 · 채널 발송(R-4).
- (1) 열거인가 구성인가(종류 × 원천 표는 어휘에서 파생, 소진) · (2) 우회 ≥5 · (2b) 새 public 표면 전수 · (3) 과잉·미달.

## in_scope

- 원천 slice 들의 workflow use case 에 알림 기록 호출을 더하는 자리(`workflow/**`) · 쓰기 어댑터 배선(`app/**/wiring/**`) · 필요한 어댑터(`adapters/**`) ·
  해당 test · `openapi/bidvector-operator-api.yaml` 의 `inboxRecordState` 계열 문면 · 생성 TS 재생성 · `config/quality/*`(기계적 귀결, 근거 동반) ·
  capability map NOTI-10·NOTI-04·STR-08 행 · 요구표 알림함 행 · `reports/evidence/m7/7c3b/**`.

## out_scope

- 채널 발송 · 새 원천(감시 실행 이력 STR-08 의 신설 · 낙찰자 상호 수집) · 새 상주 스케줄러(D-7C3B-3 이 먼저 묻는다) · `web/**` 화면 코드 · 7F.

## acceptance

- `ci.yml` Kotlin `check` + `qualityBaseline` · `web` job 전체(OpenAPI·생성 타입이 바뀌면). 마이그레이션이 생기면 `container` job 도.

## rollback

- in_scope 경로 한정 `git restore --source=9463f6e1 --staged --worktree --` + 새 파일 삭제, 목록 기계 산출, 임시 clone ①~⑥, `실측 HEAD` 기록.
  main 을 병합하면 기준은 병합된 main 고정 SHA 하나(7C⑤-b r1 교훈 — 기준 둘은 둘 다 바꾼 파일을 빠뜨린다).

## 하네스 레인 변경

- 없음.

## 계약 갱신 이력

- 2026-10-31 착수(팀장) · 운영자 지시 2026-10-31.
