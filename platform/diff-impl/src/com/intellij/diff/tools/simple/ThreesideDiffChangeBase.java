/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.tools.simple;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.MergeLineFragment;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.TextDiffType;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ThreesideDiffChangeBase {
  @NotNull private ConflictType myType;

  public ThreesideDiffChangeBase(@NotNull MergeLineFragment fragment,
                                 @NotNull List<? extends EditorEx> editors,
                                 @NotNull ComparisonPolicy policy) {
    myType = calcType(fragment, editors, policy);
  }

  //
  // Getters
  //

  public abstract int getStartLine(@NotNull ThreeSide side);

  public abstract int getEndLine(@NotNull ThreeSide side);

  @NotNull
  public TextDiffType getDiffType() {
    return myType.getDiffType();
  }

  @NotNull
  public ConflictType getType() {
    return myType;
  }

  public boolean isConflict() {
    return getDiffType() == TextDiffType.CONFLICT;
  }

  public boolean isChange(@NotNull Side side) {
    return myType.isChange(side);
  }

  public boolean isChange(@NotNull ThreeSide side) {
    switch (side) {
      case LEFT:
        return isChange(Side.LEFT);
      case BASE:
        return true;
      case RIGHT:
        return isChange(Side.RIGHT);
      default:
        throw new IllegalArgumentException(side.toString());
    }
  }

  //
  // Type
  //

  @NotNull
  private static ConflictType calcType(@NotNull MergeLineFragment fragment,
                                       @NotNull List<? extends EditorEx> editors,
                                       @NotNull ComparisonPolicy policy) {
    boolean isLeftEmpty = isIntervalEmpty(fragment, ThreeSide.LEFT);
    boolean isBaseEmpty = isIntervalEmpty(fragment, ThreeSide.BASE);
    boolean isRightEmpty = isIntervalEmpty(fragment, ThreeSide.RIGHT);
    assert !isLeftEmpty || !isBaseEmpty || !isRightEmpty;

    if (isBaseEmpty) {
      if (isLeftEmpty) { // --=
        return new ConflictType(TextDiffType.INSERTED, false, true);
      }
      else if (isRightEmpty) { // =--
        return new ConflictType(TextDiffType.INSERTED, true, false);
      }
      else { // =-=
        boolean equalModifications = compareLeftAndRight(fragment, editors, policy);
        return new ConflictType(equalModifications ? TextDiffType.INSERTED : TextDiffType.CONFLICT);
      }
    }
    else {
      if (isLeftEmpty && isRightEmpty) { // -=-
        return new ConflictType(TextDiffType.DELETED);
      }
      else { // -==, ==-, ===
        boolean unchangedLeft = compareWithBase(fragment, editors, ThreeSide.LEFT);
        boolean unchangedRight = compareWithBase(fragment, editors, ThreeSide.RIGHT);
        assert !unchangedLeft || !unchangedRight;

        if (unchangedLeft) return new ConflictType(isRightEmpty ? TextDiffType.DELETED : TextDiffType.MODIFIED, false, true);
        if (unchangedRight) return new ConflictType(isLeftEmpty ? TextDiffType.DELETED : TextDiffType.MODIFIED, true, false);

        boolean equalModifications = compareLeftAndRight(fragment, editors, policy);
        return new ConflictType(equalModifications ? TextDiffType.MODIFIED : TextDiffType.CONFLICT);
      }
    }
  }

  private static boolean compareLeftAndRight(@NotNull MergeLineFragment fragment,
                                             @NotNull List<? extends EditorEx> editors,
                                             @NotNull ComparisonPolicy policy) {
    CharSequence content1 = getRangeContent(fragment, editors, ThreeSide.LEFT);
    CharSequence content2 = getRangeContent(fragment, editors, ThreeSide.RIGHT);

    if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
      if (content1 == null) content1 = "";
      if (content2 == null) content2 = "";
    }

    if (content1 == null && content2 == null) return true;
    if (content1 == null ^ content2 == null) return false;

    return ComparisonManager.getInstance().isEquals(content1, content2, policy);
  }

  private static boolean compareWithBase(@NotNull MergeLineFragment fragment,
                                         @NotNull List<? extends EditorEx> editors,
                                         @NotNull ThreeSide side) {
    CharSequence content1 = getRangeContent(fragment, editors, ThreeSide.BASE);
    CharSequence content2 = getRangeContent(fragment, editors, side);

    return StringUtil.equals(content1, content2);
  }

  @Nullable
  private static CharSequence getRangeContent(@NotNull MergeLineFragment fragment,
                                              @NotNull List<? extends EditorEx> editors,
                                              @NotNull ThreeSide side) {
    DocumentEx document = side.select(editors).getDocument();
    int line1 = fragment.getStartLine(side);
    int line2 = fragment.getEndLine(side);
    if (line1 == line2) return null;
    return DiffUtil.getLinesContent(document, line1, line2);
  }

  private static boolean isIntervalEmpty(@NotNull MergeLineFragment fragment, @NotNull ThreeSide side) {
    return fragment.getStartLine(side) == fragment.getEndLine(side);
  }

  //
  // Helpers
  //

  public static class ConflictType {
    @NotNull private final TextDiffType myType;
    private final boolean myLeftChange;
    private final boolean myRightChange;

    public ConflictType(@NotNull TextDiffType type) {
      this(type, true, true);
    }

    public ConflictType(@NotNull TextDiffType type, boolean leftChange, boolean rightChange) {
      myType = type;
      myLeftChange = leftChange;
      myRightChange = rightChange;
    }

    @NotNull
    public TextDiffType getDiffType() {
      return myType;
    }

    public boolean isLeftChange() {
      return myLeftChange;
    }

    public boolean isRightChange() {
      return myRightChange;
    }

    public boolean isChange(@NotNull Side side) {
      return side.isLeft() ? myLeftChange : myRightChange;
    }
  }
}