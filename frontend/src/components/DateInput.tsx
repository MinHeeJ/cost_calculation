import type { InputHTMLAttributes } from "react";
import styles from "./Common.module.css";
export function DateInput(
  props: Omit<InputHTMLAttributes<HTMLInputElement>, "type">,
) {
  return <input {...props} type="date" className={styles.input} />;
}
