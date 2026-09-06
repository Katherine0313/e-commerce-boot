package mvcdemo.springmvcdemo1.mapper;

import mvcdemo.springmvcdemo1.pojo.Teacher;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TeacherMapper {
    @Select("select * from teacher")
    public List<Teacher> getTeacherAll();
   // 插入
    @Insert("insert into teacher (name, subject) VALUES ('王小花','软件工程') ")
    public int addTeacher();
    // 更新
    @Update("update teacher set name='灰太狼' where id=13")
    public int updateTeacher();
    // 删除
    @Delete("delete from teacher where id=20")
    public int deleteTeacher();
}
