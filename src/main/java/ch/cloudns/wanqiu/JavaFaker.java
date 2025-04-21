package ch.cloudns.wanqiu;

import com.github.javafaker.Faker;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.util.Locale;
import java.util.UUID;

public class JavaFaker {
  private static final System.Logger LOGGER = System.getLogger(JavaFaker.class.getName());

  public static void main(String[] args) {
    Instant wallClockStart = Instant.now(); // 墙钟时间
    long nanoStart = System.nanoTime(); // 精确时间

    Faker faker = new Faker(Locale.CHINA); // 中文姓名
    int count = 100_000;
    String filePath = "users.csv";

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false), 16 * 1024)) {
      StringBuilder sb = new StringBuilder(128);

      writer.write("ID,姓名,密码,邮箱\n");

      for (int i = 0; i < count; i++) {
        sb.setLength(0);
        sb.append('"').append(UUID.randomUUID()).append('"').append(',');
        sb.append('"').append(faker.name().fullName()).append('"').append(',');
        sb.append('"')
            .append(faker.internet().password(8, 16, true, true, true))
            .append('"')
            .append(',');
        sb.append('"').append(faker.internet().emailAddress()).append('"').append('\n');

        writer.write(sb.toString());

        if ((i & 0x3FFF) == 0) writer.flush();
      }

      Instant wallClockEnd = Instant.now();
      long nanoEnd = System.nanoTime();

      Duration wallClockDuration = Duration.between(wallClockStart, wallClockEnd);
      long nanoDuration = nanoEnd - nanoStart;

      System.out.println("✅ CSV文件生成完毕: " + filePath);
      System.out.println(
          "⏱️ 墙钟时间耗时: "
              + wallClockDuration.toMillis()
              + " 毫秒 ≈ "
              + String.format("%.3f", wallClockDuration.toMillis() / 1000.0)
              + " 秒");
      System.out.println(
          "📏 纳秒级精确耗时: "
              + nanoDuration
              + " 纳秒 ≈ "
              + String.format("%.3f", nanoDuration / 1_000_000.0)
              + " 毫秒");

    } catch (IOException e) {
      LOGGER.log(System.Logger.Level.ERROR, "生成CSV文件出错", e);
    }
  }
}
