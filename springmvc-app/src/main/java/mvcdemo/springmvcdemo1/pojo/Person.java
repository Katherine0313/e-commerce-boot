package mvcdemo.springmvcdemo1.pojo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;

@Component
public class Person {
    @Value("${person.name}")
    private String name;
    @Value("${person.age}")
    private Integer age;
    @Value("${user.salary}")
    private Integer Salary;
    @Value("${user.hobbies}")
    private String[] hobbies;
    @Value("${user.birth}")
    private Date birth;

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

    public Integer getSalary() {
        return Salary;
    }

    public void setSalary(Integer salary) {
        Salary = salary;
    }

    public String[] getHobbies() {
        return hobbies;
    }

    public void setHobbies(String[] hobbies) {
        this.hobbies = hobbies;
    }

    public Date getBirth() {
        return birth;
    }

    public void setBirth(Date birth) {
        this.birth = birth;
    }

    @Override
    public String toString() {
        return "Person{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", Salary=" + Salary +
                ", hobbies=" + Arrays.toString(hobbies) +
                ", birth=" + birth +
                '}';
    }
}
