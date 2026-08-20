package com.mrksvt.waen.ui.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mrksvt.waen.ui.fragments.base.BackNavHelper;
import com.mrksvt.waen.R;
import com.mrksvt.waen.databinding.FragmentDeletedMessageDetailBinding;
import com.mrksvt.waen.xposed.core.db.entity.DelMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;

import android.util.Size;

public class DeletedMessageDetailFragment extends Fragment {

    private FragmentDeletedMessageDetailBinding binding;
    private DelMessage message;

    public static DeletedMessageDetailFragment newInstance(DelMessage message) {
        DeletedMessageDetailFragment fragment = new DeletedMessageDetailFragment();
        Bundle args = new Bundle();
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", message.getId());
            obj.put("jid", message.getJid());
            obj.put("msgid", message.getMsgid());
            obj.put("timestamp", message.getTimestamp());
            obj.put("text", message.getText());
            obj.put("mediaPath", message.getMediaPath());
            obj.put("mediaType", message.getMediaType());
            obj.put("senderName", message.getSenderName());
            obj.put("wa", message.getWa());
            obj.put("contact", message.getContact());
            obj.put("intime", message.getIntime());
            obj.put("deltime", message.getDeltime());
            obj.put("voiceFileName", message.getVoiceFileName());
            obj.put("fileId", message.getFileId());
        } catch (JSONException e) {
            // ignore
        }
        args.putString("message_json", obj.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDeletedMessageDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BackNavHelper.install(this);

        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragment() != null) {
                getParentFragment().getChildFragmentManager().popBackStack();
            } else {
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });

        if (getArguments() != null) {
            String json = getArguments().getString("message_json");
            if (!TextUtils.isEmpty(json)) {
                try {
                    JSONObject obj = new JSONObject(json);
                    message = new DelMessage();
                    message.setId(obj.getLong("id"));
                    message.setJid(obj.optString("jid", null));
                    message.setMsgid(obj.optString("msgid", null));
                    message.setTimestamp(obj.optLong("timestamp", 0));
                    message.setText(obj.isNull("text") ? null : obj.getString("text"));
                    message.setMediaPath(obj.isNull("mediaPath") ? null : obj.getString("mediaPath"));
                    message.setMediaType(obj.optInt("mediaType", -1));
                    message.setSenderName(obj.isNull("senderName") ? null : obj.getString("senderName"));
                    message.setWa(obj.optString("wa", null));
                    message.setContact(obj.optString("contact", null));
                    message.setIntime(obj.optLong("intime", 0));
                    message.setDeltime(obj.optLong("deltime", 0));
                    message.setVoiceFileName(obj.optString("voiceFileName", null));
                    message.setFileId(obj.optString("fileId", null));
                } catch (JSONException e) {
                    // ignore
                }
            }
        }

        if (message == null) {
            return;
        }

        String contact = message.getContact();
        String jid = message.getJid();
        String displayContact = !TextUtils.isEmpty(contact) ? contact :
                (jid != null ? jid.replaceAll("@.*", "") : "-");
        binding.tvContact.setText(displayContact);

        String wa = message.getWa();
        if (!TextUtils.isEmpty(wa)) {
            if (wa.contains("w4b")) {
                binding.tvWa.setText("WhatsApp Business");
            } else if (wa.contains("whatsapp")) {
                binding.tvWa.setText("WhatsApp");
            } else {
                binding.tvWa.setText("-");
            }
        } else {
            binding.tvWa.setText("-");
        }

        String senderName = message.getSenderName();
        if (!TextUtils.isEmpty(senderName)) {
            binding.tvSender.setText(senderName);
            binding.tvSender.setVisibility(View.VISIBLE);
        } else {
            binding.tvSender.setVisibility(View.GONE);
        }

        String text = message.getText();
        binding.tvMessage.setText(!TextUtils.isEmpty(text) ? text : "(Pesan dihapus)");

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
        binding.tvMediaType.setText(mediaLabel);

        String voiceFileName = message.getVoiceFileName();
        binding.tvVoiceFile.setText(!TextUtils.isEmpty(voiceFileName) ? voiceFileName : "-");

        String fileId = message.getFileId();
        binding.tvFileId.setText(!TextUtils.isEmpty(fileId) ? fileId : "-");

        Long intime = message.getIntime();
        if (intime != null && intime > 0) {
            binding.tvIntime.setText(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(intime)));
        } else {
            binding.tvIntime.setText("-");
        }

        Long deltime = message.getDeltime();
        if (deltime != null && deltime > 0) {
            binding.tvDeltime.setText(DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(deltime)));
        } else {
            Long ts = message.getTimestamp();
            if (ts != null && ts > 0) {
                binding.tvDeltime.setText(DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT).format(new Date(ts)));
            } else {
                binding.tvDeltime.setText("-");
            }
        }

        String msgid = message.getMsgid();
        binding.tvMsgid.setText(!TextUtils.isEmpty(msgid) ? msgid : "-");

        String mediaPath = message.getMediaPath();
        int mType = mediaType != null ? mediaType : -1;
        if (!TextUtils.isEmpty(mediaPath) && new File(mediaPath).exists()) {
            if (mType == 1) {
                Bitmap bmp = BitmapFactory.decodeFile(mediaPath);
                if (bmp != null) {
                    binding.ivMedia.setImageBitmap(bmp);
                    binding.ivMedia.setVisibility(View.VISIBLE);
                }
            } else if (mType == 3) {
                Bitmap thumb = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    try {
                        thumb = ThumbnailUtils.createVideoThumbnail(
                                new File(mediaPath), new Size(512, 512), null);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (thumb == null) {
                    try {
                        thumb = ThumbnailUtils.createVideoThumbnail(mediaPath, MediaStore.Images.Thumbnails.MINI_KIND);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (thumb != null) {
                    binding.ivMedia.setImageBitmap(thumb);
                    binding.ivMedia.setVisibility(View.VISIBLE);
                }
            } else if (mType == 2 || mType == 82) {
                binding.ivMedia.setImageResource(android.R.drawable.ic_btn_speak_now);
                binding.ivMedia.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
