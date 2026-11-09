namespace RainPlayer.Core;

/// <summary>
/// 内置的第三方服务凭据。
///
/// TMDB 的 v3 API Key 免费申请，这里内置一把公共 Key 让用户开箱即用。
/// 设置页仍可替换成自己的 Key，清空则关闭 TMDB 评分。
/// Key 被吊销或配额打满时，换掉这里的常量即可，不需要动别的代码。
/// </summary>
public static class Keys
{
    /// <summary>TMDB v3 API Key（/search/movie 与 /search/tv）。</summary>
    public const string TmdbApiKey = "6f7320d3e5cae417f9a271146c538251";
}
