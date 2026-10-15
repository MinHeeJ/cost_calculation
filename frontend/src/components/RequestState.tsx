import type { ReactNode } from "react";
import type { RequestError } from "../api/client";
import { ErrorBanner } from "./ErrorBanner";
import { Button } from "./Button";
import styles from "./Common.module.css";
export function RequestState({
  loading,
  error,
  empty,
  emptyReason,
  emptyAction,
  onRetry,
  children,
}: {
  loading?: boolean;
  error?: RequestError | null;
  empty?: boolean;
  emptyReason?: string;
  emptyAction?: { label: string; onClick: () => void };
  onRetry: () => void | Promise<unknown>;
  children?: ReactNode;
}) {
  if (error)
    return <ErrorBanner error={error} onRetry={onRetry} loading={loading} />;
  if (loading)
    return (
      <div
        role="status"
        aria-label="불러오는 중"
        className={styles.requestState}
      >
        {[0, 1, 2].map((index) => (
          <div key={index} className={styles.skeleton} />
        ))}
      </div>
    );
  if (empty)
    return (
      <div className={styles.requestState}>
        <p>{emptyReason ?? "조회 결과가 없습니다."}</p>
        {emptyAction && (
          <Button onClick={emptyAction.onClick}>{emptyAction.label}</Button>
        )}
      </div>
    );
  return <>{children}</>;
}
