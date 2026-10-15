import { useState } from "react";
import { Star } from "lucide-react";
import { Button } from "../../components/Button";
import { useShortcuts } from "./preferences";
import styles from "./Shared.module.css";

export function TitleBar({
  screenId,
  name,
  groupName,
}: {
  screenId: string;
  name: string;
  groupName?: string;
}) {
  const { favorites, toggleFavorite } = useShortcuts();
  const [limit, setLimit] = useState(false);
  const favorite = favorites.includes(screenId);
  return (
    <header className={styles.title}>
      <div className={styles.breadcrumb}>
        {groupName ? `${groupName} › ${name}` : name}
      </div>
      <div className={styles.titleRow}>
        <h1 className={styles.heading}>{name}</h1>
        <Button
          aria-label={favorite ? "즐겨찾기 해제" : "즐겨찾기 추가"}
          aria-pressed={favorite}
          onClick={() => setLimit(!toggleFavorite(screenId))}
        >
          <Star
            size={18}
            aria-hidden="true"
            fill={favorite ? "currentColor" : "none"}
          />
        </Button>
        <span className={styles.screenId}>{screenId}</span>
      </div>
      {limit && (
        <p role="status" className={styles.notice}>
          즐겨찾기는 최대 10개입니다. 기존 항목을 제거해 주세요.
        </p>
      )}
    </header>
  );
}
