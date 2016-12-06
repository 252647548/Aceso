package instant

import com.google.common.collect.ImmutableList

/**
 * Created by wangzhi on 16/12/5.
 */
class InstantFixTest {

    public static void main(String[] args) {
//        inject();
        ArrayList<String> list = new ArrayList<>()
        list.add("com.mogujie.instantfix.MainActivity")
        addPatchesLoaderClass(list);
    }


    public static void inject(String[] args) {

//        StringBuilder sb = new StringBuilder()
//        sb.append("/Users/farmerjohn/Library/Android/sdk/platforms/android-22/android.jar")
//        sb.append(File.pathSeparator)
//        sb.append("/Users/farmerjohn/AndroidStudioProjects/InstantFix/app/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.2.0/jars/classes.jar")
//        sb.append(File.pathSeparator)
//        sb.append("/Users/farmerjohn/AndroidStudioProjects/InstantFix/app/build/intermediates/exploded-aar/com.android.support/support-v4/22.2.0/jars/classes.jar")

//        String[] args = new String[3];
//        args[0] = "/Users/farmerjohn/AndroidStudioProjects/InstantFix/app/build/intermediates/classes/debug/";
//        args[1] = "/Users/farmerjohn/IdeaProjects/Base/InstantFIx/resources/reload-support";
//        args[2] = sb.toString()
        IncrementalSupportVisitor.main(args)
    }

    public static void hotfix(String[] args) {
//        StringBuilder sb = new StringBuilder()
//        sb.append("/Users/farmerjohn/Library/Android/sdk/platforms/android-22/android.jar")
//        sb.append(File.pathSeparator)
//        sb.append("/Users/farmerjohn/AndroidStudioProjects/InstantFix/app/build/intermediates/exploded-aar/com.android.support/appcompat-v7/22.2.0/jars/classes.jar")
//        sb.append(File.pathSeparator)
//        sb.append("/Users/farmerjohn/AndroidStudioProjects/InstantFix/app/build/intermediates/exploded-aar/com.android.support/support-v4/22.2.0/jars/classes.jar")
//
//        String[] args = new String[3];
//        args[0] = "/Users/farmerjohn/AndroidStudioProjects/InstantFix/app/build/intermediates/classes/debug";
//        args[1] = "/Users/farmerjohn/IdeaProjects/Base/InstantFIx/resources/reload-fix";
//        args[2] = sb.toString()
        IncrementalChangeVisitor.main(args)
    }

    public static void addPatchesLoaderClass(ArrayList list, File outFile) {
//         outFile = new File("/Users/farmerjohn/IdeaProjects/Base/InstantFIx/resources/reload-add")
        ImmutableList<String> immutableList = ImmutableList.copyOf(list)
        InstantRunUtil.writePatchFileContents(immutableList, outFile, 1)

    }

}
