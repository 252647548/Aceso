package com.mogujie.instantrun;

public enum InstantRunPatchingPolicy {
    
    PRE_LOLLIPOP(DexPackagingPolicy.STANDARD, false ),

    
    MULTI_DEX(DexPackagingPolicy.INSTANT_RUN_SHARDS_IN_SINGLE_APK, true ),

    
    MULTI_APK(DexPackagingPolicy.INSTANT_RUN_MULTI_APK, true );

    private final DexPackagingPolicy dexPatchingPolicy;
    private final boolean useMultiDex;

    InstantRunPatchingPolicy(DexPackagingPolicy dexPatchingPolicy, boolean useMultiDex) {
        this.dexPatchingPolicy = dexPatchingPolicy;
        this.useMultiDex = useMultiDex;
    }


}
