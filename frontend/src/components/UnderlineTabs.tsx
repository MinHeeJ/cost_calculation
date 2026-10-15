import type { ReactNode } from "react";
import styles from "./Common.module.css";
export function UnderlineTabs({
  tabs,
  value,
  onChange,
}: {
  tabs: { id: string; label: ReactNode }[];
  value: string;
  onChange: (value: string) => void;
}) {
  if (tabs.length > 5) throw new Error("밑줄 탭은 최대 5개입니다.");
  return (
    <div role="tablist" className={styles.underlineTabs}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          aria-selected={value === tab.id}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
