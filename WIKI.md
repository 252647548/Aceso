Aceso
====================================

## 为什么要使用Aceso?

最主要的2个好处是能够兼容Android 7.0，并无需重启即生效。

蘑菇街刚开始是采用的Q-Zone方案的，线上良好运行了半年左右，一切的改变发生在Android 7.0的问世，由于Android N采用的是JIT+AOT profile的混合编译方式，Tinker跟Q-Zone方案跪了，Google为了解决ART上首次安装APK就做OAT带来的性能和IO占用的问题，采用了混合编译的折中方案，在APK首次运行时采用解释模式，然后运行期去收集”热代码“，通过JobScheduler对“热代码”做OAT，同时生成一个叫做Base.art的索引文件，里面保存了已经编译好的”热代码“在OAT中的索引，在应用启动的时候预先加载这些“热点类”到ClassLoader的dexcache缓存中，由于提前将这些类加载到了cache中，这样会导致这些“热点类”的方法永远没办法被替换。下图从Tinker那边拷贝过来的，AndroidN混合编译就像一个小的生态：

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/af1518dd0792640517638e3190b89d23/197546643098.png)

后来跟Tinker负责人进行探讨，找到了完美的解决方案：

1. 为了绕开混合编译带来的影响，我们抛弃原来的PathClassLoader，转而创建一个新的ClassLoader(AndroidNClassLoader)来加载绝大部分类，PathClassLoader将只加载ProxyApp，然后在ProxyApp中使用AndroidNClassLoader反射调用到realApp，这样从realApp开始，所有被realApp直接或者间接调用到的类都将被AndroidNClassLoader加载了，然后将HotFix的Dex插入AndroidNClassLoader的dexElements[]即可；末了，将AndroidNClassLoader注入PackageInfo中，这样系统以后也会用AndroidNClassLoader来进行类加载了，主要是Android四大组件的加载。
2. 为了继续利用系统本身已经生成好的OAT文件，防止重新生成一份OAT文件来耗磁盘，在AndroidNClassLoader中需要重新创建DexFile, 并调用makePathElements方法将老的Dex跟OAT文件的路径作为参数传入，这样就能够复用以前的OAT文件了。

当时天真的以为Game Over了，直到Tinker负责人告知又有新问题了，原来Android 7.0上inline进行了很大的优化，请参考Tinker的文章：
https://github.com/WeMobileDev/article/blob/master/ART%E4%B8%8B%E7%9A%84%E6%96%B9%E6%B3%95%E5%86%85%E8%81%94%E7%AD%96%E7%95%A5%E5%8F%8A%E5%85%B6%E5%AF%B9Android%E7%83%AD%E4%BF%AE%E5%A4%8D%E6%96%B9%E6%A1%88%E7%9A%84%E5%BD%B1%E5%93%8D%E5%88%86%E6%9E%90.md

好了，Q-Zone方案跪，Tinker因为采用分平台合成也跪，AndFix方案也跪了，因为大家都没办法修复被inline掉的代码；为了解决Inline问题，Tinker是从原来IO占用比较小的分平台合成转而做了IO很耗的全量合成的方案，AndFix方面根本没办法修复，手淘那边是采用了动态部署方案兜底，即7.0上采用动态部署（类似Tinker做全量合成）, 而Q-Zone方案没有办法能够解决。当我们正在犹豫是否要接入Tinker/Amigo这种重量级产品之际，用Tinker/Amigo做热修复简直就是大炮打蚊子，Dex，资源都做全量合成，IO占用非常耗，Tinker/Amigo比较适合做功能升级, 其实蘑菇街这边后来是接了Tinker来做类似Atlas的动态部署的事情，这个时候美团的Robust方案的文章面世了（基于InstantRun方案的改造），并得知已经在美团十几个App上运行了半年之久，这里非常感谢Tinker负责人张绍文同学的帮助跟美团那边的技术分享，说实话不想重复造轮子，但是Robust开源不知道什么时候，而且是否靠谱心里不是很有底，于是我们花了一个近月时间自研了跟Robust原理一样的Aceso方案来验证其可靠性。这里取名Aceso--希腊神话中的健康女神。

此方案有2个比较明显好处，一个是下载即生效，另外一个是兼容性好，由于是使用ASM注入的方式，有希望1，2年内都不用再跟在Google后面做兼容性适配了。  

以下总结当前主流的HotFix方案对比:
 
![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/c3009d018d3d8df0f56e205cbc7679d3/195365402460.png)

原生InstantRun方案的基本原理
---

InstantRun在打HotFix包时，会在每个类塞入一个change变量。开发过程中，如果这个类一直没有变化，那么change一直为空。如果类发生变化，change将被塞入patch的类实例。举个例子：

```
public class HotFixActivity{
...

	public static boolean setText(Context mContext, String mString){
		//HotFix中将下面的Log打开
		//Log.e("HotFixActivity", "Hello World");
		return true;
	}
}
```

通过ASM注入后的HotFixActivity.java

```
public class HotFixActivity{
	public static volatile transient /* synthetic */ IncrementalChange $change;
...

	public static boolean setText(Context mContext, String mString){
		IncrementalChange incrementalChange = $change;
		if (incrementalChange == null){
			return true;
		}
		return ((Boolean ) incrementalChange.access$dispatch(
		"setText.(Landroid/context/Context;Ljava/lang/String;)Z",
		new Object[]{mContext, mString})).booleanValue();
	}
}
```

再看下patch的实现类:


```
public class HotFixActivity$override implements IncrementalChange{
...

	public static boolean setText(Context mContext, String mString){
		Log.e("HotFixActivity", "Hello World");
		return true;
	}
}

```

InstantRun方案其实原理很简单，关键是填坑，为了暴露这些坑，尽量发现足够多的应用场景，我们采用暴力测试的方法，一次HotFix了900多个类，包括图片库，网络库这种比较频繁调用的库，后来又陆陆续续适配了蘑菇街其他组件，并把暴力测试的这些类灰度到线上，直到发现的坑都踩完填完为止。 另外，使用原生InstantRun方案会带来2个问题，一个是包会增大很多，蘑菇街apk从42M增加到了60M；另外一个是由于采用反射导致性能影响比较大，这对于非常热的方法来说就是噩梦。   
我们通过不停优化，最后包大小只增加了1.5M，性能方面，同样暴力热修复了900多个类在线上线下并没有发现性能问题，事实上，InstantRun机制本身需要的反射被大部分优化掉后，理论上不会有性能瓶颈了。

Aceso的结果
---

```
1. 本地暴力测试(修复1500+类,覆盖4.x-7.x机型)，
2. Testin和Monkey通过，线上灰度3w用户（暴力修复900+常用类),蘑菇街线上发了20多个HotFix，没看到问题。
3. 包大小增加1.5M，线上线下未发现性能问题。
```

原生InstantRun方案的那些坑
---

首先是包大小问题，主要包括三个方面

```
1. InstantRun插桩增加的包大小,
2. 为支持super.method()增加的包大小，
3. 为兼容super(), this()增加的包大小。
```

最后我们将原InstantRun方案需要增加的18M降低到了1.5M。

InstantRun插桩增加的包大小解决方案
---
InstantRun方案为每个类增加一个静态变量，并且会在每个函数前插桩，增加了指令数和字符串，其中，多了字符串是主要原因。

```
public class JustTest{
	public static IncrementalChange $change;
	
	public void test(){
		if($change != null) {
			$change.access$dispatch("test()V", new Object[0]);
		} else {
			Log.i("Aceso", "JustTest");
		}
	}
}	
```

为了解决字符串的问题，此处借用了Proguard的思想，在编译期间将所有的方法都映射为一个int值，然后将映射关系保存在一个mapping文件中。

```
public class JustTest{
	public static IncrementalChange $change;
	
	public void test(){
		if($change != null) {
			$change.access$dispatch(100, new Object[0]);
		} else {
			Log.i("Aceso", "JustTest");
		}
	}
}	

```
为支持super.method()增加的包大小
---

InstantRun是在一个叫override类去调用被修类的各种方法。但遇到如super.method的情况，它是处理不了的，因为没有办法调用另外一个对象的super.xxx。
为了解决这个问题，InstantRun在原类中增加一个超大的代理函数。这个函数中有一个switch，switch的每个case对应了一个父类方法，也就是说这个类的父类有多少个方法，那么这个switch就有多少个case。然后根据传入的参数来决定要调用父类的哪个方法。

```
	public void test(){
		super.toString();
	}
```

```
public static java.lang.Object access$super(JustTest justTest, String mtdSig, Object...){
	switch (mtdSig.hashCode()){
		case 11111:
			return super.toString();
		case 22222:
			return super.hashCode();
		case 33333:
			return super.finalize();
		...
	}
}
```

我们借鉴了Robust的方案，将需要调用super.method()的地方通过字节码处理工具将作用对象换为原对象，并且将override的父类改为被HotFix类的父类，就能够调用原有对象的super方法。例如 假设JustTest类的父类是activity，那在override类也要继承activity，并且将调用super.toString的地方，将作用对象换为JustTest的实例。这里的justTest.super()是伪代码，代表了它字节码层面是这个含义。

```
public class JustTest$override extends Activity implements IncrementalChange {
	public static void test(JustTest justTest){
		justTest.super().toString();
	}
}

```

为兼容super(), this()增加的包大小
---

InstantRun为了在override类中调用原有类的super()方法和 this()方法，会在每个类中增加一个构造方法。然后在override类中会生成两个方法args 和bodys ，arg返回一个字符串，代表要调用哪个this或super方法，body中则是原来构造函数中的除this或super调用的其他逻辑。当一个类的构造方法被HotFix时，在它的构造方法中会先调用override类的args，将args返回的字符串传递给新生成的构造方法，在新生成的构造方法里会根据字符串决定要调用哪个函数。之后再调用body方法。

![](http://s17.mogucdn.com/new1/v1/bmisc/fcf84d1aa86a2b0181e3c88cba7e6cd2/197784876835.png)

为了兼容this调用，InstantRun会让每个类额外的增加一个构造方法。我们最后选择放弃对构造函数的支持，因为使用InstantRun的增加一个构造函数的方案会使得包大小额外增加1m，另外最重要的构造函数被修的概率很低，咨询了下负责发HotFix的同学，以前从来没有修过构造函数。所以权衡之下，我们决定放弃对构造函数的支持。

HotFix类的按需加载问题
---

InstantRun原有机制是可能导致被HotFix类提前加载到虚拟机中的，这会导致一些问题（比如说一个类的静态方法中有用到mgapplicatiion类的sApp这个静态变量，如果我们加载HotFix的时候，sApp还没赋值，那就会NPE）。

```
public class IMGLiveService {
	static String SCHEME = MGApp.sApp.getAppScheme();
	...
}
```

这里我们是将HotFix的粒度从Class级别降为了Method级别，并要求写HotFix的同学在写HotFix代码时，在需要修复的Method方法上申明一个annotation。在Patch被安装的时候，会将需要Patch的Method还有Class都保存在一个PatchBuffer中，原Dex中每个方法在被调用到的时候都会去数组中查看下Class是否PatchBuffer,如果有，那么继续看当前Method是否在PatchBuffer中，这样就做到了按需加载Patch的Method。

将HotFix的粒度从Class级别降为了Method级别有三个好处：

```
1. 如果HotFix出了问题，能尽量降低其带来的负面影响, 
2. 减少HotFix带来的性能消耗,
3. 减少即时生效时的时间窗口大小。
```

性能问题
---

因为要在override类的对象中去访问原有类的属性，所以必定会涉及到访问权限问题。
InstantRun会在编译期间将：

```
1. 所有的protected以及默认访问权限的方法和字段改为public。可以不用反射，直接访问。
2. 对于private的方法，InstantRun会将被HotFix类的所有private方法拷贝到override类中，当override中的方法需要调用被hotfix类的私有方法时，直接调用override类的私有方法就好了。但是对于framework层的protected方法和字段（如activity的protected方法），就只能通过反射去调用（因为我们不能修改framework层的访问权限）。对于所有的private字段也是通过反射去访问。

```

因此，存在一定的性能问题，我们通过两个手段来优化这个问题：

1. 用一个全局的Lru Cache存储查找反射过的字段与方法，这样尽量保证反射只被调用一次，   
2. 全局将private字段改为public，做到对private字段都以public方式直接访问；
3. 最后剩下Framework中的private方法还有JNI的private方法需要反射调用，这些对性能会有影响。

由于篇幅有限，还有不少坑这里不一一列举了。


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