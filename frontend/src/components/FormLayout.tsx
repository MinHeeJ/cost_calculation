import { type FormHTMLAttributes, type ReactNode } from "react";
import styles from "./Common.module.css";

export function FormLayout({
  children,
  onSubmit,
  actions,
  ...props
}: FormHTMLAttributes<HTMLFormElement> & { actions?: ReactNode }) {
  return (
    <form
      {...props}
      className={styles.form}
      noValidate
      onSubmit={(event) => {
        const fields = Array.from(event.currentTarget.elements).filter(
          (field): field is HTMLInputElement =>
            field instanceof HTMLInputElement ||
            field instanceof HTMLSelectElement ||
            field instanceof HTMLTextAreaElement,
        );
        const invalid = fields.filter((field) => !field.checkValidity());
        if (invalid.length) {
          event.preventDefault();
          invalid[0].focus();
          return;
        }
        onSubmit?.(event);
      }}
    >
      {children}
      {actions && <div className={styles.formActions}>{actions}</div>}
    </form>
  );
}
