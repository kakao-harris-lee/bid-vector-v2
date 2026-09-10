# M2/2E checklist.md

## 리뷰 요청 조건 점검

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      빈 출력, 양성 대조 통과(commands.md 「clean-tree 게이트」).
- [x] scope.md의 acceptance_commands(S-0~S-8) 전부 exit 0 — commands.md.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — S-1(`--no-build-cache clean
      check`)이 ktlint·detekt·ArchUnit·moduleDependencyGate·`contractGate`를, S-2(`buf
      lint && buf build`)가 계약 형태를, S-3(breaking 스윕)이 호환성을 각각 검증. S-4·
      S-5가 계약 규칙을 양쪽 언어에서, S-6이 교차 언어 등록을 증명.
- [x] 변경된 fixture와 정책 version의 근거 기록 — `contracts/testdata/embedding/*`는
      round-trip·fake servicer 표본(fixture corpus와 별개 축, scope.md in_scope 주석과
      같은 관례). 정책 값(`embedding.text.max-chars`·`embedding.norm.epsilon`) 근거는
      `policy-values.md`(착수 placeholder, `OPEN-2E-TEXT-MAX`).
- [x] 알려진 제한과 rollback 기록 — 아래 「알려진 제한」·`rollback.md`.
- [x] secret 스캔 통과 — commands.md.

## D-2E-1~6 대응표 — scope.md 「계약 고정 결정」과 그것을 증명하는 게이트/테스트

| ID | 판단 | 증명 |
| --- | --- | --- |
| D-2E-1 | 계약은 `EmbedText`(벡터) — 점수 RPC 가 아니다 | `embedding.proto`의 모든 메시지에 `priority`·`match`·`similarity`·`probability`·`score` 이름의 필드 0건(육안 대조·grep) — `Embedding`은 `values`·`dimension`·`normalization`·`release` 넷뿐 |
| D-2E-2 | 별도 서비스 `EmbeddingService` | `embedding.proto`가 `BidPredictionService`와 다른 `service` 블록 — `MultiServiceContractTest`가 같은 in-process 서버에 셋 다 등록해 공존 증명 |
| D-2E-3 | 벡터는 `repeated float` + `dimension` + 정규화 enum(bytes 아님) | `Embedding` 메시지 정의 + testdata JSON이 사람이 읽는 배열(`[0.5,0.5,0.5,0.5]`) |
| D-2E-4 | `probability` 이름 어디에도 없음(strategy/Score.kt 정정은 4B-4, 2E 밖) | grep 대조(2E 신설 파일에 `probability` 0건) — `ProbabilityScore` KDoc 정정은 out_of_scope로 인계 |
| D-2E-5 | 텍스트 합성 규약은 Kotlin 소유·version화, 계약은 「텍스트+kind」만 | `EmbedTextRequest{envelope, text, kind}` — fact 조합 로직 없음, `OPEN-2E-TEXT-SYNTHESIS` 신설(4B-4 인계) |
| D-2E-6 | `EmbedText`는 멱등, `latest_promoted`는 4D-1과 같은 규칙 | 계약 자체가 요청 파라미터만으로 응답을 결정하는 형태(부수효과 필드 없음) — 멱등의 실제 강제는 M5 provider 몫으로 인계(알려진 제한 2) |

## 위협 모델 「방어한다」 목록과의 대응(scope.md)

(a) 벡터 차원·정규화 불일치의 조용한 통과 → `isAcceptableEmbedding`/`_is_acceptable_embedding`
순수 함수(양쪽 언어) + 변이 실측(dimension mismatch·비정규화 벡터 각 1건). (b) 빈/초과 텍스트
→ 빈 텍스트 testdata + provider fake servicer가 빈/공백/상한초과 셋을 전부
`ApplicationFailure(INVALID_REQUEST)`로 실제 판단(Python, 5건). (c) release 미지목·공백 응답
→ `isModelReleaseNonBlank`/`_is_model_release_non_blank` + 변이 실측(dataset_id 공백 1건).
(d) breaking 변경 → S-3(11/11 mutation 잡힘, `embedding.proto` 포함). (e) 점수·판정 필드의
계약 유입 → D-2E-1 대응표(스키마 리뷰로만 방어, 알려진 제한 1). (f) 세 서비스 공존 시 stub
충돌 → `MultiServiceContractTest`(넷째 서비스 등록, 이름 충돌 없이 서버 기동).

**방어하지 않는다**(scope.md 명시, 이 slice가 실측하지 않음): 임베딩 품질 · 실 servicer(M5) ·
kNN·코사인의 옳음(4B-4·adapters) · 텍스트에 개인정보가 섞이는 것(합성 규약 소유 4B-4) ·
승인 태그를 옮기는 행위 · 생성 도구 결함.

## 우회 후보(scope.md (1)~(7) + 설계 검토 (8)) 대응

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| (1) | `values` 개수 ≠ `dimension` | Kotlin `values 개수가 dimension 과 다르면 계약 불변식 위반이다`(변이) + Python `test_dimension_mismatch_violates_the_contract_invariant` |
| (2) | 정규화 안 된 벡터(norm≠1±ε) | Kotlin `norm 이 1 을 epsilon 이상 벗어나면...`(2배 스케일 변이) + Python `test_unnormalized_vector_violates_the_contract_invariant` |
| (3) | `TextKind` 정의 밖 정수 | 양쪽 언어 `TextKind 정의 밖 정수는 거부된다`(`setKindValue(99)`/`request.kind = 99`) |
| (4) | 승인 태그 대비 필드 번호 재사용·enum 값 삭제 | S-3 breaking 스윕(신설 파일 `embedding.proto` 포함, package-rename for-loop 반영 — 반증/정증 실측 commands.md) |
| (5) | `Unmeasurable` 가지 몰래 추가 | 스키마 리뷰 — `EmbedTextResponse`/`GetEmbeddingMetadataResponse` 모두 `oneof result`가 2가지뿐(성공/실패), testdata에 `unmeasurable` 표본 부재가 그 흔적 |
| (6) | release 공백 | 양쪽 언어 `release 성분 하나라도 공백이면...`(변이) |
| (7) | `text` 상한 리터럴 | `contract-policy.properties` `embedding.text.max-chars`(정책 파일), Python fake servicer가 그 값을 읽어 판단(`test_fake_servicer_rejects_text_over_max_chars`) |
| (8) | `MultiServiceContractTest`에 넷째 서비스를 안 넣어 stub 충돌 미검출 | `EmbeddingService`를 넷째로 등록 + `embedText` 실제 호출(`한 in-process 서버 위에서 세 서비스가 각자 정상 응답한다`) |

## 알려진 제한

1. **D-2E-1(점수 필드 0건)은 스키마 리뷰로만 방어된다** — additive라 새 필드 추가 자체를
   게이트가 막지 못한다(2B checklist 「우회 (5)」와 같은 구조적 한계). 실제 강제는 사람
   리뷰(계약 diff 검토) 몫이다.
2. **`EmbedText`의 실제 멱등성은 이 slice가 증명하지 않는다** — 계약 형태(부수효과 필드
   없음)만 보였고, 실 servicer(M5 provider slice)가 같은 텍스트·release에 같은 벡터를
   낸다는 실측은 그 slice 몫이다(D-2E-6).
3. **텍스트 합성 규약(`OPEN-2E-TEXT-SYNTHESIS`)은 계약 밖이다** — 어느 fact를 어떤 순서로
   문장에 넣는가, 개인정보 배제 규칙은 전부 4B-4가 정한다. 이 slice의 testdata 텍스트는
   임의로 작성한 예시 문장일 뿐 합성 규약의 실물이 아니다.
4. **`embedding.text.max-chars`·`embedding.norm.epsilon`은 착수 placeholder다**
   (`OPEN-2E-TEXT-MAX`) — 값 자체(4000자·0.0005)는 실측 근거가 없다(`policy-values.md`).
   종결 승인 때 확정 대상.
5. **`releaseSatisfiesSelector`(4D-1 main)를 직접 재사용하지 못했다** — 그 함수는
   `Success`(prediction.proto)에 시그니처가 묶여 있어 `Embedding`에는 안 맞는다. 같은
   규칙을 `ModelRelease` 위 로컬 순수 함수로 다시 문서화했다(값·규칙 동일, 설계와 다른
   결정 — `EmbeddingContractTest.kt` 주석). `ReleaseShapeValidation.kt`를 `ModelRelease`
   직접 수용 형태로 일반화하는 것은 out_of_scope(Kotlin main 코드) 리팩터 후보.
6. **Kotlin `CrossLangSmokeTest`는 EmbedText를 부르지 않는다** — S-6은 `EmbeddingService`가
   Python 서버에 이름 충돌 없이 등록됨만 증명한다(out_of_scope, team-lead 지시 「등록만」).
7. **`test_contract_roundtrip.py`는 이 slice가 편집하지 않았다** — scope.md in_scope가
   `test_embedding_contract.py` 파일 하나만 지정한다(2B `test_prediction_contract.py`와
   같은 관례 — 독립 module fixture로 자족). team-lead 지시문의 "test_contract_roundtrip.py
   가 새 testdata도 왕복하게"는 scope.md in_scope와 어긋나 scope.md를 따랐다(설계와 다른
   결정) — round-trip 자체는 `test_embedding_contract.py` 안에 5건 있다.

## 판단이 갈린 지점(설계와 다른 결정)

1. **`hasNonBlankRelease`(4D-1) 미재사용** — 알려진 제한 5.
2. **`test_contract_roundtrip.py` 미편집** — 알려진 제한 7.
3. **`breaking-mutations.sh`의 「신설 파일 포함」을 package-rename for-loop 추가로 좁혀
   해석** — 설계 검토는 "기존 11종이 새 파일에 적용되는 형태"라고 썼으나, 실측
   (commands.md 반증)으로 확인한바 `buf breaking`은 승인 태그에 없는 파일의 내부 변경을
   breaking으로 잡을 수 없다(원래 없던 것의 형태 변경이라서) — 전건 대상이 가능한 것은
   파일 전체에 거는 `package-rename` 하나뿐이다. 11종 전부를 `embedding.proto`로 재타겟팅
   하는 것은 기존 파일(common/prediction/training) 검증 커버리지를 잃는 트레이드오프라
   채택하지 않았다.

## 커밋 목록

**Phase 3 구현**(base `8b50461`) — 순서대로: `4f96c77`(`embedding.proto`·testdata 5쌍·
breaking-mutations.sh 신설 파일 포함·contract-policy.properties 정책 값) · `8785068`
(`EmbeddingContractTest` 19건 + `MultiServiceContractTest` 넷째 서비스) · `bb4ed94`
(`test_embedding_contract.py` 24건 + crosslang smoke 서비스 등록) · `94ef756`(detekt
ReturnCount 교정) · `c43fd9e`(ktlint 체인 줄바꿈 교정) · `3abddbc`(gate 등재·capability-map
OPEN 분해·commands.md·policy-values.md).

이 range의 첫 커밋(`f94bddb` — 착수 계약 고정, 세션 모델 단독 작성)은 이 구현 레인이 만든
것이 아니라 오케스트레이터가 착수 시 이미 커밋해 둔 것이다(scope.md·milestone-2.md 「Slice
2E」 절).

## 사용자 승인 대기

verifier 검증 전. 운영자 지시(CLAUDE.md 2026-09-04)에 따라 이 slice는 코드 slice이므로
Codex 리뷰 대상이 아니다 — 완료 조건은 **verifier ready-for-review + 사용자 승인**이다.
