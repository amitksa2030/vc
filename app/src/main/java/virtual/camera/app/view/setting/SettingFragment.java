package virtual.camera.app.view.setting;

import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import virtual.camera.app.R;
import virtual.camera.app.app.App;
import virtual.camera.app.settings.LogUtil;
import virtual.camera.app.settings.MethodType;
import virtual.camera.camera.MultiPreferences;
import virtual.camera.app.util.AppUtil;
import virtual.camera.app.util.HandlerUtil;
import virtual.camera.app.util.ToastUtils;

public class SettingFragment extends BaseFragment {
    private AppCompatButton mProtectMethodBtn, mSave;
    private AppCompatTextView mProtectMethodText, mTip, mAudioText;
    private AppCompatEditText mInput;
    private SwitchCompat mAudioSwitch;
    private AppCompatButton mChoiseVideo;
    private PopupMenu mPopupMenu = null;
    private int mMethodType = 0;
    private boolean mHasOpenDocuments = false;

    private ActivityResultLauncher<String> openDocumentedResult =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onMediaChoiseDone);

    public void onMediaChoiseDone(Uri mediaUri) {
        if (mediaUri == null) {
            // User cancelled the picker
        } else {
            mInput.setText(mediaUri.toString());
            mHasOpenDocuments = true; // Indicate that a new document has been chosen
        }
        LogUtil.log("onMediaChoiseDone URI:" + mediaUri);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_camera_settings, container, false);
        initView(view);
        return view;
    }

    private void initView(View rootView) {
        mProtectMethodBtn = rootView.findViewById(R.id.protect_method_btn);
        mSave = rootView.findViewById(R.id.protect_save);
        mProtectMethodText = rootView.findViewById(R.id.protect_method_text);
        mTip = rootView.findViewById(R.id.protect_tip);
        mInput = rootView.findViewById(R.id.protect_path);
        mAudioText = rootView.findViewById(R.id.protect_audio);
        mAudioSwitch = rootView.findViewById(R.id.protect_audio_switch);
        mChoiseVideo = rootView.findViewById(R.id.protect_video_select);
        mChoiseVideo.setOnClickListener(v -> {
            if (mMethodType == MethodType.TYPE_LOCAL_PHOTO || mMethodType == MethodType.TYPE_FACE_SWAP) { // Added TYPE_FACE_SWAP
                openDocumentedResult.launch("image/*");
            } else { // Default to video for TYPE_LOCAL_VIDEO
                openDocumentedResult.launch("video/*");
            }
        });

        mProtectMethodBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPopupMenu = new PopupMenu(getActivitySafe(), view);
                mPopupMenu.inflate(R.menu.camera_menu);
                mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int itemId = item.getItemId();
                        if (itemId == R.id.protect_method_disable_camera) {
                            onMethodTypeClick(MethodType.TYPE_DISABLE_CAMERA);
                        } else if (itemId == R.id.protect_method_local) {
                            onMethodTypeClick(MethodType.TYPE_LOCAL_VIDEO);
                        } else if (itemId == R.id.protect_method_network) {
                            onMethodTypeClick(MethodType.TYPE_NETWORK_VIDEO);
                        } else if (itemId == R.id.protect_method_photo) {
                            onMethodTypeClick(MethodType.TYPE_LOCAL_PHOTO);
                        } else if (itemId == R.id.protect_method_face_swap) { // Added
                            onMethodTypeClick(MethodType.TYPE_FACE_SWAP);
                        }
                        return true;
                    }
                });
                mPopupMenu.show();
            }
        });

        mSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        int method_type = MultiPreferences.getInstance().getInt("method_type", MethodType.TYPE_DISABLE_CAMERA);
        if (method_type > 0) {
            onMethodTypeClick(method_type);
        }
    }

    private boolean copyLocalVideo(String u) {
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            Uri uri = Uri.parse(u);
            String video_path_local = MultiPreferences.getInstance().getString("video_path_local_final_out", "");
            if (!TextUtils.isEmpty(video_path_local)) {
                new File(video_path_local).delete();
            }
            String subfix = u.substring(u.lastIndexOf("."), u.length());
            String outPath = App.getContext().getFilesDir() + "/video" + subfix;
            fos = new FileOutputStream(outPath);
            is = getActivitySafe().getContentResolver().openInputStream(uri);
            byte[] buffer = new byte[1024];
            int len = is.read(buffer);
            while (len >= 0) {
                fos.write(buffer);
                len = is.read(buffer);
            }
            fos.flush();
            fos.close();
            is.close();

            MultiPreferences.getInstance().setInt("method_type", mMethodType);
            MultiPreferences.getInstance().setString("video_path_local", u);
            MultiPreferences.getInstance().setString("video_path_local_final_out", outPath);
            MultiPreferences.getInstance().setBoolean("video_path_local_audio_enable", mAudioSwitch.isChecked());
            ToastUtils.showToast("Save Success...:");
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            HandlerUtil.runOnMain(new Runnable() {
                @Override
                public void run() {
                    mInput.setText("");
                }
            });
            ToastUtils.showToast("Video handing failed:" + e);
            return false;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable e) {

                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable e) {

                }
            }
        }
    }

    private void onMethodTypeClick(int type) {
        mMethodType = type;
        switch (type) {
            case MethodType.TYPE_DISABLE_CAMERA:
                mProtectMethodText.setText(R.string.protect_method_disable_camera);
                mTip.setText(R.string.protect_tip_disable);
                mInput.setVisibility(View.GONE);
                mChoiseVideo.setVisibility(View.GONE);
                mAudioText.setVisibility(View.GONE);
                mAudioSwitch.setVisibility(View.GONE);
                break;
            case MethodType.TYPE_LOCAL_VIDEO:
                mProtectMethodText.setText(R.string.protect_method_local);
                mTip.setText(R.string.protect_tip_local);
                mInput.setVisibility(View.VISIBLE);
                mChoiseVideo.setVisibility(View.VISIBLE);
                mChoiseVideo.setEnabled(true);
                mChoiseVideo.setText(R.string.choise_video);
                mAudioText.setVisibility(View.VISIBLE);
                mAudioSwitch.setVisibility(View.VISIBLE);
                mInput.setHint("");
                mInput.setEnabled(false);
                if (mHasOpenDocuments) {
                    mInput.setText(MultiPreferences.getInstance().getString("video_path_local", ""));
                } else {
                    mInput.setText("");
                }
                mAudioSwitch.setChecked(MultiPreferences.getInstance().getBoolean("video_path_local_audio_enable", true));
                break;
            case MethodType.TYPE_LOCAL_PHOTO:
                mProtectMethodText.setText(R.string.protect_method_photo);
                mTip.setText(R.string.protect_tip_photo);
                mInput.setVisibility(View.VISIBLE);
                mChoiseVideo.setVisibility(View.VISIBLE);
                mChoiseVideo.setEnabled(true);
                mChoiseVideo.setText(R.string.choose_photo);
                mAudioText.setVisibility(View.GONE);
                mAudioSwitch.setVisibility(View.GONE);
                mInput.setHint(""); // Or "Selected photo URI"
                mInput.setEnabled(false);
                if (mHasOpenDocuments) { // If a new document was chosen, it's already in mInput
                    // Potentially load from MultiPreferences if not mHasOpenDocuments and a photo was previously saved
                    // mInput.setText(MultiPreferences.getInstance().getString("photo_path_uri", ""));
                } else {
                     mInput.setText(MultiPreferences.getInstance().getString("photo_path_uri", ""));
                }
                break;
            case MethodType.TYPE_NETWORK_VIDEO:
                mProtectMethodText.setText(R.string.protect_method_network);
                mTip.setText(R.string.protect_tip_network);
                mInput.setVisibility(View.VISIBLE);
                mChoiseVideo.setVisibility(View.GONE);
                mAudioText.setVisibility(View.VISIBLE);
                mAudioSwitch.setVisibility(View.VISIBLE);
                mInput.setHint(R.string.protect_path_hint);
                mInput.setEnabled(true);
                mInput.setText(MultiPreferences.getInstance().getString("video_path_network", ""));
                mAudioSwitch.setChecked(MultiPreferences.getInstance().getBoolean("video_path_network_audio_enable", true));
                break;
            case MethodType.TYPE_FACE_SWAP: // Added
                mProtectMethodText.setText(R.string.protect_method_face_swap);
                mTip.setText(R.string.protect_tip_face_swap);
                mInput.setVisibility(View.VISIBLE);
                mChoiseVideo.setVisibility(View.VISIBLE);
                mChoiseVideo.setEnabled(true);
                mChoiseVideo.setText(R.string.choose_face_swap_photo);
                mAudioText.setVisibility(View.GONE);
                mAudioSwitch.setVisibility(View.GONE);
                mInput.setHint(""); // Or "Selected source photo for face swap"
                mInput.setEnabled(false);
                if (mHasOpenDocuments) {
                    // URI is already in mInput from onMediaChoiseDone
                } else {
                     mInput.setText(MultiPreferences.getInstance().getString("face_swap_source_photo_uri", ""));
                }
                break;
        }
    }

    private void saveSettings() {
        AppUtil.killAllApps(); // Consider if this is always needed, especially for just changing a photo URI
        switch (mMethodType) {
            case MethodType.TYPE_DISABLE_CAMERA:
                MultiPreferences.getInstance().setInt("method_type", mMethodType);
                ToastUtils.showToast("Save Success...");
                break;
            case MethodType.TYPE_LOCAL_VIDEO:
                if (TextUtils.isEmpty(mInput.getText())) {
                    ToastUtils.showToast("Video not set...");
                    return;
                }
                ProgressDialog progressDialog = new ProgressDialog(getActivitySafe(), ProgressDialog.STYLE_SPINNER);
                progressDialog.setCancelable(false);
                progressDialog.setMessage("Handing video...");
                progressDialog.show();
                new Thread() {
                    @Override
                    public void run() {
                        copyLocalVideo(mInput.getText().toString());
                        HandlerUtil.runOnMain(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    progressDialog.dismiss();
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                }.start();
                break;
            case MethodType.TYPE_NETWORK_VIDEO:
                if (TextUtils.isEmpty(mInput.getText())) {
                    ToastUtils.showToast("Video not set...");
                    return;
                }
                if (!mInput.getText().toString().toLowerCase().startsWith("http")) {
                    ToastUtils.showToast("Video url should start with http or https");
                    return;
                }
                MultiPreferences.getInstance().setInt("method_type", mMethodType);
                MultiPreferences.getInstance().setString("video_path_network", mInput.getText().toString());
                MultiPreferences.getInstance().setBoolean("video_path_network_audio_enable", mAudioSwitch.isChecked());
                ToastUtils.showToast("Save Success...");
                break;
            case MethodType.TYPE_LOCAL_PHOTO:
                if (TextUtils.isEmpty(mInput.getText())) {
                    ToastUtils.showToast(getString(R.string.photo_not_set));
                    return;
                }
                handleLocalImage(mInput.getText().toString());
                break;
            case MethodType.TYPE_FACE_SWAP: // Added
                if (TextUtils.isEmpty(mInput.getText())) {
                    ToastUtils.showToast(getString(R.string.face_swap_photo_not_set));
                    return;
                }
                handleFaceSwapSourcePhoto(mInput.getText().toString());
                break;
        }
    }

    private void handleLocalImage(String imageUriString) {
        LogUtil.log("handleLocalImage URI: " + imageUriString);
        MultiPreferences.getInstance().setInt("method_type", mMethodType);
        MultiPreferences.getInstance().setString("photo_path_uri", imageUriString);
        ToastUtils.showToast(getString(R.string.photo_settings_saved));
        mHasOpenDocuments = false;
    }

    private void handleFaceSwapSourcePhoto(String imageUriString) { // Added
        LogUtil.log("handleFaceSwapSourcePhoto URI: " + imageUriString);
        MultiPreferences.getInstance().setInt("method_type", mMethodType); // mMethodType should be TYPE_FACE_SWAP
        MultiPreferences.getInstance().setString("face_swap_source_photo_uri", imageUriString);
        ToastUtils.showToast(getString(R.string.face_swap_photo_saved));
        mHasOpenDocuments = false; // Reset flag after saving
    }
}
