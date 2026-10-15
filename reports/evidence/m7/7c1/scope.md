# M7/7C① — 공고 검색 API (STR-16 검색 절반) (착수 계약, 2026-10-16, 팀장)

- **근거**: `milestone-7.md` 7C ①. 운영자 지시 2026-10-16 「병렬로 진행 가능한 M7 작업」 — 7C ① 은 7B(입찰 건 모듈)와 무관하고
  마이그레이션이 없어 G1 종결 전에 연다. 이는 `milestone-7.md` 「선행 조건」(M6 완료 조건 성립)의 **운영자 예외**다(7A 와 같은 종류).
- **base**: main `77850903` · worktree `bid-vector-v2-m7-7c1` · 브랜치 `m7-7c1/2026-10-16`.
- **입력 정본**: capability map STR-16(「채택 가능: `ILIKE` 부분 일치 검색과 `X-Total-Count` 페이지네이션의 계약 형태」) ·
  `docs/discovery/m7-screen-data-requirements.md` 화면 A·B 의 STR-16 행(검색 입력: 제목·기관·금액대·마감일 / 총수 / 빈 상태 이유) ·
  ADR 0011 D-2(OpenAPI 수작성 단일 출처 → 생성 타입).

## 산출물

1. **OpenAPI** `openapi/bidvector-operator-api.yaml` 에 `GET /api/notices` 하나를 더한다.
   - 질의: `q`(제목·공고번호 부분 일치) · `agency`(발주·수요기관 부분 일치) · `amountMin`/`amountMax`(**어느 금액인지 basis 를 계약에
     선언** — 기초금액인지 추정가격인지 하나로 정하고 문서에 적는다; 값이 없는 공고의 포함 여부도 선언) · `deadlineFrom`/`deadlineTo` ·
     `page`/`size`(상한 선언) · 정렬은 마감 임박 순 하나.
   - 응답: 공고 요약 배열(번호·차수·제목·기관·금액(basis 포함)·마감·상태) + 헤더 `X-Total-Count`.
   - **빈 결과의 두 뜻**을 응답에서 가른다: 「수집된 공고가 아직 없다」 vs 「조건이 좁다」(추적표 STR-16 문면). 형태(예: 본문 필드)는 레인이
     설계 검토 아래 표에 맞춰 정한다.
2. **Kotlin**: 조회 포트(읽기 전용) + persistence 어댑터(기존 `notice` 표 읽기) + controller. 기존 controller·`ErrorBody`·
   `OperatorCredentialFilter`(모든 경로 기본 거부) 관례를 따른다. **쓰기 없음 · 마이그레이션 없음.** `ILIKE` 의 `%`·`_`·`\` 는 이스케이프한다.
3. **계약 test**: OpenAPI 문서 ↔ 실제 응답 대조(기존 계약 test 관례) · 질의별 거동 test · 경계값(빈 q · 상한 초과 size · 역전된 범위).
4. **생성 타입 갱신**: `web/src/api/generated/operator-api.ts` 재생성(W-5 바이트 등식). 화면은 만들지 않는다(7E).
5. 추적표 STR-16 행은 **검색 절반**만 근거를 적는다(투찰가 요청 결합은 7C ② — 판정은 그대로 ⓒ 로 두고 「검색 절반 성립」을 적는다).

## 설계 검토 (Phase 2.5, 팀장)

- **(0) 위협 모델**: 방어 — 인증 없는 조회(필터 밖에서 태어난 endpoint) · `ILIKE` 패턴 주입(와일드카드로 전수 조회·느린 질의) ·
  페이지 상한 우회로 표 전체 덤프 · SQL 주입. 방어하지 않음 — 인증된 단일 운영자가 공개 공고를 많이 읽는 것(공고는 공공 데이터).
- **(1) 구성**: 인증은 기존 필터의 와일드카드 등록이 구성으로 닫는다 — 새 endpoint 가 401 을 내는 test 하나로 잰다. 질의는 바인딩 매개변수만
  (문자열 조립 0) — 이름 붙은 매개변수를 쓰는 기존 어댑터 관례.
- **(2) 우회 ≥5**: ① 인증 헤더 없이 호출 → 401 ② `q=%` 로 전수 → 이스케이프로 리터럴 `%` 검색 ③ `size=100000` → 상한 거부 또는 절삭(계약에
  하나로) ④ `amountMin>amountMax` → 400 ⑤ `q` 에 `'; DROP` → 바인딩이라 리터럴 ⑥ 매우 긴 `q`(수 KB) → 길이 상한.
- **(2b) 값 획득 축**: 새 public 표면 = 조회 포트·어댑터·controller·응답 DTO. 밖에 허락하는 것은 **공고 요약 읽기**뿐이고 쓰기·다른 표 접근 0.
  응답에 원시 관측(`raw_observation`)·PII 필드가 실리지 않음을 응답 스키마 test 로 잠근다.
- **(3) 과잉·미달**: 투찰가 요청 결합(7C ②)·알림함(7C ③)·화면(7E)은 하지 않는다. 미달 금지 — `X-Total-Count`·빈 결과 두 뜻·basis 선언.

## in_scope / out_scope

- in: `openapi/bidvector-operator-api.yaml` · `web/src/api/generated/operator-api.ts` · 조회 포트·어댑터·controller 와 그 test(경로는 레인이
  정하고 기계 목록으로) · `reports/evidence/m6/6e1/acceptance-trace.md` STR-16 행 · `reports/evidence/m7/7c1/**`.
- out: 마이그레이션 · 쓰기 경로 · `web/src/**` 의 생성물 외 파일 · `.github/**` · 다른 operation.

## acceptance

`ci.yml` 의 `check` job 과 `web` job 명령 그대로(OpenAPI·생성 타입이 web job 입력이다). `ml-engine`·`container` 는 입력 변경 0 을
path-limited diff 로 보이면 생략.

## rollback

`git restore --source=77850903 --staged --worktree -- <in_scope 개별 경로>`(evidence 제외), 새 파일은 삭제. 목록 기계 산출, 마지막 산출물
커밋에서 ①~⑥ 실측.

## 검토 레인

verifier(opus) · code-reviewer(sonnet) 병렬. Codex 없음(되돌리기 어려운 경로 아님). 병렬 레인 7D 도 OpenAPI·`ErrorBody`·필터 등록을
만질 수 있다 — 충돌은 나중 머지 쪽이 정리하고, 두 레인 모두 **기존 operation 의 문면을 바꾸지 않는다**.

## 호스트 빌드

모든 Gradle·Testcontainers·npm 빌드 명령은 `flock ~/.local/bid-vector-build.lock <명령>` 으로 감싼다(레인 넷 병렬). 점검 셋은 별도 호출로
먼저. 00:30~03:00(6G 수집 창)에는 무거운 빌드를 시작하지 않는다.

## 계약 갱신 이력

(착수)

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
