package com.android.tools.fd.runtime;

import java.util.HashMap;
import java.util.concurrent.locks.ReadWriteLock;


/**
 * Created by wangzhi on 16/12/22.
 */

public class InstantFixClassMap {
    private static AtomMap sAtomMap = new AtomMap();
    private static ClassLoader sClassLoader = null;


    public static class AtomMap {
        HashMap<Integer, IncrementalChange> classChangeMap;
        HashMap<Integer, String> classIndexMap;
        HashMap<Integer, ReadWriteLock> lockMap = new HashMap<>();

        private AtomMap() {
            classChangeMap = new HashMap<>();
            classIndexMap = new HashMap<>();
            lockMap = new HashMap<>();
        }

        public AtomMap(HashMap<Integer, String> classIndexMap, HashMap<Integer, ReadWriteLock> lockMap) {
            this.classIndexMap = classIndexMap;
            this.lockMap = lockMap;
            classChangeMap = new HashMap<>();
        }
    }

    public static void setAtomMap(AtomMap atomMap) {
        sAtomMap = atomMap;
    }

    public static void setClassLoader(ClassLoader classLoader) {
        sClassLoader = classLoader;
    }

    public static IncrementalChange get(int classIndex, int mtdIndex) {
        if (sClassLoader == null) {
            return null;
        }
        ReadWriteLock lock = sAtomMap.lockMap.get(classIndex);
        if (lock == null) {
            return null;
        }
        String classNameInPackage = sAtomMap.classIndexMap.get(classIndex);
        //classNameInPackage!=null 说明该类被hotfix了
        if (classNameInPackage != null) {
            lock.readLock().lock();
            IncrementalChange incrementalChange = sAtomMap.classChangeMap.get(classIndex);
            lock.readLock().unlock();
            //该类被hotfix后第一次调用它的方法。
            if (incrementalChange == null) {
                try {
                    Class<?> aClass = sClassLoader.loadClass(classNameInPackage + "$override");
                    incrementalChange = (IncrementalChange) aClass.newInstance();
                    lock.writeLock().lock();
                    sAtomMap.classChangeMap.put(classIndex, incrementalChange);
                    lock.writeLock().unlock();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (incrementalChange != null && incrementalChange.isSupport(mtdIndex)) {
                return incrementalChange;
            }
        }
        return null;
    }


}
