package com.zwskchat.zwskchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "auditExecutor")
    public ThreadPoolTaskExecutor auditExecutor() {
        return build("audit-", 2, 8, 4096);
    }

    @Bean(name = "commonExecutor")
    public ThreadPoolTaskExecutor commonExecutor() {
        return build("zwsk-", 4, 32, 1024);
    }

    private ThreadPoolTaskExecutor build(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(core);
        ex.setMaxPoolSize(max);
        ex.setQueueCapacity(queue);
        ex.setKeepAliveSeconds(60);
        ex.setThreadNamePrefix(prefix);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(20);
        ex.initialize();
        return ex;
    }
}
