package com.example.shizukuunitybridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

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

    public static final String TAG = "UnityShellBridge";

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
            // 必须 RECEIVER_EXPORTED：结果广播由 ShizukuRunner(CommandService) 发出，跨应用才能收到
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(sReceiver, new IntentFilter(ACTION_SHELL_RESULT), Context.RECEIVER_EXPORTED);
            } else {
                activity.registerReceiver(sReceiver, new IntentFilter(ACTION_SHELL_RESULT));
            }
            sRegistered = true;
        } catch (Throwable t) {
            Log.e(TAG, "registerReceiver failed", t);
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
     *
     * @param context 建议传 Activity（如 Unity 的 currentActivity）
     * @param cmd     要执行的命令
     * @return 空字符串表示成功；非空为错误信息，可直接在 Unity 里显示
     */
    public static String runShell(Context context, String cmd) {
        Log.i(TAG, "runShell: " + cmd);
        if (context == null || cmd == null || cmd.isEmpty())
            return "参数错误: context 或 cmd 为空";
        // 1. 检查 ShizukuRunner 是否已安装
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_SERVICE_PKG, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + SHIZUKU_SERVICE_PKG);
            return "未安装 ShizukuRunner，请先安装包名为 " + SHIZUKU_SERVICE_PKG + " 的应用";
        }
        // 2. 显式启动服务
        Intent intent = new Intent();
        intent.setClassName(SHIZUKU_SERVICE_PKG, SHIZUKU_SERVICE_CLS);
        intent.putExtra("cmd", cmd);
        intent.putExtra("callback_action", ACTION_SHELL_RESULT);
        intent.putExtra("callback_package", context.getPackageName());
        ComponentName cn = null;
        try {
            cn = context.startService(intent);
        } catch (SecurityException e) {
            Log.e(TAG, "startService SecurityException", e);
            return "无权限启动服务: " + e.getMessage();
        } catch (Exception e) {
            Log.e(TAG, "startService Exception", e);
            return "启动异常: " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        if (cn != null) {
            Log.i(TAG, "startService ok: " + cn);
            return "";
        }
        // 常见原因：应用被「强制停止」后处于 stopped 状态，无法被其他应用拉起
        Log.w(TAG, "startService returned null");
        return "服务未启动(startService 返回 null)。请确认：1) 已从桌面打开过 ShizukuRunner，且未在「设置-应用」里点「强制停止」；2) 设备上安装的 ShizukuRunner 包含 CommandService 且为 exported。";
    }

    private static class ShellResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String cmd = intent.getStringExtra("cmd");
            String stdout = intent.getStringExtra("stdout");
            String stderr = intent.getStringExtra("stderr");
            int exitCode = intent.getIntExtra("exitCode", -1);

            Log.i(TAG, "onReceive:" + cmd);

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
