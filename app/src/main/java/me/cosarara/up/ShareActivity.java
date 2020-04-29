package me.cosarara.up;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

public class ShareActivity extends AppCompatActivity {

    private final OkHttpClient client = new OkHttpClient();
    private Handler mHandler;

    //private static final int UPLOAD_DONE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String mime = intent.getType();

        mHandler = new Handler(Looper.getMainLooper());
        final TextView textView = (TextView) findViewById(R.id.textView);

        textView.setText("step 1");

        if (Intent.ACTION_SEND.equals(action) && mime != null) {

            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            textView.setText("step 2: "+uri);
            if (uri != null) {
                try {

                    final InputStream uriStream = getContentResolver().openInputStream(uri);
                    //ByteArrayOutputStream buffer_stream = new ByteArrayOutputStream();
                    //this.copyStream(uriStream, buffer_stream);

                    Log.i("upload", "mime: " + mime);
                    //if (mime == null) {
                    //    mime = "application/octet-stream";
                    //}
                    final MediaType mediaType = MediaType.parse(mime);

                    // https://stackoverflow.com/questions/25367888/upload-binary-file-with-okhttp-from-resources
                    RequestBody file_part = new RequestBody() {
                        @Override
                        public MediaType contentType() {
                            return mediaType;
                        }

                        @Override
                        public long contentLength() {
                            try {
                                return uriStream.available();
                            } catch (IOException e) {
                                return 0;
                            }
                        }

                        @Override
                        public void writeTo(BufferedSink sink) throws IOException {
                            Source source = null;
                            try {
                                source = Okio.source(uriStream);
                                sink.writeAll(source);
                            } finally {
                                Util.closeQuietly(source);
                            }
                        }
                    };


                    //buffer_stream.toByteArray().toRequestBody();

                    URL api_url = new URL("https://api.teknik.io/v1/Upload");
                    String filename = uri.getLastPathSegment();
                    Log.i("upload", "filename "+filename);

                    RequestBody requestBody = new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", filename, file_part)
                            .build();

                    Request request = new Request.Builder()
                            .url(api_url)
                            .post(requestBody)
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            final String mMessage = e.toString();
                            //Log.e(LOG_TAG, mMessage); // no need inside run()
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.e("upload", "http failure "+mMessage);
                                    //mTextView.setText(mMessage); // must be inside run()
                                }
                            });
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            final String s = response.body().string();
                            String turl = "";
                            try {
                                JSONObject j = new JSONObject(s);
                                JSONObject r = j.getJSONObject("result");
                                if (r == null) {
                                    throw new JSONException("no result: " + s);
                                }
                                turl = r.getString("url");
                            } catch (JSONException e) {
                                Log.e("upload", "json problem", e);
                            }
                            final String url = turl;

                            Intent i = new Intent(Intent.ACTION_VIEW);
                            i.setData(Uri.parse(url));
                            startActivity(i);

                            //Log.i(LOG_TAG, mMessage); // no need inside run()
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    textView.setText(url); // must be inside run()
                                    Log.i("upload", "http ok "+url);
                                }
                            });
                        }
                    });

                    //PendingIntent pendingResult = createPendingResult(
                    //        UPLOAD_DONE, new Intent(), 0);
                    //Upload.startUpload(getApplicationContext(), uri, type, pendingResult);
/*
                try {
                    //String url = this.upload(uri, type);

                    textView.setText("all ok "+uri+" "+url);

                    Context context = getApplicationContext();
                    //CharSequence text = uri.toString();
                    CharSequence text = url;
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                } catch (Exception e) {
                    textView.setText("oops");
                    //throw new RuntimeException(e);
                }
 */
                } catch (Exception e) {
                    Log.e("upload", "something bad", e);
                    textView.setText("oops");
                    //throw new RuntimeException(e);
                }
            }
        } else {
            textView.setText("weird");
            // Handle other intents, such as being started from the home screen
        }

    }
}
