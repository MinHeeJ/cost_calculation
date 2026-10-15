import { useEffect, useRef, useState, type InputHTMLAttributes } from "react";
import styles from "./Common.module.css";

type Props = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  "value" | "onChange" | "type"
> & {
  value: number | null;
  onChange: (value: number | null) => void;
};
export function parseAmount(raw: string): number | null | undefined {
  const clean = raw.replace(/[,\s원]/g, "");
  if (!clean) return null;
  if (!/^\d+$/.test(clean)) return undefined;
  const number = Number(clean);
  return Number.isSafeInteger(number) && number <= 999999999999
    ? number
    : undefined;
}
export function AmountInput({ value, onChange, required, ...props }: Props) {
  const [raw, setRaw] = useState(value?.toLocaleString("ko-KR") ?? "");
  const [touched, setTouched] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const parsed = parseAmount(raw);
  const invalid = parsed === undefined || (required && parsed === null);
  const message = invalid
    ? "0~999,999,999,999 사이의 정수 금액을 입력해 주세요."
    : "";
  useEffect(() => {
    setRaw(value?.toLocaleString("ko-KR") ?? "");
  }, [value]);
  useEffect(() => {
    input.current?.setCustomValidity(message);
  }, [message]);
  return (
    <div>
      <input
        {...props}
        ref={input}
        required={required}
        className={`${styles.input} ${styles.amount}`}
        inputMode="numeric"
        value={raw}
        aria-invalid={touched && !!invalid}
        onBlur={() => setTouched(true)}
        onInvalid={() => setTouched(true)}
        onChange={(event) => {
          const next = parseAmount(event.target.value);
          setRaw(
            next === undefined
              ? event.target.value
              : (next?.toLocaleString("ko-KR") ?? ""),
          );
          if (next !== undefined) onChange(next);
        }}
      />
      {touched && message && (
        <p className={styles.error} role="alert">
          {message}
        </p>
      )}
    </div>
  );
}
