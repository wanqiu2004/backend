package ch.cloudns.wanqiu.captcha;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class CaptchaApplication {

  public static void main(String[] args) {
    SpringApplication.run(CaptchaApplication.class, args);
  }

  /** 路由配置类，配置函数式路由 */
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  public static class CaptchaRouter {

    @Bean
    public RouterFunction<ServerResponse> route(CaptchaHandler captchaHandler) {
      return RouterFunctions.route(
          GET("/api/captcha").and(accept(MediaType.APPLICATION_JSON)),
          captchaHandler::handleGetCaptcha);
    }
  }

  @Component
  public static class CaptchaHandler {

    private static final Logger log = LoggerFactory.getLogger(CaptchaHandler.class);

    private static Map<String, String> captchaMap = new ConcurrentHashMap<>();
    private static List<String> captchaKeys = List.of(); // 不可变空列表初始化

    @Value("${captcha.image-url-path}")
    private String captchaImageUrlPath;

    @Value("${captcha.map-file-path}")
    private String captchaMapFilePath;

    @PostConstruct
    public void loadCaptchaMap() {
      log.info("🔄 正在尝试加载验证码映射文件：{}", captchaMapFilePath);
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
          captchaKeys = List.copyOf(captchaMap.keySet()); // 一次性拷贝键列表
          log.info("✅ 成功加载验证码映射，条目总数：{}", captchaMap.size());
        } else {
          throw new IllegalStateException("文件格式错误：captcha_map.ser 不包含 Map<String, String> 对象。");
        }
      } catch (Exception e) {
        log.error("❌ 加载 captcha_map.ser 失败，路径：{}，异常：{}", captchaMapFilePath, e.getMessage(), e);
      }
    }

    public Mono<ServerResponse> handleGetCaptcha(ServerRequest request) {
      return Mono.fromCallable(
              () -> {
                if (captchaMap.isEmpty()) {
                  throw new IllegalStateException("验证码映射为空，可能未成功加载 captcha_map.ser 文件。");
                }

                String key =
                    captchaKeys.get(ThreadLocalRandom.current().nextInt(captchaKeys.size()));
                String fileName = key + ".png";
                String imageUrl = captchaImageUrlPath + fileName;

                log.debug("🎯 返回验证码图片：{}", imageUrl);

                return new CaptchaResponse(imageUrl);
              })
          .flatMap(
              resp -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(resp))
          .onErrorResume(
              e -> {
                log.warn("⚠️ 获取验证码失败：{}", e.getMessage());
                return ServerResponse.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("error", "生成验证码失败: " + e.getMessage()));
              });
    }

    /** Java 21 现代风格 JSON 响应体 */
    public record CaptchaResponse(String url) {}
  }
}
