package com.magister.backend.global.aop;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerLoggingAspectTests {
    @Test
    void propagatesFailureAndLogsDurationWithoutExceptionContents() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(ControllerLoggingAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("POST", "/api/gemini")));
        try {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            Signature signature = mock(Signature.class);
            when(joinPoint.getSignature()).thenReturn(signature);
            when(signature.getDeclaringType()).thenReturn(ControllerLoggingAspectTests.class);
            when(signature.getName()).thenReturn("generate");
            RuntimeException failure = new IllegalStateException("sensitive-message",
                    new RuntimeException("sensitive-cause"));
            when(joinPoint.proceed()).thenThrow(failure);

            assertThatThrownBy(() -> new ControllerLoggingAspect().logController(joinPoint)).isSameAs(failure);
            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain("sensitive-message", "sensitive-cause");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(appender.list.get(1).getFormattedMessage()).contains("API END", "Duration=[");
        } finally {
            RequestContextHolder.resetRequestAttributes();
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
