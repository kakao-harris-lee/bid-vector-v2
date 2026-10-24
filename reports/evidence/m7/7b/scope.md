# M7/7B — 입찰 건 모듈(`bidding`) · BID-01 등재 (착수 계약, 2026-10-21, 팀장)

- **근거**:
  - `milestone-7.md` 7B 행.
  - 운영자 결정 2026-10-21 「7B 예외 + G1 커널 병행」. M6 결정 블록(2026-10-15)은 「G1 9 은 7B 착수 전에 닫는다」고 적는다. 하지만 G1 잔여는
    6G 백테스트 판정(ML-01)과 M7 소비자(7C②·④)가 있어야 ⓐ 가 된다(2026-10-21 조사). 그래서 운영자가 7B 를 **예외로** 먼저 연다.
    `milestone-6.md` 결정 블록 아래에 이 예외를 한 줄 적는다(산출물 9).
  - R-5: 제출확인서 보관 **3년**(운영자 결정 2026-10-21). decisions-2026-09-07 Q-7 · Q-11 · Q-12. R-2(소유 경계 선설계).
- **되돌리기 어려운 경로**(마이그레이션 + 사용자 소유 표). 검토는 다음과 같다.
  - verifier(opus) · code-reviewer(sonnet) 병렬.
  - verifier 표적에 **migration-reviewer 관점**과 **privacy-gate 관점**을 넣는다(이 장비에 두 에이전트 정의가 없다).
  - Codex 심판은 운영자가 범위·비용을 승인하지 않았으므로 타지 않는다.
- **base**: main `bee7c36f`(2026-10-23 `a61ddff1` → `56bb7474` → 2026-10-24 `998b59dd` 위로 rebase — 겹친 자리는 `EXPECTED_EXEMPT_COUNT`(합집합 66) · `capability-map.md` 활성 OPEN 수(합집합 45)) · worktree `bid-vector-v2-m7-7b` · 브랜치 `m7-7b/2026-10-21`. 병렬 레인 중 **마이그레이션을 만드는 것은 이 slice
  뿐**이다(다음 번호 V21 — PR 시점 main 최대보다 크게, D-6A1-12).
- **입력 정본**:
  - `docs/discovery/ux-journey-research.md` §6.1(상태 기계 · BID-01 초안 acceptance)
  - `docs/adr/0006-gradle-modules.md` D-2 · D-2.1(되살리는 조건)
  - `docs/discovery/decisions-2026-09-07.md` Q-7 · Q-11 · Q-12
  - `docs/discovery/m7-screen-data-requirements.md`(화면 C · D 의 7B 행)
  - capability map `OPEN-ADR-14`

## 산출물

1. **BID-01 정식 등재**(Q-7: 한 번에 연다):
   - capability map 에 **축 `BID` 신설** · §0.6 축 표 · §10 집계(95 → 96, 날짜 붙은 「집계 변경」 블록) · `- **분류**: \`V2 필수\`` 한 줄(A3 검사 형식).
   - acceptance 는 §6.1 초안에 Q-11(소급 확인 무기한 + 표시)과 Q-12(보관 기간은 정책 데이터)를 반영한 문면이다.
   - `OPEN-ADR-14` 를 닫고 활성 OPEN 개수 줄을 맞춘다.
   - `ADR 0006` D-2 의 `bidding` 취소선을 걷고 D-2.1 절을 지운다(되살리는 조건 충족을 적는다).
   - `settings.gradle.kts` 주석을 고친다.
   - 추적표(`acceptance-trace.md`)에 BID-01 행을 넣을지는 조건 3 의 61 밖이라 레인이 근거와 함께 정한다(조정안 메모: BID-01 은 61 밖).
2. **`bidding` 도메인 모듈**(domain 층, `:shared-kernel` 만 의존):
   - **상태 집합과 전이표**는 §6.1 을 따르고, 전이는 선언적 표로 둔다(중첩 분기 금지). 「보류」 상태는 없다. 「건너뜀(사유 필수)」과
     「나중에(리마인드 시각 필수)」를 둔다. 종료 가지는 건너뜀과 마감 지남(미확인)이다.
   - **투찰 예정 초안**: 고른 안 · 금액 · 제시 시각 · 공고 스냅샷(마감 · 게시 하한율 · basis)을 싣는다. 스냅샷은 불변이다(공고가 정정돼도
     안 바뀐다).
   - **투찰 기록**: 금액(원 단위, basis) · 제출 시각 · 추첨번호 · 증빙 참조 · 메모를 싣는다. 확인 시각과 초안 시각은 따로 남는다. 같은 공고에
     기록이 여러 번 갱신돼도 건은 하나다.
   - **소급 확인**(Q-11): 마감 뒤 확인은 기한 없이 허용하고 「소급」 표시가 타임라인에 남는다.
   - **타임라인**: 모든 전이가 시각 · 주체(사용자/시스템) · 사유와 함께 남는다(append-only).
   - **다음 확인 시점**: 어느 상태에서도 비어 있지 않다. 없으면 「없음」을 명시한다(타입으로 — nullable 금지). 후보는 마감 · 개찰일시 ·
     서류 기한 · 통보 예정일 · 리마인드다. 지난 일정 경고는 서버가 한 시각 기준으로 판정한다.
   - **페이퍼(가상) 투찰과 실투찰**은 같은 상태 어휘를 쓰되 타입이 다르고, 같은 목록에 섞이지 않는다(구조로).
   - 「투찰 기록됨」 건만 개찰 대사(SET-02) 후보가 된다는 술어를 둔다(대사 자체는 SET 소유 — 이 slice 는 후보 술어까지).
3. **제출확인서 보관 정책**(Q-12): 보관 기간은 `EffectiveDatedPolicy` 관례의 versioned 정책 데이터로 둔다(값 **3년**, 효력일 · 출처
   「운영자 결정 2026-10-21 R-5」). 이 slice 는 **증빙 참조(식별자) 와 보관 기한 계산**까지다. PDF 파일 업로드 · 저장소 · 파기 실행 경로는
   하지 않는다(레인이 필요하다고 보면 Phase 1 에서 보고). 보존 분류는 `RetentionTableRegistry` 관례에 새 표를 등재한다.
4. **workflow(application)**: 포트는 **주체(account)를 받는다**(R-2). 소유하지 않은 건에 대한 조회 · 전이는 구조로 거부한다(필터 누락을 test 가
   잡는다). 전이 use case 는 기존 `workflow/strategy` 의 전이 선례(TransitionGuards · TransitionOutcome · Actor)를 따른다.
5. **마이그레이션 V21**:
   - 새 표 셋 이상: 입찰 건 · 전이(타임라인) · 기록/초안. 레인이 설계하되 `account_id BIGINT NOT NULL REFERENCES account(id)` 를 모든 사용자
     소유 표에 둔다.
   - 열 단위 GRANT(7D 관례): 앱 역할은 append-only 타임라인에 INSERT · SELECT 만 갖는다. 고칠 수 있는 열을 최소로 한다. DELETE · TRUNCATE 는
     없다.
   - CHECK 로 「보류」 상태 불가, 「건너뜀 ⇒ 사유 NOT NULL」, 「나중에 ⇒ 리마인드 NOT NULL」을 건다.
   - 공고 스냅샷은 사본으로 둔다(공고 표 참조만으로 두면 정정 뒤 바뀐다).
   - **R-2 의 남은 절반**: `operator_profile`(V12) · `operator_strategy`(V9) 의 `CHECK (id = 1)` 를 계정 단위로 옮기는 일은 milestone 문면상
     「7B 가 새 표를 세울 때 함께」다. **Phase 1 에서 범위·위험을 판정해 보고**한다(같은 V21 / 별 V22 / 별 slice). 팀장 답을 받은 뒤 구현한다.
6. **HTTP + OpenAPI**: 화면 C · D 가 쓰는 최소 operation 을 둔다.
   - 내 입찰함 목록: 상태 탭 · 다음 확인 시점 · 페이퍼/실 분리.
   - 건 단건과 타임라인.
   - 관심 담기 · 나중에 · 건너뜀 · 초안 만들기 · 「제출함」 기록 · 소급 확인 등 전이 명령.

   모두 7D 의 기본 거부 필터 아래 둔다(새 무인증 경로 0). 상태 변경은 CSRF 를 요구한다. OpenAPI 수작성 단일 출처에 더하고 생성 타입을
   재생성한다(W-5 바이트 등식). 화면은 7E 다.
   - **초안의 금액 출처**: 투찰가 메뉴(DEC-05)는 아직 출하 경로에 없다(ML-01 · 7C② 대기). 초안 명령이 받는 「고른 안」의 입력 형태는
     **Phase 1 에서 설계**한다. 메뉴 산출을 흉내 내는 값이나 운영자 입력을 메뉴 값처럼 보이게 하는 경로는 금지다. provenance 를 구별한다.
7. **test**:
   - 상태 전이 전수(허용 · 거부): 표 기반, 속성 test 포함.
   - 스냅샷 불변.
   - 다음 확인 시점 비어 있지 않음(전 상태).
   - 소급 표시.
   - 페이퍼/실 분리.
   - 소유 경계(다른 account 의 건 접근 거부 — 실 DB).
   - 마이그레이션 CHECK · GRANT(`CleanMigration*Test` 관례).
   - OpenAPI 계약 · 인증 경계(기존 기계 전수 test 에 자동 포함되는지).
   - 각 핵심 성질은 변이에서 RED 여야 한다.
8. **개인정보**:
   - 투찰 기록 · 증빙 참조 · 메모는 운영자 사업 정보다. 사업자번호 · 대표자명 슬롯을 만들지 않는다.
   - 메모 자유 텍스트의 로그 · audit 노출 0(audit 는 본문을 남기지 않는다 — 7D).
   - evidence 에 실값을 쓰지 않는다.
9. **문서**:
   - `milestone-7.md`: R-5 행을 닫는다(3년).
   - `milestone-6.md`: 결정 블록 아래에 「7B 예외(2026-10-21)」 한 줄을 적는다.
   - runbook 해당 절(새 표의 보존 · 백업 대상).
   - `tools/db-rehearsal.sh` 백업 · 복원 리허설이 V21 을 지나는지 실측한다(역할 · 권한 행렬 포함).

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**:
  - 방어:
    - 남의 건 조회 · 전이(account 필터 누락)
    - 타임라인 위조 · 삭제(append-only 위반)
    - 초안 스냅샷 변조
    - 「보류」 같은 빈 상태의 재유입
    - 다음 확인 시점 공백
    - 페이퍼가 실투찰 목록 · 대사 후보에 섞임
    - 메뉴 값 위장
    - 메모 · 증빙의 로그 노출
    - 무인증 endpoint
  - 방어하지 않음 — 다수 사업자 격리 수준 · 가입(후속, R-2) · PDF 저장소(산출물 3).
- **(1) 구성**:
  - 전이는 표(상태 × 명령 → 다음 상태 · 필수 인자)다. 금지 전이는 표에 없는 것이라 구조로 거부된다.
  - 상태 · 결과는 sealed 이고 `when` 전수다.
  - 금액은 원 단위 `Long` + basis, 시각은 `Instant`.
  - 주체는 포트 인자로 받는다(전역 · 스레드 로컬 금지).
- **(2) 우회 ≥5**:
  - ① 다른 account 의 건 id 로 조회 · 전이 → 거부
  - ② 타임라인 UPDATE · DELETE → DB 가 거부
  - ③ 초안 뒤 공고 정정 → 스냅샷 불변
  - ④ 사유 없는 건너뜀 · 리마인드 없는 나중에 → 도메인 · CHECK 둘 다 거부
  - ⑤ 페이퍼 건을 실 목록 · 대사 후보로 → 타입이 막음
  - ⑥ 다음 확인 시점 null → 타입 · CHECK
  - ⑦ 인증 없이 새 endpoint → 401
  - ⑧ CSRF 없이 전이 → 거부
- **(2b) 값 획득 축**: 새 public 표면(도메인 생성자 · 포트 · 어댑터 · controller · DTO)이 밖에 허락하는 것을 표로 적는다. 특히 「전이 use case 밖에서 건
  상태를 바꾸는 경로」가 0 인지, 「주체 없이 부르는 포트」가 0 인지.
- **(3) 과잉 · 미달**:
  - 하지 않는다: 투찰가 산출 · 자격 판정 · 개찰 대사 실행 · 알림 · 화면 · PDF 저장소 · 다수 계정 UI.
  - 미달 금지: 소유 경계 · append-only · 스냅샷 불변 · 다음 확인 시점 · 페이퍼/실 분리 · 소급 표시.

## 진행 방식

**Phase 1 — 설계 보고 후 대기.** 코드를 쓰기 전에 아래를 evidence 에 적고 팀장에게 보고한다.
- 표 설계(DDL 초안 · GRANT · CHECK · 인덱스)
- 상태 전이표
- 모듈 · 패키지 배치와 게이트 등재 지점
- operation 목록
- R-2 남은 절반의 판정(산출물 5)
- 초안 금액 입력 형태(산출물 6)
- BID-01 추적표 행 여부

팀장 답(필요하면 운영자 결정)을 받은 뒤 구현한다.

## in_scope / out_scope

- in:
  - `bidding/**`(신설) · `settings.gradle.kts`
  - `workflow/**` 의 bidding application
  - `adapters/**` 의 bidding 영속 · `adapters/src/main/resources/db/migration/V21__*.sql`(필요하면 V22)
  - `app/**` 의 bidding controller · 배선
  - `openapi/bidvector-operator-api.yaml` · `web/src/api/generated/operator-api.ts`
  - `config/quality/*.properties` · `build-logic` 의 모듈 이름 test(새 모듈 등재, 넓히는 줄마다 근거)
  - `docs/discovery/capability-map.md`(BID 축 · BID-01 · OPEN-ADR-14 · §0.6 · §10) · `docs/adr/0006-gradle-modules.md` D-2 · D-2.1
  - `milestone-7.md` R-5 행 · `milestone-6.md` 결정 블록 아래 한 줄
  - 보존 레지스트리 · runbook 해당 절 · `reports/evidence/m7/7b/**`

  경로는 레인이 기계 목록으로 적는다.
- out:
  - `.github/**`(필요하면 계약 갱신) · `docker/**` · `web/src/**` 의 생성물 외 파일
  - 투찰가 메뉴 · DEC-10 · SET 대사 실행 · 알림함(7C ③)
  - 기존 operation 의 문면 · 기존 마이그레이션 파일 수정(새 번호로만)

## acceptance

`ci.yml` 의 `check` job · `web` job 명령 그대로다. 마이그레이션이 있으므로 **`container` job 을 `if:` 평가 재생으로 돌린다**(생략 불가 — app 이미지가
마이그레이션을 싣는다). `ml-engine` 은 입력 변경 0 이면 생략한다. `tools/db-rehearsal.sh` 실측(산출물 9).

## rollback

마이그레이션은 되돌리기 어려운 경로다 — **forward-fix 가 정본**(6B-2 runbook 관례). `rollback.md` 에 두 경우를 따로 적는다.
- 머지 전: in_scope 경로 한정 복원 `git restore --source=998b59dd …`, 새 파일 삭제.
- 머지 뒤: PR revert + 개발 DB 재생성.

머지 전 경우의 ①~⑥ 을 마지막 산출물 커밋에서 실측한다. 목록은 `git diff --name-status 998b59dd..HEAD` 기계 산출이다.

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. verifier 표적:
- **migration-reviewer 관점**: DDL · CHECK · GRANT · 인덱스 · 기존 표 무변경 · 리허설.
- **privacy-gate 관점**: 개인정보 필드가 구조적으로 못 들어오는가 · 메모 · 증빙 노출 0 — 저자가 아닌 쪽이 확인.
- 소유 경계 전수 · append-only · 전이표 전수 · 다음 확인 시점 · 페이퍼/실 분리 · OpenAPI · 인증 경계 · 값 획득 표.

Codex 없음(운영자 미승인 — 승인하면 Phase 4 뒤 한 번).

## 호스트 빌드

레인 넷 병렬(COL-03 · DEC-05 · SET-01). 모든 Gradle · Testcontainers · docker · npm 명령은 `flock ~/.local/bid-vector-build.lock <명령>` 으로
감싼다. 점검 셋(pgrep · free · ps)은 별도 호출로 먼저 본다. **00:30~03:00(6G 수집 창)에는 무거운 작업을 시작하지 않는다.** 6G DB(55432) ·
run-state · collection worktree 무접촉. `bidvector/app:local` 태그는 레인끼리 공유하므로 재생 뒤 되돌린다. evidence 커밋 뒤
`leakPatternGate` 를 재실행한다.

## 계약 갱신 이력

**D-7B-1 (2026-10-21, 팀장 — Phase 1 설계 수령 · 운영자 결정).** 설계는 evidence 커밋 「docs(m7-7b): Phase 1 설계 — 상태 전이표 …」다(SHA 는 rebase 마다 바뀌어 제목으로 가리킨다).
- **운영자 결정 — R-2 남은 절반은 별 slice 로 미룬다.** 7B 는 새 표에만 `account_id` 를 둔다. `operator_profile` · `operator_strategy` 의
  `CHECK (id = 1)` 이전은 식별자 모델을 먼저 정해야 한다(`edit_session.operator_id` TEXT 와 `account.id` BIGINT 가 같은가, audit 주체 어휘 ·
  자격 헤더 인증기와의 관계). 그 결정과 `operator_strategy_revision` 키 변경은 별 slice 가 맡는다. 이 PR 이 `milestone-7.md` R-2 판단 문단의
  「7B 가 새 표를 세울 때 함께」를 이 결정으로 고쳐 적는다(in_scope 추가: `milestone-7.md` R-2 문단). 중간안(열만 추가하고 CHECK 유지)은
  채택하지 않는다.
- **수용한 설계**:
  - 상태 14 · 전이표 하나(상태 × 명령 → 다음 · 필수 인자 · 허용 주체). 「후보」는 영속 상태가 아니다.
  - V21 표 넷: `bid_case` · `bid_case_transition`(append-only) · `bid_draft`(수정 = 새 행) · `bid_record`(append-only). 전부 `account_id`
    NOT NULL FK. 열 단위 UPDATE 는 전이가 바꾸는 열에만 준다. DELETE · TRUNCATE 0.
  - **`notice` FK 를 두지 않는다**: 6B-3 보존 파기(`notice` = Purge(TimeOnly))와 충돌하기 때문이다 — RESTRICT 면 sweep 이 실패하고, CASCADE 면
    운영자 기록이 사라진다. 공고 키와 스냅샷은 사본으로 둔다. rollback.md · runbook 에 이 이유를 적는다.
  - 다음 확인 시점: 출력은 sealed(`At`/`None`), 입력은 CHECK, 전역성은 속성 test. 파생값은 저장하지 않는다.
  - **초안 금액 provenance**: 요청 DTO 에 provenance · 메뉴 참조 필드가 없다. use case 가 `OPERATOR_ENTERED` 를 싣고 응답이 그대로 내보낸다.
    `PRICE_MENU` 는 어휘만 있고 오늘 도달할 수 없다(알려진 제한, 7C② 가 연다).
  - `DEADLINE_PASSED_UNRECORDED` 는 소급 확인(Q-11)으로 나가는 간선이 있어 종단이 아니다(진짜 종단은 `SKIPPED`).
  - `CORRECT_OUTCOME` 신설(사유 필수 · 원본을 덮어쓰지 않음 · 타임라인에 남음).
  - operation 열둘. 경로당 명령 하나(`edit-sessions` 선례).
  - BID-01 의 추적표 처리: `acceptance-trace.md` 집계는 고치지 않는다. 날짜 붙은 교차 참조 한 줄(모수 61 → 62, 행은 7B evidence)만 넣고, 행은
    같은 술어로 7B evidence 에 둔다. capability map §10 은 95 → 96 · `V2 필수` 61 → 62 다.
  - PDF 저장소는 범위 밖이다. `evidence_ref` 는 제한된 형태이고 로그 · audit 노출 0 이다. 보관 기한은 「참조의 보관」으로 문면을 고정한다.
  - 보존 분류: 새 `ExemptReason.OPERATOR_BID_HISTORY` 로 네 표를 등재한다(파기 실행은 범위 밖).
- 알려진 제한 넷(evidence): `PRICE_RECEIVED` 도달 불가 · 자동 전이의 시스템 호출자 부재 · PDF 저장소 · 다수 사업자.
- Phase 2(TDD)로 간다.

**D-7B-2 (2026-10-23, 팀장 — 동결 전 수정 하나).** 계정 범위 핸들러의 `AccountId` 를 audit 주체 문자열(`account:<값>`) 파싱으로 복원하지
않는다. 세션 인증이 typed `AccountId` 를 요청 속성에 심고(주체 문자열은 audit 용으로 유지), 계정 범위 핸들러는 접근자 하나로 읽는다.
자격증명 경로는 계정을 심지 않는다(계정 범위 경로는 세션 전용 401 유지). 7C②·7C③·7C④ 가 이 접근자를 공유한다(7B 선행 머지 — D-7C3-1).
test: 주체 문자열 위조/변경이 계정을 바꾸지 않음 · 속성 부재 401 · production 의 문자열 파싱 경로 0 · 접근자가 문자열로 되돌아가는 변이 RED.

**D-7B-3 (2026-10-24, 팀장 — 검토 r1 수령 · 수정 라운드 1).** 판정 SHA `1d90c834` 에 대해 verifier r1 **not-ready**(high 3 · medium 2 · 장부층 8),
code-reviewer r1 **request_changes**(blocker 1 · high 3 · medium 11 · low 7). 재작업 1/5. 두 보고를 합친 처분이다(전문: `_workspace/m7-7b/01_verifier_report.md` ·
리뷰 파일 `cr-7b-r1.md`, 팀장이 경로를 넘긴다). 지적 ID 는 `V-`(verifier) · `C-`(code-reviewer) 접두로 evidence 처분표에 적는다.

- **결정 ① — Q-11 소급 확인(V-H1): 서버가 마감을 판정한다(사용자 op 를 새로 열지 않는다).** 마감 전 상태의 건에 명령이 오거나 건을 읽을 때, 그 건의
  마감 사본이 지났으면 use case 가 시스템 주체로 `MARK_DEADLINE_PASSED` 를 먼저 적용하고(타임라인에 시스템 주체로 남는다) 요청 명령을 이어서 판정한다.
  마감 사본은 `bid_case` 에 둔다 — 없으면 V21 에 열을 더한다(V21 은 아직 main 에 없으므로 새 번호가 아니라 V21 수정이다). 공고가 파기된 WATCHING 건도
  사본으로 판정되어 소급 기록이 가능해야 한다. test: 마감 뒤 기록 → `retroactive=true` + 타임라인 순서(시스템 전이 → 기록) · 파기된 공고 건 기록 · 마감 전 무전이 ·
  지연 판정을 끄는 변이 RED. BID-01 ⓐ 는 이 test 가 출하 조립에서 초록일 때만 유지한다.
- **결정 ② — 통보 예정일(C-H1 · V 장부층): 덮어쓰기를 멈추고, 쓰기 경로 부재는 알려진 제한으로 선언한다.** `UPDATE_CASE_STATE` 에서 그 열을 빼거나
  기존 값을 보존한다(주석과 코드를 맞춘다). 쓰는 경로는 이 slice 에서 만들지 않는다 — `NOTICE_EXPECTED` 도달 불가를 evidence 알려진 제한에 적고
  OPEN 후보 `OPEN-7B-NOTICE-EXPECTED-WRITER` 로 등재한다. 이중체가 그 열을 보존해 결함을 가린 갈림(C-M)도 함께 고친다. 회귀 test: 값이 있는 건을
  전이시켜도 값이 남는다(실 DB).
- **결정 ③ — `snapshotTakenAt`(V-M2 · C-M): 요청에서 뺀다.** 스냅샷은 서버가 뜬다(D-7B-1 위협 ③). OpenAPI · 요청 판독 · `DraftInput` · 생성 타입에서
  제거하고, 「신선도 검사」 문면을 지운다. 미래에 화면 신선도가 필요하면 별 slice 다.
- **결정 ④ — `resumeBidCase`(V-H3 · C-H2): 본문 없는 operation 으로 맞춘다.** 구현이 본문을 요구하지 않게 고친다(문서가 정본). 아울러 **문서
  `requestBody` 유무 ↔ 매핑 `@RequestBody` 유무 집합 등식** 계약 test 를 전 operation 에 건다 — 새 게이트이므로 표적 재검증 대상이다.

고친다(산출물):
- **C-B1 기초금액 스냅샷 어휘**: `JdbcNoticeSnapshotQuery` · `BidEffectWriter` 가 같은 모듈의 정본 `ProvenanceCodec` · `FloorRateOriginCodec` 을 재사용한다
  (손 어휘 금지, `FILLED_FROM_BUDGET_KEY` 포함). `bid_draft` provenance 열에 어휘 CHECK · 짝 CHECK. test: **기초금액이 있는 공고**로 초안을 만들어 스냅샷
  네 열을 실 DB 에서 단언 + 어휘 등식(ProvenanceKind.entries ↔ CHECK 목록).
- **V-H2 · C-H3 없는 test 둘**: `BidCaseColumnPrivilegeTest`(열 단위 UPDATE 집합 등식 + **출하 앱 역할 양성 대조**) · `BidCaseRowTest`(꼬리표 전수) 를 실제로
  만든다. verifier 변이 M7(`updated_at`→`created_at`)이 RED 여야 한다. `assertAllRejectedAsApp` 에 앱 역할 양성 대조(C-M).
- **AccountId 주입(팀장 표적)**: javap 등식 두 개 — app main 의 `AccountId.<init>` 호출자 = 인증 경로의 정해진 집합, 생성자 인자로 `AccountId` 를 받는
  app class = {`Authenticated`}. verifier 변이 M4 가 RED 여야 한다. 게이트 술어 변경이므로 표적 재검증.
- **V-M1 D-7B-2 구조 게이트**: 문자열 상수 술어 대신 `getAttribute`(계정 속성) 호출자 집합 등식으로 닫는다. verifier 변이 M2 가 RED 여야 한다.
- **V21 구조(C-M)**: 자식 표 셋의 `account_id` 를 부모와 묶는 복합 FK · 타임라인 상태·명령 어휘 CHECK · `SkipReason` · `TransitionReason` 길이 상한(도메인 +
  CHECK, 개인정보 축) · UNIQUE 와 중복인 `CREATE INDEX` 셋 제거(V 장부층).
- **도메인·응답(C-M)**: `bid_record.draft_id` 가 계약이 말하는 초안(기본값이 된 초안)을 가리키게 한다 — 계약 문면이 「최신 초안」이면 문면을 인용해 보고하고
  고치지 않는다. 이미 담긴 건 재요청은 201 이 아니라 기존 건 응답(200)으로 `OpenCaseResult` 두 갈래를 쓴다(OpenAPI 함께). 이중체가 생성 전이를 남기게 한다.
- **속성 test(C-M)**: 계약 산출물 7 「속성 test 포함」을 이행한다 — 전이표 불변식 하나 이상을 `checkAll` 로. 「196 쌍」 문면은 182 로.
- **장부층·low**: `connectionSourceOf` 를 `internal` 로(되돌릴 수 있으면 `ConnectionSource.kt` 를 무변경으로) · 조건부 UPDATE 계정 필터 변이 측정(V M3) ·
  evidence §3 의 낡는 좌표 제거 · ConstantTime 허용 목록의 `BidCaseRequestsKt` 근거 문장 정정 · CR low 일곱은 고치거나 근거를 들어 등재.

in_scope 명시(이 결정으로 계약에 더한다): `app/src/main/.../http/` 의 `AuthenticatedAccount.kt` · `AccountScopedRoutes.kt` · `RequestAuthentication.kt` ·
`BrowserSessionAuthenticator.kt`(D-7B-2) · `ErrorBody.kt`(상수) · `JsonValueReaders.kt`(판독 함수) · app http test 의 공유 파일(D-7B-2 · 계약 test 분할) ·
각 모듈 `build.gradle.kts`(새 모듈 의존) · adapters `CleanMigration*Test` 관례 등재 · `reports/evidence/m6/6e1/acceptance-trace.md` 교차 참조 한 줄(D-7B-1).
`ConnectionSource.kt` 는 위 처분대로 무변경이 목표다 — 남기면 근거를 evidence 넓힘 표에 적는다.

절차: 지적별 처분표(고침 · 등재 · 근거 있는 거부) → 구판 RED 확인(회귀 test 는 `1d90c834` 에서 RED 인지) → 찬 `check` · S-20 · container 재생(V21 변경)
· db-rehearsal · 변이 표(verifier M2 · M4 · M7 · 결정 ① 의 지연 판정 변이 포함) · rollback ①~⑥ 재실측 → 판정 SHA 고정 보고. 수정 라운드가 만든 새 파일은
in_scope 와 대조해 보고한다. main 이 움직이면 그 시점 main 으로 rebase 한다.

**D-7B-4 (2026-10-24, 팀장 — 검토 r2 수령 · 수정 라운드 2).** 판정 SHA `10cf5d02`(마지막 산출물 커밋 `4409825f`)에 대해 verifier r2 **ready-for-review**(차단 0 ·
게이트 medium 2 · 장부층 6), code-reviewer r2 **request_changes**(high 1 · medium 6 · low 14). 재작업 2/5. 두 보고를 합친 처분이다(전문: `_workspace/m7-7b/02_verifier_r2.md` ·
리뷰 파일 `cr-7b-r2.md`, 팀장이 경로를 넘긴다). 지적 ID 는 이번 라운드도 `V-`·`C-` 접두를 쓰고, r2 의 새 번호는 `V2-N1` · `C2-N1` 처럼 라운드를 붙여 r1 과 가른다.
main 의 #86(`b8a45da3`, vuln allowlist 만)은 이 slice 의 되돌림 경로와 겹치지 않으므로 **rebase 하지 않는다** — PR 시점 update-branch 로 받는다.

- **결정 ⑤ — 마감 판정은 되돌릴 수 있다(C2-N1 high · V2-W2): 시스템 반대 간선을 둔다.** `DEADLINE_PASSED_UNRECORDED` 에서 시스템 주체만 쓰는 명령 하나를
  전이표에 더한다(이름은 레인이 정한다, 예: `REVOKE_DEADLINE_PASSED`). 조건: 판정 시점의 유효 마감이 **미래**이고, 그 건이 그 상태에 **시스템 판정으로**
  들어왔다. 도착 상태는 마지막 시스템 `MARK_DEADLINE_PASSED` 행의 `from_state` 이고 마감 전 넷 중 하나여야 한다(전이표가 도착 집합을 정하고, 값은 use case 가
  타임라인에서 읽어 인자로 싣는다 — 표의 꼴을 깨지 않는다). 타임라인에는 시스템 주체 · `deadline-check` 로 남는다. 기록(`RECORD_BID`)으로 이미 그 상태를 떠난 건은
  대상이 아니다. 판정 함수(`settle`)가 두 방향을 같이 본다. V21 명령 CHECK 에 새 명령을 넣는다(V21 수정). test: CR 이 적은 재현 그대로 — 담기 → 읽기(판정 섬) →
  마감 연장 정정 → 읽기 → 원래 상태 + 타임라인 순서(시스템 판정 → 시스템 되돌림) · 그 뒤 `SKIP`/`DRAFT_BID` 가 받아들여짐 · 반대 간선을 끄는 변이 RED.
- **결정 ⑤ 보충 (2026-10-24, 레인 질의 수령).** 마감 전 넷 중 payload 를 싣는 것은 `DEFERRED`(리마인드 시각)뿐인데, 판정 전이가 그 값을 지우고 타임라인에도
  복원할 열이 없다. 그래서 `from_state = DEFERRED` 인 판정은 **`WATCHING` 으로 되돌린다**(나머지 셋은 `from_state` 그대로). 전이표의 도착 집합은 마감 전 넷 그대로이고
  use case 가 이 한 규칙만 더 안다. 판정이 리마인드 값을 남기는 안 · 타임라인에 복원 열을 더하는 안은 채택하지 않는다(「잔재를 남기지 않는다」 규칙 유지).
  §6 에 「미룸에서 판정된 건은 되돌릴 때 미룸을 잃는다 — 다시 미루려면 운영자가 새 리마인드로 미룬다」를 적는다. test 에 `DEFERRED` 에서 판정 → 되돌림 → `WATCHING` 한 갈래를 더한다.
- **결정 ⑥ — 「값 없음」과 「행 없음」을 가른다(C2-N7).** 마감 포트가 공고 행의 존재와 마감 값을 따로 돌려준다. 유효 마감은 **행이 있으면 그 값**(NULL 이면
  미정 → 판정하지 않는다, 이미 판정된 건이면 결정 ⑤ 로 되돌린다)이고, **행이 없을 때만**(6B-3 파기) 사본을 쓴다. 파기된 공고는 다시 정정되지 않으므로 사본 판정은
  되돌릴 일이 없다. KDoc 과 §6-2 를 이 문면으로 고친다. test: `deadline_at = NULL` 정정 한 줄 — 판정하지 않음 · 판정 뒤 NULL 정정이면 되돌림.
- **결정 ⑦ — 읽기가 일으키는 쓰기는 유지하고 사실로 선언한다(C2-N2).** GET 셋이 시스템 판정을 쓸 수 있다. 값을 요청자가 고르지 못하고 결정 ⑤ 로 비가역성이
  사라지므로 교차 출처 GET 의 실해는 「판정 시각을 앞당김」뿐이다. `BidCaseQueryController` KDoc 을 사실로 고치고, §6 에 「읽기가 시스템 전이를 쓴다 · CSRF 면제
  메서드다」와 위협 모델 경계 문장을 적는다. 읽기 전용 응답 + enqueue 분리는 이 slice 가 하지 않는다.
- **결정 ⑧ — 목록은 판정 대상을 SQL 로 좁히고 상태 탭을 DB 로 돌린다(C2-N3).** 판정 후보(마감 전 넷 중 유효 마감이 지난 건 + 결정 ⑤ 의 되돌림 후보)를
  질의 하나로 먼저 고르고 그것만 판정한 뒤, 목록은 `state` 필터를 SQL 에서 적용한다(지금 무시되는 포트 인자가 다시 쓰인다). 상한·커서는 OpenAPI·web 을 바꾸므로
  이 slice 에서 하지 않고 §6 알려진 제한 + OPEN 후보 `OPEN-7B-BIDBOX-PAGING` 으로 적는다(1 운영자 입찰함 규모 근거와 함께).

고친다(산출물):
- **게이트 우회 둘(V2-N1 · V2-N2) — 게이트 술어 변경이므로 표적 재검증 대상이다.**
  - V2-N1 계정 운반: `getAttribute` 술어를 **소유자 클래스와 무관한 메서드 서명**(`getAttribute(Ljava/lang/String;)`)의 호출자 집합 등식으로 바꾸고, app main 에서
    `AccountId` Class 상수(ldc)를 가진 class 집합 = 정해진 운반 자리(∅ 또는 명시 집합) 등식을 더한다. verifier 의 우회(래퍼 `getAttribute` + `convertValue`)를 변이로
    재현해 RED 를 잰다.
  - V2-N2 본문 없는 operation: 문서에서 `requestBody` 가 없는 operation 집합을 도출하고, 그 전부를 **본문 없이** 불렀을 때 400·415 가 아님을 집합으로 잰다
    (`@RequestBody` 주석 유무에 걸지 않는다). 주석 없이 본문을 요구하는 핸들러 변이 RED.
- **C2-N4**: `resumeBidCase` 에 400 선언. 「경로 변수를 읽는 매핑 ↔ 문서 400 선언」 집합 등식을 `OpenApiRequestBodyContractTest`(또는 이웃 test)에 더한다. 생성 타입
  재생성 · W-5 바이트 등식.
- **C2-N5**: `checkVocabulary` 재사용으로 상태 셋 · 명령 하나의 CHECK 를 `BidStateTag.entries` · `BidCommandTag.entries` 와 등식으로 잠근다(결정 ⑤ 의 새 명령 포함).
- **C2-N6**: HTTP 이중체의 마감 포트를 test 가 옮기는 값으로 바꾸고, 운반 층에서 ① 마감 뒤 `skip`/`draft` 409 ② GET 이 일으키는 판정 전이 ③ 결정 ⑤ 의 되돌림을
  한 번씩 잰다.
- **C2-M1 잔여 · V2-W4 `draft_id` 묶음**: `bid_draft` 에 `UNIQUE (id, case_id, account_id)` 를 두고 `bid_record (draft_id, case_id, account_id)` 복합 FK 로
  바꾼다(NULL `draft_id` 는 MATCH SIMPLE 로 통과). 남의 건·남의 계정 초안 거부를 행동으로 잰다(C2-N18 의 자식 둘 복합 FK 행동 측정도 여기서 닫는다).
- **C-M6 문면(TOCTOU)**: 등식 절을 지우고 「기록 시점의 최신 초안이다 — 화면의 기본값과 같다는 보장은 없다」로 세 자리(`LATEST_DRAFT_ID` · `latestDraftId` ·
  V21 주석)를 고친다. 요청에 초안 id 를 싣는 안은 채택하지 않는다.
- **V2-W1 · C2-N9**: `open` 의 `Opened` 갈래도 판정을 지난다(같은 요청 쌍이 두 답을 주지 않는다).
- **V2-W3**: 동시성 회귀 test 를 커밋한다(버전 조건을 빼는 변이 RED — 500 이 아니라 `VersionConflict`).
- **V2-W5**: 스냅샷 test 의 공고 provenance 는 정본 코덱으로 심는다(손 리터럴 금지).
- **C2-N8**: `settle` 이 `VersionConflict` 를 받으면 재적재 후 판정을 한 번 다시 시도하거나, 그 결과를 KDoc·§6 에 사실로 적는다(레인 선택, 처분표에 근거).
- **C2-N13 · C2-N14**: `memo`(2000) · `evidence_ref`(128) 에 `char_length` CHECK + OpenAPI `maxLength`, 스냅샷 통화·과세 열에 `<> ''` CHECK. 경계 test.
- **low 나머지**: C2-N10 은 evidence 에 「전이 하나가 한 트랜잭션, 요청은 둘~넷이고 판정은 요청 실패와 무관한 사실」 한 줄 · C2-N11 `submission` 판독 좁히기 ·
  C2-N12 단위 차이를 문면에 적거나 BMP 밖 문자 경계 test · C2-N15 소스의 줄 번호 좌표를 절 제목으로 · C2-N16 `Accepted` 발생 수 단언 · C2-N17 문자열 술어는 행동
  축이 정본임을 test KDoc 에 적고 `OR TRUE` 변이를 표에 더한다 · C2-N18 `attempt` 주석 정정 · C2-N19 NULL 사본은 §6 에 등재 · C2-N20 C-L5 근거 문장 정정 ·
  C2-N21 `D1-lazy2` 를 현재 트리에서 다시 잰다.
- **V2-W6**(동결 위반): 원인은 팀장 지시 순서다 — 처분표에 사실로 적고 레인 조치는 없다.

등재(이 PR 에서 활성 등재하지 않고 evidence 후보로 둔다 — 팀장이 다음 M7 문서 PR 에서 등록): `OPEN-7B-NOTICE-EXPECTED-WRITER`(유지) · `OPEN-7B-BIDBOX-PAGING`(새).

절차: 지적별 처분표 → 구판 RED 확인(회귀 test 는 `10cf5d02` 에서 RED 인지) → 찬 `check --continue` · S-20 · container 재생(V21 변경, 이미지 넷 태그 ID 기록·복원 —
scan 둘은 main 의 #86 allowlist 가 없는 트리라 외부 CVE 로 남을 수 있다, 원인과 함께 적는다) · db-rehearsal · 변이 표(결정 ⑤ 반대 간선 · 결정 ⑥ NULL · V2-N1 우회 ·
V2-N2 우회 · C2-N5 등식 · `draft_id` FK · 동시성) · rollback ①~⑥ 재실측(마지막 산출물 커밋에서) → 판정 SHA 고정 보고. **판정 SHA 보고 뒤에는 커밋하지 않는다**
— 팀장 요청이 와도 「그 SHA 로 검증이 도는가」 먼저 묻는다. 새 파일은 in_scope 와 대조해 보고한다.

**D-7B-5 (2026-10-24, 팀장 — 검토 r3 수령 · 승인 전 일괄).** 판정 SHA `1b33c925`(마지막 산출물 커밋 `da8e59e1`)에 대해 verifier r3 **ready-for-review**
(차단 0 · 게이트 medium 2 · 장부층 2), code-reviewer r3 **approve**(high 0 · medium 3 · low 8 · 질문 1). 차단이 없으므로 재작업 횟수에 넣지 않는다(2/5 유지).
남은 지적은 승인 전 일괄로 한 번에 처리한다(전문: `_workspace/m7-7b/03_verifier_r3.md` · 리뷰 파일 `cr-7b-r3.md`). ID 접두는 `V3-` · `C3-`.
게이트 술어를 바꾸는 항목(V3-N1 · V3-N2)은 표적 재검증 대상이다 — 나머지는 PR 조치 코멘트와 head CI 로 닫는다.

고친다:
- **V3-N1 계정 운반 — 손에 든 `AccountId` 로 다른 id 를 짓는 길**(`convertValue(n, seed.javaClass)`): app main 에서 Jackson 바인딩 진입점(`convertValue` · `readValue` ·
  `treeToValue` · `readerFor` 등 대상 타입을 받는 메서드의 **서명**)을 부르는 class 집합을 고정 집합 등식으로 잠근다(소유자 무관 서명 술어, 양성 대조 포함).
  verifier 우회를 변이로 재현해 RED. `AccountId` 생성 경로 자체의 봉쇄는 7D 파일이라 하지 않고 OPEN 후보 `OPEN-7D-ACCOUNTID-CONSTRUCTION` 으로 적는다.
  `AccountCarrierStructureTest` KDoc 「다른 길은 모두 Class 상수를 요구한다」를 사실로 좁힌다(V3-L2 · C3 의 V2-N1 문면 지적).
- **V3-N2 본문 없는 op 행동 등식**: 먼저 담은 건의 id 로 부르고, 응답이 401 · 404 가 아님을 양성 대조로 단언한 뒤 400 · 415 아님을 잰다. 건 존재 확인 뒤 본문을
  요구하는 핸들러 변이(G4-N3C) RED.
- **C3-M2 메모 · 증빙 상한 등식**: `BidMemo` · `EvidenceRef` 상한을 `internal` 로 올리고 `pg_get_constraintdef` 의 상한 ↔ 도메인 상수, OpenAPI `maxLength` ↔ 도메인
  상수 등식을 둔다. 「어긋나면 붉다」 주석이 사실이 되게 한다.
- **C3-M3 `restoreOf`**: 마감 전 넷을 키로 한 룩업표(`DEFERRED → Watching` 은 결정 ⑤ 보충의 의도 항목으로 표기), 그 밖은 `error`. 「되돌림 도착 키 집합 ==
  `DEADLINE_JUDGED_STATES`」 등식 test.
- **C3-L6 사용자 판정**: §6.1 표(도메인 명세)가 사용자의 「마감 지남」 기록을 요구하지 않으면 `MARK_DEADLINE_PASSED` 를 `SYSTEM_ONLY` 로 좁힌다(추천 — 출하 호출자는
  시스템뿐). 요구하면 좁히지 않고 §6-2 에 「사용자가 적은 판정은 되돌릴 수 없다 — 유일한 출구는 소급 기록」을 결과까지 적는다. 어느 쪽인지 근거 문장을 처분표에 인용.
- **C3-L3** 「남의 건은 판정 후보로도 고르지 않는다」 행동 test + `SELECT_SETTLE_CANDIDATES` `OR TRUE` 변이 행. **C3-L4** `rowPresent` 를 끄는 운반 층 test(파기 건 사본
  판정) 하나 — 쓰지 않을 것이면 지우고 KDoc 정정. **C3-L7** `SubmittedBidAmount(...)` 를 `requestValue("amountWon")` 로 감싼다. **C3-L8** 되돌림 판단은 마지막
  타임라인 행만 읽는 포트로. **C3-L5** `targetOf` KDoc 을 「표에 행이 있는 명령에 한해」로.

적는다(§6 · evidence, 코드 무변경):
- **C3-M1** 판정 뒤 소급 기록된 건에 연장 정정이 오면 `retroactive=true` 가 거짓인 채 남는다 — Q-11 집계는 그만큼 과대.
- **V3-L1** 판정 뒤 공고가 파기되면 담을 때 적은 사본이 그 판정을 뒤집거나(거둠) 이른 판정을 세운다 — 파기는 보존 기한 축이라 실해가 작다.
- **C3-L2** 「요청 하나는 트랜잭션 둘~넷」 은 명령 · 단건 조회 경로다 — 목록은 판정 후보 수에 비례한다.
- **C3-Q1** 마감이 now 를 가로질러 반복 정정되면 교차마다 시스템 행 둘 — §6-10 에 타임라인 축 한 줄.
- **C3-L1** checklist §12 의 줄 번호 좌표를 절 제목 · 인용으로 고치고, 좌표 확인 술어를 `grep -rnE ':[0-9]+'`(시각 · 바이트 등 허용형 제외)로 넓혀 다시 잰다.
- 앞 라운드 동결 뒤 측정한 「DEFERRED 로 복원」 변이 RED 1(로그 `scratchpad/7b/r2-mut-deferred-1059.log`)을 변이 표에 「판정 SHA 1b33c925 의 버릴 clone 측정」 표기로 넣는다.

등재 후보(evidence 에만, 팀장이 다음 M7 문서 PR 에서 등록): `OPEN-7B-NOTICE-EXPECTED-WRITER` · `OPEN-7B-BIDBOX-PAGING` · `OPEN-7D-ACCOUNTID-CONSTRUCTION`(새).

절차: 처분표 → 회귀 test 는 `1b33c925` 에서 RED 인지 → 찬 `check --continue` · S-20(V21 을 바꾸면 container 재생 · db-rehearsal 도) · 변이 표(V3-N1 · V3-N2 · C3-M2 ·
C3-M3 · C3-L3 · C3-L6 을 택하면 그 변이) · rollback ①~⑥ 재실측(마지막 산출물 커밋에서) → 판정 SHA 고정 보고. 보고 뒤 커밋하지 않는다. rebase 하지 않는다(#86 은
PR 시점 update-branch). 새 파일은 in_scope 와 대조해 보고한다.

## 하네스 레인 변경 (상시)

- 없음.
