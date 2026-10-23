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
- **base**: main `bee7c36f`(2026-10-23 `a61ddff1` → 같은 날 `56bb7474` 위로 rebase — 겹친 자리는 `EXPECTED_EXEMPT_COUNT` 하나, 합집합 66) · worktree `bid-vector-v2-m7-7b` · 브랜치 `m7-7b/2026-10-21`. 병렬 레인 중 **마이그레이션을 만드는 것은 이 slice
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
- 머지 전: in_scope 경로 한정 복원 `git restore --source=56bb7474 …`, 새 파일 삭제.
- 머지 뒤: PR revert + 개발 DB 재생성.

머지 전 경우의 ①~⑥ 을 마지막 산출물 커밋에서 실측한다. 목록은 `git diff --name-status 56bb7474..HEAD` 기계 산출이다.

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

## 하네스 레인 변경 (상시)

- 없음.
