# M6/6G-2c — 실행 명령과 실측

base `ecdc9d9f` · 브랜치 `m6-6g2c/2026-10-04` · 레인 둘(K: Kotlin · P: Python). 레인마다 자기 절만 쓴다.

> **마지막 HEAD 의 게이트 결과는 이 문서가 정본이 아니다**(evidence-pack 규격) — evidence 를 고치는
> 커밋은 언제나 마지막 산출물 커밋 뒤에 오므로 자기 post-state 를 담을 수 없다. 마지막 HEAD 의
> acceptance 는 verifier 와 PR 조치 코멘트가 정본이다.

## P — Python

판정 대상 SHA **`f3a47f05`**(수정 라운드 1 의 마지막 P 커밋). 라운드 1 앞의 판정 대상은
`20f1e7ba` 였다. 아래 acceptance 는 `f3a47f05` 트리에서 잰 값이고, 라운드 1 에서 **새로 만든
파일은 없다**(고친 파일 열둘).

### 항목별 처분과 변이

| 항목 | 처분 | 변이 | 결과 |
|---|---|---|---|
| **D-6G2c-17** 업무 대표 표지 셋 | 구현 | 앞 판 두 값으로 되돌림 | RED(evaluation 4건) |
| | | 행 0 이 `UNDERPOWERED` 로 남음 | RED(3건) |
| | | 하한을 상수로 | RED(3건 + 쓰임 명단 등식 + 자리 probe) |
| **D-6G2c-12** manifest 키 세 자리 등식 | 구현 | 생성기 하나에서 `incomplete_axis` 칸 제거 | RED, 문면이 그 생성기를 이름으로 지목 |
| **D-6G2c-21 ③** seed 키 열거 한 자리 | 구현 | 둘째 열거 되살림 + 길이 비교로 되돌림 | RED(자리 수 등식 · 거부 문면 이름 등식) |
| **D-6G2c-21 ④** 기존 판정 덮어쓰기 거부 | 구현 | 존재 검사 제거 | RED(사유 전수 · 바이트 불변 둘) |
| **D-6G2c-22** 판독기 URI 디코드 | 구현 | 디코딩 제거 | RED — CLI 끝까지 도는 판이 `SNAPSHOT_UNREADABLE NOT_FOUND` 로 선다 |
| | | 같은 변이를 판독기 단위에서 | RED 둘(디코딩을 재는 둘) · 음성 대조 셋은 초록 |
| | (음성 대조) | 없는 경로 · 파일 셋 결손 · 파일을 가리킴 · 다른 scheme | **거부 그대로** — `NOT_FOUND` · `NOT_FOUND` · `NOT_A_DIRECTORY` · `UNSUPPORTED_SCHEME` |
| **D-6G2c-23** app 층 HTTP 금지(게이트 술어) | 구현 | `app/pipeline.py` 에 `import urllib.request` 한 줄 | `lint-imports` BROKEN **그리고** AST 스윕 RED |
| **D-6G2c-24** 파생 지역 변수 한 단계 | 구현 | 파생 변수의 소비자를 한 단계 더 멀리(거동 동일) | RED(삼중 둘 소실) |
| | | 공시가 seed 0 만 따르고 넷은 출하값 고정 | RED(쌍 전수에서 seed 1~4; seed 0 은 초록) |

**게이트 술어를 바꾼 커밋**: `49505a74`(D-6G2c-23 — import 계약 + AST 스윕). verifier 표적 재검증 대상.

### 착수 실측 — D-6G2c-24 의 「넘치는가」 판정

파생 추적을 켜자 삼중이 **41** 늘었다(87 → 128). 그중 **열둘이 거짓 양성**이었다: 상수 첨자 좁히기가
파생 쪽에 없어 `stability_seeds[0]` 를 품은 식이 seed 다섯 전부를 나르는 것으로 읽혔고, 쓰이지 않은
seed 넷이 소비자 셋마다 올라왔다. 직접 훑기와 같은 규칙으로 좁혀 **29**(128 → 116)로 줄였고, 29 는
전부 실제 쓰임이다
(창 폭이 창 경계를 만드는 자리 · embargo 가 이력을 끊는 자리 · 업무별 호출 수가 예산 비교로 가는
자리 · 유의수준이 필요 쌍 수 공시로 가는 자리 등). 그래서 **등재로 되돌리지 않았다**.

29 전부 `DERIVED` 로 등재하고, 그 어휘는 같은 (키, 자리)에 `PROBE:`/`CLASS` 가 함께 있어야만 쓸 수
있다는 새 등식을 붙였다 — 동반 없이 등재하면 RED 다. 수정 라운드 1 에서 `DERIVED` 자체도 등식이 됐다
(아래 P-5).

**앞 판의 「39」는 틀린 수였다**(verifier F-6) — 41 − 12 = 29 다. 명단 덤프 네 벌의 줄 수로 다시 셌다.

### 수정 라운드 1 (D-6G2c-35) — verifier r1 `not-ready` + code-review r1

| finding | sev | 처분 | 변이 | 결과 |
|---|---|---|---|---|
| **F-1 = P-4** 예외를 받은 모듈이 스윕 밖 | high(게이트) | 구현 | 셋 각각에 `import urllib.request` | **RED 셋**(`lint-imports` 는 뿌리 간선 예외라 KEPT — 그래서 스윕이 진다) |
| **F-2 = P-3** 목록 밖 네트워크 모듈·동적 import | medium | 구현(열거) + 등재(경계) | `imaplib`·`poplib`·`nntplib` | BROKEN + RED 셋 |
| | | | `asyncio` · 동적 import(경계 밖) | 둘 다 초록 — **경계의 실측** |
| **F-3 = P-2** 검사-후-쓰기 창 | medium | 구현(`"xb"`) | `xb` 를 `write_bytes` 로 되돌림 | 끊긴 링크 test RED(두 기동 test 는 사전 `exists()` 가 이미 잡아 초록) |
| **F-4** 길이 비교로 되돌려도 초록 | medium | 구현(test) | `present == approved` → 길이 비교 | 새 test RED, 나머지 351 초록 |
| **P-5** `DERIVED` 가 저자 선언 | medium | 구현 | 직접 쓰임 하나를 `DERIVED` 로 거짓 등재 | 새 등식 RED + 앵커 동반 등식 RED |
| **F-5** `%00` 에서 예외 누출 | low | 구현(두 층) | 각 층의 닫힌 사유 제거 | 판독기 test RED · CLI 전수 사유 RED |
| **F-7** 표지·행 수 정합 미검사 | low | 구현 | 불변식 제거 | 어긋난 네 쌍 test RED |
| **P-7** basename 으로 센 「한 자리」 | low | 구현 | 같은 basename 의 다른 파일로 이동 | 앞 판 **초록** → 이 판 **RED** |
| **P-8** `ast.Tuple` 누락 | low | 구현 | `ast.Tuple` 제거 | RED(명단은 무변경 — 합성 조각으로 잰다) |
| **P-9** 출력 자리가 파일 | low | 구현 | 사전 거부 제거 | 전수 사유 그 파라미터 RED |
| **P-13** 음성 대조 입자 | low | 구현 | — | `detail` 끝으로 두 검사를 가른다 |
| **F-6 · P-6 · P-10** 문면 | low | 구현 | — | 수치 정정(39 → 41) · 제외 축 셋 · 낡은 docstring 셋 |
| **P-11** 문서 창이 문자 200 | low | **등재** | — | 이 slice 가 만든 test 가 아니다. 여백은 실측돼 있다(119자) |
| **P-12** `COVERED` 의 뜻 | info | **등재** | — | 아래 알려진 제한 |

새로 **코드로 막지 않기로** 한 것(F-2 경계, 알려진 제한): 동적 import(`importlib` · `__import__`)와
목록 밖 네트워크 경로(`asyncio` · `multiprocessing.connection` · `subprocess`·`os` · `webbrowser` ·
`wsgiref`). 두 층이 보는 것은 **import 문**이고, 그 층을 넘어서려면 실행 시점 관측이 필요하다 —
그것은 serving 순수성 게이트가 지는 다른 층이다. 이 문장은 pyproject 주석과 test 모듈 docstring
두 자리에 있다. **scope.md 위협 모델 「방어하지 않는 것」 한 줄은 팀장 소관이라 넣지 않았다.**

### acceptance — `ml-engine` job 명령 그대로, 전부 exit 0

| step | 핵심 결과 |
|---|---|
| S-1 `uv sync --frozen --all-extras` | exit 0 |
| S-1b serving extras 분리 | 금지 패키지 다섯 모두 미설치 |
| S-2 `ruff check .` · `ruff format --check .` | 위반 0 · 221 파일 포맷 일치 |
| S-3 `mypy --strict src/ml_engine` | 97 파일 이슈 0 |
| S-4 `lint-imports` | 계약 **8 kept, 0 broken**(앞 판 8, 금지 뿌리가 다섯 → 열셋) |
| S-5 `pytest tests -q` | **1417 passed** (331s) |
| S-6 설계 래칫 | 위반 0 |
| S-7 재활용 출처 두 자리 + 양성 대조 | 위반 0 · 양성 대조가 실패함을 확인 |
| S-9 Python 버전 두 자리 | exit 0 |
| S-11 wheel 빌드 + 설치본 재수출 | 1 passed |

무거운 step 은 `flock` 으로 K 레인의 Gradle 과 직렬화했다. 실수집 java 프로세스는 건드리지 않았다.

### 새 public 표면 (2b)

| 표면 | 변화 | 밖에 허락하는 것 |
|---|---|---|
| `DivisionCoverage.ABSENT` | enum 값 하나 추가 | 읽기만 — 판정 JSON 어휘 |
| `records.DivisionCoverageRecord` | 새 dataclass(업무·행 수·표지) | 값을 **담기만** 한다. 임계를 받는 자리가 없다 |
| `evaluation.policy.APPROVED_SEED_KEYS` · `SEED_PREFIX` | 비공개 이름이 공개로(중복 제거) | 읽기만. 패키지 `__all__` 에는 올리지 않았다 |
| `evaluation.policy.seed_key_mismatch` | 새 함수(값 하나 → 문면\|`None`) | 정책 값을 받지 않는다 — 평탄 값 매핑의 키 집합만 본다 |
| `app.backtest_cli._Refusal` | 비공개 enum, 라운드 1 에서 값 둘 추가 | 밖에 없다(이름이 `_` 로 시작) |
| `SnapshotUnreadableReason.INVALID_PATH` | enum 값 하나 추가(라운드 1 F-5) | 읽기만. 문면은 예외 **이름**만 나르고 경로를 싣지 않는다 |
| `PolicyUse.derived` · `scan_source_for_uses` | test 지원 모듈(`tests/evaluation/_backtest_support.py`) | 출하 표면이 아니다 — 명단이 재는 사실을 나르고 합성 조각을 훑는다 |

`backtest_cli` 의 public 표면은 `main(argv)` **하나 그대로**이고 그 사실을 기존 test 가 계속 잰다.
임계·seed·경로를 낱개 인자로 받는 자리는 생기지 않았다.

### 판정 JSON 어휘 변경이 건드린 기대값

**공유 파일 하나가 함께 움직였다**: 스키마 문서 §2 의 「판정문의 업무 대표 어휘」 문장(팀장 커밋
`ae1077e4`). 그 문장이 판정 JSON 어휘의 정본이고 test 가 그것과 enum 의 **집합 등식**을 단언하므로,
D-17 은 문장이 먼저 바뀌어야 성립한다(D-6G2c-31). **rollback 의 공유 파일 목록에 그 커밋이 들어간다**
— 되돌릴 때 hunk 격리가 필요한 자리다(그 문서의 다른 절은 이 slice 가 읽기만 했다).

등식 창의 실측(팀장 요청): 마커 「판정문의 업무 대표 어휘」는 문서에 **한 번** 나오고, 그 뒤 200자
창에 백틱 대문자 토큰이 다섯(`COVERED` · `UNDERPOWERED` · `ABSENT` · `ABSENT` · `UNDERPOWERED`)
있어 집합은 정확히 셋이다. 창 안에 **다른 대문자 백틱 토큰은 없다**(정책 키 이름은 소문자라 패턴에
걸리지 않는다). 가장 늦게 처음 나오는 토큰이 창 73자 자리이고 끝까지 119자 여유가 있다 — 창을 넘길
위험은 지금 없다. 다만 마커 뒤 200자 안에 **새 대문자 백틱 토큰을 넣으면** 집합 등식이 깨진다(창 바로
뒤 100자에는 `COVERED` 가 한 번 더 있고, 그것은 창 밖이라 무해하다).

golden 은 **스냅숏 입력**(`manifest.json`·`rows.jsonl`·`sample-list.tsv`)이고 판정 JSON 출력이 아니다 —
그래서 왕복 golden 바이트는 한 줄도 바뀌지 않았다. 그 밖에 바뀐 기대값은 **test 안의 단언 셋**이다:

- 업무 대표 공시를 읽던 단언(행 0 → `ABSENT`, 행 한둘 → `UNDERPOWERED`). 근거: 스키마 문서 §2 의
  「판정문의 업무 대표 어휘」 문장(팀장 커밋 `ae1077e4`, D-6G2c-31)이 정본이고 test 가 그 문장과
  enum 의 집합 등식을 단언한다. 기대 하한은 test 에 적지 않고 로드된 정책에서 꺼낸다.
- 쓰임 명단 덮개 등재(삼중 29 추가). 근거: 생성된 명단과의 등식이 그 목록을 강제한다 — 손으로 고른
  것이 아니다.
- 공시 칸 단언의 파라미터가 칸 하나에서 (칸, 키) 쌍으로. 근거: PR #53 리뷰 ④.

**golden 을 자동 생성하지 않았다.**

### 이탈과 알려진 제한

- **in_scope 정정 둘을 받았다**: 스키마 문서 §2 어휘 문장(D-6G2c-31, 팀장 편집) ·
  `adapters/snapshot_files.py`(D-6G2c-32). 둘 다 P 레인이 보고해 계약이 갱신된 뒤에 구현했다.
- `urllib` ignore 가 걸린 `app/backtest_cli.py` 자리에서는 `import urllib.request` 한 줄이
  `lint-imports` 를 붉히지 않는다(같은 뿌리 간선이라 ignore 가 함께 먹는다 — 실측). AST 스윕이 그
  자리를 잡는다. 두 층을 둔 이유가 그것이다.
- 파생 추적은 **한 단계**다. 두 단계 이상과 컨테이너 경유는 따라가지 않는다 — 담긴 값을 좇으려면
  첨자·키를 해석하는 데이터 흐름 해석기가 된다. 위 변이 하나가 그 제한의 실측이기도 하다.
- 업무 표지와 창 판정이 다른 축임을 거동으로 잠갔다(공사 다섯 행이 `UNDERPOWERED` 인 판에서 창 셋이
  서고 후보가 `StrategyPassed`). **같은 데이터로 표지만 바꿀 수는 없다** — 두 축이 같은 정책 키를
  읽으므로 하한을 밀면 창 판정도 함께 움직인다. 그래서 거동 축은 「표지가 내려간 판에서도 창이
  선다」까지이고, 그 이상은 재지 않았다.
- **P-12(운영자 가시)** — `COVERED` 의 뜻이 「행이 있다」에서 「행이 **창당 하한(483) 이상**」으로
  바뀌었다. 표본 24,000 을 업무 다섯으로 나누는 판에서는 업무 대부분이 `UNDERPOWERED` 로 공시될 수
  있다. 거동은 안전하다 — 이 표지를 읽는 판정 경로 소비자가 없다(공시 전용). 판정문을 읽는 사람의
  기대가 바뀌는 자리라 운영자에게 보이게 적는다.
- **P-11** — 어휘 등식 test 의 문서 창이 문자 200 이다(이 slice 가 만든 test 가 아니다). 지금 여백은
  119자이고 창 안에 다른 대문자 백틱 토큰이 없다 — 그 문단에 ALL-CAPS 토큰이 하나 더 들어오면 거짓
  RED 다. 창을 문단 경계로 끊으면 닫힌다.
- **F-2 경계** — 동적 import 와 목록 밖 네트워크 경로는 두 층 모두 보지 못한다(위 라운드 1 절).
- **F-7 잔여** — 음수 행 수는 표지 불변식이 잡지 못한다. 유일한 생성자가 `Counter` 로 세므로
  도달하지 않고, 수를 적으면 숫자 리터럴 게이트의 허용 목록을 늘려야 해서 닫지 않았다.
- **in_scope 한 줄 필요** — 라운드 1 의 P-10 이 `ml-engine/src/ml_engine/app/__init__.py` 의 문면
  한 곳을 고쳤다(거동 0). 그 파일은 in_scope 열거에 없다(`app/backtest_cli.py` 만 열려 있다) —
  D-6G2c-35 가 P-10 을 구현으로 배정하고 라운드 경계를 `ml-engine/**` 로 적어 그대로 했다. 계약
  갱신이 필요하면 팀장 판단이다.
- `in_scope` 밖 파일 변경 **없음**(P 레인 커밋 전부 `ml-engine/**` 안).
