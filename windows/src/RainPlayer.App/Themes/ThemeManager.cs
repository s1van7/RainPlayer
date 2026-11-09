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
            Bg: Hex("#0B0D12"), Surface: Hex("#151922"), Card: Hex("#1E2430"), CardHover: Hex("#252C3B"),
            Accent: Hex("#6C8CFF"), AccentHover: Hex("#8AA3FF"), Text: Hex("#F2F4F8"),
            SubText: Hex("#9BA3B4"), Faint: Hex("#6B7280"), Border: Hex("#2A3142")),

        new("graphite", "石墨灰",
            Bg: Hex("#08090A"), Surface: Hex("#121417"), Card: Hex("#1C1F24"), CardHover: Hex("#242830"),
            Accent: Hex("#D5DAE3"), AccentHover: Hex("#EDF1F7"), Text: Hex("#F4F5F7"),
            SubText: Hex("#9AA0AA"), Faint: Hex("#666B73"), Border: Hex("#2A2E34")),

        new("ocean", "深海青",
            Bg: Hex("#04100F"), Surface: Hex("#0A1A18"), Card: Hex("#102623"), CardHover: Hex("#153029"),
            Accent: Hex("#35D0C0"), AccentHover: Hex("#57E4D5"), Text: Hex("#EFFAF8"),
            SubText: Hex("#8FAFAA"), Faint: Hex("#5C7A76"), Border: Hex("#1D3A36")),

        new("violet", "暮光紫",
            Bg: Hex("#0B0714"), Surface: Hex("#150E24"), Card: Hex("#201634"), CardHover: Hex("#2A1D43"),
            Accent: Hex("#B57BFF"), AccentHover: Hex("#C99BFF"), Text: Hex("#F6F1FF"),
            SubText: Hex("#A99BBB"), Faint: Hex("#6E6280"), Border: Hex("#31234A")),

        new("amber", "暖琥珀",
            Bg: Hex("#100C06"), Surface: Hex("#1A1309"), Card: Hex("#261B0E"), CardHover: Hex("#322414"),
            Accent: Hex("#F0A93B"), AccentHover: Hex("#FFBE5C"), Text: Hex("#FDF7EC"),
            SubText: Hex("#B5A78F"), Faint: Hex("#7C7160"), Border: Hex("#3A2C18")),

        new("rose", "玫瑰红",
            Bg: Hex("#12070B"), Surface: Hex("#1B0C12"), Card: Hex("#28131B"), CardHover: Hex("#341922"),
            Accent: Hex("#FF6B8A"), AccentHover: Hex("#FF8CA4"), Text: Hex("#FFF1F4"),
            SubText: Hex("#BF9AA4"), Faint: Hex("#84656D"), Border: Hex("#3E1D28")),

        new("forest", "松林绿",
            Bg: Hex("#050F0A"), Surface: Hex("#0A1811"), Card: Hex("#12251A"), CardHover: Hex("#183022"),
            Accent: Hex("#5FD08A"), AccentHover: Hex("#7FE3A5"), Text: Hex("#F0FBF4"),
            SubText: Hex("#93B2A2"), Faint: Hex("#5F7D6E"), Border: Hex("#1D3626")),
    };

    public static Palette Current { get; private set; } = All[0];

    public static bool GlassEnabled { get; private set; } = true;

    public static Palette Find(string? key) =>
        All.FirstOrDefault(p => p.Key == key) ?? All[0];

    /// <summary>应用主题。glass 打开时面板底色带透明度，背景图能透出来。</summary>
    public static void Apply(string? key, bool glass)
    {
        Apply(Find(key), glass);
    }

    public static void Apply(Palette palette, bool glass)
    {
        Current = palette;
        GlassEnabled = glass;

        var res = Application.Current?.Resources;
        if (res == null) return;

        // 面板类底色：毛玻璃时降低不透明度
        Set(res, "BgBrush", palette.Bg);
        Set(res, "SurfaceBrush", glass ? WithAlpha(palette.Surface, 0xE0) : palette.Surface);
        Set(res, "CardBrush", glass ? WithAlpha(palette.Card, 0xCC) : palette.Card);
        Set(res, "CardHoverBrush", glass ? WithAlpha(palette.CardHover, 0xE6) : palette.CardHover);
        Set(res, "AccentBrush", palette.Accent);
        Set(res, "AccentHoverBrush", palette.AccentHover);
        Set(res, "TextBrush", palette.Text);
        Set(res, "SubTextBrush", palette.SubText);
        Set(res, "FaintTextBrush", palette.Faint);
        Set(res, "BorderBrush", palette.Border);
        Set(res, "GlassBorderBrush", WithAlpha(Colors.White, glass ? (byte)0x24 : (byte)0x00));
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
