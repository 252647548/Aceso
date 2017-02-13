Aceso
====================================

## 为什么要使用Aceso?


此方案有2个比较明显好处，一个是下载即生效，另外一个是兼容性好，由于是使用ASM注入的方式，有希望1，2年内都不用再跟在Google后面做兼容性适配了。  

以下总结当前主流的HotFix方案对比:
 
![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/c3009d018d3d8df0f56e205cbc7679d3/195365402460.png)


## 工程介绍


| 名称 | 描述 |  
| ----- | ---- | 
| aceso-build | gradle插件工程，包含了AcesoHost和AcesoFix两个插件<br>AcesoHost在宿主工程中使用，用来对宿主工程的代码进行插桩<br>AcesoFix在Fix工程中使用，用来生成patch包 <br>该工程包含了Aceso的主要逻辑|
| aceso-lib | android library工程，包括了运行时加载patch包的逻辑 |
| aceso-demo | demo工程，你可以运行该工程以查看效果 |
| aceso-demo-fix | demo工程的Fix工程，用来生成demo工程的patch包 |


## aceso-build模块图

![](http://s16.mogucdn.com/p1/170210/idid_ifrweolcgi2dgzlgmqzdambqhayde_662x562.png)

aceso-build分为两层：Gradle层和ASM层。

Gradle层的作用是干涉正常编译流程，在合适的时机拿到工程的所有class文件，然后传递给ASM层进行处理。

- AcesoHostPlugin: 宿主工程使用的gradle插件

- AcesoFixPlugin: Fix工程使用的gradle插件

- HookDexTransform: 该transform会hook掉android gradle插件中的DexTransform。将输入的class文件进行插桩处理后，再做为Dextransform的输入传入。

- HookWrapper: 将transform传入的参数进行一些封装，然后调用asm层的对应方法


ASM层的作用是利用[ASM](http://asm.ow2.org/)对class文件进行字节码处理。

- IncremetSupportVisitor: 被Gradle层的AcesoHotPlugin间接调用，对类进行插桩，从而使得类能够被hotfix。  

- IncremetChangeVisitor: 被Gradle层的AcesoFixPlugin间接调用，生成patch文件。



## 输出文件详解

Aceso的输出目录为build/intermediates/aceso。

| 名称 | 相对路径 | 描述 |  
| ----- | ---- | ---- | 
| allClassesJar | all-classes/${buildType+Flavor}/all-classes.jar | 包含了宿主工程的所有未被处理（即未混淆、未插桩）<br/>的类,该文件需要在宿主工程发布版本后需要保存下来 | 
| instrumentJar | instrument/${buildType+Flavor}/instrument.jar | 包含了宿主工程所有被处理过（混淆、插桩）的类,<br/>该文件需要在宿主工程发布版本后需要保存下来 | 
| acesoMapping | aceso-mapping/${buildType+Flavor}/aceso-mapping.txt | 记录了所有被插桩的类与方法对应的int型整数,<br/>该文件需要在宿主工程发布版本后需要保存下来 | 


## Aceso修复流程

1.在每次发布版本后，需要将allClassesJar，instrumentJar，acesoMapping，保存下来。

2.假如你需要修复的版本是1.0.0。如果你要发布的是1.0.0的第一个hotfix，则在Fix工程中切出一个单独的分支（假设分支名为fix_1.0.0）。否则将Fix工程切换到1.0.0版本的对应分支：fix_1.0.0。（该步骤非必需，但是**强烈建议**）

3.将需要修复的类拷贝到Fix工程的**对应目录**下，做相应修改，打包。
 
其实，patch包是可以不用创建一个额外的Fix工程，直接在宿主工程中生成的。Aceso选择新建一个额外的工程的原因有两点：

- Fix工程环境干净，避免因宿主工程环境不同导致未知问题

- 要修复的类一目了然，方便日后查看

- 针对同一个版本，如果需要发多个hotfix，可以直接在Fix工程中的对应分支上修改，方便快捷

4.将patch包下发到手机
 
 
举个例子，在发布完1.0.0版本后，你发现A类有问题，需要修复它。

这时候你需要在Fix工程中切出一个单独的分支，假设分支名为fix_1.0.0。然后将A类拷贝进去，修改bug，打出patch包，下发到手机。

之后你又发现B类也需要修复。那你应该在Fix工程的fix_1.0.0的分支上把B类也拷贝进去，修改bug，打出一个新的patch包（包含A和B两个类），再下发到手机。
（因为Aceso一次只加载一个patch包,所以你必须保证最后一个patch包含了**之前patch所有要修复的类**）
 
 


## Gradle Dsl

| 参数 | 默认值 | 描述 | 使用插件 | 
| ----- | ---- | ---- | ---- |
| logLevel | 2 | log等级，范围：0-3，3输出的内容最全 | AcesoHost,AcesoFix |
| enable | true | 是否开启aceso | AcesoHost,AcesoFix |  
| acesoMapping | module根目录/aceso-mapping.txt| 输出文件详解中的acesoMapping的路径。一般来说，你可以在发布版本时将该文件保存到maven上，然后在发布hotfix时，将该文件下载下来，例如说下载到/Downloads/aceso-mapping.txt.则acesoMapping=/Downloads/aceso-mapping.txt<br/>在AcesoFix插件中，该属性是必须的| AcesoHost,AcesoFix | 
| instrumentDebug | false | 宿主工程debug编译时是否开启插桩功能 | AcesoHost | 
| blackListPath | module根目录/aceso-blacklist.txt | 黑名单文件的路径，在黑名单中的所有类都不会进行插桩，也就是说不能被修复。 | AcesoHost |  
| instrumentJar | module根目录/instrument.jar | 输出文件详解中的instrumentJar的路径| AcesoFix | 
| methodLevelFix | true | 是否开启方法级修复<br/>True则只修复Fix工程中加了@FixMtd注解的方法<br/>否则Fix工程中所有类的所有方法都会被修复<br/>为true时，需要在被修的方法前加入@FixMtd的注解 | AcesoFix | 

## Blacklist
可以通过Gradle Dsl中提到的blackListPath指定黑名单。黑名单中的所有类都不能被fix。
黑名单格式如下：

```
#以'#'开头的行是注释，不会被读入到黑名单
#一行写一条规则

#过滤包名为com.android下的所有类
com/android/
#过滤com.mogujie.demo.Test这个类
com/mogujie/demo/Test.class
```

你可以将第三方库的所有类加入到黑名单，以减少aceso插桩增加的包大小。

## Issues

- 包大小会增加，以蘑菇街app为例，由45M增加到了46.5M   
- app安装后占用磁盘空间会增加
- 要修的类如果是由jdk7/jdk8编译的，则Fix工程编译时的环境必须是jdk7/jdk8


## TODO
- 支持构造函数的热修复
- 减少因Aceso增加的包大小
- 抹平jdk7/8的兼容性问题