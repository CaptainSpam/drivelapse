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
import java.io.Serializable;

import android.hardware.Camera;
import android.location.Location;
import android.util.Log;

/**
 * @author captainspam
 *
 */
public class PictureTaker implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String DEBUG_TAG = "PictureTaker";
    
    private String mPackageName;
    private String mDirName;
    private AssemblyLine mAl;

    public PictureTaker(String packageName) {
        mPackageName = packageName;
    }
    
    /**
     * Restarts this instance of PictureTaker.  That is to say, it starts a new
     * directory and readies any further SinglePictures for picturing.  Note
     * that past this, any old SinglePictures are invalid, and the behavior
     * from continuing to use them is undefined.
     * 
     * @return true if the directory was created, false if not
     */
    public boolean restart(AssemblyLine al) {
        mAl = al;
        // Create the directory.
        mDirName = "/sdcard/" + mPackageName + "/DriveLapse-" + (System.currentTimeMillis() / 1000) + "/";
        boolean success = (new File(mDirName)).mkdirs();
        
        if(success)
            Log.d(DEBUG_TAG, "Directory " + mDirName + " created.");
        else
            Log.e(DEBUG_TAG, "Couldn't create " + mDirName + "!");
        
        return success;
    }
    
    /**
     * Gets a SinglePicture handle, prepped with the given Location and the
     * current directory name.
     * 
     * @param loc Location of choice
     * @return a SinglePicture, of course
     */
    public SinglePicture getPictureHandle(Location loc) {
        return new SinglePicture(loc, mDirName, mAl);
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
        private AssemblyLine mmAl;
        
        /**
         * Constructs a SinglePicture with the given Location, ready for action.
         * 
         * @param loc Location at which this picture took place.
         */
        private SinglePicture(Location loc, String dirName, AssemblyLine al) {
            mLocation = loc;
            mDirName = dirName;
            mmAl = al;
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
                mmAl.addWorkOrder(order);
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "EXCEPTION!");
                e.printStackTrace();
            }
            camera.startPreview();
        }
        
    }
}
