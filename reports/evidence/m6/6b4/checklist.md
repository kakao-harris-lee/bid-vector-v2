# M6/6B-4 완료 점검 — ML training job 영속 · 대기 큐 상한

정본 계약 `scope.md`(결정 ID `D-6B4-N`). 판정 SHA(산출물 마지막) `cf4902bf`. 실행 결과는
`commands.md`, 되돌림은 `rollback.md`.

## 산출물 Q-1 ~ Q-7 과 그 술어

| ID | 무엇 | 공허함을 막는 술어의 실측 |
| --- | --- | --- |
| Q-1 | 계약 개정 — 전이표에 `ACCEPTED → FAILED` 하나 + `DetailCode` 셋 | proto·ADR 의 표 블록이 Python `_TRANSITIONS` **렌더링 바이트와 등식**이고(파서가 문면을 읽는 대신 생성물과 대조 — 화살표 철자·들여쓰기·표 모양에 의존하지 않는다), 두 파일 **전체**에서 블록 밖의 전이 주장(연결 글자 + 상태 이름 둘)을 금지한다. Kotlin 은 `//` 주석 줄을 지운 뒤 파싱 + 언급 수 등식 + 술어 본문 고정, 그리고 **Kotlin test 가 ADR 블록을 직접 읽어** 허용표·5x5 진리표를 대조한다(기대값 출처가 Python 표 — 새 데이터 파일 0). 사유 축은 런타임 닫힌 집합과 문면의 양방향 등식. 변이 전건 RED: 간선 제거 넷 · 다른 모양 추가(P1·P1b·P2·P2b·P3) · 글리프·배치(PR1~PR4·AD1~AD4·AD6) · Kotlin 주석 행·산술·`forNumber`(K1~K5) |
| Q-2 | 명시 대기 큐 — `START` 를 워커가 집는 순간으로 | **wire handle 은 언제나 `JOB_STATE_ACCEPTED`** 다(proto 문면 「즉시 ACCEPTED」 — 저장소 읽기를 대기열 투입 **앞**에 두어 결정적이고, 워커가 그 사이 집어 `RUNNING` 을 커밋해도 응답은 ACCEPTED 임을 동기 START runner 로 재며 통제군으로 워커가 실제로 집었음을 함께 단언) · 대기 5번째 제출이 최고참을 끝냄 · `RUNNING`·종단 비폐기 · 동률 `job_id` · 멱등 재사용 무폐기 · **래치 12 스레드 동시 제출에서 대기 정확히 4** · 폐기된 대기 job 을 워커가 뒤늦게 집어도 계산 0회 · **폐기 시점 WARNING 로그**(job_id·accepted_at·사유)와 통제군. 변이 M-3·M-10 RED |
| Q-3 | 상한 정책 값 | **값 축**: 출하 값이 승인 값(잠정)임 + 그 값이 실제 폐기 시점을 정함. **운반 축**: 조립 근(`_build_servicers`)으로 띄운 서버에 gRPC 로 상한+1 개를 대기시켜(워커 하나를 붙잡아) 최고참이 `PENDING_QUEUE_OVERFLOW` 가 되고 RUNNING 은 폐기되지 않으며 살아남은 대기 수가 정확히 상한임을 단언. 로더는 전수 fail-closed 이고 0·음수·키 누락을 거부. 변이 M-1(4→3)·M-2(4→5)·**V8(조립 근 상수화)** 전부 RED |
| Q-4 | 파일 저널 | 쓰기는 저장소 잠금 **안**이고 **저널 성공 뒤에만 메모리 변경**(쓰기 실패 주입에서 상태 불변·키 미선점 실측) · 원자 교체(크래시를 `fsync`·`os.replace` 두 자리에 주입해 이전 스냅숏이 온전히 읽힘) · canonical JSON 바이트 안정 · 「없음」과 「깨짐」이 다른 타입 · 판독이 2C 조합·시각 불변식을 다시 센다(파생값 사본은 **권위가 아니라** 편의값이라 어긋나면 파생값이 이기고 경고만 남긴다 — 사상을 고쳐도 구판 저널이 판독 불가가 되지 않는다) · **자기가 못 읽는 바이트를 쓰지 않는다**(tz 없는 시각은 쓰기에서 결과 타입으로 거부 + 그 바이트가 판독 불가임을 음성 대조) · **`os.replace` 가 경계**(앞에서 끊기면 `JournalFailed`, 뒤에서 끊기면 `JournalWritten(durable=False)` 이고 저장소는 메모리를 커밋해 디스크와 갈라지지 않는다 — EIO 주입 실측). 변이 M-7·M-8·M-9 RED |
| Q-5 | 재기동 처분 | RUNNING 고아 → `FAILED(RUNNER_LOST_ON_RESTART)`(재개 아님) · ACCEPTED 고아 → `accepted_at` 순 재적재 + **상한 재적용**(저널이 상한보다 많이 들고 있을 때 최고참부터 폐기) · 모르는 spec 버전은 처분 · **수렴 고정점**(재재기동 처분 0) · 조립 근에서 실제로 집혀 종단까지 감 · 깨진 저널은 기동 거부 |
| Q-6 | compose 볼륨 · Dockerfile | `container` job S-21~S-25 결과는 `commands.md` · 컨테이너 재생성 뒤 저널 생존 실측 |
| Q-7 | runbook · checklist · OPEN 처분 | `docs/runbook/m6-6b4-ml-job-journal.md`(저널 자리·메모리 커밋 시점·상한·폐기 관측 채널 전부·처분표 두 단계·기동 거부 여덟·알려진 제한 아홉) · 이 파일 · 아래 OPEN 절 |

## 위협 모델 ①~⑥ 대 실측

| 방어 | 무엇이 그것을 막는가(구조) |
| --- | --- |
| ① 동시 존재 대기 ≤ 상한 | 대기 수 = 저장소의 `ACCEPTED` 개수(카운터 캐시 없음). 판정·적재·폐기·저널 쓰기가 한 잠금. 래치 실측 |
| ② 폐기되는 것은 최고참 하나이고 사유가 상태로 남는다 | `accepted_at`(동률 `job_id`) 최소 하나만 끝내고 `FAILED(WORKER_RESOURCE_EXHAUSTED, PENDING_QUEUE_OVERFLOW, retryable=true)` 로 조회된다. `started_at` 은 미설정으로 남아 「실행했다」고 말하지 않는다. 폐기 **시점**에 로그도 남는다(상태 말고 알 길이 그것뿐이다) |
| ③ RUNNING·종단 비폐기 | 폐기 후보가 `ACCEPTED` 뿐 + `transition()` 이 **`WORKER_RESOURCE_EXHAUSTED` 를 ACCEPTED 출발에서만** 허용(그 쌍이 전이표에 있어도 거부, 닫힌 거부 사유로 구분) |
| ④ 재기동 뒤 기록 보존 + 정해진 처분 | 저널 왕복 등가 + 처분표 + 수렴 고정점 |
| ⑤ 크래시가 판독 불가 저널을 남기지 않음 · 남으면 기동 실패 | 원자 교체(두 자리 크래시 주입) + 「없음」/「깨짐」 분리 + 조립 근이 `ConfigError` 로 거부 + **정상 쓰기 경로도 판독 불가 바이트를 만들지 않는다**(tz 없는 시각을 쓰기에서 거부 — 앞 판은 판독 쪽에만 검사가 있어 이 방어를 정상 경로가 깼다) |
| ⑥ 네 자리 전이표가 같은 데이터 | 기계 추출 교차 등식 게이트(**닫힌 세계** — 파서가 모르는 문면은 조용히 비지 않고 RED) + Kotlin 5x5 전 쌍 진리표 |

## (2b) 새 public 표면 — 밖에 무엇을 허락하는가

| 표면 | 밖에 허락하는 것 | 무엇이 좁히는가(실측) |
| --- | --- | --- |
| `JobJournal(디렉터리)` | 임의 디렉터리에 저널 쓰기 | **조립 근 한 자리에서만 생성**(심볼 해석 — 별칭·속성 사슬·`from` import 가 같은 답) + **닫힌 import 집합**(grimp 그래프 — 상대 import·모듈 앨리어스를 정규화한다, vr H-1) + **이름 바인딩 집합**(재수출 한 홉 경유 획득, cr L-1) + 경로는 **`ServerConfig` 필드에 뿌리를 둔 식**이어야 하고 나머지 이름은 모듈 수준 상수이면서 **상대 경로 한 조각**이어야 한다. 음성 대조: 상대 import·모듈 앨리어스·재수출 경유를 전부 본다 |
| `JobJournal` 의 **공개 표면** | 저널 경로 객체를 얻어 잠금 밖에서 쓰기 | `path` property 를 **없앴다**. 공개 이름 집합 등식 `{read, write}` — 새 접근자가 생기면 이름과 무관하게 RED |
| 저널 **파일 모듈**(`training/jobs/journal_file`) | 파일계 호출을 아무 모듈에서나 | **닫힌 import 집합** — 이 모듈을 import 할 수 있는 모듈이 둘(저장소·조립 근)뿐이다. 이름 하나를 더하려면 게이트를 고쳐야 한다(`pyproject.toml` import-linter 계약이 in_scope 밖이라 같은 층을 게이트 test 로 세웠다) |
| `JobStore(journal=…, pending_queue_max=…)` | 저널 없는 저장소 · 상한 없는 저장소 | `journal` 이 **필수 키워드 인자**(비영속 갈래가 타입에 없다) · 상한 ≥1 생성자 거부 · **생성 자리는 `load_job_store` 하나**(심볼 해석) · 시각은 입구에서 tz 필수 |
| 패키지 뿌리 재수출 | `from ml_engine.training.jobs import JobJournal` 이라는 **둘째 획득 경로** | 생성 가능한 둘(`JobJournal`·`JobStore`)을 `__all__`·재수출에서 **뺐다**. 그 사실을 집합 단언으로 잠근다 |
| `load_job_store(...) → LoadedJobStore \| JournalFailed` | 복원 실패를 조용히 넘기기 | 결과 타입이 닫혀 있고 조립 근이 `JournalFailed` 에서 기동을 거부(출하 정책 위에서 실측) |
| `JobStore.pending_jobs()` | 대기 순서 조작 | 저장소 내부 상태에서 파생 — 외부 주입 자리 없음 |
| `JobSubmission`(저널 항목) | job 실행 입력을 밖에서 바꾸기 | 저장소가 적재 시점에 받아 저널에만 적는다. wire 메시지 변경 0 |
| 정책 키 `pending_queue_max` | 상한 값 | 로더 전수 fail-closed + ≥1 불변식 + 승인 표 등재 + **조립 근 운반 test** |
| `DetailCode` 셋 · `TransitionRejectionReason` | 조회 시 사유 어휘 | 닫힌 StrEnum(자유 문자열 0) |
| `JobFailure.retryable` | 자리마다 다른 값 | **필드가 아니다** — `code` 의 함수(파생 property). 넣을 인자가 없어 구조적으로 갈라질 수 없고, 전수 사상 test + 저널 사본 일치 검사가 잠근다 |
| Kotlin `allowedJobStateTransitions` 간선 | 소비자 계약 기대값 | 데이터 + 교차 등식 게이트 + Kotlin 5x5 전 쌍 진리표(술어 ↔ 데이터) |
| **저널 파일에 쓰는 자리** | 저장소 잠금 밖의 둘째 writer | **실행 시점 감사 훅**(`sys.addaudithook`, 세션 내내)이 닫는다 — 저널 디렉터리를 겨눈 쓰기 이벤트의 호출 프레임이 production 모듈이면서 `journal_file` 이 아니면 위반이다. 철자에 의존하지 않는다(`open` 하나가 builtin·`io.open`·`Path.open`·`os.open` 을, `os.rename` 하나가 `os.replace`·`Path.replace`·`shutil.move` 를 덮는다). 정적 이름 집합은 **좁힘**으로만 남기고 「닫음」이라 부르지 않는다(vr H-2 · cr M-1 — 그 목록은 `open(…, "w")` 를 비롯한 여섯을 통과시켰다) |
| `JobFailure` 의 **사유 조합** | 계약 문면보다 넓은 쓰임(넷째 사유) | `ACCEPTED` 출발 `FAIL` 의 (code, detail) 쌍을 **런타임 닫힌 집합**(`state._PENDING_FAILURE_REASONS`, 셋)이 거부한다 + 그 집합과 proto·ADR 문면의 `DetailCode` 등식(양방향). 정적 AST 로는 보이지 않는 자리(`runner._transition_failed` 는 코드·사유를 인자로 받는다)까지 덮는다 |

「경계로 처리」 행의 **실측 목록**: 조립 근 밖의 `JobJournal`·`JobStore` 생성 **0** · 저널 파일
모듈을 import 하는 모듈 **2**(grimp) · 이름을 바인딩하는 모듈 `JobJournal` **3** / `JobStore`
**4** · 조립 근으로 띄운 서버의 제출·폐기·취소·재기동 경로에서 저널을 쓴 모듈 **1**(감사 훅,
허용 ≥ 5건).

**경계 밖 — 실측 목록**(vr L-5. 「별칭으로 우회되지 않는다」 같은 과대 주장을 걷어낸다):

| 축 | 닫혔나 | 무엇이 남는가 |
| --- | --- | --- |
| 상대 import·모듈 앨리어스 | **닫힘** | grimp 가 절대 이름으로 정규화한다 |
| 재수출 한 홉 경유 획득 | **부분 — `from …store import JobJournal` 은 닫힘, 모듈 속성은 OPEN** | `modules_binding` 은 `ImportFrom` 의 이름만 본다. `import …jobs.store as m` 뒤 `m.JobJournal(...)` + `# type: ignore[attr-defined]` 는 **초록**이다(vr M-3 A7b 실측: ruff·mypy·lint-imports·게이트 전부 통과). 생성 자리 해석을 런타임 객체 동일성으로 정규화하면 닫힌다 → `OPEN-6B4-ASSEMBLY-BOUNDARY-HARDENING` |
| 지역 재바인딩(`factory = JobStore`) | **열림** | 생성 자리 술어는 값의 흐름을 따라가지 않는다 |
| `type(store)(...)` | **열림** | 이름이 등장하지 않는다 |
| private 속성(`store._journal`) | **열림** | 위 둘과 묶여야 쓸모가 있고 production 에 그 자리는 없다 |
| 동적 접근(`importlib`·`getattr`) | **열림** | AST·grimp 는 import 문과 호출식을 읽는다 |
| 저널 파일 쓰기(철자 무관) | **대체로 닫힘 — 실행된 경로에 한정, 다섯 갈래 OPEN** | 감사 훅이 흔한 쓰기 열한 갈래를 잡는다(F1~F4 · F7 · H1~H5 · H8). **실행되지 않는 코드 경로**는 못 보고, 실행되는데도 놓치는 다섯이 실측됐다 — dir_fd + 상대 이름 · `chdir` 뒤 상대 경로 · `exec` 로 만든 프레임(미귀속) · `os.system` · `os.chmod`(둘은 감사 이벤트 집합 밖) → `OPEN-6B4-AUDIT-HOOK-HARDENING` |
| 감사 훅의 경로 술어 | **OPEN** | 인자 문자열의 **부분 문자열**이라 ① 상대 경로를 놓치고 ② `*_journal` 로 끝나는 무관한 디렉터리를 잡는다(오탐). `abspath` + 경로 **성분** 비교로 둘이 함께 닫힌다(cr L-2) → 같은 OPEN |
| 감사 훅의 subprocess 분기 | **OPEN(거짓 양성)** | `record()` 가 subprocess 를 writer 판정 **앞에서** 무조건 위반으로 적는다 — 앞으로 저널 경로를 자식에게 넘기는 **test** 를 쓰면 세션 전역 게이트가 붉어진다(`tests/` 예외를 그 분기 앞으로 옮기면 닫힌다, cr L-1) → 같은 OPEN |
| 감사 판정의 **실행 범위** | **OPEN** | 판정은 세션 **맨 뒤 test** 다. 그 뒤에 생기는 위반(session fixture teardown · 남은 워커 스레드 · `atexit` 쓰기)은 판정되지 않고, `-x` 로 앞에서 멈추거나 `-k`·부분 경로(`pytest tests/<dir>`)로 그 test 가 수집되지 않으면 **판정 자체가 없다**(실측 exit 0). acceptance 는 `pytest tests` 전건이라 영향 0 → 같은 OPEN |
| 블록 밖 전이 주장 금지 | **좁힘(닫음 아님)** | 연결 글자 15개 열거 + 대문자 상태 이름이라 `⇢`·`↦`·`➔`·`—>` · 소문자 · **연결 글자 없는 산문** · 마크다운 표를 놓친다(vr M-2 · cr L-3 실측). 권위 있는 표는 바이트 등식으로 닫혀 **기계 산출물에는 영향이 없고**, 남는 위험은 사람이 읽는 거짓 문장 하나다 → `OPEN-6B4-TRANSITION-PROSE-GATE` |
| 다른 프로세스의 쓰기(`subprocess`) | **열림** | 훅은 자식 생성(argv 에 저널 경로)을 **의도**로 적지만, 자식 안에서 경로를 조립하면 보이지 않는다 |
| 저널 파일을 직접 편집하는 주체 | 비방어 | 위협 모델이 처음부터 제외한다 |

## 설계 검토 우회 ①~⑨ 대응표

| # | 우회 | 무엇이 막는가 |
| --- | --- | --- |
| 1 | 동시 제출 둘이 각자 「대기 4」를 보고 둘 다 적재 | 판정·적재·폐기·저널이 한 잠금 — 래치 12 스레드에서 대기 정확히 4, 폐기 8 |
| 2 | 멱등 재사용이 새 job 으로 세어져 엉뚱한 폐기 | 재사용 경로는 적재가 없고 저널도 쓰지 않는다 — 재사용 5회 뒤 대기 둘 불변 |
| 3 | 워커가 집기 직전의 job 을 폐기 | 집기(START)와 폐기가 같은 잠금 + 폐기 사유를 ACCEPTED 출발에서만 허용(구조). RUNNING 에 같은 사유를 쓰려는 전이는 닫힌 사유로 거부 |
| 4 | 취소와 폐기 경합 | 같은 잠금 · 종단은 폐기 후보 아님 · 대기 중 취소 뒤 워커가 집어도 계산 0회 |
| 5 | 상한 0·음수·누락 | 로더 fail-closed + 저장소 생성자 거부 |
| 6 | 저널 부분 쓰기 → 다음 기동이 빈 저장소 | 원자 교체 + 「없음」/「깨짐」 분리 + 기동 거부 |
| 7 | 재기동 시 ACCEPTED 고아가 상한보다 많음(이전 버전 저널) | 재적재에도 상한 적용(최고참부터 폐기, 계수 실측) + 모르는 spec 버전 처분 |
| 8 | RUNNING 고아 재실행(중복 학습·산출물 덮어쓰기) | 재개 없음 — 처분만. 산출물 디렉터리 「존재하면 거부」 관례 무변경 |
| 9 | 영속이 `apply_transition` 밖에서 일어나 H-2 재발 | 저널 쓰기가 잠금 안 + **쓰기 성공 뒤에만 커밋**(실패 주입으로 상태·저널 일치 실측) + **실행 시점 감사 훅**(저널을 겨눈 쓰기의 호출 프레임 — **닫음을 주장하는 것은 이것뿐**이고 실행된 경로에 한정이다). 정적 자리 집합(심볼 해석)·`path` 비노출·저널 파일 모듈의 닫힌 import 집합은 **좁힘**이다(vr L-2: 앞 문면은 정적 집합 등식을 막는 장치로 들었다) |

## 계약과 어긋나 판단이 필요했던 자리

1. **저장소를 쓸 수 없을 때의 실패 코드가 2A 어휘에 없다.** 여덟 값 중 뜻이 가장 가까운
   `MODEL_NOT_READY`(retryable) + `detail_code=JOB_STORE_UNAVAILABLE` 로 답한다. proto enum
   변경은 out_scope 라 추가하지 않았다 → `OPEN-6B4-STORE-UNAVAILABLE-CODE`.
2. **`StartTraining` 응답 상태는 **새 job 경로에서** `ACCEPTED` 로 못 박았다**(한정은 판단
   13). proto `StartTrainingResponse` 주석이 「즉시 `ACCEPTED` 를 돌려준다」이고, 저장소를
   **대기열 투입 앞에서** 읽으면 그 문면이 새 job 경로에서 참이 된다(`start_or_reuse` 가 한 잠금 안에서 적재·폐기·저널을 끝냈으므로 그 직후
   상태는 결정적이다). R2-1 이 막던 「다른 writer 가 먼저 종단으로 옮겼다」는 그 읽기가 그대로
   덮는다 — 빠지는 값은 「워커가 이미 집었는가」 하나이고 그것이 문면이 금지하는 값이다.
   문면 개정(선택지 b)이 아니라 순서 되돌림(선택지 a)을 골랐다(운영자 결정).
3. **재기동 뒤 대기 고아를 실행하려면 제출 입력이 필요하다.** `JobRecord` 는 `dataset_id`
   만 들고 uri·checksum·spec 버전을 모른다. 저널 항목에 `JobSubmission` 으로 함께 적었다 —
   내부 영속 형식이고 wire 메시지 변경 0(D-6B4-2 ③).
4. **`InMemoryJobStore` → `JobStore` 이름 변경.** 저널을 필수 인자로 받는 타입에 그 이름은
   거짓이다. `journal=None` 갈래를 두지 않아 「조용히 비영속으로 조립」 경로가 구성상 없다.
5. **`retryable` 은 자리마다 고르는 값이 아니라 코드의 함수로 정했다.** `training.proto` 가
   job failure 의 `retryable` 을 「계약이 정하는 값 … application failure 와 **대칭**」이라 하고
   ADR 0010 이 application 층에서 코드별 사상을 쓰므로, 그 대칭대로 `JobFailureCode` 하나가 값
   하나를 든다(용량 고갈만 참). 필드를 지우고 파생 property 로 바꿔 **넣을 인자를 없앴다**.
   부수 결정 둘: 재기동 처분(`RUNNER_LOST_ON_RESTART`)이 `TRAINING_ERROR` 를 쓰므로
   `retryable=false` 가 된다(새 제출이면 돌 수 있으나 그것은 **새 job** 이다 — 전용 코드 선택은
   `OPEN-6B4-STORE-UNAVAILABLE-CODE` 와 같은 축으로 남긴다). 저널의 `retryable` 은 파생값의
   **사본**이라 형식을 그대로 둔다 — 사본이 사상과 어긋날 때의 처분은 **판단 11**(파생값 승리 +
   경고)이다(vr L-1: 앞 문면은 여기서 「`INVARIANT_VIOLATED` 로 거부한다」고 적어 판단 11 과
   모순됐다 — 그 거부는 cr M-2 로 철회됐다).

6. **`ACCEPTED → FAILED` 간선의 사유 목록을 문면 쪽에 맞춰 넓혔다.** 문면 둘(ADR·proto 주석)은
   `WORKER_RESOURCE_EXHAUSTED` 하나만 말했는데 구현은 같은 간선을 셋에 쓴다(상한 폐기 · 재기동
   때 모르는 spec · 워커가 집는 도중 START 커밋 전 크래시). 운영자 결정: **문면을 구현에
   맞춘다**(구현을 좁히지 않는다). 세 번째 사유는 문면만 늘리지 않고 크래시 주입으로 잠갔다.

7. **저널 모듈을 둘로 나눴다 — 설계 래칫이 요구한 분리.** 교체 뒤 내구성 수정으로
   `journal.py` 가 515줄이 되어 500 한도를 넘겼다. allowlist 등재(게이트 느슨하게)가 아니라
   **파일 접근**을 `journal_file.py` 로 떼어냈다 — 값·코덱 모듈은 파일을 모르고, 저널 파일에
   쓰는 자리가 한 모듈에 갇힌다(둘째 writer 축의 구조적 근거이기도 하다).

8. **Kotlin 5x5 진리표를 새 파일이 아니라 기존 test 안에 뒀다 — in_scope 때문이다.** 처음에는
   `TrainingTransitionTableTest.kt` 로 분리했으나(`:adapters:sizeGate` 500 한도), 새 test 클래스는
   `GateRegistrationGateTask` 가 `config/quality/gate-tests.properties` 등재를 요구하고 그 파일은
   이 slice in_scope 밖이다. 계약을 스스로 넓히지 않고, 진리표가 열거 넷을 **흡수**하도록 바꿔
   줄 수를 517 → 495 로 **줄였다**(새 클래스·장부 편집 0). 흡수는 약화가 아니다 — 11/25 쌍
   열거가 25/25 데이터 대조가 됐다.

9. **`JobJournal.path` 를 지우면서 test 의 경로 획득 방식을 바꿨다.** production 표면에서
   경로를 내주지 않으므로 test 는 모듈 private 이름으로 같은 자리를 계산한다. 「저널이 산출물
   루트 안에 있다」는 단언은 상수 둘을 맞춰 보는 형태를 버리고 **쓰고 나서 디스크를 보는**
   관측으로 바꿨다(그쪽이 거동을 본다).

10. **`config/quality/image-hygiene-policy.properties` 는 in_scope 에 있으나 무편집이다**
   (재실측 주석 자리). 이 라운드가 이미지 입력(proto 주석·`ml-engine/src`)을 바꿔 `container`
   job 을 전부 다시 돌렸고 위생 게이트도 통과했으나, 정책 파일의 값·주석을 바꿀 근거는 없다 —
   계약 문면 정리는 팀장 몫으로 남긴다(verifier r1 §4 지적).

11. **파생값 사본의 불일치는 파생값 승리 + 경고다(기동 거부가 아니다).** 저널의
   `retryable` 은 `code` 의 파생값 **사본**이고 권위는 `code` 다. 앞 판은 어긋나면
   `INVARIANT_VIOLATED` 로 거부했는데, 그러면 사상을 한 줄 고치는 순간 **그 전에 쓰인 모든
   저널이 판독 불가**가 되고(`schema_version` 은 그대로라 형식 불일치로도 보이지 않는다) 그
   처방은 「파일을 치우고 다시 기동」 = 기록 전부 상실이다. 되돌릴 수 있는 불일치를 되돌릴 수
   없는 자리로 보내지 않는다 — 판독 불변식은 조합·시각처럼 **권위 있는 데이터**에만 세운다
   (cr M-2). runbook §4 에 「손으로 고친 파일」과 **다른 행**으로 적었다.

12. **「쓰이는 자리는 정확히 셋」을 런타임에 닫았다.** 문면이 「정확히 셋」이라 선언하는데
   앞 판은 셋 각각을 **양성**으로만 확인했고 넷째를 막는 술어가 0 이었다. 사유 집합을
   `state._PENDING_FAILURE_REASONS` 로 선언하고 `transition()` 이 그 밖을 거부한다 — AST 로
   생산 자리를 세는 대신 런타임으로 간 이유는 `runner._transition_failed` 가 코드·사유를
   **인자로 받기** 때문이다(정적으로 보이지 않는다). 문면과의 등식은 양방향이다(cr M-3).

13. **`StartTraining` 의 「즉시 `ACCEPTED`」는 새 job 경로의 보증이다.** 멱등 재사용은 적재가
   아니므로 기존 job 의 **현재 상태**를 싣는다(`RUNNING` 일 수 있다). 거동은 선재이고 Kotlin
   fake servicer 의 재사용 갈래와 같다 — 고친 것은 **문면**이고(proto 주석만), 재사용 handle
   상태를 잠그는 test 를 더했다(cr M-4). 판단 2 의 「문면이 참이 된다」는 **새 job 경로에서**
   참이다.

14. **둘째 writer 축의 「닫음」은 정적 술어가 아니라 실행 시점 감사 훅이 진다.** 이름 집합은
   스물 넘는 이름을 들었어도 `open(…, "w")` 를 비롯한 여섯을 통과시켰다 — 파이썬에서 파일을
   쓰는 가장 흔한 형태가 목록 밖에 남는 것이 이 축의 성질이다(이름을 더하면 다음 이름이
   남는다). `sys.addaudithook` 은 철자와 무관하게 이벤트를 내므로 그 성질이 사라진다. 대가는
   **실행된 경로만 본다**는 것이고(세션 비용은 `tests/app` 에서 약 25% 증가 — 330초 → 408초),
   그 한계를 위 경계 표에 적었다. 정적 술어는 「좁힘」으로 남긴다(vr H-2 · cr M-1).

15. **「READY 가 되는 시점」은 이 프로세스 모델에 없다 — 운영자 결정 (A)(D-6B4-3): 그 시점은
   **다음 기동**이다.**
   `ReadinessGate` 의 전이는 기동 1회 판정 · `mark_not_ready` · `begin_shutdown` 셋이고
   `mark_ready()` 는 M5 가 **M-5 로 일부러 지웠다**(「정책은 재로드하지 않는다 — 재시작만
   preload 를 다시 돈다」). 재preload 루프·`mark_ready` 부활은 M5 가 닫은 경로를 다시 여는
   범위 밖 변경이라 하지 않았고, 대신 **그 사실을 test 로 잠갔다**(제한 2). 코드 거동 변경 0.
   「그동안도 상한 계수에 든다」는 이미 참이다(대기 수는 readiness 와 무관한 상태 계수).

19. **취소 표지는 제출 시점에 등록한다 — 종료 경로의 회귀였다**(code-review A).
   `27f9f081`(M-3)이 등록을 `submit()` 에서 워커 스레드의 `_execute` 로 옮긴 뒤, `cancel_all()`
   과 `shutdown(wait=False)` **사이에** 워커가 집는 job 은 새 표지를 받아 취소 신호를 잃고
   파이프라인을 끝까지 돌렸다 — 그 스레드가 프로세스 종료를 붙잡는다. 그 표지를 호출 스레드에서
   만들어 등록하고 `_execute` 가 **재사용**한다(stopping 플래그를 더하지 않고 같은 창을
   닫는다). 「START 전이보다 먼저 등록」이라는 원래 보증은 더 강해졌다 — 제출 시점부터다.

20. **종단 전이 시각의 조정을 `apply_transition` 한 자리로 모았다**(code-review B).
   `server._resubmit` 이 `at=datetime.now(UTC)` 를 조정 없이 넘겨, 기동 시계가 저널의
   `accepted_at` 보다 앞서면(컨테이너 시계 보정·NTP 역행) `JobRecord` 의 시각 불변식이
   `ValueError` 를 내고 **매 기동이 실패**했다(저널이 남으므로 자기 치유가 없다). `_failed` 는
   자기 자리에서 이미 조정했지만 **이 메서드를 직접 부르는** 호출자는 그 조정을 지나지 않았다 —
   호출자마다 반복하는 대신 한 자리로 모은다. `START` 는 `started_at` 축이라 대상이 아니다
   (`START` 도 같은 조정을 지난다 — 제한 21 의 근거가 틀렸음이 실측됐다).

21. **`START` 전이 시각도 조정한다 — `started_at = max(at, accepted_at)`**(vr r3d M-4).
   앞 문면은 「조정하지 않는다 … 저널의 미래 시각과 만나는 경로는 재기동 처분이라 20 의 조정이
   덮는다」였고 **그 근거가 틀렸다**: **아는 spec** 의 대기 job 이 미래 `accepted_at` 으로 남아
   있으면 **재적재 경로가 그 시각과 만난다**. 그때 `START` 가 `ValueError` 를 내고 `_on_done`
   이 크래시로 받아 job 이 `FAILED(TRAINING_ERROR, RUNNER_CRASHED)` 로 끝났다 — 기동 실패는
   아니지만(20 이 막는다) **실행되지 않았는데 「러너가 크래시했다」는 오진**이고, 시계 역행
   기동에서 대기 고아(≤ 상한)가 그렇게 끝난다. 같은 일이 프로세스 안에서도 난다(제출 뒤 워커가
   집기 전 NTP 역행).

   운영자 결정은 조정이다. 치르는 값: 「언제 집었는가」가 역행 폭만큼 앞당겨진다
   (`started_at` 이 `accepted_at` 과 같아질 수 있다). 사는 값: job 이 **실제로 실행되고**
   끝난 사유가 사실이 된다. `started_at = accepted_at` 은 크래시보다 덜 거짓이다.

22. **새 job 이 자기 폐기될 수 있다**(code-review D). 폐기 대상은 `accepted_at`(동률 `job_id`)
   최소인 대기 job 전부이고 방금 적재한 job 도 그 후보다 — 동률에서 `job_id` 가 작거나 시계가
   역행하면 응답이 `FAILED(WORKER_RESOURCE_EXHAUSTED, PENDING_QUEUE_OVERFLOW)` 다. 거동은
   그대로 두고(응답이 거짓말하지 않는 쪽) proto 문면에 **예외 하나**로 적었다. 소비자는
   `started_at` **미설정**으로 「실행된 적 없다」를 안다.

## 알려진 제한

1. **볼륨이 살아 있는 동안의 영속만 든다.** 볼륨 자체의 백업·손상 복구는 M7
   (`docker compose down -v` 는 저널과 산출물을 함께 지운다 — CI S-25 가 그렇게 한다).
2. **NOT_READY 로 뜬 서버는 대기 job 을 집지 않는다 — 다음 기동이 집는다.** 「영구 정체」가
   아닌 근거는 **NOT_READY 에서 READY 로 가는 프로세스 내 경로가 없다**는 것이고, 그 사실을
   `tests/serving/test_readiness.py::test_not_ready_is_terminal_within_the_process` 가
   **공개 전이 집합 등식 + 인자 없는 공개 메서드 전수 호출**로 잠근다(D-6B4-3). 누가 그
   경로를 만들면 거기서 먼저 붉어지고, 그때 「그 시점에 재적재」가 필요해진다. 대기 job 은
   집히지 않는 동안에도 **상한 계수에 든다**(대기 수는 readiness 와 무관한 상태 계수).
3. **종단 job 이 영구 누적된다.** 전체 교체 형식이라 파일이 단조 증가 →
   `OPEN-6B4-JOURNAL-RETENTION`.
4. **재기동 등가는 in-process 다.** 새 저장소 인스턴스가 같은 저널을 읽는 형태로 쟀다
   (6D-2 가 Kotlin 에서 쓴 「in-JVM 등가」와 같은 한계 — 프로세스를 실제로 죽이지 않는다).
   컨테이너 축의 실측은 `container` job 자리에서 별도로 한다.
5. **다중 인스턴스 공유는 범위 밖**(5E 제한 6 유지) — 같은 볼륨에 둘을 올리면 서로의 쓰기를
   덮는다. 덮는 것만이 아니다: 저널의 **tmp 이름이 고정**(`jobs.json.tmp`)이라 같은 디렉터리의
   두 writer 는 **서로의 원자 교체를 깨뜨린다** — 한쪽의 `os.replace` 가 다른 쪽 tmp 를 가져가
   `ENOENT` 가 되고, 기동 경로라면 `ConfigError` 로 거부된다(vr r3b H-1 이 test 경합으로 실측했다 —
   같은 프로세스의 두 저장소에서도 같은 일이 난다). 한 프로세스에 저장소가 하나라는 전제가
   이 고정 이름을 받치고 있다.
6. **저널을 직접 편집하는 주체는 비방어.** 판독이 불변식을 다시 세지만 편집을 막지는 않는다.
7. **폐기·처분 metric 0.** 관측은 job 상태와 서버 로그 둘이고, job_id 를 쥔 쪽만 상태를 묻는다
   → `OPEN-6B4-QUEUE-METRIC`.
8. **출하 소비자 0.** `StartTraining` 을 부르는 production Kotlin 코드가 없다 — 이 slice 가
   닫는 것은 계약 안의 운영 안전성이다.
9. **`RUNNING` 종단 전이의 저널 쓰기가 실패하면 그 job 은 메모리에서 `RUNNING` 으로 남는다**
   (상태·저널 일치를 지킨 결과다). 그 사실을 오류 로그로 남기고, **다음 기동이 고아로
   처분한다** — 영원히 `RUNNING` 에 머무는 경로는 아니다. 같은 모양으로 **`START` 전이의 쓰기가
   실패한 job** 은 그 프로세스 안에서 다시 집히지 않고 `ACCEPTED` 로 남아 상한 계수를 먹는다
   (로그 있음) — 다른 제출의 상한 폐기나 다음 기동의 재적재가 치운다(runbook §4 에 등재).

10. **상한이 닫는 것은 「대기 상태의 job 수」이고 실행기 작업 큐 항목 수가 아니다.** 폐기된
   job 의 항목(파이프라인 객체 포함)은 워커가 집을 때까지 `ThreadPoolExecutor` 작업 큐에
   남는다 — 실측: 워커 하나를 묶고 200건 제출 시 저장소 `ACCEPTED` 4 · 작업 큐 199 · 저널
   106 KB. 집힌 뒤에는 대기가 아니므로 파이프라인을 **돌리지 않는다**(계산 자원은 안전하다).
   메모리 상한에는 큐 자체의 상한이 따로 필요하고 그것은 이 slice 밖이다 — 수령 OPEN 의 문면을
   이 사실에 맞춰 고쳤다(아래 OPEN 절).

11. **재적재 경로는 제출 검증 중 spec 버전만 다시 센다.** `dataset_uri_schemes`·manifest
   checksum 형식은 재기동 시 현재 정책으로 다시 보지 않는다(정책이 좁아지면 대기 고아 ≤ 상한
   건이 옛 정책으로 실행된다). 출하 정책이 `file` 하나라 오늘 영향 0.

12. **`retryable` 의 코드별 사상은 재기동 처분을 `false` 로 만든다.** `RUNNER_LOST_ON_RESTART`
   는 같은 입력을 새 job 으로 다시 넣으면 돌 수 있으나 `TRAINING_ERROR` 의 사상이 `false` 다 —
   2C ⑥ 「재시도 가치가 있는가」와 어긋난다. 전용 코드를 고르는 문제로 남긴다
   (`OPEN-6B4-STORE-UNAVAILABLE-CODE` 와 같은 축). **사상 표 자체는 Python 코드에만 있다**
   (vr L-3): proto 는 「계약이 정하는 값 … application failure 와 대칭」이라고만 하고 표가
   없으며 Kotlin 규칙도 0 이다. 문면으로 올릴지는 계약 심판 축의 결정이고 이 slice 는 하지
   않았다.

13. **`_fsync_directory` 는 leaf 만 `fsync` 한다**(cr L-6). 첫 기동의 `mkdir(parents=True)` 가
   `artifacts/` 와 `artifacts/_journal/` 둘을 만들면 **부모** 디렉터리 엔트리는 내구화되지
   않는다 — 첫 기동 + 전원 상실 한정이고, 그 경우 다음 기동은 「파일 없음」(첫 기동)으로
   처리한다.

14. **`JobStore.__init__` 는 `entries` 의 대기 수를 상한과 대조하지 않는다**(cr L-7). 상한
   적용은 `load_job_store` 에만 있다. 생성 자리가 한 곳으로 좁혀져 오늘 도달 불가지만, 그
   불변식이 타입·생성자 쪽에 없다는 사실은 적어 둔다(생성 자리 술어가 느슨해지면 함께 열린다).

15. **`_commit` 은 저장소 잠금을 쥔 채 `fsync` 둘과 `os.replace` 를 돈다**(cr L-8). 그동안
   `GetTrainingJob`(`store.get`)·`pending_jobs` 가 막힌다. `job_workers: 1` · 대기 상한 4 에서
   문제 되지 않지만 처리량 상한의 자리다.

16. **감사 훅의 경화 틈 — `OPEN-6B4-AUDIT-HOOK-HARDENING`.** 「실행된 경로에서는 철자 무관
   닫힘」이 다섯 갈래에서 성립하지 않는다(실행되는데도 위반 0): ① 디렉터리 fd + 상대 이름
   (`os.open("jobs.json", …, dir_fd=fd)`) ② `os.chdir(_journal)` 뒤 상대 경로 ③ `exec` 로 만든
   프레임(미귀속으로 분류) ④ `os.system` ⑤ `os.chmod`(뒤 둘은 감사 이벤트 집합 밖). 더해
   경로 술어가 부분 문자열이라 상대 경로를 놓치고 `*_journal` 디렉터리를 잡으며(cr L-2),
   subprocess 분기가 `tests/` 예외를 받지 않아 **거짓 양성**이 될 수 있고(cr L-1), 판정이 세션
   맨 뒤 test 라 그 뒤(teardown · 남은 워커 스레드 · `atexit`)와 부분 실행(`-x` · `-k` ·
   `pytest tests/<dir>`)에서는 판정이 없다. 오늘 production 에 이런 자리는 없다
   (`journal_file` 의 디렉터리 fd 는 `fsync` 용 `O_RDONLY` 다) — **일부러 그렇게 쓰지 않으면
   생기지 않는 형태**라 차단하지 않고 등재한다. 싼 처방은 verifier r3 §4 M-1 에 있다.

17. **전이표 「블록 밖 금지」는 좁힘이다 — `OPEN-6B4-TRANSITION-PROSE-GATE`.** 연결 글자 15개
   열거 + 대문자 상태 이름이라 `⇢`·`↦`·`➔`·`—>` · 소문자(`failed → running`) · **연결 글자
   없는 산문**(「FAILED 에서 RUNNING 으로 되돌리는 재시도도 허용」) · 마크다운 표
   (`| FAILED | RUNNING |`)가 초록이다. 게이트 docstring 의 「마크다운 표가 함께 닫힌다」는
   그만큼 과했고, **승인 전 일괄의 뒤 배치에서 좁혔다**(vr r3b L-9 — 앞 배치는 이 문면을
   좁혔다고 적었지만 그 치환이 실제로 적용되지 않아 parity 게이트 파일은 무편집이었다.
   커밋 `dfca2394` 의 제목이 「vr M-2」를 든 것도 그 때문이고, 그 커밋의 실제 편집은 조립
   경계 파일 하나뿐이다 — 이력은 되쓰지 않고 사실을 여기 적는다). **권위 있는 표는 블록 안 바이트 등식으로 닫혀
   있어 기계 산출물에는 영향이 없고**, 남는 위험은 사람이 읽는 거짓 문장 하나다. 유니코드
   화살표 문자 부류로 바꾸고 「상태 이름 둘(대소문자 무시)」로 넓히는 것이 처방이며 거짓
   양성 실측이 선행 조건이다.

18. **반례 문면과 그 test 가 사라졌다**(cr L-4, 등재). 앞 판 proto 주석은 「표에 없는 쌍(예:
   …)」을 들고 게이트가 그 쌍의 부재를 따로 단언했다(`parse_proto_forbidden_pairs`). 제한 17 의
   「블록 밖 금지」가 **그 문장을 쓸 수 없게 만들어** 문면과 test 가 함께 제거됐다. 그 검사가
   재던 것은 지금 `test_rendered_table_is_not_vacuous` 의 두 단언
   (`("ACCEPTED","SUCCEEDED")`·`("FAILED","RUNNING")` 부재)이 **권위 있는 표 쪽에서** 진다 —
   잃은 것은 소비자가 읽는 **문서의 반례**다. 되찾으려면 `transition-counterexamples-begin/end`
   블록을 하나 더 렌더링하고 허용 표와 서로소임을 단언하면 되고, 이 라운드는 술어를 바꾸지
   않기로 한 범위라 등재만 한다.

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조

착수 시점 이후 추가된 파일 **열넷** 전부가 in_scope 안이다 — r2 가 더한 둘은
`ml-engine/tests/journal_audit.py`(감사 훅의 술어·기록)와
`ml-engine/tests/gates/test_journal_write_audit.py` 이고 둘 다 `ml-engine/tests/**` 다. 그 밖은 —
`ml-engine/src/ml_engine/training/jobs/journal.py`·`journal_file.py`(`training/jobs/**`) ·
`ml-engine/tests/**` 다섯 · `docs/runbook/**` 하나 · `reports/evidence/m6/6b4/**` 넷.
in_scope 밖 신규 **0**. 기계 대조 — `git diff --name-status <base>..HEAD` 의 경로 전수를
in_scope 패턴에 넣어 매치 없는 줄을 출력하게 했고 출력이 비었다(`rollback.md` 에 같은 측정).

이 라운드가 **만들었다가 커밋 전에 지운** 파일 하나를 명시한다:
`adapters/src/test/kotlin/bidvector/adapters/contract/TrainingTransitionTableTest.kt` — 새 test
클래스가 `config/quality/gate-tests.properties`(in_scope 밖) 등재를 요구해서 접었고, 이력에
들어가지 않았다(`git log --all -- <그 경로>` 빈 출력). 위 「판단이 필요했던 자리」 8 참고.

## 계약 심판 안내 — ADR 편집 범위(vr L-7)

in_scope 는 `docs/adr/0010-*.md` 를 **「D-8 개정 블록」**으로 적는데, 이 slice 의 편집은 그 블록
밖 **원 D-8 bullet** 한 줄에도 닿았다. 그 bullet 이 전이 사슬(`ACCEPTED → RUNNING → …`)을 산문
으로 들고 있었고, 이 라운드가 세운 **「블록 밖 전이 주장 금지」** 술어가 그 문장을 허용하지
않는다 — 그래서 사슬을 렌더링 블록으로 옮기고 bullet 은 그 블록을 가리키게 고쳤다. **뜻은
보존된다**(취소가 두 비종료 상태에서 가능하다는 단서까지 유지). 되돌리면 그 술어가 자기 파일에서
붉어지므로 필요한 이동이고, 범위가 문면보다 넓다는 사실만 여기 적는다(Codex 계약 심판이 D-8 절
전체를 보도록).

같은 절의 다른 변경 둘도 같은 이유다 — 개정 블록의 `**`ACCEPTED → FAILED`**` 를 「`ACCEPTED`
에서 `FAILED` 로 가는 간선」으로, 「RUNNING 고아는 `RUNNING → FAILED`」를 「RUNNING 고아는
`FAILED` 로 처분한다(그 쪽 간선)」로. 전이 **데이터**는 렌더링 블록 여섯 줄이 유일한 자리다.

## OPEN

### 수령하고 닫는 것

| ID | 처분 |
| --- | --- |
| `OPEN-5E-JOB-PERSISTENCE` | **닫는 후보** — 파일 저널 + compose 볼륨으로 프로세스 재시작·컨테이너 재생성 둘 다 기록이 남고 고아는 정해진 처분을 받는다. 남는 것은 위 알려진 제한 1·3·5 |
| `OPEN-5E-JOB-QUEUE-BOUND` | **부분 종결 후보.** 원문은 「`JobRunner` 의 ThreadPoolExecutor **제출 큐**가 무한(`WORKER_RESOURCE_EXHAUSTED` 생산 경로 없음)」이다. 닫힌 것은 **상태 상한**(대기 = `ACCEPTED` 수 ≤ 정책 값)과 그 코드의 **생산 경로**다. 닫히지 **않은** 것은 실행기 작업 큐의 항목 수 — 폐기된 job 의 항목이 워커가 집을 때까지 남는다(실측 199, 알려진 제한 10). 큐 자체의 상한이 필요하면 별도 slice 다. 종결 판정은 이 구분을 보고 하는 것이 맞다 |

### 신설

| ID | 내용 | 소유 후보 |
| --- | --- | --- |
| `OPEN-6B4-CANCEL-REASON-UNREACHABLE` | `CANCELLED_BY_REQUEST` 가 구조적으로 생산 불가(2C 조합 불변식이 `CANCELLED` 에 실패 성분 설정을 금지) — 이 slice 가 고치지 않았다 | 계약 축(2C 개정 필요) |
| `OPEN-6B4-STORE-UNAVAILABLE-CODE` | 2A `FailureCode` 여덟에 「저장소를 쓸 수 없다」가 없다 — 지금은 `MODEL_NOT_READY` + `detail_code` 로 얹는다 | 계약 축 |
| `OPEN-6B4-JOURNAL-RETENTION` | 종단 job 보존 기간·정리 절차(전체 교체 형식의 단조 증가) | M7 운영 |
| `OPEN-6B4-QUEUE-METRIC` | 대기 깊이·폐기·처분 계수의 metric·경보 | M7 관측(6F-10 `OPEN-6F10-CLAIM-OBSERVABILITY` 와 같은 모양) |
| `OPEN-6B4-AUDIT-HOOK-HARDENING` | **감사 훅** 축 — 실행되는데도 놓치는 다섯(dir_fd 상대 이름 · `chdir` · `exec` 프레임 · `os.system` · `os.chmod`) · 부분 문자열 경로 술어(상대 경로 누락 · `*_journal` 오탐) · subprocess 분기의 `tests/` 거짓 양성 · 판정 범위(맨 뒤 test 이후 · 부분 실행) · 「누출 훅 금지」 술어가 호출식 철자와 `tests/` 범위에 걸림(계수 shim 이 처방) | 게이트 경화 축(다음 게이트형 slice) |
| `OPEN-6B4-ASSEMBLY-BOUNDARY-HARDENING` | **조립 경계** 축 — 재수출의 **모듈 속성** 경유 획득(`import …store as m` 뒤 `m.JobJournal(...)` + `type: ignore`)이 전 acceptance 초록이다(vr M-3). 생성 자리 해석을 런타임 객체 동일성으로 정규화하면 닫힌다. 같은 표의 「열림」 넷(지역 재바인딩 · `type()` · private 속성 · 동적 접근)과 같은 축이다 | 게이트 경화 축 |
| `OPEN-6B4-TRANSITION-PROSE-GATE` | 전이표 **블록 밖** 금지 술어가 연결 글자 열거 — 유니코드 화살표 부류 + 「상태 이름 둘(대소문자 무시)」로 넓히기(거짓 양성 실측 선행) · 반례 블록 복원(제한 18) | 계약 문면 축 |

### 입력이지만 수령은 아닌 것

`OPEN-OPS-03`(큐 깊이 SLO 임계 미결) — 상한 **값**의 유일한 승인 근거 자리다. 결정되면
`pending_queue_max` 를 재검토한다. `OPEN-5E-POLICY-VALUES`(잠정 승인)는 이 slice 가 키 하나를
더해 표가 늘었으므로 같은 잠정 지위를 그대로 잇는다.
