using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Threading;

namespace Cssd.TouchStation;

public partial class MainWindow : Window
{
    private readonly TouchConfig _config;
    private readonly HttpClient _http = new();
    private readonly ObservableCollection<PackageTask> _tasks = new();
    private readonly DispatcherTimer _clockTimer = new();

    public MainWindow()
    {
        InitializeComponent();
        _config = TouchConfig.Load();
        _http.BaseAddress = new Uri(_config.ServerBaseUrl.TrimEnd('/') + "/");
        PackageList.ItemsSource = _tasks;
        ConfigureStationText();
        ConfigureClock();
    }

    // 顶部空白区域支持拖动窗口，双击可最大化或还原，方便触摸端在桌面调试时移动。
    private void DragArea_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            ToggleWindowState();
            return;
        }

        if (e.ButtonState == MouseButtonState.Pressed)
        {
            try
            {
                DragMove();
            }
            catch (InvalidOperationException)
            {
                // 鼠标状态被系统打断时忽略，避免影响业务操作。
            }
        }
    }

    // 最小化窗口，用于触摸台桌面调试和多程序切换。
    private void MinimizeButton_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    // 最大化/还原窗口，等同于系统标题栏的最大化按钮。
    private void MaximizeButton_Click(object sender, RoutedEventArgs e)
    {
        ToggleWindowState();
    }

    // 关闭触摸台程序。
    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    // 在最大化和普通窗口之间切换。
    private void ToggleWindowState()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    // 登录按钮只做本地终端身份切换，真实业务操作仍由后端接口留痕。
    private async void LoginButton_Click(object sender, RoutedEventArgs e)
    {
        UserNameText.Text = $"当前用户  {LoginWorkNoInput.Text.Trim()}";
        LoginPanel.Visibility = Visibility.Collapsed;
        WorkPanel.Visibility = Visibility.Visible;
        await LoadTasksAsync();
    }

    // 底部扫码/提交按钮统一走当前工位接口，工位由 appsettings.json 控制。
    private async void SubmitButton_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var packageCode = PackageCodeInput.Text.Trim();
            var second = SecondInput.Text.Trim();
            if (string.IsNullOrWhiteSpace(packageCode) && PackageList.SelectedItem is PackageTask selected)
            {
                packageCode = selected.InstanceCode;
            }
            if (string.IsNullOrWhiteSpace(packageCode))
            {
                SetMessage("请先扫描或选择器械包");
                return;
            }

            var payload = BuildPayload(packageCode, second);
            var json = JsonSerializer.Serialize(payload);
            var response = await _http.PostAsync(ApiForStation(), new StringContent(json, Encoding.UTF8, "application/json"));
            var body = await response.Content.ReadAsStringAsync();
            if (!body.Contains("\"success\":true"))
            {
                SetMessage("操作失败：" + body);
                return;
            }

            SetMessage("操作完成，已同步后台");
            PackageCodeInput.Clear();
            await LoadTasksAsync();
        }
        catch (Exception ex)
        {
            SetMessage("连接后台失败：" + ex.Message);
            NetworkText.Text = "网络异常";
        }
    }

    // 选择任务时刷新左侧器械包信息，减少触摸台重复输入。
    private void PackageList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (PackageList.SelectedItem is not PackageTask selected)
        {
            return;
        }
        PackageCodeInput.Text = selected.InstanceCode;
        PackageInfoText.Text = $"包码：{selected.InstanceCode}\n包名称：{selected.PackageName}\n来源科室：{selected.DeptName}\n当前状态：{selected.StatusText}\n批次：{selected.BatchNo}";
    }

    // 从后台加载当前工位可处理任务，并同步到回收单概览区域。
    private async Task LoadTasksAsync()
    {
        try
        {
            var response = await _http.GetStringAsync($"station/{_config.StationCode}");
            using var doc = JsonDocument.Parse(response);
            _tasks.Clear();
            foreach (var item in doc.RootElement.GetProperty("data").GetProperty("packages").EnumerateArray())
            {
                _tasks.Add(new PackageTask(
                    item.GetProperty("instance_code").GetString() ?? "",
                    item.GetProperty("package_name").GetString() ?? "",
                    StatusText(item.GetProperty("current_status").GetString() ?? ""),
                    item.TryGetProperty("dept_name", out var dept) ? dept.GetString() ?? "" : "",
                    item.TryGetProperty("current_batch_no", out var batch) ? batch.GetString() ?? "" : ""
                ));
            }
            NetworkText.Text = "网络正常";
            UpdateSummary();
        }
        catch (Exception ex)
        {
            SetMessage("加载任务失败：" + ex.Message);
            NetworkText.Text = "网络异常";
        }
    }

    // 根据当前任务集合生成界面顶部单据摘要，贴近设计图的当前回收单区域。
    private void UpdateSummary()
    {
        var first = _tasks.FirstOrDefault();
        CurrentOrderNoText.Text = first == null ? "回收单号  -" : $"回收单号  {first.BatchNo}";
        RecycleUserText.Text = $"回收人  {LoginWorkNoInput.Text.Trim()}";
        TotalCountText.Text = $"包数量  {_tasks.Count}";
        ReturnCountText.Text = $"还包数  {_tasks.Count(x => x.StatusText.Contains("借包") || x.StatusText.Contains("使用"))}";
        PackageInfoText.Text = first == null
            ? "请扫描或选择器械包"
            : $"包码：{first.InstanceCode}\n包名称：{first.PackageName}\n来源科室：{first.DeptName}\n当前状态：{first.StatusText}\n批次：{first.BatchNo}";
        SetMessage($"已加载 {_tasks.Count} 条任务");
    }

    // 同步更新登录页和工作台提示，避免界面切换后反馈不可见。
    private void SetMessage(string message)
    {
        LoginMessageText.Text = message;
        WorkMessageText.Text = message;
    }

    // 根据不同工位生成对应请求体，确保同一个安装包可通过配置切换回收、配包、发放等模式。
    private Dictionary<string, object> BuildPayload(string packageCode, string second)
    {
        Dictionary<string, object> payload = _config.StationCode switch
        {
            "recycle" => new() { ["packageCode"] = packageCode, ["basketCode"] = string.IsNullOrWhiteSpace(second) ? "BASK-01" : second },
            "assemble" => new() { ["station"] = "assemble", ["packageCode"] = packageCode },
            "pack" => new() { ["station"] = "pack", ["packageCode"] = packageCode },
            "sterilize" => new() { ["packageCode"] = packageCode, ["equipmentCode"] = string.IsNullOrWhiteSpace(second) ? "ST-H-01" : second, ["program"] = "标准", ["needBio"] = true },
            "distribute" => new() { ["packageCode"] = packageCode, ["deptCode"] = string.IsNullOrWhiteSpace(second) ? "OR" : second },
            _ => new() { ["packageCode"] = packageCode }
        };
        // 后端据此区分业务终端和 Web 管理端，Web 端不能直接推动业务流转。
        payload["clientType"] = "TOUCH";
        payload["deviceCode"] = _config.DeviceCode;
        return payload;
    }

    // 返回当前工位要调用的后台接口地址。
    private string ApiForStation() => _config.StationCode switch
    {
        "recycle" => "workflow/recycle",
        "assemble" => "workflow/station/complete",
        "pack" => "workflow/station/complete",
        "sterilize" => "workflow/sterilize/start",
        "distribute" => "workflow/distribute",
        _ => "workflow/recycle"
    };

    // 初始化工位标题、默认输入项和登录页终端提示。
    private void ConfigureStationText()
    {
        var stationName = StationName(_config.StationCode);
        StationTitleText.Text = $"CSSD 复用无菌器械闭环追溯系统 - {stationName}操作台";
        LoginMessageText.Text = $"当前终端：{stationName}01";
        SecondInput.Text = DefaultSecondInput();
        DeptText.Text = "所属科室  CSSD";
    }

    // 每秒刷新顶部时间，保持触摸台大屏实时感。
    private void ConfigureClock()
    {
        _clockTimer.Interval = TimeSpan.FromSeconds(1);
        _clockTimer.Tick += (_, _) => CurrentTimeText.Text = DateTime.Now.ToString("HH:mm:ss");
        _clockTimer.Start();
        CurrentTimeText.Text = DateTime.Now.ToString("HH:mm:ss");
    }

    // 按工位返回第二输入框默认值，回收为筐号，灭菌为设备，发放为科室。
    private string DefaultSecondInput() => _config.StationCode switch
    {
        "recycle" => "BASK-01",
        "sterilize" => "ST-H-01",
        "distribute" => "OR",
        _ => ""
    };

    // 工位编码转换为中文名称，统一登录页和工作台标题。
    private static string StationName(string station) => station switch
    {
        "recycle" => "回收区",
        "wash" => "清洗区",
        "assemble" => "配包区",
        "pack" => "打包区",
        "sterilize" => "灭菌区",
        "distribute" => "发放区",
        _ => station
    };

    // 后端状态码转换为触摸台操作员可读文本。
    private static string StatusText(string status) => status switch
    {
        "IN_DEPT" => "科室在库",
        "RECYCLED" => "待清洗",
        "WASHED" => "待配包",
        "ASSEMBLED" => "待打包",
        "PACKED" => "待灭菌",
        "BIO_PENDING" => "待生物监测",
        "STERILIZED" => "待发放",
        "DISTRIBUTED" => "已发放",
        _ => status
    };
}

public record PackageTask(string InstanceCode, string PackageName, string StatusText, string DeptName, string BatchNo);

public record TouchConfig(string ServerBaseUrl, string StationCode, string DeviceCode, string OperatorWorkNo, bool CameraEnabled, bool AutoUploadVideo)
{
    // 从安装目录读取触摸台配置，现场只需修改配置文件即可切换服务器和工位。
    public static TouchConfig Load()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "appsettings.json");
        var json = File.ReadAllText(path);
        return JsonSerializer.Deserialize<TouchConfig>(json, new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
               ?? throw new InvalidOperationException("触摸台配置文件无效");
    }
}
