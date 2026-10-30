-- 机械表参数修正（10-mechanical-watches.sql 已同步修正，此脚本用于已执行过 10 的库）
-- 127 天梭 T006：Powermatic 80，动力 38→80 小时；标题「精钢表盘」→「精钢表壳」
-- 128 浪琴 L2.628：L888 机芯，动力 42→64 小时
-- 131 美度 M8600：Caliber 80，动力 38→80 小时
-- 134 飞亚达 GA802057：表盘 40→41mm
-- 136 雷达 R30939102（查无此型号）改为可核实的 R30939103：自动机械、38mm、精钢、30 米防水
SET NAMES utf8mb4;

UPDATE products SET
  title = '天梭力洛克 T006 机械男表 自动机械机芯 39.3mm精钢表壳 30米防水 80小时动力 官方正品 现货速发',
  specs = '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"39.3mm"},{"name":"防水","value":"30米"},{"name":"表带材质","value":"精钢"},{"name":"动力储存","value":"80小时"}]'
WHERE id = 127;

UPDATE products SET
  title = '浪琴名匠 L2.628 机械男表 自动机械机芯 38.5mm真皮表带 蓝钢指针 64小时动力 官方正品 现货速发',
  specs = '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"38.5mm"},{"name":"防水","value":"30米"},{"name":"表带材质","value":"真皮"},{"name":"动力储存","value":"64小时"}]'
WHERE id = 128;

UPDATE products SET
  title = '美度贝伦赛丽 M8600 机械表 自动机械机芯 38mm真皮表带 50米防水 80小时动力 官方正品 现货速发',
  specs = '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"38mm"},{"name":"防水","value":"50米"},{"name":"表带材质","value":"真皮"},{"name":"动力储存","value":"80小时"}]'
WHERE id = 131;

UPDATE products SET
  title = '飞亚达经典系列 GA802057 机械男表 自动机械机芯 41mm真皮表带 50米防水 40小时动力 官方正品 现货速发',
  specs = '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"41mm"},{"name":"防水","value":"50米"},{"name":"表带材质","value":"真皮"},{"name":"动力储存","value":"40小时"}]'
WHERE id = 134;

UPDATE products SET
  name = '雷达晶萃 R30939103 自动机械男表',
  title = '雷达晶萃 R30939103 自动机械男表 38mm精钢表带 30米防水 蓝宝石镜面 官方正品 现货速发',
  specs = '[{"name":"机芯","value":"自动机械"},{"name":"表盘直径","value":"38mm"},{"name":"防水","value":"30米"},{"name":"表带材质","value":"精钢"},{"name":"镜面","value":"蓝宝石"}]'
WHERE id = 136;
