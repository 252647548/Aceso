/*
 *
 *  * Copyright (C) 2017 meili-inc company
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.mogujie.aceso.transoform

import com.android.annotations.NonNull
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.SecondaryFile
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.processor.ClassProcessor
import com.mogujie.aceso.util.Log
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener

import java.lang.reflect.Field

/**
 * Created by wangzhi on 17/2/8.
 */
public abstract class HookTransform extends Transform {

    Project project

    String varName

    String varDirName

    def variant

    Transform transform

    ClassProcessor processor

    HookTransform(Project project, def variant, Transform transform, ClassProcessor processor) {
        this.transform = transform
        this.project = project
        this.variant = variant
        this.varName = variant.name.capitalize()
        this.varDirName = variant.getDirName()
        this.processor = processor
    }

    public interface TransformBuilder {
        HookTransform build(Project project, Object variant,
                            Transform transform, ClassProcessor processor)

        boolean isExactTransform(Transform transform)

    }

    /**
     * Replace specified task 's transform with HookDexTransform
     */

    public static void injectTransform(Project project,
                                       def variant, ClassProcessor processor, TransformBuilder builder) {

        project.getGradle().getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph taskGraph) {
                for (Task task : taskGraph.getAllTasks()) {
                    if (task.getProject().equals(project)
                            && task instanceof TransformTask
                            && task.name.toLowerCase().contains(variant.name.toLowerCase())) {
                        if (builder.isExactTransform(((TransformTask) task).getTransform())) {
                                Log.w("find transform. class: " + task.transform.getClass() + ". task name: " + task.name)
                                HookTransform hookTransform = builder.build(project, variant, task.transform, processor)
                                Field field = TransformTask.class.getDeclaredField("transform")
                                field.setAccessible(true)
                                field.set(task, hookTransform)
                            break;
                        }
                    }
                }
            }
        }

        );
    }


    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return transform.getOutputTypes();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return transform.getSecondaryFileInputs()
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return transform.getSecondaryDirectoryOutputs()
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return transform.getParameterInputs()
    }

    @Override
    String getName() {
        return transform.getName()
    }

    @NonNull
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return transform.getReferencedScopes()
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return transform.getInputTypes()
    }

    @NonNull
    public Collection<File> getSecondaryFileOutputs() {
        return transform.getSecondaryFileOutputs();
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return transform.getScopes()
    }

    @NonNull
    public Collection<SecondaryFile> getSecondaryFiles() {
        return transform.getSecondaryFiles()
    }

    @Override
    boolean isIncremental() {
        return transform.isIncremental()
    }

}
