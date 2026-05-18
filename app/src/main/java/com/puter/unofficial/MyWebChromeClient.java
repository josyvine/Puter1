package com.puter.unofficial;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale; // FIXED: Added missing Locale import

/**
 * Handles the native file upload functionality (Camera, Gallery, File Picker)
 * for the WebView, enabling Base64 upload support for Puter AI interactions.
 */
public class MyWebChromeClient extends WebChromeClient {

    private ValueCallback<Uri[]> uploadMessage;
    private final Activity activity;
    private String currentPhotoPath;

    public MyWebChromeClient(Activity activity) {
        this.activity = activity;
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        if (uploadMessage != null) {
            uploadMessage.onReceiveValue(null);
        }
        uploadMessage = filePathCallback;

        // Intent for picking files
        Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentIntent.setType("*/*"); // Allow all types

        // Intent for Camera
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
                takePictureIntent.putExtra("PhotoPath", currentPhotoPath);
            } catch (IOException ex) {
                // Error occurred
            }
            if (photoFile != null) {
                currentPhotoPath = "file:" + photoFile.getAbsolutePath();
                Uri photoURI = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            } else {
                takePictureIntent = null;
            }
        }

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentIntent);
        chooserIntent.putExtra(Intent.EXTRA_TITLE, "Upload File");
        if (takePictureIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takePictureIntent });
        }

        activity.startActivityForResult(chooserIntent, 1);
        return true;
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = activity.getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (uploadMessage == null) return;
        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (data == null || data.getData() == null) {
                if (currentPhotoPath != null) {
                    results = new Uri[]{Uri.parse(currentPhotoPath)};
                }
            } else {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
        }
        uploadMessage.onReceiveValue(results);
        uploadMessage = null;
    }
}