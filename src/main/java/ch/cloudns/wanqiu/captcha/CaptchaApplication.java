package ch.cloudns.wanqiu.captcha;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

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
    public RouterFunction<ServerResponse> captchaRoutes(CaptchaHandler handler) {
      return RouterFunctions
              .route(RequestPredicates.GET("/api/captcha")
                      .and(RequestPredicates.accept(MediaType.IMAGE_PNG)), handler::handleGetCaptchaImage)
              .andRoute(RequestPredicates.POST("/api/captcha/verify")
                      .and(RequestPredicates.accept(MediaType.APPLICATION_JSON)), handler::handleVerifyCaptcha);
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
          log.info("✅ 成功加载验证码映射，共 {} 条", captchaMap.size());
        } else {
          throw new IllegalStateException("错误格式：文件应包含 Map<String, String>");
        }
      } catch (Exception e) {
        log.error("❌ 加载验证码映射失败：{}，异常信息：{}", captchaMapFilePath, e.getMessage(), e);
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

        log.debug("🎯 准备返回验证码图片，文件路径：{}", fullPath);

        File imageFile = new File(fullPath);
        if (!imageFile.exists()) {
          throw new FileNotFoundException("图片文件不存在: " + fullPath);
        }

        return new AbstractMap.SimpleEntry<>(key, imageFile);
      }).flatMap(pair -> {
        String key = pair.getKey();
        File file = pair.getValue();
        try {
          InputStream inputStream = new FileInputStream(file);
          return ServerResponse.ok()
                  .contentType(MediaType.IMAGE_PNG)
                  .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                  .header(HttpHeaders.PRAGMA, "no-cache")
                  .header(HttpHeaders.EXPIRES, "0")
                  .header("X-Captcha-Key", key)  // 返回key给前端，必须保存用于校验
                  .body(BodyInserters.fromResource(new InputStreamResource(inputStream)));
        } catch (FileNotFoundException e) {
          return ServerResponse.status(500)
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(Map.of("error", "读取图片失败: " + e.getMessage()));
        }
      }).onErrorResume(e -> {
        log.warn("⚠️ 获取验证码图片失败：{}", e.getMessage());
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("error", "验证码生成失败: " + e.getMessage()));
      });
    }

    public Mono<ServerResponse> handleVerifyCaptcha(ServerRequest request) {
      return request.bodyToMono(Map.class).flatMap(body -> {
        String userCode = (String) body.get("captchaCode");
        String captchaKey = (String) body.get("captchaKey");

        if (captchaKey == null || userCode == null) {
          log.warn("⚠️ 请求缺失参数 captchaKey 或 captchaCode");
          return ServerResponse.badRequest().bodyValue(Map.of(
                  "code", 1,
                  "message", "缺少 captchaKey 或 captchaCode"
          ));
        }

        log.debug("🔍 正在验证验证码：captchaKey={}, captchaCode={}", captchaKey, userCode);

        String correctAnswer = captchaMap.get(captchaKey);
        if (correctAnswer == null) {
          log.info("🕓 验证码 key={} 不存在或已过期", captchaKey);
          return ServerResponse.ok().bodyValue(Map.of(
                  "code", 1,
                  "message", "验证码已过期或无效"
          ));
        }

        boolean matched = correctAnswer.equalsIgnoreCase(userCode.trim());
        if (matched) {
          log.info("✅ 验证成功：captchaKey={} 匹配成功", captchaKey);
          return ServerResponse.ok().bodyValue(Map.of(
                  "code", 0,
                  "message", "验证码正确"
          ));
        } else {
          log.info("❌ 验证失败：captchaKey={}，用户输入={}，正确答案={}", captchaKey, userCode, correctAnswer);
          return ServerResponse.ok().bodyValue(Map.of(
                  "code", 1,
                  "message", "验证码错误"
          ));
        }
      }).onErrorResume(e -> {
        log.error("🚨 服务器验证验证码时异常：{}", e.getMessage(), e);
        return ServerResponse.status(500).bodyValue(Map.of(
                "code", 1,
                "message", "服务器内部错误: " + e.getMessage()
        ));
      });
    }
  }
}

