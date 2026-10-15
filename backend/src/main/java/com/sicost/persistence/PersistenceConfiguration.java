package com.sicost.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** MyBatis의 blocking JDBC 트랜잭션을 사용하며 스키마 변경은 Flyway만 소유한다. */
@Configuration
@EnableTransactionManagement
public class PersistenceConfiguration {
}
