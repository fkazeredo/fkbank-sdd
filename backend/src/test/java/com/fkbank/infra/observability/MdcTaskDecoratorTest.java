package com.fkbank.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

  private final MdcTaskDecorator decorator = new MdcTaskDecorator();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void copiesTheSubmittingThreadsCorrelationIdIntoTheDecoratedTask() throws Exception {
    MDC.put("correlationId", "from-the-request-thread");
    AtomicReference<String> seenInsideTheTask = new AtomicReference<>();

    Runnable decorated = decorator.decorate(() -> seenInsideTheTask.set(MDC.get("correlationId")));
    runOnAFreshThread(decorated);

    assertThat(seenInsideTheTask.get()).isEqualTo("from-the-request-thread");
  }

  @Test
  void leavesAPooledThreadsPriorContextUndisturbedAfterwards() throws Exception {
    MDC.put("correlationId", "captured-at-submission");
    Runnable decorated = decorator.decorate(() -> {});
    // Assert on the main thread, not inside the worker: an AssertionError thrown on a plain
    // Thread is merely uncaught there, so the test would still report green even if this
    // assertion failed.
    AtomicReference<String> seenAfterTheDecoratedTaskRan = new AtomicReference<>();

    Thread worker = new Thread(() -> {
      MDC.put("correlationId", "already-on-this-pooled-thread");
      decorated.run();
      seenAfterTheDecoratedTaskRan.set(MDC.get("correlationId"));
    });
    worker.start();
    worker.join(TimeUnit.SECONDS.toMillis(5));

    assertThat(worker.isAlive()).isFalse();
    assertThat(seenAfterTheDecoratedTaskRan.get()).isEqualTo("already-on-this-pooled-thread");
  }

  @Test
  void aTaskSubmittedWithNoMdcContextRunsWithoutOne() throws Exception {
    AtomicReference<String> seenInsideTheTask = new AtomicReference<>("not-run");
    Runnable decorated = decorator.decorate(() -> seenInsideTheTask.set(MDC.get("correlationId")));

    runOnAFreshThread(decorated);

    assertThat(seenInsideTheTask.get()).isNull();
  }

  private void runOnAFreshThread(Runnable task) throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    Thread worker = new Thread(() -> {
      task.run();
      done.countDown();
    });
    worker.start();
    assertThat(done.await(5, TimeUnit.SECONDS)).as("task completed in time").isTrue();
  }
}
