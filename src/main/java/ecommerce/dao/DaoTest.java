package ecommerce.dao;

public class DaoTest {
    public static void main(String[] args){
        UserDao userDao = new UserDaoImpl();
        userDao.save();
    }
}
