# M7/7F 본편 — 운영 도메인 TLS · 반입 패키징 (저장소 쪽, 착수 계약, 2026-10-22, 팀장)

- **근거**: `milestone-7.md` 7F 행 · R-3((a) 현 legacy 서버에 compose 로 반입). 운영자 결정 2026-10-22: **도메인 `bidvector.kr`**
  (등록기관은 아직 통지 없음), 「정하고 진행」. 7F-local(#79, `reports/evidence/m7/7f/`)의 연장이다.
- **base**: main `bee7c36f` · worktree `bid-vector-v2-m7-7f2` · 브랜치 `m7-7f2/2026-10-22`.
- **외부 effect 0 (이 slice 의 경계)**: DNS 레코드 변경 · Let's Encrypt 실발급(staging 포함) · 운영 host 접속·반입 · public 포트 개방을
  **하지 않는다.** 그것은 `milestone-7.md` 「운영 host 반입과 public DNS 전환은 별도 승인 사항」이다. 이 slice 는 그 승인이 났을 때
  **one-command 반입과 되돌림이 가능한 상태**를 저장소에 만든다.
- **도메인 값의 자리**: 7F-local 은 「운영 도메인 값은 저장소에 넣지 않는다」(scope :15)로 닫았다. 이제 값이 정해졌으므로 저장소에 넣을지
  (운영 env 파일 예시 · runbook 문면만 · 코드 0) **Phase 1 에서 판정**한다. 기본 입장: 코드·이미지에는 넣지 않고 env 주입 + runbook 문면.

## 산출물 (Phase 1 에서 범위를 확정해 보고)

1. **운영 프로필 compose**: 6C compose 확장(web 정적 번들 + app + ml + db + proxy) — 7F-local 의 `compose.local-tls.yaml` 과 구성
   등식을 유지하는 운영 overlay. 비밀값 주입 경로(파일 · env, 저장소에 값 0).
2. **ACME 자동 갱신**: 프록시(Caddy)의 ACME 설정 — 도메인 · 연락 이메일 · 저장 볼륨 · 갱신 실패 감지. **실발급 없이** 검증할 수 있는 범위
   (구성 lint · 로컬 ACME 테스트 서버 사용 가능 여부)를 Phase 1 에서 판정.
3. **7F-local 이 넘긴 반입 조건** 처리 판정: cr L-4(XFF — 앱이 forwarded 헤더를 읽기 시작하면 신뢰 프록시 경계) · cr L-5(HTTP/3 광고) ·
   cr L-6(번들 인라인 env 경계) · cr L-7(캐시 키 전제) · vr F-5(스캔 등재 만료 결합) · 7D 의 `/api/session` 유량 제한. 각각 닫기/반입 조건
   유지/범위 밖을 근거와 함께.
4. **백업·복원 리허설**(6B-2 연장) · 헬스·로그·알림 경로 · **반입 스크립트와 되돌림 스크립트**(dry-run 모드 필수, 실행은 승인 뒤).
5. **운영 runbook**(6E 연장): 반입 절차 · DNS 전환 체크리스트(운영자가 실행) · 인증서 장애 대응 · 되돌림.

## Phase 1 — 설계 먼저, 코드 0 (팀장 승인 뒤 Phase 2)

checklist.md §0 에 위 1~5 의 범위·순서·위험과 「운영자가 따로 해야 하는 일」(등록기관에서 A/AAAA 레코드 · 운영 host 접근 · 포트 80/443 ·
연락 이메일)을 적고 팀장에게 보고한 뒤 답을 기다린다. R-3 의 「현 legacy 서버」가 어느 host 인지 저장소 근거로 확인할 수 없으면 추측하지
말고 질문으로 올린다.

## 검증·동결

- 무거운 빌드(Gradle · 이미지 빌드 · Testcontainers) 전 확인 셋(pgrep · free · ps)을 별도 호출로 먼저, 다른 레인과 같은 flock 으로 직렬.
  available < 6GB 또는 swap free < 2GB 면 시작하지 않는다. 00:30~03:00 KST 는 6G 수집 창 — 무거운 명령 금지.
- 이미지 태그 `bidvector/app:local` 등 **다른 레인이 container 재생에 쓰는 태그를 덮지 않는다**(g1c03 이 `94382c1026cf` 로 복원 중).
- 완료: `check` · container job 재생 · 위생/취약점 게이트 · rollback.md(명령으로 도출) · `leakPatternGate` executed → 판정 SHA 보고 후 동결.
  push 0. 검토: verifier(opus) · code-reviewer(sonnet) 병렬.

## 재개 (2026-10-31, 팀장)

- **근거**: 운영자 지시 2026-10-31 「7E-3b 끝나면 7F 착수」 — R-9(「7F 본편은 서비스가 오픈 가능한 상태가 된 뒤」)의 승인. 7E-3b(PR #110)가 main
  `957587c4` 로 머지되어 화면 다섯 + 알림함 · 활용사례 캡처 · 서비스 설명이 섰다. 이 브랜치는 2026-10-22 Phase 2(`1d1c2476`)에서 PR 없이 멈췄다.
- **새 base**: main **`957587c4`** — `git merge origin/main`(rebase 아님, 이력 보존). 손 병합 금지 · 합집합 해소(OpenAPI·게이트 설정·compose·CI
  워크플로 · runbook). **rollback 기준은 병합 뒤 `957587c4` 하나**.
- **경계 그대로**: 외부 effect 0 — DNS 변경 · Let's Encrypt 실발급(staging 포함) · 운영 host 반입 apply · public 포트 개방을 하지 않는다. 그것은 이
  slice 머지 뒤 **별도 승인**(운영자 입력: DNS 레코드 · 연락 이메일 · 80/443 · 반입 실행 승인)이다. D-7F2-1(host = 이 WSL, host nginx 공존)·D-7F2-2
  ·D-7F2-3 은 유효하다 — 병합 뒤 그 전제가 바뀌었으면 보고.
- **재판정할 막힌 것**(checklist §1.6): ① caddy Go stdlib CVE — main 의 Caddy 상향(PR #9x)·CI 이미지 교체 뒤 현재 상태를 실측해 해소/allowlist/대기
  중 하나로 ② container job 재생이 다른 레인 태그를 덮는 문제 — 레인 전용 태그로 재생하는 방법 ③ host trivy 부재 — 리허설 vuln 게이트를 건너뛰지 않는
  길(설치는 host 변경이라 보고).
- **이 사이 main 에 들어온 것과의 정합**: 7D 세션·CSRF · 7C③-b(PR #111, 미머지 — 알림 일회 러너 `notification-sweep` 가 cron 항목을 하나 더한다,
  runbook 에서 함께 다룬다) · 7E 화면 · 7F-local 이후 compose/proxy 변경. 운영 compose 가 지금 main 의 서비스 집합·환경 변수와 등식인지 재측정.
- **레인**: 구현 하나(`kotlin-implementer`). 판정 verifier(opus) + code-reviewer 병렬. **인증·비밀값 취급 경로가 있으면**(TLS 키·ACME 계정 키·env
  주입) 되돌리기 어려운 경로 — Codex 는 운영자가 범위·비용을 승인할 때만.
- **순서**: Phase 1R(재개 보고 — 병합 계획 · 충돌 예상 · 막힌 셋 재판정 · 정합 차이) → 팀장 승인 → 병합 → 재측정 → 변이 → rollback → evidence → 동결 → PR.
- 2026-10-31 **D-7F2-4(팀장, Phase 1R 수령 — 병합·이미지)**: 병합 승인(실충돌 1건 `proxy.Dockerfile.dockerignore` 합집합 + `proxy.Dockerfile` 머리 주석 정정).
  **S-21c(`local-tls-build.sh`) 실행 승인** — 덮는 태그 0(`web:local`·`proxy:local` 부재 실측), S-21·S-21b 는 돌리지 않는다(`app:local`·`ml-serving:local` 보존).
  여정 하네스는 docker 이미지를 쓰지 않으므로 다른 레인 통지는 불요. 옛 `bidvector/proxy:w7f2-dev` 는 폐기 후 재생.
- 2026-10-31 **D-7F2-5(팀장 — trivy)**: 리허설 vuln 게이트를 건너뛰지 않는다 — CI 와 같은 절차(릴리스 바이너리 다운로드 + `vuln-policy.properties` 의 sha256 핀 대조)로
  **레인 scratch 에만** 받아 그 호출의 PATH 에 얹는다(host 변경 0, 네트워크 다운로드 1 — 이미지 pull 과 같은 성질로 승인). host 영구 설치는 운영자 질문으로.
- 2026-10-31 **D-7F2-6(팀장 — 리허설 범위)**: **전건 재실행**(마이그레이션 21 · D1~D3 포함) — 완료 조건 「one-command 반입과 되돌림 리허설 증거」의 정본이다.
- 2026-10-31 **D-7F2-7(팀장 — vr F-5 공허 통과)**: 등재 0 이면 preflight 가 말 없이 건너뛴다 → ⓐ 가짜 등재 양성 대조를 변이표에 ⓑ §1.4 문면을 「등재 0 — 검사할
  입력이 없다(검사는 살아 있다: 양성 대조)」로. 가능하면 등재 0 일 때 「건너뜀」을 한 줄 출력하게(조용한 공허 금지).
- 2026-10-31 **D-7F2-8(팀장 — 알림 러너 cron)**: **(b)** — 이번 slice 는 「`notification-sweep` cron 미등록 → 시간 기반 알림 미발화」를 runbook 제한으로만 적는다.
  단 **PR #111 이 이 slice 보다 먼저 머지되면**, 이 slice 의 다음 main 병합 라운드에서 runbook cron 절에 그 항목을 더한다(나중 머지 쪽이 맞춘다).
- 2026-10-31 **rollback 재설계 수령**: 공유 파일 복원원은 병합된 main 고정 SHA `957587c4`, 목록은 `git diff --name-status 957587c4..HEAD` 기계 산출, 전제 문면 재작성.
