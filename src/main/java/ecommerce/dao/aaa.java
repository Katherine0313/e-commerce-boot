package ecommerce.dao;

public class aaa {
    // 成员属性
    int age = 18;
    String name = "宗杰";
    int birth;
    // setter方法

    public void setBirth(int birth) {
        this.birth = birth;
    }

    @Override
    public String toString() {
        return "aaa{" +
                "age=" + age +
                ", name='" + name + '\'' +
                ", birth=" + birth +
                '}';
    }
}
