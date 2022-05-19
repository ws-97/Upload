package com.kr;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;

public class MainActivity extends AppCompatActivity {

    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String[] permissions = new String[]{
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE"
        };//所需权限
        if (
                ActivityCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED
                        ||
                        ActivityCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED
        )
        //如果没有权限
        {
            ActivityCompat.requestPermissions(this, permissions, 1);//申请权限
        }
        EditText filenameEditText = findViewById(R.id.name);
        ImageView view = findViewById(R.id.view);
        Button choose = findViewById(R.id.choosee);
        Button upload = findViewById(R.id.upload);
        Button download = findViewById(R.id.download);
        choose.setOnClickListener(v ->
        {
            chooseFile();
        });
        upload.setOnClickListener(v ->
        {
            showMessage(uploadFileFile1("/data/data/com.kr/51652254519_.pic.jpg", filenameEditText.getText().toString()) ? "上传成功" : "上传失败");
        });
        download.setOnClickListener(v ->
        {
            File file = downloadFile(filenameEditText.getText().toString());
            if (file == null)
                showMessage("没有这张图片");
            else {
                view.setImageURI(null);
                view.setImageURI(Uri.fromFile(file));
                showMessage("下载成功");
            }
        });
    }

    public void chooseFile() {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);//使用系统的文件选择器
        intent.setType("*/*");//所有类型的文件
        intent.addCategory(Intent.CATEGORY_OPENABLE);//期望获取的数据可以作为一个File打开
        startActivityForResult(intent, 1);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            File dir = getExternalFilesDir(null);
            if (dir != null) {
                path = dir.toString().substring(0, dir.toString().indexOf("0") + 2) +
                        DocumentsContract.getDocumentId(uri).split(":")[1];
            }
        }
    }

    private static final String TAG = "MainActivity";

    public boolean uploadFile(String path, String filename) {
        OkHttpClient okhttp = new OkHttpClient();
        File file = new File(path);
        if (path.isEmpty() || !file.exists())
            return false;
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, RequestBody.create(new File(path), MediaType.parse("multipart/form-data")))
                .addFormDataPart("filename", filename)
                .build();
        FutureTask<Boolean> task = new FutureTask<>(() ->
        {
            try {
                Log.e(TAG, "uploadFile: body:" + body.toString());
                ResponseBody responseBody = okhttp.newCall(
                        new Request.Builder().post(body).url("http://10.0.31.178:8080/upload").build()
                ).execute().body();

                if (responseBody != null) {
                    Log.e(TAG, "uploadFile: responseBody:" + responseBody);
                    return Boolean.parseBoolean(responseBody.string());
                }
                Log.e(TAG, "uploadFile: responseBody==null" );

                return false;
            } catch (IOException e) {
                return false;
            }
        });
        try {
            new Thread(task).start();
            return task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean uploadFileFile1(String path, String filename) {
        OkHttpClient okhttp = new OkHttpClient();
        File file = new File(path);
        if (path.isEmpty() || !file.exists())
            return false;
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, RequestBody.create(file, MediaType.parse("multipart/form-data")))
                .addFormDataPart("filename", filename)
                .build();
        Request request = new Request.Builder()
                .url("http://10.0.31.178:8080/upload")
                .post(body)
                .build();

        okhttp.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Log.e(TAG, "onFailure: "+e );
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                Log.e(TAG, "onResponse: response:"+response );
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    Log.e(TAG, "onResponse: "+body );
                }
            }
        });
        return true;
    }

    public File downloadFile(String filename) {
        OkHttpClient okhttp = new OkHttpClient();
        if (filename == null || filename.isEmpty())
            return null;
        RequestBody body = new MultipartBody.Builder().addFormDataPart("filename", filename).build();

        FutureTask<File> task = new FutureTask<>(() ->
        {
            ResponseBody responseBody = okhttp.newCall(
                    new Request.Builder().post(body).url("http://192.168.1.3:8080/kr/download").build()
            ).execute().body();
            if (responseBody != null) {
                if (getExternalFilesDir(null) != null) {
                    File file = new File(getExternalFilesDir(null).toString() + "/" + filename);
                    try (
                            InputStream inputStream = responseBody.byteStream();
                            FileOutputStream outputStream = new FileOutputStream(file)
                    ) {
                        byte[] b = new byte[1024];
                        int n;
                        if ((n = inputStream.read(b)) != -1) {
                            outputStream.write(b, 0, n);
                            while ((n = inputStream.read(b)) != -1)
                                outputStream.write(b, 0, n);
                            return file;
                        } else {
                            file.delete();
                            return null;
                        }
                    }
                }
            }
            return null;
        });
        try {
            new Thread(task).start();
            return task.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void e(String message) {
        Log.e("LOG_E", message);
    }
}
