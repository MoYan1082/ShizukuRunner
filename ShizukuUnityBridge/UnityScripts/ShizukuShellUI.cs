using UnityEngine;

/// <summary>
/// 挂到名为 ShellReceiver 的 GameObject 上。
/// 接收 Shizuku 执行结果，并通过 OnGUI 显示到屏幕；可输入命令并点击执行。
/// </summary>
public class ShizukuShellUI : MonoBehaviour
{
    [Header("显示区域")]
    [Tooltip("距离屏幕边缘的边距")]
    public int margin = 32;
    [Tooltip("结果区域高度占比 (0~1)")]
    [Range(0.2f, 0.9f)]
    public float resultAreaHeight = 0.5f;
    [Tooltip("字体大小")]
    public int fontSize = 20;
    [Tooltip("执行按钮宽度")]
    public int buttonWidth = 160;
    [Tooltip("执行按钮高度")]
    public int buttonHeight = 56;

    private string _lastJson = "";
    private string _stdout = "";
    private string _stderr = "";
    private int _exitCode = -1;
    private string _lastCmd = "";
    private string _inputCmd = "pm list packages";
    private Vector2 _scrollPos;
    private bool _received;

    private void OnGUI()
    {
        GUI.skin.label.fontSize = fontSize;
        GUI.skin.textField.fontSize = fontSize;
        GUI.skin.button.fontSize = fontSize;
        GUI.skin.textArea.fontSize = fontSize;

        float w = Screen.width - 2f * margin;
        float y = margin;
        int btnW = Mathf.Max(buttonWidth, 80);
        int btnH = Mathf.Max(buttonHeight, 36);

        // 输入行
        int labelW = 80;
        GUI.Label(new Rect(margin, y, labelW, btnH), "命令:");
        _inputCmd = GUI.TextField(new Rect(margin + labelW + 8, y, w - labelW - 8 - btnW - 8, btnH), _inputCmd);
        if (GUI.Button(new Rect(margin + w - btnW, y, btnW, btnH), "执行"))
            RunShell(_inputCmd);
        y += btnH + 8;

        // 结果区域（只读 Label，避免点击弹出键盘）
        float resultH = (Screen.height - margin - y) * resultAreaHeight;
        string display = _received
            ? $"命令: {_lastCmd}\n退出码: {_exitCode}\n\n--- stdout ---\n{_stdout}\n\n--- stderr ---\n{_stderr}"
            : "执行命令后结果会显示在这里";

        GUIStyle labelStyle = new GUIStyle(GUI.skin.label);
        labelStyle.wordWrap = true;
        labelStyle.alignment = TextAnchor.UpperLeft;

        float contentH = Mathf.Max(resultH, labelStyle.CalcHeight(new GUIContent(display), w - 24) + 20);
        _scrollPos = GUI.BeginScrollView(
            new Rect(margin, y, w, resultH),
            _scrollPos,
            new Rect(0, 0, w - 24, contentH)
        );
        GUI.Label(new Rect(0, 0, w - 24, contentH), display, labelStyle);
        GUI.EndScrollView();
    }

    /// <summary>
    /// 由 Android 桥接通过 UnitySendMessage 调用，方法名必须为 OnShellResult。
    /// </summary>
    public void OnShellResult(string json)
    {
        _lastJson = json ?? "";
        _received = true;

        // 简单解析 JSON（不依赖第三方库）
        _lastCmd = GetJsonString(json, "cmd");
        _exitCode = GetJsonInt(json, "exitCode");
        _stdout = GetJsonString(json, "stdout");
        _stderr = GetJsonString(json, "stderr");

        _stdout = Unescape(_stdout);
        _stderr = Unescape(_stderr);
    }

    /// <summary>
    /// 发起执行命令（可从其他脚本调用，或通过 GUI 按钮）。
    /// </summary>
    public void RunShell(string cmd)
    {
        if (string.IsNullOrWhiteSpace(cmd)) return;

#if UNITY_ANDROID && !UNITY_EDITOR
        try
        {
            string err = "";
            using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            using (var bridge = new AndroidJavaClass("com.example.shizukuunitybridge.UnityShellBridge"))
            {
                err = bridge.CallStatic<string>("runShell", activity, cmd) ?? "";
            }
            _lastCmd = cmd;
            if (err.Length > 0)
            {
                _stdout = "";
                _stderr = err;
                _exitCode = -1;
                _received = true;
            }
            else
            {
                _received = false;
                _stdout = _stderr = "";
                _exitCode = -1;
            }
        }
        catch (System.Exception e)
        {
            _stdout = "";
            _stderr = "调用异常: " + e.Message;
            _exitCode = -1;
            _received = true;
        }
#else
        _stdout = "[仅 Android 运行时可用] " + cmd;
        _stderr = "";
        _exitCode = 0;
        _received = true;
#endif
    }

    private static int GetJsonInt(string json, string key)
    {
        string s = GetJsonString(json, key);
        return int.TryParse(s, out int v) ? v : -1;
    }

    private static string GetJsonString(string json, string key)
    {
        if (string.IsNullOrEmpty(json)) return "";
        string search = "\"" + key + "\":";
        int start = json.IndexOf(search);
        if (start < 0) return "";
        start += search.Length;
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
