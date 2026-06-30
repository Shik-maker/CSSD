# CSSD触摸台客户端

这是 Windows 触屏一体机客户端源码。它不是 Web 页面，而是可安装到 Windows 工位电脑的软件。

## 工位切换

修改 `Cssd.TouchStation/appsettings.json`：

```json
{
  "ServerBaseUrl": "http://localhost:8080/cssd-trace/api",
  "StationCode": "recycle",
  "DeviceCode": "TOUCH-RECYCLE-01"
}
```

`StationCode` 可选：

- `recycle` 回收台
- `wash` 清洗台
- `assemble` 配包台
- `pack` 打包台
- `sterilize` 灭菌台
- `distribute` 发放台

## 构建要求

当前电脑只有 .NET Runtime，没有 .NET SDK，因此这里先提交源码。安装 .NET 6 SDK 后可执行：

```powershell
dotnet publish .\Cssd.TouchStation\Cssd.TouchStation.csproj -c Release -r win-x64 --self-contained false
```

