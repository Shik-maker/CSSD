const apiBase = "api";

const state = {
    page: "dashboard",
    authed: Boolean(localStorage.getItem("cssdAdminToken")),
    user: JSON.parse(localStorage.getItem("cssdAdminUser") || "null"),
    dashboard: null,
    basic: null,
    workArea: null
};

const pageTitles = {
    dashboard: "工作区总览",
    basic: "基础信息",
    recycleWork: "回收工作区",
    print: "打印模板",
    config: "参数配置",
    trace: "追溯查询",
    report: "统计报表"
};

const statusLabels = {
    IN_DEPT: ["科室在库", "blue"],
    RECYCLED: ["待清洗", "amber"],
    WASHING: ["清洗中", "amber"],
    WASHED: ["待配包", "green"],
    ASSEMBLED: ["待打包", "green"],
    PACKED: ["待灭菌", "amber"],
    STERILIZING: ["灭菌中", "amber"],
    BIO_PENDING: ["待生物监测", "red"],
    STERILIZED: ["待发放", "green"],
    LOCKED_RECALL: ["锁定召回", "red"]
};

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".nav-item").forEach(btn => {
        btn.addEventListener("click", async () => {
            state.page = btn.dataset.page;
            document.querySelectorAll(".nav-item").forEach(x => x.classList.remove("active"));
            btn.classList.add("active");
            await refresh();
        });
    });
    document.getElementById("refreshBtn").addEventListener("click", refresh);
    document.getElementById("logoutBtn").addEventListener("click", logout);
    setInterval(updateClock, 1000);
    updateClock();
    refresh();
});

async function refresh() {
    await health();
    document.getElementById("nav").classList.toggle("hidden", !state.authed);
    document.getElementById("logoutBtn").classList.toggle("hidden", !state.authed);
    if (!state.authed) {
        renderLogin();
        return;
    }
    if (state.page === "dashboard" || state.page === "report") {
        state.dashboard = await api("dashboard");
    }
    if (state.page === "basic" || state.page === "config") {
        state.basic = await api("basic");
    }
    if (state.page === "recycleWork") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/recycle");
    }
    if (state.page === "print") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/pack");
    }
    render();
}

async function health() {
    const dot = document.getElementById("healthDot");
    const text = document.getElementById("healthText");
    try {
        await api("health", { silent: true });
        dot.className = "dot ok";
        text.textContent = "服务正常";
    } catch {
        dot.className = "dot bad";
        text.textContent = "服务异常";
    }
}

async function api(path, options = {}) {
    const response = await fetch(`${apiBase}/${path}`, {
        method: options.method || "GET",
        headers: { "Content-Type": "application/json" },
        body: options.body ? JSON.stringify(options.body) : undefined
    });
    const json = await response.json();
    if (!json.success) {
        if (!options.silent) showNotice(json.message || "操作失败", true);
        throw new Error(json.message);
    }
    return json.data;
}

function renderLogin() {
    document.getElementById("pageTitle").textContent = "后台登录";
    document.getElementById("content").innerHTML = `
        <section class="panel" style="max-width:460px">
            <div class="panel-head"><h2>CSSD管理后台</h2></div>
            <div class="panel-body big-scan">
                <label>工号<input id="loginWorkNo" value="A001" autocomplete="username"></label>
                <label>密码<input id="loginPassword" type="password" value="123456" autocomplete="current-password"></label>
                <button class="primary" id="loginBtn">登录</button>
                <p class="subtle">初始账号：A001 / 123456</p>
            </div>
        </section>
    `;
    document.getElementById("loginBtn").addEventListener("click", login);
}

function render() {
    document.getElementById("pageTitle").textContent = pageTitles[state.page];
    const content = document.getElementById("content");
    if (state.page === "dashboard") content.innerHTML = renderDashboard();
    if (state.page === "basic") content.innerHTML = renderBasic();
    if (state.page === "recycleWork") content.innerHTML = renderRecycleWork();
    if (state.page === "print") content.innerHTML = renderPrint();
    if (state.page === "config") content.innerHTML = renderConfig();
    if (state.page === "trace") content.innerHTML = renderTrace();
    if (state.page === "report") content.innerHTML = renderReport();
    bindActions();
}

async function login() {
    const data = await api("auth/login", {
        method: "POST",
        body: { workNo: value("loginWorkNo"), password: value("loginPassword") }
    });
    localStorage.setItem("cssdAdminToken", data.token);
    localStorage.setItem("cssdAdminUser", JSON.stringify(data.user));
    state.authed = true;
    state.user = data.user;
    showNotice(`欢迎，${data.user.user_name}`);
    await refresh();
}

function logout() {
    localStorage.removeItem("cssdAdminToken");
    localStorage.removeItem("cssdAdminUser");
    state.authed = false;
    state.user = null;
    renderLogin();
}

function renderDashboard() {
    const data = state.dashboard || {};
    const metrics = (data.stations || []).map(row => `
        <div class="metric">
            <b>${row.count}</b>
            <span>${row.station}</span>
        </div>
    `).join("");
    return `
        <div class="grid cols-4">${metrics}</div>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>待办工作区</h2><span class="tag green">管理端查看</span></div>
                <div class="panel-body">${table(data.recentEvents || [], [
                    ["package_code", "包编码"],
                    ["event_type", "事件"],
                    ["station", "来源工位"],
                    ["device_code", "设备"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>设备运行状态</h2></div>
                <div class="panel-body">${table(data.equipment || [], [
                    ["equipment_code", "设备编号"],
                    ["equipment_name", "设备名称"],
                    ["status", "状态", v => equipmentStatus(v)]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>近效期提醒</h2></div>
            <div class="panel-body">${table(data.nearExpiry || [], [
                ["instance_code", "包编码"],
                ["package_name", "包名称"],
                ["dept_name", "所在科室"],
                ["sterilization_expire_at", "效期"]
            ])}</div>
        </section>
    `;
}

function renderBasic() {
    const data = state.basic || {};
    return `
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>器械包管理</h2><button class="ghost" id="addClinicalPackageBtn">新增临床包示例</button></div>
                <div class="panel-body">${table(data.packageTypes || [], [
                    ["package_code", "包编码"],
                    ["package_name", "包名称"],
                    ["category", "分类"],
                    ["tracking_mode", "追溯模式", v => trackingModeTag(v)],
                    ["package_scope", "业务范围"],
                    ["validity_days", "有效期"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>器械实例状态</h2></div>
                <div class="panel-body">${table(data.packages || [], [
                    ["instance_code", "包条码"],
                    ["package_name", "包名称"],
                    ["current_status", "状态", v => statusTag(v)],
                    ["dept_name", "所在科室"]
                ])}</div>
            </section>
        </div>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>包材管理</h2><button class="ghost" id="addPackagingBtn">新增包材</button></div>
                <div class="panel-body">${table(data.packaging || [], [
                    ["packaging_code", "包材编码"],
                    ["packaging_name", "包材名称"],
                    ["validity_days", "有效期天数"],
                    ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>筐管理</h2><button class="ghost" id="addBasketBtn">新增筐</button></div>
                <div class="panel-body">${table(data.baskets || [], [
                    ["basket_code", "筐编码"],
                    ["status", "状态"],
                    ["current_batch_no", "当前批次"],
                    ["package_list", "绑定内容"]
                ])}</div>
            </section>
        </div>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>科室管理</h2></div>
                <div class="panel-body">${table(data.departments || [], [
                    ["dept_code", "科室编码"],
                    ["dept_name", "科室名称"],
                    ["dept_type", "类型"],
                    ["barcode", "条码"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>人员与角色</h2></div>
                <div class="panel-body">${table(data.users || [], [
                    ["work_no", "工号"],
                    ["user_name", "姓名"],
                    ["role_code", "角色"],
                    ["user_type", "人员类型"]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>临床批次追溯账本</h2><span class="tag blue">普通临床包标签生成前按批次追溯</span></div>
            <div class="panel-body">${table(data.traceLots || [], [
                ["lot_no", "批次号"],
                ["package_name", "包名称"],
                ["dept_name", "来源科室"],
                ["current_status", "状态", v => statusTag(v)],
                ["total_qty", "回收数"],
                ["remaining_qty", "未打包数"]
            ])}</div>
        </section>
    `;
}

// 渲染回收工作区：展示触摸台/PDA 写入后的回收单、明细和临床批次。
function renderRecycleWork() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>临床批量回收录入</h2><span class="tag amber">触摸台/PDA 写入后后台同步可见</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>包类型<select id="batchPackageType">${packageOptions("BATCH")}</select></label>
                    <label>来源科室<select id="batchDept">${deptOptions()}</select></label>
                    <label>回收数量<input id="batchQty" type="number" min="1" value="5"></label>
                    <label>绑定筐<select id="batchBasket">${basketOptions()}</select></label>
                    <button class="primary" id="createBatchRecycleBtn">生成回收单</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>回收单</h2></div>
                <div class="panel-body">${table(data.orders || [], [
                    ["order_no", "单号"],
                    ["dept_name", "科室"],
                    ["total_count", "数量"],
                    ["basket_code", "筐"],
                    ["recycle_time", "回收时间"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>回收明细</h2></div>
                <div class="panel-body">${table(data.items || [], [
                    ["order_no", "单号"],
                    ["package_name", "包名称"],
                    ["tracking_mode", "追溯模式", v => trackingModeTag(v)],
                    ["lot_no", "批次号"],
                    ["quantity", "数量"]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>待流转批次</h2></div>
            <div class="panel-body">${table(data.lots || [], [
                ["lot_no", "批次号"],
                ["package_name", "包名称"],
                ["dept_name", "来源科室"],
                ["current_status", "状态", v => statusTag(v)],
                ["total_qty", "回收数"],
                ["remaining_qty", "未打包数"],
                ["basket_code", "筐"]
            ])}</div>
        </section>
    `;
}

// 渲染打印模板页：管理模板，并按临床批次生成器械包标签。
function renderPrint() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>器械包标签打印</h2><span class="tag green">灭菌时间=打印日期</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>来源批次<select id="printLot">${lotOptions()}</select></label>
                    <label>打印数量<input id="printQty" type="number" min="1" value="1"></label>
                    <label>模板<select id="printTemplate">${templateOptions("PACKAGE_LABEL")}</select></label>
                    <button class="primary" id="printLabelBtn">打印标签</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>打印模板管理</h2><button class="ghost" id="addTemplateBtn">新增模板</button></div>
                <div class="panel-body">${table(data.templates || [], [
                    ["template_code", "模板编码"],
                    ["template_name", "模板名称"],
                    ["template_type", "类型"],
                    ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>最近标签</h2></div>
                <div class="panel-body">${table(data.labels || [], [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["assembler_name", "配包人"],
                    ["packer_name", "打包人"],
                    ["sterilization_date", "灭菌时间"],
                    ["expire_date", "失效时间"]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>可打印批次</h2></div>
            <div class="panel-body">${table(data.lots || [], [
                ["lot_no", "批次号"],
                ["package_name", "包名称"],
                ["remaining_qty", "未打印数量"],
                ["current_status", "状态", v => statusTag(v)]
            ])}</div>
        </section>
    `;
}

function renderConfig() {
    const data = state.basic || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>系统参数配置</h2><span class="tag">后续推送到触摸台配置缓存</span></div>
            <div class="panel-body">${table(data.configs || [], [
                ["module_code", "模块"],
                ["config_name", "配置项"],
                ["config_value", "当前值"],
                ["remark", "说明"],
                ["config_key", "操作", v => `<button class="ghost edit-config" data-key="${v}">修改</button>`]
            ])}</div>
        </section>
    `;
}

function renderTrace() {
    return `
        <section class="panel">
            <div class="panel-head"><h2>包追溯链查询</h2></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>包条码<input id="traceCode" value="PKG-0001"></label>
                    <button class="primary" id="traceBtn">查询</button>
                </div>
                <div id="traceResult" class="split-line"></div>
            </div>
        </section>
        <section class="panel">
            <div class="panel-head"><h2>临床批次追溯查询</h2></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>批次号<input id="traceLotNo" placeholder="LOT-..."></label>
                    <button class="primary" id="traceLotBtn">查询批次</button>
                </div>
                <div id="traceLotResult" class="split-line"></div>
            </div>
        </section>
    `;
}

function renderReport() {
    const data = state.dashboard || {};
    return `
        <div class="grid cols-3">
            ${(data.stations || []).slice(0, 6).map(row => `<div class="metric"><b>${row.count}</b><span>${row.station}工作量</span></div>`).join("")}
        </div>
        <section class="panel">
            <div class="panel-head"><h2>跨区域综合分析</h2></div>
            <div class="panel-body">${table(data.statusCounts || [], [
                ["status", "包状态", v => statusTag(v)],
                ["total", "数量"]
            ])}</div>
        </section>
    `;
}

function bindActions() {
    document.querySelectorAll(".edit-config").forEach(btn => btn.addEventListener("click", async () => {
        const newValue = prompt("请输入新的配置值");
        if (newValue === null) return;
        await api(`configs/${btn.dataset.key}`, { method: "PUT", body: { value: newValue } });
        showNotice("配置已更新");
        state.basic = await api("basic");
        render();
    }));
    const traceBtn = document.getElementById("traceBtn");
    if (traceBtn) traceBtn.addEventListener("click", traceQuery);
    const traceLotBtn = document.getElementById("traceLotBtn");
    if (traceLotBtn) traceLotBtn.addEventListener("click", traceLotQuery);
    const createBatchRecycleBtn = document.getElementById("createBatchRecycleBtn");
    if (createBatchRecycleBtn) createBatchRecycleBtn.addEventListener("click", createBatchRecycle);
    const printLabelBtn = document.getElementById("printLabelBtn");
    if (printLabelBtn) printLabelBtn.addEventListener("click", printLabel);
    const addPackagingBtn = document.getElementById("addPackagingBtn");
    if (addPackagingBtn) addPackagingBtn.addEventListener("click", addPackaging);
    const addBasketBtn = document.getElementById("addBasketBtn");
    if (addBasketBtn) addBasketBtn.addEventListener("click", addBasket);
    const addClinicalPackageBtn = document.getElementById("addClinicalPackageBtn");
    if (addClinicalPackageBtn) addClinicalPackageBtn.addEventListener("click", addClinicalPackage);
    const addTemplateBtn = document.getElementById("addTemplateBtn");
    if (addTemplateBtn) addTemplateBtn.addEventListener("click", addTemplate);
}

async function traceQuery() {
    const code = value("traceCode");
    const data = await api(`trace/${encodeURIComponent(code)}`);
    document.getElementById("traceResult").innerHTML = `
        <div class="grid cols-2">
            <div>${table([data.package], [
                ["instance_code", "包编码"],
                ["package_name", "包名称"],
                ["current_status", "状态", v => statusTag(v)],
                ["dept_name", "当前位置"],
                ["current_batch_no", "当前批次"]
            ])}</div>
            <div>${table(data.events || [], [
                ["occurred_at", "时间"],
                ["event_type", "事件"],
                ["station", "工位"],
                ["offline_flag", "来源", v => v ? tag("PDA离线", "amber") : tag("在线", "green")]
            ])}</div>
        </div>
    `;
}

// 查询临床批次追溯链：用于普通临床包在标签生成前后的完整追溯。
async function traceLotQuery() {
    const lotNo = value("traceLotNo");
    const data = await api(`trace/lot/${encodeURIComponent(lotNo)}`);
    document.getElementById("traceLotResult").innerHTML = `
        <div class="grid cols-2">
            <div>${table([data.lot], [
                ["lot_no", "批次号"],
                ["package_name", "包名称"],
                ["dept_name", "来源科室"],
                ["current_status", "状态", v => statusTag(v)],
                ["total_qty", "回收数"],
                ["remaining_qty", "未打包数"]
            ])}</div>
            <div>${table(data.events || [], [
                ["occurred_at", "时间"],
                ["event_type", "事件"],
                ["station", "工位"],
                ["qty_delta", "数量变化"]
            ])}</div>
        </div>
        ${table(data.labels || [], [
            ["label_no", "标签号"],
            ["package_name", "包名"],
            ["print_time", "打印时间"],
            ["sterilization_date", "灭菌时间"],
            ["expire_date", "失效时间"]
        ])}
    `;
}

// 创建临床批量回收单：模拟触摸台/PDA 提交批量回收业务。
async function createBatchRecycle() {
    await api("workflow/recycle/batch", {
        method: "POST",
        body: {
            packageTypeCode: value("batchPackageType"),
            deptCode: value("batchDept"),
            basketCode: value("batchBasket"),
            quantity: Number(value("batchQty") || 1),
            deviceCode: "WEB-RECYCLE",
            clientType: "WEB"
        }
    });
    showNotice("回收单和临床批次已生成");
    state.workArea = await api("workarea/recycle");
    render();
}

// 打印器械包标签：标签会记录配包人、打包人、打印时间、灭菌日期和失效日期。
async function printLabel() {
    const data = await api("print/package-labels", {
        method: "POST",
        body: {
            lotNo: value("printLot"),
            quantity: Number(value("printQty") || 1),
            templateId: value("printTemplate"),
            assemblerId: "user-operator",
            packerId: "user-operator",
            deviceCode: "WEB-PACK",
            clientType: "WEB"
        }
    });
    showNotice(`已生成标签：${data.labels.join("，")}`);
    state.workArea = await api("workarea/pack");
    render();
}

// 新增包材：包材有效期会参与标签失效日期计算。
async function addPackaging() {
    const code = `PACK-${Date.now().toString().slice(-5)}`;
    await api("admin/packaging", {
        method: "POST",
        body: { packaging_code: code, packaging_name: "新增包材", validity_days: 60, status: 1 }
    });
    showNotice("包材已新增，可继续编辑维护");
    state.basic = await api("basic");
    render();
}

// 新增筐：筐用于回收和清洗环节绑定批次或包。
async function addBasket() {
    const code = `BASK-${Date.now().toString().slice(-4)}`;
    await api("admin/baskets", {
        method: "POST",
        body: { basket_code: code, status: "IDLE", package_list: [] }
    });
    showNotice("筐已新增");
    state.basic = await api("basic");
    render();
}

// 新增临床批量包示例：默认使用批次追溯模式。
async function addClinicalPackage() {
    const suffix = Date.now().toString().slice(-4);
    await api("admin/packageTypes", {
        method: "POST",
        body: {
            package_code: `PKT-CLINIC-${suffix}`,
            package_name: `临床批量包${suffix}`,
            category: "临床包",
            tracking_mode: "BATCH",
            package_scope: "CLINICAL",
            sterilization_type: 1,
            cleaning_type: 2,
            validity_days: 30,
            packaging_id: "pack-paper",
            packaging_name: "纸塑袋",
            instrument_list: [{ code: "INS-001", name: "持针器", qty: 1 }],
            status: 1
        }
    });
    showNotice("临床批量包示例已新增");
    state.basic = await api("basic");
    render();
}

// 新增打印模板：保存模板设计器需要的字段配置。
async function addTemplate() {
    await api("print/templates", {
        method: "POST",
        body: {
            templateCode: `TPL-${Date.now().toString().slice(-5)}`,
            templateName: "新增标签模板",
            templateType: "PACKAGE_LABEL",
            config: { width: 80, height: 50, fields: [{ key: "packageName", title: "包名", visible: true }] }
        }
    });
    showNotice("打印模板已新增");
    state.workArea = await api("workarea/pack");
    render();
}

// 生成包类型下拉选项，可按追溯模式过滤。
function packageOptions(mode) {
    const rows = ((state.basic && state.basic.packageTypes) || (state.workArea && state.workArea.packageTypes) || []);
    return rows
        .filter(row => !mode || row.tracking_mode === mode)
        .map(row => `<option value="${safe(row.package_code)}">${safe(row.package_name)}（${safe(row.package_code)}）</option>`)
        .join("");
}

// 生成科室下拉选项，回收来源默认排除 CSSD 自身。
function deptOptions() {
    const rows = (state.basic && state.basic.departments) || [];
    return rows
        .filter(row => row.dept_code !== "CSSD")
        .map(row => `<option value="${safe(row.dept_code)}">${safe(row.dept_name)}</option>`)
        .join("");
}

// 生成筐下拉选项，显示当前筐状态便于操作员选择。
function basketOptions() {
    const rows = (state.workArea && state.workArea.baskets) || (state.basic && state.basic.baskets) || [];
    return rows.map(row => `<option value="${safe(row.basket_code)}">${safe(row.basket_code)} - ${safe(row.status)}</option>`).join("");
}

// 生成可打印批次下拉选项，只显示仍有剩余数量的批次。
function lotOptions() {
    const rows = (state.workArea && state.workArea.lots) || [];
    return rows
        .filter(row => Number(row.remaining_qty) > 0)
        .map(row => `<option value="${safe(row.lot_no)}">${safe(row.lot_no)} - ${safe(row.package_name)} - 剩余${safe(row.remaining_qty)}</option>`)
        .join("");
}

// 生成打印模板下拉选项，可按模板类型过滤。
function templateOptions(type) {
    const rows = (state.workArea && state.workArea.templates) || [];
    return rows
        .filter(row => !type || row.template_type === type)
        .map(row => `<option value="${safe(row.id)}">${safe(row.template_name)}</option>`)
        .join("");
}

// 将追溯模式转换成后台页面可读的标签。
function trackingModeTag(value) {
    return value === "BATCH" ? tag("临床批量", "amber") : tag("一包一码", "green");
}

function table(rows, columns) {
    if (!rows || !rows.length) return document.getElementById("emptyTpl").innerHTML;
    return `
        <table>
            <thead><tr>${columns.map(c => `<th>${c[1]}</th>`).join("")}</tr></thead>
            <tbody>${rows.map(row => `<tr>${columns.map(c => `<td>${c[2] ? c[2](row[c[0]], row) : safe(row[c[0]])}</td>`).join("")}</tr>`).join("")}</tbody>
        </table>
    `;
}

function statusTag(value) {
    const item = statusLabels[value] || [value || "未知", ""];
    return tag(item[0], item[1]);
}

function equipmentStatus(value) {
    return Number(value) === 1 ? tag("空闲", "green")
        : Number(value) === 2 ? tag("运行中", "amber")
        : Number(value) === 3 ? tag("故障", "red")
        : tag("维护/停用", "red");
}

function tag(text, color = "") {
    return `<span class="tag ${color}">${safe(text)}</span>`;
}

function safe(value) {
    if (value === null || value === undefined) return "";
    return String(value).replace(/[&<>"']/g, s => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[s]));
}

function value(id) {
    const el = document.getElementById(id);
    return el ? el.value.trim() : "";
}

function showNotice(message, error = false) {
    const notice = document.getElementById("notice");
    notice.textContent = message;
    notice.className = `notice ${error ? "error" : ""}`;
    clearTimeout(showNotice.timer);
    showNotice.timer = setTimeout(() => notice.classList.add("hidden"), 3800);
}

function updateClock() {
    document.getElementById("clock").textContent = new Date().toLocaleString();
}
