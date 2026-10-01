# M6/6G 실수집 runbook — KONEPS 개찰 데이터 수집 → 스냅숏 → 백테스트 (초안 2026-10-01)

> 정본 계약: `reports/evidence/m6/6g/scope.md`(A-1~A-5, D-6G-1~82) · `reports/evidence/m6/6g2d/scope.md`(실행 상태 형식 v2, D-6G2d-55 실수집 시작 조건).
> 이 문서는 **절차**만 담는다. 값의 근거는 위 계약이고, 실행 결과는 `reports/evidence/m6/6g/commands.md` 「실 KONEPS 호출」 절에 **건수·비율만** 적는다(원문·키 없음).

## 0. 시작 조건 (전부 참이어야 1 로 간다)

| 조건 | 상태(2026-10-01) | 근거 |
|---|---|---|
| 6G 머지 + 6G-2d 머지 | ✓ PR #50 · #51 | 6G D-6G-77 |
| A-3 재호출 상한 | ✓ N=3 확정(단위 = 마지막 정착 뒤 일시 실패 결말 수, 끊긴 라운드 `INTERRUPTED` 하나, 관문 거부 미계수 · 창 = 디렉터리 생애) | 운영자 2026-10-01 |
| `OPEN-6G2D-MAX-PAGES-FINAL` | ✓ 유지(5,000 초과 참가 축은 확정 제외, 계수 공시) | 운영자 2026-10-01 |
| `OPEN-6G-REVIEW-FOLLOWUPS` ★ 셋 | ☐ 6G-2c 머지 대기(찢어진 조각만 남은 꼬리 라운드 · `incompleteAValues` 의 기초금액 축 의존 · 복구 쓰기 순서·잠금 가드) | 6G-2d D-6G2d-53 |
| 운영계정 서비스 키 | ☐ 환경에 없음 — 운영자가 **저장소 밖 파일**로 제공 | 6G 운영자 결정 2026-09-27 |
| **수집 범위 상한 31일**(`COLLECTION_RANGE_POLICY.maxSpanDays`, 6F-8 D-6F8-3)이 **개찰 갈래에도** 걸린다 — A-1 의 최대 16주 창은 기동 거부(`SPAN_TOO_LONG`)되고, 확정 표본이 from/to 를 고정해(`sample-scope.json`) 창을 쪼개 같은 디렉터리로 돌릴 수도 없다 | ☐ **차단** — 개찰 갈래의 범위 정책을 따로 두는 slice(`OPEN-6G-OPENING-RANGE-CAP`) 머지 대기 | PR #52 `/code-review` 2026-10-01 |
| 백테스트 CLI | ☐ 없음 — 스크립트 호출(2-4) 또는 `OPEN-6G-BACKTEST-CLI` | PR #52 |
| DEC-03 지자체 판별 | ✓ 현행 유지(판정 불가 계수 + 민감도 두 판) | 운영자 2026-09-30 |

## 1. 호스트·DB 준비 (키 없이 할 수 있는 것)

1. **호스트 점검을 별도 호출 셋으로**: `pgrep -af 'GradleWrapperMain|GradleWorkerMain|GradleDaemon|pytest'` · `free -m`(available ≥ 6 GB **그리고** Swap free ≥ 2 GB) · `ps -eo pid,rss,args --sort=-rss | head`. 수집 JVM 은 가볍지만 같은 호스트의 다른 세션(easy-doc · kis_paper · legacy `bid-vector` compose)과 겹친다.
2. **개발 DB**: 컨테이너 `bid-vector-v2-dev`(postgres:16.4, `127.0.0.1:55432`, user/db `bidvector`). 멈춰 있으면 `docker start bid-vector-v2-dev` 뒤 `pg_isready`. flyway 는 V17 까지 적용돼 있어야 한다(`SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1` → `17`). **legacy compose(`bid_vector_db` 5432)와 포트가 다르다 — 55432 를 쓴다.**
3. **저장소 밖 디렉터리**(자동 생성되지 않는다, 둘 다 **WSL 내부 ext4** — DrvFs/9p 는 디렉터리 fsync 가 실패해 경고가 난다):
   - 실행 상태: `~/.local/bid-vector-run-state/m6-6g/`(공고 목록 갈래와 개찰 갈래가 **같은 디렉터리**를 쓴다 — 상한 회계가 하나다)
   - 스냅숏: `~/.local/bid-vector-snapshots/`
4. **jar**: `./gradlew --no-daemon :app:bootJar` → `app/build/libs/app.jar`. 빌드 SHA 를 적는다(`git rev-parse --short HEAD`, `bidvector.*.release-sha` 에도 넣는다).
5. **키 파일**: 운영자가 저장소 밖(예: `~/.config/bid-vector/koneps.env`, 모드 600)에 `KONEPS_SERVICE_KEY_RAW=<원문형 키>` 한 줄로 둔다. **원문형**이어야 한다 — `ServiceKey` 가 스스로 URL 인코딩한다(인코딩형을 넣으면 이중 인코딩으로 `resultCode 30` + 쿼터만 소모, 6F-8 실측). 값은 명령 문자열·argv·history·transcript 어디에도 나타나지 않게 **서브셸에서 읽어 그 프로세스 환경에만** 넘긴다. 형태 확인은 `grep -c` 같은 계수만.

## 2. 실행 — 세 갈래, 순서 고정

모든 실행은 `SPRING_MAIN_WEB_APPLICATION_TYPE=none`(HTTP 표면 없음) + `mode=once`. 종료 코드: `0` COMPLETE · `2` INCOMPLETE(상한·쿼터·일시 실패로 멈춤, 다음 실행이 이어 돈다) · `3` ALREADY_RUNNING(같은 디렉터리를 다른 프로세스가 쥠). 기동 실패 출력에 `RUN_STATE_FORMAT_MISSING|MISMATCHED|LEGACY_LINE` 이 보이면 **실행 상태 디렉터리가 옛 형식**이다 — 고치지 말고 보고(실수집 전에는 그런 디렉터리가 없어야 한다).

공통 인자(값은 A-1 승인 그대로; 비밀 아님):
```
--bidvector.persistence.jdbc-url=jdbc:postgresql://127.0.0.1:55432/bidvector
--bidvector.persistence.username=bidvector
--bidvector.koneps.base-url=...(기본값, 생략 가능)
```
DB 자격(`BIDVECTOR_PERSISTENCE_CREDENTIAL`)과 운영자 토큰(`OPERATOR_CREDENTIAL_VALUE` — `BidVectorApplication` 이 모드와 무관하게 **항상** 바인딩하므로 세 갈래 모두 필수)도 키와 같은 방식으로 환경에만.

### 2-1. 공고 목록 갈래 (표본틀) — 1회
```
( set -a; . ~/.config/bid-vector/koneps.env; set +a; \
  BIDVECTOR_KONEPS_SERVICEKEY="$KONEPS_SERVICE_KEY_RAW" \
  SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  java -jar app/build/libs/app.jar \
    --bidvector.collection.mode=once \
    --bidvector.collection.from=<업무별 하한율 변경일 중 가장 이른 날> --bidvector.collection.to=<어제> \
    --bidvector.collection.categories=construction,service \
    --bidvector.collection.calls-per-day=20000 --bidvector.collection.calls-total=80000 \
    --bidvector.collection.run-state-dir=$HOME/.local/bid-vector-run-state/m6-6g \
    --bidvector.collection.release-sha=<jar SHA> \
    <공통 인자> )
```
- 기간(A-1): 국가 공사 2026-01-30 · 용역 2026-05 이후 ~ 현재, 최대 16주. **공고 목록 갈래는 한 실행이 31일 이하**여야 한다(`SPAN_TOO_LONG`) — 31일 창을 **순서대로 여러 번** 돌린다(멱등, 같은 실행 상태 디렉터리 — 이 갈래는 표본을 고정하지 않는다).
- 끝나면 적을 것: exit · 슬롯·절단·멈춤 · 페이지 · 수신 · 정규화 · 중복 · 탈락 · `notice` 행 수 · 호출 수(원장 HTTP 줄 수 == 예산 소비).

### 2-2. 개찰 갈래 (표본 확정 + 상세 축 넷) — 매일, 상한까지
```
( ... 같은 환경 ... java -jar app/build/libs/app.jar \
    --bidvector.opening-collection.mode=once \
    --bidvector.opening-collection.from=<2-1 과 같음> --bidvector.opening-collection.to=<같음> \
    --bidvector.opening-collection.categories=construction,service \
    --bidvector.opening-collection.sampling-seed=<착수 때 정한 고정 문자열, evidence 에 기록> \
    --bidvector.opening-collection.sample-size=24000 \
    --bidvector.opening-collection.calls-per-day=20000 --bidvector.opening-collection.calls-total=80000 \
    --bidvector.opening-collection.run-state-dir=$HOME/.local/bid-vector-run-state/m6-6g \
    --bidvector.opening-collection.release-sha=<jar SHA> \
    <공통 인자> )
```
- **지금 코드로는 from/to 가 31일을 넘으면 기동 거부**된다(0 절 차단 항목). 개찰 갈래는 확정 표본이 from/to·업무를 고정하므로 창을 쪼개 돌리면 「표본틀 범위가 지금 설정과 다르다」로 거부된다 — `OPEN-6G-OPENING-RANGE-CAP` 머지 뒤에만 A-1 기간으로 돌린다.
- 첫 실행이 표본을 **뽑고 확정**한다(`sample-list.tsv` · `sample-scope.json`, 장부 해시). 이후 실행은 같은 seed 라도 다시 뽑지 않는다 — 확정된 목록이 정본.
- 하루 상한(20,000)에 닿으면 exit 2 로 멈춘다. **KST 자정**이 지나면 다시 돌린다(일 상한은 KST 날짜 단위). 총 80,000 → 최소 4일.
- 쿼터 응답(`resultCode 22` · HTTP 429)은 실행 전체를 멈춘다(exit 2). 다음 날 재실행.
- **1일차 권고**: 첫 실행은 `calls-per-day=200` 정도로 짧게 돌려 로그·원장·DB 를 눈으로 확인한 뒤(키 흔적 0 · `raw_observation` 증가 · `attempts.jsonl` 의 PENDING/HTTP/AXIS 줄 형태), 같은 디렉터리로 상한을 20,000 으로 올려 이어 돈다(상한은 실행 인자이고 원장이 소비를 세므로 올려도 회계는 이어진다).

### 2-3. 스냅숏 추출 — 수집이 멈춰 있을 때(잠금을 못 잡으면 거부)
```
( ... DB 자격 + 운영자 토큰(키는 불필요) ... java -jar app/build/libs/app.jar \
    --bidvector.snapshot-extract.mode=once \
    --bidvector.snapshot-extract.from=<첫 수집 실행일> --bidvector.snapshot-extract.to=<오늘> \
    --bidvector.snapshot-extract.snapshot-id=m6-6g-<YYYYMMDD> \
    --bidvector.snapshot-extract.output-dir=$HOME/.local/bid-vector-snapshots/m6-6g-<YYYYMMDD> \
    --bidvector.snapshot-extract.run-state-dir=$HOME/.local/bid-vector-run-state/m6-6g \
    <공통 인자> )
```
- `from/to` 는 **관측 창**(`observed_at`, 공고일 창이 아니다 — 둘을 같게 두면 manifest 가 거짓을 말한다)이고 `to` 가 정책 기준일도 정한다 — 실제 수집이 돈 날짜 범위를 넣는다. `output-dir` 은 **스냅숏마다 다른 디렉터리**(러너가 `rows.jsonl` 등을 그 디렉터리에 바로 쓰므로 같은 디렉터리를 재사용하면 앞 스냅숏을 덮어쓴다; `snapshot-id` 는 manifest 안에만 들어간다).
- 산출: `rows.jsonl` · `manifest.json`(`schema_version=snapshot-v5` · `row_count` · `sample_size` · `sampled_without_detail` · `sampled_without_notice` · `incomplete_axis` · 기간·해시 칸; 네 항 항등식 `표본 = 행 + sampled_without_detail + sampled_without_notice + incomplete_axis`) · `sample-list.tsv`. `unusable_raw_rows` · `fractional_amounts` · `incomplete_a_values` 는 manifest 가 아니라 **러너 로그 마지막 줄 `snapshot-extract finished …`** 에만 있다(A-2) — 그 줄의 계수 아홉(`rows sampleSize sampledWithoutDetail skippedWithoutNotice incompleteAxis unusableRawRows fractionalAmounts incompleteAValues outsideSample`)을 그대로 적는다.
- 수집 **도중**에도 돌릴 수 있지만(원장을 먼저 읽어 진행 중 걷기는 미완으로 떨어진다) 잠금을 쥔 프로세스가 있으면 `ALREADY_RUNNING` 으로 거부된다 — 수집 실행 사이에 돌린다.

### 2-4. 백테스트 — Python, 저장소 밖 입력·출력

`ml_engine.app.backtest_job` 에는 **CLI 가 없다**(`run_backtest_job(*, snapshot_uri, backtest_policy_path, inference_policy_path) -> JobCompleted | JobFailed`, 예외 없이 결과 타입). `__main__` 추가는 `src` 변경이라 `OPEN-6G-BACKTEST-CLI`(6G-2c 후보)로 두고, 그때까지는 아래 한 줄 스크립트로 부른다 — 정책 둘(판정 정책 `strategy-backtest-v1.yaml` · 분포 엔진 정책 `inference-v1.yaml`, 둘 다 출하 파일 그대로, CLI 로 완화 불가):
```
cd ml-engine && SNAP=$HOME/.local/bid-vector-snapshots/m6-6g-<YYYYMMDD> OUT=$HOME/.local/bid-vector-verdicts/m6-6g-<YYYYMMDD> uv run python - <<'PY'
import os, sys
from pathlib import Path
from ml_engine.app.backtest_job import run_backtest_job, JobFailed
snap = os.environ["SNAP"]  # 판독기는 file:// URI 만 받는다(절대 경로, host 없음)
r = run_backtest_job(snapshot_uri=f"file://{snap}",
                     backtest_policy_path=Path("policy/strategy-backtest-v1.yaml"),
                     inference_policy_path=Path("policy/inference-v1.yaml"))
if isinstance(r, JobFailed):
    print("FAILED", r.reason, r.detail); sys.exit(1)
out = Path(os.environ["OUT"]); out.mkdir(parents=True, exist_ok=True); out = out / "verdict.json"; out.write_bytes(r.verdict_bytes)
print("verdict", out, "sha256", r.checksum, "bytes", len(r.verdict_bytes))
PY
```
- 판정 JSON(세 판 — 주·민감도 a·b 를 하나의 canonical 바이트열로)은 **스냅숏 디렉터리 밖**(`~/.local/bid-vector-verdicts/<snapshot-id>/`, 입력은 불변 — 그 sha256 이 evidence 의 닻이다)에 두고 sha256 을 evidence 에 적는다. 요약 한 장은 `reports/evidence/m6/6g/verdict.md`(공고 식별자 없이 집계만 — 제외 사유 계수 · 세 판의 부호 · 필요 표본 수 · `MAX_PAGES` 확정 수 · 코드 SHA · 스냅숏 id·sha256 · 정책 version·checksum).
- `JobFailed` 사유: `SNAPSHOT_UNREADABLE`(먼저 URI 가 `file://` 절대 경로인지, 디렉터리에 세 파일이 있는지 본다) · `SNAPSHOT_REJECTED`(판독 거부 — 추출 쪽 결함, 6G-2d 가 닫은 부류) · `*_POLICY_REJECTED` · `PRIMARY_HYPOTHESIS_COUNT_MISMATCH`. 설정 오류가 아니면 고치지 말고 보고.

## 3. 일일 보고 (수집 시작 때 일정을 세운다 — 사용자 지시 2026-09-30)

매일 한 번(KST 자정 뒤 실행이 끝났을 때):
- 그날 호출 수 / 일 상한 20,000 · 누적 / 총 상한 80,000 (원장 HTTP 줄 수로)
- 끝난 표본 공고 수(네 축 정착) · 축별 실패·미완·`INTERRUPTED`·`FinalFailure(MAX_PAGES)` 계수(원장 AXIS 줄에서; manifest 의 `incomplete_axis` 는 추출 때)
- 쿼터·예상 밖 응답(`resultCode` 분포) · 종료 코드
- 다음 실행 예정 시각
값은 `attempts.jsonl` 과 `state.json` 에서 **계수만** 뽑는다(공고 키는 해시, 키·원문 없음).

## 4. 멈춤 조건과 되돌림

- 멈춘다: 기동 실패에 형식 토큰 · 키 흔적(64자 16진 패턴)이 로그에 1건이라도 · `StructureFailure` 가 한 축이 아니라 전반 · 일 상한 밖 호출(원장 HTTP 줄 수 > 20,000/일) · `incomplete_axis` 가 표본의 큰 몫(판정 전 운영자 보고).
- 되돌림: 수집 데이터는 개발 DB(`raw_observation` append-only, `notice`)에만 있다 — 폐기는 `docker rm -f -v bid-vector-v2-dev`(볼륨 포함, 운영 데이터 아님). 실행 상태 디렉터리를 지우면 **상한 회계가 0 에서 다시 선다** — 운영자 승인 없이는 지우지 않는다(6G-2d 가 닫은 사고 부류).

## 5. 알려진 제한 (판정문에도 실린다)

- 재호출 상한 N=3 은 디렉터리 생애 누적이라 서로 다른 날의 일시 실패 셋이 같은 축을 확정 제외한다(계수 공시).
- `MAX_PAGES` 확정: 참가 5,000 초과 축은 `incomplete_axis`.
- append 마다 fsync 셋(약 7 ms) — 80,000 호출이면 수십 분(`OPEN-6G2D-FSYNC-BATCHING`).
- 「정착했으나 0 행」·빈 번호·소수 금액·반쪽 A 는 기존 사유로 떨어지고 계수로 공시(`OPEN-6G2D-EMPTY-AXIS-REASON`).
- 백테스트 job 에 CLI 가 없다 — 위 스크립트로 부른다(`OPEN-6G-BACKTEST-CLI`).
- **수집 범위 상한 31일이 개찰 갈래에도 걸린다**(`OPEN-6G-OPENING-RANGE-CAP`) — 닫히기 전에는 A-1 기간 실수집 불가.

## 6. 검증 기록

2026-10-01 runner 대조(읽기 전용, main `30c6659e`): 2-1~2-3 의 `mode=once` 조건(`@ConditionalOnProperty` 셋) · 속성 이름 kebab 바인딩(`callsPerDay`→`calls-per-day` 등) · `SPRING_MAIN_WEB_APPLICATION_TYPE=none`(6F-8 checklist) · `BIDVECTOR_KONEPS_SERVICEKEY`→`bidvector.koneps.serviceKey` · 러너 마지막 줄의 계수 아홉 — 코드와 일치(단 토큰은 `snapshot-extract finished` 이고 초판이 `snapshot finished` 로 적은 것은 PR #52 리뷰가 잡았다). 2-4 는 CLI 부재를 그 대조가 잡아 스크립트로 바꿨다. PR #52 `/code-review`(2026-10-01)가 추가로 잡은 것: 범위 상한 31일(차단) · `file://` URI · 스냅숏별 출력 디렉터리 · 운영자 토큰 상시 필수 · manifest 키 이름 · 관측 창 뜻 · 판정 출력 위치 — 전부 반영.
