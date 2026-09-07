package mvcdemo.springmvcdemo1;

import mvcdemo.springmvcdemo1.mapper.ClassesMapper;
import mvcdemo.springmvcdemo1.mapper.TeacherMapper;
import mvcdemo.springmvcdemo1.pojo.Teacher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class TestMybatis {
    @Autowired
    private TeacherMapper teacherMapper;
    @Autowired
    private ClassesMapper classesMapper;
    @Test
    public void testGetTeacherAll(){
        List<Teacher> teacherAll  = teacherMapper.getTeacherAll();
        for(Teacher teacher :teacherAll){
            System.out.println(teacher);
        }
    }
    @Test
    public void testAddTeacher(){
       int row = teacherMapper.addTeacher();
        System.out.println(row > 0 ? "添加成功" : "添加失败");
    }
    @Test
    public void testUpdateTeacher(){
        int row = teacherMapper.updateTeacher();
        System.out.println(row > 0 ? "修改成功" : "修改失败");
    }
    @Test
    public void testDeleteTeacher(){
        int row = teacherMapper.deleteTeacher();
        System.out.println(row > 0 ? "删除成功" : "删除失败");
    }
    // 测试班级表
    @Test
    public void testGetClassesAll(){
       List<Classes> classesAll  = classesMapper.getClassesAll();
       for(Classes classes :classesAll){
           System.out.println(classes);
       }
    }


}
