package mvcdemo.springmvcdemo1.mapper;

import mvcdemo.springmvcdemo1.Classes;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ClassesMapper {
    @Select("select  * from classes")
    public List<Classes> getClassesAll();
}
