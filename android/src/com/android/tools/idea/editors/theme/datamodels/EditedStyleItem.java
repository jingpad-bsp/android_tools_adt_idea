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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.StyleResolver;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for {@link com.android.ide.common.rendering.api.ResourceValue} that allows to keep track of modifications and source so we can
 * serialize modifications back to the style file.
 */
public class EditedStyleItem {
  private final static Logger LOG = Logger.getInstance(EditedStyleItem.class);
  private final static String DEPRECATED = "deprecated";

  private final ThemeEditorStyle mySourceTheme;
  private ItemResourceValue myItemResourceValue;
  private String myNormalizedValue;
  private boolean myModified;
  private boolean isAttr;
  private final String myAttrGroup;

  public EditedStyleItem(@NotNull ItemResourceValue itemResourceValue, @NotNull ThemeEditorStyle sourceTheme) {
    myItemResourceValue = itemResourceValue;
    mySourceTheme = sourceTheme;

    AttributeDefinition attrDef = StyleResolver.getAttributeDefinition(sourceTheme.getConfiguration(), itemResourceValue);
    String attrGroup = (attrDef == null) ? null : attrDef.getAttrGroup();
    myAttrGroup = (attrGroup == null) ? "Other non-theme attributes." : attrGroup;

    parseValue(myItemResourceValue.getRawXmlValue(), myItemResourceValue.isFramework());
  }

  public void setValue(@Nullable String value) {
    boolean isFramework =
      value != null && (value.startsWith(SdkConstants.ANDROID_PREFIX) || value.startsWith(SdkConstants.ANDROID_THEME_PREFIX));

    myItemResourceValue = new ItemResourceValue(myItemResourceValue.getName(), myItemResourceValue.isFrameworkAttr(), value, isFramework);
    parseValue(myItemResourceValue.getRawXmlValue(), isFramework);
    myModified = true;
  }

  @NotNull
  public String getAttrGroup() {
    return myAttrGroup;
  }

  /**
   * Parses the passed value and sets the normalized value string.
   * @param value The possibly non normalized value.
   * @param isFramework True if this value is a framework reference.
   */
  void parseValue(@Nullable String value, boolean isFramework) {
    if (SdkConstants.NULL_RESOURCE.equals(value)) {
      myNormalizedValue = value;
      return;
    }

    ResourceValue resource = ResourceValue.parse(value, true, true, true);
    if (resource == null || !resource.isValidReference()) {
      myNormalizedValue = value;
      return;
    }

    isAttr = SdkConstants.RESOURCE_CLZ_ATTR.equals(resource.getResourceType());
    StringBuilder valueBuilder = new StringBuilder().append(resource.getPrefix());
    if (Strings.isNullOrEmpty(resource.getPackage())) {
      // Sometimes framework values won't include the package so we add it here.
      if (isFramework) {
        valueBuilder.append(SdkConstants.ANDROID_PKG).append(':');
      }
    } else {
      valueBuilder.append(resource.getPackage()).append(':');
    }
    valueBuilder.append(resource.getResourceType()).append('/').append(resource.getResourceName());

    myNormalizedValue = valueBuilder.toString();
  }

  @Nullable
  public String getValue() {
    return myNormalizedValue;
  }

  @Nullable
  public String getRawXmlValue() {
    return myNormalizedValue;
  }

  @NotNull
  public String getName() {
    return myItemResourceValue.getName();
  }

  public boolean isFrameworkAttr() {
    return myItemResourceValue.isFrameworkAttr();
  }

  /**
   * Returns whether this value has been modified since this resource value was loaded.
   */
  public boolean isModified() {
    return myModified;
  }

  @NotNull
  public ThemeEditorStyle getSourceStyle() {
    return mySourceTheme;
  }

  @NotNull
  public ItemResourceValue getItemResourceValue() {
    return myItemResourceValue;
  }

  /**
   * Returns whether this attribute value points to an attr reference.
   */
  public boolean isAttr() {
    return isAttr;
  }

  @Override
  public String toString() {
    return String.format("[%1$s] %2$s = %3$s", mySourceTheme, getName(), getValue());
  }

  @NotNull
  public String getQualifiedName() {
    return (getItemResourceValue().isFrameworkAttr() ? SdkConstants.PREFIX_ANDROID : "") + getName();
  }

  public String getAttrPropertyName() {
    if (!isAttr()) {
      return "";
    }

    String propertyName = Splitter.on('/').limit(2).splitToList(getValue()).get(1);
    return (getValue().startsWith(SdkConstants.ANDROID_THEME_PREFIX) ?
      SdkConstants.PREFIX_ANDROID :
      "") + propertyName;
  }

  public boolean isDeprecated() {
    AttributeDefinition def = StyleResolver.getAttributeDefinition(mySourceTheme.getConfiguration(), myItemResourceValue);
    String doc = (def == null) ? null : def.getDocValue(null);
    return (doc != null && StringUtil.containsIgnoreCase(doc, DEPRECATED));
  }

  public boolean isPublicAttribute() {
    if (!myItemResourceValue.isFrameworkAttr()) {
      return true;
    }
    Configuration configuration = mySourceTheme.getConfiguration();
    IAndroidTarget target = configuration.getTarget();
    if (target == null) {
      LOG.error("Unable to get IAndroidTarget.");
      return false;
    }

    AndroidTargetData androidTargetData = AndroidTargetData.getTargetData(target, configuration.getModule());
    if (androidTargetData == null) {
      LOG.error("Unable to get AndroidTargetData.");
      return false;
    }

    return androidTargetData.isResourcePublic(ResourceType.ATTR.getName(), getName());
  }
}
