package ecommerce;

import ecommerce.dao.UserDaoImpl;
import ecommerce.pojo.Person;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;



@SpringBootTest
class ApplicationTests {
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
