##Aceso

Aceso是基于Instant Run Hot Swap的Android热修复方案，使用它你能在不用重新发布版本的情况下对线上app的bug进行修复。

##Features

- 支持4.x-7.x机型
- 方案类似于aop，基本不会有兼容性问题
- 安装补丁后不用重启，实时生效
- 支持方法级别的修复
- 支持新增类


##Limitations

- 暂不支持static函数、构造函数的修复 
- 暂不支持修改类结构，如修改方法签名、新增/删除方法、新增/删除字段
- 要修的类如果是由jdk7/jdk8编译的，则fix工程编译时的环境必须是jdk7/jdk8

##TODO
- 支持构造函数的热修复
- 支持新增方法、字段
- 抹平jdk7/8的兼容性问题

##Issues

- 包大小会增加，以蘑菇街app为例，由45M增加到了46.5M   
- 如果修的方法中有一些[特殊的调用]()，会采取反射的形式调用。

##Usage
1.在最外层project的build.gradle中加入以下代码：

```groovy
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
         classpath 'com.mogujie.gradle:aceso:0.0.2.7-LOCAL'
    }
}
```

2.在module的build.gradle中加入以下代码：

```
apply plugin: 'Aceso'

dependencies {
    compile 'com.mogujie:aceso-android-lib:0.0.1-LOCAL'
}

```

如果你要在debug版本中使用aceso,需要再加入如下代码：

```
Aceso {
    disableInstrumentDebug = false
}
```

3.在App的Application中加入如下代码：

```
```

4.每次**发布版本**时需要将module目录下的build/intermediates/aceso文件夹保存下来
 

##Generate Patch
1.你需要创建一个额外fix的工程（可参考demo）

2.在最外层project的build.gradle中加入以下代码：

```groovy
buildscript {
    repositories {
        jcenter()
    }

    dependencies {
         classpath 'com.mogujie.gradle:aceso:0.0.2.7-LOCAL'
    }
}
```

3.在module的build.gradle中加入以下代码：

```
apply plugin: 'AcesoFix'

Aceso {
    //methodLevelFix为true时，是方法级的fix，也就是只对特定的方法进行修复，需要在修的方法前加@FixMtd的注解。否则是对整个类的所有方法进行修复。
    methodLevelFix = true
    modifiedJar = ‘宿主工程生成的modified.jar的路径’
    acesoMapping = ‘宿主工程生成的aceso-mapping.txt的路径’
    }

```
4.将宿主工程生成的allClassesJar以provided的形式提供给 fix工程。如：
```
dependencies {
    provided com.mogujie.demo:hotfix:xxx
}
```


5.将需要修改的方法所属的类拷贝到fix工程，并且保证包名不变。比如你需要修改的类为com.mogujie.aceso.demo.MainActivity，则你需要保证在fix工程中MainActivity的全限定名也为com.mogujie.aceso.demo.MainActivity。

6.进行你需要的修改

7.如果你将methodLevelFix设置为true，则需要对你修改的方法前加入@FixMtd的注解(com.android.annotations.FixMtd)

8.执行gradle/.gradlew acesoRelease(或acesoDebug)命令生成对应的补丁包。补丁包在/aceso-fix/app/build/outputs/apk目录下

9.将补丁包下发到手机。


##Demo
1.编译并安装aceso-demo，点击test按钮，显示的是not fix! 

2.在aceso-fix中执行gradle/.gradlew acesoRelease(或acesoDebug)

3.将产生的apk包push到手机端的/sdcard/fix.apk

4.在手机上点击fix按钮

5.点击test按钮，显示的是has been fixed !

##Contributing

##Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Robust](http://tech.meituan.com/android_robust.html)


##License
