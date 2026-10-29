import { useCallback, useState } from 'react';

/**
 * 즉시 반응의 토스트(§6.5). **실행 취소는 「API 가 되돌림 전이를 줄 때만」** 둔다(D-7E-5) —
 * 되돌릴 수 없는 일에 실행 취소 버튼을 두면 그 버튼이 거짓말이 된다. 그래서 `undo` 는
 * 선택이고, 없으면 버튼이 **그려지지 않는다**(비활성 버튼도 두지 않는다).
 */
export interface ToastMessage {
  readonly text: string;
  readonly undo?: { readonly label: string; readonly run: () => void };
}

export function useToast(): {
  readonly toast: ToastMessage | null;
  readonly show: (message: ToastMessage) => void;
  readonly dismiss: () => void;
} {
  const [toast, setToast] = useState<ToastMessage | null>(null);
  const show = useCallback((message: ToastMessage) => {
    setToast(message);
  }, []);
  const dismiss = useCallback(() => {
    setToast(null);
  }, []);
  return { toast, show, dismiss };
}

/**
 * 토스트 자리. **시간으로 사라지지 않는다** — 자동 소멸은 실행 취소를 쥐고 있는 사용자의
 * 손을 재촉하고, test 가 시간을 흉내 내야 하는 자리를 만든다. 닫기는 사용자가 한다.
 */
export function ToastHost({
  toast,
  onDismiss,
}: {
  readonly toast: ToastMessage | null;
  readonly onDismiss: () => void;
}) {
  if (toast === null) {
    return null;
  }
  return (
    <div
      data-testid="toast"
      role="status"
      aria-live="polite"
      className="fixed inset-x-4 bottom-4 z-10 mx-auto flex max-w-md items-center justify-between gap-3 rounded-lg border border-line bg-card px-4 py-3 shadow-lg"
    >
      <span className="text-sm">{toast.text}</span>
      <span className="flex items-center gap-2">
        {toast.undo === undefined ? null : (
          <button
            type="button"
            onClick={() => {
              toast.undo?.run();
              onDismiss();
            }}
            className="rounded-md border border-line px-2 py-1 text-xs"
          >
            {toast.undo.label}
          </button>
        )}
        <button type="button" onClick={onDismiss} aria-label="닫기" className="text-xs text-ink-soft">
          닫기
        </button>
      </span>
    </div>
  );
}
