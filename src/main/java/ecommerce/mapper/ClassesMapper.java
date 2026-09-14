package ecommerce.mapper;

import ecommerce.pojo.Classes;
import org.springframework.stereotype.Repository;


import java.util.List;
@Repository
public interface ClassesMapper {
    public List<Classes> getClassesAll();
    int insertClasses();
    int updateClasses();
    int deleteClasses();
}
