蘑菇街Android客户端HotFix探索之路
===

在Dalvik时代，只有Dexposed跟Q-Zone两家的方案，进入ART时代后各种Android热修复方案如雨后春笋般冒出来。

![](http://s17.mogucdn.com/new1/v1/bmisc/a7f74ecf5b7a1d2f34f6c45603e8bebf/197792399230.png)

###Dexposed

Xposed的非root版本，实现本进程的AOP, Dalvik上近乎完美，patch难写些, 但是不支持ART，现在已被AndFix取代。原理是Hook了Dalvik虚拟机的method->nativeFunc指针，如下：
![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/5209af25e692df2fbde98288b056c1cb/197537286235.png)

###Q-Zone

原理是Hook了ClassLoader.pathList.dexElements[]。因为ClassLoader的findClass是通过遍历dexElements[]中的dex来寻找类的。当然为了支持4.x的机型，需要打包的时候插庄。
![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/b3c0a5617ca709b458e5ca38bd3cf04d/197537500451.png)

###Tinker

服务端作dex差量，将差量包下载到客户端，在ART模式的机型上本地跟原apk中的classes.dex作merge，merge成为一个新的merge.dex后将merge.dex插入pathClassLoader的dexElement，原理类同Q-Zone，为了实现差量包的最小化，Tinker自研了DexDiff/DexMerge算法。Tinker还支持资源和So包的更新，原理类同InstantRun，这里不详解。

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/953ebad00001447dd9d8e8936e04eb63/197539013666.png)

###Robust

美团即将开源的Robust方案是个好东西，他是在AOSP的InstantRun方案的基础上进行的极致优化，由于是基于.class级别的AOP方式，兼容性相比其他方案天然有很大的优势，而且下载即生效也是个很大的优势，美中不足的是对包大小和磁盘占用（Android 5.x上OAT文件会比较大）仍然有影响，会虽然相比InstantRun方案已经有了很大的优化。

大体流程如下，在原Dex中对入所有可能需要HotFix的方法注入一段代码，这段代码判断当前Method是否被HotFix了，如果是那么去patch.dex中执行对应的HotFix代码，否则继续执行原来的逻辑。

![](http://s16.mogucdn.com/new1/v1/bmisc/0ce3a221716c1131107e267b358c5a64/197539758467.png)

###AndFix

其原理是替换了dex_cache中的目标ArtMethod，但是由于Android 7.0的Inline的优化，导致AndFix不能支持Android 7.0，阿里那边是采用的动态部署进行Android 7.0上的HotFix的。AndFix的原理后面会继续分析。

###Amigo

Amigo的基本原理是直接下发新的Dex，资源，so包到客户端，然后开启一个新的ClassLoader加载新的Dex，开启一个新的AssetManager加载新的Resources，可谓暴力而简单啊，估计bug不是很多。缺点也是非常明显，流量跟磁盘占用比较大，下发一次HotFix相当于安装了两遍APK。

##蘑菇街HotFix-Q-Zone篇
蘑菇街这边开始做HotFix方案的时候，当时业界只有Q-Zone跟Dexposed方案，后来是采用了Q-Zone的方案的，但是对Q-Zone方案心里不是很有底，所以去看了下ART虚拟机的方法加载机制，从虚拟机层面弄清楚了其可靠性。大致总结如下：

1. Dalvik上解释执行:
	2. field通过field name查找
	3. method通过签名查找
	4. 注入前被加载的类不能被patch
	5. const变量会被优化为常量，不能被patch
	6. 下载patch后下次启动才能生效
7. ART实现AOT优化，
	8. field/部分method通过偏移查找，修改类结构将带来找错或者找不到的问题。

这样，只要限定死不修改类结构，即只修改函数体，那么理论上Q-Zone方案是不会有问题的。

####Method调用类型

为了确认Q-Zone方案在ART上的可靠性，我们研究了所有类型Method的调用方式，如下：
![](http://s16.mogucdn.com/new1/v1/bmisc/9b79cfb92f1341f9d58ab43c4bd36e12/197543305865.png)

####Art运行时的主要数据结构

以下是Art Runtime Native的主要数据结构。
![](http://s16.mogucdn.com/new1/v1/bmisc/0cafcc309fdc970c0caf35eac06009e8/197543434434.png)

没有个Dex都对应一个DexFile/DexCache。
每一个类都对应有一个Class结构，包含加载的ClassLoader，一张IfTable(Interface-table)，vTable(virtual-table)，该Class所有的Field和Method分别保存在ArtField/ArtMethod中。

####method调用过程（狸猫换太子）


一个dexfile中的类如果从来没有被加载过，那么defineClass会为这个dexFile创建一个DexCache，这个DexCache包含一个指针数组resolved\_methods\_，数组的大小就是当前dexFile中所有的method方法数:

```HeapReference<PointerArray> resolved_methods_;```

并且初始化所有的指针都指向了一个叫做Runtime Method的ArtMethod，这个Runtime Method就是个“狸猫”，他只是一个代理，后面会将真正的“太子”方法请回来，好，这个Runtime Method也是一个ArtMethod, 它有三个跳转指针变量，其中最重要的entry\_point\_from\_quick\_compiled\_code\_指向一段汇编指令，最后回调到C函数artQuickResolutionTrampoline()

```void* entry_point_from_quick_compiled_code_;```

如果是同一个dex内部跳转，在类首次被调用时，那么它的函数调用接口最终都走到了这个artQuickResolutionTrampoline()蹦床函数.   
比如类A的fA()调用到了类B的fB()，A.fA()->B.fB()，dex2oat在生成A.fA()的native code时，其调用B.fA()最后就偏移到了Class B所在的dexcache中fB对应的ArtMethod的entry\_point\_from\_quick\_compiled\_code\_地址。dex2oat如何知道fB对应的ArtMethod呢？因为Dex文件里面保存了fB在该Dex中的信息，包括签名和methodId，而dexcache中的ArtMethods数组就是按照这个methodId来对应到各个method的。所以, dex2oat在解析了Dex文件后根据methodId就知道到哪个ArtMethod去调用entry\_point\_from\_quick\_compiled\_code\_了。

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/2c08016b2d56b15768936e2273ea85a0/197544757604.png)

如上图所示, 类初始化的时候，dex\_cache中所有的ArtMethod指针都是“狸猫”代理，在被调用到的时候会通过“蹦床”函数根据偏移找到对应的ArtMethod，然后回填到dex_cache中。而这个偏移是dex2oat过程中生成的。

#### Virtual-Table创建

invoke\_direct, invoke\_static, invoke\_super都是在direct_methods中查找到对应的ArtMethod的。

invoke\_virtual和invoke\_interface指令是通过在virtual\_methods和ifTableArray中查找到对应的ArtMethod的。

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/e131d6b9f38e1355f46fec81ed498ccc/197545718158.png)

创建过程如下：
1. 读取当前类的所有public方法和父类的virtual table，
2. 将父类virtual table的ArtMethod放在当前类的virtual table前面
3. 比较当前类的public 方法的签名，有一样的就覆盖
4. 将剩下的public方法的ARTMethods append到virtual table后面
5. 赋予每个Virtual table中的ArtMethod一个method_id，用来标记在vitual Table中的偏移

#### IfTable创建

跟virtual table的创建类似，所不同的是不仅仅IfTable相同的需要覆盖，有相同签名的ArtMethod也需要覆盖。如下：

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/0cb30f2b62f7b5df17965de6df044b1b/197545817296.png)

#### Filed访问

1. Constant变量，在编译为.class的时候就会被优化为常量。
2. Instant和static变量，dex2oat会以其在类中的偏移来访问。

#### 跨Dex访问

以上总结的都是同一个Dex中的访问方式。对于跨Dex的访问，Method和Field都是通过签名来访问的。

#### 总结

采用Q-Zone方案，即将HotFix的dex插到PathClassLoader的前面。限制HotFix不能修改类结构，即只能修改函数体的情况下：

1. Dalvik采用解释执行，即通过方法签名和field名字来查找，理论上能够支持。唯有Const变量不能修改。
2. ART模式：
从原dex访问hotfix中的方法和field，virtual函数和field将会根据类的偏移来访问，由于类结构没变化，理论上支持。
从HotFix访问原dex，将采用的是跨dex的解释模式访问，理论上支持。

####AndFix原理

顺便，这里我们来看下AndFix的原理, Andfix主要实现代码如下：

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/39a9d6d3918283150baf7ffabd983275/197546253388.png)

AndFix的核心思想就是替换了dex_cache中的目标ArtMethod，将其指向Patch中的ArtMethod。

1. 缺陷：
受限于类结构不能改变，AndFix不支持新增方法，新增类，新增field等
由于采用注入方式，适配性是个考验，有些厂商是会修改虚拟机的，如云os，三星，华为等。
2. 优势：
针对method级别的hotfix，patch中只需要带上需要改变的方法，其HotFix的size是非常小的。立即生效，不用等下次重启。

##蘑菇街HotFix-Aceso篇

如此Q-Zone方案在蘑菇街平台上良好运行了半年左右，一切的改变发生在Android 7.0的问世，由于Android N采用的是JIT+AOT profile的混合编译方式，Tinker跟Q-Zone方案跪了，Google为了解决ART上首次安装APK就做OAT带来的性能和IO占用的问题，采用了混合编译的折中方案，在APK首次运行时采用解释模式，然后运行期去收集”热代码“，通过JobScheduler对“热代码”做OAT，同时生成一个叫做Base.art的索引文件，里面保存了已经编译好的”热代码“在OAT中的索引，在应用启动的时候预先加载这些“热点类”到ClassLoader的dexcache缓存中，由于提前将这些类加载到了cache中，这样会导致这些“热点类”的方法永远没办法被替换。下图从Tinker那边拷贝过来的，AndroidN混合编译就像一个小的生态：

![](http://moguimg.u.qiniudn.com/new1/v1/bmisc/af1518dd0792640517638e3190b89d23/197546643098.png)

后来跟Tinker负责人进行探讨，找到了完美的解决方案：

1. 为了绕开混合编译带来的影响，我们抛弃原来的PathClassLoader，转而创建一个新的ClassLoader(AndroidNClassLoader)来加载绝大部分类，PathClassLoader将只加载ProxyApp，然后在ProxyApp中使用AndroidNClassLoader反射调用到realApp，这样从realApp开始，所有被realApp直接或者间接调用到的类都将被AndroidNClassLoader加载了，然后将HotFix的Dex插入AndroidNClassLoader的dexElements[]即可；末了，将AndroidNClassLoader注入PackageInfo中，这样系统以后也会用AndroidNClassLoader来进行类加载了，主要是Android四大组件的加载。
2. 为了继续利用系统本身已经生成好的OAT文件，防止重新生成一份OAT文件来耗磁盘，在AndroidNClassLoader中需要重新创建DexFile, 并调用makePathElements方法将老的Dex跟OAT文件的路径作为参数传入，这样就能够复用以前的OAT文件了。

当时天真的以为Game Over了，直到Tinker负责人告知又有新问题了，原来Android 7.0上inline进行了很大的优化，请参考Tinker的文章：
https://github.com/WeMobileDev/article/blob/master/ART%E4%B8%8B%E7%9A%84%E6%96%B9%E6%B3%95%E5%86%85%E8%81%94%E7%AD%96%E7%95%A5%E5%8F%8A%E5%85%B6%E5%AF%B9Android%E7%83%AD%E4%BF%AE%E5%A4%8D%E6%96%B9%E6%A1%88%E7%9A%84%E5%BD%B1%E5%93%8D%E5%88%86%E6%9E%90.md

好了，Q-Zone方案继续跪，Tinker因为采用分平台合成也跪，AndFix方案也跪了，因为大家都没办法修复被inline掉的代码；为了解决Inline问题，Tinker是从原来IO占用比较小的分平台合成转而做了IO很耗的全量合成的方案，AndFix方面根本没办法修复，手淘那边是采用了动态部署方案兜底，即7.0上采用动态部署（类似Tinker做全量合成）, 而Q-Zone方案没有办法能够解决。当我们正在犹豫是否要接入Tinker/Amigo这种重量级产品之际，用Tinker/Amigo做热修复简直就是大炮打蚊子，Dex，资源都做全量合成，IO占用非常耗，Tinker/Amigo比较适合做功能升级, 其实蘑菇街这边后来是接了Tinker来做类似Atlas的动态部署的事情，这个时候美团的Robust方案的文章面世了（基于InstantRun方案的改造），并得知已经在美团十几个App上运行了半年之久，这里非常感谢Tinker负责人张绍文同学的帮助跟美团那边的技术分享，说实话不想重复造轮子，但是Robust开源不知道什么时候，而且是否靠谱心里不是很有底，于是我们花了一个近月时间自研了跟Robust原理一样的Aceso方案来验证其可靠性。这里取名Aceso--希腊神话中的健康女神。

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








