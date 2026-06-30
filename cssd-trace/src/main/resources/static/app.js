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
    washWork: "清洗工作区",
    assembleWork: "配包工作区",
    print: "打印模板",
    sterilizeWork: "灭菌工作区",
    bioWork: "生物监测工作区",
    distributeWork: "发放工作区",
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
    PRINTED: ["已打印待灭菌", "amber"],
    DISTRIBUTED: ["已发放", "blue"],
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
    if (state.page === "washWork") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/wash");
    }
    if (state.page === "assembleWork") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/assemble");
    }
    if (state.page === "print") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/pack");
    }
    if (state.page === "sterilizeWork") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/sterilize");
    }
    if (state.page === "bioWork") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/bio");
    }
    if (state.page === "distributeWork") {
        state.basic = await api("basic");
        state.workArea = await api("workarea/distribute");
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
        <section class="admin-login">
            <div class="login-visual">
                <div class="login-badge">CSSD</div>
                <h2>CSSD 消毒供应中心追溯系统</h2>
                <p>复用无菌器械全生命周期追溯管理</p>
                <div class="flow-ribbon">
                    ${["回收", "清洗", "配包", "打包", "灭菌", "发放", "使用/借包"].map(x => `<span>${x}</span>`).join("")}
                </div>
                <div class="login-feature-row">
                    <span>全流程追溯</span>
                    <span>质量安全管控</span>
                    <span>数据统计分析</span>
                    <span>视频过程留痕</span>
                </div>
            </div>
            <div class="login-card">
                <h2>用户登录</h2>
                <p class="subtle">请扫描工牌或输入账号登录系统</p>
                <div class="scan-orbit">
                    <div>扫码</div>
                </div>
                <label>工号<input id="loginWorkNo" value="A001" autocomplete="username"></label>
                <label>密码<input id="loginPassword" type="password" value="123456" autocomplete="current-password"></label>
                <button class="primary login-submit" id="loginBtn">登录</button>
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
    if (state.page === "washWork") content.innerHTML = renderWashWork();
    if (state.page === "assembleWork") content.innerHTML = renderAssembleWork();
    if (state.page === "print") content.innerHTML = renderPrint();
    if (state.page === "sterilizeWork") content.innerHTML = renderSterilizeWork();
    if (state.page === "bioWork") content.innerHTML = renderBioWork();
    if (state.page === "distributeWork") content.innerHTML = renderDistributeWork();
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
    const colors = ["green", "blue", "purple", "amber", "gray", "red", "blue"];
    const stageCards = (data.stations || []).map((row, index) => `
        <div class="metric metric-${colors[index % colors.length]}">
            <span>${safe(row.station)}工作量</span>
            <b>${safe(row.count)}</b>
            <small>今日实时</small>
        </div>
    `).join("");
    return `
        <section class="dashboard-hero">
            <div>
                <p class="eyebrow">CSSD 数据看板</p>
                <h2>全流程闭环追溯</h2>
                <p class="subtle">回收、清洗、配包、打包、灭菌、发放与 PDA 离线同步统一留痕。</p>
            </div>
            <div class="hero-status">
                <span class="dot ok"></span>
                <strong>内网服务正常</strong>
                <small>${new Date().toLocaleDateString()}</small>
            </div>
        </section>
        <div class="grid metric-board">${stageCards}</div>
        <div class="grid dashboard-main">
            <section class="panel">
                <div class="panel-head"><h2>各环节工作量趋势</h2><span class="tag blue">近 7 天</span></div>
                <div class="panel-body">
                    <div class="trend-lines">
                        ${(data.stations || []).map(row => `
                            <div class="trend-row">
                                <span>${stageLabel(row.station)}</span>
                                <div><i style="width:${Math.min(100, Number(row.count || 0) * 14 + 18)}%"></i></div>
                                <b>${safe(row.count)}</b>
                            </div>
                        `).join("") || document.getElementById("emptyTpl").innerHTML}
                    </div>
                </div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>设备运行状态</h2><span class="tag green">状态可视</span></div>
                <div class="panel-body">${table(data.equipment || [], [
                    ["equipment_code", "设备编号"],
                    ["equipment_name", "设备名称"],
                    ["status", "状态", v => equipmentStatus(v)]
                ])}</div>
            </section>
        </div>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>待办事项</h2><span class="tag red">需处理</span></div>
                <div class="panel-body">${table(data.recentEvents || [], [
                    ["package_code", "包编码"],
                    ["event_type", "事件"],
                    ["station", "来源工位"],
                    ["device_code", "设备"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>近效期提醒</h2><span class="tag amber">30 天内</span></div>
                <div class="panel-body">${table(data.nearExpiry || [], [
                    ["instance_code", "包编码"],
                    ["package_name", "包名称"],
                    ["dept_name", "所在科室"],
                    ["sterilization_expire_at", "效期"]
                ])}</div>
            </section>
        </div>
    `;
}

// 将工位编码转换为看板中文名称，保持统计图和导航文案一致。
function stageLabel(value) {
    return {
        recycle: "回收",
        wash: "清洗",
        assemble: "配包",
        pack: "打包",
        sterilize: "灭菌",
        distribute: "发放",
        bio: "生物监测"
    }[value] || value;
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

// 渲染清洗工作区：临床批量包按批次进入清洗设备，并在完成后推送到配包区。
function renderWashWork() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>批次开始清洗</h2><span class="tag amber">待清洗批次来自回收工作区</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>待清洗批次<select id="washLot">${lotOptionsByStatus(["RECYCLED"])}</select></label>
                    <label>清洗设备<select id="washEquipment">${equipmentOptions()}</select></label>
                    <label>清洗程序<input id="washProgram" value="标准"></label>
                    <button class="primary" id="washLotStartBtn">开始清洗</button>
                </div>
            </div>
        </section>
        <section class="panel">
            <div class="panel-head"><h2>批次完成清洗</h2><span class="tag green">合格后进入配包区</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>清洗批次<select id="washBatch">${washBatchOptions()}</select></label>
                    <label>判定结果<select id="washPass"><option value="true">合格</option><option value="false">不合格退回</option></select></label>
                    <label>备注<input id="washRemark" value="清洗合格"></label>
                    <button class="primary" id="washLotFinishBtn">完成清洗</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>清洗批次</h2></div>
                <div class="panel-body">${table(data.lots || [], [
                    ["lot_no", "批次号"],
                    ["package_name", "包名称"],
                    ["dept_name", "来源科室"],
                    ["current_status", "状态", v => statusTag(v)],
                    ["basket_code", "筐"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>清洗记录</h2></div>
                <div class="panel-body">${table(data.records || [], [
                    ["batch_no", "清洗批号"],
                    ["equipment_name", "设备"],
                    ["program_name", "程序"],
                    ["package_list", "批次清单"],
                    ["result", "结果", v => v === null || v === undefined ? tag("进行中", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")]
                ])}</div>
            </section>
        </div>
    `;
}

// 渲染配包工作区：清洗合格批次完成配包后进入打包/打印区。
function renderAssembleWork() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>批次配包</h2><span class="tag green">配包人会写入后续标签</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>待配包批次<select id="assembleLot">${lotOptionsByStatus(["WASHED"])}</select></label>
                    <label>配包人<select id="assemblerUser">${userOptions()}</select></label>
                    <button class="primary" id="assembleLotBtn">完成配包</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>待配包/已配包批次</h2></div>
                <div class="panel-body">${table(data.lots || [], [
                    ["lot_no", "批次号"],
                    ["package_name", "包名称"],
                    ["dept_name", "来源科室"],
                    ["current_status", "状态", v => statusTag(v)],
                    ["remaining_qty", "数量"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>配包留痕</h2></div>
                <div class="panel-body">${table(data.events || [], [
                    ["lot_no", "批次号"],
                    ["event_type", "事件"],
                    ["operator_name", "配包人"],
                    ["occurred_at", "时间"],
                    ["device_code", "设备"]
                ])}</div>
            </section>
        </div>
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

// 渲染灭菌工作区：打包后的标签进入灭菌批次，完成后流转到发放区。
function renderSterilizeWork() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>标签开始灭菌</h2><span class="tag amber">选择已打印标签</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>标签号<input id="sterilizeLabels" value="${defaultLabelNos(["PRINTED", "PACKED"])}"></label>
                    <label>灭菌设备<select id="sterilizeEquipment">${sterilizeEquipmentOptions()}</select></label>
                    <label>灭菌程序<input id="sterilizeProgram" value="标准"></label>
                    <label>生物监测<select id="needBio"><option value="false">不需要</option><option value="true">需要</option></select></label>
                    <button class="primary" id="sterilizeStartBtn">开始灭菌</button>
                </div>
            </div>
        </section>
        <section class="panel">
            <div class="panel-head"><h2>标签完成灭菌</h2><span class="tag green">合格后进入发放区</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>灭菌批次<select id="sterilizeBatch">${sterilizeBatchOptions()}</select></label>
                    <label>物理监测<select id="physicalPass"><option value="true">合格</option><option value="false">不合格</option></select></label>
                    <label>化学监测<select id="chemicalPass"><option value="true">合格</option><option value="false">不合格</option></select></label>
                    <button class="primary" id="sterilizeFinishBtn">完成灭菌</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>灭菌标签</h2></div>
                <div class="panel-body">${table(data.labels || [], [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["lot_no", "来源批次"],
                    ["status", "状态", v => statusTag(v)]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>灭菌记录</h2></div>
                <div class="panel-body">${table(data.records || [], [
                    ["batch_no", "灭菌批次"],
                    ["equipment_name", "设备"],
                    ["program_name", "程序"],
                    ["package_list", "标签清单"],
                    ["need_bio_test", "生物监测", v => Number(v) === 1 ? tag("需要", "amber") : tag("不需要", "green")],
                    ["bio_test_status", "生物结果", v => Number(v) === 0 ? tag("待录入", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                    ["result", "结果", v => v === null || v === undefined || Number(v) === 0 ? tag("进行中", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")]
                ])}</div>
            </section>
        </div>
    `;
}

// 渲染生物监测工作区：对需要生物监测的灭菌批次录入结果，合格后标签才进入发放区。
function renderBioWork() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>生物监测录入</h2><span class="tag amber">控制灭菌批次放行</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>灭菌批次<select id="bioBatch">${bioBatchOptions()}</select></label>
                    <label>监测结果<select id="bioPass"><option value="true">合格</option><option value="false">阳性/不合格</option></select></label>
                    <label>指示剂批号<input id="indicatorBatch" value="BIO-${Date.now().toString().slice(-6)}"></label>
                    <button class="primary" id="bioTestBtn">录入结果</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>待监测标签</h2></div>
                <div class="panel-body">${table(data.labels || [], [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["lot_no", "来源批次"],
                    ["status", "状态", v => statusTag(v)]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>灭菌批次</h2></div>
                <div class="panel-body">${table(data.records || [], [
                    ["batch_no", "灭菌批次"],
                    ["equipment_name", "设备"],
                    ["package_list", "标签清单"],
                    ["bio_test_status", "生物结果", v => Number(v) === 0 ? tag("待录入", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                    ["remark", "备注"]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>监测流水</h2></div>
            <div class="panel-body">${table(data.tests || [], [
                ["sterilization_batch_no", "灭菌批次"],
                ["indicator_batch", "指示剂批号"],
                ["result", "结果", v => Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                ["operator_name", "录入人"],
                ["input_time", "录入时间"]
            ])}</div>
        </section>
    `;
}

// 渲染发放工作区：灭菌合格标签按科室生成发放单。
function renderDistributeWork() {
    const data = state.workArea || {};
    return `
        <section class="panel">
            <div class="panel-head"><h2>标签发放</h2><span class="tag green">只能发放灭菌合格标签</span></div>
            <div class="panel-body">
                <div class="form-row">
                    <label>标签号<input id="distributeLabels" value="${defaultLabelNos(["STERILIZED"])}"></label>
                    <label>目标科室<select id="distributeDept">${distributeDeptOptions()}</select></label>
                    <button class="primary" id="distributeBtn">发放</button>
                </div>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>发放标签</h2></div>
                <div class="panel-body">${table(data.labels || [], [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["lot_no", "来源批次"],
                    ["expire_date", "失效时间"],
                    ["status", "状态", v => statusTag(v)]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>发放单</h2></div>
                <div class="panel-body">${table(data.orders || [], [
                    ["order_no", "发放单"],
                    ["dept_name", "科室"],
                    ["package_list", "标签清单"],
                    ["operator_name", "发放人"],
                    ["distribute_time", "时间"]
                ])}</div>
            </section>
        </div>
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
    const washLotStartBtn = document.getElementById("washLotStartBtn");
    if (washLotStartBtn) washLotStartBtn.addEventListener("click", washLotStart);
    const washLotFinishBtn = document.getElementById("washLotFinishBtn");
    if (washLotFinishBtn) washLotFinishBtn.addEventListener("click", washLotFinish);
    const assembleLotBtn = document.getElementById("assembleLotBtn");
    if (assembleLotBtn) assembleLotBtn.addEventListener("click", assembleLot);
    const printLabelBtn = document.getElementById("printLabelBtn");
    if (printLabelBtn) printLabelBtn.addEventListener("click", printLabel);
    const sterilizeStartBtn = document.getElementById("sterilizeStartBtn");
    if (sterilizeStartBtn) sterilizeStartBtn.addEventListener("click", sterilizeStart);
    const sterilizeFinishBtn = document.getElementById("sterilizeFinishBtn");
    if (sterilizeFinishBtn) sterilizeFinishBtn.addEventListener("click", sterilizeFinish);
    const bioTestBtn = document.getElementById("bioTestBtn");
    if (bioTestBtn) bioTestBtn.addEventListener("click", bioTestLabels);
    const distributeBtn = document.getElementById("distributeBtn");
    if (distributeBtn) distributeBtn.addEventListener("click", distributeLabels);
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

// 开始清洗临床批次：调用后端批次清洗接口并刷新清洗工作区。
async function washLotStart() {
    const data = await api("workflow/lot/wash/start", {
        method: "POST",
        body: {
            lotNo: value("washLot"),
            equipmentCode: value("washEquipment"),
            program: value("washProgram") || "标准",
            operatorId: "user-operator",
            deviceCode: "WEB-WASH",
            clientType: "WEB"
        }
    });
    showNotice(`批次已开始清洗：${data.batchNo}`);
    state.workArea = await api("workarea/wash");
    render();
}

// 完成清洗临床批次：合格进入配包区，不合格退回待清洗。
async function washLotFinish() {
    const data = await api("workflow/lot/wash/finish", {
        method: "POST",
        body: {
            batchNo: value("washBatch"),
            pass: value("washPass") === "true",
            remark: value("washRemark"),
            operatorId: "user-operator",
            deviceCode: "WEB-WASH",
            clientType: "WEB"
        }
    });
    showNotice(`清洗已完成：${data.status}`);
    state.workArea = await api("workarea/wash");
    render();
}

// 完成配包临床批次：配包人会被后续标签打印自动继承。
async function assembleLot() {
    const data = await api("workflow/lot/assemble", {
        method: "POST",
        body: {
            lotNo: value("assembleLot"),
            assemblerId: value("assemblerUser"),
            operatorId: value("assemblerUser"),
            deviceCode: "WEB-ASSEMBLE",
            clientType: "WEB"
        }
    });
    showNotice(`配包完成：${data.assemblerName}`);
    state.workArea = await api("workarea/assemble");
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

// 开始标签灭菌：将已打印标签放入灭菌批次。
async function sterilizeStart() {
    const data = await api("workflow/label/sterilize/start", {
        method: "POST",
        body: {
            labelNos: value("sterilizeLabels"),
            equipmentCode: value("sterilizeEquipment"),
            program: value("sterilizeProgram") || "标准",
            needBio: value("needBio") === "true",
            operatorId: "user-operator",
            deviceCode: "WEB-STERILIZE",
            clientType: "WEB"
        }
    });
    showNotice(`灭菌已开始：${data.batchNo}`);
    state.workArea = await api("workarea/sterilize");
    render();
}

// 完成标签灭菌：物理和化学均合格后进入待发放。
async function sterilizeFinish() {
    const data = await api("workflow/label/sterilize/finish", {
        method: "POST",
        body: {
            batchNo: value("sterilizeBatch"),
            physicalPass: value("physicalPass") === "true",
            chemicalPass: value("chemicalPass") === "true",
            operatorId: "user-operator",
            deviceCode: "WEB-STERILIZE",
            clientType: "WEB"
        }
    });
    showNotice(`灭菌完成：${data.status}`);
    state.workArea = await api("workarea/sterilize");
    render();
}

// 录入标签灭菌批次的生物监测结果；合格进入待发放，阳性则锁定召回。
async function bioTestLabels() {
    const data = await api("workflow/label/bio-test", {
        method: "POST",
        body: {
            batchNo: value("bioBatch"),
            pass: value("bioPass") === "true",
            indicatorBatch: value("indicatorBatch"),
            operatorId: "user-operator",
            deviceCode: "WEB-BIO",
            clientType: "WEB"
        }
    });
    showNotice(`生物监测已录入：${data.status}`);
    state.workArea = await api("workarea/bio");
    render();
}

// 发放灭菌合格标签：生成发放单并回写批次追溯事件。
async function distributeLabels() {
    const data = await api("workflow/label/distribute", {
        method: "POST",
        body: {
            labelNos: value("distributeLabels"),
            deptCode: value("distributeDept"),
            operatorId: "user-operator",
            deviceCode: "WEB-DISTRIBUTE",
            clientType: "WEB"
        }
    });
    showNotice(`发放完成：${data.orderNo}`);
    state.workArea = await api("workarea/distribute");
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

// 按批次状态生成下拉选项，清洗、配包、打包各自只显示可操作数据。
function lotOptionsByStatus(statuses) {
    const rows = (state.workArea && state.workArea.lots) || [];
    return rows
        .filter(row => statuses.includes(row.current_status))
        .map(row => `<option value="${safe(row.lot_no)}">${safe(row.lot_no)} - ${safe(row.package_name)} - ${safe(statusLabels[row.current_status]?.[0] || row.current_status)}</option>`)
        .join("");
}

// 生成清洗设备下拉选项，清洗区只列出清洗机。
function equipmentOptions() {
    const rows = (state.workArea && state.workArea.equipments) || [];
    return rows
        .map(row => `<option value="${safe(row.equipment_code)}">${safe(row.equipment_name)}（${safe(row.equipment_code)}）</option>`)
        .join("");
}

// 生成正在清洗的清洗批号下拉选项，用于完成清洗判定。
function washBatchOptions() {
    const rows = (state.workArea && state.workArea.records) || [];
    return rows
        .filter(row => row.result === null || row.result === undefined)
        .map(row => `<option value="${safe(row.batch_no)}">${safe(row.batch_no)} - ${safe(row.equipment_name)}</option>`)
        .join("");
}

// 生成人员下拉选项，配包完成时需要记录配包人。
function userOptions() {
    const rows = (state.basic && state.basic.users) || [];
    return rows
        .map(row => `<option value="${safe(row.id || row.work_no)}">${safe(row.user_name)}（${safe(row.work_no)}）</option>`)
        .join("");
}

// 生成可打印批次下拉选项，只显示仍有剩余数量的批次。
function lotOptions() {
    const rows = (state.workArea && state.workArea.lots) || [];
    return rows
        .filter(row => Number(row.remaining_qty) > 0 && ["ASSEMBLED", "PART_PACKED"].includes(row.current_status))
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

// 生成灭菌设备下拉选项。
function sterilizeEquipmentOptions() {
    const rows = (state.workArea && state.workArea.equipments) || [];
    return rows
        .map(row => `<option value="${safe(row.equipment_code)}">${safe(row.equipment_name)}（${safe(row.equipment_code)}）</option>`)
        .join("");
}

// 生成正在灭菌的批次下拉选项，用于完成灭菌。
function sterilizeBatchOptions() {
    const rows = (state.workArea && state.workArea.records) || [];
    return rows
        .filter(row => row.result === null || row.result === undefined)
        .map(row => `<option value="${safe(row.batch_no)}">${safe(row.batch_no)} - ${safe(row.equipment_name)}</option>`)
        .join("");
}

// 生成待生物监测批次下拉项，优先显示尚未录入结果的灭菌批次。
function bioBatchOptions() {
    const rows = (state.workArea && state.workArea.records) || [];
    const pendingRows = rows.filter(row => Number(row.bio_test_status) === 0);
    const visibleRows = pendingRows.length ? pendingRows : rows;
    return visibleRows
        .map(row => `<option value="${safe(row.batch_no)}">${safe(row.batch_no)} - ${safe(row.equipment_name)}</option>`)
        .join("");
}

// 生成发放目标科室下拉选项。
function distributeDeptOptions() {
    const rows = (state.workArea && state.workArea.departments) || [];
    return rows
        .map(row => `<option value="${safe(row.dept_code)}">${safe(row.dept_name)}</option>`)
        .join("");
}

// 根据状态生成默认标签号文本，多个标签用逗号分隔。
function defaultLabelNos(statuses) {
    const rows = (state.workArea && state.workArea.labels) || [];
    return rows
        .filter(row => statuses.includes(row.status))
        .slice(0, 5)
        .map(row => row.label_no)
        .join(",");
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
