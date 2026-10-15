export interface Meta {
  requestId: string;
}
export interface ApiResponse<T> {
  success: true;
  data: T;
  meta: Meta;
}
export interface ApiError {
  success: false;
  code: string;
  message: string;
  field: string | null;
  requestId: string;
  meta: Meta;
}
export interface MenuNode {
  menuId: number;
  name: string;
  screenId: string | null;
  icon: string | null;
  mobile: boolean;
  children: MenuNode[];
}
export interface MonthClose {
  month: string;
  status: "OPEN" | "CLOSED";
}
export interface Health {
  status: "UP";
}
export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: 20 | 50 | 100;
  sort: string;
}
