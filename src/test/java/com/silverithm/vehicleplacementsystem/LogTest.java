package com.silverithm.vehicleplacementsystem;


import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class LogTest {

    @Test
    public void compareLoggingPerformance() {
        int iterations = 100_000;
        String user = "testUser";
        long startTime = System.currentTimeMillis();

        // 문자열 연결 방식
        long start1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            log.info("[scavenger] user(" + user + ") processed in " +
                    (System.currentTimeMillis() - startTime) + " ms: " + i + " items");
        }
        long time1 = System.nanoTime() - start1;

////        // 파라미터 치환 방식
        long start2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            log.info("[scavenger] user({}) processed in {} ms: {} items",
                    user, System.currentTimeMillis() - startTime, i);
        }
        long time2 = System.nanoTime() - start2;

        System.out.println("String concatenation: " + time1/1_000_000 + "ms");
        System.out.println("Parameter substitution: " + time2/1_000_000 + "ms");
    }
}
