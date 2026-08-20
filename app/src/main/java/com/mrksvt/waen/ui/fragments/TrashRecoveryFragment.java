package com.mrksvt.waen.ui.fragments;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.mrksvt.waen.ui.fragments.base.BackNavHelper;
import com.mrksvt.waen.R;
import com.mrksvt.waen.adapter.TrashMessageAdapter;
import com.mrksvt.waen.databinding.FragmentTrashRecoveryBinding;
import com.mrksvt.waen.ui.fragments.DeletedMessageDetailFragment;
import com.mrksvt.waen.xposed.bridge.WaeIIFace;
import com.mrksvt.waen.xposed.core.FeatureLoader;

import java.text.DateFormat;
import java.util.Date;
import com.mrksvt.waen.xposed.core.db.entity.DelMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class TrashRecoveryFragment extends Fragment {

    private FragmentTrashRecoveryBinding binding;
    private TrashMessageAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTrashRecoveryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BackNavHelper.install(this);

        // Bug 1 fix: popBackStack instead of onBackPressed
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragment() != null) {
                getParentFragment().getChildFragmentManager().popBackStack();
            } else {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        // Bug 2 fix: toolbar-local menu, no MenuProvider leak
        binding.toolbar.inflateMenu(R.menu.trash_recovery_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_all) {
                clearAll();
                return true;
            }
            if (item.getItemId() == R.id.action_export) {
                exportToJson();
                return true;
            }
            return false;
        });

        // RecyclerView
        adapter = new TrashMessageAdapter(new ArrayList<>());
        adapter.setOnMessageClickListener(message -> {
            androidx.fragment.app.FragmentManager fm = getParentFragment() != null
                    ? getParentFragment().getChildFragmentManager()
                    : requireActivity().getSupportFragmentManager();
            fm.beginTransaction()
                    .replace(R.id.frag_container, DeletedMessageDetailFragment.newInstance(message))
                    .addToBackStack(null)
                    .commit();
        });
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        loadMessages();
    }

    private void showLoading(boolean show) {
        if (getView() == null) return;
        binding.loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    private void showResultState(boolean hasMessages) {
        if (getView() == null) return;
        binding.loadingProgress.setVisibility(View.GONE);
        if (hasMessages) {
            binding.recyclerView.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
        } else {
            binding.recyclerView.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
        }
    }

    private void loadMessages() {
        showLoading(true);

        new Thread(() -> {
            List<DelMessage> messages = new ArrayList<>();
            try {
                WaeIIFace hookBinder = getHookBinder();
                if (hookBinder == null) {
                    mainHandler.post(() -> {
                        if (getView() == null) return;
                        showLoading(false);
                        Toast.makeText(requireContext(),
                                R.string.trash_recovery_wa_not_running, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                String cachePath = "/data/data/" + requireContext().getPackageName() + "/files/trash_cache.json";
                android.os.ParcelFileDescriptor pfd = hookBinder.openFile(cachePath, false);
                if (pfd != null) {
                    String json;
                    try (java.io.InputStream is = new java.io.FileInputStream(pfd.getFileDescriptor())) {
                        byte[] buf = new byte[is.available()];
                        int totalRead = 0;
                        int read;
                        while ((read = is.read(buf, totalRead, buf.length - totalRead)) != -1) {
                            totalRead += read;
                            if (totalRead == buf.length) break;
                        }
                        json = new String(buf, 0, totalRead, java.nio.charset.StandardCharsets.UTF_8);
                    } finally {
                        pfd.close();
                    }
                    if (json != null && !json.isEmpty() && !json.equals("[]")) {
                        org.json.JSONArray arr = new org.json.JSONArray(json);
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject obj = arr.getJSONObject(i);
                            DelMessage msg = new DelMessage();
                            msg.setId(obj.getLong("id"));
                            msg.setJid(obj.optString("jid", null));
                            msg.setMsgid(obj.optString("msgid", null));
                            msg.setTimestamp(obj.optLong("timestamp", 0));
                            msg.setText(obj.isNull("text") ? null : obj.getString("text"));
                            msg.setMediaPath(obj.isNull("mediaPath") ? null : obj.getString("mediaPath"));
                            msg.setMediaType(obj.optInt("mediaType", -1));
                            msg.setSenderName(obj.isNull("senderName") ? null : obj.getString("senderName"));
                            msg.setWa(obj.optString("wa", null));
                            msg.setContact(obj.optString("contact", null));
                            msg.setIntime(obj.optLong("intime", 0));
                            msg.setDeltime(obj.optLong("deltime", 0));
                            msg.setVoiceFileName(obj.optString("voiceFileName", null));
                            msg.setFileId(obj.optString("fileId", null));
                            String msgJid = obj.optString("jid", "");
                            String msgText = obj.isNull("text") ? null : obj.optString("text", null);
                            int msgMediaType = obj.optInt("mediaType", -1);
                            String msgContact = obj.isNull("contact") ? null : obj.optString("contact", null);
                            boolean hasContent = !TextUtils.isEmpty(msgText) || msgMediaType >= 0 || !TextUtils.isEmpty(msgContact);
                            boolean isStatus = "status".equals(msgJid);
                            if (isStatus && !hasContent) continue;
                            messages.add(msg);
                        }
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            requireContext().getString(R.string.trash_recovery_load_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
                return;
            }

            final List<DelMessage> finalMessages = messages;
            mainHandler.post(() -> {
                if (getView() == null) return;
                adapter.setMessages(finalMessages);
                showResultState(!finalMessages.isEmpty());
            });
        }).start();
    }

    private void showMessageDetail(DelMessage message) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(
                R.layout.bottom_sheet_message_detail, null);
        dialog.setContentView(view);

        TextView tvContact = view.findViewById(R.id.detail_contact);
        TextView tvWa = view.findViewById(R.id.detail_wa);
        TextView tvMessage = view.findViewById(R.id.detail_message);
        TextView tvMedia = view.findViewById(R.id.detail_media);
        TextView tvVoiceFile = view.findViewById(R.id.detail_voice_file);
        TextView tvFile = view.findViewById(R.id.detail_file);
        TextView tvReceived = view.findViewById(R.id.detail_received);
        TextView tvDeleted = view.findViewById(R.id.detail_deleted);
        TextView tvMsgId = view.findViewById(R.id.detail_msg_id);
        Button btnClose = view.findViewById(R.id.detail_close);

        String contact = message.getContact();
        String jid = message.getJid();
        String displayContact = !TextUtils.isEmpty(contact) ? contact :
                (jid != null ? jid.replaceAll("@.*", "") : "-");
        tvContact.setText(displayContact);

        String wa = message.getWa();
        if (!TextUtils.isEmpty(wa)) {
            if (wa.contains("w4b")) {
                tvWa.setText("Business");
            } else if (wa.contains("whatsapp")) {
                tvWa.setText("WA");
            } else {
                tvWa.setText("-");
            }
        } else {
            tvWa.setText("-");
        }

        String text = message.getText();
        tvMessage.setText(!TextUtils.isEmpty(text) ? text : "(Pesan dihapus)");

        Integer mediaType = message.getMediaType();
        String mediaLabel;
        if (mediaType == null || mediaType == -1) {
            mediaLabel = "Teks";
        } else if (mediaType == 1) {
            mediaLabel = "Gambar";
        } else if (mediaType == 2) {
            mediaLabel = "Voice";
        } else if (mediaType == 3) {
            mediaLabel = "Video";
        } else if (mediaType == 9) {
            mediaLabel = "Dokumen";
        } else {
            mediaLabel = "Media";
        }
        tvMedia.setText(mediaLabel);

        String voiceFileName = message.getVoiceFileName();
        tvVoiceFile.setText(!TextUtils.isEmpty(voiceFileName) ? voiceFileName : "-");

        String fileId = message.getFileId();
        tvFile.setText(!TextUtils.isEmpty(fileId) ? fileId : "-");

        Long intime = message.getIntime();
        if (intime != null && intime > 0) {
            tvReceived.setText(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(intime)));
        } else {
            tvReceived.setText("-");
        }

        Long deltime = message.getDeltime();
        if (deltime != null && deltime > 0) {
            tvDeleted.setText(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(deltime)));
        } else {
            Long ts = message.getTimestamp();
            if (ts != null && ts > 0) {
                tvDeleted.setText(DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT).format(new Date(ts)));
            } else {
                tvDeleted.setText("-");
            }
        }

        String msgid = message.getMsgid();
        tvMsgId.setText(!TextUtils.isEmpty(msgid) ? msgid : "-");

        ImageView ivMediaPreview = view.findViewById(R.id.iv_media_preview);
        ImageView ivVoiceIcon = view.findViewById(R.id.iv_voice_icon);
        String mediaPath = message.getMediaPath();
        int mType = mediaType != null ? mediaType : -1;
        if (!TextUtils.isEmpty(mediaPath) && new File(mediaPath).exists()) {
            if (mType == 1) {
                Bitmap bmp = BitmapFactory.decodeFile(mediaPath);
                if (bmp != null) {
                    ivMediaPreview.setImageBitmap(bmp);
                    ivMediaPreview.setVisibility(View.VISIBLE);
                }
            } else if (mType == 3) {
                Bitmap thumb = ThumbnailUtils.createVideoThumbnail(mediaPath, MediaStore.Images.Thumbnails.MINI_KIND);
                if (thumb != null) {
                    ivMediaPreview.setImageBitmap(thumb);
                    ivMediaPreview.setVisibility(View.VISIBLE);
                }
            } else if (mType == 2 || mType == 82) {
                ivVoiceIcon.setVisibility(View.VISIBLE);
            }
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void clearAll() {
        new Thread(() -> {
            // Send broadcast to WhatsApp process — hook receives and calls deleteAll()
            Intent intent = new Intent("com.mrksvt.waen.CLEAR_DELETED_LOG");
            intent.setPackage(FeatureLoader.PACKAGE_WPP);
            requireContext().sendBroadcast(intent);

            Intent intent2 = new Intent("com.mrksvt.waen.CLEAR_DELETED_LOG");
            intent2.setPackage(FeatureLoader.PACKAGE_BUSINESS);
            requireContext().sendBroadcast(intent2);

            mainHandler.post(() -> {
                if (getView() == null) return;
                Toast.makeText(requireContext(), R.string.trash_recovery_cleared,
                        Toast.LENGTH_SHORT).show();
                adapter.setMessages(new ArrayList<>());
                showResultState(false);
            });
        }).start();
    }

    private void exportToJson() {
        new Thread(() -> {
            try {
                List<DelMessage> messages = adapter.getMessages();
                JSONArray jsonArray = new JSONArray();
                for (DelMessage msg : messages) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", msg.getId());
                    obj.put("jid", msg.getJid());
                    obj.put("msgid", msg.getMsgid());
                    obj.put("timestamp", msg.getTimestamp());
                    obj.put("text", msg.getText());
                    obj.put("mediaPath", msg.getMediaPath());
                    obj.put("mediaType", msg.getMediaType());
                    obj.put("senderName", msg.getSenderName());
                    obj.put("wa", msg.getWa());
                    obj.put("contact", msg.getContact());
                    obj.put("intime", msg.getIntime());
                    obj.put("deltime", msg.getDeltime());
                    obj.put("voiceFileName", msg.getVoiceFileName());
                    obj.put("fileId", msg.getFileId());
                    jsonArray.put(obj);
                }
                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                String fileName = "trash_recovery_" + System.currentTimeMillis() + ".json";
                File outFile = new File(downloadsDir, fileName);
                FileWriter writer = new FileWriter(outFile);
                writer.write(jsonArray.toString(2));
                writer.flush();
                writer.close();
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    Toast.makeText(requireContext(), R.string.trash_recovery_export_success,
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    Toast.makeText(requireContext(), R.string.trash_recovery_export_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    @Nullable
    private WaeIIFace getHookBinder() {
        try {
            ContentResolver resolver = requireContext().getContentResolver();
            Bundle bundle = resolver.call(
                    Settings.System.CONTENT_URI, "WaEnhancer", "getHookBinder", null);
            IBinder binder = bundle != null ? bundle.getBinder("binder") : null;
            return binder != null ? WaeIIFace.Stub.asInterface(binder) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
