package com.randombank.onboarding.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect that intercepts all repository method calls
 * and enforces database operation rate limiting.
 *
 * Each repository call waits for the next available database-operation slot before
 * it proceeds. Calls are queued rather than rejected so transactions can complete.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DatabaseAccessInterceptor {

    private final DatabaseOperationRateLimiter rateLimiter;

    /**
     * Pointcut: All public methods in repository package
     *
     * This matches:
     * - CustomerRepository.findByUsername()
     * - CustomerRepository.existsByUsername()
     * - AccountRepository.findByCustomerUsername()
     * - AccountRepository.existsByIban()
     * - etc.
     */
    @Pointcut("execution(public * com.randombank.onboarding.repository.*.*(..))")
    public void repositoryMethods() {
        // Pointcut definition - no code here
    }

    /**
     * Waits for the next operation slot, then executes the repository method.
     */
    @Around("repositoryMethods()")
    public Object interceptDatabaseAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        rateLimiter.acquire();
        log.debug("Executing rate-limited DB operation: {}.{}", className, methodName);
        return joinPoint.proceed();
    }
}

