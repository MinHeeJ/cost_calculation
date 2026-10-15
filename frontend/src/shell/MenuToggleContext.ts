import { createContext } from "react";

/** Shared shell toggle keeps the header and sidebar controls on the same breakpoint state. */
export const MenuToggleContext = createContext<(() => void) | null>(null);
