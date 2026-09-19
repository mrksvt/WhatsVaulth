package com.mrksvt.waen.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mrksvt.waen.R;
import com.mrksvt.waen.databinding.FragmentTtsBinding;
import com.mrksvt.waen.databinding.ItemTtsContactBinding;
import com.mrksvt.waen.utils.ContactHelper;
import com.mrksvt.waen.xposed.features.voice_tts.app.db.VoiceTtsStore;
import com.mrksvt.waen.xposed.features.voice_tts.app.db.dao.ContactNoteCounts;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TtsFragment extends Fragment {

    public static final String PREF_CONTACT_VOICE_TTS = "contact_voice_tts";

    private FragmentTtsBinding binding;
    private ContactAdapter adapter;
    private OnBackPressedCallback backCallback;
    private final List<ContactNoteCounts> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTtsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        var prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        binding.swEnable.setChecked(prefs.getBoolean(PREF_CONTACT_VOICE_TTS, false));
        binding.swEnable.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean(PREF_CONTACT_VOICE_TTS, checked).apply());

        adapter = new ContactAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::loadContacts);

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                getChildFragmentManager().popBackStack();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        getChildFragmentManager().addOnBackStackChangedListener(() -> {
            boolean childOpen = getChildFragmentManager().getBackStackEntryCount() > 0;
            binding.ttsChildContainer.setVisibility(childOpen ? View.VISIBLE : View.GONE);
            backCallback.setEnabled(childOpen);
            if (!childOpen) loadContacts();
        });

        loadContacts();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && getChildFragmentManager().getBackStackEntryCount() == 0) {
            loadContacts();
        }
    }

    private void loadContacts() {
        if (binding == null) return;
        items.clear();
        items.addAll(VoiceTtsStore.INSTANCE.getInstance(requireContext()).messageHashDao().contacts());
        binding.swipeRefresh.setRefreshing(false);
        boolean empty = items.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private String displayName(String jid) {
        String stored = VoiceTtsStore.INSTANCE.getInstance(requireContext())
                .contactNameDao().get(jid);
        if (stored != null && !stored.isEmpty()) return stored;
        String name = ContactHelper.getContactName(requireContext(), jid);
        if (name != null && !name.isEmpty()) return name;
        return jid.contains("@") ? jid.substring(0, jid.indexOf('@')) : jid;
    }

    private void openContact(ContactNoteCounts c) {
        binding.ttsChildContainer.setVisibility(View.VISIBLE);
        VoiceNotesFragment f = VoiceNotesFragment.newInstance(c.getContactId(), displayName(c.getContactId()));
        getChildFragmentManager().beginTransaction()
                .replace(R.id.tts_child_container, f)
                .addToBackStack("voice_notes")
                .commit();
    }

    private class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemTtsContactBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ContactNoteCounts c = items.get(position);
            holder.item.tvContactName.setText(displayName(c.getContactId()));
            holder.item.tvCount.setText(getString(R.string.tts_note_count, c.getNoteCount(), c.getTrainedCount())
                    + " · " + dateFormat.format(new Date(c.getLastAt())));
            holder.itemView.setOnClickListener(v -> openContact(c));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemTtsContactBinding item;

            VH(ItemTtsContactBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
