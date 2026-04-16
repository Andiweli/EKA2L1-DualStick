/*
 * Copyright (c) 2026 EKA2L1 Team
 *
 * This file is part of EKA2L1 project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.github.eka2l1.settings;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

public final class InputBinding {
    public static final int INPUT_LEFT_STICK_UP = -1001;
    public static final int INPUT_LEFT_STICK_DOWN = -1002;
    public static final int INPUT_LEFT_STICK_LEFT = -1003;
    public static final int INPUT_LEFT_STICK_RIGHT = -1004;
    public static final int INPUT_RIGHT_STICK_UP = -1005;
    public static final int INPUT_RIGHT_STICK_DOWN = -1006;
    public static final int INPUT_RIGHT_STICK_LEFT = -1007;
    public static final int INPUT_RIGHT_STICK_RIGHT = -1008;

    public static final float ANALOG_DIRECTION_THRESHOLD = 0.50f;
    public static final float ANALOG_CAPTURE_THRESHOLD = 0.40f;
    public static final float DPAD_HAT_THRESHOLD = 0.50f;
    public static final float DPAD_HAT_CAPTURE_THRESHOLD = 0.35f;
    private static final float DEFAULT_DEADZONE = 0.25f;
    private static final float BINDING_MIN_DEADZONE = 0.08f;
    private static final float BINDING_MAX_DEADZONE = 0.18f;

    private InputBinding() {
    }

    public static boolean isJoystickMotionEvent(@NonNull MotionEvent event) {
        int source = event.getSource();
        return ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)
                || ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD);
    }

    public static boolean isAnalogDirectionCode(int inputCode) {
        return inputCode <= INPUT_LEFT_STICK_UP && inputCode >= INPUT_RIGHT_STICK_RIGHT;
    }

    public static float getLeftStickX(@NonNull MotionEvent event) {
        return getCenteredAxis(event, MotionEvent.AXIS_X);
    }

    public static float getLeftStickY(@NonNull MotionEvent event) {
        return getCenteredAxis(event, MotionEvent.AXIS_Y);
    }

    public static float getRightStickX(@NonNull MotionEvent event) {
        AxisPair pair = resolveRightStickAxes(event, false);
        return pair == null ? 0.0f : getCenteredAxis(event, pair.axisX);
    }

    public static float getRightStickY(@NonNull MotionEvent event) {
        AxisPair pair = resolveRightStickAxes(event, false);
        return pair == null ? 0.0f : getCenteredAxis(event, pair.axisY);
    }

    public static float getDpadHatX(@NonNull MotionEvent event) {
        return getCenteredAxis(event, MotionEvent.AXIS_HAT_X);
    }

    public static float getDpadHatY(@NonNull MotionEvent event) {
        return getCenteredAxis(event, MotionEvent.AXIS_HAT_Y);
    }

    public static int detectBindableInput(@NonNull MotionEvent event) {
        int action = event.getActionMasked();
        if ((action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_HOVER_MOVE)
                || !isJoystickMotionEvent(event)) {
            return Integer.MAX_VALUE;
        }

        CaptureCandidate dpadCandidate = dominantDirection(
                getBindingDpadHatX(event),
                getBindingDpadHatY(event),
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                DPAD_HAT_CAPTURE_THRESHOLD);

        CaptureCandidate leftCandidate = dominantDirection(
                getBindingLeftStickX(event),
                getBindingLeftStickY(event),
                INPUT_LEFT_STICK_LEFT,
                INPUT_LEFT_STICK_RIGHT,
                INPUT_LEFT_STICK_UP,
                INPUT_LEFT_STICK_DOWN,
                ANALOG_CAPTURE_THRESHOLD);

        CaptureCandidate rightCandidate = dominantDirection(
                getBindingRightStickX(event),
                getBindingRightStickY(event),
                INPUT_RIGHT_STICK_LEFT,
                INPUT_RIGHT_STICK_RIGHT,
                INPUT_RIGHT_STICK_UP,
                INPUT_RIGHT_STICK_DOWN,
                ANALOG_CAPTURE_THRESHOLD);

        CaptureCandidate strongest = dpadCandidate;
        if (leftCandidate.strength > strongest.strength) {
            strongest = leftCandidate;
        }
        if (rightCandidate.strength > strongest.strength) {
            strongest = rightCandidate;
        }

        return strongest.strength > 0.0f ? strongest.inputCode : Integer.MAX_VALUE;
    }

    public static String getInputName(int inputCode) {
        switch (inputCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return "D-pad Up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "D-pad Down";
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "D-pad Left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "D-pad Right";
            case INPUT_LEFT_STICK_UP:
                return "Left Stick Up";
            case INPUT_LEFT_STICK_DOWN:
                return "Left Stick Down";
            case INPUT_LEFT_STICK_LEFT:
                return "Left Stick Left";
            case INPUT_LEFT_STICK_RIGHT:
                return "Left Stick Right";
            case INPUT_RIGHT_STICK_UP:
                return "Right Stick Up";
            case INPUT_RIGHT_STICK_DOWN:
                return "Right Stick Down";
            case INPUT_RIGHT_STICK_LEFT:
                return "Right Stick Left";
            case INPUT_RIGHT_STICK_RIGHT:
                return "Right Stick Right";
            default:
                return sanitizeKeyName(KeyEvent.keyCodeToString(inputCode));
        }
    }

    private static float getBindingLeftStickX(@NonNull MotionEvent event) {
        return getCenteredAxisForBinding(event, MotionEvent.AXIS_X);
    }

    private static float getBindingLeftStickY(@NonNull MotionEvent event) {
        return getCenteredAxisForBinding(event, MotionEvent.AXIS_Y);
    }

    private static float getBindingRightStickX(@NonNull MotionEvent event) {
        AxisPair pair = resolveRightStickAxes(event, true);
        return pair == null ? 0.0f : getCenteredAxisForBinding(event, pair.axisX);
    }

    private static float getBindingRightStickY(@NonNull MotionEvent event) {
        AxisPair pair = resolveRightStickAxes(event, true);
        return pair == null ? 0.0f : getCenteredAxisForBinding(event, pair.axisY);
    }

    private static float getBindingDpadHatX(@NonNull MotionEvent event) {
        return getCenteredAxisForBinding(event, MotionEvent.AXIS_HAT_X);
    }

    private static float getBindingDpadHatY(@NonNull MotionEvent event) {
        return getCenteredAxisForBinding(event, MotionEvent.AXIS_HAT_Y);
    }

    private static String sanitizeKeyName(String keyName) {
        if (keyName == null || keyName.length() == 0) {
            return "Unknown";
        }

        String sanitized = keyName.startsWith("KEYCODE_")
                ? keyName.substring("KEYCODE_".length())
                : keyName;
        sanitized = sanitized.replace('_', ' ').trim();

        StringBuilder builder = new StringBuilder(sanitized.length());
        boolean capitalize = true;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (capitalize && Character.isLetter(c)) {
                builder.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }

            if (c == ' ') {
                capitalize = true;
            }
        }
        return builder.toString();
    }

    private static CaptureCandidate dominantDirection(float x, float y, int leftCode, int rightCode,
                                                      int upCode, int downCode, float threshold) {
        float absX = Math.abs(x);
        float absY = Math.abs(y);

        if (absX < threshold && absY < threshold) {
            return new CaptureCandidate(Integer.MAX_VALUE, 0.0f);
        }

        if (absX >= absY) {
            return new CaptureCandidate(x < 0.0f ? leftCode : rightCode, absX);
        }

        return new CaptureCandidate(y < 0.0f ? upCode : downCode, absY);
    }

    private static float getCenteredAxis(@NonNull MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return 0.0f;
        }

        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) {
            return 0.0f;
        }

        float value = event.getAxisValue(axis);
        float flat = Math.max(range.getFlat(), DEFAULT_DEADZONE);

        if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) {
            flat = Math.min(flat, 0.10f);
        }

        return Math.abs(value) > flat ? value : 0.0f;
    }

    private static float getCenteredAxisForBinding(@NonNull MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return 0.0f;
        }

        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        if (range == null) {
            return 0.0f;
        }

        float value = event.getAxisValue(axis);
        float flat = range.getFlat();

        if (axis == MotionEvent.AXIS_HAT_X || axis == MotionEvent.AXIS_HAT_Y) {
            flat = 0.05f;
        } else {
            flat = Math.max(BINDING_MIN_DEADZONE, Math.min(flat, BINDING_MAX_DEADZONE));
        }

        return Math.abs(value) > flat ? value : 0.0f;
    }

    private static AxisPair resolveRightStickAxes(@NonNull MotionEvent event, boolean forBinding) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return null;
        }

        boolean hasRxRy = hasBidirectionalAxisPair(device, event.getSource(), MotionEvent.AXIS_RX, MotionEvent.AXIS_RY);
        boolean hasZRz = hasBidirectionalAxisPair(device, event.getSource(), MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);

        if (hasRxRy && !hasZRz) {
            return new AxisPair(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY);
        }

        if (hasZRz && !hasRxRy) {
            return new AxisPair(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);
        }

        if (hasRxRy && hasZRz) {
            float rxryStrength = getAxisPairStrength(event, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, forBinding);
            float zrzStrength = getAxisPairStrength(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, forBinding);

            if (rxryStrength >= zrzStrength) {
                return new AxisPair(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY);
            }
            return new AxisPair(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ);
        }

        return null;
    }

    private static float getAxisPairStrength(@NonNull MotionEvent event, int axisX, int axisY, boolean forBinding) {
        if (forBinding) {
            return Math.abs(getCenteredAxisForBinding(event, axisX)) + Math.abs(getCenteredAxisForBinding(event, axisY));
        }
        return Math.abs(getCenteredAxis(event, axisX)) + Math.abs(getCenteredAxis(event, axisY));
    }

    private static boolean hasBidirectionalAxisPair(InputDevice device, int source, int axisX, int axisY) {
        return isBidirectionalAxis(device, source, axisX) && isBidirectionalAxis(device, source, axisY);
    }

    private static boolean isBidirectionalAxis(InputDevice device, int source, int axis) {
        InputDevice.MotionRange range = device.getMotionRange(axis, source);
        return range != null && range.getMin() < 0.0f && range.getMax() > 0.0f;
    }

    private static final class AxisPair {
        final int axisX;
        final int axisY;

        AxisPair(int axisX, int axisY) {
            this.axisX = axisX;
            this.axisY = axisY;
        }
    }

    private static final class CaptureCandidate {
        final int inputCode;
        final float strength;

        CaptureCandidate(int inputCode, float strength) {
            this.inputCode = inputCode;
            this.strength = strength;
        }
    }
}
