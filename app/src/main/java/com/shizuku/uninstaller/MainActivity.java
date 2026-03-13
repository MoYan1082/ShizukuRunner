package com.shizuku.uninstaller;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Service;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.shizuku.uninstaller.databinding.ActivityMainBinding;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    ActivityMainBinding binding;
    int shizukuStatusTextCurrentTextColor;

    //shizuku监听授权结果
    private final Shizuku.OnRequestPermissionResultListener RL = this::onRequestPermissionsResult;


    private void onRequestPermissionsResult(int i, int i1) {
        check();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // this.setFinishOnTouchOutside(false);

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
    public boolean onTouchEvent(MotionEvent event) {
        startFloatingIcon();
        return true;
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
        //在APP退出时，取消注册Shizuku授权结果监听，这是Shizuku的要求
        Shizuku.removeRequestPermissionResultListener(RL);
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

        //EditText右边的执行按钮，点击后的事件
        if (binding.commandEdit.getText().length() > 0)
            startActivity(new Intent(this, ExecActivity.class).putExtra("content", binding.commandEdit.getText().toString()));
    }
}
