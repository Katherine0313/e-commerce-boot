package ecommerce;

import ecommerce.mapper.ClassesMapper;
import ecommerce.pojo.Classes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class TestMybatisXml {
    @Autowired
    private ClassesMapper classesMapper;
    @Test
    public void testFindClassesByFloor(){
        List<Classes> all = classesMapper.findClassesByFloor("%3%");
        for(Classes classes :all){
            System.out.println(classes);
        }
    }
    @Test
    public void testGetClassesAll(){
        List<Classes> all= classesMapper.getClassesAll();
        for(Classes classes :all){
            System.out.println(classes);
        }

    }
    @Test
    public void testInsertClasses(){
       int rows = classesMapper.insertClasses();
        System.out.println("插入的行数为："  + rows);
    }
    @Test
    public void testUpdateClasses(){
        int rows = classesMapper.updateClasses();
        System.out.println("更新的行数为" + rows);
    }
    @Test
    public void testDeleteClasses(){
        int rows = classesMapper.deleteClasses();
        System.out.println("删除的行数为：" + rows);
    }


}
