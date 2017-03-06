#BleRx
##配置
####RxAndroidBle  
```java
compile "com.polidea.rxandroidble:rxandroidble:1.1.0"
```
####lambda语法配置
* 根目录build.gradle
```java
    dependencies {
    classpath 'me.tatarka:gradle-retrolambda:3.1.0'
    }
```
* 项目build.gradle
```java
android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
    allprojects {
        repositories {
            maven {
                url "https://jitpack.io"
            }
        }
        apply plugin: 'me.tatarka.retrolambda'
    }
}
```

##使用
直接复制BleRx到项目中使用
###初始化
在Application的onCreate()中
```java
BleRx.init(this);
```
###扫描
```java
Subscription subscribe = BleRx.getInstance()
                        .scan()
                        .subscribe(result -> {
                            //扫描结果
                        }, e -> {

                        });
subscribe.unsubscribe();//取消扫描
```
###连接
```java
BleRx.getInstance().connect("ff:ff:ff:ff:ff:ff", ff);
//mac地址，重连时间
```
###断开
```java
BleRx.getInstance().disConnect();
```
###读
```java
BleRx.getInstance().read(UUID).subscribe(bytes->{
                    
});
```
###写
```java
BleRx.getInstance().write(UUID,bytes);
```
###notify
```java
Subscription subscribe = BleRx.getInstance().notifyData(UUID).subscribe(bytes -> {
                    
});
subscribe.unsubscribe();//取消notify
```
###rssi
```java
BleRx.getInstance().readRssi().subscribe(rssi->{

});
```
