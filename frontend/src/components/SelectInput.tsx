import type { SelectHTMLAttributes } from "react";
import styles from "./Common.module.css";
export function SelectInput(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={styles.input} />;
}
