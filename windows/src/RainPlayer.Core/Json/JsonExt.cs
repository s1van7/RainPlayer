using System.Globalization;
using System.Text.Json;

namespace RainPlayer.Core.Json;

/// <summary>
/// System.Text.Json 的容错读取扩展。服务器返回的字段类型经常不一致
/// （数字/字符串混用、大小写不一），这里统一做宽松解析。
/// 缺失字段用 <c>default(JsonElement)</c>（ValueKind = Undefined）表示，
/// 所有读取方法遇到它都返回 null，调用方无需判空。
/// </summary>
public static class JsonExt
{
    public static JsonDocument? Parse(string text)
    {
        try
        {
            return JsonDocument.Parse(text);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    /// <summary>按名称取属性，忽略大小写。</summary>
    public static JsonElement? Get(this JsonElement element, string name)
    {
        if (element.ValueKind != JsonValueKind.Object) return null;
        if (element.TryGetProperty(name, out var exact)) return exact;
        foreach (var property in element.EnumerateObject())
        {
            if (string.Equals(property.Name, name, StringComparison.OrdinalIgnoreCase))
                return property.Value;
        }
        return null;
    }

    public static JsonElement Obj(this JsonElement element, string name)
    {
        var child = element.Get(name);
        return child is { ValueKind: JsonValueKind.Object } value ? value : default;
    }

    public static bool IsObject(this JsonElement element) => element.ValueKind == JsonValueKind.Object;

    public static List<JsonElement> Arr(this JsonElement element, string name)
    {
        var child = element.Get(name);
        return child is { ValueKind: JsonValueKind.Array } array
            ? array.EnumerateArray().ToList()
            : new List<JsonElement>();
    }

    public static List<JsonElement> Arr(this JsonElement element)
        => element.ValueKind == JsonValueKind.Array
            ? element.EnumerateArray().ToList()
            : new List<JsonElement>();

    public static string? Str(this JsonElement element, string name)
    {
        var child = element.Get(name);
        return child switch
        {
            null => null,
            { ValueKind: JsonValueKind.String } s => s.GetString(),
            { ValueKind: JsonValueKind.Number } n => n.GetRawText(),
            { ValueKind: JsonValueKind.True } => "true",
            { ValueKind: JsonValueKind.False } => "false",
            _ => null,
        };
    }

    public static string StrOr(this JsonElement element, string name, string fallback)
        => element.Str(name) is { Length: > 0 } value ? value : fallback;

    public static int? Int(this JsonElement element, string name)
    {
        var child = element.Get(name);
        if (child is null) return null;
        switch (child.Value.ValueKind)
        {
            case JsonValueKind.Number:
                return child.Value.TryGetInt32(out var i) ? i : (int)child.Value.GetDouble();
            case JsonValueKind.String:
                var text = child.Value.GetString();
                if (int.TryParse(text, NumberStyles.Integer, CultureInfo.InvariantCulture, out var parsed))
                    return parsed;
                if (double.TryParse(text, NumberStyles.Float, CultureInfo.InvariantCulture, out var d))
                    return (int)d;
                return null;
            case JsonValueKind.True:
                return 1;
            case JsonValueKind.False:
                return 0;
            default:
                return null;
        }
    }

    public static long? Long(this JsonElement element, string name)
    {
        var child = element.Get(name);
        if (child is null) return null;
        switch (child.Value.ValueKind)
        {
            case JsonValueKind.Number:
                return child.Value.TryGetInt64(out var l) ? l : (long)child.Value.GetDouble();
            case JsonValueKind.String:
                return long.TryParse(child.Value.GetString(), NumberStyles.Integer, CultureInfo.InvariantCulture, out var parsed)
                    ? parsed
                    : null;
            default:
                return null;
        }
    }

    public static double? Dbl(this JsonElement element, string name)
    {
        var child = element.Get(name);
        if (child is null) return null;
        switch (child.Value.ValueKind)
        {
            case JsonValueKind.Number:
                return child.Value.GetDouble();
            case JsonValueKind.String:
                return double.TryParse(child.Value.GetString(), NumberStyles.Float,
                    CultureInfo.InvariantCulture, out var parsed) ? parsed : null;
            default:
                return null;
        }
    }

    public static bool? Bool(this JsonElement element, string name)
    {
        var child = element.Get(name);
        if (child is null) return null;
        switch (child.Value.ValueKind)
        {
            case JsonValueKind.True: return true;
            case JsonValueKind.False: return false;
            case JsonValueKind.Number: return child.Value.GetDouble() != 0;
            case JsonValueKind.String:
                return child.Value.GetString() switch
                {
                    "true" or "True" or "1" => true,
                    "false" or "False" or "0" => false,
                    _ => null,
                };
            default: return null;
        }
    }

    public static List<string> StringList(this JsonElement element, string name)
        => element.Arr(name)
            .Select(item => item.ValueKind == JsonValueKind.String ? item.GetString() : null)
            .Where(value => !string.IsNullOrEmpty(value))
            .Select(value => value!)
            .ToList();

    /// <summary>Emby 的时长单位是 100 纳秒 tick。</summary>
    public static long TicksToMs(long? ticks) => ticks is > 0 ? ticks.Value / 10_000L : 0L;
}
