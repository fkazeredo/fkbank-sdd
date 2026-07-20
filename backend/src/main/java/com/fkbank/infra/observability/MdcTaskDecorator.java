package com.fkbank.infra.observability;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

/**
 * Carries the submitting thread's MDC (including {@code correlationId}) into work run on the
 * task executor's pool, and restores whatever the pooled thread had before, so one task's
 * context never leaks into the next task that thread happens to run.
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> contextAtSubmission = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> contextBeforeTask = MDC.getCopyOfContextMap();
      try {
        setContext(contextAtSubmission);
        runnable.run();
      } finally {
        setContext(contextBeforeTask);
      }
    };
  }

  private static void setContext(Map<String, String> context) {
    if (context != null) {
      MDC.setContextMap(context);
    } else {
      MDC.clear();
    }
  }
}
