# M3/3D — commands.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · head `63321586ded6b4f80b3e15eac975bc279355c555`
(verifier r2 수정 라운드 2, N-1·N-2·N-4·N-5 뒤 재고정 — 이 evidence 커밋 자신은 이 SHA를
가리킬 수 없다는 구조적 한계다, verifier r2 N-7). 전 명령 로컬 실측(2026-09-07/08,
macOS, Docker Desktop 29.5.3), 출력 전문은 담지 않는다(evidence 규격). 커밋 목록은
rollback.md.

verifier r1(수정 라운드 1) F-1~F-8 대응·verifier r2(수정 라운드 2) N-1~N-5 대응은
checklist.md 「finding 대응표」. **F-1~F-8·N-1·N-2·N-4·N-5는 이 head에서 닫혔다.
N-3은 설계상 열려 있는 알려진 제한(등재만), N-6은 이 evidence 자체에서 정정한다.**

| # | 명령 | 결과 |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | SUCCESS — head `6332158`에서 348 tasks 전부 executed(격리 worktree, 캐시 미재사용) |
| S-1 | `./gradlew --no-build-cache clean check` | SUCCESS — qualityBaseline 포함, 339 tasks(314 executed) |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'` | SUCCESS — test class 13종 전부 통과(아래 표) |
| S-3 | `./gradlew :adapters:test --tests '*PrecedenceMutationTest*'` | SUCCESS — 11 test |
| S-4 | `./gradlew :adapters:test --tests '*ItemAtomicityTest*'` | SUCCESS — 3 test |
| S-5 | `./gradlew :adapters:test --tests '*CleanMigrationTest*'` | SUCCESS — 13 test(D-3D-6 여덟 축 전부, 축7 트리거 목록에 `guard_notice_status` 추가) |
| S-6 | `./gradlew :adapters:moduleDependencyGate` | SUCCESS |
| S-7 | `./gradlew qualityBaseline`(및 위 `check`에 포함된 전 게이트) | SUCCESS |
| 추가(F-7 지시, 무변경) | `./gradlew :procurement:test` | SUCCESS — corpus 27/27·`ObservationKeyTest` 등 무변경 통과 |
| 추가(F-7 지시, 무변경) | `./gradlew :app:test --tests '*Conformance*'` | SUCCESS — `SharedKernelCorpusConformanceTest` |
| 추가(F-7 3B 몫 지시, 무변경) | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | SUCCESS — 「F-7 3B 몫」 test 포함 20건 전건 통과 |

## S-2 test class 13종 전건(persistence 패키지)

| class | test 수 | 요지 |
| --- | --- | --- |
| `CleanMigrationTest` | 13 | S-5 — 테이블·컬럼·타입·NOT NULL·UNIQUE·FK·트리거·CHECK 여덟 축(D-3D-6) |
| `PrecedenceMutationTest` | 11 | S-3 — F-1·N-1(값만/provenance만 변경 거부)·N-2(vat·status 위조 거부)·wide UPDATE 통과·존재 가드·CHECK 불변식·F-4 단일쌍 |
| `PrecedenceParityTest` | 1 | F-4 — Kotlin `mayOverwrite`/DB 가드 12쌍 parity(`PrecedenceMutationTest`에서 분리, sizeGate) |
| `ItemAtomicityTest` | 3 | S-4 — 항목 원자성, collection_run 별도 트랜잭션 |
| `NoticeVersioningTest` | 3 | ③ 멱등·revision+audit·OPEN-DIC-06(F-1 정상 fold 「(iii)」 경로도 이 test가 잰다) |
| `NoticeReconstructionTest` | 1 | N-5 — `NoticeStatus` 여섯 값 전부 예외 없이 왕복 복원(DB 없는 순수 test) |
| `RawAppendOnlyTest` | 8 | ②·F-6 — raw·notice_audit append-only 두 겹 방어 + 위조 INSERT 거부 + SECURITY DEFINER 정상 동작 |
| `ProvenanceAuthoritySeedTest` | 1 | DB seed ↔ `IS_AUTHORITATIVE` 완전 일치 |
| `OpeningQualificationRepositoryTest` | 6 | 최신 관측 우선 upsert + F-5 세 재현 + N-4(부분 관측 COALESCE) |
| `PersistenceAdapterDependencyTest` | 1 | persistence 패키지 domain import 경계 |
| `F2CollisionRegressionTest` | 1 | F-2 — "Aa"/"BB" 32비트 hashCode 충돌쌍이 이제 서로 다른 키·raw 2행을 낸다 |
| `NoticeFindRoundTripTest` | 1 | F-8 — 금액·범주·마감 전부 실은 notice의 `find()` 왕복 손실 없음 |
| `RawObservationSourceTextRoundTripTest` | 3 | F-7(3A·3D 몫) — sourceText 있으면 payload 바이트 동일(재직렬화 없음), 없으면 등재분 투영 대체, sourceText 다르면 다른 ObservationKey |
| **합계** | **53** | 전건 PASS, 0 skipped |

`procurement`: `ObservationKeyTest` 4 test(값 타입 불변식만) 전건 PASS — `AccountingTest`
등 3A 기존 test는 무변경 통과.

## F-3 변이 재재현(verifier r2 N-7 — checklist.md ⑧ 행이 가리키던 절이 이 절이다)

verifier r1이 실측한 세 변이를 `V1__schema.sql`에 동시 적용해 S-5가 다시 무는지 재확인
했다(원판 커밋 `e529256` 이후 이 절 자체가 빠져 있었다 — 이번에 채운다).

| 변이 | 적용 | 결과 |
| --- | --- | --- |
| COL-06 항등식 CHECK 삭제 | `collection_run`의 `CHECK (received = normalized + duplicate + dropped)` 제거 | `CleanMigrationTest`의 「축8 CHECK 개수」·「축8 부가」 2건 FAIL |
| 금액 정밀도 변경 | `notice.base_amount_won`을 `NUMERIC(20, 0)` → `NUMERIC(20, 4)` | 「축3 부가 — 금액 won 컬럼은 정확히 NUMERIC(20,0)이다」 FAIL |
| `notice_round` 형식 CHECK 삭제 | `notice.notice_round`의 `CHECK (notice_round ~ '^[0-9]{3}$')` 제거 | 「축8 부가」 FAIL(위와 같은 test, 별도 어설션) |

세 변이를 동시 적용한 파일로 `./gradlew :adapters:test --tests '*CleanMigrationTest*'`
실행 — 13건 중 3건 FAIL(위 표), 원본으로 복원 후 재실행 — 13건 전건 PASS, `git diff`
빈 출력(원상 복구 확인).

## F-7 3B 몫 — koneps test 신설/영향(변경 없음, 이전 라운드 기록 보존)

`KonepsOpenApiNoticeSourceTest`에 test 1건 추가(합계 20건, 전건 PASS) — 「불규칙 공백·키
순서를 둔 항목을 mock server로 서빙하고 `RawNoticeObservation.sourceText`가 그 바이트와
정확히 같다」를 잰다. `KonepsAdapterDependencyTest`·`ServiceKeyTest`는 무변경 통과.

## 직접 SQL 우회 재검증(verifier r2 지시 — §2 표 전건 + N-1·N-2 재확인)

격리 probe 컨테이너(`postgres:16.4`, docker run, gradle 밖에서 psql로 V1→V2→V3 적용)에
`SET ROLE bidvector_app`으로 재현 — Kotlin 경로를 전혀 타지 않는다.

| # | 시도 | 결과 |
| --- | --- | --- |
| (1) | `UPDATE notice SET base_amount_won = 1`(같은 key) | 거부 — 신선도 가드 |
| (2) | 권위 자리를 DERIVED_FROM_OPENING으로(값+provenance+새 관측 전부) | 거부 — 점유 가드 |
| **(2c)/N-1** | 값 동일 · provenance만 UNDECLARED로 강등(같은 key) | **거부**(회귀 수정 확인 — r1 판은 거부, r2 판은 통과했었다, 이 판은 다시 거부) |
| **N-2** | 값·provenance 동일 · `base_amount_vat`만 변경(같은 key) | **거부**(신설 — 축 판정에 동반 컬럼 포함) |
| **N-2** | `status`만 직접 위조(같은 key) | **거부**(신설 — `guard_notice_status`) |
| 존재 가드 | 값 있는 base_amount를 NULL 화 | 거부 |
| (3) | raw 없이 canonical INSERT | 거부 — FK 위반 |
| **N-3(알려진 제한, 그대로 열림)** | app 역할로 새 raw 행을 스스로 만들고 그 key로 값 변경 | **통과**(raw INSERT 권한이 정상 경로에 필요해 완전 방어는 경계 밖 — checklist.md 알려진 제한) |
| (4) | `revision = 1`로 되감기(새 관측 동반) | 무력화 — 트리거가 재계산 |
| 정상 fold(iii) | 값 변경 + PUBLISHED 유지 + 새 관측 | 통과 — revision 증가, audit 1행 추가 |
| (8) | app 역할로 `provenance_authority` UPDATE | 거부 — 권한 없음 |
| (9) | app 역할로 `TRUNCATE raw_observation` | 거부 — 권한 없음 |
| (10) | app 역할로 `notice_audit` DELETE | 거부 — 권한 없음 |
| 추가 | raw `UPDATE`/`DELETE`·`DISABLE TRIGGER`·`DROP FUNCTION`·`session_replication_role=replica`·`CREATE FUNCTION`·`DELETE FROM notice` | 전부 거부 — 권한 없음 |
| 추가 | 같은 PK로 재`INSERT` | 거부 — PK 중복 |
| **알려진 제한(그대로 열림)** | `deadline_at`만 직접 위조(같은 key) | 통과 — M-a 범위 밖(통화·과세·status만 다뤘다), checklist.md 알려진 제한에 등재 |

## Docker 부재 시 붉음 — 실측과 한계(변경 없음, 알려진 제한 유지)

`DOCKER_HOST` 무효화 + `~/.testcontainers.properties` 캐시 삭제 두 방법 모두 이 머신의
실제 Docker 소켓(`~/.docker/run/docker.sock`)을 다시 찾아 성공한다(Testcontainers 다중
전략 fallback) — 진짜 "Docker 완전 부재" 재현은 사용자의 살아 있는 Docker Desktop을
정지시켜야 해서 실행하지 않았다. 코드 검사(`assumeTrue` 0건, `@Testcontainers
(disabledWithoutDocker = false)`, `start()` 직접 호출)로 "SKIP이 아니라 실패"를 확인한다.
검증 레인도 별도 환경에서 같은 결론에 도달했다(verifier r1 §5·r2 「범위 밖 참고」).

## 부가 검증(evidence-pack 규격)

| 검사 | 명령 요지 | 결과 |
| --- | --- | --- |
| clean-tree 게이트(개별 pathspec) | `git status --porcelain -- <in_scope 경로 10개 개별 인자>` | 빈 출력(clean) |
| secret 스캔 | `grep -rn "PGPASSWORD\|password.*=.*['\"]" adapters/src/main/kotlin/bidvector/adapters/persistence/ procurement/.../RawObservation.kt` | main 소스 0건 |
| Docker 버전 | `docker --version` | `Docker version 29.5.3, build d1c06ef` |
| 컨테이너 이미지 태그 | `PersistenceTestSupport.POSTGRES_IMAGE = "postgres:16.4"` | 코드 상수, 출처 KDoc |
| 역방향 좌표 파급 | `grep -rn "V1__schema.sql:[0-9]\|V2__provenance_guard.sql:[0-9]\|CleanMigrationTest.kt:[0-9]" **/*.md` | 0건 |
| rollback 실행 가능성 | 임시 clone에서 `rollback.md`의 명령 실행 후 `./gradlew --no-build-cache clean check` | rollback.md 「실측」 절 |

## 하네스 레인 변경 확인

`git log --oneline 01ecbba..HEAD -- CLAUDE.md .claude/` — **없음**.
