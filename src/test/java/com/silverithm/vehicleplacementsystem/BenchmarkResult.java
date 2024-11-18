package com.silverithm.vehicleplacementsystem;

import lombok.Value;

@Value
public class BenchmarkResult {
    TestResult original;
    TestResult v2;

    @Value
    public static class TestResult {
        long executionTime;  // 나노초
        long cpuTime;        // 나노초
        long memoryUsed;     // 바이트

        public double getExecutionTimeSeconds() {
            return executionTime / 1_000_000_000.0;
        }

        public double getCpuTimeSeconds() {
            return cpuTime / 1_000_000_000.0;
        }

        public double getMemoryUsedMB() {
            return memoryUsed / (1024.0 * 1024.0);
        }
    }

    public double getImprovement() {
        return ((original.executionTime - v2.executionTime) / (double) original.executionTime) * 100;
    }

    public double getCpuImprovement() {
        return ((original.cpuTime - v2.cpuTime) / (double) original.cpuTime) * 100;
    }

    public double getMemoryImprovement() {
        return ((original.memoryUsed - v2.memoryUsed) / (double) original.memoryUsed) * 100;
    }
}