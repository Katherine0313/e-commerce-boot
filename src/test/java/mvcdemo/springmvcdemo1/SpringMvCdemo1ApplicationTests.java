package mvcdemo.springmvcdemo1;

import mvcdemo.springmvcdemo1.dao.UserDaoImpl;
import mvcdemo.springmvcdemo1.pojo.Person;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;



@SpringBootTest
class SpringMvCdemo1ApplicationTests {
    @Autowired
    UserDaoImpl userDao;
    @Test
    void contextLoads() {
    }
    @Test
    void saveTest(){
        userDao.save();
        System.out.println(userDao);
    }

    @Autowired
    Person person;
    @Test
    void personTest(){
        System.out.println(person);
    }

}
