/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.annotations.VisibleForTesting;
import com.android.prefs.AndroidLocation;
import com.android.tools.idea.wizard.WizardUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Wrapper around data passed from the installer.
 */
public class InstallerData {
  public static final String PATH_FIRST_RUN_PROPERTIES = FileUtil.join("studio", "installer", "firstrun.data");
  @Nullable private final File myJavaDir;
  @Nullable private final File myAndroidSrc;
  @Nullable private final File myAndroidDest;

  @VisibleForTesting
  InstallerData(@Nullable File javaDir, @Nullable File androidSrc, @Nullable File androidDest) {
    myJavaDir = javaDir;
    myAndroidSrc = androidSrc;
    myAndroidDest = androidDest;
  }

  @Nullable
  private static InstallerData parse() {
    Map<String, String> properties = readProperties();
    if (properties == null) {
      return null;
    }
    String androidSdkPath = properties.get("androidsdk.dir");
    File androidDest = StringUtil.isEmptyOrSpaces(androidSdkPath) ? null : new File(androidSdkPath);
    return new InstallerData(getIfExists(properties, "jdk.dir"), getIfExists(properties, "androidsdk.repo"), androidDest);
  }

  @Nullable
  private static Map<String, String> readProperties() {
    try {
      // Firstrun properties file contains a series of "key=value" lines.
      File file = new File(AndroidLocation.getFolder(), PATH_FIRST_RUN_PROPERTIES);
      if (file.isFile()) {
        Map<String, String> properties = Maps.newHashMap();
        try {
          final List<String> lines = Files.readLines(file, Charsets.UTF_16LE);
          for (String line : lines) {
            int keyValueSeparator = line.indexOf('=');
            if (keyValueSeparator < 0) {
              continue;
            }
            final String key = line.substring(0, keyValueSeparator).trim();
            final String value = line.substring(keyValueSeparator + 1).trim();
            if (key.isEmpty()) {
              continue;
            }
            properties.put(key, value);
          }
        }
        catch (IOException e) {
          Logger.getInstance(InstallerData.class).error(e);
        }

        return properties;
      }
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Logger.getInstance(InstallerData.class).error(e);
    }
    return null;
  }

  @Nullable
  private static File getIfExists(Map<String, String> properties, String propertyName) {
    String path = properties.get(propertyName);
    if (!StringUtil.isEmptyOrSpaces(path)) {
      File file = new File(path);
      return file.isDirectory() ? file : null;
    }
    return null;
  }

  @VisibleForTesting
  static synchronized void set(@Nullable InstallerData data) {
    Holder.INSTALLER_DATA = data;
  }

  @Nullable
  public static synchronized InstallerData get() {
    return Holder.INSTALLER_DATA;
  }

  @Nullable
  public File getJavaDir() {
    return myJavaDir;
  }

  @Nullable
  public File getAndroidSrc() {
    return myAndroidSrc;
  }

  @Nullable
  public File getAndroidDest() {
    return myAndroidDest;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("jdk.dir", myJavaDir).add("androidsdk.repo", myAndroidSrc).add("androidsdk.dir", myAndroidDest)
      .toString();
  }

  public boolean hasValidSdkLocation() {
    File location = getAndroidDest();
    if (location == null) {
      return false;
    }
    else {
      String path = location.getAbsolutePath();
      WizardUtils.ValidationResult validationResult = WizardUtils.validateLocation(path, SdkComponentsStep.FIELD_SDK_LOCATION, false);
      return !validationResult.isError();
    }
  }

  public boolean hasValidJdkLocation() {
    File javaDir = getJavaDir();
    return javaDir != null && JdkLocationStep.validateJdkLocation(javaDir) == null;
  }

  private static class Holder {
    @Nullable private static InstallerData INSTALLER_DATA = parse();
  }
}
