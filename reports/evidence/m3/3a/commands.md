# 실행 명령과 종료 코드 — M3 / 3A

base `a9f1ff9c54b62fb7cfa859fff43dae7b943daf8f`, head `041d39ac11ae0090aba45cac580cb9c6d0e938ae`.
출력 전문은 남기지 않는다(evidence-pack 스킬 규격) — 핵심 결과 한 줄만.

## acceptance_commands (scope.md)

### S-0 — 격리 worktree `clean check`
## 2026-09-07T05:48:21Z
- cmd: `git worktree add --detach /tmp/m3-3a-s0-worktree HEAD && (cd /tmp/m3-3a-s0-worktree && ./gradlew --no-build-cache clean check)`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 337 actionable tasks, 전부 실행(캐시 없음). worktree 는 `git worktree remove --force` 로 정리했다.

### S-1 — 저장소 루트 `clean check`
## 2026-09-07T05:48:21Z
- cmd: `./gradlew --no-build-cache clean check`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 328 actionable tasks. (직전 라운드는 `suspend fun` 이 `kotlin.coroutines.Continuation`
  을 남겨 `app:test` 의 `ArchitectureGateTest`가 8건 위반으로 실패 — `Ports.kt` 세 인터페이스에서 `suspend` 제거로 수정,
  아래 「판단이 갈린 지점」 참고.)

### S-2 — 도메인 test
## 2026-09-07T05:48:21Z
- cmd: `./gradlew :procurement:test`
- exit: 0
- 핵심 결과: 52 tests, 0 failed, 0 error(`procurement/build/test-results/test/*.xml` 집계).

### S-3 — 도메인 게이트 5종
## 2026-09-07T05:48:21Z
- cmd: `./gradlew :procurement:domainApiTypeGate :procurement:domainSourceReferenceGate :procurement:typeShapeGate :procurement:sizeGate :procurement:cpdCheck`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL`, 5개 게이트 전부 통과(파일 500·함수 50·타입 멤버 30 한도, 상속 깊이 0·인터페이스 1 래칫, api-type 금지 타입 0건, CPD main 중복 0).

### S-4 — app conformance (조건부: authoritative ≥ 1 아님 → 기존 corpus 회귀 확인으로 기록)
## 2026-09-07T05:48:21Z
- cmd: `./gradlew :app:test --tests '*Conformance*'`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — `koneps-collection` dispatch 미등록(curator 승격 전, scope.md 대로) 상태에서
  기존 5축(rate-unit·money-basis·license·base-amount-provenance·floor-shortfall·floor-threshold·strategy) 회귀 무손상.

### S-5 — quality baseline
## 2026-09-07T05:48:21Z
- cmd: `./gradlew qualityBaseline`
- exit: 0
- 핵심 결과: `BUILD SUCCESSFUL` — `build/reports/quality-baseline/quality-baseline.md` 갱신.

### S-6 — mutation sweep(조건부: manifest 편집 시) → 해당 없음
- `fixtures/manifest.yaml` 은 이 slice out_of_scope(curator 레인 소유)이고 편집하지 않았다. 실행하지 않는다.

## 리뷰 요청 조건 점검

### clean-tree 게이트(in_scope 경로 개별 인자 + 양성 대조)
## 2026-09-07T05:48:21Z
- cmd: `git status --porcelain -- procurement/ shared-kernel/src/main/kotlin/bidvector/sharedkernel/NoticeRound.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Provenance.kt shared-kernel/src/main/kotlin/bidvector/sharedkernel/Rate.kt shared-kernel/src/test/kotlin config/quality/gate-tests.properties app/src/test/kotlin/bidvector/app/conformance decision/src/test/kotlin/bidvector/decision/FloorShortfallKernelTest.kt reports/evidence/m3/3a`
- exit: 0, 출력 없음(clean).
- 양성 대조: `procurement/.../NoticeId.kt` 에 한 줄을 추가한 뒤 같은 명령이 `M procurement/...` 를 냄을 확인, `git checkout --`
  로 복구 후 다시 clean 확인.

### 역방향 파급 grep — `Provenance.kt`·`Rate.kt` 를 `file:line` 으로 가리키는 문서
## 2026-09-07T05:48:21Z
- cmd: `grep -rn 'Provenance\.kt:[0-9]\|Rate\.kt:[0-9]' --include='*.md' --include='*.kt' --include='*.properties' . | grep -v /build/`
- exit: 1(매치 있음 — `reports/evidence/m1/1a/commands.md:818`), 확인: `BidRate.kt:8` 부분 문자열 우연 매치, `shared-kernel/.../Rate.kt`
  를 가리키지 않는다. 실제 영향 없음.

### secret 스캔
## 2026-09-07T05:48:21Z
- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m3/3a/`
- exit: 1(매치 없음 = 통과)
## 2026-09-07T05:48:21Z
- cmd: `git diff a9f1ff9c..HEAD -- procurement/ shared-kernel/ decision/src/test app/src/test/kotlin/bidvector/app/conformance config/quality/gate-tests.properties | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
- exit: 0(매치 3건), 확인: `provenanceFromToken`(함수명, 사전 존재) 2건 + `PageCursor.token`(불투명 페이지 토큰 필드명) 1건 —
  전부 벤진(비밀값 아님), 육안 확인 완료.

## 판단이 갈린 지점

- **`Ports.kt` 의 `suspend` 제거(D-3A-0/설계 검토 예상과 다름)**: 설계 검토 「구현 지침」은 `suspend fun` 인터페이스를
  "코루틴 취소 전파, `kotlin.coroutines` 는 stdlib 이라 도메인 외부 의존 0"으로 전제했다. 실측(S-1 1차 라운드)은
  `suspend fun` 이 컴파일된 시그니처에 `kotlin.coroutines.Continuation` 매개변수를 남기고, 그 패키지가
  `architecture-policy.properties` 의 domain 허용 목록(T-B/T-C)에 없어 `ArchitectureGateTest` 가 8건 위반으로
  거부함을 확인했다. `config/quality/architecture-policy.properties` 는 이 slice `out_of_scope`(하네스 소유)라
  게이트를 여는 대신 port 시그니처에서 `suspend` 를 걷어 정합시켰다 — 취소 전파는 3B(어댑터) 구현이 자기
  시그니처를 감싸 처리한다. `Ports.kt` KDoc 에 근거를 남겼다.
- **decision 모듈 test 파급(scope 갭)**: 위 커밋 메시지·team-lead 보고 참고. shared-kernel 타입 교체가
  `decision/src/test/.../FloorShortfallKernelTest.kt` 를 깨뜨렸는데 scope.md 「영향 test」절이 `shared-kernel/src/test`
  만 적어 이 파일을 놓쳤다. 인자 형태만(값 불변) 바꾸는 기계적 수정으로 판단해 별도 커밋으로 처리했다.
- **canonicalize 의 계층(procurement vs 어댑터)**: scope.md ⑧ 문면("콤마 금액 문자열...은 어댑터가 원문 unit을
  기록하며 변환하고 3A는 Money·Rate·Instant 타입만 받는다")과 ②·설계 검토 파일 분할("Canonicalize.kt: canonicalize
  한 함수")이 문면상 긴장한다. 이 slice는 후자(설계 검토가 명시한 파일 분할)를 정본으로 채택해 `canonicalize`
  (procurement)가 raw 문자열→canonical 타입 변환을 수행하도록 구현했다 — 그 변환이 필드 계약(도메인 정책 데이터)의
  `scale`/`sourceZone` 선언에 종속되므로, 정책을 소유한 도메인 옆에 두는 편이 §5.3 규율과 합치한다고 판단했다.
  어댑터(3B)의 몫은 raw 문자열 추출(원문 정직성)로 좁게 해석했다. 문면 정합은 문서 레인 확인 대상.
- **커밋 메시지 test 수 오기**: `feat(m3-3a): procurement 도메인` 커밋 메시지가 "92개 test"라 적었으나 실측(S-2)은
  52 tests(53 `@Test` 선언 중 1개 차이는 property test 집계 방식) 다. 정본은 이 문서의 S-2 실측이다.

## 알려진 제한

- `KONEPS_COLLECTION_POLICY`(main 운영 인스턴스)는 형태 + 최소 내용뿐이다 — 필드 계약·resultCode 범주·해석
  순서 값은 curator 승인 표(`reports/evidence/m3/3a/policy-values.md`, 아직 미도착) 수령 뒤 별도 커밋으로 채운다.
- `koneps-collection` corpus dispatch 는 미등록이다(9 case 전부 `insufficient-evidence`, D-M3-8 curator 승격 선행).
- `Notice`·`OpeningResult`·`QualificationText` 세 canonical fact(D-3A-1 (a) aggregate 경계)는 이 slice가 값
  객체·타입까지만 두고 aggregate root 조립은 두지 않았다(3D·4B, 「과잉 금지」).
- `parseSourceZonedInstant`(D-3A-4)의 `ASSUME_KST` 매핑은 `ZoneId.of("Asia/Seoul")` 리터럴을 쓴다 — 규칙 **선택**은
  정책 데이터(`dateInterpretation`)지만 그 규칙의 **의미**(문자열→ZoneId)는 상수다. `OPEN-3A-SOURCE-TZ` 는 그대로
  활성 남는다(공식 문서 확인 전).
- `resultCodeCategories`·`baseAmountResolutionOrder` 등 정책 슬롯의 **값**(curator 표 도착 전)은 이 slice가
  지어내지 않았다 — main 정책은 빈 목록/0 시간(inert placeholder)이다.
