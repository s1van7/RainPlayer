# 发布约定

## Release 标题

格式固定为：`RainPlayer v<版本号>`

- ✅ `RainPlayer v1.0.1`
- ❌ `RainPlayer v1.0.1 · Android + Windows`（不要带平台后缀）
- ❌ `RainPlayer v1.0.1 · 签名证书统一`（不要带副标题）

平台信息写在正文里，不写进标题。

## Release 正文

正文可以用 `## Android / Android TV`、`## Windows` 这样的分节来区分各端资产，
但标题本身只保留产品名和版本号。

## CI 行为

`.github/workflows/android.yml` 里的 `softprops/action-gh-release` 没有设置 `name`，
因此 tag 触发时标题默认就是 tag 名（`v1.0.1`）。发布后如需调整，只用
`gh release edit --title` 改成 `RainPlayer v1.0.1` 即可，不要添加额外修饰。
