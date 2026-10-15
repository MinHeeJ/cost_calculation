import { useEffect, useState, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { App } from "../../app/App";
import { RequestState } from "../../components/RequestState";
import { getMenus, flattenMenus } from "./api";
import { useRequest } from "./useRequest";
import { useNavigation } from "./navigation";
import { useShortcuts } from "./preferences";
import { screenById } from "./registry";
import { Sidebar } from "./Sidebar";
import { ScreenTabs } from "./ScreenTabs";
import { PlaceholderScreen } from "./PlaceholderScreen";
import {
  UnsavedChangesGuard,
  useNavigationConfirmation,
} from "./UnsavedChangesGuard";

/** Mount once from wiring's route registry; no business screens or additional route aliases are registered here. */
export function SharedWorkspace({
  screenBodies = {},
}: {
  screenBodies?: Record<string, ReactNode>;
}) {
  const result = useRequest(getMenus, "menus");
  const navigate = useNavigate();
  const location = useLocation();
  const { confirm, dialog } = useNavigationConfirmation();
  const active = useNavigation((state) => state.active);
  const [mobile, setMobile] = useState(window.innerWidth <= 767);
  useEffect(() => {
    const resize = () => setMobile(window.innerWidth <= 767);
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);
  async function open(id: string) {
    if (await useNavigation.getState().open(id, confirm)) {
      useShortcuts.getState().visit(id);
      navigate(`/${id}`);
    }
  }
  async function close(id: string) {
    if (await useNavigation.getState().close(id, confirm)) {
      const destination = useNavigation.getState().active;
      if (destination !== active) useShortcuts.getState().visit(destination);
      navigate(`/${destination}`);
    }
  }
  useEffect(() => {
    const id = location.pathname === "/" ? "UI-16" : location.pathname.slice(1);
    if (!screenById(id)) return;
    if (id !== useNavigation.getState().active) void open(id);
    else {
      useShortcuts.getState().visit(id);
      if (location.pathname === "/") navigate("/UI-16", { replace: true });
    }
    // Explicit workspace links call open before navigating; direct entry initializes the same tab state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);
  const menus = result.data ?? [];
  const nodes = flattenMenus(menus);
  const screen = screenById(active);
  const node = nodes.find((menu) => menu.screenId === active);
  const group = menus.find((menu) =>
    flattenMenus(menu.children).some((child) => child.screenId === active),
  );
  return (
    <div
      onClickCapture={(event) => {
        const link = (event.target as HTMLElement).closest("a");
        const id = link?.getAttribute("href")?.slice(1);
        if (id && screenById(id)) {
          event.preventDefault();
          event.stopPropagation();
          void open(id);
        }
      }}
    >
      <App
        sidebar={
          <>
            <RequestState
              loading={result.loading}
              error={result.error}
              empty={!menus.length}
              emptyReason="사용 중인 메뉴가 없습니다."
              onRetry={result.retry}
            />
            {!!menus.length && (
              <Sidebar
                menus={menus}
                active={active}
                mobile={mobile}
                onOpen={open}
              />
            )}
          </>
        }
        tabs={<ScreenTabs menus={nodes} onOpen={open} onClose={close} />}
      >
        {screen && node ? (
          <PlaceholderScreen
            screen={screen}
            name={node.name}
            groupName={group?.name}
            mobile={mobile}
            onDashboard={() => open("UI-16")}
          >
            {screenBodies[screen.screenId]}
          </PlaceholderScreen>
        ) : (
          <RequestState
            loading={result.loading}
            error={result.error}
            empty={!result.loading && !result.error}
            emptyReason="사용 중인 화면을 찾을 수 없습니다."
            onRetry={result.retry}
          />
        )}
        <UnsavedChangesGuard />
        {dialog}
      </App>
    </div>
  );
}
