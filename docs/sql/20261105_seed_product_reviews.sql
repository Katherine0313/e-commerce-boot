-- 商品评价种子数据：每个上架商品补 2 条虚拟评价（按商品品类生成差异化文案）
-- 用途：商品详情页「商品评价」tab、星级统计、有图筛选的展示/联调数据
--
-- 说明：
--   1) 只处理 products.listing_status = 1 的商品；
--   2) 每条商品 2 条：主评价带晒图，副评价纯文字；星级 5/4 为主，每 10 个商品出现 3 星中评、每 20 个出现 2 星差评；
--   3) 文案按商品品类（手机/笔记本/平板/耳机/音箱/手表/相机/手机壳/外设/存储/充电/扫地机/智能家居/机械表）定制，
--      手机讲拍照续航、笔记本讲性能散热、耳机讲降噪音质……并拼接具体商品名，保证每条评价内容不同；
--   4) 走后台已有的虚拟评价机制：order_id=0、user_id=0、is_virtual=1、created_by=1（admin）；
--      C 端当前会把 is_virtual=1 的昵称显示为「系统」，后台可按「虚拟评价」筛选和清理；
--   5) created_at 用固定基准日 + 商品 id 偏移，不依赖 MySQL 服务器时区；
--   6) 幂等：执行前先删除本脚本上一版种子（is_virtual=1 且 created_at 在下面时间范围内），再重新插入。
-- 注意：本地执行后必须同步生产库（项目约定）。

DROP TEMPORARY TABLE IF EXISTS tmp_review_seed;
CREATE TEMPORARY TABLE tmp_review_seed (
    product_id BIGINT PRIMARY KEY,
    category   VARCHAR(16) NOT NULL,
    pros       VARCHAR(255) NOT NULL DEFAULT '',
    cons       VARCHAR(255) NOT NULL DEFAULT '',
    bad        VARCHAR(255) NOT NULL DEFAULT '',
    img1       VARCHAR(128) NOT NULL DEFAULT '',
    img2       VARCHAR(128) NOT NULL DEFAULT ''
);

-- 品类判定：顺序很关键（手机壳要在手机前，平板电脑要在笔记本前，机械键盘要在机械表前）
INSERT INTO tmp_review_seed (product_id, category)
SELECT p.id,
       CASE
           WHEN p.name LIKE '%手机壳%' OR p.name LIKE '%保护壳%' OR p.name LIKE '%防摔壳%'
                OR p.name LIKE '%透明壳%' OR p.name LIKE '%磁吸壳%' THEN 'case'
           WHEN p.name LIKE '%平板%' THEN 'tablet'
           WHEN p.name LIKE '%手机%' THEN 'phone'
           WHEN p.name LIKE '%电脑%' OR p.name LIKE '%笔记本%' THEN 'laptop'
           WHEN p.name LIKE '%鼠标%' OR p.name LIKE '%键盘%' OR p.name LIKE '%扩展坞%'
                OR p.name LIKE '%Hub%' THEN 'peripheral'
           WHEN p.name LIKE '%机械表%' OR p.name LIKE '%机械男表%' OR p.name LIKE '%自动机械%'
                OR p.name LIKE '%石英表%' THEN 'mwatch'
           WHEN p.name LIKE '%手表%' OR p.name LIKE '%Watch%' THEN 'swatch'
           WHEN p.name LIKE '%耳机%' THEN 'earphone'
           WHEN p.name LIKE '%音箱%' THEN 'speaker'
           WHEN p.name LIKE '%相机%' THEN 'camera'
           WHEN p.name LIKE '%硬盘%' OR p.name LIKE '%SD卡%' OR p.name LIKE '%TF卡%'
                OR p.name LIKE '%U盘%' OR p.name LIKE '%读卡器%' THEN 'storage'
           WHEN p.name LIKE '%充电%' OR p.name LIKE '%移动电源%' THEN 'charger'
           WHEN p.name LIKE '%扫地%' OR p.name LIKE '%扫拖%' THEN 'cleaner'
           WHEN p.name LIKE '%摄像头%' OR p.name LIKE '%传感器%' OR p.name LIKE '%门磁%'
                OR p.name LIKE '%台灯%' OR p.name LIKE '%灯泡%' OR p.name LIKE '%灯带%'
                OR p.name LIKE '%开关%' OR p.name LIKE '%吸顶灯%' OR p.name LIKE '%氛围灯%' THEN 'smarthome'
           ELSE 'other'
       END
FROM products p
WHERE p.listing_status = 1;

-- 每个品类的共性评价短语（优点 / 小缺点 / 差评原因）
UPDATE tmp_review_seed
SET pros = CASE category
               WHEN 'phone'      THEN '屏幕和拍照表现都不错，系统也流畅'
               WHEN 'laptop'     THEN '性能释放足，屏幕和键盘手感都好'
               WHEN 'tablet'     THEN '屏幕清晰，追剧和记笔记都方便'
               WHEN 'earphone'   THEN '音质清晰，降噪和佩戴舒适度都不错'
               WHEN 'speaker'    THEN '低音有力量，声场和氛围感都不错'
               WHEN 'swatch'     THEN '屏幕清晰，运动和健康监测都实用'
               WHEN 'mwatch'     THEN '表盘做工精致，走时也比较稳定'
               WHEN 'camera'     THEN '画质细腻，对焦和色彩表现都不错'
               WHEN 'case'       THEN '开孔精准，贴合度和手感都不错'
               WHEN 'peripheral' THEN '手感扎实，连接稳定，响应也快'
               WHEN 'storage'    THEN '读写速度快，兼容性和发热控制都不错'
               WHEN 'charger'    THEN '充电速度快，体积小巧，发热控制也不错'
               WHEN 'cleaner'    THEN '清扫路线聪明，吸力和避障都够用'
               WHEN 'smarthome'  THEN '配网简单，联动灵敏，做工也小巧'
               ELSE '做工和描述一致，日常使用没问题'
           END,
    cons = CASE category
               WHEN 'phone'      THEN '玩游戏久了会有点温热'
               WHEN 'laptop'     THEN '高负载时风扇声音偏大'
               WHEN 'tablet'     THEN '部分应用横屏适配一般'
               WHEN 'earphone'   THEN '戴久了耳朵会有点胀'
               WHEN 'speaker'    THEN '音量开大时高音有点刺'
               WHEN 'swatch'     THEN '续航大概两天一充'
               WHEN 'mwatch'     THEN '表带偏硬，需要戴几天适应'
               WHEN 'camera'     THEN '电池续航偏短'
               WHEN 'case'       THEN '用久了边缘容易沾灰'
               WHEN 'peripheral' THEN '驱动软件偶尔识别慢'
               WHEN 'storage'    THEN '连续写入大文件会降速'
               WHEN 'charger'    THEN '满载时外壳有点烫'
               WHEN 'cleaner'    THEN '拖地后水痕比较明显'
               WHEN 'smarthome'  THEN 'App 偶尔会有延迟'
               ELSE '包装比较简单'
           END,
    bad = CASE category
              WHEN 'phone'      THEN '拍照偏色、系统偶尔卡顿，续航也一般'
              WHEN 'laptop'     THEN '屏幕有轻微漏光，玩游戏会降频'
              WHEN 'tablet'     THEN '触控偶尔不灵，续航掉得快'
              WHEN 'earphone'   THEN '左耳偶尔断连，通话对方听不清'
              WHEN 'speaker'    THEN '蓝牙偶尔卡顿，音量大了会破音'
              WHEN 'swatch'     THEN '连接手机偶尔断开，睡眠监测不太准'
              WHEN 'mwatch'     THEN '表盘有轻微划痕，走时不太稳定'
              WHEN 'camera'     THEN '对焦偶尔拉风箱，高感噪点多'
              WHEN 'case'       THEN '开孔有偏差，按键偏硬，磁吸也一般'
              WHEN 'peripheral' THEN '用了几天出现双击或断连'
              WHEN 'storage'    THEN '实际速度低于标称，偶尔识别不到'
              WHEN 'charger'    THEN '接触不良，充电时断时续'
              WHEN 'cleaner'    THEN '避障经常撞家具，拖地也不干净'
              WHEN 'smarthome'  THEN '经常离线，联动触发不稳定'
              ELSE '细节做工和描述有差距'
          END;

-- 每个品类两张买家晒图：img1 给主评价，img2 给副评价（文件在 upload/image/review/，生产同一目录）
UPDATE tmp_review_seed
SET img1 = CASE category
               WHEN 'phone'      THEN '/image/review/buyer-phone-1.webp'
               WHEN 'laptop'     THEN '/image/review/buyer-laptop-1.webp'
               WHEN 'tablet'     THEN '/image/review/buyer-tablet-1.webp'
               WHEN 'earphone'   THEN '/image/review/buyer-earphone-1.webp'
               WHEN 'speaker'    THEN '/image/review/buyer-speaker-1.webp'
               WHEN 'swatch'     THEN '/image/review/buyer-swatch-1.webp'
               WHEN 'mwatch'     THEN '/image/review/buyer-mwatch-1.webp'
               WHEN 'camera'     THEN '/image/review/buyer-camera-1.webp'
               WHEN 'case'       THEN '/image/review/buyer-case-1.webp'
               WHEN 'peripheral' THEN '/image/review/buyer-peripheral-1.webp'
               WHEN 'storage'    THEN '/image/review/buyer-storage-1.webp'
               WHEN 'charger'    THEN '/image/review/buyer-charger-1.webp'
               WHEN 'cleaner'    THEN '/image/review/buyer-cleaner-1.webp'
               WHEN 'smarthome'  THEN '/image/review/buyer-smarthome-1.webp'
               ELSE '/image/review/buyer-peripheral-1.webp'
           END,
    img2 = CASE category
               WHEN 'phone'      THEN '/image/review/buyer-phone-2.webp'
               WHEN 'laptop'     THEN '/image/review/buyer-laptop-2.webp'
               WHEN 'tablet'     THEN '/image/review/buyer-tablet-2.webp'
               WHEN 'earphone'   THEN '/image/review/buyer-earphone-2.webp'
               WHEN 'speaker'    THEN '/image/review/buyer-speaker-2.webp'
               WHEN 'swatch'     THEN '/image/review/buyer-swatch-2.webp'
               WHEN 'mwatch'     THEN '/image/review/buyer-mwatch-2.webp'
               WHEN 'camera'     THEN '/image/review/buyer-camera-2.webp'
               WHEN 'case'       THEN '/image/review/buyer-case-2.webp'
               WHEN 'peripheral' THEN '/image/review/buyer-peripheral-2.webp'
               WHEN 'storage'    THEN '/image/review/buyer-storage-2.webp'
               WHEN 'charger'    THEN '/image/review/buyer-charger-2.webp'
               WHEN 'cleaner'    THEN '/image/review/buyer-cleaner-2.webp'
               WHEN 'smarthome'  THEN '/image/review/buyer-smarthome-2.webp'
               ELSE '/image/review/buyer-peripheral-2.webp'
           END;

-- 删除上一版种子（时间范围只覆盖本脚本的固定基准日，不会误删后台之后造的评价）
DELETE FROM product_reviews
WHERE is_virtual = 1
  AND created_at >= '2026-09-20 00:00:00'
  AND created_at <= '2026-11-04 23:59:59';

-- 主评价：5 星好评（按 id 轮换 3 套品类文案）/ 3 星中评 / 2 星差评，带商品晒图
INSERT INTO product_reviews (order_id, user_id, product_id, product_name, product_image,
                             rating, tags, content, images, status, is_deleted,
                             is_virtual, created_by, created_at, updated_at)
SELECT 0, 0, p.id, p.name, p.image,
       CASE WHEN p.id % 20 = 0 THEN 2 WHEN p.id % 10 = 0 THEN 3 ELSE 5 END,
       CASE WHEN p.id % 20 = 0 THEN JSON_ARRAY('与描述不符', '包装破损')
            WHEN p.id % 10 = 0 THEN JSON_ARRAY('有待改进', '物流慢')
            WHEN p.id % 3 = 0 THEN JSON_ARRAY('质量很好', '物流很快')
            WHEN p.id % 3 = 1 THEN JSON_ARRAY('质量很好', '服务周到')
            ELSE JSON_ARRAY('质量很好', '性价比高') END,
       CASE
           WHEN p.id % 20 = 0 THEN
               CONCAT(p.name, ' 收到后有点失望，', t.bad, '，和预期有差距。')
           WHEN p.id % 10 = 0 THEN
               CONCAT(p.name, ' 整体还行，', t.pros, '，但', t.cons, '，这个价位算中规中矩。')
           ELSE
               CASE t.category
                   WHEN 'phone' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 入手两周，屏幕观感细腻，拍照人像和夜景都很稳，充电快，日常使用完全不卡，续航一天没问题。')
                           WHEN 1 THEN CONCAT('对比了几款最后选了', p.name, '，长焦拍照很出片，系统流畅，信号比旧手机好，握持手感也舒服。')
                           ELSE CONCAT(p.name, ' 的影像能力确实在线，随手拍色彩自然，高刷屏很顺滑，电池耐用，这个价位很值。')
                       END
                   WHEN 'laptop' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 开机很快，屏幕色彩准，键盘敲着舒服，跑设计软件和游戏都稳，散热控制得不错。')
                           WHEN 1 THEN CONCAT('工作用', p.name, ' 很合适，性能释放足，多开网页和办公软件不卡，机身比想象中轻，出差方便。')
                           ELSE CONCAT(p.name, ' 的屏幕和做工都满意，风扇声音小，接口够用，剪视频渲染比旧电脑快很多。')
                       END
                   WHEN 'tablet' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 屏幕大且清晰，追剧和看文档很舒服，手写笔跟手，记笔记方便，重量控制得不错。')
                           WHEN 1 THEN CONCAT('给孩子上网课买的', p.name, '，系统流畅，护眼模式好用，外放声音够大，续航能撑一天。')
                           ELSE CONCAT(p.name, ' 看漫画和轻办公都合适，分屏实用，金属机身质感好，充电速度也可以。')
                       END
                   WHEN 'earphone' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 降噪效果很惊喜，通勤路上安静不少，音质人声清晰，佩戴轻，连接稳定。')
                           WHEN 1 THEN CONCAT('试听了一周', p.name, '，低音有弹性，通话降噪好用，耳机盒小巧，续航和充电速度都满意。')
                           ELSE CONCAT(p.name, ' 佩戴舒适不夹耳，音质通透，打游戏延迟低，双设备切换很方便，值得推荐。')
                       END
                   WHEN 'speaker' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 低音很有力量，客厅放歌氛围感拉满，蓝牙连接快，续航也够用。')
                           WHEN 1 THEN CONCAT(p.name, ' 音质比预期好，人声和乐器都清晰，体积不大但声音很足，户外带着也方便。')
                           ELSE CONCAT('入手', p.name, ' 后每天下班都放歌，声场宽，做工精致，按键手感好，很满意。')
                       END
                   WHEN 'swatch' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 屏幕清晰，运动记录准确，心率监测好用，续航比预期长，表带戴着舒服。')
                           WHEN 1 THEN CONCAT('跑步一直戴', p.name, '，GPS 定位快，配速和心率数据准，抬腕亮屏灵敏，和手机联动方便。')
                           ELSE CONCAT(p.name, ' 外观精致，表盘可换，消息提醒及时，睡眠监测有参考价值，整体很满意。')
                       END
                   WHEN 'mwatch' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 表盘做工精致，机芯走时稳定，戴在手上很有质感，包装也体面。')
                           WHEN 1 THEN CONCAT('对比了很久选了', p.name, '，自动上链顺畅，走时误差小，表带柔软，日常通勤百搭。')
                           ELSE CONCAT(p.name, ' 实物比图片好看，表壳打磨细腻，夜光清晰，送人自用都合适。')
                       END
                   WHEN 'camera' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 对焦很快，画质细腻，暗光噪点控制好，色彩讨喜，握持和按键布局顺手。')
                           WHEN 1 THEN CONCAT('拍了一组人像，', p.name, ' 的虚化和肤色表现很自然，视频防抖稳，屏幕取景清晰。')
                           ELSE CONCAT(p.name, ' 连拍和追焦都强，镜头素质高，机身防抖有用，出门扫街很省心。')
                       END
                   WHEN 'case' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 开孔精准，贴合度高，手感细腻不沾指纹，装上后按键很好按。')
                           WHEN 1 THEN CONCAT(p.name, ' 做工比想象中好，边角有缓冲，磁吸很稳，摔了一次手机没事。')
                           ELSE CONCAT('给新手机配的', p.name, '，颜色好看，厚度控制不错，无线充电不受影响。')
                       END
                   WHEN 'peripheral' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 手感很好，按键反馈干脆，连接稳定，长时间使用手腕也不累。')
                           WHEN 1 THEN CONCAT(p.name, ' 响应速度快，做工扎实，驱动可自定义，办公和游戏都够用。')
                           ELSE CONCAT('换了', p.name, ' 后效率高不少，无线连接没有延迟，续航也长。')
                       END
                   WHEN 'storage' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 读写速度很快，拷大文件不用等，做工小巧，兼容性也好。')
                           WHEN 1 THEN CONCAT('给相机和电脑扩容买的', p.name, '，速度稳定，发热控制不错，容量够用。')
                           ELSE CONCAT(p.name, ' 插上就能用，实际速度接近标称，外壳结实，携带方便。')
                       END
                   WHEN 'charger' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 充电速度很快，体积小巧，出差带着方便，发热控制也不错。')
                           WHEN 1 THEN CONCAT('一个', p.name, ' 能同时充手机和平板，功率分配合理，插脚稳，做工扎实。')
                           ELSE CONCAT(p.name, ' 给笔记本充电也够用，线材质量好，充满自动断电，很省心。')
                       END
                   WHEN 'cleaner' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 清扫路线规划聪明，边角也能扫到，拖地干净，回充准确，解放双手。')
                           WHEN 1 THEN CONCAT('家里养猫，', p.name, ' 吸毛效果好，避障灵敏，噪音可接受，App 操作方便。')
                           ELSE CONCAT(p.name, ' 建图快，分区清扫实用，越障能力不错，滚刷清理也方便。')
                       END
                   WHEN 'smarthome' THEN
                       CASE p.id % 3
                           WHEN 0 THEN CONCAT(p.name, ' 安装简单，App 配网一次成功，联动场景好用，反应灵敏。')
                           WHEN 1 THEN CONCAT(p.name, ' 和家里其它智能设备联动很顺，语音控制方便，做工小巧不占地方。')
                           ELSE CONCAT('装了', p.name, ' 后方便很多，夜灯和定时功能实用，信号稳定。')
                       END
                   ELSE CONCAT(p.name, ' 做工不错，和描述一致，使用体验满意，物流也快。')
               END
       END,
       CONCAT('[{"url":"', t.img1, '","type":"image"}]'),
       1, 0, 1, 1,
       DATE_ADD('2026-09-20 10:00:00', INTERVAL (p.id % 41) DAY),
       DATE_ADD('2026-09-20 10:00:00', INTERVAL (p.id % 41) DAY)
FROM products p
JOIN tmp_review_seed t ON t.product_id = p.id
WHERE p.listing_status = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_reviews r
      WHERE r.product_id = p.id
        AND r.is_virtual = 1
        AND r.created_at = DATE_ADD('2026-09-20 10:00:00', INTERVAL (p.id % 41) DAY)
  );

-- 副评价：4 星为主（每 10 个商品出现一条 5 星回购），纯文字，结合品类特性
INSERT INTO product_reviews (order_id, user_id, product_id, product_name, product_image,
                             rating, tags, content, images, status, is_deleted,
                             is_virtual, created_by, created_at, updated_at)
SELECT 0, 0, p.id, p.name, p.image,
       CASE WHEN p.id % 20 = 0 THEN 4 WHEN p.id % 10 = 0 THEN 5 ELSE 4 END,
       CASE WHEN p.id % 3 = 0 THEN JSON_ARRAY('性价比高', '服务周到')
            WHEN p.id % 3 = 1 THEN JSON_ARRAY('性价比高', '物流很快')
            ELSE JSON_ARRAY('性价比高', '质量很好') END,
       CASE
           WHEN p.id % 20 = 0 THEN
               CONCAT(p.name, ' 出了点小问题，客服补发了配件，态度还行，', t.pros, '，给四星。')
           WHEN p.id % 10 = 0 THEN
               CONCAT('第二次买', p.name, ' 了，品质稳定，', t.pros, '，家里人都说不错。')
           ELSE
               CONCAT(p.name, ' 整体满意，', t.pros, '，就是', t.cons, '，不影响使用。')
       END,
       CONCAT('[{"url":"', t.img2, '","type":"image"}]'),
       1, 0, 1, 1,
       DATE_ADD('2026-10-20 15:00:00', INTERVAL (p.id % 15) DAY),
       DATE_ADD('2026-10-20 15:00:00', INTERVAL (p.id % 15) DAY)
FROM products p
JOIN tmp_review_seed t ON t.product_id = p.id
WHERE p.listing_status = 1
  AND NOT EXISTS (
      SELECT 1 FROM product_reviews r
      WHERE r.product_id = p.id
        AND r.is_virtual = 1
        AND r.created_at = DATE_ADD('2026-10-20 15:00:00', INTERVAL (p.id % 15) DAY)
  );

DROP TEMPORARY TABLE IF EXISTS tmp_review_seed;
