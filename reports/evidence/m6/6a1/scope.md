# M6/6A-1 — HTTP 골격 · 단일 운영자 토큰 · 요청 audit · 전략 조회 축 (2026-09-17)

```yaml
milestone: M6
slice: 6a1-http-skeleton-auth-audit
base_sha: 128f9cd3   # PR #39(6F-5-a) 병합 커밋 = main (계약 갱신 (4) — 착수 재개)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - app/build.gradle.kts                                   # spring-boot-starter-web 추가 + bootJar 활성(지금 disabled) — 6C 가 6A 로 인계한 앱 이미지의 전제
  - app/src/main/kotlin/bidvector/app/BidVectorApplication.kt        # `main()` 신설(저장소 최초) — 조립 근
  - app/src/main/kotlin/bidvector/app/http/OperatorCredentialFilter.kt # 단일 운영자 자격증명(운영자 결정 2026-09-16 ②) — 값은 환경변수 주입, 기본값 없음. 실패는 401, 그 값을 로그·응답에 싣지 않는다. **클래스 이름에 스캔 어휘를 쓰지 않는다**(D-6A1-9)
  - app/src/main/kotlin/bidvector/app/http/RequestAuditFilter.kt      # 전 요청 audit — 시각·주체·메서드·경로·상태·소요·correlation id 를 표에 적는다(운영자 결정 ③ 전용 표)
  - app/src/main/kotlin/bidvector/app/http/ErrorBody.kt               # 명시적 error body 규약(코드·사유·correlation id). 도메인 실패를 HTTP 로 옮기는 매핑표는 이 파일 하나
  - app/src/main/kotlin/bidvector/app/http/StrategyReadController.kt  # 현 전략 조회 endpoint(읽기 전용) — 유일한 endpoint
  - app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt     # DataSource·Flyway·repository 조립(값은 환경변수, 기본값 없음)
  - adapters/src/main/resources/db/migration/V15__api_audit.sql   # 요청 감사 표. **번호는 고정값이 아니라 정의다(계약 갱신 (4) D-6A1-12)** — 「PR 시점 main 최대 + 선점 통보 회피」. 현재 기대값 V15(main 최대 V13 · 6F-4 가 V14 선점). **V10·V11 공석을 줍지 않는다**
  - adapters/src/main/kotlin/bidvector/adapters/audit/**                                # **패키지 신설**(계약 갱신 (6) D-6A1-22·23) — `ApiAuditStore`(추가 전용)·`ApiAuditSql`. Sql.kt 는 무편집(sizeGate 회피, EventSql·RequirementSql 선례)
  - adapters/src/test/kotlin/bidvector/adapters/audit/**                                # 왕복 test + **의존 게이트 + 등재 완결성 게이트**(D-6A1-22 — 신설 패키지는 한 벌이다)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigration*Test.kt      # 스키마 스냅샷 래칫 넷(표를 더하면 반드시 따라 움직인다)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt   # TRUNCATE 목록 추가(기계적)
  - config/quality/gate-tests.properties                                                # 신설 게이트 test 등재(추가만) — 공유 파일
  - app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt       # spring-boot-starter-web 을 expectedModules 에 등재(D-6A1-16)
  - app/src/test/kotlin/bidvector/app/http/**               # 인증(없는·틀린·맞는 토큰) · audit 행 생성 · error body 형태 · 조회 응답 · 토큰이 로그·응답에 안 실림
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
  - 전략 영속(`StrategyRepository` 실 구현·전략 표)  # **6F-1 로 이동**(계약 갱신 (3), 운영자 지시 「배선이 먼저」). 이 slice 의 조회 endpoint 는 **6F-1 이 낸 구현을 소비**한다 — 6F-1 병합이 선행이다(D-6A1-10)
acceptance_commands:
  - "./gradlew --no-daemon check"                                                          # S-10 — 전건(evidence 커밋마다 그 HEAD 에서, 마지막은 보고·PR 코멘트가 정본)
  - "./gradlew --no-daemon :app:test --tests '*http*' --rerun-tasks"                       # S-40 — 인증·audit·error body·조회(캐시 우회)
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcStrategyRepositoryTest*' --rerun-tasks"   # S-41 — 전략 영속 왕복
  - "./gradlew --no-daemon :app:test --tests '*OpenApiContractTest*' --rerun-tasks"        # S-42 — OpenAPI 단일 출처 ↔ 구현 대조
  - "./tools/one-command-check.sh"                                                         # S-20 승계(6C)
rollback: |
  신규 파일 삭제 + 수정 파일을 base 로 복원. **base 는 고정 SHA 가 아니라 정의**다(D-6A1-15·39) —
  「이 브랜치가 분기해 나온 현재 main」 = `$(git merge-base HEAD origin/main)`. 고정 SHA 를 박으면
  main 이 전진할 때마다 낡는다(F-5 실측). 목록·수치는 라운드마다 재산출하며 **정본은
  `reports/evidence/m6/6a1/rollback.md`** 다 — 이 블록은 요약이고, 둘이 갈리면 정본이 이긴다.
  **마이그레이션 비대칭**: 적용된 DB 에는 `V15`(api_request_audit)가 남는다. 되돌린 코드는 그 표를
  쓰지 않으므로 무해하고, 실제 삭제는 파일 삭제가 아니라 **새 V 파일의 DROP + 사용자 승인**이다.
  임시 clone ①~⑥ + 빈 컨테이너 재적용으로 실측.
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
| **D-6A1-10** (계약 갱신 (3), 운영자 지시 2026-09-17) | 전략 영속은 **6F-1**(배선 slice 군) 소관으로 옮긴다 — 이 slice 는 HTTP 골격·인증·요청 감사까지이고 조회 endpoint 는 6F-1 이 낸 구현을 **소비**한다(6F-1 병합 선행). 마이그레이션 번호는 **V10** | 운영자가 「어댑터 부재는 중요한 결함이고 배선이 먼저」로 우선순위를 정했다. 전략 영속은 `evaluate()` 의 첫 줄이 요구하는 **판정 경로의 선행 의존**이라 HTTP 축보다 먼저 서야 하고, HTTP 와 묶으면 판정 배선이 웹 스택 결정에 묶인다 |
| **D-6A1-9** (착수 직후 정정) | 인증 관련 **클래스·파일·설정 키 이름에 비밀값 스캔 어휘를 쓰지 않는다** — 그 이름이 evidence 에 등장하는 순간 `leakPatternGate` 의 자기참조가 된다. 이 slice 는 `OperatorCredentialFilter`·`operator.credential.*` 로 명명하고 evidence 는 「자격증명」으로 적는다 | 팀장 실측: 초판이 그 어휘를 클래스 이름으로 써 계약 파일에서 매치 둘이 났다. 하네스 2026-09-16(어휘 축어 금지)의 **이름 축** — 스캔 어휘를 담은 식별자는 문서에 인용될 수밖에 없다 |
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
| `OperatorCredentialFilter` | 인증 판정 | **경계로 처리** — 자격증명 주입 자리가 공개 표면이다. 값은 환경변수에서만 오고 생성자가 리터럴을 받지 않음을 실측 |

## 병행 레인 — 마이그레이션 번호 충돌

**6B-1 이 `V8` 을 쓴다**(같은 날 착수, worktree `bid-vector-v2-m6b`). 이 slice 는 **`V9`** 를 쓴다. 두 브랜치가
같은 번호를 쓰면 병합 시점에 Flyway 가 깨지므로, 병합 순서가 바뀌면 **번호를 다시 붙인다**(먼저 병합된 쪽이
번호를 지키고 뒤가 밀린다). 그 사실을 rollback.md·checklist 에 적고, PR 요청 시점에 `main` 의 최신 번호를
재확인한다. 겹치는 다른 파일: `Sql.kt`(6B-1 도 편집 — 문장 추가만, 같은 함수 무편집) · `milestone-6.md`(다른 절).

## 계약 갱신 (4) — 착수 재개 (2026-09-19, 팀장)

이 계약은 **2026-09-17 에 고정되고 배선 우선 지시로 정지**했다. 그 사이 6F 군 다섯(6F-1·6F-2·6F-5-a·6F-6
및 진행 중인 6F-4)이 돌았고 `main` 이 크게 움직였다. **착수 전에 낡은 자리를 고친다** — 그리고 **계약이
사실과 다른 근거를 든 자리가 하나 있다.**

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-11**(base 갱신) | `base_sha` `c4d09cc` → **`128f9cd3`**(PR #39 병합 커밋 = 현재 `main`). 레인 worktree `bid-vector-v2-m6a`·브랜치 `m6-6a/2026-09-17` 은 유지하고 `main` 을 **merge** 로 흡수했다(rebase 아님) | 정지 기간에 `main` 이 6F-1(전략 영속)·6B-1(세션)·6F-2(후보 공급·trace)·6F-6(운영자 프로필)·6F-5-a(요건 영속·면허 게이트)를 받았다. rebase 는 **순효과 0 인 신설→철회 쌍을 재생해 상대 레인 파일을 지운다**(6F-1 실측) |
| **D-6A1-12**(마이그레이션 번호를 **고정값이 아니라 정의**로 둔다) | 번호는 **「PR 시점 `main` 의 최대 번호보다 크고, 병행 레인이 선점 통보한 번호를 피한다」**로 정의한다. **현재 기대값은 `V15`** — `main` 최대가 `V13` 이고 **6F-4 가 `V14` 를 선점**했다(병행 세션이 스스로 정해 통보). 파일명은 `V15__api_audit.sql` 로 시작하되 **PR 직전에 정의대로 재확인**한다 | 계약 갱신 (3) 이 「V10」으로 **고정값**을 박았는데 그 번호는 이미 무의미하다(`main` 은 `V12`·`V13` 까지 갔고 `V10`·`V11` 은 공석이다). **공석을 주워 쓰면 안 된다** — 「버전 순서 == 병합 순서」 불변식이 깨진 채 파일 이력에 영구히 남고, 언젠가 `V13` 을 적용한 긴 수명 DB 가 생기는 첫날 터진다(6F-4 세션이 V11→V14 재번호를 택한 것과 같은 근거). **이것은 6F-5-a 가 일곱 라운드에 걸쳐 산 교훈의 적용이다**(D-6F5-30): **고정 참조를 박지 말고 기준을 정의하면 범위가 레인 밖에서 움직여도 절차가 따라온다** |
| **D-6A1-13**(**계약의 근거 하나가 더 이상 참이 아니다** — 정정) | `out_of_scope` 의 「후보평가·알림 축 endpoint」 근거 문장 「**포트 구현이 없다** — production 구현은 ML 축 하나뿐」은 **거짓이 됐다.** 실측으로 갈음한다(세는 기준을 함께 적는다): D-6A1-2 가 「여섯」으로 묶은 어댑터 중 **셋**이 생겼다 — **후보 공급**(`JdbcCandidateSource`, 6F-2) · **면허 게이트**(`StoredRequirementLicenseGate`, 6F-5-a) · **correlation id**(`UuidCorrelationIdFactory`, 6F-2). 남은 **셋**은 **감시 대상**(`WatchSubjectPort` — 6F-4 진행 중) · **여력**(`CapacityPort` — 6F-3) · **알림 요청**(`NotificationRequestPort` — 6F-7). 여섯 밖에서도 **전략 저장**(`JdbcStrategyRepository`, 6F-1)·`Clock`(`SystemClock`)·**운영자 프로필**(`JdbcOperatorProfileRepository`, 6F-6)이 생겼다. **범위는 바뀌지 않는다** — 후보평가 endpoint 는 여전히 **6A-3** 이다 | 이 slice 는 **verifier 가 「계약이 사실과 다른 근거를 들었다」를 HIGH 로 내는** 저장소에서 돈다(6F-5-a r1·r2 가 그 계열이었다). 범위 판단이 옳더라도 **근거가 낡으면 계약이 거짓을 진술한다.** 수치를 적을 때 **무엇을 세는지 함께** 적는 것도 같은 라운드의 교훈이다(6F-5-a r7 — 같은 파일에서 척도 셋이 3·4·5 로 갈렸고 **셋 다 각자 옳았다**) |
| **D-6A1-14**(`OPEN-6A-EVALUATION-ADAPTERS` **부분 닫힘** 등재) | 그 OPEN 을 **부분 닫힘**으로 갱신한다 — 여섯 중 셋이 닫혔고 셋이 남았으며, **남은 셋에는 이미 받는 레인이 정해져 있다**(6F-4·6F-3·6F-7). **6A-3 의 선행 조건은 그 셋의 병합**이다 | 「slice 계획을 별도로 받는다」(D-6A1-2)의 답이 그 사이에 **6F 군 분할로 이미 나왔다.** OPEN 을 열어 둔 채로 두면 이미 답이 있는 물음을 다시 묻게 된다 |
| **D-6A1-15**(rollback 의 **복원 기준을 정의로** 둔다) | `rollback.md` 를 쓸 때 공유 파일의 복원 기준을 **고정 SHA 로 적지 않는다.** 「**이 브랜치가 분기해 나온 현재 `main`**」으로 정의하고 산출법(`git merge-base HEAD origin/main`)을 함께 적는다. **파괴적 명령보다 「아래」에 경고를 두지 않는다** — 절차가 틀렸으면 절차를 교체한다 | 6F-5-a r6 실측: 복원 기준이 고정 SHA(옆 slice 병합 이전)라 **문서대로 실행하면 남의 산출물이 exit 0 · stderr 없이 사라졌다**(상수 3→0 · 등재 4→0 · TRUNCATE 2→0). 그 slice 는 「무엇 **뒤에** 재산출하는가」를 **다섯 라운드에 걸쳐 한 칸씩 넓히다가** 종점이 열거가 아니라 **기준의 정의**임을 실측으로 확인했다. **이 slice 는 그것을 착수 계약에 처음부터 넣는다** |
| **D-6A1-16**(`compatibilitySmoke` 는 **등재형 게이트**다) | `app/build.gradle.kts` 에 `spring-boot-starter-web` 을 더하면 같은 파일의 `compatibilitySmoke` **`expectedModules` 목록에도 등재**한다. 등재하지 않으면 **그 의존이 해석·컴파일·로드되는지 아무도 재지 않는다** | `gate-tests.properties` 와 **같은 축**이다 — 「신설 게이트는 등재까지가 한 벌」(6F-2 verifier r1 HIGH-2). 그 task 는 **명시 목록**에 대해서만 해석을 단언하므로, 목록에 없는 의존은 게이트가 통째로 초록인 채 빠진다 |

**병행 레인 갱신** — 「6B-1 이 `V8`, 이 slice 는 `V9`」는 **두 갱신 전 상태**라 폐기한다. 현재:
`main` = `128f9cd3`(마이그레이션 `V1`~`V9`·`V12`·`V13`, **`V10`·`V11` 공석**) · **6F-4**(병행 세션, 로컬
브랜치, `V14` 선점, 수정 라운드 중 — `Sql.kt`·`gate-tests.properties`·`CleanMigration*Test`·`milestone-6.md`
겹침) · 6B-1·6F-1·6F-2·6F-6·6F-5-a **전부 병합 완료**(겹침 해소됨).

**병합 순서 규율에 대한 사실** — 운영자 지시(2026-09-19 「병합하고, M6 잔여 진행해」)로 `V12`·`V13` 이
`V11` 보다 먼저 병합됐다. **오늘 실질 위험은 0**(적용된 영속 DB 0, CI 는 매번 새 컨테이너)이나 그 규율에
자동 게이트가 없다는 사실은 `OPEN-MIGRATION-ORDER-GATE`(6F-5-a 신설)로 하네스 레인에 있다. 이 slice 는
**D-6A1-12 의 정의로 그 규율을 스스로 지킨다.**

## 계약 갱신 (5) — preflight 조사의 결정 (2026-09-19, 팀장)

구현 레인의 Phase 2 조사(`_workspace/m6-6a/01_preflight.md`)가 설계 영향이 큰 선택 셋을 올렸고, 팀장이
그중 둘을 실측으로 보강해 여기서 고정한다. **조사의 우회 폐쇄 계획 여섯은 전부 구조적이라 채택한다**
(필터를 `/*` 로 등록 + `RequestMappingHandlerMapping.handlerMethods` 기계 전수 · audit 필터를 인증 필터
**바깥**에 · 매핑표 밖 `Throwable` 은 고정 문구 + correlation id · `MessageDigest.isEqual` 상수 시간 ·
OpenAPI 를 `Set.equals` 로 완전 일치). 재사용 조사도 충분하다 — 새로 만드는 라이브러리·factory 가 **0** 이고
`JdbcStrategyRepository`·`SystemClock`·`UuidCorrelationIdFactory`·`ContentCachingResponseWrapper`·
`MessageDigest.isEqual`·`snakeyaml` 을 그대로 쓴다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-17**(audit 실패 처분 = **fail-closed**) | audit insert 가 성공해야 실제 응답을 내보낸다. 실패하면 버퍼를 버리고 고정 500 `ErrorBody`(+correlation id)로 덮는다. **대가를 알려진 제한에 등재**한다 — ① audit 저장소 장애가 **읽기 endpoint 가용성을 죽인다**(받는 쪽 **6E** 런북) ② 응답을 메모리에 버퍼링하므로 **대용량·스트리밍 응답이 생기면 재검토**(목록 endpoint 가 생기는 slice = pagination 을 정하는 그 slice, D-6A1-5 와 같은 자리) | 위협 모델 **(c)「모든 요청이 audit 에 남는다」를 fail-open 으로는 구조적으로 보장할 수 없다** — audit 저장소 장애 구간 **전체가 침묵 감사 공백**이 되고, 그 공백은 사후에 「요청이 없었다」와 구별되지 않는다. 결합 비용은 낮다: 이 slice 의 endpoint 는 **읽기 하나**(D-6A1-4)이고 소비자는 **단일 운영자**(운영자 결정 2026-09-16 ②)다. **가용성을 감사보다 위에 두는 선택은 지금 하지 않는다** — 그 교환은 소비자가 늘 때(`OPEN-6A-RBAC` 와 같은 시점) 다시 판단한다 |
| **D-6A1-18**(커넥션 풀을 **들이지 않는다**) | `PGSimpleDataSource`(비풀링)를 production 배선에도 쓴다. `spring-boot-starter-jdbc`(HikariCP)를 **추가하지 않는다**. `OPEN-6A1-CONNECTION-POOL` 로 등재 — 받는 쪽 **6C/6E** | 단일 운영자·저동시성이라 오늘 필요가 없고, 들이면 **카탈로그 좌표 + `compatibilitySmoke.expectedModules` + 호환 표면**이 함께 늘어난다(D-6A1-16). test 관례(`PersistenceTestSupport`)가 이미 같은 타입을 쓴다 — **production 과 test 가 같은 DataSource 타입**인 편이 배선 차이로 인한 오판을 줄인다. **다만 이 상태로 운영에 나갈 수는 없다** — 그래서 알려진 제한이 아니라 **OPEN** 이다 |
| **D-6A1-19**(설정 키 이름 — **D-6A1-9 를 모든 키로 일반화**) | DataSource 설정 키는 `bidvector.persistence.jdbc-url` · `bidvector.persistence.username` · **`bidvector.persistence.credential`** 이다. **`leak-patterns.txt` 에 걸리는 어휘를 키 이름에 쓰지 않는다**(그래서 셋째 키가 `credential` 이다). D-6A1-9 가 「인증 관련 클래스·파일·설정 키」로 적은 규율을 **모든 설정 키 이름**으로 넓힌다 | **팀장 실측**: 조사 레인이 제안한 초안 키 셋 중 **셋째**(스캔 어휘를 담은 이름 — 여기 축어로 옮기지 않는다)를 `grep -qiE -f config/quality/leak-patterns.txt` 에 넣으면 **걸린다**. `jdbc-url`·`username`·`credential` 은 통과한다. 그 이름은 evidence·계약·주석에 **인용될 수밖에 없으므로** 이름 자체가 `leakPatternGate` 의 자기참조가 된다 — D-6A1-9 가 초판에서 겪은 것과 **똑같은 덫**이고, 그때는 「인증 관련」으로 좁게 적혀 있었다 |
| **D-6A1-20**(OpenAPI 대조의 **깊이**를 단언한다) | 「top-level 키 집합 + nullable + 상태 코드」 대조만으로는 부족하다 — **중첩 object 속성이 생기는 순간 조용히 통과**한다. 둘 중 하나를 반드시 한다: ⓐ 대조를 **재귀**로 하거나 ⓑ **응답 스키마에 중첩 object 속성이 없음을 단언**해 중첩이 생기면 test 가 붉어지게 한다. 지금 DTO 가 평탄하면 ⓑ 로 족하되 **「지금 평탄하다」를 단언으로 박아야** 한다 | **이 저장소가 네 번 뚫린 「있는지만 보는 단언」 계열**이다(6F-5-a r1·r2·r4). 부분 대조는 **현재 상태에서만** 참이고, 다음 사람이 필드를 중첩시키는 순간 **게이트가 통째로 초록인 채** 계약과 구현이 갈린다. 우회 (6) 의 처분이 「경로 이름만 보지 마라」였는데 **깊이도 같은 축**이다 |
| **D-6A1-21**(필터가 **덮지 않는 디스패치**를 실측하라) | `RequestMappingHandlerMapping` 전수는 **핸들러 매핑만** 본다. ① Spring Boot 가 자동 구성하는 **`/error`** 가 그 목록에 있는지 ② 필터가 **어떤 `DispatcherType` 에서 도는지**(내 이해로는 기본이 `REQUEST` 뿐이다 — **단정하지 말고 재라**) ③ 예외가 컨테이너까지 올라가 **ERROR 디스패치**가 일어나는 요청에도 **audit 행이 정확히 하나**(둘도 0 도 아닌) 생기는지. 필요하면 등록 시 디스패처 타입을 명시한다 | **우회 (1) 과 (2) 가 만나는 자리**다. 「등록된 모든 endpoint 가 필터를 지난다」는 전수가 참이어도, **핸들러 매핑 밖의 디스패치**가 남으면 (1) 이 열리고, 그 경로에서 audit 이 빠지면 (2) 가 열린다. 두 전수가 각자 참인데 **교집합 밖이 비는 형태** — 이 저장소가 「커버리지 공백은 대개 계약이 가른 바로 그 자리에 생긴다」로 이름 붙인 것과 같다 |

**(2b) 값 획득 축 — 조사가 낸 다섯 줄을 계약에 흡수한다**: `OperatorCredentialFilter`·`RequestAuditFilter`
(**경계로 처리** — 표준 SPI, 도메인 값 생성 없음) · `ErrorBody`(**닫는다** — 매핑표가 유일 생성 경로) ·
`StrategyReadResponse`(**닫는다** — `OperatorStrategy` 필드를 그대로 옮기고 **새 계산값을 만들지 않으며
도메인 타입을 Jackson 에 직접 물리지 않는다**) · `ApiAuditRow`·`ApiAuditStore`(**닫는다** — 추가 전용,
읽기·삭제 메서드 없음) · `PersistenceWiring`(**경계로 처리**). **「경계로 처리」 세 줄은 실측 목록에
넣는다** — 경계 논증은 주체를 한정할 때만 서고, 가시성이 그 한정을 강제하지 않으면 논증이 아니라 희망이다.

**조사가 확인한 사실 하나를 등재한다** — Flyway 기동 배선은 **production 코드에 선례가 없다**
(`PersistenceTestSupport` 가 test 전용으로만 쓴다). `PersistenceWiring` 이 그 관례를 **처음 production 에
놓는다**. 재사용이 아니라 **첫 배선**이므로 그 사실을 알려진 제한과 리뷰 요청에 적는다.

## 계약 갱신 (6) — 구현 완료 뒤 in_scope 대조 (2026-09-19, 팀장)

구현 레인이 완료를 보고했고(HEAD `986c3c50`) 팀장이 **실제 변경 파일을 in_scope 와 대조**했다. **계약이
예상하지 못한 자리 셋**이 나왔고 그중 **하나는 게이트 공백**이다. 「수정 라운드가 만드는 새 파일은 in_scope
와 대조해 계약을 갱신한다」(반복 사각)의 적용이다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-22**(**게이트 공백 — 신설 어댑터 패키지에 의존 게이트가 없다**) | `bidvector.adapters.audit` 패키지 신설에 **바이트코드 상수 풀 의존 게이트 + `gate-tests.properties` 등재 + 등재 완결성 게이트**를 **한 벌로** 낸다(형제 셋과 같은 형태). **닫힘은 변이로만** 판정한다 — 그 패키지에서 **금지 루트를 전체 한정 좌표로** 참조해 RED 인지, 게이트 파일을 **삭제**해 완결성 게이트가 RED 인지 | 계약은 `ApiAuditStore` 를 **`adapters/persistence/`** 에 두라고 적었다. 구현은 팀장의 `Sql.kt` 분리 조언(`sizeGate` 회피)을 따라 **새 패키지로 갔고**, 그 이동이 **`PersistenceAdapterDependencyTest` 의 적용 범위 밖으로 나가는 것**임을 아무도 짚지 않았다. 형제 패키지는 전부 한 벌을 갖는다 — `evaluation`(6F-2) · `qualification`(6F-5-a) · `profile`(6F-6), **D-6F5-6 이 그것을 규율로 적었다**. 지금 `adapters/test/.../audit/` 에는 `ApiAuditStoreTest` 하나뿐이다. **패키지를 옮기면 게이트 적용 범위가 따라오지 않는다** — 6F-5-a r2 가 「술어의 범위가 계약의 선언보다 좁다」로 HIGH 를 받은 것과 **같은 축의 반대 방향**(코드가 술어 밖으로 나갔다) |
| **D-6A1-23**(in_scope 를 실제에 맞춘다) | 계약의 `adapters/…/persistence/ApiAuditStore.kt`·`…/persistence/Sql.kt` 두 줄을 **`adapters/…/audit/**`** 로 바꾼다(`ApiAuditSql.kt`·`ApiAuditStore.kt` + 신설 게이트들 + `ApiAuditStoreTest`). 그리고 계약이 빠뜨린 **스키마 스냅샷 래칫 넷**(`CleanMigration{,Check,Column,Privilege}Test`)·**`PersistenceTestSupport`**(TRUNCATE 목록)·**`config/quality/gate-tests.properties`**·**`app/…/compatibility/BootCompatibilitySmokeTest.kt`** 를 in_scope 에 넣는다 | `Sql.kt` 는 **무편집**이 됐다(감사 SQL 이 `ApiAuditSql` 로 갔다) — 그 편이 옳다(6F-4 도 `Sql.kt` 를 늘리고, 직전 병합에서 두 레인의 합이 `sizeGate` 를 울린 실측이 있다). 나머지 넷은 **표를 하나 더하면 반드시 따라 움직이는 자리**인데 계약이 이름을 안 적었다 — 6B-1·6F-1·6F-2·6F-5-a·6F-6 이 전부 같은 파일들을 만졌으므로 **예측 가능했던 누락**이다 |
| **D-6A1-24**(test 전용 좌표 셋을 등재한다) | `spring-boot-test`·`spring-boot-resttestclient`·`spring-boot-restclient` 를 in_scope 의 `gradle/libs.versions.toml` 아래 **test 전용**으로 명시 등재한다. **`compatibilitySmoke.expectedModules` 에는 올리지 않는다**(production 좌표가 아니다). 그 판단 근거를 알려진 제한에 적는다 | 구현 실측: Boot 4.1 에서 `TestRestTemplate` 이 `spring-boot-resttestclient` 로 옮겨졌는데 **그 모듈의 선언된 의존 그래프에 `RestTemplateBuilder` 가 빠져 있어**(`NoClassDefFoundError`) 셋째 좌표를 직접 채워야 했다. `@AutoConfigureTestRestTemplate` 자동 배선도 `@ConditionalOnMissingBean` 타입 추론 예외를 내 우회했다. **이것은 Boot 쪽 결함으로 판단된 우회**이므로 **사실과 판단 근거를 evidence 에 남긴다** — 다음 Boot 상향에서 되돌릴 수 있는지 보는 자리다 |

**팀장이 확인한 것(실측)** — HEAD `986c3c50` clean · 마이그레이션 `V15__api_audit.sql`(정의대로) · `Sql.kt`
무편집 · `gate-tests.properties` 에 신설 test 다섯 등재(`adapters.audit.ApiAuditStoreTest` 포함).

**아직 안 된 것 — `main` 미흡수.** merge-base 가 `128f9cd3` 이고 `origin/main` 은 **`1881c82c`**(6F-4, **V14**)
다. 팀장이 **두 번** 지시했으나 흡수되지 않은 채 구현 여섯 커밋이 쌓였다. **이 사실을 그대로 적는다** —
직전 slice 가 늦은 흡수로 충돌 7 hunk + `sizeGate` 둘을 한꺼번에 맞았고, 이 레인은 6F-4 와 **`CleanMigration*`
넷·`PersistenceTestSupport`·`gate-tests.properties`** 를 공유한다(위 D-6A1-23 이 in_scope 로 편입한 바로 그
파일들). **검증 전에 흡수한다.**

## 계약 갱신 (7) — Codex 심판 제외 (2026-09-19, 운영자 결정)

**운영자 결정 2026-09-19 — 이번 라운드는 Codex 심판을 태우지 않는다(사유: 토큰 예산).**

이 slice 는 인증·인가와 DB 마이그레이션을 건드려 **Codex 심판의 대상 조건에 해당**한다(운영자 지시
2026-09-11: 되돌리기 어려운 경로). 팀장이 범위·비용을 제시하고 승인을 구했고, **운영자가 이번 라운드
제외를 결정**했다. 「승인 없이 걸지 않았다」와 **「구했고 제외 결정을 받았다」**는 다른 기록이므로 여기에
사실로 남긴다. 「이번엔」이라는 문면대로 **다음 라운드에서 다시 물을 수 있다**.

**종결 조건이 이에 따라 읽힌다** — milestone 계약의 「Codex approve + 사용자 승인」은 이 slice 에서
**「verifier `ready-for-review` + 사용자 승인」**이다(CLAUDE.md 운영자 지시 2026-09-04·09-11).

**그래서 Claude 측 검토가 무게를 전부 진다.** 전역 규약 §3 의 세 줄이 **전부 해당하는 첫 slice** 이므로
다섯 레인을 **병렬로** 띄운다 — `verifier`(저작과 다른 패스) · `code-reviewer`(**호출 시 `model: sonnet`
명시**) · `privacy-gate`(인증·audit·개인정보 경로) · `contract-keeper`(공개 HTTP 계약 = OpenAPI 단일 출처) ·
`migration-reviewer`(V15). **게이트의 「확인 불가」는 통과가 아니다** — 사유를 남기고, 남길 수 없으면 다시 본다.

**토큰 예산에 대한 팀장 처분**: 레인을 **줄이지 않는다**(무엇이 붙는지는 변경이 닿은 곳이 정하지 팀장이
정하지 않는다). 대신 ① 다섯을 **한 번에 병렬**로 띄우고 ② 각 레인에 **범위를 좁힌 표적**을 줘 훑기를
막으며 ③ finding 이 요구하지 않는 한 **추가 라운드를 돌리지 않는다**.

## 계약 갱신 (8) — 게이트 사각 발견 + 이관 판단 (2026-09-19, 팀장)

구현 레인이 D-6A1-22 의 변이 실측 중에 **바이트코드 의존 게이트 계열 전체가 공유하는 사각**을 찾았다.
그리고 `OPEN-6F4-TITLE-WIRING` 의 겹침 판단을 냈다. 둘 다 등재한다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-25**(**게이트 사각 — `const val` 은 상수 풀에 흔적을 남기지 않는다**) | `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` 을 신설해 **하네스 레인**에 넘긴다. 이 slice 는 게이트를 고치지 않는다 — **네 게이트가 공유하는 사각**이고 한 slice 의 범위가 아니다 | **실측으로 발견됐다**: 금지 루트 참조를 `const val` 로 심었더니 **컴파일러가 인라인해 상수 풀에서 사라져** 의존 게이트가 못 봤다. `object` 참조로 바꾸자 RED 였다. **구현 레인이 오탐을 오탐으로 알아채고 변이 형태를 바꿔 다시 잰 것**이 이 발견의 경로다 — 「변이를 넣었는데 초록」을 게이트 실패로만 읽었으면 **닫힌 줄 알고 넘어갔을 자리**다(6F-4 가 하네스에 박은 규율이 바로 이 축이다). 영향 범위: `evaluation`(6F-2)·`qualification`(6F-5-a)·`profile`(6F-6)·`audit`(이 slice) **넷 전부**. 위험도: `const val` 은 컴파일 시간 상수(문자열·원시값)라 **호출보다 약한 위반**이지만, 정책 매직값을 그 경로로 끌어오면 게이트가 통째로 초록이다 |
| **D-6A1-26**(`OPEN-6F4-TITLE-WIRING` 은 **이 slice 와 겹치지 않는다**) | 이관·계약 갱신 없음. 그 OPEN 이 말하는 **실행 진입점**은 수집→canonical 배선을 실제로 트리거하는 자리이고, **6A-3**(`OPEN-6A-EVALUATION-ADAPTERS`) 소관이다. `milestone-6.md` 의 「(6A-1)」 표기는 **6A 가 셋으로 갈리기 전 참조**라 팀장이 같은 커밋에서 **6A-3 으로 정정**했다 | 6A-1 은 **D-6A1-4 로 읽기 endpoint 하나**에 못박혀 실행·명령 경로를 전혀 열지 않는다. **낡은 참조를 그대로 두면 다음 사람이 이 slice 에 없는 책임을 찾게 된다** — 이 저장소가 「낡는 좌표」로 반복해 겪은 형태의 **문면 판**이다 |

**팀장이 실측한 것** — HEAD `b7dde925` clean · `origin/main`(`1881c82c`, V14) **흡수됨** · 신설 게이트 한 벌
실재(`AuditAdapterDependencyTest`·`AuditGateRegistrationTest`) · `gate-tests.properties` 에 `adapters.audit` **3**건 ·
마지막 내용 커밋 `fff0faf5`.

**흡수가 예상보다 쌌다(사실 기록)** — 팀장이 `Sql.kt`·`gate-tests.properties` 충돌을 경고했으나 **실제로는
겹치지 않았다**(6F-4 가 `gate-tests.properties` 를 안 만졌고, `Sql.kt` 는 이 레인이 무편집이라 충돌 자체가
없었다). 자동 병합 conflict 0. **경고가 과했던 것을 사실로 적는다** — 다만 **커밋 여섯 단계에서 흡수한 것이
쌌던 이유**이기도 하다(직전 slice 는 서른여섯 커밋 뒤에 흡수해 충돌 7 hunk + `sizeGate` 둘을 맞았다).

## 계약 갱신 (9) — 검토 레인 다섯의 결과 (2026-09-19, 팀장)

Codex 제외 결정(계약 갱신 (7)) 아래 Claude 측 **다섯 레인을 병렬**로 돌렸다. 결과:
`privacy-gate` **통과**(위반 0·확인 불가 0) · `contract-keeper` **계약 준수** · `migration-reviewer` **통과**
(수정 1) · `code-reviewer` **HIGH 1 · MEDIUM 2 · LOW 1** · `verifier` **not-ready — 산출물 HIGH 3**.
재작업 **1/5**.

**이 라운드가 가르친 것 — 다섯을 돌린 값이 어디서 나왔는가.** `privacy-gate` 는 위협 모델 (a)·(c) 를
**준수**로 판정했고 그 판정은 **소스에 대해 옳았다**(production 조립에 필터가 등록돼 있다). 그러나
**verifier 만이 production 조립에 변이를 심었고**, 거기서 **그 등록을 통째로 지워도 전건이 초록**임이
드러났다. **「코드가 맞다」와 「그 맞음이 게이트로 잠겨 있다」는 다른 물음**이고, 후자는 **변이로만**
답해진다. 두 레인의 판정이 서로 모순이 아니라 **다른 층**이다 — 그 사실을 여기 적어 둔다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-27**(**F-1, HIGH — 재는 조립이 배포되는 조립이 아니다**) | HTTP test 가 위협 모델 **(a)·(c)** 를 **production 조립**(`BidVectorApplication`)에 대해 재도록 고친다. 지금 넷 전부 `HttpTestApplication`(test 전용)을 띄우고 **필터 등록을 다시 선언**하며, `BidVectorApplication` 을 부팅하는 test 가 **0** 이다. **닫힘 판정은 오직 이것** — **production 필터 bean 을 삭제했을 때 전건 `check` 가 RED** 인가 | verifier 실측: ① production `urlPatterns` 를 아무것도 안 맞는 패턴으로 좁힘 → http test **exit 0** ② **production 자격증명 필터 bean 삭제(9줄) → `./gradlew check` BUILD SUCCESSFUL** — **배포 앱이 무인증으로 열려도 전건 초록**이다 ③ `main()` 의 디스패치 속성 둘 제거 → exit 0, 그래서 `HttpTestSupport` 의 「드리프트가 나면 D-6A1-21 test 가 곧바로 실패한다」는 **거짓으로 실측**됐다. **기제 자체는 건전하다**(새 controller + **test 조립** 필터 축소는 RED) — **재는 대상이 틀렸을 뿐이다.** 이 저장소가 「게이트는 산출물이다」로 적어 둔 것의 가장 비싼 판이다 |
| **D-6A1-28**(**F-2, HIGH — 게이트가 공허해졌다**) | `app/build.gradle.kts` 에서 **`jar` 를 다시 켠다**(`bootJar` 와 공존). `jarContentGate` 의 `entries` 가 **0 → 20** 으로 복구되는지 실측으로 확인한다. 「파일명 충돌」이라 적은 주석을 **사실로 정정**한다 | base `entries=1/verified=1` → head **`entries=0`/verified=20**. **`bootJar` 를 켜고 `jar` 를 끈 in_scope 편집의 부작용**으로 게이트가 **아무것도 안 보게** 됐다 — 배포물은 `bootJar`(우리 클래스 23개)인데 게이트가 그것을 안 보고 `check` 는 만들지도 않는다. 「파일명 충돌」은 **실측과 다르다**: 둘 다 켜면 `app-plain.jar`+`app.jar` 가 공존하고 게이트가 복구된다(한 줄 처방). **초록인 게이트와 아무것도 안 보는 게이트는 구별되지 않는다** |
| **D-6A1-29**(**F-3, HIGH — 상수 시간 게이트가 클래스 하나에 걸려 있다**) | `ConstantTimeComparisonStructureTest` 를 **패키지 전체 class 파일**(`walkTopDown()`)에 대해 돌린다 — **같은 slice 의 자매 게이트 `AuditAdapterDependencyTest` 와 같은 형태로.** 닫힘: `doFilter` 조건에 단락 비교(`presented != expected ||`)를 되살렸을 때 RED | 지금 술어는 `OperatorCredentialFilterKt.class`(**파사드**)만 본다. 단락을 넣으면 **타이밍 차가 그대로 되살아나는데 http test 16건 전부 초록**이고, `OperatorCredentialFilter.class` 상수 풀에는 `Intrinsics.areEqual` 이 **2건** 남는다(javap 확인). **한 slice 안에서 자매 게이트 하나는 구조로 닫고 하나는 안 닫았다** — 6F-5-a r2 가 HIGH 를 받은 「술어의 범위가 계약의 선언보다 좁다」의 **재발**이고, 이번엔 **선례가 옆 파일에 있었는데도** 그랬다 |
| **D-6A1-30**(**HIGH — 깊이 방어가 배열 안 object 를 못 본다. 두 레인이 독립으로 수렴**) | `OpenApiContractTest` 의 중첩 검사 **둘 다** 고친다 — 정적(`collectNestedObjectProperties`)은 `type: array, items: {type: object}` 를, 런타임은 값이 `List<Map>` 인 경우를. 닫힘: 스키마에 object 배열 필드를 넣어 RED | `contract-keeper` 와 `code-reviewer` 가 **서로 모른 채 같은 자리**를 짚었다. 런타임 쪽은 `values.none { it is Map }` 이라 값이 `List<Map<*,*>>` 이면 `it` 이 List 라 **false** 다. 지금 스키마엔 없어 즉시 위반은 아니나 **D-6A1-20 이 선언한 보장 범위보다 실제 커버리지가 좁다.** **팀장 처방의 사각이다** — 내가 「중첩」을 **한 가지 형태로만** 상상했다 |
| **D-6A1-31**(migration 수정 1 — 신설 CHECK 본문) | `api_request_audit` 의 CHECK 둘(`status_code BETWEEN 100 AND 599`·`duration_ms >= 0`)을 **표 범위 본문 집합 등식**으로 잠근다(6F-5-a D-6F5-21 의 종점 형태). 개수 축만으로는 제자리 `OR TRUE` 항진명제화를 못 잡는다 | `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` 가 **이 slice 의 신설 표에서 재현**됐다. 그 OPEN 은 「**남의 표**라 안 고친다」로 넘긴 것이고, **이번 둘은 이 slice 자신의 표**라 같은 논리로 **닫는다** |
| **D-6A1-32**(contract 확인 불가 둘 — 닫는다) | `OpenApiContractTest` 의 spec-map 대조에 **500** 을 넣고, `InvalidStoredStrategyException` 전용 500 분기를 때리는 test 를 더한다 | 지금 500 은 `RequestAuditFilterTest` 가 code/message 만 개별 확인하고 **계약 대조 경로에는 없다**. 「계약에 적혀 있는데 대조는 안 한다」가 D-6A1-8(단일 출처)의 구멍이다 |
| **D-6A1-33**(`provenanceLabel` 중복 — **고치지 않는다**, 근거를 사실로) | `ProvenanceCodec` 가시성을 넓히지 **않는다**. 중복을 유지하고 **그 이유를 알려진 제한에 근거와 함께** 적는다 | `code-reviewer` 가 「가시성만 넓히면 제거 가능」이라고 더 값싼 대안을 냈고 그 지적은 옳다. 그러나 **표현 계층의 라벨 하나를 위해 공개 표면을 영구히 넓히는** 거래다. **양쪽 다 sealed 타입에 대한 소진 `when`** 이라 **drift 는 컴파일러가 잡는다** — 이것은 **안전한 중복**이다. D-6F5-11(「회피가 필요하지 않았다」)과 다른 자리다: 거기서는 위임이 **표면을 안 넓혔다** |
| **D-6A1-34**(F-9 — `const val` OPEN 의 범위를 넓힌다) | `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` 의 문면을 **「모든 컴파일 시간 상수 참조」**로 고친다 — `object` const · **top-level** const · **다른 모듈 companion** const 셋 다 미검출임이 실측됐다(실제 정책 상수로도 재현) | 등재 문면이 실측보다 **좁았다.** 좁게 적은 OPEN 은 받는 레인이 **그 좁은 범위만 닫게** 만든다 |

**장부층(막지 않으나 같은 라운드에 닫는다)** — **F-4** rollback 목록이 in_scope `milestone-6.md` 를 빠뜨렸다
(생성 명령 범위 밖 — 유효성 확인 명령도 같은 누락) · **F-5** rollback **실행 블록이 `--source=1881c82c` 고정
SHA** 라 **D-6A1-15 문면 위반**이다(서술은 정의인데 실행은 고정 — **서술과 실행이 갈리면 실행이 이긴다**) ·
**F-6** rollback ② 수치 미재현(문서 D 17 · 재현 **D 18**) · **F-7 + code-reviewer LOW** production KDoc 이
**존재하지 않는 test 둘**(`RequestAuditFilterDispatchTest`·`StrategyReadResponseIsFlatTest`)을 근거로 인용 ·
**F-8** **「두 설정 둘 다 필요」는 거짓** — `throw-exception-if-no-handler-found` 만 빼면 GREEN 이고 지탱하는
것은 `add-mappings=false` **하나**다(**팀장이 이 거짓 주장을 운영자에게 전달했다 — 여기서 정정한다**) ·
**code-reviewer MEDIUM** D-6A1-21 ① 이 `shouldNotBeEmpty()` 로만 확인돼 **`/error` 포함을 직접 단언하지
않는다**(Boot 가 바뀌면 조용히 미검증으로 돌아간다 — 직접 단언으로).

**다음 slice 취약 자리로 등재만**(이 slice 가 고치지 않는다): `currency`·`vatTreatment`·`provenance` 문자열
필드에 **enum 제약이 없어 계약이 실제 고정값 집합보다 느슨하다** · `/{unmatched}` 경로가 **실 codegen 소비자
에게 진짜 path param 으로 오인될 수 있다**(지금은 D-6A1-8 수작성 방침이라 안전).

**verifier 가 확인한 닫힘** — 기계 전수가 손 목록이 아니고 수집 결과가 `/api/strategy`·`/error` 둘(Boot 자동
구성 `/error` 까지 401 대조에 든다) · 미매핑·인증실패·예외 **세 경로 전부 audit 행 정확히 하나** · fail-closed
는 **구현 보고와 다른 자리**에 심은 변이로도 RED · 신설 패키지 게이트 한 벌 **셋 다** 닫힘 · rollback 트리
동일성과 6F-4 줄 보존 양방향 확인 · evidence 규격 전건 통과.

## 계약 갱신 (10) — 수정 라운드 1 마무리 (2026-09-22, 팀장)

수정 라운드 1 에서 **HIGH 넷이 전부 변이로 닫혔다**(D-6A1-27·28·29·30 + migration 의 D-6A1-31).
특히 D-6A1-27 은 내가 지정한 **유일한 닫힘 판정** — **production 자격증명 필터 bean 삭제(8줄) → 전건
`check` BUILD FAILED** — 이 실제로 섰다. `jarContentGate` 는 `entries` **0/0 → 20/20** 으로 복구됐다.

**레인 사정(사실 기록)** — 구현 레인이 **연달아 둘 다 사용량 한도로 끊겼다**(앞 레인은 변이 원복 직전,
뒤 레인은 아래 두 항목을 남기고). 팀장이 그 둘을 대신 처리했고 **레인 교차를 사실로 선언한다**:
① `origin/main`(`00d49135`, 하네스 문서 정정) **병합** — 그 worktree 의 `CLAUDE.md` 에 **폐기된 술어**
(「실측 HEAD == 판정 SHA」)가 남아 있었고 **곧 verifier 가 그 파일을 읽을 참이었다**(낡은 문장으로 유효한
rollback 을 미검증으로 뒤집을 수 있었다) ② `rollback.md` 의 **사실 오류 한 문단** 정정(아래 D-6A1-36).

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-35**(production 제외 필터 — **이번 slice 는 유지, 그러나 「필요해서」가 아니다**) | `BidVectorApplication` 의 `@ComponentScan(excludeFilters = [ANNOTATION, SpringBootApplication])` 를 **이번 라운드에는 유지**한다. 다만 **기술적으로 불가피해서가 아니라 예산 때문**이라는 것을 계약에 적는다. **더 깨끗한 대안이 실재한다** — 받는 쪽은 **6A-2**(앱 이미지·entrypoint 를 확정하는 slice) | **팀장 실측**: `HttpTestApplication` 은 `app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt` 안, 패키지 `bidvector.app.http` 에 있다 — **production 스캔 루트(`bidvector.app`) 안**이다. 따라서 ⓐ 그 test 앱을 **`bidvector.app` 밖 패키지로 옮기거나** ⓑ `@SpringBootApplication` 을 떼면 **production 을 전혀 안 건드리고** 충돌이 사라진다. 즉 **production 양보는 피할 수 있었다.** 지금 유지하는 이유는 셋 — ① production 런타임에서는 **공집합**이다(배포 jar 에 test 클래스가 없다) ② 대안은 test 전용 이동이지만 **D-6A1-27 의 변이 실측 셋을 다시 돌려야** 한다 ③ **구현 레인이 둘 다 한도로 끊겨 지금 그 라운드를 살 예산이 없다.** **위험은 낮지만 0 이 아니다** — 필터가 넓어, 앞으로 스캔 루트 안에 `@SpringBootApplication` 이 생기면 **조용히 제외**된다 |
| **D-6A1-36**(rollback.md 의 사실 오류 — 정정하고 **남긴다**) | 「`origin/main` 이 전진했지만 `merge-base` 는 그대로 **`1881c82c`** 를 낸다」는 문장이 흡수 직후 **거짓**이 됐다(흡수 뒤엔 `00d49135`). 숫자를 빼고 **성질**로 다시 적되, **그 일이 있었다는 사실 자체를 지우지 않고 남긴다** | **F-5 가 고친 교훈이 한 층 위에서 그대로 재현됐다** — **실행문(`$(git merge-base …)`)은 살아남고 그것을 설명하던 산문이 죽었다.** 정의는 낡지 않았고 **정의를 설명하려고 박아 둔 숫자**가 낡았다. 이 저장소가 「낡는 좌표 금지」로 이름 붙인 것의 **가장 얄궂은 판**이라 교훈으로 보존할 값이 있다 |

**팀장이 대신 실측한 것** — F-5 수정이 **시험대를 통과했다**: 병합으로 merge-base 가 `1881c82c` → `00d49135`
로 움직였는데 실행 블록의 `$(git merge-base HEAD origin/main)` 이 **새 값을 정확히** 냈다(고정 SHA 였다면
지금 틀렸을 자리다). **rollback 유효성도 새 술어로 확인** — `git diff --name-only 440a7286..b2e89b6d --
<되돌림 대상 경로 전부>` **빈 출력**이므로 `실측 HEAD: 440a7286` 은 **그대로 유효**하다. 이것이 2026-09-19
정정 술어의 **첫 실제 적용**이다.

**운영자에게 올리는 것** — D-6A1-35 는 **예산 제약이 만든 결정**이다. 대안을 사겠다면 test 전용 이동
한 번 + D-6A1-27 변이 셋 재실행이면 된다. 사지 않겠다면 위 위험을 안고 **6A-2 로 넘긴다.**

## 계약 갱신 (11) — 재판정 (2026-09-23, 팀장)

verifier 재판정 **`not-ready` — 산출물 HIGH 2**. 판정 대상 `1a45dfb0`, 동결 성립. 재작업 **2/5**.

**지정한 표적 일곱은 전부 닫혔다**(변이로 확인) — 특히 **D-6A1-27**: 필터 bean 삭제(−10줄) → **전건
`check` BUILD FAILED**. 「배포 앱이 무인증으로 열려도 초록」이 진짜로 닫혔고, 새 boot test 가 **조용히
건너뛰지 않고 실제로 도는지**까지 쟀다(XML `tests=2 skipped=0` · Docker 가용 실측). rollback 도 문서대로
실행해 **D 19·M 10 · 트리 동일성 · 되돌린 트리 전건 초록 · 양방향 줄 확인**, `$(git merge-base …)` 가
clone 에서 `00d49135` 를 정확히 산출했다.

**막는 것은 새로 고안한 우회 둘이고, 둘 다 같은 형태다 — 차단 목록에는 종점이 없다.**

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6A1-37**(F-1, HIGH — **판정 범위를 허용 목록으로 뒤집는다**) | `ConstantTimeComparisonStructureTest` 의 판정 대상을 `startsWith("OperatorCredentialFilter")` **이름 축에서 떼어**, **패키지 전체 class 파일**을 판정하되 `Intrinsics.areEqual` 을 써도 되는 클래스를 **명시 허용 목록**으로 둔다. **닫힘**: 비교를 같은 패키지 형제(`CredentialComparator` 류)로 추출하고 `==` 를 쓰면 **RED** | **D-6A1-29 는 발견만 넓히고 판정을 다시 좁혔다.** verifier 실측: 비교를 형제 클래스로 추출하고 `==` 사용 → 상수 풀에 `Intrinsics.areEqual` **1건**(javap)인데 **전건 `check` BUILD SUCCESSFUL**. **앞 F-3 의 「파일 하나」가 「이름 하나」로 옮겨갔을 뿐**이다 — `walkTopDown()` 으로 훑어 놓고 이름으로 거른 것이라 **범위가 실질적으로 그대로였다.** **차단·이름 목록과 허용 목록의 차이는 기본값이다** — 전자에서 새 코드는 **기본이 미판정**이고, 후자에서 새 코드는 **기본이 판정**이다. 그래서 허용 목록은 종점이 있고 이름 목록은 없다 |
| **D-6A1-38**(F-2, HIGH — **깊이 방어도 허용 목록으로 뒤집고 런타임을 재귀화**) | `OpenApiContractTest` 의 중첩 판정을 **「이런 형태는 중첩이다」 열거에서 「이런 평탄한 형태만 허용한다」로 뒤집는다.** 런타임 검사는 **재귀**로 바꾼다. **닫힘**: 정적 다섯 형태(`items:{type:object}`·`items:$ref`·직접 `$ref`·배열의 배열·`additionalProperties`) **전부 RED**, 런타임은 **깊이 2 이상도 RED** | **내가 물은 「또 다른 갈래」가 실제로 남아 있었다.** verifier 실측: 정적 5형태 중 **4개 GREEN**, 런타임은 **깊이 2부터 GREEN** — 실제 응답에 중첩 object 가 실려도 전건 초록이다. D-6A1-30 이 **배열 축 하나만** 더했기 때문이다. **이것은 D-6A1-37 과 같은 병**이고, 이 slice 안에서 **세 번째** 재발이다(F-3 → D-6A1-29 → F-1). **열거를 한 칸씩 늘리는 처방이 발산한다**는 것을 세 번 확인한 셈이라, 이번엔 **기본값을 뒤집어** 끝낸다 |
| **D-6A1-39**(L-2 — **처방한 레인이 자기 파일에서 그 결함을 이고 있었다**) | `scope.md` 의 `rollback:` YAML 블록을 F-5 시정과 정합시킨다 — 고정 SHA `c4d09cc` → **정의**(`git merge-base HEAD origin/main`), 낡은 파일 목록(`Sql.kt`·`V9`) → 현재 실제(`ApiAuditSql.kt`·`V15`·`jar` 재활성). **팀장이 자기 레인에서 고친다** | **F-5 는 `rollback.md` 의 실행 블록만 고쳤고 `scope.md` 의 요약 블록은 그대로였다.** 그 요약을 쓴 것도, F-5 를 처방한 것도 팀장이다 — **같은 결함이 처방자의 파일에 한 칸 옆으로 남아 있었다.** D-6A1-36(「실행문은 살아남고 산문이 죽었다」)과 **같은 축의 세 번째 사례**다 |

**장부층(막지 않음, 같은 라운드에)** — **L-1** `jarContentGate` 가 **빈 입력을 통과**시키는 성질은 그대로이고
(실측 exit 0) 그 사실이 **OPEN 으로 정식 등재되지 않았다** → `OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT` 을
정식 등재하고 **「빈 입력=통과」도 그 OPEN 에 포함**한다 · **L-3·L-4** OPEN 문면 부분 반영, 실측 HEAD 라벨
부정확(실측 자체는 유효).

**L-5 — 리뷰 규약이 값을 한 자리(사실 기록)**: verifier 가 `_workspace/m6-6a/12_privacy_gate.md` 를 찾지
못해 privacy-gate 판정을 **대조하지 못했다**(세션 재시작으로 소실). **그러나 그 판정은 살아 있다 — PR #41
의 코멘트로 남겼기 때문이다.** 「세션 안에만 있는 리뷰는 남에게는 없는 리뷰다」가 **실제로 구해 낸 경우**다.

**확인하지 않은 것으로 분리 — S-20**: `./tools/one-command-check.sh` 가 **exit 1**. Kotlin 전건 통과이고
Python 은 `1 failed / 957 passed` 인데 원인이 `ml-engine` wheel 게이트의 **PyPI `operation timed out`**,
즉 **샌드박스 네트워크 문제**다. 이 slice 는 `ml-engine` **무접촉**이고 그 test 도 미편집이다. verifier 가
**우회를 시도하지 않고** 통과로도 결함으로도 세지 않은 것이 옳다 — **네트워크 있는 CI 러너에서 재실행**해
확인한다(PR #41 의 CI 가 그 자리다).

**finding 이 아닌 것으로 닫힌 둘** — **D-6A1-35**(production 제외 필터): `main` 의 `@SpringBootApplication`
은 하나뿐이고 그것은 **primary source 로 등록돼 필터 영향 밖**이며, **배포물에 우리 클래스 24개·test 클래스
0개**로 **production 공집합이 실측 확인**됐다. **과잉 제외 없음.** 팀장의 우려는 측정으로 해소됐다 ·
**팀장 교차 셋**: `d240e9c2..HEAD` 산출물 델타 **빈 출력**, 병합은 `CLAUDE.md`+`change-history.md` 둘뿐 —
**산출물 무접촉 확인**.

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
| `OPEN-6A1-CONNECTION-POOL`(신설, D-6A1-18) | 커넥션 풀 없이(`PGSimpleDataSource`) production 에 배선한다 — 단일 운영자·저동시성이라 오늘 필요가 없으나 **이 상태로 운영에 나갈 수 없다**. 받는 쪽 **6C/6E** |
| `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`(신설, D-6A1-25) | **`const val` 참조는 컴파일러 인라인으로 상수 풀에서 사라져 바이트코드 의존 게이트가 못 본다**(실측). `evaluation`·`qualification`·`profile`·`audit` **네 게이트가 공유하는 사각**이라 한 slice 범위가 아니다. 받는 쪽 **하네스 레인** |

## 리뷰 레인

마이그레이션 파일(V9)이 생기므로 **`migration-reviewer`**, 인증·audit·개인정보 경로가 생기므로
**`privacy-gate`**, 공개 HTTP 계약(OpenAPI)이 생기므로 **`contract-keeper`** 가 추가로 붙는다(전역 규약 §3
세 줄 전부 해당). Codex 는 인증·인가·마이그레이션이 되돌리기 어려운 경로라 **대상**이 되지만 유료 호출이므로
리뷰 요청 시점에 범위·비용을 운영자에게 제시하고 승인받은 뒤에만 건다.

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-17 배선 우선(3) | **D-6A1-10 신설** — 전략 영속을 6F-1 로 이동(in_scope 에서 repository·row·test·전략 표 제거), 마이그레이션 번호 V9 → **V10**, 조회 endpoint 는 6F-1 소비(선행) | 운영자 지시 2026-09-17 「배선이 먼저」 · 배선 재고가 전략 영속을 첫 착수로 지목 |
| 2026-09-17 착수 | 초판 — D-6A1-1~8 | 운영자 결정 다섯(2026-09-16 둘 · 2026-09-17 셋) · 6A 입력 재고 · **포트 구현 실측이 6A 를 셋으로 가르게 했다** |
