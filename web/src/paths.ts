/**
 * 세션 전 자리의 주소 **한 자리**(M15/15B 수정 라운드 2, code-review r1 N-4).
 *
 * 앞 판은 두 상수가 서로의 화면 모듈에 살아 `LoginScreen` ⇄ `SignUpScreen` **순환 import** 가
 * 됐다. 오늘 터지지 않는 이유는 둘 다 상대의 값을 **렌더 시점에만** 읽기 때문인데, 한쪽이 그 값을
 * 최상위 식(상수 묶음·`satisfies` 표)에 쓰는 순간 TDZ 로 적재가 깨진다 — 화면 하나가 아니라 앱
 * 전체가 흰 화면이다. `import/no-cycle` 린트도 없어 다음 고리를 아무도 보지 못한다.
 *
 * **라우트 표에 두지 않는 이유는 그대로다** — 화면이 표를 import 하면 화면 → 표 → 화면 고리가
 * 생긴다. 이 모듈은 **아무것도 import 하지 않으므로** 어느 고리에도 들지 않는다.
 *
 * 라우트 표(`routes.tsx`)의 `loginRoute.path` 는 **문자열 리터럴**이다(여정 화면 표 게이트가 그
 * 파일을 AST 로 읽어 리터럴을 요구한다). 두 자리가 갈리지 않는 것은 `routes.test.ts` 의 등식이 든다.
 */
export const LOGIN_PATH = '/login';

export const SIGN_UP_PATH = '/signup';
