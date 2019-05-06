package org.hm8090.cordova;

import org.json.JSONException;
import org.json.JSONObject;

public class Version {
    private Integer nativeInterface;
    private Long version;
    private String update;
    private String assertTarget;
    private String assertTargetMd5;
    private String nativeTarget;
    private String nativeTargetMd5;

    public Integer getNativeInterface() {
        return nativeInterface;
    }

    public void setNativeInterface(Integer nativeInterface) {
        this.nativeInterface = nativeInterface;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getUpdate() {
        return update;
    }

    public void setUpdate(String update) {
        this.update = update;
    }

    public String getAssertTargetMd5() {
        return assertTargetMd5;
    }

    public void setAssertTargetMd5(String assertTargetMd5) {
        this.assertTargetMd5 = assertTargetMd5;
    }

    public String getNativeTargetMd5() {
        return nativeTargetMd5;
    }

    public void setNativeTargetMd5(String nativeTargetMd5) {
        this.nativeTargetMd5 = nativeTargetMd5;
    }

    public String getAssertTarget() {
        return assertTarget;
    }

    public void setAssertTarget(String assertTarget) {
        this.assertTarget = assertTarget;
    }

    public String getNativeTarget() {
        return nativeTarget;
    }

    public void setNativeTarget(String nativeTarget) {
        this.nativeTarget = nativeTarget;
    }

    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        obj.optLong("nativeInterface", this.nativeInterface);
        obj.optLong("version", this.version);
        obj.optString("update", this.update);
        obj.optString("assertTarget", this.assertTarget);
        obj.optString("assertTargetMd5", this.assertTargetMd5);
        obj.optString("nativeTarget", this.nativeTarget);
        obj.optString("nativeTargetMd5", this.nativeTargetMd5);
        return obj;
    }

    public static Version fromString(String jsonStr) throws JSONException {
        JSONObject json = new JSONObject(jsonStr);

        Version version = new Version();
        version.setVersion(json.getLong("version"));
        version.setNativeInterface(json.optInt("native_interface"));
        version.setUpdate(json.optString("update"));
        version.setAssertTarget(json.optString("assert_target"));
        version.setAssertTargetMd5(json.optString("assert_target_md5"));
        version.setNativeTarget(json.optString("native_target"));
        version.setNativeTargetMd5(json.optString("native_target_md5"));
        return version;
    }
}
