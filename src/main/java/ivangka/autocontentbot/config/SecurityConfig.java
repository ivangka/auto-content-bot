package ivangka.autocontentbot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.HashSet;
import java.util.Set;

@PropertySource("classpath:application.properties")
@Configuration
public class SecurityConfig {

    private final Set<Long> allowedUserIds = new HashSet<>();

    @Value("${allowed.user.ids}")
    private String allowedUserIdsString;

    @PostConstruct
    public void init() {
        String[] ids = allowedUserIdsString.split(",");
        for (String id : ids) {
            allowedUserIds.add(Long.parseLong(id.trim()));
        }
    }

    public boolean isUserAllowed(Long userId) {
        return allowedUserIds.contains(userId);
    }

}
