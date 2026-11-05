# M9 / inbox-vocab — 유찰 알림 어휘 · 개정 재안읽음 (OPEN-7C3B-FAILED-BID-DETAIL · OPEN-7C3B-REVISION-REUNREAD)

- base: main `79f3e5c4` · 브랜치 `feature/m9-inbox-vocab` · worktree `bid-vector-v2-m9-inbox-vocab`
- 근거: `reports/evidence/m7/7c3b/scope.md` D-7C3B-7 ⒝ · `7c3b/checklist.md` 알려진 제한 9·13 · M7 종결 §4.
- 사용자 지시 2026-11-05(전체 진행, 추천안). **DB 마이그레이션 slice** — 리뷰는 verifier + code-reviewer + migration-reviewer 게이트. Codex 는 운영자가 따로 요청할 때만.

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | **유찰**: `AWARD_DECIDED` 종류의 detail 어휘에 `FAILED_OR_REBID` 를 더한다. `NotificationFact.AwardDecided(awarded: Boolean)` 을 닫힌 어휘(`AwardOutcome` 같은 sealed/enum: Awarded · NotAwarded · FailedOrRebid)로 바꾼다. `BidCaseNotifications` 의 `FailedOrRebid` 갈래가 항목을 기록한다. **`NOT_AWARDED` 로 접지 않는다.** |
| D-2 | 마이그레이션 **V24** 한 파일: `notification_inbox` 의 detail CHECK 와 종류↔detail 짝 CHECK 를 재선언(제약 이름 유지 또는 명시적 DROP/ADD). 열·PK·GRANT 변경 0. 되돌림 리허설 두 갈래(미적용 DB = 파일 삭제 / 적용 DB = 새 V 로 원복 — 기존 `FAILED_OR_REBID` 행 처분 포함)를 evidence 에. |
| D-3 | **개정 재안읽음**: 길 ⒝ — 읽음 해제 규칙을 **종류별 정책**으로 가른다. `NOTICE_REVISED` 는 같은 키 재기록이면 `read_at` 을 NULL 로(새 개정 = 새 소식). 다른 종류는 지금 규칙(detail 이 바뀔 때만) 유지. 정책은 Kotlin 의 종류 전수 표(`when` 완전성)에서 SQL 로 내려가게 — SQL 에 종류 문자열을 손으로 늘어놓지 않는다(구조로). 마이그레이션 0. |
| D-4 | openapi `detailCode` enum 에 값 추가 → `web/src/api/generated/operator-api.ts` 재생 → `web/src/display/notificationDisplay.ts` 의 DETAIL 표에 문면(「유찰·재입찰」). 화면 파일(`InboxScreen.tsx`)은 건드리지 않는다(다른 레인 소유) — 표만. |

## in_scope

- `adapters/src/main/resources/db/migration/V24__*.sql`
- `workflow/src/main/kotlin/bidvector/workflow/notificationinbox/**` · `workflow/src/main/kotlin/bidvector/workflow/bidding/BidCaseNotifications.kt` 와 그 test
- `adapters/src/main/kotlin/bidvector/adapters/notificationinbox/**` 와 그 test
- `openapi/bidvector-operator-api.yaml` (detailCode 절만) · `web/src/api/generated/**` · `web/src/display/notificationDisplay.ts`(+ test)
- `config/quality/**` 게이트 등재 · `docs/discovery/data-dictionary.md`(알림함 어휘 절) · `reports/evidence/m9/inbox-vocab/**`

## out_scope

PK·열·GRANT 변경 · 페이퍼 투찰 알림(`OPEN-7C3B-PAPER-BID-NOTIFICATION` — 이 slice 머지 뒤 별도) · 화면 파일 · 발송 채널.

## acceptance

- CI Kotlin `check` job · `web` job 명령 그대로 exit 0 (`.github/workflows/ci.yml`).
- 실 DB test(Testcontainers): `FAILED_OR_REBID` 전이 → 항목 1 + 새 detail · CHECK 어휘 ↔ Kotlin 어휘 집합 등식 · 읽은 `NOTICE_REVISED` 재기록 → `read_at` NULL + 안읽음 수 증가 · 다른 종류 전수에서 단순 재기록은 읽음 유지.
- 변이: 유찰→`NOT_AWARDED` 매핑 · 읽음 해제 `CASE` 원판 복귀 · V24 CHECK 에서 새 값 제거 — 각각 RED.

## rollback

머지 전: in_scope 경로 한정 `git restore --source=79f3e5c4 …` + V24 삭제(미적용 DB). 적용 DB 는 D-2 리허설의 새 V. 목록 기계 산출(`rollback.md`).

## 하네스 레인 변경

없음.

## 계약 갱신 2026-11-05 (팀장)

- **D-3′ 승인** — D-3 문면(「같은 키 재기록이면 해제」)은 개정 알림의 원천인 훑기가 같은 개정을 run 마다 다시 기록하므로 배지를 영영 끌 수 없게 만든다(레인 preflight 실측: 사실 시각 = `notice.updated_at`, V2 트리거가 `revision`·`updated_at` 을 함께 올림). 정책 갈래는 `ON_DETAIL_CHANGE`(기본) · `ON_NEWER_FACT`(`NOTICE_REVISED` — `EXCLUDED.occurred_at > notification_inbox.occurred_at` 일 때 해제).
- acceptance 셋째 줄 대체: 「읽은 `NOTICE_REVISED` 에 **더 늦은 사실 시각**의 재기록 → `read_at` NULL + 안읽음 수 증가, **같은 사실 시각**의 재기록 → 읽음 유지(음성 대조)」.
- `docs/discovery/data-dictionary.md` 에 알림함 어휘 절이 없어 손대지 않은 것은 정당(in_scope 의 「알림함 어휘 절」은 없는 절). OPEN 두 건의 종결 등재(`reports/evidence/m7/**`·capability map)는 M9 장부 slice 가 받는다.
