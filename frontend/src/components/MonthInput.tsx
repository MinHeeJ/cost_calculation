import type { InputHTMLAttributes } from "react";
import styles from "./Common.module.css";
export function MonthInput(
  props: Omit<InputHTMLAttributes<HTMLInputElement>, "type">,
) {
  return <input {...props} type="month" className={styles.input} />;
}
