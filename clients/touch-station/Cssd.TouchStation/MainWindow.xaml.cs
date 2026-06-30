using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Windows;

namespace Cssd.TouchStation;

public partial class MainWindow : Window
{
    private readonly TouchConfig _config;
    private readonly HttpClient _http = new();
    private readonly ObservableCollection<PackageTask> _tasks = new();

    public MainWindow()
    {
        InitializeComponent();
        _config = TouchConfig.Load();
        _http.BaseAddress = new Uri(_config.ServerBaseUrl.TrimEnd('/') + "/");
        PackageList.ItemsSource = _tasks;
        StationText.Text = $"{StationName(_config.StationCode)} / {_config.DeviceCode}";
        SecondInput.Text = DefaultSecondInput();
        Loaded += async (_, _) => await LoadTasksAsync();
    }

    private async void RefreshButton_Click(object sender, RoutedEventArgs e)
    {
        await LoadTasksAsync();
    }

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
                MessageText.Text = "请先扫描或选择包";
                return;
            }

            var payload = BuildPayload(packageCode, second);
            var api = ApiForStation();
            var json = JsonSerializer.Serialize(payload);
            var response = await _http.PostAsync(api, new StringContent(json, Encoding.UTF8, "application/json"));
            var body = await response.Content.ReadAsStringAsync();
            if (!body.Contains("\"success\":true"))
            {
                MessageText.Text = "操作失败：" + body;
                return;
            }
            MessageText.Text = "操作完成，已同步后台";
            PackageCodeInput.Clear();
            await LoadTasksAsync();
        }
        catch (Exception ex)
        {
            MessageText.Text = "连接后台失败：" + ex.Message;
        }
    }

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
            MessageText.Text = $"已加载 {_tasks.Count} 条任务";
        }
        catch (Exception ex)
        {
            MessageText.Text = "加载任务失败：" + ex.Message;
        }
    }

    private Dictionary<string, object> BuildPayload(string packageCode, string second)
    {
        return _config.StationCode switch
        {
            "recycle" => new() { ["packageCode"] = packageCode, ["basketCode"] = string.IsNullOrWhiteSpace(second) ? "BASK-01" : second, ["deviceCode"] = _config.DeviceCode },
            "assemble" => new() { ["station"] = "assemble", ["packageCode"] = packageCode },
            "pack" => new() { ["station"] = "pack", ["packageCode"] = packageCode },
            "sterilize" => new() { ["packageCode"] = packageCode, ["equipmentCode"] = string.IsNullOrWhiteSpace(second) ? "ST-H-01" : second, ["program"] = "标准", ["needBio"] = true },
            "distribute" => new() { ["packageCode"] = packageCode, ["deptCode"] = string.IsNullOrWhiteSpace(second) ? "OR" : second },
            _ => new() { ["packageCode"] = packageCode }
        };
    }

    private string ApiForStation() => _config.StationCode switch
    {
        "recycle" => "workflow/recycle",
        "assemble" => "workflow/station/complete",
        "pack" => "workflow/station/complete",
        "sterilize" => "workflow/sterilize/start",
        "distribute" => "workflow/distribute",
        _ => "workflow/recycle"
    };

    private string DefaultSecondInput() => _config.StationCode switch
    {
        "recycle" => "BASK-01",
        "sterilize" => "ST-H-01",
        "distribute" => "OR",
        _ => ""
    };

    private static string StationName(string station) => station switch
    {
        "recycle" => "回收台",
        "wash" => "清洗台",
        "assemble" => "配包台",
        "pack" => "打包台",
        "sterilize" => "灭菌台",
        "distribute" => "发放台",
        _ => station
    };

    private static string StatusText(string status) => status switch
    {
        "IN_DEPT" => "科室在库",
        "RECYCLED" => "待清洗",
        "WASHED" => "待配包",
        "ASSEMBLED" => "待打包",
        "PACKED" => "待灭菌",
        "BIO_PENDING" => "待生物监测",
        "STERILIZED" => "待发放",
        _ => status
    };
}

public record PackageTask(string InstanceCode, string PackageName, string StatusText, string DeptName, string BatchNo);

public record TouchConfig(string ServerBaseUrl, string StationCode, string DeviceCode, string OperatorWorkNo, bool CameraEnabled, bool AutoUploadVideo)
{
    public static TouchConfig Load()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "appsettings.json");
        var json = File.ReadAllText(path);
        return JsonSerializer.Deserialize<TouchConfig>(json, new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
               ?? throw new InvalidOperationException("触摸台配置文件无效");
    }
}
