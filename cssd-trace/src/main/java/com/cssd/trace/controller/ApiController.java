package com.cssd.trace.controller;

import com.cssd.trace.service.TraceService;
import com.cssd.trace.service.BusinessService;
import com.cssd.trace.support.ApiResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class ApiController {

    private final TraceService traceService;
    private final BusinessService businessService;

    public ApiController(TraceService traceService, BusinessService businessService) {
        this.traceService = traceService;
        this.businessService = businessService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "service", "cssd-trace",
                "status", "UP",
                "time", LocalDateTime.now()
        ));
    }

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.login(body));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(traceService.dashboard());
    }

    @GetMapping("/basic")
    public ApiResponse<Map<String, Object>> basicData() {
        return ApiResponse.ok(traceService.basicData());
    }

    // 后台基础资料列表：器械包、器械、包材、设备、科室、人员、筐都从这里读取。
    @GetMapping("/admin/{entity}")
    public ApiResponse<Map<String, Object>> listEntity(@PathVariable String entity) {
        return ApiResponse.ok(businessService.listEntity(entity));
    }

    // 后台基础资料新增：新增后会同步记录数据审计日志。
    @PostMapping("/admin/{entity}")
    public ApiResponse<Map<String, Object>> createEntity(@PathVariable String entity, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.createEntity(entity, body));
    }

    // 后台基础资料修改：修改前后快照会进入审计日志。
    @PutMapping("/admin/{entity}/{id}")
    public ApiResponse<Map<String, Object>> updateEntity(@PathVariable String entity, @PathVariable String id,
                                                         @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.updateEntity(entity, id, body));
    }

    // 后台基础资料删除或停用：优先停用，避免历史追溯链被物理删除破坏。
    @DeleteMapping("/admin/{entity}/{id}")
    public ApiResponse<Map<String, Object>> deleteEntity(@PathVariable String entity, @PathVariable String id) {
        return ApiResponse.ok(businessService.deleteEntity(entity, id));
    }

    @GetMapping("/configs")
    public ApiResponse<?> configs() {
        return ApiResponse.ok(traceService.configs());
    }

    @PutMapping("/configs/{key}")
    public ApiResponse<Map<String, Object>> updateConfig(@PathVariable String key, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.updateConfig(key, body));
    }

    @GetMapping("/station/{station}")
    public ApiResponse<Map<String, Object>> station(@PathVariable String station) {
        return ApiResponse.ok(traceService.station(station));
    }

    // 后台工作区查询：工作区读取真实单据、明细、批次和标签数据。
    @GetMapping("/workarea/{area}")
    public ApiResponse<Map<String, Object>> workArea(@PathVariable String area) {
        return ApiResponse.ok(businessService.workArea(area));
    }

    @PostMapping("/workflow/recycle")
    public ApiResponse<Map<String, Object>> recycle(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.recycle(body));
    }

    // 临床批量包回收：没有标签时先生成批次号，用批次贯穿回收、清洗、打包和后续标签。
    @PostMapping("/workflow/recycle/batch")
    public ApiResponse<Map<String, Object>> recycleBatch(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.recycleBatch(body));
    }

    @PostMapping("/workflow/wash/start")
    public ApiResponse<Map<String, Object>> washStart(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.washStart(body));
    }

    // 临床批量批次开始清洗：按批次而不是标签推动普通临床包流转。
    @PostMapping("/workflow/lot/wash/start")
    public ApiResponse<Map<String, Object>> washLotStart(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.washLotStart(body));
    }

    @PostMapping("/workflow/wash/finish")
    public ApiResponse<Map<String, Object>> washFinish(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.washFinish(body));
    }

    // 临床批量批次完成清洗：合格进入待配包，不合格退回待清洗。
    @PostMapping("/workflow/lot/wash/finish")
    public ApiResponse<Map<String, Object>> washLotFinish(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.washLotFinish(body));
    }

    @PostMapping("/workflow/station/complete")
    public ApiResponse<Map<String, Object>> completeStation(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.completeStation(body));
    }

    // 临床批量批次配包完成：记录配包人，并把批次推送到打包标签环节。
    @PostMapping("/workflow/lot/assemble")
    public ApiResponse<Map<String, Object>> assembleLot(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.assembleLot(body));
    }

    @PostMapping("/workflow/sterilize/start")
    public ApiResponse<Map<String, Object>> sterilizeStart(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.sterilizeStart(body));
    }

    // 标签开始灭菌：临床批量包打包后以标签号进入灭菌批次。
    @PostMapping("/workflow/label/sterilize/start")
    public ApiResponse<Map<String, Object>> sterilizeLabelsStart(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.sterilizeLabelsStart(body));
    }

    @PostMapping("/workflow/sterilize/finish")
    public ApiResponse<Map<String, Object>> sterilizeFinish(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.sterilizeFinish(body));
    }

    // 标签完成灭菌：合格标签进入待发放，不合格标签退回待灭菌。
    @PostMapping("/workflow/label/sterilize/finish")
    public ApiResponse<Map<String, Object>> sterilizeLabelsFinish(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.sterilizeLabelsFinish(body));
    }

    @PostMapping("/workflow/bio-test")
    public ApiResponse<Map<String, Object>> bioTest(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.bioTest(body));
    }

    @PostMapping("/workflow/distribute")
    public ApiResponse<Map<String, Object>> distribute(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.distribute(body));
    }

    // 标签发放：灭菌合格标签按科室生成发放单。
    @PostMapping("/workflow/label/distribute")
    public ApiResponse<Map<String, Object>> distributeLabels(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.distributeLabels(body));
    }

    // 打包标签打印：支持手术室一包一码，也支持临床批次生成多个标签。
    @PostMapping("/print/package-labels")
    public ApiResponse<Map<String, Object>> printPackageLabels(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.printPackageLabels(body));
    }

    // 打印模板列表：用于后台打印模板管理和触摸台取模板。
    @GetMapping("/print/templates")
    public ApiResponse<?> printTemplates() {
        return ApiResponse.ok(businessService.printTemplates(null));
    }

    // 打印模板新增：保存设计器字段和属性配置。
    @PostMapping("/print/templates")
    public ApiResponse<Map<String, Object>> savePrintTemplate(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.savePrintTemplate(body));
    }

    // 打印模板修改：更新字段、位置、字号、显隐、条码等属性。
    @PutMapping("/print/templates/{id}")
    public ApiResponse<Map<String, Object>> updatePrintTemplate(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(businessService.updatePrintTemplate(id, body));
    }

    @PostMapping("/borrow/apply")
    public ApiResponse<Map<String, Object>> borrowApply(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.borrowApply(body));
    }

    @PostMapping("/borrow/audit")
    public ApiResponse<Map<String, Object>> borrowAudit(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.borrowAudit(body));
    }

    @PostMapping("/borrow/distribute")
    public ApiResponse<Map<String, Object>> borrowDistribute(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.borrowDistribute(body));
    }

    @PostMapping("/pda/sync")
    public ApiResponse<Map<String, Object>> pdaSync(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(traceService.pdaSync(body));
    }

    @GetMapping("/trace/{packageCode}")
    public ApiResponse<Map<String, Object>> trace(@PathVariable String packageCode) {
        return ApiResponse.ok(traceService.trace(packageCode));
    }

    // 临床批次追溯：用于普通临床科室包在标签生成前后的完整链路查询。
    @GetMapping("/trace/lot/{lotNo}")
    public ApiResponse<Map<String, Object>> traceLot(@PathVariable String lotNo) {
        return ApiResponse.ok(businessService.traceLot(lotNo));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<?> handleBusinessError(IllegalArgumentException ex) {
        return ApiResponse.fail(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleSystemError(Exception ex) {
        return ApiResponse.fail("系统处理失败：" + ex.getMessage());
    }
}
