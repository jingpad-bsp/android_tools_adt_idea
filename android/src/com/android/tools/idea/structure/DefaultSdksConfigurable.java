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

package com.android.tools.idea.structure;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkPaths.ValidationResult;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.Function;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import static com.android.tools.idea.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkUtils.tryToChooseAndroidSdk;

/**
 * Allows the user set global Android SDK and JDK locations that are used for Gradle-based Android projects.
 */
public class DefaultSdksConfigurable extends BaseConfigurable {
  private static final String CHOOSE_VALID_JDK_DIRECTORY_ERR = "Please choose a valid JDK directory.";
  private static final String CHOOSE_VALID_SDK_DIRECTORY_ERR = "Please choose a valid Android SDK directory.";

  @Nullable private final AndroidProjectStructureConfigurable myHost;
  @Nullable private final Project myProject;

  // These paths are system-dependent.
  private String myOriginalJdkHomePath;
  private String myOriginalSdkHomePath;

  private TextFieldWithBrowseButton mySdkLocationTextField;
  private TextFieldWithBrowseButton myJdkLocationTextField;
  private JPanel myWholePanel;

  private DetailsComponent myDetailsComponent;

  public DefaultSdksConfigurable(@Nullable AndroidProjectStructureConfigurable host, @Nullable Project project) {
    myHost = host;
    myProject = project;
    myWholePanel.setPreferredSize(new Dimension(700, 500));

    myDetailsComponent = new DetailsComponent();
    myDetailsComponent.setContent(myWholePanel);
    myDetailsComponent.setText("SDK Location");
  }

  @Override
  public void disposeUIResources() {
  }

  @Override
  public void reset() {
    myOriginalSdkHomePath = getDefaultSdkPath();
    myOriginalJdkHomePath = getDefaultJdkPath();

    mySdkLocationTextField.setText(myOriginalSdkHomePath);
    myJdkLocationTextField.setText(myOriginalJdkHomePath);
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        IdeSdks.setJdkPath(getJdkLocation());
        IdeSdks.setAndroidSdkPath(getSdkLocation(), myProject);

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          RunAndroidSdkManagerAction.updateInWelcomePage(myDetailsComponent.getComponent());
        }
      }
    });
  }

  private void createUIComponents() {
    createSdkLocationTextField();
    createJdkLocationTextField();
  }

  private void createSdkLocationTextField() {
    final FileChooserDescriptor descriptor = createSingleFolderDescriptor("Choose Android SDK Location", new Function<File, Void>() {
      @Override
      public Void fun(File file) {
        ValidationResult validationResult = validateAndroidSdk(file, false);
        if (!validationResult.success) {
          String msg = validationResult.message;
          if (isEmpty(msg)) {
            msg = CHOOSE_VALID_SDK_DIRECTORY_ERR;
          }
          throw new IllegalArgumentException(msg);
        }
        return null;
      }
    });

    JTextField textField = new JTextField(10);
    mySdkLocationTextField = new TextFieldWithBrowseButton(textField, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        VirtualFile suggestedDir = null;
        File sdkLocation = getSdkLocation();
        if (sdkLocation.isDirectory()) {
          suggestedDir = findFileByIoFile(sdkLocation, false);
        }
        VirtualFile chosen = FileChooser.chooseFile(descriptor, null, suggestedDir);
        if (chosen != null) {
          File f = virtualToIoFile(chosen);
          mySdkLocationTextField.setText(f.getPath());
        }
      }
    });
    installValidationListener(textField);
  }

  private void createJdkLocationTextField() {
    JTextField textField = new JTextField(10);
    myJdkLocationTextField = new TextFieldWithBrowseButton(textField, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        chooseJdkLocation();
      }
    });
    installValidationListener(textField);
  }

  public void chooseJdkLocation() {
    myJdkLocationTextField.getTextField().requestFocus();

    VirtualFile suggestedDir = null;
    File jdkLocation = getJdkLocation();
    if (jdkLocation.isDirectory()) {
      suggestedDir = findFileByIoFile(jdkLocation, false);
    }
    VirtualFile chosen = FileChooser.chooseFile(createSingleFolderDescriptor("Choose JDK Location", new Function<File, Void>() {
      @Override
      public Void fun(File file) {
        if (!validateAndUpdateJdkPath(file)) {
          throw new IllegalArgumentException(CHOOSE_VALID_JDK_DIRECTORY_ERR);
        }
        return null;
      }
    }), null, suggestedDir);
    if (chosen != null) {
      File f = virtualToIoFile(chosen);
      myJdkLocationTextField.setText(f.getPath());
    }
  }

  private void installValidationListener(@NotNull JTextField textField) {
    if (myHost != null) {
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          myHost.requestValidation();
        }
      });
    }
  }

  @NotNull
  private static FileChooserDescriptor createSingleFolderDescriptor(@NotNull String title, @NotNull final Function<File, Void> validation) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        for (VirtualFile virtualFile : files) {
          File file = virtualToIoFile(virtualFile);
          validation.fun(file);
        }
      }
    };
    if (SystemInfo.isMac) {
      descriptor.withShowHiddenFiles(true);
    }
    descriptor.setTitle(title);
    return descriptor;
  }

  @Override
  public String getDisplayName() {
    return "SDK Location";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myDetailsComponent.getComponent();
  }

  @NotNull
  public JComponent getContentPanel() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    return !myOriginalSdkHomePath.equals(getSdkLocation().getPath()) || !myOriginalJdkHomePath.equals(getJdkLocation().getPath());
  }

  /**
   * Returns the first SDK it finds that matches our default naming convention. There will be several SDKs so named, one for each build
   * target installed in the SDK; which of those this method returns is not defined.
   *
   * @param create True if this method should attempt to create an SDK if one does not exist.
   * @return null if an SDK is unavailable or creation failed.
   */
  @Nullable
  private static Sdk getFirstDefaultAndroidSdk(boolean create) {
    List<Sdk> allAndroidSdks = IdeSdks.getEligibleAndroidSdks();
    if (!allAndroidSdks.isEmpty()) {
      return allAndroidSdks.get(0);
    }
    if (!create) {
      return null;
    }
    AndroidSdkData sdkData = tryToChooseAndroidSdk();
    if (sdkData == null) {
      return null;
    }
    List<Sdk> sdks = IdeSdks.createAndroidSdkPerAndroidTarget(sdkData.getLocation());
    return !sdks.isEmpty() ? sdks.get(0) : null;
  }

  /**
   * @return what the IDE is using as the home path for the Android SDK for new projects.
   */
  @NotNull
  private static String getDefaultSdkPath() {
    File path = IdeSdks.getAndroidSdkPath();
    if (path != null) {
      return path.getPath();
    }
    Sdk sdk = getFirstDefaultAndroidSdk(true);
    if (sdk != null) {
      String sdkHome = sdk.getHomePath();
      if (sdkHome != null) {
        return toSystemDependentName(sdkHome);
      }
    }
    return "";
  }

  /**
   * @return what the IDE is using as the home path for the JDK.
   */
  @NotNull
  private static String getDefaultJdkPath() {
    File javaHome = IdeSdks.getJdkPath();
    return javaHome != null ? javaHome.getPath() : "";
  }

  @NotNull
  private File getSdkLocation() {
    String sdkLocation = mySdkLocationTextField.getText();
    return new File(toSystemDependentName(sdkLocation));
  }

  @Override
  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return mySdkLocationTextField.getTextField();
  }

  public boolean validate() throws ConfigurationException {
    ValidationResult validationResult = validateAndroidSdk(getSdkLocation(), false);
    if (!validationResult.success) {
      String msg = validationResult.message;
      if (isEmpty(msg)) {
        msg = CHOOSE_VALID_SDK_DIRECTORY_ERR;
      }
      throw new ConfigurationException(msg);
    }

    if (!validateAndUpdateJdkPath(getJdkLocation())) {
      throw new ConfigurationException(CHOOSE_VALID_JDK_DIRECTORY_ERR);
    }
    return true;
  }

  @NotNull
  public List<ProjectConfigurationError> validateState() {
    List<ProjectConfigurationError> errors = Lists.newArrayList();

    ValidationResult validationResult = validateAndroidSdk(getSdkLocation(), false);
    if (!validationResult.success) {
      String msg = validationResult.message;
      if (isEmpty(msg)) {
        msg = CHOOSE_VALID_SDK_DIRECTORY_ERR;
      }
      ProjectConfigurationError error = new ProjectConfigurationError(msg, mySdkLocationTextField.getTextField());
      errors.add(error);
    }

    if (!validateAndUpdateJdkPath(getJdkLocation())) {
      ProjectConfigurationError error =
        new ProjectConfigurationError(CHOOSE_VALID_JDK_DIRECTORY_ERR, myJdkLocationTextField.getTextField());
      errors.add(error);
    }

    return errors;
  }

  @NotNull
  private File getJdkLocation() {
    String jdkLocation = myJdkLocationTextField.getText();
    return new File(toSystemDependentName(jdkLocation));
  }

  private boolean validateAndUpdateJdkPath(@NotNull File file) {
    if (JavaSdk.checkForJdk(file)) {
      return true;
    }
    if (SystemInfo.isMac) {
      File potentialPath = new File(file, IdeSdks.MAC_JDK_CONTENT_PATH);
      if (potentialPath.isDirectory() && JavaSdk.checkForJdk(potentialPath)) {
        myJdkLocationTextField.setText(potentialPath.getPath());
        return true;
      }
    }
    return false;
  }

  /**
   * @return {@code true} if the configurable is needed: e.g. if we're missing a JDK or an Android SDK setting.
   */
  public static boolean isNeeded() {
    String jdkPath = getDefaultJdkPath();
    String sdkPath = getDefaultSdkPath();
    boolean validJdk = !jdkPath.isEmpty() && JavaSdk.checkForJdk(new File(jdkPath));
    boolean validSdk = !sdkPath.isEmpty() && IdeSdks.isValidAndroidSdkPath(new File(sdkPath));
    return !validJdk || !validSdk;
  }
}
