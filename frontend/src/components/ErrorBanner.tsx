import { useState } from "react";
import { RequestError } from "../api/client";
import { Button } from "./Button";
import styles from "./Common.module.css";

export function ErrorBanner({
  error,
  onRetry,
  loading = false,
}: {
  error: RequestError;
  onRetry: () => void | Promise<unknown>;
  loading?: boolean;
}) {
  const [busy, setBusy] = useState(false);
  async function retry() {
    if (busy || loading) return;
    setBusy(true);
    try {
      await onRetry();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div role="alert" className={styles.errorBanner}>
      <span>{error.message}</span>
      <span>
        {error.source === "server" ? "문의 번호" : "클라이언트 문의 번호"}:{" "}
        {error.requestId}
      </span>
      <Button busy={busy || loading} onClick={retry}>
        다시 시도
      </Button>
    </div>
  );
}
