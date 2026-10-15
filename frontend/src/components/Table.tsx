import type { Key, ReactNode } from "react";
import styles from "./Common.module.css";
import typography from "../styles/typography.module.css";

export interface TableColumn<T> {
  key: string;
  label: ReactNode;
  render: (row: T) => ReactNode;
  kind?: "text" | "number" | "code" | "status" | "date";
  sensitive?: boolean;
}
export function Table<T>({
  columns,
  rows,
  rowKey,
  total,
  caption,
}: {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => Key;
  total: number;
  caption?: string;
}) {
  return (
    <section>
      <p>총 {total.toLocaleString("ko-KR")}건</p>
      <div className={styles.tableContainer}>
        <table className={styles.table}>
          {caption && <caption>{caption}</caption>}
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  className={typography[column.kind ?? "text"]}
                >
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={typography[column.kind ?? "text"]}
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
