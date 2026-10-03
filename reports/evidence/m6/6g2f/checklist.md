# M6/6G-2f — 리뷰 요청 조건 점검

| 항목 | 상태 | 근거 |
|---|---|---|
| 구현 diff 가 커밋되어 base/head 고정 | ✓ | 아래 clean-tree 양성 대조 |
| acceptance 전부 exit 0 | ✓ | `commands.md` 「acceptance」 표 셋 |
| test/lint/type/architecture/contract 전건 통과 | ✓ | 축약 없이 `check` 전건 + `qualityBaseline` + `one-command-check.sh`(Python job 까지) |
| 변경된 fixture 와 정책 version 의 근거 | **N/A** | fixture·golden·정책 데이터 무변경. 페이지 크기는 wire 설정이고 `KonepsHttpPolicyData.maxPages` 는 손대지 않았다(D-6G2f-4) |
| 알려진 제한과 rollback | ✓ | 아래 「알려진 제한」 · `rollback.md` |
| 누출 어휘 스캔 | ✓ | `commands.md` — evidence 와 이 slice 가 더한 줄 둘 다 매치 0 |
| `differential.json` | **N/A** | Python 대조 축이 없다(Kotlin 전용, ml-engine 무변경) |
| `golden-manifest.json` | **N/A** | fixture 를 쓰지도 바꾸지도 않는다 — 두 축 test 는 loopback mock 응답으로만 선다 |
| 설계 검토(Phase 2.5) 우회 대응표 | **N/A** | 게이트 술어 확장 0(D-6G2f-8). 게이트·계약형이 아니라 **값 한 줄 slice** 이고, scope.md 가 그 값에 두 축 test 를 요구한 것이 이 slice 의 방어다 |

## clean-tree 양성 대조

HEAD `bf8bbac3` 에서 산출물 경로(코드·runbook·`config/quality`)로 쟀다. **빈 출력 → `M` → 빈 출력**:
in_scope 파일 하나(43줄) 끝에 한 줄을 붙여 `M` 으로 잡히는 것을 확인한 뒤 **비파괴 절삭**
(`head -n <원래 줄수>` 를 임시 파일에 받아 덮어쓰기)으로 되돌렸고 줄 수가 43 으로 돌아왔다.
`git checkout --` 은 쓰지 않고(다른 레인 미커밋 편집을 지운다) 경로는 **개별 인자**로 넘겼다 — 변수
하나로 묶으면 pathspec 이 하나가 되어 「빈 출력·exit 0」이 더러운 트리와 구별되지 않는다. evidence
경로를 포함한 마지막 상태의 판정은 **verifier 와 PR 조치 코멘트** 몫이다.

## 새 public 표면 (설계 검토 (2b) 값 획득 축) — 하나다

| 표면 | 값을 받는 자리 | 실측 |
|---|---|---|
| `KonepsOpeningEndpointProperties.rowsPerPage`(`Int`, data class 생성자, prefix `bidvector.koneps.opening`) | 설정(명령행 인자·환경변수). 기본값은 게이트웨이 실측 상한 상수 | 밖에 허락하는 것은 **1..상한 안의 값 선택**뿐이다. 상한 초과·0·음수는 생성자 `require` 가 거부하고 `@ConfigurationProperties` 바인딩이 그 생성자를 지나므로 거부가 곧 **기동 실패**다(`OpeningRowsPerPageTest` 둘이 든다) |

곁의 상수 `KONEPS_MAX_ROWS_PER_PAGE` 는 `internal` 이라 app 모듈 밖에서 이름 붙일 수 없고, **기본값과
상한이 그 한 자리에서** 나온다 — 코드에 그 수의 리터럴은 거기 하나뿐이고 test 는 수를 다시 적지 않고
그 상수와 맞댄다(두 자리에 적으면 한쪽만 바뀌는 날 test 가 바뀐 쪽을 따라간다).

`adapters` 의 `KonepsSourceConfig.numOfRowsPerPage` 는 이 slice 가 만든 표면이 **아니다** — 기본값 있는
생성자 인자로 이미 있었고, 배선이 그 인자에 값을 주기 시작한 것이 이 slice 다. test 쪽 새 표면 둘
(`MockPagingMode` · `RequestedRows`)은 `internal` test 타입이고 출하 바이트에 없다.

## 알려진 제한

1. **한도 자체는 바뀌지 않는다**(키 × operation × 일 1,000). 운영계정 승인이 유일한 근본 조치이고 이
   slice 는 호출당 행 수를 올려 **약 2배**까지다. 업무 공통 **단일 operation 은 둘**이다(개찰완료 ·
   입찰가격산식 A, cr M-2) — 산식 A 는 공고당 1 행이라 쪽 크기로 줄지 않고 공사 공고만 부른다.
2. **참가 999 초과 공고는 여전히 2쪽 이상**(day1 최대 35쪽 ≈ 참가 3,500 → 쪽 4).
3. **효과는 미정착 공고부터** — 이미 정착한 축은 다시 부르지 않으므로 걷은 공고의 비용은 줄지 않는다.
4. **`MAX_PAGES` 확정 제외의 뜻이 넓어졌다** — 쪽 수 상한 50 은 무변경이고 문턱이 참가 5,000 에서
   49,950 으로 올라간다(D-6G2f-4). 폭주 방지 문턱이지 데이터 정확성 문턱이 아니다.
5. **공고 목록 레인은 그대로 100**(`OPEN-6G2F-NOTICE-LIST-ROWS`) — 일일 증분은 한도 안이지만 백필
   재실행이면 그 operation 이 한도를 넘는다.
6. **`OPEN-6G2D-FRAME-REWALK` 는 열려 있다** — 매 기동 재걷기는 남고 비용만 줄어든다.
7. **mock 파일이 크기 한도에 가깝다** — `MockOpeningKonepsHttp.kt` 474줄(한도 500). 다음에 판을 더하는
   slice 는 먼저 쪼갤 자리를 정해야 한다(기계적 분할이 아니라 응답 판과 행 생성의 경계로).
8. **ⓑ 가 잠그는 것은 「쪽 크기가 표본을 바꾸지 않는다」까지**다. 실 디렉터리 장부 해시와의 호환은 그
   성질에서 따라오지만 실 디렉터리로는 재지 않았다(실행 상태 접근 금지).
9. **공시하는 확정 제외 문턱의 출처가 기록되지 않는다**(cr M-1, `OPEN-6G2F-MAX-PAGES-PROVENANCE`) —
   판정문도 원장도 쪽 크기를 싣지 않으므로 되돌림 인자를 쓴 날이 섞이면 한 디렉터리에 「>5,000」과
   「>49,950」이 함께 있고 구별되지 않는다. 임시 운용은 **그 인자를 쓴 날과 값을 evidence 에 적는
   것**(runbook §7 3항)이고 구조로 닫는 것은 뒤 slice 몫이다.
10. **바인딩 경로의 기동 거부를 재는 test 가 없다**(vr L2) — `require` 를 생성자 호출로만 잰다.
    바인딩이 setter 방식으로 바뀌면 그 회귀를 잡는 test 가 없다(verifier 가 이번 라운드에 실제 기동
    거부를 프로브로 확인했다).
11. **999 그 수를 잠그는 test·게이트가 없다**(cr L-1) — 기본값은 상한 상수와, 거부는 상한+1 과 맞대어
    셋 다 상수를 따라가므로 상수 자체를 올리는 변이는 전건 초록이다. 잠그는 자리는 test 가 아니라
    **runbook §7 0항의 operation 별 실 호출 표**이고, 그 표의 개찰완료 행이 비면 배포가 막힌다.
