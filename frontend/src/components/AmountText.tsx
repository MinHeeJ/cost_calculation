import { formatAmount } from "../lib/format";
import styles from "../styles/typography.module.css";

export function AmountText({
  value,
}: {
  value: number | bigint | null | undefined;
}) {
  return (
    <span
      className={`${styles.number} ${value != null && value < 0 ? styles.negative : ""}`}
    >
      {formatAmount(value)}
    </span>
  );
}
