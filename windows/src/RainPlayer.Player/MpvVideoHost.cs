using System.Runtime.InteropServices;
using System.Windows.Interop;

namespace RainPlayer.Player;

/// <summary>
/// 承载 mpv 视频输出的子窗口。
///
/// mpv 的 --wid 模式需要一个真实的 HWND，这里用 HwndHost 在 WPF 里开一个子窗口，
/// 背景刷成黑色避免起播瞬间白闪。
/// 注意：HwndHost 会遮挡同层 WPF 元素（airspace 问题），
/// 因此播放控制条必须放在 Popup（独立窗口）里。
/// </summary>
public sealed class MpvVideoHost : HwndHost
{
    private const int WsChild = 0x40000000;
    private const int WsVisible = 0x10000000;
    private const int WsClipSiblings = 0x04000000;
    private const int WsClipChildren = 0x02000000;
    private const int CsHRedraw = 0x0002;
    private const int CsVRedraw = 0x0001;
    private const int BlackBrush = 4;

    private static readonly IntPtr HInstance = GetModuleHandle(null);
    private static readonly string ClassName = "RainPlayerVideoHost_" + Guid.NewGuid().ToString("N")[..8];
    private static WndProcDelegate? _wndProc;   // 保持引用，防止委托被 GC
    private static bool _classRegistered;

    /// <summary>hwnd → 控件实例，供静态 WndProc 转发鼠标消息。</summary>
    private static readonly Dictionary<IntPtr, MpvVideoHost> Instances = new();

    private const int WmMouseMove = 0x0200;
    private const int WmLButtonDown = 0x0201;
    private const int WmLButtonUp = 0x0202;
    private const int WmLButtonDblClk = 0x0203;

    private IntPtr _hwnd;
    private bool _classReady;

    /// <summary>子窗口句柄创建完成。mpv 必须在这个句柄就绪后再初始化。</summary>
    public event Action<IntPtr>? HandleReady;

    /// <summary>视频区域被点击（用于切换播放/暂停）。</summary>
    public event Action? VideoClicked;

    /// <summary>视频区域被双击（用于切换全屏）。</summary>
    public event Action? VideoDoubleClicked;

    /// <summary>鼠标在视频区域移动（用于唤出控制条）。</summary>
    public event Action? MouseMoved;

    public IntPtr VideoHandle => _hwnd;

    protected override HandleRef BuildWindowCore(HandleRef hwndParent)
    {
        EnsureClassRegistered();

        _hwnd = CreateWindowEx(
            0,
            ClassName,
            "",
            WsChild | WsVisible | WsClipSiblings | WsClipChildren,
            0, 0,
            Math.Max((int)ActualWidth, 16),
            Math.Max((int)ActualHeight, 16),
            hwndParent.Handle,
            IntPtr.Zero,
            HInstance,
            IntPtr.Zero);

        lock (Instances) Instances[_hwnd] = this;

        HandleReady?.Invoke(_hwnd);
        return new HandleRef(this, _hwnd);
    }

    protected override void DestroyWindowCore(HandleRef hwnd)
    {
        lock (Instances) Instances.Remove(hwnd.Handle);
        if (hwnd.Handle != IntPtr.Zero) DestroyWindow(hwnd.Handle);
        _hwnd = IntPtr.Zero;
    }

    /// <summary>
    /// 视频窗口是独立 HWND，WPF 收不到它的鼠标消息，
    /// 这里在窗口过程里转发，让上层能做点击暂停、双击全屏。
    /// </summary>
    private static IntPtr WindowProc(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam)
    {
        MpvVideoHost? host = null;
        lock (Instances)
        {
            if (Instances.TryGetValue(hwnd, out var found)) host = found;
        }

        if (host != null)
        {
            switch (msg)
            {
                case WmMouseMove:
                    host.MouseMoved?.Invoke();
                    break;
                case WmLButtonDown:
                    host.VideoClicked?.Invoke();
                    break;
                case WmLButtonDblClk:
                    host.VideoDoubleClicked?.Invoke();
                    break;
            }
        }

        return DefWindowProc(hwnd, msg, wParam, lParam);
    }

    private void EnsureClassRegistered()
    {
        if (_classReady) return;

        lock (typeof(MpvVideoHost))
        {
            if (_classRegistered)
            {
                _classReady = true;
                return;
            }

            _wndProc = WindowProc;

            var windowClass = new WndClassEx
            {
                cbSize = (uint)Marshal.SizeOf<WndClassEx>(),
                style = CsHRedraw | CsVRedraw,
                lpfnWndProc = Marshal.GetFunctionPointerForDelegate(_wndProc),
                hInstance = HInstance,
                hbrBackground = GetStockObject(BlackBrush),
                lpszClassName = ClassName,
            };

            var atom = RegisterClassEx(ref windowClass);
            if (atom == 0)
            {
                var error = Marshal.GetLastWin32Error();
                throw new InvalidOperationException($"视频窗口类注册失败（错误 {error}）");
            }

            _classRegistered = true;
            _classReady = true;
        }
    }

    private delegate IntPtr WndProcDelegate(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct WndClassEx
    {
        public uint cbSize;
        public uint style;
        public IntPtr lpfnWndProc;
        public int cbClsExtra;
        public int cbWndExtra;
        public IntPtr hInstance;
        public IntPtr hIcon;
        public IntPtr hCursor;
        public IntPtr hbrBackground;
        [MarshalAs(UnmanagedType.LPWStr)] public string? lpszMenuName;
        [MarshalAs(UnmanagedType.LPWStr)] public string lpszClassName;
        public IntPtr hIconSm;
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern ushort RegisterClassEx(ref WndClassEx windowClass);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateWindowEx(
        int exStyle, string className, string windowName, int style,
        int x, int y, int width, int height,
        IntPtr parent, IntPtr menu, IntPtr instance, IntPtr param);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyWindow(IntPtr hwnd);

    [DllImport("user32.dll")]
    private static extern IntPtr DefWindowProc(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam);

    [DllImport("gdi32.dll")]
    private static extern IntPtr GetStockObject(int index);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr GetModuleHandle(string? moduleName);
}
