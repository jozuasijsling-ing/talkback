/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.interfaces;

import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Exposes some BrailleDisplay behavior to TalkBack. */
public interface BrailleDisplayForTalkBack {
  /** Starts braille display. */
  void start();
  /** Stops braille display. */
  void stop();
  /** Notifies receiving accessibility event. */
  void onAccessibilityEvent(AccessibilityEvent accessibilityEvent);

  /** Notifies receiving key event. */
  boolean onKeyEvent(KeyEvent keyEvent);

  /** Notifies receiving reading control changed with overlay shown event. */
  void onReadingControlChanged(CharSequence readingControlDescription);
  /** Switches braille display on or off. */
  void switchBrailleDisplayOnOrOff();
}
