package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fkbank.testsupport.PostgresContainer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves correlationId propagation past the request thread at the mechanism level: no real
 * outbox consumer exists yet, so this dispatches through the same task executor a future one
 * would use, from a thread already carrying a correlationId in MDC.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("correlationId survives a hop onto the async task executor")
class AsyncCorrelationPropagationIT {

  @Autowired private TaskExecutor taskExecutor;

  private ListAppender<ILoggingEvent> appender;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    PostgresContainer.registerDataSource(registry);
  }

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    ((Logger) LoggerFactory.getLogger(AsyncCorrelationPropagationIT.class)).addAppender(appender);
  }

  @AfterEach
  void detachAppenderAndClearMdc() {
    ((Logger) LoggerFactory.getLogger(AsyncCorrelationPropagationIT.class)).detachAppender(appender);
    MDC.clear();
  }

  @Test
  void theDispatchedTaskSeesAndLogsTheTriggeringRequestsCorrelationId() throws Exception {
    String correlationId = "async-hop-" + System.identityHashCode(this);
    MDC.put("correlationId", correlationId);
    CountDownLatch taskRan = new CountDownLatch(1);

    taskExecutor.execute(
        () -> {
          LoggerFactory.getLogger(AsyncCorrelationPropagationIT.class)
              .info("dispatched task running with correlationId={}", MDC.get("correlationId"));
          taskRan.countDown();
        });

    assertThat(taskRan.await(5, TimeUnit.SECONDS)).as("dispatched task ran in time").isTrue();
    assertThat(appender.list)
        .as("the dispatched task's own log line")
        .hasSize(1)
        .first()
        .satisfies(event -> assertThat(event.getMDCPropertyMap()).containsEntry("correlationId", correlationId));
  }
}
