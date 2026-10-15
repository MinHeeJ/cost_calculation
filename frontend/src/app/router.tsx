import { useEffect, useRef } from "react";
import { Link, Navigate, useBlocker, type RouteObject } from "react-router-dom";
import { SharedWorkspace } from "../pages/shared/SharedWorkspace";
import { useNavigation } from "../pages/shared/navigation";
import { screenById, sharedRoutes } from "../pages/shared/registry";
import { useNavigationConfirmation } from "../pages/shared/UnsavedChangesGuard";

/** Keep browser history transitions behind the workspace's atomic confirmation port. */
function WorkspaceRoute() {
  const { confirm, dialog } = useNavigationConfirmation();
  const pending = useRef<string>();
  const blocker = useBlocker(({ nextLocation }) => {
    const id =
      nextLocation.pathname === "/" ? "UI-16" : nextLocation.pathname.slice(1);
    return !!screenById(id) && id !== useNavigation.getState().active;
  });

  useEffect(() => {
    if (blocker.state !== "blocked") {
      pending.current = undefined;
      return;
    }
    if (pending.current === blocker.location.key) return;
    pending.current = blocker.location.key;
    const id =
      blocker.location.pathname === "/"
        ? "UI-16"
        : blocker.location.pathname.slice(1);
    void useNavigation
      .getState()
      .open(id, confirm)
      .then((accepted) => {
        if (accepted) blocker.proceed();
        else blocker.reset();
      });
  }, [blocker, confirm]);

  return (
    <>
      <SharedWorkspace />
      {dialog}
    </>
  );
}

/** Only canonical screen paths are routable; design aliases are not product URLs. */
export const applicationRoutes: RouteObject[] = [
  {
    element: <WorkspaceRoute />,
    children: [
      { path: "/", element: <Navigate to="/UI-16" replace /> },
      ...sharedRoutes.map((path) => ({ path, element: null })),
    ],
  },
  {
    path: "*",
    element: (
      <main>
        <h1>화면을 찾을 수 없습니다.</h1>
        <Link to="/UI-16">대시보드로</Link>
      </main>
    ),
  },
];
