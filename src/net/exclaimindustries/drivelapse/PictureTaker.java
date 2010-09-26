/**
 * PictureTaker.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.io.File;
import java.io.FileOutputStream;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.location.Location;
import android.util.Log;

/**
 * @author captainspam
 *
 */
public class PictureTaker {
    private static final String DEBUG_TAG = "PictureTaker";
    
    private String mPackageName;
    private String mDirName;
    private Context mContext;

    public PictureTaker(String packageName, Context context) {
        mPackageName = packageName;
        mContext = context;
    }
    
    /**
     * Restarts this instance of PictureTaker.  That is to say, it starts a new
     * directory and readies any further SinglePictures for picturing.  Note
     * that past this, any old SinglePictures are invalid, and the behavior
     * from continuing to use them is undefined.
     * 
     * If the directory specified already exists, this will simply resume adding
     * pictures to it.
     * 
     * @param currentTime the time of this session (and thus part of the name of
     *                    the directory to be made); this is intended to be the
     *                    current system time as retrieved by the static
     *                    System.currentTimeMillis() method, and will be
     *                    divided by 1000
     * @return true if we're good to go, false if not
     */
    public boolean restart(long currentTime) {
        // Create the directory.
        mDirName = "/sdcard/" + mPackageName + "/DriveLapse-" + (currentTime / 1000) + "/";
        
        File dir = new File(mDirName);
        
        if(dir.exists() && dir.isDirectory()) {
            // Directory already exists, we're in a resume situation.
            Log.i(DEBUG_TAG, "Directory " + mDirName + " already exists, using that...");
            return true;
        } else if(dir.exists() && !dir.isDirectory()) {
            // That file exists, but ISN'T a directory?  What?
            Log.e(DEBUG_TAG, mDirName + " already exists, but doesn't appear to be a directory!");
            return false;
        } else {
            // Make the directory.
            boolean success = dir.mkdirs();
            
            if(success)
                Log.d(DEBUG_TAG, "Directory " + mDirName + " created.");
            else
                Log.e(DEBUG_TAG, "Couldn't create " + mDirName + "!");

            return success;
        }
    }
    
    /**
     * Gets a SinglePicture handle, prepped with the given Location and the
     * current directory name.
     * 
     * @param loc Location of choice
     * @return a SinglePicture, of course
     */
    public SinglePicture getPictureHandle(Location loc) {
        return new SinglePicture(loc, mDirName, mContext);
    }
    
    /**
     * A SinglePicture is one unit of picture-taking.  This stores the Location
     * of a single picture, writes the picture, and shoves it out to the
     * Annotator.
     * 
     * @author captainspam
     */
    public class SinglePicture implements Camera.PictureCallback {
        
        private Location mLocation;
        private String mDirName;
        private Context mContext;
        
        /**
         * Constructs a SinglePicture with the given Location, ready for action.
         * 
         * @param loc Location at which this picture took place.
         */
        private SinglePicture(Location loc, String dirName, Context context) {
            mLocation = loc;
            mDirName = dirName;
            mContext = context;
        }

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            String filename = mDirName + mLocation.getTime() + ".jpg";
            File output = new File(filename);
            try {
                // Write it out to SD as soon as possible, then pass the file
                // location and the GPS location off to the AssemblyLine.
                FileOutputStream ostream = new FileOutputStream(output);
                ostream.write(data);
                ostream.close();
                
                AssemblyLine.WorkOrder order = new AssemblyLine.WorkOrder(filename, mLocation);
                Intent i = new Intent(mContext, AssemblyLine.class);
                i.putExtra(AssemblyLine.WORK_ORDER, order);
                mContext.startService(i);
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "EXCEPTION!");
                e.printStackTrace();
            }
            camera.startPreview();
        }
        
    }
}
