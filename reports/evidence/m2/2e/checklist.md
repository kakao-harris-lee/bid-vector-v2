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

## 위협 모델 「방어한다」 목록과의 대응(scope.md, verifier r1 F-1(a) 반영 후 재정렬)

(a) 벡터 차원·정규화 불일치의 조용한 통과 → `isAcceptableEmbedding`/`_is_acceptable_embedding`
순수 함수(양쪽 언어) + 변이 실측(dimension 만 다른 정규화 벡터·비정규화 벡터 각 1건,
verifier r1 F-2 수정 후 — 원소를 "떼기만" 하던 이전 판은 norm 항에 먼저 걸려 dimension
항의 확인력이 0이었다) + `EmbedText.dimension`↔`GetEmbeddingMetadata.dimension` 대조(F-2
설계 검토 (1) 미구현 지적 반영). (b) 빈/초과 텍스트 → 빈 텍스트 testdata + provider fake
servicer가 빈/공백/상한초과 셋을 전부 `ApplicationFailure(INVALID_REQUEST)`로 실제 판단
(Python, 5건). (c) release 미지목·공백 응답 → `isModelReleaseNonBlank`/
`_is_model_release_non_blank` + 변이 실측(다섯 성분 **각각** 1건씩, verifier r1 F-3 수정 —
이전 판은 `datasetId` 하나뿐). (d) 점수·판정 필드의 계약 유입 → D-2E-1 대응표(스키마
리뷰로만 방어, 알려진 제한 1). (e) 세 서비스 공존 시 stub 충돌 → `MultiServiceContractTest`
(셋째 서비스 등록, 이름 충돌 없이 서버 기동 — verifier r1 F-5, 이전 판 「넷째」는 계수 오류).

**방어하지 않는다**(scope.md 명시, 이 slice가 실측하지 않음): 임베딩 품질 · 실 servicer(M5) ·
kNN·코사인의 옳음(4B-4·adapters) · 텍스트에 개인정보가 섞이는 것(합성 규약 소유 4B-4) ·
승인 태그를 옮기는 행위 · 생성 도구 결함 · **신설 `embedding.proto` 자체의 breaking 변경**
(verifier r1 F-1(high) — 이전 판은 이것을 「방어한다 (d)」로 **반대로** 적었다. 실측:
승인 태그에 이 파일이 없어 `buf breaking`·`contractGate`·S-3 스윕 전부가 이 파일 내부의
필드·enum 값·필드 번호 변경을 못 본다 — enum 값 삭제는 전 게이트를 완전 통과. 정본 방어는
아래 「종결 승인 결정 항목」의 새 승인 태그뿐이고, `EmbeddingTestdataCanonicalTest`는
그 전까지의 부분적 임시 대응이다, 알려진 제한 8).

## 우회 후보(scope.md (1)~(7) + 설계 검토 (8)) 대응

| # | 우회 | 막는 장치 |
| --- | --- | --- |
| (1) | `values` 개수 ≠ `dimension` | Kotlin `values 개수가 dimension 과 다르면 계약 불변식 위반이다(norm 은 1 로 유지)`(dimension 은 원본 그대로 두고 정규화된 3원소로 교체 — verifier r1 F-2 수정) + Python `test_dimension_mismatch_violates_the_contract_invariant`(동형 수정) + 양쪽 `metadata.dimension` 대조 test 신설 |
| (2) | 정규화 안 된 벡터(norm≠1±ε) | Kotlin `norm 이 1 을 epsilon 이상 벗어나면...`(2배 스케일 변이) + Python `test_unnormalized_vector_violates_the_contract_invariant` |
| (3) | `TextKind` 정의 밖 정수 | 양쪽 언어 `TextKind 정의 밖 정수는 거부된다`(`setKindValue(99)`/`request.kind = 99`) |
| (4) | 승인 태그 대비 필드 번호 재사용·enum 값 삭제 | **S-3 breaking 스윕은 못 막는다**(verifier r1 F-1(high) — `embedding.proto`가 승인 태그에 없어 게이트가 파일 내부 변경을 못 봄, 위 위협 모델 수정 참고). 임시 대응: `EmbeddingTestdataCanonicalTest`(JSON→binpb 왕복, 필드·enum 값·필드 번호 변경 셋을 잡음 — `rpc` 삭제는 못 잡음). 정본 대응: 「종결 승인 결정 항목」의 새 승인 태그 |
| (5) | `Unmeasurable` 가지 몰래 추가 | 스키마 리뷰 — `EmbedTextResponse`/`GetEmbeddingMetadataResponse` 모두 `oneof result`가 2가지뿐(성공/실패), testdata에 `unmeasurable` 표본 부재가 그 흔적 |
| (6) | release 공백 | 양쪽 언어 `release 다섯 성분 중 하나라도 공백이면...`(성분 5개 각각 변이 — verifier r1 F-3 수정, 이전 판은 `datasetId` 하나뿐) |
| (7) | `text` 상한 리터럴 | `contract-policy.properties` `embedding.text.max-chars`(정책 파일), Python fake servicer가 그 값을 읽어 판단(`test_fake_servicer_rejects_text_over_max_chars`) |
| (8) | `MultiServiceContractTest`에 `EmbeddingService`를 안 넣어 stub 충돌 미검출 | `EmbeddingService`를 셋째로 등록(verifier r1 F-5 — 이전 판 「넷째」는 계수 오류) + `embedText` 실제 호출(`한 in-process 서버 위에서 세 서비스가 각자 정상 응답한다`) |

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
5. **`hasNonBlankRelease`(4D-1 main)를 직접 재사용하지 못했다**(verifier r1 F-4 —
   이전 판은 이 함수 이름을 `releaseSatisfiesSelector`로 잘못 적었다. `releaseSatisfiesSelector`
   는 별개 함수로 scope.md ③이 4D-2로 넘긴 것이지 이 slice가 재사용을 시도한 대상이
   아니다). `hasNonBlankRelease`는 `Success`(prediction.proto)에 시그니처가 묶여 있어
   `Embedding`에는 안 맞는다. 같은 규칙을 `ModelRelease` 위 로컬 순수 함수
   (`isModelReleaseNonBlank`)로 다시 문서화했다(값·규칙 동일 — 4D-1 `allNotBlank`와 같은
   다섯 성분에 `isNotBlank()`, 설계와 다른 결정 — `EmbeddingContractTest.kt` 주석).
   `ReleaseShapeValidation.kt`를 `ModelRelease` 직접 수용 형태로 일반화하는 것은
   out_of_scope(Kotlin main 코드) 리팩터 후보.
6. **Kotlin `CrossLangSmokeTest`는 EmbedText를 부르지 않는다** — S-6은 `EmbeddingService`가
   Python 서버에 이름 충돌 없이 등록됨만 증명한다(out_of_scope, team-lead 지시 「등록만」).
7. **`test_contract_roundtrip.py`는 이 slice가 편집하지 않았다** — scope.md in_scope가
   `test_embedding_contract.py` 파일 하나만 지정한다(2B `test_prediction_contract.py`와
   같은 관례 — 독립 module fixture로 자족). team-lead 지시문의 "test_contract_roundtrip.py
   가 새 testdata도 왕복하게"는 scope.md in_scope와 어긋나 scope.md를 따랐다(설계와 다른
   결정) — round-trip 자체는 `test_embedding_contract.py` 안에 5건 있다.
8. **신설 `embedding.proto`는 다음 승인 태그가 찍히기 전까지 breaking 무방비다**
   (verifier r1 F-1(high) — 착수 문면 정정, 위 위협 모델·우회 (4) 참고). `buf breaking`은
   비교 기준(승인 태그)에 없는 파일의 내부 변경을 볼 수 없다 — 필드 삭제·enum 값 삭제·
   필드 번호 재사용이 `contractGate`·S-1(`clean check`)·S-3(스윕) 전부를 통과한다(실측:
   enum 값 삭제는 코드 참조가 없어 **완전 통과**). `EmbeddingTestdataCanonicalTest`(신설,
   `EmbeddingContractTest.kt`와 같은 패키지)가 JSON→binpb 왕복을 매 실행 재확인해 필드·
   enum 값·필드 번호 변경 셋은 잡지만(변이 실측: `1a02 0102`→`1a01 01` 등 바이트 표류)
   **`rpc` 삭제는 못 잡는다**(메시지 wire 형식과 무관). 정본 대응은 아래 「종결 승인
   결정 항목」.

## 종결 승인 결정 항목

- **승인 태그 `contracts/v1-approved-<date>` 신설(운영자 결정 대상)** — 이 slice가
  `embedding.proto`를 승인 태그에 포함시키는 유일한 방법은 새 태그를 찍는 것이고, 태그
  이동은 이 slice가 하지 않는다(2D 규칙, out_of_scope). 이 태그가 찍히기 전까지 알려진
  제한 8의 사각은 구조적으로 남는다 — `EmbeddingTestdataCanonicalTest`는 임시 안전망일
  뿐 정본 방어가 아니다.

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
(`EmbeddingContractTest` 19건 + `MultiServiceContractTest` 셋째 서비스 등록 — 원 커밋
메시지는 「넷째」, verifier r2 잔여 low로 이 문서에서 정정, `ddf3514`가 코드 쪽 표기도
정정) · `bb4ed94`
(`test_embedding_contract.py` 24건 + crosslang smoke 서비스 등록) · `94ef756`(detekt
ReturnCount 교정) · `c43fd9e`(ktlint 체인 줄바꿈 교정) · `3abddbc`(gate 등재·capability-map
OPEN 분해·commands.md·policy-values.md) · `2db1063`(checklist·rollback·S-0/S-1 실측).

**수정 라운드 1**(verifier r1, F-1·F-2 high · F-3 medium · F-4·F-5 low) — `5227aa1`
(F-1 — `EmbeddingTestdataCanonicalTest` 신설·gate-tests.properties 등재·scope.md 위협
모델 장부 정직화) · `0f542cf`(F-2 — dimension 변이 재설계·metadata 대조 신설).
**장부 정정**: `0f542cf`의 커밋 메시지는 "F-2 hunk만 포함"이라 적었으나 실제로는
F-3(release 성분별 변이) 코드도 같은 커밋에 함께 실렸다 — `git commit -- <path>`는
index 가 아니라 **working tree 의 현재 내용**을 그 경로 기준으로 커밋한다(`git add -p`
로 인덱스를 부분 스테이징해도 그 뒤 `git commit -- <path>`가 working tree 전체를
다시 실어 소용없다, 이번에 실측으로 확인 — `git show 0f542cf:<path> | grep
ReleaseBlankCase` 매치됨). 코드 내용 자체는 올바르고 완전하다(F-2·F-3 전부 반영,
`gradlew test`로 검증 완료) — 어긋난 것은 그 커밋 메시지의 「F-2만」이라는 범위
주장뿐이다. F-4(함수 이름 정정)·F-5(「넷째」→「셋째」 전수)와 이 evidence 갱신은
이 커밋(뒤 SHA)에 실렸다.

이 range의 첫 커밋(`f94bddb` — 착수 계약 고정, 세션 모델 단독 작성)은 이 구현 레인이 만든
것이 아니라 오케스트레이터가 착수 시 이미 커밋해 둔 것이다(scope.md·milestone-2.md 「Slice
2E」 절).

## 사용자 승인 — 2026-09-10, slice 2E 종결

다섯 항목 승인:

1. **2E 종결** — verifier r1 not-ready(산출물 high 2: F-1 breaking 사각·F-2 차원 가드
   확인력 0) → 수정 라운드 1(재작업 **1/5**) → r2 ready-for-review 위에서 승인.
2. **D-2E-1(임베딩 RPC) 확정** — 계약은 `EmbedText`(벡터)이지 점수 RPC가 아니라는 착수
   가정이 최종 판단으로 확정됐다(scope.md D-2E-1 「착수 가정, 종결 승인 시 운영자
   재확인」 → 「확정」).
3. **정책 값 승인** — `embedding.text.max-chars=4000`·`embedding.norm.epsilon=0.0005`
   (`OPEN-2E-TEXT-MAX` 종결, `policy-values.md`).
4. **승인 태그 신설** — `contracts/v1-approved-2026-09-10`(팀장이 이 승인 기록 커밋
   SHA에 찍는다). `embedding.proto`가 이 태그의 기준선에 포함돼 verifier r1 F-1의
   구조적 사각(신설 파일은 다음 태그 전까지 breaking 무방비)이 정본으로 닫힌다 —
   `EmbeddingTestdataCanonicalTest`(임시 대응)는 계속 남지만 이제 `contractGate`가
   1차 방어선이다.
5. **병합 진행** — 팀장이 처리.

head는 승인 시점 `git rev-parse HEAD`(이 문서에 값을 박지 않는다 — 2A verifier r1 F-1
관례). 재작업 카운터 **1/5**(verifier r1 한 라운드)로 확정.

## 잔여 — verifier r2 low(등재 후 이 커밋에서 정정)

- 「넷째 서비스」 잔존 두 곳 — `rollback.md`(MultiServiceContractTest 편집 파일 설명)·
  이 문서 「커밋 목록」의 `8785068` 인용(원 커밋 메시지 자체는 정정하지 않음, 역사적
  인용이라는 점만 명시) — 둘 다 이 커밋에서 「셋째」로 정정.

## 알려진 제한(종결 승인 시점 최종)

승인 태그 신설 뒤에도 남는 것: **`rpc` 삭제는 `EmbeddingTestdataCanonicalTest`도
`buf breaking`도 못 잡는다**(메시지 wire 형식과 무관, 알려진 제한 8 KDoc) · **실
servicer는 M5**(알려진 제한 2) · **텍스트 합성 규약은 4B-4**(알려진 제한 3,
`OPEN-2E-TEXT-SYNTHESIS`).
