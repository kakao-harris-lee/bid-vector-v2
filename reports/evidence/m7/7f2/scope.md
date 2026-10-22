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
