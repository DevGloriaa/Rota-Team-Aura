package com.aura.ajo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String dbUrl = System.getenv("DATABASE_URL");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        HikariConfig config = new HikariConfig();

        if (dbUrl == null || dbUrl.isBlank()) {
            config.setJdbcUrl("jdbc:postgresql://localhost:5432/ajo");
        } else if (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) {
            // Render provides DATABASE_URL as postgres://user:password@host:port/db —
            // the JDBC driver doesn't understand embedded userinfo, so pull it out.
            URI uri = URI.create(dbUrl);
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts.length > 1 ? parts[1] : "";
            }
            config.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath());
        } else {
            config.setJdbcUrl(dbUrl);
        }

        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        return new HikariDataSource(config);
    }
}
