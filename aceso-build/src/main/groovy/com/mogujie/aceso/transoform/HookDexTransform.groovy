package com.mogujie.aceso.transoform

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformInvocationBuilder
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.builder.signing.SignedJarBuilder
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.mogujie.aceso.processor.ClassProcessor
import com.mogujie.groovy.util.Log
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.execution.TaskExecutionGraphListener

import java.lang.reflect.Field

/**
 * Created by wangzhi on 16/11/24.
 */
public class HookDexTransform extends Transform {

    Project project

    String varName

    String varDirName

    def variant

    DexTransform dexTransform

    ClassProcessor processor


    HookDexTransform(Project project, def variant, DexTransform dexTransform, ClassProcessor processor) {
        this.dexTransform = dexTransform
        this.project = project
        this.variant = variant
        this.varName = variant.name.capitalize()
        this.varDirName = variant.getDirName()
        this.processor = processor
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return dexTransform.getOutputTypes();
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryFileInputs() {
        return dexTransform.getSecondaryFileInputs()
    }

    @NonNull
    @Override
    public Collection<File> getSecondaryDirectoryOutputs() {
        return dexTransform.getSecondaryDirectoryOutputs()
    }

    @NonNull
    @Override
    public Map<String, Object> getParameterInputs() {
        return dexTransform.getParameterInputs()
    }

    @Override
    String getName() {
        return dexTransform.getName()
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return dexTransform.getInputTypes()
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return dexTransform.getScopes()
    }

    @Override
    boolean isIncremental() {
        return dexTransform.isIncremental()
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {
        List<JarInput> jarInputs = Lists.newArrayList();
        List<DirectoryInput> dirInputs = Lists.newArrayList();

        for (TransformInput input : transformInvocation.getInputs()) {
            jarInputs.addAll(input.getJarInputs());
        }

        for (TransformInput input : transformInvocation.getInputs()) {
            dirInputs.addAll(input.getDirectoryInputs());
        }

        //init dir
        processor.prepare()

        JarMerger jarMerger = new JarMerger(processor.getMergedJar())
        jarMerger.setFilter(new SignedJarBuilder.IZipEntryFilter() {
            @Override
            public boolean checkEntry(String archivePath)
                    throws SignedJarBuilder.IZipEntryFilter.ZipAbortException {
                return archivePath.endsWith(SdkConstants.DOT_CLASS);
            }
        });

        jarInputs.each { jar ->
            Log.i("add jar " + jar.getFile())
            jarMerger.addJar(jar.getFile())
        }
        dirInputs.each { dir ->
            Log.i("add dir " + dir.getFile())
            jarMerger.addFolder(dir.getFile())
        }

        jarMerger.close()

        processor.process()

        //invoke the original transform method
        TransformInvocationBuilder builder = new TransformInvocationBuilder(transformInvocation.getContext());
        builder.addInputs(jarFileToInputs(processor.getOutJar()))
        builder.addOutputProvider(transformInvocation.getOutputProvider())
        builder.addReferencedInputs(transformInvocation.getReferencedInputs())
        builder.addSecondaryInputs(transformInvocation.getSecondaryInputs())
        builder.setIncrementalMode(transformInvocation.isIncremental())
        dexTransform.transform(builder.build())

    }

    Collection<TransformInput> jarFileToInputs(File jarFile) {
        TransformInput transformInput = new TransformInput() {
            @Override
            Collection<JarInput> getJarInputs() {
                JarInput jarInput = new JarInput() {
                    @Override
                    Status getStatus() {
                        return Status.ADDED
                    }

                    @Override
                    String getName() {
                        return jarFile.getName().substring(0,
                                jarFile.getName().length() - ".jar".length())
                    }

                    @Override
                    File getFile() {
                        return jarFile
                    }

                    @Override
                    Set<QualifiedContent.ContentType> getContentTypes() {
                        return HookDexTransform.this.getInputTypes()
                    }

                    @Override
                    Set<QualifiedContent.Scope> getScopes() {
                        return HookDexTransform.this.getScopes()
                    }
                }
                return ImmutableList.of(jarInput)
            }


            @Override
            Collection<DirectoryInput> getDirectoryInputs() {
                return ImmutableList.of()
            }
        }
        return ImmutableList.of(transformInput)
    }


    public static void injectDexTransform(Project project, def variant, ClassProcessor processor) {
        Log.i("prepare inject dex transform ")
        project.getGradle().getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph taskGraph) {
                for (Task task : taskGraph.getAllTasks()) {
                    if (task.getProject().equals(project)
                            && task instanceof TransformTask
                            && task.name.toLowerCase().contains(variant.name.toLowerCase())) {
                        if (isDexTransform(((TransformTask) task).getTransform())
                                && !(((TransformTask) task).getTransform() instanceof HookDexTransform)) {
                            Log.w("find dex transform. class: " + task.transform.getClass() + ". task name: " + task.name)
                            DexTransform dexTransform = task.transform
                            HookDexTransform hookDexTransform = new HookDexTransform(project,
                                    variant, dexTransform, processor)
                            Field field = TransformTask.class.getDeclaredField("transform")
                            field.setAccessible(true)
                            field.set(task, hookDexTransform)
                            Log.w("transform class after hook: " + task.transform.getClass())
                            Log.w("ClassProcessor class after hook: " + processor.getClass())
                            break;
                        }
                    }
                }
            }
        });

    }

    public static isDexTransform(Transform transform) {
        return ((transform instanceof DexTransform) || transform.getName().equals("dex"))
    }


}



