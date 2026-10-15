package com.sicost.persistence;

import com.sicost.common.CurrentUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 후속 쓰기 어댑터의 감사 입력 자리. DB trigger가 updated_at 최종값을 소유한다. */
@Component
public class AuditContext {
    private final CurrentUser currentUser;

    public AuditContext(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public Long actorId() {
        return currentUser.userId();
    }

    public LocalDateTime updatedAt() {
        return LocalDateTime.now(Clock.system(ZoneId.of("Asia/Seoul")));
    }
}
