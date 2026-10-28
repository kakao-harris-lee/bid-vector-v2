# M7/7C③ — 알림함 새 표 · NOTI-10 중복 억제 · 읽음/안읽음 · 건별 묶음 (착수 계약, 2026-10-23, 팀장)

- **근거**: `milestone-7.md` 7C ③(「알림함 새 표(NOTI-10 중복 억제 키·읽음/안읽음·건별 묶음 — 기존 `inbox` 표는 이벤트 소비 멱등
  표(ADR 0005 D-3)라 재사용하지 않는다)」) · R-4(외부 effect 는 알림함 기록까지, 채널 발송 없음) · R-2(사용자 소유 표에 `account_id`).
  운영자 지시 2026-10-23 「7C②·③·⑤ 도 지금 레인 열어서 병렬로 진행」 — `milestone-7.md` 선행 조건(M6 완료 조건 성립)의 **운영자 예외**
  (7C①·7C④ 와 같은 종류).
- **base**: main `0768bda1` · worktree `bid-vector-v2-m7-7c3` · 브랜치 `m7-7c3/2026-10-23`.
  **착수 base 는 `a61ddff1` 이었고 7B 머지(PR #89) 뒤 `46ac0949` 로 재고정했다**(팀장 2026-10-26 ·
  D-7C3-1 ①). 옮긴 방식은 `git merge origin/main`(rebase 가 아니다 — 17 ahead / 198 behind 라
  rebase 는 같은 충돌을 커밋마다 재생한다). 충돌 9 를 전부 합집합으로 해소했다.
- **병렬 레인**(전부 push 0): 7B(V21, `AccountScopedRoutes`) · 7C④(V22) · 7C② · 7C⑤ · 7C①b · g1c03 · g1set01 · g1dec05.
  무거운 빌드는 `flock ~/.local/bid-vector-build.lock` 직렬.
- **입력 정본**: `docs/discovery/m7-screen-data-requirements.md` 「알림함」 절(항목 목록·읽음·묶음·중복 억제 키·트리거 여섯·클릭 → 워크시트) ·
  capability map / 추적표(`reports/evidence/m6/6e1/acceptance-trace.md`)의 NOTI-10 · NOTI-04 · STR-08 행 · ADR 0005 D-3(기존 `inbox`
  의 뜻) · `bidvector.workflow.evaluation.OutboxNotificationRequestPortTest`(멱등 키가 `noticeId` 로 고정되는 기존 측정) · 7D 의 세션 주체
  (main) · 7B 의 계정 범위 경로 결정(`bid-vector-v2-m7-7b/reports/evidence/m7/7b/checklist.md`, 읽기 전용 참고).

## 이 slice 가 닫는 절반과 남기는 절반

트리거 여섯 중 대부분의 **원천**(STR-08 감시 이력 · BID-01 입찰 건 · SET-01/02 대사)은 아직 main 에 없다. 그래서 이 slice 는
**알림함 자체**(표 · 쓰기 포트 · 중복 억제 · 읽기 API)를 세우고, 트리거는 **main 에 원천이 있는 것만** 배선한다.

- **닫는다**: 알림함 표(V23 — 번호는 머지 순서에 따라 재조정) · 쓰기 포트 `NotificationInboxPort`(이름은 레인이 정함, 다른 slice 가 이것만
  부른다) · NOTI-10 중복 억제 키(같은 건·같은 종류는 **한 항목 갱신**, 제목 문구가 바뀌어도 흔들리지 않음) · 읽음/안읽음 전이 · 건별 묶음 ·
  목록/읽음 처리 endpoint · 항목이 공고 ID 를 실어 클릭 → 워크시트가 클라이언트에서 닫힘(ⓐ).
- **배선하는 트리거**: main 에 원천이 있는 것 — 공고 변경(정정공고, COL-01: 공고 버전은 있으나 변경 알림 경로가 없다)을 Phase 1 에서
  확인하고, 원천이 있으면 배선한다. 나머지 다섯(새 후보 · 마감 D-1/D-0 · 개찰 완료 · 서류 기한 D-2 · 낙찰/패찰 확정)은 **원천 slice 가
  이 포트를 부르는 것으로 닫는다** — 이 slice 는 트리거 종류 어휘(enum)와 각 종류의 중복 억제 키 규칙을 선언만 하고, 그 원천의 판정은
  흉내 내지 않는다(가짜 원천 금지).
- **R-4 경계**: 알림함 기록이 끝이다. outbox → relay → 채널 경로에 새 일을 싣지 않는다(채널은 `OPEN-STR-12` 뒤).

## 산출물

1. **V23 마이그레이션**: 알림함 표. `account_id`(R-2) · 중복 억제 키(유일 제약 — 계정 범위 안에서) · 종류 · 공고 참조 · 읽음 시각(null =
   안읽음) · 생성/갱신 시각 · 표시용 본문은 **PII 없는 구조 필드**(사업자번호·담당자 이름·연락처 0). 보존·파기 정책이 필요한지 Phase 1 에서
   6B-3(보존 90일 파기)과 대조해 판정한다.
2. **Kotlin**: 쓰기 포트 + 읽기 포트 + persistence 어댑터 + controller. 중복 억제는 DB 유일 제약 + upsert 한 문장으로(경합에서 두 항목이
   생기지 않음을 실 DB 동시성 test 로).
3. **OpenAPI**: 목록(안읽음 수 · 건별 묶음 · 페이지 상한 선언) · 읽음 처리(단건 · 묶음 전체). 생성 타입 재생성(W-5).
4. **test**: 중복 억제(같은 키 두 번 → 항목 1, 갱신 시각만 이동) · 제목 문구 변경에도 1 · 읽음 전이 · 다른 계정 항목 접근 0(인가를 구조로) ·
   인증 없음 401 · CSRF 없는 쓰기 거부 · 응답 스키마에 PII 필드 0.
5. **추적표·capability map**: NOTI-10 행에 근거(알림함 절반 성립 — 트리거 배선은 원천 slice 몫으로 홀더). 판정 문자는 측정이 서는 만큼만 올린다.

## 설계 검토 (Phase 2.5 — 레인이 Phase 1 설계 문서 §0 에서 채우고 팀장이 수령)

- **(0) 위협 모델**: 방어 — 다른 계정 항목 읽기·읽음 처리(IDOR) · 중복 억제 키 경합으로 항목 증식 · 쓰기 포트를 HTTP 로 노출(알림 위조) ·
  알림 본문으로 PII 누출 · CSRF. 방어하지 않음 — 단일 운영자가 자기 알림을 많이 읽는 것.
- **(1) 구성**: 쓰기 포트는 HTTP 표면에 없다(아키텍처 게이트로 — 포트 호출 쌍 · denied-types). 계정 범위는 포트 시그니처가 주체를 받는 구조로.
- **(2) 우회 ≥5** · **(2b) 값 획득 축**(새 public 표면 전수) · **(3) 과잉·미달** — 레인이 채운다.

## in_scope / out_scope

- in: V23 마이그레이션 · 알림함 포트·어댑터·controller 와 test · `openapi/bidvector-operator-api.yaml` · `web/src/api/generated/operator-api.ts` ·
  게이트 설정(`config/quality/**`)의 필요한 줄(넓힌 줄마다 근거) · 추적표·capability map 의 NOTI-10 행 · `reports/evidence/m7/7c3/**`.
- out: 채널 발송 · 원천 판정(STR-08·BID-01·SET) · 기존 `inbox` 표 · `web/src/**` 의 생성물 외 · `.github/**`.

## acceptance

`ci.yml` 의 `check` job 과 `web` job 명령 그대로. 마이그레이션이 있으므로 `container` job 입력이 바뀌는지 path-limited diff 로 보이고,
바뀌면 그 job 도 잰다.

## rollback

`git restore --source=0768bda1 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일 삭제. **base 는 다섯 번 재고정됐다**(`a61ddff1` → `46ac0949` → `23837b19` → `1632242e` → `734e11b9` → `0768bda1`) — 정본 목록과 실측은 `rollback.md` 이고 이 줄은 그 요약이다. 옛 base 로 복원하면 **7C②·7C⑤·DEC-05·guava 의 공유 파일 줄까지 되돌아간다.** 마이그레이션은 **배포 전**
되돌림만 이 방식이다 — 배포 뒤 되돌림(V23 표 drop 순방향 마이그레이션) 경로를 rollback.md 에 따로 적는다. 목록 기계 산출, 마지막 산출물
커밋에서 ①~⑥ 실측.

## 검토 레인

되돌리기 어려운 경로(마이그레이션 + 사용자 소유 표). verifier(opus) · code-reviewer(sonnet) 병렬, verifier 표적에 **migration-reviewer 관점**과
**privacy-gate 관점**을 넣는다(이 장비에 두 에이전트 정의가 없다). Codex 는 운영자가 범위·비용을 승인하지 않았으므로 타지 않는다.

## 레인 사이 계약

- 7C②(투찰가 요청 결과 기록) · 7C⑤(낙찰/패찰 확정) · 7B(마감·서류 기한) 가 이 쓰기 포트를 부른다. **포트 시그니처와 종류 어휘를 Phase 1
  에서 먼저 확정해 팀장에게 보고**한다 — 팀장이 다른 레인에 전달한다. 확정 뒤 바꾸려면 팀장 경유.
- 7B·7C④ 와 마이그레이션 번호가 겹친다. 뒤에 머지되는 쪽이 번호를 다시 매긴다.

## 호스트 빌드

모든 Gradle·Testcontainers·npm 명령은 `flock ~/.local/bid-vector-build.lock <명령>`. 점검 셋(pgrep·free·ps)은 별도 호출로 먼저.
00:30~03:00 KST 무거운 빌드 금지.

## 계약 갱신 이력

**D-7C3-1 (2026-10-23, 팀장 — Phase 1 설계 `f50498e9` 수령).**
- **머지 순서 7B → 7C③.** 계정 전용 경로(`AccountScopedRoutes`)와 그에 맞춘 공유 test 둘(본문 거부 전수 · 「인증 operation 은 자격 헤더 대안을
  남긴다」 문서 test)의 고침은 7B 가 한 번 한다. 이 slice 는 중복 구현하지 않고, 7B 머지 뒤 rebase 해 뿌리 하나를 더한다. 동결·전체
  acceptance·변이·rollback 은 rebase 뒤.
- 포트 `NotificationInboxPort.record(NotificationInboxRecord) → Created|Updated|Failed`, 종류 어휘 여덟, 중복 억제 키
  `(account_id, kind, notice_number, notice_round)` = 기본키 — 수령. 7C②·7C⑤ 에 전달함.
- **트리거 배선 0** — COL-01 은 변경 감지는 있으나 관련성(감시 이력·입찰 건) 원천이 main 에 없어 배선하지 않는다(전수 공고가 알림함을
  덮는다). 알려진 제한 첫 줄. 완화: 출하 조립 어댑터·실 DB 로 쓰기 포트를 두드리는 test.
- **산출물 4 문면 정정**: 같은 키 재기록은 `occurred_at`·`updated_at` 이 이동하고 `created_at` 불변, `read_at` 은 detail 이 같으면 유지·
  다르면 해제(「갱신 시각만 이동」을 이렇게 읽는다).
- 읽음 처리 두 operation(단건 · 공고 묶음) · 보존 90일 안읽음 예외 없음 · 표시 문구 비저장(읽을 때 `notice` 합침, 파기된 공고는 표시 칸
  부재 — 「파기됨」 상태를 null 과 구별할지 레인이 판정) · `notice` FK 미설정(DDL 주석에 사유) — 수령.

**in_scope 확장 (2026-10-27, 팀장 수령).**

- **2026-10-27 팀장 수령 — in_scope 에 15 경로 추가.** 기계 대조: `git diff --name-only 46ac0949..HEAD`
  44 경로 ↔ 위 in_scope 문면 → 문면 밖 **15**. 이 중 **9** 는 승인된 설계 §0.12 가 이름을 든
  공유 래칫·등록부·게이트 자리다 — `RetentionTableRegistry` · `RetentionFixture` ·
  `CleanMigrationTest`·`CleanMigrationCheckTest`·`CleanMigrationColumnTest`·`CleanMigrationIndexTest`·
  `CleanMigrationPrivilegeTest` · `AppHttpDependencyGateTest`(면제 계수) · `AccountScopedRoutes`.
  나머지 **6** 은 §0.12 밖이고 **고른 것이 아니라 강제된 것**이다(각각 무엇이 강제했는가):

  | 경로 | 무엇이 강제했나 |
  |---|---|
  | `adapters/src/test/.../persistence/PersistenceTestSupport.kt` | 비우기 목록. 새 표가 빠지면 「행 하나」·안읽음 계수·묶음 계수 대조가 **실행 순서에 의존**한다(공고 키에 FK 가 없어 `notice` 를 비워도 이 표는 비지 않는다) |
  | `app/src/main/.../http/ErrorBody.kt` | 알림함 표면의 거부 어휘가 필요하다(추가형 — 기존 줄 변경 0). **병합 충돌 후보**: 다른 레인도 같은 파일에 거부 타입을 더한다 — 해소는 두 블록을 **다 남기는** 합집합이다 |
  | `app/src/test/.../http/AccountCarrierStructureTest.kt` | 7B 의 `getAttribute` 호출자 집합 **등식**. 새 controller 를 등재하지 않으면 그 등식이 붉다 |
  | `app/src/test/.../http/ConstantTimeComparisonStructureTest.kt` | `Intrinsics.areEqual` 허용 목록. 키 집합이 **두 자리**라 둘을 같이 맞춰야 한다 |
  | `app/src/test/.../http/HttpTestSupport.kt` | 크기 상한(500 줄). 조립을 가르고, 이중체 둘은 가시성만 넓혔다 |
  | `app/src/test/.../http/HttpTestApplication.kt` | 같은 상한이 강제한 **새 파일**(조립 ↔ 이중체·도우미로 책임 분리) |

  rollback 목록은 기계 산출이라 이 15 를 이미 포함한다(`comm -23` 빈 출력으로 확인 — `rollback.md`).

**r2 (2026-10-28) — base 재고정 둘과 새 파일 하나.**
- **base 를 두 번 더 받았다**: `23837b19`(7C⑤·DEC-05) → `1632242e`(7C②·guava CVE) → `734e11b9`
  (문서 커밋). 기계 대조 계수가 **44 → 42** 로 줄었다 — 앞 base 에서 내 차이로 세어졌던 공유 파일
  편집 일부가 그 main 에 이미 들어갔기 때문이다(목록은 `rollback.md` 가 라운드마다 재산출).
- **새 파일 하나를 in_scope 와 대조했다**: `app/src/test/kotlin/bidvector/app/http/NotificationInboxEndpointTest.kt`
  — r2 가 verifier F1·F2(wire 키 등식 · 표면 400)를 닫기 위해 만든 **알림함 HTTP 표면 test** 다.
  in_scope 문면의 「알림함 포트·어댑터·controller 와 test」에 해당하고, 2026-10-27 에 승인된 확장
  분류로는 **「게이트·상한이 강제한 여섯」이 아니라 명시 항목**이다(그 여섯은 그대로 여섯이다).
  `gate.tests.app` 에 등재했다(그 키는 전수 등식이다).
- r2 가 **지운** 것도 있다: 주입 허용 목록의 죽은 등재 둘(`NoticeId` · `NotificationItemKey`) —
  중간 sealed 타입을 걷어 주입 표면이 좁아진 결과이고, 게이트의 등식이 그 삭제를 요구했다.

**r3 (2026-10-28) — base 다섯째 재고정, 새 파일 0.**

- **base `734e11b9` → `0768bda1`.** 사유는 산출물이 아니다: 취약점 DB 갱신으로 `CVE-2026-116587`
  (go-jose, Caddy 바이너리 내장)이 web·proxy 이미지에서 잡혀 **모든 PR 의 `container` job 이
  막혔고**, 그 chore(PR #97)가 머지되자 받았다. 기계 대조로 **겹침 0** — chore 의 경로는
  `config/quality/vuln-allowlist.properties` 하나와 자기 evidence 셋이고 이 slice 의 42 와
  교집합이 없다. 계수는 **42 그대로**(복원 19 · 삭제 23).
- **이 라운드가 만든 새 파일 0** — r3 의 산출물 커밋 둘은 기존 파일 넷을 고쳤을 뿐이라
  「수정 라운드가 만든 새 파일 ↔ in_scope 대조」가 대조할 대상이 없다. 기계로 확인했다
  (`--diff-filter=A` 에서 산출물 쪽 빈 출력).
- **게이트 술어를 또 고쳤다**(F3 의 어휘 「닫힘」 판정 — 세 번째). 그래서 이 라운드의 검증은
  severity 와 무관하게 **표적 재검증**이다(하네스 Phase 4 예외). 재검증 축은 그 술어의 네
  축과 r3 이 세운 포착 단언 하나다 — `checklist.md` §2.

## 하네스 레인 변경 (상시)

- 없음.
