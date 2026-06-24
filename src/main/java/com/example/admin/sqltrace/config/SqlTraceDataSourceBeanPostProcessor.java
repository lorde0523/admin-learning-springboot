package com.example.admin.sqltrace.config;

import com.example.admin.sqltrace.jdbc.SqlTraceDataSource;
import com.example.admin.sqltrace.jdbc.SqlTraceRecorder;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class SqlTraceDataSourceBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<SqlTraceRecorder> recorderProvider;

    public SqlTraceDataSourceBeanPostProcessor(ObjectProvider<SqlTraceRecorder> recorderProvider) {
        this.recorderProvider = recorderProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && !(dataSource instanceof SqlTraceDataSource)) {
            return new SqlTraceDataSource(dataSource, recorderProvider.getObject());
        }
        return bean;
    }
}
