using System.Runtime.InteropServices;

namespace RainPlayer.Player;

/// <summary>libmpv 的 C API 声明。对应 mpv/client.h。</summary>
internal static class MpvNative
{
    private const string Lib = "libmpv-2.dll";

    // ---------------------------------------------------------- 生命周期

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern IntPtr mpv_create();

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_initialize(IntPtr ctx);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern void mpv_terminate_destroy(IntPtr ctx);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern IntPtr mpv_error_string(int error);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern void mpv_free(IntPtr data);

    // ---------------------------------------------------------- 选项与属性

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_set_option_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string value);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_set_option(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        int format, ref long data);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_set_property_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string value);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_set_property(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        int format, ref double data);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_set_property(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        int format, ref long data);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern IntPtr mpv_get_property_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_get_property(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        int format, ref double data);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_get_property(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name,
        int format, ref long data);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_observe_property(IntPtr ctx, ulong replyUserData,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string name, int format);

    // ---------------------------------------------------------- 命令与事件

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_command(IntPtr ctx, IntPtr args);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_command_string(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string args);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern IntPtr mpv_wait_event(IntPtr ctx, double timeout);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern void mpv_wakeup(IntPtr ctx);

    [DllImport(Lib, CallingConvention = CallingConvention.Cdecl)]
    internal static extern int mpv_request_log_messages(IntPtr ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string level);

    // ---------------------------------------------------------- 常量

    internal const int FormatNone = 0;
    internal const int FormatString = 1;
    internal const int FormatFlag = 3;
    internal const int FormatInt64 = 4;
    internal const int FormatDouble = 5;

    internal const int EventNone = 0;
    internal const int EventShutdown = 1;
    internal const int EventLogMessage = 2;
    internal const int EventStartFile = 6;
    internal const int EventEndFile = 7;
    internal const int EventFileLoaded = 8;
    internal const int EventVideoReconfig = 17;
    internal const int EventAudioReconfig = 18;
    internal const int EventSeek = 20;
    internal const int EventPlaybackRestart = 21;
    internal const int EventPropertyChange = 22;

    /// <summary>mpv_event 结构（顺序与 mpv/client.h 一致）。</summary>
    [StructLayout(LayoutKind.Sequential)]
    internal struct MpvEvent
    {
        public int EventId;
        public int Error;
        public ulong ReplyUserData;
        public IntPtr Data;
    }

    /// <summary>mpv_event_end_file 结构。</summary>
    [StructLayout(LayoutKind.Sequential)]
    internal struct MpvEventEndFile
    {
        public int Reason;
        public int Error;
        public long PlaylistEntryId;
        public long PlaylistInsertId;
        public int PlaylistInsertNumEntries;
    }

    /// <summary>mpv_event_property 结构。</summary>
    [StructLayout(LayoutKind.Sequential)]
    internal struct MpvEventProperty
    {
        public IntPtr Name;
        public int Format;
        public IntPtr Data;
    }

    internal static string ErrorText(int code)
    {
        try
        {
            var ptr = mpv_error_string(code);
            return ptr == IntPtr.Zero ? $"错误码 {code}" : Marshal.PtrToStringUTF8(ptr) ?? $"错误码 {code}";
        }
        catch
        {
            return $"错误码 {code}";
        }
    }

    /// <summary>把字符串数组转成 mpv_command 需要的 char** 并执行。</summary>
    internal static int Command(IntPtr ctx, params string[] args)
    {
        var pointers = new IntPtr[args.Length + 1];
        var handles = new IntPtr[args.Length];
        try
        {
            for (var i = 0; i < args.Length; i++)
            {
                handles[i] = Marshal.StringToCoTaskMemUTF8(args[i]);
                pointers[i] = handles[i];
            }
            pointers[args.Length] = IntPtr.Zero;

            var array = Marshal.AllocHGlobal(IntPtr.Size * pointers.Length);
            try
            {
                Marshal.Copy(pointers, 0, array, pointers.Length);
                return mpv_command(ctx, array);
            }
            finally
            {
                Marshal.FreeHGlobal(array);
            }
        }
        finally
        {
            foreach (var handle in handles)
            {
                if (handle != IntPtr.Zero) Marshal.FreeCoTaskMem(handle);
            }
        }
    }
}
