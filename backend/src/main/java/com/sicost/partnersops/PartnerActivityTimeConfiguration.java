package com.sicost.partnersops;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Feature-local injectable Seoul clock; no environment or service is required. */
@Configuration
public class PartnerActivityTimeConfiguration {
    @Bean("partnerActivityClock")
    public Clock partnerActivityClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
