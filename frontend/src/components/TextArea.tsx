import { useState, type TextareaHTMLAttributes } from "react";
import styles from "./Common.module.css";
export function TextArea({
  maxLength,
  value,
  onChange,
  ...props
}: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const [local, setLocal] = useState("");
  const text = String(value ?? local);
  return (
    <div>
      <textarea
        {...props}
        className={styles.input}
        style={{ height: "auto" }}
        maxLength={maxLength}
        value={text}
        onChange={(event) => {
          const next =
            maxLength == null
              ? event.target.value
              : event.target.value.slice(0, maxLength);
          event.target.value = next;
          setLocal(next);
          onChange?.(event);
        }}
      />
      {maxLength != null && (
        <div className={styles.counter}>
          {text.length}/{maxLength}
        </div>
      )}
    </div>
  );
}
