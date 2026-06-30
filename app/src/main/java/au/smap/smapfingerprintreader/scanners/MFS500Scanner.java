package au.smap.smapfingerprintreader.scanners;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.mantra.morfinauth.DeviceInfo;
import com.mantra.morfinauth.MorfinAuth;
import com.mantra.morfinauth.MorfinAuth_Callback;
import com.mantra.morfinauth.enums.DeviceDetection;
import com.mantra.morfinauth.enums.DeviceModel;
import com.mantra.morfinauth.enums.ImageFormat;
import com.mantra.morfinauth.enums.LogLevel;
import com.mantra.morfinauth.enums.TemplateFormat;

import au.smap.smapfingerprintreader.application.FingerprintReader;
import au.smap.smapfingerprintreader.model.ScannerViewModel;
import au.smap.smapfingerprintreader.utilities.FileUtilities;

public class MFS500Scanner extends Scanner implements MorfinAuth_Callback {
    FingerprintReader app;
    Context context;
    private DeviceInfo lastDeviceInfo;
    TemplateFormat captureTemplateDatas;
    ImageFormat captureImageData;
    private enum ScannerAction {
        Capture, MatchISO, MatchAnsi
    }
    private ScannerAction scannerAction = ScannerAction.Capture;
    public MorfinAuth morfinAuth;
    public MFS500Scanner(Context context) {
        this.context = context;
        app = FingerprintReader.getInstance();
        app.setLogs("setScanner", false);
        morfinAuth = new MorfinAuth(context, this);
        app.setLogs("Scanner added", false);

        captureImageData = (ImageFormat.BMP);
        captureTemplateDatas = (TemplateFormat.FMR_V2005);

    }

    /*
     * Fingerprint reader callback functions
     * Called when the device is connected or disconnected
     */
    @Override
    public void OnDeviceDetection(String deviceName, DeviceDetection detection) {
        if (detection == DeviceDetection.CONNECTED) {
            app.model.getScannerState().postValue(ScannerViewModel.CONNECTED);

        } else if (detection == DeviceDetection.DISCONNECTED) {
            app.setLogs("Device Not Connected", true);
            app.model.getScannerState().postValue(ScannerViewModel.DISCONNECTED);
        } else {
            app.setLogs("Unknown device detection status: " + detection.toString(), true);
        }
    }

    @Override
    public void OnPreview(int errorCode, int quality, byte[] image) {
        try {
            if (errorCode == 0 && image != null) {

                app.setLogs("Preview Quality: " + quality, false);
            } else {
                if(errorCode == -2057){
                    app.setLogs("Device Not Connected",true);
                }else{
                    app.setLogs("Preview Error Code: " + errorCode + " (" + morfinAuth.GetErrorMessage(errorCode) + ")", true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void OnComplete(int errorCode, int quality, int nfiq) {
        app.setLogs("Complete" + errorCode, false);
        try {
            if (errorCode == 0) {
                app.setLogs("Capture Success" + quality, false);
                if (scannerAction == ScannerAction.Capture) {
                    int Size = lastDeviceInfo.Width * lastDeviceInfo.Height + 1111;
                    byte[] bImage = new byte[Size];
                    int[] tSize = new int[Size];
                    int ret = morfinAuth.GetImage(bImage, tSize, 1, captureImageData);
                    if (ret == 0) {
                        app.setLogs("Got image from reader: " + Size, false);

                        Bitmap bitmap = BitmapFactory.decodeByteArray(bImage, 0, bImage.length);
                        if (bitmap == null) {
                            app.setLogs("Could not decode fingerprint image", true);
                            app.model.getScannerState().postValue(ScannerViewModel.ERROR);
                            return;
                        }
                        Uri uri = FileUtilities.getUri(context, app, bitmap);
                        app.model.getImage().postValue(uri);

                    } else {
                        app.setLogs("Get Image: " + morfinAuth.GetErrorMessage(ret), true);
                    }
                }

            } else {
                if(errorCode == -2057){
                    app.setLogs("Device Not Connected",true);
                } else{
                    app.setLogs("Error: " + errorCode + " (" + morfinAuth.GetErrorMessage(errorCode) + ")", true);
                }
                app.model.getScannerState().postValue(ScannerViewModel.ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void connect() {
        app.setLogs("MFS500 Connect - Not Used", false);
    }
    private void getFingerprint(int minQuality, int timeOut) {

        if(!morfinAuth.IsCaptureRunning()) {
            app.model.getScannerState().postValue(ScannerViewModel.SCANNING);

            scannerAction = ScannerAction.Capture;
            try {
                app.setLogs("Start capture: " + minQuality + " : " + timeOut, false);
                int ret = morfinAuth.StartCapture(minQuality, timeOut);
                app.setLogs("StartCapture Ret: " + ret + " (" + morfinAuth.GetErrorMessage(ret) + ")", ret == 0 ? false : true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void isConnected() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(morfinAuth.IsDeviceConnected(DeviceModel.valueFor("MFS500"))) {
                        startCapture();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    app.setLogs("Device not found", true);
                }
            }
        }).start();
    }
    public void destroy() {
        app.setLogs("Destroy", false);
        if(morfinAuth != null) {
            morfinAuth.Uninit();
            morfinAuth.Dispose();
            morfinAuth = null;
        }
    }

    public void startCapture() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DeviceInfo info = new DeviceInfo();
                    int ret = morfinAuth.Init(DeviceModel.valueFor("MFS500"), info);
                    lastDeviceInfo = info;
                    if (ret != 0) {
                        app.setLogs("Init: " + ret + " (" + morfinAuth.GetErrorMessage(ret) + ")", true);
                    } else {
                        app.setLogs("Init Success", false);
                    }
                    getFingerprint(app.minQuality, app.timeout);

                } catch (Exception e) {
                    e.printStackTrace();
                    app.setLogs("Initialization Failed " + e.getMessage(), false);
                }
            }
        }).start();
    }
}
