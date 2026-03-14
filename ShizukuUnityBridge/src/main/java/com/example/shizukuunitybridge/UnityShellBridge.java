package com.example.shizukuunitybridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import com.unity3d.player.UnityPlayer;

/**
 * 供 Unity 通过 ShizukuRunner 执行 shell 并接收结果。
 * 结果通过广播回传，由本类接收后 UnitySendMessage 到 Unity。
 */
public class UnityShellBridge {

    /** 与 ShizukuRunner CommandService 约定的结果广播 action */
    public static final String ACTION_SHELL_RESULT = "com.example.shizukuunitybridge.ACTION_SHELL_RESULT";

    private static final String SHIZUKU_SERVICE_PKG = "com.shizuku.uninstaller";
    private static final String SHIZUKU_SERVICE_CLS = "com.shizuku.uninstaller.CommandService";

    /** Unity 接收结果的 GameObject 名 */
    private static final String UNITY_GO_NAME = "ShellReceiver";
    /** Unity 接收结果的方法名 */
    private static final String UNITY_METHOD_NAME = "OnShellResult";

    private static Activity sActivity;
    private static ShellResultReceiver sReceiver;
    private static boolean sRegistered;

    /**
     * 在 Activity.onCreate 中调用，注册结果广播接收器。
     */
    public static void register(Activity activity) {
        if (activity == null || sRegistered) return;
        sActivity = activity;
        sReceiver = new ShellResultReceiver();
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(sReceiver, new IntentFilter(ACTION_SHELL_RESULT), Activity.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(sReceiver, new IntentFilter(ACTION_SHELL_RESULT));
            }
            sRegistered = true;
        } catch (Throwable t) {
            // ignore
        }
    }

    /**
     * 在 Activity.onDestroy 中调用，注销接收器。
     */
    public static void unregister() {
        if (!sRegistered || sActivity == null || sReceiver == null) return;
        try {
            sActivity.unregisterReceiver(sReceiver);
        } catch (Throwable ignored) {
        }
        sRegistered = false;
        sActivity = null;
        sReceiver = null;
    }

    /**
     * 执行 shell 命令，结果通过广播回传到本模块的 Receiver，再 UnitySendMessage 到 Unity。
     * Unity 端需存在名为 ShellReceiver 的 GameObject，并挂有实现 OnShellResult(string json) 的脚本。
     *
     * @param context 建议传 Activity（如 Unity 的 currentActivity）
     * @param cmd     要执行的命令
     */
    public static void runShell(Context context, String cmd) {
        if (context == null || cmd == null || cmd.isEmpty()) return;
        Intent intent = new Intent();
        intent.setClassName(SHIZUKU_SERVICE_PKG, SHIZUKU_SERVICE_CLS);
        intent.putExtra("cmd", cmd);
        intent.putExtra("callback_action", ACTION_SHELL_RESULT);
        intent.putExtra("callback_package", context.getPackageName());
        context.startService(intent);
    }

    private static class ShellResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String cmd = intent.getStringExtra("cmd");
            String stdout = intent.getStringExtra("stdout");
            String stderr = intent.getStringExtra("stderr");
            int exitCode = intent.getIntExtra("exitCode", -1);

            String payload = buildPayload(cmd, stdout, stderr, exitCode);
            UnityPlayer.UnitySendMessage(UNITY_GO_NAME, UNITY_METHOD_NAME, payload);
        }

        private static String buildPayload(String cmd, String stdout, String stderr, int exitCode) {
            return "{"
                    + "\"cmd\":\"" + escape(cmd) + "\","
                    + "\"exitCode\":" + exitCode + ","
                    + "\"stdout\":\"" + escape(stdout) + "\","
                    + "\"stderr\":\"" + escape(stderr) + "\""
                    + "}";
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }
}
