package ch.cloudns.wanqiu;

public class UserAccounts {
  private String userId;
  private String username;
  private String password;
  private String email;

  public UserAccounts() {
    // 无参构造
  }

  public UserAccounts(String userId, String username, String password, String email) {
    this.userId = userId;
    this.username = username;
    this.password = password;
    this.email = email;
  }

  // Getter 和 Setter 方法
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  // toString 方法（调试方便）
  @Override
  public String toString() {
    return "UserAccounts{"
        + "userId='"
        + userId
        + '\''
        + ", username='"
        + username
        + '\''
        + ", password='********'"
        + ", email='"
        + email
        + '\''
        + '}';
  }
}
