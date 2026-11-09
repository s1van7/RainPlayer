using System.Net;
using System.Net.Http.Headers;
using System.Text;

namespace RainPlayer.Core.Net;

/// <summary>
/// 全局 HttpClient 封装。服务器多为自建 NAS、常用自签证书，
/// 这里默认放行证书错误（与移动端一致），仅用于局域网自建服务。
/// </summary>
public static class Http
{
    private static readonly HttpClientHandler Handler = new()
    {
        ServerCertificateCustomValidationCallback = (_, _, _, _) => true,
        AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate,
        AllowAutoRedirect = true,
    };

    public static readonly HttpClient Client = new(Handler)
    {
        Timeout = TimeSpan.FromSeconds(30),
    };

    public static async Task<string> GetTextAsync(
        string url,
        IReadOnlyDictionary<string, string>? headers = null,
        CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        Apply(request, headers);
        using var response = await Client.SendAsync(request, ct).ConfigureAwait(false);
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
            throw new HttpRequestException($"HTTP {(int)response.StatusCode} {response.ReasonPhrase} · {url}\n{Truncate(text)}");
        return text;
    }

    public static async Task<string> PostJsonAsync(
        string url,
        string body,
        IReadOnlyDictionary<string, string>? headers = null,
        CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(body, Encoding.UTF8, "application/json"),
        };
        Apply(request, headers);
        using var response = await Client.SendAsync(request, ct).ConfigureAwait(false);
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
            throw new HttpRequestException($"HTTP {(int)response.StatusCode} {response.ReasonPhrase} · {url}\n{Truncate(text)}");
        return text;
    }

    public static Task<string> PutJsonAsync(string url, string body, IReadOnlyDictionary<string, string>? headers = null, CancellationToken ct = default)
        => SendWithBodyAsync(HttpMethod.Put, url, body, headers, ct);

    public static Task<string> DeleteJsonAsync(string url, string? body, IReadOnlyDictionary<string, string>? headers = null, CancellationToken ct = default)
        => SendWithBodyAsync(HttpMethod.Delete, url, body, headers, ct);

    public static async Task<string> SendWithBodyAsync(
        HttpMethod method,
        string url,
        string? body,
        IReadOnlyDictionary<string, string>? headers,
        CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(method, url);
        if (body != null)
            request.Content = new StringContent(body, Encoding.UTF8, "application/json");
        Apply(request, headers);
        using var response = await Client.SendAsync(request, ct).ConfigureAwait(false);
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
        if (!response.IsSuccessStatusCode)
            throw new HttpRequestException($"HTTP {(int)response.StatusCode} {response.ReasonPhrase} · {url}\n{Truncate(text)}");
        return text;
    }

    public static async Task<byte[]> GetBytesAsync(
        string url,
        IReadOnlyDictionary<string, string>? headers = null,
        CancellationToken ct = default)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        Apply(request, headers);
        using var response = await Client.SendAsync(request, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(false);
    }

    /// <summary>下载图片，失败返回 null（列表页不应因海报失败而中断）。</summary>
    public static async Task<byte[]?> TryGetBytesAsync(
        string url,
        IReadOnlyDictionary<string, string>? headers = null,
        CancellationToken ct = default)
    {
        try
        {
            return await GetBytesAsync(url, headers, ct).ConfigureAwait(false);
        }
        catch
        {
            return null;
        }
    }

    private static void Apply(HttpRequestMessage request, IReadOnlyDictionary<string, string>? headers)
    {
        if (headers == null) return;
        foreach (var (key, value) in headers)
        {
            if (string.IsNullOrEmpty(value)) continue;
            if (key.Equals("Content-Type", StringComparison.OrdinalIgnoreCase))
            {
                request.Content?.Headers.TryAddWithoutValidation(key, value);
                continue;
            }
            if (!request.Headers.TryAddWithoutValidation(key, value))
                request.Content?.Headers.TryAddWithoutValidation(key, value);
        }
    }

    private static string Truncate(string text)
        => text.Length <= 300 ? text : text[..300] + "…";
}
