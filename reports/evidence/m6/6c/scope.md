# M6/6C — container 와 local environment (2026-09-16)

```yaml
milestone: M6
slice: 6c-container-local-env
base_sha: f3ac571   # PR #28(하네스 재구성) 머지 커밋 = main
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - docker/ml-serving.Dockerfile                    # multi-stage(wheel 빌드 → 런타임), non-root, serving extra 만, 버전 고정
  - docker/compose.yaml                             # ml-serving + postgres(+ 필요한 경우에만 그 둘) — broker 는 소비자가 없어 세우지 않는다
  - docker/probe/readiness.py                       # readiness 프로브 — GetModelMetadata.readiness 가 READY 인지(gate 실물). liveness 와 다른 종료 코드 의미
  - docker/probe/liveness.py                        # liveness 프로브 — 서버가 RPC 를 받는지(readiness 무관, NOT_READY 여도 살아 있음)
  - docker/ml-serving.Dockerfile.dockerignore        # 빌드 컨텍스트에서 .venv·build·reports·_workspace·bid-vector symlink 제외. **파일명은 `<Dockerfile>.dockerignore`**(계약 갱신 (3) — docker 가 `-f` 로 지정한 Dockerfile 의 형제 파일을 읽는다, 구현 레인 실측)
  - config/quality/image-hygiene-policy.properties   # 위생 게이트의 정책 값(허용 베이스 다이제스트·금지 패키지 목록·크기 상한) — 매직넘버를 스크립트에 박지 않는다(v2-지침서 §5). 계약 갱신 (3)
  - tools/one-command-check.sh                      # 완료 조건 1 — 새 checkout 에서 Kotlin check + Python 전건을 한 명령으로(내부는 CI job 명령 그대로)
  - tools/image-hygiene-check.sh                    # non-root·고정 태그·금지 패키지 부재·크기 상한을 만든 이미지에 대해 실측(6C 게이트)
  - adapters/src/test/kotlin/bidvector/adapters/contract/RealServerIntegrationTest.kt   # OPEN-5E2-CROSSLANG-REAL-SERVER — 컨테이너의 실 Python 서버에 실 Kotlin gateway 로 붙는다(조건부 실행, 기본 check 에서 skip). **패키지는 `adapters.contract`**(계약 갱신 (2), D-6C-8) — 같은 축의 기존 전례 `CrossLangSmokeTest` 와 한자리
  - gradle/libs.versions.toml                       # 위 test 가 컨테이너를 띄우는 데 필요한 의존(Testcontainers) 추가만
  - adapters/build.gradle.kts                       # 같은 test 의 의존·태그 배선만
  - .github/workflows/ci.yml                        # 이미지 빌드·위생 게이트·실 서버 통합 test step 추가(기존 두 job 의 명령 무편집)
  - reports/evidence/m6/6c/**
  - milestone-6.md                                  # M6 착수·6C 착수 문단(팀장 커밋)
out_of_scope:
  - Kotlin 앱 이미지·entrypoint            # `app/` 에 `main()` 이 없다(모듈 경계 앵커 + test 뿐). 앱 배선은 4B-6b 가 6A 로 인계했고 이미지는 그 배선 뒤에만 의미가 있다 — D-6C-1
  - 표준 `grpc.health.v1` Health 서비스    # 오케스트레이터 소비자가 아직 없다. 프로브 둘로 분리 의미를 세우고, 표준 서비스 채택은 6A/6D 결정 — D-6C-3
  - 임베딩 모델 실물(`OPEN-5E-EMBEDDING-MODEL`)  # 모델 선택·가중치 배포·이미지 크기는 컨테이너 축과 독립이고 승인된 모델 입력이 없다 — 6C 는 이 OPEN 을 닫지 않는다(D-6C-6)
  - ml-engine/src/**                       # M5 종결 산출물 무편집. 컨테이너는 있는 서버를 포장할 뿐 서버를 고치지 않는다
  - SBOM·취약점 스캔의 외부 서비스 연동        # 네트워크·계정이 필요한 축. 6C 는 고정 태그·금지 패키지·non-root 까지, 취약점 스캔 도구 채택은 6E 운영 축 — D-6C-5
  - Flyway 전체 재현·backup/restore         # 6B 소관(compose 의 postgres 는 6B 가 쓸 자리를 만드는 것까지)
  - 운영 배포·레지스트리 push·실 외부 호출   # milestone-6 「완료 후 별도 승인 사항」
acceptance_commands:
  - "./gradlew --no-daemon check"                                              # S-10 — 기존 전건(이 slice 는 test 하나·배선만 더한다), evidence 커밋마다 그 HEAD 에서
  - "(cd ml-engine && uv sync --frozen --all-extras && uv run python -m pytest tests -q)"   # S-5 승계 — ml-engine 무편집 확인
  - "./tools/one-command-check.sh"                                             # S-20 — 완료 조건 1: 한 명령으로 Kotlin+Python 전건
  - "docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local ."   # S-21 — 이미지 빌드
  - "./tools/image-hygiene-check.sh bidvector/ml-serving:local"                # S-22 — non-root·고정 태그·금지 패키지 0·크기 상한
  - "docker compose -f docker/compose.yaml up -d && docker compose -f docker/compose.yaml ps"   # S-23 — 로컬 환경 기동(프로브가 healthy 로 수렴)
  - "./gradlew --no-daemon :adapters:test --tests '*RealServerIntegrationTest*' -PrealServer=true"   # S-24 — 실 Kotlin ↔ 컨테이너의 실 Python 통합
  - "docker compose -f docker/compose.yaml down -v"                            # S-25 — 정리(볼륨까지)
rollback: |
  신규 파일 삭제 + 편집 넷(`ci.yml`·`libs.versions.toml`·`adapters/build.gradle.kts`·`milestone-6.md`)을 base 로.
  `git restore --source=f3ac571 --staged --worktree -- <in_scope 경로 개별 인자>`, `docker/**`·`reports/evidence/m6/6c/**` 는 삭제.
  되돌리면 컨테이너 자산이 사라지고 `OPEN-5E2-CROSSLANG-REAL-SERVER` 가 재개방된다(M5 상태). 이미지·볼륨은
  `docker compose down -v` + `docker image rm bidvector/ml-serving:local` 로 정리(호스트 상태라 git 밖 — rollback.md 에 명시).
  임시 clone ①~⑥ 실측, 정본 `reports/evidence/m6/6c/rollback.md`.
```

작성: 2026-09-16, 세션 모델 단독. 근거: `milestone-6.md` 「Slice 6C」·완료 조건 1·8 · M5 종결 판정
(`reports/evidence/m5/closure/checklist.md` §2.5 — `OPEN-5E2-CROSSLANG-REAL-SERVER` 6C 이월) ·
M6 입력 재고(`_workspace/m6-prep/01_inputs.md` §4·§6) · 5E-1 `ReadinessGate`·`GetModelMetadata.readiness`(5E-2) ·
5A D-5A-0 (b)(serving extra 경량) · 5E-1 알려진 제한 8(wheel 빌드는 `grpcio-tools` 를 build-system 에 요구).

## 이 slice 가 하는 일

① **실 ML 서빙 이미지** — multi-stage(빌드 단계에서 wheel 을 만들고 런타임 단계는 wheel + serving extra 만),
non-root 사용자, 베이스·패키지 버전 고정. 런타임에 학습·DB 의존이 들어오지 않음을 이미지에서 실측한다(5A 의
S-1b 를 이미지 층으로 옮긴 형태 — 금지 패키지 다섯 각각 import 실패).
② **로컬 환경** — `docker compose` 로 ml-serving + postgres 를 세운다. broker 는 **소비자가 없어 세우지 않는다**
(milestone-6 문면 「필요한 경우에만」). postgres 는 6B 가 쓸 자리이며 6C 는 기동·프로브까지.
③ **health/readiness 분리** — 두 프로브가 **다른 것을 잰다**: liveness 는 서버가 RPC 를 받는지, readiness 는
`GetModelMetadata.readiness == READY`(5E-1 gate 실물, 정책 넷 전부 로드). 출하 정책이 READY 인 것은 5F-1 이
닫았으므로 이 환경은 **정책 그대로 READY 로 수렴**해야 한다 — 수렴하지 않으면 6C 의 전제가 틀린 것이다.
④ **실 교차 언어 통합**(`OPEN-5E2-CROSSLANG-REAL-SERVER` 종결) — 컨테이너의 실 Python 서버에 **실 Kotlin
gateway**(`GrpcBidPredictionGateway`, fake 아님)로 붙어 `CalculateOptimalBid`·`GetModelMetadata` 를 호출하고,
5E-2 가 Python 미러로만 확인했던 Kotlin 소비자 규칙 다섯(`ReleaseShapeValidation`·`ReleaseCheck`·
`ParsedSuccessFields`·`FractionRules`·`ResponseMapping`)이 **실 응답에서** 성립함을 잰다. 5F-2 가 맞춘
`featureSchemaVersion` 이 실제로 수용되는지도 여기서 처음 실측된다.
⑤ **완료 조건 1 의 한 명령** — `tools/one-command-check.sh` 가 새 checkout 에서 Kotlin 전건 + Python 전건을
한 번에 돌린다. 내부 명령은 CI job 의 명령 **그대로**(하네스 2026-09-12) — 새 축약을 만들지 않는다.
⑥ **이미지 위생 게이트** — non-root·고정 태그(`:latest` 금지)·금지 패키지 0·크기 상한을 만든 이미지에 실측하고
CI step 으로 건다. 「상시 붉은 게이트도, 안 돌린 게이트도 아무것도 막지 못한다」(하네스)에 따라 CI 에 넣는다.

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6C-1** | Kotlin 앱 이미지·entrypoint 는 **6A** — 6C 는 만들지 않는다 | `app/` 에 `main()` 이 없다(앵커 + test 뿐). 4B-6b 종결 문면이 「`UnavailableMlAnalysis` 처분과 app 배선은 6A」로 인계했다. 실행할 것이 없는 이미지는 위생 게이트만 통과하는 빈 껍데기다 |
| **D-6C-2** | compose 는 **ml-serving + postgres 둘만**. broker 는 세우지 않는다 | milestone-6 「broker가 필요한 경우에만」. outbox/inbox(V6)는 DB 기반이고 현재 broker 소비자 코드가 없다 |
| **D-6C-3** | health/readiness 는 **프로브 둘**로 분리하고 표준 `grpc.health.v1` 은 채택하지 않는다 | 서버 코드(M5 종결)를 열지 않고 분리 의미를 세운다. 표준 서비스는 오케스트레이터가 소비할 때(6A 배선·6D E2E) 결정 — 그때 프로브를 대체한다 |
| **D-6C-4** | 실 서버 통합 test 는 **태그로 분리**하고 기본 `check` 에 넣지 않되 **CI 에서는 돈다** | Docker 를 요구하는 test 가 기본 `check` 에 들어가면 Docker 없는 환경에서 상시 붉어진다. CI step 으로 걸어 「안 돌린 게이트」가 되지 않게 한다 |
| **D-6C-5** | SBOM·CVE 스캔 도구 채택은 **6E**, 6C 는 고정 태그·금지 패키지·non-root·크기까지 | 스캔은 외부 서비스·계정·정책(무엇을 차단하는가)이 필요한 운영 축이다. 6C 에서 흉내만 낸 스캔은 상시 초록 게이트가 된다 |
| **D-6C-6** | `OPEN-5E-EMBEDDING-MODEL` 은 6C 가 **닫지 않는다** | 모델 선택·가중치 배포는 승인된 입력이 없고 컨테이너 축과 독립이다. 6C 종결 보고에서 운영자에게 처분(별 slice / M6 뒤)을 묻는다 |
| **D-6C-8** (계약 갱신 (2), 구현 레인 정지·보고) | 실 서버 통합 test 는 `bidvector.adapters.contract` 에 둔다 — **게이트 술어를 건드리지 않는다**. `adapters.ml` 안에서는 기존 게이트 둘(디렉터리 전수 등재 요구 · 등재된 게이트 test 의 skip 0 요구)이 **환경 조건부 test 에 대해 동시에 만족 불가**다(등재하면 skip 위반, 빼면 등재 위반). 채택하지 않은 안: ⓐ 게이트에 「조건부 허용」 키 신설 · ⓑ 등재 요구 예외 목록 — 둘 다 **어떤 test 든 자기를 조건부로 선언해 영원히 안 돌 수 있는 문**을 만든다(오탐을 닫으려다 미탐을 여는 방향, 하네스 2026-09-04 게이트 술어 예외 조항). 채택안은 같은 축의 기존 전례와 동일 — `CrossLangSmokeTest`(교차 언어 스모크)가 이미 `adapters.contract` 에 있고 이 test 는 그 컨테이너 판이다. 잔여는 `OPEN-6C-CONDITIONAL-GATE-TEST` | 구현 레인 실측: `MlGateRegistrationTest` 는 `src/test/kotlin/bidvector/adapters/ml` 디렉터리의 `*Test.kt` 전수를 `gate.tests.adapters` 등재와 대조하고, `GateExecutionGateTask` 는 등재된 클래스의 skip 0 을 요구한다 |
| **D-6C-9** (계약 갱신 (4), verifier r1 HIGH 둘) | 위생 게이트의 두 술어는 **선언이 아니라 실행·빌드 산출물**을 읽는다 — ① non-root: `--entrypoint` 로 덮어쓴 `id -u`(선언된 `USER` 만 잼)가 아니라 **실 ENTRYPOINT 로 띄운 컨테이너의 pid 1 사용자**(`docker top -o user` 류)를 정책 값과 대조 ② 베이스 고정: Dockerfile 이 손으로 적는 라벨 문자열이 아니라 **빌드가 실제로 해석한 베이스 다이제스트**(BuildKit provenance attestation, 없으면 layer 접두 대조)를 대조. 라벨·선언은 보조 근거로만 쓰고 단독 근거로 쓰지 않는다 | verifier r1 실측: `USER` 를 유지한 채 setuid 로 root 를 얻는 entrypoint 이미지에서 게이트 exit 0(그냥 실행하면 EUID 0) · `FROM` 을 떠 있는 태그로 바꾸고 라벨만 남긴 이미지에서 게이트 exit 0. **출하 이미지 자신은 안전**(pid 1 uid 10001, root 잠김)이고 게이트의 맹점이다. 우회 (1)·위협 모델 (b)(c) 가 한 토큰 편집으로 무력화됐다 |
| **D-6C-10** (계약 갱신 (5), verifier r2 HIGH 둘) | ① 위생 게이트의 정책 파서는 **키 모양뿐 아니라 값 모양**을 검증한다 — 빈 값 거부(직전 판에 있었고 F-3 시정이 지웠다)·수치 키 `^[0-9]+$`·열거 키 원소 ≥1·CRLF 절삭, 위반은 비-0. 수치 비교를 `elif` 조건에 두어 `set -e` 를 면제시키지 않는다. ② **베이스 고정의 정책 값은 layer 목록이 아니라 고정 다이제스트 하나**로 두고, 게이트가 그 다이제스트를 pull 해 layer 체인을 **파생**해 대조한다(값 바꿔치기 표면을 목록에서 다이제스트 하나로 줄인다. pull 불가 환경이면 목록 유지 + 사유 등재). ③ non-root 는 점 표집에 기대지 않고 **능력 차단**을 함께 요구한다(`--security-opt no-new-privileges` 로 상승 자체를 막고, 출하 이미지가 그 조건에서 READY 로 서는 것을 실측). ④ CI 에서 6C 게이트를 돌리는 step 은 **그 job 의 전제(도구·이력 깊이)가 이미 충족된 job**에 둔다 — 전제를 새 job 에 복제하지 않는다 | verifier r2 실측: F-3 시정이 빈 값 거부를 함께 지워 `forbidden.packages=`·`base.image.layers=`·`size.cap.bytes=` 를 비우면 게이트가 통과하고, 특히 layer 목록을 비우면 **`base-layer-접두-일치=false` 를 찍으면서 exit 0** 이었다 · F-6 시정이 넣은 S-20 step 이 `buf`·전체 이력 부재로 통과 불가이고 그것이 step 6 이라 **S-21~S-25(6C 게이트 전부)가 CI 에서 한 번도 실행되지 않았다** · 점 표집(`sleep 1`)은 표집 뒤 상승하는 entrypoint 를 놓쳤다(t=2s 비특권 → t=8s root) |
| **D-6C-11** (계약 갱신 (6), 운영자 결정 2026-09-17 (a)) | 6C 는 **현 셸 위생 게이트로 종결**한다. 잔여 「값 모양」 위험은 알려진 제한으로 등재하고, 구조적 처방(정책 파싱·술어를 `build-logic` 타입 게이트 태스크로 이식 + 술어별 음성 대조 test)은 **6C 안의 패치가 아니라 별 slice**(`OPEN-6C-POLICY-GATE-STRUCTURAL`)로 둔다 — 착수 가치는 **배포 경로가 생길 때**(앱 이미지·레지스트리·배포가 있는 slice) 평가한다 | verifier r3 패턴 진단: 세 라운드의 게이트 finding 이 **같은 함수**이고 매번 술어가 틀린 답을 내는 게 아니라 **아무 일도 하지 않게** 됐다(중복 키 → 빈 값 → 공백 원소). 열거 방어의 서명이며 저장소의 기존 교훈(「불가능한 상태는 타입으로 닫는다」)이 가리키는 자리다. 같은 라운드가 반례도 냈다 — 능력 차단 한 줄이 권한 상승 계열을 구성으로 닫았고, 이는 non-root 술어를 세 라운드 갈아 만든 것과 대비된다. 지금 이 게이트가 지키는 것은 **아무데도 배포되지 않는 이미지 하나**다(D-6C-1 로 앱 이미지 없음, 레지스트리·배포 없음) |
| **D-6C-7** | 이미지 안 정책 파일은 **저장소의 것을 복사**하고 런타임 경로를 env 로 지정한다(기본값 없음, 5E-1 D-5E-7 계승) | 정책 값의 정본은 저장소이고 이미지가 두 번째 자리가 되면 안 된다 — 이미지 빌드 시점의 정책 checksum 을 evidence 에 남긴다 |

## 위협 모델 — 6C 고유 경계

**방어한다**: (a) 서빙 런타임에 DB driver·ORM·학습 의존이 들어오지 않는다(이미지 층 실측) (b) 컨테이너가
root 로 돌지 않는다 (c) 버전이 떠 있지 않다(`:latest`·미고정 0) (d) 자격증명이 이미지·compose 파일에 박히지
않는다(환경변수·자격증명 저장소 주입만, 값은 파일에 없다) (e) readiness 가 정책 로드 실패를 덮지 않는다(gate 실물) (f)
빌드 컨텍스트가 `bid-vector` symlink·`.venv`·`reports` 를 담지 않는다.
**방어하지 않는다**: 이미지 취약점(CVE)·SBOM — D-6C-5 · 레지스트리·배포·서명 · 멀티아키(arm/amd) 빌드 ·
오케스트레이터(k8s) manifest · 표준 health 프로토콜 — D-6C-3 · 임베딩 모델 — D-6C-6 · Kotlin 런타임 이미지 — D-6C-1.

**우회 후보**: (1) 위생 게이트가 이미지가 아니라 **Dockerfile 텍스트**를 읽으면 `USER` 를 적고 뒤에서 되돌리는
빌드가 통과한다 → 게이트는 **만든 이미지에 실측**(`docker inspect`·이미지 안 `id -u`·실제 import 시도)으로 건다
(2026-09-16 하네스 「게이트 술어는 문자열이 아니라 구조로」). (2) 금지 패키지 검사가 `pip list` 이름 대조면 벤더링된
사본을 놓친다 → 각 패키지를 **실제로 import 해 실패**하는지 본다(5A S-1b 형태). (3) 실 서버 통합 test 가
컨테이너가 아니라 로컬 `.venv` 서버에 붙으면 「실 통합」이 아니다 → test 가 **컨테이너 포트**에 붙었음을
스스로 확인(컨테이너 id·이미지 태그를 단언). (4) readiness 프로브가 TCP 연결만 보면 NOT_READY 를 healthy 로
읽는다 → 프로브가 `GetModelMetadata.readiness` 값을 보고 **NOT_READY 에서 실패**하는지 변이로 실측(정책 한
줄을 지운 이미지). (5) one-command 스크립트가 실패를 삼키면(`|| true`·파이프 마지막 exit) 초록이 거짓이 된다 →
각 단계 실패를 심어 스크립트가 비-0 으로 끝나는지 실측. (6) `.dockerignore` 누락으로 symlink 가 컨텍스트에
들어오면 완료 조건 2 가 깨진다 → 빌드 컨텍스트 목록을 실측하고 이미지 안에 `bid-vector` 경로 0 을 단언.

## (2b) 값 획득 축

| 표면 | 허락하는 것 | 판정 |
| --- | --- | --- |
| `docker/compose.yaml` 의 postgres 자격증명 | 로컬 환경 접속 | **경계로 처리** — 값은 개발용 고정값이 아니라 env 참조(기본값 없음), compose 파일에 자격증명 리터럴 0 을 게이트로 실측 |
| readiness/liveness 프로브 스크립트 | 서버 상태 조회 | 닫는다 — 조회 전용 RPC 둘만 호출, 쓰기 RPC 를 부르지 않음(스크립트 전수) |
| `RealServerIntegrationTest` 의 gateway 생성 | 실 서버 호출 | **경계로 처리** — test 소스셋 한정(`src/test`), 태그로 기본 실행에서 분리. production 표면 증가 0 을 AST/시그니처로 확인 |
| `one-command-check.sh` | 전건 실행 | 닫는다 — CI 명령 그대로이고 새 플래그·축약을 만들지 않음 |

## 하네스 레인 변경

`git log --oneline f3ac571..HEAD -- CLAUDE.md .claude/ docs/harness/` — 없음(착수 시점).

## OPEN — 수령·신설

| 식별자 | 처분 |
| --- | --- |
| `OPEN-5E2-CROSSLANG-REAL-SERVER`(M5 이월) | 이 slice ④ 로 **종결 예정** — 실 Kotlin gateway ↔ 컨테이너의 실 Python 서버 |
| `OPEN-5E-EMBEDDING-MODEL`(M5 이월) | **유지** — D-6C-6, 6C 밖. 종결 보고에서 운영자 처분을 묻는다 |
| `OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND`(M5 이월) | **6B** — 이 slice 밖(compose 의 postgres 가 그 자리를 만든다) |
| `OPEN-6C-IMAGE-VULN-SCAN`(신설) | SBOM·CVE 스캔 도구·차단 정책 — D-6C-5, 6E |
| `OPEN-6C-POLICY-GATE-STRUCTURAL`(신설, 구현 레인 등재·D-6C-11) | 정책 파싱·위생 술어를 `build-logic` 타입 게이트 태스크로 이식(잘못된 정책 = 파싱 실패, 술어별 음성 대조 test). **별 slice**, 배포 경로가 생길 때 가치 평가 |
| `OPEN-6C-CONDITIONAL-GATE-TEST`(신설) | `adapters.ml` 안에 **환경 조건부 test 가 필요해지면** 기존 게이트 둘이 동시 만족 불가다(D-6C-8). 지금은 패키지 분리로 피했고, 그 자리가 실제로 생기면 게이트 술어 개정을 **설계 검토**로 받는다(조건부 허용 목록을 그냥 두면 미탐 문이다) |
| `OPEN-6C-MULTIARCH`(신설) | arm64/amd64 멀티아키 빌드 필요 여부 — 배포 대상이 정해질 때(6E) |
| `OPEN-ADR-09`(운영자 결정 2026-09-16) | **닫힘 — API 전용 + 기존 화면 유지**(운영자 답변). 6A 가 그 결정을 계약으로 옮기고 ADR 에 등재한다 |

## 계약 갱신 이력

| 일자 | 갱신 | 사유 |
| --- | --- | --- |
| 2026-09-16 착수 | 초판 — D-6C-1~7 | 사용자 「M6 착수」 + 착수 slice 6C 선택 · M6 입력 재고 |
| 2026-09-17 종결 전(6) | **D-6C-11 신설** — 운영자 결정 (a): 현 셸 게이트로 종결, 잔여 값 모양 위험은 알려진 제한, 구조적 이식은 `OPEN-6C-POLICY-GATE-STRUCTURAL`(별 slice) · 그 식별자를 OPEN 표에 등재 | 사용자 선택 「(a) 현 게이트로 6C 닫기」(verifier r3 패턴 진단 + 지키는 대상 부재를 근거로 제시된 선택지) |
| 2026-09-17 수정 라운드 2(5) | **D-6C-10 신설** — 정책 **값** 모양 검증(빈 값 거부 복원 포함) · 베이스 고정을 다이제스트 하나 + 파생 대조로 · non-root 에 능력 차단 동반 · CI 게이트 step 은 전제가 충족된 job 에 · R2-4·LOW 넷 처분 지시 | verifier r2 not-ready — r1 HIGH 둘은 닫혔고 **수정 라운드가 새 HIGH 둘을 만들었다**(정책 파서의 빈 값 거부 소실 · S-20 이 통과 불가여서 6C 게이트 전부가 CI 미실행). 재작업 2/5 |
| 2026-09-16 수정 라운드 1(4) | **D-6C-9 신설** — 위생 게이트 두 술어의 정본 형태를 실행·빌드 산출물로 못 박는다(선언 단독 금지) · F-3~F-6 처분 지시(정책 파서 중복 키 · 어휘 스캔 경로와 `ci.yml` 누락 · rollback 시작 트리 · one-command 의 `qualityBaseline` 누락과 CI 미연결) | verifier r1 not-ready(HIGH 2 — 게이트 술어, 표적 재검증 대상) |
| 2026-09-16 리뷰 요청(3) | in_scope 에 `config/quality/image-hygiene-policy.properties`(위생 게이트 정책 값 외부화 — 스크립트에 매직넘버 금지) 추가 · `.dockerignore` 파일명을 실제 동작하는 `docker/ml-serving.Dockerfile.dockerignore` 로 정정 | 구현 완료 보고 `50c108a` 의 새 파일 대조 — 둘 다 계약 초판에 없었다. 값의 자리를 스크립트 밖으로 뺀 것은 규율에 맞고, 파일명은 docker 의 실제 해석 규칙이다 |
| 2026-09-16 구현 중(2) | **D-6C-8 신설** — 실 서버 통합 test 패키지를 `adapters.ml` → `adapters.contract`(같은 축 기존 전례와 한자리), in_scope 경로 갱신 · `OPEN-6C-CONDITIONAL-GATE-TEST` 신설 | 구현 레인 정지·보고: 기존 게이트 둘이 환경 조건부 test 에 동시 만족 불가. 게이트 술어를 약화시키는 두 안을 거부하고 패키지 분리 |
