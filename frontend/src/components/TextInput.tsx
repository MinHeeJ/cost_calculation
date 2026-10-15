import { useState, type InputHTMLAttributes } from "react";
import styles from "./Common.module.css";

export function TextInput({
  maxLength,
  value,
  onChange,
  ...props
}: InputHTMLAttributes<HTMLInputElement>) {
  const [local, setLocal] = useState("");
  const [error, setError] = useState("");
  const text = String(value ?? local);
  return (
    <div>
      <input
        {...props}
        value={text}
        maxLength={maxLength}
        className={styles.input}
        aria-invalid={!!error}
        onChange={(event) => {
          const limited =
            maxLength == null
              ? event.target.value
              : event.target.value.slice(0, maxLength);
          event.target.value = limited;
          setLocal(limited);
          onChange?.(event);
        }}
        onBlur={(event) => {
          setError(
            event.target.validity.valid
              ? ""
              : "필수 항목과 입력 형식을 확인해 주세요.",
          );
          props.onBlur?.(event);
        }}
        onInvalid={() => setError("필수 항목과 입력 형식을 확인해 주세요.")}
      />
      {maxLength != null && (
        <div className={styles.counter}>
          {text.length}/{maxLength}
        </div>
      )}
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
    </div>
  );
}
