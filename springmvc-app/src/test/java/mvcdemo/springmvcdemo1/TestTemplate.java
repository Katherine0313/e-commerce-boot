package mvcdemo.springmvcdemo1;


import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest
public class TestTemplate {
    // 注入JdbcTemplate对象
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Test
    public void testTemplate(){
        // 编写SQL语句
        String sql = "INSERT INTO `class`(`name`,`floor`,`teacher_id`)" + "VALUES('英语', 2, 2)";
        // 执行sql语句（调用JdbcTemplate的update方法）
        int rows = jdbcTemplate.update(sql);
        // 三元运算符判断
        System.out.println(rows > 0 ? "添加" + rows + "条成功" : "添加失败");
    }
    @Test
    public void testQueryForList(){
        // 1. 编写SQL语句
        String sql = "SELECT * FROM class";
        // 2. 调用queryForList方法执行SQL
        // 返回List集合，集合里面存放Map对象
        // 每行一个map，每个map 包含key列名，value值
       List<Map<String, Object>> maps = jdbcTemplate.queryForList(sql);
       // 3. 遍历每条map，打印
        for(Map<String, Object>map :maps){
            System.out.println(map);
        }
    }
    @Test
    public void testQuery(){
        // 1. 编写sql语句
        String sql = "select id,name,floor,teacher_id from classes where name like ?";
        // 2. 调用query方法
        List<Classes> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Classes.class),"%3%");
        // 3. 打印
        for(Classes classes :list){
            System.out.println(classes);
        }
    }
    @Test
    public void testQueryForObject(){
        // 1. 执行sql
        String sql = "select count(id) from classes";
        // 2. 调用jdbcTemplate的queryForObject方法
        Long total = jdbcTemplate.queryForObject(sql, long.class);
        // 3. 输出查询结果
        System.out.println(total);
    }
    @Test
    public void testLog(){
        log.debug("杰米多多的日志调试");
        log.info("打印info普通业务信息");
        log.trace("打印trace");
    }

}










