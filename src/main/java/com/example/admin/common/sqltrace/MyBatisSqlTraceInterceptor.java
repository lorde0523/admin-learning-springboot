package com.example.admin.common.sqltrace;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class})
})
public class MyBatisSqlTraceInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MyBatisSqlTraceInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startedAt = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            if (!Boolean.parseBoolean(MDC.get("sqlCapturePaused"))) {
                logSql(invocation, elapsedMillis);
            }
        }
    }

    private void logSql(Invocation invocation, long elapsedMillis) {
        try {
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            BoundSql boundSql = statementHandler.getBoundSql();
            MappedStatement mappedStatement = mappedStatement(statementHandler);
            String queryId = mappedStatement == null ? "UNKNOWN" : mappedStatement.getId();
            String sqlText = fullSql(boundSql);
            log.info(
                    "SQL_TRACE requestTraceId={} sqlBatchId={} pageId={} queryId={} executedAt={} elapsedMillis={} sqlText={}",
                    MDC.get("requestTraceId"),
                    MDC.get("sqlBatchId"),
                    MDC.get("pageId"),
                    queryId,
                    LocalDateTime.now(),
                    elapsedMillis,
                    sqlText);
        } catch (RuntimeException exception) {
            log.debug("Failed to write SQL trace log.", exception);
        }
    }

    private MappedStatement mappedStatement(StatementHandler statementHandler) {
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        Object value = metaObject.getValue("delegate.mappedStatement");
        return value instanceof MappedStatement mappedStatement ? mappedStatement : null;
    }

    private String fullSql(BoundSql boundSql) {
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null || parameterMappings.isEmpty()) {
            return sql;
        }

        MetaObject parameterMetaObject = parameterObject == null
                ? null
                : SystemMetaObject.forObject(parameterObject);
        for (ParameterMapping parameterMapping : parameterMappings) {
            Object value = parameterValue(boundSql, parameterMetaObject, parameterMapping.getProperty());
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(formatValue(value)));
        }
        return sql;
    }

    private Object parameterValue(BoundSql boundSql, MetaObject parameterMetaObject, String propertyName) {
        if (boundSql.hasAdditionalParameter(propertyName)) {
            return boundSql.getAdditionalParameter(propertyName);
        }
        if (parameterMetaObject != null && parameterMetaObject.hasGetter(propertyName)) {
            return parameterMetaObject.getValue(propertyName);
        }
        return null;
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }
}
