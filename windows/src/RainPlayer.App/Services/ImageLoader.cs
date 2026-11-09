using System.Collections.Concurrent;
using System.IO;
using System.Windows.Media.Imaging;
using RainPlayer.Core.Net;

namespace RainPlayer.App.Services;

/// <summary>
/// 海报加载器。
///
/// WPF 的 BitmapImage 不支持自定义请求头，而飞牛的图片接口需要鉴权，
/// 所以统一走 HttpClient 下载字节再解码。带内存缓存与并发限流，
/// 避免滚动列表时把 NAS 打满。
/// </summary>
public static class ImageLoader
{
    private static readonly ConcurrentDictionary<string, BitmapSource> Cache = new();
    private static readonly ConcurrentDictionary<string, byte> Pending = new();
    private static readonly SemaphoreSlim Gate = new(6, 6);

    /// <summary>当前服务器的图片请求头，登录后由主窗口设置。</summary>
    public static IReadOnlyDictionary<string, string>? Headers { get; set; }

    public static BitmapSource? GetCached(string url)
        => Cache.TryGetValue(url, out var image) ? image : null;

    public static async Task<BitmapSource?> LoadAsync(string? url, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(url)) return null;
        if (Cache.TryGetValue(url, out var cached)) return cached;
        if (!Pending.TryAdd(url, 0)) return null;

        try
        {
            await Gate.WaitAsync(ct).ConfigureAwait(false);
            try
            {
                var bytes = await Http.TryGetBytesAsync(url, Headers, ct).ConfigureAwait(false);
                if (bytes is not { Length: > 0 }) return null;

                var image = Decode(bytes);
                if (image != null) Cache[url] = image;
                return image;
            }
            finally
            {
                Gate.Release();
            }
        }
        catch
        {
            return null;
        }
        finally
        {
            Pending.TryRemove(url, out _);
        }
    }

    private static BitmapSource? Decode(byte[] bytes)
    {
        try
        {
            using var stream = new MemoryStream(bytes);
            var bitmap = new BitmapImage();
            bitmap.BeginInit();
            bitmap.CacheOption = BitmapCacheOption.OnLoad;
            bitmap.CreateOptions = BitmapCreateOptions.IgnoreColorProfile;
            // 限制解码尺寸，列表里不需要原始分辨率
            bitmap.DecodePixelWidth = 420;
            bitmap.StreamSource = stream;
            bitmap.EndInit();
            bitmap.Freeze();
            return bitmap;
        }
        catch
        {
            return null;
        }
    }

    public static void Clear()
    {
        Cache.Clear();
        Pending.Clear();
    }
}
