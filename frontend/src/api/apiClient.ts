/** Public operation registry; feature adapters depend on client.ts, never on this registry. */
export { request, api, RequestError } from "./client";
export { getMenus, getMonthClose, getHealth } from "../pages/shared/api";
