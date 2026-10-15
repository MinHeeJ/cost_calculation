package com.sicost.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class MigrationSourceTest {
    private byte[] bytes(String path) throws Exception {
        return new ClassPathResource(path).getContentAsByteArray();
    }

    @Test
    void fixedOriginalDiffersOnlyByOneRemovedDropLineAndMigrationsAreImmutable() throws Exception {
        String v1 = new String(bytes("db/migration/V1__schema.sql"), StandardCharsets.UTF_8);
        var lines = new java.util.ArrayList<>(v1.lines().toList());
        lines.add(25, "DROP SCHEMA IF EXISTS ai, fin, bill, cst, hr, asgn, prj, bsn, mst, sys, cmn CASCADE;");
        String restored = String.join("\n", lines) + "\n";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(restored.getBytes(StandardCharsets.UTF_8)));
        assertThat(hash).isEqualTo("f654190eb098a554f24d79a43aae594f1b2ca2ac7df957c0af62a0c90931a32a");
        var provenance = new ObjectMapper().readTree(bytes("db/source-provenance.json"));
        for (String filename : new String[] {"V1__schema.sql", "V2__seed_month_close.sql"}) {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bytes("db/migration/" + filename)));
            assertThat(actual).isEqualTo(provenance.path("migration_sha256").path(filename).asText());
        }
    }
}
