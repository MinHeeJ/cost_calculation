import { useCallback, useEffect, useRef, useState } from "react";
import { ConfirmDialog } from "../../components/ConfirmDialog";
import { useNavigation, type Confirmation } from "./navigation";

/** Wiring uses this confirmation port for tab/menu/route transitions before changing its URL. */
export function useNavigationConfirmation() {
  const [question, setQuestion] = useState<Confirmation>();
  const resolve = useRef<(accepted: boolean) => void>();
  const confirm = useCallback(
    (next: Confirmation) =>
      new Promise<boolean>((answer) => {
        resolve.current?.(false);
        resolve.current = answer;
        setQuestion(next);
      }),
    [],
  );
  const answer = (accepted: boolean) => {
    resolve.current?.(accepted);
    resolve.current = undefined;
    setQuestion(undefined);
  };
  useEffect(() => () => resolve.current?.(false), []);
  return {
    confirm,
    dialog: (
      <ConfirmDialog
        open={!!question}
        title={question?.kind === "limit" ? "열린 화면 한도" : "미저장 내용"}
        message={
          question?.kind === "limit"
            ? "화면은 최대 8개입니다. 가장 오래 안 본 탭을 닫을까요?"
            : "저장하지 않은 내용이 있습니다. 나가면 사라집니다."
        }
        confirmLabel={question?.kind === "limit" ? "닫고 열기" : "나가기"}
        cancelLabel={question?.kind === "limit" ? "취소" : "계속 작성"}
        onConfirm={() => answer(true)}
        onCancel={() => answer(false)}
      />
    ),
  };
}

export function UnsavedChangesGuard() {
  const dirty = useNavigation((state) => state.dirty);
  useEffect(() => {
    if (!Object.values(dirty).some(Boolean)) return;
    const protect = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", protect);
    return () => window.removeEventListener("beforeunload", protect);
  }, [dirty]);
  return null;
}
