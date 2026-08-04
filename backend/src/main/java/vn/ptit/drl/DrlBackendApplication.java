package vn.ptit.drl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hệ chạy MỘT instance duy nhất — đó là lý do job neo dùng {@code @Scheduled} thuần,
 * không cần ShedLock. Nếu về sau chạy nhiều instance thì phải thêm khóa phân tán,
 * nếu không mỗi instance sẽ neo trùng lô và đốt gas vô ích.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DrlBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrlBackendApplication.class, args);
    }
}
