# Unity 端用法（接收 Shizuku 执行结果）

## 1. 场景里添加接收结果的 GameObject

- 在场景中创建一个空物体，命名为 **`ShellReceiver`**（必须与桥接代码中的 `UNITY_GO_NAME` 一致）。
- 在该物体上挂载下面的 C# 脚本。

## 2. 接收结果的脚本示例

```csharp
using UnityEngine;

public class ShellReceiver : MonoBehaviour
{
    // 方法名必须为 OnShellResult，参数为 string（JSON）
    public void OnShellResult(string json)
    {
        Debug.Log("Shizuku result: " + json);
        // 可选：用 JsonUtility 或第三方库解析 json，字段为 cmd, exitCode, stdout, stderr
    }
}
```

## 3. 发起执行并收到结果

在任意脚本中通过 AndroidJavaObject 调用桥接类的静态方法：

```csharp
#if UNITY_ANDROID && !UNITY_EDITOR
using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
using (var bridge = new AndroidJavaClass("com.example.shizukuunitybridge.UnityShellBridge"))
{
    bridge.CallStatic("runShell", activity, "pm list packages");
}
#endif
```

执行完成后，ShizukuRunner 的 CommandService 会发广播，本模块的 `ShellResultReceiver` 收到后调用 `UnitySendMessage("ShellReceiver", "OnShellResult", payload)`，你的 `ShellReceiver.OnShellResult` 就会收到 JSON 字符串。

## 4. JSON 格式

```json
{"cmd":"你的命令","exitCode":0,"stdout":"标准输出内容","stderr":"错误输出内容"}
```

字符串中的换行已转义为 `\n`，可先替换再使用或按需解析。
