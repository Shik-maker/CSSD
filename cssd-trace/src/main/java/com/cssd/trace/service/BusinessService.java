package com.cssd.trace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class BusinessService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BusinessService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // 后台基础资料查询入口，只允许访问白名单内的业务表，避免前端拼表名造成风险。
    public Map<String, Object> listEntity(String entity) {
        EntityMeta meta = entityMeta(entity);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity", entity);
        data.put("columns", meta.fields());
        data.put("rows", jdbc.queryForList("SELECT * FROM " + meta.tableName() + " ORDER BY " + meta.orderBy()));
        return data;
    }

    // 后台基础资料新增入口，所有新增都会同时记录数据审计日志。
    @Transactional
    public Map<String, Object> createEntity(String entity, Map<String, Object> body) {
        EntityMeta meta = entityMeta(entity);
        String rowId = id();
        Map<String, Object> values = normalizeValues(meta, body);
        values.put("id", rowId);

        List<String> columns = new ArrayList<>(values.keySet());
        String placeholders = String.join(",", columns.stream().map(x -> "?").toList());
        jdbc.update("INSERT INTO " + meta.tableName() + "(" + String.join(",", columns) + ") VALUES(" + placeholders + ")",
                columns.stream().map(values::get).toArray());

        Map<String, Object> after = one("SELECT * FROM " + meta.tableName() + " WHERE id=?", rowId);
        audit(meta.tableName(), rowId, "CREATE", null, after);
        return after;
    }

    // 后台基础资料修改入口，只更新白名单字段，并把修改前后快照写入审计表。
    @Transactional
    public Map<String, Object> updateEntity(String entity, String rowId, Map<String, Object> body) {
        EntityMeta meta = entityMeta(entity);
        Map<String, Object> before = one("SELECT * FROM " + meta.tableName() + " WHERE id=?", rowId);
        Map<String, Object> values = normalizeValues(meta, body);
        values.remove("id");
        if (values.isEmpty()) {
            return before;
        }

        String setSql = String.join(",", values.keySet().stream().map(field -> field + "=?").toList());
        List<Object> params = new ArrayList<>(values.values());
        params.add(rowId);
        jdbc.update("UPDATE " + meta.tableName() + " SET " + setSql + " WHERE id=?", params.toArray());

        Map<String, Object> after = one("SELECT * FROM " + meta.tableName() + " WHERE id=?", rowId);
        audit(meta.tableName(), rowId, "UPDATE", before, after);
        return after;
    }

    // 后台基础资料停用入口，能软删除的表优先改 status，避免历史追溯链断裂。
    @Transactional
    public Map<String, Object> deleteEntity(String entity, String rowId) {
        EntityMeta meta = entityMeta(entity);
        Map<String, Object> before = one("SELECT * FROM " + meta.tableName() + " WHERE id=?", rowId);
        if (meta.fields().contains("status")) {
            jdbc.update("UPDATE " + meta.tableName() + " SET status=0 WHERE id=?", rowId);
        } else {
            jdbc.update("DELETE FROM " + meta.tableName() + " WHERE id=?", rowId);
        }
        audit(meta.tableName(), rowId, "DELETE", before, null);
        return Map.of("id", rowId, "deleted", true);
    }

    // 后台工作区查询入口，按区域返回真实单据、明细和关联批次。
    public Map<String, Object> workArea(String area) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("area", area);
        if ("recycle".equals(area)) {
            data.put("orders", recycleOrders());
            data.put("items", jdbc.queryForList("""
                SELECT roi.*, pt.package_code, pt.package_name
                FROM cssd_recycle_order_item roi
                JOIN cssd_package_type pt ON pt.id=roi.package_type_id
                ORDER BY roi.created_at DESC
                LIMIT 200
                """));
            data.put("lots", traceLots("ORDER BY tl.created_at DESC LIMIT 100"));
            data.put("baskets", jdbc.queryForList("SELECT * FROM cssd_basket ORDER BY basket_code"));
        } else if ("pack".equals(area)) {
            data.put("lots", traceLots("WHERE tl.current_status IN ('RECYCLED','WASHED','ASSEMBLED','PART_PACKED','PACKED') ORDER BY tl.updated_at DESC LIMIT 100"));
            data.put("labels", recentLabels());
            data.put("templates", printTemplates(null));
        } else {
            data.put("packages", jdbc.queryForList("""
                SELECT pi.*, pt.package_code, pt.package_name, pt.tracking_mode, pt.package_scope
                FROM cssd_package_instance pi
                JOIN cssd_package_type pt ON pt.id=pi.package_type_id
                ORDER BY pi.updated_at DESC
                LIMIT 100
                """));
            data.put("lots", traceLots("ORDER BY tl.updated_at DESC LIMIT 100"));
        }
        return data;
    }

    // 临床批量包回收入口：回收时没有标签，因此先生成批次号，用批次承接后续追溯。
    @Transactional
    public Map<String, Object> recycleBatch(Map<String, Object> body) {
        String packageTypeCode = str(body, "packageTypeCode", "PKT-DRESS").toUpperCase(Locale.ROOT);
        String deptCode = str(body, "deptCode", "ICU").toUpperCase(Locale.ROOT);
        String basketCode = str(body, "basketCode", "BASK-01").toUpperCase(Locale.ROOT);
        int quantity = intVal(body, "quantity", 1);
        if (quantity <= 0) {
            throw new IllegalArgumentException("回收数量必须大于0");
        }

        Map<String, Object> packageType = one("SELECT * FROM cssd_package_type WHERE package_code=?", packageTypeCode);
        if (!"BATCH".equals(str(packageType.get("tracking_mode")))) {
            throw new IllegalArgumentException("该器械包不是临床批量追溯模式，请扫描一包一码");
        }
        Map<String, Object> dept = one("SELECT * FROM cssd_department WHERE dept_code=?", deptCode);

        String orderId = id();
        String orderNo = nextNo("REC");
        String lotId = id();
        String lotNo = nextNo("LOT");

        jdbc.update("""
            INSERT INTO cssd_recycle_order
            (id, order_no, source_type, source_user, recycle_user, dept_id, total_count, return_count, package_list, abnormal_record, remark, basket_code, video_url)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, orderId, orderNo, intVal(body, "sourceType", 2), str(body, "sourceUser", str(dept.get("dept_name"))),
                str(body, "operatorId", "user-operator"), dept.get("id"), quantity, 0,
                json(List.of(Map.of("packageTypeCode", packageTypeCode, "quantity", quantity, "lotNo", lotNo))),
                json(Map.of("abnormal", str(body, "abnormal", "无"))), str(body, "remark", ""), basketCode,
                bool(body, "video", true) ? "video://" + orderNo : null);

        jdbc.update("""
            INSERT INTO cssd_trace_lot
            (id, lot_no, package_type_id, dept_id, source_order_id, source_order_no, current_status, total_qty, remaining_qty, basket_code, created_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, lotId, lotNo, packageType.get("id"), dept.get("id"), orderId, orderNo, "RECYCLED", quantity, quantity,
                basketCode, str(body, "operatorId", "user-operator"));

        jdbc.update("""
            INSERT INTO cssd_recycle_order_item
            (id, recycle_order_id, order_no, line_no, tracking_mode, package_type_id, lot_id, lot_no, package_name, quantity, abnormal_flag, abnormal_desc, basket_code)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id(), orderId, orderNo, 1, "BATCH", packageType.get("id"), lotId, lotNo,
                packageType.get("package_name"), quantity, bool(body, "abnormalFlag", false) ? 1 : 0,
                str(body, "abnormal", ""), basketCode);

        jdbc.update("UPDATE cssd_basket SET status='WAIT_WASH', current_batch_no=?, package_list=? WHERE basket_code=?",
                orderNo, json(List.of(lotNo)), basketCode);

        lotEvent(lotId, lotNo, "临床批量回收", "recycle", quantity, str(body, "operatorId", "user-operator"),
                str(body, "deviceCode", "TOUCH-RECYCLE"), Map.of("orderNo", orderNo, "basketCode", basketCode));
        operation("RECYCLE_ORDER", orderId, orderNo, "CREATE_BATCH_RECYCLE", str(body, "operatorId", "user-operator"),
                str(body, "clientType", "TOUCH"), str(body, "deviceCode", "TOUCH-RECYCLE"), null,
                Map.of("orderNo", orderNo, "lotNo", lotNo, "quantity", quantity), "临床批量包按批次追溯");

        return Map.of("orderNo", orderNo, "lotNo", lotNo, "quantity", quantity, "basketCode", basketCode);
    }

    // 打包标签生成入口：临床批量包按来源批次生成标签，手术室包按唯一包码生成标签。
    @Transactional
    public Map<String, Object> printPackageLabels(Map<String, Object> body) {
        String lotNo = str(body, "lotNo", "");
        String packageCode = str(body, "packageCode", "").toUpperCase(Locale.ROOT);
        int quantity = intVal(body, "quantity", 1);
        if (quantity <= 0) {
            throw new IllegalArgumentException("打印数量必须大于0");
        }

        Map<String, Object> business = lotNo.isBlank()
                ? printUniquePackageLabel(packageCode, body)
                : printBatchPackageLabels(lotNo, quantity, body);
        return business;
    }

    // 打印模板列表查询，后台模板管理页面使用。
    public List<Map<String, Object>> printTemplates(String type) {
        if (type == null || type.isBlank()) {
            return jdbc.queryForList("SELECT * FROM cssd_print_template ORDER BY template_type, template_code");
        }
        return jdbc.queryForList("SELECT * FROM cssd_print_template WHERE template_type=? ORDER BY template_code", type);
    }

    // 打印模板保存入口，保存设计器选择的字段和属性。
    @Transactional
    public Map<String, Object> savePrintTemplate(Map<String, Object> body) {
        String rowId = id();
        jdbc.update("""
            INSERT INTO cssd_print_template
            (id, template_code, template_name, template_type, config_json, created_by, updated_by)
            VALUES(?,?,?,?,?,?,?)
            """, rowId, str(body, "templateCode", nextNo("TPL")), str(body, "templateName", "新打印模板"),
                str(body, "templateType", "PACKAGE_LABEL"), json(body.getOrDefault("config", Map.of())),
                str(body, "operatorId", "user-admin"), str(body, "operatorId", "user-admin"));
        Map<String, Object> after = one("SELECT * FROM cssd_print_template WHERE id=?", rowId);
        audit("cssd_print_template", rowId, "CREATE", null, after);
        return after;
    }

    // 打印模板修改入口，修改模板字段、位置、字号、条码等设计属性。
    @Transactional
    public Map<String, Object> updatePrintTemplate(String templateId, Map<String, Object> body) {
        Map<String, Object> before = one("SELECT * FROM cssd_print_template WHERE id=?", templateId);
        jdbc.update("""
            UPDATE cssd_print_template
            SET template_name=?, template_type=?, config_json=?, updated_by=?
            WHERE id=?
            """, str(body, "templateName", str(before.get("template_name"))),
                str(body, "templateType", str(before.get("template_type"))),
                json(body.getOrDefault("config", before.get("config_json"))),
                str(body, "operatorId", "user-admin"), templateId);
        Map<String, Object> after = one("SELECT * FROM cssd_print_template WHERE id=?", templateId);
        audit("cssd_print_template", templateId, "UPDATE", before, after);
        return after;
    }

    // 临床批次追溯查询入口，用于解决普通临床包“标签生成前如何追溯”的问题。
    public Map<String, Object> traceLot(String lotNo) {
        Map<String, Object> lot = one("""
            SELECT tl.*, pt.package_code, pt.package_name, pt.instrument_list, d.dept_code, d.dept_name
            FROM cssd_trace_lot tl
            JOIN cssd_package_type pt ON pt.id=tl.package_type_id
            JOIN cssd_department d ON d.id=tl.dept_id
            WHERE tl.lot_no=?
            """, lotNo.toUpperCase(Locale.ROOT));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lot", lot);
        data.put("events", jdbc.queryForList("SELECT * FROM cssd_trace_lot_event WHERE lot_no=? ORDER BY occurred_at", lotNo));
        data.put("recycleItems", jdbc.queryForList("SELECT * FROM cssd_recycle_order_item WHERE lot_no=? ORDER BY created_at", lotNo));
        data.put("labels", jdbc.queryForList("""
            SELECT pl.*
            FROM cssd_package_label pl
            WHERE pl.source_lot_id=?
            ORDER BY pl.print_time
            """, lot.get("id")));
        data.put("operationLogs", jdbc.queryForList("""
            SELECT *
            FROM cssd_business_operation_log
            WHERE business_no=? OR JSON_EXTRACT(after_snapshot, '$.lotNo')=?
            ORDER BY created_at
            """, lotNo, "\"" + lotNo + "\""));
        return data;
    }

    // 生成临床批量包标签，并把标签反向挂到来源批次。
    private Map<String, Object> printBatchPackageLabels(String lotNo, int quantity, Map<String, Object> body) {
        Map<String, Object> lot = one("""
            SELECT tl.*, pt.package_name, pt.instrument_list, pt.validity_days, pt.packaging_id, pt.id package_type_id
            FROM cssd_trace_lot tl
            JOIN cssd_package_type pt ON pt.id=tl.package_type_id
            WHERE tl.lot_no=?
            """, lotNo.toUpperCase(Locale.ROOT));
        int remaining = num(lot.get("remaining_qty"));
        if (quantity > remaining) {
            throw new IllegalArgumentException("打印数量不能超过批次剩余数量");
        }

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            labels.add(insertPackageLabel(lot, null, body));
        }

        int newRemaining = remaining - quantity;
        String nextStatus = newRemaining == 0 ? "PACKED" : "PART_PACKED";
        jdbc.update("UPDATE cssd_trace_lot SET remaining_qty=?, current_status=? WHERE id=?", newRemaining, nextStatus, lot.get("id"));
        lotEvent(str(lot.get("id")), str(lot.get("lot_no")), "打包完成并打印标签", "pack", -quantity,
                str(body, "packerId", "user-operator"), str(body, "deviceCode", "TOUCH-PACK"),
                Map.of("labels", labels, "remainingQty", newRemaining));
        operation("TRACE_LOT", str(lot.get("id")), str(lot.get("lot_no")), "PRINT_BATCH_LABEL",
                str(body, "packerId", "user-operator"), str(body, "clientType", "TOUCH"),
                str(body, "deviceCode", "TOUCH-PACK"), null, Map.of("labels", labels, "quantity", quantity),
                "临床批量包标签已关联来源批次");
        return Map.of("lotNo", lotNo, "labels", labels, "remainingQty", newRemaining, "status", nextStatus);
    }

    // 生成手术室一包一码标签，标签和包实例直接绑定。
    private Map<String, Object> printUniquePackageLabel(String packageCode, Map<String, Object> body) {
        Map<String, Object> pkg = one("""
            SELECT pi.id package_instance_id, pi.instance_code, pi.current_status,
                   pt.id package_type_id, pt.package_name, pt.instrument_list, pt.validity_days, pt.packaging_id
            FROM cssd_package_instance pi
            JOIN cssd_package_type pt ON pt.id=pi.package_type_id
            WHERE pi.instance_code=?
            """, packageCode);
        String labelNo = insertPackageLabel(pkg, str(pkg.get("package_instance_id")), body);
        operation("PACKAGE_INSTANCE", str(pkg.get("package_instance_id")), packageCode, "PRINT_UNIQUE_LABEL",
                str(body, "packerId", "user-operator"), str(body, "clientType", "TOUCH"),
                str(body, "deviceCode", "TOUCH-PACK"), null, Map.of("labelNo", labelNo),
                "手术室一包一码标签已打印");
        return Map.of("packageCode", packageCode, "labels", List.of(labelNo));
    }

    // 写入标签主表和打印日志，灭菌时间按用户确认规则取打印日期且精确到天。
    private String insertPackageLabel(Map<String, Object> source, String packageInstanceId, Map<String, Object> body) {
        LocalDate sterilizationDate = LocalDate.now();
        int validityDays = packagingValidityDays(source);
        LocalDate expireDate = sterilizationDate.plusDays(validityDays);
        String labelId = id();
        String labelNo = nextNo("LBL");
        String templateId = str(body, "templateId", defaultTemplateId());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("labelNo", labelNo);
        snapshot.put("packageName", source.get("package_name"));
        snapshot.put("contents", contentsText(source.get("instrument_list")));
        snapshot.put("assemblerId", str(body, "assemblerId", "user-operator"));
        snapshot.put("assemblerName", userName(str(body, "assemblerId", "user-operator")));
        snapshot.put("packerId", str(body, "packerId", "user-operator"));
        snapshot.put("packerName", userName(str(body, "packerId", "user-operator")));
        snapshot.put("printTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        snapshot.put("sterilizationDate", sterilizationDate.toString());
        snapshot.put("expireDate", expireDate.toString());

        jdbc.update("""
            INSERT INTO cssd_package_label
            (id, label_no, label_type, package_type_id, package_instance_id, source_lot_id, package_name, content_snapshot,
             assembler_id, packer_id, sterilization_date, expire_date, template_id)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, labelId, labelNo, "PACKAGE_LABEL", source.get("package_type_id"), packageInstanceId, source.get("id"),
                source.get("package_name"), json(snapshot), str(body, "assemblerId", "user-operator"),
                str(body, "packerId", "user-operator"), sterilizationDate, expireDate, templateId);
        jdbc.update("""
            INSERT INTO cssd_print_log
            (id, template_id, business_type, business_id, label_no, printer_user_id, payload_json)
            VALUES(?,?,?,?,?,?,?)
            """, id(), templateId, "PACKAGE_LABEL", labelId, labelNo, str(body, "packerId", "user-operator"), json(snapshot));
        return labelNo;
    }

    // 基础资料实体元信息，集中声明表名、允许字段和默认排序。
    private EntityMeta entityMeta(String entity) {
        return switch (entity) {
            case "departments" -> new EntityMeta("cssd_department",
                    List.of("dept_code", "dept_name", "dept_type", "barcode", "status"), "dept_code");
            case "users" -> new EntityMeta("cssd_user",
                    List.of("work_no", "user_name", "password_hash", "dept_id", "role_code", "user_type", "status", "login_method"), "work_no");
            case "instruments" -> new EntityMeta("cssd_instrument",
                    List.of("instrument_code", "instrument_name", "spec", "category", "unit"), "instrument_code");
            case "packaging" -> new EntityMeta("cssd_packaging",
                    List.of("packaging_code", "packaging_name", "validity_days", "status"), "packaging_code");
            case "packageTypes" -> new EntityMeta("cssd_package_type",
                    List.of("package_code", "package_name", "category", "tracking_mode", "package_scope", "sterilization_type",
                            "cleaning_type", "validity_days", "packaging_id", "packaging_name", "instrument_list",
                            "assemble_image", "pack_image", "status"), "package_code");
            case "equipments" -> new EntityMeta("cssd_equipment",
                    List.of("equipment_code", "equipment_name", "equipment_type", "model", "location", "status",
                            "preset_programs", "default_bio_test"), "equipment_code");
            case "baskets" -> new EntityMeta("cssd_basket",
                    List.of("basket_code", "status", "current_batch_no", "package_list"), "basket_code");
            default -> throw new IllegalArgumentException("不支持的基础资料类型：" + entity);
        };
    }

    // 归一化前端提交字段，只保留白名单字段并处理 JSON 字段。
    private Map<String, Object> normalizeValues(EntityMeta meta, Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : meta.fields()) {
            if (body.containsKey(field)) {
                Object value = body.get(field);
                values.put(field, isJsonField(field) && !(value instanceof String) ? json(value) : value);
            }
        }
        return values;
    }

    // 判断字段是否需要以 JSON 存储。
    private boolean isJsonField(String field) {
        return List.of("instrument_list", "package_list").contains(field);
    }

    // 查询回收工作区单据列表，供后台查看和后续编辑回收单。
    private List<Map<String, Object>> recycleOrders() {
        return jdbc.queryForList("""
            SELECT ro.*, d.dept_code, d.dept_name, u.user_name recycle_user_name
            FROM cssd_recycle_order ro
            LEFT JOIN cssd_department d ON d.id=ro.dept_id
            LEFT JOIN cssd_user u ON u.id=ro.recycle_user
            ORDER BY ro.recycle_time DESC
            LIMIT 100
            """);
    }

    // 查询批次列表，工作区按批次展示普通临床科室包的状态。
    private List<Map<String, Object>> traceLots(String tail) {
        return jdbc.queryForList("""
            SELECT tl.*, pt.package_code, pt.package_name, pt.tracking_mode, d.dept_code, d.dept_name
            FROM cssd_trace_lot tl
            JOIN cssd_package_type pt ON pt.id=tl.package_type_id
            JOIN cssd_department d ON d.id=tl.dept_id
            """ + tail);
    }

    // 查询最近打印的标签，便于后台打包区核对打印结果。
    private List<Map<String, Object>> recentLabels() {
        return jdbc.queryForList("""
            SELECT pl.label_no, pl.package_name, pl.print_time, pl.sterilization_date, pl.expire_date,
                   au.user_name assembler_name, pu.user_name packer_name
            FROM cssd_package_label pl
            LEFT JOIN cssd_user au ON au.id=pl.assembler_id
            LEFT JOIN cssd_user pu ON pu.id=pl.packer_id
            ORDER BY pl.print_time DESC
            LIMIT 100
            """);
    }

    // 写入临床批次事件，批次在生成标签前的每一步都靠这里留痕。
    private void lotEvent(String lotId, String lotNo, String eventType, String station, int qtyDelta,
                          String operatorId, String deviceCode, Map<String, Object> payload) {
        jdbc.update("""
            INSERT INTO cssd_trace_lot_event
            (id, lot_id, lot_no, event_type, station, qty_delta, operator_id, device_code, payload)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, id(), lotId, lotNo, eventType, station, qtyDelta, operatorId, deviceCode, json(payload));
    }

    // 写入业务操作日志，跟随单据或批次保存端类型、设备号、操作人和快照。
    private void operation(String businessType, String businessId, String businessNo, String action, String operatorId,
                           String clientType, String deviceCode, Object before, Object after, String remark) {
        jdbc.update("""
            INSERT INTO cssd_business_operation_log
            (id, business_type, business_id, business_no, action, operator_id, client_type, device_code, before_snapshot, after_snapshot, remark)
            VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """, id(), businessType, businessId, businessNo, action, operatorId, clientType, deviceCode,
                before == null ? null : json(before), after == null ? null : json(after), remark);
    }

    // 写入数据审计日志，后台增删改都可反查修改前后数据。
    private void audit(String tableName, String rowId, String action, Object before, Object after) {
        jdbc.update("""
            INSERT INTO cssd_data_audit_log
            (id, table_name, row_id, action, operator_id, before_snapshot, after_snapshot)
            VALUES(?,?,?,?,?,?,?)
            """, id(), tableName, rowId, action, "user-admin",
                before == null ? null : json(before), after == null ? null : json(after));
    }

    // 从包材表读取有效期天数，若未维护包材则回退到器械包自身有效期。
    private int packagingValidityDays(Map<String, Object> source) {
        String packagingId = str(source.get("packaging_id"));
        if (!packagingId.isBlank()) {
            try {
                return jdbc.queryForObject("SELECT validity_days FROM cssd_packaging WHERE id=?", Integer.class, packagingId);
            } catch (EmptyResultDataAccessException ignored) {
                return num(source.get("validity_days"));
            }
        }
        return num(source.get("validity_days"));
    }

    // 获取默认器械包标签模板。
    private String defaultTemplateId() {
        return jdbc.queryForObject("""
            SELECT id FROM cssd_print_template
            WHERE template_type='PACKAGE_LABEL' AND status=1
            ORDER BY updated_at DESC
            LIMIT 1
            """, String.class);
    }

    // 读取人员姓名，标签上显示配包人和打包人的姓名。
    private String userName(String userId) {
        try {
            return jdbc.queryForObject("SELECT user_name FROM cssd_user WHERE id=?", String.class, userId);
        } catch (EmptyResultDataAccessException ex) {
            return userId;
        }
    }

    // 将器械包内容物 JSON 转成标签可读文本。
    private String contentsText(Object instrumentList) {
        if (instrumentList == null || instrumentList.toString().isBlank()) {
            return "";
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(instrumentList.toString(), new TypeReference<>() {});
            return String.join("；", rows.stream()
                    .map(row -> row.get("name") + "x" + row.getOrDefault("qty", 1))
                    .toList());
        } catch (Exception ex) {
            return instrumentList.toString();
        }
    }

    // 查询单行数据，统一把未找到转换成业务提示。
    private Map<String, Object> one(String sql, Object... args) {
        try {
            return jdbc.queryForMap(sql, args);
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalArgumentException("未找到对应数据");
        }
    }

    // 从请求体读取字符串字段。
    private String str(Map<String, Object> body, String key, String fallback) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString();
    }

    // 将任意对象转成字符串。
    private String str(Object value) {
        return value == null ? "" : value.toString();
    }

    // 从请求体读取整数字段。
    private int intVal(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.toString());
    }

    // 将数据库数字字段安全转成整数。
    private int num(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    // 从请求体读取布尔字段，兼容触摸台和 PDA 传入的常见写法。
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

    // 生成数据库主键。
    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 生成业务编号。
    private String nextNo(String prefix) {
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    // 将对象序列化为 JSON 字符串。
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("JSON序列化失败：" + ex.getMessage());
        }
    }

    // 基础资料实体白名单描述。
    private record EntityMeta(String tableName, List<String> fields, String orderBy) {
    }
}
