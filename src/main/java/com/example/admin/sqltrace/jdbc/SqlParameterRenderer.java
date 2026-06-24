package com.example.admin.sqltrace.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.time.temporal.TemporalAccessor;
import java.sql.Timestamp;
import java.sql.Time;
import java.util.Date;
import java.util.Map;

public class SqlParameterRenderer {

    private final SqlValueMasker masker;

    public SqlParameterRenderer(SqlValueMasker masker) {
        this.masker = masker;
    }

    public String render(String sql, Map<Integer, Object> values) {
        StringBuilder rendered = new StringBuilder(sql.length() + 32);
        State state = State.NORMAL;
        int parameterIndex = 1;
        char alternativeQuoteEnd = '\0';

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (state == State.NORMAL) {
                if ((current == 'q' || current == 'Q')
                        && next == '\''
                        && index + 2 < sql.length()) {
                    char openingDelimiter = sql.charAt(index + 2);
                    alternativeQuoteEnd = closingDelimiter(openingDelimiter);
                    rendered.append(current).append(next).append(openingDelimiter);
                    index += 2;
                    state = State.ALTERNATIVE_QUOTE;
                    continue;
                } else if (current == '\'') {
                    state = State.SINGLE_QUOTE;
                } else if (current == '"') {
                    state = State.DOUBLE_QUOTE;
                } else if (current == '-' && next == '-') {
                    state = State.LINE_COMMENT;
                } else if (current == '/' && next == '*') {
                    state = State.BLOCK_COMMENT;
                } else if (current == '?') {
                    if (values.containsKey(parameterIndex)) {
                        Object masked = masker.mask(sql, parameterIndex, values.get(parameterIndex));
                        rendered.append(toLiteral(masked));
                    } else {
                        rendered.append(current);
                    }
                    parameterIndex++;
                    continue;
                }
            } else if (state == State.SINGLE_QUOTE && current == '\'') {
                if (next == '\'') {
                    rendered.append(current).append(next);
                    index++;
                    continue;
                }
                state = State.NORMAL;
            } else if (state == State.DOUBLE_QUOTE && current == '"') {
                if (next == '"') {
                    rendered.append(current).append(next);
                    index++;
                    continue;
                }
                state = State.NORMAL;
            } else if (state == State.LINE_COMMENT && (current == '\n' || current == '\r')) {
                state = State.NORMAL;
            } else if (state == State.BLOCK_COMMENT && current == '*' && next == '/') {
                rendered.append(current).append(next);
                index++;
                state = State.NORMAL;
                continue;
            } else if (state == State.ALTERNATIVE_QUOTE
                    && current == alternativeQuoteEnd
                    && next == '\'') {
                rendered.append(current).append(next);
                index++;
                state = State.NORMAL;
                continue;
            }

            rendered.append(current);
        }

        return rendered.toString();
    }

    private String toLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            return quote("<BINARY length=" + bytes.length + ">");
        }
        if (value instanceof InputStream || value instanceof Reader) {
            return quote("<STREAM>");
        }
        if (value instanceof Timestamp timestamp) {
            return quote(timestamp.toInstant().toString());
        }
        if (value instanceof java.sql.Date date) {
            return quote(date.toLocalDate().toString());
        }
        if (value instanceof Time time) {
            return quote(time.toLocalTime().toString());
        }
        if (value instanceof Date date) {
            return quote(date.toInstant().toString());
        }
        if (value instanceof TemporalAccessor
                || value instanceof Enum<?>
                || value instanceof Character
                || value instanceof CharSequence) {
            return quote(value.toString());
        }
        return quote(String.valueOf(value));
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private char closingDelimiter(char openingDelimiter) {
        return switch (openingDelimiter) {
            case '[' -> ']';
            case '{' -> '}';
            case '(' -> ')';
            case '<' -> '>';
            default -> openingDelimiter;
        };
    }

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        ALTERNATIVE_QUOTE
    }
}
