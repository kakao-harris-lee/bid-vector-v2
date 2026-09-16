# checklist.md — M4 / 4D-4 리뷰 요청 조건 점검

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로 15개
      개별 인자>` 결과 없음(clean). 양성 대조 1회: `PredictionFacts.kt` 에 주석 한 줄을
      일부러 추가해 `git status --porcelain` 이 `M` 으로 잡는 것을 확인 → `git restore`
      로 절삭 복원(그 파일이 원래 HEAD 와 바이트 동일했음을 사전에 `git status --porcelain`
      으로 확인한 뒤에만 사용 — `git checkout --` 계열의 위험을 피한 비파괴 절삭).
- [x] scope.md acceptance_commands 전부 exit 0 — `commands.md` 의 최종 전건 실측(HEAD
      `d00f8cb`) GREEN.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — 부분 게이트가 아니라
      `./gradlew --no-build-cache --no-daemon clean check` 전건(워크플로 job `check` 그대로)을
      네 번 돌렸고 마지막 실행이 GREEN(`commands.md` 참고). `ArchitectureGateTest`·
      `NotificationBoundaryTest`(S-5) 포함 전 게이트가 이 안에서 돈다.
- [x] 변경된 fixture와 정책 version의 근거 기록 — 신규 정책 슬롯 0(D-4D4-6). 기존 fixture
      `TEST_DIAGNOSTICS`·`TEST_RELEASE`(`OpportunityAnalysisFixtures.kt`, 4D-3 산출물)를
      그대로 재사용 — 이 slice가 새로 추가한 fixture는 `EvaluateCandidatesUseCaseTest`의
      로컬 `evidence`/`evidenceA`/`evidenceB` 값(값 리터럴, manifest 대상 아님)뿐이다.
- [x] 알려진 제한과 rollback 또는 비활성화 방법 기록 — 아래 「알려진 제한」·`rollback.md`.
- [x] 비밀값 스캔 통과 — `grep -rniE -f config/quality/leak-patterns.txt <in_scope 15개
      개별 인자> reports/evidence/m4/4d4/` exit 1(매치 없음). 육안 확인 — Telegram id·
      사업자 정보 없음.

## 종결 조건 대조(scope.md)

| 조건 | 충족 근거 |
| --- | --- |
| `Analyzed.evidence` 가 `Predicted.diagnostics`·`Supplied.excluded` 와 등가(통합 층) | `OpportunityAnalysisTest`: 「Supplied 예측 성공 경로 Analyzed evidence 는 Predicted diagnostics release Supplied excluded 를 옮긴다(D-4D4-7)」 |
| `NotificationRequest.evidence == Analyzed.evidence`(use case test) | `EvaluateCandidatesUseCaseTest`: 「BidNow 알림 요청의 evidence 는 Analyzed evidence 와 같다(우회 3)」 |
| 우회 (1)~(8) 각 실측 | 아래 「우회 대조표」 |
| `EvidenceLinesTest` 골든(두 줄 + `NotPredicted` 11 전수 + `SampleExclusionReason` 8 전수 + 같은 점수·다른 근거 → 같은 verdict) | `EvidenceLinesTest` 9건(골든 2 포함, 전수 2, 순서·생략 규칙) + `EvaluateCandidatesUseCaseTest`: 「같은 점수 다른 evidence 는 같은 Verdict 를 낸다(우회 2)」 |
| 전건 `check` | `commands.md` 의 최종 전건 실측(HEAD `d00f8cb`) GREEN |
| verifier `ready-for-review` | r2 ready-for-review(blocker 0 · high 0) |
| 사용자 승인 | 팀장·사용자 소관 |

## 우회 대조표(scope.md 위협 모델, Phase 2.5 대응)

| # | 우회 | 막는 게이트/test | 실측 |
| --- | --- | --- | --- |
| (1) | `Analyzed`·`NotificationRequest` 를 근거 없이 짓는다 | 필수 인자(기본값 없음) — 컴파일 거부 | 변이 실측(`commands.md` 12:40): `Analyzed.evidence` 제거 시 main 2 파일(`EvaluateCandidatesUseCase.kt`·`OpportunityAnalysisPipeline.kt`) 즉시 컴파일 거부 — main 이 먼저 막혀 `compileTestKotlin` 자체가 진행되지 않는다. scope.md 사전 추정("main 1·test 3")보다 강한 결과(모듈 전체 컴파일 거부) — 아래 「알려진 제한」에 사유 기록. |
| (2) | 근거가 `LadderInput`·`Verdict` 에 스며 판정을 바꾼다 | `decision` 무접촉(모듈 의존 `shared-kernel` 뿐) | `EvaluateCandidatesUseCaseTest`: 「같은 점수 다른 evidence 는 같은 Verdict 를 낸다」 + `PredictionFactsTest`: 「진단만 다른 두 Predicted 는 같은 ScoreFact 쌍을 낸다」(ScoreFact 층) |
| (3) | 다른 공고·다른 호출의 근거가 `NotificationRequest` 에 실린다 | `reach` 안 같은 `outcome` 값만, `internal` 생성자 | `EvaluateCandidatesUseCaseTest`: 「BidNow 알림 요청의 evidence 는 Analyzed evidence 와 같다」 |
| (4) | 문구가 결과 타입을 만든다 | `evidenceLinesFor` 는 `List<String>` 만 반환, 입력 sealed/enum 전수 `when`(else 없음) | 컴파일 시점 강제(새 `MlUnavailableReason`·`SampleExclusionReason` 값 추가 시 `EvidenceLines.kt` 컴파일 거부) — 구조 자체가 증거, 별도 test 불요 |
| (5) | `Diagnosed` 가 `Supplied.excluded` 를 빼먹고 빈 맵을 넣는다 | `OpportunityAnalysisTest` D-4D4-7 test | 위 표 첫 행과 동일 test |
| (6) | `BidNow` 가 `NotPredicted` 와 동반 | 불가능 상태가 아니라 정직한 상태 — 골든 문구로 표현 | `EvidenceLinesTest`: 「NotPredicted 11 사유 전부 근거 없음 줄을 낸다」(`bidNowVia()` 로 실제 `BidNow` 판정과 `NotPredicted` 근거를 짝지어 렌더링이 깨지지 않음을 확인) |
| (7) | `String.format`·`%.2f`·`Locale` 로 숫자를 찍는다 | **계약 갱신 2** — `EvidenceLinesBoundaryTest`(소스 텍스트 경계 test, `EvidenceLines.kt` 한 파일 대상, 금지 어휘 5종). `ArchitectureGateTest` 는 `layer.domain` 모듈에만 걸려 `workflow`(application 층)는 대상 밖이라 이 우회를 막지 못한다(verifier r1 V-1) | 변이 세 종(`Locale.getDefault()`+`String.format` / `.format(` 단독 / `DecimalFormat`) 을 `EvidenceLines.kt` 에 주입할 때마다 `EvidenceLinesBoundaryTest` 가 붉어짐을 확인, 원복. `EvidenceLines.kt` 에 금지 어휘 실측 0건(파일 열람 확인). |
| (8) | 문구를 이벤트·persistence 에 싣는다 | persistence·event 편집 0(out_of_scope) | `git diff --name-status 44721cf..HEAD` 에 `workflow/event/**`·`adapters/**` 없음 |

## 알려진 제한

- **우회 (1) 변이 실측의 실제 파급이 scope.md 사전 추정과 다르다.** scope.md 결정표는
  「main 1·test 3 파일」로 적었으나(계약 작성 시점 추정), 최종 구현은 `EvaluateCandidatesUseCase.kt`
  가 `outcome.evidence` 를 읽는 두 번째 main 참조점을 추가로 만들어(D-4D4-3, `reach` 로
  근거를 옮기는 배선) main 컴파일이 먼저 실패하고 `compileTestKotlin` 자체가 돌지 않는다 —
  test 파일 개수를 셀 수 없다(모듈 전체 컴파일 거부가 더 강한 형태의 같은 보장). 사전 추정과
  다르다는 사실만 기록하고 별도 시정은 필요 없다(우회는 여전히 닫혀 있다).
- **`OPEN-4D4-CONTENT-REF`·`OPEN-4D4-REVIEW-EVIDENCE`** — scope.md D-4D4-5·D-4D4-8 가 이미
  정의한 후속 항목. Phase 6 에스컬레이션(팀장 소관, `capability-map.md` OPEN 표 등재)으로 남긴다.
- **계약 갱신 1(패키지 재배치)이 이 slice 의 「구현 전 발견해야 했을 사실」을 구현 중간에
  발견하게 했다** — scope.md 착수 조사 표가 4E `NotificationBoundaryTest`(S-5)를 못 봤다.
  갱신 이력에 사유를 남겼고(scope.md 「계약 갱신 이력」 #1) 재발 방지는 이 slice 범위 밖
  (착수 조사 절차 개선은 하네스/오케스트레이터 소관).
