# 학습·평가용 데이터 스냅샷 반출 요청 (서버 담당자용)

- **요청일**: 2026-09-07 · **요청 근거**: `docs/discovery/decisions-2026-09-07.md` Q-1 (c)·Q-3 (b),
  반출 계획 정본 `docs/discovery/ml-value-and-data-locality.md` §5
- **받는 쪽**: bid-vector 운영 서버 담당자 · **보내는 쪽**: V2 개발(운영자)
- **원칙**: 서버 접속 정보는 주고받지 않습니다. 담당자가 서버에서 아래를 실행해 **파일**로 전달합니다.
  개인·사업자 식별 정보는 원문으로 나오지 않습니다. 이 파일은 V2 저장소에 커밋되지 않고 개발 장비의
  저장소 밖 디렉터리에만 둡니다.

---

## 보내는 문안

안녕하세요. bid-vector V2 의 학습·평가 작업에 쓸 데이터 스냅샷을 부탁드립니다. 서버 접속 없이 담당자님이
직접 뽑아 파일로 주시면 됩니다. 필요한 것은 아래 셋입니다.

### 1. 데이터 스냅샷 — 테이블 셋, 전 기간

운영 DB(`bid_vector`, PostgreSQL 16)에서 세 테이블을 **CSV(UTF-8, 헤더 포함, gzip)** 로 뽑아 주세요.
parquet 이 편하시면 parquet 도 좋습니다. 공고번호와 상호는 **salted SHA-256** 으로 바꿔 주세요. salt 는
담당자님이 임의 문자열로 정하고, **운영자에게만 별도 채널로** 알려 주세요(다음 반출 때 같은 salt 를 써야
같은 공고가 같은 값으로 이어집니다). `psql` 한 세션 안에서 먼저 `SET snap.salt = '임의문자열';` 을 실행한 뒤 아래
`\copy` 셋을 실행하면 `current_setting('snap.salt')` 가 그 값을 읽습니다. **`\copy` 는 한 줄로** 적어야 합니다
(아래는 읽기 좋게 줄을 나눈 것이니 붙여 넣을 때 한 줄로 합쳐 주세요).

**① `projects` → `projects.csv.gz`**

```sql
\copy (
  SELECT id,
         encode(sha256(convert_to(current_setting('snap.salt') || coalesce(notice_number,''), 'UTF8')), 'hex') AS notice_number_hash,
         title, category, business_type_code, business_type_label,
         issuing_agency, demand_agency,
         budget_estimate, budget_min, budget_max,
         award_floor_rate, status, deadline, created_at,
         eligibility_raw
  FROM projects
) TO 'projects.csv' WITH (FORMAT csv, HEADER true);
```

- 빼는 컬럼: `description`, `requirements`, `semantic_text`, `embedding*`, `source_url` (본문·벡터는 이번 용도에
  없습니다). `issuing_agency`·`demand_agency` 는 공공기관명이라 원문 그대로 둡니다.

**② `historical_data` → `historical_data.csv.gz`**

```sql
\copy (
  SELECT id, project_id,
         encode(sha256(convert_to(current_setting('snap.salt') || coalesce(notice_number,''), 'UTF8')), 'hex') AS notice_number_hash,
         agency_name, category,
         base_amount, base_amount_basis, base_amount_estimated, basis_checked_at,
         bid_rate, reserve_prices, selected_numbers,
         opened_at, reserve_detail_checked_at, created_at
  FROM historical_data
) TO 'historical_data.csv' WITH (FORMAT csv, HEADER true);
```

- 빼는 컬럼: `predicted_price` (기존 시스템의 출력이라 정답이 아닙니다).
- `reserve_prices`(예비가 15개)와 `selected_numbers` 는 **문자열 그대로** 주세요. 이 둘이 이번 반출의 핵심입니다.

**③ `tender_results` → `tender_results.csv.gz`**

```sql
\copy (
  SELECT id, project_id, is_current,
         encode(sha256(convert_to(current_setting('snap.salt') || coalesce(winning_company,''), 'UTF8')), 'hex') AS winning_company_hash,
         winning_amount, winning_rate, result_status, announced_at,
         encode(sha256(convert_to(current_setting('snap.salt') || coalesce(opening_rank1_company,''), 'UTF8')), 'hex') AS opening_rank1_company_hash,
         opening_rank1_amount, opening_rank1_rate, opening_participant_count,
         opened_at, opening_checked_at, created_at
  FROM tender_results
) TO 'tender_results.csv' WITH (FORMAT csv, HEADER true);
```

- 빼는 컬럼: `winning_company`, `opening_rank1_company`, `opening_rank1_business_no` **원문**. 해시만 주세요
  (같은 업체가 몇 번 이겼는지는 세되 누구인지는 모르게).
- `is_current = true` 만 걸지 말고 **전부** 주세요. 재공고·차수 처리를 이쪽에서 봅니다.

세 파일이 나오면 `gzip projects.csv historical_data.csv tender_results.csv` 로 압축해 주세요.

### 2. manifest — `manifest.json` 한 파일

파일마다 아래를 적어 주세요. 이 값이 없으면 스냅샷을 쓸 수 없습니다.

```json
{
  "extracted_at": "2026-09-08T10:00:00+09:00",
  "server_git_sha": "git rev-parse HEAD 결과",
  "server_git_branch": "git rev-parse --abbrev-ref HEAD 결과",
  "alembic_revision": "alembic current 결과",
  "database": "bid_vector",
  "salt_shared_with": "운영자 (별도 채널)",
  "files": [
    {"name": "projects.csv.gz",        "rows": 0, "sha256": "", "min_created_at": "", "max_created_at": ""},
    {"name": "historical_data.csv.gz", "rows": 0, "sha256": "", "min_opened_at": "",  "max_opened_at": ""},
    {"name": "tender_results.csv.gz",  "rows": 0, "sha256": "", "min_announced_at": "", "max_announced_at": ""}
  ],
  "table_counts": {"projects": 0, "historical_data": 0, "tender_results": 0}
}
```

- `rows` 는 CSV 행 수(헤더 제외), `table_counts` 는 `SELECT count(*)` — 둘이 같아야 합니다.
- `sha256` 은 gzip 파일 기준(`shasum -a 256 파일명`).

### 3. 선택 — 있으면 함께

- `models/manifests/` 와 `models/training-runs/` 디렉터리 전체(수 MB). 이전 학습의 dataset id·metric 을 대조하는
  데 씁니다.
- 낙찰률 GBM 아티팩트 JSON — 컨테이너 환경변수 `PRICE_PREDICTION_AWARD_RATE_GBM_MODEL_PATH` 가 가리키는
  파일. **없으면 없다고만** 알려 주세요. 재현은 스냅샷 + seed 로 하므로 필수가 아닙니다.
- 임베딩 모델(`models/sbert/`)은 **보내지 마세요**. HuggingFace 에서 받습니다.

### 전달

- 예상 크기: 세 CSV gzip 합쳐 수십~100MB 대. 전달 채널은 편하신 것으로 부탁드리고, `manifest.json` 의 sha256 은
  파일과 **다른 채널**로 한 번 더 보내 주시면 대조하겠습니다.
- 운영 DB 에 쓰기는 없습니다. `\copy` 는 읽기만 하고, 서비스 컨테이너를 건드릴 필요도 없습니다. 다만
  training-worker 가 학습 중이면 끝난 뒤에 뽑아 주세요.
- 하지 말아 주실 것: `users`·`company_profiles`·`operator_strategies`·`notifications`·`bid_decision_records`
  등 운영자·알림 테이블은 **포함하지 않습니다**. `pg_dump` 전체본도 필요 없습니다.

끝나면 `manifest.json` 내용을 회신해 주세요. 감사합니다.

---

## 받은 뒤 이쪽이 하는 일 (담당자 몫 아님)

1. sha256·행 수 대조 → 불일치면 재요청.
2. `~/.local/bid-vector-snapshots/<extracted_at>/` 에 보관. V2 저장소에는 `manifest.json` 의 값만 evidence 로 적는다
   (`data-extract.md` §7 — DB 행 커밋 금지).
3. `fixtures/` 의 `observed` 층은 여기서 **소수 case 만** 비식별 인용(`access_approval_required` 해제 = 이 요청의 승인).
4. M5 5C 결정 실험(D-ML-2)의 입력 스냅샷 id 로 `extracted_at` + `server_git_sha` 를 쓴다.
