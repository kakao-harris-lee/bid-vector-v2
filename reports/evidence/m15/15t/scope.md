# M15 / 15T — 이미지 공급망: ml-serving 의 pip 입력 해시 고정 (15S 후속 · 15S verifier L-C 문면 정정)

```yaml
milestone: M15
slice: 15t-ml-serving-pip-hashes
base_sha: afc3b9db6f494b76285207e04a39649401dcfec8   # main, PR #206 머지 커밋
head_sha: 리뷰 시점의 PR head   # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다. 승인 전 일괄 라운드의 마지막 **산출물** 커밋은 `fc4d0766e`(pins 주석)이고, rollback 실측은 main 병합 뒤로 미뤘다(팀장 지시 2026-11-27 — 별건 PR #208 이 같은 Dockerfile 의 apt RUN 을 고치므로 실측 자리가 그 병합 커밋이 된다)
in_scope:
  - docker/ml-serving.Dockerfile              # builder: lock 에서 해시 목록 산출 · 빌드 의존 해시 강제 / runtime: --require-hashes 설치 + 설치 보고서
  - tools/image-pip-lock-check.sh             # 신설 — 이미지 설치 집합·내려받은 바이트 ↔ uv.lock 등식 게이트(이름은 구현 레인이 census 관례에 맞춰 조정 가능)
  - tools/image-pip-lock-check.py             # 신설(필요하면) — uv.lock 판독(stdlib tomllib 만)
  - .github/workflows/ci.yml                  # container job 에 게이트 step 한 자리
  - tools/one-command-check.sh                # ci.yml 과 같은 step 을 로컬 진입점에(그 파일이 container 축을 지면)
  - config/quality/shell-guard-policy.properties   # 새 스크립트가 그 census 에 걸릴 때만
  - .dockerignore                             # uv.lock 이 컨텍스트에서 빠져 있을 때만
  - tools/workflow-action-pins-check.sh       # **주석만** — L-C 문면 정정(D-15T-6). 술어 무편집
  - reports/evidence/m15/15s/scope.md         # L-C 문면 정정(D-15T-6) — 「흐름 매핑 → exit 2」 두 자리
  - milestone-15.md                           # 이 slice 가 닫는 것 한 문단
  - reports/evidence/m15/15t/
out_of_scope:
  - Gradle `verification-metadata.xml` · wrapper `distributionSha256Sum` — 다음 slice.
  - `ml-engine/uv.lock`·`pyproject.toml` 의 **내용 변경** — 판을 올리지 않는다. 설치되는 집합의 거동 변화 0 이 목표다.
  - `ml-engine` CI job 의 `uv sync --frozen` — 이미 lock 해시로 설치한다(uv 가 lock 의 해시를 검증한다).
  - web·proxy·app 이미지의 패키지 입력(apk·apt·npm·Gradle) — 다른 축이다.
  - apt 로 올리는 `apt`·`libapt-pkg6.0`(이 Dockerfile 의 runtime RUN) — apt 서명 체인이 지는 축이고 하한 술어가 이미 있다.
  - 핀 게이트의 **술어** 변경(따옴표 키 흐름 매핑을 게이트가 직접 정지시키게 하는 것) — 수집 완전성은 parity 등식이 진다(15S H-1 의 결론). 여기서는 문면만 사실에 맞춘다.
  - 운영 반입 — 이미지 빌드가 바뀌므로 다음 release 에 실린다. 반입 자체는 운영자 몫이다.
acceptance:
  - docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .      # CI S-21 그대로
  - ./tools/image-hygiene-check.sh bidvector/ml-serving:local <ml-serving 정책 파일>   # 기존 위생 게이트가 그대로 초록(ci.yml 의 명령 그대로)
  - <신설 게이트 명령 — ci.yml 에 넣은 그대로>                                         # exit 0 + 계수 출력
  - ./tools/workflow-action-pins-check.sh .github/workflows                            # 주석만 고친 게이트가 그대로 초록
  - ./gradlew check --no-daemon                                                        # Kotlin job(leakPatternGate 가 evidence 를 훑는다 · shell-guard census)
  - PR 의 CI 다섯 job(check · ml-engine · web · container · mutation) green
rollback: reports/evidence/m15/15t/rollback.md
```

## 무엇을 닫는가

`docker/ml-serving.Dockerfile` 의 runtime stage 는 `pip install "${wheel_file}[serving]"` 로 의존을 받는다.
`pyproject.toml` 이 직접 의존의 **판**은 `==` 로 고정하지만 ① 전이 의존(scipy 등)은 판도 떠 있고
② 어느 것도 **바이트**(해시)가 고정돼 있지 않다. 같은 저장소의 `ml-engine/uv.lock` 은 그 닫힘 전체를
sha256 으로 갖고 있고 CI `ml-engine` job 은 그것으로 설치한다. 즉 **검사한 집합과 출하하는 집합이 다른
경로로 풀린다.** builder stage 의 `uv build` 도 같다 — 격리 빌드 환경의 `setuptools>=68`·`grpcio-tools`
(와 그 전이)가 해시 없이 들어오고, 그 도구들이 **출하 wheel 안의 생성 stub** 을 만든다.

## 위협 모델 경계

**막는 것**: PyPI(또는 그 CDN·미러)가 **같은 이름·판에 다른 바이트**를 내주는 것, 전이 의존의 판이
빌드 시점에 조용히 달라지는 것, 그리고 sdist 로 떨어져 **설치 중에 임의 빌드 코드가 도는 것**. 닫는
방법은 「이미지에 들어가는 Python 배포물의 바이트 == `uv.lock` 이 적은 바이트」다.

**막지 않는 것**:
- **lock 에 적힌 그 바이트가 이미 나쁜 경우** — 해시는 「바뀌지 않음」이지 「검토됨」이 아니다(15S 와 같은 문장).
- 이 저장소에 쓸 수 있는 저자 — Dockerfile·게이트·lock 을 함께 고치는 PR 은 통과한다. 게이트는 실수의 래칫이다.
- base 이미지가 싣고 오는 `pip` 자신과 표준 라이브러리 — base 다이제스트 핀(위생 게이트 (2))이 진다.
- `uv` 바이너리 — 이미 다이제스트 핀.
- apt 축 — out_of_scope.

요구 축소가 아니다: 후속 목록의 문면은 「`uv export` 해시 + `--require-hashes`」이고, 이 slice 는 거기에
빌드 의존(builder)과 결과 등식 게이트를 **더한다**.

## 결정

**D-15T-1 — 해시 목록은 커밋하지 않고 builder stage 가 `uv.lock` 에서 만든다.** `uv export`(serving
extra · dev 제외 · 프로젝트 자신 제외 · 해시 포함)의 산출물을 runtime stage 가 받아 쓴다. 생성물을
저장소에 두면 「lock ↔ 생성물」 등식 게이트가 하나 더 필요해진다 — 원천을 하나(`uv.lock`)로 둔다.
lock 이 `pyproject.toml` 과 어긋났을 때 조용히 지나가지 않도록 `--locked` 를 먼저 시도하고, 그것이
빌드 안에서 서지 않으면(네트워크·해석 비용) `--frozen` 으로 내리되 사유와 「어긋남은 `ml-engine` job 의
어느 step 이 받는가」를 실측해 commands.md 에 적는다.

**D-15T-2 — runtime 설치는 두 걸음이다.** ① 의존: `pip install --require-hashes --only-binary :all:
--no-deps -r <목록>` — 해시 불일치는 설치 **전에** 실패하고, wheel 만 받으므로 설치 중 빌드 코드가 돌지
않는다. ② 프로젝트 wheel: `pip install --no-deps --no-index <wheel>` — 같은 빌드가 방금 만든 파일이라
해시를 적지 않고, 색인을 못 보게 해 이 걸음이 무엇도 새로 받지 못하게 한다. ③ `pip check` — wheel 의
메타데이터가 요구하는 것이 ①의 집합으로 충족되는지(닫힘이 모자라면 여기서 비-0).

**D-15T-3 — builder 의 빌드 의존도 lock 의 해시로 묶는다.** `uv build` 에 빌드 제약 파일(lock 에서
뽑은 해시 목록)과 해시 강제 옵션을 준다 — uv 0.9.22 의 정확한 옵션 이름과 거동은 **공식 문서와 실측**
으로 확정한다(기억으로 쓰지 않는다). `setuptools>=68` 이 lock 의 판으로 좁혀지는 것이 의도된 부수
효과다. uv 가 격리 빌드 환경에 해시를 강제할 길이 없으면 구현을 멈추고 보고한다(제한으로 내릴지는
팀장 판단).

**D-15T-4 — 결과 등식 게이트는 Dockerfile 텍스트가 아니라 만든 이미지에 건다.** 두 등식:
- **(가) 설치 집합**: 이미지 안 `importlib.metadata` 가 보는 배포물의 `(정규화 이름, 판)` 집합
  == 설치 보고서의 집합 ∪ {프로젝트 자신} ∪ {base 가 싣고 온 것}. 「base 가 싣고 온 것」은 손 목록이
  아니라 **같은 base 다이제스트 이미지에서 같은 질의로** 잰다(위생 게이트가 이미 그 다이제스트를 푼다 —
  그 경로가 비싸면 정책 파일의 한 줄로 내리고 사유를 적는다).
- **(나) 바이트**: ①의 `pip install --report` 가 남긴 보고서(이미지 안에 둔다)의 항목마다
  **실제로 내려받은 archive 의 sha256** ∈ `uv.lock` 의 그 `(이름, 판)` 의 해시 집합. 보고서 항목 수 ≥ 1
  (비공허) 이고 계수를 출력한다.
(나)는 `--require-hashes` 플래그와 **독립된 측정**이다 — 플래그가 사라져도 받은 바이트가 lock 과 다르면
붉는다. lock 판독은 stdlib `tomllib` 만 쓴다(`container` job 에는 uv 가 없다 · 닫힘 해석을 다시 구현하지
않는다 — 조회만 한다). 보고서가 해시를 싣지 않는 경우가 있는지(캐시·wheel 종류)는 실측으로 확인하고,
싣지 않는 항목은 통과가 아니라 **정지**다.

**D-15T-5 — 양성·음성 대조.** 게이트는 임시 사본으로 자기 변이를 잰다(15S 선례): 보고서의 해시 한 글자
**바꿔치우기** → 붉음 / 보고서에서 한 항목 **제거** → (가)가 붉음 / lock 사본에서 한 패키지의 해시 제거
→ 붉음. 변이마다 사본이 실제로 달라졌는지 먼저 확인한다. 빌드 층의 대조(목록의 해시를 바꿔치우면
`docker build` 가 ①에서 실패하는가 · 빌드 제약의 해시를 바꿔치우면 `uv build` 가 실패하는가)는 **일회
실측**으로 commands.md 에 남긴다 — 매 CI 에서 실패 빌드를 돌리지 않는다(제한 1).

**D-15T-6 — 15S verifier L-C 문면 정정.** 실측(2026-11-27, main `afc3b9db6`): 흐름 매핑 안의 **따옴표 친
키** `- {"uses": a@v5}` 는 `tools/workflow-action-pins-check.sh` 가 **수집도 정지도 하지 않고 지나간다**
(`--count` exit 0 · 수집 0). 따옴표 없는 `- {uses: a@v5}` 는 UNSUPPORTED 로 선다. 그 꼴을 받는 것은
`tools/workflow-action-pins-parity.py` 의 다중집합 등식이다. 게이트 머리 주석의 「읽을 수 있는 두 꼴이
아니면 exit 2(흐름 매핑 …)」와 15s 계약의 「흐름 매핑 … 은 exit 2」·「④ 흐름 매핑·앵커 → exit 2」가 그
꼴에서 거짓이다. 세 자리를 「따옴표 없는 흐름 매핑은 exit 2 · 따옴표 친 키의 흐름 매핑은 이 게이트
혼자서는 초록이고 parity 가 받는다」로 고친다. **술어는 건드리지 않는다**(주석만 — 거동 0 은 게이트의
자기 변이 대조와 `--count` 출력 동일로 확인).

## 설계 검토 (세션 모델 직접 수행)

**(1) 열거인가 구성인가.** 설치는 구성이다 — `--require-hashes` 는 목록의 **모든** 항목에 해시를
요구하고 `--no-deps` + `--no-index` 가 목록 밖 유입 경로를 닫는다. 게이트 (가)·(나)는 집합 등식·포함이고
대상(이미지 안 배포물 전부)을 게이트가 손으로 적지 않는다.

**(2) 우회 — 실수로 다시 열리는 경로.**
① `--require-hashes` 를 지운다 → (나)가 바이트를 lock 과 대조하므로 받은 것이 다르면 붉다. 같으면
초록이다 — 그것은 사실이 그렇다는 뜻이고, 플래그 부재 자체는 제한 1.
② 뒤에 `RUN pip install <새 패키지>` 를 더한다 → (가)가 붉다(보고서에 없는 배포물).
②' 뒤에 **같은 (이름, 판)을** `--force-reinstall` 로 다시 넣는다 → **두 등식 밖이다.** (가)는
(이름, 판) 집합이라 바이트가 갈린 것을 보지 못하고, 보고서는 앞 걸음의 기록이라 그대로다
(verifier 가 위조 wheel 로 실측 — 위생 게이트도 초록이었다). 실수 경로는 「Requirement already
satisfied」로 no-op 이라 의도가 있어야 열린다(저자 축). 알려진 제한 8.
③ extra 를 넓힌다(`--extra training`) → lock 안의 것이라 (나)는 초록, (가)도 초록이다. 금지 import 는
기존 위생 게이트가 실 import 로 잰다 — 그 게이트가 받는지 실측으로 확인한다.
④ lock 을 갱신하지 않고 `pyproject.toml` 판만 올린다 → `--locked` 가 빌드에서 붉히거나(D-15T-1),
`pip check` 가 wheel 메타데이터와 설치 집합의 어긋남으로 붉다.
⑤ 보고서 생성을 지운다 → 게이트가 보고서 부재로 정지(exit 2).
⑥ sdist 만 있는 의존이 lock 에 들어온다 → `--only-binary :all:` 로 빌드가 붉다(조용히 빌드하지 않는다).
⑦ 플랫폼이 다른 러너(arm64) → lock 은 플랫폼별 wheel 해시를 모두 갖는다. 표식(marker) 평가는 pip 이 한다.

**(2b) 값 획득 축.** 새 공개 표면은 셋이다 — 게이트 스크립트(읽기 전용, 이미지를 `docker run` 으로
질의), 이미지 안의 설치 보고서 파일(내려받은 URL·해시·판 — 전부 공개 색인 정보이고 비밀값 없음.
`leak-patterns` 로 한 번 훑어 확인), builder 가 만든 해시 목록(runtime 에 남기지 않거나 남겨도 같은 급).
이미지 크기 상한(위생 게이트)에 보고서가 닿지 않는지 실측.

**(3) 과잉·미달.** 과잉 아님 — (나)는 후속 목록 문면에 없지만, 없으면 이 slice 의 닫힘이 Dockerfile
한 줄의 플래그에만 걸려 회귀를 재는 자리가 0 이다. 미달 후보: base 의 pip 자신(경계로 처리).

## 알려진 제한

**이 절이 「막지 않는 것」의 정본이다**(실측은 `commands.md`).

1. **상시 게이트가 재지 않는 설치 플래그는 `--require-hashes` 하나가 아니다**(vr F-5). `--only-binary
   :all:`(sdist 의 해시도 lock 집합에 들어 있어 (나)가 받는다)와 wheel 걸음의 `--no-deps`·`--no-index`
   도 그렇다 — verifier 실측: 뒤 둘을 지워도 설치 집합이 바이트까지 같다. 오늘은 거동 0 이고, 설치
   **전** 차단이 사라지는 회귀는 리뷰와 일회 실측이 본다.
2. base 이미지의 pip·stdlib 은 base 다이제스트 핀에 의존한다.
3. 해시는 무결성이지 검토가 아니다.

4. **extra 를 `dev` 로 넓히면 세 술어가 모두 초록이다**(vr F-2 로 문면 정정). 설치되는 것이 lock 안이라
   바이트도 집합도 맞고 (다)의 요구 이름도 그대로 든다. 위생 게이트의 금지 패키지 다섯(DB·HTTP 계열)에
   그 도구들이 없어 금지 축도 받지 않는다. **받는 것은 위생 게이트의 크기 상한 하나**다 — 실측
   455,619,980 > 400,000,000 로 exit 1. 방어가 앞 판 문면보다 강한 쪽으로 틀려 있었다(근거가 실 빌드가
   아니라 이름 대조였다).
5. `(가)`의 base 모집단과 위생 게이트 (2)의 base layer 파생은 같은 정책 두 키를 **다른 방법**으로
   쓴다 — 레지스트리 접근이 없으면 둘 다 판정 불가다.
6. 보고서 경로가 Dockerfile 과 CI step 인자 **두 자리**에 산다(어긋나면 fail-closed exit 2). 정책 키로
   옮기려면 `config/quality/image-hygiene-policy.properties`(in_scope 밖)를 건드려야 한다.
7. `lightgbm` 은 이 이미지에서 import 되지 않는다(`libgomp.so.1` 부재). **착수 전에도 같았다**(verifier
   가 base 커밋 이미지에서 같은 실패를 재현) — 이 slice 가 만든 것도 고친 것도 아니고, 별건으로 보고한다.
   파급: 서버는 READY 로 뜬다(추론 경로가 lightgbm 을 싣지 않는다) 그러나 training job 이 trainer 에
   닿으면 `OSError` 가 나고 그 예외는 `except lgb.basic.LightGBMError` 가 받지 못한다(verifier 실측).
8. **같은 (이름, 판)의 강제 재설치는 세 술어 밖이다**(cr F-3 = vr F-1). (가)는 (이름, 판) 집합이라
   바이트가 갈린 것을 보지 못하고, 보고서는 설치 RUN **한 걸음의 기록**이라 뒤 걸음을 적지 않는다.
   verifier 실측: 위조 wheel(원본 + 표지 한 줄)을 `--force-reinstall --no-deps` 로 넣으면 신설 게이트와
   위생 게이트가 모두 초록이고 표지가 이미지에서 import 된다. 구조로 닫으려면 설치된 dist 의 RECORD 를
   보고서 시점의 값에 묶어야 하고, 그 비용은 재지 않았다 — 이번 범위가 아니다.
9. **dist-info 없이 site-packages 에 놓은 `.py` 는 (가)의 질의에 보이지 않는다**(vr F-4). 실행 사용자는
   그것을 import 할 수 있다. 「만든 이미지의 **배포물**」 정의 밖이고 저자 축이다. 반면 `--target`
   (PYTHONPATH 없음)·root `--user` 설치는 질의에도 실행 사용자 import 에도 보이지 않아 실해가 없고,
   `--target` + `ENV PYTHONPATH` 나 `.pth` 는 질의가 따라가 (가)가 받는다(둘 다 verifier 실측).
10. **네트워크가 흔들리면 base 질의가 상시 정지다**(cr F-8). `docker pull` 에 재시도 셋을 붙였지만 셋 다
   실패하면 exit 2 다 — 제한 5 와 같은 축이고, 기존 위생 게이트도 같은 모양이다.

## 계약 갱신 (2026-11-27, 구현 레인의 사실 기록 — 승인 전 일괄 수정 라운드까지)

**① 「설치되는 집합의 거동 변화 0 이 목표다」(out_of_scope 둘째 줄)는 달성되지 않았고, 될 수 없다.**
실측: 출하 이미지의 전이 의존 `narwhals` 가 2.29.0 인데 `uv.lock` 은 2.26.0 이다. 이 slice 가 닫는 것이
바로 그 갈림이므로 **그 한 판이 lock 의 값으로 내려가는 것이 의도된 거동 변화**다(lock 은 CI
`ml-engine` job 이 test 하는 집합이다). `uv.lock`·`pyproject.toml` 의 **내용**은 손대지 않았다 — 그 절은
지켰다.

**② 우회 ①의 문면을 실측으로 좁혔다.** 「`--require-hashes` 를 지운다」만으로는 열리지 않는다 — 목록이
해시를 싣고 있으면 pip 이 해시 검사 모드를 암묵으로 켠다(문서 + 실측, `docker build` exit 1). 실제로
여는 변이는 목록을 `--no-hashes` 로 만드는 것이고, 그 이미지에서 (나)가 붉어지는 것을 쟀다.

**③ D-15T-5 의 변이 셋이 다섯이 됐다.** 넷째 「설치 집합 사본에서 한 줄 제거 → (가)」(앞 셋이 모두
「보고서·lock 이 틀렸다」 방향이라 이미지 질의가 줄어드는 축을 받지 못했다)와 다섯째 「보고서에서
요구 이름 제거 → (다)」다.

**⑤ 술어가 둘에서 셋이 됐다 — (다) 를 더했다**(cr F-2, 승인 전 일괄 라운드). D-15T-2 가 설치를 두
걸음으로 나누면서 **extras 닫힘의 원천이 wheel 메타데이터에서 `uv export` 의 인자 하나로 옮겨갔고**,
그것을 wheel 의 선언과 대조하는 자리가 없었다(`pip check` 는 extra 조건부 의존을 보지 않고, 목록이
좁아지면 이미지도 함께 좁아져 (가)·(나)가 둘 다 초록이다). (다)는 lock 의 프로젝트 항목이 그 extra 에
선언한 직접 의존 **이름**의 포함만 본다 — 닫힘을 다시 구현하지 않는다. extra 이름은 **인자**로 받고
(기존 「정책 파일을 인자로」 관례와 같은 사유 — 어느 extra 로 판정했는지가 호출 자리에 보인다) lock 의
`provides-extras` 와 대조한다(없으면 정지). 빌드 층 일회 대조: `--extra serving` 을 뺀 빌드 → exit 1.

**⑥ 자기 변이의 기대를 「비-0」에서 「exit 1 + 기대 사유」로 좁혔다**(cr F-1). 종료 코드 계약이 세
갈래라 비-0 으로 세면 판독기가 서지 못하는 것이 성공으로 읽힌다(RED 실측: 판독기 exit 2 인 입력에서
앞 판이 「4건 전부 붉음」·exit 0). 더불어 **본 판정이 이미 위반이면 자기 변이를 건너뛴다** — 붉은
입력에서는 사유 대조가 원래 있던 위반을 집어 거짓 어긋남을 낸다(F-2 대조에서 실측).

**⑦ CI step 의 자리를 위생 계열 넷 뒤(S-22b4)로 옮겼다**(cr F-7). ml-serving 전용 게이트가 app·web·
proxy 세 이미지의 위생 판정을 같은 회차에서 지우지 않게 — 이 워크플로가 S-22e 에 적어 둔 결정
(PR #68 M-3)과 같은 방향이다.

**⑧ 「이미지에 들어간 바이트」 주장을 술어만큼으로 좁혔다**(cr F-3 = vr F-1) — 게이트 머리 주석 ·
ci.yml step 주석 · Dockerfile · milestone-15.md 네 자리. 막지 않는 것은 제한 8 로 등재했다.

**④ in_scope 의 조건부 셋은 조건이 거짓이어서 건드리지 않았다** — `tools/one-command-check.sh`(그
파일에 container 축이 0 건) · `config/quality/shell-guard-policy.properties`(그 파일은 파서 핀이고
스크립트 census 가 아니다. `tools/` 를 훑는 Gradle task 도 0 건) · `.dockerignore`(이 저장소에 없고
`uv.lock` 이 컨텍스트에 든다).

## 하네스 레인 변경

없음. `tools/` 의 게이트 스크립트는 `.claude/` 하네스가 아니다. `base..<실측 HEAD>` 에
`CLAUDE.md`·`.claude/**` 커밋 **0** 건(실측, rollback.md).
