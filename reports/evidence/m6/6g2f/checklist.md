# M6/6G-2f — 리뷰 요청 조건 점검

| 항목 | 상태 | 근거 |
|---|---|---|
| diff 가 커밋되어 base/head 고정 | ✓ | 아래 clean-tree 양성 대조 |
| acceptance 전부 exit 0 | ✓ | `commands.md` 「acceptance」 셋 |
| test/lint/type/architecture/contract 전건 | ✓ | 축약 없이 `check` + `qualityBaseline` + `one-command-check.sh`(Python job 까지) |
| fixture·정책 version 근거 | **N/A** | fixture·golden·정책 데이터 무변경. 페이지 크기는 wire 설정이고 `maxPages` 는 손대지 않았다(D-6G2f-4) |
| 알려진 제한과 rollback | ✓ | 아래 「알려진 제한」 · `rollback.md` |
| 누출 어휘 스캔 | ✓ | `commands.md` — evidence 와 이 slice 가 더한 줄 둘 다 매치 0 |
| `differential.json` | **N/A** | Python 대조 축 없음(Kotlin 전용, ml-engine 무변경) |
| `golden-manifest.json` | **N/A** | fixture 를 쓰지도 바꾸지도 않는다 — 두 축 test 는 mock 응답으로만 선다 |
| 설계 검토 우회 대응표 | **N/A** | 게이트 술어 확장 0(D-6G2f-8). 값 한 줄 slice 이고, 그 값에 두 축 test 를 요구한 것이 방어다 |

## 크기 게이트

| | evidence(`scope.md` 포함) | 레인 세 파일만 | 산출물(코드·runbook·`config/quality` 추가분) |
|---|---|---|---|
| 줄 | 382 | 265 | 462 |
| 바이트 | 34,508 | 20,258 | 32,462 |

**줄은 통과, 바이트는 2,046 B(6%) 초과로 남는다.** 그 초과는 전부 `scope.md` 쪽이다 — 한 파일이 evidence
바이트의 **42%**(14,250 B)이고 이 라운드의 계약 갱신(D-6G2f-9~13)으로 커졌으며, 레인이 만지지 않는 팀장
파일이다. 레인 세 파일은 23,695 → 20,258 B 로 **15% 줄였다**(처분 표 행 묶기 · 라운드 서술 삭제 · 문면
압축 네 차례) 그리고 그 셋만 보면 산출물의 62% 다. 한국어 산문은 한 자 3 바이트이고 Kotlin 은 1 이라 이
축은 같은 일의 양을 같은 바이트로 세지 않는다 — **줄 축이 이 slice 에서 더 바른 척도**다.

## clean-tree 양성 대조

HEAD `bf8bbac3` 에서 산출물 경로로 쟀다 — **빈 출력 → `M` → 빈 출력**(43줄 파일 끝에 한 줄을 붙여 `M` 을
확인한 뒤 **비파괴 절삭**으로 되돌리고 줄 수 복귀 확인). `git checkout --` 금지(다른 레인 미커밋 편집을
지운다), 경로는 **개별 인자**(변수 하나로 묶으면 pathspec 이 하나가 되어 「빈 출력」이 더러운 트리와 구별
되지 않는다). evidence 를 포함한 마지막 상태의 판정은 verifier 와 PR 조치 코멘트 몫이다.

## 새 public 표면 — 하나다

`KonepsOpeningEndpointProperties.rowsPerPage`(`Int`, data class 생성자, prefix `bidvector.koneps.opening`).
밖에 허락하는 것은 **1..상한 안의 선택**뿐이고 상한 초과·0·음수는 생성자 `require` 가 거부한다 —
`@ConfigurationProperties` 가 그 생성자를 지나므로 거부가 곧 **기동 실패**다(`OpeningRowsPerPageTest` 둘).
`internal` 상수 `KONEPS_MAX_ROWS_PER_PAGE` 에서 **기본값과 상한이 함께** 나와 수의 리터럴은 한 자리다.
`adapters` 의 `KonepsSourceConfig.numOfRowsPerPage` 는 이 slice 가 만든 표면이 **아니고**(이미 있던 인자에
배선이 값을 주기 시작했다), test 표면 둘(`MockPagingMode`·`RequestedRows`)은 출하 바이트에 없다.

## 알려진 제한

1. **한도 자체는 바뀌지 않는다**(키 × operation × 일 1,000) — 운영계정 승인이 유일한 근본 조치이고 이
   slice 는 약 2배까지다. 업무 공통 **단일 operation 은 둘**(개찰완료·산식 A, cr M-2)이고 산식 A 는 공고당
   1 행이라 쪽 크기로 줄지 않는다(공사 공고만 부른다).
2. **참가 999 초과 공고는 여전히 2쪽 이상**(day1 최대 35쪽 ≈ 참가 3,500 → 쪽 4).
3. **효과는 미정착 공고부터** — 이미 정착한 축은 다시 부르지 않는다.
4. **`MAX_PAGES` 확정 제외의 뜻이 넓어졌다** — 쪽 수 상한 50 은 무변경이고 문턱이 5,000 → 49,950 이다
   (D-6G2f-4). 폭주 방지 문턱이지 데이터 정확성 문턱이 아니다.
5. **공고 목록 레인은 그대로 100**(`OPEN-6G2F-NOTICE-LIST-ROWS`, 백필 재실행이면 한도 초과) ·
   `OPEN-6G2D-FRAME-REWALK` 도 열려 있다(재걷기는 남고 비용만 줄어든다 — 슬롯 축 약 240).
6. **mock 파일이 크기 한도에 가깝다** — `MockOpeningKonepsHttp.kt` **485줄**(한도 500). 다음에 판을 더하는
   slice 는 먼저 쪼갤 자리를 정해야 한다(응답 판과 행 생성의 경계로).
7. **ⓑ 가 잠그는 것은 「쪽 크기가 표본을 바꾸지 않는다」까지**다 — 실 디렉터리 장부 해시 호환은 그 성질에서
   따라오지만 실 디렉터리로는 재지 않았다(실행 상태 접근 금지).
8. **999 응답의 경과는 두 operation 만 실측**(목록 용역 2.80 s · 예비가격 상세 공사 1.83 s) — 나머지 미기록,
   개찰완료는 배포 전 확인 몫이고 문턱은 `total < 5 s`(runbook §7 0항).
9. **공시 문턱의 출처가 기록되지 않는다**(cr M-1, `OPEN-6G2F-MAX-PAGES-PROVENANCE`) — 판정문·원장이 쪽
   크기를 싣지 않아 되돌림 인자를 쓴 날이 섞이면 한 디렉터리에 두 문턱이 구별 없이 남는다. 임시 운용은 그
   인자를 쓴 날·값을 evidence 에 적는 것(runbook §7 5항)이고 구조로 닫는 것은 뒤 slice 몫이다.
10. **바인딩 경로의 기동 거부 test 가 없다**(vr L2) — `require` 를 생성자 호출로만 잰다. setter 바인딩으로
    바뀌면 회귀를 잡지 못한다(verifier 가 기동 probe 로 실제 거부를 확인했다).
11. **999 그 수를 잠그는 것이 없다**(cr L-1) — 기본값·거부 test 가 셋 다 상수를 따라가므로 상수를 올리는
    변이는 전건 초록이다. 잠그는 자리는 **runbook §7 0항의 실 호출 표**다.
