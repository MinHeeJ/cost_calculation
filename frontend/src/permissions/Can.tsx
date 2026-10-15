import type { ReactNode } from "react";
import { can } from "./permissions";

export function Can({ perm, children }: { perm: string; children: ReactNode }) {
  return can(perm) ? <>{children}</> : null;
}
