import { useState } from "react";
import { Button } from "../../components/Button";
import { RequestState } from "../../components/RequestState";
import { useRequest } from "../shared/useRequest";
import { getPartnerAssignments, getPartnerHistory } from "./api";
import styles from "./PartnerActivity.module.css";

export type PartnerActivityTab = "assignments" | "history";

/** Embedded in the existing /UI-15 drawer; these regions never create a new route or domain data. */
export function PartnerActivityScreen({
  partnerId,
  initialTab = "assignments",
  revision = 0,
}: {
  partnerId: number;
  initialTab?: PartnerActivityTab;
  revision?: number;
}) {
  const [tab, setTab] = useState<PartnerActivityTab>(initialTab);
  return (
    <section className={styles.screen} data-testid="partner-activity-screen">
      <div className={styles.tabs} role="tablist" aria-label="거래처 활동">
        <Button
          role="tab"
          aria-selected={tab === "assignments"}
          data-testid="PTNR-ASSIGNMENTS"
          onClick={() => setTab("assignments")}
        >
          투입 내역
        </Button>
        <Button
          role="tab"
          aria-selected={tab === "history"}
          data-testid="PTNR-HISTORY"
          onClick={() => setTab("history")}
        >
          변경 이력
        </Button>
      </div>
      <PartnerActivityPanel
        partnerId={partnerId}
        tab={tab}
        revision={revision}
      />
    </section>
  );
}

/** Controlled alternative for the host's existing basic-information/assignments/history tab bar. */
export function PartnerActivityPanel({
  partnerId,
  tab,
  revision = 0,
}: {
  partnerId: number;
  tab: PartnerActivityTab;
  revision?: number;
}) {
  return tab === "assignments" ? (
    <Assignments
      key={`assignments-${partnerId}`}
      id={partnerId}
      revision={revision}
    />
  ) : (
    <History key={`history-${partnerId}`} id={partnerId} revision={revision} />
  );
}

function Assignments({ id, revision }: { id: number; revision: number }) {
  const state = useRequest(
    () => getPartnerAssignments(id),
    `assignments-${id}-${revision}`,
  );
  return (
    <div
      role="tabpanel"
      className={styles.pane}
      data-testid="partner-assignments-panel"
      aria-label="투입 내역"
    >
      <RequestState
        loading={state.loading}
        error={state.error}
        empty={state.data?.length === 0}
        emptyReason="지금 투입 중인 내역이 없습니다."
        onRetry={state.retry}
      >
        <div className={styles.tableScroll}>
          <table>
            <caption className={styles.srOnly}>현재 투입 내역</caption>
            <thead>
              <tr>
                <th>프로젝트</th>
                <th>이름</th>
                <th>소속</th>
                <th>직무</th>
                <th>투입률</th>
                <th>철수일</th>
              </tr>
            </thead>
            <tbody>
              {state.data?.map((row, index) => (
                <tr key={index} data-testid={`partner-assignment-row-${index}`}>
                  <td>{row.projectName}</td>
                  <td>{row.developerName}</td>
                  <td>{row.affiliationName}</td>
                  <td>{row.jobName}</td>
                  <td className={styles.number}>{row.assignmentRate}</td>
                  <td>{row.endDate}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </RequestState>
    </div>
  );
}

function History({ id, revision }: { id: number; revision: number }) {
  const state = useRequest(
    () => getPartnerHistory(id),
    `history-${id}-${revision}`,
  );
  return (
    <div
      role="tabpanel"
      className={styles.pane}
      data-testid="partner-history-panel"
      aria-label="변경 이력"
    >
      <RequestState
        loading={state.loading}
        error={state.error}
        empty={state.data?.length === 0}
        emptyReason="아직 바꾼 기록이 없습니다."
        onRetry={state.retry}
      >
        <div className={styles.tableScroll}>
          <table>
            <caption className={styles.srOnly}>거래처 변경 이력</caption>
            <thead>
              <tr>
                <th>일시</th>
                <th>항목</th>
                <th>바꾸기 전</th>
                <th>바꾼 뒤</th>
                <th>처리자</th>
              </tr>
            </thead>
            <tbody>
              {state.data?.map((row, index) => (
                <tr key={index} data-testid={`partner-history-row-${index}`}>
                  <td>{row.chgAt.replace("T", " ")}</td>
                  <td>{row.clmnNm ?? row.dataChgTyCd}</td>
                  <td>{row.bfrVal ?? "–"}</td>
                  <td>{row.aftVal ?? "–"}</td>
                  <td>{row.chgUserId ?? "기본 사용자"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </RequestState>
    </div>
  );
}
