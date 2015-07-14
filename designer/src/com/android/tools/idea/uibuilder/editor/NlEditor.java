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
package com.android.tools.idea.uibuilder.editor;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class NlEditor extends UserDataHolderBase implements FileEditor {
  private final AndroidFacet myFacet;
  private final VirtualFile myFile;
  private NlEditorPanel myEditorPanel;

  public NlEditor(AndroidFacet facet, VirtualFile file) {
    myFacet = facet;
    myFile = file;
  }

  @NonNull
  @Override
  public NlEditorPanel getComponent() {
    if (myEditorPanel == null) {
      myEditorPanel = new NlEditorPanel(this, myFacet, myFile);
    }
    return myEditorPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getComponent().getPreferredFocusedComponent();
  }

  @NonNull
  @Override
  public String getName() {
    return "Nele";
  }

  @Override
  @NonNull
  public FileEditorState getState(@NonNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NonNull FileEditorState state) {
  }

  @Override
  public void dispose() {
    getComponent().dispose();
  }

  @Override
  public void selectNotify() {
    getComponent().activate();
  }

  @Override
  public void deselectNotify() {
    getComponent().deactivate();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void addPropertyChangeListener(@NonNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NonNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }
}
