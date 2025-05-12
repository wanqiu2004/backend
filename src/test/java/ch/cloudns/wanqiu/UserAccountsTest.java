package ch.cloudns.wanqiu;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UserAccountsTest {

  private static SqlSessionFactory sqlSessionFactory;

  @BeforeAll
  public static void setup() throws Exception {
    String resource = "mybatis-config.xml";
    InputStream inputStream = Resources.getResourceAsStream(resource);
    sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
  }

  @Test
  public void testSelectAllUsers() {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      List<UserAccounts> users =
          session.selectList("ch.cloudns.wanqiu.UserAccountsMapper.selectAll");

      System.out.printf("%-38s %-20s\n", "ID", "USERNAME");
      System.out.println("--------------------------------------------------------------");

      for (UserAccounts user : users) {
        System.out.printf("%-38s %-20s\n", user.getUserId(), user.getUsername());
      }

      assertNotNull(users);
    }
  }
}
