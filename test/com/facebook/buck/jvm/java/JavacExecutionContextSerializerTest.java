/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java;

import static org.junit.Assert.assertThat;

import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.ProjectFilesystemFactory;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.io.filesystem.impl.DefaultProjectFilesystemFactory;
import com.facebook.buck.rules.DefaultCellPathResolver;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.util.ClassLoaderCache;
import com.facebook.buck.util.ContextualProcessExecutor;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.Verbosity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.Test;

public class JavacExecutionContextSerializerTest {
  @Test
  public void testSerializingAndDeserializing() throws Exception {
    Path tmp = Files.createTempDirectory("junit-temp-path").toRealPath();

    JavacEventSink eventSink =
        new JavacEventSinkToBuckEventBusBridge(BuckEventBusForTests.newInstance());
    PrintStream stdErr = new PrintStream(new ByteArrayOutputStream());
    ClassLoaderCache classLoaderCache = new ClassLoaderCache();
    Verbosity verbosity = Verbosity.COMMANDS_AND_OUTPUT;
    DefaultCellPathResolver cellPathResolver =
        DefaultCellPathResolver.of(
            Paths.get("/some/cell/path/resolver/path"),
            ImmutableMap.of("key1", Paths.get("/path/1")));
    DefaultJavaPackageFinder javaPackageFinder =
        new DefaultJavaPackageFinder(
            ImmutableSortedSet.of("paths", "from", "root"), ImmutableSet.of("path", "elements"));
    ProjectFilesystem projectFilesystem = TestProjectFilesystems.createProjectFilesystem(tmp);
    ImmutableMap<String, String> environment = ImmutableMap.of("k1", "v1", "k2", "v2");
    ImmutableMap<String, String> processExecutorContext =
        ImmutableMap.of("pek1", "pev1", "pek2", "pev2");
    ProcessExecutor processExecutor =
        new ContextualProcessExecutor(
            new DefaultProcessExecutor(new TestConsole()), processExecutorContext);
    ImmutableList<Path> pathToInputs =
        ImmutableList.of(Paths.get("/path/one"), Paths.get("/path/two"));

    ProjectFilesystemFactory projectFilesystemFactory = new DefaultProjectFilesystemFactory();
    JavacExecutionContext input =
        JavacExecutionContext.of(
            eventSink,
            stdErr,
            classLoaderCache,
            verbosity,
            cellPathResolver,
            javaPackageFinder,
            projectFilesystem,
            projectFilesystemFactory,
            environment,
            processExecutor,
            pathToInputs);
    Map<String, Object> data = JavacExecutionContextSerializer.serialize(input);
    JavacExecutionContext output =
        JavacExecutionContextSerializer.deserialize(
            projectFilesystemFactory, data, eventSink, stdErr, classLoaderCache, new TestConsole());

    assertThat(output.getEventSink(), Matchers.equalTo(eventSink));
    assertThat(output.getStdErr(), Matchers.equalTo(stdErr));
    assertThat(output.getClassLoaderCache(), Matchers.equalTo(classLoaderCache));
    assertThat(output.getVerbosity(), Matchers.equalTo(verbosity));

    assertThat(output.getCellPathResolver(), Matchers.instanceOf(DefaultCellPathResolver.class));
    DefaultCellPathResolver outCellPathResolver =
        (DefaultCellPathResolver) output.getCellPathResolver();
    assertThat(outCellPathResolver.getRoot(), Matchers.equalToObject(cellPathResolver.getRoot()));
    assertThat(
        outCellPathResolver.getCellPaths(),
        Matchers.equalToObject(cellPathResolver.getCellPaths()));

    assertThat(output.getProcessExecutor(), Matchers.instanceOf(ContextualProcessExecutor.class));
    ContextualProcessExecutor contextualProcessExecutor =
        (ContextualProcessExecutor) output.getProcessExecutor();
    assertThat(
        contextualProcessExecutor.getContext(), Matchers.equalToObject(processExecutorContext));

    assertThat(output.getJavaPackageFinder(), Matchers.instanceOf(DefaultJavaPackageFinder.class));
    DefaultJavaPackageFinder outputJavaPackageFinder =
        (DefaultJavaPackageFinder) output.getJavaPackageFinder();
    assertThat(
        outputJavaPackageFinder.getPathsFromRoot(),
        Matchers.equalToObject(javaPackageFinder.getPathsFromRoot()));
    assertThat(
        outputJavaPackageFinder.getPathElements(),
        Matchers.equalToObject(javaPackageFinder.getPathElements()));

    assertThat(
        output.getProjectFilesystem().getRootPath(),
        Matchers.equalToObject(projectFilesystem.getRootPath()));

    assertThat(output.getEnvironment(), Matchers.equalToObject(environment));

    assertThat(output.getAbsolutePathsForInputs(), Matchers.equalToObject(pathToInputs));
  }
}
