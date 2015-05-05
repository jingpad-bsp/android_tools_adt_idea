/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.PreciseRevision;
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.customizer.android.*;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixGradleModelVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.sdklib.repository.PreciseRevision.parseRevision;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.EXTRA_GENERATED_SOURCES;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.messages.Message.Type.*;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleVersion;
import static com.android.tools.idea.gradle.util.GradleUtil.hasLayoutRenderingIssue;
import static com.android.tools.idea.sdk.Jdks.isApplicableJdk;
import static com.android.tools.idea.startup.AndroidStudioSpecificInitializer.isAndroidStudio;
import static com.intellij.ide.impl.NewProjectUtil.applyJdkToProject;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidProjectDataService implements ProjectDataService<IdeaAndroidProject, Void> {
  private static final Logger LOG = Logger.getInstance(AndroidProjectDataService.class);

  private final List<ModuleCustomizer<IdeaAndroidProject>> myCustomizers;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  @SuppressWarnings("unused")
  public AndroidProjectDataService() {
    this(ImmutableList.of(new AndroidSdkModuleCustomizer(), new AndroidFacetModuleCustomizer(), new ContentRootModuleCustomizer(),
                          new RunConfigModuleCustomizer(), new DependenciesModuleCustomizer(), new CompilerOutputModuleCustomizer()));
  }

  @VisibleForTesting
  AndroidProjectDataService(@NotNull List<ModuleCustomizer<IdeaAndroidProject>> customizers) {
    myCustomizers = customizers;
  }

  @NotNull
  @Override
  public Key<IdeaAndroidProject> getTargetDataKey() {
    return AndroidProjectKeys.IDE_ANDROID_PROJECT;
  }

  /**
   * Sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
   *
   * @param toImport    contains the Android-Gradle project.
   * @param project     IDEA project to configure.
   * @param synchronous indicates whether this operation is synchronous.
   */
  @Override
  public void importData(@NotNull Collection<DataNode<IdeaAndroidProject>> toImport, @NotNull Project project, boolean synchronous) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project);
      }
      catch (Throwable e) {
        LOG.error(String.format("Failed to set up Android modules in project '%1$s'", project.getName()), e);
        String msg = e.getMessage();
        if (msg == null) {
          msg = e.getClass().getCanonicalName();
        }
        GradleSyncState.getInstance(project).syncFailed(msg);
      }
    }
  }

  private void doImport(final Collection<DataNode<IdeaAndroidProject>> toImport, final Project project) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        LanguageLevel javaLangVersion = null;

        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
        boolean hasExtraGeneratedFolders = false;

        Map<String, IdeaAndroidProject> androidProjectsByModuleName = indexByModuleName(toImport);

        FullRevision gradleVersion = getGradleVersion(project);
        boolean checkGradleCompatibility = false;
        if (gradleVersion != null) {
          checkGradleCompatibility = gradleVersion.compareTo(new PreciseRevision(2, 4, 0), PreviewComparison.IGNORE) >= 0;
        }

        Charset ideEncoding = EncodingProjectManager.getInstance(project).getDefaultCharset();
        FullRevision oneDotTwoModelVersion = new PreciseRevision(1, 2, 0);

        String incompatibleModelVersionFound = null;
        String nonMatchingModelEncodingFound = null;
        String modelVersionWithLayoutRenderingIssue = null;

        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
          IdeaAndroidProject androidProject = androidProjectsByModuleName.get(module.getName());

          customizeModule(module, project, androidProject);
          if (androidProject != null) {
            AndroidProject delegate = androidProject.getDelegate();

            // Verify that if Gradle is 2.4 (or newer,) the model is at least version 1.2.0.
            if (modelVersionWithLayoutRenderingIssue == null && hasLayoutRenderingIssue(delegate)) {
              modelVersionWithLayoutRenderingIssue = delegate.getModelVersion();
            }

            FullRevision modelVersion = parseRevision(delegate.getModelVersion());
            boolean isModelVersionOneDotTwoOrNewer = modelVersion.compareTo(oneDotTwoModelVersion, PreviewComparison.IGNORE) >= 0;

            if (checkGradleCompatibility && incompatibleModelVersionFound == null && !isModelVersionOneDotTwoOrNewer) {
              incompatibleModelVersionFound = delegate.getModelVersion();
            }

            // Verify that the encoding in the model is the same as the encoding in the IDE's project settings.
            Charset modelEncoding = null;
            if (isModelVersionOneDotTwoOrNewer) {
              try {
                modelEncoding = Charset.forName(delegate.getJavaCompileOptions().getEncoding());
              }
              catch (UnsupportedCharsetException ignore) {
                // It's not going to happen.
              }
            }
            if (nonMatchingModelEncodingFound == null && modelEncoding != null && ideEncoding.compareTo(modelEncoding) != 0) {
              nonMatchingModelEncodingFound = modelEncoding.displayName();
            }

            // Get the Java language version from the model.
            if (javaLangVersion == null) {
              javaLangVersion = androidProject.getJavaLanguageLevel();
            }

            // Warn users that there are generated source folders at the wrong location.
            File[] sourceFolders = androidProject.getExtraGeneratedSourceFolders();
            if (sourceFolders.length > 0) {
              hasExtraGeneratedFolders = true;
            }
            for (File folder : sourceFolders) {
              // Have to add a word before the path, otherwise IDEA won't show it.
              String[] text = {"Folder " + folder.getPath()};
              messages.add(new Message(EXTRA_GENERATED_SOURCES, WARNING, text));
            }
          }
        }

        if (incompatibleModelVersionFound != null) {
          addModelVersionIncompatibilityMessage(incompatibleModelVersionFound, project);
        }

        if (nonMatchingModelEncodingFound != null) {
          setIdeEncodingAndAddEncodingMismatchMessage(nonMatchingModelEncodingFound, project);
        }

        if (modelVersionWithLayoutRenderingIssue != null) {
          addLayoutRenderingIssueMessage(modelVersionWithLayoutRenderingIssue, project);
        }

        if (hasExtraGeneratedFolders) {
          messages.add(new Message(EXTRA_GENERATED_SOURCES, INFO, "3rd-party Gradle plug-ins may be the cause"));
        }

        Sdk jdk = ProjectRootManager.getInstance(project).getProjectSdk();

        if (jdk == null || (!isAndroidStudio() && !isApplicableJdk(jdk, javaLangVersion))) {
          jdk = Jdks.chooseOrCreateJavaSdk(javaLangVersion);
        }
        if (jdk == null) {
          String title = String.format("Problems importing/refreshing Gradle project '%1$s':\n", project.getName());
          LanguageLevel level = javaLangVersion != null ? javaLangVersion : LanguageLevel.JDK_1_6;
          String msg = String.format("Unable to find a JDK %1$s installed.\n", level.getPresentableText());
          msg += "After configuring a suitable JDK in the \"Project Structure\" dialog, sync the Gradle project again.";
          NotificationData notification = new NotificationData(title, msg, NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
          ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notification);
        }
        else {
          String homePath = jdk.getHomePath();
          if (homePath != null) {
            applyJdkToProject(project, jdk);
            homePath = toSystemDependentName(homePath);
            IdeSdks.setJdkPath(new File(homePath));
            PostProjectBuildTasksExecutor.getInstance(project).updateJavaLangLevelAfterBuild();
          }
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  private static void addModelVersionIncompatibilityMessage(@NotNull String modelVersion, @NotNull Project project) {
    NotificationHyperlink quickFix = new FixGradleModelVersionHyperlink(GRADLE_PLUGIN_RECOMMENDED_VERSION,
                                                                        null /* do not update Gradle version */, false);
    String[] text = {
      String.format("Android plugin version %1$s is not compatible with Gradle version 2.4 (or newer.)", modelVersion),
      "Please use Android plugin version 1.2 or newer."
    };
    ProjectSyncMessages.getInstance(project).add(new Message(UNHANDLED_SYNC_ISSUE_TYPE, ERROR, text), quickFix);
  }

  private static void setIdeEncodingAndAddEncodingMismatchMessage(@NotNull String newEncoding, @NotNull Project project) {
    EncodingProjectManager encodings = EncodingProjectManager.getInstance(project);
    String[] text = {
      String.format("The project encoding (%1$s) has been reset to the encoding specified in the Gradle build files (%2$s).",
                    encodings.getDefaultCharset().displayName(), newEncoding),
      "Mismatching encodings can lead to serious bugs."
    };
    encodings.setDefaultCharsetName(newEncoding);
    NotificationHyperlink openDocHyperlink = new OpenUrlHyperlink("http://tools.android.com/knownissues/encoding", "More Info...");
    ProjectSyncMessages.getInstance(project).add(new Message(UNHANDLED_SYNC_ISSUE_TYPE, INFO, text), openDocHyperlink);
  }

  private static void addLayoutRenderingIssueMessage(String modelVersion, @NotNull Project project) {
    // See https://code.google.com/p/android/issues/detail?id=170841
    NotificationHyperlink quickFix = new FixGradleModelVersionHyperlink(false);
    NotificationHyperlink openDocHyperlink = new OpenUrlHyperlink("https://code.google.com/p/android/issues/detail?id=170841",
                                                                  "More Info...");
    String[] text = {
      String.format("Using an obsolete version of the Gradle plugin (%1$s); this can lead to layouts not rendering correctly.",
                    modelVersion)
    };
    ProjectSyncMessages.getInstance(project).add(new Message(UNHANDLED_SYNC_ISSUE_TYPE, WARNING, text), openDocHyperlink, quickFix);
  }

  @NotNull
  private static Map<String, IdeaAndroidProject> indexByModuleName(@NotNull Collection<DataNode<IdeaAndroidProject>> dataNodes) {
    Map<String, IdeaAndroidProject> index = Maps.newHashMap();
    for (DataNode<IdeaAndroidProject> d : dataNodes) {
      IdeaAndroidProject androidProject = d.getData();
      index.put(androidProject.getModuleName(), androidProject);
    }
    return index;
  }

  private void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      for (ModuleCustomizer<IdeaAndroidProject> customizer : myCustomizers) {
        customizer.customizeModule(project, rootModel, ideaAndroidProject);
      }
    }
    finally {
      rootModel.commit();
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Void> toRemove, @NotNull Project project, boolean synchronous) {
  }
}
