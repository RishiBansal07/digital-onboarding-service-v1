package com.randombank.onboarding.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseAccessInterceptorTest {

    @Test
    void waitsForSlotThenProceedsAndPreservesResult() throws Throwable {
        DatabaseOperationRateLimiter limiter = new DatabaseOperationRateLimiter(
                Integer.MAX_VALUE, System::nanoTime, ignored -> { });
        DatabaseAccessInterceptor interceptor = new DatabaseAccessInterceptor(limiter);
        ProceedingJoinPoint joinPoint = joinPointReturning("result");

        Object result = interceptor.interceptDatabaseAccess(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
    }

    @Test
    void propagatesRepositoryException() throws Throwable {
        DatabaseOperationRateLimiter limiter = new DatabaseOperationRateLimiter(
                Integer.MAX_VALUE, System::nanoTime, ignored -> { });
        DatabaseAccessInterceptor interceptor = new DatabaseAccessInterceptor(limiter);
        ProceedingJoinPoint joinPoint = joinPointReturning(null);
        IllegalStateException failure = new IllegalStateException("database failure");
        when(joinPoint.proceed()).thenThrow(failure);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> interceptor.interceptDatabaseAccess(joinPoint));

        assertEquals(failure, thrown);
    }

    private ProceedingJoinPoint joinPointReturning(Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn("findByUsername");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }
}

