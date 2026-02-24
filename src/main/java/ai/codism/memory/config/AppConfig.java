package ai.codism.memory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetAddress;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public String hostname() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            log.info("Detected hostname: {}", hostname);
            return hostname;
        } catch (Exception e) {
            log.warn("Failed to detect hostname, using 'unknown'", e);
            return "unknown";
        }
    }

    @Bean
    public ApplicationRunner migrateHostColumn(JdbcTemplate jdbcTemplate,
                                               @Qualifier("hostname") String hostname) {
        return args -> {
            int memories = jdbcTemplate.update(
                    "UPDATE memories SET host = ? WHERE host IS NULL", hostname);
            int notes = jdbcTemplate.update(
                    "UPDATE notes SET host = ? WHERE host IS NULL", hostname);
            if (memories > 0 || notes > 0) {
                log.info("Migrated host column: memories={}, notes={} → {}", memories, notes, hostname);
            }
        };
    }
}
