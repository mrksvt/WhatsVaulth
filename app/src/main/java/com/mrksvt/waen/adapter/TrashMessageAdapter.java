package com.mrksvt.waen.adapter;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mrksvt.waen.R;
import com.mrksvt.waen.databinding.ItemTrashMessageBinding;
import com.mrksvt.waen.xposed.core.db.entity.DelMessage;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrashMessageAdapter extends RecyclerView.Adapter<TrashMessageAdapter.ViewHolder> {

    public interface OnMessageClickListener {
        void onMessageClick(DelMessage message);
    }

    private List<DelMessage> messages;
    private final Map<String, String> contactNameCache = new HashMap<>();
    private OnMessageClickListener clickListener;

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.clickListener = listener;
    }

    public TrashMessageAdapter(List<DelMessage> messages) {
        this.messages = messages;
    }

    public void setMessages(List<DelMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public List<DelMessage> getMessages() {
        return messages != null ? messages : new java.util.ArrayList<>();
    }

    private String getContactDisplayName(String phoneNumber, ContentResolver resolver) {
        if (TextUtils.isEmpty(phoneNumber) || resolver == null) {
            return phoneNumber;
        }
        String cached = contactNameCache.get(phoneNumber);
        if (cached != null) {
            return cached;
        }
        String displayName = null;
        try {
            Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Cursor cursor = resolver.query(
                    uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(0);
                }
                cursor.close();
            }
        } catch (Exception e) {
            // Fall through to phone number fallback
        }
        String result = displayName != null ? displayName : phoneNumber;
        contactNameCache.put(phoneNumber, result);
        return result;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTrashMessageBinding binding = ItemTrashMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DelMessage item = messages.get(position);
        ItemTrashMessageBinding b = holder.binding;
        ContentResolver resolver = b.getRoot().getContext().getContentResolver();

        String jid = item.getJid();
        String contact = item.getContact();
        String phoneNumber = null;
        if (!TextUtils.isEmpty(contact)) {
            phoneNumber = contact;
        } else if (jid != null) {
            int atIdx = jid.indexOf('@');
            if (atIdx >= 0) {
                phoneNumber = jid.substring(0, atIdx);
            } else {
                phoneNumber = jid;
            }
        }
        String displayName;
        if (!TextUtils.isEmpty(phoneNumber)) {
            displayName = getContactDisplayName(phoneNumber, resolver);
        } else if ("status".equals(jid)) {
            displayName = "Status";
        } else {
            displayName = "Unknown";
        }
        b.contactName.setText(displayName);

        Long intime = item.getIntime();
        Long deltime = item.getDeltime();
        boolean hasIntime = intime != null && intime > 0;
        boolean hasDeltime = deltime != null && deltime > 0;
        if (hasIntime && hasDeltime) {
            String inFormatted = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(intime));
            String delFormatted = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(deltime));
            b.timestamp.setText("Diterima: " + inFormatted + "\nDihapus: " + delFormatted);
        } else if (hasIntime || hasDeltime) {
            Long ts = hasIntime ? intime : deltime;
            String formatted = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT, DateFormat.SHORT).format(new Date(ts));
            b.timestamp.setText(formatted);
        } else {
            Long ts = item.getTimestamp();
            if (ts != null && ts > 0) {
                String formatted = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT, DateFormat.SHORT).format(new Date(ts));
                b.timestamp.setText(formatted);
            } else {
                b.timestamp.setText("");
            }
        }

        String text = item.getText();
        Integer mediaType = item.getMediaType();
        boolean hasMedia = mediaType != null && mediaType >= 0;
        if (!TextUtils.isEmpty(text)) {
            b.messagePreview.setText(text);
        } else if (hasMedia) {
            b.messagePreview.setText(b.messagePreview.getContext().getString(R.string.trash_recovery_media));
        } else {
            b.messagePreview.setText("(Pesan dihapus)");
        }

        if (hasMedia) {
            b.mediaTypeIcon.setVisibility(View.VISIBLE);
            if (mediaType != null && (mediaType == 2 || mediaType == 82)) {
                b.mediaTypeIcon.setImageResource(android.R.drawable.ic_btn_speak_now);
            } else if (mediaType != null && mediaType == 1) {
                b.mediaTypeIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            } else if (mediaType != null && mediaType == 3) {
                b.mediaTypeIcon.setImageResource(android.R.drawable.ic_media_play);
            } else if (mediaType != null && mediaType == 9) {
                b.mediaTypeIcon.setImageResource(android.R.drawable.ic_menu_agenda);
            } else {
                b.mediaTypeIcon.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            b.mediaTypeIcon.setVisibility(View.GONE);
        }

        String wa = item.getWa();
        if (!TextUtils.isEmpty(wa)) {
            if (wa.contains("w4b")) {
                b.waBadge.setText("Business");
                b.waBadge.setVisibility(View.VISIBLE);
            } else if (wa.contains("whatsapp")) {
                b.waBadge.setText("WA");
                b.waBadge.setVisibility(View.VISIBLE);
            } else {
                b.waBadge.setVisibility(View.GONE);
            }
        } else {
            b.waBadge.setVisibility(View.GONE);
        }

        // Sender name: only show for group chats (jid contains @g.us)
        String senderName = item.getSenderName();
        boolean isGroup = jid != null && jid.contains("@g.us");
        if (isGroup && !TextUtils.isEmpty(senderName)) {
            b.senderName.setText(senderName);
            b.senderName.setVisibility(View.VISIBLE);
        } else {
            b.senderName.setVisibility(View.GONE);
        }

        b.card.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onMessageClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemTrashMessageBinding binding;

        public ViewHolder(@NonNull ItemTrashMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
