-- 商品副标题（sub_title）优化：商品名（型号）+ 完整卖点，35 字以内，电商 3C 副标题风格
-- 执行方式（Windows 客户端务必带 utf8mb4 防中文乱码）：
--   mysql -u root -p --default-character-set=utf8mb4 e_commerce_db < docs/sql/product-subtitle.sql

UPDATE `products` SET `sub_title` = 'iPhone 15 Pro A17 Pro芯片 钛金属 Pro级摄像' WHERE `id` = 1;
UPDATE `products` SET `sub_title` = 'Pura 70 Ultra 麒麟9010芯片 1英寸伸缩主摄 卫星通信' WHERE `id` = 2;
UPDATE `products` SET `sub_title` = 'Mate 60 Pro 麒麟9000s芯片 XMAGE影像 卫星通话' WHERE `id` = 3;
UPDATE `products` SET `sub_title` = '小米14 Pro 骁龙8 Gen3 徕卡可变光圈 120W快充' WHERE `id` = 4;
UPDATE `products` SET `sub_title` = '小米15 Pro 骁龙8 Elite 徕卡光学镜头 6100mAh电池' WHERE `id` = 5;
UPDATE `products` SET `sub_title` = '小米15 骁龙8 Elite 徕卡三摄 5400mAh电池' WHERE `id` = 6;
UPDATE `products` SET `sub_title` = '小米12 骁龙8 120Hz高刷直屏 5000万主摄' WHERE `id` = 7;
UPDATE `products` SET `sub_title` = 'iPhone 15 A16仿生芯片 4800万双摄 USB-C接口' WHERE `id` = 8;
UPDATE `products` SET `sub_title` = 'MacBook Pro M系列自研芯片 高色准原色屏 超长续航' WHERE `id` = 9;
UPDATE `products` SET `sub_title` = 'MacBook Pro 14 M4 Pro芯片 XDR屏 18小时续航' WHERE `id` = 10;
UPDATE `products` SET `sub_title` = 'MacBook Air 15 M4芯片 15.3英寸大屏 无风扇静音' WHERE `id` = 11;
UPDATE `products` SET `sub_title` = 'MacBook Pro 16 M4 Max芯片 专业级工作站性能' WHERE `id` = 12;
UPDATE `products` SET `sub_title` = 'MacBook Air M3 M3芯片 无风扇静音 超长续航' WHERE `id` = 13;
UPDATE `products` SET `sub_title` = 'iPad Air M2芯片 支持妙控键盘 轻薄全能' WHERE `id` = 14;
UPDATE `products` SET `sub_title` = 'iPad Pro 12.9 M2芯片 XDR屏 ProMotion高刷' WHERE `id` = 15;
UPDATE `products` SET `sub_title` = '联想Tab P12 12.7英寸3K大屏 JBL四扬声器 杜比全景声' WHERE `id` = 16;
UPDATE `products` SET `sub_title` = 'iPad 10代 A14仿生芯片 10.9英寸全面屏' WHERE `id` = 17;
UPDATE `products` SET `sub_title` = 'AirPods Pro 主动降噪 自适应音频 个性化空间音频' WHERE `id` = 18;
UPDATE `products` SET `sub_title` = '索尼WH-1000XM5 HD降噪处理器 30小时续航 LDAC高解析' WHERE `id` = 19;
UPDATE `products` SET `sub_title` = '索尼WH-CH720N 轻量化设计 35小时续航 多点连接' WHERE `id` = 20;
UPDATE `products` SET `sub_title` = '索尼SRS-XB33 EXTRA BASS IP67防水 24小时续航' WHERE `id` = 21;
UPDATE `products` SET `sub_title` = 'AirPods Max H2芯片 主动降噪 沉浸式空间音频' WHERE `id` = 22;
UPDATE `products` SET `sub_title` = '索尼LinkBuds S 轻巧舒适 智能免摘对话 20小时续航' WHERE `id` = 23;
UPDATE `products` SET `sub_title` = 'AirPods 4 H2芯片 个性化空间音频 30小时续航' WHERE `id` = 24;
UPDATE `products` SET `sub_title` = '索尼SRS-XG300 MEGA BASS IP67防水 25小时续航' WHERE `id` = 25;
UPDATE `products` SET `sub_title` = 'Watch Ultra 49mm钛金属 100米防水 双频GPS' WHERE `id` = 26;
UPDATE `products` SET `sub_title` = 'Watch Ultimate 100米深潜防水 双频GPS 14天续航' WHERE `id` = 27;
UPDATE `products` SET `sub_title` = 'Watch Series 9 S9芯片 血氧心率监测 抬手即用' WHERE `id` = 28;
UPDATE `products` SET `sub_title` = 'Watch Fit 3 1.82英寸AMOLED大屏 100+运动模式' WHERE `id` = 29;
UPDATE `products` SET `sub_title` = 'Watch GT 4 14天超长续航 100+运动模式 科学睡眠' WHERE `id` = 30;
UPDATE `products` SET `sub_title` = '尼康Z8 4571万像素 8K30p视频 120fps连拍' WHERE `id` = 31;
UPDATE `products` SET `sub_title` = '尼康Zf 复古设计 2450万像素 8级防抖' WHERE `id` = 32;
