package com.sicost.shared;

import com.sicost.shared.health.HealthController;
import org.springframework.core.io.ClassPathResource;
import java.util.Map;
import com.sicost.common.GlobalExceptionHandler;
import com.sicost.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthContractTest {
    @Test
    void publicGetHealthReturnsSingleUpPayloadAndConsistentRequestId() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController())
            .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            Map<String, Object> contract = new org.yaml.snakeyaml.Yaml().load(input);
            var paths = (Map<?, ?>) contract.get("paths");
            var operation = (Map<?, ?>) ((Map<?, ?>) paths.get("/api/health")).get("get");
            assertThat(operation.get("operationId")).isEqualTo("getHealth");
        }
        var result = mvc.perform(get("/api/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.items").doesNotExist()).andReturn();
        var body = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertThat(result.getResponse().getHeader("X-Request-Id")).isNotBlank()
            .isEqualTo(body.path("meta").path("requestId").asText());
    }
}
