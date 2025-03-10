/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.platform.connect;

import android.content.Context;
import com.google.android.accessibility.braille.brailledisplay.FeatureFlagReader;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager.AccessibilityServiceContextProvider;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.common.FakeBrailleDisplayController;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Manages braille display connection. */
public abstract class ConnectManager {
  private AccessibilityServiceContextProvider accessibilityServiceContextProvider;

  /** Types of connection. */
  public enum ConnectType {
    BLUETOOTH,
    USB
  }

  /** Encodes the reason for the most recent state change. */
  public enum Reason {
    UNKNOWN,
    START_STARTED,
    START_SCREEN_ON,
    START_SETTINGS,
    START_BLUETOOTH_TURNED_ON,
    START_BLUETOOTH_TURNED_OFF,
    START_USER_SELECTED_RESCAN,
    START_USB_ATTACH_DETACH, // Triggered when braille display is attached or detached via usb
    STOP_STOPPED,
    STOP_SCREEN_OFF,
    STOP_DISCOVERY_FAILED,
  }

  /** Callback for {@link ConnectManager}. */
  public interface Callback {

    /** Types of connection. */
    enum Type {
      RFCOMM,
      HID
    }

    /** Informed to clear device list. */
    void onDeviceListCleared();

    /** Informed when a device seen or updated. */
    void onDeviceSeenOrUpdated(ConnectableDevice device);

    /** Informed when a device deleted. */
    void onDeviceDeleted(ConnectableDevice device);

    /** Informed when searching status changed. */
    void onSearchStatusChanged();

    /** Informed when search failed. */
    void onSearchFailure();

    /** Informed when connectitiy is enabled/disabled. */
    void onConnectivityEnabled(boolean enabled);

    /** When starting to connect. */
    void onConnectStarted(Type type);

    /** The connection object is ready. */
    void onConnected(D2dConnection connection);

    /** When the connection ends. */
    void onDisconnected();

    /** Informed when connection to the deive is denied. */
    void onDenied(ConnectableDevice device);

    /**
     * An Exception occurred during setup.
     *
     * @param exception the Exception that was thrown
     * @param manual triggered by user
     * @param device the device that failed to connect
     */
    void onConnectFailure(ConnectableDevice device, boolean manual, Exception exception);
  }

  /** Returns connection type. */
  public abstract ConnectType getType();

  /** Instructs this manager to start its behaviors, such as listening for bond events. */
  public abstract void onStart();

  /** Instructs this manager to stop its behaviors. */
  public abstract void onStop();

  /** Instructs this manager to search for new devices. */
  public abstract void startSearch(Reason reason);

  /** Instructs this manager to stop searching for new devices. */
  public abstract void stopSearch(Reason reason);

  /** Instructs this manager to connect to a device. */
  public abstract void connect(ConnectableDevice device, boolean manual);

  /** Instructs this manager to disconnect a connecting or connected device. */
  public abstract void disconnect();

  /** Instructs this manager to forget a device. */
  public abstract void forget(ConnectableDevice device);

  /** Instructs this manager to send packets. */
  public abstract void sendOutgoingPacket(byte[] packet);

  /** Returns if this manager is connecting to a device. */
  public abstract boolean isConnecting();

  /** Returns if this manager is connected to a device. */
  public abstract boolean isConnected();

  /** Returns if this manager is scanning for new devices. */
  public abstract boolean isScanning();

  /** Returns a collection of connectable devices. */
  public abstract Collection<ConnectableDevice> getConnectableDevices();

  /** Returns a set of currently bonded devices. */
  public abstract Set<ConnectableDevice> getBondedDevices();

  /** Returns currently connecting devices. */
  public abstract Optional<ConnectableDevice> getCurrentlyConnectingDevice();

  /** Returns currently connected devices. */
  public abstract Optional<ConnectableDevice> getCurrentlyConnectedDevice();

  /** Returns true if the device is a HID device. */
  public abstract boolean isHidDevice(ConnectableDevice device);

  /** Set accessibility service context provider. */
  public void setAccessibilityServiceContextProvider(
      AccessibilityServiceContextProvider accessibilityServiceContextProvider) {
    this.accessibilityServiceContextProvider = accessibilityServiceContextProvider;
  }

  /** Gets accessibility service context provider. */
  public AccessibilityServiceContextProvider getAccessibilityServiceContextProvider() {
    return accessibilityServiceContextProvider;
  }

  /** Gets braille display controller for hid connection. */
  public FakeBrailleDisplayController getBrailleDisplayController() {
    return new FakeBrailleDisplayController();
  }

  /** Returns whether the HID protocol should be used for connection. */
  public boolean useHid(Context context, ConnectableDevice device) {
    return FeatureFlagReader.isBdHidSupported(context) && isHidDevice(device);
  }
}
