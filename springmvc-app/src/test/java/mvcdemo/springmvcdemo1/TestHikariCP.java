package mvcdemo.springmvcdemo1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@SpringBootTest
public class TestHikariCP {
    @Autowired
    DataSource dataSource;
    @Test
    void testInsert() throws SQLException{
        // 1. 建立连接，连接数据库
        Connection connection = dataSource.getConnection();
        // 2. SQL语句，对数据库执行操作
        String sql = "INSERT INTO `class`(name,floor,teacher_id) VALUES(?,?,?)";
        // 3. 创建PreparedStatement对象(编号从1开始，不是从0开始)
        PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
        // 4. 通过PreparedStatement对象给每个字段设置值
        ps.setString(1,"软件工程");
        ps.setInt(2,1);
        ps.setInt(3,99);
        // 5. 执行SQL语句
        int rows = ps.executeUpdate();
        // 6. 判断是否插入成功
        System.out.println(rows > 0 ? "成功插入" + rows + "行" : "插入失败");
        // 7. 关闭资源
        ps.close();
        connection.close();
    }
}
