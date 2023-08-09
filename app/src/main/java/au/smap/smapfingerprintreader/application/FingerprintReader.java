package au.smap.smapfingerprintreader.application;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.mantra.morfinauth.DeviceInfo;
import com.mantra.morfinauth.MorfinAuth;
import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.DeviceModel;
import com.mantra.morfinauth.enums.ImageFormat;
import com.mantra.morfinauth.enums.TemplateFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import au.smap.smapfingerprintreader.model.ScannerViewModel;

public class FingerprintReader extends Application {

    private static FingerprintReader singleton;

    public TextView logView;

    public boolean isStartCaptureRunning;
    public boolean isStopCaptureRunning;
    public MorfinAuth morfinAuth;
    public String currentDevice;            // Name of currently connected device
    public boolean setupComplete = false;   // Set true when a scanner has been enabled
    public String clientKey = "";

    public ScannerViewModel model;
    private DeviceInfo lastDeviceInfo;
    private enum ScannerAction {
        Capture, MatchISO, MatchAnsi
    }
    public int minQuality = 60;
    public int timeOut = 10000;
    TemplateFormat captureTemplateDatas;
    ImageFormat captureImageData;
    private byte[] lastCapFingerData;
    private ScannerAction scannerAction = ScannerAction.Capture;

    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
    }

    public static FingerprintReader getInstance() {
        return singleton;
    }

    public void deviceDetected(String deviceName, DeviceDetection detection, boolean startCapture) {
        isStartCaptureRunning = false;
        isStopCaptureRunning = false;
        if (detection == DeviceDetection.CONNECTED) {
            currentDevice = deviceName;
            setLogs("Device Detected " + deviceName + " connected", false);

            if(setupComplete) {
                initialise();
                if(startCapture) {
                    startCapture(minQuality, timeOut);
                }
            }

        } else if (detection == DeviceDetection.DISCONNECTED) {
            try {
                setLogs("Device Not Connected", true);
                currentDevice = null;
                morfinAuth.Uninit();
            } catch (Exception e) {
                setLogs("Failed to disconnect " + e.getMessage(), true);
                e.printStackTrace();
            } finally {

            }
        }
    }

    public void setScanner(Context context, MorfinAuth_Callback callback) {
        setLogs("setScanner", false);
        morfinAuth = new MorfinAuth(context, callback);
        setLogs("Scanner added", false);
        setupComplete = true;

        captureImageData = (ImageFormat.BMP);
        captureTemplateDatas = (TemplateFormat.FMR_V2005);

        if(currentDevice != null) {
            initialise();
        }

    }
    public void destroy() {
        setLogs("Destroy", false);
        isStartCaptureRunning = false;
        isStopCaptureRunning = false;
        if(morfinAuth != null) {
            morfinAuth.Uninit();
            morfinAuth.Dispose();
            morfinAuth = null;
        }
    }

    public void setLogs(final String logs, boolean isError) {
        logView.post(new Runnable() {
            @Override
            public void run() {
                if (isError) {
                    logView.setTextColor(Color.RED);
                } else {
                    logView.setTextColor(Color.BLACK);
                }
                logView.setText(logs + "\n" + logView.getText());
            }
        });
    }

    public void clearLogs() {
        logView.setText("");
    }

    public void startCapture(int minQuality, int timeOut) {
        if (isStartCaptureRunning) {
            //setLogs("StartCapture Ret: " + MorfinAuthNative.CAPTURE_ALREADY_STARTED
            //        + " (" + morfinAuth.GetErrorMessage(MorfinAuthNative.CAPTURE_ALREADY_STARTED) + ")", true);
            return;
        }
        if (isStopCaptureRunning) {
            return;
        }
        //if (lastDeviceInfo == null) {
        //    setLogs("Please run device init first", true);
        //    return;
        //}
        isStartCaptureRunning = true;
        scannerAction = ScannerAction.Capture;
        try {
            setLogs("Start capture: " + minQuality + " : " + timeOut, false);
            int ret = morfinAuth.StartCapture(minQuality, timeOut);
            if (ret != 0) {
                isStartCaptureRunning = false;
            }
            setLogs("StartCapture Ret: " + ret + " (" + morfinAuth.GetErrorMessage(ret) + ")", ret == 0 ? false : true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void complete(int errorCode, int quality, int nfiq) {
        try {
            isStartCaptureRunning = false;
            if (errorCode == 0) {
                setLogs("Capture Success" + quality, false);

                setLogs("Scanner action: " + scannerAction.name(), false);
                if (scannerAction == ScannerAction.Capture) {
                    int Size = lastDeviceInfo.Width * lastDeviceInfo.Height + 1111;
                    byte[] bImage = new byte[Size];
                    int[] tSize = new int[Size];
                    int ret = morfinAuth.GetImage(bImage, tSize, 1, captureImageData);
                    if (ret == 0) {
                        setLogs("Got image from reader: " + Size, false);

                        File imagePath = new File(getApplicationContext().getFilesDir(), "scan_images");
                        if(!imagePath.exists()) {
                            imagePath.mkdir();
                        }

                        try {
                            File outputFile = File.createTempFile("fpr", ".png", imagePath);
                            FileOutputStream of = new FileOutputStream(outputFile);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bImage, 0, bImage.length);
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, of);

                            Uri uri = FileProvider.getUriForFile(getApplicationContext(), "au.com.smap.FingerprintReader.fileprovider", outputFile);

                            setLogs(uri.toString(), false);

                            model.getImage().postValue(uri);
                        } catch (Exception e) {
                            e.printStackTrace();
                            setLogs("Error: " + e.getMessage(), true);
                        }
                    } else {
                        setLogs("Get Image: " + morfinAuth.GetErrorMessage(ret), true);
                    }
                }


            } else {
                if(errorCode == -2057){
                    setLogs("Device Not Connected",true);
                }else{
                    setLogs("CaptureComplete: " + errorCode + " (" + morfinAuth.GetErrorMessage(errorCode) + ")", true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getTemplate() {
        return bytesToHex(lastCapFingerData);
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void initialise() {
        try {
            DeviceInfo info = new DeviceInfo();
            int ret = morfinAuth.Init(DeviceModel.valueFor(currentDevice), (clientKey.isEmpty()) ? null : clientKey, info);
            lastDeviceInfo = info;
            if (ret != 0) {
                setLogs("Init: " + ret + " (" + morfinAuth.GetErrorMessage(ret) + ")", true);
            } else {
                setLogs("Init Success", false);
                //setDeviceInfo(info);
            }
        } catch (Exception e) {
            e.printStackTrace();
            setLogs("Initilialisation Failed " + e.getMessage(), false);
            currentDevice = null;
        }
    }
}