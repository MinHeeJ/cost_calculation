import type { ReactNode } from "react";
import { Button } from "./Button";
import styles from "./Common.module.css";
export function SearchBar({
  children,
  onSearch,
  onReset,
  busy = false,
}: {
  children: ReactNode;
  onSearch: () => void;
  onReset: () => void;
  busy?: boolean;
}) {
  return (
    <form
      className={styles.search}
      onSubmit={(event) => {
        event.preventDefault();
        if (!busy) onSearch();
      }}
    >
      {children}
      <div className={styles.searchActions}>
        <Button onClick={onReset} disabled={busy}>
          초기화
        </Button>
        <Button type="submit" variant="primary" busy={busy}>
          조회
        </Button>
      </div>
    </form>
  );
}
