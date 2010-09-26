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
    
    private static final String SAVE_STATE = "State";
    private static final String SAVE_ACTIVE_DATE = "ActiveDate";
    
    /** The recording is stopped entirely.  Display the Go button. */
    private static final int STATE_STOP = 0;
    /** We're recording!  Display the Pause button. */
    private static final int STATE_RECORD = 1;
    /** We've paused.  Display both the Go and Stop buttons. */
    private static final int STATE_PAUSE = 2;
    
    private int mLastState;

    private LocationManager mLocationManager;
    
    private Button mGoButton;
    private Button mStopButton;
    private Button mPauseButton;
    
    private TextView mTextView;
    
    private int mCount;
    
    private Location mLastLoc;
    
    private WakeLock mWakeLock;
    
    private Camera mCamera;
    
    private PictureTaker mPictureTaker;
    
    private ScrollView mScroller;
    private SurfaceView mSurface;
    
    private long mActiveDate = -1;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        
        mGoButton = (Button)findViewById(R.id.gobutton);
        mStopButton = (Button)findViewById(R.id.stopbutton);
        mPauseButton = (Button)findViewById(R.id.pausebutton);
        mTextView = (TextView)findViewById(R.id.textstuff);
        mScroller = (ScrollView)findViewById(R.id.debugscroller);
        mSurface = (SurfaceView)findViewById(R.id.camerasurface);
        
        mLastState = STATE_STOP;
        
        SurfaceHolder holder = mSurface.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mPictureTaker = new PictureTaker(getPackageName(), this);
        
        PowerManager pl = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pl.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, DEBUG_TAG);
        
        mGoButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // First test: Every 100 meters.  So... um... 400 feet or so?
                String logString;
                
                // If we were stopped, make a new AssemblyLine.
                if(mLastState == STATE_STOP) {
                    // Hold on to the current time.
                    mActiveDate = System.currentTimeMillis();
                    mPictureTaker.restart(mActiveDate);
                    mCount = 0;
                    logString = "\n\n--- START! ---\n";
                } else {
                    logString = "--- RESUME! ---\n";
                }
                
                switchButtonStates(STATE_RECORD);

                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 100, DriveLapse.this);
                if(!mWakeLock.isHeld()) mWakeLock.acquire();
                writeLog(logString);
            }
        });
        
        mStopButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // STOP
                switchButtonStates(STATE_STOP);
                mActiveDate = -1;
                mLocationManager.removeUpdates(DriveLapse.this);
                if(mWakeLock.isHeld()) mWakeLock.release();
                writeLog("--- END ---\nTotal clicks: " + mCount + "\n");
            }
            
        });
        
        mPauseButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // PAUSE
                switchButtonStates(STATE_PAUSE);
                mLocationManager.removeUpdates(DriveLapse.this);
                // We don't add in the end order yet.  We just pause updates.
                if(mWakeLock.isHeld()) mWakeLock.release();
                writeLog("--- PAUSED ---\n");
            }
            
        });
        
        mCount = 0;
        
        // Grab a LocationManager!
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        
        // Good.  Now, repopulate mAssembly and mPictureTaker if need be.  If
        // need DOES be, chances are we'll need to reset the go button, too.
        if(savedInstanceState != null
                && savedInstanceState.containsKey(SAVE_STATE)
                && savedInstanceState.containsKey(SAVE_ACTIVE_DATE)) {
            // We have a state and an active date (hopefully).  If said date is
            // actually valid, restart the PictureTaker.
            mActiveDate = savedInstanceState.getLong(SAVE_ACTIVE_DATE);
            if(mActiveDate >= 0) {
                mPictureTaker.restart(mActiveDate);
            }
            
            // The state determines if we should be looking for locations right
            // away.
            int state = savedInstanceState.getInt(SAVE_STATE);
            
            if(state == STATE_RECORD) {
                // We're recording!  LocationManager, back to work!  We need to
                // get started immediately!
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 100, this);
            }
            
            // And switch the buttons to whatever they need to be.
            switchButtonStates(state);
        } else {
            // No saved state.  Start in stop mode.
            switchButtonStates(STATE_STOP);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
     
        // Right!  Save everything!
        outState.putLong(SAVE_ACTIVE_DATE, mActiveDate);
        outState.putInt(SAVE_STATE, mLastState);
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
            writeLog("\n\n*** Turn GPS on and try again... ***\n");
        } else {
            writeLog("\n\nReady.\n");
        }
    }
    
    @Override
    public void onLocationChanged(Location loc) {
        writeLog("Location: " + loc.getLatitude() + "," + loc.getLongitude() + "\n");
        if(mLastLoc != null) {
            writeLog("(displacement: " + mLastLoc.distanceTo(loc) + ")\n");
        }
        mLastLoc = loc;

        if(mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            params.setGpsLatitude(loc.getLatitude());
            params.setGpsLongitude(loc.getLongitude());
            params.setGpsTimestamp(loc.getTime());
            params.setGpsAltitude(loc.getAltitude());
            
            // TODO: This should be set by an option!
            params.setPictureSize(1024, 768);
            mCamera.setParameters(params);
            mCamera.takePicture(null, null, mPictureTaker.getPictureHandle(loc));
        }
        mCount++;
    }

    @Override
    public void onProviderDisabled(String provider) {
        writeLog("PROVIDER DISABLED\n");
        
    }

    @Override
    public void onProviderEnabled(String provider) {
        writeLog("PROVIDER ENABLED\n");
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
    
    private void switchButtonStates(int newState) {
        mLastState = newState;

        if(mGoButton == null || mStopButton == null || mPauseButton == null) {
            return;
        }
        
        switch(mLastState) {
            case STATE_RECORD:
                // Aha!  We're recording!  Only the pause button should be
                // visible at this point.
                mGoButton.setVisibility(View.GONE);
                mStopButton.setVisibility(View.GONE);
                mPauseButton.setVisibility(View.VISIBLE);
                break;
            case STATE_PAUSE:
                // We've paused.  We want to give either the stop or go options.
                // This implies the user needs to pause before stopping.
                mGoButton.setVisibility(View.VISIBLE);
                mStopButton.setVisibility(View.VISIBLE);
                mPauseButton.setVisibility(View.GONE);
                break;
            case STATE_STOP:
                // We're stopped entirely.  This means we're just coming in from
                // the start.  Only the Go button shows up here.
                mGoButton.setVisibility(View.VISIBLE);
                mStopButton.setVisibility(View.GONE);
                mPauseButton.setVisibility(View.GONE);
        }
    }
    
    private void writeLog(String data) {
        if(mTextView == null || mScroller == null) return;
        
        mTextView.append(data);
        mScroller.fullScroll(View.FOCUS_DOWN);
    }
}