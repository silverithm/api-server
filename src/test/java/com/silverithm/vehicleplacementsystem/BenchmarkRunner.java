package com.silverithm.vehicleplacementsystem;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

// 1. 실행용 메인 클래스
@Slf4j
public class BenchmarkRunner {
    public static void main(String[] args) {
        log.info("벤치마크 시작");

        // 여러 번 실행하여 평균 측정
        int runs = 5;
        ChromosomeBenchmark benchmark = new ChromosomeBenchmark();

        log.info("총 {}회 실행", runs);

        List<BenchmarkResult> results = new ArrayList<>();
        for (int i = 0; i < runs; i++) {
            log.info("\n=== 실행 #{} ===", (i + 1));
            System.gc(); // 실행 전 GC
            try {
                Thread.sleep(2000); // GC와 시스템 안정화를 위한 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            benchmark.runBenchmark();

        }

    }


}
