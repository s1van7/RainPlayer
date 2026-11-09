using System.Windows;
using System.Windows.Media;

namespace RainPlayer.App.Themes;

/// <summary>一套配色。所有界面只读这些语义色，换主题时整站一起变。</summary>
public sealed record Palette(
    string Key,
    string DisplayName,
    Color Bg,
    Color Surface,
    Color Card,
    Color CardHover,
    Color Accent,
    Color AccentHover,
    Color Text,
    Color SubText,
    Color Faint,
    Color Border);

/// <summary>
/// 主题与毛玻璃。
///
/// 实现方式：Theme.xaml 里的画刷是 <see cref="SolidColorBrush"/> 实例，
/// 视图用 StaticResource 引用它们——引用的是同一个对象，所以运行时改
/// 这个对象的 Color 就能让所有引用处立即变色，不需要重建窗口，
/// 也不用把几百处 StaticResource 改成 DynamicResource。
/// </summary>
public static class ThemeManager
{
    public static readonly Palette[] All =
    {
        new("midnight", "极夜蓝",
            Bg: Hex("#121A24"), Surface: Hex("#1A2432"), Card: Hex("#243244"), CardHover: Hex("#2C3C50"),
            Accent: Hex("#6C9BFF"), AccentHover: Hex("#8FB3FF"), Text: Hex("#F3F6FB"),
            SubText: Hex("#A4B0C2"), Faint: Hex("#76839A"), Border: Hex("#31415A")),

        new("graphite", "石墨灰",
            Bg: Hex("#16181C"), Surface: Hex("#1E2126"), Card: Hex("#2A2E35"), CardHover: Hex("#333840"),
            Accent: Hex("#D5DAE3"), AccentHover: Hex("#EDF1F7"), Text: Hex("#F4F5F7"),
            SubText: Hex("#A3A9B4"), Faint: Hex("#727880"), Border: Hex("#353A42")),

        new("ocean", "深海青",
            Bg: Hex("#0F2024"), Surface: Hex("#162C31"), Card: Hex("#1F3C42"), CardHover: Hex("#284A50"),
            Accent: Hex("#3FD6C4"), AccentHover: Hex("#63E8D8"), Text: Hex("#EFFAF8"),
            SubText: Hex("#9CBDB7"), Faint: Hex("#6D8F8A"), Border: Hex("#28464C")),

        new("violet", "暮光紫",
            Bg: Hex("#1A1326"), Surface: Hex("#231831"), Card: Hex("#302343"), CardHover: Hex("#3A2C51"),
            Accent: Hex("#BE8CFF"), AccentHover: Hex("#D0A8FF"), Text: Hex("#F7F2FF"),
            SubText: Hex("#B2A4C6"), Faint: Hex("#7F7294"), Border: Hex("#3F2F58")),

        new("amber", "暖琥珀",
            Bg: Hex("#1D1610"), Surface: Hex("#261C13"), Card: Hex("#35281A"), CardHover: Hex("#403120"),
            Accent: Hex("#F5B34A"), AccentHover: Hex("#FFC76B"), Text: Hex("#FDF8EE"),
            SubText: Hex("#C0B199"), Faint: Hex("#8C7F6B"), Border: Hex("#473522")),

        new("rose", "玫瑰红",
            Bg: Hex("#21141B"), Surface: Hex("#2A1820"), Card: Hex("#38222D"), CardHover: Hex("#442A36"),
            Accent: Hex("#FF7E9B"), AccentHover: Hex("#FF9DB4"), Text: Hex("#FFF2F5"),
            SubText: Hex("#C8A4AE"), Faint: Hex("#8E6B75"), Border: Hex("#4A2C38")),

        new("forest", "松林绿",
            Bg: Hex("#101F17"), Surface: Hex("#15271D"), Card: Hex("#1E3628"), CardHover: Hex("#274433"),
            Accent: Hex("#6CDB96"), AccentHover: Hex("#8CE9AF"), Text: Hex("#F1FBF5"),
            SubText: Hex("#A0BFAF"), Faint: Hex("#729182"), Border: Hex("#27402F")),
    };

    public static Palette Current { get; private set; } = All[0];

    public static bool GlassEnabled { get; private set; } = true;

    /// <summary>当前是否浅色模式（主题决定色相，明暗模式决定底色）。</summary>
    public static bool IsLight { get; private set; }

    public static Palette Find(string? key) =>
        All.FirstOrDefault(p => p.Key == key) ?? All[0];

    /// <summary>
    /// 浅色变体：底色换成中性灰白，强调色压暗一档，保证在白底上仍然读得清。
    /// </summary>
    public static Palette LightOf(Palette dark) => dark with
    {
        Bg = Hex("#F4F5F9"),
        Surface = Hex("#FFFFFF"),
        Card = Hex("#FFFFFF"),
        CardHover = Hex("#EEF0F5"),
        Accent = Darken(dark.Accent, 0.42),
        AccentHover = Darken(dark.Accent, 0.28),
        Text = Hex("#171B22"),
        SubText = Hex("#6A7280"),
        Faint = Hex("#8A929E"),
        Border = Hex("#DDE1E9"),
    };

    /// <summary>按明暗模式取实际配色。mode 为 system 时读系统「应用模式」。</summary>
    public static void Apply(string? key, bool glass, string? mode = null)
    {
        var light = mode switch
        {
            "light" => true,
            "dark" => false,
            _ => SystemPrefersLight(),
        };
        Apply(Find(key), glass, light);
    }

    private static bool SystemPrefersLight()
    {
        try
        {
            using var k = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            if (k?.GetValue("AppsUseLightTheme") is int v) return v != 0;
        }
        catch
        {
            // 读不到注册表就按深色处理
        }
        return false;
    }

    private static Color Darken(Color c, double amount)
    {
        var f = 1.0 - Math.Clamp(amount, 0, 1);
        return Color.FromRgb((byte)(c.R * f), (byte)(c.G * f), (byte)(c.B * f));
    }

    public static void Apply(Palette palette, bool glass, bool light = false)
    {
        Current = palette;
        GlassEnabled = glass;
        IsLight = light;

        // 浅色模式用独立的浅色变体，深色沿用主题本色
        var p = light ? LightOf(palette) : palette;

        var res = Application.Current?.Resources;
        if (res == null) return;

        // 面板类底色：毛玻璃时降低不透明度
        Set(res, "BgBrush", p.Bg);
        Set(res, "SurfaceBrush", glass ? WithAlpha(p.Surface, light ? (byte)0xD8 : (byte)0xE0) : p.Surface);
        Set(res, "CardBrush", glass ? WithAlpha(p.Card, light ? (byte)0xC0 : (byte)0xCC) : p.Card);
        Set(res, "CardHoverBrush", glass ? WithAlpha(p.CardHover, light ? (byte)0xE0 : (byte)0xE6) : p.CardHover);
        Set(res, "AccentBrush", p.Accent);
        Set(res, "AccentHoverBrush", p.AccentHover);
        Set(res, "TextBrush", p.Text);
        Set(res, "SubTextBrush", p.SubText);
        Set(res, "FaintTextBrush", p.Faint);
        Set(res, "BorderBrush", p.Border);
        Set(res, "GlassBorderBrush", WithAlpha(light ? Colors.Black : Colors.White, glass ? (light ? (byte)0x18 : (byte)0x24) : (byte)0x00));
    }

    private static void Set(ResourceDictionary res, string key, Color color)
    {
        if (res[key] is SolidColorBrush brush)
        {
            if (brush.IsFrozen)
            {
                // 理论上不会走到这里；万一被冻结了就换一个新画刷。
                res[key] = new SolidColorBrush(color);
                return;
            }
            brush.Color = color;
        }
        else
        {
            res[key] = new SolidColorBrush(color);
        }
    }

    public static Color WithAlpha(Color c, byte alpha) => Color.FromArgb(alpha, c.R, c.G, c.B);

    public static Color Hex(string hex)
    {
        var value = hex.TrimStart('#');
        return value.Length switch
        {
            6 => Color.FromRgb(
                Convert.ToByte(value[..2], 16),
                Convert.ToByte(value.Substring(2, 2), 16),
                Convert.ToByte(value.Substring(4, 2), 16)),
            8 => Color.FromArgb(
                Convert.ToByte(value[..2], 16),
                Convert.ToByte(value.Substring(2, 2), 16),
                Convert.ToByte(value.Substring(4, 2), 16),
                Convert.ToByte(value.Substring(6, 2), 16)),
            _ => Colors.Black,
        };
    }
}
