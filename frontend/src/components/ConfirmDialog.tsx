import { type ReactNode } from "react";
import { WorkDialog } from "./WorkDialog";
import { Button } from "./Button";

export function ConfirmDialog({
  open,
  title = "확인",
  children,
  message,
  confirmLabel = "확인",
  cancelLabel = "취소",
  onConfirm,
  onCancel,
  destructive = false,
  busy = false,
}: {
  open: boolean;
  title?: string;
  children?: ReactNode;
  message?: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void | Promise<unknown>;
  onCancel: () => void;
  destructive?: boolean;
  busy?: boolean;
}) {
  return (
    <WorkDialog
      open={open}
      title={title}
      compact
      cancelLabel={cancelLabel}
      onClose={onCancel}
      actions={
        <Button
          variant={destructive ? "danger" : "primary"}
          busy={busy}
          onClick={onConfirm}
        >
          {confirmLabel}
        </Button>
      }
    >
      {children ?? message}
    </WorkDialog>
  );
}
