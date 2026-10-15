import { useContext, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  Calendar,
  ChartNoAxesCombined,
  ChevronDown,
  ChevronsLeft,
  ChevronsRight,
  Database,
  Folder,
  Grid2X2,
  LockKeyhole,
  Receipt,
  Settings,
  Sparkles,
  Wallet,
} from "lucide-react";
import type { MenuNode } from "../../api/types";
import { MenuToggleContext } from "../../shell/MenuToggleContext";
import { Button } from "../../components/Button";
import { usePreferences } from "../../stores/preferences";
import { flattenMenus } from "./api";
import { Favorites } from "./Favorites";
import { RecentScreens } from "./RecentScreens";
import { screenById } from "./registry";
import styles from "./Shared.module.css";

const icons = {
  grid: Grid2X2,
  db: Database,
  folder: Folder,
  calendar: Calendar,
  receipt: Receipt,
  chart: ChartNoAxesCombined,
  wallet: Wallet,
  lock: LockKeyhole,
  sparkle: Sparkles,
  gear: Settings,
};
export function Sidebar({
  menus,
  active,
  mobile = false,
  collapsed: supplied,
  onOpen,
}: {
  menus: MenuNode[];
  active: string;
  mobile?: boolean;
  collapsed?: boolean;
  onOpen: (id: string) => unknown;
}) {
  const toggleShellMenu = useContext(MenuToggleContext);
  const root = useRef<HTMLElement>(null);
  const [narrow, setNarrow] = useState(false);
  const collapsed = supplied ?? narrow;
  const selected = menus.find((menu) =>
    flattenMenus([menu]).some((item) => item.screenId === active),
  );
  const [expanded, setExpanded] = useState<number | null>(
    selected?.menuId ?? null,
  );
  const [flyout, setFlyout] = useState<{ id: number; top: number }>();
  const { setMenuCollapsed } = usePreferences();
  useEffect(() => {
    setExpanded(selected?.menuId ?? null);
  }, [selected?.menuId]);
  useEffect(() => {
    if (!root.current || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver((entries) =>
      setNarrow(entries[0].contentRect.width < 100),
    );
    observer.observe(root.current);
    return () => observer.disconnect();
  }, []);
  const allowed = (menu: MenuNode) =>
    !!menu.screenId && !!screenById(menu.screenId) && (!mobile || menu.mobile);
  const leaves = flattenMenus(menus).filter(allowed);
  function link(menu: MenuNode) {
    return (
      <a
        key={menu.menuId}
        href={`/${menu.screenId}`}
        className={styles.row}
        aria-current={active === menu.screenId ? "page" : undefined}
        onClick={(event) => {
          event.preventDefault();
          setFlyout(undefined);
          onOpen(menu.screenId!);
        }}
      >
        {menu.name}
      </a>
    );
  }
  return (
    <nav
      ref={root}
      aria-label="화면 메뉴"
      className={`${styles.navigation} ${collapsed ? styles.collapsed : ""}`}
    >
      {!collapsed && (
        <>
          <Favorites menus={leaves} active={active} onOpen={onOpen} />
          <RecentScreens menus={leaves} active={active} onOpen={onOpen} />
        </>
      )}
      {menus.map((group) => {
        const children = flattenMenus(group.children).filter(allowed);
        if (!children.length && !allowed(group)) return null;
        const Icon = icons[group.icon as keyof typeof icons] ?? Folder;
        const dashboard = children.find((menu) => menu.screenId === "UI-16");
        return (
          <div
            key={group.menuId}
            className={styles.group}
            onMouseEnter={(event) => {
              if (collapsed && !dashboard) {
                setFlyout({
                  id: group.menuId,
                  top: event.currentTarget.getBoundingClientRect().top,
                });
              }
            }}
            onMouseLeave={() => setFlyout(undefined)}
            onBlur={(event) => {
              const next = event.relatedTarget as HTMLElement | null;
              const inFlyout = next
                ?.closest("[data-menu-flyout]")
                ?.getAttribute("data-menu-flyout");
              if (
                !event.currentTarget.contains(next) &&
                inFlyout !== String(group.menuId)
              )
                setFlyout(undefined);
            }}
          >
            <button
              type="button"
              className={styles.row}
              aria-label={group.name}
              aria-current={dashboard?.screenId === active ? "page" : undefined}
              data-active={selected?.menuId === group.menuId || undefined}
              aria-expanded={
                dashboard
                  ? undefined
                  : expanded === group.menuId || flyout?.id === group.menuId
              }
              onFocus={(event) => {
                if (collapsed && !dashboard) {
                  setFlyout({
                    id: group.menuId,
                    top: event.currentTarget.getBoundingClientRect().top,
                  });
                }
              }}
              onClick={() => {
                if (dashboard) onOpen(dashboard.screenId!);
                else if (allowed(group)) onOpen(group.screenId!);
                else
                  setExpanded(expanded === group.menuId ? null : group.menuId);
              }}
            >
              <Icon size={20} aria-hidden="true" />
              {!collapsed && (
                <>
                  <span>{group.name}</span>
                  {!dashboard && <ChevronDown size={16} />}
                </>
              )}
            </button>
            {!collapsed && !dashboard && expanded === group.menuId && (
              <div className={styles.leaves}>{children.map(link)}</div>
            )}
            {collapsed &&
              flyout?.id === group.menuId &&
              createPortal(
                <div
                  className={styles.flyout}
                  data-menu-flyout={group.menuId}
                  style={{
                    top: Math.max(
                      56,
                      Math.min(flyout.top, window.innerHeight - 240),
                    ),
                  }}
                  onMouseEnter={() => setFlyout(flyout)}
                  onMouseLeave={() => setFlyout(undefined)}
                >
                  <strong>{group.name}</strong>
                  {children.map(link)}
                </div>,
                document.body,
              )}
          </div>
        );
      })}
      <div className={styles.collapse}>
        <Button
          aria-label={collapsed ? "메뉴 펼치기" : "메뉴 접기"}
          onClick={() => {
            if (toggleShellMenu) toggleShellMenu();
            else setMenuCollapsed(!collapsed);
          }}
        >
          {collapsed ? <ChevronsRight size={20} /> : <ChevronsLeft size={20} />}
          {!collapsed && "메뉴 접기"}
        </Button>
      </div>
    </nav>
  );
}
