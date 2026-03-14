package com.shizuku.uninstaller;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class CommandService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String cmd = intent != null ? intent.getStringExtra("cmd") : null;
        final String callbackAction = intent != null ? intent.getStringExtra("callback_action") : null;
        final String callbackPackage = intent != null ? intent.getStringExtra("callback_package") : null;
        if (cmd != null && !cmd.isEmpty()) {
            new Thread(() -> runWithShizuku(cmd, callbackAction, callbackPackage)).start();
        }
        return START_NOT_STICKY;
    }

    private void runWithShizuku(String cmd, String callbackAction, String callbackPackage) {
        try {
            Log.e("CommandService", "runWithShizuku:" + cmd);

            ShizukuRemoteProcess p = newProcess(new String[]{"sh"});
            if (p == null) {
                Log.e("CommandService", "Shizuku process is null");
                return;
            }
            OutputStream out = p.getOutputStream();
            out.write((cmd + "\nexit\n").getBytes());
            out.flush();
            out.close();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            BufferedReader rOut = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = rOut.readLine()) != null) {
                stdout.append(line).append('\n');
                Log.d("CommandService", line);
            }
            rOut.close();

            BufferedReader rErr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = rErr.readLine()) != null) {
                stderr.append(line).append('\n');
                Log.e("CommandService", line);
            }
            rErr.close();

            p.waitFor();
            int exitCode = p.exitValue();
            Log.d("CommandService", "exitCode=" + exitCode);

            if (callbackAction != null && !callbackAction.isEmpty()) {
                Intent resp = new Intent(callbackAction);
                if (callbackPackage != null && !callbackPackage.isEmpty()) {
                    resp.setPackage(callbackPackage);
                }
                resp.putExtra("cmd", cmd);
                resp.putExtra("stdout", stdout.toString());
                resp.putExtra("stderr", stderr.toString());
                resp.putExtra("exitCode", exitCode);
                sendBroadcast(resp);
            }
        } catch (Exception e) {
            Log.e("CommandService", "exec error", e);
        }
    }

    private static ShizukuRemoteProcess newProcess(String[] cmd) {
        try {
            Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            return (ShizukuRemoteProcess) m.invoke(null, cmd, null, null);
        } catch (Exception e) {
            Log.e("CommandService", "newProcess error", e);
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

