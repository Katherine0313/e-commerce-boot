START TRANSACTION ;

UPDATE user SET money = money - 100 WHERE name = '张三';
COMMIT ;
SELECT * FROM user;

ROLLBACK ;
