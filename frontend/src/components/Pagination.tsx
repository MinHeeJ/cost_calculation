import { Button } from "./Button";
import styles from "./Common.module.css";
export type PageSize = 20 | 50 | 100;
export function Pagination({
  total,
  page,
  size,
  onPageChange,
  onSizeChange,
}: {
  total: number;
  page: number;
  size: PageSize;
  onPageChange: (page: number) => void;
  onSizeChange: (size: PageSize) => void;
}) {
  const pages = Math.max(1, Math.ceil(total / size));
  const start = Math.floor((page - 1) / 10) * 10 + 1;
  const numbers = Array.from(
    { length: Math.min(10, pages - start + 1) },
    (_, i) => start + i,
  );
  return (
    <nav aria-label="목록 페이지" className={styles.pagination}>
      <span>
        {total === 0 ? 0 : (page - 1) * size + 1}-{Math.min(page * size, total)}{" "}
        / {total}건
      </span>
      <select
        aria-label="페이지당 건수"
        value={size}
        onChange={(event) => {
          onSizeChange(Number(event.target.value) as PageSize);
          onPageChange(1);
        }}
      >
        {[20, 50, 100].map((value) => (
          <option key={value} value={value}>
            {value}건씩
          </option>
        ))}
      </select>
      <Button
        aria-label="처음 페이지"
        disabled={page <= 1}
        onClick={() => onPageChange(1)}
      >
        «
      </Button>
      <Button
        aria-label="이전 페이지"
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
      >
        ‹
      </Button>
      {numbers.map((number) => (
        <Button
          key={number}
          aria-current={page === number ? "page" : undefined}
          onClick={() => onPageChange(number)}
        >
          {number}
        </Button>
      ))}
      <Button
        aria-label="다음 페이지"
        disabled={page >= pages}
        onClick={() => onPageChange(page + 1)}
      >
        ›
      </Button>
      <Button
        aria-label="마지막 페이지"
        disabled={page >= pages}
        onClick={() => onPageChange(pages)}
      >
        »
      </Button>
    </nav>
  );
}
