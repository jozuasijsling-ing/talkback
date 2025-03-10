/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback;

import static android.media.AudioAttributes.USAGE_GAME;
import static android.media.AudioAttributes.USAGE_MEDIA;

import android.content.Context;
import android.content.Intent;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.monitor.CallStateMonitor;
import com.google.android.accessibility.utils.monitor.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.monitor.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.monitor.MediaRecorderMonitor;
import com.google.android.accessibility.utils.monitor.SpeechStateMonitor;
import com.google.android.accessibility.utils.monitor.VoiceActionDelegate;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Monitors voice actions from other applications. Prevents TalkBack's audio feedback from
 * interfering with voice assist applications.
 */
public class VoiceActionMonitor implements VoiceActionDelegate {

  private static final String TAG = "VoiceActionMonitor";

  private static final int SSB = 0;
  private static final int MEDIA_RECORDER = 1;
  private static final int AUDIO_PLAYBACK = 2;
  private static final int CALL_STATE = 3;

  @VisibleForTesting
  // The waiting time to ensure "Talkback on" is read out.
  protected static final int WAITING_INITIAL_ANNOUNCEMENT_FINISHED_MS = 2000;

  private final TalkBackService service;
  private final MediaRecorderMonitor mediaRecorderMonitor;
  private final AudioPlaybackMonitor audioPlaybackMonitor;
  private final CallStateMonitor callStateMonitor;
  private final SpeechStateMonitor speechStateMonitor;

  private boolean skipInterruption = true;

  /** Defines voice action sources that would interrupt TalkBack audio. */
  @IntDef({
    SSB,
    MEDIA_RECORDER,
    AUDIO_PLAYBACK,
    CALL_STATE,
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface VoiceActionSource {}

  private final MediaRecorderMonitor.MicrophoneStateChangedListener microphoneStateChangedListener =
      () -> {
        if (!isHeadphoneOn()) {
          interruptTalkBackAudio(MEDIA_RECORDER);
        }
      };

  private final AudioPlaybackMonitor.AudioPlaybackStateChangedListener
      audioPlaybackStateChangedListener =
          (configs) -> {
            if (skipInterruption) {
              return;
            }
            // No need to interrupt if only media and game playback are activated.
            for (AudioPlaybackConfiguration config : configs) {
              int usage = config.getAudioAttributes().getUsage();
              if (usage == USAGE_MEDIA || usage == USAGE_GAME) {
                continue;
              }

              LogUtils.v(
                  TAG,
                  "AudioPlaybackStateChangedListener: interruptTalkBackAudio (config=%s)",
                  config);
              interruptTalkBackAudio(AUDIO_PLAYBACK);
              break;
            }
          };

  private final CallStateMonitor.CallStateChangedListener callStateChangedListener =
      (oldState, newState) -> {
        if (newState == TelephonyManager.CALL_STATE_OFFHOOK) {
          interruptTalkBackAudio(CALL_STATE);
        }
      };

  public VoiceActionMonitor(
      TalkBackService service,
      CallStateMonitor callStateMonitor,
      SpeechStateMonitor speechStateMonitor) {
    this.service = service;

    this.speechStateMonitor = speechStateMonitor;

    mediaRecorderMonitor = new MediaRecorderMonitor(service);
    mediaRecorderMonitor.setMicrophoneStateChangedListener(microphoneStateChangedListener);

    audioPlaybackMonitor = new AudioPlaybackMonitor(service);
    audioPlaybackMonitor.setAudioPlaybackStateChangedListener(audioPlaybackStateChangedListener);

    this.callStateMonitor = callStateMonitor;
    callStateMonitor.addCallStateChangedListener(callStateChangedListener);
  }

  /** Used for test only. Updates phone call state in instrumentation test. */
  // TODO: Revisit this method when instrumentation test is settled down.
  public void onReceivePhoneStateChangedIntent(Context context, Intent intent) {
    if (callStateMonitor != null
        && CallStateMonitor.STATE_CHANGED_FILTER.hasAction(intent.getAction())) {
      callStateMonitor.onReceive(context, intent);
    } else {
      throw new RuntimeException("Unable to send intent.");
    }
  }

  public void onSpeakingForcedFeedback() {
    interruptOtherAudio();
  }

  /** Returns {@code true} if audio play back is active. */
  public boolean isAudioPlaybackActive() {
    return audioPlaybackMonitor.isAudioPlaybackActive() || speechStateMonitor.isSpeaking();
  }

  /** Returns {@code true} if microphone is active and the user is not using a headset. */
  public boolean isMicrophoneActiveAndHeadphoneOff() {
    return isMicrophoneActive() && !isHeadphoneOn();
  }

  /**
   * Returns {@code true} if voice recognition/dictation is active and the user is not using a
   * headset.
   */
  public boolean isSsbActiveAndHeadphoneOff() {
    return isVoiceRecognitionActive() && !isHeadphoneOn();
  }

  /** Returns {@code true} if phone call is active. */
  public boolean isPhoneCallActive() {
    return callStateMonitor != null && callStateMonitor.isPhoneCallActive();
  }

  /**
   * Returns the current device call state. Returns {@link TelephonyManager#CALL_STATE_IDLE} if the
   * device doesn't support telephony feature.
   */
  public int getCurrentCallState() {
    if (callStateMonitor == null) {
      return TelephonyManager.CALL_STATE_IDLE;
    } else {
      return callStateMonitor.getCurrentCallState();
    }
  }

  public void onResumeInfrastructure() {
    mediaRecorderMonitor.onResumeInfrastructure();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioPlaybackMonitor.onResumeInfrastructure();
    }
  }

  public void onSuspendInfrastructure() {
    mediaRecorderMonitor.onSuspendInfrastructure();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioPlaybackMonitor.onSuspendInfrastructure();
    }
  }

  public boolean isHeadphoneOn() {
    return HeadphoneStateMonitor.isHeadphoneOn(service);
  }

  @Override
  public boolean isVoiceRecognitionActive() {
    return mediaRecorderMonitor.isVoiceRecognitionActive();
  }

  @Override
  public boolean isMicrophoneActive() {
    return mediaRecorderMonitor.isMicrophoneActive() || speechStateMonitor.isListening();
  }

  private void interruptTalkBackAudio(@VoiceActionSource int source) {
    LogUtils.v(
        TAG, "Interrupt TalkBack audio. voice action source=%s", voiceActionSourceToString(source));
    service.interruptAllFeedback(false /* stopTtsSpeechCompletely */);
  }

  public void onTtsReady() {
    if (skipInterruption) {
      Handler mainHandler = new Handler(Looper.getMainLooper());
      mainHandler.postDelayed(
          () -> skipInterruption = false, WAITING_INITIAL_ANNOUNCEMENT_FINISHED_MS);
    }
  }

  private void interruptOtherAudio() {}

  @SuppressWarnings("MissingDefault") // This switch statement is exhaustive.
  private static String voiceActionSourceToString(@VoiceActionSource int source) {
    switch (source) {
      case AUDIO_PLAYBACK:
        return "AUDIO_PLAYBACK";
      case CALL_STATE:
        return "CALL_STATE";
      case MEDIA_RECORDER:
        return "MEDIA_RECORDER";
      case SSB:
        return "SSB";
    }
    return "UNKNOWN_VOICE_ACTION_SOURCE";
  }
}
