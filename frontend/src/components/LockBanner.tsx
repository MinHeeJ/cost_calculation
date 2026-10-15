import { LockKeyhole } from "lucide-react";
import styles from "./Common.module.css";
export function LockBanner({
  month,
  children,
}: {
  month?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className={styles.lockBanner} role="status">
      <LockKeyhole aria-hidden="true" size={16} />
      {children ??
        `${month}는 마감되었습니다. 고칠 내용은 다음 달 전월 조정으로 입력하세요.`}
    </div>
  );
}
