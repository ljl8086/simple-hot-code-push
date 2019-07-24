package org.hm8090.cordova;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.ionicframework.cordova.webview.IonicWebViewEngine;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * 简单热更新组件.
 */
public class SimpleHotCodePush extends CordovaPlugin {
    private Handler handler;
    private Long mTaskId;
    private DownloadManager downloadManager;
    private Context context;
    private SharedPreferences pref;
    private String configFile;
    private String defaultUrl;
    // 初始资源版本号
    private int version = 0;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action){
            case "getVersion":
                this.getVersion(callbackContext);
            case "getRemoteVersion":
                this.getRemoteVersion(version -> {
                    callbackContext.success(version.toJSONObject().toString());
                }, error -> {
                    callbackContext.error(error);
                });
                break;
        }
        return true;
    }

    /**
     * 获取app版本号。
     * @param callbackContext
     */
    private void getVersion(CallbackContext callbackContext) {
        try {
            int nativeVersion = getNativeVersion();
            long version = getCurrentVersion();
            JSONObject object = new JSONObject();
            object.put("nativeVersion", nativeVersion);
            object.put("version", version);
            callbackContext.success(object);
        }catch (Exception e) {
            e.printStackTrace();
            log("版本号获取失败");
            callbackContext.error("获取版本号失败");
        }
    }

    private int getNativeVersion() {
        try {
            PackageInfo info = context.getApplicationContext().getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        }catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        handler = new Handler();
        downloadManager = (DownloadManager)webView.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        this.context = webView.getContext();
        this.pref = this.context.getSharedPreferences("data", Context.MODE_PRIVATE);
        this.configFile = super.preferences.getString("config_file",null);
	    this.defaultUrl = super.preferences.getString("default_url",null);
	    this.version = super.preferences.getInt("version", 0);
    }

    @Override
    public void onStart() {
        super.onStart();
        webView.handleStop();
        setServerBasePath(getBaseDir(null));

        try {
            getRemoteVersion( version -> {
                if (version.getNativeInterface() > getNativeVersion()) {
                    if(version.getNativeTarget()!=null && version.getNativeTarget().length()>0) {
                        handler.post(()->{
                            AlertDialog.Builder normalDialog = new AlertDialog.Builder(context);
                            normalDialog.setTitle("更新").setMessage("发现新版本，是否现在更新?");
                            normalDialog.setNegativeButton("取消", ((dialog, which) -> {
                                dialog.cancel();
                            }));
                            normalDialog.setPositiveButton("立即更新", (dialog,which) -> {
                                updateNativeApp(version.getNativeTarget());
                                dialog.dismiss();
                            });
                            normalDialog.show();
                        });
                    }else {
                        log("app下载地址有问题");
                    }
                    return;
                }

                if (version.getVersion() > getCurrentVersion()) {
                    updateAssert(version);
                }

            }, error->{
                log(error);
            });

        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 异步获取远程版本号
     * @param success
     * @param error
     */
    private void getRemoteVersion(Event<Version> success, Event<String> error) {
        new Thread(()->{
            HttpURLConnection conn = null;
            try {
                URL url = new URL(this.configFile);

                conn = (HttpURLConnection)url.openConnection();
                int responseCode = conn.getResponseCode();
                if(responseCode==200) {
                    String response = getStringFromInputStream(conn.getInputStream());
                    Version version = Version.fromString(response);
                    success.callback(version);
                }
            }catch (Exception e) {
                e.printStackTrace();
                error.callback(e.getMessage());
            }finally {
                if(conn!=null) conn.disconnect();
            }
        }).start();
    }


    /**
     * 更新H5代码
     * @param version
     */
    private void updateAssert(Version version) {
        if( version.getVersion() > getCurrentVersion() ) {
            log("有新版本更新");
            this.show("发现新版本，正在更新中……", 3);

            if (version.getAssertTarget()==null || version.getAssertTarget().trim().length()==0) return;
            String fileName = version.getAssertTarget().substring(version.getAssertTarget().lastIndexOf("/") + 1);
            String downloadUrl = version.getAssertTarget();
            if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
                downloadUrl = this.defaultUrl + "/" + version.getAssertTarget();
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setDestinationInExternalFilesDir(context,null, fileName);
            mTaskId = downloadManager.enqueue(request);

            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(mTaskId);
                    Cursor c = downloadManager.query(query);
                    if (c.moveToFirst()) {
                        int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        switch (status) {
                            case DownloadManager.STATUS_SUCCESSFUL:
                                String uri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                log(">>>下载完成"+uri);

                                File downloadFile = new File(Uri.parse(uri).getPath());
                                String fileMd5 = getFileMD5(downloadFile);
                                String sourceFileMd5 = version.getAssertTargetMd5();
                                if (fileMd5.equalsIgnoreCase(sourceFileMd5)){
                                    log("下载成功，准备更新");
                                    try {
                                        String desDir = getBaseDir(version.getVersion());
                                        unzip(downloadFile, desDir);
                                        saveVersion(version.getVersion());
                                        setServerBasePath(desDir);
                                        show("新版本更新成功", 5);
                                    }catch (Exception e) {
                                        e.printStackTrace();
                                        log("解压更新文件错误");
                                        show("新版本更新失败，请联系客服", 5);
                                    }
                                }else {
                                    log("文件下载错误，原始MD5[%s],下载文件MD5[%S]",sourceFileMd5, fileMd5);
                                    show("新版本更新失败，请联系客服", 5);
                                }
                                break;
                            case DownloadManager.STATUS_FAILED:
                                log(">>>下载失败");
                                show("新版本下载失败，下次启动时将重新更新", 5);
                                break;
                        }
                    }
                }
            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }else {
            log("已经是最新版本，无需更新");
        }
    }

    /**
     * 更新APP
     * @param url
     */
    private void updateNativeApp(String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        String fileName = url.substring(url.lastIndexOf("/") + 1);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setTitle("下载");
        request.setDescription("正在下载");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        long requestId = downloadManager.enqueue(request);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(requestId);
                Cursor c = downloadManager.query(query);
                if (c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    switch (status) {
                        case DownloadManager.STATUS_SUCCESSFUL:
                            String uri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                            log(">>>下载完成" + uri);

                            intent = new Intent(Intent.ACTION_VIEW);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.setDataAndType(Uri.parse(uri), "application/vnd.android.package-archive");
                            context.startActivity(intent);
                            break;
                    }
                }
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        return;
    }

    private Long getCurrentVersion() {
        return this.pref.getLong("version", this.version);
    }

    private void saveVersion(Long version) {
        SharedPreferences.Editor edit = this.pref.edit();
        edit.putLong("version", version);
        edit.commit();
    }

    private static String getStringFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = -1;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        is.close();
        String state = os.toString();
        os.close();
        return state;
    }


    private String getBaseDir(Long ver) {
        Long temp = ver==null ? getCurrentVersion() : ver;
        if (temp!=null && temp>0) {
            File www = new File(webView.getContext().getExternalFilesDir(null) + "/www_"+temp);
            if(!www.exists()) www.mkdirs();
            return www.toString();
        }else {
            return null;
        }
    }

    private void setServerBasePath(String url) {
        log("导航到此目录：%s", url);
        if (url!=null && url.trim().length()>0) {
            IonicWebViewEngine engine = (IonicWebViewEngine) webView.getEngine();
            engine.setServerBasePath(url);
        }
    }

    private static String getFileMD5(File file) {
        if (!file.isFile()) {
            return null;
        }
        MessageDigest digest = null;
        FileInputStream in = null;
        byte buffer[] = new byte[1024];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            in = new FileInputStream(file);
            while ((len = in.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return bytesToHexString(digest.digest());
    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    private void show(String message, int duration) {
        this.handler.post(()->{
            Toast.makeText(this.cordova.getActivity(), message, duration).show();
        });
    }

    private void log(String s, Object ...args) {
        LOG.i(this.getClass().getName(), s, args);
    }

    private void unzip(File zipFile, String location) throws IOException {
        int BUFFER = 10240;
        try {
            File f = new File(location);
            if (!f.isDirectory()) {
                f.mkdirs();
            }
            ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile));
            try {
                ZipEntry ze = null;
                while ((ze = zin.getNextEntry()) != null) {
                    String path = location + File.separator + ze.getName();

                    if (ze.isDirectory()) {
                        File unzipFile = new File(path);
                        if (!unzipFile.isDirectory()) {
                            unzipFile.mkdirs();
                        }
                    } else {
                        byte data[] = new byte[BUFFER];
                        FileOutputStream fos = new FileOutputStream(path);
                        BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
                        int currentByte;
                        while ((currentByte = zin.read(data, 0, BUFFER)) != -1) {
                            dest.write(data, 0, currentByte);
                        }
                        dest.flush();
                        dest.close();
                        zin.close();
                    }
                }
            } finally {
                zin.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("", "Unzip exception", e);
        }
    }

//    @Override
//    public boolean onOverrideUrlLoading(String url) {
////        return super.onOverrideUrlLoading(url);
//        return true;
//    }
}
