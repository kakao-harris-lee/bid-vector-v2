# M7/7F 본편 — 실행 명령과 종료 코드

**acceptance 정의**: `.github/workflows/ci.yml` 의 `container` job 명령 그대로. 이 slice 는 그 job 끝에 S-27·S-27b·S-27c 를 더했다.
`check` job 입력은 evidence(`leakPatternGate`) 하나만 닿는다. `web`·`ml-engine` job 입력 변경 0.
**job 전건 재생은 돌리지 않았다** — S-21·S-21b·S-21c 가 `bidvector/*:local` 을 다시 굽는데, 팀장 지시가 그 태그를 덮지
못하게 한다(checklist §1.6 ②). 아래는 그 대신 돈 것이다. 마지막 HEAD 의 게이트 정본은 verifier 와 PR 조치 코멘트다.

호스트 규칙: 컨테이너·빌드 명령은 전부 `flock ~/.local/bid-vector-build.lock` 아래에서, 확인 셋(pgrep·free·ps)을 별도
호출로 먼저 돌린 뒤 실행했다. 00:30~03:00 KST 실행 0.

## 2026-10-22 (KST)

| 명령 | exit | 핵심 결과 |
|---|---|---|
| `./tools/prod-env-keys-check.sh` (S-27) | 0 | 키 18 등식 |
| `./tools/prod-proxy-sites-check.sh --proxy-image bidvector/proxy:w7f2-dev` (S-27b 와 같은 명령, 이미지 태그만 레인 것) | 0 | 하위 라우트 4 등식 · compose 단언 전부 |
| `./tools/prod-nginx-check.sh --proxy-image bidvector/proxy:w7f2-dev` (S-27c 와 같은 명령, 이미지 태그만 레인 것) | 0 | 불변 3표지 · N2~N9 |
| `./tools/prod-nginx-check.sh --host-config /etc/nginx --proxy-image bidvector/proxy:w7f2-dev` | 0 | 불변 5표지 · N2~N9 · 사본 삭제 확인 |
| 로컬 site adapt — base 파일 vs 분리 뒤 이미지 | — | JSON 바이트 동일 |
| `./tools/workflow-step-refs-check.sh .github/workflows/ci.yml` | 0 | 매달린 참조 0 |
| `./tools/image-hygiene-check.sh` × 4 (release 안에서, 리허설 태그) | 0 ×4 | proxy 는 새 정책(내용 하한 3) |
| `./tools/vuln-scan-check.sh bidvector/proxy:w7f2-dev proxy …` | **1** | 미등재 1(`CVE-2026-111943`, Go stdlib). **같은 명령을 base 이미지 `bidvector/proxy:local` 에 → 1, 같은 미등재 1** — 이 slice 무관(checklist §1.6 ①). trivy 0.75.0(정책 판과 같다)은 앞 레인의 재생 임시 디렉터리 것을 썼다 |
| 반입 리허설 part 1·2(release·deploy·rollback·health·변이 D1~D3·정리) | 표 참조 | checklist §1.5 |
| rollback ①~⑥ 의 ⑥ⓐ: 되돌린 임시 clone 에서 `./gradlew --no-daemon -q leakPatternGate` | 0 | 실행 뒤 트리가 여전히 되돌린 상태(base 대비 evidence 둘뿐) |
| `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m7/7f2/` | 1(매치 0) | evidence 편집마다 |
| 공존 서비스 식별자 두 문자열(D-7F2-2 가 정한 것)의 대소문자 무시 계수 — 커밋마다 그 diff 와 새 파일 | 0 | D-7F2-2 지시(커밋 전 직접 확인). 문자열 자체는 evidence 에 적지 않는다 |
