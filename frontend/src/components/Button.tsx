import {
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type MouseEvent,
} from "react";
import { LoaderCircle } from "lucide-react";
import styles from "./Common.module.css";

export interface ButtonProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "onClick"> {
  variant?: "primary" | "secondary" | "danger";
  size?: "normal" | "small";
  busy?: boolean;
  onClick?: (
    event: MouseEvent<HTMLButtonElement>,
  ) => unknown | Promise<unknown>;
}
export function Button({
  variant = "secondary",
  size = "normal",
  busy = false,
  onClick,
  children,
  className = "",
  disabled,
  type = "button",
  ...props
}: ButtonProps) {
  const [pending, setPending] = useState(false);
  const running = useRef(false);
  async function click(event: MouseEvent<HTMLButtonElement>) {
    if (running.current || busy || disabled || !onClick) return;
    running.current = true;
    setPending(true);
    try {
      await onClick?.(event);
    } finally {
      running.current = false;
      setPending(false);
    }
  }
  return (
    <button
      {...props}
      type={type}
      className={`${styles.button} ${styles[variant]} ${size === "small" ? styles.small : ""} ${className}`}
      disabled={disabled || busy || pending}
      aria-busy={busy || pending}
      onClick={click}
    >
      {(busy || pending) && (
        <LoaderCircle
          aria-label="처리 중"
          aria-hidden="true"
          className={styles.spinner}
          size={16}
        />
      )}
      {children}
    </button>
  );
}
