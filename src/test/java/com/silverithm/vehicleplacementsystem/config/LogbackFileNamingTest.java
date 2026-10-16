package com.silverithm.vehicleplacementsystem.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 운영 logback 설정이 지금 쓰는 로그를 오늘 날짜 이름의 파일에 남기는지 본다.
 * 에러 로그가 'TODAY_IS_UNDEFINED_server.log'에 쌓이던 것(정의되지 않은 ${TODAY})이 다시 생기지 않게 한다.
 */
class LogbackFileNamingTest {

    @TempDir
    Path dir;

    @Test
    void currentLogsGoToTodaysDatedFiles() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/logback-spring.xml")) {
            xml = new String(in.readAllBytes(), UTF_8).replace("${LOG_PATH}", dir.toString().replace('\\', '/'));
        }
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter()); // 새로 만든 컨텍스트는 직접 넣어 줘야 로그가 써진다
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        Logger log = context.getLogger("naming-test");
        log.info("정보 한 줄");
        log.error("에러 한 줄");
        assertThat(new StatusUtil(context).getHighestLevel(0)).isLessThan(Status.ERROR);
        context.stop();

        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(fileNames(dir.resolve("error"))).containsExactly(today + "_server_0.log");
        assertThat(fileNames(dir.resolve("info"))).containsExactly(today + "_server_0.log");

        String error = Files.readString(dir.resolve("error/" + today + "_server_0.log"));
        assertThat(error).contains("에러 한 줄").doesNotContain("정보 한 줄");
        assertThat(Files.readString(dir.resolve("info/" + today + "_server_0.log")))
                .contains("정보 한 줄").doesNotContain("에러 한 줄");
    }

    private static List<String> fileNames(Path folder) throws Exception {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(p -> p.getFileName().toString()).toList();
        }
    }
}
