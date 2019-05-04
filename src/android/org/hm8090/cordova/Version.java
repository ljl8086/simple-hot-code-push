package org.hm8090.cordova;

public class Version {
    private Long nativeInterface;
    private Long version;
    private String update;
    private String assertTarget;
    private String assertTargetMd5;
    private String nativeTarget;
    private String nativeTargetMd5;

    public Long getNativeInterface() {
        return nativeInterface;
    }

    public void setNativeInterface(Long nativeInterface) {
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
}
