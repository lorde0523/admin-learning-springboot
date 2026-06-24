package com.example.admin.sqltrace.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlParameterRendererTests {

    private final SqlParameterRenderer renderer =
            new SqlParameterRenderer(new NoOpSqlValueMasker());

    @Test
    void replacesParametersWithoutTouchingQuotedOrCommentQuestionMarks() {
        String sql = """
                select * from admin_menu
                where menu_name = ?
                  and note = 'literal ?'
                  and enabled = ?
                  -- ignored ?
                  and id = ?
                  /* ignored ? */
                """;

        String rendered = renderer.render(sql, Map.of(
                1, "O'Brien",
                2, true,
                3, 42L));

        assertThat(rendered).contains("menu_name = 'O''Brien'");
        assertThat(rendered).contains("note = 'literal ?'");
        assertThat(rendered).contains("enabled = true");
        assertThat(rendered).contains("-- ignored ?");
        assertThat(rendered).contains("id = 42");
        assertThat(rendered).contains("/* ignored ? */");
    }

    @Test
    void rendersNullTemporalBinaryAndStreamValues() {
        Map<Integer, Object> values = new HashMap<>();
        values.put(1, null);
        values.put(2, LocalDate.of(2026, 6, 25));
        values.put(3, new byte[3]);
        values.put(4, new ByteArrayInputStream(new byte[] {1}));

        assertThat(renderer.render("select ?, ?, ?, ?", values))
                .isEqualTo("select NULL, '2026-06-25', '<BINARY length=3>', '<STREAM>'");
    }

    @Test
    void leavesUnboundPlaceholdersVisible() {
        assertThat(renderer.render("select * from t where a = ? and b = ?", Map.of(1, "x")))
                .isEqualTo("select * from t where a = 'x' and b = ?");
    }

    @Test
    void delegatesEachBoundValueToMasker() {
        SqlParameterRenderer maskingRenderer = new SqlParameterRenderer(
                (sql, parameterIndex, value) -> parameterIndex == 1 ? "***" : value);

        assertThat(maskingRenderer.render("select ?, ?", Map.of(1, "secret", 2, 7)))
                .isEqualTo("select '***', 7");
    }

    @Test
    void rendersLegacyDateAndTimestampAsIsoValues() {
        java.util.Date date = java.util.Date.from(Instant.parse("2026-06-25T05:20:31Z"));
        Timestamp timestamp = Timestamp.from(Instant.parse("2026-06-25T05:20:31.123Z"));

        assertThat(renderer.render("select ?, ?", Map.of(1, date, 2, timestamp)))
                .isEqualTo("select '2026-06-25T05:20:31Z', '2026-06-25T05:20:31.123Z'");
    }

    @Test
    void ignoresQuestionMarksInsideOracleAlternativeQuotedLiterals() {
        String sql = "select q'[What's ?]' as note from dual where id = ?";

        assertThat(renderer.render(sql, Map.of(1, 7)))
                .isEqualTo("select q'[What's ?]' as note from dual where id = 7");
    }
}
