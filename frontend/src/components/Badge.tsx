import type { ReactNode } from "react";
import styles from "./Common.module.css";
export type BadgeTone = "success" | "warning" | "danger" | "info" | "locked";
export function Badge({
  tone = "info",
  children,
}: {
  tone?: BadgeTone;
  children: ReactNode;
}) {
  return (
    <span
      className={`${styles.badge} ${tone === "danger" ? styles.dangerBadge : styles[tone]}`}
    >
      {children}
    </span>
  );
}
