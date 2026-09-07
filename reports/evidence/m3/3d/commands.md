# M3/3D — commands.md

base `01ecbba69e21e4b85ee8303416fe06a77b919fd6` · 코드 head `15f33251cb61435b5b16f47d0b6c3cf00bc5eba7`
(verifier r3 수정 라운드 3, P-1 뒤 재고정 — 이 evidence 커밋 자신은 이 SHA를 가리킬 수
없다는 구조적 한계다, verifier r2 N-7·r3 P-4). verifier r4는 `ready-for-review` — 신규
V-1~V-4는 운영자 결정으로 코드 수정 없이 등재만 하고 종결한다(checklist.md 참고). 이
커밋 자신도 `commands.md`·`checklist.md`·`rollback.md` 셋만 만진다. 전 명령 로컬 실측
(2026-09-08), 출력 전문은 담지 않는다(evidence 규격). 커밋 목록은 rollback.md.

**경과(진행 중 관측, 등재만)** — 이 라운드 작업 도중 본체 작업 트리에 3C 레인(`impl-3c`)의
미커밋 편집(신규 `extraction/**` 파일 다수, 3D in_scope에 속한 공유 파일 셋
`adapters/build.gradle.kts`·`gradle/libs.versions.toml`·`config/quality/gate-tests.properties`
포함, 셋 다 diff 확인상 내용은 전부 3C 것)이 있어 한때 본체에서 `clean check`가 3C WIP
때문에 성립하지 않았다 — 그래서 처음에는 격리 worktree(`15f3325`)로 S-0~S-7을 실측했다.
그 뒤 3C가 자신의 작업을 커밋했고(`b013eb6`~`1db93dd`), 3D 자신의 등재분(gate-tests.properties
의 persistence 15종 등)은 온전히 보존됐다 — 이제 본체 작업 트리에서 재실측해도 결과가
같음을 아래로 재확인했다(head `1db93dd`, 코드는 여전히 `15f3325` 그대로다).

verifier r1(수정 라운드 1) F-1~F-8·r2(수정 라운드 2) N-1~N-5·r3(수정 라운드 3) P-1
대응은 checklist.md 「finding 대응표」. **F-1~F-8·N-1·N-2·N-4·N-5·P-1은 이 head에서
닫혔다. N-3·deadline_at·business_category·V-1·V-2는 설계상/후속으로 열려 있는 알려진
제한(등재만), N-6·P-2·P-3·V-3·V-4는 문서 정정/서술 등재다.**

| # | 명령 | 결과 |
| --- | --- | --- |
| S-0 | `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)` | SUCCESS — head `1db93dd`에서 348 tasks 전부 executed(격리 worktree, 3C 커밋 뒤 재확인) |
| S-1 | `./gradlew --no-build-cache clean check`(본체 작업 트리, 3C 커밋 뒤) | SUCCESS — qualityBaseline 포함, 339 tasks(314 executed) |
| S-2 | `./gradlew :adapters:test --tests 'bidvector.adapters.persistence.*'`(본체) | SUCCESS — test class 15종 전부 통과(아래 표) |
| S-3 | `./gradlew :adapters:test --tests '*PrecedenceMutationTest*'` | SUCCESS — 11 test |
| S-4 | `./gradlew :adapters:test --tests '*ItemAtomicityTest*'` | SUCCESS — 3 test |
| S-5 | `./gradlew :adapters:test --tests '*CleanMigrationTest*'` | SUCCESS — 12 test(축7은 `CleanMigrationTriggerTest`로 분리) |
| S-6 | `./gradlew :adapters:moduleDependencyGate` | SUCCESS |
| S-7 | `./gradlew qualityBaseline`(및 위 `check`에 포함된 전 게이트) | SUCCESS |
| 추가(F-7 지시, 무변경) | `./gradlew :procurement:test` | SUCCESS — corpus 27/27·`ObservationKeyTest` 등 무변경 통과 |
| 추가(F-7 지시, 무변경) | `./gradlew :app:test --tests '*Conformance*'` | SUCCESS — `SharedKernelCorpusConformanceTest` |
| 추가(F-7 3B 몫 지시, 무변경) | `./gradlew :adapters:test --tests 'bidvector.adapters.koneps.*'` | SUCCESS — 20 test 전건 통과 |

## S-2 test class 15종 전건(persistence 패키지)

| class | test 수 | 요지 |
| --- | --- | --- |
| `CleanMigrationTest` | 12 | S-5 — 테이블·컬럼·타입·NOT NULL·UNIQUE·FK·CHECK 일곱 축(D-3D-6, 축7은 분리) |
| `CleanMigrationTriggerTest` | 2 | S-5 축7 — 트리거 목록 + P-1 가드 인자(동반 컬럼) 완전성 대조(`CleanMigrationTest`에서 분리, sizeGate) |
| `PrecedenceMutationTest` | 11 | S-3 — F-1·N-1(값만/provenance만 변경 거부)·N-2(vat·status 위조 거부)·wide UPDATE 통과·존재 가드·CHECK 불변식·F-4 단일쌍 |
| `PrecedenceParityTest` | 1 | F-4 — Kotlin `mayOverwrite`/DB 가드 12쌍 parity |
| `PrecedenceLabelColumnTest` | 5 | P-1 — provenance detail·estimated source key·floor_rate origin 위조 넷 거부 + 정상 fold 라벨 변경 통과 |
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
| **합계** | **59** | 전건 PASS, 0 skipped |

`procurement`: `ObservationKeyTest` 4 test(값 타입 불변식만) 전건 PASS — `AccountingTest`
등 3A 기존 test는 무변경 통과.

## 직접 SQL 우회 재검증(verifier r3 지시 — 전건 재실측, V-1·V-2 두 행은 verifier r4가 독립 실측한 신규 finding 등재)

격리 probe 컨테이너(`postgres:16.4`, docker run, gradle 밖에서 psql로 V1→V2→V3 적용, head
`15f3325`)에 `SET ROLE bidvector_app`으로 재현 — Kotlin 경로를 전혀 타지 않는다.

| # | 시도 | 결과 |
| --- | --- | --- |
| (1) | `UPDATE notice SET base_amount_won = 1`(같은 key) | 거부 — 신선도 가드 |
| (2c)/N-1 | 값 동일 · provenance만 UNDECLARED로 강등(같은 key) | 거부 — 점유 가드(회귀 수정 유지) |
| N-2 | 값·provenance 동일 · `base_amount_vat`만 변경(같은 key) | 거부 — 축 판정에 동반 컬럼 포함 |
| N-2 | `status`만 직접 위조(같은 key) | 거부 — `guard_notice_status` |
| **P-1** | `base_amount_provenance_detail`만 위조(같은 key) | **거부**(신설) |
| **P-1** | `floor_rate_origin_kind`만 위조(같은 key) | **거부**(신설) |
| **P-1** | `floor_rate_origin_detail`만 위조(같은 key) | **거부**(신설) |
| (2) | 권위 자리를 DERIVED_FROM_OPENING으로(값+provenance+새 관측 전부) | 거부 — 점유 가드 |
| 정상 fold(iii) | 값 변경 + PUBLISHED 유지 + 새 관측 | 통과 — revision 증가, audit 행 추가 |
| (3) | raw 없이 canonical INSERT | 거부 — FK 위반 |
| (8) | app 역할로 `provenance_authority` UPDATE | 거부 — 권한 없음 |
| (9) | app 역할로 `TRUNCATE raw_observation` | 거부 — 권한 없음 |
| (10) | app 역할로 `notice_audit` DELETE | 거부 — 권한 없음 |
| 추가 | app 역할로 `notice_audit`에 위조 행 직접 INSERT | 거부 — 권한 없음 |
| **N-3(알려진 제한, 열려 있음)** | app 역할이 새 raw 행을 스스로 만들고 그 key로 값 변경 | 통과(raw INSERT 권한이 정상 경로에 필요) |
| **알려진 제한(열려 있음)** | `deadline_at`만 직접 위조(같은 key) | 통과 — 가드 축 밖 |
| **알려진 제한(열려 있음)** | `business_category_code`만 직접 위조(같은 key) | 통과 — 가드 축 아님(P-3 뒤 사유 정정, checklist.md) |
| **V-1(신규, 알려진 제한 — 열려 있음)** | `opening_result.derived_base_amount_currency`/`_vat`만 직접 위조(같은 key) | 통과 — P-1이 `notice` 넷에만 라벨 동반 컬럼을 넣었다, `opening_result` 트리거는 미확장(후속 소폭으로 등재, checklist.md) |
| **V-2(신규, 알려진 제한 — 열려 있음)** | `notice_number`/`notice_round`(PK) 이동, `created_at`·`observation_key` 단독 교체 | 통과 — 권한 경계, 가드 축 아님(경계 문장에 명시, checklist.md) |

## Docker 부재 시 붉음 — 실측과 한계(변경 없음, 알려진 제한 유지)

`DOCKER_HOST` 무효화 + `~/.testcontainers.properties` 캐시 삭제 두 방법 모두 이 머신의
실제 Docker 소켓(`~/.docker/run/docker.sock`)을 다시 찾아 성공한다(Testcontainers 다중
전략 fallback) — 진짜 "Docker 완전 부재" 재현은 사용자의 살아 있는 Docker Desktop을
정지시켜야 해서 실행하지 않았다. 코드 검사(`assumeTrue` 0건, `@Testcontainers
(disabledWithoutDocker = false)`, `start()` 직접 호출)로 "SKIP이 아니라 실패"를 확인한다.

## 부가 검증(evidence-pack 규격)

| 검사 | 명령 요지 | 결과 |
| --- | --- | --- |
| clean-tree 게이트(개별 pathspec) | `git status --porcelain -- <in_scope 경로 10개 개별 인자>` | 빈 출력(clean) — 3C가 자신의 작업을 커밋한 뒤(§상단 「경과」) 본체 작업 트리에서 재확인 |
| secret 스캔 | `grep -rn "PGPASSWORD\|password.*=.*['\"]" adapters/src/main/kotlin/bidvector/adapters/persistence/ procurement/.../RawObservation.kt` | main 소스 0건 |
| Docker 버전 | `docker --version` | `Docker version 29.5.3, build d1c06ef` |
| 컨테이너 이미지 태그 | `PersistenceTestSupport.POSTGRES_IMAGE = "postgres:16.4"` | 코드 상수, 출처 KDoc |
| 역방향 좌표 파급 | `grep -rn "V1__schema.sql:[0-9]\|V2__provenance_guard.sql:[0-9]\|CleanMigrationTest.kt:[0-9]" **/*.md` | 0건 |
| rollback 실행 가능성 | 임시 clone에서 `rollback.md`의 명령 실행 후 `./gradlew --no-build-cache clean check` | rollback.md 「실측」 절 |

## 하네스 레인 변경 확인

`git log --oneline 01ecbba..HEAD -- CLAUDE.md .claude/` — **없음**.
