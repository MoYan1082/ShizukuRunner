package com.shizuku.uninstaller;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Service;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.shizuku.uninstaller.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Locale;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class MainActivity extends Activity {

    ActivityMainBinding binding;
    int shizukuStatusTextCurrentTextColor;

    Process p;
    Thread h1, h2, h3;
    boolean br = false;

    //shizuku监听授权结果
    private final Shizuku.OnRequestPermissionResultListener RL = this::onRequestPermissionsResult;


    private void onRequestPermissionsResult(int i, int i1) {
        check();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(binding.getRoot());

        binding.logo.setOnClickListener(this::change);
        binding.shizukuStatus.setOnClickListener(v -> check());
        binding.exec.setOnClickListener(this::exe);

        //设置猫猫图案的长按事件为手动触发悬浮小图标
        binding.logo.setOnLongClickListener(view -> {
            startFloatingIcon();
            return true;
        });

        //shizuku返回授权结果时将执行RL函数
        Shizuku.addRequestPermissionResultListener(RL);

        //m用于保存shizuku状态显示按钮的初始颜色（int类型哦），为的是适配安卓12的莫奈取色，方便以后恢复颜色时用
        shizukuStatusTextCurrentTextColor = binding.shizukuStatus.getCurrentTextColor();

        //检查Shizuku是否运行，并申请Shizuku权限
        check();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        startFloatingIcon();
    }

    private void startFloatingIcon() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先在设置中开启“在其他应用上层显示”权限", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }
        startService(new Intent(this, FloatingService.class));
        moveTaskToBack(true);
    }

    private void check() {

        //本函数用于检查shizuku状态，b代表shizuk是否运行，c代表shizuku是否授权
        boolean shizukuStatus = true;
        boolean shizukuPermission = false;
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
                Shizuku.requestPermission(0);
            else shizukuPermission = true;
        } catch (Exception e) {
            if (checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED)
                shizukuPermission = true;
            if (e.getClass() == IllegalStateException.class) {
                shizukuStatus = false;
                Toast.makeText(this, "Shizuku未运行", Toast.LENGTH_SHORT).show();
            }
        }
        binding.shizukuStatus.setText(shizukuStatus ? "Shizuku\n已运行" : "Shizuku\n未运行");
        binding.shizukuStatus.setTextColor(shizukuStatus ? shizukuStatusTextCurrentTextColor : 0x77ff0000);
        binding.shizukuPermission.setText(shizukuPermission ? "Shizuku\n已授权" : "Shizuku\n未授权");
        binding.shizukuPermission.setTextColor(shizukuPermission ? shizukuStatusTextCurrentTextColor : 0x77ff0000);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(RL);
        br = true;
        new Handler().postDelayed(() -> {
            try {
                if (p != null) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        p.destroyForcibly();
                    } else {
                        p.destroy();
                    }
                }
                if (h1 != null) h1.interrupt();
                if (h2 != null) h2.interrupt();
                if (h3 != null) h3.interrupt();
            } catch (Exception ignored) {
            }
        }, 1000);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        //在点击返回键时直接退出APP，因为APP比较轻量，没必要双击返回退出或者设置什么退出限制
        finish();
    }

    public void change(View view) {
        //单击猫猫头像的点击事件，让命令输入框可见。

        flipAnimation(view);
        binding.execCommandRoot.setVisibility(View.VISIBLE);
        binding.commandEdit.setEnabled(true);
        binding.commandEdit.requestFocus();
        binding.commandEdit.postDelayed(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(binding.commandEdit, 0), 200);
        binding.commandEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                exe(v);
            }
            return false;
        });
        binding.commandEdit.setOnKeyListener((view2, i, keyEvent) -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN)
                exe(view2);
            return false;
        });

        view.setOnClickListener(view3 -> change(view3));
    }


    private void flipAnimation(View view) {
        ObjectAnimator a2 = ObjectAnimator.ofFloat(view, "rotationY", 90f, 0f);
        a2.setDuration(300);
        a2.start();

    }


    public void exe(View view) {
        if (binding.commandEdit.getText().length() > 0) {
            if (h1 != null && h1.isAlive()) {
                Toast.makeText(this, "上一个命令还在执行中", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.t1.setText("执行中...");
            binding.t2.setText("");
            final String cmd = binding.commandEdit.getText().toString();
            h1 = new Thread(() -> ShizukuExec(cmd));
            h1.start();
        }
    }

    public void ShizukuExec(String cmd) {
        try {
            long time = System.currentTimeMillis();
            p = newProcess(new String[]{"sh"});
            if (p == null) {
                runOnUiThread(() -> binding.t1.setText("Shizuku进程创建失败"));
                return;
            }
            OutputStream out = p.getOutputStream();
            out.write((cmd + "\nexit\n").getBytes());
            out.flush();
            out.close();

            h2 = new Thread(() -> {
                try {
                    BufferedReader mReader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String inline;
                    while ((inline = mReader.readLine()) != null) {
                        if (binding.t2.length() > 2000 || br) break;
                        String finalInline = inline;
                        runOnUiThread(() -> {
                            binding.t2.append(finalInline);
                            binding.t2.append("\n");
                        });
                    }
                    mReader.close();
                } catch (Exception ignored) {
                }
            });
            h2.start();

            h3 = new Thread(() -> {
                try {
                    BufferedReader mReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                    String inline;
                    while ((inline = mReader.readLine()) != null) {
                        if (binding.t2.length() > 2000 || br) break;
                        SpannableString ss = new SpannableString(inline + "\n");
                        ss.setSpan(new ForegroundColorSpan(Color.RED), 0, ss.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        runOnUiThread(() -> binding.t2.append(ss));
                    }
                    mReader.close();
                } catch (Exception ignored) {
                }
            });
            h3.start();

            p.waitFor();
            String exitValue = String.valueOf(p.exitValue());
            binding.t1.post(() -> binding.t1.setText(
                    String.format(Locale.getDefault(), "返回值：%s\n执行用时：%.2f秒",
                            exitValue, (System.currentTimeMillis() - time) / 1000f)));
        } catch (Exception ignored) {
        }
    }

    private static ShizukuRemoteProcess newProcess(String[] cmd) {
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            return (ShizukuRemoteProcess) newProcess.invoke(null, cmd, null, null);
        } catch (Exception e) {
            Log.e("MainActivity", "Error creating new process", e);
            return null;
        }
    }
}
