package ch.cloudns.wanqiu;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class CaptchaGenerator {

  private static final System.Logger LOGGER = System.getLogger(CaptchaGenerator.class.getName());
  private static final String OUTPUT_DIR = "./captcha/";
  private static final String CAPTCHA_MAP_FILE = "./captcha_map.ser";
  private static final int CAPTCHA_COUNT = 443;

  private static final Map<String, String> captchaMap = new ConcurrentHashMap<>();

  public static void main(String[] args) {
    ExecutorService executorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    while (captchaMap.size() < CAPTCHA_COUNT) {
      String valueCaptcha = generateRandomCaptcha();
      String keyCaptcha = generateRandomCaptcha();

      if (captchaMap.containsKey(keyCaptcha)) continue;

      captchaMap.put(keyCaptcha, valueCaptcha);
      saveCaptchaMapToFile();
      executorService.submit(() -> runPythonScript(valueCaptcha, keyCaptcha));
    }

    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(10, TimeUnit.MINUTES)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
    }
  }

  private static String generateRandomCaptcha() {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    StringBuilder captcha = new StringBuilder();
    Random random = new Random();
    for (int i = 0; i < 4; i++) {
      captcha.append(chars.charAt(random.nextInt(chars.length())));
    }
    return captcha.toString();
  }

  private static void runPythonScript(String valueCaptcha, String keyCaptcha) {
    try {
      List<String> command =
          Arrays.asList("python", "generate_captcha.py", valueCaptcha, OUTPUT_DIR, keyCaptcha);
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(new File(System.getProperty("user.dir")));
      pb.redirectErrorStream(true);

      LOGGER.log(System.Logger.Level.INFO, "执行命令: {0}", String.join(" ", command));
      LOGGER.log(System.Logger.Level.INFO, "工作目录: {0}", pb.directory().getAbsolutePath());

      Process process = pb.start();

      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          LOGGER.log(System.Logger.Level.INFO, "[Python] {0}", line);
        }
      }

      int exitCode = process.waitFor();
      LOGGER.log(System.Logger.Level.INFO, "Python 脚本退出码: {0}", exitCode);

    } catch (Exception e) {
      LOGGER.log(System.Logger.Level.ERROR, "运行 Python 脚本失败: " + valueCaptcha, e);
    }
  }

  private static synchronized void saveCaptchaMapToFile() {
    try {
      // 设置保存目录
      Path baseDir = Paths.get("C:/Users/wanqi/Desktop/backend/wanqiu/src/main/java/");
      Files.createDirectories(baseDir);

//      // 保存为 .txt 文件（key=value）
//      Path txtPath = baseDir.resolve("captcha_map.txt");
//      try (BufferedWriter writer = Files.newBufferedWriter(txtPath, StandardCharsets.UTF_8)) {
//        for (Map.Entry<String, String> entry : captchaMap.entrySet()) {
//          writer.write(entry.getKey() + "=" + entry.getValue());
//          writer.newLine();
//        }
//      }

      // 保存为 .csv 文件（key,value）
      Path csvPath = baseDir.resolve("captcha_map.csv");
      try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
        writer.write("key,value"); // CSV 表头
        writer.newLine();
        for (Map.Entry<String, String> entry : captchaMap.entrySet()) {
          writer.write(entry.getKey() + "," + entry.getValue());
          writer.newLine();
        }
      }

      // 保存为 .ser 文件（序列化对象）
      Path serPath = baseDir.resolve("captcha_map.ser");
      try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(serPath))) {
        oos.writeObject(captchaMap);
      }

      LOGGER.log(System.Logger.Level.INFO, "已保存 captchaMap（txt/csv/ser），共 {0} 项", captchaMap.size());
    } catch (IOException e) {
      System.err.println("保存 captchaMap 失败：" + e.getMessage());
      e.printStackTrace();
    }
  }


}
