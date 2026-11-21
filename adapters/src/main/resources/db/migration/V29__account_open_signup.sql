-- M15/15B — 열린 가입(계약 `milestone-15.md` 의 15B 절, 2026-11-21).
--
-- **이 파일은 V20 의 결정 한 줄을 뒤집는다.** V20 은 「`account` 에 INSERT 가 없다 — 앱 프로세스가
-- 장악돼도 새 계정을 만들 수 없다」를 세웠다. 운영자 지시 2026-11-21(「초대 코드 + 운영자 승인
-- **없이** 바로 가입해서 사용할 수 있도록」)이 그 결정의 **DB 권한 절반**을 되돌린다. 테스트 단계
-- 진입용 완화이고, 상용 전 재결정은 `OPEN-M15-SIGNUP-HARDENING` 이 든다.
--
-- 뒤집지 **않는** 절반을 여기 적어 둔다 — 그 둘은 한 덩어리가 아니다.
--  · `passphrase_hash` · `login_name` 의 UPDATE 는 **여전히 0** 이다. 해시 교체는 운영자 명령의
--    소유자 연결만 할 수 있고, 그래서 인증 결함 하나가 **계정 탈취**로 번지는 길은 오늘도 없다.
--  · `created_at` · `updated_at` · `failed_attempts` 의 INSERT 도 **주지 않는다**. 앱은 계정이 언제
--    생겼는지를 고를 수 없다 — 아래 DEFAULT 가 그 값을 DB 쪽에서 정한다.
--  · DELETE · TRUNCATE 는 그대로 0(파기는 보존 레인의 소유자 연결이 한다).
--  · 같은 경계를 타입이 한 겹 더 진다: 요청 경로가 받는 포트는 `AccountSignUpPort` 하나이고 그
--    서명에 해시 교체가 없다(`workflow/access/AccessPorts.kt`).

-- =============================================================================
-- ① NOT NULL 세 열의 DEFAULT — **GRANT 를 넓히지 않기 위한 선택**이다.
--
--    V20 은 이 셋에 DEFAULT 를 두지 않았다(「시각은 애플리케이션이 싣는다」). 그 전제 아래에서
--    열 둘만 적는 INSERT 는 NOT NULL 위반으로 떨어진다 — 그러면 가입을 열기 위해 INSERT 권한을
--    다섯 열로 넓혀야 하고, 그 순간 앱이 `created_at` 을 **먼 미래로 지정**할 수 있게 된다
--    (보존·파기의 cutoff 축이 영영 지나지 않는 행). DEFAULT 는 그 반대 방향이다: 권한을 좁게 둔 채
--    값을 DB 가 정한다.
--
--    운영자 명령(`bidvector.account.mode=CREATE`)은 지금처럼 **명시 값**을 싣는다 — DEFAULT 는
--    적지 않은 자리에만 적용되므로 그 경로의 거동은 바뀌지 않는다.
-- =============================================================================
ALTER TABLE account ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE account ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE account ALTER COLUMN failed_attempts SET DEFAULT 0;

-- =============================================================================
-- ② 열 단위 INSERT — 가입이 쓰는 두 열뿐이다.
--
--    `id` 는 `GENERATED ALWAYS AS IDENTITY` 다. identity 열의 시퀀스는 열에 **소유**되어 있어
--    `serial` 과 달리 그 시퀀스에 대한 별도 USAGE 를 요구하지 않는다 — 그래서 시퀀스 GRANT 를
--    두지 않는다(권한을 넓히지 않는다). 그 사실은 문장이 아니라 **실측**이 든다:
--    `AccessPrivilegeTest` 가 앱 역할로 이 INSERT 를 직접 쏴 성공을 잰다.
-- =============================================================================
GRANT INSERT (login_name, passphrase_hash) ON account TO bidvector_app;
