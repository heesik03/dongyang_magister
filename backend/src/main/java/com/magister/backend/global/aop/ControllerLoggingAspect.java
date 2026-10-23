package com.magister.backend.global.aop;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class ControllerLoggingAspect {

    @Pointcut("execution(* com.magister.backend..controller..*(..)) || @within(org.springframework.web.bind.annotation.RestController)")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {

        RequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes attributes)) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();

        String uri = request.getRequestURI();
        String httpMethod = request.getMethod();
        String className =
                joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName =
                joinPoint.getSignature().getName();

        long startTime = System.nanoTime();
        log.info(
                "API START | Method=[{}] | URI=[{}] | Handler=[{}.{}]",
                httpMethod,
                uri,
                className,
                methodName
        );
        try {
            return joinPoint.proceed();
        } finally {
            long durationMs =
                    (System.nanoTime() - startTime) / 1_000_000;
            log.info(
                    "API END | Method=[{}] | URI=[{}] | Handler=[{}.{}] | Duration=[{}ms]",
                    httpMethod,
                    uri,
                    className,
                    methodName,
                    durationMs
            );
        }
    }
}
