/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors;

import com.android.tools.idea.editors.strings.StringsVirtualFile;
import com.android.tools.idea.editors.theme.ThemeEditorVirtualFile;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AndroidFakeFileSystem extends DummyFileSystem {
  @NonNls public static final String PROTOCOL = "android-dummy";
  public static final VirtualFileSystem INSTANCE = new AndroidFakeFileSystem();
  public static final char SEPARATOR = '/';

  @NotNull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    List<String> components = ContainerUtil.collect(Splitter.on(SEPARATOR).split(path).iterator());
    int size = components.size();
    if (size < 3) { // all files are of form: projectPath/moduleName/fileName
      return null;
    }

    String projectPath = Joiner.on(SEPARATOR).join(components.subList(0, size - 2));
    String moduleName = components.get(size - 2);
    Module m = findModule(findProject(projectPath), moduleName);
    if (m == null) {
      return null;
    }

    String fileName = components.get(size - 1);
    if (StringsVirtualFile.NAME.equals(fileName)) {
      return StringsVirtualFile.getStringsVirtualFile(m);
    } else if (ThemeEditorVirtualFile.FILENAME.equals(fileName)) {
      return ThemeEditorVirtualFile.getThemeEditorFile(m);
    }

    return null;
  }

  @NotNull
  public static String constructPathForFile(@NotNull String fileName, @NotNull Module module) {
    return Joiner.on(SEPARATOR).join(module.getProject().getBasePath(), module.getName(), fileName);
  }

  @Nullable
  private static Module findModule(@Nullable Project project, @NotNull String name) {
    if (project == null) {
      return null;
    }

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      if (m.getName().equals(name)) {
        return m;
      }
    }

    return null;
  }

  @Nullable
  private static Project findProject(@NotNull String basePath) {
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      if (basePath.equals(p.getBasePath())) {
        return p;
      }
    }

    return null;
  }
}
