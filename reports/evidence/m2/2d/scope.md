# Slice 계약 — M2 / 2D · 생성·호환성·provider test (게이트 증명) — **초안, 구현 전**

> **지위**: M1/1E 병행 중에 세션 모델이 쓴 **계약 초안**이다. 구현·gradle·의존성 추가·fixture 편집은 하지 않았다.
> 착수는 2A~2C 승인 뒤 운영자 지시로 한다. 2A 가 생성 배선을 선행 흡수했으므로(D-M2-2 (a)) 2D 의 정체성은 **「게이트가
> 실제로 잡는다」는 증거**다(`ADR 0003` D-6 · `milestone-2.md` 완료 조건 「compatibility gate가 실제 breaking mutation을 잡는지」).

```yaml
milestone: m2
slice: 2d-generation-compat-and-provider-tests
base_sha: c9022d9989c4b2a09cf8b9ff94795176dc5dc00c   # 초안 작성 시점 HEAD — **2A~2C 승인 뒤 착수 시 재고정**
head_sha: 리뷰 시점의 HEAD
in_scope:
  - contracts/buf.yaml                                # breaking 규칙(FILE 카테고리) + against 기준(승인 태그)
  - contracts/tools/**                                # breaking mutation 표본 생성·실행 스크립트(mutation 은 저장소에 커밋하지 않고 임시 디렉터리에서 만든다)
  - build-logic/**                                    # `contractGate` task: buf lint·breaking·generateProto 결정성(두 번 생성 diff 0)·생성물 비커밋 실측
  - config/quality/gate-tests.properties              # `gate.tests.adapters` 키(키는 Gradle 모듈 이름 규약 — 2D 의 손으로 쓴 test 는 전부 adapters 에 있다; 게이트 정의 편집, 사유를 evidence 에)
  - config/quality/contract-policy.properties         # 정책 데이터: max_message_bytes · 승인 태그 이름 규칙 · mutation 집합 목록과 최소 크기(매직넘버 금지)
  - adapters/src/test/kotlin/**                        # Kotlin 쪽 전부: unknown field·unknown enum·max payload·deadline·cancellation·round-trip(in-process grpc-testing) + 2B·2C consumer test 한 suite. `ml-contract` 는 생성물만(2A D-2A-0a·D-2A-6)
  - ml-engine/tests/**                                 # provider test: fake servicer 계약 준수 + 같은 testdata round-trip + async(grpc.aio) in-process
  - ml-engine/pyproject.toml                          # grpcio-testing dev 의존만(serving 런타임 아님)
  - contracts/testdata/**                             # canonical 바이트 + JSON 원본 + **breaking mutation 기대 결과 표**
  - .github/workflows/**                             # 조건부 — `contractGate` 는 `check` 안이라 등재 불요. **CI 에는 Python 툴체인이 없다**(setup-java + `gradlew check` 뿐) — Python 쪽(S-5)은 로컬 acceptance 이고 CI 등재는 Python 설치 단계 추가 결정(D-2D-4)
  - milestone-2.md                                    # 「Slice 2D」 착수 문단, **착수 시**
  - reports/evidence/m2/2d/**
out_of_scope:
  - 계약 내용의 변경                                    # 2D 는 2A~2C 의 .proto 를 바꾸지 않는다 — 게이트가 요구하는 수정이 나오면 멈추고 해당 slice 로
  - 실제 socket 을 여는 교차 언어 test 의 **CI 필수화**  # 로컬 실측 1회는 한다(S-6) — 상시 게이트로 둘지는 D-2D-3
  - 실제 client 배선(4D)·실제 servicer(5E)·circuit breaker(4D)
  - 성능·지연 측정                                      # OPEN-M2-DEADLINE-VALUES — 5E
  - mutation test 도구(pitest, OPEN-ADR-07)             # 여기서 말하는 mutation 은 **.proto 의 breaking mutation** 이지 코드 mutation 이 아니다
  - shared-kernel/**, 도메인 모듈, fixtures/**
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — contractGate 가 check 안에
  - "./gradlew contractGate"                                                                          # S-2 — Kotlin 쪽만: buf lint·breaking(against 승인 태그)·generateProto 결정성(두 번 생성 diff 0, build/ 안). Python 생성은 S-5 의 pytest 가 한다(Gradle check 밖)
  - "(cd contracts && ./tools/breaking-mutations.sh)"                                                 # S-3 — mutation 집합 전건이 buf breaking 에서 **실패**함을 증명(exit 0 = 전건 잡힘, 1 = 하나라도 통과, 2 = 도구 오류 — 스윕 규약)
  - "./gradlew :ml-contract:test :adapters:test --tests '*Contract*'"                                 # S-4 — Kotlin 쪽 unknown/max/deadline/cancel/round-trip/consumer
  - "(cd ml-engine && python -m pytest tests -q)"                                                     # S-5 — provider·round-trip·aio in-process
  - "./tools/contract-crosslang-smoke.sh"                                                            # S-6 — localhost socket 1회: Kotlin client(생성 stub) ↔ Python fake servicer, 로컬 실측(D-2D-3)
  - "./gradlew qualityBaseline"                                                                       # S-7
rollback: |
    **정본은 `reports/evidence/m2/2d/rollback.md`**(착수 시 작성). `contractGate` task 와 정책 파일·test 를 걷으면 2C 상태.
    게이트를 걷는 것은 계약을 걷는 것이 아니다 — .proto 와 생성물은 그대로.
```

작성: 2026-09-06, 세션 모델(Fable 5.1) 단독. 근거: `milestone-2.md` 「Slice 2D」·「설계 규칙」·「완료 조건」 · `ADR 0003` D-6·D-7 · `ADR 0007`
contract 층·래칫 · `ADR 0010` 초안 D-2·D-4·D-7·§6 · `milestone-1.md` 「완료 조건 — 게이트 회피 경계」(게이트를 test 로 표현할 때의 규율) ·
1A 의 `gateExecutionGate`·`config/quality/gate-tests.properties` 관례 · 브리프 2 스윕 종료 코드 규약(`data-extract.md` §6) · 조사 노트 02.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — **착수 시 재고정한 base 로 다시 낸다.** 초안 시점은 해당 없음.

---

## 이 slice 가 하는 일

`milestone-2.md` 「Slice 2D」 여섯 항목을 **게이트 하나(`contractGate`) + 증명 셋(breaking·의미·교차 언어)** 으로 낸다.

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **breaking gate 의 증명** — `contracts/tools/breaking-mutations.sh` 가 승인된 `.proto` 에 mutation 집합을 **임시 디렉터리**에서 하나씩 적용하고 `buf breaking --against <승인 태그>` 가 **전건 실패**함을 낸다. 집합(정책 데이터 `contract-policy.properties` 에 목록): 필드 삭제 · 필드 번호 변경 · 타입 변경(`int64 → string`) · enum 값 삭제 · enum 값 번호 변경 · `oneof` 에서 필드 빼기 · RPC 삭제 · RPC 스트리밍 전환 · 패키지 이름 변경 · `reserved` 번호 재사용 · `optional` 제거. 종료 코드는 스윕 규약(0/1/2). **잡히지 않는 mutation 이 하나라도 있으면 게이트는 증명되지 않은 것** | 완료 조건 「compatibility gate가 실제 breaking mutation을 잡는지」 · `ADR 0003` D-6 「게이트가 실제로 잡는다는 증거가 기준」 |
| ② | **호환 변경은 통과함도 증명** — 필드 추가·enum 값 추가·RPC 추가·메시지 추가는 `buf breaking` 통과(양성 대조). 게이트가 모든 변경을 막는 것이 아니라 breaking 만 막는다는 것 | ADR 0010 D-7 |
| ③ | **생성물은 VCS 밖, 생성은 결정적** — 2A D-2A-0a 로 생성물은 `build/generated/` 에만 있어 수동 편집이 **구조적으로 불가**하다. `contractGate` 는 (a) `generateProto` 가 `check` 의 의존임 (b) 두 번 생성한 산출물이 바이트 동일함(결정성 — protoc·플러그인 버전 리터럴 고정의 실측) (c) `git status --porcelain -- ml-contract` 가 빔을 낸다. Python 생성물도 VCS 밖(pytest fixture 가 `grpc_tools.protoc` 로 임시 생성) — **Gradle `check` 는 Python 을 부르지 않는다**(CI·리뷰 레인의 오프라인 worktree 에 Python 의존이 없다) | 「설계 규칙」 생성된 코드는 수동 편집하지 않는다 · 2A D-2A-0a |
| ④ | **unknown field / unknown enum** — Kotlin·Python 양쪽에서 (a) 정의 밖 필드 번호가 든 바이트를 파싱하면 **보존**되고(proto3 unknown field 보존) 응답으로 되돌아오지 않는다 (b) 정의 밖 enum 정수는 **거부**(2A ⑥ fail-closed — 파서는 통과시키므로 validation 층이 잡는다는 test). 두 성질은 다르다 — (a) 는 전방 호환, (b) 는 의미 안전 | 2D 「unknown field/enum」 · 2A ⑥ |
| ⑤ | **max payload** — `max_message_bytes` 정책 값(근거: 2B 요청의 상한 = `CompetitionSample` 최대 수 × 크기 — 착수 시 계산해 evidence 에) 을 양쪽 channel/server 옵션에 같은 값으로 두고, 초과 메시지가 `RESOURCE_EXHAUSTED` 로 거부됨을 양쪽 test | 완료 조건 「최대 메시지 크기와 deadline 정책이 근거와 함께 문서화됨」 |
| ⑥ | **deadline / cancellation** — Kotlin in-process(grpc-testing) fake servicer 가 지연을 흉내내고 (a) deadline 초과가 `DEADLINE_EXCEEDED` 로 client 에 도달 (b) coroutine 취소가 servicer 의 취소 관측으로 이어져 **자원 해제**가 일어남(조사 02 — `cancelled()` 플래그 폴링이 아니라 해제 훅/카운터로 검증, grpc/grpc#36193) (c) 재시도는 `retryable` 인 경우만 정책 상한 안에서(fake 가 `UNAVAILABLE` n회 뒤 성공) | 2D 「deadline, cancellation test」 · 완료 조건 「fake server 장애 시 retry 가능한 경우만 제한 횟수로 재시도」 · ADR 0010 D-2·D-4 |
| ⑦ | **round-trip** — 같은 `contracts/testdata/*.binpb` 를 양쪽이 읽어 canonicalization 뒤 바이트 동일 + `Rate.fraction` 정규형 동일(2A ⑦ 의 표본을 2B·2C 메시지로 확장). `Unmeasurable` 표본이 양쪽에서 `Unmeasurable` 로 남고 `0`·transport error 로 바뀌지 않음 | 완료 조건 「round-trip 결과가 canonicalization 후 일치」·「`Unmeasurable`가 transport error나 0으로 변환되지 않음」 |
| ⑧ | **consumer/provider(fake)** — 2B·2C 의 fake servicer test 를 한 suite 로 묶고, provider 쪽(Python)은 `grpc.aio` in-process 로 같은 규칙을 낸다. **교차 언어 socket 스모크 1회**(S-6): Python fake servicer 를 localhost 에 띄우고 Kotlin 생성 client 로 2B·2C 를 한 번씩 부른다 — 「요청만으로 Python 이 DB 조회 없이 계산 가능」의 형태 증명(fake 는 DB 를 갖지 않는다) | 2D 「fake servicer를 이용한 consumer/provider test」 · 완료 조건 「요청만으로 Python serving이 DB 조회 없이 계산 가능」 |
| ⑨ | **게이트 등재** — `gate.tests.adapters` 키(모듈 이름 규약)로 1A 의 `gateExecutionGate` 가 2D test 의 실행을 강제(제외·필터 우회 차단 — 1A 관례). `contractGate` 는 `check` 의 의존 | `milestone-1.md` 「완료 조건 — 게이트 회피 경계」 · 1A `gate-tests.properties` |

**만들지 않는 것**: 계약 내용 변경 · 실제 client/servicer · 성능 측정 · 코드 mutation test · CI 인프라 신설(정의가 없으면 로컬 `check` 가 게이트).

---

## 운영자 결정 필요 — 착수 전(D-2D-1~3) · 계약 고정(D-2D-4~6)

| ID | 물음 | 선택지 | 추천·근거 | 상태 |
| --- | --- | --- | --- | --- |
| **D-2D-1** | **`buf breaking` 의 `against` 기준.** | (a) **승인 태그(`contracts/v1-approved-<date>`) — 2A~2C 승인 시 운영자가 태그, 게이트는 그 태그 대비** (b) `origin/main` 대비 (c) 직전 커밋 대비 | **(a)** — (b)(c) 는 「승인된 계약」이 아니라 「마지막 커밋」을 기준으로 삼아 두 커밋에 걸친 breaking 을 못 잡고, 원격 main 이 뒤처진 이 저장소 운영(255 커밋 미push)에서 (b) 는 실질 무의미. 태그는 push 전에도 로컬에 있다. **태그 이름 규칙은 정책 데이터** | 착수 전 |
| **D-2D-2** | **생성물의 자리** — 2A D-2A-0a 가 「VCS 밖」으로 정했다. 2D 는 그 결정을 바꾸지 않고 ③ 의 결정성·비커밋 실측만 낸다 | — | 2A D-2A-0a (a) 로 닫힘 — (커밋 + 재생성 diff 0) 안은 1A 게이트 가족(`SourceLanguageGate`·`expected-source-sets`·java srcDirs)과 충돌해 기각(리뷰 r1). 오프라인 worktree 의 protoc·플러그인은 Maven 아티팩트라 RO 의존 캐시가 덮는다 — 착수 시 §4b 스모크로 실측 | 닫힘(2A) |
| **D-2D-3** | **교차 언어 socket 스모크(S-6)를 상시 게이트로 두는가.** | (a) **착수·리뷰 요청 시 로컬 실측 1회 + evidence 한 줄, 상시 게이트는 in-process 만** (b) `check` 에 포함 | **(a)** — socket 은 codex sandbox 에서 금지(codex-review-gate §4b 실측)이고 verifier 도 worktree 안에서 Python 환경을 세워야 한다. in-process 양쪽 test 가 계약 규칙을 덮고, socket 은 「연결된다」만 더한다. **M6 6C 통합 검증이 상시 자리** | 착수 전 |
| **D-2D-4** | **Python 쪽 test 의 CI 자리.** 저장소 CI 는 JDK 만 세운다 | (a) **로컬 acceptance(S-5)로만, CI 미등재 — 알려진 제한 등재, M5 5A 가 Python CI 를 세울 때 승계** (b) CI 에 Python 설치 + `grpcio-tools` 단계 추가(2D in_scope 확장) | **(a)** — Python 패키지 CI 는 5A(「Ruff, typecheck, pytest, import-linter … CI 에」)의 소유이고 2D 가 그 골격을 먼저 세우면 5A 와 두 자리. 리뷰 레인(네트워크 차단)도 Python 의존을 못 깐다 | 착수 전 |
| **D-2D-5** | mutation 집합은 `.proto` 를 **저장소에 두지 않고** 스크립트가 임시로 만든다 — 저장소에 breaking 인 `.proto` 가 있으면 buf 가 그것을 읽는다. 스크립트 종료 코드는 브리프 2 스윕 규약(0/1/2). 집합 최소 크기는 정책 데이터(빈 집합 exit 0 방지) | — | 계약 고정 |
| **D-2D-6** | `max_message_bytes` 는 정책 데이터이고 **양쪽이 같은 파일을 읽지 않는다**(Python 은 Gradle 정책을 못 읽음) — 값 동일성은 **경계 쌍**으로 증명한다: 선언값 바로 아래 크기의 표본은 **양쪽에서 수용**되고 바로 위 표본은 **양쪽에서 거부**된다는 두 단언을 같은 testdata 로 건다. 초과 표본 하나의 거부만으로는 한계가 달라도 초록이라 값 동일성을 재지 못한다(리뷰 r1) | — | 계약 고정 |
| **D-2D-7** | 취소 검증은 플래그가 아니라 **자원 해제**(fake servicer 의 「계산 시작/중단」 카운터, Kotlin 의 채널 반환) — 조사 02 의 grpc/grpc#36193 | — | 계약 고정 |

---

## 위협 모델 — 2D 고유 경계 (게이트 slice — 1A·1A-b 와 같은 급, Phase 2.5 설계 검토 대상)

**(0) 경계 문장**: 2D 가 방어하는 것은 **계약 drift**(승인된 `.proto` 에서 의도치 않게 멀어짐)와 **의미 손실**(unknown·`Unmeasurable`·deadline 의 접힘)이다. 적대적 저자(게이트 정의 자체를 편집하는 사람)는 방어하지 않는다 — `milestone-1.md` 「완료 조건 — 게이트 회피 경계」와 같은 경계(`enabled=false`·`-x`·`Test.filter` 는 `gateExecutionGate` 가 잡는 범위까지).

**방어한다**: (a) breaking 변경이 승인 없이 들어옴(①, against 태그) (b) 생성물 수동 편집(③) (c) unknown enum 의 기본값 통과(④b) (d) 큰 메시지의 무제한 수용(⑤) (e) deadline 없는 호출·무한 재시도(⑥, 4D 의 client 가 아니라 **생성 stub 위의 test** — 실제 배선은 4D 가 같은 test 를 재사용) (f) `Unmeasurable` 의 변환(⑦) (g) 2D test 의 실행 제외(⑨).
**방어하지 않는다**: 게이트 정의(`buf.yaml`·`contract-policy`·`gate-tests`) 편집 · 승인 태그를 옮기는 행위(운영자) · 실제 servicer 의 준수(5E) · 실제 client 의 준수(4D) · 네트워크·인증(M6) · buf 자체의 결함 · 계약이 **의미상** 틀린 것(그건 2A~2C 의 리뷰).

**우회 후보(≥5)**: (1) mutation 스크립트가 빈 집합을 돌고 exit 0 → 양성 대조(②) + 집합 크기 ≥ N 을 정책 데이터로 단언 (2) `against` 를 HEAD 로 바꿈 → `contract-policy` 의 태그 규칙과 대조하는 test(게이트 정의 편집 — 경계 밖이나 검출은 함) (3) `generateProto` 를 `onlyIf { false }` 로 꺼 낡은 산출물로 컴파일 → 생성물이 VCS 밖이라 `clean check` 에서는 산출물 자체가 없어 컴파일 실패; 증분 빌드에서의 우회는 `gateExecutionGate` 가 `onlyIf` 를 잡는지 착수 시 실측(1A 실측 범위 확인) (4) unknown enum 거부를 Kotlin 만 하고 Python 은 안 함 → 양쪽 test 가 같은 testdata (5) `max_message_bytes` 를 한쪽만 올림 → D-2D-6 경계 쌍(수용 단언이 어긋난다) (6) 취소 test 를 `cancelled()` 폴링으로 → D-2D-7 리뷰 항목(test 가 거짓 초록일 수 있는 자리 — 알려진 제한) (7) 승인 태그를 breaking 뒤로 옮김 → 운영자 행위, 경계 밖·등재 (8) 생성물을 `src/` 로 옮겨 커밋 → `SourceLanguageGate` 가 `.java` 를 잡는다.

---

## 조사 결과 — 이 slice 에 영향을 주는 것 (조사 노트 02)

- buf 1.72.0 은 lint·breaking 로컬 완결(BSR 불요) → S-2·S-3 이 네트워크 없이 선다(codex-review-gate 운영과 정합).
- grpc-kotlin 1.5.0 + grpc-java 1.84.0 조합 검증 이력 없음 → 2A 스모크가 선행이지만 2D 의 in-process grpc-testing 이 그 조합의 **런타임** 증명 자리.
- grpc/grpc#36193(콜백 중 `cancelled()` False) → D-2D-6.
- `grpcio-testing` 의 `grpc.aio` in-process fake 1급 지원 **미확인** → 착수 시 실측, 안 되면 실제 loopback 서버를 pytest fixture 로(그 경우 Python 쪽만 socket — codex sandbox 는 Python test 를 어차피 못 돈다).
- `grpcio-tools` 번들 protoc 버전 미확인 → 생성 결정성(③) 의 전제: **Kotlin 플러그인의 protoc 과 Python 의 protoc 이 같은 `.proto` 에서 같은 descriptor 를 내야** 한다 — 버전이 달라도 proto3 wire 는 같지만 생성 소스는 다르므로 **양쪽 생성물은 각자의 도구 버전에 고정**(정책 데이터에 둘 다 기록).
- Java·Python protobuf 런타임은 독립 버전 축 → evidence 에 「같은 major」 서술 금지.

---

## OPEN — 수령·신설

| OPEN | 2D 처리 |
| --- | --- |
| `OPEN-ADR-07`(mutation 도구) | 무관 — 코드 mutation 아님. 혼동 방지 문장만 |
| `OPEN-ADR-11` | ⑥ 의 deadline·retry test 는 규칙(ADR 0010)을 검증, 값은 정책 데이터 표본 |
| `OPEN-M2-DEADLINE-VALUES` | 2D 가 만들지 않는다 — 5E 실측 |
| 신설 후보 `OPEN-2D-AIO-INPROCESS` | `grpcio-testing` 의 aio in-process 지원 여부 — 착수 시 실측으로 닫힘 |
| 신설 후보 `OPEN-2D-CROSSLANG-CI` | 교차 언어 socket 스모크의 상시화 자리(M6 6C) — D-2D-3 (a) 의 대가 |
