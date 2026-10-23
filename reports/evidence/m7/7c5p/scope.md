# M7/7C⑤-0 — 개찰 결과 canonical 승격: 수집된 원문 → `opening_result` (착수 계약, 2026-10-23, 팀장)

- **근거**: 7C⑤ Phase 1 실측(`bid-vector-v2-m7-7c5` 설계 커밋 `31b60c8e`, checklist §0.1) — V4·V5 의 canonical 개찰 슬롯은 있으나
  **production 쓰기 경로가 없다**(`JdbcOpeningResultRepository` 를 생성하는 자리가 `app/src/main` 에 0). 6G 개찰 수집은 원문만 적재하고
  (`reports/evidence/m6/6g/scope.md` D-6G-1 · `JdbcSnapshotSource` KDoc 「이 갈래는 canonical 승격을 하지 않는다」), 원문 → 도메인 사상
  함수는 어디에도 없다. 화면 E · 7C⑤-a 의 데이터 · 7C③ 개찰 트리거 · 7C⑤-b 대사가 모두 이 승격을 선행으로 공유한다. 팀장 결정 D-7C5-2
  (2026-10-23)로 별 slice 로 연다 — 운영자 지시 2026-10-23 R-8(7C 병렬 착수)의 범위 안이다.
- **base**: main `a61ddff1` · worktree `bid-vector-v2-m7-7c5p` · 브랜치 `m7-7c5p/2026-10-23`.
- **`OPEN-SET01-AWARD-NAME-INGEST` 와 다른 일이다**: 그것은 낙찰 목록 끝점을 새로 걷는 **외부 호출**이고(운영자 결정 2026-10-22: 6G 종료 뒤
  착수) 이 slice 는 **이미 적재된 원문**을 옮기는 내부 변환이다. 새 외부 호출 · 새 끝점 · 쿼터 소비 0. 6G 가 걷지 않는 키(`bidwinnrNm` 등)는
  이 slice 가 만들어 내지 않는다.
- **6G 실수집과의 경계**: 6G cron 은 옛 jar 로 돈다(~2026-10-29 종료 예정). 이 slice 는 그 jar 를 다시 빌드·배포하지 않고, 실 DB 에 쓰지
  않는다(실 DB 승격 실행은 별도 승인). 수집 경로의 거동(원문 적재 · 원장 · 회계)을 바꾸지 않는다.

## 산출물

1. **사상(순수)**: 원문 투영(`raw_observation.payload_fields`, 계약 등재 키) → `OpeningResult` canonical. 필드 계약(키·크기)과 provenance
   짝을 따른다. 값이 없거나 해석할 수 없는 칸은 **부재**로 두고 0·빈 문자열로 적재하지 않는다(`OPEN-ML-04` 의 「미정산을 0 으로 적재하지
   않는다」와 같은 원칙). 개찰 1위(잠정)는 확정 칸을 바꾸지 않는다(기존 측정 유지).
2. **걷기 선택**: 같은 공고에 원문 관측이 여럿일 때 어느 관측을 canonical 로 삼는지 **규칙을 선언**하고 test 로 잠근다. 행 존재·행 시각으로
   걷기를 짐작하지 않는다(6G 에서 다섯 라운드를 쓴 결함류 — 레인은 6G evidence 의 해당 결정을 Phase 1 에서 인용한다).
3. **승격 실행 자리**: 수집 run 끝의 후처리인지 별 job 인지 Phase 1 에서 판정(기본 입장: 수집 경로를 건드리지 않는 별 use case + 출하 조립
   배선, 멱등 — 같은 입력 두 번 → 행 불변). 출하 조립에 `JdbcOpeningResultRepository` 를 처음으로 꽂는다.
4. **보존·개인정보**: `opening_result` 는 시각 축 파기 대상, `raw_observation` 은 업체 식별 개념 마스킹(운영자 결정 2026-10-14). canonical 에
   옮기는 업체 식별 칸(낙찰업체명·1위 투찰업체명 등)이 그 결정과 **같은 보존 축**을 갖는지 판정하고, 마스킹된 원문을 승격하면 무엇이 되는지
   test 로. 사업자번호·대표자명은 계약 미등재라 원문에도 없다 — 그 부재를 단언한다.
5. **test**: 사상 property/표 test(키별 · 부재 · 해석 불가) · 걷기 선택 · 멱등 · 잠정/확정 분리 · 마스킹된 원문 · 출하 조립 + 실 DB E2E
   (Testcontainers, 원문 심기 → 승격 → canonical 읽기) · 수집 경로 거동 불변(기존 수집 test 무편집 초록).
6. **추적표·capability map**: COL-02 · SET-02 행에 근거, 승격 부재를 적은 자리(6G scope D-6G-1 인용처)에 「M7/7C⑤-0 이 닫음」.

## 설계 검토 (Phase 2.5 — 레인이 Phase 1 설계 문서 §0 에서 채우고 팀장이 수령)

- **(0) 위협 모델**: 방어 — 잘못된 관측 선택으로 틀린 낙찰가 확정 · 잠정 값이 확정 칸 오염 · 재실행으로 행 중복·흔들림 · 마스킹 우회(마스킹 전
  사본이 canonical 에 90일 넘어 남음) · 부재를 0 으로 적재. 방어하지 않음 — 상류 원문 자체의 오류(관측 그대로 옮긴다, provenance 로 표시).
- **(1)·(2) 우회 ≥5 · (2b) 값 획득 축 · (3) 과잉·미달** — 레인이 채운다. 과잉 금지: 새 끝점 · 새 표/마이그레이션 · 조회 API(7C⑤-a 몫) ·
  수집 경로 변경. 마이그레이션이 필요해지면 **멈추고 보고**(계약 갱신 + 되돌리기 어려운 경로 검토로 등급이 바뀐다).

## in_scope / out_scope

- in: 사상·승격 use case·출하 조립 배선과 그 test · 게이트 설정(`config/quality/**`)의 필요한 줄 · 추적표·capability map 의 위 행 ·
  `reports/evidence/m7/7c5p/**`.
- out: 수집 use case·원장·클라이언트 · 마이그레이션 · OpenAPI · 실 DB 실행 · 6G jar 재배포 · 낙찰 목록 끝점.

## acceptance

`ci.yml` 의 `check` job 명령 그대로. `web` 은 입력 변경 0 이면 생략, `container` 는 app 배포물이 바뀌므로 path-limited diff 로 판정해
바뀌면 잰다.

## rollback

`git restore --source=a61ddff1 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일 삭제. 배포 뒤라면 승격 배선 끄기(조립
한 줄)로 비활성화 가능해야 한다 — 그 경로를 rollback.md 에 적는다. 이미 쓴 canonical 행은 원문에서 다시 만들 수 있는 파생 데이터임을 근거와
함께 적는다. 목록 기계 산출, 마지막 산출물 커밋에서 ①~⑥ 실측.

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬, verifier 표적에 **privacy-gate 관점**(업체 식별 칸의 보존 축). Codex 없음(마이그레이션·인증·
파기 경로 아님 — 그렇게 되면 멈추고 보고).

## 레인 사이

7C⑤(-a 는 canonical 읽기로 이 slice 와 독립 진행) · 7C③(개찰 완료 트리거는 이 승격이 원천) · g1c03(수집 age 게이트 — 수집 경로를 만지는
레인, 겹치는 파일은 Phase 1 에서 목록으로).

## 호스트 빌드

모든 Gradle·Testcontainers 명령은 `flock ~/.local/bid-vector-build.lock <명령>`. 점검 셋은 별도 호출로 먼저. 00:30~03:00 KST 무거운 빌드 금지.

## 계약 갱신 이력

**D-7C5P-1~7 (2026-10-23, 팀장 — Phase 1 설계 `8e0959d6` 수령).** 마이그레이션 불필요(슬롯·GRANT 모두 V4·V5 에 있음) — 등급 변화 없음.
- **1** 이 slice 는 **개찰 관측 승격**으로 좁혀 닫는다. 6G 는 낙찰 목록 축을 걷지 않으므로 최종낙찰 네 칸(금액·업체명·률·일자)은 원문이
  없다. 그 축은 `OPEN-SET01-AWARD-NAME-INGEST` 가 들여오며, 들어오면 계약 행만으로 코드 변경 없이 승격된다(test 로 보인다). 산출물 1 의
  「낙찰가」는 이 결정으로 좁혀 읽는다.
- **2** 추첨번호 축은 승격하지 않는다(D-3F-4 (a) 와 D-6G-38 이 어긋남 — 새 OPEN, 명세 정정 대상). 부재 test.
- **3** 파기 시계가 승격 시점에 다시 시작하는 것은 알려진 제한으로 등재한다. 완화: 마스킹된 원문 불승격 · 보존 창 밖 관측 불승격 · 수집 직후
  승격(runbook). 업체 상호 잔존 상한을 수치로 묶는다. 운영자 보고는 팀장.
- **4** 멱등은 읽기-대조(같으면 쓰지 않음). `Sql` 의 조건은 바꾸지 않는다. **5** 실행 상태 잠금을 공유한다(실 실행 0, test 만).
  **6** 승격 범위는 확정 표본(정본 문서 인용). **7** use case 패키지 `workflow.collection`.
- g1c03 과 겹치는 설정 파일 셋은 줄 추가만, 수집 경로 무편집. g1c03 머지 뒤 rebase.

**D-7C5P-8 (2026-10-23, 팀장 — 구현 중 판단 변경 수령).** ① 보존 창은 적재 시각 기준(마스킹 시계와 같은 열) — 업체 식별 칸의 canonical 잔존
상한은 「최초 적재 + 보존 창 + 승격 지연」 ≤ **보존 창의 두 배**(운영자 보고 대상) ② 참가업체수는 목록 축 둘에서만 ③ 저장 키는 가장 늦은 걷기의
최소 관측 키 ④ 멱등은 관측 시각 비교(upsert 조건과 같은 축). 최종낙찰일자는 canonical 슬롯이 없어 낙찰 목록 축이 들어와도 셋만 채워진다(제한).

**D-7C5P-9 (2026-10-23, 팀장 — acceptance 판정 질의 수령).** `container` 는 job 전체를 CI 명령 그대로 잰다(app 갈래만 재는 안 기각 —
줄일 수 있는 단위는 job 뿐). 「ml-serving·web·proxy 입력 변경 0」 path-limited diff 는 보조 근거로만 남긴다. 실행 전후 네 태그 이미지 ID 기록·복원,
변이·rollback 과 같은 락 큐 직렬. RED 별 라운드 미관측은 사실로 선언하고 test 강도는 변이(바꿔치우기 13 + 음성 대조 1)로 세운다 — 게이트의 자연
발생 RED 는 test RED 로 세지 않는다(verifier 표적).

**D-7C5P-10 (2026-10-23, 팀장 — 검토 r1 수령: verifier not-ready(재작업 1/5) · CR REQUEST_CHANGES, 일괄 배치).**
- **F1(high) = CR M「목록 축만 물러선다」 거짓**: `conclusion == null` 물러섬을 목록 축으로만 한정. 결말 없는 상세 축의 끊긴 걷기는 승격하지 않는다
  (추출 쪽 `incomplete_axis` 와 같은 판정). verifier VPROBE1 을 회귀 test 로.
- **F2(medium)**: 빈 선택 축에서 `maxOf` 예외 → 실행 전체 정지. 빈 축은 맵에서 뺀다, VPROBE2 회귀 test.
- **F3 = CR HIGH**: `OpeningPromotionRunner` 를 짓는 test — 잠금 중 기동 거부 · 종료 코드 셋 · 잠금 래퍼 제거 변이 RED(형제 러너 test 형태).
- **CR**: 실 DB E2E 를 출하 조립(배선 → use case → 저장소)으로 · 개찰완료 행 묶음에서 확정 세 칸 부재 단언 · `finalAwardCompanyName`·
  `progressDivision` trim 대칭(production 수정, test 동반) · 자식 행 `revision` 흔들림과 창 안 전 행 메모리 적재는 제한 등재(상한 수치 선언).
- 장부층: container 절 매달린 자리 → verifier 재생 결과 · 변이 패치·필터 명령 기록 · 예비가격 자식 행 잔존 · 180일 상한에 파기 주기·
  `reference-date` 가산 · 커밋 메시지 test 수 정정은 evidence 에 사실로.
- 그 뒤 마지막 산출물 커밋에서 check · 변이(신규 포함) · rollback ①~⑥ · container 재생 → 동결.

**D-7C5P-11 (2026-10-23, 팀장 — 검토 r2 수령: verifier not-ready R2-1 high(재작업 2/5) · CR APPROVE(MEDIUM 2 · LOW 2), 일괄 배치).**
- **R2-1 = CR MEDIUM-1 — 검증자 처방을 택한다.** 결말(`AXIS` 줄)이 없는 축은 **목록 축만** 행 시각으로 물러서고, 상세 축은 열린 라운드 유무와
  무관하게 `Unusable` 이다. 근거: 잘린 상세 걷기가 승격되면 1위·자식 행이 굳고 자식 행은 지워지지 않는다(D-3E-3 (a)) — 되돌리기 어려운 쪽을
  막는다. CR 의 「구현 유지 + 등재」안은 기각한다. 비용(원장 이전·다른 실행 상태 디렉터리의 상세 축 원문은 승격되지 않음 — 다음 수집이
  원장 줄과 함께 다시 적재하면 승격된다)은 제한으로 등재한다.
  - 목록/상세 판정은 **둘째 목록을 만들지 않고** 기존 선언 하나에서 유도한다(`SourceEndpoint` 쪽 속성, 또는 원장의 「목록 축 줄은 공고 키가 없다」
    규칙의 정본 — 어느 쪽이든 정본 하나, 다른 자리는 그것을 부른다). 수집 쪽 `DetailAxis` 와 등식 test 하나.
  - 열린 라운드 판독은 남긴다(무해). 회귀 test 는 VP3 모양(원장 줄 0 + 상세 축 원문 → 승격 0) 순수 층·use case 층 각 하나, 구판 `73f31710` 에서 RED 실측.
  - 점검표 §2.1 의 「추출 `incomplete_axis` 와 같은 판정」 문장을 실측과 맞춘다.
- **CR MEDIUM-2**: 비교자 2차 키 test 의 주석·처분표를 「2차 키 **방향**을 잠근다」로 고치고, 같은 test 에 열거 순서 단언
  (`OPENING_AWARD_LIST.ordinal < OPENING_RESULT_LIST.ordinal`)을 더한다.
- **CR LOW-1**: `commands.md` §3 변이표에 새 넷(+R2-1 몫)을 행으로 더하고(패치 한 줄 · 붉어진 test 이름) 제목 수를 §3b 와 맞춘다.
- **CR LOW-2**: 러너 실패 경로(`promoteOrFail` catch) 한 사례를 대역으로 잰다. `Unlockable` 은 형제도 재지 않으므로 제한 한 줄로 둔다.
- **verifier L-r2-1**: rollback.md 「되돌리는 법」 코드 블록의 낡은 수(A 15건)를 재산출 값으로. **L-r2-2**: 출하 조립 실 DB test 가 배선 클래스
  `OpeningPromotionWiring` 을 거치게 하거나, 거치지 못하는 이유를 제한으로 등재(레인 판단, 처분표에 적는다).
- **rebase 는 이 배치 커밋 뒤, 동결 전에 한다**: main `54e2c5e2`(g1c03 머지) 위로. 설정 파일 겹침은 합집합, 의존 게이트 면제 수 재계산,
  g1c03 `INTERRUPTED_ROUND` 와 닫힌 라운드 사례 하나 추가. 그 HEAD 에서 check · 변이(신규 포함) · rollback ①~⑥(base 재고정) · container 재생 → 동결.

**D-7C5P-12 (2026-10-23, 팀장 — r2 배치 동결 보고 수령, 판정 산출물 `a2d4724b`).** 레인 요청 넷을 수용한다.
1. **in_scope 추가 한 줄**: `workflow/.../collection/CollectOpeningResultsUseCase.kt` 의 `DetailAxis` 가시성 `private → internal`(값·순서·사용처
   불변, 거동 변화 0). 위 out 「수집 use case」 는 이 한 줄을 뺀 나머지로 읽는다. rollback 목록에 포함.
2. **구판 RED 자리 대체**: `73f31710` 은 g1c03 머지 전이라 새 test 가 컴파일되지 않는다 — 같은 트리의 rebase 등가 `7c2167c7` 에서 잰 것을 수용.
3. **계약 밖 추가 둘 수용**: 추출 축 등식 test(`OpeningPromotionAxisParityTest`) · `OPENING_PER_NOTICE_AXES` public(test 전용 창구를 두지 않기 위함).
   (2b) 값 획득 축 등재로 족하다.
4. **제한 20(공고 단위 축 집합 사본 네 벌)**: 등식 test 둘로 갈림을 막는 지금 모양을 수용하고, domain 으로 합치기는 별 slice(후속 등재).

## 하네스 레인 변경 (상시)

- 없음.
