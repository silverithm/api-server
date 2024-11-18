package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.ChromosomeV2;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChromosomeBenchmark {
    private static final int POPULATION_SIZE = 20000;
    private static final int ITERATIONS = 300;
    private static final Random RANDOM = new Random();

    private List<EmployeeDTO> employees;
    private List<ElderlyDTO> elderly;
    private List<CoupleRequestDTO> couples;
    private Map<Integer, List<Integer>> fixedAssignments;

    public void runBenchmark() {
        initializeTestData();

        // JVM 워밍업
        warmup();

        log.info("=== 벤치마크 시작 ===");
        log.info("인구 크기: {}", POPULATION_SIZE);
        log.info("반복 횟수: {}", ITERATIONS);

        // 원본 Chromosome 테스트
        BenchmarkResult originalResult = testOriginalChromosome();

        // ChromosomeV2 테스트
        BenchmarkResult v2Result = testChromosomeV2();

        // 결과 출력
        printResults(originalResult, v2Result);
    }

    private void warmup() {
        log.info("JVM 워밍업 시작...");
        for (int i = 0; i < 1000; i++) {
            try {
                new Chromosome(couples, employees, elderly, fixedAssignments);
                new ChromosomeV2(couples, employees, elderly, fixedAssignments);
            } catch (Exception e) {
                log.error("워밍업 중 에러 발생", e);
            }
        }
        System.gc();
        log.info("워밍업 완료");
    }

    private void initializeTestData() {
        // 테스트 데이터 초기화
        employees = createEmployees(100);  // 100명의 직원
        elderly = createElderly(2000);     // 2000명의 노인
        couples = createCouples(200);      // 200쌍의 부부
        fixedAssignments = createFixedAssignments(20);  // 20개의 고정 배정
    }

    private BenchmarkResult testOriginalChromosome() {
        log.info("원본 Chromosome 테스트 시작");

        long startTime = System.nanoTime();
        long startCpuTime = getThreadCpuTime();
        long startMemory = getUsedMemory();

        List<Chromosome> population = new ArrayList<>();

        try {
            // 초기 인구 생성
            for (int i = 0; i < POPULATION_SIZE; i++) {
                population.add(new Chromosome(couples, employees, elderly, fixedAssignments));
            }

            // 연산 수행
            for (int i = 0; i < ITERATIONS; i++) {
                for (Chromosome chromosome : population) {
                    Chromosome copy = Chromosome.copy(chromosome);
                    // 추가 연산 시뮬레이션
                    performOperations(copy);
                }
            }
        } catch (Exception e) {
            log.error("원본 Chromosome 테스트 중 에러 발생", e);
        }

        long endTime = System.nanoTime();
        long endCpuTime = getThreadCpuTime();
        long endMemory = getUsedMemory();

        return new BenchmarkResult(
                endTime - startTime,
                endCpuTime - startCpuTime,
                endMemory - startMemory
        );
    }

    private BenchmarkResult testChromosomeV2() {
        log.info("ChromosomeV2 테스트 시작");

        long startTime = System.nanoTime();
        long startCpuTime = getThreadCpuTime();
        long startMemory = getUsedMemory();

        List<ChromosomeV2> population = new ArrayList<>();

        try {
            // 초기 인구 생성
            for (int i = 0; i < POPULATION_SIZE; i++) {
                population.add(new ChromosomeV2(couples, employees, elderly, fixedAssignments));
            }

            // 연산 수행
            for (int i = 0; i < ITERATIONS; i++) {
                for (ChromosomeV2 chromosome : population) {
                    ChromosomeV2 copy = ChromosomeV2.copy(chromosome);
                    // 추가 연산 시뮬레이션
                    performOperationsV2(copy);
                }
            }
        } catch (Exception e) {
            log.error("ChromosomeV2 테스트 중 에러 발생", e);
        }

        long endTime = System.nanoTime();
        long endCpuTime = getThreadCpuTime();
        long endMemory = getUsedMemory();

        return new BenchmarkResult(
                endTime - startTime,
                endCpuTime - startCpuTime,
                endMemory - startMemory
        );
    }

    private void performOperations(Chromosome chromosome) {
        // 연산 시뮬레이션
        List<List<Integer>> genes = chromosome.getGenes();
        for (List<Integer> gene : genes) {
            Collections.shuffle(gene);
            Collections.sort(gene);
        }
    }

    private void performOperationsV2(ChromosomeV2 chromosome) {
        // 연산 시뮬레이션
        int[][] genes = chromosome.getGenes();
        for (int[] gene : genes) {
            shuffleArray(gene);
            Arrays.sort(gene);
        }
    }

    private void shuffleArray(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    private long getThreadCpuTime() {
        return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private void printResults(BenchmarkResult original, BenchmarkResult v2) {
        log.info("\n=== 벤치마크 결과 ===");
        log.info("원본 Chromosome:");
        log.info("  실행 시간: {} 초", original.executionTime / 1_000_000_000.0);
        log.info("  CPU 시간: {} 초", original.cpuTime / 1_000_000_000.0);
        log.info("  메모리 사용: {} MB", original.memoryUsed / (1024 * 1024));

        log.info("\nChromosomeV2:");
        log.info("  실행 시간: {} 초", v2.executionTime / 1_000_000_000.0);
        log.info("  CPU 시간: {} 초", v2.cpuTime / 1_000_000_000.0);
        log.info("  메모리 사용: {} MB", v2.memoryUsed / (1024 * 1024));

        double timeImprovement = ((original.executionTime - v2.executionTime) / (double) original.executionTime) * 100;
        double cpuImprovement = ((original.cpuTime - v2.cpuTime) / (double) original.cpuTime) * 100;
        double memoryImprovement = ((original.memoryUsed - v2.memoryUsed) / (double) original.memoryUsed) * 100;

        log.info("\n성능 향상:");
        log.info("  실행 시간: {:.2f}% 감소", timeImprovement);
        log.info("  CPU 사용: {:.2f}% 감소", cpuImprovement);
        log.info("  메모리 사용: {:.2f}% 감소", memoryImprovement);
    }

    @Value
    private static class BenchmarkResult {
        long executionTime;  // 나노초
        long cpuTime;        // 나노초
        long memoryUsed;     // 바이트
    }

    // 테스트 데이터 생성 메서드들
    private List<EmployeeDTO> createEmployees(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new EmployeeDTO(
                        (long) i,
                        "Employee" + i,
                        "Address" + i,
                        "Workplace" + i,
                        new Location(123.1, 123.1),
                        new Location(123.1, 123.1),
                        5,
                        false

                ))
                .collect(Collectors.toList());
    }

    private List<ElderlyDTO> createElderly(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new ElderlyDTO(
                        (long) i,
                        "Elderly" + i,
                        new Location(123.1, 123.1),
                        false,
                        "Address" + i
                ))
                .collect(Collectors.toList());
    }

    private List<CoupleRequestDTO> createCouples(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new CoupleRequestDTO(
                        (long) (i * 2),
                        (long) (i * 2 + 1)
                ))
                .collect(Collectors.toList());
    }

    private Map<Integer, List<Integer>> createFixedAssignments(int count) {
        Map<Integer, List<Integer>> assignments = new HashMap<>();
        for (int i = 0; i < count; i++) {
            List<Integer> fixed = IntStream.range(0, RANDOM.nextInt(3) + 1)
                    .map(j -> RANDOM.nextInt(2000))
                    .boxed()
                    .collect(Collectors.toList());
            assignments.put(i, fixed);
        }
        return assignments;
    }
}