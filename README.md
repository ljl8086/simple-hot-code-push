# 说明
可以使用本插件来达到cordova 项目热更新功能

# 使用方法
## 安装插件
$ ionic cordova plugin add simple-hot-code-push
## 修改配置文件config.xml，增加版本控制请求url
<preference name="config_file" value="http://x.x.x.x/version" />
## 版本控制请求url，返回内容如下
```
{
"version": 6,
"native_interface": 2,
"update": "now",
"assert_target":"target.zip",
"assert_target_md5": "da21ea445cbf283e126ca2aab7cf5bb2",
"native_target":"http://192.168.0.219/test3.apk",
"native_target_md5":""
}
```

# 使用限制
* 目前只支持android
