package ecommerce.dao;
import org.springframework.stereotype.Repository;

@Repository
public class UserDaoImpl implements UserDao{
private String name;
private Integer age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "UserDaoImpl{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
    @Override
    public void save() {
        System.out.println("杰米多多");
    }
}
