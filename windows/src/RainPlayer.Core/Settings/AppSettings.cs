using System.Text.Json;
using System.Text.Json.Serialization;
using RainPlayer.Core.Models;

namespace RainPlayer.Core.Settings;

/// <summary>本地配置，存放于 %APPDATA%\RainPlayer\settings.json。</summary>
public sealed class AppSettings
{
    public List<ServerConfig> Servers { get; set; } = new();
    public string LastServerId { get; set; } = "";

    /// <summary>音频直通：把 AC3/EAC3/DTS/TrueHD 原码交给电视或功放。</summary>
    public bool AudioPassthrough { get; set; } = true;

    /// <summary>自动连播下一集。</summary>
    public bool AutoPlayNext { get; set; } = true;

    /// <summary>硬件解码：D3D11VA，4K/8K 必需。</summary>
    public bool HardwareDecoding { get; set; } = true;

    /// <summary>HDR 直通：把片源色彩空间原样交给系统（需 Windows 开启 HDR）。</summary>
    public bool HdrPassthrough { get; set; } = true;

    /// <summary>播放结束后的默认音量（0-100）。</summary>
    public int Volume { get; set; } = 80;

    /// <summary>记住窗口大小与位置。</summary>
    public double WindowWidth { get; set; } = 1280;
    public double WindowHeight { get; set; } = 800;
    public bool WindowMaximized { get; set; }

    private static string SettingsDirectory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RainPlayer");

    private static string SettingsPath => Path.Combine(SettingsDirectory, "settings.json");

    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        WriteIndented = true,
        Converters = { new JsonStringEnumConverter() },
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
    };

    public static AppSettings Load()
    {
        try
        {
            if (!File.Exists(SettingsPath)) return new AppSettings();
            var text = File.ReadAllText(SettingsPath);
            var settings = JsonSerializer.Deserialize<AppSettings>(text, SerializerOptions);
            if (settings == null) return new AppSettings();

            // 老配置可能没有 DeviceId
            foreach (var server in settings.Servers.Where(s => string.IsNullOrEmpty(s.DeviceId)))
                server.DeviceId = NewDeviceId();
            return settings;
        }
        catch
        {
            return new AppSettings();
        }
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(SettingsDirectory);
            File.WriteAllText(SettingsPath, JsonSerializer.Serialize(this, SerializerOptions));
        }
        catch
        {
            // 配置写入失败不应影响使用
        }
    }

    public static string NewDeviceId()
    {
        // 与移动端保持一致的短 id 形态，服务器端识别为一个设备
        var bytes = Guid.NewGuid().ToByteArray();
        return Convert.ToHexString(bytes, 0, 8).ToLowerInvariant();
    }

    public ServerConfig? FindServer(string id)
        => Servers.FirstOrDefault(s => s.Id == id);

    public ServerConfig? CurrentServer()
        => FindServer(LastServerId) ?? Servers.FirstOrDefault();

    public void UpsertServer(ServerConfig config)
    {
        var index = Servers.FindIndex(s => s.Id == config.Id);
        if (index >= 0) Servers[index] = config;
        else Servers.Add(config);
        LastServerId = config.Id;
    }
}
