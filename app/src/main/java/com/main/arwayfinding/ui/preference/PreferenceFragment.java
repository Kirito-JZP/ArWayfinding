package com.main.arwayfinding.ui.preference;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.main.arwayfinding.databinding.FragmentPreferenceBinding;

public class PreferenceFragment extends Fragment {

    private FragmentPreferenceBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPreferenceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

}