package com.cssd.trace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TraceService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TraceService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statusCounts", jdbc.queryForList("""
            SELECT current_status status, COUNT(*) total
            FROM cssd_package_instance
            GROUP BY current_status
            ORDER BY total DESC
            """));
        data.put("stations", stationCounters());
        data.put("equipment", jdbc.queryForList("""
            SELECT equipment_code, equipment_name, equipment_type, status, preset_programs, default_bio_test
            FROM cssd_equipment
            ORDER BY equipment_type, equipment_code
            """));
        data.put("nearExpiry", jdbc.queryForList("""
            SELECT pi.instance_code, pt.package_name, d.dept_name, pi.sterilization_expire_at
            FROM cssd_package_instance pi
            JOIN cssd_package_type pt ON pt.id = pi.package_type_id
            LEFT JOIN cssd_department d ON d.id = pi.current_dept_id
            WHERE pi.sterilization_expire_at IS NOT NULL
              AND pi.sterilization_expire_at <= DATE_ADD(NOW(), INTERVAL 30 DAY)
            ORDER BY pi.sterilization_expire_at
            LIMIT 8
            """));
        data.put("recentEvents", jdbc.queryForList("""
            SELECT package_code, event_type, station, batch_no, occurred_at, offline_flag, device_code
            FROM cssd_workflow_event
            ORDER BY event_seq DESC
            LIMIT 12
            """));
        data.put("borrowTodos", jdbc.queryForList("""
            SELECT bo.order_no, d.dept_name, bo.status, bo.urgent, bo.apply_time, bo.reject_reason
            FROM cssd_borrow_order bo
            JOIN cssd_department d ON d.id = bo.dept_id
            WHERE bo.status IN ('WAIT_AUDIT', 'WAIT_PICKUP')
            ORDER BY bo.apply_time DESC
            LIMIT 8
            """));
        return data;
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String workNo = str(body, "workNo", "");
        String password = str(body, "password", "");
        Map<String, Object> user = one("""
            SELECT u.id, u.work_no, u.user_name, u.role_code, u.user_type, u.status,
                   d.dept_code, d.dept_name
            FROM cssd_user u
            JOIN cssd_department d ON d.id = u.dept_id
            WHERE u.work_no=? AND u.password_hash=? AND u.status=1
            """, workNo, password);
        return Map.of(
                "token", "local-demo-token-" + user.get("work_no"),
                "user", user
        );
    }

    public Map<String, Object> basicData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("departments", jdbc.queryForList("SELECT * FROM cssd_department ORDER BY dept_code"));
        data.put("users", jdbc.queryForList("SELECT work_no, user_name, role_code, user_type, status FROM cssd_user ORDER BY work_no"));
        data.put("packageTypes", jdbc.queryForList("SELECT * FROM cssd_package_type ORDER BY package_code"));
        // 后台基础信息需要直接展示包材，标签失效期由包材有效期驱动。
        data.put("packaging", jdbc.queryForList("SELECT * FROM cssd_packaging ORDER BY packaging_code"));
        data.put("packages", jdbc.queryForList(packageSql("ORDER BY pi.updated_at DESC")));
        data.put("equipments", jdbc.queryForList("SELECT * FROM cssd_equipment ORDER BY equipment_code"));
        data.put("baskets", jdbc.queryForList("SELECT * FROM cssd_basket ORDER BY basket_code"));
        // 临床批量包在生成标签前按批次追溯，后台基础页也给出最近批次便于验证数据互通。
        data.put("traceLots", jdbc.queryForList("""
            SELECT tl.*, pt.package_code, pt.package_name, d.dept_code, d.dept_name
            FROM cssd_trace_lot tl
            JOIN cssd_package_type pt ON pt.id=tl.package_type_id
            JOIN cssd_department d ON d.id=tl.dept_id
            ORDER BY tl.created_at DESC
            LIMIT 50
            """));
        // 打印模板是打包标签、借包单、发放单打印的配置来源。
        data.put("printTemplates", jdbc.queryForList("SELECT * FROM cssd_print_template ORDER BY template_type, template_code"));
        data.put("configs", configs());
        return data;
    }

    public List<Map<String, Object>> configs() {
        return jdbc.queryForList("""
            SELECT config_key, config_name, config_value, module_code, remark, updated_at
            FROM cssd_config
            ORDER BY module_code, config_key
            """);
    }

    @Transactional
    public Map<String, Object> updateConfig(String key, Map<String, Object> body) {
        String value = str(body, "value", "");
        int rows = jdbc.update("UPDATE cssd_config SET config_value=? WHERE config_key=?", value, key);
        if (rows == 0) {
            throw new IllegalArgumentException("配置项不存在：" + key);
        }
        return Map.of("configKey", key, "configValue", value);
    }

    public Map<String, Object> station(String station) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("station", station);
        data.put("packages", stationPackages(station));
        data.put("baskets", jdbc.queryForList("SELECT * FROM cssd_basket ORDER BY basket_code"));
        data.put("equipments", jdbc.queryForList("SELECT * FROM cssd_equipment ORDER BY equipment_type, equipment_code"));
        data.put("borrowOrders", jdbc.queryForList("""
            SELECT bo.*, d.dept_name
            FROM cssd_borrow_order bo
            JOIN cssd_department d ON d.id = bo.dept_id
            ORDER BY bo.apply_time DESC
            LIMIT 20
            """));
        data.put("washRecords", jdbc.queryForList("""
            SELECT wr.batch_no, e.equipment_code, e.equipment_name, wr.program_name, wr.start_time, wr.end_time, wr.result, wr.package_list
            FROM cssd_wash_record wr
            JOIN cssd_equipment e ON e.id = wr.equipment_id
            ORDER BY wr.start_time DESC
            LIMIT 12
            """));
        data.put("sterilizationRecords", jdbc.queryForList("""
            SELECT sr.batch_no, e.equipment_code, e.equipment_name, sr.program_name, sr.start_time, sr.end_time,
                   sr.need_bio_test, sr.bio_test_status, sr.physical_result, sr.chemical_result, sr.result, sr.package_list
            FROM cssd_sterilization_record sr
            JOIN cssd_equipment e ON e.id = sr.equipment_id
            ORDER BY sr.start_time DESC
            LIMIT 12
            """));
        return data;
    }

    @Transactional
    public Map<String, Object> recycle(Map<String, Object> body) {
        return doRecycle(body, false, str(body, "deviceCode", "TOUCH-RECYCLE"));
    }

    @Transactional
    public Map<String, Object> washStart(Map<String, Object> body) {
        String basketCode = str(body, "basketCode", "BASK-01").toUpperCase(Locale.ROOT);
        String equipmentCode = str(body, "equipmentCode", "WASH-01").toUpperCase(Locale.ROOT);
        String program = str(body, "program", "标准");
        Map<String, Object> equipment = equipmentByCode(equipmentCode);
        if (num(equipment.get("equipment_type")) != 1) {
            throw new IllegalArgumentException("请选择清洗机设备");
        }
        List<String> packageCodes = basketPackages(basketCode);
        if (packageCodes.isEmpty()) {
            throw new IllegalArgumentException("该筐没有待清洗包");
        }
        for (String code : packageCodes) {
            Map<String, Object> pkg = packageByCode(code);
            if (!"RECYCLED".equals(pkg.get("current_status"))) {
                throw new IllegalArgumentException(code + " 当前不是待清洗状态");
            }
        }

        String batchNo = nextNo("WASH");
        String id = id();
        jdbc.update("""
            INSERT INTO cssd_wash_record
            (id, batch_no, equipment_id, program_name, wash_type, operator_id, basket_list, package_list, param_data)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, id, batchNo, equipment.get("id"), program, 1, "user-operator", json(List.of(basketCode)), json(packageCodes),
                """
                {"waterTemp":92,"timeMinutes":8,"a0":3600}
                """);
        jdbc.update("UPDATE cssd_equipment SET status=2 WHERE id=?", equipment.get("id"));
        jdbc.update("UPDATE cssd_basket SET status='WASHING', current_batch_no=? WHERE basket_code=?", batchNo, basketCode);
        jdbc.update("UPDATE cssd_package_instance SET current_status='WASHING', current_batch_no=? WHERE instance_code IN (" + placeholders(packageCodes) + ")",
                params(batchNo, packageCodes));
        for (String code : packageCodes) {
            event(code, "开始清洗", "wash", batchNo, false, "TOUCH-WASH", Map.of("basketCode", basketCode, "program", program));
        }
        return Map.of("batchNo", batchNo, "packages", packageCodes);
    }

    @Transactional
    public Map<String, Object> washFinish(Map<String, Object> body) {
        String batchNo = str(body, "batchNo", "");
        boolean pass = bool(body, "pass", true);
        Map<String, Object> record = one("SELECT * FROM cssd_wash_record WHERE batch_no=?", batchNo);
        List<String> packageCodes = jsonList(record.get("package_list"));
        String nextStatus = pass ? "WASHED" : "RECYCLED";
        jdbc.update("UPDATE cssd_wash_record SET end_time=NOW(), result=?, remark=? WHERE batch_no=?",
                pass ? 1 : 2, str(body, "remark", pass ? "清洗合格" : "清洗不合格，退回待清洗"), batchNo);
        jdbc.update("UPDATE cssd_equipment SET status=1 WHERE id=?", record.get("equipment_id"));
        jdbc.update("UPDATE cssd_package_instance SET current_status=?, updated_at=NOW() WHERE instance_code IN (" + placeholders(packageCodes) + ")",
                params(nextStatus, packageCodes));
        List<String> baskets = jsonList(record.get("basket_list"));
        for (String basket : baskets) {
            jdbc.update("UPDATE cssd_basket SET status='IDLE', current_batch_no=NULL, package_list='[]' WHERE basket_code=?", basket);
        }
        for (String code : packageCodes) {
            event(code, pass ? "清洗合格出锅" : "清洗不合格退回", "wash", batchNo, false, "TOUCH-WASH",
                    Map.of("result", pass ? "PASS" : "FAIL"));
        }
        return Map.of("batchNo", batchNo, "nextStatus", nextStatus, "packages", packageCodes);
    }

    @Transactional
    public Map<String, Object> completeStation(Map<String, Object> body) {
        String station = str(body, "station", "");
        String packageCode = str(body, "packageCode", "").toUpperCase(Locale.ROOT);
        Map<String, Object> pkg = packageByCode(packageCode);
        String from;
        String to;
        String eventType;
        if ("assemble".equals(station)) {
            from = "WASHED";
            to = "ASSEMBLED";
            eventType = "配包完成";
        } else if ("pack".equals(station)) {
            from = "ASSEMBLED";
            to = "PACKED";
            eventType = "打包完成";
        } else {
            throw new IllegalArgumentException("未知工位：" + station);
        }
        if (!from.equals(pkg.get("current_status"))) {
            throw new IllegalArgumentException(packageCode + " 当前状态不能执行该操作");
        }
        jdbc.update("UPDATE cssd_package_instance SET current_status=?, updated_at=NOW() WHERE instance_code=?", to, packageCode);
        event(packageCode, eventType, station, str(pkg.get("current_batch_no")), false,
                "assemble".equals(station) ? "TOUCH-ASSEMBLE" : "TOUCH-PACK",
                Map.of("video", "已留痕", "operator", "user-operator"));
        return packageView(packageCode);
    }

    @Transactional
    public Map<String, Object> sterilizeStart(Map<String, Object> body) {
        String packageCode = str(body, "packageCode", "").toUpperCase(Locale.ROOT);
        String equipmentCode = str(body, "equipmentCode", "ST-H-01").toUpperCase(Locale.ROOT);
        String program = str(body, "program", "标准");
        Map<String, Object> pkg = packageByCode(packageCode);
        Map<String, Object> equipment = equipmentByCode(equipmentCode);
        if (!"PACKED".equals(pkg.get("current_status"))) {
            throw new IllegalArgumentException(packageCode + " 当前不是待灭菌状态");
        }
        int packageSterilizationType = num(pkg.get("sterilization_type"));
        int equipmentType = num(equipment.get("equipment_type"));
        boolean match = (packageSterilizationType == 1 && equipmentType == 2)
                || (packageSterilizationType == 2 && equipmentType == 3)
                || (packageSterilizationType == 3 && equipmentType == 4);
        if (!match && "0".equals(configValue("sterilize.mismatch.behavior", "0"))) {
            throw new IllegalArgumentException("包灭菌方式与设备类型不匹配，已禁止装载");
        }

        boolean needBio = bool(body, "needBio", num(equipment.get("default_bio_test")) == 1);
        String batchNo = nextNo("STER");
        jdbc.update("""
            INSERT INTO cssd_sterilization_record
            (id, batch_no, equipment_id, program_name, operator_id, package_list, need_bio_test, bio_test_status, param_data)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, id(), batchNo, equipment.get("id"), program, "user-operator", json(List.of(packageCode)), needBio ? 1 : 2, needBio ? 0 : 1,
                """
                {"temperature":135,"pressure":196,"timeMinutes":5}
                """);
        jdbc.update("UPDATE cssd_equipment SET status=2 WHERE id=?", equipment.get("id"));
        jdbc.update("UPDATE cssd_package_instance SET current_status='STERILIZING', current_batch_no=?, updated_at=NOW() WHERE instance_code=?",
                batchNo, packageCode);
        event(packageCode, "开始灭菌", "sterilize", batchNo, false, "TOUCH-STERILIZE",
                Map.of("equipmentCode", equipmentCode, "program", program, "needBio", needBio));
        return Map.of("batchNo", batchNo, "packageCode", packageCode, "needBio", needBio);
    }

    @Transactional
    public Map<String, Object> sterilizeFinish(Map<String, Object> body) {
        String batchNo = str(body, "batchNo", "");
        boolean physicalPass = bool(body, "physicalPass", true);
        boolean chemicalPass = bool(body, "chemicalPass", true);
        Map<String, Object> record = one("SELECT * FROM cssd_sterilization_record WHERE batch_no=?", batchNo);
        List<String> packageCodes = jsonList(record.get("package_list"));
        boolean needBio = num(record.get("need_bio_test")) == 1;
        boolean pass = physicalPass && chemicalPass;
        String nextStatus = pass ? (needBio ? "BIO_PENDING" : "STERILIZED") : "PACKED";

        jdbc.update("""
            UPDATE cssd_sterilization_record
            SET end_time=NOW(), physical_result=?, chemical_result=?, result=?, remark=?
            WHERE batch_no=?
            """, physicalPass ? 1 : 2, chemicalPass ? 1 : 2, pass ? 1 : 2,
                pass ? "物理与化学监测合格" : "监测不合格，退回待灭菌", batchNo);
        jdbc.update("UPDATE cssd_equipment SET status=1 WHERE id=?", record.get("equipment_id"));
        jdbc.update("UPDATE cssd_package_instance SET current_status=?, updated_at=NOW() WHERE instance_code IN (" + placeholders(packageCodes) + ")",
                params(nextStatus, packageCodes));
        for (String code : packageCodes) {
            event(code, pass ? "灭菌物理化学合格" : "灭菌不合格退回", "sterilize", batchNo, false, "TOUCH-STERILIZE",
                    Map.of("physicalPass", physicalPass, "chemicalPass", chemicalPass, "nextStatus", nextStatus));
        }
        return Map.of("batchNo", batchNo, "nextStatus", nextStatus, "needBio", needBio, "packages", packageCodes);
    }

    @Transactional
    public Map<String, Object> bioTest(Map<String, Object> body) {
        String batchNo = str(body, "batchNo", "");
        boolean pass = bool(body, "pass", true);
        Map<String, Object> record = one("SELECT * FROM cssd_sterilization_record WHERE batch_no=?", batchNo);
        List<String> packageCodes = jsonList(record.get("package_list"));
        String nextStatus = pass ? "STERILIZED" : "LOCKED_RECALL";
        jdbc.update("""
            INSERT INTO cssd_bio_test_record
            (id, sterilization_batch_no, equipment_id, indicator_batch, result, operator_id)
            VALUES(?,?,?,?,?,?)
            """, id(), batchNo, record.get("equipment_id"), str(body, "indicatorBatch", "BI-" + NO_TIME.format(LocalDateTime.now())),
                pass ? 1 : 2, "user-operator");
        jdbc.update("UPDATE cssd_sterilization_record SET bio_test_status=?, result=? WHERE batch_no=?", pass ? 1 : 2, pass ? 1 : 2, batchNo);
        jdbc.update("UPDATE cssd_package_instance SET current_status=?, locked=?, updated_at=NOW() WHERE instance_code IN (" + placeholders(packageCodes) + ")",
                params(nextStatus, pass ? 0 : 1, packageCodes));
        for (String code : packageCodes) {
            event(code, pass ? "生物监测合格" : "生物监测阳性锁定召回", "bio", batchNo, false, "WEB-BIO",
                    Map.of("result", pass ? "PASS" : "FAIL"));
        }
        return Map.of("batchNo", batchNo, "nextStatus", nextStatus, "packages", packageCodes);
    }

    @Transactional
    public Map<String, Object> distribute(Map<String, Object> body) {
        String packageCode = str(body, "packageCode", "").toUpperCase(Locale.ROOT);
        String deptCode = str(body, "deptCode", "OR").toUpperCase(Locale.ROOT);
        Map<String, Object> pkg = packageByCode(packageCode);
        Map<String, Object> dept = deptByCode(deptCode);
        if (!"STERILIZED".equals(pkg.get("current_status"))) {
            throw new IllegalArgumentException(packageCode + " 当前不可发放");
        }
        String orderNo = nextNo("DIST");
        jdbc.update("""
            INSERT INTO cssd_distribute_order
            (id, order_no, distribute_type, operator_id, dept_id, package_list, status)
            VALUES(?,?,?,?,?,?,?)
            """, id(), orderNo, intVal(body, "distributeType", 1), "user-operator", dept.get("id"), json(List.of(packageCode)), 2);
        jdbc.update("""
            UPDATE cssd_package_instance
            SET current_status='IN_DEPT', current_dept_id=?, current_batch_no=?, updated_at=NOW()
            WHERE instance_code=?
            """, dept.get("id"), orderNo, packageCode);
        event(packageCode, "发放到科室", "distribute", orderNo, false, "TOUCH-DISTRIBUTE",
                Map.of("deptCode", deptCode, "deptName", dept.get("dept_name")));
        return packageView(packageCode);
    }

    @Transactional
    public Map<String, Object> borrowApply(Map<String, Object> body) {
        String deptCode = str(body, "deptCode", "OR").toUpperCase(Locale.ROOT);
        String packageTypeCode = str(body, "packageTypeCode", "PKT-DRESS").toUpperCase(Locale.ROOT);
        int quantity = intVal(body, "quantity", 1);
        Map<String, Object> dept = deptByCode(deptCode);
        Map<String, Object> packageType = one("SELECT * FROM cssd_package_type WHERE package_code=?", packageTypeCode);
        String orderNo = nextNo("BORROW");
        List<Map<String, Object>> packageList = List.of(Map.of(
                "packageTypeCode", packageTypeCode,
                "packageName", packageType.get("package_name"),
                "quantity", quantity
        ));
        jdbc.update("""
            INSERT INTO cssd_borrow_order
            (id, order_no, dept_id, applicant_id, package_list, urgent, status)
            VALUES(?,?,?,?,?,?,?)
            """, id(), orderNo, dept.get("id"), "user-operator", json(packageList), bool(body, "urgent", false) ? 1 : 0, "WAIT_AUDIT");
        return Map.of("orderNo", orderNo, "status", "WAIT_AUDIT");
    }

    @Transactional
    public Map<String, Object> borrowAudit(Map<String, Object> body) {
        String orderNo = str(body, "orderNo", "");
        boolean pass = bool(body, "pass", true);
        Map<String, Object> order = one("SELECT * FROM cssd_borrow_order WHERE order_no=?", orderNo);
        if (!"WAIT_AUDIT".equals(order.get("status"))) {
            throw new IllegalArgumentException("该申领单当前不在待审核状态");
        }
        jdbc.update("""
            UPDATE cssd_borrow_order
            SET status=?, audit_user_id='user-head', audit_time=NOW(), reject_reason=?
            WHERE order_no=?
            """, pass ? "WAIT_PICKUP" : "REJECTED", pass ? null : str(body, "rejectReason", "库存不足"), orderNo);
        return Map.of("orderNo", orderNo, "status", pass ? "WAIT_PICKUP" : "REJECTED");
    }

    @Transactional
    public Map<String, Object> borrowDistribute(Map<String, Object> body) {
        String orderNo = str(body, "orderNo", "");
        String packageCode = str(body, "packageCode", "").toUpperCase(Locale.ROOT);
        Map<String, Object> order = one("SELECT * FROM cssd_borrow_order WHERE order_no=?", orderNo);
        if (!"WAIT_PICKUP".equals(order.get("status"))) {
            throw new IllegalArgumentException("借包单尚未审核通过或已处理");
        }
        Map<String, Object> pkg = packageByCode(packageCode);
        if (!"STERILIZED".equals(pkg.get("current_status"))) {
            throw new IllegalArgumentException("借出包必须是已灭菌可发放状态");
        }
        String distNo = nextNo("BDIST");
        jdbc.update("""
            INSERT INTO cssd_distribute_order
            (id, order_no, distribute_type, operator_id, dept_id, package_list, status)
            VALUES(?,?,?,?,?,?,?)
            """, id(), distNo, 3, "user-operator", order.get("dept_id"), json(List.of(packageCode)), 2);
        jdbc.update("""
            UPDATE cssd_borrow_order
            SET status='BORROWED', distribute_order_id=?
            WHERE order_no=?
            """, distNo, orderNo);
        jdbc.update("""
            UPDATE cssd_package_instance
            SET current_status='IN_DEPT', current_dept_id=?, borrowed=1, borrow_order_id=?, current_batch_no=?, updated_at=NOW()
            WHERE instance_code=?
            """, order.get("dept_id"), order.get("id"), distNo, packageCode);
        event(packageCode, "借包发放", "borrow", distNo, false, "TOUCH-DISTRIBUTE", Map.of("borrowOrderNo", orderNo));
        return Map.of("orderNo", orderNo, "distributeNo", distNo, "packageCode", packageCode);
    }

    @Transactional
    public Map<String, Object> pdaSync(Map<String, Object> body) {
        String deviceCode = str(body, "deviceCode", "PDA-01");
        List<Map<String, Object>> events = jsonMapList(body.get("events"));
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> item : events) {
            try {
                String type = str(item, "type", "recycle");
                if ("recycle".equals(type)) {
                    doRecycle(item, true, deviceCode);
                } else if ("distribute".equals(type)) {
                    distribute(item);
                } else {
                    throw new IllegalArgumentException("未知 PDA 事件类型：" + type);
                }
                success++;
            } catch (Exception ex) {
                errors.add(ex.getMessage());
            }
        }
        jdbc.update("""
            INSERT INTO cssd_pda_sync_log(id, device_code, event_count, success_count, failed_count, raw_payload)
            VALUES(?,?,?,?,?,?)
            """, id(), deviceCode, events.size(), success, events.size() - success, json(body));
        return Map.of("deviceCode", deviceCode, "eventCount", events.size(), "successCount", success,
                "failedCount", events.size() - success, "errors", errors);
    }

    public Map<String, Object> trace(String packageCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("package", packageView(packageCode.toUpperCase(Locale.ROOT)));
        data.put("events", jdbc.queryForList("""
            SELECT event_type, station, batch_no, payload, occurred_at, upload_time, offline_flag, device_code
            FROM cssd_workflow_event
            WHERE package_code=?
            ORDER BY event_seq
            """, packageCode.toUpperCase(Locale.ROOT)));
        data.put("recycleOrders", jdbc.queryForList("""
            SELECT order_no, source_type, recycle_time, total_count, return_count, abnormal_record, remark, basket_code, video_url
            FROM cssd_recycle_order
            WHERE JSON_CONTAINS(package_list, JSON_QUOTE(?))
            ORDER BY recycle_time DESC
            """, packageCode.toUpperCase(Locale.ROOT)));
        data.put("washRecords", jdbc.queryForList("""
            SELECT batch_no, start_time, end_time, result, param_data, remark
            FROM cssd_wash_record
            WHERE JSON_CONTAINS(package_list, JSON_QUOTE(?))
            ORDER BY start_time DESC
            """, packageCode.toUpperCase(Locale.ROOT)));
        data.put("sterilizationRecords", jdbc.queryForList("""
            SELECT batch_no, start_time, end_time, need_bio_test, bio_test_status, physical_result, chemical_result, result, param_data, remark
            FROM cssd_sterilization_record
            WHERE JSON_CONTAINS(package_list, JSON_QUOTE(?))
            ORDER BY start_time DESC
            """, packageCode.toUpperCase(Locale.ROOT)));
        data.put("distributeOrders", jdbc.queryForList("""
            SELECT order_no, distribute_type, distribute_time, dept_id, package_list, status
            FROM cssd_distribute_order
            WHERE JSON_CONTAINS(package_list, JSON_QUOTE(?))
            ORDER BY distribute_time DESC
            """, packageCode.toUpperCase(Locale.ROOT)));
        return data;
    }

    private Map<String, Object> doRecycle(Map<String, Object> body, boolean offline, String deviceCode) {
        String packageCode = str(body, "packageCode", "").toUpperCase(Locale.ROOT);
        String basketCode = str(body, "basketCode", "BASK-01").toUpperCase(Locale.ROOT);
        Map<String, Object> pkg = packageByCode(packageCode);
        if ("LOCKED_RECALL".equals(pkg.get("current_status"))) {
            throw new IllegalArgumentException(packageCode + " 已锁定召回，不能按普通流程回收");
        }
        Map<String, Object> dept = pkg.get("current_dept_id") == null
                ? deptByCode(str(body, "deptCode", "OR"))
                : one("SELECT * FROM cssd_department WHERE id=?", pkg.get("current_dept_id"));
        int returnCount = num(pkg.get("borrowed")) == 1 ? 1 : 0;
        int sourceType = returnCount == 1 ? 3 : intVal(body, "sourceType", offline ? 1 : 2);
        String orderNo = nextNo(returnCount == 1 ? "RETURN" : "REC");
        List<String> packages = List.of(packageCode);
        jdbc.update("""
            INSERT INTO cssd_recycle_order
            (id, order_no, source_type, source_user, recycle_user, dept_id, total_count, return_count, package_list, abnormal_record, remark, basket_code, video_url)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id(), orderNo, sourceType, str(body, "sourceUser", dept.get("dept_name").toString()),
                "user-operator", dept.get("id"), 1, returnCount, json(packages),
                json(Map.of("abnormal", str(body, "abnormal", "无"))), str(body, "remark", ""),
                basketCode, offline ? null : "video://" + orderNo);

        if (returnCount == 1 && pkg.get("borrow_order_id") != null) {
            jdbc.update("UPDATE cssd_borrow_order SET status='RETURNED' WHERE id=?", pkg.get("borrow_order_id"));
        }
        List<String> basketList = basketPackages(basketCode);
        if (!basketList.contains(packageCode)) {
            basketList.add(packageCode);
        }
        jdbc.update("UPDATE cssd_basket SET status='WAIT_WASH', current_batch_no=?, package_list=? WHERE basket_code=?",
                orderNo, json(basketList), basketCode);
        jdbc.update("""
            UPDATE cssd_package_instance
            SET current_status='RECYCLED', current_dept_id=?, borrowed=0, borrow_order_id=NULL, current_batch_no=?, updated_at=NOW()
            WHERE instance_code=?
            """, dept.get("id"), orderNo, packageCode);
        event(packageCode, returnCount == 1 ? "借包归还回收" : "污染包回收", "recycle", orderNo, offline, deviceCode,
                Map.of("basketCode", basketCode, "sourceType", sourceType, "returnCount", returnCount));
        return Map.of("orderNo", orderNo, "packageCode", packageCode, "basketCode", basketCode, "returnCount", returnCount);
    }

    private List<Map<String, Object>> stationCounters() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("station", "回收", "count", countStatus("IN_DEPT")));
        rows.add(Map.of("station", "清洗", "count", countStatus("RECYCLED")));
        rows.add(Map.of("station", "配包", "count", countStatus("WASHED")));
        rows.add(Map.of("station", "打包", "count", countStatus("ASSEMBLED")));
        rows.add(Map.of("station", "灭菌", "count", countStatus("PACKED")));
        rows.add(Map.of("station", "生物监测", "count", countStatus("BIO_PENDING")));
        rows.add(Map.of("station", "发放", "count", countStatus("STERILIZED")));
        return rows;
    }

    private int countStatus(String status) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM cssd_package_instance WHERE current_status=?", Integer.class, status);
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> stationPackages(String station) {
        return switch (station) {
            case "recycle" -> jdbc.queryForList(packageSql("WHERE pi.current_status IN ('IN_DEPT') ORDER BY pi.updated_at DESC"));
            case "wash" -> jdbc.queryForList(packageSql("WHERE pi.current_status='RECYCLED' ORDER BY pi.updated_at DESC"));
            case "assemble" -> jdbc.queryForList(packageSql("WHERE pi.current_status='WASHED' ORDER BY pi.updated_at DESC"));
            case "pack" -> jdbc.queryForList(packageSql("WHERE pi.current_status='ASSEMBLED' ORDER BY pi.updated_at DESC"));
            case "sterilize" -> jdbc.queryForList(packageSql("WHERE pi.current_status='PACKED' ORDER BY pi.updated_at DESC"));
            case "bio" -> jdbc.queryForList(packageSql("WHERE pi.current_status='BIO_PENDING' ORDER BY pi.updated_at DESC"));
            case "distribute" -> jdbc.queryForList(packageSql("WHERE pi.current_status='STERILIZED' ORDER BY pi.updated_at DESC"));
            default -> jdbc.queryForList(packageSql("ORDER BY pi.updated_at DESC LIMIT 50"));
        };
    }

    private String packageSql(String tail) {
        return """
            SELECT pi.id, pi.instance_code, pi.current_status, pi.current_dept_id, pi.sterilization_expire_at,
                   pi.borrowed, pi.current_batch_no, pi.flags, pi.locked, pi.updated_at,
                   pt.package_code package_type_code, pt.package_name, pt.category, pt.tracking_mode, pt.package_scope,
                   pt.sterilization_type, pt.cleaning_type, pt.validity_days, pt.packaging_name,
                   pt.instrument_list, pt.assemble_image, pt.pack_image,
                   d.dept_code, d.dept_name
            FROM cssd_package_instance pi
            JOIN cssd_package_type pt ON pt.id = pi.package_type_id
            LEFT JOIN cssd_department d ON d.id = pi.current_dept_id
            """ + tail;
    }

    private Map<String, Object> packageView(String packageCode) {
        return one(packageSql("WHERE pi.instance_code=?"), packageCode);
    }

    private Map<String, Object> packageByCode(String packageCode) {
        return packageView(packageCode);
    }

    private Map<String, Object> equipmentByCode(String equipmentCode) {
        return one("SELECT * FROM cssd_equipment WHERE equipment_code=?", equipmentCode);
    }

    private Map<String, Object> deptByCode(String deptCode) {
        return one("SELECT * FROM cssd_department WHERE dept_code=?", deptCode.toUpperCase(Locale.ROOT));
    }

    private String configValue(String key, String defaultValue) {
        try {
            return jdbc.queryForObject("SELECT config_value FROM cssd_config WHERE config_key=?", String.class, key);
        } catch (EmptyResultDataAccessException ex) {
            return defaultValue;
        }
    }

    private List<String> basketPackages(String basketCode) {
        try {
            Object value = jdbc.queryForObject("SELECT package_list FROM cssd_basket WHERE basket_code=?", Object.class, basketCode);
            return new ArrayList<>(jsonList(value));
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("筐编码不存在：" + basketCode);
        }
    }

    private void event(String packageCode, String eventType, String station, String batchNo, boolean offline, String deviceCode, Map<String, Object> payload) {
        Map<String, Object> pkg = packageByCode(packageCode);
        jdbc.update("""
            INSERT INTO cssd_workflow_event
            (id, package_instance_id, package_code, event_type, station, operator_id, dept_id, batch_no, payload, offline_flag, device_code)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, id(), pkg.get("id"), packageCode, eventType, station, "user-operator", pkg.get("current_dept_id"),
                batchNo, json(payload), offline ? 1 : 0, deviceCode);
    }

    private Map<String, Object> one(String sql, Object... args) {
        try {
            return jdbc.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("未找到对应数据");
        }
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    private String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private int intVal(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.toString());
    }

    private int num(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private boolean bool(Map<String, Object> body, String key, boolean fallback) {
        Object value = body.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "是".equals(text) || "PASS".equalsIgnoreCase(text);
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String nextNo(String prefix) {
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON序列化失败：" + ex.getMessage());
        }
    }

    private List<String> jsonList(Object value) {
        if (value == null || value.toString().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsonMapList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (value == null || value.toString().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("PDA同步数据格式不正确");
        }
    }

    private String placeholders(List<?> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("缺少包清单");
        }
        return String.join(",", values.stream().map(v -> "?").toList());
    }

    private Object[] params(Object first, List<?> values) {
        List<Object> result = new ArrayList<>();
        result.add(first);
        result.addAll(values);
        return result.toArray();
    }

    private Object[] params(Object first, Object second, List<?> values) {
        List<Object> result = new ArrayList<>();
        result.add(first);
        result.add(second);
        result.addAll(values);
        return result.toArray();
    }
}
