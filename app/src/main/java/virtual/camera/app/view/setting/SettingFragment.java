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
import virtual.camera.app.databinding.ActivityCameraSettingsBinding;
import virtual.camera.app.settings.LogUtil;
import virtual.camera.app.settings.MethodType;
import virtual.camera.camera.MultiPreferences;
import virtual.camera.app.util.AppUtil;
import virtual.camera.app.util.HandlerUtil;
import virtual.camera.app.util.ToastUtils;

public class SettingFragment extends BaseFragment {
    private ActivityCameraSettingsBinding binding;
    private PopupMenu mPopupMenu = null;
    private int mMethodType = 0;
    private boolean mHasOpenDocuments = false;

    private ActivityResultLauncher<String> openDocumentedResult =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onVideoChoiseDone);

    public void onVideoChoiseDone(Uri video) {
        if (video == null) {
        } else {
            binding.protectPath.setText(video.toString());
            mHasOpenDocuments = true;
        }
        LogUtil.log("onVideoChoiseDone:" + video);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityCameraSettingsBinding.inflate(inflater, container, false);
        initView();
        return binding.getRoot();
    }

    private void initView() {
        binding.protectVideoSelect.setOnClickListener(v -> {
            openDocumentedResult.launch("video/*");
        });

        binding.protectMethodBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPopupMenu = new PopupMenu(getActivitySafe(), view);
                mPopupMenu.inflate(R.menu.camera_menu);
                mPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.protect_method_disable_camera:
                                onMethodTypeClick(MethodType.TYPE_DISABLE_CAMERA);
                                break;
                            case R.id.protect_method_local:
                                onMethodTypeClick(MethodType.TYPE_LOCAL_VIDEO);
                                break;
                            case R.id.protect_method_network:
                                onMethodTypeClick(MethodType.TYPE_NETWORK_VIDEO);
                                break;
                        }
                        return true;
                    }
                });
                mPopupMenu.show();
            }
        });

        binding.protectSave.setOnClickListener(new View.OnClickListener() {
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
            MultiPreferences.getInstance().setBoolean("video_path_local_audio_enable", binding.protectAudioSwitch.isChecked());
            ToastUtils.showToast("Save Success...:");
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
                            HandlerUtil.runOnMain(new Runnable() {
                    @Override
                    public void run() {
                        binding.protectPath.setText("");
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
                binding.protectMethodText.setText(R.string.protect_method_disable_camera);
                binding.protectTip.setText(R.string.protect_tip_disable);
                binding.protectPath.setVisibility(View.GONE);
                binding.protectVideoSelect.setVisibility(View.GONE);
                binding.protectAudio.setVisibility(View.GONE);
                binding.protectAudioSwitch.setVisibility(View.GONE);
                break;
            case MethodType.TYPE_LOCAL_VIDEO:
                binding.protectMethodText.setText(R.string.protect_method_local);
                binding.protectTip.setText(R.string.protect_tip_local);
                binding.protectPath.setVisibility(View.VISIBLE);
                binding.protectVideoSelect.setVisibility(View.VISIBLE);
                binding.protectVideoSelect.setEnabled(true);
                binding.protectVideoSelect.setText(R.string.choise_video);
                binding.protectAudio.setVisibility(View.VISIBLE);
                binding.protectAudioSwitch.setVisibility(View.VISIBLE);
                binding.protectPath.setHint("");
                binding.protectPath.setEnabled(false);
                if (mHasOpenDocuments) {
                    binding.protectPath.setText(MultiPreferences.getInstance().getString("video_path_local", ""));
                } else {
                    binding.protectPath.setText("");
                }
                binding.protectAudioSwitch.setChecked(MultiPreferences.getInstance().getBoolean("video_path_local_audio_enable", true));
                break;
            case MethodType.TYPE_NETWORK_VIDEO:
                binding.protectMethodText.setText(R.string.protect_method_network);
                binding.protectTip.setText(R.string.protect_tip_network);
                binding.protectPath.setVisibility(View.VISIBLE);
                binding.protectVideoSelect.setVisibility(View.GONE);
                binding.protectAudio.setVisibility(View.VISIBLE);
                binding.protectAudioSwitch.setVisibility(View.VISIBLE);
                binding.protectPath.setHint(R.string.protect_path_hint);
                binding.protectPath.setEnabled(true);
                binding.protectPath.setText(MultiPreferences.getInstance().getString("video_path_network", ""));
                binding.protectAudioSwitch.setChecked(MultiPreferences.getInstance().getBoolean("video_path_network_audio_enable", true));
                break;
        }
    }

    private void saveSettings() {
        AppUtil.killAllApps();
        switch (mMethodType) {
            case MethodType.TYPE_DISABLE_CAMERA:
                MultiPreferences.getInstance().setInt("method_type", mMethodType);
                ToastUtils.showToast("Save Success...");
                break;
            case MethodType.TYPE_LOCAL_VIDEO:
                if (TextUtils.isEmpty(binding.protectPath.getText())) {
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
                        copyLocalVideo(binding.protectPath.getText().toString());
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
                if (TextUtils.isEmpty(binding.protectPath.getText())) {
                    ToastUtils.showToast("Video not set...");
                    return;
                }
                if (!binding.protectPath.getText().toString().toLowerCase().startsWith("http")) {
                    ToastUtils.showToast("Video url should start with http or https");
                    return;
                }
                MultiPreferences.getInstance().setInt("method_type", mMethodType);
                MultiPreferences.getInstance().setString("video_path_network", binding.protectPath.getText().toString());
                MultiPreferences.getInstance().setBoolean("video_path_network_audio_enable", binding.protectAudioSwitch.isChecked());
                ToastUtils.showToast("Save Success...");
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
