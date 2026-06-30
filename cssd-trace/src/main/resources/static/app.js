const apiBase = "api";

const state = {
    page: "dashboard",
    authed: Boolean(localStorage.getItem("cssdAdminToken")),
    user: JSON.parse(localStorage.getItem("cssdAdminUser") || "null"),
    dashboard: null,
    basic: null,
    workArea: null,
    filters: {}
};

const pageTitles = {
    dashboard: "数据总览",
    basic: "基础信息",
    packageManage: "器械包管理",
    packagingManage: "包装管理",
    instrumentManage: "器械管理",
    equipmentManage: "设备管理",
    recycleWork: "回收单管理",
    washWork: "清洗记录管理",
    assembleWork: "配包记录管理",
    print: "打印模板",
    sterilizeWork: "灭菌记录管理",
    bioWork: "生物监测记录管理",
    distributeWork: "发放单管理",
    departmentApply: "器械包申领（借包）",
    config: "参数配置",
    roleManage: "角色管理",
    userManage: "人员管理",
    departmentManage: "科室管理",
    menuManage: "菜单管理",
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

const yesNoStatusOptions = [
    ["1", "启用"],
    ["0", "停用"]
];

const adminEntityConfigs = {
    packageTypes: {
        title: "器械包",
        entity: "packageTypes",
        rowsKey: "packageTypes",
        columns: [
            ["package_code", "包编码"],
            ["package_name", "包名称"],
            ["category", "分类"],
            ["tracking_mode", "追溯模式", v => trackingModeTag(v)],
            ["package_scope", "业务范围"],
            ["validity_days", "有效期"],
            ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
        ],
        fields: [
            ["package_code", "包编码", "text", "PKT-" + Date.now().toString().slice(-5)],
            ["package_name", "包名称", "text", "新增器械包"],
            ["category", "分类", "text", "临床包"],
            ["tracking_mode", "追溯模式", "select", "BATCH", [["BATCH", "临床批量"], ["UNIQUE", "一包一码"]]],
            ["package_scope", "业务范围", "select", "CLINICAL", [["CLINICAL", "临床科室"], ["OR", "手术室"]]],
            ["sterilization_type", "灭菌方式", "select", "1", [["1", "高温"], ["2", "低温"], ["3", "无需灭菌"]]],
            ["cleaning_type", "清洗方式", "select", "2", [["1", "手工"], ["2", "机洗"], ["3", "特殊感染"]]],
            ["validity_days", "有效期天数", "number", "30"],
            ["packaging_id", "包材ID", "text", "pack-paper"],
            ["packaging_name", "包材名称", "text", "纸塑袋"],
            ["instrument_list", "包内物JSON", "textarea", "[{\"code\":\"INS-001\",\"name\":\"持针器\",\"qty\":1}]"],
            ["assemble_image", "配包图谱", "text", ""],
            ["pack_image", "打包图谱", "text", ""],
            ["status", "状态", "select", "1", yesNoStatusOptions]
        ]
    },
    packaging: {
        title: "包材",
        entity: "packaging",
        rowsKey: "packaging",
        columns: [
            ["packaging_code", "包材编码"],
            ["packaging_name", "包材名称"],
            ["validity_days", "有效期天数"],
            ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
        ],
        fields: [
            ["packaging_code", "包材编码", "text", "PACK-" + Date.now().toString().slice(-5)],
            ["packaging_name", "包材名称", "text", "新增包材"],
            ["validity_days", "有效期天数", "number", "60"],
            ["status", "状态", "select", "1", yesNoStatusOptions]
        ]
    },
    instruments: {
        title: "器械",
        entity: "instruments",
        rowsKey: "instruments",
        columns: [
            ["instrument_code", "器械编码"],
            ["instrument_name", "器械名称"],
            ["spec", "规格"],
            ["category", "分类"],
            ["unit", "单位"]
        ],
        fields: [
            ["instrument_code", "器械编码", "text", "INS-" + Date.now().toString().slice(-5)],
            ["instrument_name", "器械名称", "text", "新增器械"],
            ["spec", "规格", "text", ""],
            ["category", "分类", "text", "基础器械"],
            ["unit", "单位", "text", "把"]
        ]
    },
    equipments: {
        title: "设备",
        entity: "equipments",
        rowsKey: "equipments",
        columns: [
            ["equipment_code", "设备编号"],
            ["equipment_name", "设备名称"],
            ["equipment_type", "类型", v => equipmentTypeText(v)],
            ["location", "位置"],
            ["status", "状态", v => equipmentStatus(v)]
        ],
        fields: [
            ["equipment_code", "设备编号", "text", "EQ-" + Date.now().toString().slice(-5)],
            ["equipment_name", "设备名称", "text", "新增设备"],
            ["equipment_type", "设备类型", "select", "1", [["1", "清洗设备"], ["2", "高温灭菌"], ["3", "低温灭菌"]]],
            ["model", "型号", "text", ""],
            ["location", "位置", "text", "CSSD"],
            ["status", "状态", "select", "1", [["1", "空闲"], ["2", "运行中"], ["3", "故障"], ["0", "停用"]]],
            ["preset_programs", "预设程序", "text", "标准"],
            ["default_bio_test", "默认生物监测", "select", "0", [["0", "不需要"], ["1", "需要"]]]
        ]
    },
    baskets: {
        title: "筐",
        entity: "baskets",
        rowsKey: "baskets",
        columns: [
            ["basket_code", "筐编码"],
            ["status", "状态"],
            ["current_batch_no", "当前批次"],
            ["package_list", "绑定内容"]
        ],
        fields: [
            ["basket_code", "筐编码", "text", "BASK-" + Date.now().toString().slice(-4)],
            ["status", "状态", "select", "IDLE", [["IDLE", "空闲"], ["BOUND", "已绑定"], ["CLEANING", "清洗中"], ["DISABLED", "停用"]]],
            ["current_batch_no", "当前批次", "text", ""],
            ["package_list", "绑定内容JSON", "textarea", "[]"]
        ]
    },
    departments: {
        title: "科室",
        entity: "departments",
        rowsKey: "departments",
        columns: [
            ["dept_code", "科室编码"],
            ["dept_name", "科室名称"],
            ["dept_type", "类型"],
            ["barcode", "条码"],
            ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
        ],
        fields: [
            ["dept_code", "科室编码", "text", "DEPT-" + Date.now().toString().slice(-4)],
            ["dept_name", "科室名称", "text", "新增科室"],
            ["dept_type", "类型", "select", "clinical", [["clinical", "临床科室"], ["or", "手术室"], ["cssd", "消毒供应中心"]]],
            ["barcode", "条码", "text", "DEPT-" + Date.now().toString().slice(-4)],
            ["status", "状态", "select", "1", yesNoStatusOptions]
        ]
    },
    users: {
        title: "人员",
        entity: "users",
        rowsKey: "users",
        columns: [
            ["work_no", "工号"],
            ["user_name", "姓名"],
            ["dept_id", "所属科室", v => deptNameById(v)],
            ["role_code", "角色"],
            ["user_type", "人员类型"],
            ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
        ],
        fields: [
            ["work_no", "工号", "text", "U" + Date.now().toString().slice(-5)],
            ["user_name", "姓名", "text", "新增人员"],
            ["password_hash", "密码", "text", "123456"],
            ["dept_id", "所属科室", "select", () => firstDeptId(), () => departmentOptions()],
            ["role_code", "角色", "select", "operator", [["admin", "管理员"], ["operator", "操作员"], ["quality", "质控员"], ["nurse", "科室护士"]]],
            ["user_type", "人员类型", "select", "staff", [["staff", "院内人员"], ["external", "外部人员"]]],
            ["status", "状态", "select", "1", yesNoStatusOptions],
            ["login_method", "登录方式", "select", "card", [["card", "工牌/扫码"], ["password", "账号密码"]]]
        ]
    },
    roles: {
        title: "角色",
        entity: "roles",
        rowsKey: "roles",
        columns: [
            ["role_code", "角色编码"],
            ["role_name", "角色名称"],
            ["remark", "说明"],
            ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
        ],
        fields: [
            ["role_code", "角色编码", "text", "ROLE-" + Date.now().toString().slice(-4)],
            ["role_name", "角色名称", "text", "新增角色"],
            ["remark", "说明", "textarea", ""],
            ["status", "状态", "select", "1", yesNoStatusOptions]
        ]
    },
    permissions: {
        title: "权限菜单",
        entity: "permissions",
        rowsKey: "permissions",
        columns: [
            ["permission_code", "权限编码"],
            ["permission_name", "权限名称"],
            ["module_code", "模块"],
            ["permission_type", "类型"],
            ["sort_no", "排序"],
            ["status", "状态", v => Number(v) === 1 ? tag("启用", "green") : tag("停用", "red")]
        ],
        fields: [
            ["permission_code", "权限编码", "text", "perm:" + Date.now().toString().slice(-4)],
            ["permission_name", "权限名称", "text", "新增权限"],
            ["module_code", "模块", "text", "system"],
            ["permission_type", "类型", "select", "MENU", [["MENU", "菜单"], ["ACTION", "操作"]]],
            ["sort_no", "排序", "number", "100"],
            ["status", "状态", "select", "1", yesNoStatusOptions]
        ]
    }
};

const documentEditConfigs = {
    recycleOrders: {
        title: "回收单",
        fields: [
            ["source_user", "来源/送包人", "text", ""],
            ["total_count", "回收数量", "number", "0"],
            ["basket_code", "筐号", "text", ""],
            ["abnormal_record", "异常记录JSON", "textarea", ""],
            ["remark", "备注", "textarea", ""],
            ["video_url", "视频留痕地址", "text", ""]
        ]
    },
    washRecords: {
        title: "清洗记录",
        fields: [
            ["program_name", "清洗程序", "text", ""],
            ["remark", "备注", "textarea", ""]
        ]
    },
    sterilizationRecords: {
        title: "灭菌记录",
        fields: [
            ["program_name", "灭菌程序", "text", ""],
            ["remark", "备注", "textarea", ""]
        ]
    },
    distributeOrders: {
        title: "发放单",
        fields: [
            ["status", "状态", "select", "2", [["1", "待处理"], ["2", "已完成"], ["0", "作废"]]]
        ]
    }
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
    if (state.page === "dashboard" || state.page === "report" || state.page === "equipmentManage") {
        state.dashboard = await api("dashboard");
    }
    if (["basic", "config", "packageManage", "packagingManage", "instrumentManage", "equipmentManage", "roleManage", "userManage", "departmentManage", "menuManage", "departmentApply"].includes(state.page)) {
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
    document.body.classList.add("is-login");
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
    document.body.classList.remove("is-login");
    document.getElementById("pageTitle").textContent = pageTitles[state.page];
    const content = document.getElementById("content");
    if (state.page === "dashboard") content.innerHTML = renderDashboard();
    if (state.page === "basic") content.innerHTML = renderBasic();
    if (state.page === "packageManage") content.innerHTML = renderPackageManage();
    if (state.page === "packagingManage") content.innerHTML = renderPackagingManage();
    if (state.page === "instrumentManage") content.innerHTML = renderInstrumentManage();
    if (state.page === "equipmentManage") content.innerHTML = renderEquipmentManage();
    if (state.page === "recycleWork") content.innerHTML = renderRecycleWork();
    if (state.page === "washWork") content.innerHTML = renderWashWork();
    if (state.page === "assembleWork") content.innerHTML = renderAssembleWork();
    if (state.page === "print") content.innerHTML = renderPrint();
    if (state.page === "sterilizeWork") content.innerHTML = renderSterilizeWork();
    if (state.page === "bioWork") content.innerHTML = renderBioWork();
    if (state.page === "distributeWork") content.innerHTML = renderDistributeWork();
    if (state.page === "departmentApply") content.innerHTML = renderDepartmentApply();
    if (state.page === "config") content.innerHTML = renderConfig();
    if (state.page === "roleManage") content.innerHTML = renderRoleManage();
    if (state.page === "userManage") content.innerHTML = renderUserManage();
    if (state.page === "departmentManage") content.innerHTML = renderDepartmentManage();
    if (state.page === "menuManage") content.innerHTML = renderMenuManage();
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
                <p class="subtle">Web 端只做数据查询、单据查看、单据编辑和追溯留痕；现场业务由触摸端和 PDA 执行。</p>
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

function renderCrudSection(entityKey, rows, title) {
    const config = adminEntityConfigs[entityKey];
    const visibleRows = filterRows(`crud-${entityKey}`, rows, config.columns.map(col => col[0]));
    return `
        <section class="panel">
            <div class="panel-head">
                <h2>${safe(title || config.title + "管理")}</h2>
                <div class="panel-actions">
                    ${filterInput(`crud-${entityKey}`, "筛选编码/名称/状态")}
                    <button class="primary crud-add" data-entity="${entityKey}">新增</button>
                </div>
            </div>
            <div class="panel-body">${table(visibleRows, [
                ...config.columns,
                ["id", "操作", (v, row) => renderCrudActions(entityKey, row)]
            ])}</div>
        </section>
    `;
}

function renderCrudActions(entityKey, row) {
    const encoded = encodeRow(row);
    return `
        <div class="table-actions">
            <button class="ghost crud-edit" data-entity="${entityKey}" data-row="${encoded}">编辑</button>
            <button class="ghost danger crud-delete" data-entity="${entityKey}" data-id="${safe(row.id)}">停用</button>
        </div>
    `;
}

function filterInput(key, placeholder) {
    return `<input class="compact-input data-filter" data-filter="${safe(key)}" value="${safe(state.filters[key] || "")}" placeholder="${safe(placeholder)}">`;
}

function filterRows(key, rows, fields) {
    const text = (state.filters[key] || "").trim().toLowerCase();
    if (!text) return rows || [];
    return (rows || []).filter(row => fields.some(field => String(row[field] ?? "").toLowerCase().includes(text)));
}

function encodeRow(row) {
    return safe(btoa(unescape(encodeURIComponent(JSON.stringify(row)))));
}

function decodeRow(value) {
    return JSON.parse(decodeURIComponent(escape(atob(value))));
}

function packageInstrumentRows(packageTypes) {
    return (packageTypes || []).flatMap(pkg => {
        const instruments = normalizeList(pkg.instrument_list);
        return instruments.map(item => ({
            package_code: pkg.package_code,
            package_name: pkg.package_name,
            instrument_code: item.code,
            instrument_name: item.name,
            qty: item.qty
        }));
    });
}

function deptNameById(deptId) {
    const rows = (state.basic && state.basic.departments) || [];
    return rows.find(row => row.id === deptId)?.dept_name || deptId || "";
}

function firstDeptId() {
    return ((state.basic && state.basic.departments) || [])[0]?.id || "";
}

function departmentOptions() {
    return ((state.basic && state.basic.departments) || []).map(row => [row.id, row.dept_name]);
}

function renderBasic() {
    const data = state.basic || {};
    return `
        <div class="grid cols-2">
            ${renderCrudSection("packageTypes", data.packageTypes || [])}
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
            ${renderCrudSection("packaging", data.packaging || [])}
            ${renderCrudSection("baskets", data.baskets || [])}
        </div>
        <div class="grid cols-2">
            ${renderCrudSection("departments", data.departments || [])}
            ${renderCrudSection("users", data.users || [])}
        </div>
        ${renderCrudSection("instruments", data.instruments || [])}
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

function renderPackageManage() {
    const data = state.basic || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">基础信息</p>
                <h2>器械包管理</h2>
                <p class="subtle">维护器械包编码、追溯模式、业务范围和有效期，作为后续回收、清洗、配包和发放的基础数据。</p>
            </div>
            <button class="primary crud-add" data-entity="packageTypes">新增器械包</button>
        </section>
        ${renderCrudSection("packageTypes", data.packageTypes || [], "器械包目录")}
        <section class="panel">
            <div class="panel-head"><h2>器械实例状态</h2><span class="tag green">库存状态</span></div>
            <div class="panel-body">${table(data.packages || [], [
                ["instance_code", "包条码"],
                ["package_name", "包名称"],
                ["current_status", "状态", v => statusTag(v)],
                ["dept_name", "所在科室"],
                ["current_batch_no", "当前批次"]
            ])}</div>
        </section>
    `;
}

function renderPackagingManage() {
    const data = state.basic || {};
    return `
        ${renderCrudSection("packaging", data.packaging || [], "包装管理")}
        ${renderCrudSection("baskets", data.baskets || [], "筐管理")}
    `;
}

function renderInstrumentManage() {
    const data = state.basic || {};
    return `
        ${renderCrudSection("instruments", data.instruments || [], "器械字典")}
        <section class="panel">
            <div class="panel-head"><h2>包内物引用视图</h2><span class="tag blue">来自器械包清单</span></div>
            <div class="panel-body">${table(packageInstrumentRows(data.packageTypes || []), [
                ["instrument_code", "器械编码"],
                ["instrument_name", "器械名称"],
                ["qty", "数量"],
                ["package_name", "所属器械包"],
                ["package_code", "包编码"]
            ])}</div>
        </section>
    `;
}

function normalizeList(value) {
    if (Array.isArray(value)) return value;
    if (!value) return [];
    if (typeof value === "string") {
        try {
            const parsed = JSON.parse(value);
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    }
    return [];
}

function renderEquipmentManage() {
    const data = state.basic || {};
    return `
        ${renderCrudSection("equipments", data.equipments || [], "设备管理")}
    `;
}

function renderDepartmentApply() {
    const data = state.basic || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">科室工作区</p>
                <h2>器械包申领（借包）</h2>
                <p class="subtle">当前版本先展示可申领器械包和科室基础数据，后续可接入正式申领、审批和借包归还流程。</p>
            </div>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>可申领器械包</h2></div>
                <div class="panel-body">${table(data.packages || [], [
                    ["instance_code", "包条码"],
                    ["package_name", "包名称"],
                    ["current_status", "状态", v => statusTag(v)],
                    ["dept_name", "当前科室"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>科室信息</h2></div>
                <div class="panel-body">${table(data.departments || [], [
                    ["dept_code", "科室编码"],
                    ["dept_name", "科室名称"],
                    ["dept_type", "类型"],
                    ["barcode", "条码"]
                ])}</div>
            </section>
        </div>
    `;
}

function renderRoleManage() {
    const data = state.basic || {};
    return `
        ${renderCrudSection("roles", data.roles || [], "角色管理")}
        ${renderRolePermissionMatrix(data)}
    `;
}

function renderUserManage() {
    const data = state.basic || {};
    return `
        ${renderCrudSection("users", data.users || [], "人员管理")}
    `;
}

function renderDepartmentManage() {
    const data = state.basic || {};
    return `
        ${renderCrudSection("departments", data.departments || [], "科室管理")}
    `;
}

function renderMenuManage() {
    const rows = [
        ["数据总览", "数据总览", "dashboard"],
        ["追溯查询", "追溯查询", "trace"],
        ["统计报表", "工作量统计", "report"],
        ["基础信息", "器械包管理 / 包装管理 / 器械管理 / 设备管理", "basic"],
        ["单据管理", "回收单 / 清洗记录 / 配包记录 / 打包标签 / 灭菌记录 / 生物监测记录 / 发放单", "documents"],
        ["系统管理", "角色管理 / 人员管理 / 科室管理 / 菜单管理", "system"]
    ].map(row => ({ group: row[0], menu: row[1], page: row[2] }));
    return `
        ${renderCrudSection("permissions", (state.basic && state.basic.permissions) || [], "权限菜单维护")}
        <section class="panel">
            <div class="panel-head"><h2>菜单管理</h2><span class="tag amber">前端菜单结构</span></div>
            <div class="panel-body">${table(rows, [
                ["group", "一级菜单"],
                ["menu", "二级菜单"],
                ["page", "页面标识"]
            ])}</div>
        </section>
    `;
}

function renderRolePermissionMatrix(data) {
    const permissions = data.permissions || [];
    const grouped = permissions.reduce((map, permission) => {
        const moduleCode = permission.module_code || "other";
        map[moduleCode] = map[moduleCode] || [];
        map[moduleCode].push(permission);
        return map;
    }, {});
    return `
        <section class="panel">
            <div class="panel-head"><h2>角色权限配置</h2><span class="tag blue">勾选后保存</span></div>
            <div class="panel-body role-permission-board">
                ${(data.roles || []).map(role => {
                    const owned = new Set((data.rolePermissions || [])
                        .filter(row => row.role_id === role.id)
                        .map(row => row.permission_id));
                    return `
                        <div class="role-permission-card">
                            <div class="role-permission-head">
                                <strong>${safe(role.role_name)}</strong>
                                <span>${safe(role.role_code)}</span>
                            </div>
                            ${Object.entries(grouped).map(([moduleCode, rows]) => `
                                <div class="permission-group">
                                    <b>${safe(moduleCode)}</b>
                                    <div class="permission-options">
                                        ${rows.map(permission => `
                                            <label class="check-line">
                                                <input type="checkbox" class="role-permission-check" data-role-id="${safe(role.id)}" value="${safe(permission.id)}" ${owned.has(permission.id) ? "checked" : ""}>
                                                <span>${safe(permission.permission_name)}</span>
                                            </label>
                                        `).join("")}
                                    </div>
                                </div>
                            `).join("")}
                            <button class="primary save-role-permissions" data-role-id="${safe(role.id)}">保存权限</button>
                        </div>
                    `;
                }).join("") || document.getElementById("emptyTpl").innerHTML}
            </div>
        </section>
    `;
}

// 渲染回收单管理：Web 端只展示触摸台/PDA 生成的回收单，并允许编辑备注等单据字段。
function renderRecycleWork() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>回收单查看与编辑</h2>
                <p class="subtle">回收业务由触摸端或 PDA 完成，Web 端只查看、修改单据备注、查看明细和追溯批次。</p>
            </div>
            <span class="tag blue">非业务操作入口</span>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>回收单</h2><div class="panel-actions">${filterInput("recycle-orders", "筛选单号/科室/筐")}</div></div>
                <div class="panel-body">${table(filterRows("recycle-orders", data.orders || [], ["order_no", "dept_name", "basket_code", "remark"]), [
                    ["order_no", "单号"],
                    ["dept_name", "科室"],
                    ["total_count", "数量"],
                    ["basket_code", "筐"],
                    ["recycle_time", "回收时间"],
                    ["remark", "备注"],
                    ["id", "操作", (v, row) => `<button class="ghost edit-document" data-type="recycleOrders" data-id="${safe(v)}" data-row="${encodeRow(row)}">编辑单据</button>`]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>回收明细</h2><div class="panel-actions">${filterInput("recycle-items", "筛选包名/批次")}</div></div>
                <div class="panel-body">${table(filterRows("recycle-items", data.items || [], ["order_no", "package_name", "lot_no", "package_code"]), [
                    ["order_no", "单号"],
                    ["package_name", "包名称"],
                    ["tracking_mode", "追溯模式", v => trackingModeTag(v)],
                    ["lot_no", "批次号"],
                    ["quantity", "数量"]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>待流转批次</h2><div class="panel-actions">${filterInput("recycle-lots", "筛选批次/包名/科室")}</div></div>
            <div class="panel-body">${table(filterRows("recycle-lots", data.lots || [], ["lot_no", "package_name", "dept_name", "basket_code"]), [
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

// 渲染清洗记录管理：Web 端只读取清洗批次和清洗记录，不触发开始/完成清洗。
function renderWashWork() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>清洗记录查看与编辑</h2>
                <p class="subtle">清洗开始、完成和判定在触摸台执行；Web 端展示清洗批次、设备、程序和结果。</p>
            </div>
            <span class="tag blue">查询 / 编辑</span>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>清洗批次</h2><div class="panel-actions">${filterInput("wash-lots", "筛选批次/包名/科室")}</div></div>
                <div class="panel-body">${table(filterRows("wash-lots", data.lots || [], ["lot_no", "package_name", "dept_name", "basket_code"]), [
                    ["lot_no", "批次号"],
                    ["package_name", "包名称"],
                    ["dept_name", "来源科室"],
                    ["current_status", "状态", v => statusTag(v)],
                    ["basket_code", "筐"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>清洗记录</h2><div class="panel-actions">${filterInput("wash-records", "筛选批号/设备/程序")}</div></div>
                <div class="panel-body">${table(filterRows("wash-records", data.records || [], ["batch_no", "equipment_name", "program_name", "package_list", "remark"]), [
                    ["batch_no", "清洗批号"],
                    ["equipment_name", "设备"],
                    ["program_name", "程序"],
                    ["package_list", "批次清单"],
                    ["result", "结果", v => v === null || v === undefined ? tag("进行中", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                    ["remark", "备注"],
                    ["id", "操作", (v, row) => `<button class="ghost edit-document" data-type="washRecords" data-id="${safe(v)}" data-row="${encodeRow(row)}">编辑单据</button>`]
                ])}</div>
            </section>
        </div>
    `;
}

// 渲染配包记录管理：配包完成由触摸端写入，Web 端只查看批次状态和配包留痕。
function renderAssembleWork() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>配包记录查看</h2>
                <p class="subtle">配包人、配包时间和设备来自触摸端留痕，Web 端只做核对和查询。</p>
            </div>
            <span class="tag blue">只读留痕</span>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>待配包/已配包批次</h2><div class="panel-actions">${filterInput("assemble-lots", "筛选批次/包名/科室")}</div></div>
                <div class="panel-body">${table(filterRows("assemble-lots", data.lots || [], ["lot_no", "package_name", "dept_name"]), [
                    ["lot_no", "批次号"],
                    ["package_name", "包名称"],
                    ["dept_name", "来源科室"],
                    ["current_status", "状态", v => statusTag(v)],
                    ["remaining_qty", "数量"]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>配包留痕</h2><div class="panel-actions">${filterInput("assemble-events", "筛选批次/人员/设备")}</div></div>
                <div class="panel-body">${table(filterRows("assemble-events", data.events || [], ["lot_no", "event_type", "operator_name", "device_code"]), [
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

// 渲染打包标签管理：标签由打包触摸台生成，Web 端只查看标签和维护打印模板。
function renderPrint() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>打包标签与打印模板</h2>
                <p class="subtle">实际打包和标签打印在打包触摸台执行；Web 端展示打印结果，并维护模板字段属性。</p>
            </div>
            <span class="tag blue">模板维护</span>
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
                <div class="panel-head"><h2>最近标签</h2><div class="panel-actions">${filterInput("pack-labels", "筛选标签/包名/人员")}</div></div>
                <div class="panel-body">${table(filterRows("pack-labels", data.labels || [], ["label_no", "package_name", "assembler_name", "packer_name"]), [
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
            <div class="panel-head"><h2>待打包批次</h2><div class="panel-actions">${filterInput("pack-lots", "筛选批次/包名")}<span class="tag amber">仅展示</span></div></div>
            <div class="panel-body">${table(filterRows("pack-lots", data.lots || [], ["lot_no", "package_name", "current_status"]), [
                ["lot_no", "批次号"],
                ["package_name", "包名称"],
                ["remaining_qty", "未打印数量"],
                ["current_status", "状态", v => statusTag(v)]
            ])}</div>
        </section>
    `;
}

// 渲染灭菌记录管理：灭菌开始/完成由触摸端执行，Web 端只查询记录和编辑备注。
function renderSterilizeWork() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>灭菌记录查看与编辑</h2>
                <p class="subtle">灭菌装载、完成和监测判定在灭菌触摸台执行；Web 端展示锅次、标签、监测结果和备注。</p>
            </div>
            <span class="tag blue">查询 / 编辑</span>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>灭菌标签</h2><div class="panel-actions">${filterInput("sterilize-labels", "筛选标签/包名/批次")}</div></div>
                <div class="panel-body">${table(filterRows("sterilize-labels", data.labels || [], ["label_no", "package_name", "lot_no", "status"]), [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["lot_no", "来源批次"],
                    ["status", "状态", v => statusTag(v)]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>灭菌记录</h2><div class="panel-actions">${filterInput("sterilize-records", "筛选批次/设备/程序")}</div></div>
                <div class="panel-body">${table(filterRows("sterilize-records", data.records || [], ["batch_no", "equipment_name", "program_name", "package_list", "remark"]), [
                    ["batch_no", "灭菌批次"],
                    ["equipment_name", "设备"],
                    ["program_name", "程序"],
                    ["package_list", "标签清单"],
                    ["need_bio_test", "生物监测", v => Number(v) === 1 ? tag("需要", "amber") : tag("不需要", "green")],
                    ["bio_test_status", "生物结果", v => Number(v) === 0 ? tag("待录入", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                    ["result", "结果", v => v === null || v === undefined || Number(v) === 0 ? tag("进行中", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                    ["remark", "备注"],
                    ["id", "操作", (v, row) => `<button class="ghost edit-document" data-type="sterilizationRecords" data-id="${safe(v)}" data-row="${encodeRow(row)}">编辑单据</button>`]
                ])}</div>
            </section>
        </div>
    `;
}

// 渲染生物监测记录管理：生物结果录入由业务终端完成，Web 端只展示影响范围和流水。
function renderBioWork() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>生物监测记录查看</h2>
                <p class="subtle">这里展示需要生物监测的灭菌批次、标签影响范围和录入流水，不在 Web 端执行业务放行。</p>
            </div>
            <span class="tag blue">只读留痕</span>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>待监测标签</h2><div class="panel-actions">${filterInput("bio-labels", "筛选标签/包名/批次")}</div></div>
                <div class="panel-body">${table(filterRows("bio-labels", data.labels || [], ["label_no", "package_name", "lot_no", "status"]), [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["lot_no", "来源批次"],
                    ["status", "状态", v => statusTag(v)]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>灭菌批次</h2><div class="panel-actions">${filterInput("bio-records", "筛选批次/设备/备注")}</div></div>
                <div class="panel-body">${table(filterRows("bio-records", data.records || [], ["batch_no", "equipment_name", "package_list", "remark"]), [
                    ["batch_no", "灭菌批次"],
                    ["equipment_name", "设备"],
                    ["package_list", "标签清单"],
                    ["bio_test_status", "生物结果", v => Number(v) === 0 ? tag("待录入", "amber") : Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                    ["remark", "备注"]
                ])}</div>
            </section>
        </div>
        <section class="panel">
            <div class="panel-head"><h2>监测流水</h2><div class="panel-actions">${filterInput("bio-tests", "筛选批次/指示剂/人员")}</div></div>
            <div class="panel-body">${table(filterRows("bio-tests", data.tests || [], ["sterilization_batch_no", "indicator_batch", "operator_name"]), [
                ["sterilization_batch_no", "灭菌批次"],
                ["indicator_batch", "指示剂批号"],
                ["result", "结果", v => Number(v) === 1 ? tag("合格", "green") : tag("不合格", "red")],
                ["operator_name", "录入人"],
                ["input_time", "录入时间"]
            ])}</div>
        </section>
    `;
}

// 渲染发放单管理：发放由触摸端/PDA 执行，Web 端只查看发放单和标签去向。
function renderDistributeWork() {
    const data = state.workArea || {};
    return `
        <section class="section-hero">
            <div>
                <p class="eyebrow">单据管理</p>
                <h2>发放单查看</h2>
                <p class="subtle">发放业务在发放触摸台或 PDA 执行，Web 端只查看发放单、目标科室和标签状态。</p>
            </div>
            <span class="tag blue">非业务操作入口</span>
        </section>
        <div class="grid cols-2">
            <section class="panel">
                <div class="panel-head"><h2>发放标签</h2><div class="panel-actions">${filterInput("distribute-labels", "筛选标签/包名/批次")}</div></div>
                <div class="panel-body">${table(filterRows("distribute-labels", data.labels || [], ["label_no", "package_name", "lot_no", "status"]), [
                    ["label_no", "标签号"],
                    ["package_name", "包名"],
                    ["lot_no", "来源批次"],
                    ["expire_date", "失效时间"],
                    ["status", "状态", v => statusTag(v)]
                ])}</div>
            </section>
            <section class="panel">
                <div class="panel-head"><h2>发放单</h2><div class="panel-actions">${filterInput("distribute-orders", "筛选单号/科室/人员")}</div></div>
                <div class="panel-body">${table(filterRows("distribute-orders", data.orders || [], ["order_no", "dept_name", "operator_name", "package_list"]), [
                    ["order_no", "发放单"],
                    ["dept_name", "科室"],
                    ["package_list", "标签清单"],
                    ["operator_name", "发放人"],
                    ["distribute_time", "时间"],
                    ["id", "操作", (v, row) => `<button class="ghost edit-document" data-type="distributeOrders" data-id="${safe(v)}" data-row="${encodeRow(row)}">编辑单据</button>`]
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
    document.querySelectorAll(".edit-document").forEach(btn => btn.addEventListener("click", editDocumentField));
    document.querySelectorAll(".data-filter").forEach(input => input.addEventListener("input", handleFilterInput));
    document.querySelectorAll(".crud-add").forEach(btn => btn.addEventListener("click", () => openCrudModal(btn.dataset.entity)));
    document.querySelectorAll(".crud-edit").forEach(btn => btn.addEventListener("click", () => openCrudModal(btn.dataset.entity, decodeRow(btn.dataset.row))));
    document.querySelectorAll(".crud-delete").forEach(btn => btn.addEventListener("click", () => deleteCrudRow(btn.dataset.entity, btn.dataset.id)));
    document.querySelectorAll(".save-role-permissions").forEach(btn => btn.addEventListener("click", () => saveRolePermissions(btn.dataset.roleId)));
    const addTemplateBtn = document.getElementById("addTemplateBtn");
    if (addTemplateBtn) addTemplateBtn.addEventListener("click", addTemplate);
}

function handleFilterInput(event) {
    const input = event.currentTarget;
    state.filters[input.dataset.filter] = input.value;
    clearTimeout(handleFilterInput.timer);
    handleFilterInput.timer = setTimeout(render, 180);
}

function openCrudModal(entityKey, row = null) {
    const config = adminEntityConfigs[entityKey];
    const modal = ensureModal();
    modal.innerHTML = `
        <div class="modal-card">
            <div class="modal-head">
                <h2>${row ? "编辑" : "新增"}${safe(config.title)}</h2>
                <button class="ghost modal-close">关闭</button>
            </div>
            <div class="modal-body">
                <div class="form-grid">
                    ${config.fields.map(field => renderField(field, row)).join("")}
                </div>
            </div>
            <div class="modal-foot">
                <button class="ghost modal-close">取消</button>
                <button class="primary crud-save">保存</button>
            </div>
        </div>
    `;
    modal.classList.remove("hidden");
    modal.querySelectorAll(".modal-close").forEach(btn => btn.addEventListener("click", closeModal));
    modal.querySelector(".crud-save").addEventListener("click", () => saveCrudRow(entityKey, row && row.id));
}

function renderField(field, row) {
    const [name, label, type, fallback, options] = field;
    const defaultValue = typeof fallback === "function" ? fallback() : fallback;
    const value = row && row[name] !== undefined && row[name] !== null ? row[name] : defaultValue;
    if (type === "select") {
        const rows = typeof options === "function" ? options() : options;
        return `
            <label>${safe(label)}
                <select data-field="${safe(name)}">
                    ${(rows || []).map(item => `<option value="${safe(item[0])}" ${String(value) === String(item[0]) ? "selected" : ""}>${safe(item[1])}</option>`).join("")}
                </select>
            </label>
        `;
    }
    if (type === "textarea") {
        return `<label class="wide-field">${safe(label)}<textarea data-field="${safe(name)}">${safe(formatFieldValue(value))}</textarea></label>`;
    }
    return `<label>${safe(label)}<input data-field="${safe(name)}" type="${safe(type)}" value="${safe(formatFieldValue(value))}"></label>`;
}

function formatFieldValue(value) {
    if (Array.isArray(value) || (value && typeof value === "object")) {
        return JSON.stringify(value);
    }
    return value ?? "";
}

async function saveCrudRow(entityKey, rowId) {
    const config = adminEntityConfigs[entityKey];
    const modal = ensureModal();
    const body = {};
    config.fields.forEach(field => {
        const [name, , type] = field;
        const element = modal.querySelector(`[data-field="${name}"]`);
        if (!element) return;
        body[name] = normalizeFieldValue(type, element.value);
    });
    await api(rowId ? `admin/${config.entity}/${rowId}` : `admin/${config.entity}`, {
        method: rowId ? "PUT" : "POST",
        body
    });
    closeModal();
    showNotice(`${config.title}已保存`);
    state.basic = await api("basic");
    render();
}

function normalizeFieldValue(type, value) {
    if (type === "number") return Number(value || 0);
    if (type === "textarea") {
        const text = value.trim();
        if ((text.startsWith("[") && text.endsWith("]")) || (text.startsWith("{") && text.endsWith("}"))) {
            try {
                return JSON.parse(text);
            } catch {
                return text;
            }
        }
    }
    return value;
}

async function deleteCrudRow(entityKey, rowId) {
    const config = adminEntityConfigs[entityKey];
    if (!rowId || !confirm(`确认停用/删除该${config.title}？`)) return;
    await api(`admin/${config.entity}/${rowId}`, { method: "DELETE" });
    showNotice(`${config.title}已停用`);
    state.basic = await api("basic");
    render();
}

async function saveRolePermissions(roleId) {
    const permissionIds = Array.from(document.querySelectorAll(`.role-permission-check[data-role-id="${roleId}"]:checked`))
        .map(input => input.value);
    await api(`roles/${roleId}/permissions`, { method: "PUT", body: { permissionIds } });
    showNotice("角色权限已保存");
    state.basic = await api("basic");
    render();
}

function ensureModal() {
    let modal = document.getElementById("modalRoot");
    if (!modal) {
        modal = document.createElement("section");
        modal.id = "modalRoot";
        modal.className = "modal hidden";
        document.body.appendChild(modal);
    }
    return modal;
}

function closeModal() {
    ensureModal().classList.add("hidden");
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

// 编辑已产生单据的管理字段，不触发任何现场业务流转。
function editDocumentField(event) {
    const btn = event.currentTarget;
    openDocumentModal(btn.dataset.type, btn.dataset.id, decodeRow(btn.dataset.row));
}

function openDocumentModal(docType, rowId, row) {
    const config = documentEditConfigs[docType];
    const modal = ensureModal();
    modal.innerHTML = `
        <div class="modal-card">
            <div class="modal-head">
                <h2>编辑${safe(config.title)}</h2>
                <button class="ghost modal-close">关闭</button>
            </div>
            <div class="modal-body">
                <div class="form-grid">
                    ${config.fields.map(field => renderField(field, row)).join("")}
                </div>
            </div>
            <div class="modal-foot">
                <button class="ghost modal-close">取消</button>
                <button class="primary document-save">保存更正</button>
            </div>
        </div>
    `;
    modal.classList.remove("hidden");
    modal.querySelectorAll(".modal-close").forEach(btn => btn.addEventListener("click", closeModal));
    modal.querySelector(".document-save").addEventListener("click", () => saveDocumentRow(docType, rowId));
}

async function saveDocumentRow(docType, rowId) {
    const config = documentEditConfigs[docType];
    const modal = ensureModal();
    const body = {};
    config.fields.forEach(field => {
        const [name, , type] = field;
        const element = modal.querySelector(`[data-field="${name}"]`);
        if (!element) return;
        body[name] = normalizeFieldValue(type, element.value);
    });
    await api(`documents/${docType}/${rowId}`, {
        method: "PUT",
        body
    });
    closeModal();
    showNotice("单据已更新");
    await refresh();
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
        <div class="table-wrap">
        <table>
            <thead><tr>${columns.map(c => `<th>${c[1]}</th>`).join("")}</tr></thead>
            <tbody>${rows.map(row => `<tr>${columns.map(c => `<td>${c[2] ? c[2](row[c[0]], row) : safe(row[c[0]])}</td>`).join("")}</tr>`).join("")}</tbody>
        </table>
        </div>
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

function equipmentTypeText(value) {
    return Number(value) === 1 ? "清洗设备"
        : Number(value) === 2 ? "高温灭菌"
        : Number(value) === 3 ? "低温灭菌"
        : value || "";
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
