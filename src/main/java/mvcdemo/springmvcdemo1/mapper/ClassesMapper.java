package mvcdemo.springmvcdemo1.mapper;

import mvcdemo.springmvcdemo1.pojo.Classes;
import org.springframework.stereotype.Repository;


import java.util.List;
@Repository
public interface ClassesMapper {
    public List<Classes> getClassesAll();
    int insertClasses();
    int updateClasses();
    int deleteClasses();
}
