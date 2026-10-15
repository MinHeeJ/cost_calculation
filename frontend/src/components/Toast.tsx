import { useEffect } from "react";
import styles from "./Common.module.css";

export function Toast({
  message,
  onClose,
}: {
  message: string;
  onClose: () => void;
}) {
  useEffect(() => {
    const timeout = window.setTimeout(onClose, 3000);
    return () => clearTimeout(timeout);
  }, [message, onClose]);
  return (
    <div className={styles.toast} role="status" aria-live="polite">
      {message}
    </div>
  );
}
