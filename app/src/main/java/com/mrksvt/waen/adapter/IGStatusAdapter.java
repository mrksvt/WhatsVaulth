package com.mrksvt.waen.adapter;

import static com.mrksvt.waen.xposed.features.customization.IGStatus.itens;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mrksvt.waen.R;
import com.mrksvt.waen.views.dialog.TabDialogContent;
import com.mrksvt.waen.xposed.core.WppCore;
import com.mrksvt.waen.xposed.core.components.FMessageWpp;
import com.mrksvt.waen.xposed.core.components.WaContactWpp;
import com.mrksvt.waen.xposed.core.devkit.Unobfuscator;
import com.mrksvt.waen.xposed.core.devkit.UnobfuscatorCache;
import com.mrksvt.waen.xposed.utils.DesignUtils;
import com.mrksvt.waen.xposed.utils.ReflectionUtils;
import com.mrksvt.waen.xposed.utils.Utils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class IGStatusAdapter extends ArrayAdapter {


    private final Class<?> clazzImageStatus;
    private final Class<?> statusInfoClazz;
    private final Method setCountStatus;

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (position >= itens.size()) {
            return convertView != null ? convertView : new View(getContext());
        }
        var item = itens.get(position);
        IGStatusViewHolder holder;
        if (convertView == null) {
            holder = new IGStatusViewHolder();
            convertView = createLayoutStatus(holder);
            convertView.setTag(holder);
        } else {
            holder = (IGStatusViewHolder) convertView.getTag();
        }
        if (item == null) {
            holder.setInfo("my_status");
            holder.addButton.setVisibility(View.VISIBLE);
        } else if (statusInfoClazz.isInstance(item)) {
            if (item instanceof View v) {
                v.setClickable(false);
            }
            holder.setInfo(item);
            holder.addButton.setVisibility(View.GONE);
        }
        convertView.setOnClickListener(v -> {
            if (holder.myStatus) {
                var activity = WppCore.getCurrentActivity();
                var dialog = WppCore.createBottomDialog(activity);
                var tabdialog = new TabDialogContent(activity);
                tabdialog.setTitle(activity.getString(R.string.select_status_type));
                tabdialog.addTab(UnobfuscatorCache.getInstance().getString("mystatus"), DesignUtils.getIconByName("ic_status", true), (view) -> {
                    try {
                        var clazz = Unobfuscator.getClassByName("MyStatusesActivity", getContext().getClassLoader());
                        var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                        WppCore.getCurrentActivity().startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                    dialog.dismissDialog();
                });

                // Botão da camera
                var iconCamera = DesignUtils.getDrawable(R.drawable.camera);
                DesignUtils.coloredDrawable(iconCamera, DesignUtils.isNightMode() ? Color.WHITE : Color.BLACK);
                tabdialog.addTab(activity.getString(R.string.open_camera), iconCamera, (view) -> {
                    try {
                        Intent intent = new Intent();
                        var clazz = Unobfuscator.getClassByName("CameraActivity", getContext().getClassLoader());
                        intent.setClassName(activity.getPackageName(), clazz.getName());
                        intent.putExtra("jid", "status@broadcast");
                        intent.putExtra("camera_origin", 4);
                        intent.putExtra("is_coming_from_chat", false);
                        intent.putExtra("media_sharing_user_journey_origin", 32);
                        intent.putExtra("media_sharing_user_journey_start_target", 9);
                        intent.putExtra("media_sharing_user_journey_chat_type", 4);
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                    dialog.dismissDialog();
                });
                // Botão de editar
                var iconEdit = DesignUtils.getDrawable(R.drawable.edit2);
                DesignUtils.coloredDrawable(iconEdit, DesignUtils.isNightMode() ? Color.WHITE : Color.BLACK);

                tabdialog.addTab(activity.getString(R.string.edit_text), iconEdit, (view) -> {
                    try {
                        Intent intent = new Intent();
                        Class clazz;
                        try {
                            clazz = Unobfuscator.getClassByName("TextStatusComposerActivity", activity.getClassLoader());
                        } catch (Exception ignored) {
                            clazz = Unobfuscator.getClassByName("ConsolidatedStatusComposerActivity", getContext().getClassLoader());
                            intent.putExtra("status_composer_mode", 2);
                        }
                        intent.setClassName(activity.getPackageName(), clazz.getName());
                        activity.startActivity(intent);
                    } catch (Exception e) {
                        Utils.showToast(e.getMessage(), 1);
                    }
                    dialog.dismissDialog();
                });
                dialog.setContentView(tabdialog);
                dialog.showDialog();
                return;
            }
            try {
                var clazz = Unobfuscator.getClassByName("StatusPlaybackActivity", getContext().getClassLoader());
                var intent = new Intent(WppCore.getCurrentActivity(), clazz);
                intent.putExtra("jid", holder.userJid.getPhoneRawString());
                WppCore.getCurrentActivity().startActivity(intent);
            } catch (Exception e) {
                Utils.showToast(e.getMessage(), 1);
            }
        });

        return convertView;
    }

    public IGStatusAdapter(@NonNull Context context, @NonNull Class<?> statusInfoClazz) throws Exception {
        super(context, 0);
        this.clazzImageStatus = Unobfuscator.findFirstClassUsingName(this.getContext().getClassLoader(), StringMatchType.EndsWith, ".ContactStatusThumbnail");
        this.statusInfoClazz = statusInfoClazz;
        this.setCountStatus = ReflectionUtils.findMethodUsingFilter(this.clazzImageStatus, m -> m.getParameterCount() == 3 && Arrays.equals(new Class[]{int.class, int.class, int.class}, m.getParameterTypes()));
    }

    @Override
    public int getCount() {
        return itens.size();
    }

    class IGStatusViewHolder {
        public ImageView igStatusContactPhoto;
        public RelativeLayout addButton;
        public TextView igStatusContactName;
        public boolean myStatus;
        private FMessageWpp.UserJid userJid;

        public void setInfo(Object item) {

            if (Objects.equals(item, "my_status")) {
                myStatus = true;
                igStatusContactName.setText(UnobfuscatorCache.getInstance().getString("mystatus"));
                var profile = WppCore.getMyPhoto();
                if (profile == null)
                    profile = DesignUtils.createInitialsAvatar(
                            WppCore.getMyName(),
                            WppCore.getMyUserJid() != null ? WppCore.getMyUserJid().getPhoneRawString() : WppCore.getMyName(),
                            Utils.dipToPixels(64)
                    );
                igStatusContactPhoto.setImageDrawable(profile);
                setCountStatus(0, 0);
                return;
            }
            try {
                var statusInfo = resolveStatusInfo(item);
                if (statusInfo == null) {
                    logStatusShape(item);
                    return;
                }
                var jid = resolveJid(statusInfo);
                if (jid == null) {
                    logStatusShape(item);
                    return;
                }
                this.userJid = new FMessageWpp.UserJid(jid);
                var waContact = WaContactWpp.getWaContactFromJid(this.userJid);
                XposedBridge.log("[IGStatus] jid=" + this.userJid.getPhoneRawString()
                        + " statusCls=" + statusInfo.getClass().getSimpleName()
                        + " waContact=" + (waContact != null));

                Drawable profile = null;
                String contactName = null;
                if (waContact != null) {
                    contactName = waContact.getDisplayName();
                    try (var stream = waContact.getProfilePhoto(false)) {
                        if (stream != null) {
                            profile = BitmapDrawable.createFromStream(stream, "profile");
                        }
                    }
                }

                if (contactName == null || contactName.isEmpty()) {
                    contactName = WppCore.getContactName(this.userJid);
                }
                if (contactName == null || contactName.isEmpty()) {
                    contactName = safeFallbackName();
                }
                igStatusContactName.setText(contactName);
                igStatusContactPhoto.setImageDrawable(profile != null ? profile : defaultPhoto(contactName));

                setCountStatus(readIntField(item, "A01"), readIntField(item, "A00"));
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
        }

        /**
         * Item daftar status tidak menyimpan Jid secara langsung, dan nama
         * field statusnya bergeser antar versi WhatsApp (di 2.26.x objek status
         * pindah ke `A00` sementara `A01` sudah menjadi int). Karena itu status
         * dicari dari field objek mana pun yang di dalamnya ada Jid.
         */
        private Object resolveStatusInfo(Object item) {
            if (resolveJid(item) != null) {
                return item;
            }
            try {
                for (var f : item.getClass().getDeclaredFields()) {
                    if (f.getType().isPrimitive()) continue;
                    var value = ReflectionUtils.getObjectField(f, item);
                    if (value != null && resolveJid(value) != null) {
                        return value;
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
            return null;
        }

        /** Jid dibaca lewat field maupun getter, karena namanya ter-obfuscate. */
        private Object resolveJid(Object status) {
            try {
                var classJid = Unobfuscator.findFirstClassUsingName(
                        statusInfoClazz.getClassLoader(), StringMatchType.EndsWith, "jid.Jid");
                if (classJid == null) return null;

                var field = ReflectionUtils.getFieldByExtendType(status.getClass(), classJid);
                var direct = ReflectionUtils.getObjectField(field, status);
                if (direct != null) return direct;

                for (var m : status.getClass().getMethods()) {
                    if (m.getParameterCount() != 0) continue;
                    if (!classJid.isAssignableFrom(m.getReturnType())) continue;
                    try {
                        var result = m.invoke(status);
                        if (result != null) return result;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
            return null;
        }

        private int readIntField(Object target, String name) {
            try {
                return XposedHelpers.getIntField(target, name);
            } catch (Throwable ignored) {
                try {
                    Object boxed = ReflectionUtils.getObjectField(
                            findFieldByName(target.getClass(), name), target);
                    return boxed instanceof Number ? ((Number) boxed).intValue() : 0;
                } catch (Throwable ignored2) {
                    return 0;
                }
            }
        }

        private java.lang.reflect.Field findFieldByName(Class<?> type, String name) {
            var current = type;
            while (current != null && current != Object.class) {
                try {
                    var f = current.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private void logStatusShape(Object item) {
            try {
                var sb = new StringBuilder("[IGStatus] shape item=").append(item.getClass().getName());
                for (var f : item.getClass().getDeclaredFields()) {
                    sb.append(' ').append(f.getName()).append(':').append(f.getType().getSimpleName());
                }
                XposedBridge.log(sb.toString());
            } catch (Throwable ignored) {
            }
        }

        private String safeFallbackName() {
            var phone = this.userJid.getPhoneNumber();
            if (phone == null || phone.isEmpty()) {
                phone = this.userJid.getPhoneRawString();
            }
            return (phone == null || phone.isEmpty()) ? "?" : phone;
        }

        private Drawable defaultPhoto(String name) {
            return DesignUtils.createInitialsAvatar(
                    name,
                    this.userJid != null ? this.userJid.getPhoneRawString() : name,
                    Utils.dipToPixels(64)
            );
        }

        public void setCountStatus(int countUnseen, int total) {
            if (setCountStatus != null) {
                try {
                    setCountStatus.invoke(igStatusContactPhoto, total, countUnseen, total);
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        }

    }

    @NonNull
    private RelativeLayout createLayoutStatus(IGStatusViewHolder holder) {
        RelativeLayout relativeLayout = new RelativeLayout(this.getContext());
        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(86), ViewGroup.LayoutParams.WRAP_CONTENT);
        relativeLayout.setLayoutParams(relativeParams);

        // Criando o FrameLayout
        FrameLayout frameLayout = new FrameLayout(this.getContext());
        frameLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Criando o LinearLayout
        LinearLayout linearLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams linearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(linearParams);

        // Criando o RelativeLayout interno
        RelativeLayout internalRelativeLayout = new RelativeLayout(this.getContext());
        RelativeLayout.LayoutParams internalRelativeParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(64), Utils.dipToPixels(64));
        internalRelativeLayout.setLayoutParams(internalRelativeParams);

        // Adicionando os elementos ao RelativeLayout interno
        var contactPhoto = (ImageView) XposedHelpers.newInstance(this.clazzImageStatus, this.getContext());
        RelativeLayout.LayoutParams photoParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contactPhoto.setLayoutParams(photoParams);
        contactPhoto.setPadding(Utils.dipToPixels(2.5F), Utils.dipToPixels(2.5F), Utils.dipToPixels(2.5F), Utils.dipToPixels(2.5F));
        contactPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        contactPhoto.setImageDrawable(DesignUtils.getDrawableByName("avatar_contact"));
        holder.igStatusContactPhoto = contactPhoto;
        contactPhoto.setClickable(true);
        XposedHelpers.callMethod(contactPhoto, "setBorderSize", (float) Utils.dipToPixels(2.5f));
        XposedHelpers.callMethod(contactPhoto, "setCornerRadius", (float) Utils.dipToPixels(80f));
        XposedHelpers.setObjectField(contactPhoto, "A02", Color.GRAY);
        XposedHelpers.setObjectField(contactPhoto, "A03", DesignUtils.getUnSeenColor());

        RelativeLayout addBtnRelativeLayout = new RelativeLayout(this.getContext());
        addBtnRelativeLayout.setBackgroundColor(Color.TRANSPARENT);
        RelativeLayout.LayoutParams addBtnParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        addBtnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        addBtnRelativeLayout.setLayoutParams(addBtnParams);
        addBtnRelativeLayout.setVisibility(View.GONE);

        ImageView iconImageView = new ImageView(this.getContext());
        RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(Utils.dipToPixels(24), Utils.dipToPixels(24));
        iconImageView.setLayoutParams(iconParams);
        var icon = DesignUtils.getDrawableByName("my_status_add_button_new");
        var coloredIcon = DesignUtils.generatePrimaryColorDrawable(icon);
        iconImageView.setImageDrawable(coloredIcon != null ? coloredIcon : icon);
        iconImageView.setBackgroundColor(Color.TRANSPARENT);
        addBtnRelativeLayout.addView(iconImageView);
        holder.addButton = addBtnRelativeLayout;


        internalRelativeLayout.addView(contactPhoto);
        internalRelativeLayout.addView(addBtnRelativeLayout);

        TextView contactName = new TextView(this.getContext());
        contactName.setEllipsize(TextUtils.TruncateAt.END);
        contactName.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contactName.setLayoutParams(nameParams);
        contactName.setText("Name");
        contactName.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        contactName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        contactName.setTypeface(Typeface.DEFAULT_BOLD);
        contactName.setMaxLines(1);
        holder.igStatusContactName = contactName;
        linearLayout.addView(internalRelativeLayout);
        linearLayout.addView(contactName);
        frameLayout.addView(linearLayout);
        relativeLayout.addView(frameLayout);
        return relativeLayout;
    }
}
