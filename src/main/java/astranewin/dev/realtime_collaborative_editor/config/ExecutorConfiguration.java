package astranewin.dev.realtime_collaborative_editor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Configuration
public class ExecutorConfiguration {
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(4);
        executor.setRemoveOnCancelPolicy(true);

        return executor;
    }
}
