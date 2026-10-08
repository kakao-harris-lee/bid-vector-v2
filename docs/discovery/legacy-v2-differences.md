# legacy ↔ V2 차이 목록 — 의도적 폐기 · 후속 · 근거 부족 · 상호작용 모델 변경 (M6/6E-1, 2026-10-07)

> **지위.** 분류의 정본은 `docs/discovery/capability-map.md`(M0/0A)이고, 폐기 원칙의 정본은 `v2-지침서.md` §1·§9 다. 이 문서는
> 그 둘에 **흩어진 「legacy 에 있었고 V2 에 없다 또는 다르다」를 한 자리에 모은다** — 운영 반입 시점에 「이 기능은 V2 에 없다」를
> 한 페이지에서 읽기 위해서다. 분류를 바꾸지 않는다. 바꾸려면 capability-map 의 분류 줄을 고치고 이 문서는 그것을 따라 재생성한다.
> 계약 `reports/evidence/m6/6e1/scope.md` C-6.

## 0. 집계 등식 — 이 문서의 건수는 capability-map §10 과 같아야 한다

| 분류 | capability-map §10 | 이 문서 | 비고 |
|---|---:|---:|---|
| `V2 필수` | 61 | (이 문서 범위 밖 — `reports/evidence/m6/6e1/acceptance-trace.md` C-1) | — |
| `후속` | 17 | 17 (§2) | 분류 줄 서식은 **둘**이다 — 표준 14 + 굵은 서식 3(NOTI-02·NOTI-05·NOTI-06, 운영자 결정으로 재분류된 것) |
| `폐기` | 6 | 6 (§1) | — |
| `근거 부족` | 11 | 11 (§3) | 표준 10 + 굵은 서식 1(OPS-06). **굵은 서식은 두 분류에 걸쳐 모두 4 다**(cr r1 G-9 — 앞 판은 OPS-06 을 **셋째 서식**처럼 적었다. OPS-06 이 다른 것은 서식이 아니라 **뒤따르는 종속 문구**(`OPEN-OPS-10` 종속)다) |
| 계 | 95 | 95 | capability 절 105 중 **설계 입력 절 10**(ML-11 · DEC-11~13 · SET-07~10 · OPS-00 · OPS-21, 분류 줄 없음)은 capability 가 아니라 제외 |

기계 대조(저장소 루트에서; `reports/evidence/m6/6e1/commands.md` 에 결과):

```
python3 - <<'EOF'
import re
s=open('docs/discovery/capability-map.md',encoding='utf-8').read()
h=[(m.start(),m.group(1)) for m in re.finditer(r'^### ((?:COL|STR|QUAL|ML|DEC|NOTI|SET|OPS)-\d+) · ',s,re.M)]
c={}
for i,(p,cid) in enumerate(h):
    b=s[p:(h[i+1][0] if i+1<len(h) else len(s))]
    m=re.search(r'분류\*\*: \**`([^`]+)`',b)
    c[m.group(1) if m else '설계 입력']=c.get(m.group(1) if m else '설계 입력',0)+1
print(c)   # 기대: V2 필수 61 · 후속 17 · 폐기 6 · 근거 부족 11 · 설계 입력 10
EOF
```

## 1. 의도적 폐기 6 — legacy 에 있었고 V2 가 **승계하지 않기로 결정한** 것

| ID | legacy 기능 | 왜 폐기하는가(capability-map 분류 근거 요약) | V2 에서 그 자리에 있는 것 |
|---|---|---|---|
| COL-10 | mock / `fallback_mock` 데이터 — 라이브 수집 실패 시 하드코딩 공고 2건을 **실제 결과처럼** 영속화 | mock 행이 실데이터 경로를 타고 하류가 구분하기 어렵다(`metadata.mode` 하나뿐) | production 수집 경로에 fallback 없음 — 실패는 수집 보고의 계수·종료 코드(`INCOMPLETE 2`)로 드러난다. mock KONEPS 는 **test 소스셋에만**(`MockKonepsServer`) |
| QUAL-12 | 같은 면허를 판정하는 두 경로(자유 텍스트 vs 구조화 필드, 결과 타입·요건 없을 때 동작까지 전부 다름) | 같은 공고에 다른 답을 낼 수 있는 구조 자체 | 판정 경로 하나 — `LicenseGatePort` + 저장된 요건만 읽는 `StoredRequirementLicenseGate`(6F-5-a). 요건 추출(LLM)은 판정 경로 밖(6F-5-b, 운영자 승인 대상) |
| OPS-14 | in-process 스케줄러 이중 실행 경로 | 같은 작업이 두 경로로 돌 수 있는 구조 | 상주 스케줄러 없음 — 일회 러너(`mode=once`, cron 이 돌림) + 프로세스당 러너 하나 guard(`OneShotRunnerGuard`, 6F-10) + run-state 파일 잠금 / advisory lock 임대. 상주 스케줄러 도입은 `OPEN-6F10-SCHEDULER` |
| OPS-15 | 의존성 부재 시 동기 fallback shim — 비동기 import 실패 시 의미만 바꿔 동기 실행 | 숨은 fallback 이 거동을 조용히 바꾼다 | 없음. 미가용은 **값 또는 예외**로 드러난다 — ML 자리지킴 `UnavailableMlAnalysis`·발송 자리지킴은 호출되면 던진다(6F-10 A-5), 조용한 degrade 경로 0 |
| OPS-16 | 프로덕션 DB 전용 semantics 의 다른 엔진 무음 no-op | 엔진이 다르면 제약이 조용히 사라진다 | PostgreSQL 단일 엔진 — Flyway 마이그레이션 + test 는 Testcontainers 실 PostgreSQL(H2·인메모리 대체 0). `FOR UPDATE SKIP LOCKED`·advisory lock 같은 PG 의미를 그대로 쓴다 |
| OPS-17 | 관측이 전부 「생산된 행」 기반 — 소비자가 막히면 볼 행이 안 생겨 KPI 가 초록으로 남는다(OPS-00 사고) | 구조적 맹점 | **원칙은 채택, 계측은 미구현** — ADR 0005 D-8 「산출물의 부재가 관측 가능해야 한다」, D-7 「무엇을 계측할지가 결정」. 게이지·임계는 6E-2(`OPEN-OPS-03`·`OPEN-OPS-04`). 오늘 관측 가능한 것은 러너 종료 코드·보고 줄·DB 상태 분포(runbook `m6-6e-operations.md` §3) |

**폐기 6 의 부재를 잠그는 게이트는 없다**(착수 조사 실측). 구조상 부재인 것(OPS-15·OPS-16 — Kotlin 포트 모델·단일 엔진)과 게이트가 있어야 유지되는 것(COL-10 — production 수집에 fixture 경로가 생기지 않음; OPS-14 — 러너 guard 가 그 성질을 잠근다)을 가른 뒤, 게이트가 필요한 자리는 6E-2 후보로 넘긴다(`reports/evidence/m6/6e1/checklist.md`).

## 2. 후속 17 — legacy 에 있고 V2 범위 밖(backlog). **운영 반입 시점에 「V2 에 아직 없다」인 기능**

| ID | legacy 기능 | 요지 |
|---|---|---|
| STR-05 | 카테고리별 우선순위 가중 / 워크로드 감점 배율 | JSON 문자열 컬럼, 파싱 실패 시 조용히 빈 매핑으로 degrade — 정책이 소리 없이 사라지는 모양 |
| STR-12 | 전략이 다른 워크로드의 우선순위를 지배 | 자격 원문 백필의 2티어 정렬 |
| STR-13 | 실적 기반 임계치 튜닝 추천 | 결정 퍼널에서 추천 + experiment plan; 전략을 바꾸지 않는 튜닝 루프 |
| STR-14 | 온보딩 — 공고 데이터에서 전략·프로필 초안 역추천 | 집계만, 저장 없음, 확정은 별도 쓰기 경계 |
| QUAL-06 | 운영자 정답 라벨 축적과 정밀도 실측 | 3값 라벨 × 3 source |
| QUAL-07 | 「왜 오늘 추천이 없나」 원인 분해 | 파이프라인 순서의 원인 캐스케이드 집계 |
| QUAL-09 | 지역가산점 신호 | 3조건 AND 가점; legacy 스스로 「실제 적격심사 가산점 계산이 아니다」 |
| ML-06 | win-proxy 착지점 곡선 | 배선 0 의 측정 단계; 도메인 통찰은 `ml-value-and-data-locality.md`·`bid-price-predictability-2026-09-27.md` 로 인계 |
| ML-10 | predictor 자동 선택(rolling backtest) | 승격 게이트(ML-07)와 목적 중복 |
| DEC-07 | 강점 / 리스크 플래그 | 표시 전용 문장 |
| NOTI-02 | 알림 피로도 게이트(일일 상한 / 재알림 쿨다운) | pull 모델에서 가치 저하 — 운영자 결정 2026-08-27, `OPEN-NOTI-09` 해소 |
| NOTI-05 | 배달 outbox(커밋 경계를 넘는 전달) | at-most-once + best-effort 확정으로 「커밋 후에만 보낸다」로 충분. **V2 의 outbox(ADR 0005)는 이 capability 의 승계가 아니라 도메인 이벤트 경계다** — 이름이 같다고 같은 것이 아니다 |
| NOTI-06 | 채널 인바운드로 판단을 처리 | push 상호작용 — pull 모델 확정으로 재분류(운영자 결정 2026-08-26, §0.7) |
| NOTI-08 | 투찰 보고서 메일 | legacy 라이브 송신 경로 미검증(`OPEN-NOTI-04`) |
| NOTI-09 | 알림 전달 건강도 보고서 | 텔레메트리 집계·healthy/watch/critical |
| SET-05 | 추천가 vs 실낙찰가 정확도 리포트 | 정산 완료 건 기준, 표본 상한 `truncated` 명시 — 6G 백테스트가 같은 물음을 저장소 밖에서 묻는다 |
| OPS-18 | 비동기 작업 상태 조회(결과 백엔드 기반) | V2 workflow 모델 확정 뒤 판단 |

## 3. 근거 부족 11 — V2 요구인지 조사만으로 판정할 수 없어 **결정 대기**인 것(`OPEN-*` 또는 운영자 결정)

| ID | legacy 기능 | 왜 근거 부족인가 |
|---|---|---|
| COL-09 | 브라우저(Playwright) 크롤 | DOM id 13+ 하드코딩, 위치 휴리스틱 — 취약; V2 는 OpenAPI 수집(6G)으로 가고 있다 |
| STR-11 | Telegram 대화형 전략 편집 | pending 상태가 analytics 로그 행에 영속; V2 전략 편집은 HTTP 세션(6A-2b) |
| STR-15 | 실험 결과가 전략을 **무승인** 자동 갱신 | 의도된 운영인지 승인 게이트 누락인지 판정 불가 |
| QUAL-10 | 실적(수행실적) 자격 | **legacy 에 판정 자체가 없다**; 운영자 실적 데이터 없음 |
| ML-09 | Platt 캘리브레이션 | 사용자 응답 도달 여부 미확인; 라벨이 자격 판정에 의존 → 경계 위반 후보(v2-지침서 §3.2) |
| NOTI-11 | 채널 우선순위 / fallback | legacy 구현 없음, V2 요구 미확인(`OPEN-NOTI-07`) |
| SET-03 | 페이퍼 투찰 정산 | 항목마다 두 판정, 경계 불일치(SET-08) |
| SET-04 | 정산 진행 상태 표시 | 규칙표 + router 잔존 루프 |
| OPS-06 | 배포 전 queue topology 게이트 | `OPEN-OPS-10` ④ 종속(DB 큐 위에 재정의할지 폐기할지) |
| OPS-19 | realtime 이벤트 fanout | 조사 범위 밖(`OPEN-OPS-08`) |
| OPS-20 | 특정 검증 프로그램용 evidence 수집 스케줄 | 상시 capability 인지 불명(`OPEN-OPS-09`) |

## 4. 상호작용 모델 변경 — push·batch → **pull**(운영자 결정 2026-08-26, capability-map §0.7)

| capability | 변경 | 뜻 |
|---|---|---|
| STR-16(신규) | → `V2 필수` | pull 모델의 운영자 주 경로(목록·검색·요청) |
| NOTI-06 | `V2 필수` → `후속` | 알림 버튼 판단 처리는 push 상호작용 |
| NOTI-02 | `V2 필수` → `후속` | 알림을 다 소비하지 않아도 되므로 피로도 억제 가치 저하 |
| NOTI-05 | `V2 필수` → `후속` | at-most-once + best-effort |
| NOTI-10 | `V2 필수` 유지·**강화** | 앱 알림함이 기록이자 회수 경로 |
| DEC-02 | `V2 필수` 유지, **배치 선산출 제거** | legacy 에도 요청 경로가 있었으므로 변경분은 「배치 선산출을 승계하지 않는다」뿐 |

**이 모델이 큐 폭주의 뿌리를 없애지 않는다**(§0.7 라운드 2 정정): legacy OPS-00 의 21,321건은 투찰가 배치가 아니라 유사공고 임베딩 백필이 쌓은 것이다. `OPEN-OPS-03`·`OPEN-OPS-04` 는 활성 잔존.

## 5. 가져오지 않는 것 — 원칙(`v2-지침서.md` §1·§9)

- Python service 의 파일·클래스·함수 구조 · FastAPI/SQLAlchemy/Celery 내부 결합(ML 코드에 딸린 ORM·DB 접근·설정 로딩·업무 판정 포함) · 기존 DB schema 와 public API 의 자동 호환성 · 기존 출력값을 정답으로 보는 characterization · 버그·임시 fallback 을 보존하는 golden.
- 「기존 프로젝트의 모든 기능을 옮기는 것은 완료 조건이 아니다. M0 에서 채택한 capability 만 V2 범위이며, 나머지는 명시적으로 폐기하거나 후속 backlog 로 둔다.」 — 이 문서의 §1~§3 이 그 「명시」다.
- 같은 §9 의 완료 정의 「M0 회귀 ledger 의 각 항목에 예방 제약이 연결됨」은 `reports/evidence/m6/6e1/ledger-constraint-trace.md`(C-7)가 전수로 보인다 — 미연결 건수는 그 표의 머리에 있다.

## 6. 이 문서가 말하지 않는 것

- `V2 필수` 61 의 **충족 여부** — C-1 추적표(`acceptance-trace.md`)와 완료 조건 3.
- legacy 와 V2 의 **출력값 차이**(differential) — slice 별 `differential.json` 과 `data-extract.md` 의 판정 어휘(`legacy-defect / v2-defect / intentional-redesign / insufficient-evidence`).
- 폐기 6 의 부재를 잠그는 게이트 — 없다(§1 말미).
