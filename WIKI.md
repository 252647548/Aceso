##为什么要使用Aceso?
 



|| Aceso | QZone | Tinker | AndFix|
|:-----:|:----:|:----:|:----:|:----:|
| add    | √    | √    |√ |   √   |     
| change    | √    |  √   |√ |   √   | 
| remove   | √    |   √  |x|   -   | 



##工程介绍
| 名称 | 描述 |  
| :-----: | :----: | 
| aceso-build | gradle插件工程，包含了AcesoHost和AcesoFix两个插件<br>AcesoHost在宿主工程中使用，用来对宿主工程的代码进行插桩<br>AcesoFix在Fix工程中使用，用来生成patch包 <br>该工程包含了Aceso的主要逻辑|
| aceso-lib | android library工程，包括了运行时加载patch包的逻辑 |
| aceso-demo | demo工程，你可以编译、运行这个工程的app |
| aceso-demo-fix | demo工程的Fix工程，用来生成demo工程的patch包 |



##输出文件详解
Aceso的输出目录为build/intermediates/aceso。

| 名称 | 相对路径 | 描述 |  
| :-----: | :----: | :----: | 
| allClassesJar | all-classes/${buildType+Flavor}/all-classes.jar | 包含了宿主工程的所有未被处理（即未混淆、未插桩）的类，该文件需要在宿主工程发布版本后需要保存下来 | 
| instrumentJar | instrument/${buildType+Flavor}/instrument.jar | 包含了宿主工程所有被处理过（混淆、插桩）的类，该文件需要在宿主工程发布版本后需要保存下来 | 
| acesoMapping | aceso-mapping/${buildType+Flavor}/aceso-mapping.txt | 记录了所有被插桩的类与方法对应的int型整数，该文件需要在宿主工程发布版本后需要保存下来 | 


##Gradle Dsl

| 参数 | 默认值 | 描述 | 使用插件 | 
| :-----: | :----: | :----: | :----: |
| logLevel | 2 | log等级，范围：0-3，3输出的内容最全 | AcesoHost,AcesoFix |
| enable | true | 是否开启aceso | AcesoHost,AcesoFix |  
| acesoMapping | module根目录/aceso-mapping.txt| 输出文件详解中的acesoMapping的路径。一般来说，你可以在发布版本时将该文件保存到maven上，然后在发布hotfix时，将该文件下载下来，例如说下载到/Downloads/aceso-mapping.txt.则acesoMapping=/Downloads/aceso-mapping.txt。在AcesoFix插件中，该属性是必须的| AcesoHost | 
| instrumentDebug | false | debug编译时是否开启Aceso插件 | AcesoHost | 
| blackListPath | module根目录/aceso-blacklist.txt | 黑名单文件的路径，在黑名单中的所有类都不会进行插桩，也就是说不能被修复。 | AcesoHost |  
| instrumentJar | module根目录/instrument.jar | 输出文件详解中的instrumentJar的路径| AcesoFix | 
| methodLevelFix | true | 是否开启方法级修复，如果为true，则只修复Fix工程中加了@FixMtd注解的方法，否则Fix工程中所有类的所有方法都会被修复。为true时，需要在被修的方法前加入@FixMtd的注解 | AcesoFix | 

##blackList
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

##Issues

- 包大小会增加，以蘑菇街app为例，由45M增加到了46.5M   
- 安装后的oat文件大小会增加，以蘑菇街app为例，在5.x上安装后大小由xx -xx
- 要修的类如果是由jdk7/jdk8编译的，则Fix工程编译时的环境必须是jdk7/jdk8


##TODO
- 支持构造函数的热修复
- 支持新增方法、字段
- 抹平jdk7/8的兼容性问题