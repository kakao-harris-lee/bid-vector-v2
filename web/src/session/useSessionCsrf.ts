import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';

import { rememberCsrf } from '../api/client';
import { readSession } from '../api/queries';

/**
 * 쓰기를 하는 화면이 **새로고침 뒤에도 CSRF 값을 쥐게** 한다(M7/7E-1 — 7A 가 알려진 제한으로
 * 남긴 cr r2 L-1).
 *
 * CSRF 값은 메모리에만 있어 새로고침하면 사라진다. 서버는 `GET /api/session` 으로 쥔 쿠키에서
 * 그 값을 다시 유도해 주고 읽기는 CSRF 면제다. 세션이 없으면 `null` 이고 그때는 **값을
 * 기억하지 않는다**(fail-closed — 서버가 쓰기를 거부한다).
 *
 * 쓰기 미들웨어 안에서 네트워크를 타지 않는 이유: 숨은 호출은 「어느 요청이 세션을 조회하는가」를
 * 호출자에게서 감춘다. 화면이 뜰 때 한 번 부르는 쪽이 그 자리를 드러낸다.
 */
export function useSessionCsrf(): { readonly signedIn: boolean | null } {
  const session = useQuery({ queryKey: ['session'], queryFn: readSession });

  useEffect(() => {
    if (session.data === undefined) {
      return;
    }
    rememberCsrf(session.data === null ? null : session.data.csrf);
  }, [session.data]);

  if (session.data === undefined) {
    return { signedIn: null };
  }
  return { signedIn: session.data !== null };
}
