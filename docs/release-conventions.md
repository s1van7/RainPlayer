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

## 发布后必做的两步

CI 只负责 Android 的 6 个 APK，并且会用 `generate_release_notes` 自动生成一段正文。
所以 tag 推送、CI 跑完之后还要补两件事：

```bash
# 1) 改标题 + 换成手写说明（CI 自动生成的正文会被覆盖掉，这是预期行为）
gh release edit v1.2.0 --title "RainPlayer v1.2.0" \
  --notes-file release/RELEASE-NOTES-v1.2.0.md

# 2) 补上 Windows 两个包
gh release upload v1.2.0 \
  release/RainPlayer-1.2.0-win-x64.zip \
  release/RainPlayer-1.2.0-win-x64-setup.exe --clobber
```

注意资产文件名必须是纯 ASCII。GitHub 会把中文文件名（如 `安装说明.txt`）
改写成 `default.txt`，所以上传前先复制成 `INSTALL-zh.txt` 之类的名字。
