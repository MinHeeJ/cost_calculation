import { request } from "../../api/client";
import type { Health, MenuNode, MonthClose } from "../../api/types";

export const getMenus = () => request<MenuNode[]>("/api/menus");
export const getMonthClose = (from: string, to: string) =>
  request<MonthClose[]>(
    `/api/month-close?${new URLSearchParams({ from, to })}`,
  );
export const getHealth = () => request<Health>("/api/health");

export function flattenMenus(nodes: MenuNode[]): MenuNode[] {
  return nodes.flatMap((node) => [node, ...flattenMenus(node.children)]);
}
