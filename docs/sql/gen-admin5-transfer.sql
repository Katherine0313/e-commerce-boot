-- 把生产的 admin id=5（客服2，已禁用）拼成一条 INSERT，供管道搬运到本地库。
-- 结果用 --batch --skip-column-names 输出，直接喂给本地 mysql，不落盘、不打印。
SELECT CONCAT(
  'INSERT INTO admin (id, username, password, name, phone, status, create_time, update_time) VALUES (',
  id, ',', QUOTE(username), ',', QUOTE(password), ',', QUOTE(name), ',',
  IFNULL(QUOTE(phone), 'NULL'), ',', status, ',',
  QUOTE(DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s')), ',',
  QUOTE(DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s')), ');'
)
FROM admin WHERE id = 5;
