/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mogujie.instantfix

import com.android.annotations.NonNull
import com.android.build.api.transform.*
import com.android.build.gradle.internal.transforms.JarMergingTransform
import com.google.common.io.Files
import com.mogujie.groovy.util.Log

import static com.google.common.base.Preconditions.checkNotNull

/**
 * Transform for calling AuxiliaryClassInjector.
 *
 * @author tangyinsheng
 */
public class PatchClassTransform extends JarMergingTransform {

    boolean enableRelease = false
    boolean enableDebug = false

    PatchClassTransform(Set<QualifiedContent.Scope> scopes) {
        super(scopes)
    }


    @NonNull
    @Override
    public String getName() {
        return "jarMergingForPatch";
    }

    public boolean  enable(){}
    @Override
    void transform(TransformInvocation invocation) throws TransformException, IOException {
        if (enable) {
            Log.i("patch class is enable.")
            super.transform(invocation)
        } else {
            Log.i("patch class is not enable.")

            TransformOutputProvider outputProvider = invocation.getOutputProvider();
            checkNotNull(outputProvider, "Missing output object for transform " + getName());

            // all the output will be the same since the transform type is COMBINED.
            // and format is SINGLE_JAR so output is a jar
            File fileOutput = outputProvider.getContentLocation("combined", getOutputTypes(), getScopes(),
                    Format.DIRECTORY);

            for (TransformInput input : invocation.getInputs()) {
                for (JarInput jarInput : input.getJarInputs()) {
                    Files.copy(jarInput.file, fileOutput)
                }

                for (DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    Files.copy(directoryInput.file, fileOutput)
                }
            }
        }

    }
}

