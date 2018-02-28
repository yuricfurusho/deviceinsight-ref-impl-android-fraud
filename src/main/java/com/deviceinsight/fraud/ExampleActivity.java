package com.deviceinsight.fraud;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.deviceinsight.android.DeviceInsightCollector;
import com.deviceinsight.android.DeviceInsightException;
import android.support.v4.app.ActivityCompat;
import android.support.design.widget.Snackbar;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ExampleActivity extends AppCompatActivity
{
   private final int PHONE_PERMISSION_REQUEST_CODE = 0;
   private EditText remoteUriTextField;
   private EditText payloadTextView;
   private WebView webView;
   private TextView payloadLabel;
   private Button collectButton;
   private DeviceInsightCollector collector;

   /**
    * Called when the activity is first created.
    *
    * @param savedInstanceState If the activity is being re-initialized after
    *                           previously being shut down then this Bundle contains the data it most
    *                           recently supplied in onSaveInstanceState(Bundle). <b>Note: Otherwise it is null.</b>
    */
   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);

      setContentView(R.layout.main);

      remoteUriTextField = (EditText) findViewById(R.id.remoteUriTextField);
      payloadTextView = (EditText) findViewById(R.id.payloadTextView);
      payloadTextView.setHorizontallyScrolling(true);
      payloadLabel = (TextView) findViewById(R.id.payloadLabel);
      collectButton = (Button) findViewById(R.id.collectButton);
      webView = (WebView) findViewById(R.id.mainWebView);

      payloadTextView.setMovementMethod(new android.text.method.ScrollingMovementMethod());
      payloadTextView.setOnLongClickListener(new View.OnLongClickListener()
      {
         public boolean onLongClick(View v)
         {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setText(payloadTextView.getText());
            return true;
         }
      });

      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED)
      {
         // Disable Ui Elements while we ask for user permission asynchronously.
         enableInputUiElements(false);

         // For proper functioning of the collector Phone permission is required.
         requestPhonePermissions();
      }

      // Create the collector object on create.
      collector = new DeviceInsightCollector(getApplicationContext());
   }

   /**
    * Callback received when a permissions request has been completed.
    */
   @Override
   public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults)
   {
      if (requestCode == PHONE_PERMISSION_REQUEST_CODE && grantResults.length == 1)
      {
         if (grantResults[0] != PackageManager.PERMISSION_GRANTED)
         {
            // NOTE: In this example we show a message to the user and continue to run.
            // However we RECOMMEND that the application exit at this point for 2 reasons
            // a. To avoid fraudulent activity from this device.
            // b. To improve the accuracy of the DeviceInsight ID.
            Toast.makeText(getApplicationContext(), R.string.permission_not_granted, Toast.LENGTH_LONG).show();
         }
      }
      else
      {
         super.onRequestPermissionsResult(requestCode, permissions, grantResults);
      }
      enableInputUiElements(true);
   }

   private void enableInputUiElements(boolean enable)
   {
      collectButton.setEnabled(enable);
      remoteUriTextField.setEnabled(enable);
   }

   /**
    * Requests the Phone permission.
    * If the permission has been denied previously, a SnackBar will prompt the user to grant the
    * permission, otherwise it is requested directly.
    */
   private void requestPhonePermissions()
   {
      if (ActivityCompat.shouldShowRequestPermissionRationale(this,
            Manifest.permission.READ_PHONE_STATE))
      {
         // Provide an additional rationale to the user if the permission was not granted
         // and the user would benefit from additional context for the use of the permission.
         // For example if the user has previously denied the permission.
         // NOTE: Permission to Phone is required for the accuracy of the DeviceInsight ID.
         Snackbar
            .make(findViewById(R.id.main_layout), R.string.permission_phone_rationale, Snackbar.LENGTH_INDEFINITE)
            .setAction(android.R.string.ok, new View.OnClickListener()
            {
               @Override
               public void onClick(View view)
               {
                  ActivityCompat.requestPermissions(ExampleActivity.this,
                     new String[]{Manifest.permission.READ_PHONE_STATE},
                     PHONE_PERMISSION_REQUEST_CODE);
               }
            })
            .show();
      }
      else
      {
         // Phone permission has not been granted yet. Request it directly.
         ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},
            PHONE_PERMISSION_REQUEST_CODE);
      }
   }

   public void collectPayload(View v)
   {
      collectButton.setEnabled(false); // Don't pile up background threads.
      new Thread(new Runnable()
      {
         public void run()
         {
            // Attempt the collection, catching any exception if it fails.
            try
            {
               final String payload = collector.collect();
               final String version = collector.getVersion();
               Log.i("DeviceInsight", "Collected payload: " + payload);
               Log.i("DeviceInsight", "Collector version: " + version);
               runOnUiThread(new Runnable()
               {
                  public void run()
                  {
                     payloadLabel.setText(R.string.payload_label);
                     payloadTextView.setText(payload);
                     submitPayload(payload);
                  }
               });
            }
            catch (final DeviceInsightException ex)
            {
               runOnUiThread(new Runnable()
               {
                  public void run()
                  {
                     // If an error occurs, display it in the UI.
                     payloadLabel.setText(R.string.error_label);
                     payloadTextView.setText(ex.getMessage());
                     Log.e("DeviceInsight", "Collection failed", ex);
                  }
               });
            }
            finally
            {
               runOnUiThread(new Runnable()
               {
                  public void run()
                  {
                     payloadLabel.setVisibility(View.VISIBLE);
                     payloadTextView.setVisibility(View.VISIBLE);
                     payloadTextView.selectAll();
                     collectButton.setEnabled(true);
                  }
               });
            }
         }
      }, "Collector").start();
   }

   void submitPayload(String payload)
   {
      String uri = remoteUriTextField.getText().toString().trim();
      if (uri.length() > 0 && payload.length() > 0)
      {
         try
         {
            webView.postUrl(uri, ("user_prefs=" + URLEncoder.encode(payload, "UTF-8")).getBytes());
         }
         catch (UnsupportedEncodingException ex) {}
      }
   }
}

