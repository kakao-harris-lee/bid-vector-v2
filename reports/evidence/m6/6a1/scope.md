# M6/6A-1 — HTTP 골격 · 단일 운영자 토큰 · 요청 audit · 전략 조회 축 (2026-09-17)

```yaml
milestone: M6
slice: 6a1-http-skeleton-auth-audit
base_sha: c4d09cc   # PR #31(6C) 머지 커밋 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - app/build.gradle.kts                                   # spring-boot-starter-web 추가 + bootJar 활성(지금 disabled) — 6C 가 6A 로 인계한 앱 이미지의 전제
  - app/src/main/kotlin/bidvector/app/BidVectorApplication.kt        # `main()` 신설(저장소 최초) — 조립 근
  - app/src/main/kotlin/bidvector/app/http/OperatorTokenFilter.kt     # 단일 운영자 토큰(운영자 결정 2026-09-16 ②) — 값은 환경변수 주입, 기본값 없음. 실패는 401, 토큰 값을 로그·응답에 싣지 않는다
  - app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt      # 전 요청 audit — 시각·주체·메서드·경로·상태·소요·correlation id 를 표에 적는다(운영자 결정 ③ 전용 표)
  - app/src/main/kotlin/bidvector/app/http/ErrorBody.kt               # 명시적 error body 규약(코드·사유·correlation id). 도메인 실패를 HTTP 로 옮기는 매핑표는 이 파일 하나
  - app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt  # 현 전략 조회 endpoint(읽기 전용) — 유일한 endpoint
  - app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt     # DataSource·Flyway·repository 조립(값은 환경변수, 기본값 없음)
  - adapters/src/main/resources/db/migration/V9__strategy_and_api_audit.sql   # 전략 영속 표 + `api_audit` 표. **V8 은 6B-1 이 쓴다**(레인 겹침 — 아래 「병행 레인」)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/JdbcStrategyRepository.kt   # `StrategyRepository` 실 구현(저장소에 production 구현이 없다 — 실측)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/StrategyRow.kt              # 행 ↔ 도메인 매핑. `AppliedStrategy`·`OperatorStrategy` 의 가시성을 넓히지 않는다
  - adapters/src/main/kotlin/bidvector/adapters/persistence/ApiAuditStore.kt            # audit 표 쓰기(추가 전용)
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                      # 위 둘의 SQL 추가만
  - app/src/test/kotlin/bidvector/app/http/**               # 인증(없는·틀린·맞는 토큰) · audit 행 생성 · error body 형태 · 조회 응답 · 토큰이 로그·응답에 안 실림
  - adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcStrategyRepositoryTest.kt
  - openapi/bidvector-operator-api.yaml                     # OpenAPI 단일 출처(수작성) + 구현과의 대조 test
  - gradle/libs.versions.toml                               # spring-boot-starter-web 좌표 추가만
  - reports/evidence/m6/6a1/**
  - milestone-6.md                                          # 6A 분할·6A-1 착수 문단(팀장 커밋)
out_of_scope:
  - 후보평가·알림 축 endpoint                # **포트 구현이 없다** — `EvaluateCandidatesUseCase` 의 여덟 포트 중 production 구현은 ML 축 하나뿐이고 후보 공급·감시 대상·면허 게이트·여력·알림 요청·correlation id·전략 저장이 test fake 뿐이다(실측). 어댑터 여섯을 쓰는 것은 slice 하나가 아니다 — `OPEN-6A-EVALUATION-ADAPTERS`(D-6A1-2)
  - 세션 편집 명령 endpoint                  # `EditSessionRepository` 실 구현이 **6B-1** 소관이다. 그것이 병합된 뒤 **6A-2**(D-6A1-3)
  - 검색·투찰가 요청 use case(STR-16)        # 운영자 결정 2026-09-16: 6A 는 기존 use case 만 노출, 검색 축은 별 slice
  - scope·RBAC·다중 사용자                   # 운영자 결정 ②: 단일 토큰 + audit 으로 시작, 소비자가 늘 때 확장(`OPEN-6A-RBAC`)
  - 실제 외부 effect(알림 발송·수집 트리거)  # milestone-6 「완료 후 별도 승인 사항」. 이 slice 의 endpoint 는 **읽기 하나**뿐이라 effect 경로가 없다(D-6A1-4)
  - Kotlin 앱 이미지·compose 편입            # 6C 가 6A 로 인계했으나 `bootJar` 활성까지가 이 slice — 이미지·compose 는 **6A-2**(entrypoint 가 실제로 무엇을 서비스하는지 정해진 뒤)
  - pagination                               # 목록 endpoint 가 없다. 목록이 생기는 slice 가 규약과 함께(D-6A1-5)
acceptance_commands:
  - "./gradlew --no-daemon check"                                                          # S-10 — 전건(evidence 커밋마다 그 HEAD 에서, 마지막은 보고·PR 코멘트가 정본)
  - "./gradlew --no-daemon :app:test --tests '*http*' --rerun-tasks"                       # S-40 — 인증·audit·error body·조회(캐시 우회)
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks"   # S-41 — 전략 영속 왕복
  - "./gradlew --no-daemon :app:test --tests '*OpenApiContractTest*' --rerun-tasks"        # S-42 — OpenAPI 단일 출처 ↔ 구현 대조
  - "./tools/one-command-check.sh"                                                         # S-20 승계(6C)
rollback: |
  신규 파일 삭제 + 편집 넷(`app/build.gradle.kts`·`Sql.kt`·`libs.versions.toml`·`milestone-6.md`)을 base 로.
  `git restore --source=c4d09cc --staged --worktree -- <in_scope 경로 개별 인자>`, 신규는 삭제. `bootJar` 는 다시 disabled 로 돌아간다.
  **마이그레이션 비대칭**: 적용된 DB 에는 V9 가 남는다(되돌린 코드는 그 표를 쓰지 않는다). 실제 삭제는 사용자 승인 대상이다.
  임시 clone ①~⑥ + 빈 컨테이너 재적용으로 실측. 정본 `reports/evidence/m6/6a1/rollback.md`.
```

작성: 2026-09-17, 세션 모델 단독. 근거: `milestone-6.md` 「Slice 6A」·완료 조건 1·4 · **운영자 결정 2026-09-16**(① 소비자는 API 전용, 화면은 기존 시스템 — `OPEN-ADR-09` 닫힘 ② 인바운드 인증은 단일 운영자 토큰 + 전 요청 audit) · **운영자 결정 2026-09-17**(③ 웹 스택 Spring Boot Web ④ 6A 는 기존 use case 만 노출 ⑤ audit 은 전용 표 신설) · 6A 입력 재고 `_workspace/m6-6a/01_inputs.md` · 4B-6b 인계(「`UnavailableMlAnalysis` 처분과 app 배선은 6A」).

## 착수 조사가 바꾼 것 — 6A 를 축으로 가른다 (D-6A1-1)

재고는 「노출 후보 use case 다섯」을 냈지만, 그 use case 들이 받는 **포트의 production 구현을 세어 보니
ML 축 하나뿐**이었다(실측: 나머지는 전부 생성자 매개변수와 test fake). 즉 「기존 use case 를 노출한다」는
지금 상태로 성립하지 않는다 — 노출하려면 어댑터 여섯을 먼저 써야 하고 그것은 slice 하나가 아니다.
그래서 6A 를 **셋**으로 가른다: **6A-1** HTTP 골격·인증·audit·전략 조회(배선이 가능한 유일한 축) ·
**6A-2** 세션 편집 명령 endpoint + 앱 이미지(6B-1 병합 뒤) · **6A-3** 후보평가·알림 축(어댑터 여섯이
생긴 뒤 — `OPEN-6A-EVALUATION-ADAPTERS`).

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-1** | 6A 를 6A-1(골격·인증·audit·조회)·6A-2(세션 명령·앱 이미지)·6A-3(후보평가 축)으로 가른다 | 위 실측. 6C 가 축을 섞어 재작업 3 라운드를 썼다 |
| **D-6A1-2** | 후보평가 축 어댑터 여섯(후보 공급·감시 대상·면허 게이트·여력·알림 요청·correlation id)은 **6A 밖** — `OPEN-6A-EVALUATION-ADAPTERS` 로 등재하고 slice 계획을 별도로 받는다 | 각 어댑터가 외부 계약(KONEPS·면허·알림 채널)을 지고 있어 slice 하나가 아니다. M3 가 수집을, M4 가 판정을 세웠으나 **그 둘을 잇는 어댑터가 없다** |
| **D-6A1-3** | 세션 편집 명령 endpoint 는 **6B-1 병합 뒤 6A-2** | `EditSessionRepository` 실 구현이 6B-1 소관이다. 두 레인이 같은 포트를 동시에 만들지 않는다 |
| **D-6A1-4** | 이 slice 의 endpoint 는 **읽기 하나**다 — 실행·명령 경로를 만들지 않는다 | 후보평가 use case 는 호출마다 알림 요청을 낳는다(재고 §7). 승인 문면 없는 외부 effect 를 HTTP 로 열지 않는다. 실행 경로는 6A-2 에서 **dry-run 강제**로 시작한다 |
| **D-6A1-5** | pagination 규약은 **목록 endpoint 가 생기는 slice** 가 정한다 | 지금 목록이 없다. 쓰지 않는 규약을 먼저 만들지 않는다 |
| **D-6A1-6** | 토큰은 환경변수 주입·기본값 없음이고 **값을 로그·응답·audit 에 싣지 않는다**. 실패는 401 이며 사유를 나누지 않는다(없음/틀림 구분 금지) | 6C D-6C-7·5E-1 D-5E-7 계승(기본값 없음). 사유를 나누면 토큰 존재를 알려 준다 |
| **D-6A1-7** | audit 은 **추가 전용** 표이고 요청 본문·토큰을 담지 않는다 — 시각·주체·메서드·경로·상태·소요·correlation id 까지 | 보존·파기 정책은 6B-3 이고 아직 승인된 기간이 없다. 본문을 담으면 그 결정 없이 개인정보가 쌓인다 |
| **D-6A1-8** | OpenAPI 는 **수작성 단일 출처**이고 test 가 구현과 대조한다(생성 도구 도입 안 함) | milestone-6 「OpenAPI 단일 출처」. 구현에서 자동 생성하면 「단일 출처」가 구현이 되어 계약이 사라진다(6B-1 의 스키마 기대치와 같은 이유) |

## 위협 모델 — 6A-1 고유 경계

**방어한다**: (a) 토큰 없는·틀린 요청이 endpoint 에 닿지 않는다 (b) 토큰 값이 로그·응답·audit 에 남지 않는다
(c) 모든 요청이 audit 에 남는다(성공·실패 모두, 인증 실패 포함) (d) 이 slice 가 **외부 effect 경로를 열지
않는다**(읽기 하나) (e) 도메인 실패가 HTTP 200 으로 위장되지 않는다(error body 규약) (f) OpenAPI 와 구현이
어긋나면 게이트가 붉다.
**방어하지 않는다**: RBAC·다중 사용자(`OPEN-6A-RBAC`) · 토큰 회전·만료 · TLS·네트워크 배치(6C·6E) · rate
limit · 요청 본문 감사(D-6A1-7) · 후보평가 축 전부(D-6A1-2) · pagination(D-6A1-5).

**우회 후보**: (1) 필터가 **일부 경로만** 덮으면 새 endpoint 가 인증 밖으로 태어난다 → 기본 거부로 짜고
(허용 목록이 아니라 **전부 인증 + 예외 명시**) 「등록된 모든 endpoint 가 필터를 지난다」를 test 로 전수(우회
축은 6C 의 「열거 대신 구성」과 같다). (2) audit 이 **성공 경로에만** 있으면 인증 실패·예외가 안 남는다 →
실패·예외에서도 행이 생기는지 test. (3) error body 가 예외 메시지를 그대로 실으면 내부 구조가 샌다 →
매핑표를 지나지 않은 예외는 **고정 문구 + correlation id** 로만. (4) 토큰 비교가 짧은 회로(`==`)면 타이밍
차가 생긴다 → 상수 시간 비교. (5) audit 쓰기가 요청 실패를 삼키면(또는 audit 실패가 요청을 죽이면) 둘 다
문제다 → 어느 쪽을 택했는지 명시하고 test. (6) OpenAPI 대조가 **경로 이름만** 보면 응답 형태 변경을 놓친다
→ 상태 코드·필드·nullability 를 대조.

## (2b) 값 획득 축

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `main()`·Spring 컨텍스트 | 앱 기동 | **경계로 처리** — 조립 근은 layers 밖(5E-1 D-5E-1 과 같은 자리). 조립이 도메인 값을 만들지 않음을 실측 |
| `StrategyReadController` | 전략 조회 | 닫는다 — 읽기 전용, use case 반환값을 그대로 옮기고 도메인 타입을 새로 만들지 않는다 |
| `JdbcStrategyRepository`·`StrategyRow` | 전략 값 복원 | 닫는다 — `AppliedStrategy`·`OperatorStrategy` 가시성을 넓히지 않는다(M4 가 닫은 위조 축). 새 public 표면 0 을 AST 로 |
| `ApiAuditStore` | audit 행 쓰기 | 닫는다 — 추가 전용, 읽기·삭제 메서드를 두지 않는다 |
| `OperatorTokenFilter` | 인증 판정 | **경계로 처리** — 토큰 값 주입 자리가 공개 표면이다. 값은 환경변수에서만 오고 생성자가 리터럴을 받지 않음을 실측 |

## 병행 레인 — 마이그레이션 번호 충돌

**6B-1 이 `V8` 을 쓴다**(같은 날 착수, worktree `bid-vector-v2-m6b`). 이 slice 는 **`V9`** 를 쓴다. 두 브랜치가
같은 번호를 쓰면 병합 시점에 Flyway 가 깨지므로, 병합 순서가 바뀌면 **번호를 다시 붙인다**(먼저 병합된 쪽이
번호를 지키고 뒤가 밀린다). 그 사실을 rollback.md·checklist 에 적고, PR 요청 시점에 `main` 의 최신 번호를
재확인한다. 겹치는 다른 파일: `Sql.kt`(6B-1 도 편집 — 문장 추가만, 같은 함수 무편집) · `milestone-6.md`(다른 절).

## 하네스 레인 변경

`git log --oneline c4d09cc..HEAD -- CLAUDE.md .claude/ docs/harness/` — 없음(착수 시점).

## OPEN — 수령·신설

| 식별자 | 처분 |
| --- | --- |
| `OPEN-ADR-09`(운영자 결정 2026-09-16 닫힘) | 이 slice 가 ADR 문면으로 옮긴다(API 전용 소비자) — evidence 에 등재 |
| 4B-6b 인계(app 배선·`UnavailableMlAnalysis` 처분) | **부분** — 조립 근과 전략 축은 이 slice, ML 축 배선은 후보평가 축과 함께 **6A-3** |
| `OPEN-6A-EVALUATION-ADAPTERS`(신설) | 후보평가 축 어댑터 여섯 — slice 계획을 별도로 받는다(D-6A1-2) |
| `OPEN-6A-RBAC`(신설) | scope·RBAC — 소비자가 늘 때(운영자 결정 ②) |
| `OPEN-6A-SESSION-ENDPOINTS`(신설) | 세션 편집 명령 endpoint — 6B-1 병합 뒤 6A-2(D-6A1-3) |

## 리뷰 레인

마이그레이션 파일(V9)이 생기므로 **`migration-reviewer`**, 인증·audit·개인정보 경로가 생기므로
**`privacy-gate`**, 공개 HTTP 계약(OpenAPI)이 생기므로 **`contract-keeper`** 가 추가로 붙는다(전역 규약 §3
세 줄 전부 해당). Codex 는 인증·인가·마이그레이션이 되돌리기 어려운 경로라 **대상**이 되지만 유료 호출이므로
리뷰 요청 시점에 범위·비용을 운영자에게 제시하고 승인받은 뒤에만 건다.

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-17 착수 | 초판 — D-6A1-1~8 | 운영자 결정 다섯(2026-09-16 둘 · 2026-09-17 셋) · 6A 입력 재고 · **포트 구현 실측이 6A 를 셋으로 가르게 했다** |
