package com.hfdlys.bjtuselfservice.fragment.loading;

import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.hfdlys.bjtuselfservice.R;


public class LoadingFragment extends DialogFragment {
    public LoadingFragment() {
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());
        dialog.setContentView(R.layout.dialog_loading);
        dialog.setCancelable(false);
        return dialog;
    }


}