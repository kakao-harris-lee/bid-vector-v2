# bid-vector V2 재작성 작업실

이 디렉터리는 기존 [`bid-vector`](./bid-vector)를 통째로 옮기는 migration 저장소가 아니다.
V2는 성숙도가 다른 두 레이어를 서로 다른 전략으로 다룬다.

- **service/업무 레이어 — Kotlin으로 재설계·재작성한다.** 기존 Python service는 불필요하게
  방대해졌고 basis·rate·자격·queue 계열 회귀가 반복됐다. 재작성의 목적은 기능 추가가 아니라
  **유지보수성 향상과 회귀 감소**다. 기존 service 코드는 도메인 규칙 후보, 데이터 사례,
  실패 원인을 조사하기 위한 **참고 자료**로만 사용한다.
- **Python ML 엔진 — 재활용이 기본 전략이다.** 기존 ML 구현(LightGBM/KDE 수학, feature
  변환, 평가 로직)은 완성 단계이며 활용 가능하다. 새로 설계하지 않고, 새 패키지 경계와 M2
  계약(serving 순수성, versioned contract)에 맞게 **이식·정리하고 필요한 튜닝을 수행**한다.
  단 service 결합(FastAPI/SQLAlchemy/Celery, DB 접근, 업무 판정)은 함께 가져오지 않는다.

두 레이어 모두 기대 동작은 승인된 도메인 명세와 authoritative fixture로 판정한다. 코드를
재활용한다는 사실이 기존 Python 출력을 정답으로 만들지 않는다.

## 조사 기준

- 조사일: 2026-08-21
- 기존 저장소 기준: `bid-vector`의 `main` / `ed4b06c`
- 확인 규모: `app/**/*.py` 88,588줄, Python 테스트 288개 파일 / 100,201줄
- 큰 파일 예: `collector.py` 971줄, `config.py` 848줄, `persistence.py` 825줄
- 현재 회귀 방어: Ruff, 점진적 mypy strict island, pytest, PostgreSQL tier,
  `scripts/design_ratchet.py`, characterization/golden test
- 아직 없는 신규 경계: Kotlin application, versioned contracts, 독립 ML engine
  (기존 ML 코드를 이 경계로 이식한다)

기존 저장소의 작업 트리에 있던 미커밋 문서는 위 코드 기준선의 일부로 간주하지 않는다.
구현을 시작할 때는 마일스톤 0에서 SHA와 측정값을 다시 고정한다.

이 디렉터리는 기존 `bid-vector`와 분리된 독립 Git 저장소다(baseline 커밋 `74c1516`,
2026-08-21 초기화). 문서·하네스 변경은 이 저장소에 커밋하여 Codex 리뷰의 base/head를
고정한다. `bid-vector` symlink는 로컬 조사 편의용이며(gitignore 대상) V2 build/runtime
dependency가 되어서는 안 된다.

## 문서 읽는 순서

1. [`v2-지침서.md`](./v2-지침서.md): 전체 원칙, 목표 구조, 완료 정의
2. [`agent-workflow.md`](./agent-workflow.md): Claude 구현과 Codex 독립 리뷰 절차
3. [`data-extract.md`](./data-extract.md): 근거가 있는 검증 corpus를 만드는 절차
4. [`milestone-0.md`](./milestone-0.md)부터 번호 순서대로 실행
5. Claude는 [`CLAUDE.md`](./CLAUDE.md), Codex는 [`CODEX-REVIEW.md`](./CODEX-REVIEW.md)를
   역할별 진입점으로 사용

| 단계 | 목적 | 런타임 변경 |
| --- | --- | --- |
| M0 | 기존 구현 조사, 요구사항·회귀 장부, 아키텍처 결정 | 없음 |
| M1 | Kotlin 빌드 골격과 순수 도메인 커널 | 신규 V2 코드만 |
| M2 | Kotlin↔Python 및 외부 API 계약 | 신규 V2 코드만 |
| M3 | KONEPS 수집·정규화·저장 adapter | mock/fixture 우선 |
| M4 | workflow, event, 상태 제어, prediction gateway | side effect는 fake |
| M5 | 기존 ML 코드를 이식·튜닝한 독립 Python training/serving engine | DB 없는 serving |
| M6 | persistence·API·E2E·배포 후보 완성 | 승인된 테스트 환경만 |

## 절대 규칙

- Claude가 구현하고, Codex는 구현에 참여하지 않은 상태에서 diff를 읽기 전용으로 리뷰한다.
- Codex `approve` 전에는 다음 마일스톤으로 넘어가지 않는다.
- 기존 Python의 출력과 같다는 이유만으로 V2 동작을 승인하지 않는다. 승인된 도메인 명세,
  공식 데이터 계약, 수작업으로 판정한 기대값이 우선한다.
- Kotlin service 재작성에서 Python 파일·클래스·함수·DB schema·API를 1:1 번역하거나
  호환시키지 않는다.
- ML 엔진은 코드 재활용이 기본이지만, 재활용해도 새 패키지 경계·M2 계약·크기/의존성 래칫을
  통과해야 하고 service 결합은 함께 가져오지 않는다. 재활용 범위와 출처(파일, commit)를
  기록한다.
- 기존 구현과의 differential 비교는 누락과 예상 밖 차이를 찾는 진단 수단일 뿐 자동 합격
  조건이 아니다. 차이는 도메인 명세로 판정한다.
- `if` 자체를 금지하지 않는다. 숨은 의미, 중첩 라우팅, 중복 규칙을 금지하고 단순 guard
  clause와 명시적 validation은 허용한다.
- 임의로 만든 샘플을 운영 사실처럼 쓰지 않는다. 모든 fixture는 출처, 정규화 규칙, 해시를
  가진다.
- DB write, 실제 KONEPS/LLM 호출, Telegram/email 발송, 원격 push/merge, release 전환은 별도
  사용자 승인 없이는 실행하지 않는다.

## 작업 시작 조건

M0 산출물이 승인되기 전에는 Kotlin/Spring/Python 서비스 코드를 만들지 않는다. 기존
`bid-vector`에는 V2 코드를 추가하지 않고 이 디렉터리에서 새 프로젝트를 만든다. 각
마일스톤은 하나의 큰 변경이 아니라 독립 검증 가능한 slice들로 나누며, slice마다
`Claude 구현 -> 로컬 검증 -> Codex 리뷰 -> 수정 -> 재리뷰 -> 사용자 승인`을 반복한다.
