package com.mrksvt.waen.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.mrksvt.waen.R;
import com.mrksvt.waen.databinding.FragmentVoiceNotesBinding;
import com.mrksvt.waen.databinding.ItemVoiceNoteBinding;
import com.mrksvt.waen.xposed.features.voice_tts.app.VoiceTtsWorker;
import com.mrksvt.waen.xposed.features.voice_tts.app.db.VoiceTtsStore;
import com.mrksvt.waen.xposed.features.voice_tts.app.db.entity.MessageHashEntity;
import com.mrksvt.waen.xposed.features.voice_tts.core.AudioDecoder;
import com.mrksvt.waen.xposed.features.voice_tts.core.SpeakerEmbedding;
import com.mrksvt.waen.xposed.features.voice_tts.core.VoiceExpression;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class VoiceNotesFragment extends Fragment {

    private static final String ARG_CONTACT_ID = "contact_id";
    private static final String ARG_CONTACT_NAME = "contact_name";

    private FragmentVoiceNotesBinding binding;
    private String contactId;
    private NoteAdapter adapter;
    private MediaPlayer player;
    private String playingHash;
    private int playToken;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<MessageHashEntity> items = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault());

    public static VoiceNotesFragment newInstance(String contactId, String contactName) {
        VoiceNotesFragment f = new VoiceNotesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CONTACT_ID, contactId);
        args.putString(ARG_CONTACT_NAME, contactName);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentVoiceNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        contactId = requireArguments().getString(ARG_CONTACT_ID, "");
        binding.tvTitle.setText(requireArguments().getString(ARG_CONTACT_NAME, contactId));
        binding.btnBack.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });

        adapter = new NoteAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(this::loadNotes);
        loadNotes();
    }

    private void loadNotes() {
        if (binding == null) return;
        stopPlayback();
        items.clear();
        items.addAll(VoiceTtsStore.INSTANCE.getInstance(requireContext())
                .messageHashDao().allForContact(contactId));
        binding.swipeRefresh.setRefreshing(false);
        boolean empty = items.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void stopPlayback() {
        playToken++;
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        playingHash = null;
    }

    private void togglePlayback(MessageHashEntity entity) {
        if (playingHash != null && playingHash.equals(entity.getMessageHash())) {
            stopPlayback();
            adapter.notifyDataSetChanged();
            return;
        }
        stopPlayback();
        final File src = new File(entity.getAudioPath());
        final String hash = entity.getMessageHash();
        if (!src.exists()) {
            Toast.makeText(requireContext(), R.string.tts_play_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        // WhatsApp voice notes are Ogg/Opus, which MediaPlayer cannot decode on
        // Android Go devices: convert once to a cached PCM16 WAV, play that.
        final Context appCtx = requireContext().getApplicationContext();
        final int myToken = playToken;
        new Thread(() -> {
            final File wav = playableFile(appCtx, src, hash);
            mainHandler.post(() -> {
                if (binding == null || myToken != playToken) return;
                if (wav == null) {
                    Toast.makeText(appCtx, R.string.tts_play_failed, Toast.LENGTH_SHORT).show();
                } else {
                    startPlayback(wav, hash);
                }
            });
        }).start();
    }

    private static File playableFile(Context ctx, File src, String hash) {
        if (src.getName().endsWith(".wav")) return src;
        File wav = new File(ctx.getCacheDir(),
                "tts_play_" + hash.replaceAll("[^A-Za-z0-9._-]", "_") + ".wav");
        if (wav.exists() && wav.length() > 44) return wav;
        short[] pcm = AudioDecoder.INSTANCE.decodeToPcm(src);
        if (pcm == null || pcm.length == 0) return null;
        try {
            writeWav(wav, pcm, SpeakerEmbedding.SAMPLE_RATE);
            return wav;
        } catch (IOException e) {
            return null;
        }
    }

    private void startPlayback(File file, String hash) {
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                stopPlayback();
                if (binding != null) adapter.notifyDataSetChanged();
            });
            player.prepare();
            player.start();
            playingHash = hash;
            adapter.notifyDataSetChanged();
        } catch (Exception e) {
            stopPlayback();
            Toast.makeText(requireContext(), R.string.tts_play_failed, Toast.LENGTH_SHORT).show();
            adapter.notifyDataSetChanged();
        }
    }

    private static void writeWav(File out, short[] pcm, int sampleRate) throws IOException {
        int dataLen = pcm.length * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(new byte[]{'R', 'I', 'F', 'F'});
        buf.putInt(36 + dataLen);
        buf.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * 2);
        buf.putShort((short) 2);
        buf.putShort((short) 16);
        buf.put(new byte[]{'d', 'a', 't', 'a'});
        buf.putInt(dataLen);
        buf.asShortBuffer().put(pcm);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(buf.array());
        }
    }

    private int expressionLabelRes(String expression) {
        return switch (expression) {
            case VoiceExpression.AUTO -> R.string.tts_expression_auto;
            case VoiceExpression.BAHAGIA -> R.string.tts_expression_bahagia;
            case VoiceExpression.SEDIH -> R.string.tts_expression_sedih;
            case VoiceExpression.SEMANGAT -> R.string.tts_expression_semangat;
            default -> R.string.tts_expression_normal;
        };
    }

    private void showTrainDialog(MessageHashEntity entity) {
        String[] labels = {
                getString(R.string.tts_expression_auto),
                getString(R.string.tts_expression_normal),
                getString(R.string.tts_expression_bahagia),
                getString(R.string.tts_expression_sedih),
                getString(R.string.tts_expression_semangat),
        };
        String[] values = {
                VoiceExpression.AUTO,
                VoiceExpression.NORMAL,
                VoiceExpression.BAHAGIA,
                VoiceExpression.SEDIH,
                VoiceExpression.SEMANGAT,
        };
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(entity.getExpression())) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tts_train_expression_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String expression = values[which];
                    VoiceTtsStore.INSTANCE.getInstance(requireContext())
                            .messageHashDao().markTrained(entity.getMessageHash(), expression);
                    Data data = new Data.Builder()
                            .putString(VoiceTtsWorker.KEY_ACTION, VoiceTtsWorker.ACTION_EXTRACT_EMBEDDING)
                            .putString(VoiceTtsWorker.KEY_CONTACT_ID, contactId)
                            .putString(VoiceTtsWorker.KEY_MESSAGE_HASH, entity.getMessageHash())
                            .putString(VoiceTtsWorker.KEY_EXPRESSION, expression)
                            .build();
                    OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(VoiceTtsWorker.class)
                            .setInputData(data)
                            .build();
                    WorkManager.getInstance(requireContext()).enqueue(request);
                    Toast.makeText(requireContext(),
                            getString(R.string.tts_train_started, labels[which]), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadNotes();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemVoiceNoteBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            MessageHashEntity e = items.get(position);
            holder.item.tvTitle.setText(dateFormat.format(new Date(e.getCreatedAt())));
            String meta = formatDuration(e.getDurationMs()) + " · ";
            if (e.getTrained()) {
                meta += getString(R.string.tts_trained, getString(expressionLabelRes(e.getExpression())));
            } else {
                meta += getString(R.string.tts_note_untrained);
            }
            holder.item.tvMeta.setText(meta);
            holder.item.btnPlay.setImageResource(
                    e.getMessageHash().equals(playingHash) ? R.drawable.ic_pause : R.drawable.ic_play);
            holder.item.btnPlay.setOnClickListener(v -> togglePlayback(e));
            holder.item.btnTrain.setOnClickListener(v -> showTrainDialog(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemVoiceNoteBinding item;

            VH(ItemVoiceNoteBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }

    private static String formatDuration(long durationMs) {
        long sec = durationMs / 1000;
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPlayback();
        binding = null;
    }
}
