package org.hm8090.cordova;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
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
    private String baseUrl;
    private String configFile;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("showToast")) {
            String message = args.getString(0);
            this.showToast(message, callbackContext);
            return true;
        }
        return false;
    }

    private void showToast(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            Toast.makeText(this.cordova.getActivity(), message, Toast.LENGTH_SHORT).show();
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        handler = new Handler();
        downloadManager = (DownloadManager)webView.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        this.context = webView.getContext();
        this.pref = this.context.getSharedPreferences("data", Context.MODE_PRIVATE);
        this.baseUrl = super.preferences.getString("base_url",null);
        this.configFile = super.preferences.getString("config_file",null);
    }

    @Override
    public void onStart() {
        super.onStart();
        webView.handleStop();
        setServerBasePath(getBaseDir(null));

        try {
            new Thread(()->{
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(this.configFile);

                    conn = (HttpURLConnection)url.openConnection();
                    int responseCode = conn.getResponseCode();
                    if(responseCode==200) {
                        String response = getStringFromInputStream(conn.getInputStream());
                        Version version = json2Entity(response);

                        if( version.getVersion() > getCurrentVersion() ) {
                            log("有新版本更新");
                            this.show("发现新版本，正在更新中……", 3);

                            if (version.getAssertTarget()==null || version.getAssertTarget().trim().length()==0) return;

                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(this.baseUrl+"/"+ version.getAssertTarget()));
                            request.setDestinationInExternalFilesDir(context,null, version.getAssertTarget());
                            mTaskId = downloadManager.enqueue(request);

                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    checkDownloadStatus(version);
                                }
                            }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                        }else {
                            log("已经是最新版本，无需更新");
                        }

                    }
                }catch (Exception e) {
                    e.printStackTrace();
                    log(e.getMessage());
                }finally {
                    if(conn!=null) conn.disconnect();
                }
            }).start();

        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Version json2Entity(String jsonStr) throws JSONException {
        JSONObject json = new JSONObject(jsonStr);

        Version version = new Version();
        version.setVersion(json.getLong("version"));
        version.setNativeInterface(json.optLong("native_interface"));
        version.setUpdate(json.optString("update"));
        version.setAssertTarget(json.optString("assert_target"));
        version.setAssertTargetMd5(json.optString("assert_target_md5"));
        version.setNativeTarget("native_target");
        version.setNativeTargetMd5("native_target_md5");
        return version;
    }

    private Long getCurrentVersion() {
        return this.pref.getLong("version",0);
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
        if (temp!=null) {
            File www = new File(webView.getContext().getExternalFilesDir(null) + "/www_"+temp);
            if(!www.exists()) www.mkdirs();
            return www.toString();
        }else {
            return null;
        }
    }

    private void checkDownloadStatus(Version version) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(mTaskId);//筛选下载任务，传入任务ID，可变参数
        Cursor c = downloadManager.query(query);
        if (c.moveToFirst()) {
            int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            switch (status) {
                case DownloadManager.STATUS_PAUSED:
                    log(">>>下载暂停");
                case DownloadManager.STATUS_PENDING:
                    log(">>>下载延迟");
                case DownloadManager.STATUS_RUNNING:
                    log(">>>正在下载");
                    break;
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
                            this.show("新版本更新成功", 5);
                        }catch (Exception e) {
                            e.printStackTrace();
                            log("解压更新文件错误");
                            this.show("新版本更新失败，请联系客服", 5);
                        }
                    }else {
                        log("文件下载错误，原始MD5[%s],下载文件MD5[%S]",sourceFileMd5, fileMd5);
                        this.show("新版本更新失败，请联系客服", 5);
                    }
                    break;
                case DownloadManager.STATUS_FAILED:
                    log(">>>下载失败");
                    this.show("新版本下载失败，下次启动时将重新更新", 5);
                    break;
            }
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
                        FileOutputStream fout = new FileOutputStream(path, false);

                        try {
                            for (int c = zin.read(); c != -1; c = zin.read()) {
                                fout.write(c);
                            }
                            zin.closeEntry();
                        } finally {
                            fout.close();
                        }
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
