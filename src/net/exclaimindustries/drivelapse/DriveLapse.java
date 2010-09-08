package net.exclaimindustries.drivelapse;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
//import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.hardware.Camera;

public class DriveLapse extends Activity implements LocationListener, SurfaceHolder.Callback {
    private static final String DEBUG_TAG = "DriveLapse";
    
    private static final String SAVE_ASSEMBLY_LINE = "AssemblyLine";
    private static final String SAVE_PICTURE_TAKER = "PictureTaker";
    private static final String SAVE_STATE = "State";
    
    private static final int STATE_STOP = 0;
    private static final int STATE_RECORD = 1;
    private static final int STATE_PAUSE = 2;
    
    private int mLastState;

    private LocationManager mLocationManager;
    
    private Button mGoButton;
    private Button mStopButton;
    
    private TextView mTextView;
    
    private int mCount;
    
    private Location mLastLoc;
    
    private WakeLock mWakeLock;
    
    private Camera mCamera;
    
    private PictureTaker mPictureTaker;
    private AssemblyLine mAssembly;
    
    private ScrollView mScroller;
    private SurfaceView mSurface;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        mGoButton = (Button)findViewById(R.id.gobutton);
        mStopButton = (Button)findViewById(R.id.stopbutton);
        mTextView = (TextView)findViewById(R.id.textstuff);
        mScroller = (ScrollView)findViewById(R.id.debugscroller);
        mSurface = (SurfaceView)findViewById(R.id.camerasurface);
        
        mLastState = STATE_STOP;
        
        SurfaceHolder holder = mSurface.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mPictureTaker = new PictureTaker(getPackageName());
        
        PowerManager pl = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pl.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, DEBUG_TAG);
        
        mGoButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // First test: Every 100 meters.  So... um... 400 feet or so?
                
                // We create a new AssemblyLine and set of Stations every time
                // we start.
                if(mAssembly != null) mAssembly.addEndOrder();
                
                mAssembly = new AssemblyLine();
                
                mAssembly.addStation(new Annotator(mAssembly, DriveLapse.this));
                mAssembly.start();
                mPictureTaker.restart(mAssembly);
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 100, DriveLapse.this);
                mGoButton.setEnabled(false);
                mCount = 0;
                if(!mWakeLock.isHeld()) mWakeLock.acquire();
                mTextView.append("\n\n--- START ---\n");
                mScroller.fullScroll(View.FOCUS_DOWN);
            }
        });
        
        mStopButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // STOP
                mLocationManager.removeUpdates(DriveLapse.this);
                mAssembly.addEndOrder();
                mGoButton.setEnabled(true);
                if(mWakeLock.isHeld()) mWakeLock.release();
                mTextView.append("--- END ---\nTotal clicks: " + mCount + "\n");
                mScroller.fullScroll(View.FOCUS_DOWN);
            }
            
        });
        
        mCount = 0;
        
        // Grab a LocationManager!
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        
        // Good.  Now, repopulate mAssembly and mPictureTaker if need be.  If
        // need DOES be, chances are we'll need to reset the go button, too.
        if(savedInstanceState != null
                && savedInstanceState.containsKey(SAVE_PICTURE_TAKER)
                && savedInstanceState.containsKey(SAVE_ASSEMBLY_LINE)
                && savedInstanceState.containsKey(SAVE_STATE)) {
            mAssembly = (AssemblyLine)savedInstanceState.get(SAVE_ASSEMBLY_LINE);
            mPictureTaker = (PictureTaker)savedInstanceState.get(SAVE_PICTURE_TAKER);
            
            // The state determines if we should be looking for locations right
            // away.
            mLastState = savedInstanceState.getInt(SAVE_STATE);
            
            if(mLastState == STATE_RECORD) {
                // We're recording!  LocationManager, back to work!
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 100, this);
                mGoButton.setEnabled(false);
            }
            
            if(mLastState == STATE_STOP) {
                mGoButton.setEnabled(true);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
     
        // Right!  Save everything!
        outState.putSerializable(SAVE_ASSEMBLY_LINE, mAssembly);
        outState.putSerializable(SAVE_PICTURE_TAKER, mPictureTaker);
    }

    @Override
    public void finish() {
        // Make sure the assembly line completes itself.
        if(mAssembly != null) mAssembly.addEndOrder();
        super.finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        mLocationManager.removeUpdates(this);
        if(mWakeLock.isHeld()) mWakeLock.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if(!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mGoButton.setEnabled(false);
            mStopButton.setEnabled(false);
            mTextView.append("\n\n*** Turn GPS on and try again... ***\n");
            mScroller.fullScroll(View.FOCUS_DOWN);
        } else {
            mTextView.append("\n\nReady.\n");
            mScroller.fullScroll(View.FOCUS_DOWN);
        }
    }
    
    @Override
    public void onLocationChanged(Location loc) {
        mTextView.append("Location: " + loc.getLatitude() + "," + loc.getLongitude() + "\n");
        if(mLastLoc != null) {
            mTextView.append("(displacement: " + mLastLoc.distanceTo(loc) + ")\n");
        }
        mScroller.fullScroll(View.FOCUS_DOWN);
        mLastLoc = loc;

        if(mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            params.setGpsLatitude(loc.getLatitude());
            params.setGpsLongitude(loc.getLongitude());
            params.setGpsTimestamp(loc.getTime());
            params.setGpsAltitude(loc.getAltitude());
            mCamera.setParameters(params);
            mCamera.takePicture(null, null, mPictureTaker.getPictureHandle(loc));
        }
        mCount++;
    }

    @Override
    public void onProviderDisabled(String provider) {
        mTextView.append("PROVIDER DISABLED\n");
        mScroller.fullScroll(View.FOCUS_DOWN);
        
    }

    @Override
    public void onProviderEnabled(String provider) {
        mTextView.append("PROVIDER ENABLED\n");
        mScroller.fullScroll(View.FOCUS_DOWN);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
        Camera.Parameters params = mCamera.getParameters();
        
        // It appears that not setting the preview size seems to just make it
        // take up the whole screen.  Which is good, as that's what we need to
        // do, and I've seen crashes on some phones (i.e. the Droid) if I
        // manually feed in a size it doesn't like (i.e. no title but with
        // notifications still on).
        
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        mCamera.setParameters(params);
        mCamera.startPreview();
        
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mCamera = Camera.open();
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (Exception e) {
            mCamera.release();
            mCamera = null;
            e.printStackTrace();
        }
        
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }
}