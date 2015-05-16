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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.RenderTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class DrawableRenderer implements TableCellRenderer {
  private final DrawableComponent myComponent;
  private final RenderTask myRenderTask;

  public DrawableRenderer(final @NotNull JTable table, final @Nullable RenderTask renderTask) {
    myRenderTask = renderTask;

    myComponent = new DrawableComponent();
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    myComponent.configure((EditedStyleItem) value, myRenderTask);
    return myComponent;
  }
}
