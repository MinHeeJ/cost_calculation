package com.sicost.partners;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A feature-only clock keeps all relationship counts on the same Seoul business date. */
@Configuration("partnerCatalogTimeConfiguration")
public class PartnerCatalogClock {
    @Bean("partnerCatalogClock")
    public Clock partnerCatalogClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
