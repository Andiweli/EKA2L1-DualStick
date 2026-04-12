/*
 * Copyright (c) 2020 EKA2L1 Team
 *
 * This file is part of EKA2L1 project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.github.eka2l1.settings;

import android.os.Bundle;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.github.eka2l1.R;
import com.github.eka2l1.config.ProfileModel;
import com.github.eka2l1.config.ProfilesManager;
import com.github.eka2l1.emu.Keycode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.github.eka2l1.emu.Constants.KEY_CONFIG_PATH;

public class KeyMapperFragment extends Fragment implements View.OnClickListener, View.OnLongClickListener {
    private final SparseIntArray defaultKeyMap = KeyMapper.getDefaultKeyMap();
    private static final SparseIntArray idToSymbianKey = new SparseIntArray();
    private final SparseArray<Button> buttonById = new SparseArray<>();
    private static SparseIntArray androidToSymbian;
    private ProfileModel params;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_keymapper, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHasOptionsMenu(true);
        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.pref_control_key_binding_sect_title);

        Bundle args = getArguments();
        String path = args.getString(KEY_CONFIG_PATH);
        if (path == null) {
            Toast.makeText(getContext(), "Error", Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStackImmediate();
            return;
        }

        params = ProfilesManager.loadConfig(new File(path));
        if (params == null) {
            Toast.makeText(getContext(), "Error", Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStackImmediate();
            return;
        }

        setupButton(R.id.virtual_key_left_soft, Keycode.KEY_SOFT_LEFT);
        setupButton(R.id.virtual_key_right_soft, Keycode.KEY_SOFT_RIGHT);
        setupButton(R.id.virtual_key_d, Keycode.KEY_SEND);
        setupButton(R.id.virtual_key_c, Keycode.KEY_CLEAR);
        setupButton(R.id.virtual_key_left, Keycode.KEY_LEFT);
        setupButton(R.id.virtual_key_right, Keycode.KEY_RIGHT);
        setupButton(R.id.virtual_key_up, Keycode.KEY_UP);
        setupButton(R.id.virtual_key_down, Keycode.KEY_DOWN);
        setupButton(R.id.virtual_key_f, Keycode.KEY_FIRE);
        setupButton(R.id.virtual_key_1, Keycode.KEY_NUM1);
        setupButton(R.id.virtual_key_2, Keycode.KEY_NUM2);
        setupButton(R.id.virtual_key_3, Keycode.KEY_NUM3);
        setupButton(R.id.virtual_key_4, Keycode.KEY_NUM4);
        setupButton(R.id.virtual_key_5, Keycode.KEY_NUM5);
        setupButton(R.id.virtual_key_6, Keycode.KEY_NUM6);
        setupButton(R.id.virtual_key_7, Keycode.KEY_NUM7);
        setupButton(R.id.virtual_key_8, Keycode.KEY_NUM8);
        setupButton(R.id.virtual_key_9, Keycode.KEY_NUM9);
        setupButton(R.id.virtual_key_0, Keycode.KEY_NUM0);
        setupButton(R.id.virtual_key_star, Keycode.KEY_STAR);
        setupButton(R.id.virtual_key_pound, Keycode.KEY_POUND);

        SparseIntArray keyMap = params.keyMappings;
        androidToSymbian = keyMap == null ? defaultKeyMap.clone() : keyMap.clone();
        refreshButtonLabels();
    }

    private void setupButton(int resId, int index) {
        idToSymbianKey.put(resId, index);
        Button button = requireView().findViewById(resId);
        button.setOnClickListener(this);
        button.setOnLongClickListener(this);
        button.setTag(button.getText().toString());
        buttonById.put(resId, button);
    }

    private void refreshButtonLabels() {
        for (int i = 0; i < buttonById.size(); i++) {
            int resId = buttonById.keyAt(i);
            Button button = buttonById.valueAt(i);
            int canvasKey = idToSymbianKey.get(resId);
            String label = (String) button.getTag();
            String mappings = getMappingsDescription(canvasKey);
            button.setText(getString(R.string.mapping_button_text, label, mappings));
        }
    }

    @Override
    public void onClick(View v) {
        int canvasKey = idToSymbianKey.get(v.getId());
        if (canvasKey != 0) {
            showMappingDialog(canvasKey);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        int canvasKey = idToSymbianKey.get(v.getId());
        if (canvasKey == 0) {
            return false;
        }

        boolean removed = clearBindingsForCanvasKey(canvasKey);
        if (removed) {
            params.keyMappings = androidToSymbian;
            ProfilesManager.saveConfig(params);
            refreshButtonLabels();
            Toast.makeText(getContext(), "Binding cleared", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "No binding to clear", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void showMappingDialog(int canvasKey) {
        TextView captureView = new TextView(requireContext());
        int padding = (int) (24 * requireContext().getResources().getDisplayMetrics().density);
        captureView.setPadding(padding, padding, padding, padding);
        captureView.setFocusableInTouchMode(true);
        captureView.setClickable(true);
        captureView.setText(getString(R.string.mapping_dialog_message_full,
                getMappingsDescription(canvasKey),
                getString(R.string.mapping_dialog_hint)));

        final AlertDialog[] dialogRef = new AlertDialog[1];

        View.OnKeyListener keyListener = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
                return true;
            }

            bindInput(canvasKey, keyCode);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            return true;
        };

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mapping_dialog_title)
                .setView(captureView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogRef[0] = dialog;

        View.OnGenericMotionListener motionListener = (v, event) -> {
            int inputCode = InputBinding.detectBindableInput(event);
            if (inputCode == Integer.MAX_VALUE) {
                return false;
            }

            bindInput(canvasKey, inputCode);
            dialog.dismiss();
            return true;
        };

        dialog.setOnShowListener(unused -> {
            captureView.setOnKeyListener(keyListener);
            captureView.setOnGenericMotionListener(motionListener);
            captureView.setFocusable(true);
            captureView.setFocusableInTouchMode(true);
            captureView.requestFocus();

            if (dialog.getWindow() != null) {
                dialog.getWindow().getDecorView().setOnKeyListener(keyListener);
                dialog.getWindow().getDecorView().setOnGenericMotionListener(motionListener);
                dialog.getWindow().getDecorView().requestFocus();
            }
        });

        dialog.show();
    }

    private void bindInput(int canvasKey, int inputCode) {
        deleteBindingForInput(inputCode);
        androidToSymbian.put(inputCode, canvasKey);
        params.keyMappings = androidToSymbian;
        ProfilesManager.saveConfig(params);
        refreshButtonLabels();
    }

    private void deleteBindingForInput(int inputCode) {
        int index = androidToSymbian.indexOfKey(inputCode);
        if (index >= 0) {
            androidToSymbian.removeAt(index);
        }
    }

    private boolean clearBindingsForCanvasKey(int canvasKey) {
        boolean removed = false;
        for (int i = androidToSymbian.size() - 1; i >= 0; i--) {
            if (androidToSymbian.valueAt(i) == canvasKey) {
                androidToSymbian.removeAt(i);
                removed = true;
            }
        }
        return removed;
    }

    private String getMappingsDescription(int canvasKey) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < androidToSymbian.size(); i++) {
            if (androidToSymbian.valueAt(i) == canvasKey) {
                names.add(InputBinding.getInputName(androidToSymbian.keyAt(i)));
            }
        }

        if (names.isEmpty()) {
            return getString(R.string.mapping_unbound);
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(names.get(i));
        }
        return builder.toString();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.keymapper, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            getParentFragmentManager().popBackStackImmediate();
        } else if (itemId == R.id.action_reset_mapping) {
            androidToSymbian = defaultKeyMap.clone();
            params.keyMappings = androidToSymbian;
            ProfilesManager.saveConfig(params);
            refreshButtonLabels();
        }
        return super.onOptionsItemSelected(item);
    }
}
