import { useEffect, useRef, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import { MenuToggleContext } from "../shell/MenuToggleContext";
import { Header } from "../shell/Header";
import { usePreferences } from "../stores/preferences";
import styles from "../shell/Shell.module.css";
import "../styles/tokens.css";

/** Shared shell; feature integration supplies navigation, tabs and page content. */
export function App({
  children,
  sidebar,
  tabs,
}: {
  children?: ReactNode;
  sidebar?: ReactNode;
  tabs?: ReactNode;
}) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const main = useRef<HTMLElement>(null);
  const aside = useRef<HTMLElement>(null);
  useEffect(() => {
    setMobileOpen(false);
    if (main.current) main.current.scrollTop = 0;
  }, [location.pathname]);
  useEffect(() => {
    if (!mobileOpen) return;
    aside.current?.querySelector<HTMLElement>("a, button")?.focus();
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileOpen(false);
        document
          .querySelector<HTMLButtonElement>('[aria-label="메뉴 열기"]')
          ?.focus();
      }
    };
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [mobileOpen]);
  const [tabletExpanded, setTabletExpanded] = useState(false);
  const { menuCollapsed, setMenuCollapsed } = usePreferences();
  function toggleMenu() {
    if (window.innerWidth <= 767) setMobileOpen((value) => !value);
    else if (window.innerWidth < 1280) setTabletExpanded((value) => !value);
    else setMenuCollapsed(!menuCollapsed);
  }
  return (
    <div className={styles.app}>
      <Header onToggleMenu={toggleMenu} />
      <div className={styles.body}>
        <aside
          ref={aside}
          className={`${styles.sidebar} ${menuCollapsed ? styles.collapsed : ""}
          ${tabletExpanded ? styles.expanded : ""} ${mobileOpen ? styles.mobileOpen : ""}`}
        >
          <MenuToggleContext.Provider value={toggleMenu}>
            {sidebar}
          </MenuToggleContext.Provider>
        </aside>
        <div
          className={`${styles.scrim} ${mobileOpen ? styles.open : ""}`}
          onClick={() => setMobileOpen(false)}
        />
        <main ref={main} className={styles.main}>
          <div className={styles.tabs}>{tabs}</div>
          <div className={styles.content}>{children}</div>
        </main>
      </div>
    </div>
  );
}
export default App;
