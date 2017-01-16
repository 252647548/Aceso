/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mogujie.instantrun;


import com.mogujie.groovy.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Implementation of Android's {@link ILogger} over Gradle's .
 */
public class LoggerWrapper implements ILogger {

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        Log.e(String.format(msgFormat, args) + "\n\t" + sw.toString());
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        Log.w(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        Log.i(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        Log.v(String.format(msgFormat, args));
    }

    @NonNull
    public static LoggerWrapper getLogger(@NonNull Class<?> klass) {
        return new LoggerWrapper();
    }
}
