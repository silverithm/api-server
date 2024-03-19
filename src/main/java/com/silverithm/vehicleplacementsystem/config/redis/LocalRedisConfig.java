package com.silverithm.vehicleplacementsystem.config.redis;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import redis.embedded.RedisServer;

@Slf4j
@Profile("!prod") // profile이 prod(배포환경)이 아닐 경우에만 활성화
// -> 로컬 환경에서만 Embedded Redis를 사용하고, 실제 배포 환경에서는 외부 Redis 서버를 사용하기 때문
@Configuration
public class LocalRedisConfig {


    private int redisPort;


    public LocalRedisConfig(@Value("${redis.port}") int redisPort) {
        this.redisPort = redisPort;
    }

    private RedisServer redisServer;

    // Redis 서버를 실행시키는 메서드
    // 해당 클래스가 로딩될 때, startRedis() 메서드가 자동으로 실행돼서 Embedded Redis를 실행함
    @PostConstruct
    public void redisServer() throws IOException {
        int port = isRedisRunning() ? findAvailablePort() : redisPort; // Redis 서버가 실행 중인지 확인
        // 실행 중이라면 사용 가능한 다른 포트를 찾아서 port 변수에 할당하고,
        // 실행 중이 아니라면 redisPort 변수의 값을 사용

        // 현재 시스템이 ARM 아키텍처인지 확인
        if (isArmArchitecture()) {
            // ARM 아키텍처가 맞다면, RedisServer 클래스를 사용하여 Redis 서버를 생성
            System.out.println("ARM Architecture");
            redisServer = new RedisServer(Objects.requireNonNull(getRedisServerExecutable()), port);
            // getRedisServerExecutable() - ARM 아키텍처에서 Redis Server를 실행할 때 사용할 Redis Server 실행 파일을 가져오는 메서드
            // ( 가져올 파일이 없는 경우 예외를 던짐 )
        } else {
            // ARM 아키텍처가 아니라면, RedisServer.builder()를 사용하여 Redis 서버를 생성
            redisServer = RedisServer.builder()
                    .port(port)
                    .setting("maxmemory 128M")
                    .build();
        }

        // 위에서 생성한 Redis 서버 객체를 실행
        redisServer.start();
    }

    // PreDestroy 애너테이션으로 해당 클래스가 종료될 때 stopRedis() 메서드가 자동으로 실행되어 Embedded Redis를 종료함
    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    // Embedded Redis가 현재 실행중인지 확인
    private boolean isRedisRunning() throws IOException {
        return isRunning(executeGrepProcessCommand(redisPort));
    }

    // 현재 PC/서버에서 사용가능한 포트 조회
    public int findAvailablePort() throws IOException {

        for (int port = 10000; port <= 65535; port++) {
            Process process = executeGrepProcessCommand(port);
            if (!isRunning(process)) {
                return port;
            }
        }

        throw new IllegalArgumentException("Not Found Available port: 10000 ~ 65535");
    }

    // 해당 port를 사용중인 프로세스 확인하는 sh 실행
    private Process executeGrepProcessCommand(int port) throws IOException {
        String OS = System.getProperty("os.name").toLowerCase();
        System.out.println("OS: " + OS);
        System.out.println(System.getProperty("os.arch"));
        if (OS.contains("win")) {
            log.info("OS is  " + OS + " " + port);
            String command = String.format("netstat -nao | find \"LISTEN\" | find \"%d\"", port);
            String[] shell = {"cmd.exe", "/y", "/c", command};
            return Runtime.getRuntime().exec(shell);
        }
        String command = String.format("netstat -nat | grep LISTEN|grep %d", port);
        String[] shell = {"/bin/sh", "-c", command};
        return Runtime.getRuntime().exec(shell);
    }


    // 해당 Process가 현재 실행중인지 확인
    private boolean isRunning(Process process) {
        String line;
        StringBuilder pidInfo = new StringBuilder();

        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while ((line = input.readLine()) != null) {
                pidInfo.append(line);
            }
        } catch (Exception e) {
        }

        return !StringUtils.isEmpty(pidInfo.toString());
    }

    private boolean isArmArchitecture() {
        return System.getProperty("os.arch").contains("aarch64");
    }

    private File getRedisServerExecutable() throws IOException {
        try {
            //return  new ClassPathResource("binary/redis/redis-server-linux-arm64-arc").getFile();
            return new File("src/main/resources/binary/redis/redis-server");
        } catch (Exception e) {
            throw new IOException("Redis Server Executable not found");
        }
    }
}



