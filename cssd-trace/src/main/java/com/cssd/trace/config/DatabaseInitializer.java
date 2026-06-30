package com.cssd.trace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DatabaseInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final boolean seedData;

    public DatabaseInitializer(JdbcTemplate jdbc, @Value("${cssd.seed-data:true}") boolean seedData) {
        this.jdbc = jdbc;
        this.seedData = seedData;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        migrateTables();
        if (seedData && isEmpty("cssd_department")) {
            seed();
        }
        if (seedData) {
            seedMissingReferenceData();
        }
    }

    private void migrateTables() {
        if (!columnExists("cssd_user", "password_hash")) {
            jdbc.execute("ALTER TABLE cssd_user ADD COLUMN password_hash varchar(128) NOT NULL DEFAULT '123456' AFTER user_name");
        }
        if (!columnExists("cssd_workflow_event", "event_seq")) {
            jdbc.execute("ALTER TABLE cssd_workflow_event ADD COLUMN event_seq bigint NOT NULL AUTO_INCREMENT UNIQUE FIRST");
        }
        // 为器械包定义补充追溯模式：手术室包按一包一码追溯，临床科室包按批次追溯。
        if (!columnExists("cssd_package_type", "tracking_mode")) {
            jdbc.execute("ALTER TABLE cssd_package_type ADD COLUMN tracking_mode varchar(20) NOT NULL DEFAULT 'UNIQUE' AFTER category");
        }
        // 为器械包定义补充业务范围，便于后台区分手术室包和普通临床科室包。
        if (!columnExists("cssd_package_type", "package_scope")) {
            jdbc.execute("ALTER TABLE cssd_package_type ADD COLUMN package_scope varchar(20) NOT NULL DEFAULT 'OR' AFTER tracking_mode");
        }
        // 为器械包定义补充包材引用，标签失效期按包材有效期自动计算。
        if (!columnExists("cssd_package_type", "packaging_id")) {
            jdbc.execute("ALTER TABLE cssd_package_type ADD COLUMN packaging_id varchar(32) AFTER validity_days");
        }
        // 为器械包实例补充来源批次，临床批量包打包后生成的标签要能反查回收批次。
        if (!columnExists("cssd_package_instance", "source_lot_id")) {
            jdbc.execute("ALTER TABLE cssd_package_instance ADD COLUMN source_lot_id varchar(32) AFTER current_batch_no");
        }
        // 审计动作名称会记录 UPDATE_ROLE_PERMISSION 等较长动作，旧库需要扩容。
        jdbc.execute("ALTER TABLE cssd_data_audit_log MODIFY COLUMN action varchar(60) NOT NULL");
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void createTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_department (
              id varchar(32) PRIMARY KEY,
              dept_code varchar(30) NOT NULL UNIQUE,
              dept_name varchar(60) NOT NULL,
              dept_type varchar(30) NOT NULL,
              barcode varchar(60) NOT NULL UNIQUE,
              status tinyint NOT NULL DEFAULT 1,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_user (
              id varchar(32) PRIMARY KEY,
              work_no varchar(30) NOT NULL UNIQUE,
              user_name varchar(40) NOT NULL,
              password_hash varchar(128) NOT NULL DEFAULT '123456',
              dept_id varchar(32) NOT NULL,
              role_code varchar(40) NOT NULL,
              user_type varchar(40) NOT NULL,
              status tinyint NOT NULL DEFAULT 1,
              login_method varchar(40) NOT NULL DEFAULT 'card',
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_role (
              id varchar(32) PRIMARY KEY,
              role_code varchar(40) NOT NULL UNIQUE,
              role_name varchar(60) NOT NULL,
              remark varchar(255),
              status tinyint NOT NULL DEFAULT 1,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_permission (
              id varchar(32) PRIMARY KEY,
              permission_code varchar(80) NOT NULL UNIQUE,
              permission_name varchar(80) NOT NULL,
              module_code varchar(40) NOT NULL,
              permission_type varchar(30) NOT NULL DEFAULT 'MENU',
              sort_no int NOT NULL DEFAULT 0,
              status tinyint NOT NULL DEFAULT 1
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_role_permission (
              id varchar(32) PRIMARY KEY,
              role_id varchar(32) NOT NULL,
              permission_id varchar(32) NOT NULL,
              UNIQUE KEY uk_role_perm(role_id, permission_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_instrument (
              id varchar(32) PRIMARY KEY,
              instrument_code varchar(30) NOT NULL UNIQUE,
              instrument_name varchar(60) NOT NULL,
              spec varchar(80),
              category varchar(40),
              unit varchar(20) NOT NULL DEFAULT '把'
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_package_type (
              id varchar(32) PRIMARY KEY,
              package_code varchar(30) NOT NULL UNIQUE,
              package_name varchar(80) NOT NULL,
              category varchar(40),
              tracking_mode varchar(20) NOT NULL DEFAULT 'UNIQUE',
              package_scope varchar(20) NOT NULL DEFAULT 'OR',
              sterilization_type tinyint NOT NULL,
              cleaning_type tinyint NOT NULL,
              validity_days int NOT NULL,
              packaging_id varchar(32),
              packaging_name varchar(50) NOT NULL,
              instrument_list json,
              assemble_image varchar(255),
              pack_image varchar(255),
              status tinyint NOT NULL DEFAULT 1
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_packaging (
              id varchar(32) PRIMARY KEY,
              packaging_code varchar(30) NOT NULL UNIQUE,
              packaging_name varchar(60) NOT NULL,
              validity_days int NOT NULL,
              status tinyint NOT NULL DEFAULT 1,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_package_instance (
              id varchar(32) PRIMARY KEY,
              instance_code varchar(40) NOT NULL UNIQUE,
              package_type_id varchar(32) NOT NULL,
              current_status varchar(40) NOT NULL,
              current_dept_id varchar(32),
              sterilization_expire_at datetime,
              borrowed tinyint NOT NULL DEFAULT 0,
              borrow_order_id varchar(32),
              current_batch_no varchar(40),
              source_lot_id varchar(32),
              flags json,
              locked tinyint NOT NULL DEFAULT 0,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              INDEX idx_pkg_status(current_status),
              INDEX idx_pkg_dept(current_dept_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_recycle_order_item (
              id varchar(32) PRIMARY KEY,
              recycle_order_id varchar(32) NOT NULL,
              order_no varchar(40) NOT NULL,
              line_no int NOT NULL,
              tracking_mode varchar(20) NOT NULL,
              package_type_id varchar(32) NOT NULL,
              package_instance_id varchar(32),
              package_code varchar(40),
              lot_id varchar(32),
              lot_no varchar(40),
              package_name varchar(80) NOT NULL,
              quantity int NOT NULL,
              abnormal_flag tinyint NOT NULL DEFAULT 0,
              abnormal_desc varchar(255),
              basket_code varchar(30),
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_recycle_item_order(recycle_order_id),
              INDEX idx_recycle_item_lot(lot_no),
              INDEX idx_recycle_item_pkg(package_code)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_trace_lot (
              id varchar(32) PRIMARY KEY,
              lot_no varchar(40) NOT NULL UNIQUE,
              package_type_id varchar(32) NOT NULL,
              dept_id varchar(32) NOT NULL,
              source_order_id varchar(32) NOT NULL,
              source_order_no varchar(40) NOT NULL,
              current_status varchar(40) NOT NULL,
              total_qty int NOT NULL,
              remaining_qty int NOT NULL,
              basket_code varchar(30),
              created_by varchar(32),
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              INDEX idx_lot_status(current_status),
              INDEX idx_lot_order(source_order_no)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_trace_lot_event (
              id varchar(32) PRIMARY KEY,
              lot_id varchar(32) NOT NULL,
              lot_no varchar(40) NOT NULL,
              event_type varchar(60) NOT NULL,
              station varchar(40) NOT NULL,
              qty_delta int NOT NULL DEFAULT 0,
              operator_id varchar(32),
              device_code varchar(60),
              payload json,
              occurred_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_lot_event_lot(lot_no),
              INDEX idx_lot_event_station(station)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_business_operation_log (
              id varchar(32) PRIMARY KEY,
              business_type varchar(40) NOT NULL,
              business_id varchar(32) NOT NULL,
              business_no varchar(40),
              action varchar(60) NOT NULL,
              operator_id varchar(32),
              client_type varchar(30) NOT NULL,
              device_code varchar(60),
              before_snapshot json,
              after_snapshot json,
              remark varchar(255),
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_oplog_business(business_type, business_id),
              INDEX idx_oplog_no(business_no)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_data_audit_log (
              id varchar(32) PRIMARY KEY,
              table_name varchar(80) NOT NULL,
              row_id varchar(32) NOT NULL,
              action varchar(60) NOT NULL,
              operator_id varchar(32),
              before_snapshot json,
              after_snapshot json,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_audit_table_row(table_name, row_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_print_template (
              id varchar(32) PRIMARY KEY,
              template_code varchar(40) NOT NULL UNIQUE,
              template_name varchar(80) NOT NULL,
              template_type varchar(40) NOT NULL,
              config_json json NOT NULL,
              status tinyint NOT NULL DEFAULT 1,
              created_by varchar(32),
              updated_by varchar(32),
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              INDEX idx_template_type(template_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_package_label (
              id varchar(32) PRIMARY KEY,
              label_no varchar(50) NOT NULL UNIQUE,
              label_type varchar(40) NOT NULL,
              package_type_id varchar(32) NOT NULL,
              package_instance_id varchar(32),
              source_lot_id varchar(32),
              package_name varchar(80) NOT NULL,
              content_snapshot json NOT NULL,
              assembler_id varchar(32),
              packer_id varchar(32),
              print_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              sterilization_date date NOT NULL,
              expire_date date NOT NULL,
              template_id varchar(32),
              status varchar(30) NOT NULL DEFAULT 'PRINTED',
              INDEX idx_label_package(package_instance_id),
              INDEX idx_label_lot(source_lot_id),
              INDEX idx_label_no(label_no)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_print_log (
              id varchar(32) PRIMARY KEY,
              template_id varchar(32),
              business_type varchar(40) NOT NULL,
              business_id varchar(32) NOT NULL,
              label_no varchar(50),
              printer_user_id varchar(32),
              print_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              payload_json json NOT NULL,
              INDEX idx_print_business(business_type, business_id),
              INDEX idx_print_label(label_no)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_equipment (
              id varchar(32) PRIMARY KEY,
              equipment_code varchar(30) NOT NULL UNIQUE,
              equipment_name varchar(60) NOT NULL,
              equipment_type tinyint NOT NULL,
              model varchar(80),
              location varchar(80),
              status tinyint NOT NULL DEFAULT 1,
              preset_programs varchar(255),
              default_bio_test tinyint NOT NULL DEFAULT 2
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_basket (
              id varchar(32) PRIMARY KEY,
              basket_code varchar(30) NOT NULL UNIQUE,
              status varchar(30) NOT NULL DEFAULT 'IDLE',
              current_batch_no varchar(40),
              package_list json,
              created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_recycle_order (
              id varchar(32) PRIMARY KEY,
              order_no varchar(40) NOT NULL UNIQUE,
              source_type tinyint NOT NULL,
              source_user varchar(80),
              recycle_user varchar(32),
              recycle_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              dept_id varchar(32),
              total_count int NOT NULL,
              return_count int NOT NULL DEFAULT 0,
              package_list json,
              abnormal_record json,
              remark text,
              basket_code varchar(30),
              video_url varchar(255)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_wash_record (
              id varchar(32) PRIMARY KEY,
              batch_no varchar(40) NOT NULL UNIQUE,
              equipment_id varchar(32) NOT NULL,
              program_name varchar(60),
              wash_type tinyint NOT NULL,
              operator_id varchar(32),
              start_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              end_time datetime,
              basket_list json,
              package_list json,
              param_data json,
              result tinyint,
              remark text
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_sterilization_record (
              id varchar(32) PRIMARY KEY,
              batch_no varchar(40) NOT NULL UNIQUE,
              equipment_id varchar(32) NOT NULL,
              program_name varchar(60),
              operator_id varchar(32),
              start_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              end_time datetime,
              package_list json,
              need_bio_test tinyint NOT NULL DEFAULT 1,
              bio_test_status tinyint NOT NULL DEFAULT 0,
              physical_result tinyint,
              chemical_result tinyint,
              param_data json,
              result tinyint,
              remark text
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_bio_test_record (
              id varchar(32) PRIMARY KEY,
              sterilization_batch_no varchar(40) NOT NULL,
              equipment_id varchar(32),
              test_date datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              indicator_batch varchar(80),
              result tinyint NOT NULL,
              operator_id varchar(32),
              input_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_distribute_order (
              id varchar(32) PRIMARY KEY,
              order_no varchar(40) NOT NULL UNIQUE,
              distribute_type tinyint NOT NULL,
              recycle_order_id varchar(32),
              operator_id varchar(32),
              distribute_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              dept_id varchar(32),
              package_list json,
              status tinyint NOT NULL DEFAULT 2
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_borrow_order (
              id varchar(32) PRIMARY KEY,
              order_no varchar(40) NOT NULL UNIQUE,
              dept_id varchar(32) NOT NULL,
              applicant_id varchar(32),
              apply_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              package_list json,
              urgent tinyint NOT NULL DEFAULT 0,
              status varchar(30) NOT NULL,
              audit_user_id varchar(32),
              audit_time datetime,
              reject_reason text,
              distribute_order_id varchar(32)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_config (
              config_key varchar(80) PRIMARY KEY,
              config_name varchar(120) NOT NULL,
              config_value varchar(255) NOT NULL,
              module_code varchar(40) NOT NULL,
              remark varchar(255),
              updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_workflow_event (
              event_seq bigint NOT NULL AUTO_INCREMENT UNIQUE,
              id varchar(32) PRIMARY KEY,
              package_instance_id varchar(32),
              package_code varchar(40) NOT NULL,
              event_type varchar(60) NOT NULL,
              station varchar(40) NOT NULL,
              operator_id varchar(32),
              dept_id varchar(32),
              batch_no varchar(40),
              payload json,
              occurred_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              upload_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              offline_flag tinyint NOT NULL DEFAULT 0,
              device_code varchar(60),
              INDEX idx_event_pkg(package_code),
              INDEX idx_event_batch(batch_no)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS cssd_pda_sync_log (
              id varchar(32) PRIMARY KEY,
              device_code varchar(60) NOT NULL,
              event_count int NOT NULL,
              success_count int NOT NULL,
              failed_count int NOT NULL,
              raw_payload json,
              synced_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }

    private boolean isEmpty(String tableName) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null || count == 0;
    }

    private void seed() {
        jdbc.update("INSERT INTO cssd_department(id, dept_code, dept_name, dept_type, barcode) VALUES(?,?,?,?,?)",
                "dept-cssd", "CSSD", "消毒供应中心", "CSSD", "DEPT-CSSD");
        jdbc.update("INSERT INTO cssd_department(id, dept_code, dept_name, dept_type, barcode) VALUES(?,?,?,?,?)",
                "dept-or", "OR", "手术室", "临床科室", "DEPT-OR");
        jdbc.update("INSERT INTO cssd_department(id, dept_code, dept_name, dept_type, barcode) VALUES(?,?,?,?,?)",
                "dept-icu", "ICU", "重症医学科", "临床科室", "DEPT-ICU");
        jdbc.update("INSERT INTO cssd_department(id, dept_code, dept_name, dept_type, barcode) VALUES(?,?,?,?,?)",
                "dept-surgery", "SURGERY", "普外科", "临床科室", "DEPT-SURGERY");

        jdbc.update("INSERT INTO cssd_user(id, work_no, user_name, dept_id, role_code, user_type) VALUES(?,?,?,?,?,?)",
                "user-admin", "A001", "系统管理员", "dept-cssd", "ADMIN", "管理员");
        jdbc.update("INSERT INTO cssd_user(id, work_no, user_name, dept_id, role_code, user_type) VALUES(?,?,?,?,?,?)",
                "user-head", "N001", "CSSD护士长", "dept-cssd", "HEAD_NURSE", "护士长");
        jdbc.update("INSERT INTO cssd_user(id, work_no, user_name, dept_id, role_code, user_type) VALUES(?,?,?,?,?,?)",
                "user-operator", "O001", "供应室操作员", "dept-cssd", "OPERATOR", "CSSD操作员");
        jdbc.update("INSERT INTO cssd_user(id, work_no, user_name, dept_id, role_code, user_type) VALUES(?,?,?,?,?,?)",
                "user-delivery", "D001", "下送人员", "dept-cssd", "DELIVERY", "下送人");

        jdbc.update("INSERT INTO cssd_instrument(id, instrument_code, instrument_name, spec, category, unit) VALUES(?,?,?,?,?,?)",
                "inst-needle", "INS-001", "持针器", "14cm", "基础器械", "把");
        jdbc.update("INSERT INTO cssd_instrument(id, instrument_code, instrument_name, spec, category, unit) VALUES(?,?,?,?,?,?)",
                "inst-scissor", "INS-002", "组织剪", "16cm", "基础器械", "把");
        jdbc.update("INSERT INTO cssd_instrument(id, instrument_code, instrument_name, spec, category, unit) VALUES(?,?,?,?,?,?)",
                "inst-forceps", "INS-003", "止血钳", "弯头", "基础器械", "把");
        jdbc.update("INSERT INTO cssd_instrument(id, instrument_code, instrument_name, spec, category, unit) VALUES(?,?,?,?,?,?)",
                "inst-lap", "INS-004", "腹腔镜镜头", "10mm", "腔镜器械", "支");

        jdbc.update("""
            INSERT INTO cssd_package_type
            (id, package_code, package_name, category, sterilization_type, cleaning_type, validity_days, packaging_name, instrument_list, assemble_image, pack_image)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, "ptype-lap", "PKT-LAP", "腹腔镜基础包", "腔镜包", 1, 1, 180, "无纺布",
                """
                [{"code":"INS-001","name":"持针器","qty":2},{"code":"INS-002","name":"组织剪","qty":1},{"code":"INS-004","name":"腹腔镜镜头","qty":1}]
                """, "标准配包图谱-腹腔镜", "标准打包图谱-腹腔镜");
        jdbc.update("""
            INSERT INTO cssd_package_type
            (id, package_code, package_name, category, sterilization_type, cleaning_type, validity_days, packaging_name, instrument_list, assemble_image, pack_image)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, "ptype-dress", "PKT-DRESS", "换药包", "基础包", 1, 2, 30, "纸塑袋",
                """
                [{"code":"INS-001","name":"持针器","qty":1},{"code":"INS-003","name":"止血钳","qty":2}]
                """, "标准配包图谱-换药包", "标准打包图谱-换药包");
        jdbc.update("""
            INSERT INTO cssd_package_type
            (id, package_code, package_name, category, sterilization_type, cleaning_type, validity_days, packaging_name, instrument_list, assemble_image, pack_image)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, "ptype-low", "PKT-LOW", "低温精密器械包", "精密包", 2, 1, 90, "硬质容器",
                """
                [{"code":"INS-002","name":"组织剪","qty":2},{"code":"INS-003","name":"止血钳","qty":1}]
                """, "标准配包图谱-低温包", "标准打包图谱-低温包");

        jdbc.update("INSERT INTO cssd_package_instance(id, instance_code, package_type_id, current_status, current_dept_id, flags) VALUES(?,?,?,?,?,?)",
                "pkg-0001", "PKG-0001", "ptype-lap", "IN_DEPT", "dept-or", "[\"加急\"]");
        jdbc.update("INSERT INTO cssd_package_instance(id, instance_code, package_type_id, current_status, current_dept_id, flags) VALUES(?,?,?,?,?,?)",
                "pkg-0002", "PKG-0002", "ptype-dress", "IN_DEPT", "dept-icu", "[]");
        jdbc.update("INSERT INTO cssd_package_instance(id, instance_code, package_type_id, current_status, current_dept_id, flags) VALUES(?,?,?,?,?,?)",
                "pkg-0003", "PKG-0003", "ptype-lap", "PACKED", "dept-cssd", "[\"待灭菌\"]");
        jdbc.update("INSERT INTO cssd_package_instance(id, instance_code, package_type_id, current_status, current_dept_id, sterilization_expire_at, flags) VALUES(?,?,?,?,?,DATE_ADD(NOW(), INTERVAL 150 DAY),?)",
                "pkg-0004", "PKG-0004", "ptype-dress", "STERILIZED", "dept-cssd", "[]");
        jdbc.update("INSERT INTO cssd_package_instance(id, instance_code, package_type_id, current_status, current_dept_id, sterilization_expire_at, borrowed, flags) VALUES(?,?,?,?,?,DATE_ADD(NOW(), INTERVAL 20 DAY),?,?)",
                "pkg-0005", "PKG-0005", "ptype-low", "IN_DEPT", "dept-surgery", 1, "[\"已借出\"]");

        jdbc.update("INSERT INTO cssd_equipment(id, equipment_code, equipment_name, equipment_type, model, location, status, preset_programs, default_bio_test) VALUES(?,?,?,?,?,?,?,?,?)",
                "eq-wash-01", "WASH-01", "全自动清洗机一号", 1, "WD-500", "清洗区", 1, "标准,加强,特殊感染,手工", 2);
        jdbc.update("INSERT INTO cssd_equipment(id, equipment_code, equipment_name, equipment_type, model, location, status, preset_programs, default_bio_test) VALUES(?,?,?,?,?,?,?,?,?)",
                "eq-sth-01", "ST-H-01", "高温灭菌器一号", 2, "STEAM-01", "灭菌区", 1, "标准,快速,特殊感染", 1);
        jdbc.update("INSERT INTO cssd_equipment(id, equipment_code, equipment_name, equipment_type, model, location, status, preset_programs, default_bio_test) VALUES(?,?,?,?,?,?,?,?,?)",
                "eq-stl-01", "ST-L-01", "低温灭菌器一号", 3, "LOW-01", "灭菌区", 1, "低温,EO", 1);

        jdbc.update("INSERT INTO cssd_basket(id, basket_code, status, package_list) VALUES(?,?,?,?)",
                "basket-01", "BASK-01", "IDLE", "[]");
        jdbc.update("INSERT INTO cssd_basket(id, basket_code, status, package_list) VALUES(?,?,?,?)",
                "basket-02", "BASK-02", "IDLE", "[]");

        config("login.method", "登录方式", "1", "common", "1-工牌刷卡，2-账号密码，3-人脸识别");
        config("lock.timeout.seconds", "锁超时时间", "120", "common", "触摸台前端锁默认超时秒数");
        config("recycle.video.enabled", "回收环节录像", "1", "recycle", "1-开启，2-关闭");
        config("wash.temperature.min", "水温下限", "90", "wash", "单位摄氏度");
        config("wash.a0.min", "A0值下限", "3000", "wash", "清洗判定阈值");
        config("sterilize.temperature.min", "物理监测温度下限", "134", "sterilize", "单位摄氏度");
        config("sterilize.temperature.max", "物理监测温度上限", "137", "sterilize", "单位摄氏度");
        config("sterilize.mismatch.behavior", "灭菌方式不匹配时行为", "0", "sterilize", "0-禁止装载，1-警告放行");
        config("bio.positive.auto.lock", "生物监测阳性自动锁定", "1", "bio", "1-是，0-否");
        config("distribute.partial.allowed", "科室发放允许部分发放", "1", "distribute", "1-允许，2-不允许");

        jdbc.update("""
            INSERT INTO cssd_borrow_order
            (id, order_no, dept_id, applicant_id, package_list, urgent, status, audit_user_id, audit_time)
            VALUES(?,?,?,?,?,?,?,?,NOW())
            """, "borrow-demo", "BORROW-DEMO-001", "dept-surgery", "user-operator",
                """
                [{"packageTypeCode":"PKT-LOW","packageName":"低温精密器械包","quantity":1}]
                """, 1, "BORROWED", "user-head");
        jdbc.update("UPDATE cssd_package_instance SET borrow_order_id=? WHERE instance_code=?", "borrow-demo", "PKG-0005");
    }

    private void config(String key, String name, String value, String module, String remark) {
        jdbc.update("INSERT INTO cssd_config(config_key, config_name, config_value, module_code, remark) VALUES(?,?,?,?,?)",
                key, name, value, module, remark);
    }

    private void seedMissingReferenceData() {
        // 初始化角色和权限，系统管理页可在此基础上维护角色授权。
        if (isEmpty("cssd_role")) {
            role("role-admin", "ADMIN", "系统管理员", "拥有全部后台和业务终端权限");
            role("role-head", "HEAD_NURSE", "护士长", "审核、质控和报表权限");
            role("role-operator", "OPERATOR", "操作员", "触摸端业务操作权限");
            role("role-delivery", "DELIVERY", "下送人员", "发放和下送相关权限");
        }
        if (isEmpty("cssd_permission")) {
            permission("perm-dashboard", "dashboard:view", "数据总览", "dashboard", 10);
            permission("perm-trace", "trace:view", "追溯查询", "trace", 20);
            permission("perm-doc-recycle", "documents:recycle", "回收单管理", "documents", 30);
            permission("perm-doc-wash", "documents:wash", "清洗记录管理", "documents", 40);
            permission("perm-doc-pack", "documents:pack", "配包打包记录", "documents", 50);
            permission("perm-doc-sterilize", "documents:sterilize", "灭菌记录管理", "documents", 60);
            permission("perm-doc-distribute", "documents:distribute", "发放单管理", "documents", 70);
            permission("perm-basic", "basic:manage", "基础资料维护", "basic", 80);
            permission("perm-system", "system:manage", "系统管理", "system", 90);
            permission("perm-touch", "terminal:operate", "触摸端业务操作", "terminal", 100);
            permission("perm-pda", "pda:sync", "PDA离线同步", "terminal", 110);
        }
        if (isEmpty("cssd_role_permission")) {
            grantAll("role-admin");
            grant("role-head", "perm-dashboard", "perm-trace", "perm-doc-recycle", "perm-doc-wash", "perm-doc-pack",
                    "perm-doc-sterilize", "perm-doc-distribute", "perm-basic");
            grant("role-operator", "perm-touch", "perm-trace");
            grant("role-delivery", "perm-doc-distribute", "perm-touch", "perm-pda");
        }

        // 初始化包材资料，标签失效日期统一依赖这里维护的有效期天数。
        if (isEmpty("cssd_packaging")) {
            jdbc.update("INSERT INTO cssd_packaging(id, packaging_code, packaging_name, validity_days) VALUES(?,?,?,?)",
                    "pack-nonwoven", "PACK-NONWOVEN", "无纺布", 180);
            jdbc.update("INSERT INTO cssd_packaging(id, packaging_code, packaging_name, validity_days) VALUES(?,?,?,?)",
                    "pack-paper", "PACK-PAPER", "纸塑袋", 30);
            jdbc.update("INSERT INTO cssd_packaging(id, packaging_code, packaging_name, validity_days) VALUES(?,?,?,?)",
                    "pack-container", "PACK-CONTAINER", "硬质容器", 90);
        }

        // 迁移默认器械包的追溯模式：手术室包一包一码，临床科室包按批次追溯。
        jdbc.update("""
            UPDATE cssd_package_type
            SET tracking_mode='UNIQUE', package_scope='OR', packaging_id='pack-nonwoven'
            WHERE package_code='PKT-LAP'
            """);
        jdbc.update("""
            UPDATE cssd_package_type
            SET tracking_mode='BATCH', package_scope='CLINICAL', packaging_id='pack-paper'
            WHERE package_code='PKT-DRESS'
            """);
        jdbc.update("""
            UPDATE cssd_package_type
            SET tracking_mode='UNIQUE', package_scope='OR', packaging_id='pack-container'
            WHERE package_code='PKT-LOW'
            """);

        // 初始化四类打印模板，后台模板设计器可以在此基础上调整字段和样式。
        if (isEmpty("cssd_print_template")) {
            printTemplate("tpl-package-label", "TPL-PACKAGE-LABEL", "器械包标签模板", "PACKAGE_LABEL");
            printTemplate("tpl-unique-label", "TPL-UNIQUE-LABEL", "器械包唯一码标签模板", "UNIQUE_CODE_LABEL");
            printTemplate("tpl-borrow-order", "TPL-BORROW-ORDER", "借包单打印模板", "BORROW_ORDER");
            printTemplate("tpl-distribute-order", "TPL-DISTRIBUTE-ORDER", "发放单打印模板", "DISTRIBUTE_ORDER");
        }
    }

    private void printTemplate(String id, String code, String name, String type) {
        // 默认模板保存字段清单和基础属性，后续设计器只需要更新 config_json。
        jdbc.update("""
            INSERT INTO cssd_print_template
            (id, template_code, template_name, template_type, config_json, created_by, updated_by)
            VALUES(?,?,?,?,?,?,?)
            """, id, code, name, type,
                """
                {"width":80,"height":50,"fields":[
                  {"key":"packageName","title":"包名","visible":true,"fontSize":14,"bold":true},
                  {"key":"contents","title":"包内物","visible":true,"fontSize":9},
                  {"key":"assemblerName","title":"配包人","visible":true,"fontSize":9},
                  {"key":"packerName","title":"打包人","visible":true,"fontSize":9},
                  {"key":"expireDate","title":"失效时间","visible":true,"fontSize":9},
                  {"key":"printTime","title":"打印时间","visible":true,"fontSize":9},
                  {"key":"sterilizationDate","title":"灭菌时间","visible":true,"fontSize":9},
                  {"key":"labelNo","title":"追溯码","visible":true,"fontSize":9,"barcode":"QR"}
                ]}
                """, "user-admin", "user-admin");
    }

    private void role(String id, String code, String name, String remark) {
        jdbc.update("INSERT INTO cssd_role(id, role_code, role_name, remark) VALUES(?,?,?,?)", id, code, name, remark);
    }

    private void permission(String id, String code, String name, String module, int sortNo) {
        jdbc.update("INSERT INTO cssd_permission(id, permission_code, permission_name, module_code, sort_no) VALUES(?,?,?,?,?)",
                id, code, name, module, sortNo);
    }

    private void grantAll(String roleId) {
        for (Map<String, Object> permission : jdbc.queryForList("SELECT id FROM cssd_permission")) {
            grant(roleId, permission.get("id").toString());
        }
    }

    private void grant(String roleId, String... permissionIds) {
        for (String permissionId : permissionIds) {
            jdbc.update("INSERT IGNORE INTO cssd_role_permission(id, role_id, permission_id) VALUES(REPLACE(UUID(),'-',''),?,?)",
                    roleId, permissionId);
        }
    }
}
