package ecommerce.mapper;

import ecommerce.pojo.Classes;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
@Repository
public interface ClassesMapper {
    List<Classes> getClassesAll();
    int insertClasses();
    int updateClasses();
    int deleteClasses();
    List<Classes> findClassesByFloor(@Param("name") String name);

}
