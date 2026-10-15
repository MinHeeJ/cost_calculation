import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Bell, ChevronDown, Menu, UserRound } from "lucide-react";
import { usePreferences } from "../stores/preferences";
import { Button } from "../components/Button";
import styles from "./Shell.module.css";

export function Header({ onToggleMenu }: { onToggleMenu: () => void }) {
  const [open, setOpen] = useState<"notifications" | "user" | null>(null);
  const actions = useRef<HTMLDivElement>(null);
  const { pageSize, setPageSize } = usePreferences();
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!actions.current?.contains(event.target as Node)) setOpen(null);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      actions.current
        ?.querySelector<HTMLButtonElement>('[aria-expanded="true"]')
        ?.focus();
      setOpen(null);
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);
  return (
    <header className={styles.header}>
      <Button
        aria-label="메뉴 열기"
        className={styles.iconButton}
        onClick={onToggleMenu}
      >
        <Menu size={20} aria-hidden="true" />
      </Button>
      <Link
        to="/UI-16"
        className={styles.brand}
        aria-label="프로젝트·원가 관리"
      >
        <span className={styles.logo} aria-hidden="true">
          SI
        </span>
        <span>프로젝트·원가 관리</span>
      </Link>
      <div className={styles.headerActions} ref={actions}>
        <Button
          aria-label="알림"
          aria-expanded={open === "notifications"}
          aria-controls={
            open === "notifications" ? "header-popover" : undefined
          }
          className={styles.iconButton}
          onClick={() =>
            setOpen(open === "notifications" ? null : "notifications")
          }
        >
          <Bell size={20} aria-hidden="true" />
        </Button>
        <Button
          aria-label="기본 사용자 설정"
          aria-expanded={open === "user"}
          aria-controls={open === "user" ? "header-popover" : undefined}
          className={styles.userButton}
          onClick={() => setOpen(open === "user" ? null : "user")}
        >
          <UserRound size={18} aria-hidden="true" />
          <span className={styles.userName}>기본 사용자</span>
          <ChevronDown
            size={16}
            className={styles.userChevron}
            aria-hidden="true"
          />
        </Button>
        {open && (
          <section
            id="header-popover"
            className={styles.popover}
            aria-label={open === "user" ? "사용자 설정" : "알림 목록"}
          >
            <h2>{open === "user" ? "사용자 설정" : "알림"}</h2>
            {open === "notifications" ? (
              <p className={styles.notificationEmpty}>
                <Bell size={24} aria-hidden="true" />새 알림이 없습니다
              </p>
            ) : (
              <div className={styles.settingField}>
                <label htmlFor="default-page-size">목록 기본 건수</label>
                <select
                  id="default-page-size"
                  value={pageSize}
                  onChange={(event) =>
                    setPageSize(Number(event.target.value) as 20 | 50 | 100)
                  }
                >
                  {[20, 50, 100].map((size) => (
                    <option key={size} value={size}>
                      {size}건
                    </option>
                  ))}
                </select>
              </div>
            )}
          </section>
        )}
      </div>
    </header>
  );
}
