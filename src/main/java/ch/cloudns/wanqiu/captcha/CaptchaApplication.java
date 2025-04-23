package ch.cloudns.wanqiu.captcha;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class CaptchaApplication {
  public static void main(String[] args) {
    SpringApplication.run(CaptchaApplication.class, args);
  }

  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  public static class CaptchaRouter {

    @Bean
    public RouterFunction<ServerResponse> route(CaptchaHandler captchaHandler) {
      return RouterFunctions.route(
              GET("/api/captcha").and(accept(MediaType.IMAGE_PNG)),
              captchaHandler::handleGetCaptchaImage);
    }
  }

  @Component
  public static class CaptchaHandler {

    private static final Logger log = LoggerFactory.getLogger(CaptchaHandler.class);

    private static Map<String, String> captchaMap = new ConcurrentHashMap<>();
    private static List<String> captchaKeys = List.of();

    @Value("${captcha.image-file-path}")
    private String captchaImageFilePath;

    @Value("${captcha.map-file-path}")
    private String captchaMapFilePath;

    @PostConstruct
    public void loadCaptchaMap() {
      log.info("🔄 正在加载验证码映射文件：{}", captchaMapFilePath);
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(captchaMapFilePath))) {
        Object obj = ois.readObject();
        if (obj instanceof Map<?, ?> map) {
          Map<String, String> tempMap = new ConcurrentHashMap<>();
          for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String val) {
              tempMap.put(key, val);
            }
          }
          captchaMap = tempMap;
          captchaKeys = List.copyOf(captchaMap.keySet());
          log.info("✅ 成功加载验证码映射，条目数：{}", captchaMap.size());
        } else {
          throw new IllegalStateException("错误格式：文件应包含 Map<String, String>");
        }
      } catch (Exception e) {
        log.error("❌ 加载失败：{}，异常：{}", captchaMapFilePath, e.getMessage(), e);
      }
    }

    public Mono<ServerResponse> handleGetCaptchaImage(ServerRequest request) {
      return Mono.fromCallable(() -> {
        if (captchaMap.isEmpty()) {
          throw new IllegalStateException("验证码映射为空，请检查 captcha_map.ser 是否加载成功。");
        }

        String key = captchaKeys.get(ThreadLocalRandom.current().nextInt(captchaKeys.size()));
        String fileName = key + ".png";
        String fullPath = captchaImageFilePath + fileName;

        log.debug("🎯 返回验证码图片文件：{}", fullPath);

        File imageFile = new File(fullPath);
        if (!imageFile.exists()) {
          throw new FileNotFoundException("图片文件不存在: " + fullPath);
        }

        return imageFile;
      }).flatMap(file -> {
        try {
          InputStream inputStream = new FileInputStream(file);
          return ServerResponse.ok()
                  .contentType(MediaType.IMAGE_PNG)
                  .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                  .header(HttpHeaders.PRAGMA, "no-cache")
                  .header(HttpHeaders.EXPIRES, "0")
                  .body(BodyInserters.fromResource(new InputStreamResource(inputStream)));
        } catch (FileNotFoundException e) {
          return ServerResponse.status(500)
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(Map.of("error", "读取图片失败: " + e.getMessage()));
        }
      }).onErrorResume(e -> {
        log.warn("⚠️ 获取验证码失败：{}", e.getMessage());
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("error", "验证码生成失败: " + e.getMessage()));
      });
    }
  }
}


