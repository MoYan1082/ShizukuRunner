using UnityEngine;

/// <summary>
/// Perfetto 录制控制：提供「开始」「停止」「检查」按钮，通过 Shizuku 执行 shell。
/// 开始：先 kill 所有 perfetto，再启动录制；启动后约 2 秒自动检查进程是否起来。
/// 检查结果在本脚本下方显示（需挂到名为 ShellReceiver 的 GameObject 上才能收到回调）。
/// </summary>
public class PerfettoController : MonoBehaviour
{
    [Header("界面")]
    public int margin = 32;
    public int buttonWidth = 160;
    public int buttonHeight = 56;
    public int fontSize = 20;
    [Tooltip("开始录制后多少秒自动执行一次进程检查")]
    public float checkDelayAfterStart = 2f;

    /// <summary>仅查询 perfetto 进程，不 kill</summary>
    private const string CMD_CHECK_PERFETTO = "ps -A | grep perfetto";

    /// <summary>用 ps -A | grep perfetto 查出并 kill 所有 perfetto 进程（与操作 1、3 一致）</summary>
    private const string CMD_KILL_PERFETTO =
        "ps -A | grep perfetto | grep -v grep | while read a b c; do [ -n \"$b\" ] && kill \"$b\" 2>/dev/null; done";

    /// <summary>启动 perfetto：先 kill 再后台执行 cat config | perfetto（操作 2）</summary>
    private const string CMD_START_PERFETTO =
        "ps -A | grep perfetto | grep -v grep | while read a b c; do [ -n \"$b\" ] && kill \"$b\" 2>/dev/null; done; sleep 1; cat /data/local/tmp/config.pbtx | perfetto --txt -c - -o /data/misc/perfetto-traces/trace.perfetto-trace &";

    private string _status = "";
    private float _statusTime;
    private bool _pendingCheck;
    private string _lastCheckOutput = "";
    private string _lastCheckStderr = "";
    private Vector2 _checkScroll;

    /// <summary>由 UnityShellBridge 通过 UnitySendMessage 调用，需挂到 ShellReceiver 上才能收到</summary>
    public void OnShellResult(string json)
    {
        if (!_pendingCheck || string.IsNullOrEmpty(json)) return;
        _pendingCheck = false;
        _lastCheckOutput = GetJsonString(json, "stdout") ?? "";
        _lastCheckStderr = GetJsonString(json, "stderr") ?? "";
        _lastCheckOutput = Unescape(_lastCheckOutput);
        _lastCheckStderr = Unescape(_lastCheckStderr);
    }

    private void OnGUI()
    {
        GUI.skin.button.fontSize = fontSize;
        GUI.skin.label.fontSize = fontSize;
        GUI.skin.textArea.fontSize = fontSize;

        float x = margin;
        float y = margin;

        if (GUI.Button(new Rect(x, y, buttonWidth, buttonHeight), "开始"))
        {
            RunShell(CMD_START_PERFETTO);
            _status = "已发送：先 kill perfetto，再启动录制；" + checkDelayAfterStart + " 秒后自动检查进程";
            _statusTime = Time.time;
            Invoke(nameof(DoDelayedCheck), checkDelayAfterStart);
        }
        x += buttonWidth + 12;

        if (GUI.Button(new Rect(x, y, buttonWidth, buttonHeight), "停止"))
        {
            RunShell(CMD_KILL_PERFETTO);
            _status = "已发送：kill perfetto";
            _statusTime = Time.time;
        }
        x += buttonWidth + 12;

        if (GUI.Button(new Rect(x, y, buttonWidth, buttonHeight), "检查"))
        {
            _pendingCheck = true;
            RunShell(CMD_CHECK_PERFETTO);
            _status = "已发送：ps -A | grep perfetto，结果将显示在下方";
            _statusTime = Time.time;
        }

        y += buttonHeight + 12;
        float contentWidth = Screen.width - 2f * margin - 24f; // 留出滚动条宽度
        GUIStyle labelStyle = new GUIStyle(GUI.skin.label);
        labelStyle.wordWrap = true;

        float statusH = labelStyle.CalcHeight(new GUIContent(_status), contentWidth + 24f);
        if (!string.IsNullOrEmpty(_status) && Time.time - _statusTime < 5f)
            GUI.Label(new Rect(margin, y, Screen.width - 2 * margin, statusH), _status, labelStyle);
        y += statusH + 8;

        // 检查结果区域（只读 Label，避免点击弹出键盘）
        string titleText = "进程检查结果 (ps -A | grep perfetto):";
        float titleH = labelStyle.CalcHeight(new GUIContent(titleText), contentWidth + 24f);
        GUI.Label(new Rect(margin, y, Screen.width - 2 * margin, titleH), titleText, labelStyle);
        y += titleH + 4;
        float areaH = 140;
        string display = _pendingCheck ? "检查中…" : (string.IsNullOrEmpty(_lastCheckOutput) && string.IsNullOrEmpty(_lastCheckStderr)
            ? "（未检测到 perfetto 进程或尚未检查）"
            : _lastCheckOutput + (string.IsNullOrEmpty(_lastCheckStderr) ? "" : "\n--- stderr ---\n" + _lastCheckStderr));
        float contentH = Mathf.Max(areaH, labelStyle.CalcHeight(new GUIContent(display), contentWidth) + 20);
        _checkScroll = GUI.BeginScrollView(new Rect(margin, y, Screen.width - 2 * margin, areaH), _checkScroll, new Rect(0, 0, contentWidth + 24, contentH));
        GUI.Label(new Rect(0, 0, contentWidth, contentH), display, labelStyle);
        GUI.EndScrollView();
    }

    private void DoDelayedCheck()
    {
        _pendingCheck = true;
        RunShell(CMD_CHECK_PERFETTO);
    }

    private static void RunShell(string cmd)
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        try
        {
            using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            using (var bridge = new AndroidJavaClass("com.example.shizukuunitybridge.UnityShellBridge"))
            {
                string err = bridge.CallStatic<string>("runShell", activity, cmd) ?? "";
                if (err.Length > 0)
                    Debug.LogWarning("[PerfettoController] runShell error: " + err);
            }
        }
        catch (System.Exception e)
        {
            Debug.LogException(e);
        }
#else
        Debug.Log("[PerfettoController] 仅 Android 运行时执行: " + cmd);
#endif
    }

    private static string GetJsonString(string json, string key)
    {
        if (string.IsNullOrEmpty(json)) return "";
        string search = "\"" + key + "\":";
        int start = json.IndexOf(search);
        if (start < 0) return "";
        start += search.Length;
        if (start >= json.Length) return "";
        if (json[start] == '"')
        {
            start++;
            int end = start;
            while (end < json.Length)
            {
                if (json[end] == '\\') { end += 2; continue; }
                if (json[end] == '"') break;
                end++;
            }
            return json.Substring(start, end - start);
        }
        int numEnd = start;
        while (numEnd < json.Length && (char.IsDigit(json[numEnd]) || json[numEnd] == '-')) numEnd++;
        return json.Substring(start, numEnd - start);
    }

    private static string Unescape(string s)
    {
        if (string.IsNullOrEmpty(s)) return s;
        return s.Replace("\\n", "\n").Replace("\\r", "\r").Replace("\\\"", "\"").Replace("\\\\", "\\");
    }
}
