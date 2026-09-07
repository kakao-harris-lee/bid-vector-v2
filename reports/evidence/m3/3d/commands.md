# M3/3D — commands.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · head `eb82bb814847e7e689554711bedc0d10fc51b0b7`
(F-7 3A·3D 몫 이어붙임 뒤 재고정 — 이전 실측 head `a79a6d6`). 전 명령 로컬 실측
(2026-09-07/08, macOS, Docker Desktop 29.5.3), 출력 전문은 담지 않는다(evidence 규격).
커밋 목록은 rollback.md.

verifier r1(수정 라운드 1) — F-1~F-8 대응은 checklist.md 「finding 대응표」. F-7은 3A·3D
몫만 이 head에서 닫혔고 3B 몫은 새 OPEN(checklist.md 「F-7」).

| # | 명령 | 결과 |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | SUCCESS — head `eb82bb8`에서 348 tasks 전부 executed(격리 worktree, 캐시 미재사용) |
| S-1 | `./gradlew --no-build-cache clean check` | SUCCESS — qualityBaseline 포함, 339 tasks(316 executed) |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'` | SUCCESS — test class 11종 전부 통과(아래 표) |
| S-3 | `./gradlew :adapters:test --tests '*PrecedenceMutationTest*'` | SUCCESS — 8 test |
| S-4 | `./gradlew :adapters:test --tests '*ItemAtomicityTest*'` | SUCCESS — 3 test |
| S-5 | `./gradlew :adapters:test --tests '*CleanMigrationTest*'` | SUCCESS — 13 test(D-3D-6 여덟 축 전부, raw_observation CHECK 4개로 갱신) |
| S-6 | `./gradlew :adapters:moduleDependencyGate` | SUCCESS |
| S-7 | `./gradlew qualityBaseline`(및 위 `check`에 포함된 전 게이트) | SUCCESS |
| 추가(F-7 지시) | `./gradlew :procurement:test` | SUCCESS — corpus 27/27·`ObservationKeyTest` 등 무변경 통과 |
| 추가(F-7 지시) | `./gradlew :app:test --tests '*Conformance*'` | SUCCESS — `SharedKernelCorpusConformanceTest` |
| 추가(F-7 지시) | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | SUCCESS — 3B 몫 미착수라 변경 없음(3건 클래스 그대로 통과) |

## S-2 test class 11종 전건(persistence 패키지)

| class | test 수 | 요지 |
| --- | --- | --- |
| `CleanMigrationTest` | 13 | S-5(F-3 뒤) — 테이블·컬럼·타입·NOT NULL·UNIQUE·FK·트리거·CHECK 여덟 축 |
| `PrecedenceMutationTest` | 8 | S-3(F-1·F-4 뒤) — 값만 변경·비권위→비권위 거부, Kotlin/DB 술어 12쌍 일치, 존재 가드, CHECK |
| `ItemAtomicityTest` | 3 | S-4 — 항목 원자성, collection_run 별도 트랜잭션 |
| `NoticeVersioningTest` | 3 | ③ 멱등·revision+audit·OPEN-DIC-06 |
| `RawAppendOnlyTest` | 8 | ②·F-6 — raw·notice_audit append-only 두 겹 방어 + 위조 INSERT 거부 + SECURITY DEFINER 정상 동작 |
| `ProvenanceAuthoritySeedTest` | 1 | DB seed ↔ `IS_AUTHORITATIVE` 완전 일치 |
| `OpeningQualificationRepositoryTest` | 5 | 최신 관측 우선 upsert + F-5 세 재현(직접 SQL 위조 거부) |
| `PersistenceAdapterDependencyTest` | 1 | persistence 패키지 domain import 경계 |
| `F2CollisionRegressionTest` | 1 | F-2 — "Aa"/"BB" 32비트 hashCode 충돌쌍이 이제 서로 다른 키·raw 2행을 낸다 |
| `NoticeFindRoundTripTest` | 1 | F-8 — 금액·범주·마감 전부 실은 notice의 `find()` 왕복 손실 없음 |
| `RawObservationSourceTextRoundTripTest` | 3 | F-7(3A·3D 몫) — sourceText 있으면 payload 바이트 동일(재직렬화 없음), 없으면 등재분 투영 대체, sourceText 다르면 다른 ObservationKey |
| **합계** | **47** | 전건 PASS, 0 skipped |

`procurement`: `ObservationKeyTest` 4 test(값 타입 불변식만, F-2 뒤 유도 규칙 제거) 전건
PASS — `AccountingTest` 등 3A 기존 test는 무변경 통과.

## 직접 SQL 우회 재검증(F-1·F-4·F-5·F-6 뒤, verifier §11 지시)

격리 probe 컨테이너(`postgres:16.4`, docker run, gradle 밖에서 psql로 V1→V2→V3 적용)에
`SET ROLE bidvector_app`으로 재현 — Kotlin 경로를 전혀 타지 않는다.

| # | 시도 | 결과 |
| --- | --- | --- |
| (1)/F-1 | `UPDATE notice SET base_amount_won = 1`(provenance·observation_key 그대로) | 거부 — 「새 observation_key(새 관측)를 동반해야 한다」 |
| F-4 | 비권위(FILLED_FROM_BUDGET_KEY)를 다른 비권위(DERIVED_FROM_OPENING)로, 값+provenance+새 observation_key 전부 | 거부 — 「권위 있는 유입만 값을 바꿀 수 있다」 |
| (2) | 권위 자리를 DERIVED_FROM_OPENING으로(값+provenance+새 관측 전부) | 거부 — 동일 |
| 존재 가드 | 값 있는 base_amount를 NULL 화 | 거부 |
| (3) | raw 없이 canonical INSERT | 거부 — FK 위반 |
| (4) | `revision = 1`로 되감기(새 관측 동반) | 무력화 — 트리거가 2로 재계산 |
| (8) | app 역할로 `provenance_authority` UPDATE | 거부 — 권한 없음 |
| (9) | app 역할로 `TRUNCATE raw_observation`/`notice` | 거부 — 권한 없음 |
| (10) | app 역할로 `notice_audit` DELETE | 거부 — 권한 없음 |
| F-6 | app 역할로 `notice_audit` 위조 INSERT | 거부 — 권한 없음(INSERT 제거) |
| F-5 | `UPDATE opening_result SET derived_base_amount_won = 1`(같은 observation_key) | 거부 — 신선도 가드 |
| 추가 | app 역할로 `SET session_replication_role = 'replica'` | 거부 — 권한 없음 |

## Docker 부재 시 붉음 — 실측과 한계(변경 없음, 알려진 제한 유지)

`DOCKER_HOST` 무효화 + `~/.testcontainers.properties` 캐시 삭제 두 방법 모두 이 머신의
실제 Docker 소켓(`~/.docker/run/docker.sock`)을 다시 찾아 성공한다(Testcontainers 다중
전략 fallback) — 진짜 "Docker 완전 부재" 재현은 사용자의 살아 있는 Docker Desktop을
정지시켜야 해서 실행하지 않았다. 코드 검사(`assumeTrue` 0건, `@Testcontainers
(disabledWithoutDocker = false)`, `start()` 직접 호출)로 "SKIP이 아니라 실패"를 확인한다.
검증 레인도 별도 환경에서 같은 결론에 도달했다(verifier r1 §5).

## 부가 검증(evidence-pack 규격)

| 검사 | 명령 요지 | 결과 |
| --- | --- | --- |
| clean-tree 게이트(개별 pathspec) | `git status --porcelain -- <in_scope 경로 9개 개별 인자>` | 빈 출력(clean) |
| clean-tree 게이트(F-7 재검, `RawObservation.kt` 포함) | `git status --porcelain -- <경로 7개, RawObservation.kt 포함>` | 빈 출력(clean) |
| secret 스캔 | `grep -rn "PGPASSWORD\|password.*=.*['\"]" adapters/src/main/kotlin/bidvector/adapters/persistence/ procurement/.../RawObservation.kt` | main 소스 0건 |
| Docker 버전 | `docker --version` | `Docker version 29.5.3, build d1c06ef` |
| 컨테이너 이미지 태그 | `PersistenceTestSupport.POSTGRES_IMAGE = "postgres:16.4"` | 코드 상수, 출처 KDoc |
| 역방향 좌표 파급 | `grep -rn "V1__schema.sql:[0-9]\|V2__provenance_guard.sql:[0-9]\|CleanMigrationTest.kt:[0-9]" **/*.md` | 0건 |
| rollback 실행 가능성 | 임시 clone에서 `rollback.md`의 명령 실행 후 `./gradlew --no-build-cache clean check` | rollback.md 「실측」 절 |

## 하네스 레인 변경 확인

`git log --oneline 01ecbba..HEAD -- CLAUDE.md .claude/` — **없음**.
